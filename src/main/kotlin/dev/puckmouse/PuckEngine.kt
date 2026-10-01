package dev.puckmouse

import com.sun.jna.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class SizeT(value: Long=0) : IntegerType(Native.SIZE_T_SIZE,value,true) {
    override fun toByte()=toLong().toByte()
    override fun toShort()=toLong().toShort()
}
interface PuckAbi : Library {
    fun puck_abi_version(): Int
    fun puck_create(json: Pointer,length: SizeT): Int
    fun puck_destroy(handle: Int): Int
    fun puck_feed(handle: Int,time: Double,x: Double,y: Double,z: Double,rx: Double,ry: Double,rz: Double): Int
    fun puck_command(handle: Int,json: Pointer,length: SizeT): Int
    fun puck_response_len(handle: Int): SizeT
    fun puck_response_copy(handle: Int,out: Pointer,capacity: SizeT): SizeT
}
const val PUCK_DLL_SHA256="70d68ca3ce66547214410cb0ba99011b90eb0315e05ebee21affffde0ced1853"
fun defaultDllPath(): Path {
    val resources=System.getProperty("compose.application.resources.dir")
    return if(resources!=null) Path.of(resources,"puck-ffi-windows-x64.dll") else Path.of("native-resources/windows/puck-ffi-windows-x64.dll").toAbsolutePath()
}
class PuckEngine(profile: Profile, file: Path=defaultDllPath()) : AutoCloseable {
    private val owner=Thread.currentThread()
    private val abi: PuckAbi
    private val mappings=profile.mappings
    private var handle=0
    init {
        require(file.isAbsolute && Files.isRegularFile(file)) { "The bundled Puck DLL is missing" }
        val hash=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)).joinToString(""){"%02x".format(it)}
        require(hash==PUCK_DLL_SHA256) { "The Puck DLL does not match this app's pinned release" }
        abi=Native.load(file.toString(),PuckAbi::class.java)
        check(abi.puck_abi_version()==1) { "Unsupported Puck ABI version" }
        val controls=buildJsonObject {
            for(m in mappings) put(m.output.name,buildJsonObject {
                put("kind","continuous"); put("source","axes")
                put("options",buildJsonObject {
                    put("as","velocity"); put("speed",if(m.enabled)m.speed else 0.0)
                    put("deadzone",m.deadzone); put("curve",m.curve); put("responseMs",m.responseMs)
                    put("scale",buildJsonObject { for(a in Axis.entries) put(a.wire,if(a==m.axis) if(m.inverted)-1.0 else 1.0 else 0.0) })
                })
            })
        }
        val definition=buildJsonObject { put("kind","puck"); put("options",buildJsonObject {put("controls",controls);put("maxFrameMs",24)}) }
        withBytes(definition.toString()) { p,n -> handle=abi.puck_create(p,n) }
        check(handle!=0) { response(0).toString() }
    }
    private fun checkThread()=check(Thread.currentThread()===owner) { "Puck engine calls must stay on their owning OS thread" }
    private fun response(id: Int=handle): JsonElement {
        val size=abi.puck_response_len(id).toLong(); check(size in 1..1_048_576) { "Invalid Puck response length" }
        return Memory(size).use { out -> check(abi.puck_response_copy(id,out,SizeT(size)).toLong()==size)
            val envelope=Json.parseToJsonElement(String(out.getByteArray(0,size.toInt()),Charsets.UTF_8)).jsonObject
            check(envelope["ok"]!!.jsonPrimitive.boolean) { envelope["error"].toString() }
            envelope["value"] ?: JsonNull
        }
    }
    private inline fun withBytes(text: String, fn: (Pointer,SizeT)->Unit) {
        val bytes=text.toByteArray(Charsets.UTF_8); Memory(bytes.size.toLong()).use { p -> p.write(0,bytes,0,bytes.size); fn(p,SizeT(bytes.size.toLong())) }
    }
    private fun command(request: JsonObject): JsonElement {
        checkThread(); check(handle!=0)
        withBytes(request.toString()) { p,n -> check(abi.puck_command(handle,p,n)==1) { response().toString() } }
        return response()
    }
    fun feed(time: Double,axes: Axes) { checkThread(); check(axes.values().all{it.isFinite() && it in -1.0..1.0})
        check(abi.puck_feed(handle,time,axes.x,axes.y,axes.z,axes.rx,axes.ry,axes.rz)==1) { response().toString() }
    }
    fun frame(time: Double): Map<Output,Double> {
        val value=command(buildJsonObject {put("op","frame");put("time",time)}).jsonObject["value"]!!.jsonObject["results"]!!.jsonObject
        return mappings.associate { m -> m.output to value["controls.${m.output.name}"]!!.jsonObject[m.axis.wire]!!.jsonPrimitive.double }
    }
    fun rates(): Map<Output,Double> = mappings.associate { m ->
        val value=command(buildJsonObject {put("op","read");put("control","controls.${m.output.name}")}).jsonObject["value"]!!.jsonObject
        m.output to value[m.axis.wire]!!.jsonPrimitive.double
    }
    fun interrupt(time: Double) {
        command(buildJsonObject {put("op","interrupt");put("time",time);put("reason","pause")})
        // Puck retains the integrated interval before cancellation; the desktop host discards it.
        frame(time)
    }
    override fun close() { checkThread(); if(handle!=0) {check(abi.puck_destroy(handle)==1);handle=0} }
}

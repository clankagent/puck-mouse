package dev.puckmouse

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed interface Report {
    data class Motion(val axes: Axes): Report
    data class Buttons(val mask: Int): Report
}
object WirelessReportDecoder {
    fun decode(bytes: ByteArray): Report? {
        if(bytes.size!=13) return null
        return when(bytes[0].toInt() and 255) {
            1 -> { val b=ByteBuffer.wrap(bytes,1,12).order(ByteOrder.LITTLE_ENDIAN)
                val a=List(6){(b.short.toDouble()/350.0).coerceIn(-1.0,1.0)}
                Report.Motion(Axes(a[0],a[1],a[2],a[3],a[4],a[5])) }
            3 -> Report.Buttons(bytes[1].toInt() and 3)
            else -> null
        }
    }
}
/** A pause/disconnect/configuration change discards all fractions, including wheel carry. */
class OutputAccumulator {
    private val fractions=DoubleArray(4)
    fun take(delta: Map<Output,Double>): Map<Output,Int> = Output.entries.associateWith { out ->
        val amount=(delta[out] ?: 0.0)*if(out.name.startsWith("SCROLL"))120.0 else 1.0
        require(amount.isFinite())
        if(amount*fractions[out.ordinal]<0) fractions[out.ordinal]=0.0
        fractions[out.ordinal]+=amount
        val whole=fractions[out.ordinal].toInt(); fractions[out.ordinal]-=whole
        whole
    }
    fun clear()=fractions.fill(0.0)
}
class NeutralGate {
    var armed=false; private set
    fun interrupt() {armed=false}
    fun observeRealMotion(axes: Axes, noButtonsHeld: Boolean): Boolean {
        if(!armed && axes.neutral() && noButtonsHeld) armed=true
        return armed
    }
}
/** Buttons are balanced against the action captured on down, even if settings change later. */
class ButtonTracker(private val send: (ButtonAction,Boolean)->Unit, private val pause: (Boolean?)->Unit) {
    private val held=mutableMapOf<Int,ButtonAction>()
    private var mask=0
    fun report(next: Int, actions: List<ButtonAction>, active: Boolean) {
        var canOutput=active
        for(i in 0..1) {
            val bit=1 shl i
            if(next and bit!=0 && mask and bit==0) {
                val action=actions[i]
                when(action) {
                    ButtonAction.PAUSE_TOGGLE -> {canOutput=false;pause(null)}
                    ButtonAction.PAUSE_HOLD -> {canOutput=false;held[i]=action;pause(true)}
                    ButtonAction.NONE -> {}
                    else -> if(canOutput) {held[i]=action;send(action,true)}
                }
            }
            if(next and bit==0 && mask and bit!=0) held[i]?.let { action ->
                if(action==ButtonAction.PAUSE_HOLD) pause(false) else send(action,false)
                held.remove(i)
            }
        }
        mask=next
    }
    fun release() {
        var error: Exception?=null
        for((index,action) in held.toMap()) if(action!=ButtonAction.PAUSE_HOLD) {
            try {send(action,false);held.remove(index)} catch(e:Exception) {error=e}
        }
        error?.let {throw it}
    }
    fun disconnect() {release();held.clear();mask=0}
    fun allReleased()=mask==0
}

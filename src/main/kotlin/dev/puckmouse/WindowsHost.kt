package dev.puckmouse

import com.sun.jna.*
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

interface RawUser32 : StdCallLibrary {
    fun GetRawInputDeviceList(list: Pointer?,count: IntByReference,size: Int): Int
    fun GetRawInputDeviceInfoW(device: Pointer,command: Int,data: Pointer,size: IntByReference): Int
    fun RegisterRawInputDevices(devices: Array<RawDevice>,count: Int,size: Int): Boolean
    fun GetRawInputData(input: Pointer,command: Int,data: Pointer?,size: IntByReference,headerSize: Int): Int
    fun RegisterHotKey(window: WinDef.HWND?,id: Int,modifiers: Int,key: Int): Boolean
    fun UnregisterHotKey(window: WinDef.HWND?,id: Int): Boolean
}
@Structure.FieldOrder("usagePage","usage","flags","window")
class RawDevice : Structure() {
    @JvmField var usagePage: Short=1
    @JvmField var usage: Short=8
    @JvmField var flags: Int=0
    @JvmField var window: WinDef.HWND?=null
}
class WindowsOutput {
    private fun send(flags: Int,dx: Int=0,dy: Int=0,data: Int=0) {
        val input=WinUser.INPUT(); input.type=WinDef.DWORD(0)
        input.input.setType(WinUser.MOUSEINPUT::class.java)
        input.input.mi.dx=WinDef.LONG(dx.toLong());input.input.mi.dy=WinDef.LONG(dy.toLong())
        input.input.mi.mouseData=WinDef.DWORD(data.toLong() and 0xffffffffL)
        input.input.mi.dwFlags=WinDef.DWORD(flags.toLong());input.input.mi.time=WinDef.DWORD(0)
        input.input.mi.dwExtraInfo=BaseTSD.ULONG_PTR(0x5055434b)
        input.write()
        @Suppress("UNCHECKED_CAST") val inputs=input.toArray(1) as Array<WinUser.INPUT>
        if(User32.INSTANCE.SendInput(WinDef.DWORD(1),inputs,input.size()).toInt()!=1)
            throw IllegalStateException("Windows blocked desktop input. Puck Mouse can control apps running at the same privilege level; it does not need administrator access.")
    }
    fun motion(delta: Map<Output,Int>) {
        val x=delta[Output.POINTER_X]?:0; val y=delta[Output.POINTER_Y]?:0
        if(x!=0 || y!=0) send(0x0001,x,y)
        val vertical=delta[Output.SCROLL_Y]?:0; if(vertical!=0) send(0x0800,data=vertical)
        val horizontal=delta[Output.SCROLL_X]?:0; if(horizontal!=0) send(0x1000,data=horizontal)
    }
    fun button(action: ButtonAction,down: Boolean) {
        val pair=when(action) {
            ButtonAction.LEFT_CLICK -> 0x0002 to 0x0004
            ButtonAction.RIGHT_CLICK -> 0x0008 to 0x0010
            ButtonAction.MIDDLE_CLICK -> 0x0020 to 0x0040
            ButtonAction.BACK,ButtonAction.FORWARD -> 0x0080 to 0x0100
            else -> return
        }
        send(if(down)pair.first else pair.second,data=if(action==ButtonAction.BACK)1 else if(action==ButtonAction.FORWARD)2 else 0)
    }
}

class WindowsHost(private val state: ()->AppState, private val publish: ((AppState)->AppState)->Unit, private val allowDesktopOutput: Boolean=true,
    private val clock: ()->Double = {System.nanoTime()/1_000_000.0}) : AutoCloseable {
    private val queue=ConcurrentLinkedQueue<()->Unit>()
    private val running=AtomicBoolean(true)
    private val thread=Thread(::run,"Puck Mouse input").apply {isDaemon=true}
    private var raw: RawUser32?=null
    private var window: WinDef.HWND?=null
    private var engine: PuckEngine?=null
    private val output=WindowsOutput()
    private val fractions=OutputAccumulator()
    private val gate=NeutralGate()
    private var paused=true
    private var clutch=false
    private var preview=false
    private var simulated=Axes()
    private var lastMotionAt=0.0
    private var selectedDevice: Long?=null
    private var registeredHotkey: Hotkey?=null
    private var hotkeyId=0x504c
    private var hotkeyError: String?=null
    private var processingError: String?=null
    private var appliedProfile: Profile?=null
    private val configurationPending=AtomicBoolean(false)
    private val buttons=ButtonTracker({action,down -> if(!preview && allowDesktopOutput) output.button(action,down)}, {hold ->
        if(hold==null) togglePauseOnThread() else {
            clutch=hold;interrupt();publish {it.copy(paused=paused||clutch)}
        }
    })
    fun start()=thread.start()
    private fun time()=clock()
    private fun submit(task: ()->Unit) {if(running.get()) queue.add(task)}
    fun togglePause()=submit {togglePauseOnThread()}
    private fun togglePauseOnThread() {
        if(paused && engine==null) replaceEngine(state().profile)
        paused=!paused;interrupt()
        val previousError=processingError
        publish {it.copy(paused=paused||clutch,error=if(!paused && it.error==previousError)null else it.error)}
        if(!paused) processingError=null
    }
    fun setPreview(value: Boolean)=submit { if(value && engine==null) replaceEngine(state().profile)
        interrupt();preview=value;paused=true;clutch=false;buttons.disconnect();simulated=Axes()
        publish {it.copy(preview=value,paused=true,axes=Axes(),outputs=emptyMap())}
        if(value) engine?.feed(time(),simulated)
    }
    fun simulate(axis: Axis,value: Double)=submit {if(preview) {simulated=simulated.with(axis,value);engine?.feed(time(),simulated);publish{it.copy(axes=simulated)}}}
    fun centerPreview()=submit {if(preview){simulated=Axes();engine?.feed(time(),simulated);publish{it.copy(axes=simulated)}}}
    fun reconfigure() {
        if(configurationPending.compareAndSet(false,true)) submit {
            configurationPending.set(false)
            val next=state().profile
            val motionChanged=engine==null || next.id!=appliedProfile?.id || next.mappings!=appliedProfile?.mappings
            val buttonsChanged=next.button1!=appliedProfile?.button1 || next.button2!=appliedProfile?.button2
            if(motionChanged || buttonsChanged) interrupt()
            if(motionChanged) replaceEngine(next)
            appliedProfile=next;registerHotkey()
        }
    }
    private fun replaceEngine(profile: Profile) {
        val previous=engine;engine=null;previous?.close()
        val replacement=PuckEngine(profile)
        try {
            replacement.frame(time())
            if(preview) {simulated=Axes();replacement.feed(time(),simulated);publish{it.copy(axes=simulated)}}
            engine=replacement;appliedProfile=profile
        } catch(error: Throwable) {
            try {replacement.close()} catch(_: Throwable) {}
            throw error
        }
    }
    private fun interrupt() {
        gate.interrupt();fractions.clear();buttons.release();engine?.interrupt(time());publish {it.copy(armed=false,outputs=emptyMap())}
    }
    private fun registerHotkey() {
        val next=state().settings.hotkey
        if(next==registeredHotkey) {
            publish {it.copy(hotkeyReady=true,error=if(it.error==hotkeyError)null else it.error)}
            hotkeyError=null;return
        }
        val api=raw ?: return
        // Use a new ID first: a conflict never removes the currently working shortcut.
        val candidateId=if(hotkeyId==0x504c)0x504d else 0x504c
        val success=api.RegisterHotKey(window,candidateId,next.modifiers,next.key)
        if(success) {
            api.UnregisterHotKey(window,hotkeyId)
            hotkeyId=candidateId;registeredHotkey=next
            publish{it.copy(hotkeyReady=true,error=if(it.error==hotkeyError)null else it.error)}
            hotkeyError=null
        } else {
            val old=registeredHotkey
            hotkeyError="${next.label} is already in use. ${if(old!=null) "${old.label} still pauses Puck Mouse." else "Choose another pause shortcut."}"
            publish {it.copy(hotkeyReady=false,error=hotkeyError)}
        }
    }
    private fun refreshDevices() {
        val api=raw!!; val count=IntByReference()
        check(api.GetRawInputDeviceList(null,count,16)!=-1) {"Windows could not enumerate input devices"}
        require(count.value in 0..4096)
        var found: Long?=null;var unknown=false
        if(count.value>0) Memory(count.value.toLong()*16).use {list ->
            val actual=api.GetRawInputDeviceList(list,count,16);check(actual!=-1)
            repeat(actual) {i -> val offset=i*16L
                if(list.getInt(offset+8)==2) {
                    val handle=list.getPointer(offset)
                    Memory(32).use {info -> info.clear();info.setInt(0,32)
                        if(api.GetRawInputDeviceInfoW(handle,0x2000000b,info,IntByReference(32))!=-1) {
                            val vendor=info.getInt(8);val product=info.getInt(12)
                            if(vendor==0x256f && product==0xc63a && info.getShort(20).toInt()==1 && info.getShort(22).toInt()==8)
                                if(found==null || Pointer.nativeValue(handle)==selectedDevice) found=Pointer.nativeValue(handle)
                            if((vendor==0x256f || vendor==0x046d) && info.getShort(20).toInt()==1 && info.getShort(22).toInt()==8 && product!=0xc63a) unknown=true
                        }
                    }
                }
            }
        }
        if(found!=selectedDevice) {
            interrupt();buttons.disconnect();clutch=false;selectedDevice=found
            publish {it.copy(connected=found!=null,armed=false,axes=Axes(),deviceName=if(found!=null) "SpaceMouse Wireless · Bluetooth" else if(unknown) "Device needs a supported report profile" else "No SpaceMouse connected",paused=paused)}
        } else if(found==null) publish {it.copy(deviceName=if(unknown) "Device needs a supported report profile" else "No SpaceMouse connected")}
    }
    private fun input(pointer: Pointer) {
        val api=raw!!;val size=IntByReference()
        if(api.GetRawInputData(pointer,0x10000003,null,size,24)==-1 || size.value !in 32..65536) return
        Memory(size.value.toLong()).use {memory ->
            val copied=api.GetRawInputData(pointer,0x10000003,memory,size,24)
            if(copied<32 || memory.getInt(0)!=2 || Pointer.nativeValue(memory.getPointer(8))!=selectedDevice) return
            val length=memory.getInt(24);val count=memory.getInt(28)
            if(length!=13 || count !in 1..256 || 32L+length.toLong()*count>copied) return
            repeat(count) {i -> report(WirelessReportDecoder.decode(memory.getByteArray(32L+i*length,length))) }
        }
    }
    private fun report(report: Report?) {
        if(preview) return
        when(report) {
            is Report.Motion -> {
                val now=time();lastMotionAt=now
                publish {it.copy(axes=report.axes)}
                if(paused || clutch) return
                val wasArmed=gate.armed
                if(gate.observeRealMotion(report.axes,buttons.allReleased())) {
                    if(!wasArmed) {engine?.feed(now,report.axes);engine?.frame(now);fractions.clear();publish{it.copy(armed=true)}}
                    engine?.feed(now,report.axes)
                }
            }
            is Report.Buttons -> { val p=state().profile;buttons.report(report.mask,listOf(p.button1,p.button2),!paused && !clutch && gate.armed) }
            null -> {}
        }
    }
    private fun fail(error: Throwable, recover: Boolean=true) {
        paused=true;gate.interrupt();fractions.clear()
        try {buttons.release()} catch(_: Throwable) {}
        try {engine?.interrupt(time())} catch(_: Throwable) {}
        val recoveryError=if(recover) try {replaceEngine(state().profile);null} catch(e:Throwable){e} else null
        processingError=when {
            !recover -> "Input processing stopped. Restart Puck Mouse to retry. ${error.message ?: ""}"
            recoveryError!=null -> "Motion processing is unavailable. Resume to retry. ${recoveryError.message ?: error.message ?: ""}"
            else -> "Input processing paused. Resume when ready, then release the cap to center. ${error.message ?: ""}"
        }.trim()
        publish{it.copy(paused=true,armed=false,outputs=emptyMap(),error=processingError)}
    }
    private fun run() {
        var className="";var instance: WinDef.HINSTANCE?=null;var callback: WinUser.WindowProc?=null
        var rawRegistered=false
        try {
            require(System.getProperty("os.name").startsWith("Windows") && Native.POINTER_SIZE==8) {"Puck Mouse requires 64-bit Windows"}
            replaceEngine(state().profile)
            raw=Native.load("user32",RawUser32::class.java)
            instance=WinDef.HINSTANCE().apply {pointer=Kernel32.INSTANCE.GetModuleHandle(null).pointer}
            className="PuckMouseInput-${ProcessHandle.current().pid()}-${System.identityHashCode(this)}"
            callback=object: WinUser.WindowProc {
                override fun callback(hwnd: WinDef.HWND,msg: Int,w: WinDef.WPARAM,l: WinDef.LPARAM): WinDef.LRESULT {
                    try {
                        when(msg) {0xff -> input(Pointer(l.toLong()));0xfe -> refreshDevices();0x312 -> togglePauseOnThread()}
                    } catch(e:Throwable) {fail(e)}
                    return User32.INSTANCE.DefWindowProc(hwnd,msg,w,l)
                }
            }
            val wc=WinUser.WNDCLASSEX().apply {hInstance=instance;lpfnWndProc=callback;lpszClassName=className}
            check(User32.INSTANCE.RegisterClassEx(wc).toInt()!=0) {"Could not register the input window"}
            window=User32.INSTANCE.CreateWindowEx(0,className,"Puck Mouse input",0,0,0,0,0,WinDef.HWND(Pointer(-3)),null,instance,null)
            check(window!=null) {"Could not create the input window"}
            val rid=RawDevice();@Suppress("UNCHECKED_CAST") val rids=rid.toArray(1) as Array<RawDevice>
            rids[0].flags=0x100 or 0x2000;rids[0].window=window
            check(raw!!.RegisterRawInputDevices(rids,1,rid.size())) {"Could not register background SpaceMouse input"};rawRegistered=true
            registerHotkey();refreshDevices()
            val msg=WinUser.MSG();val timing=InputLoopTiming(::time)
            while(running.get()) {
                var task=queue.poll();while(task!=null) {try{task()}catch(e:Throwable){fail(e)};task=queue.poll()}
                var budget=0
                while(budget++<128 && User32.INSTANCE.PeekMessage(msg,null,0,0,1)) {User32.INSTANCE.TranslateMessage(msg);User32.INSTANCE.DispatchMessage(msg)}
                try {
                    timing.tick(::refreshDevices, { now ->
                        // Silence is an interruption, never a manufactured neutral input.
                        if(!preview && gate.armed && now-lastMotionAt>120 && !state().axes.neutral()) interrupt()
                    }, { frameTime,feedbackDue ->
                        val delta=engine?.frame(frameTime) ?: emptyMap()
                        if(allowDesktopOutput && !preview && !paused && !clutch && gate.armed && selectedDevice!=null) output.motion(fractions.take(delta)) else fractions.clear()
                        if(feedbackDue) {val rates=if(preview || gate.armed)engine?.rates() ?: emptyMap() else emptyMap();publish{it.copy(outputs=rates)}}
                    })
                } catch(e:Throwable) {fail(e)}
                LockSupport.parkNanos(1_000_000)
            }
        } catch(e:Throwable) {fail(e,recover=false)} finally {
            try{buttons.disconnect()}catch(_:Throwable){}
            try{engine?.close()}catch(_:Throwable){}
            if(rawRegistered) {val rid=RawDevice();@Suppress("UNCHECKED_CAST") val rids=rid.toArray(1) as Array<RawDevice>;rids[0].flags=1;rids[0].window=null;raw?.RegisterRawInputDevices(rids,1,rid.size())}
            raw?.UnregisterHotKey(window,0x504c);raw?.UnregisterHotKey(window,0x504d)
            window?.let {User32.INSTANCE.DestroyWindow(it)}
            if(instance!=null && className.isNotEmpty()) User32.INSTANCE.UnregisterClass(className,instance)
            java.lang.ref.Reference.reachabilityFence(callback)
            running.set(false)
            publish{it.copy(armed=false,connected=false,hotkeyReady=false)}
        }
    }
    override fun close() { running.set(false);if(Thread.currentThread()!==thread && thread.isAlive)thread.join(3000) }
}

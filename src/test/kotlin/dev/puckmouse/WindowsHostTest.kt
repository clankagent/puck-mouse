package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import com.sun.jna.Native
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

class WindowsHostTest {
    private fun awaitCondition(condition: ()->Boolean) {
        val end=System.nanoTime()+8_000_000_000
        while(!condition() && System.nanoTime()<end) Thread.sleep(10)
        assertTrue(condition())
    }
    @Test fun realMessageWindowHotkeyAndPreviewLifecycleNeverRequireDesktopInjection() {
        val s=MutableStateFlow(AppState(settings=Settings(hotkey=Hotkey(0x87,true,true,true))))
        val host=WindowsHost({s.value},{s.update(it)},allowDesktopOutput=false)
        host.start()
        try {
            awaitCondition{s.value.hotkeyReady || s.value.error!=null}
            assertNull(s.value.error);assertTrue(s.value.paused)
            host.setPreview(true);awaitCondition{s.value.preview}
            host.simulate(Axis.TWIST,.5);awaitCondition{(s.value.outputs[Output.SCROLL_Y]?:0.0)>0.0}
            assertFalse(s.value.armed);assertTrue(s.value.paused)
            host.centerPreview();awaitCondition{s.value.axes.neutral() && (s.value.outputs[Output.SCROLL_Y]?:0.0)==0.0}
            host.setPreview(false);awaitCondition{!s.value.preview};assertFalse(s.value.armed)
        } finally {host.close()}
        assertFalse(s.value.hotkeyReady);assertFalse(s.value.connected)
    }
    @Test fun conflictingShortcutKeepsOldRegistrationAndCanRecover() {
        val api=Native.load("user32",RawUser32::class.java)
        val original=Hotkey(0x87,true,true,true);val conflict=Hotkey(0x86,true,true,true)
        assertTrue(api.RegisterHotKey(null,0x6810,conflict.modifiers,conflict.key))
        val s=MutableStateFlow(AppState(settings=Settings(hotkey=original)))
        val host=WindowsHost({s.value},{s.update(it)},allowDesktopOutput=false);host.start()
        try {
            awaitCondition{s.value.hotkeyReady || s.value.error!=null};assertNull(s.value.error)
            s.update{it.copy(settings=it.settings.copy(hotkey=conflict))};host.reconfigure()
            awaitCondition{s.value.error!=null};assertFalse(s.value.hotkeyReady)
            assertFalse(api.RegisterHotKey(null,0x6811,original.modifiers,original.key))
            api.UnregisterHotKey(null,0x6810);host.reconfigure()
            awaitCondition{s.value.hotkeyReady}
            assertTrue(api.RegisterHotKey(null,0x6811,original.modifiers,original.key))
        } finally {host.close();api.UnregisterHotKey(null,0x6810);api.UnregisterHotKey(null,0x6811)}
    }
    @Test fun monotonicTimestampFailureResetsTimersAndProcessingWithoutLosingTheInputThread() {
        val offset=AtomicLong(0)
        val s=MutableStateFlow(AppState(settings=Settings(hotkey=Hotkey(0x87,true,true,true))))
        val host=WindowsHost({s.value},{s.update(it)},allowDesktopOutput=false,clock={
            System.nanoTime()/1_000_000.0+offset.get()
        })
        host.start()
        try {
            awaitCondition{s.value.hotkeyReady || s.value.error!=null};assertNull(s.value.error)
            host.setPreview(true);awaitCondition{s.value.preview}
            host.simulate(Axis.TWIST,.5);awaitCondition{(s.value.outputs[Output.SCROLL_Y]?:0.0)>0}
            // Feed a future sample, then return to the normal clock. The next feed
            // must elicit the exact monotonic-timestamp error from the released DLL.
            offset.set(10_000)
            host.simulate(Axis.TWIST,.6);awaitCondition{s.value.axes.rz==.6}
            offset.set(0)
            host.centerPreview()
            awaitCondition{s.value.error?.contains("timestamps must be monotonic")==true}
            assertTrue(s.value.paused);assertFalse(s.value.armed);assertTrue(s.value.hotkeyReady)
            assertTrue(s.value.axes.neutral());assertTrue(s.value.outputs.values.all{it==0.0})
            host.togglePause();awaitCondition{!s.value.paused}
            assertNull(s.value.error);assertFalse(s.value.armed)
            host.setPreview(false);awaitCondition{!s.value.preview}
            host.setPreview(true);awaitCondition{s.value.preview}
            host.simulate(Axis.TWIST,.5);awaitCondition{(s.value.outputs[Output.SCROLL_Y]?:0.0)>0}
            assertNull(s.value.error);assertTrue(s.value.hotkeyReady)
        } finally {host.close()}
    }
    @Test fun scheduledTimingFailureKeepsTheMessageLoopAvailable() {
        val fault=AtomicBoolean(false)
        val s=MutableStateFlow(AppState(settings=Settings(hotkey=Hotkey(0x87,true,true,true))))
        val host=WindowsHost({s.value},{s.update(it)},allowDesktopOutput=false,clock={
            if(fault.compareAndSet(true,false)) error("Simulated clock read failure")
            System.nanoTime()/1_000_000.0
        })
        host.start()
        try {
            awaitCondition{s.value.hotkeyReady || s.value.error!=null};assertNull(s.value.error)
            host.setPreview(true);awaitCondition{s.value.preview}
            host.simulate(Axis.TWIST,.5);awaitCondition{(s.value.outputs[Output.SCROLL_Y]?:0.0)>0}
            host.togglePause();awaitCondition{!s.value.paused}
            // With no pending commands, this fails in the scheduled loop, whose
            // old exception boundary terminated the Windows input thread.
            fault.set(true);awaitCondition{s.value.error?.contains("Simulated clock read failure")==true}
            assertTrue(s.value.paused);assertFalse(s.value.armed);assertTrue(s.value.hotkeyReady)
            host.simulate(Axis.TWIST,.5);awaitCondition{(s.value.outputs[Output.SCROLL_Y]?:0.0)>0}
        } finally {host.close()}
    }
}

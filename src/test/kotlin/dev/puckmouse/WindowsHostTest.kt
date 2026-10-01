package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import com.sun.jna.Native

class WindowsHostTest {
    private fun awaitCondition(condition: ()->Boolean) {
        val end=System.nanoTime()+8_000_000_000
        while(!condition() && System.nanoTime()<end) Thread.sleep(10)
        assertTrue(condition())
    }
    @Test fun realMessageWindowHotkeyAndPreviewLifecycleNeverRequireDesktopInjection() {
        val s=MutableStateFlow(AppState(settings=Settings(hotkey=Hotkey(0x87,true,true,true))))
        val host=WindowsHost({s.value},{s.update(it)})
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
        val host=WindowsHost({s.value},{s.update(it)});host.start()
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
}

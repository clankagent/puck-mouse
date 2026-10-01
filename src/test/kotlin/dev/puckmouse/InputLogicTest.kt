package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InputLogicTest {
    @Test fun aPauseInTheSameReportPreventsALaterButtonFromClicking() {
        val events=mutableListOf<Pair<ButtonAction,Boolean>>()
        lateinit var tracker:ButtonTracker
        tracker=ButtonTracker({a,d->events.add(a to d)},{tracker.release()})
        tracker.report(3,listOf(ButtonAction.PAUSE_TOGGLE,ButtonAction.RIGHT_CLICK),true)
        assertTrue(events.isEmpty())
        tracker.report(0,listOf(ButtonAction.PAUSE_TOGGLE,ButtonAction.RIGHT_CLICK),false)
        assertTrue(events.isEmpty());assertTrue(tracker.allReleased())
    }
    @Test fun aFailedButtonReleaseIsRetriedDuringInterruption() {
        var failRelease=true;val events=mutableListOf<Boolean>()
        val tracker=ButtonTracker({_,down->if(!down && failRelease)throw IllegalStateException("blocked") else events.add(down)},{})
        tracker.report(1,listOf(ButtonAction.LEFT_CLICK,ButtonAction.NONE),true)
        assertFails{tracker.report(0,listOf(ButtonAction.LEFT_CLICK,ButtonAction.NONE),true)}
        failRelease=false;tracker.release()
        assertEquals(listOf(true,false),events)
    }
    @Test fun decodeTheMeasuredWirelessReport() {
        val report=ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN).put(1)
        listOf(350,-350,175,0,-175,700).forEach{report.putShort(it.toShort())}
        assertEquals(Axes(1.0,-1.0,.5,0.0,-.5,1.0),(WirelessReportDecoder.decode(report.array()) as Report.Motion).axes)
        assertNull(WirelessReportDecoder.decode(ByteArray(12)))
        assertNull(WirelessReportDecoder.decode(ByteArray(14)))
        assertNull(WirelessReportDecoder.decode(ByteArray(13){4}))
        assertEquals(Report.Buttons(3),WirelessReportDecoder.decode(ByteArray(13).also{it[0]=3;it[1]=3}))
    }
    @Test fun pauseAndDisconnectRequireFreshNeutral() {
        val gate=NeutralGate();assertFalse(gate.observeRealMotion(Axes(rz=.2),true))
        assertFalse(gate.observeRealMotion(Axes(),false));assertTrue(gate.observeRealMotion(Axes(),true))
        gate.interrupt();assertFalse(gate.armed);assertFalse(gate.observeRealMotion(Axes(x=.3),true))
        assertTrue(gate.observeRealMotion(Axes(),true))
    }
    @Test fun smallMotionAccumulatesWithoutLosingPrecisionAndPauseDropsCarry() {
        val fractions=OutputAccumulator()
        repeat(3){assertEquals(0,fractions.take(mapOf(Output.POINTER_X to .25))[Output.POINTER_X])}
        assertEquals(1,fractions.take(mapOf(Output.POINTER_X to .25))[Output.POINTER_X])
        fractions.take(mapOf(Output.SCROLL_Y to .004));fractions.clear()
        assertEquals(0,fractions.take(mapOf(Output.SCROLL_Y to .004))[Output.SCROLL_Y])
        assertEquals(-1,fractions.take(mapOf(Output.POINTER_X to -1.1))[Output.POINTER_X])
    }
    @Test fun interruptionReleasesTheCapturedMouseActionAndDoesNotClickWhilePaused() {
        val events=mutableListOf<Pair<ButtonAction,Boolean>>()
        val tracker=ButtonTracker({a,b->events.add(a to b)},{})
        tracker.report(1,listOf(ButtonAction.LEFT_CLICK,ButtonAction.NONE),true)
        tracker.release()
        tracker.report(0,listOf(ButtonAction.RIGHT_CLICK,ButtonAction.NONE),true)
        assertEquals(listOf(ButtonAction.LEFT_CLICK to true,ButtonAction.LEFT_CLICK to false),events)
        tracker.report(1,listOf(ButtonAction.LEFT_CLICK,ButtonAction.NONE),false)
        tracker.report(0,listOf(ButtonAction.LEFT_CLICK,ButtonAction.NONE),false)
        assertEquals(2,events.size)
    }
    @Test fun clutchReleaseIsRetainedAcrossPauseInterruption() {
        val pauses=mutableListOf<Boolean?>();val tracker=ButtonTracker({_,_->},{pauses.add(it)})
        tracker.report(2,listOf(ButtonAction.NONE,ButtonAction.PAUSE_HOLD),true)
        tracker.release();tracker.report(0,listOf(ButtonAction.NONE,ButtonAction.PAUSE_TOGGLE),false)
        assertEquals(listOf<Boolean?>(true,false),pauses)
    }
    @Test fun releaseAttemptsAllButtonsWhenOneOutputFails() {
        val attempts=mutableListOf<ButtonAction>();val tracker=ButtonTracker({a,down->if(!down){attempts.add(a);if(a==ButtonAction.LEFT_CLICK)error("blocked")}}, {})
        tracker.report(3,listOf(ButtonAction.LEFT_CLICK,ButtonAction.RIGHT_CLICK),true)
        assertFails{tracker.release()};assertEquals(listOf(ButtonAction.LEFT_CLICK,ButtonAction.RIGHT_CLICK),attempts)
    }
}

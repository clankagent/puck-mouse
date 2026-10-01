package dev.puckmouse

import org.junit.Test
import java.nio.file.Path
import kotlin.test.*

class InputLoopTimingTest {
    @Test fun deviceRefreshAndSilenceInterruptionNeverReuseAnOlderFrameTimestamp() {
        var now=0.0
        val timing=InputLoopTiming {now}
        val profile=Profile(mappings=defaultMappings().map{it.copy(responseMs=0.0)})
        PuckEngine(profile,Path.of(System.getProperty("puck.dll"))).use {engine ->
            engine.frame(now);engine.feed(now,Axes(x=.7))
            var refreshes=0
            var interruptions=0
            val frameTimes=mutableListOf<Double>()
            fun tick()=timing.tick({
                refreshes++;now+=1;engine.interrupt(now)
            }, {
                interruptions++;now+=1;engine.interrupt(now)
            }, {frameTime,_ ->
                frameTimes+=frameTime
                assertTrue(engine.frame(frameTime).values.all{it==0.0})
            })
            now=2000.0;tick()
            assertEquals(listOf(2002.0),frameTimes);assertEquals(1,refreshes)
            now=2010.0;tick()
            assertEquals(listOf(2002.0,2011.0),frameTimes);assertEquals(1,refreshes);assertEquals(2,interruptions)
            // Recovery still requires actual neutral input; held motion cannot rearm it.
            engine.feed(2012.0,Axes(x=.7));assertTrue(engine.frame(2020.0).values.all{it==0.0})
            engine.feed(2021.0,Axes());engine.feed(2022.0,Axes(x=.7))
            assertTrue(engine.frame(2030.0)[Output.POINTER_X]!!>0)
        }
    }
    @Test fun failedScheduledWorkIsRetriedAtItsNormalCadence() {
        var now=0.0;val timing=InputLoopTiming{now}
        var refreshes=0;var frames=0
        now=2000.0
        assertFails {timing.tick({refreshes++;error("device read failed")},{},{_,_->frames++})}
        now=2001.0;timing.tick({refreshes++},{},{_,_->frames++})
        assertEquals(1,refreshes);assertEquals(1,frames)
        now=2009.0
        assertFails {timing.tick({refreshes++},{},{_,_->frames++;error("frame failed")})}
        now=2010.0;timing.tick({refreshes++},{},{_,_->frames++})
        assertEquals(2,frames)
        now=2017.0;timing.tick({refreshes++},{},{_,_->frames++})
        assertEquals(3,frames)
    }
}

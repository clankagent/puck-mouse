package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import java.nio.file.Path

class LiveMotionTest {
    private val dll=Path.of(System.getProperty("puck.dll"))
    private val profile=Profile(mappings=defaultMappings().map {
        it.copy(speed=1000.0,deadzone=0.0,curve=1.0,responseMs=0.0,fullSpeedAt=1.0)
    })

    @Test fun reportGapStopsOutputAndFreshHeldInputContinuesWithoutCentering() {
        PuckEngine(profile,dll).use {engine ->
            val motion=LiveMotion();engine.frame(0.0)
            motion.feed(engine,0.0,Axes(),true)
            motion.feed(engine,1.0,Axes(x=.5),true)
            assertEquals(4,motion.frame(engine,9.0)[Output.POINTER_X])
            assertTrue(motion.fresh(121.0))
            assertTrue(motion.frame(engine,122.0).isEmpty())
            assertTrue(motion.armed);assertFalse(motion.fresh(122.0))
            assertTrue(motion.frame(engine,200.0).isEmpty())
            // A held drag button must not prevent resuming an already armed session.
            motion.feed(engine,210.0,Axes(x=.5),false)
            assertTrue(motion.armed)
            assertEquals(4,motion.frame(engine,218.0)[Output.POINTER_X])
        }
    }

    @Test fun firstReportAfterAWholeLoopStallDiscardsStaleMovementAndFractions() {
        PuckEngine(profile,dll).use {engine ->
            val motion=LiveMotion();engine.frame(0.0)
            motion.feed(engine,0.0,Axes(),true)
            motion.feed(engine,1.0,Axes(x=.05),true)
            assertEquals(0,motion.frame(engine,9.0)[Output.POINTER_X])
            // No frames run during this gap. Recovery must discard the engine's
            // accumulated old interval before fresh motion becomes eligible.
            motion.feed(engine,1000.0,Axes(x=.05),true)
            assertEquals(0,motion.frame(engine,1008.0)[Output.POINTER_X])
            motion.feed(engine,1008.0,Axes(x=-.5),true)
            assertEquals(-4,motion.frame(engine,1016.0)[Output.POINTER_X])
        }
    }

    @Test fun silenceCannotArmAndHardInterruptionsStillRequireRealNeutral() {
        PuckEngine(profile,dll).use {engine ->
            val motion=LiveMotion();engine.frame(0.0)
            motion.feed(engine,1.0,Axes(x=.5),true)
            assertTrue(motion.frame(engine,500.0).isEmpty());assertFalse(motion.armed)
            motion.feed(engine,501.0,Axes(),true);assertTrue(motion.armed)
            motion.feed(engine,502.0,Axes(x=.5),true)
            assertEquals(4,motion.frame(engine,510.0)[Output.POINTER_X])
            motion.interrupt();engine.interrupt(511.0)
            motion.feed(engine,800.0,Axes(x=.5),true)
            assertTrue(motion.frame(engine,808.0).isEmpty());assertFalse(motion.armed)
            motion.feed(engine,809.0,Axes(),false);assertFalse(motion.armed)
            motion.feed(engine,810.0,Axes(),true);assertTrue(motion.armed)
            motion.feed(engine,811.0,Axes(x=.5),true)
            assertEquals(4,motion.frame(engine,819.0)[Output.POINTER_X])
        }
    }
}

package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class PuckEngineTest {
    private val dll=Path.of(System.getProperty("puck.dll"))
    private fun profile(axis: Axis=Axis.TWIST)=Profile(mappings=defaultMappings().map{it.copy(deadzone=0.0,curve=1.0,responseMs=0.0,axis=if(it.output==Output.SCROLL_Y)axis else it.axis)})
    @Test fun twistTiltMappingAndPauseNeutralRearmUseTheReleasedCore() {
        PuckEngine(profile(),dll).use{e ->
            e.frame(0.0);e.feed(0.0,Axes(rz=.5));assertEquals(.096,e.frame(16.0)[Output.SCROLL_Y]!!,1e-9)
            e.interrupt(17.0);e.feed(18.0,Axes(rz=.5));assertEquals(0.0,e.frame(30.0)[Output.SCROLL_Y])
            e.feed(31.0,Axes());e.feed(32.0,Axes(rz=.5));assertEquals(.096,e.frame(48.0)[Output.SCROLL_Y]!!,1e-9)
            e.feed(49.0,Axes());assertEquals(0.0,e.rates()[Output.SCROLL_Y])
        }
        PuckEngine(profile(Axis.TILT_X),dll).use{e ->
            e.frame(0.0);e.feed(0.0,Axes(rx=-.5));assertEquals(-.096,e.frame(16.0)[Output.SCROLL_Y]!!,1e-9)
        }
    }
    @Test fun wrongThreadCallsFailBeforeTouchingNativeMemory() {
        PuckEngine(profile(),dll).use{e ->
            val failure=AtomicReference<Throwable?>();val thread=Thread{try{e.feed(0.0,Axes())}catch(t:Throwable){failure.set(t)}}
            thread.start();thread.join();assertIs<IllegalStateException>(failure.get())
        }
    }
}

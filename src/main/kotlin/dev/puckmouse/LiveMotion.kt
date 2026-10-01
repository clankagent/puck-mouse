package dev.puckmouse

/** Report silence suspends output, but is not evidence of a pause or neutral cap. */
internal class LiveMotion {
    private val gate=NeutralGate()
    private val fractions=OutputAccumulator()
    private var lastReportAt: Double?=null
    val armed get()=gate.armed
    fun fresh(time: Double)=lastReportAt?.let {time-it<=120} ?: false

    fun interrupt() {
        gate.interrupt();fractions.clear();lastReportAt=null
    }

    fun feed(engine: PuckEngine?,time: Double,axes: Axes,noButtonsHeld: Boolean) {
        val wasArmed=armed
        val wasFresh=fresh(time)
        lastReportAt=time
        if(!gate.observeRealMotion(axes,noButtonsHeld)) return
        if(!wasArmed) {
            engine?.feed(time,axes);engine?.frame(time);fractions.clear()
        } else if(!wasFresh) {
            // Discard the stale interval before accepting new input. Never replay
            // movement accumulated while reports were missing, even after a stall.
            engine?.frame(time);fractions.clear()
        }
        engine?.feed(time,axes)
    }

    fun frame(engine: PuckEngine?,time: Double): Map<Output,Int> {
        val delta=engine?.frame(time) ?: emptyMap()
        if(armed && fresh(time)) return fractions.take(delta)
        fractions.clear();return emptyMap()
    }
}

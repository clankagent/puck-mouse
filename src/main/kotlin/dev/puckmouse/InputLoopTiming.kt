package dev.puckmouse

/** Schedule host work without reusing a timestamp across operations that can interrupt Puck. */
internal class InputLoopTiming(private val clock: () -> Double) {
    private var frameAt=clock()
    private var devicesAt=frameAt
    private var feedbackAt=frameAt

    fun tick(refreshDevices: () -> Unit, checkSilence: (Double) -> Unit, frame: (Double,Boolean) -> Unit) {
        val observed=clock()
        if(observed-devicesAt>=2000) {
            devicesAt=observed
            refreshDevices()
        }
        checkSilence(observed)
        // Device changes and the silence watchdog can advance the engine clock.
        // Sample again after them, immediately before submitting the frame.
        val frameTime=clock()
        if(frameTime-frameAt>=8) {
            frameAt=frameTime
            val feedbackDue=frameTime-feedbackAt>=32
            if(feedbackDue) feedbackAt=frameTime
            frame(frameTime,feedbackDue)
        }
    }
}

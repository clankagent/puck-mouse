package dev.puckmouse

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign

/** Shape speed from push strength, never from an already-integrated mouse delta. */
internal fun shapedPush(push: Double, mapping: Mapping): Double =
    ((push - mapping.deadzone) / (mapping.fullSpeedAt - mapping.deadzone))
        .coerceIn(0.0, 1.0).pow(mapping.curve)

internal fun pointerMagnitude(profile: Profile, axes: Axes): Double {
    val x = profile.mappings.first { it.output == Output.POINTER_X }
    val y = profile.mappings.first { it.output == Output.POINTER_Y }
    return hypot(if (x.enabled) axes[x.axis] else 0.0, if (y.enabled) axes[y.axis] else 0.0)
}

/** Each output gets its own native input slot, so shared source axes cannot
 * accidentally apply the pointer curve to scrolling or another mapping. */
internal fun shapedOutputs(profile: Profile, axes: Axes): DoubleArray {
    val result = DoubleArray(4)
    val x = profile.mappings.first { it.output == Output.POINTER_X }
    val y = profile.mappings.first { it.output == Output.POINTER_Y }
    val radial = profile.radialPointer && x.axis != y.axis
    val magnitude = pointerMagnitude(profile, axes)
    for (mapping in profile.mappings) {
        if (!mapping.enabled) continue
        val input = axes[mapping.axis]
        val value = if (radial && mapping.output.name.startsWith("POINTER")) {
            if (magnitude == 0.0) 0.0 else input / magnitude * shapedPush(magnitude, mapping)
        } else input.sign * shapedPush(abs(input), mapping)
        result[mapping.output.ordinal] = value * if (mapping.inverted) -1.0 else 1.0
    }
    return result
}

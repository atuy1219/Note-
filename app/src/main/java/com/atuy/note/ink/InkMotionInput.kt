package com.atuy.note.ink

import kotlin.math.PI

private const val TWO_PI = 2.0 * PI

/**
 * Android MotionEvent stylus orientation is in [-PI, PI] relative to the
 * vertical/upwards axis; a positive angle points right. AndroidX Ink instead
 * measures clockwise (in screen coordinates) from positive X and requires
 * orientationRadians in [0, 2*PI). Convert axes and wrap the signed result.
 *
 * Use Double for the intermediate math. A Float-rounded value equal to
 * 2*PI can be rejected by Ink, so wrap it back to zero.
 */
internal fun motionOrientationToInkRadians(androidRadians: Float): Float {
    if (!androidRadians.isFinite()) return 0f
    val rotated = androidRadians.toDouble() - PI / 2.0
    val wrapped = ((rotated % TWO_PI) + TWO_PI) % TWO_PI
    val rounded = wrapped.toFloat()
    return if (rounded.toDouble() >= TWO_PI) 0f else rounded
}

/** Ink validates every tilt in [0, PI/2]; always return a present value. */
internal fun motionTiltToInkRadians(androidTilt: Float): Float =
    if (androidTilt.isFinite()) androidTilt.coerceIn(0f, (PI / 2.0).toFloat()) else 0f

/** Keep an always-present pressure field across all inputs in a batch. */
internal fun motionPressureToInk(pressure: Float): Float =
    if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f

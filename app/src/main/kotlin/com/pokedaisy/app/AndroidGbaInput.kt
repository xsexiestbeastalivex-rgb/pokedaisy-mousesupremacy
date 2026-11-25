package com.pokedaisy.app

import android.view.InputDevice
import android.view.MotionEvent

/** Android events end here; the mappings and input state live in the shared GbaInput. */
fun GbaInput.onMotion(event: MotionEvent): Boolean {
    setAxes(GbaInput.motionBits(event) ?: return false)
    return true
}

fun GbaInput.Companion.motionBits(event: MotionEvent): Int? {
    if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return null
    return axisBits(
        event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
        event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
    )
}

/** The Activity's single source for capture, analog key transitions, and manual takeover observation. */
internal data class ControllerTriggers(val left: Float, val right: Float) {
    val leftDown get() = left > 0.5f
    val rightDown get() = right > 0.5f

    fun hasManualInput(directionBits: Int?): Boolean = leftDown || rightDown || (directionBits ?: 0) != 0

    companion object {
        fun read(axis: (Int) -> Float) = ControllerTriggers(
            maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE)),
            maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS), axis(MotionEvent.AXIS_THROTTLE)),
        )
    }
}

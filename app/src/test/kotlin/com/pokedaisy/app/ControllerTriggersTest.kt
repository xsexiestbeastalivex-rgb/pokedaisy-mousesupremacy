package com.pokedaisy.app

import android.view.MotionEvent.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerTriggersTest {
    @Test fun everyUpstreamTriggerAliasSharesTheManualInputDecision() {
        val aliases = listOf(
            AXIS_LTRIGGER to true, AXIS_BRAKE to true, AXIS_RTRIGGER to false,
            AXIS_GAS to false, AXIS_THROTTLE to false,
        )
        for ((axis, left) in aliases) {
            val triggered = ControllerTriggers.read { if (it == axis) 0.8f else 0f }
            assertEquals(if (left) 0.8f else 0f, triggered.left, 0f)
            assertEquals(if (left) 0f else 0.8f, triggered.right, 0f)
            assertEquals(left, triggered.leftDown)
            assertEquals(!left, triggered.rightDown)
            assertTrue("Axis $axis must interrupt a pointer", triggered.hasManualInput(0))
            assertFalse(ControllerTriggers.read { if (it == axis) 0.5f else 0.04f }.hasManualInput(0))
        }
        val both = ControllerTriggers.read {
            when (it) {
                AXIS_LTRIGGER -> 0.6f
                AXIS_BRAKE -> 0.7f
                AXIS_RTRIGGER -> 0.75f
                AXIS_GAS -> 0.65f
                AXIS_THROTTLE -> 0.85f
                else -> 0f
            }
        }
        assertEquals(0.7f, both.left, 0f)
        assertEquals(0.85f, both.right, 0f)
    }

    @Test fun upstreamStickAndHatBitsShareTheInterruptionDecision() {
        val neutral = ControllerTriggers.read { 0f }
        for (value in listOf(-0.8f, 0.8f)) {
            assertTrue(neutral.hasManualInput(GbaInput.axisBits(value, 0f, 0f, 0f)))
            assertTrue(neutral.hasManualInput(GbaInput.axisBits(0f, value, 0f, 0f)))
            assertTrue(neutral.hasManualInput(GbaInput.axisBits(0f, 0f, value, 0f)))
            assertTrue(neutral.hasManualInput(GbaInput.axisBits(0f, 0f, 0f, value)))
        }
        assertFalse(neutral.hasManualInput(GbaInput.axisBits(.5f, -.5f, -.05f, .05f)))
        assertFalse(neutral.hasManualInput(null))
    }
}

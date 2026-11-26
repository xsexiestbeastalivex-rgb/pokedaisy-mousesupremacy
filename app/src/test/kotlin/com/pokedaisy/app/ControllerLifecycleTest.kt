package com.pokedaisy.app

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.pokedaisy.app.companion.KeyCapture
import com.pokedaisy.app.companion.data.CoreKey
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Drives the real Android key port and consumes the upstream key mix at the same lock boundary as the engine. */
class ControllerLifecycleTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Port(parser: Hotkeys? = null, notify: (Hotkeys.Event) -> Unit = {}) {
        var stamp = 1L
        val hotkeys = mutableListOf<Hotkeys.Event>()
        val input = GbaInput().apply {
            setControls(mapOf(R2 to CoreKey.A, L2 to CoreKey.B, BUTTON_A to CoreKey.A,
                SELECT to CoreKey.SELECT, R1 to CoreKey.R))
        }
        val hardware = AndroidHardwareInput(input, { parser }, {
            hotkeys += it
            notify(it)
        })

        fun digital(device: Int, key: Int, down: Boolean, at: Long = stamp, repeat: Int = 0,
            cancelled: Boolean = false, touch: Boolean = false): AndroidHardwareInput.KeyPhase {
            return hardware.digital(device, key, down, repeat, cancelled, at, stamp++, touch).also {
                when (it) {
                    AndroidHardwareInput.KeyPhase.PRESS -> hardware.playKey(key, true)
                    AndroidHardwareInput.KeyPhase.RELEASE -> {
                        hardware.playKey(key, false)
                        hardware.finishRelease(key)
                    }
                    else -> Unit
                }
            }
        }

        fun motion(device: Int, held: Boolean = false, source: Int = InputDevice.SOURCE_JOYSTICK,
            action: Int = MotionEvent.ACTION_MOVE, directions: Int = 0, left: Boolean = false) {
            val triggers = ControllerTriggers(if (left) .8f else 0f, if (held) .8f else 0f)
            if (hardware.joystick(device, source, action, stamp++, triggers, directions) { key, down ->
                hardware.playKey(key, down)
                if (!down) hardware.finishRelease(key)
            }) hardware.gameAxes(device, directions)
        }

        fun device(id: Int, detached: Boolean = true) = hardware.deviceBoundary(id, stamp++, detached)

        fun focus(remapping: Boolean = false) = hardware.windowBoundary(stamp++, remapping)

        fun frame(turboPhase: Boolean = false) = input.sampleMouseInput { stamp ->
            val mask = input.mask or input.tapMask() or (if (turboPhase) input.turboMask else 0)
            input.withMouseInput(stamp) { idle, _ -> assertFalse(idle && mask != 0) }
            mask
        }
    }

    @Test fun cancelledHeldAxesRequireTheirOwnNeutralEvenWithOtherDevicesAndMouseNoise() {
        val p = Port()
        p.motion(1, true, directions = CoreKey.UP)
        assertEquals(CoreKey.A or CoreKey.UP, p.frame())
        p.motion(2)
        p.motion(1, source = InputDevice.SOURCE_TOUCHSCREEN)
        p.motion(1, source = InputDevice.SOURCE_MOUSE)
        assertEquals(CoreKey.A or CoreKey.UP, p.frame())

        p.motion(1, true, action = MotionEvent.ACTION_CANCEL, directions = CoreKey.UP)
        assertEquals(0, p.frame())
        p.motion(2)
        p.motion(1, true, directions = CoreKey.UP)
        assertEquals(0, p.frame())
        // Neutral is per channel and per device. A neutral stick alone must not rearm R2.
        p.motion(1, true)
        p.motion(1, true, directions = CoreKey.UP)
        assertEquals(CoreKey.UP, p.frame())
        p.motion(1)
        p.motion(1, true)
        assertEquals(CoreKey.A, p.frame())
    }

    @Test fun aLateCancelCannotResetNewerGameplayOrNativeAxes() {
        val p = Port()
        val old = p.stamp++
        p.motion(1, held = true, directions = CoreKey.LEFT)
        val decisions = mutableListOf<Boolean>()
        var retired = 0
        fun cancel(time: Long) = p.hardware.joystick(1, InputDevice.SOURCE_JOYSTICK, MotionEvent.ACTION_CANCEL,
            time, ControllerTriggers(0f, .8f), CoreKey.LEFT, observe = { decisions += it },
            retired = { retired++ }) { _, _ -> error("terminal is not a routed key edge") }
        cancel(old)
        assertEquals(CoreKey.A or CoreKey.LEFT, p.frame())
        assertEquals(0, retired)
        cancel(p.stamp++)
        assertEquals(0, p.frame())
        assertEquals(1, retired)
        assertEquals(listOf(false, true), decisions)
    }

    @Test fun everyDeviceAndChannelRetirementOrderPreservesAnOverlappingA() {
        for (firstAnalog in listOf(false, true)) for (secondAnalog in listOf(false, true)) {
            for (detachFirst in listOf(false, true)) for (sameDevice in listOf(false, true)) {
                if (sameDevice && (firstAnalog == secondAnalog || detachFirst)) continue
                val p = Port()
                val second = if (sameDevice) 1 else 2
                val firstDown = p.stamp
                if (firstAnalog) p.motion(1, true) else p.digital(1, R2, true)
                val secondDown = p.stamp
                if (secondAnalog) p.motion(second, true) else p.digital(second, R2, true)
                assertEquals(CoreKey.A, p.frame())
                if (detachFirst) p.device(1)
                else if (firstAnalog) p.motion(1, true, action = MotionEvent.ACTION_CANCEL)
                else p.digital(1, R2, false, firstDown, cancelled = true)
                assertEquals(CoreKey.A, p.frame())
                if (secondAnalog) p.motion(second) else p.digital(second, R2, false, secondDown)
                assertEquals(0, p.frame())
                if (detachFirst) {
                    p.motion(1, true)
                    assertEquals(0, p.frame())
                    p.device(1, detached = false)
                    p.motion(1, true)
                    assertEquals(0, p.frame())
                }
                p.motion(1)
                p.motion(1, true)
                assertEquals(CoreKey.A, p.frame())
            }
        }
    }

    @Test fun aDelayedDigitalUpStillReleasesItsOwnHoldAfterJoystickCancel() {
        val p = Port()
        val at = p.stamp
        p.digital(1, R2, true)
        val delayedUp = p.stamp++
        p.motion(1, held = true)
        p.motion(2, held = true)
        p.motion(1, held = true, action = MotionEvent.ACTION_CANCEL)
        assertEquals(CoreKey.A, p.frame())
        assertEquals(AndroidHardwareInput.KeyPhase.IGNORE,
            p.hardware.digital(1, R2, false, 0, false, at, delayedUp))
        assertEquals(CoreKey.A, p.frame())
        p.motion(2)
        assertEquals(0, p.frame())
    }

    @Test fun detachRetiresOnlyItsDeviceIncludingHatAndTurbo() {
        val p = Port()
        p.input.setControls(mapOf(R2 to (GbaControls.TURBO or CoreKey.A), BUTTON_A to CoreKey.A))
        p.motion(1, true, directions = CoreKey.LEFT)
        p.motion(2, directions = CoreKey.UP)
        val other = p.stamp
        p.digital(2, BUTTON_A, true)
        p.input.setTouchBits(CoreKey.B)
        assertEquals(CoreKey.A or CoreKey.B or CoreKey.LEFT or CoreKey.UP, p.frame(true))
        p.device(1)
        assertEquals(CoreKey.A or CoreKey.B or CoreKey.UP, p.frame(true))
        assertEquals(0, p.input.turboMask)
        p.digital(2, BUTTON_A, false, other)
        assertEquals(CoreKey.B or CoreKey.UP, p.frame())
    }

    @Test fun focusAndRemapFenceOldAxesAndDigitalGenerationWithoutDroppingTouch() {
        val p = Port()
        val old = p.stamp
        p.digital(2, R2, true)
        p.motion(1, true)
        p.input.setTouchBits(CoreKey.SELECT)
        val sample = p.input.sampleMouseInput { it }
        p.focus()
        p.focus() // regain
        assertEquals(CoreKey.SELECT, p.frame())
        p.input.withMouseInput(sample) { idle, manual -> assertFalse(idle); assertFalse(manual) }
        p.motion(1, true)
        assertEquals(AndroidHardwareInput.KeyPhase.IGNORE, p.digital(2, R2, true, old, repeat = 1))
        assertEquals(AndroidHardwareInput.KeyPhase.IGNORE, p.digital(2, R2, true, old))
        assertEquals(CoreKey.SELECT, p.frame())
        p.motion(1)
        p.motion(1, true)
        assertEquals(CoreKey.A or CoreKey.SELECT, p.frame())
        p.focus(remapping = true)
        p.input.setControls(mapOf(R2 to CoreKey.B))
        p.motion(1, true)
        assertEquals(CoreKey.SELECT, p.frame())
        p.motion(1)
        p.motion(1, true)
        assertEquals(CoreKey.B or CoreKey.SELECT, p.frame())
    }

    @Test fun terminalReleaseEndsHotkeyOnlyAtLastOwnerAndCannotMintDeferredTap() {
        val parser = Hotkeys.load(tmp.newFolder().toOkioPath())
        val p = Port(parser)
        p.motion(1, true)
        p.motion(2, true)
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.FF_HOLD, true)), p.hotkeys)
        p.motion(1, true, action = MotionEvent.ACTION_CANCEL)
        assertEquals(1, p.hotkeys.size)
        p.device(2)
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.FF_HOLD, true),
            Hotkeys.Event(Hotkeys.Action.FF_HOLD, false)), p.hotkeys)
        assertEquals(0, p.frame())

        val first = p.stamp
        p.digital(1, SELECT, true)
        assertEquals(0, p.frame())
        p.digital(1, SELECT, false, first, cancelled = true)
        assertTrue(List(GbaInput.TAP_FRAMES + 1) { p.frame() }.all { it == 0 })
        val fresh = p.stamp
        p.digital(1, SELECT, true)
        p.digital(1, SELECT, false, fresh)
        assertEquals(List(GbaInput.TAP_FRAMES) { CoreKey.SELECT } + 0, List(GbaInput.TAP_FRAMES + 1) { p.frame() })
    }

    @Test fun aChordOrModalCannotStrandAPreviouslyForwardedKey() {
        val dir = tmp.newFolder().toOkioPath()
        Hotkeys.setBinding(dir, Hotkeys.Action.SAVE_STATE, listOf(BUTTON_A, R1))
        val p = Port(Hotkeys.load(dir))
        val downAt = p.stamp
        p.digital(1, BUTTON_A, true)
        assertEquals(CoreKey.A, p.frame())
        val chordAt = p.stamp
        p.digital(1, R1, true)
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.SAVE_STATE, true)), p.hotkeys)
        p.digital(1, BUTTON_A, false, downAt)
        p.digital(1, R1, false, chordAt)
        assertEquals(0, p.frame())

        val physical = p.stamp
        p.digital(2, BUTTON_A, true)
        p.digital(-1, BUTTON_A, true, touch = true)
        p.focus()
        assertEquals(CoreKey.A, p.frame())
        // The old physical release cannot remove the explicitly separate synthetic touch key.
        p.digital(2, BUTTON_A, false, physical)
        assertEquals(CoreKey.A, p.frame())
        p.digital(-1, BUTTON_A, false, touch = true)
        assertEquals(0, p.frame())
    }

    @Test fun terminalCallbackCannotDeliverTheSecondEdgeFromItsOldReport() {
        val parser = Hotkeys.load(tmp.newFolder().toOkioPath())
        lateinit var p: Port
        p = Port(parser) {
            if (it.pressed && it.action == Hotkeys.Action.FF_HOLD) p.focus()
        }
        p.motion(1, true, left = true)
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.FF_HOLD, true),
            Hotkeys.Event(Hotkeys.Action.FF_HOLD, false)), p.hotkeys)
        assertEquals(0, p.frame())
    }

    @Test fun aMenuOpeningTriggerCannotStrandAReleaseFromTheSameReport() {
        val dir = tmp.newFolder().toOkioPath()
        Hotkeys.releaseKey(dir, "BUTTON_L2")
        Hotkeys.releaseKey(dir, "BUTTON_R2")
        Hotkeys.setBinding(dir, Hotkeys.Action.FF_HOLD, listOf(L2))
        Hotkeys.setBinding(dir, Hotkeys.Action.QUICK_MENU, listOf(R2))
        lateinit var p: Port
        p = Port(Hotkeys.load(dir)) {
            if (it.pressed && it.action == Hotkeys.Action.QUICK_MENU) p.hardware.clearGameButtons()
        }
        p.motion(1, left = true)
        p.motion(1, held = true)
        p.motion(1, held = true)
        assertEquals(listOf(Hotkeys.Event(Hotkeys.Action.FF_HOLD, true),
            Hotkeys.Event(Hotkeys.Action.FF_HOLD, false),
            Hotkeys.Event(Hotkeys.Action.QUICK_MENU, true)), p.hotkeys)
        assertEquals(0, p.frame())
    }

    @Test fun anInterruptedCaptureCannotSaveTheRemainderOfAChord() {
        val capture = KeyCapture()
        val saved = mutableListOf<List<Int>>()
        capture.start(KeyCapture.Request("Held chord", true, { saved += it }))
        capture.onKey(BUTTON_A, true)
        capture.onKey(R1, true)
        capture.retireKeys(setOf(BUTTON_A))
        capture.onKey(R1, false)
        assertTrue(saved.isEmpty())
        assertTrue(capture.active)
        capture.onKey(SELECT, true)
        capture.onKey(SELECT, false)
        assertEquals(listOf(listOf(SELECT)), saved)
    }

    private companion object {
        const val R2 = KeyEvent.KEYCODE_BUTTON_R2
        const val L2 = KeyEvent.KEYCODE_BUTTON_L2
        const val R1 = KeyEvent.KEYCODE_BUTTON_R1
        const val BUTTON_A = KeyEvent.KEYCODE_BUTTON_A
        const val SELECT = KeyEvent.KEYCODE_BUTTON_SELECT
    }
}

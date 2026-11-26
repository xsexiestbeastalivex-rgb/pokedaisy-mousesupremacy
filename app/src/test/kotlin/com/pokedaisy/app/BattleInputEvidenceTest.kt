package com.pokedaisy.app

import android.view.InputDevice
import android.view.MotionEvent
import com.pokedaisy.app.companion.data.CoreKey
import com.pokedaisy.app.companion.ui.TouchHits
import com.pokedaisy.app.companion.ui.TouchInput
import com.pokedaisy.app.companion.ui.TouchPressTracker
import com.pokedaisy.app.companion.ui.TouchPressed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleInputEvidenceTest {
    @Test fun identicalControllerReportsRetainSeparateAliasesAndTerminalAction() {
        val rows = mutableListOf<String>()
        val evidence = BattleInputEvidence({ rows += it }, limit = 12)
        fun report(id: Int, action: Int = MotionEvent.ACTION_MOVE, admitted: Boolean = true) {
            evidence.controller(id, action, CoreKey.UP, false, true, 20, InputDevice.SOURCE_JOYSTICK, admitted)
        }
        report(1001)
        report(1001)
        report(2002)
        report(1001, MotionEvent.ACTION_CANCEL, false)
        evidence.controllerRetired(1001, "device-removed")
        report(1001)
        assertEquals(5, rows.size)
        assertTrue(rows[0].contains("device=1 action=2"))
        assertTrue(rows[1].contains("device=2 action=2"))
        assertTrue(rows[2].contains("device=1 action=3"))
        assertTrue(rows[3].contains("device=1 terminal=device-removed"))
        assertTrue(rows[4].contains("device=3 action=2"))
        assertTrue(rows.none { "1001" in it || "2002" in it })
    }

    @Test fun nativePadMotionReportsOnlyItsChangesToThePublishedTouchMask() {
        val published = mutableListOf<Int>()
        val observed = mutableListOf<Int>()
        val tracker = TouchPressTracker(TouchPressed())
        tracker.input = TouchInput(onKeys = { keys, _ -> published += keys })
        tracker.update(TouchPressTracker.SOURCE_DRAWN, TouchHits(0, CoreKey.UP, 0))
        fun native(bits: Int) = tracker.update(TouchPressTracker.SOURCE_WINDOWS, TouchHits(0, bits, 0)) { keys, _ ->
            assertEquals(keys, published.last())
            observed += keys
        }
        native(CoreKey.UP) // Identical contribution already published by the independent drawn source.
        native(CoreKey.LEFT)
        native(CoreKey.LEFT)
        native(0)
        assertEquals(listOf(CoreKey.UP or CoreKey.LEFT, CoreKey.UP), observed)
    }

    @Test fun commitsRetainZeroReservationsAndDoNotSpendRowsOnUnchangedFrames() {
        val rows = mutableListOf<String>()
        val evidence = BattleInputEvidence({ rows += it }, limit = 8)
        val input = GbaInput()
        val baseline = BattleInputEvidence.Frame(input.mouseSources(), 0, 0, 0, 0, 0, 0, 0,
            true, false, false, "open", "MOVE", "MOVE", 0)
        repeat(10000) { evidence.frame(1, -1, it.toLong(), baseline) }
        assertEquals(1, rows.size)
        input.setControls(mapOf(91 to GbaControls.COMPANION, 90 to CoreKey.A))
        assertTrue(input.onKey(91, true))
        val zero = input.mouseSources()
        assertTrue(zero.keyPending)
        assertEquals(0, zero.key)
        evidence.frame(1, 10, 10001, baseline.copy(sources = zero, rawIdle = false, gate = "raw-input"))
        assertTrue(input.onKey(91, false))
        input.tap(91)
        val sampled = input.mouseSources()
        assertTrue(sampled.tapPending)
        assertEquals(0, sampled.queuedTap)
        assertEquals(0, input.tapMask())

        input.onKey(90, true)
        val a = baseline.copy(sources = input.mouseSources(), held = input.mask, mixed = input.mask,
            upstream = input.mask, committed = input.mask, rawIdle = false, gate = "observer-failed")
        repeat(50) { evidence.frame(-1, -1, 10002L + it, a) }
        assertEquals(3, rows.size)
        assertTrue(rows.last().contains("committed=1"))
        assertTrue(rows.last().contains("gate=observer-failed"))

        repeat(12) { evidence.raw("edge=$it") }
        assertEquals(8, rows.count { it.startsWith("raw ") })
        assertEquals(1, rows.count { it == "stop stream=raw reason=record-limit" })
        evidence.frame(1, 11, 10100, baseline.copy(fieldAction = true, gate = "upstream-gate"))
        assertTrue(rows.last().contains("fieldAction=true"))
        assertFalse(rows.contains("stop stream=commit reason=record-limit"))
    }
}

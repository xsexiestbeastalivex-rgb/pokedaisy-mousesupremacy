package com.pokedaisy.app

import com.pokedaisy.app.companion.data.CoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleInputEvidenceTest {
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

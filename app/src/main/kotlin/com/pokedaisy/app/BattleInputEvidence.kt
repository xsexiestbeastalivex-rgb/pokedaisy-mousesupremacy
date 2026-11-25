package com.pokedaisy.app

import java.util.concurrent.atomic.AtomicInteger

/** Opt-in development evidence. Raw edges and final-mask transitions have independent budgets. */
internal class BattleInputEvidence(private val emit: (String) -> Unit, limit: Int = 4096) {
    data class Frame(
        val sources: GbaInput.SourceSnapshot, val held: Int, val tap: Int, val turbo: Int, val reset: Int,
        val mixed: Int, val upstream: Int, val committed: Int, val rawIdle: Boolean,
        val companion: Boolean, val fieldAction: Boolean, val gate: String,
        val menu: String, val ready: String, val cursor: Int,
    )

    private val frames = AtomicInteger(limit)
    private val events = AtomicInteger(limit)
    private val workRows = AtomicInteger(limit / 4)
    private var lastFrame: Frame? = null
    private var lastSession = Long.MIN_VALUE
    private var lastNativeFrame = -1L
    private var lastController: Triple<Int?, Boolean, Boolean>? = null

    fun raw(message: String) = record(events, "raw", message)

    fun controller(bits: Int?, left: Boolean, right: Boolean, event: Long, source: Int) {
        val state = Triple(bits, left, right)
        if (lastController == state) return
        lastController = state
        raw("channel=controller event=$event source=$source axes=$bits left=$left right=$right")
    }

    fun work(message: String) = record(workRows, "field", message)

    // Emulation thread only. Include native cursor/menu and zero-mask reservations in transitions.
    fun frame(session: Long, nativeFrame: Long, engineStep: Long, snapshot: Frame) {
        val sameStream = lastSession == session &&
            (nativeFrame == lastNativeFrame || nativeFrame == (lastNativeFrame + 1) % 0x1_0000_0000L)
        lastSession = session
        lastNativeFrame = nativeFrame
        if (lastFrame == snapshot && sameStream) return
        lastFrame = snapshot
        record(frames, "commit", "step=$engineStep session=$session before=$nativeFrame $snapshot")
    }

    private fun record(budget: AtomicInteger, stream: String, message: String) {
        val left = budget.getAndUpdate { if (it > 0) it - 1 else 0 }
        try {
            if (left > 0) emit("$stream $message")
            if (left == 1) emit("stop stream=$stream reason=record-limit")
        } catch (_: Exception) {
            budget.set(0)
        }
    }
}

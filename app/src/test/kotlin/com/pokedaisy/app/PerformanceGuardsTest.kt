package com.pokedaisy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level guards for the crash / CPU / memory regressions of v1.1.0, which no
 * screenshot test could see (they render one screen once). The runtime side is
 * ui-preview's `gradle stress`, which switches tabs rapidly against memory budgets.
 */
class PerformanceGuardsTest {
    private val src = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.isDirectory }
    private val appDir = File(src, "com/pokedaisy/app")
    private fun read(path: String) = File(appDir, path).readText()
    private fun kotlinFiles() = src.walkTopDown().filter { it.isFile && it.extension == "kt" }

    @Test
    fun `fonts are only built by PixelTypeface, once per process`() {
        // Building one loads the 1 MB PixelMplusJP and 1.5 MB GalmuriKO; v1.1.0 built one per GbaText,
        // tens of MB per tab switch, and crashed when tabs were switched quickly.
        val builders = listOf("Font.Builder(", "CustomFallbackBuilder(", "Typeface.createFrom", "ComposeFont(", "Font(\"fonts/")
        val offenders = kotlinFiles().filter { it.name != "PixelTypeface.kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, line) -> builders.any { it in line } }.map { (i, _) -> "${f.name}:${i + 1}" }
        }.toList()
        assertTrue("build fonts through pixelFontFamily / gameFontFamily / readingFontFamily (PixelTypeface.kt), not at $offenders", offenders.isEmpty())
        val typeface = read("PixelTypeface.kt")
        assertTrue("pixelFontFamily must return its cached family", "pixelFamily?.let { return it }" in typeface)
        assertTrue("gameFontFamily must reuse a family it already built", "gameFamilies[stamp]?.let { return it }" in typeface)
        assertTrue("readingFontFamily must return its cached family", "readingFamily?.let { return it }" in typeface)
    }

    @Test
    fun `the GL thread lets go of the frame buffer before the core frees it`() {
        // pkDeinit frees the buffer EmulatorView's GL thread uploads from (a native
        // crash with no Java stack when the order is wrong).
        val engine = read("EmulatorEngine.kt")
        val finally = engine.substring(engine.indexOf("} finally {", engine.indexOf("emu loop crashed")))
        assertTrue(
            "EmulatorEngine must run onCoreStopping (the view's unbind) before MgbaCore.pkDeinit",
            finally.indexOf("onCoreStopping?.invoke()") in 0 until finally.indexOf("MgbaCore.pkDeinit()"),
        )
        val activity = read("PokeDaisyActivity.kt")
        // onPause's work (pauseGame: onPause skips it only for the pause around a move to the other screen).
        val onPause = activity.substring(activity.indexOf("private fun pauseGame()")).substringBefore("\n    }\n")
        assertTrue(
            "onPause must unbind the view before stopping the engine",
            onPause.indexOf("view.unbindCoreBlocking()") in 0 until onPause.indexOf("engine.stop"),
        )
        assertTrue("the activity must wire onCoreStopping to the view's unbind", "view.unbindCoreBlocking()" in lambda(activity, "onCoreStopping = {"))
    }

    @Test
    fun hardwareTerminalsReachTheOwnershipPortBeforeOrdinaryRouting() {
        val activity = read("PokeDaisyActivity.kt")
        assertOrdered(lambda(activity, "private fun dispatchOwnedKey("),
            "hardwareInput.digital", "AndroidHardwareInput.KeyPhase.RELEASE", "hardwareInput.finishRelease")
        assertOrdered(lambda(activity, "private fun routeJoystick("),
            "hardwareInput.joystick", "controllerMotion", "onGameMotion")
        assertOrdered(lambda(activity, "override fun onPause() {"), "retireHardwareInput", "super.onPause()")
        assertTrue("both device loss and reconfiguration retire hardware",
            listOf("onInputDeviceRemoved", "onInputDeviceChanged").all { method ->
                "retireInputDevice" in lambda(activity, "override fun $method(")
            })
    }

    @Test
    fun theFlightPollAndForegroundActionsKeepTheirDifferentOwnersAtTheFrameBoundary() {
        val activity = read("PokeDaisyActivity.kt")
        val engine = read("EmulatorEngine.kt")
        val periodic = lambda(activity, "private fun syncFlightArea(")
        assertTrue("the Activity must use the maintenance bridge", "engine.requestFlightAreaMaintenance" in periodic)
        assertTrue("the poll cannot reserve gameplay", "requestFieldItemJob" !in periodic)
        assertTrue("the foreground bridge still vetoes queued work",
            "mouse.interrupt(\"companion-field-request\")" in lambda(engine, "fun requestFieldItemJob("))
        assertOrdered(engine.substringAfter("while (running) {\n                if (!run.beforeFrame())"),
            "run.runCore()", "run.afterCore()", "run.watchScreens()", "run.fieldStep()", "run.serviceFieldMaintenance()")
        val commit = lambda(engine, "private fun commitBattleMouse() {")
        assertOrdered(commit.substring(commit.indexOf("commitWithMouseReservation")),
            "commitWithMouseReservation", "withMouseInput", "hasQueuedFieldWork()",
            "mouse.commit", "MgbaCore.pkSetKeys(final)", "evidence = inputFrame", "inputEvidence?.frame")
    }

    @Test
    fun framePublicationUsesCompletedPixelsAndMatchingMetadata() {
        // The GL thread uploads a copy made after the core step. Sample and window follow the
        // same copy; neither a held draw nor a not-yet-swapped draw is fresh presentation.
        val engine = read("EmulatorEngine.kt")
        val frame = lambda(engine, "fun runCore() {")
        assertOrdered(frame, "commitBattleMouse()", "MgbaCore.pkRunFrame()", "sampleCompletedBattleFrame()",
            "if (nds) stepDsHold()", "onFrame?.invoke()", "frameCoop?.afterFrame()", "RetroAchievements.onFrame()")
        val beforePublish = frame.substringAfter("MgbaCore.pkRunFrame()").substringBefore("onFrame?.invoke()")
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }.toList()
        assertEquals("only the bounded sample and DS hold may run between core and publication",
            listOf("val coreEnd = System.nanoTime()", "sampleCompletedBattleFrame()", "if (nds) stepDsHold()", "try {"),
            beforePublish)
        val capture = lambda(engine, "private fun sampleCompletedBattleFrame() {")
        assertOrdered(capture, "battleMouseSample = null", "battleMouseWindow = mouseWideWindow",
            "if (mouseObserverFailed || rewinding || mouseResetting) return",
            "battleMouse?.applied(mouseFrameBefore, reader.frame(), reader.sample())")
        assertTrue("Activity must publish the completed sample paired with the frame's camera window",
            "view.publishFrame(battleMouseSample, battleMouseWindow)" in
                lambda(read("PokeDaisyActivity.kt"), "onFrame = {"))

        val view = read("EmulatorView.kt")
        val front = lambda(view, "internal fun publishFrame(")
        assertOrdered(front, "if (wide && MgbaCore.pkWideSkipped()) return",
            "if (gpuFrames && MgbaCore.pkNdsGlPresented())", "renderer.publishGpu(",
            "sample.takeIf { win === sampleWindow && w == win.w && h == win.h }")
        val publish = lambda(view, "fun publish(wideW:")
        val copy = lambda(publish, "synchronized(frameLock) {")
        assertOrdered(copy, "val src = wideSrc ?: buffer", "val dst = ready", "dst.put(src)",
            "readyWin =", "readyBattle =", "fresh = true")
        val upload = lambda(lambda(view, "private fun draw() {"), "synchronized(frameLock) {")
        assertOrdered(upload, "val buf = ready", "if (fresh && !holdFrame()", "takeGpuFrame()")
        for ((api, binding) in listOf("glTexImage2D(" to "if (!texAllocated) {",
            "glTexSubImage2D(" to "else if (fresh && !holdFrame()) {")) {
            val take = lambda(upload, binding)
            assertOrdered(take, "buf.position(0)", api, "shownWin = readyWin", "shownBattle = readyBattle")
            assertTrue("each upload must use ready pixels", "GLES20.GL_UNSIGNED_BYTE, buf," in take)
        }
        val draw = lambda(view, "override fun onDrawFrame(")
        assertOrdered(draw, "mouse.show(battleDraw?.takeIf { pointerPicture != null && !holdFrame() }",
            "draw()", "reportPointerPicture()", "val sample = shownBattle", "Picture(sample, geometry.transform")
    }

    @Test
    fun optionalFrameCallbacksContainExceptions() {
        val engine = read("EmulatorEngine.kt")
        val frame = lambda(engine, "fun runCore() {")
        val sample = lambda(engine, "private fun sampleCompletedBattleFrame() {")
        assertCaught(sample, "battleMouse?.applied(", "mouseObserverFailed = true")
        assertCaught(frame, "onFrame?.invoke()", "framePublisherFailed = true")
        assertCaught(frame, "RetroAchievements.onFrame()", "raFrameFailed = true")
        assertTrue("AudioTrack creation and start must fall back to silence",
            Regex("""runCatching \{ buildAudioTrack\(sampleRate\)\.also \{ it\.play\(\) \} \}\s*\.onFailure [^\n]*\n\s*\.getOrNull\(\)""")
                .containsMatchIn(engine))
    }

    private fun assertOrdered(body: String, vararg steps: String) {
        var previous = -1
        for (step in steps) {
            val index = body.indexOf(step)
            assertTrue("expected $step after the preceding frame operation", index > previous)
            previous = index
        }
    }

    private fun assertCaught(body: String, operation: String, recovery: String) {
        val i = body.indexOf(operation)
        assertTrue("expected protected call $operation", i >= 0)
        val start = body.lastIndexOf("try {", i)
        val attempt = if (start >= 0) lambda(body.substring(start), "try {") else ""
        assertTrue("$operation must execute inside its try", operation in attempt)
        val tail = body.substring((start + attempt.length).coerceAtLeast(0)).trimStart()
        assertTrue("$operation must recover after Exception",
            Regex("""^catch \(\w+: Exception\) \{""").containsMatchIn(tail) && recovery in lambda(tail, "catch ("))
    }

    @Test
    fun `main-core reads are guarded against teardown`() {
        // The companion's ROM readers call pkReadBytes from IO threads.
        val cpp = File(src.parentFile, "cpp")
        val jni = File(cpp, "pokedaisy_jni.c").readText()
        val session = File(cpp, "pk_session.c").readText()
        // JNI keeps the DS path; the portable C API owns the GBA / Game Boy path and its lock now.
        val readAny = lambda(jni, "static bool pk_read_any(")
        assertCoreLockCovers(readAny, "pthread_rwlock_rdlock(&pk_coreLock)", "pk_nds_read(", "DS reads")
        assertTrue("pk_read_any must delegate GBA / GB reads to the guarded C API", "pk_api_read(" in readAny)
        for (fn in listOf("pkReadBytes", "pkReadInto")) {
            val body = lambda(jni, "MgbaCore_$fn(")
            assertTrue("$fn must use the guarded reader", "pk_read_any(" in body)
        }
        assertTrue("pkRomRead must use the guarded C API", "pk_api_rom_read(" in lambda(jni, "MgbaCore_pkRomRead("))
        val readers = mapOf(
            "pk_api_read" to listOf("pk_gb_read_banked(", "pk_read_rom_file(", "pk_read_range(", "pk_cheats_overlay("),
            "pk_api_rom_read" to listOf("g.core->platform(", "memcpy("),
        )
        for ((fn, accesses) in readers) {
            val body = lambda(session, "bool $fn(")
            accesses.forEach { assertCoreLockCovers(body, "pthread_rwlock_rdlock(&pk_coreLock)", it, fn) }
        }
        val teardown = lambda(jni, "static void pkTeardown(void)")
        assertCoreLockCovers(teardown, "pthread_rwlock_wrlock(&pk_coreLock)", "pk_nds_deinit()", "DS teardown")
        assertTrue("pkTeardown must also close the portable core", "pk_api_deinit()" in teardown)
        val portableTeardown = lambda(session, "void pk_api_deinit(void)")
        for (access in listOf("g.core->deinit(g.core)", "g.romFileVf->unmap(", "free(g.video)")) {
            assertCoreLockCovers(portableTeardown, "pthread_rwlock_wrlock(&pk_coreLock)", access, "GBA / GB teardown")
        }
    }

    private fun assertCoreLockCovers(body: String, acquire: String, access: String, operation: String) {
        val lock = body.indexOf(acquire)
        val use = body.indexOf(access)
        val unlock = body.indexOf("pthread_rwlock_unlock(&pk_coreLock)")
        assertTrue("$operation must hold pk_coreLock around $access", lock >= 0 && use > lock && unlock > use)
    }

    /** The brace-delimited body [start] opens in [src]: a Kotlin lambda or a native C function. */
    private fun lambda(src: String, start: String): String {
        val from = src.indexOf(start)
        if (from < 0) return ""
        var depth = 0
        for (i in from until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return src.substring(from, i + 1)
            }
        }
        return ""
    }
}

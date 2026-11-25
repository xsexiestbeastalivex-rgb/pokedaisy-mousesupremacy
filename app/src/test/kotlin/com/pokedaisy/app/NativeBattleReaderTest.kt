package com.pokedaisy.app

import com.pokedaisy.app.companion.data.MemoryReader
import com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT
import com.pokedaisy.app.companion.data.GameCalls
import com.pokedaisy.app.game.GameSession
import com.pokedaisy.app.mouse.NativeBattleMouse
import com.pokedaisy.app.mouse.NativeBattleMouse.A
import com.pokedaisy.app.mouse.NativeBattleMouse.B
import com.pokedaisy.app.mouse.NativeBattleMouse.LEFT
import com.pokedaisy.app.mouse.NativeBattleMouse.Picture
import com.pokedaisy.mousesupremacy.pointer.PointerLayout
import com.pokedaisy.app.mouse.NativeBattleMouse.Menu
import com.pokedaisy.app.mouse.NativeBattleMouse.Native
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import com.pokedaisy.app.NativeBattleReader.Profile

class NativeBattleReaderTest {
    private class Fixture(private val directory: String = "firered_shiny_battle") : MemoryReader {
        private val ewram = resource("ewram.bin")
        private val iwram = resource("iwram.bin")
        var shortAt = -1L

        private fun resource(name: String) =
            checkNotNull(javaClass.getResourceAsStream("/fixtures/$directory/$name")).use { it.readBytes() }

        fun set(address: Long, vararg values: Int) {
            val (bytes, off) = locate(address)
            values.forEachIndexed { i, value -> bytes[off + i] = value.toByte() }
        }

        private fun locate(address: Long) =
            (if (address >= 0x03000000) iwram else ewram) to (address and 0xFFFFFF).toInt()

        override fun readCoreMemory(addr: Long, size: Int): ByteArray {
            val (bytes, off) = locate(addr)
            return bytes.copyOfRange(off, off + if (addr == shortAt) size - 1 else size)
        }

        fun setWord(address: Long, value: Long) {
            set(address, *(0..3).map { (value ushr (it * 8)).toInt() and 255 }.toIntArray())
        }

        fun ordinary() {
            set(0x02022B4C, 4, 0, 0, 0)
            setWord(Profile.FIRERED_REV1.config.gMain + 8, 0x0807FB55L)
            set(FADE, *IntArray(10))
        }
    }

    @Test fun scriptedFixtureWithTheActionHandlerDoesNotQualify() {
        val fixture = Fixture()
        assertEquals(Native.UNKNOWN, NativeBattleReader(fixture, Profile.FIRERED_REV1, FADE).sample())
    }

    @Test fun exactControllerAndCommandAreNecessaryEvenWithTheMenuVisible() {
        val fixture = Fixture()
        fixture.ordinary()
        val reader = NativeBattleReader(fixture, Profile.FIRERED_REV1, FADE)
        assertEquals(Native(Menu.ACTION, Menu.ACTION, 0, 4), reader.sample())
        fixture.set(0x02023BC8, 0)
        assertEquals(Native(Menu.ACTION, null, -1, 0), reader.sample())
        fixture.set(0x02023BC8, 1)
        fixture.set(0x03004FE4, 0x25, 0xEA, 0x02, 0x08)
        assertEquals(Native(Menu.ACTION, null, -1, 0), reader.sample())
        fixture.shortAt = 0x03003529
        assertEquals(Native.UNKNOWN, reader.sample())
    }

    @Test fun threeMoveCellsAndFadeAreDecodedIndependently() {
        val fixture = Fixture()
        fixture.ordinary()
        fixture.set(0x02022976, 0x40, 0x01)
        fixture.set(0x03004FE0, 0x25, 0xEA, 0x02, 0x08)
        fixture.set(0x02022BC4, 20, 0, 0, 0, 33, 0, 52, 0, 10, 0, 0, 0)
        fixture.set(0x02023FFC, 2)
        val reader = NativeBattleReader(fixture, Profile.FIRERED_REV1, FADE)
        assertEquals(Native(Menu.MOVE, Menu.MOVE, 2, 3), reader.sample())
        fixture.set(0x02023FFC, 3)
        assertEquals(Native(Menu.MOVE, null, -1, 0), reader.sample())
        fixture.set(0x02023FFC, 2)
        fixture.set(FADE + 4, 0x40, 0, 0, 0x80)
        assertEquals(Native(Menu.MOVE, null, -1, 0), reader.sample())
    }

    @Test fun exactIdentityDoesNotAcceptAHeaderOrAnotherRevisionHash() {
        for (profile in Profile.entries) {
            assertEquals(profile, NativeBattleReader.exactProfile(profile.code, profile.revision, profile.sha1))
            assertNull(NativeBattleReader.exactProfile(profile.code, profile.revision, "0".repeat(40)))
            assertNull(NativeBattleReader.exactProfile(profile.code, profile.revision + 1, profile.sha1))
        }
        assertNull(NativeBattleReader.exactProfile("BPEE", 0, Profile.FIRERED_REV0.sha1))
    }

    @Test fun scriptedReturnAloneBlocksReadyLookingFixturesOnAllProfiles() {
        val cases = listOf(
            Triple(Profile.FIRERED_REV0, "firered_randomized_rev0_battle", 0x0807FB41L),
            Triple(Profile.FIRERED_REV1, "firered_shiny_battle", 0x0807FB55L),
            Triple(Profile.EMERALD, "emerald_shiny_battle", 0x080B0AF9L),
        )
        for ((profile, directory, ordinaryReturn) in cases) {
            val fixture = Fixture(directory)
            val fade = profile.fade ?: FADE
            val reader = NativeBattleReader(fixture, profile, fade)
            assertNull(reader.sample().ready())
            fixture.set(profile.config.battleTypeFlags, 4, 0, 0, 0)
            fixture.set(profile.bg0y, 160, 0)
            fixture.set(profile.exec, 1, 0, 0, 0)
            fixture.set(profile.commandBuffer, 18)
            fixture.set(profile.config.actionCursor, 0)
            fixture.set(fade, *IntArray(10))
            fixture.setWord(profile.config.battlerControllerFuncs, profile.config.handleInputChooseAction + 1)
            // Normalize every other readiness field while retaining the fixture's real scripted return.
            assertEquals(Native.UNKNOWN, reader.sample())
            fixture.setWord(profile.config.gMain + 8, ordinaryReturn)
            assertEquals(Native(Menu.ACTION, Menu.ACTION, 0, 4), reader.sample())
            fixture.shortAt = profile.config.gMain
            assertEquals(Native.UNKNOWN, reader.sample())
        }
    }

    @Test fun ordinarySetupRequiresTheExpectedThumbStoreAndReturnLiteral() {
        val profile = Profile.EMERALD
        val address = profile.ordinarySetup
        val rom = mutableMapOf<Long, Byte>()
        fun write(at: Long, value: Long, count: Int) {
            for (i in 0 until count) rom[at + i] = (value ushr (8 * i)).toByte()
        }
        val opcodes = listOf(0x4914L, 0x4814L, 0x6088L, 0x4C14L, 0x2000L, 0x6020L)
        for ((i, op) in opcodes.withIndex()) write(address + 2 * i, op, 2)
        val firstLiteral = ((address + 4) and 3L.inv()) + 0x14 * 4
        write(firstLiteral, 0x030022C0, 4)
        write(firstLiteral + 4, 0x080B0AF9, 4)
        write(firstLiteral + 8, 0x02022FEC, 4)
        val read = object : MemoryReader {
            override fun readCoreMemory(addr: Long, size: Int) =
                ByteArray(size) { i -> checkNotNull(rom[addr + i]) }
        }
        assertTrue(NativeBattleReader.ordinarySetupMatches(read, profile))
        write(firstLiteral + 4, 0x080B0B6D, 4)
        assertFalse(NativeBattleReader.ordinarySetupMatches(read, profile))
        write(firstLiteral + 4, 0x080B0AF9, 4)
        write(address + 4, 0x6080, 2)
        assertFalse(NativeBattleReader.ordinarySetupMatches(read, profile))
    }

    @Test fun periodicFieldBridgeLeavesBothNativeMenusOwnedByThePointer() {
        for ((profile, directory) in listOf(
            Profile.FIRERED_REV0 to "firered_randomized_rev0_battle",
            Profile.EMERALD to "emerald_shiny_battle",
        )) for (dock in listOf(512, 640)) {
            val rig = PhysicalInputRig(profile, directory, dock)
            rig.qualify()
            rig.press(1)
            rig.tickAcrossPoll()
            rig.release()
            rig.neutral(5)
            assertEquals(listOf(A), rig.edges())
            assertEquals(Menu.MOVE, rig.reader.sample().ready())

            // While MOVE is visible, another due poll must neither call the cartridge nor steal Back.
            rig.masks.clear()
            rig.press(2)
            rig.tickAcrossPoll()
            rig.release()
            rig.neutral(5)
            assertEquals(listOf(B), rig.edges())
            assertEquals(Menu.ACTION, rig.reader.sample().ready())

            rig.masks.clear()
            rig.press(1)
            var foregroundExecuted = false
            rig.session.requestField(true) { _, busy -> foregroundExecuted = busy }
            rig.neutral(1)
            assertTrue(foregroundExecuted)
            rig.release()
            rig.neutral(5)
            assertTrue("a zero-mask foreground reservation cancelled the old press", rig.edges().isEmpty())

            rig.press(1)
            rig.step(LEFT) // Real GbaInput touch channel supplies ordinary manual direction.
            rig.release()
            rig.neutral(5)
            assertEquals(listOf(LEFT), rig.edges())
            rig.masks.clear()
            rig.press(1)
            rig.release()
            rig.neutral(5)
            assertEquals(listOf(A), rig.edges())
        }
    }

    /** Real Android frame-input components with fixture-derived native menu snapshots. */
    private class PhysicalInputRig(private val profile: Profile, directory: String, dock: Int) {
        private val fixture = Fixture(directory)
        private val fade = profile.fade ?: FADE
        val reader = NativeBattleReader(fixture, profile, fade)
        private val input = GbaInput()
        private val trace = mutableListOf<String>()
        private val mouse = NativeBattleMouse { trace += it }
        val masks = mutableListOf<Int>()
        private val calls = object : GameCalls {
            override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int): Long = error("unsafe field call")
            override fun read(addr: Long, len: Int) = fixture.readCoreMemory(addr, len)
            override fun write(addr: Long, bytes: ByteArray): Boolean = error("unexpected write")
        }
        val session = GameSession(calls, { true }) { _, failure -> throw failure }
        private val fieldWidth = 1600f - dock
        private val plane = PointerLayout.Bounds(0f, 0f, fieldWidth, 640f)
        private val gamePoint = PointerLayout(PointerLayout.Bounds(0f, 0f, 1600f, 900f),
            PointerLayout.Bounds(fieldWidth, 0f, 1600f, 900f), emptyList(), emptyList(),
            false, false, plane, plane, dock.toLong()).hit(fieldWidth * 0.60f, 512f).pixel()
        private var frame = 40L
        private var gesture = 0L
        private var destination: Menu? = null
        private var busyFrames = 0
        private val transform = dock.toLong()

        init {
            fixture.set(profile.config.battleTypeFlags, 4, 0, 0, 0)
            fixture.setWord(profile.config.gMain + 8, profile.ordinaryReturn)
            fixture.set(fade, *IntArray(10))
            showMenu(Menu.ACTION)
            mouse.session(true)
            mouse.layout(1, dock.toLong())
            neutral(2)
        }

        private fun showMenu(menu: Menu) {
            val move = menu == Menu.MOVE
            fixture.set(profile.bg0y, if (move) 64 else 160, if (move) 1 else 0)
            fixture.set(profile.exec, 1, 0, 0, 0)
            val cfg = profile.config
            fixture.setWord(cfg.battlerControllerFuncs,
                (if (move) cfg.handleInputChooseMove else cfg.handleInputChooseAction) + 1)
            fixture.set(profile.commandBuffer, if (move) 20 else 18, 0, 0, 0, 33, 0, 45, 0, 0, 0, 0, 0)
            fixture.set(if (move) cfg.moveCursor else cfg.actionCursor, 0)
        }

        fun qualify() {
            step(A)
            step()
            step(B)
            neutral(3)
            step(B)
            neutral(4)
            assertTrue(trace.any { "enabled=true" in it })
            masks.clear()
        }

        fun press(button: Int) {
            val point = checkNotNull(gamePoint)
            mouse.down(++gesture, button, point.x().toFloat(), point.y().toFloat(), transform, frame * 10)
        }

        fun release() {
            val point = checkNotNull(gamePoint)
            mouse.up(gesture, point.x().toFloat(), point.y().toFloat(), transform, frame * 10)
        }

        fun tickAcrossPoll() {
            neutral(29)
            session.requestFieldMaintenance { error("maintenance must be deferred in battle") }
            neutral(3)
            assertFalse(session.hasQueuedFieldWork())
        }

        fun edges() = masks.filter { it != 0 }

        fun neutral(count: Int) = repeat(count) { step() }

        fun step(manual: Int = 0) {
            input.setTouchBits(manual)
            val native = reader.sample()
            mouse.observe(frame, native)
            val reserved = session.hasQueuedFieldWork()
            var applied = 0
            input.sampleMouseInput { stamp ->
                input.withMouseInput(stamp) { idle, manualOwner ->
                    if (reserved) mouse.interrupt("upstream-gate")
                    mouse.commit(frame, input.mask, !reserved && idle, !reserved && manualOwner) { applied = it }
                }
            }
            masks += applied
            val next = destination
            if (next != null) {
                if (busyFrames-- == 0) {
                    showMenu(next)
                    destination = null
                }
            } else if ((native.ready() == Menu.ACTION && applied == A) ||
                (native.ready() == Menu.MOVE && applied == B)) {
                destination = if (native.ready() == Menu.ACTION) Menu.MOVE else Menu.ACTION
                busyFrames = 1
                fixture.set(profile.exec, 0, 0, 0, 0)
            }
            val sample = mouse.applied(frame, frame + 1, reader.sample())
            frame++
            if (sample != null) mouse.show(Picture(sample, transform, frame * 10))
            session.serviceField(true, false)
            session.observeField(false, -1, BATTLE_INPUT_ACTION_SELECT, false)
            session.serviceFieldMaintenance(liveField = false)
        }
    }

    private companion object {
        const val FADE = 0x0203FF00L
    }
}

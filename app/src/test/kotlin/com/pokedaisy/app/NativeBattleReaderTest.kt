package com.pokedaisy.app

import com.pokedaisy.app.companion.data.MemoryReader
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

    private companion object {
        const val FADE = 0x0203FF00L
    }
}

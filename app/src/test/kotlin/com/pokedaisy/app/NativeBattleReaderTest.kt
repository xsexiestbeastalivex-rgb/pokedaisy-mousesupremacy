package com.pokedaisy.app

import com.pokedaisy.app.companion.data.MemoryReader
import com.pokedaisy.app.mouse.NativeBattleMouse.Menu
import com.pokedaisy.app.mouse.NativeBattleMouse.Native
import org.junit.Assert.assertEquals
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

        fun ordinary() {
            set(0x02022B4C, 4, 0, 0, 0)
            set(FADE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
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

    @Test fun revisionZeroAndEmeraldUseTheirOwnBattleReadinessConfig() {
        for ((profile, directory) in listOf(
            Profile.FIRERED_REV0 to "firered_randomized_rev0_battle",
            Profile.EMERALD to "emerald_shiny_battle",
        )) {
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
            val handler = profile.config.handleInputChooseAction + 1
            fixture.set(profile.config.battlerControllerFuncs,
                *(0..3).map { (handler ushr (it * 8)).toInt() and 255 }.toIntArray())
            assertEquals(Native(Menu.ACTION, Menu.ACTION, 0, 4), reader.sample())
        }
    }

    private companion object {
        const val FADE = 0x0203FF00L
    }
}

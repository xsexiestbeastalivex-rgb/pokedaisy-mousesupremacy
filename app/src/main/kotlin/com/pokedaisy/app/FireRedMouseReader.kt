package com.pokedaisy.app

import com.pokedaisy.app.companion.data.InProcessReader
import com.pokedaisy.app.companion.data.MemoryReader
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV1
import com.pokedaisy.app.mouse.FireRedBattleMouse.Menu
import com.pokedaisy.app.mouse.FireRedBattleMouse.Native
import java.io.File
import java.security.MessageDigest

/** Read only, on the emulation thread, between mGBA frames. */
internal class FireRedMouseReader(private val read: MemoryReader, private val paletteFade: Long) {
    private val cfg = NATIVE_FIRERED_REV1

    fun frame(): Long = runCatching { read.u32(cfg.gMainVblankCtr) }.getOrDefault(-1)

    fun sample(): Native = runCatching { readBattle() }.getOrDefault(Native.UNKNOWN)

    private fun readBattle(): Native {
        if (read.u8(cfg.gMainInBattle) and 2 == 0) return Native.UNKNOWN
        // IS_MASTER can be set in non-link setup. All special, scripted and trainer flags are excluded.
        if (read.u32(cfg.battleTypeFlags) !in WILD_FLAGS ||
            read.u8(cfg.battlersCount) != 2 ||
            !read.bytes(cfg.battlerPositions, 2).contentEquals(PLAYER_AND_FOE)) return Native.UNKNOWN
        if (read.u32(cfg.gMain) != BATTLE_MAIN_1 || read.u32(cfg.gMain + 4) != BATTLE_MAIN_2 ||
            read.u32(cfg.gMain + 12) != BATTLE_VBLANK) return Native.UNKNOWN

        if (read.u16(BATTLE_BG0_Y - 2) != 0) return Native.UNKNOWN
        val visible = when (read.u16(BATTLE_BG0_Y)) {
            160 -> Menu.ACTION
            320 -> Menu.MOVE
            else -> null
        }
        val blocked = Native(visible, null, -1, 0)
        if (visible == null || read.u32(BATTLE_EXEC) != 1L) return blocked
        val expectedHandler = if (visible == Menu.ACTION) cfg.handleInputChooseAction else cfg.handleInputChooseMove
        val controllers = read.bytes(cfg.battlerControllerFuncs, 16)
        if (controllers.le32(0) != expectedHandler + 1 ||
            (4..12 step 4).any { off ->
                val ptr = controllers.le32(off) and 1L.inv()
                ptr == cfg.handleInputChooseAction || ptr == cfg.handleInputChooseMove ||
                    ptr == cfg.handleInputChooseTarget
            }) return blocked
        val command = read.bytes(PLAYER_BUFFER, 12)
        if (command[0].byte() != (if (visible == Menu.ACTION) 18 else 20)) return blocked
        val fade = read.bytes(paletteFade, 10)
        val amount = fade.le16(4) ushr 6 and 31
        val targetAmount = fade.le16(4) ushr 11 and 31
        val activeFade = fade[7].byte() and 0x80 != 0
        // MOVE hover schedules a zero-intensity OBJ refresh. Block real fades and nonnormal active modes.
        if (amount != 0 || activeFade && (targetAmount != 0 || fade[9].byte() and 3 != 0)) return blocked
        val cursor = read.u8(if (visible == Menu.ACTION) cfg.actionCursor else cfg.moveCursor)
        val slots = if (visible == Menu.ACTION) 4 else moveSlots(command)
        return if (cursor in 0 until slots) Native(visible, visible, cursor, slots) else blocked
    }

    private fun moveSlots(command: ByteArray): Int {
        val moves = (4..10 step 2).map { command.le16(it) }
        val used = moves.takeWhile { it in 1..354 }.size
        return if (moves.drop(used).all { it == 0 }) used else 0
    }

    companion object {
        private const val ROM_BASE = 0x08000000L
        private const val ROM_SIZE = 0x1000000L
        private const val SHA1 = "dd5945db9b930750cb39d00c84da8571feebf417"
        private val WILD_FLAGS = setOf(0L, 4L)
        private val PLAYER_AND_FOE = byteArrayOf(0, 1)
        // Exact rev 1: pret c75f352 source layout, checked against the upstream scripted fixture.
        private const val BATTLE_BG0_Y = 0x02022976L
        private const val BATTLE_EXEC = 0x02023BC8L
        private const val PLAYER_BUFFER = 0x02022BC4L
        private const val BATTLE_MAIN_1 = 0x080123F9L
        private const val BATTLE_MAIN_2 = 0x08011115L
        private const val BATTLE_VBLANK = 0x08011709L

        fun bind(rom: File): FireRedMouseReader? = runCatching {
            // pk_api_read serves GBA cart addresses from the core session's unmodified ROM mapping.
            // Hash the entire loaded payload, including bytes masked by the companion's compatibility hash.
            val read = InProcessReader
            if (!rom.extension.equals("gba", ignoreCase = true) || rom.length() != ROM_SIZE ||
                MgbaCore.pkRomSize() != ROM_SIZE ||
                read.bytes(ROM_BASE + 0xAC, 4).decodeToString() != "BPRE" ||
                read.u8(ROM_BASE + 0xBC) != 1) return null
            val digest = MessageDigest.getInstance("SHA-1")
            val chunk = ByteArray(64 * 1024)
            for (off in 0L until ROM_SIZE step chunk.size.toLong()) {
                if (!MgbaCore.pkReadInto(ROM_BASE + off, chunk)) return null
                digest.update(chunk)
            }
            if (digest.digest().joinToString("") { "%02x".format(it.byte()) } != SHA1) return null
            // Same exact-ROM Thumb literal used by BattleBall.find for CompleteWhenChoseItem.
            val cfg = NATIVE_FIRERED_REV1
            val at = cfg.completeWhenChoseItem + 0x0C
            val instruction = read.u16(at)
            if (instruction and 0xF800 != 0x4800) return null
            val palette = read.u32(((at + 4) and 3L.inv()) + (instruction and 0xFF) * 4)
            if (palette !in 0x02000000L until 0x0203FFF8L) return null
            FireRedMouseReader(read, palette)
        }.getOrNull()

        private fun Byte.byte() = toInt() and 0xFF
        private fun ByteArray.le16(i: Int) = this[i].byte() or (this[i + 1].byte() shl 8)
        private fun ByteArray.le32(i: Int) = le16(i).toLong() or (le16(i + 2).toLong() shl 16)
        private fun MemoryReader.bytes(addr: Long, size: Int) = readCoreMemory(addr, size).also { check(it.size == size) }
        private fun MemoryReader.u8(addr: Long) = bytes(addr, 1)[0].byte()
        private fun MemoryReader.u16(addr: Long) = bytes(addr, 2).le16(0)
        private fun MemoryReader.u32(addr: Long) = bytes(addr, 4).le32(0)
    }
}

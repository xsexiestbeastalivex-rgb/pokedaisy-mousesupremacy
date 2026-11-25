package com.pokedaisy.app

import com.pokedaisy.app.companion.data.InProcessReader
import com.pokedaisy.app.companion.data.MemoryReader
import com.pokedaisy.app.companion.data.NATIVE_EMERALD_RETAIL
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV0_RETAIL
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV1
import com.pokedaisy.app.companion.data.NativeConfig
import com.pokedaisy.app.mouse.NativeBattleMouse.Menu
import com.pokedaisy.app.mouse.NativeBattleMouse.Native
import java.io.File
import java.security.MessageDigest

/** Read only, on the emulation thread, between mGBA frames. */
internal class NativeBattleReader(
    private val read: MemoryReader,
    val profile: Profile,
    private val paletteFade: Long,
) {
    private val cfg = profile.config

    // FireRed rev 0/1 share the battle RAM layout. Callback addresses are revision-specific.
    internal enum class Profile(
        val code: String, val revision: Int, val sha1: String, val config: NativeConfig,
        val bg0y: Long, val exec: Long, val commandBuffer: Long,
        val main1: Long, val main2: Long, val vblank: Long,
        val ordinarySetup: Long, val ordinaryReturn: Long, val fade: Long? = null,
    ) {
        FIRERED_REV0("BPRE", 0, "41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc", NATIVE_FIRERED_REV0_RETAIL,
            0x02022976L, 0x02023BC8L, 0x02022BC4L, 0x080123E5L, 0x08011101L, 0x080116F5L,
            0x0807F756L, 0x0807FB41L),
        FIRERED_REV1("BPRE", 1, "dd5945db9b930750cb39d00c84da8571feebf417", NATIVE_FIRERED_REV1,
            0x02022976L, 0x02023BC8L, 0x02022BC4L, 0x080123F9L, 0x08011115L, 0x08011709L,
            0x0807F76AL, 0x0807FB55L),
        // Byte-matched Emerald symbols from pk_coop_emerald.h and the upstream RAM fixture.
        EMERALD("BPEE", 0, "f3ae088181bf583e55daf962a92bb46f4f1d07b7", NATIVE_EMERALD_RETAIL,
            0x02022E16L, 0x02024068L, 0x02023064L, 0x08039EF1L, 0x08038421L, 0x08038A29L,
            0x080B06CAL, 0x080B0AF9L, 0x02037FD4L),
    }

    fun frame(): Long = runCatching { read.u32(cfg.gMainVblankCtr) }.getOrDefault(-1)

    fun sample(): Native = runCatching { readBattle() }.getOrDefault(Native.UNKNOWN)

    private fun readBattle(): Native {
        if (read.u8(cfg.gMainInBattle) and 2 == 0) return Native.UNKNOWN
        // IS_MASTER can be set in non-link setup. Emerald scripted encounters can share these flags.
        // gMain.savedCallback carries the return route installed by the exact encounter setup.
        if (read.u32(cfg.battleTypeFlags) !in WILD_FLAGS ||
            read.u8(cfg.battlersCount) != 2 ||
            !read.bytes(cfg.battlerPositions, 2).contentEquals(PLAYER_AND_FOE)) return Native.UNKNOWN
        val callbacks = read.bytes(cfg.gMain, 16)
        if (callbacks.le32(0) != profile.main1 || callbacks.le32(4) != profile.main2 ||
            callbacks.le32(8) != profile.ordinaryReturn || callbacks.le32(12) != profile.vblank) return Native.UNKNOWN

        if (read.u16(profile.bg0y - 2) != 0) return Native.UNKNOWN
        val visible = when (read.u16(profile.bg0y)) {
            160 -> Menu.ACTION
            320 -> Menu.MOVE
            else -> null
        }
        val blocked = Native(visible, null, -1, 0)
        if (visible == null || read.u32(profile.exec) != 1L) return blocked
        val expectedHandler = if (visible == Menu.ACTION) cfg.handleInputChooseAction else cfg.handleInputChooseMove
        val controllers = read.bytes(cfg.battlerControllerFuncs, 16)
        if (controllers.le32(0) != expectedHandler + 1 ||
            (4..12 step 4).any { off ->
                val ptr = controllers.le32(off) and 1L.inv()
                ptr == cfg.handleInputChooseAction || ptr == cfg.handleInputChooseMove ||
                    ptr == cfg.handleInputChooseTarget
            }) return blocked
        val command = read.bytes(profile.commandBuffer, 12)
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
        private val WILD_FLAGS = setOf(0L, 4L)
        private val PLAYER_AND_FOE = byteArrayOf(0, 1)

        internal fun exactProfile(code: String, revision: Int, sha1: String): Profile? =
            Profile.entries.firstOrNull { it.code == code && it.revision == revision && it.sha1 == sha1 }

        fun bind(rom: File): NativeBattleReader? = runCatching {
            // pk_api_read serves GBA cart addresses from the core session's unmodified ROM mapping.
            // Hash the entire loaded payload, including bytes masked by the companion's compatibility hash.
            val read = InProcessReader
            if (!rom.extension.equals("gba", ignoreCase = true) || rom.length() != ROM_SIZE ||
                MgbaCore.pkRomSize() != ROM_SIZE) return null
            val code = read.bytes(ROM_BASE + 0xAC, 4).decodeToString()
            val revision = read.u8(ROM_BASE + 0xBC)
            if (Profile.entries.none { it.code == code && it.revision == revision }) return null
            val digest = MessageDigest.getInstance("SHA-1")
            val chunk = ByteArray(64 * 1024)
            for (off in 0L until ROM_SIZE step chunk.size.toLong()) {
                if (!MgbaCore.pkReadInto(ROM_BASE + off, chunk)) return null
                digest.update(chunk)
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.byte()) }
            val profile = exactProfile(code, revision, hash) ?: return null
            if (!ordinarySetupMatches(read, profile)) return null
            val palette = profile.fade ?: fireRedPaletteAddress(read, profile.config) ?: return null
            NativeBattleReader(read, profile, palette)
        }.getOrNull()

        // Corroborate the profile against the exact loaded ROM's DoStandardWildBattle:
        // load gMain, load CB2_EndWildBattle, store savedCallback, then clear gBattleTypeFlags.
        internal fun ordinarySetupMatches(read: MemoryReader, profile: Profile): Boolean = runCatching {
            val at = profile.ordinarySetup
            val code = read.bytes(at, 12)
            val flagsLoad = code.le16(6)
            val flagsRegister = flagsLoad ushr 8 and 7
            code.le16(0) and 0xFF00 == 0x4900 && code.le16(2) and 0xFF00 == 0x4800 &&
                code.le16(4) == 0x6088 && flagsLoad and 0xF800 == 0x4800 && code.le16(8) == 0x2000 &&
                code.le16(10) == (0x6000 or (flagsRegister shl 3)) &&
                read.thumbLiteral(at, code.le16(0)) == profile.config.gMain &&
                read.thumbLiteral(at + 2, code.le16(2)) == profile.ordinaryReturn &&
                read.thumbLiteral(at + 6, flagsLoad) == profile.config.battleTypeFlags
        }.getOrDefault(false)

        private fun MemoryReader.thumbLiteral(at: Long, instruction: Int) =
            u32(((at + 4) and 3L.inv()) + (instruction and 0xFF) * 4)

        // The exact-ROM Thumb literal that upstream BattleBall.find uses for CompleteWhenChoseItem.
        private fun fireRedPaletteAddress(read: MemoryReader, cfg: NativeConfig): Long? {
            val at = cfg.completeWhenChoseItem + 0x0C
            val instruction = read.u16(at)
            if (instruction and 0xF800 != 0x4800) return null
            val palette = read.thumbLiteral(at, instruction)
            return palette.takeIf { it in 0x02000000L..0x0203FFF6L }
        }

        private fun Byte.byte() = toInt() and 0xFF
        private fun ByteArray.le16(i: Int) = this[i].byte() or (this[i + 1].byte() shl 8)
        private fun ByteArray.le32(i: Int) = le16(i).toLong() or (le16(i + 2).toLong() shl 16)
        private fun MemoryReader.bytes(addr: Long, size: Int) = readCoreMemory(addr, size).also { check(it.size == size) }
        private fun MemoryReader.u8(addr: Long) = bytes(addr, 1)[0].byte()
        private fun MemoryReader.u16(addr: Long) = bytes(addr, 2).le16(0)
        private fun MemoryReader.u32(addr: Long) = bytes(addr, 4).le32(0)
    }
}

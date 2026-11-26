package com.pokedaisy.app

import com.pokedaisy.app.companion.data.CoreKey
import com.pokedaisy.app.companion.data.KeyCodes
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * Combines canonical key events, axes and touch into a live game key bitmask. Face /
 * shoulder / menu buttons are remappable via [setControls] (GbaControls); the
 * D-pad and analog stick are fixed.
 */
class GbaInput : Hotkeys.GameKeys, ScriptInput {
    private val lock = SynchronizedObject()
    @Volatile
    var mask: Int = 0
        private set

    @Volatile private var buttonBits = 0   // from key events: the bits of every key in heldKeys
    private var turboBits = 0    // TURBO A / B held: pulsed by the engine ([turboMask])
    private val turboKeys = HashMap<Int, Int>()
    // Per key, not per bit: START and X are two keys for one bit, and
    // letting go of one mustn't release the other.
    private val heldKeys = HashMap<Int, Int>()
    @Volatile private var hatBits = 0      // from D-pad HAT / analog axes
    @Volatile private var touchBits = 0    // from the on-screen TouchControlsView (any thread: TouchInputWindows)
    @Volatile private var scriptBits = 0   // synthetic presses (BattleInputController)
    @Volatile private var scriptExclusive = false
    /**
     * The QUICK MENU's TURBO: these GBA buttons (A) mash while the player holds them, as a TURBO A key would, until
     * it's switched off. Kept apart from the steady bits ([recompute]) and pulsed by the engine ([turboMask]).
     */
    @Volatile var turboLatch = 0
        set(v) = synchronized(lock) { if (field != v) mouseInputSerial++; field = v; recompute() }
    @Volatile private var latchedHeld = 0
    private var mouseInputSerial = 0L

    data class MouseInput(val serial: Long, val reserved: Boolean, val manual: Boolean)

    private fun mouseInputLocked() = MouseInput(mouseInputSerial,
        heldKeys.isNotEmpty() || turboKeys.isNotEmpty() || hatBits != 0 || touchBits != 0 ||
            touchTurboBits != 0 || scriptExclusive || scriptBits != 0 || pressBits != 0 || tapFrames != 0,
        (buttonBits or hatBits or touchBits) != 0 && !scriptExclusive && scriptBits == 0 &&
            pressBits == 0 && tapFrames == 0 && turboMask == 0)

    fun <T> sampleMouseInput(read: (MouseInput) -> T): T = synchronized(lock) { read(mouseInputLocked()) }

    data class SourceSnapshot(
        val serial: Long, val key: Int, val axes: Int, val touch: Int, val script: Int,
        val exclusive: Boolean, val press: Int, val queuedTap: Int, val turboHeld: Int,
        val keyPending: Boolean, val tapPending: Boolean,
    )

    /** The Android diagnostic sampler reads these under the same lock as the existing tap consumption. */
    fun mouseSources() = synchronized(lock) {
        SourceSnapshot(mouseInputSerial, buttonBits, hatBits, touchBits, scriptBits,
            scriptExclusive, pressBits, tapBits, turboBits or touchTurboBits or latchedHeld,
            heldKeys.isNotEmpty() || turboKeys.isNotEmpty(), tapFrames > 0)
    }

    fun withMouseInput(sample: MouseInput, commit: (Boolean, Boolean) -> Unit) = synchronized(lock) {
        val now = mouseInputLocked()
        val unchanged = now.serial == sample.serial
        commit(unchanged && !sample.reserved && !now.reserved, unchanged && sample.manual && now.manual)
    }

    @Volatile
    private var gbaControls: Map<Int, Int> = DEFAULT_CONTROLS
    @Volatile
    private var dsControls: Map<Int, Int> = DEFAULT_CONTROLS

    fun setControls(maps: GbaControls.KeyMaps) { gbaControls = maps.gba; dsControls = maps.ds }

    /** One map for every game (tests). */
    fun setControls(map: Map<Int, Int>) { gbaControls = map; dsControls = map }

    /** A DS game runs: the keys go by GAME BUTTONS' DS map (X / Y), else by the GBA / Game Boy one (START 2, SELECT 2,
     * TO COMPANION). */
    @Volatile var ds = false

    private val controls get() = if (ds) dsControls else gbaControls

    /** @return true if the event was consumed. */
    override fun onKey(keyCode: Int, down: Boolean): Boolean = synchronized(lock) {
        if (!down) {
            val wasHeld = heldKeys.remove(keyCode) != null
            val wasTurbo = turboKeys.remove(keyCode) != null
            if (wasHeld || wasTurbo) {
                mouseInputSerial++
                updateButtons()
            }
            return@synchronized wasHeld || wasTurbo || keyToBit(keyCode) != null
        }
        val bit = keyToBit(keyCode) ?: return@synchronized false
        mouseInputSerial++
        if (bit and GbaControls.TURBO != 0) {
            turboKeys[keyCode] = bit and (GbaControls.TURBO or GbaControls.COMPANION).inv()
            turboBits = turboKeys.values.fold(0) { acc, b -> acc or b }
            return@synchronized true
        }
        // A TO COMPANION key with no button of its own is still the player's: it's taken, the game gets nothing.
        heldKeys[keyCode] = bit and GbaControls.COMPANION.inv()
        updateButtons()
        true
    }

    /** Atomically withdraw the routed keys and joystick mask. Touch and script input keep their owners. */
    fun retireRoutedKeys(keys: Set<Int>, axes: Int? = null, clearTap: Boolean = false) = synchronized(lock) {
        mouseInputSerial++
        for (key in keys) {
            heldKeys.remove(key)
            turboKeys.remove(key)
        }
        if (axes != null) hatBits = axes
        if (clearTap) {
            tapFrames = 0
            tapBits = 0
        }
        updateButtons()
    }

    private fun updateButtons() {
        buttonBits = heldKeys.values.fold(0) { acc, bit -> acc or bit }
        turboBits = turboKeys.values.fold(0) { acc, bit -> acc or bit }
        recompute()
    }

    /** The GBA button [keyCode] is bound to (D-pad, or the player's GAME BUTTONS; a TURBO key counts as its button),
     * null = none. The companion's window reads keys through this, so it follows the player's bindings. */
    override fun buttonFor(keyCode: Int): Int? = keyToBit(keyCode)?.let { it and (GbaControls.TURBO or GbaControls.COMPANION).inv() }

    /** [keyCode] is GAME BUTTONS' TO COMPANION (a GBA / Game Boy game), whatever else it presses in the game. */
    fun isCompanion(keyCode: Int): Boolean = !ds && (gbaControls[keyCode] ?: 0) and GbaControls.COMPANION != 0

    /** The player holds a game button (a key, the stick / D-pad or the touch controls). */
    override val holdsButtons: Boolean get() = synchronized(lock) {
        heldKeys.isNotEmpty() || turboKeys.isNotEmpty() || hatBits != 0 || touchBits != 0
    }

    /** A press that was held back from the game (SELECT / START of a hotkey chord, [Hotkeys.gameKey]) and let go:
     * pressed for [TAP_FRAMES] game frames from now ([tapMask]). */
    @Volatile private var tapBits = 0
    @Volatile private var tapFrames = 0

    override fun tap(keyCode: Int) = synchronized(lock) {
        tapBits = buttonFor(keyCode) ?: return@synchronized
        mouseInputSerial++
        tapFrames = TAP_FRAMES
    }

    /** [tap]'s button for this frame, counting it down; the engine calls it once per game frame. None while a script
     * steers, like [turboMask]. */
    fun tapMask(): Int = synchronized(lock) {
        if (tapFrames <= 0) return@synchronized 0
        val bits = tapBits
        if (--tapFrames == 0) tapBits = 0
        if (scriptExclusive) 0 else bits
    }

    /** Lets go of every key the player holds (not a script's): the companion's window took the buttons, or a rebind
     *  the keys' releases. */
    fun releaseAll() = synchronized(lock) {
        mouseInputSerial++
        tapFrames = 0
        tapBits = 0
        heldKeys.clear()
        turboKeys.clear()
        buttonBits = 0
        turboBits = 0
        hatBits = 0
        touchBits = 0
        touchTurboBits = 0
        recompute()
    }

    /** The TURBO A / B buttons held (keys or the touch controls'), which the engine presses every other 2 frames;
     * none while a script steers. */
    val turboMask: Int get() = if (scriptExclusive) 0 else turboBits or touchTurboBits or latchedHeld

    @Volatile private var touchTurboBits = 0

    /** The touch controls' GBA buttons held ([bits]) and their TURBO A / B ([turbo], pulsed like [turboMask]). */
    fun setTouchBits(bits: Int, turbo: Int = 0) = synchronized(lock) {
        if (touchBits != bits || touchTurboBits != turbo) mouseInputSerial++
        touchBits = bits
        touchTurboBits = turbo
        recompute()
    }

    /**
     * Synthetic presses from the companion's battle controls. While
     * [exclusive] (an automated sequence is running) the player's own keys -
     * buttons, D-pad, touch controls - are ignored, so they can't knock the
     * sequence off course; they count again the moment it ends.
     */
    override fun setScript(bits: Int, exclusive: Boolean) = synchronized(lock) {
        if (scriptBits != bits || scriptExclusive != exclusive) mouseInputSerial++
        scriptBits = bits
        scriptExclusive = exclusive
        recompute()
    }

    /** One-off presses for the game (the QUICK MENU's SELECT for a bike or a rod): pressed on top of everything else,
     * apart from [setScript]'s channel, which the battle controls send every frame (they'd wipe it). */
    @Volatile private var pressBits = 0

    fun setPress(bits: Int) = synchronized(lock) {
        if (pressBits != bits) mouseInputSerial++
        pressBits = bits
        recompute()
    }

    /** Fixed D-pad / analog directions, supplied by the platform's event adapter. */
    fun setAxes(bits: Int) = synchronized(lock) {
        if (hatBits != bits) mouseInputSerial++
        hatBits = bits
        recompute()
    }

    // Synchronized: the touch controls' input windows set their bits from a thread of their own (TouchInputWindows), the
    // battle scripts from the emu thread, the keys from the UI thread - each recompute must see the others' latest.
    private fun recompute() = synchronized(lock) {
        val held = buttonBits or hatBits or touchBits
        latchedHeld = held and turboLatch
        mask = (if (scriptExclusive) scriptBits else (held and turboLatch.inv()) or scriptBits) or pressBits
    }

    private fun keyToBit(keyCode: Int): Int? = when (keyCode) {
        KeyCodes.DPAD_UP -> CoreKey.UP
        KeyCodes.DPAD_DOWN -> CoreKey.DOWN
        KeyCodes.DPAD_LEFT -> CoreKey.LEFT
        KeyCodes.DPAD_RIGHT -> CoreKey.RIGHT
        // Some controllers (8BitDo, GameSir in a few modes) send a diagonal as one key.
        KeyCodes.DPAD_UP_LEFT -> CoreKey.UP or CoreKey.LEFT
        KeyCodes.DPAD_UP_RIGHT -> CoreKey.UP or CoreKey.RIGHT
        KeyCodes.DPAD_DOWN_LEFT -> CoreKey.DOWN or CoreKey.LEFT
        KeyCodes.DPAD_DOWN_RIGHT -> CoreKey.DOWN or CoreKey.RIGHT
        else -> controls[keyCode]   // remappable face/shoulder/menu buttons
    }

    companion object {
        private const val DEAD = 0.5f
        /** A held-back tap's length: the Game Boy games miss shorter presses outside their menus (3 frames), and
         * Crystal Clear's menus want 6. */
        const val TAP_FRAMES = 6

        /** D-pad HAT takes priority over the stick on each axis. Negative Y points up. */
        fun axisBits(stickX: Float, stickY: Float, hatX: Float = 0f, hatY: Float = 0f): Int {
            val x = if (abs(hatX) > DEAD) hatX else stickX
            val y = if (abs(hatY) > DEAD) hatY else stickY
            var bits = 0
            if (x < -DEAD) bits = bits or CoreKey.LEFT
            if (x > DEAD) bits = bits or CoreKey.RIGHT
            if (y < -DEAD) bits = bits or CoreKey.UP
            if (y > DEAD) bits = bits or CoreKey.DOWN
            return bits
        }

        private val DEFAULT_CONTROLS = mapOf(
            KeyCodes.BUTTON_A to CoreKey.A, KeyCodes.Z to CoreKey.A,
            KeyCodes.BUTTON_B to CoreKey.B, KeyCodes.X to CoreKey.B,
            KeyCodes.BUTTON_L1 to CoreKey.L, KeyCodes.A to CoreKey.L,
            KeyCodes.BUTTON_R1 to CoreKey.R, KeyCodes.S to CoreKey.R,
            KeyCodes.BUTTON_START to CoreKey.START, KeyCodes.ENTER to CoreKey.START,
            KeyCodes.BUTTON_SELECT to CoreKey.SELECT,
            KeyCodes.BACKSLASH to CoreKey.SELECT, KeyCodes.SHIFT_RIGHT to CoreKey.SELECT,
        )
    }
}

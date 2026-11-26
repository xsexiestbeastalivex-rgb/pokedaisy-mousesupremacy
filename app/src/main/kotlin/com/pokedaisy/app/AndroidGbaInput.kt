package com.pokedaisy.app

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Android events end here; the mappings and input state live in the shared GbaInput. */
fun GbaInput.onMotion(event: MotionEvent): Boolean {
    setAxes(GbaInput.motionBits(event) ?: return false)
    return true
}

fun GbaInput.Companion.motionBits(event: MotionEvent): Int? {
    if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return null
    return axisBits(
        event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
        event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
    )
}

/** The Activity's single source for capture, analog key transitions, and manual takeover observation. */
internal data class ControllerTriggers(val left: Float, val right: Float) {
    val leftDown get() = left > 0.5f
    val rightDown get() = right > 0.5f

    fun hasManualInput(directionBits: Int?): Boolean = leftDown || rightDown || (directionBits ?: 0) != 0

    companion object {
        fun read(axis: (Int) -> Float) = ControllerTriggers(
            maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE)),
            maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS), axis(MotionEvent.AXIS_THROTTLE)),
        )
    }
}

/** Owns the Android inputs before they enter the logical hotkey and game key ports. UI thread. */
internal class AndroidHardwareInput(
    private val input: GbaInput,
    private val hotkeys: () -> Hotkeys?,
    private val notifyHotkey: (Hotkeys.Event) -> Unit,
    private val retireUiKeys: (Set<Int>) -> Unit = {},
) {
    enum class KeyPhase { PRESS, REPEAT, RELEASE, IGNORE }
    private enum class Source { DIGITAL, TRIGGER, TOUCH_SHORTCUT }
    private data class Owner(val device: Int, val source: Source)
    private class Stick {
        var fence = -1L
        var digitalFence = -1L
        var sampledAt = -1L
        var triggers = 0
        var reportedTriggers = 0
        var needsNeutral = 0
        var needsCenteredStick = false
        var gameAxes = 0
    }

    private val keys = mutableMapOf<Int, MutableMap<Owner, Long>>()
    private val sticks = mutableMapOf<Int, Stick>()
    private val detachedDevices = mutableSetOf<Int>()
    private var fence = -1L
    private var epoch = 0L

    fun tracks(key: Int) = key in keys

    fun digital(
        device: Int, key: Int, down: Boolean, repeat: Int, cancelled: Boolean,
        downTime: Long, eventTime: Long, touchShortcut: Boolean = false,
    ): KeyPhase {
        val owner = Owner(device, if (touchShortcut) Source.TOUCH_SHORTCUT else Source.DIGITAL)
        if (!touchShortcut && device in detachedDevices) return KeyPhase.IGNORE
        val since = if (touchShortcut) 0L else downTime
        val boundary = if (touchShortcut) -1L else maxOf(fence, sticks[device]?.digitalFence ?: -1L)
        if (down && (eventTime < boundary || since < boundary)) return KeyPhase.IGNORE
        val owners = keys[key]
        if (cancelled) {
            if (owners?.get(owner) == since) retire({ code, source -> code == key && source == owner })
            return KeyPhase.IGNORE
        }
        if (!down) {
            if (owners?.get(owner) != since) return KeyPhase.IGNORE
            return if (remove(key, owner)) KeyPhase.RELEASE else KeyPhase.IGNORE
        }
        if (repeat != 0) return if (owners?.get(owner) == since) KeyPhase.REPEAT else KeyPhase.IGNORE
        if (owners?.containsKey(owner) == true) return KeyPhase.IGNORE
        return if (add(key, owner, since)) KeyPhase.PRESS else KeyPhase.IGNORE
    }

    /** Only joystick MOVE can open a trigger. CANCEL coordinates are never a fresh sample. */
    fun joystick(
        device: Int, source: Int, action: Int, time: Long, triggers: ControllerTriggers,
        directions: Int, centered: Boolean = directions == 0, observe: ((Boolean) -> Unit)? = null,
        retired: (() -> Unit)? = null, edge: (Int, Boolean) -> Unit,
    ): Boolean {
        if (device in detachedDevices || source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) {
            observe?.invoke(false)
            return false
        }
        val stick = sticks.getOrPut(device) { Stick() }
        if (time < maxOf(fence, stick.fence, stick.sampledAt)) {
            observe?.invoke(false)
            return false
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            observe?.invoke(stick.triggers != 0 || stick.gameAxes != 0)
            fenceStick(stick, time)
            retire({ _, owner -> owner.device == device && owner.source == Source.TRIGGER })
            retired?.invoke()
            return false
        }
        if (action != MotionEvent.ACTION_MOVE) {
            observe?.invoke(false)
            return false
        }
        return input.withRoutedKeyReport {
        stick.sampledAt = time
        val held = (if (triggers.rightDown) RIGHT_TRIGGER else 0) or (if (triggers.leftDown) LEFT_TRIGGER else 0)
        stick.reportedTriggers = held
        stick.needsNeutral = stick.needsNeutral and held
        val active = held and stick.needsNeutral.inv()
        if (centered) stick.needsCenteredStick = false
        observe?.invoke(active != 0 || !stick.needsCenteredStick && directions != 0)
        val previous = stick.triggers
        val observed = epoch
        val owner = Owner(device, Source.TRIGGER)
        // Release channels first. A later press can open a menu and revoke the rest of this report.
        for ((bit, key) in TRIGGERS) {
            if (previous and bit != 0 && active and bit == 0) {
                stick.triggers = stick.triggers and bit.inv()
                if (remove(key, owner)) edge(key, false)
            }
        }
        for ((bit, key) in TRIGGERS) {
            if (observed != epoch) break
            if (active and bit != 0 && previous and bit == 0) {
                stick.triggers = stick.triggers or bit
                if (add(key, owner, time)) edge(key, true)
            }
        }
        observed == epoch && !stick.needsCenteredStick
        }
    }

    fun gameAxes(device: Int, bits: Int) {
        val stick = sticks[device] ?: return
        if (stick.gameAxes != bits) {
            stick.gameAxes = bits
            input.setAxes(axes())
        }
    }

    /** The game-to-modal route lost its buttons, but ordinary release can still end a held hotkey. */
    fun clearGameButtons() {
        epoch++
        for (stick in sticks.values) {
            stick.needsCenteredStick = true
            stick.gameAxes = 0
            // Quarantine any sibling press that had not reached its owner before this callback.
            // An admitted trigger still owns its key until the real release.
            stick.needsNeutral = stick.needsNeutral or (stick.reportedTriggers and stick.triggers.inv())
        }
        input.releaseAll()
    }

    fun deviceBoundary(device: Int, time: Long, detached: Boolean) {
        if (detached) {
            detachedDevices += device
            sticks.remove(device)
        } else {
            detachedDevices.remove(device)
            val stick = sticks.getOrPut(device) { Stick() }
            fenceStick(stick, time)
            stick.digitalFence = stick.fence
        }
        retire({ _, owner -> owner.device == device && owner.source != Source.TOUCH_SHORTCUT })
    }

    /** The Activity's device listener is paused while its window is away. Refresh its detach bookkeeping. */
    fun resumeDevices(deviceIds: IntArray) {
        for (id in deviceIds) if (detachedDevices.remove(id)) fenceStick(sticks.getOrPut(id) { Stick() }, fence)
    }

    fun windowBoundary(time: Long, remapping: Boolean = false) {
        fence = maxOf(fence, time)
        sticks.values.forEach { fenceStick(it, fence) }
        retire({ _, owner -> remapping || owner.source != Source.TOUCH_SHORTCUT }, clearTap = true)
    }

    fun hotkeyTakes(key: Int, down: Boolean): Boolean {
        val parser = hotkeys() ?: return false
        val observed = epoch
        return parser.gameKey(key, down, input, notifyHotkey) { observed == epoch }
    }

    fun playKey(key: Int, down: Boolean) = hotkeyTakes(key, down) || input.onKey(key, down)

    /** Every final release cleans the original admission, even if a modal or new mapping intercepted it. */
    fun finishRelease(key: Int) {
        if (tracks(key)) return
        input.onKey(key, false)
        hotkeys()?.retireKey(key)?.forEach(notifyHotkey)
        retireUiKeys(setOf(key))
    }

    fun letThrough() {
        hotkeys()?.letThrough()?.forEach { if (tracks(it)) input.onKey(it, true) }
    }

    private fun fenceStick(stick: Stick, time: Long) {
        stick.fence = maxOf(stick.fence, time)
        stick.triggers = 0
        stick.reportedTriggers = 0
        stick.needsNeutral = RIGHT_TRIGGER or LEFT_TRIGGER
        stick.needsCenteredStick = true
        stick.gameAxes = 0
    }

    private fun add(key: Int, owner: Owner, time: Long): Boolean {
        val owners = keys.getOrPut(key) { mutableMapOf() }
        val first = owners.isEmpty()
        owners[owner] = time
        return first
    }

    private fun remove(key: Int, owner: Owner): Boolean {
        val owners = keys[key] ?: return false
        if (owners.remove(owner) == null || owners.isNotEmpty()) return false
        keys.remove(key)
        return true
    }

    private fun axes() = sticks.values.fold(0) { bits, stick -> bits or stick.gameAxes }

    private fun retire(ownedBy: (Int, Owner) -> Boolean, clearTap: Boolean = false) {
        epoch++
        val lost = mutableSetOf<Int>()
        val iterator = keys.iterator()
        while (iterator.hasNext()) {
            val (key, owners) = iterator.next()
            owners.keys.removeAll { ownedBy(key, it) }
            if (owners.isEmpty()) {
                iterator.remove()
                lost += key
            }
        }
        input.retireRoutedKeys(lost, axes(), clearTap)
        val parser = hotkeys()
        if (parser != null) {
            lost.flatMap(parser::retireKey).forEach(notifyHotkey)
        }
        if (lost.isNotEmpty()) retireUiKeys(lost)
    }

    private companion object {
        const val RIGHT_TRIGGER = 1
        const val LEFT_TRIGGER = 2
        val TRIGGERS = listOf(RIGHT_TRIGGER to KeyEvent.KEYCODE_BUTTON_R2, LEFT_TRIGGER to KeyEvent.KEYCODE_BUTTON_L2)
    }
}

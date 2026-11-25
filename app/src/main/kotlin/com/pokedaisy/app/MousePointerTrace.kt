package com.pokedaisy.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.pokedaisy.mousesupremacy.pointer.DeviceAliases
import com.pokedaisy.mousesupremacy.pointer.DirectTouch
import com.pokedaisy.mousesupremacy.pointer.PointerLayout
import com.pokedaisy.mousesupremacy.pointer.PointerNormalizer
import com.pokedaisy.mousesupremacy.pointer.PointerRoute
import com.pokedaisy.mousesupremacy.pointer.PointerState
import com.pokedaisy.mousesupremacy.pointer.PointerState.Action
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Activity-thread observation only. Android continues to dispatch every original event. */
internal class MousePointerTrace private constructor(
    private val profile: Profile,
    private val slop: Float,
) {
    private enum class Profile(val marker: String) {
        ANDROID("ms_pointer_android"), WSA("ms_pointer_wsa"), DIRECT("ms_pointer_touch"),
    }

    internal data class Scene(val layout: PointerLayout, val drawnFrame: Long?)
    private val input = PointerNormalizer(SLOTS)
    private val devices = DeviceAliases(SLOTS)
    private val direct = DirectTouch()
    private val route = PointerRoute()
    private val deadline = SystemClock.uptimeMillis() + 90_000
    private var remaining = 768
    private var lastMove: Long = -100
    private val padEpoch = AtomicLong()
    private val main = Handler(Looper.getMainLooper())
    private var seenPadEpoch = 0L
    private var layout: PointerLayout? = null
    private var layoutEpoch = 0L
    private var row = 0L
    private var dispatchRow = 0L
    private var insideActivity = false
    private var lastViewOnlyMove = -100L

    init { note("start schema=1 profile=$profile mode=observe-only") }

    fun invalidate(reason: String, through: Long = SystemClock.uptimeMillis()) {
        input.fenceOpenings(through)
        for (slot in 1..SLOTS) {
            val device = devices.at(slot) ?: continue
            cancellation(device.alias(), input.invalidate(slot, through, reason, false))
            if (profile == Profile.WSA) cancellation(device.alias(), input.invalidate(slot, through, reason, true))
        }
        cancellation(0, direct.invalidate(through, reason))
        outcome(route.cancel(reason))
        note("invalidate reason=$reason through=$through layout=$layoutEpoch")
    }

    fun padCallback(action: Int, time: Long) {
        if (action == MotionEvent.ACTION_MOVE || SystemClock.uptimeMillis() > deadline) return
        padEpoch.incrementAndGet()
        main.post {
            note("view channel=native-pad action=$action time=$time")
            checkPadEpoch()
        }
    }

    private fun checkPadEpoch() {
        val epoch = padEpoch.get()
        if (seenPadEpoch == epoch) return
        seenPadEpoch = epoch
        invalidate("native-pad-contact")
    }

    fun layoutObserved(scene: () -> Scene?) {
        if (active()) updateLayout(scene())
    }

    private fun active() = remaining > 0 && SystemClock.uptimeMillis() <= deadline

    fun deviceChanged(id: Int) {
        invalidate("device-change")
        devices.remove(id)?.let { input.detach(it.slot()) }
    }

    fun dispatch(
        event: MotionEvent,
        channel: String,
        focused: Boolean,
        scene: () -> Scene?,
        upstream: () -> Boolean,
    ): Boolean {
        if (!active()) return upstream()
        checkPadEpoch()
        val previous = dispatchRow
        val wasInsideActivity = insideActivity
        dispatchRow = observe(event, channel, focused, scene())
        insideActivity = true
        try {
            val handled = upstream()
            if (dispatchRow != 0L) note("dispatch raw=$dispatchRow handled=$handled")
            return handled
        } finally {
            dispatchRow = previous
            insideActivity = wasInsideActivity
        }
    }

    /** Child callbacks are evidence of delivery, never a second source of normalized events. */
    fun view(event: MotionEvent, channel: String) {
        val moving = event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        if (moving && dispatchRow == 0L) {
            val now = SystemClock.uptimeMillis()
            if (insideActivity || now - lastViewOnlyMove < 100) return
            lastViewOnlyMove = now
        }
        note("view channel=$channel activity=$insideActivity raw=$dispatchRow action=${event.actionMasked} time=${event.eventTime}")
    }

    private fun observe(event: MotionEvent, channel: String, focused: Boolean, scene: Scene?): Long {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> Action.DOWN
            MotionEvent.ACTION_UP -> Action.UP
            MotionEvent.ACTION_MOVE -> Action.MOVE
            MotionEvent.ACTION_CANCEL -> Action.CANCEL
            MotionEvent.ACTION_POINTER_DOWN -> Action.POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> Action.POINTER_UP
            MotionEvent.ACTION_HOVER_ENTER -> Action.HOVER_ENTER
            MotionEvent.ACTION_HOVER_MOVE -> Action.HOVER_MOVE
            MotionEvent.ACTION_HOVER_EXIT -> Action.HOVER_EXIT
            MotionEvent.ACTION_BUTTON_PRESS -> Action.BUTTON_PRESS
            MotionEvent.ACTION_BUTTON_RELEASE -> Action.BUTTON_RELEASE
            MotionEvent.ACTION_SCROLL -> Action.SCROLL
            else -> Action.OTHER
        }
        if (action == Action.OTHER || event.pointerCount == 0) return 0
        updateLayout(scene)

        val x = event.x
        val y = event.y
        var hit = layout?.hit(x, y) ?: PointerLayout.Hit(PointerLayout.Owner.OUTSIDE, null)
        val region = if (hit.owner() == PointerLayout.Owner.OUTSIDE) PointerState.OUTSIDE else "window"
        val tool = event.getToolType(0)
        val pointer = event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
        val finger = event.source == InputDevice.SOURCE_TOUCHSCREEN && tool == MotionEvent.TOOL_TYPE_FINGER &&
            channel == "touch"
        val sample = PointerState.Sample(action, region, x, y, event.downTime, event.eventTime,
            event.getPointerId(0), event.pointerCount, event.actionButton, event.buttonState, focused, event.historySize > 0)
        val device = devices.get(event.deviceId, { "android" }, input::attach)
        val normalized = input.observe(sample, device.slot(), event.source, tool, profile == Profile.WSA, slop)
        val touch = if (profile == Profile.DIRECT) direct.observe(device.alias(), sample, finger, pointer && !finger, slop) else null
        val edge = normalized.result() ?: touch
        val source = normalized.interpretation() ?: if (touch != null) "direct-touch" else "upstream"
        if (source == PointerNormalizer.STANDARD && input.hasDirectContact() && hit.owner() == PointerLayout.Owner.GAME) {
            hit = PointerLayout.Hit(PointerLayout.Owner.UNOWNED, null)
        }
        val interrupted = when {
            normalized.displaced() != null -> route.cancel(normalized.displaced().phase())
            touch != null && touch.cancelled() != 0 -> route.cancel(touch.phase())
            route.held() && source == "upstream" && pointer && action == Action.DOWN &&
                sample.hasContactTime() -> route.cancelCompetingContact(event.downTime)
            else -> null
        }
        val routed = if (interrupted == null && (edge != null || normalized.interpretation() != null)) {
            route.observe(device.alias(), sample, edge, hit, slop)
        } else null
        val ordinaryMove = action == Action.MOVE || action == Action.HOVER_MOVE
        if (ordinaryMove && interrupted == null && routed == null && (edge?.cancelled() ?: 0) == 0
            && SystemClock.uptimeMillis() - lastMove < 100) return 0
        if (ordinaryMove) lastMove = SystemClock.uptimeMillis()

        val raw = ++row
        note("activity raw=$raw channel=$channel event=${event.eventTime} down=${event.downTime} " +
            "device=${device.alias()} source=${event.source} tool=$tool action=$action " +
            "buttons=${event.buttonState} actionButton=${event.actionButton} count=${event.pointerCount} " +
            "history=${event.historySize} x=$x y=$y focused=$focused layout=$layoutEpoch frame=${scene?.drawnFrame}")
        note("normalized raw=$raw adapter=$source phase=${edge?.phase()} down=${edge?.pressed()} " +
            "up=${edge?.released()} cancelled=${edge?.cancelled()} held=${edge?.held()} " +
            "wheelX=${event.getAxisValue(MotionEvent.AXIS_HSCROLL)} wheelY=${event.getAxisValue(MotionEvent.AXIS_VSCROLL)} " +
            "owner=${if (layout == null) "unavailable" else hit.owner()} pixel=${hit.pixel()} route=${routed ?: interrupted} mouseGameplayKeys=0")
        return raw
    }

    private fun updateLayout(scene: Scene?) {
        if (layout == scene?.layout) return
        layout = scene?.layout
        layoutEpoch++
        // Cancel an old contact even if the layout later returns to the same bounds.
        invalidate("layout-change")
        note("layout epoch=$layoutEpoch frame=${scene?.drawnFrame} geometry=$layout")
    }

    private fun outcome(value: PointerRoute.Outcome?) {
        if (value != null) note("route $value layout=$layoutEpoch mouseGameplayKeys=0")
    }

    private fun cancellation(alias: Long, edge: PointerState.Result?) {
        if (edge != null && edge.cancelled() != 0) note("cancel device=$alias phase=${edge.phase()} bits=${edge.cancelled()}")
    }

    private fun note(message: String) {
        if (remaining <= 0 || SystemClock.uptimeMillis() > deadline) return
        Log.i(TAG, message)
        if (--remaining == 0) Log.i(TAG, "stop reason=record-limit")
    }

    companion object {
        private const val SLOTS = 16
        private const val TAG = "MsPointer"

        fun create(activity: PokeDaisyActivity): MousePointerTrace? {
            if (!BuildConfig.DEBUG || BuildConfig.APPLICATION_ID != "com.pokedaisy.app.dev") return null
            val profile = Profile.entries.singleOrNull { File(activity.filesDir, it.marker).isFile } ?: return null
            return MousePointerTrace(profile, ViewConfiguration.get(activity).scaledTouchSlop.toFloat())
        }
    }
}

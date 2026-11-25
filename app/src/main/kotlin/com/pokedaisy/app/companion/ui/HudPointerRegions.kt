package com.pokedaisy.app.companion.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Measured visual and interactive bounds, queried on the UI thread. */
class PlacedHudRegion {
    private var placed: LayoutCoordinates? = null
    val modifier: Modifier = Modifier.onGloballyPositioned { placed = it }

    fun bounds(minimumTarget: Size = Size.Zero): Rect? {
        val layout = placed?.takeIf { it.isAttached } ?: return null
        return try {
            val w = layout.size.width.toFloat()
            val h = layout.size.height.toFloat()
            val dx = ((minimumTarget.width - w) / 2).coerceAtLeast(0f)
            val dy = ((minimumTarget.height - h) / 2).coerceAtLeast(0f)
            // Query the current layer translation, including a slide that did not trigger a new measurement.
            val start = layout.localToWindow(Offset(-dx, -dy))
            val end = layout.localToWindow(Offset(w + dx, h + dy))
            Rect(start.x, start.y, end.x, end.y).takeIf {
                it.left.isFinite() && it.top.isFinite() && it.right.isFinite() && it.bottom.isFinite() &&
                    it.width > 0f && it.height > 0f
            }
        } catch (_: IllegalStateException) {
            null // A placement retired while Compose was replacing the layer.
        }
    }
}

/** Battle Buttons supplies this only for the pointer-enabled Android surface. Null reserves the game while uncertain or modal. */
class BattleButtonsPointerRegions {
    private var read: (() -> List<Rect>?)? = null

    fun bounds(): List<Rect>? = read?.invoke()

    internal fun publish(source: (() -> List<Rect>?)?) {
        read = source
    }
}

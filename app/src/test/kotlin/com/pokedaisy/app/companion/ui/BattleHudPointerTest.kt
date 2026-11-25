package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.HtmlReportWriter
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.pokedaisy.app.companion.BattleStyle
import com.pokedaisy.mousesupremacy.pointer.PointerLayout
import com.pokedaisy.mousesupremacy.pointer.PointerLayout.Bounds
import com.pokedaisy.mousesupremacy.pointer.PointerLayout.Owner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercise real Compose placement and the same window-local hit boundary the Activity uses, without screenshot goldens. */
class BattleHudPointerTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE),
        theme = "android:Theme.Material.NoActionBar",
        snapshotHandler = HtmlReportWriter(),
    )

    private val setup = FieldHudScreenshotTest()
    @Before fun hosts() {
        installTestHosts(paparazzi.context)
        setup.game()
    }
    @After fun reset() = setup.reset()

    @Test fun enabledButNoRoomAtBothDockWidths() {
        for (dockWidth in listOf(512, 640)) {
            val hud = measured(dockWidth = dockWidth, fillPicture = true)
            assertEquals(emptyList<Rect>(), hud)
            val right = 1920f - dockWidth
            val core = Rect(0f, 0f, right, 1080f)
            val panel = Bounds(right, 0f, 1920f, 1080f)
            val scene = layout(hud, core = core, companion = panel)
            assertEquals(Owner.GAME, scene.hit(right * 144f / 240f, 1080f * 128f / 160f).owner())
            assertEquals(Owner.COMPANION, scene.hit(right + 1f, 850f).owner())
        }
    }

    @Test fun actualButtonsReserveOnlyTheirTargetsAndKeepCompanionPriority() {
        for (style in listOf(BattleStyle.GEN4, BattleStyle.GEN5)) {
            val hud = checkNotNull(measured(style = style))
            assertEquals(3, hud.size)
            assertTrue(hud.first().left > 1000f)
            assertTrue(hud.zipWithNext().all { (a, b) -> a.center.y < b.center.y })
            val picture = Rect(0f, 0f, 1745f, 1080f)
            val covered = hud.first().intersect(picture)
            assertTrue(covered.width > 0 && covered.height > 0)
            val active = covered.center
            assertEquals(Owner.HUD, layout(hud, core = picture).hit(active.x, active.y).owner())
            assertEquals(
                Owner.GAME,
                layout(hud, core = picture).hit(picture.width * 144f / 240f, picture.height * 128f / 160f).owner(),
            )
            val panel = Bounds(hud.first().left, 0f, 1920f, 1080f)
            assertEquals(Owner.COMPANION, layout(hud, core = picture, companion = panel).hit(active.x, active.y).owner())
        }
    }

    @Test fun pendingAndDimmedPickersStayClosedToTheGame() {
        assertNull(BattleButtonsPointerRegions().bounds())
        for (picker in BattleButtonsWindow.entries) {
            val layout = layout(measured(picker = picker))
            assertEquals(Owner.HUD, layout.hit(864f, 700f).owner())
        }
        assertEquals(emptyList<Rect>(), measured(shown = false))
    }

    private fun measured(
        shown: Boolean = true,
        dockWidth: Int = 0,
        fillPicture: Boolean = false,
        style: BattleStyle = BattleStyle.GEN4,
        picker: BattleButtonsWindow? = null,
    ): List<Rect>? {
        val pointerRegions = BattleButtonsPointerRegions()
        var captured: List<Rect>? = null
        paparazzi.snapshot {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                val inset = with(LocalDensity.current) { dockWidth.toDp() }
                Box(Modifier.fillMaxSize().padding(end = inset)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val (w, h) = with(LocalDensity.current) { maxWidth.toPx() to maxHeight.toPx() }
                        BattleButtons(
                            BattleButtonsScreenshotTest.battle, shown, BattleButtonsScreenshotTest.input, showHints = false,
                            core = Rect(0f, 0f, if (fillPicture) w else w - 175f, h),
                            initialWindow = picker, style = style, pointerRegions = pointerRegions,
                        )
                        // Layoutlib detaches the composable when snapshot() returns. Read while all layers are placed.
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { captured = pointerRegions.bounds() }
                    }
                }
            }
        }
        return captured
    }

    private fun layout(
        hud: List<Rect>?,
        core: Rect = Rect(0f, 0f, 1920f, 1080f),
        companion: Bounds? = null,
    ): PointerLayout {
        val frame = Bounds(0f, 0f, 1920f, 1080f)
        val game = Bounds(core.left, core.top, core.right, core.bottom)
        return PointerLayout(
            frame, companion, emptyList(), emptyList(), false, hud == null, frame, game, 1,
            hud.orEmpty().map { Bounds(it.left, it.top, it.right, it.bottom) },
        )
    }
}

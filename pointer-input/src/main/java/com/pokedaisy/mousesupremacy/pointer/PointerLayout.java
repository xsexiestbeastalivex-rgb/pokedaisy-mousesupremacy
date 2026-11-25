package com.pokedaisy.mousesupremacy.pointer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Window-local exclusions and the supported portion of the renderer's last drawn picture. */
public record PointerLayout(Bounds window, Bounds companion, List<Bounds> chrome,
                            List<Bounds> controls, boolean modal, boolean hud,
                            Bounds clip, Bounds gameQuad, long transform) {
    public PointerLayout {
        chrome = Collections.unmodifiableList(new ArrayList<>(chrome));
        controls = Collections.unmodifiableList(new ArrayList<>(controls));
    }

    public enum Owner { OUTSIDE, COMPANION, CHROME, OVERLAY, TOUCH_CONTROLS, HUD, GAME, UNOWNED }
    public record Bounds(float left, float top, float right, float bottom) {
        public boolean contains(float x, float y) {
            return Float.isFinite(left) && Float.isFinite(top) && Float.isFinite(right) && Float.isFinite(bottom)
                    && Float.isFinite(x) && Float.isFinite(y)
                    && x >= left && x < right && y >= top && y < bottom;
        }
    }
    public record Pixel(int x, int y) {}
    public record Hit(Owner owner, Pixel pixel) {}

    public Hit hit(float x, float y) {
        if (!window.contains(x, y)) return new Hit(Owner.OUTSIDE, null);
        if (contains(chrome, x, y)) return new Hit(Owner.CHROME, null);
        if (companion != null && companion.contains(x, y)) return new Hit(Owner.COMPANION, null);
        if (modal) return new Hit(Owner.OVERLAY, null);
        if (contains(controls, x, y)) return new Hit(Owner.TOUCH_CONTROLS, null);
        if (clip == null || !clip.contains(x, y)) return new Hit(Owner.UNOWNED, null);
        if (hud) return new Hit(Owner.HUD, null);
        if (gameQuad == null || !gameQuad.contains(x, y)) return new Hit(Owner.UNOWNED, null);
        double u = ((double)x - gameQuad.left()) / ((double)gameQuad.right() - gameQuad.left());
        double v = ((double)y - gameQuad.top()) / ((double)gameQuad.bottom() - gameQuad.top());
        return new Hit(Owner.GAME, new Pixel((int)(u * 240), (int)(v * 160)));
    }

    private static boolean contains(List<Bounds> bounds, float x, float y) {
        for (Bounds region : bounds) if (region.contains(x, y)) return true;
        return false;
    }
}

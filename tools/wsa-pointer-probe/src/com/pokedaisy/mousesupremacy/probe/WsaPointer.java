package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.BUTTONS;
import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;

/** Experimental interpretation of the translated events observed on WSA. One instance per guest ID. */
final class WsaPointer {
    private static final int SOURCE_TOUCHSCREEN = 0x1002;
    private static final int TOOL_TYPE_FINGER = 1;

    record Sample(PointerState.Action action, String hit, float x, float y,
                  long downTime, long eventTime, int pointerId, int pointerCount,
                  int actionButton, int buttons, boolean focused, boolean batched) {}

    static boolean accepts(boolean enabled, int source, int tool, int alias) {
        return enabled && source == SOURCE_TOUCHSCREEN && tool == TOOL_TYPE_FINGER && alias != 0;
    }

    private final float slopSquared;
    private Sample start;
    private long lastDownTime;
    private boolean sawTouchDown;
    private boolean sawButtonPress;
    private boolean sawPress;
    private boolean sawHover;
    private boolean dragging;
    private boolean clickEligible;
    private int held;
    private String region = OUTSIDE;

    WsaPointer(float touchSlop) {
        slopSquared = touchSlop * touchSlop;
    }

    PointerState.Result observe(Sample sample) {
        if (!sample.focused()) return null;
        if (sample.pointerCount() != 1) return cancel("ambiguous-contact");
        return switch (sample.action()) {
            case HOVER_ENTER, HOVER_MOVE, HOVER_EXIT -> hover(sample);
            case DOWN, BUTTON_PRESS -> begin(sample);
            case MOVE, UP, BUTTON_RELEASE, CANCEL -> continueContact(sample);
            default -> null;
        };
    }

    private PointerState.Result hover(Sample sample) {
        if (start != null || sample.buttons() != 0) return null;
        if (sample.action() == PointerState.Action.HOVER_EXIT && !OUTSIDE.equals(sample.hit())) {
            return result("hover-exit-inside", false, false, 0, 0, 0, 0);
        }
        boolean changed = setRegion(sample.hit());
        boolean first = sample.action() == PointerState.Action.HOVER_MOVE
                && !sawHover && !sawPress && !OUTSIDE.equals(region);
        sawHover |= first;
        return result(sample.action().name(), changed, first, 0, 0, 0, 0);
    }

    private PointerState.Result begin(Sample sample) {
        boolean same = matches(sample);
        if (start != null && !same) {
            // A newer contact on the same reported ID makes the current contact ambiguous.
            if (sample.downTime() <= lastDownTime) return null;
            lastDownTime = sample.downTime();
            return cancel("overlapping-contact");
        }
        int button = sample.buttons();
        boolean touchDown = sample.action() == PointerState.Action.DOWN;
        if ((button & BUTTONS) != button || Integer.bitCount(button) != 1
                || (!touchDown && sample.actionButton() != button)
                || sample.eventTime() != sample.downTime() || OUTSIDE.equals(sample.hit())) return null;
        if (!same) {
            if (sample.downTime() <= lastDownTime) return null;
            lastDownTime = sample.downTime();
            start = sample;
        } else if (button != start.buttons() || !sample.hit().equals(start.hit())
                || sample.x() != start.x() || sample.y() != start.y()) {
            return cancel("inconsistent-press");
        }
        sawTouchDown |= touchDown;
        sawButtonPress |= !touchDown;
        if (held != 0 || !sawTouchDown || !sawButtonPress) return null;
        held = button;
        boolean changed = setRegion(sample.hit());
        sawPress = true;
        clickEligible = true;
        return result("BUTTON_PRESS", changed, false, held, 0, 0, 0);
    }

    private PointerState.Result continueContact(Sample sample) {
        if (!matches(sample)) return null;
        if (sample.action() == PointerState.Action.CANCEL) return cancel("android-cancel");
        if (OUTSIDE.equals(sample.hit())) return cancel("spatial-exit");
        if (held == 0) {
            clearContact();
            return null;
        }
        if (sample.action() == PointerState.Action.MOVE && sample.buttons() != held) {
            return cancel("button-state-changed");
        }
        if (sample.action() == PointerState.Action.BUTTON_RELEASE
                && (sample.actionButton() != held || sample.buttons() != 0)) {
            return cancel("inconsistent-release");
        }
        if (sample.action() == PointerState.Action.UP && sample.buttons() != 0) {
            return cancel("inconsistent-release");
        }

        float dx = sample.x() - start.x();
        float dy = sample.y() - start.y();
        boolean wasDragging = dragging;
        dragging |= dx * dx + dy * dy > slopSquared;
        // A batched move can hide an excursion in its history. Suppress a click when that path is unknown.
        clickEligible &= !sample.batched() && start.hit().equals(sample.hit());
        boolean changed = setRegion(sample.hit());
        if (sample.action() == PointerState.Action.MOVE) {
            String phase = dragging ? (wasDragging ? "drag" : "drag-start") : "MOVE";
            return result(phase, changed, false, 0, 0, 0, 0);
        }
        int released = held;
        held = 0;
        int click = !dragging && clickEligible ? released : 0;
        var result = result(dragging ? "drag-release" : "BUTTON_RELEASE", changed, false, 0, released, 0, click);
        clearContact();
        return result;
    }

    PointerState.Result cancel(String reason) {
        boolean changed = setRegion(OUTSIDE);
        int cancelled = held;
        clearContact();
        return result(reason, changed, false, 0, 0, cancelled, 0);
    }

    private void clearContact() {
        start = null;
        sawTouchDown = sawButtonPress = dragging = clickEligible = false;
        held = 0;
    }

    private boolean matches(Sample sample) {
        return start != null && sample.downTime() == start.downTime() && sample.pointerId() == start.pointerId();
    }

    private boolean setRegion(String hit) {
        boolean changed = !region.equals(hit);
        region = hit;
        return changed;
    }

    private PointerState.Result result(String phase, boolean changed, boolean firstHover,
                                       int pressed, int released, int cancelled, int click) {
        return new PointerState.Result(phase, region, changed, firstHover, pressed, released,
                cancelled, held, !sawPress, dragging, click);
    }
}

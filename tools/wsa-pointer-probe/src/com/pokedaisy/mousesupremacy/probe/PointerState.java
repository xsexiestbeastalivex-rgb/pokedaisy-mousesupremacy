package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;

/** Standard Android absolute-mouse edge diagnostics. */
final class PointerState {
    static final int BUTTONS = 1 | 2 | 4;
    static final String OUTSIDE = "outside";

    enum Action {
        DOWN, UP, MOVE, CANCEL, HOVER_ENTER, HOVER_MOVE, HOVER_EXIT, SCROLL,
        BUTTON_PRESS, BUTTON_RELEASE, POINTER_DOWN, POINTER_UP, OTHER
    }

    record Sample(Action action, String hit, float x, float y, long downTime, long eventTime,
                  int pointerId, int pointerCount, int actionButton, int buttons, boolean focused, boolean batched) {
        boolean isOpening() { return action == DOWN || action == BUTTON_PRESS; }
        boolean isTerminal() { return action == UP || action == BUTTON_RELEASE; }
        boolean isHover() { return action == HOVER_ENTER || action == HOVER_MOVE || action == HOVER_EXIT; }

        boolean isContact() {
            return isOpening() || isTerminal() || action == MOVE || action == CANCEL
                    || action == POINTER_DOWN || action == POINTER_UP;
        }

        boolean samePoint(Sample other) { return hit.equals(other.hit) && x == other.x && y == other.y; }
        boolean hasPosition() { return Float.isFinite(x) && Float.isFinite(y); }
    }

    record Result(String phase, String region, boolean regionChanged, boolean firstUnpressedMove,
                  int pressed, int released, int cancelled, int held, boolean beforeFirstPress,
                  boolean dragging, int click) {}

    private int held;
    private String region = OUTSIDE;
    private boolean sawPress;
    private boolean sawUnpressedMove;
    private long generation = -1;
    private long closedThrough = -1;
    private long lastEvent;
    private int pointerId;
    private final long[] releasedAt = new long[3];

    boolean held() { return held != 0; }

    Result observe(Sample sample) {
        if (!sample.focused()) return null;
        var action = sample.action();
        if (action == CANCEL || sample.pointerCount() != 1 || action == POINTER_DOWN || action == POINTER_UP) {
            if (held() && sample.downTime() > 0 && sample.downTime() < generation) return null;
            return invalidate(sample.downTime(), action == CANCEL ? "android-cancel" : "ambiguous-contact");
        }
        if (action == HOVER_EXIT && !OUTSIDE.equals(sample.hit())) {
            return result("hover-exit-inside", false, false, 0, 0, 0);
        }
        if (action == OTHER) return null;

        long down = sample.downTime();
        if (sample.isContact() && (down <= closedThrough || down < generation || sample.eventTime() < down)) return null;
        if (sample.isOpening() && sample.eventTime() < lastEvent) return null;
        int superseded = 0;
        if (held()) {
            if (sample.eventTime() < lastEvent) {
                return sample.isContact() && !sample.isOpening() ? invalidate(down, "reordered-contact") : null;
            }
            if (sample.isContact() && (down != generation || sample.pointerId() != pointerId)) {
                // A fresh opening can recover from a lost terminal. Retire the older generation first.
                if (!sample.isOpening() || down == generation) return invalidate(down, "ambiguous-contact");
                superseded = invalidate(generation, "superseded-contact").cancelled();
            }
            if (held() && (!region.equals(sample.hit()) || !sample.hasPosition())) {
                boolean changed = !region.equals(sample.hit());
                var cancelled = invalidate(generation, "region-cancel");
                region = sample.hit();
                return result(cancelled.phase(), changed, false, 0, 0, cancelled.cancelled());
            }
            if ((action == MOVE || sample.isHover()) && (held & ~sample.buttons()) != 0) {
                return invalidate(generation, "button-state-lost");
            }
        }

        boolean changed = !region.equals(sample.hit());
        region = sample.hit();
        int pressed = 0;
        int released = 0;
        int cancelled = 0;
        int mask = sample.buttons() & BUTTONS;
        if (sample.isOpening()) {
            sawPress = true;
            int candidate = action == BUTTON_PRESS ? sample.actionButton() : mask;
            if (Integer.bitCount(candidate) == 1 && (candidate & mask) == candidate
                    && !OUTSIDE.equals(region) && sample.hasPosition()) {
                if (generation != down) java.util.Arrays.fill(releasedAt, -1);
                if (sample.eventTime() > releasedAt[Integer.numberOfTrailingZeros(candidate)]) {
                    pressed = candidate & ~held;
                    held |= pressed;
                    generation = down;
                    pointerId = sample.pointerId();
                }
            }
        }
        if (sample.isTerminal()) {
            if (action == UP) {
                released = held & ~mask;
            } else if (Integer.bitCount(sample.actionButton()) == 1 && (sample.actionButton() & mask) == 0) {
                released = held & sample.actionButton();
                cancelled = held & ~mask & ~released;
            } else {
                cancelled = held;
            }
            int retired = released | cancelled;
            for (int index = 0; index < releasedAt.length; index++) {
                if ((retired & (1 << index)) != 0) releasedAt[index] = sample.eventTime();
            }
            held &= ~retired;
            if (!held()) {
                closedThrough = Math.max(closedThrough, down);
                generation = -1;
            }
        }
        if (sample.isContact() && (held() || released != 0 || cancelled != 0)) lastEvent = sample.eventTime();

        boolean firstMove = !sawUnpressedMove && !sawPress && sample.buttons() == 0
                && (action == HOVER_MOVE || action == MOVE) && !OUTSIDE.equals(region);
        sawUnpressedMove |= firstMove;
        String phase = superseded == 0 ? action.name() : "superseded-contact";
        return result(phase, changed, firstMove, pressed, released, cancelled | superseded);
    }

    Result invalidate(long through, String reason) {
        boolean changed = !region.equals(OUTSIDE);
        region = OUTSIDE;
        int cancelled = held;
        held = 0;
        closedThrough = Math.max(closedThrough, Math.max(generation, through));
        generation = -1;
        return result(reason, changed, false, 0, 0, cancelled);
    }

    private Result result(String phase, boolean changed, boolean firstMove, int pressed, int released,
                          int cancelled) {
        return new Result(phase, region, changed, firstMove, pressed, released, cancelled, held,
                !sawPress, false, 0);
    }
}

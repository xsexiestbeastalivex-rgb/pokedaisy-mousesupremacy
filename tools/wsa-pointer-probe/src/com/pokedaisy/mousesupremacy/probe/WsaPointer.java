package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.BUTTONS;
import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;
import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;
import com.pokedaisy.mousesupremacy.probe.PointerState.Sample;

/** Interprets the paired touch/generic representations observed on the selected WSA host. */
final class WsaPointer {
    private static final int SOURCE_TOUCHSCREEN = 0x1002;
    private static final int TOOL_TYPE_FINGER = 1;

    static boolean accepts(boolean enabled, int source, int tool, int slot) {
        return enabled && source == SOURCE_TOUCHSCREEN && tool == TOOL_TYPE_FINGER && slot != 0;
    }

    private Sample start;
    private Sample terminal;
    private long closedThrough = -1;
    private long lastEvent;
    private boolean sawTouchDown;
    private boolean sawButtonPress;
    private boolean sawPress;
    private boolean sawHover;
    private boolean dragging;
    private boolean clickEligible;
    private String region = OUTSIDE;

    boolean active() {
        return start != null;
    }

    boolean applies(Sample sample) {
        return active() && sample.eventTime() >= start.downTime()
                && (sample.isHover() || sample.downTime() <= 0 || sample.downTime() >= start.downTime());
    }

    PointerState.Result observe(Sample sample, float slop) {
        if (!sample.focused()) return null;
        if (sample.isHover()) return hover(sample);
        if (!sample.isContact()) return null;
        if (sample.action() == CANCEL || sample.pointerCount() != 1
                || sample.action() == POINTER_DOWN || sample.action() == POINTER_UP) {
            return reject(sample, sample.action() == CANCEL ? "android-cancel" : "ambiguous-contact");
        }
        if (sample.downTime() <= closedThrough || active() && sample.downTime() < start.downTime()) return null;
        if (sample.isOpening() && (!active() || sample.downTime() > start.downTime())
                && sample.eventTime() < lastEvent) return null;
        if (active() && sample.downTime() > start.downTime()) {
            var abandoned = cancel("superseded-contact");
            // Keep the new opening half. Report the previous generation's cancellation on this row.
            if (sample.isOpening()) begin(sample);
            else retire(sample.downTime());
            return abandoned;
        }
        if (sample.isOpening()) return begin(sample);
        if (!active()) {
            // Terminal-first delivery, or motion before a complete opening, cannot later arm.
            retire(sample.downTime());
            return null;
        }
        return continueContact(sample, slop);
    }

    private PointerState.Result hover(Sample sample) {
        if (active()) {
            if (sample.eventTime() < lastEvent) return null;
            if (OUTSIDE.equals(sample.hit())) return cancel("spatial-exit");
            if (terminal != null) return cancel("incomplete-release");
        }
        if (sample.buttons() != 0) return null;
        if (sample.action() == HOVER_EXIT && !OUTSIDE.equals(sample.hit())) {
            return result("hover-exit-inside", false, false, 0, 0, 0, armedButton(), 0);
        }
        if (active()) return cancel("button-state-lost");
        boolean changed = setRegion(sample.hit());
        boolean first = sample.action() == HOVER_MOVE && !sawHover && !sawPress && !OUTSIDE.equals(region);
        sawHover |= first;
        return result(sample.action().name(), changed, first, 0, 0, 0, 0, 0);
    }

    private PointerState.Result begin(Sample sample) {
        int button = sample.buttons();
        boolean touchDown = sample.action() == DOWN;
        boolean valid = button == (button & BUTTONS) && Integer.bitCount(button) == 1
                && (touchDown ? sample.actionButton() == 0 : sample.actionButton() == button)
                && sample.eventTime() == sample.downTime() && !OUTSIDE.equals(sample.hit()) && sample.hasPosition();
        if (!valid || active() && (!start.samePoint(sample) || start.pointerId() != sample.pointerId()
                || start.buttons() != button)) return reject(sample, "inconsistent-press");
        // Repeated opening representations after admission can carry the old opening timestamp.
        if (armedButton() != 0 || terminal != null) return null;
        if (!active()) {
            start = sample;
            lastEvent = sample.eventTime();
        }
        sawTouchDown |= touchDown;
        sawButtonPress |= !touchDown;
        if (!sawTouchDown || !sawButtonPress) return null;
        sawPress = clickEligible = true;
        return result("BUTTON_PRESS", setRegion(sample.hit()), false, button, 0, 0, button, 0);
    }

    private PointerState.Result continueContact(Sample sample, float slop) {
        if (sample.eventTime() < lastEvent) return cancel("reordered-contact");
        if (sample.pointerId() != start.pointerId() || !sample.hasPosition()) return cancel("ambiguous-contact");
        if (OUTSIDE.equals(sample.hit())) return cancel("spatial-exit");
        int button = admittedButton();
        if (button == 0) {
            cancel("incomplete-press");
            return null;
        }
        lastEvent = sample.eventTime();
        if (sample.action() == MOVE && (sample.buttons() != button || terminal != null)) {
            return cancel("button-state-changed");
        }
        if (sample.isTerminal() && !validTerminal(sample, button)) return cancel("inconsistent-release");

        float dx = sample.x() - start.x();
        float dy = sample.y() - start.y();
        boolean wasDragging = dragging;
        dragging |= dx * dx + dy * dy > slop * slop;
        clickEligible &= !sample.batched() && start.hit().equals(sample.hit());
        boolean changed = setRegion(sample.hit());
        if (sample.action() == MOVE) {
            String phase = dragging ? (wasDragging ? "drag" : "drag-start") : "MOVE";
            return result(phase, changed, false, 0, 0, 0, button, 0);
        }

        if (terminal == null) {
            terminal = sample;
            // Either representation ends the held state. A click needs corroboration from both.
            return result("release-pending", changed, false, 0, 0, 0, 0, 0);
        }
        if (terminal.eventTime() != sample.eventTime() || !terminal.samePoint(sample)) {
            return cancel("inconsistent-release");
        }
        if (terminal.action() == sample.action()) return null;
        int click = !dragging && clickEligible ? button : 0;
        var released = result(dragging ? "drag-release" : "BUTTON_RELEASE", changed,
                false, 0, button, 0, 0, click);
        close();
        return released;
    }

    private static boolean validTerminal(Sample sample, int button) {
        return sample.buttons() == 0 && (sample.action() == UP ? sample.actionButton() == 0
                : sample.actionButton() == button);
    }

    PointerState.Result reject(Sample sample, String reason) {
        if (active() && !applies(sample)) return null;
        retire(sample.downTime());
        return active() ? cancel(reason) : null;
    }

    PointerState.Result invalidate(long through, String reason) {
        retire(through);
        return cancel(reason);
    }

    PointerState.Result cancel(String reason) {
        var result = result(reason, setRegion(OUTSIDE), false, 0, 0, admittedButton(), 0, 0);
        close();
        return result;
    }

    private int admittedButton() {
        return active() && sawTouchDown && sawButtonPress ? start.buttons() : 0;
    }

    private int armedButton() {
        return terminal == null ? admittedButton() : 0;
    }

    private void close() {
        if (active()) retire(start.downTime());
        start = terminal = null;
        sawTouchDown = sawButtonPress = dragging = clickEligible = false;
    }

    private void retire(long downTime) {
        closedThrough = Math.max(closedThrough, downTime);
    }

    private boolean setRegion(String hit) {
        boolean changed = !region.equals(hit);
        region = hit;
        return changed;
    }

    private PointerState.Result result(String phase, boolean changed, boolean firstHover,
                                       int pressed, int released, int cancelled, int held, int click) {
        return new PointerState.Result(phase, region, changed, firstHover, pressed, released,
                cancelled, held, !sawPress, dragging, click);
    }
}

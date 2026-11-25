package com.pokedaisy.mousesupremacy.probe;

/** Per-device interpretation of absolute mouse events. Raw dispatch observations live alongside it. */
final class PointerState {
    static final int BUTTONS = 1 | 2 | 4;
    static final String OUTSIDE = "outside";

    enum Action {
        DOWN, UP, MOVE, CANCEL, HOVER_ENTER, HOVER_MOVE, HOVER_EXIT, SCROLL,
        BUTTON_PRESS, BUTTON_RELEASE, OTHER
    }

    record Result(String phase, String region, boolean regionChanged, boolean firstUnpressedMove,
                  int pressed, int released, int cancelled, int held, boolean beforeFirstPress,
                  boolean dragging, int click) {}

    private int held;
    private String region = OUTSIDE;
    private boolean sawPress;
    private boolean sawUnpressedMove;

    Result observe(Action action, String hit, int button, int rawButtons, boolean focused) {
        if (!focused) return result("unfocused", false, false, 0, 0, 0);
        if (action == Action.CANCEL) return cancel("android-cancel");
        if (action == Action.HOVER_EXIT && !OUTSIDE.equals(hit)) {
            // Android can send a hover exit at the press coordinates before the down event.
            return result("hover-exit-inside", false, false, 0, 0, 0);
        }
        if (action == Action.OTHER) return result("uninterpreted", false, false, 0, 0, 0);

        boolean changed = !region.equals(hit);
        int cancelled = 0;
        if (changed && held != 0) {
            cancelled = held;
            held = 0;
        }
        region = hit;
        int pressed = 0;
        int released = 0;
        String phase = action.name();
        if (!OUTSIDE.equals(region)) {
            int pressedCandidate = switch (action) {
                case BUTTON_PRESS -> button & BUTTONS;
                case DOWN -> rawButtons & BUTTONS;
                default -> 0;
            };
            int releasedCandidate = switch (action) {
                case BUTTON_RELEASE -> button & BUTTONS;
                case UP -> held & ~rawButtons;
                default -> 0;
            };
            if (Integer.bitCount(pressedCandidate) == 1) pressed = pressedCandidate & ~held;
            if (Integer.bitCount(releasedCandidate) == 1) released = releasedCandidate & held;
            held = (held | pressed) & ~released;
        }
        sawPress |= action == Action.DOWN || action == Action.BUTTON_PRESS;

        boolean firstMove = !sawUnpressedMove && !sawPress && rawButtons == 0
                && (action == Action.HOVER_MOVE || action == Action.MOVE) && !OUTSIDE.equals(region);
        sawUnpressedMove |= firstMove;
        if (cancelled != 0) phase = "region-cancel";
        return result(phase, changed, firstMove, pressed, released, cancelled);
    }

    Result cancel(String reason) {
        boolean changed = !OUTSIDE.equals(region);
        region = OUTSIDE;
        int cancelled = held;
        held = 0;
        return result(reason, changed, false, 0, 0, cancelled);
    }

    private Result result(String phase, boolean changed, boolean firstMove, int pressed, int released,
                          int cancelled) {
        return new Result(phase, region, changed, firstMove, pressed, released, cancelled, held,
                !sawPress, false, 0);
    }
}

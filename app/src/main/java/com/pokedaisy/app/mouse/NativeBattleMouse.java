package com.pokedaisy.app.mouse;

import java.util.function.IntConsumer;

/** One native Gen III battle lease. All UI publications and the final key write share this monitor. */
public final class NativeBattleMouse {
    public static final int A = 1, B = 2, RIGHT = 16, LEFT = 32, UP = 64, DOWN = 128;
    private static final int HOVER = 0, FIGHT = 1, BACK = 2;
    private static final int MAX_LAG = 6, MAX_ACK = 16;

    public enum Menu { ACTION, MOVE }

    public record Native(Menu visible, Menu ready, int cursor, int slots) {
        public static final Native UNKNOWN = new Native(null, null, -1, 0);
    }

    public record Sample(long session, long context, long frame, Native nativeState) {}
    public record Picture(Sample sample, long transform, long time) {}
    private record Target(long session, long context, long layout, long transform, Menu menu, int slot) {}
    private record Press(long gesture, int action, Target target) {}
    private record Intent(long gesture, int action, Target target, long time) {}
    private record Pulse(Intent intent, int mask, int expectedCursor, Menu expectedMenu, long before) {}
    private record Reference(Menu from, long before, long busyBack, long result) {}

    @FunctionalInterface
    public interface Trace { void log(String row); }

    private final Trace trace;
    private Sample current;
    private Picture picture;
    private long session, context, layout;
    private long layoutTransform = -1;
    private int lastApplied, dispatched;
    private Pulse submitted;
    private boolean qualifiedBusy;
    private boolean active, qualifiedAction, qualifiedMove;
    private Reference reference;
    private Press press;
    private Intent intent;
    private Pulse pulse;
    private long appliedAt = -1;
    private boolean released;
    private float pointerX, pointerY;
    private long hoverTime;
    private boolean hover;
    private long hoverTransform;
    private long presentedFrame = -1;
    private Native lastPresented;

    public NativeBattleMouse(Trace trace) { this.trace = trace; }

    public synchronized void session(boolean enabled) {
        session++;
        active = enabled;
        qualifiedAction = qualifiedMove = qualifiedBusy = false;
        submitted = null;
        picture = null;
        current = null;
        presentedFrame = -1;
        lastPresented = null;
        lastApplied = -1;
        reference = null;
        invalidate("session");
    }

    public synchronized void invalidate(String reason) {
        if (press != null || intent != null || pulse != null) trace.log("cancel reason=" + reason);
        press = null;
        intent = null;
        pulse = null;
        hover = false;
    }

    public synchronized void interrupt(String reason) {
        invalidate(reason);
        reference = null;
    }

    public synchronized void resetState() {
        session(active);
    }

    public synchronized void layout(long epoch, long transform) {
        layout = epoch;
        layoutTransform = transform;
        picture = null;
        invalidate("layout");
    }

    public synchronized void show(Picture next) {
        if (!active || (next != null && next.sample.session != session)) return;
        if (next == null) {
            if (picture != null) interrupt("picture-withdrawn");
            picture = null;
            return;
        }
        if (picture != null && picture.sample.frame == next.sample.frame && picture.transform == next.transform) return;
        picture = next;
        if (reference != null && reference.result >= 0 && next.sample.frame >= reference.result &&
                next.sample.frame - reference.result <= MAX_ACK && next.transform == layoutTransform &&
                next.sample.nativeState.ready == (reference.from == Menu.ACTION ? Menu.MOVE : Menu.ACTION)) {
            if (reference.from == Menu.ACTION) {
                qualifiedAction = true;
                qualifiedBusy |= reference.busyBack >= 0;
            } else qualifiedMove = true;
            trace.log("reference accepted from=" + reference.from + " ready=" + reference.result
                    + " swap=" + next.sample.frame + " enabled=" + qualified());
            reference = null;
        }
        if (next.sample.frame != presentedFrame && !next.sample.nativeState.equals(lastPresented)) {
            lastPresented = next.sample.nativeState;
            presentedFrame = next.sample.frame;
            Native state = next.sample.nativeState;
            trace.log("swap frame=" + presentedFrame + " timeNs=" + next.time + " layout=" + layout
                    + " visible=" + state.visible + " ready=" + state.ready + " cursor=" + state.cursor);
        }
    }

    /** Points are already normalized and routed through the existing application ownership path. */
    public synchronized void pointer(float x, float y, long transform, long time) {
        if (press != null && !press.target.equals(target(x, y, transform, press.action))) press = null;
        if (intent != null && !intent.target.equals(target(x, y, transform, intent.action))) intent = null;
        pointerX = x;
        pointerY = y;
        hoverTransform = transform;
        hoverTime = time;
        hover = true;
    }

    public synchronized void down(long gesture, int button, float x, float y, long transform, long time) {
        invalidate("new-press");
        int action = button == 1 ? FIGHT : button == 2 ? BACK : -1;
        if (action < 0) return;
        Target target = target(x, y, transform, action);
        if (target != null && qualified() && compatible(target)) {
            press = new Press(gesture, action, target);
            trace.log("press gesture=" + gesture + " frame=" + current.frame + " timeNs=" + time);
        }
    }

    public synchronized void up(long gesture, float x, float y, long transform, long time) {
        Press previous = press;
        press = null;
        if (previous == null || previous.gesture != gesture ||
                !previous.target.equals(target(x, y, transform, previous.action)) ||
                !compatible(previous.target)) return;
        intent = new Intent(gesture, previous.action, previous.target, time);
        pointerX = x;
        pointerY = y;
        hoverTransform = transform;
        hoverTime = time;
        hover = true;
    }

    public synchronized Sample observe(long frame, Native state) {
        if (!active) return null;
        if (current == null || current.nativeState.ready != state.ready || current.nativeState.slots != state.slots) {
            context++;
            press = null;
            intent = null;
            if (pulse == null) hover = false;
            trace.log("sample session=" + session + " context=" + context + " frame=" + frame
                    + " visible=" + state.visible + " ready=" + state.ready + " cursor=" + state.cursor);
        }
        current = new Sample(session, context, frame, state);
        return current;
    }

    /**
     * The only path supplying mouse bits. Call from the emulator thread after arbitration.
     * The recheck is based on the native pre-frame sample and the most recent swapped picture.
     */
    public synchronized void commit(long before, int upstream, boolean idle, boolean referenceInput,
                                    IntConsumer write) {
        int mask = upstream;
        if (!active || current == null || before != current.frame) {
            invalidate("sample-unavailable");
        } else if (!idle || upstream != 0) {
            invalidate("upstream-owner");
            nativeReference(before, upstream, referenceInput);
        } else if (qualified()) {
            mask = nextMask(before);
        }
        dispatched = mask;
        submitted = pulse != null && pulse.before == before && mask == pulse.mask ? pulse : null;
        if (submitted != null) {
            trace.log("dispatch sample=" + before + " context=" + context + " mask=" + mask
                    + " gesture=" + pulse.intent.gesture + " pointerNs=" + pulse.intent.time + " layout=" + layout);
        }
        write.accept(mask);
    }

    private boolean qualified() { return qualifiedAction && qualifiedMove && qualifiedBusy; }

    private void nativeReference(long before, int mask, boolean allowed) {
        if (qualified() || !allowed || lastApplied < 0 || mask == 0 || (lastApplied & mask) != 0) return;
        if (reference != null && reference.from == Menu.ACTION && reference.result < 0 &&
                current.nativeState.ready == null && current.nativeState.visible != null && mask == B &&
                picture != null && picture.transform == layoutTransform &&
                picture.sample.frame <= current.frame && current.frame - picture.sample.frame <= MAX_LAG &&
                picture.sample.nativeState.visible == current.nativeState.visible) {
            reference = new Reference(Menu.ACTION, reference.before, before + 1, -1);
            trace.log("reference busy dispatched frame=" + before + " mask=" + mask + " shown=" + picture.sample.frame);
            return;
        }
        reference = null;
        if (lastApplied != 0 || !pictureMatches()) return;
        if ((current.nativeState.ready == Menu.ACTION && current.nativeState.cursor == 0 && mask == A) ||
                (current.nativeState.ready == Menu.MOVE && mask == B)) {
            reference = new Reference(current.nativeState.ready, before, -1, -1);
            trace.log("reference dispatched frame=" + before + " mask=" + mask);
        }
    }

    /** Report the actual core counter before/after the step. Reused/held images never acknowledge an edge. */
    public synchronized Sample applied(long before, long after, Native state) {
        if (!active) return null;
        if (before < 0 || after < 0 || after != (before + 1) % 0x1_0000_0000L) {
            invalidate("no-forward-step");
            submitted = null;
            reference = null;
            lastApplied = -1;
            return null;
        }
        boolean edge = (dispatched & ~lastApplied) != 0 && lastApplied >= 0;
        lastApplied = dispatched;
        if (submitted != null && submitted.before == before) {
            appliedAt = after;
            trace.log("applied frame=" + after + " mask=" + dispatched + " edge=" + edge
                    + " gesture=" + submitted.intent.gesture);
            if (!edge) invalidate("no-new-edge");
        } else if (appliedAt >= 0 && dispatched == 0 && !released) {
            released = true;
            trace.log("applied frame=" + after + " mask=0 release=true");
        }
        submitted = null;
        if (reference != null) {
            if (reference.before == before && edge) {
                trace.log("reference applied frame=" + after + " mask=" + dispatched);
            }
            if (reference.busyBack == after) {
                if (edge && dispatched == B) trace.log("reference busy applied frame=" + after + " mask=" + B);
                else reference = new Reference(reference.from, reference.before, -1, -1);
            }
            updateReference(after, state);
        }
        Native previous = current == null ? Native.UNKNOWN : current.nativeState;
        Sample sample = observe(after, state);
        // One settled forward frame covers BG and cursor changes across the VBlank/render boundary.
        if (sample != null && state.ready != null && !state.equals(previous)) {
            return new Sample(session, context, after, new Native(state.visible, null, -1, 0));
        }
        return sample;
    }

    private void updateReference(long after, Native state) {
        if (after - reference.before > MAX_ACK || state.visible == null) {
            reference = null;
            return;
        }
        if (reference.result >= 0) return;
        if (state.ready != null && state.ready != reference.from) {
            reference = new Reference(reference.from, reference.before, reference.busyBack, after);
        } else if (state.ready == reference.from) {
            reference = null;
        }
    }

    private boolean pictureMatches() {
        if (picture == null || picture.transform != layoutTransform || current == null ||
                current.nativeState.ready == null) return false;
        Sample shown = picture.sample;
        return shown.session == current.session && shown.context == current.context &&
                shown.nativeState.ready == current.nativeState.ready &&
                shown.nativeState.cursor == current.nativeState.cursor &&
                shown.frame <= current.frame && current.frame - shown.frame <= MAX_LAG;
    }

    private boolean compatible(Target target) {
        return pictureMatches() && target.session == session && target.context == context
                && target.layout == layout && target.transform == picture.transform
                && target.menu == current.nativeState.ready
                && (target.slot < 0 || target.slot < current.nativeState.slots);
    }

    private Target target(float x, float y, long transform, int action) {
        if (!pictureMatches() || picture.transform != transform || !Float.isFinite(x) || !Float.isFinite(y)) return null;
        Menu menu = picture.sample.nativeState.ready;
        int slot = slot(menu, x, y, picture.sample.nativeState.slots);
        if (action == BACK && (menu != Menu.MOVE || x < 0 || x >= 240 || y < 0 || y >= 160)) return null;
        if (action == FIGHT && (menu != Menu.ACTION || slot != 0)) return null;
        if (action == HOVER && slot < 0) return null;
        return new Target(session, context, layout, transform, menu, action == BACK ? -1 : slot);
    }

    static int slot(Menu menu, float x, float y, int slots) {
        int left = menu == Menu.ACTION ? 128 : 8;
        int middle = menu == Menu.ACTION ? 184 : 80;
        int right = menu == Menu.ACTION ? 232 : 152;
        if (x < left || x >= right || y < 120 || y >= 152) return -1;
        int slot = (x < middle ? 0 : 1) + (y < 136 ? 0 : 2);
        return slot < slots ? slot : -1;
    }

    private int nextMask(long frame) {
        if (pulse != null && !settlePulse(frame)) return 0;
        if (lastApplied != 0 || press != null) return 0;
        Intent request = intent;
        if (request == null && hover) {
            Target target = target(pointerX, pointerY, hoverTransform, HOVER);
            if (target != null) request = new Intent(0, HOVER, target, hoverTime);
        }
        if (request == null) return 0;
        if (!compatible(request.target)) {
            if (intent != null && (picture == null || current.context != intent.target.context ||
                    picture.transform != intent.target.transform || current.frame - picture.sample.frame > MAX_LAG)) {
                invalidate("target-unavailable");
            }
            return 0;
        }
        Native state = current.nativeState;
        int cursor = state.cursor;
        int key;
        if (request.action == BACK) key = B;
        else if (cursor != request.target.slot) key = direction(cursor, request.target.slot, state.slots);
        else key = request.action == FIGHT ? A : 0;
        if (key == 0) return 0;
        Menu expectedMenu = key == A ? Menu.MOVE : key == B ? Menu.ACTION : state.ready;
        int expectedCursor = switch (key) {
            case LEFT, RIGHT -> cursor ^ 1;
            case UP, DOWN -> cursor ^ 2;
            default -> -1;
        };
        if (key == A || key == B) intent = null;
        pulse = new Pulse(request, key, expectedCursor, expectedMenu, frame);
        released = false;
        appliedAt = -1;
        return key;
    }

    private boolean settlePulse(long frame) {
        if (appliedAt < 0 || frame - pulse.before > MAX_ACK) {
            invalidate("pulse-timeout");
            return false;
        }
        if (!released) return false;
        Native state = current.nativeState;
        if (state.ready == null) return false;
        boolean direction = pulse.expectedCursor >= 0;
        if (state.ready != pulse.expectedMenu || direction && (state.cursor != pulse.expectedCursor ||
                current.context != pulse.intent.target.context)) {
            invalidate("unexpected-ack");
            return false;
        }
        if (!pictureMatches()) return false;
        trace.log("ack mask=" + pulse.mask + " applied=" + appliedAt + " native=" + frame
                + " shown=" + picture.sample.frame + " cursor=" + state.cursor
                + " latencyNs=" + (picture.time - pulse.intent.time));
        pulse = null;
        return true;
    }

    static int direction(int from, int to, int slots) {
        if (((from ^ to) & 1) != 0 && (from ^ 1) < slots) return (from & 1) == 0 ? RIGHT : LEFT;
        if (((from ^ to) & 2) != 0 && (from ^ 2) < slots) return (from & 2) == 0 ? DOWN : UP;
        return 0;
    }
}

package com.pokedaisy.app.mouse;

import java.util.function.IntConsumer;

/** Battle input, live leases and completed-frame evidence share the final-key-write monitor. */
public final class NativeBattleMouse {
    public static final int A = 1, B = 2, RIGHT = 16, LEFT = 32, UP = 64, DOWN = 128;
    private static final int HOVER = 0, FIGHT = 1, BACK = 2;
    private static final int MAX_LAG = 6, MAX_ACK = 16, MAX_PROBE_BUSY = 4;
    private static final long NO_FRAME = -1;

    public enum Menu { ACTION, MOVE }

    public record Native(Menu visible, Menu ready, int cursor, int slots) {
        public static final Native UNKNOWN = new Native(null, null, -1, 0);
    }

    // An unsettled ready frame keeps its raw readiness. It cannot masquerade as native busy.
    public record Sample(long session, long context, long frame, Native nativeState, boolean settled) {}
    public record Picture(Sample sample, long transform, long time) {}
    private record Surface(long session, long layout, long transform) {}
    private record MenuLease(Surface surface, long context, Menu menu) {}
    private record Target(MenuLease lease, int slot) {}
    private record Press(long gesture, int action, Target target) {}
    private record Intent(long gesture, int action, Target target, long time) {}
    private record Pulse(Intent intent, int mask, int expectedCursor, Menu expectedMenu, long before) {}
    private record Arm(MenuLease lease, long frame) {}
    private record Reference(Surface surface, Menu from, long before, long applied,
                             long busy, long ready, boolean probe) {
        Reference withApplied(long frame) { return new Reference(surface, from, before, frame, busy, ready, probe); }
        Reference withBusy(long frame) { return new Reference(surface, from, before, applied, frame, ready, probe); }
        Reference withReady(long frame) { return new Reference(surface, from, before, applied, busy, frame, probe); }
        Menu destination() { return from == Menu.ACTION ? Menu.MOVE : Menu.ACTION; }
    }

    @FunctionalInterface
    public interface Trace { void log(String row); }

    private final Trace trace;
    private boolean traceFailed;
    private boolean active, qualifiedAction, qualifiedMove, qualifiedBusy;
    private long session, context, layout, layoutTransform = -1;
    private Sample current;
    private Picture picture;
    private long highestSwapped = NO_FRAME, withdrawnThrough = NO_FRAME;
    private Sample lastLogged;
    private Press press;
    private Intent intent, hover;
    private Arm probeArm;
    private Reference reference;
    private Pulse pulse, submitted;
    private long appliedAt = NO_FRAME;
    private boolean released;
    private int lastApplied, dispatched, probeSubmitted;
    private long probeBefore;

    public NativeBattleMouse(Trace trace) { this.trace = trace; }

    private void note(String row) {
        if (traceFailed) return;
        try {
            trace.log(row);
        } catch (Exception observerFailure) {
            traceFailed = true;
        }
    }

    public synchronized void session(boolean enabled) {
        session++;
        active = enabled;
        qualifiedAction = qualifiedMove = qualifiedBusy = false;
        submitted = null;
        probeSubmitted = 0;
        picture = null;
        current = null;
        highestSwapped = withdrawnThrough = NO_FRAME;
        lastLogged = null;
        lastApplied = -1;
        appliedAt = NO_FRAME;
        released = false;
        interrupt("session");
    }

    private void retireGesture(String reason) {
        if (press != null || intent != null || pulse != null) note("cancel reason=" + reason);
        press = null;
        intent = null;
        pulse = null;
        hover = null;
    }

    private void retireReference(String reason) {
        if (probeArm != null || reference != null) note("reference unavailable reason=" + reason);
        probeArm = null;
        reference = null;
    }

    public synchronized void invalidate(String reason) {
        retireGesture(reason);
        if (probeArm != null || (reference != null && reference.probe)) retireReference(reason);
    }

    public synchronized void interrupt(String reason) {
        retireGesture(reason);
        retireReference(reason);
    }

    public synchronized void resetState() {
        session(active);
    }

    public synchronized void layout(long epoch, long transform) {
        if (epoch == layout && transform == layoutTransform) return;
        // The UI can discover a transform after GL already swapped it. Retire work, not GL evidence.
        layout = epoch;
        layoutTransform = transform;
        interrupt("layout");
    }

    private Surface surface() { return new Surface(session, layout, layoutTransform); }

    private MenuLease menuLease() {
        return new MenuLease(surface(), current.context, current.nativeState.ready);
    }

    private static boolean within(long newer, long older, int limit) {
        return older >= 0 && newer >= older && newer - older <= limit;
    }

    /** Expiry must be processed before either the native frame or the swapped picture is replaced. */
    private void expirePresentation(long frame) {
        if (picture == null || within(frame, picture.sample.frame, MAX_LAG)) return;
        Picture expired = picture;
        picture = null;
        if (expired.transform == layoutTransform) interrupt("presentation-expired");
    }

    private boolean readyPicture(boolean requireCursor) {
        if (current == null || current.nativeState.ready == null || !pictureCurrent()) return false;
        Sample shown = picture.sample;
        return shown.settled && shown.context == current.context &&
                shown.nativeState.ready == current.nativeState.ready &&
                (!requireCursor || shown.nativeState.cursor == current.nativeState.cursor);
    }

    private boolean pictureCurrent() {
        return current != null && picture != null && layoutTransform >= 0 &&
                picture.transform == layoutTransform && picture.sample.session == session &&
                within(current.frame, picture.sample.frame, MAX_LAG);
    }

    public synchronized void show(Picture next) {
        if (!active || (next != null && next.sample.session != session)) return;
        if (next == null) {
            withdrawnThrough = highestSwapped;
            picture = null;
            interrupt("picture-withdrawn");
            return;
        }
        if (current == null) return;
        expirePresentation(current.frame);
        long frame = next.sample.frame;
        if (frame <= withdrawnThrough || frame < highestSwapped ||
                !within(current.frame, frame, MAX_LAG)) return;
        if (picture != null && frame == picture.sample.frame && picture.transform == next.transform) return;
        if (picture != null && picture.transform == layoutTransform && next.transform != layoutTransform) {
            interrupt("presentation-transform");
        }
        picture = next;
        highestSwapped = frame;
        acknowledgeReference();
        Sample shown = picture.sample;
        if (lastLogged == null || lastLogged.context != shown.context || lastLogged.settled != shown.settled ||
                !lastLogged.nativeState.equals(shown.nativeState)) {
            lastLogged = shown;
            Native state = shown.nativeState;
            note("swap frame=" + shown.frame + " timeNs=" + next.time + " layout=" + layout
                    + " visible=" + state.visible + " ready=" + state.ready + " cursor=" + state.cursor
                    + " settled=" + shown.settled);
        }
    }

    private void acknowledgeReference() {
        if (reference == null || reference.ready < 0 || !reference.surface.equals(surface()) ||
                !readyPicture(true) || current.nativeState.ready != reference.destination() ||
                !within(picture.sample.frame, reference.ready, MAX_ACK)) return;
        if (reference.from == Menu.ACTION) {
            qualifiedAction = true;
            qualifiedBusy |= reference.busy >= 0;
        } else qualifiedMove = true;
        note("reference accepted from=" + reference.from + " ready=" + reference.ready
                + " swap=" + picture.sample.frame + " enabled=" + qualified());
        reference = null;
    }

    public synchronized void refuseReadinessProbe(String reason) {
        note("reference probe refused reason=" + reason);
    }

    /** One explicit opt-in command. A newly discovered layout may wait for its matching swap. */
    public synchronized void armReadinessProbe() {
        if (!active || current == null || layoutTransform < 0) {
            refuseReadinessProbe("scene-unavailable");
        } else if (qualified() || probeArm != null || reference != null) {
            refuseReadinessProbe("already-active-or-qualified");
        } else if (current.frame < 0 || current.nativeState.ready != Menu.ACTION || current.nativeState.cursor != 0) {
            refuseReadinessProbe("fight-not-ready");
        } else {
            retireGesture("reference-probe");
            probeArm = new Arm(menuLease(), current.frame);
            note("reference probe armed frame=" + current.frame);
        }
    }

    /** Points are already normalized and routed through the application ownership path. */
    public synchronized void pointer(float x, float y, long transform, long time) {
        if (probeArm != null || (reference != null && reference.probe && reference.busy < 0)) {
            retireReference("reference-pointer");
        }
        if (press != null && !press.target.equals(target(x, y, transform, press.action, false))) press = null;
        if (intent != null && !intent.target.equals(target(x, y, transform, intent.action, false))) intent = null;
        Target target = target(x, y, transform, HOVER, false);
        hover = qualified() && target != null ? new Intent(0, HOVER, target, time) : null;
    }

    public synchronized void down(long gesture, int button, float x, float y, long transform, long time) {
        invalidate("new-press");
        int action = button == 1 ? FIGHT : button == 2 ? BACK : -1;
        if (action < 0) return;
        Target target = target(x, y, transform, action, true);
        if (target != null && qualified()) {
            press = new Press(gesture, action, target);
            note("press gesture=" + gesture + " frame=" + current.frame + " timeNs=" + time);
        }
    }

    public synchronized void up(long gesture, float x, float y, long transform, long time) {
        Press previous = press;
        press = null;
        if (previous == null || previous.gesture != gesture ||
                !previous.target.equals(target(x, y, transform, previous.action, true))) return;
        intent = new Intent(gesture, previous.action, previous.target, time);
    }

    public synchronized Sample observe(long frame, Native state) {
        if (!active) return null;
        expirePresentation(frame);
        if (reference != null && reference.from == Menu.ACTION && reference.applied >= 0 &&
                reference.busy < 0 && state.visible != Menu.ACTION) {
            retireReference("left-action-before-busy-witness");
        }
        if (current == null || frame < current.frame || current.nativeState.visible != state.visible ||
                current.nativeState.ready != state.ready || current.nativeState.slots != state.slots) {
            context++;
            press = null;
            intent = null;
            hover = null;
            // A confirmation can be acknowledged on its destination. It has no steering authority there.
            if (pulse != null && pulse.expectedCursor >= 0) pulse = null;
            if (probeArm != null) retireReference("armed-menu-changed");
            note("sample session=" + session + " context=" + context + " frame=" + frame
                    + " visible=" + state.visible + " ready=" + state.ready + " cursor=" + state.cursor);
        }
        current = new Sample(session, context, frame, state, true);
        return current;
    }

    /** Supply no mouse bits when existing input or a hold owns this frame. */
    public synchronized void commit(long before, int upstream, boolean idle, boolean referenceInput, IntConsumer write) {
        int mask = upstream;
        probeSubmitted = 0;
        expirePresentation(before);
        if (!active || current == null || before < 0 || before != current.frame) {
            interrupt("sample-unavailable");
        } else if (!idle || upstream != 0) {
            invalidate("upstream-owner");
            if (!referenceInput) retireReference("upstream-reservation");
            else nativeReference(before, upstream);
        } else if (qualified()) {
            mask = nextMask(before);
        } else {
            mask = probeMask(before);
            probeSubmitted = mask;
            probeBefore = before;
        }
        dispatched = mask;
        submitted = pulse != null && pulse.before == before && mask == pulse.mask ? pulse : null;
        if (submitted != null) {
            note("dispatch sample=" + before + " context=" + context + " mask=" + mask
                    + " gesture=" + pulse.intent.gesture + " pointerNs=" + pulse.intent.time + " layout=" + layout);
        }
        write.accept(mask);
    }

    private int probeMask(long before) {
        if (probeArm != null) {
            if (!probeArm.lease.equals(menuLease()) || !within(before, probeArm.frame, MAX_LAG)) {
                retireReference("armed-picture-deadline");
                return 0;
            }
            if (lastApplied != 0 || !readyPicture(true) || current.nativeState.cursor != 0) return 0;
            probeArm = null;
            startReference(before, Menu.ACTION, A, true);
            note("reference probe A frame=" + before);
            return A;
        }
        if (reference == null || !reference.probe || reference.busy >= 0 || reference.ready >= 0) return 0;
        if (before - reference.before > MAX_PROBE_BUSY) {
            retireReference("busy-swap-deadline");
            return 0;
        }
        if (lastApplied != 0 || !busyWitness()) return 0;
        submitBusy(before);
        note("reference probe busy B frame=" + before);
        return B;
    }

    private boolean qualified() { return qualifiedAction && qualifiedMove && qualifiedBusy; }

    private boolean busyWitness() {
        if (reference == null || reference.from != Menu.ACTION || reference.applied < 0 ||
                reference.busy >= 0 || reference.ready >= 0 || !reference.surface.equals(surface()) ||
                !within(current.frame, reference.before, MAX_PROBE_BUSY) || !pictureCurrent()) return false;
        Sample shown = picture.sample;
        return current.nativeState.ready == null && current.nativeState.visible == Menu.ACTION &&
                shown.settled && shown.context == current.context && shown.frame >= reference.applied &&
                shown.nativeState.ready == null && shown.nativeState.visible == Menu.ACTION;
    }

    private void startReference(long before, Menu from, int mask, boolean probe) {
        reference = new Reference(surface(), from, before, NO_FRAME, NO_FRAME, NO_FRAME, probe);
        note("reference dispatched frame=" + before + " mask=" + mask);
    }

    private void submitBusy(long before) {
        reference = reference.withBusy(before + 1);
        note("reference busy dispatched frame=" + before + " mask=" + B + " shown=" + picture.sample.frame);
    }

    private void nativeReference(long before, int mask) {
        if (qualified() || lastApplied < 0 || mask == 0 || (lastApplied & mask) != 0) return;
        if (mask == B && lastApplied == 0 && busyWitness()) {
            submitBusy(before);
            return;
        }
        if (lastApplied == 0 && readyPicture(true) &&
                ((mask == A && current.nativeState.ready == Menu.ACTION && current.nativeState.cursor == 0) ||
                 (mask == B && current.nativeState.ready == Menu.MOVE))) {
            retireReference("new-ready-reference");
            startReference(before, current.nativeState.ready, mask, false);
        } else if (reference != null) {
            retireReference("unexpected-reference-input");
        }
    }

    /** Submitted edges survive work retirement. Only a verified forward step is applied evidence. */
    public synchronized Sample applied(long before, long after, Native state) {
        if (!active) return null;
        if (before < 0 || after < 0 || after != (before + 1) % 0x1_0000_0000L) {
            interrupt("no-forward-step");
            submitted = null;
            probeSubmitted = 0;
            lastApplied = -1;
            return null;
        }
        boolean edge = (dispatched & ~lastApplied) != 0 && lastApplied >= 0;
        lastApplied = dispatched;
        if (probeSubmitted != 0 && probeBefore == before) {
            note("reference probe applied frame=" + after + " mask=" + probeSubmitted + " edge=" + edge);
        }
        probeSubmitted = 0;
        if (submitted != null && submitted.before == before) {
            appliedAt = after;
            note("applied frame=" + after + " mask=" + dispatched + " edge=" + edge
                    + " gesture=" + submitted.intent.gesture);
            if (!edge) invalidate("no-new-edge");
        } else if (appliedAt >= 0 && dispatched == 0 && !released) {
            released = true;
            note("applied frame=" + after + " mask=0 release=true");
        }
        submitted = null;
        if (reference != null) advanceReference(before, after, state, edge);
        Native previous = current == null ? Native.UNKNOWN : current.nativeState;
        Sample sample = observe(after, state);
        // Ready results use one settled GBA frame to span changes near VBlank.
        return state.ready != null && !state.equals(previous)
                ? new Sample(session, context, after, state, false) : sample;
    }

    private void advanceReference(long before, long after, Native state, boolean edge) {
        if (reference.before == before) {
            int initial = reference.from == Menu.ACTION ? A : B;
            if (!edge || dispatched != initial) {
                retireReference("reference-edge-not-applied");
                return;
            }
            reference = reference.withApplied(after);
            note("reference applied frame=" + after + " mask=" + dispatched);
        }
        if (reference.busy == after) {
            if (!edge || dispatched != B) {
                retireReference("busy-edge-not-applied");
                return;
            }
            note("reference busy applied frame=" + after + " mask=" + B);
        }
        if (!within(after, reference.before, MAX_ACK) || state.visible == null) {
            retireReference("reference-transition-unavailable");
        } else if (reference.ready >= 0) {
            if (state.ready != reference.destination()) retireReference("destination-changed-before-swap");
        } else if (state.ready == reference.destination()) {
            if (reference.from == Menu.ACTION && reference.busy < 0) retireReference("move-before-busy-witness");
            else reference = reference.withReady(after);
        } else if (state.ready == reference.from) {
            retireReference("reference-did-not-transition");
        }
    }

    private Target target(float x, float y, long transform, int action, boolean requireCursor) {
        if (!readyPicture(requireCursor) || picture.transform != transform || !Float.isFinite(x) ||
                !Float.isFinite(y)) return null;
        Menu menu = current.nativeState.ready;
        int slot = slot(menu, x, y, current.nativeState.slots);
        if (action == BACK && (menu != Menu.MOVE || x < 0 || x >= 240 || y < 0 || y >= 160)) return null;
        if (action == FIGHT && (menu != Menu.ACTION || slot != 0)) return null;
        if (action == HOVER && slot < 0) return null;
        return new Target(menuLease(), action == BACK ? -1 : slot);
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
        if (lastApplied != 0 || press != null || !readyPicture(true)) return 0;
        Intent request = intent != null ? intent : hover;
        if (request == null || !request.target.lease.equals(menuLease())) return 0;
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
        if (key == A || key == B) {
            intent = null;
            hover = null;
        }
        pulse = new Pulse(request, key, expectedCursor, expectedMenu, frame);
        released = false;
        appliedAt = NO_FRAME;
        return key;
    }

    private boolean settlePulse(long frame) {
        if (appliedAt < 0 || !within(frame, pulse.before, MAX_ACK)) {
            retireGesture("pulse-timeout");
            return false;
        }
        if (!released || current.nativeState.ready == null) return false;
        Native state = current.nativeState;
        if (state.ready != pulse.expectedMenu || (pulse.expectedCursor >= 0 &&
                (state.cursor != pulse.expectedCursor || !pulse.intent.target.lease.equals(menuLease())))) {
            retireGesture("unexpected-ack");
            return false;
        }
        if (!readyPicture(true) || picture.sample.frame < appliedAt) return false;
        note("ack mask=" + pulse.mask + " applied=" + appliedAt + " native=" + frame
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

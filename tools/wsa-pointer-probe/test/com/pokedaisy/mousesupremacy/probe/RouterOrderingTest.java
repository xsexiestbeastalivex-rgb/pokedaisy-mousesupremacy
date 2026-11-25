package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;

import com.pokedaisy.mousesupremacy.probe.PointerState.Action;
import com.pokedaisy.mousesupremacy.probe.PointerState.Sample;
import com.pokedaisy.mousesupremacy.probe.ProbeInput.Decision;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The schedule prelabels which contact can produce each edge. The oracle uses only public decisions. */
public final class RouterOrderingTest {
    private static final int WSA_SOURCE = 0x1002;
    private static final int MOUSE_SOURCE = 0x2002;
    private static final Action[] PAIR = {DOWN, BUTTON_PRESS, UP, BUTTON_RELEASE};
    private static int sequences;
    private static int callbacks;
    private static String scenario;

    public static void main(String[] args) {
        long start = System.nanoTime();
        staleSchedules();
        lifecycleOrders();
        conflicts();
        invalidTimes();
        sameSlotBoundary();
        System.out.printf("PASS router ordering: %d sequences, %d callbacks, %d ms%n",
                sequences, callbacks, (System.nanoTime() - start) / 1_000_000);
    }

    private static void staleSchedules() {
        Set<List<Action>> schedules = new LinkedHashSet<>();
        permute(new ArrayList<>(), schedules);
        for (int order = 0; order < 4; order++) {
            for (int slot : new int[]{1, 2}) {
                for (long terminalTime : new long[]{120, 500}) {
                    for (int position = 0; position <= 4; position++) {
                        for (var schedule : schedules) {
                            var router = fresh("stale order=" + order + " slot=" + slot
                                    + " end=" + terminalTime + " position=" + position + " " + schedule);
                            // Establish a newer reference before the injection. Its slot can differ from all later slots.
                            gesture(router, 3, 150, 170, order);
                            for (int step = 0; step <= 4; step++) {
                                if (step == position) {
                                    for (Action action : schedule) {
                                        ignored(send(router, slot, action, 100,
                                                terminal(action) ? terminalTime : 100));
                                    }
                                }
                                if (step < 4) validEdge(router, 1, 200, 220, order, step);
                            }
                            gesture(router, 2, 240, 260, order);
                        }
                    }
                }
            }
        }
    }

    private static void permute(List<Action> prefix, Set<List<Action>> schedules) {
        if (prefix.size() == PAIR.length) {
            schedules.add(List.copyOf(prefix));
            for (int i = 0; i < PAIR.length; i++) {
                var missing = new ArrayList<>(prefix);
                missing.remove(i);
                schedules.add(missing);
                var duplicate = new ArrayList<>(prefix);
                duplicate.add(i, prefix.get(i));
                schedules.add(duplicate);
            }
            return;
        }
        for (Action action : PAIR) {
            if (prefix.contains(action)) continue;
            prefix.add(action);
            permute(prefix, schedules);
            prefix.remove(prefix.size() - 1);
        }
    }

    private enum Boundary { COMPLETE, CANCEL, FOCUS, LAYOUT, DETACH, CAPTURE_RESET, TERMINAL_FIRST,
        LOST_OPENING_HALF, LOST_TERMINAL_HALF }

    private static void lifecycleOrders() {
        for (Boundary boundary : Boundary.values()) {
            for (int slot : new int[]{1, 2}) {
                for (long down : new long[]{100, 220, 240}) {
                    for (int order = 0; order < 4; order++) {
                        var router = fresh(boundary + " slot=" + slot + " down=" + down + " order=" + order);
                        close(router, boundary, order);
                        if (down > 220) gesture(router, slot, down, 260, order);
                        else {
                            for (Action action : PAIR) ignored(send(router, slot, action, down,
                                    terminal(action) ? down + 10 : down));
                        }
                        for (Action action : PAIR) ignored(send(router, slot, action, 200,
                                terminal(action) ? 500 : 200));
                        gesture(router, 3, 300, 320, order);
                    }
                }
            }
        }
    }

    private static void close(ProbeInput r, Boundary boundary, int order) {
        if (boundary == Boundary.TERMINAL_FIRST) {
            ignored(send(r, 1, BUTTON_RELEASE, 200, 220));
            return;
        }
        validEdge(r, 1, 200, 220, order, 0);
        if (boundary == Boundary.LOST_OPENING_HALF) {
            ignored(send(r, 1, UP, 200, 220));
            return;
        }
        validEdge(r, 1, 200, 220, order, 1);
        switch (boundary) {
            case COMPLETE -> {
                validEdge(r, 1, 200, 220, order, 2);
                validEdge(r, 1, 200, 220, order, 3);
            }
            case CANCEL -> cancel(send(r, 1, CANCEL, 200, 220), 0, 1);
            case FOCUS, LAYOUT -> {
                String reason = boundary == Boundary.FOCUS ? "focus-loss" : "layout-change";
                r.fenceOpenings(220);
                check(r.invalidate(1, 220, reason, true).cancelled() == 1, "lifecycle cancel");
            }
            case DETACH -> {
                validEdge(r, 1, 200, 220, order, 2);
                r.detach(1);
                r.attach(1);
            }
            case CAPTURE_RESET -> {
                for (int slot = 1; slot <= 3; slot++) {
                    r.detach(slot);
                    r.attach(slot);
                }
                r.fenceOpenings(220);
            }
            case LOST_TERMINAL_HALF -> {
                validEdge(r, 1, 200, 220, order, 2);
                cancel(send(r, 1, CANCEL, 200, 220), 0, 1);
            }
            default -> throw new AssertionError(boundary);
        }
    }

    private enum Conflict { EQUAL_ALIAS, NEWER_ALIAS, STANDARD, DIRECT, UNKNOWN_DOWN, UNKNOWN_CLOCK }

    private static void conflicts() {
        for (Conflict conflict : Conflict.values()) {
            for (int state = 1; state <= 3; state++) {
                for (int order = 0; order < 4; order++) {
                    var r = fresh(conflict + " after " + state + " callbacks, order=" + order);
                    for (int step = 0; step < state; step++) validEdge(r, 1, 200, 220, order, step);
                    switch (conflict) {
                        case EQUAL_ALIAS -> cancel(send(r, 2, DOWN, 200, 200), 1, state == 1 ? 0 : 1);
                        case NEWER_ALIAS -> cancel(send(r, 2, DOWN, 230, 230), 1, state == 1 ? 0 : 1);
                        case STANDARD -> {
                            var decision = external(r, 2, DOWN, 230, 230, 1, MOUSE_SOURCE, 3);
                            check(decision.result() != null && decision.result().pressed() == 1
                                    && decision.result().click() == 0, "standard edge preserved");
                            cancel(decision, 1, state == 1 ? 0 : 1);
                            var up = external(r, 2, UP, 230, 250, 0, MOUSE_SOURCE, 3);
                            check(up.result() != null && up.result().released() == 1, "standard release");
                        }
                        case DIRECT -> {
                            cancel(external(r, 2, DOWN, 230, 230, 0, WSA_SOURCE, 1), 1, state == 1 ? 0 : 1);
                            ignored(external(r, 2, UP, 230, 250, 0, WSA_SOURCE, 1));
                        }
                        case UNKNOWN_DOWN -> cancel(send(r, 2, CANCEL, 0, 230), 1, state == 1 ? 0 : 1);
                        case UNKNOWN_CLOCK -> cancel(send(r, 2, CANCEL, 0, 0), 1, state == 1 ? 0 : 1);
                    }
                    for (Action action : PAIR) ignored(send(r, 1, action, 200, terminal(action) ? 500 : 200));
                    for (Action action : PAIR) ignored(send(r, 2, action, 200, terminal(action) ? 500 : 200));
                    if (conflict == Conflict.NEWER_ALIAS) {
                        for (int slot : new int[]{2, 3}) {
                            for (Action action : PAIR) ignored(send(r, slot, action, 230,
                                    terminal(action) ? 260 : 230));
                        }
                    }
                    gesture(r, 3, 300, 320, order);
                }
            }
        }
    }

    private static void invalidTimes() {
        for (int slot : new int[]{1, 2}) {
            for (Action action : List.of(DOWN, BUTTON_PRESS, MOVE, UP, BUTTON_RELEASE, CANCEL)) {
                var r = fresh("malformed " + action + " slot=" + slot);
                validEdge(r, 1, 200, 220, 0, 0);
                validEdge(r, 1, 200, 220, 0, 1);
                ignored(send(r, slot, action, 9000, 190));
                validEdge(r, 1, 200, 220, 0, 2);
                validEdge(r, 1, 200, 220, 0, 3);
                gesture(r, slot, 240, 260, 0);
            }
        }

        var r = fresh("conventional/direct stale blocker");
        gesture(r, 1, 200, 220, 0);
        ignored(external(r, 2, DOWN, 100, 100, 1, MOUSE_SOURCE, 3));
        ignored(external(r, 2, DOWN, 100, 100, 0, WSA_SOURCE, 1));
        ignored(external(r, 2, DOWN, 100, 100, 0, WSA_SOURCE, 2));
        gesture(r, 3, 240, 260, 0);

        r = fresh("applicable cancel with invalid future down time");
        validEdge(r, 1, 200, 220, 0, 0);
        validEdge(r, 1, 200, 220, 0, 1);
        cancel(send(r, 2, CANCEL, 9000, 210), 1, 1);
        gesture(r, 2, 240, 260, 0);

        r = fresh("standard cancellation with invalid future down time");
        check(external(r, 1, DOWN, 200, 200, 1, MOUSE_SOURCE, 3).result().pressed() == 1, "standard press");
        cancel(external(r, 1, CANCEL, 9000, 210, 0, MOUSE_SOURCE, 3), 0, 1);
        check(external(r, 1, DOWN, 240, 240, 1, MOUSE_SOURCE, 3).result().pressed() == 1, "standard recovery");
    }

    private static void sameSlotBoundary() {
        for (int order = 0; order < 4; order++) {
            var r = fresh("same-slot opening tied with release-pending, order=" + order);
            for (int step = 0; step < 3; step++) validEdge(r, 1, 200, 220, order, step);
            cancel(send(r, 1, DOWN, 220, 220), 0, 1);
            ignored(send(r, 1, BUTTON_PRESS, 220, 220));
            for (Action action : PAIR) ignored(send(r, 1, action, 200, terminal(action) ? 500 : 200));
            gesture(r, 1, 221, 240, order);
        }

        var r = fresh("direct touch missing terminal, newer orphan up");
        ignored(external(r, 1, DOWN, 100, 100, 0, WSA_SOURCE, 1));
        ignored(external(r, 1, UP, 200, 220, 0, WSA_SOURCE, 1));
        gesture(r, 2, 300, 320, 0);
    }

    private static ProbeInput fresh(String name) {
        scenario = name;
        sequences++;
        var r = new ProbeInput(3);
        for (int slot = 1; slot <= 3; slot++) r.attach(slot);
        return r;
    }

    private static void gesture(ProbeInput r, int slot, long down, long end, int order) {
        for (int step = 0; step < 4; step++) validEdge(r, slot, down, end, order, step);
    }

    private static void validEdge(ProbeInput r, int slot, long down, long end, int order, int step) {
        int flip = step < 2 ? order & 1 : (order >> 1) & 1;
        var decision = send(r, slot, PAIR[step ^ flip], down, step < 2 ? down : end);
        if (step == 0) {
            ignored(decision);
            return;
        }
        var result = decision.result();
        check(decision.displacedSlot() == 0 && decision.displaced() == null && result != null,
                "unexpected displacement or missing edge " + decision);
        check(result.pressed() == (step == 1 ? 1 : 0) && result.held() == (step == 1 ? 1 : 0)
                && result.released() == (step == 3 ? 1 : 0) && result.click() == (step == 3 ? 1 : 0)
                && result.cancelled() == 0 && (step != 2 || result.phase().equals("release-pending")),
                "wrong edge at step " + step + " " + decision);
    }

    private static Decision send(ProbeInput r, int slot, Action action, long down, long time) {
        int button = action == BUTTON_PRESS || action == BUTTON_RELEASE ? 1 : 0;
        int mask = action == DOWN || action == BUTTON_PRESS || action == MOVE ? 1 : 0;
        callbacks++;
        return r.observe(new Sample(action, "A", 0, 0, down, time, 0, 1, button, mask, true, false),
                slot, WSA_SOURCE, 1, true, 10);
    }

    private static Decision external(ProbeInput r, int slot, Action action, long down, long time,
                                     int mask, int source, int tool) {
        callbacks++;
        return r.observe(new Sample(action, "A", 0, 0, down, time, 0, 1, 0, mask, true, false),
                slot, source, tool, true, 10);
    }

    private static boolean terminal(Action action) { return action == UP || action == BUTTON_RELEASE; }

    private static void ignored(Decision d) {
        check(d.result() == null && d.displacedSlot() == 0 && d.displaced() == null,
                "expected no normalized edges or displacement " + d);
    }

    private static void cancel(Decision d, int displacedSlot, int button) {
        var result = displacedSlot == 0 ? d.result() : d.displaced();
        check(d.displacedSlot() == displacedSlot && result != null && result.cancelled() == button
                && result.held() == 0 && result.click() == 0 && result.pressed() == 0
                && result.released() == 0, "expected cancellation " + d);
        if (displacedSlot == 0) check(d.displaced() == null, "unexpected second cancellation " + d);
        else if (!ProbeInput.STANDARD.equals(d.interpretation())) {
            check(d.result() == null, "translated contender emitted an edge " + d);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(scenario + ": " + message);
    }
}

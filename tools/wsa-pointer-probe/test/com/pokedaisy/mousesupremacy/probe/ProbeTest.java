package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;
import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;
import static com.pokedaisy.mousesupremacy.probe.Trace.fields;

import com.pokedaisy.mousesupremacy.probe.PointerState.Action;
import com.pokedaisy.mousesupremacy.probe.PointerState.Sample;

import java.util.List;
import java.util.Map;

public final class ProbeTest {
    public static void main(String[] args) {
        run("recording is opt-in and resets", ProbeTest::optIn);
        run("motion coalesces without crossing boundaries", ProbeTest::coalescing);
        run("bounded trace retains boundaries before motion", ProbeTest::bounds);
        run("move index stays bounded under churn", ProbeTest::keyChurn);
        run("pre-press hover and region crossings", ProbeTest::hover);
        run("duplicate representations in either order", ProbeTest::duplicates);
        run("secondary, middle and simultaneous buttons", ProbeTest::buttons);
        run("in-place hover exit and spatial cancellation", ProbeTest::spatialCancellation);
        run("focus, resize, device removal and ACTION_CANCEL", ProbeTest::cancellation);
        run("ambiguous masks, hover and scroll do not invent edges", ProbeTest::noInvention);
        run("loss and recovery of release state", ProbeTest::lostRelease);
        System.out.println("11 focused recorder and standard-mouse JVM tests passed.");
    }

    private static void optIn() {
        Trace trace = new Trace(5);
        trace.add(fields("name", "off"), null);
        expect(0, events(trace.stop()).size());
        trace.start();
        trace.add(fields("name", "start"), null);
        trace.add(fields("name", "end"), null);
        var stopped = trace.stop();
        expect(1, stopped.get("schemaVersion"));
        expect(List.of(1L, 2L), sequences(stopped));
        trace.add(fields("name", "off again"), null);
        trace.start();
        trace.add(fields("name", "fresh"), null);
        expect(List.of(1L), sequences(trace.stop()));
        expect(List.of(1L, 2L), sequences(stopped));
    }

    private static void coalescing() {
        var trace = new Trace(12);
        trace.start();
        trace.add(fields("kind", "hover-enter"), null);
        trace.add(fields("device", 1, "x", 3), "activity");
        trace.add(fields("device", 1, "x", 3), "view");
        trace.add(fields("device", 1, "x", 9), "activity");
        trace.add(fields("device", 2, "x", 9), "activity");
        trace.add(fields("kind", "button-down"), null);
        trace.add(fields("device", 2, "x", 11), "activity");
        trace.add(fields("kind", "end"), null);
        var capture = trace.stop();
        expect(List.of(1L, 3L, 4L, 5L, 6L, 7L, 8L), sequences(capture));
        expect(1L, capture.get("coalescedMoves"));
    }

    private static void bounds() {
        var trace = new Trace(3);
        trace.start();
        trace.add(fields("kind", "start"), null);
        trace.add(fields("kind", "move"), "activity");
        trace.add(fields("kind", "focus"), null);
        trace.add(fields("kind", "scroll"), null);
        trace.add(fields("kind", "move"), "activity");
        trace.add(fields("kind", "end"), null);
        var capture = trace.stop();
        expect(6L, capture.get("observedCount"));
        expect(List.of(3L, 4L, 6L), sequences(capture));
        expect(2L, capture.get("discardedMoves"));
        expect(1L, capture.get("discardedBoundaries"));
    }

    private static void keyChurn() {
        var trace = new Trace(8);
        trace.start();
        trace.add(fields("kind", "start"), null);
        trace.add(fields("kind", "cancellation"), null);
        for (int i = 0; i < 10_000; i++) {
            trace.add(fields("kind", "move"), "key" + i);
            if (trace.indexedMoves() > 6) throw new AssertionError("move index exceeds retained moves");
        }
        trace.add(fields("kind", "end"), null);
        var capture = trace.stop();
        expect(8, events(capture).size());
        expect("cancellation", events(capture).get(1).get("kind"));
        expect(9995L, capture.get("discardedMoves"));
        expect(0L, capture.get("discardedBoundaries"));
        expect(0, trace.indexedMoves());

        trace.start();
        for (int i = 0; i < 8; i++) trace.add(fields("kind", "layout"), null);
        for (int i = 0; i < 100; i++) trace.add(fields("kind", "move"), "dropped" + i);
        expect(0, trace.indexedMoves());
        trace.add(fields("kind", "end"), null);
        var truncated = trace.stop();
        expect(1L, truncated.get("discardedBoundaries"));
        expect(100L, truncated.get("discardedMoves"));
        expect(109L, truncated.get("observedCount"));
    }

    private static void hover() {
        var state = new PointerState();
        expect("A", state.observe(s(HOVER_ENTER, "A", 100, 100, 0, 0)).region());
        var first = state.observe(s(HOVER_MOVE, "A", 100, 110, 0, 0));
        expect(true, first.firstUnpressedMove());
        expect(true, first.beforeFirstPress());
        var cross = state.observe(s(HOVER_MOVE, "B", 100, 120, 0, 0));
        expect(true, cross.regionChanged());
        expect(false, cross.firstUnpressedMove());
        expect(OUTSIDE, state.observe(s(HOVER_EXIT, OUTSIDE, 100, 130, 0, 0)).region());

        var outsideClick = new PointerState();
        outsideClick.observe(s(DOWN, OUTSIDE, 100, 100, 0, 1));
        expect(false, outsideClick.observe(s(HOVER_MOVE, "A", 110, 110, 0, 0)).beforeFirstPress());
    }

    private static void duplicates() {
        for (boolean reverse : new boolean[]{false, true}) {
            var p = new PointerState();
            var down = s(DOWN, "A", 100, 100, 0, 1);
            var press = s(BUTTON_PRESS, "A", 100, 100, 1, 1);
            expect(1, p.observe(reverse ? press : down).pressed());
            expect(0, p.observe(reverse ? down : press).pressed());
            var up = s(UP, "A", 100, 130, 0, 0);
            var release = s(BUTTON_RELEASE, "A", 100, 130, 1, 0);
            expect(1, p.observe(reverse ? release : up).released());
            expect(null, p.observe(reverse ? up : release));
            expect(null, p.observe(press));
            expect(1, p.observe(s(DOWN, "A", 200, 200, 0, 1)).pressed());
        }
    }

    private static void buttons() {
        for (int button : new int[]{2, 4}) {
            var p = new PointerState();
            p.observe(s(DOWN, "A", 100, 100, 0, 1));
            var press = p.observe(s(BUTTON_PRESS, "A", 100, 110, button, 1 | button));
            expect(button, press.pressed());
            expect(1 | button, press.held());
            var release = p.observe(s(BUTTON_RELEASE, "A", 100, 120, button, 1));
            expect(button, release.released());
            expect(1, release.held());
            expect(1, p.observe(s(UP, "A", 100, 130, 0, 0)).released());
            expect(button, p.observe(s(BUTTON_PRESS, "B", 200, 200, button, button)).pressed());
            expect(button, p.observe(s(BUTTON_RELEASE, "B", 200, 220, button, 0)).released());
        }
    }

    private static void spatialCancellation() {
        var p = new PointerState();
        p.observe(s(HOVER_MOVE, "A", 90, 90, 0, 0));
        expect("hover-exit-inside", p.observe(s(HOVER_EXIT, "A", 90, 95, 0, 0)).phase());
        p.observe(s(DOWN, "A", 100, 100, 0, 1));
        expect(1, p.observe(s(MOVE, "B", 100, 110, 0, 1)).cancelled());
        expect(null, p.observe(s(BUTTON_PRESS, "B", 100, 111, 1, 1)));
        expect(null, p.observe(s(UP, "A", 100, 120, 0, 0)));
        expect(1, p.observe(s(DOWN, "A", 200, 200, 0, 1)).pressed());
        expect(1, p.observe(s(MOVE, OUTSIDE, 200, 210, 0, 1)).cancelled());
        expect(null, p.observe(s(BUTTON_PRESS, "A", 200, 220, 1, 1)));
    }

    private static void cancellation() {
        for (String reason : List.of("focus-loss", "layout-change", "view-detached", "device-removed")) {
            var p = new PointerState();
            p.observe(s(DOWN, "A", 100, 100, 0, 1));
            expect(1, p.invalidate(110, reason).cancelled());
            expect(null, p.observe(s(DOWN, "A", 110, 110, 0, 1)));
            expect(null, p.observe(s(BUTTON_PRESS, "A", 100, 120, 1, 1)));
            expect(null, p.observe(s(UP, "A", 100, 130, 0, 0)));
            expect(1, p.observe(s(DOWN, "B", 200, 200, 0, 1)).pressed());
            expect(1, p.observe(s(CANCEL, "B", 200, 210, 0, 0)).cancelled());
            expect(0, p.observe(s(CANCEL, "B", 200, 220, 0, 0)).cancelled());
            expect(1, p.observe(s(DOWN, "B", 300, 300, 0, 1)).pressed());
        }
    }

    private static void noInvention() {
        var p = new PointerState();
        expect(0, p.observe(s(DOWN, "A", 100, 100, 0, 0)).pressed());
        expect(0, p.observe(s(DOWN, "A", 110, 110, 0, 3)).pressed());
        expect(0, p.observe(s(BUTTON_PRESS, "A", 120, 120, 8, 8)).pressed());
        expect(0, p.observe(s(BUTTON_RELEASE, "A", 120, 130, 1, 0)).released());
        expect(0, p.observe(s(HOVER_MOVE, "A", 140, 140, 0, 1)).held());
        expect(0, p.observe(s(SCROLL, "A", 150, 150, 0, 1)).held());
    }

    private static void lostRelease() {
        var p = new PointerState();
        p.observe(s(DOWN, "A", 100, 100, 0, 1));
        p.observe(s(BUTTON_PRESS, "A", 100, 110, 2, 3));
        var up = p.observe(s(UP, "A", 100, 120, 0, 0));
        expect(3, up.released());
        expect(0, up.held());
        expect(0, up.click());
        p.observe(s(DOWN, "A", 200, 200, 0, 1));
        p.observe(s(BUTTON_PRESS, "A", 200, 210, 2, 3));
        var observed = p.observe(s(BUTTON_RELEASE, "A", 200, 220, 2, 0));
        expect(2, observed.released());
        expect(1, observed.cancelled());
        expect(0, observed.held());
        p.observe(s(DOWN, "A", 300, 300, 0, 1));
        expect(1, p.observe(s(MOVE, "A", 300, 310, 0, 0)).cancelled());
        expect(null, p.observe(s(BUTTON_PRESS, "A", 300, 320, 1, 1)));
        p.observe(s(DOWN, "A", 400, 400, 0, 1));
        var fresh = p.observe(s(DOWN, "B", 500, 500, 0, 1));
        expect(1, fresh.cancelled());
        expect(1, fresh.pressed());
        expect(null, p.observe(s(UP, "A", 400, 510, 0, 0)));
        expect(1, p.observe(s(UP, "B", 500, 520, 0, 0)).released());
        expect(null, p.observe(s(DOWN, "B", 510, 510, 0, 1)));
        p.observe(s(DOWN, "A", 600, 600, 0, 1));
        p.observe(s(BUTTON_PRESS, "A", 600, 610, 2, 3));
        expect(1, p.observe(s(BUTTON_RELEASE, "A", 600, 620, 1, 2)).released());
        expect(0, p.observe(s(BUTTON_PRESS, "A", 600, 620, 1, 3)).pressed());
        expect(2, p.observe(s(UP, "A", 600, 650, 0, 0)).released());
    }

    private static Sample s(Action a, String hit, long down, long event, int button, int mask) {
        return new Sample(a, hit, 0, 0, down, event, 0, 1, button, mask, true, false);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> events(Map<String, Object> trace) {
        return (List<Map<String, Object>>) trace.get("events");
    }

    private static List<Object> sequences(Map<String, Object> trace) {
        return events(trace).stream().map(event -> event.get("seq")).toList();
    }

    private static void run(String name, Runnable test) {
        test.run();
        System.out.println("PASS " + name);
    }

    private static void expect(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}

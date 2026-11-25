package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;
import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;
import static com.pokedaisy.mousesupremacy.probe.Trace.fields;

import java.util.List;
import java.util.Map;

public final class ProbeTest {
    public static void main(String[] args) {
        run("recording is opt-in and resets", ProbeTest::optIn);
        run("motion coalesces without crossing boundaries", ProbeTest::coalescing);
        run("bounded trace retains boundaries before motion", ProbeTest::bounds);
        run("pre-press hover and region crossings", ProbeTest::hover);
        run("duplicate representations in either order", ProbeTest::duplicates);
        run("secondary, middle and simultaneous buttons", ProbeTest::buttons);
        run("in-place hover exit and spatial cancellation", ProbeTest::spatialCancellation);
        run("focus, resize, device removal and ACTION_CANCEL", ProbeTest::cancellation);
        run("ambiguous masks, hover and scroll do not invent edges", ProbeTest::noInvention);
        System.out.println("9 focused JVM tests passed.");
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

    private static void hover() {
        var state = new PointerState();
        expect("A", state.observe(HOVER_ENTER, "A", 0, 0, true).region());
        var first = state.observe(HOVER_MOVE, "A", 0, 0, true);
        expect(true, first.firstUnpressedMove());
        expect(true, first.beforeFirstPress());
        var cross = state.observe(HOVER_MOVE, "B", 0, 0, true);
        expect(true, cross.regionChanged());
        expect(false, cross.firstUnpressedMove());
        expect(OUTSIDE, state.observe(HOVER_EXIT, OUTSIDE, 0, 0, true).region());

        var outsideClick = new PointerState();
        outsideClick.observe(DOWN, OUTSIDE, 0, 1, true);
        expect(false, outsideClick.observe(HOVER_MOVE, "A", 0, 0, true).beforeFirstPress());
    }

    private static void duplicates() {
        var state = new PointerState();
        expect(1, state.observe(DOWN, "A", 0, 1, true).pressed());
        expect(0, state.observe(BUTTON_PRESS, "A", 1, 1, true).pressed());
        expect(1, state.observe(BUTTON_RELEASE, "A", 1, 0, true).released());
        expect(0, state.observe(UP, "A", 0, 0, true).released());
        expect(1, state.observe(BUTTON_PRESS, "B", 1, 1, true).pressed());
        expect(0, state.observe(DOWN, "B", 0, 1, true).pressed());
        expect(1, state.observe(UP, "B", 0, 0, true).released());
        expect(0, state.observe(BUTTON_RELEASE, "B", 1, 0, true).released());
    }

    private static void buttons() {
        for (int button : new int[]{2, 4}) {
            var state = new PointerState();
            state.observe(DOWN, "A", 0, 1, true);
            var press = state.observe(BUTTON_PRESS, "A", button, 1 | button, true);
            expect(button, press.pressed());
            expect(1 | button, press.held());
            var release = state.observe(BUTTON_RELEASE, "A", button, 1, true);
            expect(button, release.released());
            expect(1, release.held());
        }
    }

    private static void spatialCancellation() {
        var state = new PointerState();
        state.observe(HOVER_MOVE, "A", 0, 0, true);
        expect("hover-exit-inside", state.observe(HOVER_EXIT, "A", 0, 0, true).phase());
        state.observe(DOWN, "A", 0, 1, true);
        expect(1, state.observe(MOVE, "B", 0, 1, true).cancelled());
        expect(0, state.observe(UP, "A", 0, 0, true).released());
        state.observe(DOWN, "A", 0, 1, true);
        expect(1, state.observe(MOVE, OUTSIDE, 0, 1, true).cancelled());
    }

    private static void cancellation() {
        var state = new PointerState();
        for (String reason : List.of("focus-loss", "layout-change", "view-detached", "device-removed")) {
            state.observe(DOWN, "A", 0, 1, true);
            expect(1, state.cancel(reason).cancelled());
            expect(0, state.observe(MOVE, "A", 0, 1, false).held());
            expect(0, state.observe(MOVE, "A", 0, 1, true).held());
            expect(0, state.observe(UP, "A", 0, 0, true).released());
        }
        state.observe(DOWN, "B", 0, 1, true);
        expect(1, state.observe(CANCEL, "B", 0, 0, true).cancelled());
        expect(0, state.observe(CANCEL, "B", 0, 0, true).cancelled());
        expect(1, state.observe(DOWN, "B", 0, 1, true).pressed());
    }

    private static void noInvention() {
        var state = new PointerState();
        expect(0, state.observe(DOWN, "A", 0, 0, true).pressed());
        expect(0, state.observe(DOWN, "A", 0, 3, true).pressed());
        expect(0, state.observe(BUTTON_PRESS, "A", 8, 8, true).pressed());
        expect(0, state.observe(BUTTON_RELEASE, "A", 1, 0, true).released());
        expect(0, state.observe(HOVER_MOVE, "A", 0, 1, true).held());
        expect(0, state.observe(SCROLL, "A", 0, 1, true).held());
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
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}

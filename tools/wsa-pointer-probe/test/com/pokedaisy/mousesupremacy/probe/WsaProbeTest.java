package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;
import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class WsaProbeTest {
    public static void main(String[] args) throws Exception {
        replay(Path.of(args[0]));
        admission();
        isolation();
        ordering();
        cancellation();
        dragAndClick();
        System.out.println("5 focused WSA tests and 12 sanitized physical replay cases passed.");
    }

    private record Expected(List<Integer> presses, List<Integer> releases, List<Integer> cancels,
                            List<Integer> clicks, boolean hover, boolean drag) {}

    private static void replay(Path file) throws Exception {
        var cases = new LinkedHashMap<String, List<String[]>>();
        for (String row : Files.readAllLines(file)) {
            if (row.isBlank() || row.startsWith("#")) continue;
            var fields = row.split("\t");
            cases.computeIfAbsent(fields[0], ignored -> new ArrayList<>()).add(fields);
        }
        expect(12, cases.size());
        var allButtons = List.of(1, 2, 4);
        var left = List.of(1);
        List<Integer> none = List.of();
        for (var entry : cases.entrySet()) {
            String name = entry.getKey();
            var expected = switch (name) {
                case "windowed-hover", "maximized-hover" -> new Expected(none, none, none, none, true, false);
                case "windowed-buttons", "maximized-buttons" ->
                        new Expected(allButtons, allButtons, none, allButtons, true, false);
                case "windowed-vertical-swipes", "maximized-wheel" ->
                        new Expected(none, none, none, none, false, false);
                case "windowed-horizontal-swipes" -> new Expected(none, none, none, none, true, false);
                case "windowed-drag" -> new Expected(left, left, none, none, true, true);
                case "windowed-outside" -> new Expected(left, none, left, none, false, true);
                case "windowed-focus" -> new Expected(left, none, left, none, false, false);
                case "maximized-focus-cancel" -> new Expected(List.of(1, 1), left, left, left, false, true);
                case "maximized-resize" -> new Expected(left, left, none, left, true, false);
                default -> throw new AssertionError(name);
            };
            var pointer = new WsaPointer(10);
            var pressed = new ArrayList<Integer>();
            var released = new ArrayList<Integer>();
            var cancelled = new ArrayList<Integer>();
            var clicks = new ArrayList<Integer>();
            boolean hover = false;
            boolean drag = false;
            for (var fields : entry.getValue()) {
                PointerState.Result result;
                if ("layout".equals(fields[1]) || "window-focus".equals(fields[1])) {
                    if ("window-focus".equals(fields[1]) && bit(fields[2])) continue;
                    result = pointer.cancel(fields[1]);
                } else {
                    expect(true, WsaPointer.accepts(true, number(fields, 13), number(fields, 14), 1));
                    expect(false, bit(fields[15]));
                    result = pointer.observe(new WsaPointer.Sample(PointerState.Action.valueOf(fields[1]),
                            fields[2], number(fields, 3), number(fields, 4), number(fields, 5), number(fields, 6),
                            number(fields, 7), number(fields, 8), number(fields, 9), number(fields, 10),
                            bit(fields[11]), bit(fields[12])));
                }
                if (result == null) continue;
                if (result.pressed() != 0) pressed.add(result.pressed());
                if (result.released() != 0) released.add(result.released());
                if (result.cancelled() != 0) cancelled.add(result.cancelled());
                if (result.click() != 0) clicks.add(result.click());
                hover |= result.firstUnpressedMove();
                drag |= result.dragging();
            }
            var actual = new Expected(pressed, released, cancelled, clicks, hover, drag);
            if (!expected.equals(actual)) throw new AssertionError(name + ": " + expected + " vs " + actual);
            System.out.println("PASS replay " + name);
        }
    }

    private static void admission() {
        expect(false, WsaPointer.accepts(false, 0x1002, 1, 1));
        expect(false, WsaPointer.accepts(true, 0x1002, 1, 0));
        expect(false, WsaPointer.accepts(true, 0x2002, 3, 1));
        expect(false, WsaPointer.accepts(true, 0x1002, 2, 1));
        var state = new WsaPointer(10);
        expect(null, state.observe(sample(DOWN, "A", 100, 0, 0, 0)));
        expect(null, state.observe(sample(MOVE, "B", 100, 0, 0, 80)));
        expect(null, state.observe(sample(UP, "B", 100, 0, 0, 80)));
        expect(null, state.observe(sample(BUTTON_PRESS, "A", 200, 2, 2, 0)));
        expect(null, state.observe(sample(UP, "A", 200, 0, 0, 0)));
    }

    private static void isolation() {
        var first = new WsaPointer(10);
        var second = new WsaPointer(10);
        first.observe(sample(DOWN, "A", 100, 0, 1, 0));
        expect(null, second.observe(sample(BUTTON_PRESS, "A", 100, 1, 1, 0)));
        expect(1, first.observe(sample(BUTTON_PRESS, "A", 100, 1, 1, 0)).pressed());
        expect(null, second.observe(sample(UP, "A", 100, 0, 0, 0)));
        expect(null, first.observe(sample(UP, "A", 101, 0, 0, 0)));
        expect(null, first.observe(new WsaPointer.Sample(UP, "A", 0, 0, 100, 103, 1, 1, 0, 0, true, false)));
        expect(1, first.observe(sample(DOWN, "B", 102, 0, 0, 0)).cancelled());
        expect(null, first.observe(sample(BUTTON_RELEASE, "A", 100, 1, 0, 0)));
    }

    private static void ordering() {
        var state = new WsaPointer(10);
        expect(null, state.observe(sample(BUTTON_PRESS, "A", 100, 1, 1, 0)));
        expect(1, state.observe(sample(DOWN, "A", 100, 0, 1, 0)).pressed());
        expect(null, state.observe(sample(BUTTON_PRESS, "A", 100, 1, 1, 0)));
        expect(1, state.observe(sample(UP, "A", 100, 0, 0, 0)).released());
        expect(null, state.observe(sample(BUTTON_RELEASE, "A", 100, 1, 0, 0)));
        expect(null, state.observe(sample(DOWN, "A", 100, 0, 1, 0)));
        expect(null, state.observe(sample(BUTTON_PRESS, "A", 100, 1, 1, 0)));
    }

    private static void cancellation() {
        var state = new WsaPointer(10);
        press(state, 100);
        expect(1, state.cancel("layout-change").cancelled());
        expect(null, state.observe(sample(BUTTON_RELEASE, "A", 100, 1, 0, 0)));
        press(state, 200);
        expect(1, state.observe(sample(CANCEL, "A", 200, 0, 0, 0)).cancelled());
        press(state, 300);
        expect(1, state.observe(new WsaPointer.Sample(OTHER, "A", 0, 0, 300, 301,
                0, 2, 0, 1, true, false)).cancelled());
        press(state, 400);
        expect(1, state.observe(sample(MOVE, OUTSIDE, 400, 0, 1, 40)).cancelled());
        expect(null, state.observe(sample(UP, "A", 400, 0, 0, 0)));
    }

    private static void dragAndClick() {
        var state = new WsaPointer(10);
        press(state, 100);
        expect("drag-start", state.observe(sample(MOVE, "B", 100, 0, 1, 40)).phase());
        expect(0, state.observe(sample(UP, "A", 100, 0, 0, 0)).click());
        press(state, 200);
        state.observe(sample(MOVE, "B", 200, 0, 1, 2));
        expect(0, state.observe(sample(UP, "A", 200, 0, 0, 0)).click());
        press(state, 300);
        state.observe(new WsaPointer.Sample(MOVE, "A", 1, 0, 300, 301, 0, 1, 0, 1, true, true));
        expect(0, state.observe(sample(UP, "A", 300, 0, 0, 0)).click());
        press(state, 400);
        expect(1, state.observe(sample(UP, "A", 400, 0, 0, 0)).click());
    }

    private static void press(WsaPointer state, long time) {
        expect(null, state.observe(sample(DOWN, "A", time, 0, 1, 0)));
        expect(1, state.observe(sample(BUTTON_PRESS, "A", time, 1, 1, 0)).pressed());
    }

    private static WsaPointer.Sample sample(PointerState.Action action, String region, long down,
                                            int button, int buttons, int x) {
        return new WsaPointer.Sample(action, region, x, 0, down, down, 0, 1, button, buttons, true, false);
    }

    private static int number(String[] fields, int index) { return Integer.parseInt(fields[index]); }
    private static boolean bit(String value) { return "1".equals(value); }

    private static void expect(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}

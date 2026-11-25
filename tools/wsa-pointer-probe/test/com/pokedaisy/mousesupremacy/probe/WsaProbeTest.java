package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;
import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;
import com.pokedaisy.mousesupremacy.probe.PointerState.Action;
import com.pokedaisy.mousesupremacy.probe.PointerState.Result;
import com.pokedaisy.mousesupremacy.probe.PointerState.Sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class WsaProbeTest {
    public static void main(String[] args) throws Exception {
        var rows = Files.readAllLines(Path.of(args[0]));
        replay(rows, true);
        mutation("windowed-hover", rows.stream().map(row -> row.startsWith("windowed-hover\t")
                ? row.replace("\tB\t", "\tA\t") : row).toList());
        mutation("maximized-hover", rows.stream().map(row -> row.startsWith("maximized-hover\t")
                ? row.replace("\tB\t", "\tA\t") : row).toList());
        mutation("maximized-resize", rows.stream().filter(row -> !row.startsWith("maximized-resize\tlayout\t")).toList());
        admission();
        pairedOrdering();
        terminalFirst();
        reordered();
        missingRepresentations();
        contradictoryTerminals();
        spatialAndAmbiguousCancel();
        dragAndSlop();
        arbitration();
        staleAliasTerminalOrders();
        rejectedAliasCannotReopen();
        competingTranslatedAliases();
        deviceChurn();
        System.out.println("PASS 13 WSA/router synthetic cases, 12 physical-derived replays, 3 deliberate mutations rejected");
    }

    private static void replay(List<String> rows, boolean report) {
        var cases = new LinkedHashMap<String, List<String[]>>();
        for (String row : rows) {
            if (row.isBlank() || row.startsWith("#")) continue;
            var fields = row.split("\t");
            cases.computeIfAbsent(fields[0], ignored -> new ArrayList<>()).add(fields);
        }
        expect(12, cases.size());
        for (var entry : cases.entrySet()) {
            String name = entry.getKey();
            String expected = switch (name) {
                case "windowed-hover" -> "first:A hover:outside hover:B hover:outside";
                case "maximized-hover" -> "first:A hover:B hover:A";
                case "windowed-buttons", "maximized-buttons" -> "first:A p1:A pending:A r1:A click1:A "
                        + "p2:A pending:A r2:A click2:A p4:A pending:A r4:A click4:A";
                case "windowed-vertical-swipes", "maximized-wheel" -> "";
                case "windowed-horizontal-swipes" -> "first:A";
                case "windowed-drag" -> "first:A p1:A drag-start:A move:B pending:B r1:B";
                case "windowed-outside" -> "p1:A drag-start:A cancel1:spatial-exit";
                case "windowed-focus" -> "p1:A focus:0 cancel1:window-focus focus:1";
                case "maximized-focus-cancel" -> "p1:A drag-start:A focus:0 cancel1:window-focus focus:1 "
                        + "hover:A p1:A pending:A r1:A click1:A";
                case "maximized-resize" -> "focus:0 layout:0 focus:1 layout:1 first:A focus:0 focus:1 "
                        + "hover:A hover:B hover:A p1:A pending:A r1:A click1:A";
                default -> throw new AssertionError(name);
            };
            var pointer = new WsaPointer();
            var actual = new ArrayList<String>();
            for (var fields : entry.getValue()) {
                Result result;
                boolean marker = "layout".equals(fields[1]) || "window-focus".equals(fields[1]);
                if (marker) {
                    actual.add(("layout".equals(fields[1]) ? "layout:" : "focus:") + fields[2]);
                    if ("window-focus".equals(fields[1]) && bit(fields[2])) continue;
                    result = pointer.cancel(fields[1]);
                } else {
                    expect(true, WsaPointer.accepts(true, number(fields, 13), number(fields, 14), 1));
                    expect(false, bit(fields[15]));
                    result = pointer.observe(new Sample(Action.valueOf(fields[1]), fields[2],
                            number(fields, 3), number(fields, 4), number(fields, 5), number(fields, 6),
                            number(fields, 7), number(fields, 8), number(fields, 9), number(fields, 10),
                            bit(fields[11]), bit(fields[12])), 10);
                }
                if (result == null) continue;
                String region = ":" + result.region();
                if (result.firstUnpressedMove()) actual.add("first" + region);
                else if (result.phase().startsWith("HOVER") && result.regionChanged()) actual.add("hover" + region);
                if (result.pressed() > 0) actual.add("p" + result.pressed() + region);
                if (result.phase().equals("drag-start")) actual.add("drag-start" + region);
                else if (result.phase().equals("drag") && result.regionChanged()) actual.add("move" + region);
                if (result.phase().equals("release-pending")) actual.add("pending" + region);
                if (result.released() > 0) actual.add("r" + result.released() + region);
                if (result.cancelled() > 0) actual.add("cancel" + result.cancelled() + ":" + result.phase());
                if (result.click() > 0) actual.add("click" + result.click() + region);
            }
            String ordered = String.join(" ", actual);
            if (!expected.equals(ordered)) throw new AssertionError(name + ": expected [" + expected + "], got [" + ordered + "]");
            if (report) System.out.println("PASS replay " + name);
        }
    }

    private static void mutation(String affected, List<String> rows) {
        try {
            replay(rows, false);
        } catch (AssertionError error) {
            if (!error.getMessage().startsWith(affected + ":")) throw error;
            System.out.println("PASS mutation rejected " + affected);
            return;
        }
        throw new AssertionError("mutation escaped " + affected);
    }

    private static void admission() {
        expect(false, WsaPointer.accepts(false, 0x1002, 1, 1));
        expect(false, WsaPointer.accepts(true, 0x1002, 1, 0));
        expect(false, WsaPointer.accepts(true, 0x2002, 3, 1));
        expect(false, WsaPointer.accepts(true, 0x1002, 2, 1));
        var state = new WsaPointer();
        expect(null, feed(state, sample(DOWN, "A", 100, 100, 0, 0)));
        expect(null, feed(state, sample(MOVE, "B", 100, 130, 0, 0)));
        expect(null, feed(state, sample(UP, "B", 100, 140, 0, 0)));
        expect(null, feed(state, sample(BUTTON_PRESS, "A", 100, 100, 1, 1)));
    }

    private static void pairedOrdering() {
        for (int button : new int[]{1, 2, 4}) {
            for (boolean reverse : new boolean[]{false, true}) {
                var p = new WsaPointer();
                var down = sample(DOWN, "A", 100, 100, 0, button);
                var press = sample(BUTTON_PRESS, "A", 100, 100, button, button);
                expect(null, feed(p, reverse ? press : down));
                expect(null, feed(p, reverse ? press : down));
                expect(button, feed(p, reverse ? down : press).pressed());
                expect(null, feed(p, reverse ? down : press));
                var up = sample(UP, "A", 100, 120, 0, 0);
                var release = sample(BUTTON_RELEASE, "A", 100, 120, button, 0);
                var pending = feed(p, reverse ? up : release);
                expect(0, pending.held());
                expect(0, pending.click());
                expect(null, feed(p, reverse ? up : release));
                var done = feed(p, reverse ? release : up);
                expect(button, done.released());
                expect(button, done.click());
                expect(null, feed(p, up));
                expect(null, feed(p, down));
            }
        }
    }

    private static void terminalFirst() {
        for (Action terminal : new Action[]{UP, BUTTON_RELEASE}) {
            var p = new WsaPointer();
            expect(null, feed(p, sample(terminal, "A", 100, 120, terminal == UP ? 0 : 1, 0)));
            expect(null, feed(p, sample(DOWN, "A", 100, 100, 0, 1)));
            expect(null, feed(p, sample(BUTTON_PRESS, "A", 100, 100, 1, 1)));
            open(p, 200);
            expect(1, complete(p, 200, 220, "A").click());
        }
    }

    private static void reordered() {
        var p = new WsaPointer();
        open(p, 100);
        feed(p, sample(MOVE, "A", 100, 140, 0, 1));
        expect(1, feed(p, sample(UP, "A", 100, 120, 0, 0)).cancelled());
        expect(null, feed(p, sample(BUTTON_RELEASE, "A", 100, 120, 1, 0)));
        open(p, 200);
        expect(null, feed(p, sample(UP, "A", 100, 250, 0, 0)));
        expect(null, feed(p, sample(CANCEL, "A", 100, 260, 0, 0)));
        expect(1, complete(p, 200, 265, "A").click());
        expect(null, feed(p, sample(DOWN, "A", 250, 250, 0, 1)));
        expect(null, feed(p, sample(BUTTON_PRESS, "A", 250, 250, 1, 1)));
        expect(null, complete(p, 250, 270, "A"));
        open(p, 300);
        feed(p, sample(MOVE, "A", 300, 350, 0, 1));
        expect(null, feed(p, sample(DOWN, "A", 325, 325, 0, 1)));
        expect(1, complete(p, 300, 360, "A").click());
    }

    private static void missingRepresentations() {
        var p = new WsaPointer();
        for (Action opening : new Action[]{DOWN, BUTTON_PRESS}) {
            long down = opening == DOWN ? 100 : 200;
            expect(null, feed(p, sample(opening, "A", down, down, opening == DOWN ? 0 : 1, 1)));
            expect(null, feed(p, sample(UP, "A", down, down + 5, 0, 0)));
            expect(null, feed(p, sample(BUTTON_RELEASE, "A", down, down + 5, 1, 0)));
        }
        open(p, 300);
        var lonely = feed(p, sample(UP, "A", 300, 60_300, 0, 0));
        expect(0, lonely.held());
        expect(0, lonely.click());
        expect(1, feed(p, sample(DOWN, "A", 60_400, 60_400, 0, 1)).cancelled());
        expect(1, feed(p, sample(BUTTON_PRESS, "A", 60_400, 60_400, 1, 1)).pressed());
        expect(1, complete(p, 60_400, 120_400, "A").click()); // corroborated stationary long hold
        open(p, 120_500);
        expect(1, feed(p, sample(DOWN, "A", 120_600, 120_600, 0, 1)).cancelled());
        expect(1, feed(p, sample(BUTTON_PRESS, "A", 120_600, 120_600, 1, 1)).pressed());
        expect(1, complete(p, 120_600, 120_610, "A").click());
        open(p, 120_700);
        expect(1, feed(p, sample(HOVER_MOVE, "A", 0, 120_710, 0, 0)).cancelled());
    }

    private static void contradictoryTerminals() {
        for (boolean wrongFirst : new boolean[]{false, true}) {
            var p = new WsaPointer();
            open(p, 100);
            var up = sample(UP, "A", 100, 140, 0, 0);
            var wrong = sample(BUTTON_RELEASE, "A", 100, 140, 2, 0);
            var first = feed(p, wrongFirst ? wrong : up);
            expect(0, first.click());
            expect(wrongFirst ? 1 : 0, first.cancelled());
            var last = feed(p, wrongFirst ? up : wrong);
            if (wrongFirst) expect(null, last);
            else { expect(1, last.cancelled()); expect(0, last.click()); }
        }
        var p = new WsaPointer();
        open(p, 200);
        feed(p, sample(UP, "A", 200, 230, 0, 0));
        expect(1, feed(p, sample(BUTTON_RELEASE, "A", 200, 231, 1, 0)).cancelled());
        open(p, 300);
        expect(1, feed(p, sample(BUTTON_PRESS, "A", 300, 300, 2, 2)).cancelled());
    }

    private static void spatialAndAmbiguousCancel() {
        var p = new WsaPointer();
        open(p, 100);
        expect(1, feed(p, sample(HOVER_EXIT, "A", 0, 102, 0, 0)).held());
        expect(1, feed(p, sample(HOVER_EXIT, OUTSIDE, 0, 120, 0, 0)).cancelled());
        expect(null, complete(p, 100, 140, "A"));
        open(p, 200);
        expect(1, feed(p, new Sample(CANCEL, "A", 0, 0, 200, 215, 7, 1, 0, 0, true, false)).cancelled());
        expect(null, complete(p, 200, 220, "A"));
        open(p, 300);
        expect(1, feed(p, new Sample(POINTER_DOWN, "A", 0, 0, 300, 310, 0, 2, 0, 1, true, false)).cancelled());
        expect(null, complete(p, 300, 330, "A"));
        open(p, 400);
        expect(1, complete(p, 400, 430, "A").click());
    }

    private static void dragAndSlop() {
        var p = new WsaPointer();
        open(p, 100);
        expect("drag-start", feed(p, new Sample(MOVE, "A", 20, 0, 100, 110, 0, 1, 0, 1, true, false)).phase());
        expect(0, complete(p, 100, 120, "A").click());
        open(p, 200);
        feed(p, sample(MOVE, "B", 200, 205, 0, 1));
        expect(0, complete(p, 200, 210, "A").click());
        open(p, 300);
        feed(p, new Sample(MOVE, "A", 1, 0, 300, 305, 0, 1, 0, 1, true, true));
        expect(0, complete(p, 300, 310, "A").click());
        open(p, 400);
        expect(1, p.invalidate(450, "density-layout").cancelled());
        expect(null, complete(p, 400, 460, "A"));
        open(p, 451); // dispatch-time layout fence allows its first, distinct press
        expect(false, p.observe(new Sample(MOVE, "A", 20, 0, 451, 460, 0, 1, 0, 1, true, false), 30).dragging());
        expect(1, complete(p, 451, 470, "A").click());
    }

    private static void arbitration() {
        var input = new ProbeInput(3);
        for (int i = 1; i <= 3; i++) input.attach(i);
        start(input, 100);
        expect(null, input.observe(sample(HOVER_MOVE, "A", 105, 105, 0, 0), 2, 0x2002, 3, true, 10).displaced());
        expect(null, input.observe(sample(UP, "A", 80, 115, 0, 0), 2, 0x2002, 3, true, 10).displaced());
        var ambiguous = input.observe(sample(CANCEL, "A", 0, 120, 0, 0), 0, 0, 0, true, 10);
        expect(1, ambiguous.displacedSlot());
        expect(1, ambiguous.displaced().cancelled());
        expect(null, finish(input, 100, 130));
        start(input, 200);
        var touch = input.observe(sample(DOWN, "A", 220, 220, 0, 0), 2, 0x1002, 1, true, 10);
        expect(1, touch.displaced().cancelled());
        expect(null, finish(input, 200, 225));
        input.observe(sample(UP, "A", 220, 230, 0, 0), 2, 0x1002, 1, true, 10);
        start(input, 300);
        var mouse = input.observe(sample(DOWN, "A", 310, 310, 0, 1), 1, 0x2002, 3, true, 10);
        expect(1, mouse.displaced().cancelled());
        expect(1, mouse.result().pressed());
        expect(0, mouse.result().click());
        input.observe(sample(UP, "A", 310, 320, 0, 0), 1, 0x2002, 3, true, 10);
        expect(null, finish(input, 300, 330));
        start(input, 400);
        var exit = input.observe(sample(HOVER_EXIT, OUTSIDE, 0, 405, 0, 0), 0, 0, 0, true, 10);
        expect(1, exit.displaced().cancelled());
        expect(null, finish(input, 400, 420));
        start(input, 500);
        expect(1, finish(input, 500, 520).click());
    }

    private static void staleAliasTerminalOrders() {
        for (int stalePairPosition = 0; stalePairPosition < 3; stalePairPosition++) {
            for (boolean genericFirst : new boolean[]{false, true}) {
                var input = new ProbeInput(2);
                input.attach(1);
                input.attach(2);
                start(input, 200);
                expectIgnored(translated(input, 2, DOWN, 100, 100, 0, 1));
                expectIgnored(translated(input, 2, BUTTON_PRESS, 100, 100, 1, 1));
                expect(1, translated(input, 1, MOVE, 200, 210, 0, 1).result().held());

                Action first = genericFirst ? BUTTON_RELEASE : UP;
                Action second = genericFirst ? UP : BUTTON_RELEASE;
                int button = genericFirst ? 1 : 0;
                if (stalePairPosition == 0) ignoreStalePair(input, first, second, button);
                var pending = translated(input, 1, first, 200, 220, button, 0);
                expect(0, pending.displacedSlot());
                expect("release-pending", pending.result().phase());
                expect(0, pending.result().click());
                expect(0, pending.result().held());
                if (stalePairPosition == 1) ignoreStalePair(input, first, second, button);
                var completed = translated(input, 1, second, 200, 220, 1 - button, 0);
                expect(0, completed.displacedSlot());
                expect(1, completed.result().released());
                expect(1, completed.result().click());
                expect(0, completed.result().held());
                if (stalePairPosition == 2) ignoreStalePair(input, first, second, button);
                expectIgnored(translated(input, 1, second, 200, 220, 1 - button, 0));
            }
        }
        System.out.println("PASS stale translated alias in all tested terminal orders");
    }

    private static void ignoreStalePair(ProbeInput input, Action first, Action second, int firstButton) {
        expectIgnored(translated(input, 2, first, 100, 120, firstButton, 0));
        expectIgnored(translated(input, 2, second, 100, 120, 1 - firstButton, 0));
    }

    private static void rejectedAliasCannotReopen() {
        var input = new ProbeInput(2);
        input.attach(1);
        input.attach(2);
        start(input, 200);
        expectIgnored(translated(input, 2, DOWN, 100, 100, 0, 1));
        // An inconsistent timestamp is inapplicable even when its down time is newer.
        expectIgnored(translated(input, 2, MOVE, 250, 190, 0, 1));
        expectIgnored(translated(input, 2, CANCEL, 250, 260, 0, 0));
        expect(1, finish(input, 200, 220).click());

        expectIgnored(translated(input, 2, BUTTON_PRESS, 100, 100, 1, 1));
        expectIgnored(translated(input, 2, DOWN, 100, 100, 0, 1));
        expectIgnored(translated(input, 2, DOWN, 250, 250, 0, 1));
        expectIgnored(translated(input, 2, BUTTON_PRESS, 250, 250, 1, 1));
        expectIgnored(translated(input, 2, UP, 250, 270, 0, 0));
        expectIgnored(translated(input, 2, BUTTON_RELEASE, 250, 270, 1, 0));
        System.out.println("PASS rejected contact stays retired across owner completion");
    }

    private static void competingTranslatedAliases() {
        var input = new ProbeInput(2);
        input.attach(1);
        input.attach(2);
        start(input, 200);
        var conflict = translated(input, 2, DOWN, 200, 200, 0, 1);
        expect(1, conflict.displacedSlot());
        expect("competing-stream", conflict.displaced().phase());
        expect(1, conflict.displaced().cancelled());
        expect(0, conflict.displaced().click());
        expect(null, conflict.result());
        expectIgnored(translated(input, 2, BUTTON_PRESS, 200, 200, 1, 1));
        expect(null, finish(input, 200, 220));
        expectIgnored(translated(input, 2, UP, 200, 220, 0, 0));
        expectIgnored(translated(input, 2, BUTTON_RELEASE, 200, 220, 1, 0));

        expectIgnored(translated(input, 2, DOWN, 300, 300, 0, 1));
        expect(1, translated(input, 2, BUTTON_PRESS, 300, 300, 1, 1).result().pressed());
        expect("release-pending", translated(input, 2, UP, 300, 320, 0, 0).result().phase());
        expect(1, translated(input, 2, BUTTON_RELEASE, 300, 320, 1, 0).result().click());

        start(input, 400);
        var ambiguous = translated(input, 2, CANCEL, 0, 410, 0, 0);
        expect(1, ambiguous.displacedSlot());
        expect("ambiguous-cancel", ambiguous.displaced().phase());
        expect(1, ambiguous.displaced().cancelled());
        expect(null, finish(input, 400, 420));
        System.out.println("PASS genuine translated conflict cancels and fresh alias recovers");
    }

    private static ProbeInput.Decision translated(ProbeInput input, int slot, Action action,
                                                   long down, long event, int button, int mask) {
        return input.observe(sample(action, "A", down, event, button, mask), slot, 0x1002, 1, true, 10);
    }

    private static void expectIgnored(ProbeInput.Decision decision) {
        expect(null, decision.result());
        expect(0, decision.displacedSlot());
        expect(null, decision.displaced());
    }

    private static void deviceChurn() {
        var aliases = new DeviceAliases(1);
        var input = new ProbeInput(1);
        var first = aliases.get(8, id -> "virtual", input::attach);
        expect(0L, aliases.get(9, id -> "other", input::attach).alias());
        start(input, 100);
        expect(1, input.invalidate(first.slot(), 140, "device-changed", true).cancelled());
        input.fenceOpenings(140);
        for (int i = 0; i < 70; i++) {
            var removed = aliases.remove(8);
            input.detach(removed.slot());
            var restored = aliases.get(8, id -> "registered", input::attach);
            expect(removed.slot(), restored.slot());
            expect(removed.alias() + 1, restored.alias());
            expect("registered", restored.identity());
        }
        expect(null, translated(input, sample(DOWN, "A", 100, 100, 0, 1)));
        expect(null, finish(input, 100, 145));
        start(input, 200);
        expect(1, finish(input, 200, 230).click());
    }

    private static Result translated(ProbeInput input, Sample sample) {
        return input.observe(sample, 1, 0x1002, 1, true, 10).result();
    }

    private static void start(ProbeInput input, long time) {
        expect(null, translated(input, sample(DOWN, "A", time, time, 0, 1)));
        expect(1, translated(input, sample(BUTTON_PRESS, "A", time, time, 1, 1)).pressed());
    }

    private static Result finish(ProbeInput input, long down, long event) {
        translated(input, sample(UP, "A", down, event, 0, 0));
        return translated(input, sample(BUTTON_RELEASE, "A", down, event, 1, 0));
    }

    private static void open(WsaPointer p, long time) {
        expect(null, feed(p, sample(DOWN, "A", time, time, 0, 1)));
        expect(1, feed(p, sample(BUTTON_PRESS, "A", time, time, 1, 1)).pressed());
    }

    private static Result complete(WsaPointer p, long down, long event, String region) {
        feed(p, sample(UP, region, down, event, 0, 0));
        return feed(p, sample(BUTTON_RELEASE, region, down, event, 1, 0));
    }

    private static Sample sample(Action action, String region, long down, long event, int button, int mask) {
        return new Sample(action, region, 0, 0, down, event, 0, 1, button, mask, true, false);
    }

    private static Result feed(WsaPointer p, Sample sample) { return p.observe(sample, 10); }
    private static int number(String[] fields, int index) { return Integer.parseInt(fields[index]); }
    private static boolean bit(String value) { return "1".equals(value); }

    private static void expect(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}

package com.pokedaisy.mousesupremacy.probe;

import com.pokedaisy.mousesupremacy.pointer.DirectTouch;
import com.pokedaisy.mousesupremacy.pointer.PointerLayout;
import com.pokedaisy.mousesupremacy.pointer.PointerNormalizer;
import com.pokedaisy.mousesupremacy.pointer.PointerRoute;
import com.pokedaisy.mousesupremacy.pointer.PointerState.Sample;
import com.pokedaisy.mousesupremacy.pointer.PointerState.Action;

import java.util.List;
import java.util.Objects;

import static com.pokedaisy.mousesupremacy.pointer.PointerLayout.Owner.*;
import static com.pokedaisy.mousesupremacy.pointer.PointerState.Action.*;

public final class ApplicationPointerTest {
    private static final int MOUSE = 0x2002;
    private static final int FINGER = 0x1002;
    private static final float SLOP = 8;

    public static void main(String[] args) {
        projection();
        hudOwnership();
        ownership();
        ordinaryTouch();
        conventionalCancellation();
        contactLifecycle();
        staleDirectCancellation();
        translatedTransitions();
        System.out.println("Application pointer tests passed: 2 layout widths, 4 ownership boundary classes, "
                + "direct-touch gating, 11 conventional cancel cases, and 4 translated callback orders.");
    }

    private static PointerLayout layout(float companionX, boolean modal, boolean hud, boolean available) {
        return new PointerLayout(box(10, 20, 1010, 640), box(companionX, 20, 1010, 640),
                List.of(box(companionX - 20, 40, companionX, 85)), List.of(box(20, 300, 90, 400)),
                modal, hud, box(30, 46, companionX, 574),
                available ? box(30, 65.5f, companionX - .5f, 490.75f) : null, (long)companionX);
    }

    private static void projection() {
        for (float width : new float[] {790, 630}) {
            var scene = layout(width, false, false, true);
            expect(new PointerLayout.Pixel(0, 0), scene.hit(30, 65.5f).pixel());
            expect(new PointerLayout.Pixel(120, 80),
                    scene.hit((30 + width - .5f) / 2, (65.5f + 490.75f) / 2).pixel());
            expect(new PointerLayout.Pixel(239, 159),
                    scene.hit(Math.nextDown(width - .5f), Math.nextDown(490.75f)).pixel());
            expect(UNOWNED, scene.hit(Math.nextDown(30f), 130).owner());
            expect(UNOWNED, scene.hit(30, Math.nextDown(65.5f)).owner());
            expect(UNOWNED, scene.hit(width - .5f, 100).owner());
            expect(UNOWNED, scene.hit(50, 490.75f).owner());
            expect(OUTSIDE, scene.hit(Float.NaN, 100).owner());
            expect(COMPANION, scene.hit(width, 100).owner());
            expect(CHROME, scene.hit(width - 10, 70).owner());
            expect(TOUCH_CONTROLS, scene.hit(48, 330).owner());
            expect(OVERLAY, layout(width, true, false, true).hit(200, 200).owner());
            expect(HUD, layout(width, false, true, true).hit(200, 200).owner());
            expect(UNOWNED, layout(width, false, false, false).hit(200, 200).owner());
        }
        var invalid = new PointerLayout(box(0, 0, 410, 300), null, List.of(), List.of(), false, false,
                box(0, 0, 410, 300), box(0, 0, Float.POSITIVE_INFINITY, 300), 9);
        expect(UNOWNED, invalid.hit(200, 200).owner());
        var clip = new PointerLayout(box(0, 0, 410, 300), null, List.of(), List.of(), false, false,
                box(23, 34, 360, 270), box(0, 0, 400, 300), 5);
        expect(UNOWNED, clip.hit(22, 75).owner());
        expect(new PointerLayout.Pixel(13, 40), clip.hit(23, 75).pixel());
    }

    private static void hudOwnership() {
        var window = box(10, 20, 1010, 640);
        var panel = box(810, 20, 1010, 640);
        var quad = box(30, 65, 815, 500);
        var regions = new java.util.ArrayList<>(List.of(box(750, 80, 895, 160), box(60, 25, 160, 46)));
        var scene = new PointerLayout(window, panel, List.of(), List.of(), false, false, quad, quad, 42, regions);
        expect(HUD, scene.hit(760, 120).owner());
        expect(HUD, scene.hit(70, 40).owner());
        expect(COMPANION, scene.hit(830, 120).owner());
        expect(GAME, scene.hit(749.5f, 120).owner()); // adjacent native picture
        expect(GAME, scene.hit(760, 160).owner()); // bottom is exclusive
        regions.clear();
        expect(HUD, scene.hit(760, 120).owner()); // immutable scene evidence
    }

    private static void ownership() {
        var scene = layout(630, false, false, true);
        var harness = new Harness(scene);
        expect(null, harness.mouse(sample(HOVER_MOVE, 100, 100, 0, 10, 0, 0)));
        var down = harness.mouse(sample(DOWN, 100, 100, 11, 11, 1, 0));
        expect(GAME, down.owner());
        expect(null, harness.mouse(sample(BUTTON_PRESS, 100, 100, 11, 11, 1, 1)));
        expect(null, harness.mouse(sample(HOVER_EXIT, 100, 100, 11, 12, 0, 0)));
        var up = harness.mouse(sample(UP, 100, 100, 11, 13, 0, 0));
        expect(down.gesture(), up.gesture());
        expect("observed-release", up.disposition());
        expect(null, harness.mouse(sample(BUTTON_RELEASE, 100, 100, 11, 13, 0, 1)));

        for (float[] point : new float[][] {{631, 100}, {617, 70}, {50, 330}, {50, 500}}) {
            var isolated = new Harness(scene);
            var previous = isolated.mouse(sample(DOWN, 100, 100, 20, 20, 1, 0));
            var crossing = isolated.mouse(sample(MOVE, point[0], point[1], 20, 21, 1, 0));
            expect(previous.gesture(), crossing.gesture());
            expect("owner-crossing", crossing.disposition());
            expect(null, isolated.mouse(sample(UP, 100, 100, 20, 22, 0, 0)));
            var fresh = isolated.mouse(sample(DOWN, 100, 100, 23, 23, 1, 0));
            expect(GAME, fresh.owner());
        }

        var companion = harness.mouse(sample(DOWN, 660, 100, 20, 20, 1, 0));
        expect(COMPANION, companion.owner());
        expect("observed-release", harness.mouse(sample(UP, 660, 100, 20, 21, 0, 0)).disposition());

        var interrupted = harness.mouse(sample(DOWN, 100, 100, 30, 30, 1, 0));
        harness.input.fenceOpenings(31);
        harness.input.invalidate(1, 31, "focus-loss", false);
        expect(interrupted.gesture(), harness.route.cancel("focus-loss").gesture());
        expect(null, harness.mouse(sample(UP, 100, 100, 30, 32, 0, 0)));
        expect(null, harness.mouse(sample(DOWN, 100, 100, 31, 31, 1, 0)));
        expect(GAME, harness.mouse(sample(DOWN, 100, 100, 33, 33, 1, 0)).owner());
        expect(null, harness.route.cancelCompetingContact(32));
        expect("upstream-touch", harness.route.cancelCompetingContact(34).disposition());
        expect(null, harness.mouse(sample(UP, 100, 100, 33, 35, 0, 0)));
        expect(GAME, harness.mouse(sample(DOWN, 100, 100, 36, 36, 1, 0)).owner());
        expect("superseded-contact", harness.mouse(sample(DOWN, 100, 100, 37, 37, 1, 0)).disposition());
        expect(null, harness.mouse(sample(UP, 100, 100, 37, 38, 0, 0)));
    }

    private static void conventionalCancellation() {
        var scene = layout(630, false, false, true);
        record Case(int slot, int source, int tool, long down, long event, float x, boolean retires) {}
        var cases = List.of(
                new Case(2, 0, 0, 0, 605, 100, true),           // independent review sequence
                new Case(1, 0, 0, 0, 605, 100, true),           // same alias without contact time
                new Case(1, MOUSE, 3, 600, 605, 100, true),     // identified owner
                new Case(2, 0, 0, 600, 605, 100, true),         // matching time, missing identity
                new Case(1, 0, 0, 0, 599, 100, false),         // stale on the owning slot
                new Case(1, MOUSE, 3, 590, 605, 100, false),    // prior generation on the owning slot
                new Case(1, FINGER, 1, 0, 605, 100, false),    // unrelated tool on the owning slot
                new Case(2, MOUSE, 3, 600, 605, 100, false),   // known separate mouse, same down time
                new Case(2, 0, 0, 601, 605, 100, false),       // different identifiable generation
                new Case(2, 0, 0, 0, 605, -1, false),         // unknown stream outside window
                new Case(2, 0, 0, 0, 615, 100, false)         // future callback cannot fence a contact
        );
        for (var c : cases) {
            var harness = new Harness(scene);
            var press = harness.mouse(sample(DOWN, 100, 100, 600, 600, 1, 0));
            var cancel = new Sample(CANCEL, c.x() < 0 ? "outside" : "window", c.x(), 100, c.down(),
                    c.event(), 0, 1, 0, 0, true, false);
            var result = harness.cancel(c.slot(), c.source(), c.tool(), cancel, 610);
            if (c.retires()) expect(press.gesture(), result.gesture()); else expect(null, result);
            expect(null, harness.cancel(c.slot(), c.source(), c.tool(), cancel, 610));
            if (c.retires()) {
                expect(null, harness.mouse(sample(DOWN, 100, 100, 600, 600, 1, 0)));
                expect(null, harness.mouse(sample(DOWN, 100, 100, 605, 605, 1, 0)));
            }
            var ending = harness.mouse(sample(UP, 100, 100, 600, 610, 0, 0));
            if (c.retires()) expect(null, ending); else expect("observed-release", ending.disposition());
            expect(null, harness.mouse(sample(BUTTON_RELEASE, 100, 100, 600, 611, 0, 1)));
            var fresh = harness.mouse(sample(DOWN, 100, 100, 620, 620, 1, 0));
            expect(GAME, fresh.owner());
            // A late duplicate cancellation cannot retire a newer legitimate owner.
            expect(null, harness.cancel(c.slot(), c.source(), c.tool(), cancel, 622));
            expect(fresh.gesture(), harness.mouse(sample(UP, 100, 100, 620, 622, 0, 0)).gesture());
        }
    }

    private static void contactLifecycle() {
        int histories = 0;
        for (int profile = 0; profile < 3; profile++) {
            for (int slot : new int[]{1, 2}) {
                for (Action terminal : new Action[]{UP, CANCEL}) {
                    for (int order = 0; order < 3; order++) {
                        for (boolean held : new boolean[]{false, true}) {
                            var bridge = new Harness(layout(630, false, false, true), profile);
                            var down = sample(DOWN, 100, 100, 590, 590, 0, 0);
                            var end = sample(terminal, 100, 100, 590, 605, 0, 0);
                            var owner = held ? bridge.mouse(sample(DOWN, 100, 100, 600, 600, 1, 0)) : null;
                            if (order == 0) bridge.deliver(slot, FINGER, 1, end, profile, 605);
                            bridge.deliver(slot, FINGER, 1, down, profile, 605);
                            expect(order != 0, bridge.input.hasDirectContact());
                            if (held) expect(true, bridge.route.conventionalHeld());
                            if (order == 1) {
                                bridge.deliver(slot, FINGER, 1, end, profile, 605);
                                expect(false, bridge.input.hasDirectContact());
                            }
                            if (held) {
                                var released = bridge.mouse(sample(UP, 100, 100, 600, 610, 0, 0));
                                expect(owner.gesture(), released.gesture());
                                expect("observed-release", released.disposition());
                            }
                            if (order == 2) {
                                expect(true, bridge.input.hasDirectContact());
                                var blocked = bridge.mouse(sample(DOWN, 100, 100, 620, 620, 1, 0));
                                expect(false, blocked != null && blocked.disposition().equals("observed-press"));
                                expect(false, bridge.route.conventionalHeld());
                                expect(null, bridge.mouse(sample(UP, 100, 100, 620, 621, 0, 0)));
                                bridge.deliver(slot, FINGER, 1, end, profile, 621);
                            }
                            expect(false, bridge.input.hasDirectContact());
                            expect(null, bridge.deliver(slot, FINGER, 1, end, profile, 622));
                            expect(null, bridge.deliver(slot, FINGER, 1, down, profile, 622));
                            expect(false, bridge.input.hasDirectContact());
                            var fresh = bridge.mouse(sample(DOWN, 100, 100, 630, 630, 1, 0));
                            expect(GAME, fresh.owner());
                            expect(fresh.gesture(), bridge.mouse(sample(UP, 100, 100, 630, 635, 0, 0)).gesture());
                            histories++;
                        }
                    }

                    for (long generation : new long[]{600, 602}) {
                        var bridge = new Harness(layout(630, false, false, true), profile);
                        var owner = bridge.mouse(sample(DOWN, 100, 100, 600, 600, 1, 0));
                        var takeOver = bridge.deliver(slot, FINGER, 1,
                                sample(DOWN, 100, 100, generation, generation, 0, 0), profile, 605);
                        expect("upstream-touch", takeOver.disposition());
                        expect(owner.gesture(), takeOver.gesture());
                        expect(true, bridge.input.hasDirectContact());
                        expect(false, bridge.route.held());
                        bridge.deliver(slot, FINGER, 1, sample(terminal, 100, 100, 590, 606, 0, 0), profile, 606);
                        expect(true, bridge.input.hasDirectContact());
                        bridge.deliver(slot, FINGER, 1,
                                sample(terminal, 100, 100, generation, 608, 0, 0), profile, 608);
                        expect(false, bridge.input.hasDirectContact());
                        expect(null, bridge.mouse(sample(UP, 100, 100, 600, 610, 0, 0)));
                        var fresh = bridge.mouse(sample(DOWN, 100, 100, 620, 620, 1, 0));
                        expect(fresh.gesture(), bridge.mouse(sample(UP, 100, 100, 620, 625, 0, 0)).gesture());
                        histories++;
                    }
                }
            }

            // A conventional-only fence cannot clear another contact. An ambiguous terminal
            // with neither identity nor clock evidence from another slot cannot clear it either.
            var bridge = new Harness(layout(630, false, false, true), profile);
            bridge.mouse(sample(DOWN, 100, 100, 600, 600, 1, 0));
            var touch = sample(DOWN, 100, 100, 590, 590, 0, 0);
            bridge.deliver(1, FINGER, 1, touch, profile, 601);
            expect("cancelled", bridge.cancel(1, MOUSE, 3, sample(CANCEL, 100, 100, 600, 605, 0, 0), 605).disposition());
            expect(true, bridge.input.hasDirectContact());
            bridge.deliver(2, 0, 0, sample(CANCEL, 100, 100, 0, 606, 0, 0), profile, 606);
            expect(true, bridge.input.hasDirectContact());
            bridge.input.fenceOpenings(610);
            bridge.input.invalidate(1, 610, "layout", false);
            bridge.input.invalidate(2, 610, "layout", false);
            bridge.direct.invalidate(610, "layout");
            expect(false, bridge.input.hasDirectContact());
            expect(null, bridge.deliver(1, FINGER, 1, touch, profile, 611));
            expect(false, bridge.input.hasDirectContact());
            var fresh = bridge.mouse(sample(DOWN, 100, 100, 620, 620, 1, 0));
            expect(fresh.gesture(), bridge.mouse(sample(UP, 100, 100, 620, 625, 0, 0)).gesture());
            histories++;
        }
        var directBridge = new Harness(layout(630, false, false, true), 2);
        var finger = sample(DOWN, 100, 100, 700, 700, 0, 0);
        var pressed = directBridge.deliver(1, FINGER, 1, finger, 2, 700);
        expect("observed-press", pressed.disposition());
        expect(null, directBridge.deliver(1, FINGER, 1, finger, 2, 701));
        expect(true, directBridge.route.held());
        expect(true, directBridge.input.hasDirectContact());
        expect(null, directBridge.deliver(1, FINGER, 1, sample(CANCEL, 100, 100, 700, 900, 0, 0), 2, 702));
        expect(true, directBridge.route.held());
        expect(true, directBridge.input.hasDirectContact());
        var released = directBridge.deliver(1, FINGER, 1, sample(UP, 100, 100, 700, 705, 0, 0), 2, 705);
        expect(pressed.gesture(), released.gesture());
        expect("observed-release", released.disposition());
        expect(false, directBridge.input.hasDirectContact());
        System.out.println((histories + 1) + " application contact lifecycle histories passed");
    }

    private static void staleDirectCancellation() {
        var harness = new Harness(layout(630, false, false, true));
        var touch = new DirectTouch();
        var start = harness.mouse(sample(DOWN, 100, 100, 600, 600, 1, 0));
        var delayed = sample(DOWN, 100, 100, 590, 590, 0, 0);
        var directDown = touch.observe(2, delayed, true, false, SLOP);
        expect(null, harness.route.observe(2, delayed, directDown, harness.layout.hit(100, 100), SLOP, false));
        var directCancel = touch.observe(2, sample(CANCEL, 100, 100, 590, 605, 0, 0), true, false, SLOP);
        expect(DirectTouch.CONTACT, directCancel.cancelled());
        expect(true, harness.route.conventionalHeld());
        expect(start.gesture(), harness.mouse(sample(UP, 100, 100, 600, 610, 0, 0)).gesture());
    }

    private static void ordinaryTouch() {
        var down = sample(DOWN, 100, 100, 50, 50, 0, 0);
        var up = sample(UP, 100, 100, 50, 52, 0, 0);
        var normal = new PointerNormalizer(1);
        normal.attach(1);
        for (boolean wsa : new boolean[] {false, true}) {
            expect(null, normal.observe(down, 1, FINGER, 1, wsa, SLOP).result());
            expect(null, normal.observe(up, 1, FINGER, 1, wsa, SLOP).result());
        }
        normal.observe(sample(DOWN, 100, 100, 55, 55, 0, 0), 1, FINGER, 1, false, SLOP);
        expect(true, normal.hasDirectContact());
        normal.observe(sample(UP, 100, 100, 55, 56, 0, 0), 1, FINGER, 1, false, SLOP);
        expect(false, normal.hasDirectContact());

        var touch = new DirectTouch();
        var contradiction = new DirectTouch();
        expect(DirectTouch.CONTACT, contradiction.observe(1, down, true, false, SLOP).pressed());
        expect(DirectTouch.CONTACT, contradiction.observe(1,
                sample(UP, 100, 100, 50, 51, 1, 0), true, false, SLOP).cancelled());
        expect(null, contradiction.observe(1, up, true, false, SLOP));
        expect(DirectTouch.CONTACT, touch.observe(1, down, true, false, SLOP).pressed());
        expect(DirectTouch.CONTACT, touch.observe(1, up, true, false, SLOP).released());
        expect(null, touch.observe(1, down, true, false, SLOP));
        expect(null, touch.observe(1, sample(DOWN, 100, 100, 55, 55, 1, 0), true, false, SLOP));
        expect(DirectTouch.CONTACT,
                touch.observe(1, sample(DOWN, 100, 100, 56, 56, 0, 0), true, false, SLOP).pressed());
        expect(DirectTouch.CONTACT,
                touch.observe(1, sample(MOVE, 109, 100, 56, 57, 0, 0), true, false, SLOP).cancelled());
        expect(null, touch.observe(1, sample(UP, 100, 100, 56, 58, 0, 0), true, false, SLOP));
        expect(DirectTouch.CONTACT,
                touch.observe(1, sample(DOWN, 100, 100, 59, 59, 0, 0), true, false, SLOP).pressed());
        expect(DirectTouch.CONTACT,
                touch.observe(2, sample(DOWN, 100, 100, 60, 60, 1, 0), false, true, SLOP).cancelled());
    }

    private static void translatedTransitions() {
        for (boolean buttonFirst : new boolean[] {false, true}) {
            for (boolean releaseFirst : new boolean[] {false, true}) {
                var harness = new Harness(layout(630, false, false, true));
                var start = sample(DOWN, 200, 100, 80, 80, 1, 0);
                var press = sample(BUTTON_PRESS, 200, 100, 80, 80, 1, 1);
                var up = sample(UP, 200, 100, 80, 82, 0, 0);
                var release = sample(BUTTON_RELEASE, 200, 100, 80, 82, 0, 1);
                expect(null, harness.wsa(buttonFirst ? press : start));
                var owned = harness.wsa(buttonFirst ? start : press);
                expect(GAME, owned.owner());
                expect(null, harness.wsa(releaseFirst ? release : up));
                var resolved = harness.wsa(releaseFirst ? up : release);
                expect("observed-release", resolved.disposition());
                expect(owned.gesture(), resolved.gesture());
                expect(null, harness.wsa(up));
                expect(null, harness.wsa(start));
            }
        }
    }

    private static final class Harness {
        final PointerNormalizer input = new PointerNormalizer(2);
        final PointerRoute route = new PointerRoute();
        final DirectTouch direct = new DirectTouch();
        final PointerLayout layout;
        final int mouseProfile;
        Harness(PointerLayout layout) { this(layout, 0); }
        Harness(PointerLayout layout, int profile) {
            this.layout = layout;
            mouseProfile = profile;
            input.attach(1);
            input.attach(2);
        }

        PointerRoute.Outcome mouse(Sample s) {
            return deliver(1, MOUSE, 3, s, mouseProfile, s.eventTime());
        }

        PointerRoute.Outcome cancel(int slot, int source, int tool, Sample sample, long receipt) {
            var origin = source == MOUSE && tool == 3 ? PointerRoute.CancelOrigin.CONVENTIONAL
                    : source == 0 && tool == 0 ? PointerRoute.CancelOrigin.UNIDENTIFIED : PointerRoute.CancelOrigin.OTHER;
            long owner = route.conventionalCancelOwner(slot, sample, origin, receipt);
            if (owner != 0) {
                input.invalidateConventional((int)owner, sample.eventTime(), "cancelled");
                return route.cancel("cancelled");
            }
            return deliver(slot, source, tool, sample, 0, receipt);
        }

        // Android maps the callback to a Sample and delegates normalization and hit
        // projection to these same methods. This harness does not dispatch MotionEvents.
        PointerRoute.Outcome deliver(int slot, int source, int tool, Sample sample, int profile, long receipt) {
            var normalized = route.normalize(input, sample, slot, slot, source, tool, profile == 1, SLOP, receipt);
            boolean finger = source == FINGER && tool == 1;
            var touch = profile == 2 && !PointerRoute.isFutureCancellation(sample, receipt)
                    ? direct.observe(slot, sample, finger, (source & 2) != 0 && !finger, SLOP) : null;
            if (touch != null && touch.cancelled() != 0 && route.conventionalHeld()) touch = null;
            if (normalized.displaced() != null && !route.conventionalHeld()) return route.cancel(normalized.displaced().phase());
            if (touch != null && touch.cancelled() != 0) return route.cancel(touch.phase());
            if (route.conventionalHeld() && input.isDirectOpening(slot, sample)) {
                var takeover = route.cancelCompetingContact(sample.downTime());
                if (takeover != null) return takeover;
            }
            var edge = normalized.result() == null ? touch : normalized.result();
            if (edge == null && normalized.interpretation() == null) return null;
            boolean conventional = PointerNormalizer.STANDARD.equals(normalized.interpretation());
            var hit = layout.hit(sample.x(), sample.y());
            if (conventional) hit = route.withConventionalOccupancy(input, hit);
            return route.observe(slot, sample, edge, hit, SLOP, conventional);
        }

        PointerRoute.Outcome wsa(Sample s) {
            return route.observe(1, s, input.observe(s, 1, FINGER, 1, true, SLOP).result(), layout.hit(s.x(), s.y()), SLOP, false);
        }
    }

    private static Sample sample(Action action, float x, float y, long down, long time, int buttons, int actionButton) {
        return new Sample(action, "window", x, y, down, time, 0, 1, actionButton, buttons, true, false);
    }

    private static PointerLayout.Bounds box(float l, float t, float r, float b) {
        return new PointerLayout.Bounds(l, t, r, b);
    }

    private static void expect(Object want, Object actual) {
        if (!Objects.equals(want, actual)) throw new AssertionError("Expected " + want + ", got " + actual);
    }
}

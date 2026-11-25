package com.pokedaisy.app.mouse;

import com.pokedaisy.app.mouse.NativeBattleMouse.Menu;
import com.pokedaisy.app.mouse.NativeBattleMouse.Native;
import com.pokedaisy.mousesupremacy.pointer.PointerLayout;
import java.util.ArrayList;
import java.util.List;
import static com.pokedaisy.app.mouse.NativeBattleMouse.*;

/** Forward core fake with independent cursor and masked input ledger. Runnable with JDK 17. */
public final class NativeBattleMouseHarness {
    private static final class Core {
        final ArrayList<String> trace = new ArrayList<>();
        final NativeBattleMouse mouse;
        final ArrayList<Integer> masks = new ArrayList<>();
        long frame = 7, layout = 1, transform = 21, gesture;
        Menu menu = Menu.ACTION;
        int cursor = 3, slots = 3, applied;
        boolean busy, hold, suppressDraw;
        Menu transition;
        int waitBusy;
        Native nativeState() {
            return busy ? new Native(menu, null, -1, 0) :
                    new Native(menu, menu, cursor, menu == Menu.ACTION ? 4 : slots);
        }
        Core() { this(false); }
        Core(boolean faultOnApplied) {
            mouse = new NativeBattleMouse(row -> {
                if (faultOnApplied && row.startsWith("reference applied")) throw new IllegalStateException("test sink");
                trace.add(row);
            });
            mouse.session(true);
            mouse.layout(layout, transform);
            step(0, true);
        }
        void step(int upstream, boolean idle) {
            long before = frame;
            mouse.observe(before, nativeState());
            mouse.commit(before, upstream, idle, upstream != 0, mask -> applied = mask);
            masks.add(applied);
            if (!hold) {
                frame++;
                if (transition != null && waitBusy > 0) {
                    waitBusy--;
                } else if (transition != null) {
                    menu = transition;
                    transition = null;
                    busy = false;
                    cursor = 0;
                } else if (applied == A && menu == Menu.ACTION && cursor == 0) {
                    transition = Menu.MOVE;
                    busy = true;
                } else if (applied == B && menu == Menu.MOVE) {
                    transition = Menu.ACTION;
                    busy = true;
                } else if (applied == LEFT && (cursor & 1) != 0) cursor ^= 1;
                else if (applied == RIGHT && (cursor & 1) == 0 && (cursor ^ 1) < nativeState().slots()) cursor ^= 1;
                else if (applied == UP && (cursor & 2) != 0) cursor ^= 2;
                else if (applied == DOWN && (cursor & 2) == 0 && (cursor ^ 2) < nativeState().slots()) cursor ^= 2;
            }
            Sample sample = mouse.applied(before, frame, nativeState());
            if (sample != null && !suppressDraw) {
                mouse.show(new Picture(sample, transform, frame * 10));
            }
        }
        void run(int frames) { for (int i = 0; i < frames; i++) step(0, true); }
        void hover(int x, int y) { mouse.pointer(x, y, transform, frame * 10); }
        void down(int button, int x, int y) { mouse.down(++gesture, button, x, y, transform, frame * 10); }
        void up(int x, int y) { mouse.up(gesture, x, y, transform, frame * 10); }
        void click(int button, int x, int y) { down(button, x, y); up(x, y); }
        void resize() { mouse.layout(++layout, ++transform); run(2); }
        void reference() {
            cursor = 0;
            run(2);
            step(A, false);
            step(B, false); // Actual ordinary B on the still-visible native transition.
            run(3);
            step(B, false);
            run(3);
            check(trace.stream().anyMatch(x -> x.contains("enabled=true")), "Native A/B and swap qualification");
            masks.clear();
        }
        List<Integer> nonzero() { return masks.stream().filter(mask -> mask != 0).toList(); }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    private static void twoWidths() {
        Core core = new Core();
        core.reference();
        for (int width : new int[]{500, 690}) {
            core.resize();
            core.cursor = 3;
            core.run(2);
            core.masks.clear();
            var game = new PointerLayout.Bounds(20, 40, 20 + width, 40 + width * 2f / 3f);
            var companion = new PointerLayout.Bounds(20 + width, 0, 990, 570);
            var layout = new PointerLayout(new PointerLayout.Bounds(0, 0, 1000, 600),
                    companion, List.of(), List.of(), false, false, game, game, core.transform);
            var pixel = layout.hit(20 + width * (145f / 240), 40 + width * (128f / 240)).pixel();
            check(pixel != null, "projected FIGHT");
            core.hover(pixel.x(), pixel.y());
            core.run(12);
            check(core.nonzero().equals(List.of(LEFT, UP)), "diagonal hover and cardinal order " + core.masks);
            check(core.masks.indexOf(UP) > core.masks.indexOf(LEFT) + 1, "applied neutral release between directions");
            core.click(1, pixel.x(), pixel.y());
            core.up(pixel.x(), pixel.y());
            core.run(12);
            check(core.menu == Menu.MOVE, "FIGHT reaches native MOVE");
            core.hover(97, 127);
            core.run(10);
            check(core.cursor == 1, "right move slot");
            core.hover(20, 143);
            core.run(12);
            check(core.cursor == 2, "three-slot diagonal through valid cells");
            core.click(1, 20, 143);
            core.run(6);
            check(core.menu == Menu.MOVE, "left click does not commit a move");
            core.click(2, 210, 110);
            core.up(210, 110);
            core.run(12);
            check(core.menu == Menu.ACTION, "right-click returns to native ACTION");
            check(core.nonzero().stream().filter(mask -> mask == A).count() == 1, "one FIGHT edge");
            check(core.nonzero().stream().filter(mask -> mask == B).count() == 1, "one BACK edge");
            check(layout.hit(970, 200).owner() == PointerLayout.Owner.COMPANION, "companion still owns its region");
        }
    }

    private static void interruptions() {
        for (String veto : List.of("owner", "manual", "zero reservation", "layout", "reset", "busy", "draw", "picture withdrawn", "crossing")) {
            Core core = new Core();
            core.reference();
            core.down(1, 133, 129);
            switch (veto) {
                case "owner" -> core.mouse.invalidate("focus");
                case "manual" -> core.step(LEFT, false);
                case "zero reservation" -> core.step(0, false);
                case "layout" -> core.resize();
                case "reset" -> core.mouse.resetState();
                case "busy" -> { core.busy = true; core.run(2); core.busy = false; core.run(2); }
                case "draw" -> {
                    core.suppressDraw = true;
                    core.run(10);
                    core.suppressDraw = false;
                }
                case "picture withdrawn" -> {
                    core.mouse.show(null);
                    core.run(2);
                }
                case "crossing" -> { core.hover(185, 129); core.hover(133, 129); }
                default -> throw new AssertionError(veto);
            }
            core.up(133, 129);
            core.run(12);
            check(core.nonzero().stream().noneMatch(mask -> mask == A), "stale click rejected for " + veto);
            if (veto.equals("reset")) core.reference();
            core.click(1, 133, 129);
            core.run(10);
            check(core.nonzero().stream().filter(mask -> mask == A).count() == 1, "fresh recovery for " + veto);
        }
    }

    private static void unavailableAndUnapplied() {
        Core core = new Core();
        core.click(1, 135, 128);
        core.hover(135, 128);
        core.run(12);
        check(core.nonzero().isEmpty(), "no mouse input before positive readiness");
        core.reference();
        core.click(1, 135, 128);
        core.hold = true;
        core.step(0, true);
        core.hold = false;
        core.run(12);
        check(core.trace.stream().noneMatch(x -> x.startsWith("applied") && x.contains("mask=1 edge=true")),
                "a held step is not applied");
        check(core.menu == Menu.ACTION, "unapplied confirmation retired");
    }

    private static void afterCommitCancellation() {
        Core core = new Core();
        core.reference();
        core.click(1, 135, 128);
        core.mouse.observe(core.frame, core.nativeState());
        core.mouse.commit(core.frame, 0, true, false, mask -> core.applied = mask);
        check(core.applied == A, "the write was committed");
        core.mouse.interrupt("focus-after-write");
        core.mouse.applied(core.frame, core.frame + 1, new Native(Menu.ACTION, null, -1, 0));
        check(core.trace.stream().anyMatch(x -> x.startsWith("applied") && x.contains("mask=1 edge=true")),
                "applied ledger survives a later cancellation");
    }

    private static void expandedProjection() {
        var viewport = new PointerLayout.Bounds(0, 0, 900, 600);
        var texture = new PointerLayout.Bounds(10, 5, 650, 405);
        var quad = PointerLayout.coreQuad(texture, -40, -20, 320, 200, 240, 160);
        check(quad.equals(new PointerLayout.Bounds(90, 45, 570, 365)), "expanded native source origin");
        var dock = new PointerLayout.Bounds(600, 0, 900, 600);
        var layout = new PointerLayout(viewport, dock, List.of(), List.of(), false, false,
                viewport.intersection(texture), quad, 7);
        check(layout.hit(380, 301).pixel().equals(new PointerLayout.Pixel(145, 128)), "expanded FIGHT");
        check(layout.hit(50, 60).owner() == PointerLayout.Owner.UNOWNED, "synthetic margin stays unowned");
        check(layout.hit(610, 300).owner() == PointerLayout.Owner.COMPANION, "expanded companion precedence");
        var hud = new PointerLayout(viewport, dock, List.of(), List.of(), false, true,
                viewport.intersection(texture), quad, 7);
        check(hud.hit(380, 301).owner() == PointerLayout.Owner.HUD, "HUD reservation wins");
        var crop = new PointerLayout.Bounds(100, 50, 500, 330);
        var cropCore = PointerLayout.coreQuad(crop, 20, 15, 200, 140, 240, 160);
        var cropped = new PointerLayout(viewport, null, List.of(), List.of(), false, false,
                viewport.intersection(crop), cropCore, 8);
        check(cropped.hit(350, 276).pixel().equals(new PointerLayout.Pixel(145, 128)), "cropped FIGHT retains origin");
        check(cropped.hit(90, 276).owner() == PointerLayout.Owner.UNOWNED, "unshown source is clipped");
        check(PointerLayout.coreQuad(texture, 240, 0, 320, 200, 240, 160) == null, "no native overlap");
    }

    private static void boundedAutomaticReference() {
        var core = new Core();
        core.cursor = 0;
        core.run(2);
        core.masks.clear();
        core.waitBusy = 1;
        core.mouse.armReadinessProbe();
        core.mouse.armReadinessProbe();
        core.run(8);
        check(core.nonzero().equals(List.of(A, B)), "exactly one A and busy B");
        check(core.masks.subList(0, 3).equals(List.of(A, 0, B)), "busy B follows an applied neutral step");
        check(core.menu == Menu.MOVE, "busy Back has no effect on settled MOVE");
        check(core.trace.stream().noneMatch(row -> row.contains("enabled=true")), "ready MOVE Back still required");
        core.step(B, false);
        core.run(3);
        check(core.trace.stream().anyMatch(row -> row.contains("enabled=true")), "ready Back qualifies with all witnesses");
        core.mouse.armReadinessProbe();
        core.masks.clear();
        core.run(8);
        check(core.nonzero().isEmpty(), "probe stops once qualified");

        var missingMenu = new Core();
        missingMenu.cursor = 0;
        missingMenu.run(2);
        missingMenu.mouse.armReadinessProbe();
        missingMenu.mouse.observe(missingMenu.frame, missingMenu.nativeState());
        missingMenu.mouse.commit(missingMenu.frame, 0, true, false, mask -> missingMenu.applied = mask);
        check(missingMenu.applied == A, "explicit probe can request FIGHT");
        var unknown = Native.UNKNOWN;
        missingMenu.mouse.applied(missingMenu.frame, missingMenu.frame + 1, unknown);
        missingMenu.mouse.commit(missingMenu.frame + 1, 0, true, false, mask -> missingMenu.applied = mask);
        check(missingMenu.applied == 0, "unknown result retires the probe without B");

        var shortWindow = new Core();
        shortWindow.cursor = 0;
        shortWindow.run(2);
        shortWindow.masks.clear();
        shortWindow.mouse.armReadinessProbe();
        shortWindow.run(8);
        check(shortWindow.nonzero().equals(List.of(A)), "a one-frame busy window cannot skip the release");
        shortWindow.step(B, false);
        shortWindow.run(3);
        check(shortWindow.trace.stream().noneMatch(row -> row.contains("enabled=true")), "short busy case remains disabled");

        for (String gate : List.of("companion", "manual", "picture", "no advancement")) {
            var interrupted = new Core();
            interrupted.cursor = 0;
            interrupted.run(2);
            interrupted.waitBusy = 2;
            interrupted.mouse.armReadinessProbe();
            if (gate.equals("no advancement")) interrupted.hold = true;
            interrupted.run(1);
            interrupted.masks.clear();
            switch (gate) {
                case "companion" -> interrupted.step(0, false);
                case "manual" -> interrupted.step(LEFT, false);
                case "picture" -> interrupted.mouse.show(null);
                case "no advancement" -> interrupted.hold = false;
                default -> throw new AssertionError(gate);
            }
            interrupted.run(8);
            check(!interrupted.nonzero().contains(B), "no busy B after " + gate);
            check(interrupted.trace.stream().noneMatch(row -> row.contains("enabled=true")), "no unsupported activation");
        }
    }

    private static void traceExceptionDoesNotAbortAnAppliedFrame() {
        var core = new Core(true);
        core.cursor = 0;
        core.run(2);
        core.waitBusy = 1;
        core.mouse.armReadinessProbe();
        core.run(8);
        check(core.nonzero().equals(List.of(A, B)), "optional trace failure does not interrupt core or presentation");
        core.step(B, false);
        core.run(3);
        core.click(1, 135, 128);
        core.run(8);
        check(core.menu == Menu.MOVE, "the next interaction still works after a throwing trace sink");
    }

    private static void probeSubmissionSurvivesCancellation() {
        var core = new Core();
        core.cursor = 0;
        core.run(2);
        core.mouse.armReadinessProbe();
        core.mouse.observe(core.frame, core.nativeState());
        core.mouse.commit(core.frame, 0, true, false, mask -> core.applied = mask);
        check(core.applied == A, "probe A was committed");
        core.mouse.interrupt("focus-after-probe-write");
        core.mouse.applied(core.frame, core.frame + 1, new Native(Menu.ACTION, null, -1, 0));
        check(core.trace.stream().anyMatch(row -> row.startsWith("reference probe applied") && row.contains("mask=1 edge=true")),
                "cancelled probe still records its already applied A");
    }

    public static void main(String[] args) {
        boundedAutomaticReference();
        probeSubmissionSurvivesCancellation();
        traceExceptionDoesNotAbortAnAppliedFrame();
        expandedProjection();
        twoWidths();
        interruptions();
        unavailableAndUnapplied();
        afterCommitCancellation();
        System.out.println("Native battle fake core: two widths, nine interruptions, unqualified/no-step cases, bounded reference and throwing trace passed");
    }
}

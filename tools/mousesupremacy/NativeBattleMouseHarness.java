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
        Sample completed;
        int waitBusy;
        final int[] pp = {35, 25, 15, 20};
        int selectedMove = -1, messages = 2;
        long page = 1;
        boolean rejectMove;
        Native nativeState() {
            if (menu == null) return Native.UNKNOWN;
            if (menu == Menu.MESSAGE && !busy) return new Native(menu, menu, 0, 1, page);
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
                    if (menu == Menu.MESSAGE) page++;
                } else if (applied == A && menu == Menu.ACTION && cursor == 0) {
                    transition = Menu.MOVE;
                    busy = true;
                } else if (applied == B && menu == Menu.MOVE) {
                    transition = Menu.ACTION;
                    busy = true;
                } else if (applied == A && menu == Menu.MOVE) {
                    selectedMove = cursor;
                    if (!rejectMove) pp[cursor]--;
                    menu = Menu.BUSY;
                    transition = Menu.MESSAGE;
                    waitBusy = 24; // Longer than the input acknowledgment deadline.
                    busy = true;
                } else if (applied == A && menu == Menu.MESSAGE) {
                    busy = true;
                    if (--messages == 0) menu = null;
                    else { transition = Menu.MESSAGE; waitBusy = 1; }
                } else if (applied == LEFT && (cursor & 1) != 0) cursor ^= 1;
                else if (applied == RIGHT && (cursor & 1) == 0 && (cursor ^ 1) < nativeState().slots()) cursor ^= 1;
                else if (applied == UP && (cursor & 2) != 0) cursor ^= 2;
                else if (applied == DOWN && (cursor & 2) == 0 && (cursor ^ 2) < nativeState().slots()) cursor ^= 2;
            }
            completed = mouse.applied(before, frame, nativeState());
            if (!suppressDraw) swap();
        }
        void swap() {
            if (completed != null) mouse.show(new Picture(completed, transform, frame * 10));
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
            waitBusy = 1;
            step(A, false);
            step(0, true);
            step(B, false); // Ordinary B on a swapped blocked ACTION sample.
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

    private static int stalePresentedHistories() {
        int histories = 0;
        for (int gap = 0; gap <= 9; gap++) {
            for (String boundary : List.of("none", "withdrawal", "zero-owner", "layout", "new-context", "new-session")) {
                for (boolean delayedSwap : List.of(false, true)) {
                    var core = new Core();
                    core.reference();
                    core.cursor = 1;
                    core.run(2);
                    var old = core.completed;
                    core.masks.clear();
                    core.click(1, 135, 128);
                    core.suppressDraw = true;
                    core.run(gap + 1); // The first LEFT advances without a replacement swap.
                    switch (boundary) {
                        case "none" -> {}
                        case "withdrawal" -> core.mouse.show(null);
                        case "zero-owner" -> core.step(0, false);
                        case "layout" -> core.mouse.layout(++core.layout, ++core.transform);
                        case "new-context" -> {
                            core.busy = true; core.run(1);
                            core.busy = false; core.run(1);
                        }
                        case "new-session" -> core.mouse.resetState();
                        default -> throw new AssertionError(boundary);
                    }
                    if (delayedSwap) core.mouse.show(new Picture(old, core.transform, core.frame * 10));
                    // A replacement can arrive immediately after the post-step that made the old lease stale.
                    core.swap();
                    core.suppressDraw = false;
                    core.run(11);
                    var powers = core.nonzero();
                    boolean shouldConfirm = boundary.equals("none") && gap + 1 <= 6;
                    check(powers.equals(shouldConfirm ? List.of(LEFT, A) : List.of(LEFT)),
                            "post-UP gap " + gap + " " + boundary + " delayed=" + delayedSwap + " " + core.masks);
                    if (!shouldConfirm) {
                        if (boundary.equals("new-session")) core.reference();
                        core.masks.clear();
                        core.click(1, 135, 128);
                        core.run(6);
                        check(core.nonzero().equals(List.of(A)), "fresh click recovers after " + boundary);
                    }
                    histories++;
                }
            }
        }
        return histories;
    }

    private static void transitionDoesNotInheritHover() {
        for (boolean moveDuringTransition : List.of(false, true)) {
            var core = new Core();
            core.reference();
            core.hover(135, 128);
            core.click(1, 135, 128); // This pixel also maps to the right MOVE slot.
            core.run(1);
            if (moveDuringTransition) core.hover(135, 128);
            core.run(11);
            check(core.masks.get(0) == A && core.masks.subList(1, 12).stream().allMatch(mask -> mask == 0),
                    "stationary FIGHT cannot steer MOVE");
            core.hover(135, 128);
            core.run(8);
            check(core.nonzero().equals(List.of(A, RIGHT)), "fresh MOVE hover still steers");
            core.masks.clear();
            core.click(2, 200, 128); // This pixel also maps to the right ACTION cell.
            core.run(1);
            if (moveDuringTransition) core.hover(200, 128);
            core.run(11);
            check(core.masks.get(0) == B && core.masks.subList(1, 12).stream().allMatch(mask -> mask == 0),
                    "stationary Back cannot steer ACTION");
            core.hover(200, 128);
            core.run(8);
            check(core.nonzero().equals(List.of(B, RIGHT)), "fresh ACTION hover still steers");
        }
    }

    private static int probeSwapHistories() {
        int histories = 0;
        for (int gap = 0; gap <= 5; gap++) {
            for (String picture : List.of("actual-busy", "old-ready", "unsettled-ready", "wrong-transform", "wrong-context")) {
                var core = new Core();
                core.cursor = 0;
                core.run(2);
                core.waitBusy = 5;
                core.masks.clear();
                var ready = core.completed;
                core.mouse.armReadinessProbe();
                core.suppressDraw = true;
                core.run(1 + gap);
                var live = core.completed;
                switch (picture) {
                    case "actual-busy" -> core.swap();
                    case "old-ready" -> core.mouse.show(new Picture(ready, core.transform, core.frame * 10));
                    case "unsettled-ready" -> core.mouse.show(new Picture(
                            new Sample(live.session(), live.context(), live.frame(), ready.nativeState(), false),
                            core.transform, core.frame * 10));
                    case "wrong-transform" -> core.mouse.show(new Picture(live, core.transform + 1, core.frame * 10));
                    case "wrong-context" -> core.mouse.show(new Picture(
                            new Sample(live.session(), ready.context(), live.frame(), live.nativeState(), true),
                            core.transform, core.frame * 10));
                    default -> throw new AssertionError(picture);
                }
                core.run(5);
                boolean busyBack = picture.equals("actual-busy") && gap <= 3;
                check(core.nonzero().equals(busyBack ? List.of(A, B) : List.of(A)),
                        "probe swap witness at gap " + gap + " " + picture + " " + core.masks);
                core.suppressDraw = false;
                core.run(3);
                core.step(B, false);
                core.run(4);
                check(core.trace.stream().anyMatch(x -> x.contains("enabled=true")) == busyBack,
                        "probe qualification depends on matched blocked swap");
                histories++;
            }
        }
        for (String discovery : List.of("swap-first", "layout-first", "no-matching-swap", "changed-layout", "withdrawn")) {
            var core = new Core();
            core.cursor = 0;
            core.run(2);
            core.masks.clear();
            core.waitBusy = 2;
            core.transform++;
            if (discovery.equals("swap-first")) core.run(1);
            core.mouse.layout(++core.layout, core.transform);
            core.mouse.armReadinessProbe();
            switch (discovery) {
                case "layout-first" -> {
                    core.suppressDraw = true;
                    core.run(2);
                    check(core.nonzero().isEmpty(), "arm waits rather than guessing");
                    core.swap();
                    core.suppressDraw = false;
                }
                case "no-matching-swap" -> {
                    core.suppressDraw = true;
                    core.run(8);
                    core.suppressDraw = false;
                }
                case "changed-layout" -> core.resize();
                case "withdrawn" -> core.mouse.show(null);
                case "swap-first" -> {}
                default -> throw new AssertionError(discovery);
            }
            core.run(9);
            check(core.nonzero().equals(discovery.equals("swap-first") || discovery.equals("layout-first") ?
                    List.of(A, B) : List.of()), "one F8 layout binding: " + discovery + " " + core.masks);
            histories++;
        }
        var reopened = new Core();
        reopened.cursor = 0;
        reopened.run(2);
        reopened.waitBusy = 5;
        reopened.masks.clear();
        reopened.mouse.armReadinessProbe();
        reopened.run(1);
        reopened.menu = Menu.MOVE;
        reopened.run(1);
        reopened.menu = Menu.ACTION;
        reopened.run(8);
        check(reopened.nonzero().equals(List.of(A)), "a new blocked ACTION cannot complete the previous reference");
        reopened.step(B, false);
        reopened.run(4);
        check(reopened.trace.stream().noneMatch(x -> x.contains("enabled=true")), "transient menu return is unqualified");
        return histories + 1;
    }

    public static void main(String[] args) {
        immediateMoveClickHistories();
        int histories = stalePresentedHistories() + probeSwapHistories();
        transitionDoesNotInheritHover();
        System.out.println("Lease histories passed: " + histories + "; both menu transitions require fresh motion");
        boundedAutomaticReference();
        probeSubmissionSurvivesCancellation();
        traceExceptionDoesNotAbortAnAppliedFrame();
        expandedProjection();
        twoWidths();
        interruptions();
        unavailableAndUnapplied();
        afterCommitCancellation();
        moveAndMessageCompletion();
        System.out.println("Native battle fake core: two widths, nine interruptions, unqualified/no-step cases, bounded reference and throwing trace passed");
    }

    private static void immediateMoveClickHistories() {
        int histories = 0;
        for (int phase : new int[]{1, 0, 2, 3}) {
          for (int chosen : new int[]{2, 3}) {
            for (String veto : List.of("none", "owner", "manual", "reservation", "layout", "session",
                    "context", "page", "withdrawn", "expired", "crossing")) {
                Core core = new Core();
                core.reference();
                core.click(1, 135, 128);
                core.run(8);
                core.slots = 4;
                core.run(2);
                core.masks.clear();
                int x = chosen == 2 ? 20 : 97;
                int initialPp = core.pp[chosen];
                core.hover(x, 143); // Both one-direction and diagonal targets.
                if (phase == 1) core.suppressDraw = true;
                if (phase > 0) core.run(1); // Applied steering: old image, unsettled image, or settled image.
                if (phase == 3) core.run(2);
                core.down(1, x, 143);
                core.hover(x + 1, 144); // Same-cell motion during a held click cannot retire its latch.
                core.up(x, 143);
                core.hover(x + 1, 144); // Nor can duplicate motion after its matching release.
                int clickedAt = core.masks.size();
                switch (veto) {
                    case "none" -> { }
                    case "owner" -> core.mouse.interrupt("companion-or-focus");
                    case "manual" -> core.step(LEFT, false);
                    case "reservation" -> core.step(0, false);
                    case "layout" -> core.resize();
                    case "session" -> core.mouse.resetState();
                    case "context" -> { core.busy = true; core.run(1); core.busy = false; }
                    case "page" -> { core.page++; core.menu = Menu.MESSAGE; core.run(1); }
                    case "withdrawn" -> core.mouse.show(null);
                    case "expired" -> { core.frame += 8; core.mouse.observe(core.frame, core.nativeState()); }
                    case "crossing" -> core.hover(20, 127);
                    default -> throw new AssertionError(veto);
                }
                if (phase == 1 && veto.equals("none")) {
                    core.run(3);
                    check(!core.nonzero().contains(A), "old picture cannot acknowledge steering");
                }
                core.suppressDraw = false;
                core.run(12);
                if (veto.equals("none")) {
                    check(core.selectedMove == chosen && core.pp[chosen] == initialPp - 1 && core.pp[0] == 35,
                            "immediate click reaches one intended move phase=" + phase + " " + core.trace);
                    check(core.nonzero().stream().filter(mask -> mask == A).count() == 1,
                            "exactly one confirmation phase=" + phase);
                    int directionAt = core.masks.indexOf(DOWN), confirmAt = core.masks.indexOf(A);
                    check(directionAt >= 0 && confirmAt > directionAt + 1 && confirmAt >= clickedAt &&
                                    core.masks.subList(directionAt + 1, confirmAt).contains(0),
                            "applied release gap before clicked confirmation");
                } else {
                    check(!core.nonzero().contains(A) && core.pp[chosen] == initialPp && core.pp[0] == 35,
                            "interrupted click never confirms " + phase + "/" + veto);
                    core.menu = Menu.MOVE; core.busy = false; core.cursor = 0;
                    if (veto.equals("session")) { core.menu = Menu.ACTION; core.reference(); core.menu = Menu.MOVE; }
                    core.run(2); core.masks.clear();
                    core.click(1, x, 143); core.run(12);
                    check(core.selectedMove == chosen && core.pp[chosen] == initialPp - 1 && core.nonzero().stream().filter(m -> m == A).count() == 1,
                            "fresh recovery " + phase + "/" + veto);
                }
                histories++;
            }
          }
        }
        System.out.println("Immediate move-click histories passed: " + histories);
    }

    private static void moveAndMessageCompletion() {
        for (boolean rejected : new boolean[]{false, true}) {
            var core = new Core();
            core.reference();
            core.click(1, 135, 128);
            core.run(8);
            core.masks.clear();
            core.rejectMove = rejected;
            core.click(1, 20, 143); // Non-default third move, selected by ordinary DOWN.
            core.up(20, 143);
            core.run(40);
            check(core.selectedMove == 2, "click reaches the named move");
            check(core.pp[0] == 35 && core.pp[1] == 25 && core.pp[2] == (rejected ? 15 : 14), "only chosen PP changes");
            check(core.nonzero().stream().filter(x -> x == A).count() == 1, "one move A, including rejection");
            check(core.menu == Menu.MESSAGE, "long animation reaches a native message");
            core.masks.clear();
            core.hover(20, 143);
            core.run(3);
            core.click(2, 20, 143);
            core.click(1, 20, 50);
            core.run(3);
            check(core.nonzero().isEmpty(), "message hover, right click and outside click do nothing");
            core.down(1, 20, 143);
            core.page++;
            core.run(2);
            core.up(20, 143);
            core.run(3);
            check(core.nonzero().isEmpty(), "old-page release cannot advance a new page");
            core.click(1, 20, 143);
            core.step(0, true);
            core.up(20, 143);
            core.click(1, 20, 143); // A printing/busy frame cannot admit the next page's click.
            core.run(10);
            check(core.nonzero().equals(List.of(A)), "one intentional page advance");
            core.click(1, 20, 143);
            core.run(10);
            check(core.menu == null, "second fresh click completes the battle messages");
            check(core.nonzero().equals(List.of(A, A)), "no automatic message follow-up");
        }
        System.out.println("Move/message models: named PP, native rejection, long busy, page leases and return to field passed");
    }
}

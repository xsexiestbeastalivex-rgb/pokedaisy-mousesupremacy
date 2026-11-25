package com.pokedaisy.mousesupremacy.pointer;

import com.pokedaisy.mousesupremacy.pointer.PointerLayout.Hit;
import com.pokedaisy.mousesupremacy.pointer.PointerLayout.Owner;
import com.pokedaisy.mousesupremacy.pointer.PointerState.Result;
import com.pokedaisy.mousesupremacy.pointer.PointerState.Sample;

/** One latched observation at a time. Outcomes have no emulator input sink. */
public final class PointerRoute {
    public record Outcome(long gesture, Owner owner, String disposition) {}
    public enum CancelOrigin { CONVENTIONAL, UNIDENTIFIED, OTHER }
    private record Press(long id, long alias, long contact, int button, Hit hit, float x, float y,
                         boolean conventional) {}
    private long sequence;
    private Press press;

    public boolean held() { return press != null; }

    public boolean conventionalHeld() {
        return press != null && press.conventional();
    }

    public boolean ownsConventional(long alias) {
        return conventionalHeld() && press.alias() == alias;
    }

    /** Select the one stream to fence before the shared normalizer sees an Android CANCEL. */
    public long conventionalCancelOwner(long alias, Sample sample, CancelOrigin origin, long receiptTime) {
        if (press == null || !press.conventional() || sample.action() != PointerState.Action.CANCEL
                || !sample.focused() || sample.eventTime() < press.contact() || sample.eventTime() > receiptTime
                || (sample.downTime() > 0 && sample.downTime() != press.contact())) return 0;
        if (origin == CancelOrigin.CONVENTIONAL) return ownsConventional(alias) ? alias : 0;
        if (origin == CancelOrigin.UNIDENTIFIED
                && (ownsConventional(alias) || (sample.hasPosition() && !PointerState.OUTSIDE.equals(sample.hit())))) {
            return press.alias();
        }
        return 0;
    }

    public static boolean isFutureCancellation(Sample sample, long receiptTime) {
        return sample.action() == PointerState.Action.CANCEL && sample.eventTime() > receiptTime;
    }

    /** Keep the callback slot authoritative for contact cleanup, even when its CANCEL cannot own the gesture. */
    public PointerNormalizer.Decision normalize(PointerNormalizer input, Sample sample, int slot, long alias,
                                                int source, int tool, boolean wsa, float slop, long receiptTime) {
        if (isFutureCancellation(sample, receiptTime)) return new PointerNormalizer.Decision(null, null, 0, null);
        if (sample.action() == PointerState.Action.CANCEL && ownsConventional(alias)) {
            return input.observeContactTerminal(sample, slot, source, tool);
        }
        return input.observe(sample, slot, source, tool, wsa, slop);
    }

    public Hit withConventionalOccupancy(PointerNormalizer input, Hit hit) {
        // Older delivered touch can block new admission, but cannot reclassify a newer held gesture.
        if (hit.owner() == Owner.GAME && input.hasDirectContactSince(conventionalHeld() ? press.contact() : 0)) {
            return new Hit(Owner.UNOWNED, null);
        }
        return hit;
    }

    public Outcome cancelCompetingContact(long downTime) {
        return press != null && downTime >= press.contact() ? cancel("upstream-touch") : null;
    }

    public Outcome cancel(String reason) {
        if (press == null) return null;
        var result = new Outcome(press.id(), press.hit().owner(), reason);
        press = null;
        return result;
    }

    public Outcome observe(long alias, Sample sample, Result edge, Hit hit, float slop, boolean conventional) {
        if (press != null) {
            if (sample.isContact() && sample.downTime() > 0 && sample.downTime() < press.contact()) return null;
            if (sample.eventTime() > 0 && sample.eventTime() < press.contact()) return null;
            boolean sameStream = alias == press.alias() && sample.downTime() == press.contact();
            if (edge != null && edge.cancelled() != 0 && alias == press.alias()) {
                return cancel(edge.phase());
            }
            if (edge != null && edge.pressed() != 0) return cancel("competing-pointer");
            if (sample.isContact() && !sameStream) return null;
            if ("hover-exit-inside".equals(edge == null ? null : edge.phase())) return null;
            if (sample.isContact() && edge == null) return null;
            if (hit.owner() != press.hit().owner()) return cancel("owner-crossing");
            if (sample.isContact()) {
                float dx = sample.x() - press.x();
                float dy = sample.y() - press.y();
                if (!sample.hasPosition() || sample.batched() || dx * dx + dy * dy > slop * slop
                        || edge.dragging()) return cancel("drag-or-ambiguous-motion");
            }
            if (edge != null && (edge.released() & press.button()) != 0 && sameStream) {
                return cancel("observed-release");
            }
            return null;
        }
        return edge != null && edge.pressed() != 0 ? begin(alias, sample, edge, hit, conventional) : null;
    }

    private Outcome begin(long alias, Sample sample, Result edge, Hit hit, boolean conventional) {
        if (hit.owner() == Owner.OUTSIDE || hit.owner() == Owner.UNOWNED || alias == 0
                || !sample.hasContactTime() || sample.batched() || Integer.bitCount(edge.pressed()) != 1) return null;
        press = new Press(++sequence, alias, sample.downTime(), edge.pressed(), hit, sample.x(), sample.y(), conventional);
        return new Outcome(press.id(), hit.owner(), "observed-press");
    }
}

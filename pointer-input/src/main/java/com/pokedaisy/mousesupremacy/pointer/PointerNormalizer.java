package com.pokedaisy.mousesupremacy.pointer;

import static com.pokedaisy.mousesupremacy.pointer.PointerState.Action.*;
import static com.pokedaisy.mousesupremacy.pointer.PointerState.OUTSIDE;
import com.pokedaisy.mousesupremacy.pointer.PointerState.Result;
import com.pokedaisy.mousesupremacy.pointer.PointerState.Sample;

import java.util.Arrays;

/** Arbitrates authoritative Android pointer samples without dispatching or consuming platform events. */
public final class PointerNormalizer {
    public static final String STANDARD = "android-mouse";
    public static final String WSA = "wsa-experimental";
    private static final int SOURCE_MOUSE = 0x2002;
    private static final int TOOL_MOUSE = 3;
    private static final int CLASS_POINTER = 2;

    public record Decision(Result result, String interpretation, int displacedSlot, Result displaced) {}

    private final PointerState[] standard;
    private final WsaPointer[] wsa;
    private final long[] directContact;
    private final long[] directClosedThrough;
    private long openingFence = -1;
    // Inclusive Android uptime cutoff shared by all translated slots, including when idle.
    private long wsaClosedThrough = -1;
    private int wsaOwner;

    public PointerNormalizer(int deviceLimit) {
        standard = new PointerState[deviceLimit + 1];
        wsa = new WsaPointer[deviceLimit + 1];
        directContact = new long[deviceLimit + 1];
        Arrays.fill(directContact, -1);
        directClosedThrough = new long[deviceLimit + 1];
        Arrays.fill(directClosedThrough, -1);
    }

    public void attach(int slot) {
        standard[slot] = new PointerState();
        wsa[slot] = new WsaPointer();
    }

    public void detach(int slot) {
        if (wsaOwner == slot) retireOwner(wsa[slot].latestEvent(), "device-detached");
        standard[slot] = null;
        wsa[slot] = null;
        directContact[slot] = -1;
        directClosedThrough[slot] = -1;
    }

    public void fenceOpenings(long through) {
        openingFence = Math.max(openingFence, through);
    }

    public Result invalidate(int slot, long through, String reason, boolean translated) {
        closeDirectContact(slot, through);
        if (translated) {
            if (wsaOwner == slot) return retireOwner(through, reason);
            closeThrough(through);
            return wsa[slot].invalidate(through, reason);
        }
        return invalidateConventional(slot, through, reason);
    }

    public Result invalidateConventional(int slot, long through, String reason) {
        return standard[slot].invalidate(through, reason);
    }

    /** Retire an applicable contact without passing the terminal to either mouse state machine. */
    Decision observeContactTerminal(Sample sample, int slot, int source, int tool) {
        endDirectContact(sample, slot, source, tool);
        return new Decision(null, null, 0, null);
    }

    public Decision observe(Sample sample, int slot, int source, int tool, boolean wsaEnabled, float slop) {
        boolean translated = WsaPointer.accepts(wsaEnabled, source, tool, slot);
        boolean mouse = tool == TOOL_MOUSE && (source & SOURCE_MOUSE) == SOURCE_MOUSE;
        boolean pointer = (source & CLASS_POINTER) != 0;
        String interpretation = translated ? WSA : mouse ? STANDARD : null;
        if (!sample.focused() || sample.isOpening() && sample.downTime() <= openingFence) {
            return new Decision(null, interpretation, 0, null);
        }

        int ownerSlot = wsaOwner;
        WsaPointer owner = ownerSlot == 0 ? null : wsa[ownerSlot];
        boolean contact = sample.isContact();
        long downTime = sample.downTime();
        boolean timed = contact && sample.hasContactTime()
                && (!translated || !sample.isOpening() || downTime == sample.eventTime());
        boolean continuing = translated && owner != null && ownerSlot == slot && owner.generation() == downTime;
        boolean stale = timed && (downTime <= Math.max(wsaClosedThrough, openingFence)
                || owner != null && downTime < owner.generation());
        endDirectContact(sample, slot, source, tool);
        if (translated && contact) {
            if (!timed) return unorderable(sample, slot, ownerSlot, owner, continuing);
            if (stale && !continuing) return new Decision(null, WSA, 0, null);
        }

        boolean conflictingStream = owner != null && (!translated || ownerSlot != slot);
        boolean ambiguousIdentity = slot == 0 || slot == ownerSlot;
        boolean exit = sample.isHover() && OUTSIDE.equals(sample.hit()) && ambiguousIdentity;
        boolean competing = (pointer || source == 0 && ambiguousIdentity) && contact;
        boolean conflict = conflictingStream && !stale && (competing || exit || sample.action() == CANCEL)
                && (contact && !timed ? ambiguousNow(sample, owner) : owner.applies(sample));
        int displacedSlot = 0;
        Result displaced = null;
        if (conflict) {
            displacedSlot = ownerSlot;
            String reason = sample.action() == CANCEL ? "ambiguous-cancel"
                    : exit ? "spatial-exit" : "competing-stream";
            displaced = retireOwner(sample.eventTime(), reason);
        }

        if (slot == 0) return new Decision(null, interpretation, displacedSlot, displaced);

        if (sample.action() == DOWN && pointer && !mouse && (!translated || sample.buttons() == 0)
                && timed && !stale && downTime > directClosedThrough[slot] && downTime >= directContact[slot]) {
            directContact[slot] = downTime;
        }

        Result result = null;
        if (translated) {
            if (!conflict) result = observeTranslated(sample, slot, slop);
        } else if (mouse || sample.action() == CANCEL && standard[slot].held()) {
            if (!stale || !sample.isOpening() || standard[slot].holds(downTime)) {
                result = standard[slot].observe(sample);
            }
            interpretation = STANDARD;
        }
        return new Decision(result, interpretation, displacedSlot, displaced);
    }

    private void endDirectContact(Sample sample, int slot, int source, int tool) {
        if (slot == 0 || !sample.focused() || (sample.action() != UP && sample.action() != CANCEL)
                || tool == TOOL_MOUSE || (source & SOURCE_MOUSE) == SOURCE_MOUSE) return;
        boolean knownPointer = (source & CLASS_POINTER) != 0;
        boolean unidentifiedCancel = sample.action() == CANCEL && source == 0 && tool == 0;
        if (!knownPointer && !unidentifiedCancel) return;

        if (sample.hasContactTime()) {
            // A newer terminal on this slot can close a lost older contact. Tombstone its own
            // generation even when its DOWN has not arrived, without fencing unrelated slots.
            if (knownPointer || sample.downTime() == directContact[slot]) {
                directClosedThrough[slot] = Math.max(directClosedThrough[slot], sample.downTime());
                if (directContact[slot] <= sample.downTime()) directContact[slot] = -1;
            }
        } else if (sample.action() == CANCEL && directContact[slot] >= 0
                && (sample.eventTime() <= 0 || sample.eventTime() >= directContact[slot])) {
            closeDirectContact(slot, directContact[slot]);
        }
    }

    private void closeDirectContact(int slot, long through) {
        directClosedThrough[slot] = Math.max(directClosedThrough[slot], Math.max(directContact[slot], through));
        directContact[slot] = -1;
    }

    private Result observeTranslated(Sample sample, int slot, float slop) {
        WsaPointer stream = wsa[slot];
        long previous = stream.generation();
        long last = stream.latestEvent();
        if (sample.isOpening() && previous >= 0 && sample.downTime() > previous && sample.downTime() == last) {
            return retireOwner(last, "ambiguous-contact-time");
        }
        if (sample.isOpening() && previous < 0 && hasCompetingContact(slot, sample)) {
            closeThrough(sample.eventTime());
            return null;
        }

        Result result = stream.observe(sample, slop);
        if (stream.active()) {
            if (previous >= 0 && previous != stream.generation()) closeThrough(last);
            wsaOwner = slot;
        } else if (previous >= 0 || sample.isContact()) {
            closeThrough(Math.max(last, sample.eventTime()));
            if (wsaOwner == slot) wsaOwner = 0;
        }
        return result;
    }

    private Decision unorderable(Sample sample, int slot, int ownerSlot, WsaPointer owner, boolean continuing) {
        if (owner == null || !continuing && !ambiguousNow(sample, owner)) return new Decision(null, WSA, 0, null);
        String reason = sample.action() == CANCEL ? "ambiguous-cancel" : "invalid-contact-time";
        Result cancelled = retireOwner(sample.eventTime(), reason);
        return slot == ownerSlot ? new Decision(cancelled, WSA, 0, null)
                : new Decision(null, WSA, ownerSlot, cancelled);
    }

    private static boolean ambiguousNow(Sample sample, WsaPointer owner) {
        if (sample.eventTime() > 0 && sample.eventTime() < owner.generation()) return false;
        return sample.isCancellationSignal()
                || sample.downTime() <= 0 && sample.eventTime() > 0 && !sample.isOpening();
    }

    private Result retireOwner(long through, String reason) {
        WsaPointer owner = wsa[wsaOwner];
        closeThrough(Math.max(owner.latestEvent(), through));
        wsaOwner = 0;
        return owner.cancel(reason);
    }

    private void closeThrough(long through) {
        wsaClosedThrough = Math.max(wsaClosedThrough, through);
    }

    /** Current buttonless occupancy survives between Activity callbacks. */
    public boolean hasDirectContact() { return hasDirectContactSince(0); }

    /** True only for a buttonless opening that the contact ledger admitted on this slot. */
    public boolean isDirectOpening(int slot, Sample sample) {
        return slot != 0 && sample.action() == DOWN && sample.buttons() == 0
                && directContact[slot] >= 0 && directContact[slot] == sample.downTime();
    }

    boolean hasDirectContactSince(long generation) {
        for (long contact : directContact) if (contact >= generation) return true;
        return false;
    }

    private boolean hasCompetingContact(int slot, Sample sample) {
        for (int other = 1; other < standard.length; other++) {
            if (standard[other] != null && standard[other].held()) return true;
            if (directContact[other] >= 0 && (other != slot || sample.downTime() <= directContact[other])) return true;
        }
        // A newer translated generation on the same guest ID can recover a missing direct-touch up.
        closeDirectContact(slot, sample.downTime());
        return false;
    }
}

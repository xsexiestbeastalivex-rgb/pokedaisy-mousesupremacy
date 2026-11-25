package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.Action.*;
import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;
import com.pokedaisy.mousesupremacy.probe.PointerState.Result;
import com.pokedaisy.mousesupremacy.probe.PointerState.Sample;

import java.util.Arrays;

/** Arbitration for authoritative probe callbacks. No raw Android event is consumed here. */
final class ProbeInput {
    static final String STANDARD = "android-mouse";
    static final String WSA = "wsa-experimental";
    private static final int SOURCE_MOUSE = 0x2002;
    private static final int TOOL_MOUSE = 3;
    private static final int CLASS_POINTER = 2;

    record Decision(Result result, String interpretation, int displacedSlot, Result displaced) {}

    private final PointerState[] standard;
    private final WsaPointer[] wsa;
    private final long[] directContact;
    private long openingFence = -1;
    private int wsaOwner;

    ProbeInput(int deviceLimit) {
        standard = new PointerState[deviceLimit + 1];
        wsa = new WsaPointer[deviceLimit + 1];
        directContact = new long[deviceLimit + 1];
        Arrays.fill(directContact, -1);
    }

    void attach(int slot) {
        standard[slot] = new PointerState();
        wsa[slot] = new WsaPointer();
    }

    void detach(int slot) {
        standard[slot] = null;
        wsa[slot] = null;
        directContact[slot] = -1;
        if (wsaOwner == slot) wsaOwner = 0;
    }

    void fenceOpenings(long through) {
        openingFence = Math.max(openingFence, through);
    }

    Result invalidate(int slot, long through, String reason, boolean translated) {
        directContact[slot] = -1;
        if (translated) {
            if (wsaOwner == slot) wsaOwner = 0;
            return wsa[slot].invalidate(through, reason);
        }
        return standard[slot].invalidate(through, reason);
    }

    Decision observe(Sample sample, int slot, int source, int tool, boolean wsaEnabled, float slop) {
        boolean translated = WsaPointer.accepts(wsaEnabled, source, tool, slot);
        boolean mouse = tool == TOOL_MOUSE && (source & SOURCE_MOUSE) == SOURCE_MOUSE;
        boolean pointer = (source & CLASS_POINTER) != 0;
        String interpretation = translated ? WSA : mouse ? STANDARD : null;
        if (!sample.focused() || sample.isOpening() && sample.downTime() <= openingFence) {
            return new Decision(null, interpretation, 0, null);
        }

        int displacedSlot = 0;
        Result displaced = null;
        boolean conflictingStream = wsaOwner != 0 && (!translated || wsaOwner != slot);
        boolean ambiguousIdentity = slot == 0 || slot == wsaOwner;
        boolean exit = sample.isHover() && OUTSIDE.equals(sample.hit()) && ambiguousIdentity;
        boolean competing = (pointer || source == 0 && ambiguousIdentity) && sample.isContact();
        boolean conflict = (competing || exit || sample.action() == CANCEL)
                && conflictingStream && wsa[wsaOwner].applies(sample);
        if (conflict) {
            displacedSlot = wsaOwner;
            String reason = sample.action() == CANCEL ? "ambiguous-cancel"
                    : exit ? "spatial-exit" : "competing-stream";
            displaced = wsa[wsaOwner].reject(sample, reason);
            wsaOwner = 0;
        }

        if (slot == 0) return new Decision(null, interpretation, displacedSlot, displaced);

        if ((!conflictingStream || conflict) && pointer && !mouse) {
            boolean direct = !translated || sample.buttons() == 0;
            if (sample.action() == DOWN && direct && sample.downTime() >= directContact[slot]) {
                directContact[slot] = sample.downTime();
            }
            if ((sample.action() == UP || sample.action() == CANCEL)
                    && (sample.downTime() <= 0 || sample.downTime() >= directContact[slot])) {
                directContact[slot] = -1;
            }
        }
        if (sample.action() == CANCEL && !translated && !mouse && directContact[slot] >= 0
                && (sample.downTime() <= 0 || sample.downTime() >= directContact[slot])) directContact[slot] = -1;

        Result result = null;
        if (translated) {
            if (conflict || sample.isOpening() && !wsa[slot].active() && hasCompetingContact(slot, sample)) {
                result = wsa[slot].reject(sample, "competing-contact");
            } else {
                result = wsa[slot].observe(sample, slop);
            }
            if (wsa[slot].active()) wsaOwner = slot;
            else if (wsaOwner == slot) wsaOwner = 0;
        } else if (mouse || sample.action() == CANCEL && standard[slot].held()) {
            result = standard[slot].observe(sample);
            interpretation = STANDARD;
        }
        return new Decision(result, interpretation, displacedSlot, displaced);
    }

    private boolean hasCompetingContact(int slot, Sample sample) {
        for (int other = 1; other < standard.length; other++) {
            if (standard[other] != null && standard[other].held()) return true;
            if (directContact[other] >= 0 && (other != slot || sample.downTime() <= directContact[other])) return true;
        }
        // A newer translated generation on the same guest ID can recover a missing direct-touch up.
        directContact[slot] = -1;
        return false;
    }
}

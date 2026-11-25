package com.pokedaisy.mousesupremacy.pointer;

import com.pokedaisy.mousesupremacy.pointer.PointerState.Result;
import com.pokedaisy.mousesupremacy.pointer.PointerState.Sample;

import static com.pokedaisy.mousesupremacy.pointer.PointerState.Action.*;

/** Opt-in, single-contact diagnostic adapter. Touch selection never becomes a mouse button. */
public final class DirectTouch {
    public static final int CONTACT = 8;
    private Sample start;
    private long alias;
    private long latest;
    private long closedThrough = -1;

    public Result invalidate(long through, String reason) {
        closedThrough = Math.max(closedThrough, Math.max(through, latest));
        if (start == null) return null;
        var result = edge(reason, start.hit(), 0, 0, CONTACT, 0);
        start = null;
        return result;
    }

    public Result observe(long device, Sample sample, boolean finger, boolean otherPointer, float slop) {
        if (!sample.focused() || !sample.isContact()) return null;
        long down = sample.downTime();
        if (sample.hasContactTime() && (down <= closedThrough || start != null && down < start.downTime())) return null;
        if (start != null && (sample.action() == CANCEL || sample.isCancellationSignal())) {
            if (sample.eventTime() <= 0 || sample.eventTime() >= start.downTime()) {
                return invalidate(sample.eventTime(), "direct-cancel");
            }
            return null;
        }
        if (start != null && otherPointer) return invalidate(sample.eventTime(), "competing-pointer");
        if (!finger || device == 0 || !sample.hasContactTime() || sample.buttons() != 0 || sample.actionButton() != 0) {
            return start != null && device == alias && (down == start.downTime() || sample.eventTime() >= latest)
                    ? invalidate(sample.eventTime(), "direct-unqualified-contact") : null;
        }
        if (start == null) {
            if (sample.action() != DOWN || sample.eventTime() != down || sample.pointerCount() != 1
                    || !sample.hasPosition() || sample.batched() || PointerState.OUTSIDE.equals(sample.hit())) {
                closedThrough = Math.max(closedThrough, sample.eventTime());
                return null;
            }
            start = sample;
            latest = down;
            alias = device;
            return edge("direct-down", sample.hit(), CONTACT, 0, 0, CONTACT);
        }
        if (device != alias || down != start.downTime() || sample.pointerId() != start.pointerId()
                || sample.eventTime() < latest || !sample.hasPosition() || sample.batched()
                || PointerState.OUTSIDE.equals(sample.hit())) return invalidate(sample.eventTime(), "direct-ambiguous");
        float dx = sample.x() - start.x();
        float dy = sample.y() - start.y();
        latest = sample.eventTime();
        if (dx * dx + dy * dy > slop * slop) return invalidate(latest, "direct-slop");
        if (sample.action() == MOVE) return edge("direct-move", sample.hit(), 0, 0, 0, CONTACT);
        if (sample.action() == UP) {
            invalidate(latest, "direct-up");
            return edge("direct-up", sample.hit(), 0, CONTACT, 0, 0);
        }
        return null;
    }

    private static Result edge(String phase, String region, int down, int up, int cancel, int held) {
        return new Result(phase, region, false, false, down, up, cancel, held, false, false, 0);
    }
}

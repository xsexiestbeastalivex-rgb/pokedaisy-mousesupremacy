package com.pokedaisy.mousesupremacy.probe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Main-thread recorder. Entries are immutable in practice after dispatch finishes. */
final class Trace {
    static final int SCHEMA = 1;
    static final int CAPACITY = 4096;

    private final int capacity;
    private final LinkedHashMap<Long, Map<String, Object>> entries = new LinkedHashMap<>();
    private final LinkedHashMap<Long, String> moves = new LinkedHashMap<>();
    private final Map<String, Long> segmentMoves = new HashMap<>();
    private long sequence;
    private long coalesced;
    private long discardedMoves;
    private long discardedBoundaries;
    private boolean recording;

    Trace(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("positive capacity required");
        this.capacity = capacity;
    }

    void start() {
        entries.clear();
        moves.clear();
        segmentMoves.clear();
        sequence = coalesced = discardedMoves = discardedBoundaries = 0;
        recording = true;
    }

    boolean recording() {
        return recording;
    }

    void add(Map<String, Object> entry, String moveKey) {
        if (!recording) return;
        long id = ++sequence;
        entry.put("seq", id);

        if (moveKey == null) {
            segmentMoves.clear();
        } else {
            Long previous = segmentMoves.get(moveKey);
            Map<String, Object> earlier = entries.get(previous);
            if (earlier != null && sameMotion(earlier, entry)) {
                entries.remove(previous);
                moves.remove(previous);
                coalesced++;
            }
        }

        if (entries.size() == capacity) {
            if (!moves.isEmpty()) {
                var oldest = moves.entrySet().iterator();
                var removed = oldest.next();
                entries.remove(removed.getKey());
                segmentMoves.remove(removed.getValue(), removed.getKey());
                oldest.remove();
                discardedMoves++;
            } else if (moveKey != null) {
                discardedMoves++;
                return;
            } else {
                entries.remove(entries.keySet().iterator().next());
                discardedBoundaries++;
            }
        }

        entries.put(id, entry);
        if (moveKey != null) {
            moves.put(id, moveKey);
            segmentMoves.put(moveKey, id);
        }
    }

    int indexedMoves() { return segmentMoves.size(); }

    private static boolean sameMotion(Map<String, Object> a, Map<String, Object> b) {
        return Objects.equals(a.get("device"), b.get("device"))
                && Objects.equals(a.get("source"), b.get("source"))
                && Objects.equals(a.get("toolType"), b.get("toolType"))
                && Objects.equals(a.get("buttonState"), b.get("buttonState"))
                && Objects.equals(a.get("hitRegion"), b.get("hitRegion"));
    }

    Map<String, Object> stop() {
        recording = false;
        List<Map<String, Object>> snapshot = new ArrayList<>(entries.values());
        entries.clear();
        moves.clear();
        segmentMoves.clear();
        return fields("schemaVersion", SCHEMA, "capacity", capacity, "observedCount", sequence,
                "coalescedMoves", coalesced, "discardedMoves", discardedMoves,
                "discardedBoundaries", discardedBoundaries, "events", snapshot);
    }

    static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}

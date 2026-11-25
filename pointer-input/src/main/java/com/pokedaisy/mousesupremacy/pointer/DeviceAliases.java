package com.pokedaisy.mousesupremacy.pointer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/** Bounded concurrent slots. Public session aliases are never reused for another attachment. */
public final class DeviceAliases {
    public record Device(int slot, long alias, String identity) {}
    private static final Device UNKNOWN = new Device(0, 0, "unaliased");

    private final Map<Integer, Device> ids = new HashMap<>();
    private final Device[] slots;
    private long nextAlias;

    public DeviceAliases(int capacity) {
        slots = new Device[capacity + 1];
    }

    public Device get(int id, IntFunction<String> identify, java.util.function.IntConsumer attach) {
        Device device = ids.get(id);
        if (device != null) return device;
        for (int slot = 1; slot < slots.length; slot++) {
            if (slots[slot] != null) continue;
            device = new Device(slot, ++nextAlias, identify.apply(id));
            attach.accept(slot);
            ids.put(id, device);
            slots[slot] = device;
            return device;
        }
        return UNKNOWN;
    }

    public Device remove(int id) {
        Device device = ids.remove(id);
        if (device != null) slots[device.slot()] = null;
        return device;
    }

    public Device at(int slot) {
        return slots[slot];
    }

    public void clear() {
        ids.clear();
        java.util.Arrays.fill(slots, null);
        nextAlias = 0;
    }
}

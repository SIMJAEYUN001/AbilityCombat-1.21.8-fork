package com.abilitycombat.utils.collection;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class QueueOnIterateHashSet<E> {

    private final Set<E> data = new HashSet<>();
    private final Set<E> pendingAdd = new HashSet<>();
    private final Set<E> pendingRemove = new HashSet<>();
    private int iterating = 0;

    public boolean add(E value) {
        if (value == null) {
            return false;
        }
        if (iterating > 0) {
            pendingRemove.remove(value);
            return pendingAdd.add(value);
        }
        return data.add(value);
    }

    public boolean remove(E value) {
        if (value == null) {
            return false;
        }
        if (iterating > 0) {
            if (pendingAdd.remove(value)) {
                return true;
            }
            return pendingRemove.add(value);
        }
        return data.remove(value);
    }

    public boolean contains(E value) {
        if (value == null) {
            return false;
        }
        if (pendingAdd.contains(value)) {
            return true;
        }
        if (pendingRemove.contains(value)) {
            return false;
        }
        return data.contains(value);
    }

    public boolean isEmpty() {
        if (!pendingAdd.isEmpty()) {
            return false;
        }
        return data.isEmpty() || data.size() == pendingRemove.size();
    }

    public int size() {
        return data.size() + pendingAdd.size() - pendingRemove.size();
    }

    public void clear() {
        data.clear();
        pendingAdd.clear();
        pendingRemove.clear();
    }

    public void forEach(Consumer<? super E> consumer) {
        if (consumer == null) {
            return;
        }
        beginIteration();
        try {
            for (E value : data) {
                if (pendingRemove.contains(value)) {
                    continue;
                }
                consumer.accept(value);
            }
        } finally {
            endIteration();
        }
    }

    private void beginIteration() {
        iterating++;
    }

    private void endIteration() {
        iterating = Math.max(0, iterating - 1);
        if (iterating == 0) {
            flushPending();
        }
    }

    private void flushPending() {
        if (!pendingRemove.isEmpty()) {
            data.removeAll(pendingRemove);
            pendingRemove.clear();
        }
        if (!pendingAdd.isEmpty()) {
            data.addAll(pendingAdd);
            pendingAdd.clear();
        }
    }
}

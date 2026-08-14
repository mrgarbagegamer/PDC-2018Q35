package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import org.jspecify.annotations.Nullable;

class TaskPool<T extends CombinationGeneratorTask> {

    private final @Nullable T[] array;
    private final int capacity;

    private int head = 0;
    private int tail = 0;
    private int size = 0;

    // Safe, since the array will only contain T instances from put(T).
    @SuppressWarnings("unchecked")
    TaskPool(int capacity) {
        this.capacity = mustBePositive(capacity, "capacity");
        this.array = (T[]) new CombinationGeneratorTask[this.capacity];
    }

    @Nullable
    T get() {
        if (this.size == 0)
            return null;

        T task = this.array[this.head];

        this.array[this.head] = null; // Help GC
        this.head = (this.head + 1) % this.capacity;
        this.size--;
        return task;
    }

    boolean put(T task) {
        if (this.size >= this.capacity)
            return false;

        this.array[this.tail] = mustNotBeNull(task, "task");
        this.tail = (this.tail + 1) % this.capacity;
        this.size++;
        return true;
    }

    int size() { return this.size; }

    int capacity() { return this.capacity; }

    boolean isEmpty() { return this.size == 0; }

    boolean isFull() { return this.size == this.capacity; }
}

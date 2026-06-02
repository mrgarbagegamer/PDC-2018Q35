package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.AccessMode;
import com.github.mrgarbagegamer.queues.QueueMetadataProvider.Boundedness;

public final class QueueTestFixtures {

    private QueueTestFixtures() { utilityClassError("QueueTestFixtures"); }

    /**
     * A lightweight fake queue used purely for testing validation and preallocation.
     * 
     * @param <Q> the type of the underlying queue being wrapped
     */
    public static class MockQueueWrapper<Q> implements QueueWrapper<Q> {
        private final Q underlyingQueue; // To satisfy unwrap()
        private final Boundedness boundedness;
        private final AccessMode accessMode;
        private final int capacity;
        private final boolean acceptableCapacityOverride;
        private boolean empty;

        MockQueueWrapper(Q underlyingQueue, Boundedness boundedness, AccessMode accessMode,
                int capacity, boolean acceptableCapacityOverride, boolean empty) {
            this.underlyingQueue = underlyingQueue;
            this.boundedness = boundedness;
            this.accessMode = accessMode;
            this.capacity = capacity;
            this.acceptableCapacityOverride = acceptableCapacityOverride;
            this.empty = empty;
        }

        @Override
        public Q unwrap() { return underlyingQueue; }

        @Override
        public Boundedness boundedness() { return boundedness; }

        @Override
        public AccessMode accessMode() { return accessMode; }

        @Override
        public int capacity() { return capacity; }

        @Override
        public boolean isCapacityAcceptable(int expectedCapacity) {
            return acceptableCapacityOverride;
        }

        @Override
        public int size() { return empty ? 0 : 1; }

        @Override
        public boolean isEmpty() { return empty; }

        public void setEmpty(boolean empty) { this.empty = empty; }

        @Override
        public boolean offer(WorkBatch batch) {
            if (capacity > 0 && !empty)
                return false;
            this.empty = false;
            return true;
        }
    }

    /**
     * Fluent Builder for generating MockQueueWrappers.
     */
    public static class MockQueueBuilder<Q> {
        private Q underlyingQueue = null; // Can be null if the test doesn't unwrap
        private Boundedness boundedness = Boundedness.BOUNDED;
        private AccessMode accessMode = AccessMode.MPMC;
        private int capacity = 1024;
        private boolean acceptableCapacityOverride = true;
        private boolean empty = true;

        public static <T> MockQueueBuilder<T> create() { return new MockQueueBuilder<>(); }

        public MockQueueBuilder<Q> underlying(Q queue) {
            this.underlyingQueue = queue;
            return this;
        }

        public MockQueueBuilder<Q> unbounded() {
            this.boundedness = Boundedness.UNBOUNDED;
            this.capacity = Integer.MAX_VALUE;
            return this;
        }

        public MockQueueBuilder<Q> accessMode(AccessMode mode) {
            this.accessMode = mode;
            return this;
        }

        public MockQueueBuilder<Q> capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public MockQueueBuilder<Q> rejectsCapacity() {
            this.acceptableCapacityOverride = false;
            return this;
        }

        public MockQueueBuilder<Q> notEmpty() {
            this.empty = false;
            return this;
        }

        public MockQueueWrapper<Q> build() {
            return new MockQueueWrapper<>(underlyingQueue, boundedness, accessMode, capacity,
                    acceptableCapacityOverride, empty);
        }
    }
}

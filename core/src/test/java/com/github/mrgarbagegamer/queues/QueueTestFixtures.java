package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static java.util.Objects.checkIndex;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

        MockQueueWrapper(MockQueueBuilder<Q> builder) {
            this.underlyingQueue = builder.underlyingQueue;
            this.boundedness = builder.boundedness;
            this.accessMode = builder.accessMode;
            this.capacity = builder.capacity;
            this.acceptableCapacityOverride = builder.acceptableCapacityOverride;
            this.empty = builder.empty;
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
            this.accessMode = mustNotBeNull(mode, "mode");
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

        public MockQueueWrapper<Q> build() { return new MockQueueWrapper<>(this); }
    }

    /**
     * Generates a list of perfectly uniform, valid MockQueueWrappers for baseline testing.
     * 
     * @param <Q>      the type of the underlying queues being wrapped
     * @param count    the number of queues to generate in the list
     * @param template the builder that will be used to generate each queue in the list (each queue
     *                 will be built from the template, so they will all be identical)
     * @return a list of MockQueueWrappers where each queue is built from the provided template,
     *         resulting in a perfectly uniform list of valid queues
     */
    public static <Q> List<MockQueueWrapper<Q>> createUniformList(int count,
            MockQueueBuilder<Q> template) {
        mustNotBeNull(template, "template");
        return Stream.generate(template::build).limit(count).collect(toUnmodifiableList());
    }

    /**
     * Generates a list where exactly one queue (at the specified index) is misconfigured. Excellent
     * for testing indexed exception messages.
     * 
     * @param <Q>         the type of the underlying queues being wrapped
     * @param count       the total number of queues in the list
     * @param poisonIndex the index at which the misconfigured "poison pill" queue should be placed
     * @param template    a builder for the well-behaved queues that should fill the rest of the
     *                    list
     * @param poison      a builder for the misconfigured "poison pill" queue that should be placed
     *                    at {@code poisonIndex}
     * @return a list of MockQueueWrappers where all queues are built from the template except for
     *         the one at {@code poisonIndex}, which is built from poison
     */
    public static <Q> List<MockQueueWrapper<Q>> createListWithPoisonPill(int count, int poisonIndex,
            MockQueueBuilder<Q> template, MockQueueBuilder<Q> poison) {
        mustNotBeNull(template, "template");
        mustNotBeNull(poison, "poison");
        checkIndex(poisonIndex, count);

        List<MockQueueWrapper<Q>> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i == poisonIndex) {
                list.add(poison.build());
            } else {
                list.add(template.build());
            }
        }

        return List.copyOf(list);
    }
}

package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static com.github.mrgarbagegamer.queues.QueueUtils.newImmutableQueueList;
import static java.util.Objects.checkIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

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
        private final boolean rejectsOffer;
        private int size;

        MockQueueWrapper(MockQueueBuilder<Q> builder) {
            this.underlyingQueue = builder.underlyingQueue;
            this.boundedness = builder.boundedness;
            this.accessMode = builder.accessMode;
            this.capacity = builder.capacity;
            this.size = builder.size;
            this.rejectsOffer = builder.rejectsOffer;
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
        public int size() { return size; }

        public void setSize(int size) { this.size = size; }

        @Override
        public boolean isEmpty() { return size == 0; }

        @Override
        public boolean offer(WorkBatch batch) {
            if (rejectsOffer || (boundedness.isBounded() && capacity > 0 && size >= capacity))
                return false;
            size++;
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
        private int size = 0;
        private boolean rejectsOffer = false;

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

        public MockQueueBuilder<Q> initialSize(int size) {
            this.size = size;
            return this;
        }

        public MockQueueBuilder<Q> rejectsOffer() {
            this.rejectsOffer = true;
            return this;
        }

        public MockQueueWrapper<Q> build() { return new MockQueueWrapper<>(this); }

        public List<MockQueueWrapper<Q>> buildList(int listSize) {
            return newImmutableQueueList(listSize, this::build);
        }
    }

    /**
     * Generates a list of perfectly uniform, valid MockQueueWrappers for baseline testing.
     * 
     * @param template the builder that will be used to generate each queue in the list (each queue
     *                 will be built from the template, so they will all be identical)
     * @param count    the number of queues to generate in the list
     * 
     * @param <Q>      the type of the underlying queues being wrapped
     * @return a list of MockQueueWrappers where each queue is built from the provided template,
     *         resulting in a perfectly uniform list of valid queues
     */
    public static <Q> List<MockQueueWrapper<Q>> createUniformList(MockQueueBuilder<Q> template,
            int count) {
        return newImmutableQueueList(count, mustNotBeNull(template, "template")::build);
    }

    /**
     * Generates a list where exactly one queue (at the specified index) is misconfigured. Excellent
     * for testing indexed exception messages.
     * 
     * @param template    a builder for the well-behaved queues that should fill the rest of the
     *                    list
     * @param poison      a builder for the misconfigured "poison pill" queue that should be placed
     *                    at {@code poisonIndex}
     * @param count       the total number of queues in the list
     * @param poisonIndex the index at which the misconfigured "poison pill" queue should be placed
     * 
     * @param <Q>         the type of the underlying queues being wrapped
     * @return a list of MockQueueWrappers where all queues are built from the template except for
     *         the one at {@code poisonIndex}, which is built from poison
     */
    public static <Q> List<MockQueueWrapper<Q>> createListWithPoisonPill(
            MockQueueBuilder<Q> template, MockQueueBuilder<Q> poison, int count, int poisonIndex) {
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

    private static final QueueSelector<Object> DUMMY_SELECTOR = new QueueSelector<>() {

        @Override
        public WorkBatch poll(int threadId, List<? extends Object> queues, BackoffStrategy backoff,
                BooleanSupplier shouldContinue) {
            return null;
        }

        @Override
        public boolean offer(WorkBatch batch, int threadId, List<? extends Object> queues,
                BackoffStrategy backoff, BooleanSupplier shouldContinue) {
            return false;
        }

        @Override
        public String toString() { return "DummySelector"; }
    };

    /**
     * {@return a dummy QueueSelector that can be used in tests where the selector's behavior is not
     * under test} This QueueSelector will return {@code null} for polls and {@code false} for
     * offers.
     * 
     * @param <Q> the type of the queues that would be passed to the selector
     */
    public static <Q> QueueSelector<Q> dummySelector() { return DUMMY_SELECTOR.asType(); }
}

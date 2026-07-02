package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;

import com.github.mrgarbagegamer.WorkBatch;

// TODO: Remove Javadocs, since the interface isn't public and is used internally.
interface QueueWrapper<Q> {
    /**
     * Unwraps the underlying queue from this wrapper.
     * 
     * @return the non-{@code null}, underlying queue being wrapped by this wrapper.
     * @performance {@code O(1)} unwrapping.
     * @threading Must be thread-safe.
     * @memory Must not allocate.
     */
    Q unwrap();

    /**
     * Unwraps a list of queue wrappers into a list of their underlying queues.
     * 
     * @param <Q>      the type of the underlying queues
     * @param wrappers the non-{@code null} list of queue wrappers to unwrap
     * @return the list of underlying queues
     * @performance {@code O(n)} unwrapping, where {@code n} is the size of the input list.
     * @threading Not thread-safe. The caller must ensure that the input list is not modified
     *            concurrently during unwrapping.
     * @memory Allocates a new list to hold the unwrapped queues plus stream overhead.
     */
    static <Q> List<Q> unwrapList(List<? extends QueueWrapper<Q>> wrappers) {
        return mustNotBeNull(wrappers, "wrappers").stream().map(QueueWrapper::unwrap)
                .collect(toUnmodifiableList());
    }

    /**
     * Offers a batch of work to the underlying queue. This method must be implemented by all
     * wrappers to aid preallocation utilities. This method should follow the same contract as
     * {@link java.util.Queue#offer(Object)}.
     * 
     * @param batch the non-{@code null} batch of work to be offered to the underlying queue.
     * @return {@code true} if the batch was successfully offered to the underlying queue,
     *         {@code false} otherwise.
     */
    boolean offer(WorkBatch batch);

    /**
     * Returns the number of elements in the underlying queue. This method is used by preallocation
     * utilities to determine how many elements are currently in the queue and whether preallocation
     * is needed. This method should follow the same contract as {@link java.util.Queue#size()}.
     * 
     * @return the number of elements in the underlying queue.
     */
    int size();

    /**
     * Returns {@code true} if the underlying queue is empty, {@code false} otherwise. This method
     * is used by preallocation utilities to determine whether the queue is empty and whether
     * preallocation is needed. This method should follow the same contract as
     * {@link java.util.Queue#isEmpty()}.
     * 
     * @return {@code true} if the underlying queue is empty, {@code false} otherwise.
     */
    boolean isEmpty();

    /**
     * Gets the {@link AccessMode} of the queue.
     * 
     * @return the non-{@code null} {@code AccessMode} of the queue.
     */
    AccessMode accessMode();

    /**
     * Gets the {@link Boundedness} of the queue.
     * 
     * @return the non-{@code null} {@code Boundedness} of the queue.
     */
    Boundedness boundedness();

    /**
     * Gets the capacity of the {@linkplain Boundedness#BOUNDED bounded} queue.
     * 
     * @apiNote This method should be used in conjunction with {@link Boundedness#isBounded()} to
     *          determine whether the returned capacity is meaningful.
     * @implSpec Implementations must return a non-negative capacity for bounded queues. The
     *           capacity for {@link Boundedness#UNBOUNDED unbounded} queues can be any value, but
     *           it is recommended to pick a special value that clearly indicates unboundedness,
     *           such as {@code -1} or {@code Integer.MAX_VALUE}.
     * 
     * @return the non-negative capacity of the queue if it is bounded, or a special value
     *         otherwise.
     */
    int capacity();

    /**
     * Represents the access mode of a queue, indicating whether it supports multiple producers
     * and/or multiple consumers.
     * 
     * @apiNote A queue should have only one access mode to define its concurrency properties, as
     *          enforced by {@link QueueMetadataProvider#accessMode()}.
     * @implNote The access mode is represented as an {@code enum} to ensure mutual exclusivity.
     *           While a previous design used marker interfaces with a sealed hierarchy, this
     *           approach was less intuitive and more error-prone.
     * 
     * @since 2026.02 - Queue Injection Refactor
     */
    public enum AccessMode {
        /**
         * Multiple Producers, Multiple Consumers: The queue supports concurrent access by multiple
         * producer threads and multiple consumer threads. This is the most flexible access mode,
         * allowing for high concurrency and throughput in multi-threaded environments.
         */
        MPMC(true, true),

        /**
         * Multiple Producers, Single Consumer: The queue supports concurrent access by multiple
         * producer threads but only one consumer thread.
         */
        MPSC(true, false),

        /**
         * Single Producer, Multiple Consumers: The queue supports access by only one producer
         * thread but allows multiple consumer threads to access it concurrently.
         */
        SPMC(false, true),

        /**
         * Single Producer, Single Consumer: The queue supports access by only one producer thread
         * and one consumer thread. This is the simplest and most restrictive access mode, but it
         * can prove highly efficient per the Single Writer Principle.
         */
        SPSC(false, false);

        private final boolean multiProducer;
        private final boolean multiConsumer;

        private AccessMode(boolean multiProducer, boolean multiConsumer) {
            this.multiProducer = multiProducer;
            this.multiConsumer = multiConsumer;
        }

        /**
         * Indicates whether the queue supports multiple producers.
         * 
         * @return {@code true} if the queue supports multiple producers, {@code false} otherwise.
         * @since 2026.05 - Queue Metadata Interface
         */
        public final boolean isMultiProducer() { return multiProducer; }

        /**
         * Indicates whether the queue supports multiple consumers.
         * 
         * @return {@code true} if the queue supports multiple consumers, {@code false} otherwise.
         * @since 2026.05 - Queue Metadata Interface
         */
        public final boolean isMultiConsumer() { return multiConsumer; }

        /**
         * Indicates whether the queue supports only a single producer.
         * 
         * @return {@code true} if the queue supports only a single producer, {@code false}
         *         otherwise.
         * @since 2026.05 - Queue Metadata Interface
         */
        public final boolean isSingleProducer() { return !multiProducer; }

        /**
         * Indicates whether the queue supports only a single consumer.
         * 
         * @return {@code true} if the queue supports only a single consumer, {@code false}
         *         otherwise.
         * @since 2026.05 - Queue Metadata Interface
         */
        public final boolean isSingleConsumer() { return !multiConsumer; }
    }

    /**
     * Represents the boundedness of a queue, indicating whether it has a fixed capacity or can grow
     * dynamically.
     * 
     * @apiNote A queue should have only one boundedness to define its capacity properties, as
     *          enforced by {@link QueueMetadataProvider#boundedness()}.
     * @implNote The boundedness is represented as an {@code enum} to ensure mutual exclusivity.
     *           While a previous design used marker interfaces with a sealed hierarchy, this
     *           approach was less intuitive and more error-prone.
     * 
     * @since 2026.02 - Queue Injection Refactor
     */
    public enum Boundedness {
        /**
         * Bounded: The queue has a fixed capacity determined at construction time.
         */
        BOUNDED,

        /**
         * Unbounded: The queue can grow dynamically to accommodate any number of elements, limited
         * only by system resources.
         */
        UNBOUNDED;

        /**
         * Indicates whether the queue is bounded.
         * 
         * @return {@code true} if the queue is bounded, {@code false} if it is unbounded.
         * @since 2026.05 - Queue Metadata Interface
         */
        public final boolean isBounded() { return this == BOUNDED; }
    }
}

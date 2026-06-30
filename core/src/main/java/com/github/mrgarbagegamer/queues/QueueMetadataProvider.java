package com.github.mrgarbagegamer.queues;

// TODO: Update Javadocs
/**
 * Represents metadata about a queue, including its {@link AccessMode} and {@link Boundedness}. This
 * interface provides a standardized way to retrieve important properties of a queue, which are used
 * for validation and pre-allocation in {@link QueueUtils}. By defining these properties in a single
 * interface, we can bridge the gap between different queue implementations and provide a consistent
 * API for accessing queue metadata, regardless of the underlying queue type.
 * 
 * @since 2026.05 - Queue Metadata Interface
 * @performance All methods, except for {@link #isCapacityAcceptable(int)}, must be {@code O(1)}
 *              retrievals of immutable properties.
 * @threading All methods must be thread-safe, as the metadata should be immutable after
 *            construction.
 * @memory All methods must not allocate, as they should simply return existing properties of the
 *         queue.
 */
public interface QueueMetadataProvider {

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
     * Gets the capacity of the queue if it is {@link #BOUNDED bounded}, or a special value (e.g.
     * {@code -1}) if it is {@link #UNBOUNDED unbounded}. This additional information about the
     * queue's capacity is helpful for the validation logic in {@link QueueUtils}, including
     * pre-allocation utilities.
     * 
     * <p>
     * Though this method must return a standard value for bounded queues (something like
     * {@code (0, Integer.MAX_VALUE)}), the special value for unbounded queues is not strictly
     * defined and can be implementation-specific. By leaving this detail open, we allow this
     * interface to be compatible with a wide range of queue interfaces and implementations, which
     * may have different conventions for representing unbounded capacity. It is recommended that
     * implementations document their chosen convention for unbounded queues to ensure clarity, and
     * that {@link Boundedness#isBounded()} is used before interpreting the capacity to avoid
     * confusion.
     * </p>
     * 
     * @return the positive (or potentially zero) capacity of the queue if it is bounded, or a
     *         special value otherwise.
     * @see java.util.concurrent.BlockingQueue#remainingCapacity()
     * @see org.jctools.queues.MessagePassingQueue#UNBOUNDED_CAPACITY
     * @see org.jctools.queues.MessagePassingQueue#capacity()
     */
    int capacity();

    /**
     * Represents the access mode of a queue, indicating whether it supports multiple producers
     * and/or multiple consumers.
     * 
     * <p>
     * Each {@code enum} constant represents a specific access mode for a queue. A queue should have
     * only one access mode to define its concurrency properties, which is crucial for the
     * validation logic in {@link QueueUtils}.
     * </p>
     * 
     * <p>
     * While this used to be represented by marker interfaces with a sealed hierarchy, using
     * conflicting {@code default} methods to "ensure" mutual exclusivity, this approach proved to
     * be less intuitive and more error-prone. By using an {@code enum}, we can clearly define the
     * access modes in a single, cohesive unit, eliminating the possibility of conflicting
     * properties and simplifying validation logic.
     * </p>
     * 
     * @see QueueMetadataProvider#accessMode()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} property retrieval for all access mode methods.
     * @threading All methods in this {@code enum} are thread-safe by nature of immutability.
     * @memory All methods in this {@code enum} do not allocate.
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
     * <p>
     * A queue can be either {@link #BOUNDED}, meaning it has a fixed capacity and will block or
     * reject new elements when full, or {@link #UNBOUNDED}, meaning it can grow dynamically to
     * accommodate any number of elements. This property is crucial for understanding the behavior
     * of the queue under load and for validating queue implementations in {@link QueueUtils}.
     * </p>
     * 
     * @see QueueMetadataProvider#boundedness()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} property retrieval for all boundedness methods.
     * @threading All methods in this {@code enum} are thread-safe by nature of immutability.
     * @memory All methods in this {@code enum} do not allocate.
     */
    public enum Boundedness {
        /**
         * Bounded: The queue has a fixed capacity determined at construction time.
         * 
         * @see #isBounded()
         * @see QueueMetadata#capacity()
         */
        BOUNDED,

        /**
         * Unbounded: The queue can grow dynamically to accommodate any number of elements, limited
         * only by system resources.
         * 
         * @see #isBounded()
         */
        UNBOUNDED;

        /**
         * Indicates whether the queue is bounded.
         * 
         * @return {@code true} if the queue is bounded, {@code false} if it is unbounded.
         * @since 2026.05 - Queue Metadata Interface
         * @performance {@code O(1)} boundedness check.
         * @threading Thread-safe by nature of being an immutable {@code enum}.
         * @memory Does not allocate.
         */
        public final boolean isBounded() { return this == BOUNDED; }
    }
}

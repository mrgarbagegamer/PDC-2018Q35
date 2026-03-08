package com.github.mrgarbagegamer.queues;

/**
 * Marker interfaces for queue properties. These interfaces provide metadata about the queue's
 * {@link AccessMode} and {@link Boundedness}, simplifying the validation logic of queues in
 * {@link QueueUtils}.
 * 
 * @see BlockingQueueWrappers
 * @see JCToolsWrappers
 * @since 2026.02 - Queue Injection Refactor
 */
public final class QueueMarkers {

    /**
     * Private constructor to prevent instantiation. This class is a utility class that only
     * contains {@code static} members and should not be instantiated.
     * 
     * @throws UnsupportedOperationException always
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} instantiation prevention.
     * @threading Thread-safe by nature of being uninstantiable.
     * @memory Allocates a new exception.
     */
    private QueueMarkers() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    /**
     * A class containing marker interfaces for queue access modes.
     * 
     * <p>
     * Each interface represents a specific access mode for a queue, indicating whether it supports
     * multiple producers, multiple consumers, or a combination thereof. A queue should implement
     * exactly one of these interfaces to indicate its access mode, which can then be used for
     * validation and optimization purposes in queue utilities.
     * </p>
     * 
     * @see QueueUtils.BlockingQueueUtils#ensureMultiConsumerSupport(java.util.List, String)
     * @see QueueUtils.BlockingQueueUtils#ensureMultiProducerSupport(java.util.List, String)
     * @see QueueUtils.JCToolsUtils#ensureMultiConsumerSupport(java.util.List, String)
     * @see QueueUtils.JCToolsUtils#ensureMultiProducerSupport(java.util.List, String)
     * @since 2026.02 - Queue Injection Refactor
     */
    public static final class AccessMode {

        /**
         * Private constructor to prevent instantiation. This class is a utility class that only
         * contains {@code static} members and should not be instantiated.
         * 
         * @throws UnsupportedOperationException always
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} instantiation prevention.
         * @threading Thread-safe by nature of being uninstantiable.
         * @memory Allocates a new exception.
         */
        private AccessMode() {
            throw new UnsupportedOperationException("This class cannot be instantiated.");
        }

        /**
         * Multi-producer, multi-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public interface MPMC {}

        /**
         * Multi-producer, single-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public interface MPSC {}

        /**
         * Single-producer, multi-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public interface SPMC {}

        /**
         * Single-producer, single-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public interface SPSC {}
    }

    /**
     * A class containing marker interfaces for queue boundedness.
     * 
     * <p>
     * These interfaces define a queue's capacity characteristics, indicating whether it has a fixed
     * capacity ({@link Bounded}) or can grow as needed ({@link Unbounded}). A queue should
     * implement exactly one of these interfaces to indicate its boundedness, which can then be used
     * for validation and optimization purposes in queue utilities.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     */
    public static final class Boundedness {

        /**
         * Private constructor to prevent instantiation. This class is a utility class that only
         * contains {@code static} members and should not be instantiated.
         * 
         * @throws UnsupportedOperationException always
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} instantiation prevention.
         * @threading Thread-safe by nature of being uninstantiable.
         * @memory Allocates a new exception.
         */
        private Boundedness() {
            throw new UnsupportedOperationException("This class cannot be instantiated.");
        }

        /**
         * Queue has a fixed capacity defined by the {@link Bounded#capacity() capacity()} method.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public interface Bounded {
            /**
             * Returns the fixed capacity of the queue. This method should be implemented by any
             * queue that implements the {@link Bounded} interface to provide its capacity
             * information for validation and optimization purposes in queue utilities.
             * 
             * @return the fixed capacity of the queue
             * @since 2026.02 - Queue Injection Refactor
             * @performance (Ideally) {@code O(1)} capacity retrieval.
             * @threading Must be thread-safe.
             * @memory Should not allocate.
             */
            int capacity();
        }

        /**
         * Queue does not have a fixed capacity. This could mean that the queue can grow
         * indefinitely or that its capacity is determined by external factors.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public interface Unbounded {}
    }
}

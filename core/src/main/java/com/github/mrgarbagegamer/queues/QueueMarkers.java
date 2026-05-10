package com.github.mrgarbagegamer.queues;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

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
    @ExcludeFromGeneratedCoverage
    private QueueMarkers() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    /**
     * A sealed interface for queue access modes.
     * 
     * <p>
     * Each interface represents a specific access mode for a queue, indicating whether it supports
     * multiple producers, multiple consumers, or a combination thereof. A queue should implement
     * exactly one of these interfaces to indicate its access mode, which can then be used for
     * validation and optimization purposes in queue utilities.
     * </p>
     * 
     * @since 2026.02 - Queue Injection Refactor
     */
    public sealed interface AccessMode
            permits AccessMode.MPMC, AccessMode.MPSC, AccessMode.SPMC, AccessMode.SPSC {

        /**
         * Multi-producer, multi-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public non-sealed interface MPMC extends AccessMode {
            @Override
            default boolean isMultiProducer() {
                return true;
            }

            @Override
            default boolean isMultiConsumer() {
                return true;
            }
        }

        /**
         * Multi-producer, single-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public non-sealed interface MPSC extends AccessMode {
            @Override
            default boolean isMultiProducer() {
                return true;
            }

            @Override
            default boolean isMultiConsumer() {
                return false;
            }
        }

        /**
         * Single-producer, multi-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public non-sealed interface SPMC extends AccessMode {
            @Override
            default boolean isMultiProducer() {
                return false;
            }

            @Override
            default boolean isMultiConsumer() {
                return true;
            }
        }

        /**
         * Single-producer, single-consumer.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public non-sealed interface SPSC extends AccessMode {
            @Override
            default boolean isMultiProducer() {
                return false;
            }

            @Override
            default boolean isMultiConsumer() {
                return false;
            }
        }

        /**
         * Indicates whether the queue supports multiple producers. This method should not be
         * implemented directly by queues; instead, it ensures that the compiler will error if a
         * queue implements multiple access mode interfaces that conflict in their producer support.
         * 
         * @return {@code true} if the queue supports multiple producers, {@code false} otherwise
         * @see #isMultiConsumer()
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} access mode check.
         * @threading Must be thread-safe.
         * @memory Must not allocate.
         */
        public boolean isMultiProducer();

        /**
         * Indicates whether the queue supports multiple consumers. This method should not be
         * implemented directly by queues; instead, it ensures that the compiler will error if a
         * queue implements multiple access mode interfaces that conflict in their consumer support.
         * 
         * @return {@code true} if the queue supports multiple consumers, {@code false} otherwise
         * @see #isMultiProducer()
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} access mode check.
         * @threading Must be thread-safe.
         * @memory Must not allocate.
         */
        public boolean isMultiConsumer();

        /**
         * Indicates whether the queue supports only a single producer. This is a convenience method
         * equivalent to {@code !isMultiProducer()}.
         * 
         * @return {@code true} if the queue supports only a single producer, {@code false}
         *         otherwise
         * @see #isMultiProducer()
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} access mode check.
         * @threading Must be thread-safe.
         * @memory Must not allocate.
         */
        public default boolean isSingleProducer() {
            return !isMultiProducer();
        }

        /**
         * Indicates whether the queue supports only a single consumer. This is a convenience method
         * equivalent to {@code !isMultiConsumer()}.
         * 
         * @return {@code true} if the queue supports only a single consumer, {@code false}
         *         otherwise
         * @see #isMultiConsumer()
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} access mode check.
         * @threading Must be thread-safe.
         * @memory Must not allocate.
         */
        public default boolean isSingleConsumer() {
            return !isMultiConsumer();
        }
    }

    /**
     * A sealed interface for queue boundedness.
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
    public sealed interface Boundedness permits Boundedness.Bounded, Boundedness.Unbounded {

        /**
         * Queue has a fixed capacity defined by the {@link #capacity()} method.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public non-sealed interface Bounded extends Boundedness {
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

            @Override
            default boolean isBounded() {
                return true;
            }
        }

        /**
         * Queue does not have a fixed capacity. This could mean that the queue can grow
         * indefinitely or that its capacity is determined by external factors.
         * 
         * @since 2026.02 - Queue Injection Refactor
         */
        public non-sealed interface Unbounded extends Boundedness {
            @Override
            default boolean isBounded() {
                return false;
            }
        }

        /**
         * Indicates whether the queue has a fixed capacity. This method should not be implemented
         * directly by queues; instead, it ensures that the compiler will error if a queue
         * implements multiple boundedness interfaces that conflict in their boundedness support.
         * 
         * @return {@code true} if the queue has a fixed capacity, {@code false} otherwise
         * @see Bounded#capacity()
         * @since 2026.02 - Queue Injection Refactor
         * @performance {@code O(1)} boundedness check.
         * @threading Must be thread-safe.
         * @memory Must not allocate.
         */
        public boolean isBounded();
    }
}

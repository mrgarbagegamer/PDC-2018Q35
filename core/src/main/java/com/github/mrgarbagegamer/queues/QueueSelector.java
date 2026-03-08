package com.github.mrgarbagegamer.queues;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.WorkBatch;

/**
 * An interface for selecting among a list of queues.
 * 
 * <p>
 * Implementations of this interface define the strategy for {@link #poll polling} and {@link #offer
 * offering} {@link WorkBatch} instances across multiple queues. The selector is responsible for
 * handling {@link BackoffStrategy backoff strategies} and graceful termination based on a provided
 * {@link BooleanSupplier}. A {@link com.github.mrgarbagegamer.QueueStrategy QueueStrategy} should
 * use {@code QueueSelector}s as fields to manage the actual queue interactions, allowing for
 * flexible and interchangeable selection strategies without coupling them to the higher-level queue
 * management logic. All implementations must handle thread interruptions themselves rather than
 * relying on the caller (e.g., by returning {@code null} from {@code poll} or {@code false} from
 * {@code offer} when interrupted).
 * </p>
 * 
 * @param <Q> the type of queues this selector operates on
 * @see ContinuationPredicates
 * @see QueueSelectors
 * @threading Must be thread-safe; implementations may be shared across threads.
 * @memory Must not allocate in the hot path of polling or offering.
 */
public interface QueueSelector<Q> {

    /**
     * Polls for a {@link WorkBatch} from the given queues.
     * 
     * <p>
     * The selector will loop, backing off between attempts, until either a batch is successfully
     * polled, the thread {@link Thread#isInterrupted() is interrupted}, or {@code shouldContinue}
     * returns {@code false}.
     * </p>
     * 
     * @param threadId       the calling thread's logical ID
     * @param queues         the queues to poll from
     * @param backoff        the backoff strategy between failed poll attempts
     * @param shouldContinue a pre-backoff check; if it returns {@code false}, the selector stops
     *                       trying and returns {@code null}
     * @return a polled {@link WorkBatch}, or {@code null} if the thread was interrupted or
     *         {@code shouldContinue} returned {@code false} before a batch could be polled.
     * @since 2026.02 - Queue Injection Refactor
     * @threading Must be thread-safe.
     * @memory Must not allocate.
     */
    WorkBatch poll(int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    /**
     * Offers a {@link WorkBatch} to the given queues.
     *
     * <p>
     * The selector will loop, backing off between attempts, until either the batch is accepted,
     * the thread {@link Thread#isInterrupted() is interrupted}, or {@code shouldContinue} returns
     * {@code false}.
     * </p>
     *
     * @param batch          the batch to offer
     * @param threadId       the calling thread's logical ID
     * @param queues         the queues to offer to
     * @param backoff        the backoff strategy between failed offer attempts
     * @param shouldContinue a pre-backoff check; if it returns {@code false}, the selector stops
     *                       trying and returns {@code false}
     * @return {@code true} if the batch was successfully offered, {@code false} if
     *         {@code shouldContinue} returned {@code false} before the batch could be placed.
     * @since 2026.02 - Queue Injection Refactor
     * @threading Must be thread-safe.
     * @memory Must not allocate.
     */
    boolean offer(WorkBatch batch, int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);
}

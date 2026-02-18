package com.github.mrgarbagegamer.queues;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.mrgarbagegamer.WorkBatch;

public interface QueueSelector<Q> {

    /**
     * Polls for a {@link WorkBatch} from the given queues.
     *
     * <p>
     * The selector will loop, backing off between attempts, until either a batch is found, the
     * thread is interrupted, or {@code shouldContinue} returns {@code false}. In the latter case,
     * the method returns {@code null} to signal graceful termination rather than throwing.
     *
     * @param threadId       the calling thread's logical ID
     * @param queues         the queues to poll from
     * @param backoff        the backoff strategy between failed poll attempts
     * @param shouldContinue a pre-backoff check; if it returns {@code false}, the selector stops
     *                       polling and returns {@code null}
     * @return a {@link WorkBatch}, or {@code null} if {@code shouldContinue} returned {@code false}
     * @throws InterruptedException if the thread is interrupted while polling
     */
    WorkBatch poll(int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);

    /**
     * Offers a {@link WorkBatch} to the given queues.
     *
     * <p>
     * The selector will loop, backing off between attempts, until either the batch is accepted, the
     * thread is interrupted, or {@code shouldContinue} returns {@code false}.
     *
     * @param batch          the batch to offer
     * @param threadId       the calling thread's logical ID
     * @param queues         the queues to offer to
     * @param backoff        the backoff strategy between failed offer attempts
     * @param shouldContinue a pre-backoff check; if it returns {@code false}, the selector stops
     *                       trying and returns {@code false}
     * @return {@code true} if the batch was successfully offered, {@code false} if
     *         {@code shouldContinue} returned {@code false} before the batch could be placed
     * @throws InterruptedException if the thread is interrupted while offering
     */
    boolean offer(WorkBatch batch, int threadId, List<? extends Q> queues, BackoffStrategy backoff,
            BooleanSupplier shouldContinue);
}

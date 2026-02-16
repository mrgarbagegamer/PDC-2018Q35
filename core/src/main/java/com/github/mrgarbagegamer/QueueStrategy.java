package com.github.mrgarbagegamer;

public interface QueueStrategy {

    /**
     * Polls for a {@link WorkBatch} for the given generator.
     *
     * @return a {@link WorkBatch}, or {@code null} if the strategy has determined that no more work
     *         will arrive (e.g., graceful shutdown)
     */
    WorkBatch generatorPoll(int generatorId) throws InterruptedException;

    /**
     * Offers a completed {@link WorkBatch} from the given generator.
     *
     * @return {@code true} if the batch was accepted, {@code false} if the strategy signaled that
     *         offering should stop (e.g., solution found)
     */
    boolean generatorOffer(WorkBatch batch, int generatorId) throws InterruptedException;

    /**
     * Polls for a {@link WorkBatch} for the given monkey.
     *
     * @return a {@link WorkBatch}, or {@code null} if the strategy has determined that no more work
     *         will arrive (e.g., generation complete and all queues drained)
     */
    WorkBatch monkeyPoll(int monkeyId) throws InterruptedException;

    /**
     * Offers a recycled {@link WorkBatch} from the given monkey.
     *
     * @return {@code true} if the batch was accepted, {@code false} if the strategy signaled that
     *         offering should stop
     */
    boolean monkeyOffer(WorkBatch batch, int monkeyId) throws InterruptedException;
}

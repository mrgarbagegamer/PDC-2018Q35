package com.github.mrgarbagegamer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A strategy interface for managing the interaction between {@link CombinationGeneratorTask
 * generators} and {@link TestClickCombination monkeys}.
 * 
 * <p>
 * This interface defines the contract for how generators and monkeys exchange {@link WorkBatch work
 * batches}. It decouples the core solver logic from specific queueing topologies and libraries,
 * enabling different work distribution, load balancing, and termination schemes to be plugged in.
 * 
 * @apiNote Strategy methods accept a thread ID ({@code generatorId} or {@code monkeyId}) to support
 *          routing schemes with thread-local or dedicated queues (such as those leveraging
 *          single-writer or single-reader optimizations). Strategies using a single shared queue or
 *          otherwise ignoring thread identity may safely discard this parameter.
 * 
 * @implSpec Implementations of this interface must be thread-safe, supporting concurrent invocation
 *           by generators and monkeys without external synchronization.
 *           <p>
 *           To ensure consistent termination behavior across all worker threads, once a poll method
 *           returns {@code null} or an offer method returns {@code false} (indicating that work
 *           processing has stopped, e.g., due to a solution being found or generation completing),
 *           subsequent invocations of that same method must continue to return {@code null} or
 *           {@code false} respectively.
 * 
 * @see SolverConfiguration.QueueStrategyFactory
 * @see com.github.mrgarbagegamer.queues
 * @since 2026.02 - Queue Injection Refactor
 */
@NullMarked
public interface QueueStrategy {

    /**
     * Polls for an empty {@link WorkBatch} for the given {@link CombinationGeneratorTask
     * generator}.
     * 
     * @param generatorId the zero-indexed ID of the generator thread
     * @return a {@code WorkBatch}, or {@code null} if the strategy has determined that no more work
     *         will arrive
     * @see GeneratorContext#getCurrentBatch()
     * @since 2026.02 - Queue Injection Refactor
     */
    @Nullable
    WorkBatch generatorPoll(int generatorId);

    /**
     * Offers a (probably) full {@link WorkBatch} from the given {@link CombinationGeneratorTask
     * generator}.
     *
     * @apiNote Although this method generally operates on full batches, it may be called with a
     *          non-full batch during the generator shutdown's final flush phase.
     *
     * @param batch       the batch to offer
     * @param generatorId the zero-indexed ID of the generator thread
     * @return {@code true} if the batch was accepted, {@code false} if the strategy signaled that
     *         offering should stop
     * @see GeneratorContext#flushCurrentBatch()
     * @since 2026.02 - Queue Injection Refactor
     */
    boolean generatorOffer(WorkBatch batch, int generatorId);

    /**
     * Polls for a (probably) full {@link WorkBatch} for the given {@link TestClickCombination
     * monkey}.
     * 
     * @apiNote The batch returned by this method may be non-full during the generator shutdown's
     *          final flush phase.
     * 
     * @param monkeyId the zero-indexed ID of the monkey thread
     * @return a {@code WorkBatch}, or {@code null} if the strategy has determined that no more work
     *         will arrive
     * @since 2026.02 - Queue Injection Refactor
     */
    @Nullable
    WorkBatch monkeyPoll(int monkeyId);

    /**
     * Offers an empty {@link WorkBatch} from the given {@link TestClickCombination monkey}.
     * 
     * @param batch    the batch to offer
     * @param monkeyId the zero-indexed ID of the monkey thread
     * @return {@code true} if the batch was accepted, {@code false} if the strategy signaled that
     *         offering should stop
     * @since 2026.02 - Queue Injection Refactor
     */
    boolean monkeyOffer(WorkBatch batch, int monkeyId);
}

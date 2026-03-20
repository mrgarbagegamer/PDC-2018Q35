package com.github.mrgarbagegamer;

/**
 * A strategy interface for managing the interaction between {@link CombinationGeneratorTask
 * generators} and {@link TestClickCombination monkeys}.
 * 
 * <p>
 * This interface defines the contract for how generators and monkeys will exchange {@link WorkBatch
 * work batches}, allowing for different queueing strategies to be implemented and tested without
 * the need to modify the core logic of the generators and monkeys. Implementations of this
 * interface can define various strategies for load balancing, work distribution, and termination
 * signaling.
 * </p>
 * 
 * <h2>Architectural Role</h2>
 * <p>
 * This codebase's previous design tightly coupled the generators and monkeys to a specific queue
 * implementation, making it very difficult to experiment with different queueing strategies or to
 * optimize the work distribution. Testing or benchmarking the system with different strategies
 * required heavy mocking of components or core logic modifications, neither of which was ideal. By
 * introducing the {@code QueueStrategy} interface, we have an abstraction layer that allows for
 * flexible and modular management of solver communication, aiding the dependency injection refactor
 * of the codebase and enabling easier testing and optimization.
 * </p>
 * 
 * <p>
 * We place the strategy in this class to keep it close to the core logic of the generators and
 * monkeys, leaving the default implementations of the strategy in the
 * {@link com.github.mrgarbagegamer.queues} subpackage. This allows for a clear separation of
 * concerns while keeping the strategy interface easily accessible to the components that need it.
 * </p>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * All implementations of this interface must be thread-safe, as they will be accessed concurrently
 * by generators and monkeys. We leave single-sided thread-safety to the implementer (since the
 * minimum thread count is two, allowing single-writer optimizations to be implemented if desired),
 * but the strategy must be designed to allow for concurrent access to at least two threads without
 * external synchronization.
 * </p>
 * 
 * @see SolverConfiguration.QueueStrategyFactory
 * @see com.github.mrgarbagegamer.queues.BlockingQueueStrategy
 * @see com.github.mrgarbagegamer.queues.JCToolsQueueStrategy
 * @see com.github.mrgarbagegamer.queues.QueueSelector
 * @since 2026.02 - Queue Injection Refactor
 * @threading Thread-safe.
 */
public interface QueueStrategy {

    /**
     * Polls for an empty {@link WorkBatch} for the given {@link CombinationGeneratorTask
     * generator}.
     * 
     * @param generatorId the ID of the generator thread ({@code 0} to {@code numGenerators - 1})
     * @return a {@code WorkBatch}, or {@code null} if the strategy has determined that no more work
     *         will arrive (e.g., {@link SolverState#solutionFound() solution found})
     * @see GeneratorContext#getCurrentBatch()
     * @since 2026.02 - Queue Injection Refactor
     * @threading Thread-safe.
     */
    WorkBatch generatorPoll(int generatorId);

    /**
     * Offers a (probably) full {@link WorkBatch} from the given {@link CombinationGeneratorTask
     * generator}. Note that this method can be called with a non-full batch during the
     * {@link ContextRegistry#flushAllPendingBatches() final flush} phase.
     *
     * @param batch       the batch to offer
     * @param generatorId the ID of the generator thread ({@code 0} to {@code numGenerators - 1})
     * @return {@code true} if the batch was accepted, {@code false} if the strategy signaled that
     *         offering should stop (e.g., {@link SolverState#solutionFound() solution found})
     * @see GeneratorContext#flushCurrentBatch()
     * @since 2026.02 - Queue Injection Refactor
     * @threading Thread-safe.
     */
    boolean generatorOffer(WorkBatch batch, int generatorId);

    /**
     * Polls for a (probably) full {@link WorkBatch} for the given {@link TestClickCombination
     * monkey}. Note that the batch returned by this method may be non-full during the
     * {@link ContextRegistry#flushAllPendingBatches() final flush} phase.
     * 
     * @param monkeyId the ID of the monkey thread ({@code 0} to {@code numMonkeys - 1})
     * @return a {@code WorkBatch}, or {@code null} if the strategy has determined that no more work
     *         will arrive (e.g., {@link SolverState#generationComplete() generation complete} or
     *         {@link SolverState#solutionFound() solution found})
     * @since 2026.02 - Queue Injection Refactor
     * @threading Thread-safe.
     */
    WorkBatch monkeyPoll(int monkeyId);

    /**
     * Offers an empty {@link WorkBatch} from the given {@link TestClickCombination monkey}.
     * 
     * @param batch    the batch to offer
     * @param monkeyId the ID of the monkey thread ({@code 0} to {@code numMonkeys - 1})
     * @return {@code true} if the batch was accepted, {@code false} if the strategy signaled that
     *         offering should stop (e.g., {@link SolverState#generationComplete() generation complete} or
     *         {@link SolverState#solutionFound() solution found})
     * @since 2026.02 - Queue Injection Refactor
     * @threading Thread-safe.
     */
    boolean monkeyOffer(WorkBatch batch, int monkeyId);
}

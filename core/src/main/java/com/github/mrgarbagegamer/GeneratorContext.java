package com.github.mrgarbagegamer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

// Add Javadocs
@NullMarked
public interface GeneratorContext {

    /**
     * Gets the name of the thread owning this context. Used for logging purposes.
     * 
     * @return The name of the this context's thread.
     * @see ContextRegistry#flushAllPendingBatches()
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe, as it returns an immutable field.
     * @memory Does not allocate; returns a reference to an existing {@code String}.
     */
    String getName();

    boolean hasBatch();

    /**
     * Gets the current {@link WorkBatch} for this context or
     * {@link QueueStrategy#generatorPoll(int) polls for a new batch} if there is no current batch.
     * This method should only return {@code null} if a {@link SolverState#solutionFound() solution
     * has been found}, signalling that the generator should stop processing and exit.
     * 
     * @return the current {@link WorkBatch} for this context or {@code null} if a solution has been
     *         found
     * @see #hasBatch()
     * @since 2026.01 - Generator DI Refactor
     * @threading Must be thread-safe.
     * @memory Should not allocate.
     */
    @Nullable
    WorkBatch getCurrentBatch();

    default int getCurrentBatchSize() {
        if (this.hasBatch()) {
            WorkBatch currentBatch = this.getCurrentBatch();
            // Check if the returned batch is null (should be impossible)
            return currentBatch != null ? currentBatch.size() : 0;
        }
        return 0;
    }

    ArrayPool getArrayPool();

    TaskPool getTaskPool();

    QueueStrategy getQueueStrategy();

    /**
     * Flushes the {@link #getCurrentBatch() current batch} for this context, sending it to the
     * {@link QueueStrategy#generatorOffer(WorkBatch, int) queue(s)} for processing by the
     * {@link TestClickCombination monkeys}. This method should be called after the current batch
     * has been filled with work and before calling getCurrentBatch() again.
     * 
     * <p>
     * Though general use of this method operates on a full batch, it is also called during the
     * {@link ContextRegistry#flushAllPendingBatches() final flush phase} of the generator shutdown,
     * where the batch may only be partially filled.
     * </p>
     * 
     * @return {@code true} if the batch was successfully flushed and sent to the queue(s), or
     *         {@code false} if the batch could not be flushed (e.g. if a
     *         {@link SolverState#solutionFound() solution has been found} and the generator should
     *         stop processing)
     * @since 2026.01 - Generator DI Refactor
     * @threading Must be thread-safe.
     */
    boolean flushCurrentBatch();

    SolverConfiguration getConfiguration();

    static GeneratorContext ofDefault(String name, int generatorId, QueueStrategy queueStrategy,
            ContextRegistry registry, SolverConfiguration config) {
        return DefaultGeneratorContext.of(name, generatorId, queueStrategy, registry, config);
    }
}

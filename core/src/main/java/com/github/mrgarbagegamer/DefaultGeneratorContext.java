package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

// TODO: Update Javadoc
/**
 * A container for all {@link ThreadLocal thread-local} resources used by a generator.
 *
 * <p>
 * This class is a key part of the resource management strategy. It consolidates all expensive,
 * thread-specific objects into a single container. An instance of this context is stored in a
 * {@link GeneratorWorkerThread#context field}, ensuring that each thread in the
 * {@link ForkJoinPool} has its own set of resource pools and a dedicated {@link WorkBatch}.
 * </p>
 *
 * <h2>Optimization Strategy</h2>
 * <p>
 * By fetching this context object only once per task via {@code context.get()}, we avoid the
 * performance penalty of multiple {@code ThreadLocal} lookups in the hot path. The context is then
 * passed as a parameter to downstream methods, providing fast, contention-free access to:
 * <ul>
 * <li>A {@link #taskPool} for recycling {@code CombinationGeneratorTask} objects.</li>
 * <li>The {@link #currentBatch} being filled by the thread.</li>
 * </ul>
 * This pattern is crucial for achieving near-zero allocation during combination generation.
 * </p>
 *
 * @since 2025.07 - {@code GeneratorContext} Introduction
 * @performance {@code O(1)} access time after initial allocation.
 * @threading This class is NOT thread-safe, but is intended to be used in a thread-local manner.
 * @memory Minimal memory footprint of two fixed-capacity pools and a batch reference.
 */
class DefaultGeneratorContext implements GeneratorContext {
    private final Logger logger;

    /**
     * The name of the thread owning this context.
     *
     * @see #getName()
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint as a {@code String} reference.
     */
    private final String name;
    private final int generatorId;
    private volatile boolean terminated = false;

    @Override
    public String getName() { return this.name; }

    private final SolverConfiguration config;
    private final SolverServices services;

    public DefaultGeneratorContext(String name, int generatorId, QueueStrategy queueStrategy,
            ContextRegistry registry, SolverConfiguration config, SolverServices services) {
        this.config = mustNotBeNull(config, "config");
        this.services = mustNotBeNull(services, "services");

        this.logger = this.services.getLogger(DefaultGeneratorContext.class);
        this.name = mustNotBeNull(name, "name");
        this.generatorId = generatorId;

        this.taskPool = new TaskPool(this.config);
        this.queueStrategy = mustNotBeNull(queueStrategy, "queueStrategy");
        mustNotBeNull(registry, "registry").registerContext(this);
    }

    public DefaultGeneratorContext(String name, int generatorId, QueueStrategy queueStrategy,
            ContextRegistry registry, SolverConfiguration config) {
        this(name, generatorId, queueStrategy, registry, config, SolverServices.defaultServices());
    }

    public static DefaultGeneratorContext of(String name, int generatorId,
            QueueStrategy queueStrategy, ContextRegistry registry, SolverConfiguration config,
            SolverServices services) {
        return new DefaultGeneratorContext(name, generatorId, queueStrategy, registry, config,
                services);
    }

    /**
     * A {@link ThreadLocal thread-local} {@link TaskPool pool} for recycling
     * {@link CombinationGeneratorTask} instances.
     * 
     * @since 2025.07 - {@code GeneratorContext} Introduction
     * @performance {@code O(1)} amortized access time for {@link TaskPool#get()} and
     *              {@link TaskPool#put(CombinationGeneratorTask)}.
     * @threading Not thread-safe, should be used in a thread-local manner.
     * @memory Fixed footprint of ~4 bytes as a reference.
     */
    private final TaskPool taskPool;
    /**
     * The {@link WorkBatch} currently being filled by this thread.
     * 
     * <p>
     * Each generator thread works on its own batch, which is preserved across multiple tasks
     * executed by that thread. When the batch is full, it is flushed to a queue, and a new, clean
     * batch is obtained from a central pool. Storing the batch here, within the {@link ThreadLocal
     * thread-local} context, eliminates contention and simplifies batch management.
     * </p>
     * 
     * @see WorkBatch#BATCH_SIZE
     * @see #getCurrentBatch()
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} access and update time.
     * @threading Not thread-safe. References to this batch should not be kept after flushing.
     * @memory Fixed footprint of ~4 bytes as a reference.
     */
    private @Nullable WorkBatch currentBatch = null;

    private final QueueStrategy queueStrategy;

    @Override
    public boolean hasBatch() { return this.currentBatch != null; }

    @Override
    public @Nullable WorkBatch getCurrentBatch() {
        if (this.currentBatch == null) {
            this.currentBatch = pollBatch();
        }
        return this.currentBatch;
    }

    /**
     * {@link QueueStrategy#generatorPoll(int) Polls} for a new batch from the
     * {@link QueueStrategy}. If the poll returns {@code null}, the method
     * {@link #handleTermination(boolean) handles termination logging and state updates}, and the
     * method returns {@code null} to signal that no more batches will be available. Otherwise, the
     * polled batch is {@link WorkBatch#clear() cleared} and returned for use.
     * 
     * @return a new, empty {@link WorkBatch} from the {@code QueueStrategy}, or {@code null} if a
     *         termination condition was met.
     * @since 2026.01 - Generator DI Refactor
     * @performance {@code O(1)} calls to the {@code QueueStrategy}, with {@code O(batch.size())}
     *              clearing.
     * @threading Thread-safe queue interactions and termination handling.
     * @memory Does not allocate.
     */
    private @Nullable WorkBatch pollBatch() {
        final WorkBatch batch = this.queueStrategy.generatorPoll(this.generatorId);
        if (batch == null) {
            // Delegate to the extracted method to handle termination logging.
            handleTermination(true);
            return null; // Necessary to avoid an NPE from the clear() call below.
        }
        batch.clear(); // Ensure the recycled batch is clean before use
        return batch;
    }

    private void handleTermination(boolean onPoll) {
        // Use DCL to avoid unnecessary synchronization after termination has been signaled
        if (!this.terminated) {
            synchronized (this) {
                if (!this.terminated) {
                    this.logger.debug(
                            onPoll ? "Termination condition was met during poll, shutting down"
                                    : "Termination condition was met during offer, shutting down");
                    this.terminated = true;
                }
            }
        }
    }

    @Override
    public TaskPool getTaskPool() { return this.taskPool; }

    @Override
    public SolverConfiguration getConfiguration() { return this.config; }

    @Override
    public SolverServices getServices() { return this.services; }

    @Override
    public QueueStrategy getQueueStrategy() { return this.queueStrategy; }

    @Override
    public boolean flushCurrentBatch() {
        checkState(this.currentBatch != null,
                "A new batch must be acquired before calling this method");

        final boolean success = this.queueStrategy.generatorOffer(this.currentBatch,
                this.generatorId);
        this.currentBatch = null;

        if (!success) {
            handleTermination(false);
        }
        return success;
    }
}

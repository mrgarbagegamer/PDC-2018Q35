package com.github.mrgarbagegamer;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.Logger;

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
 * <li>An {@link #prefixArrayPool} for recycling {@code short[]} arrays.</li>
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
    public String getName() {
        return this.name;
    }

    private final SolverConfiguration config;

    /**
     * Initializes a new {@link DefaultGeneratorContext} and registers it in the
     * {@link #ALL_CONTEXTS global context list}.
     * 
     * <p>
     * This constructor is meant to be called only by the {@link GeneratorWorkerThread}'s
     * {@link GeneratorWorkerThread#GeneratorWorkerThread(ForkJoinPool) initializer}, and
     * {@link ConcurrentLinkedQueue#add(Object) adds} the context to the global list for the
     * {@link ContextRegistry#flushAllPendingBatches() final flush}.
     * </p>
     * 
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} amortized insertion time into the global context list.
     * @threading Thread-safe by nature of construction and use of a {@link ConcurrentLinkedQueue
     *            thread-safe queue}.
     * @memory Does not allocate, apart from the instance itself.
     */
    public DefaultGeneratorContext(String name, int generatorId, QueueStrategy queueStrategy,
            ContextRegistry registry, SolverConfiguration config) {
        // TODO: Consider importing Guava's Preconditions for null checks
        // Perform the config null check first, as it's needed for logging and we want to fail fast
        // if it's missing
        this.config = requireNonNull(config, "config cannot be null");

        this.logger = config.getLogger(DefaultGeneratorContext.class);
        this.name = requireNonNull(name, "name cannot be null");
        this.generatorId = generatorId;
        this.arrayPool = new ArrayPool(config);

        this.taskPool = new TaskPool(config);
        this.queueStrategy = requireNonNull(queueStrategy, "queueStrategy cannot be null");
        registry.registerContext(this);
    }

    public static DefaultGeneratorContext of(String name, int generatorId,
            QueueStrategy queueStrategy, ContextRegistry registry, SolverConfiguration config) {
        return new DefaultGeneratorContext(name, generatorId, queueStrategy, registry, config);
    }

    /**
     * A {@link ThreadLocal thread-local} {@link ArrayPool pool} for recycling {@code short[]}
     * arrays used for {@code prefix}es.
     * 
     * @see ArrayPool
     * @since 2025.07 - {@code GeneratorContext} Introduction
     * @performance {@code O(1)} amortized access time for {@link ArrayPool#get()} and
     *              {@link ArrayPool#put(short[])}.
     * @threading Not thread-safe, should be used in a thread-local manner.
     * @memory Fixed footprint of ~4 bytes as a reference.
     */
    private final ArrayPool arrayPool;
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
    private WorkBatch currentBatch = null;

    private final QueueStrategy queueStrategy;

    @Override
    public boolean hasBatch() {
        return this.currentBatch != null;
    }

    @Override
    public WorkBatch getCurrentBatch() {
        return this.currentBatch == null ? this.currentBatch = pollBatch() : this.currentBatch;
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
    private WorkBatch pollBatch() {
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
    public WorkBatch resetBatch() {
        return this.currentBatch = pollBatch();
    }

    @Override
    public ArrayPool getArrayPool() {
        return this.arrayPool;
    }

    @Override
    public TaskPool getTaskPool() {
        return this.taskPool;
    }

    @Override
    public SolverConfiguration getConfiguration() {
        return this.config;
    }

    @Override
    public QueueStrategy getQueueStrategy() {
        return this.queueStrategy;
    }

    @Override
    public boolean flushCurrentBatch() {
        final boolean success = this.queueStrategy.generatorOffer(this.currentBatch,
                this.generatorId);
        this.currentBatch = null;

        if (!success) {
            handleTermination(false);
        }
        return success;
    }
}

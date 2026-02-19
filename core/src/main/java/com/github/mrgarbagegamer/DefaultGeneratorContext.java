package com.github.mrgarbagegamer;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;

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
public class DefaultGeneratorContext implements GeneratorContext {
    private final Logger logger;

    /**
     * The name of the thread owning this context, for logging purposes.
     *
     * @see #ALL_CONTEXTS
     * @see CombinationGeneratorTask#flushAllPendingBatches()
     * @see Thread#getName()
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of ~20 bytes as a {@code String}.
     */
    private final String name;
    private final int generatorId;
    private volatile boolean terminated = false;

    /**
     * Gets the {@link #threadName name} of the thread owning this context. Used for logging
     * purposes.
     * 
     * @return The name of the this context's thread.
     * @see CombinationGeneratorTask#flushAllPendingBatches()
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe, as it returns an immutable field.
     * @memory Does not allocate; returns a reference to an existing {@code String}.
     */
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
     * {@link CombinationGeneratorTask#flushAllPendingBatches() final flush}.
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

    @Override
    public DefaultGeneratorContext newContext(String name, int generatorId,
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
     * @see #getOrCreateBatch()
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
     * Retrieves a new {@link WorkBatch} from the {@link CombinationQueueArray#getWorkBatchPool()
     * global pool}, blocking until one is available.
     *
     * <p>
     * When a {@link TestClickCombination monkey} finishes with a {@code WorkBatch}, it returns it
     * to a central pool for recycling. This method retrieves a batch from that pool. It uses a
     * lightweight loop with a {@link org.jctools.queues.MpmcArrayQueue#relaxedPoll() relaxedPoll()}
     * to ensure the generator thread remains responsive to cancellation signals from the
     * {@link ForkJoinPool}, which a standard {@link Thread#onSpinWait()} call would prevent.
     * </p>
     *
     * <p>
     * The loop now properly checks for thread interruption, allowing the {@link ForkJoinPool} to
     * shutdown generator threads when a solution is found. If the thread is interrupted during the
     * spin loop, the method returns {@code null} to signal shutdown. The thread also
     * {@link LockSupport#parkNanos(long) parks} briefly to reduce CPU usage during the wait.
     * </p>
     *
     * @return A clean, recycled {@code WorkBatch}, or {@code null} if the thread was interrupted.
     * @see CombinationQueueArray#getWorkBatchPool()
     * @see TestClickCombination#triggerGeneratorShutdown()
     * @since 2025.07 - {@code GeneratorContext} Introduction
     * @performance {@code O(1)} amortized access time, spinning if necessary.
     * @threading Thread-safe interactions with the global pool, but the method itself is not
     *            thread-safe.
     * @memory Does not allocate; reuses existing batches in the pool.
     */
    private WorkBatch pollBatch() {
        final WorkBatch batch = this.queueStrategy.generatorPoll(this.generatorId);
        if (batch == null) {
            if (!this.terminated) {
                this.logger.debug("Termination condition was met during poll, shutting down");
                this.terminated = true;
            }
            return null; // Necessary to avoid an NPE from the clear() call below.
        }
        batch.clear(); // Ensure the recycled batch is clean before use
        return batch;
    }

    /**
     * Replaces the {@link #currentBatch} with a new one obtained from the
     * {@link CombinationQueueArray#getWorkBatchPool() central pool}.
     * 
     * <p>
     * This is called after the current {@link WorkBatch batch} is successfully
     * {@link CombinationGeneratorTask#flushBatchFast(WorkBatch) flushed}. It discards the reference
     * to the old batch and retrieves a new, clean one, ensuring the generator thread can
     * immediately start filling it.
     * </p>
     * 
     * @return The new {@code WorkBatch} or {@code null} if interrupted.
     * @see #pollBatch()
     * @since 2025.07 - {@code GeneratorContext} Introduction
     * @performance {@code O(1)} amortized access time, spinning if necessary.
     * @threading Not thread-safe. Each thread should manage its own context instance.
     * @memory Does not allocate; reuses existing batches in the pool.
     */
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

        if (!success && !this.terminated) {
            this.logger.debug("Termination condition was met during offer, shutting down");
            this.terminated = true;
        }
        return success;
    }
}

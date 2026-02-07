package com.github.mrgarbagegamer;

import org.jctools.queues.MpmcArrayQueue;

/**
 * A singleton structure for managing work distribution and shared resources between
 * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
 *
 * <p>
 * This class serves as the central coordination point for the solver. It has two primary
 * responsibilities:
 * </p>
 * <ol>
 * <li><b>Work Distribution:</b> It holds an array of {@link CombinationQueue} instances, one for
 * each monkey. This partitions the workload and reduces contention.</li>
 * <li><b>Centralized Pooling:</b> It manages a global {@link #workBatchPool} for recycling
 * {@link WorkBatch} objects. This is critical for minimizing garbage collection by allowing
 * consumers to return processed batches to a central location for reuse by generators.</li>
 * </ol>
 *
 * <h2>Architectural Role</h2>
 * <p>
 * As a singleton instance, this class provides the infrastructure for decoupling generators from
 * monkeys. Generators acquire empty {@code WorkBatch} objects from the central pool, fill them with
 * work, and offer them to one of the work queues. Monkeys poll their designated queue, process the
 * entire batch, and return the now-empty {@code WorkBatch} to the central pool.
 * </p>
 *
 * <p>
 * It also manages the application's lifecycle by tracking the state of the pool and signaling
 * termination conditions via the {@link #solutionFound} and {@link #generationComplete} flags.
 * </p>
 *
 * <h2>Memory and Performance</h2>
 * <p>
 * The entire structure is designed to minimize allocations and synchronization in the hot path. The
 * {@link #workBatchPool} is pre-allocated at startup to a size that guarantees a recycled batch is
 * never discarded, eliminating the need for runtime allocations. Communication relies on lock-free
 * queues from JCTools and {@code volatile} flags, ensuring high throughput.
 * </p>
 *
 * @see MpmcArrayQueue
 * @since 2025.05 - Multiple {@code CombinationQueue}s
 * @performance {@code O(1)} for most operations, {@code O(queues.length)} for iterating through all
 *              queues.
 * @threading Thread-safe; uses lock-free structures and {@code volatile} flags for safe concurrent
 *            access and updates.
 * @memory Fixed memory footprint after initialization; no dynamic allocations in the hot path.
 */
public class CombinationQueueArray {
    /**
     * An array of {@link CombinationQueue work queues}, with each queue dedicated to a specific
     * {@link TestClickCombination monkey}.
     *
     * <p>
     * This design partitions the workload, significantly reducing contention compared to a single
     * shared queue. Each monkey primarily polls its own dedicated queue. The array structure also
     * facilitates work-stealing, allowing an idle monkey to poll other queues for work.
     * </p>
     *
     * @see #getQueue(int)
     * @see #getAllQueues()
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} access of an individual queue, {@code O(queues.length)} for
     *              iterating through all queues.
     * @threading Thread-safe; immutable for read operations after construction.
     * @memory Minimal overhead of {@code queues.length * 4} bytes as an array of references.
     */
    private final CombinationQueue[] queues;
    /**
     * A central, thread-safe pool for recycling {@link WorkBatch} objects.
     *
     * <p>
     * This pool is the cornerstone of the application's memory management strategy. It allows
     * {@link WorkBatch} instances to be reused, effectively eliminating them as a source of garbage
     * collection pressure.
     * </p>
     *
     * <p>
     * The lifecycle is as follows:
     * </p>
     * <ol>
     * <li>The pool is pre-filled with empty {@code WorkBatch} instances at startup.</li>
     * <li>A {@link CombinationGeneratorTask generator} polls a batch from this pool.</li>
     * <li>The generator fills the batch with work items.</li>
     * <li>The full batch is offered to a work {@link #queues queue}.</li>
     * <li>A {@link TestClickCombination monkey} polls the batch, processes its contents, and clears
     * it.</li>
     * <li>The monkey returns the empty, cleared batch to this pool.</li>
     * </ol>
     *
     * <p>
     * The pool is sized to hold a number of batches equal to the total capacity of all work queues,
     * guaranteeing that a recycled batch is never discarded.
     * </p>
     *
     * @see #getWorkBatchPool()
     * @see MpmcArrayQueue
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} for both offer and poll operations,
     *              {@code O(workBatchPool.capacity())} for {@link MpmcArrayQueue#size() size()}.
     * @threading Thread-safe through JCTools wizardry
     * @memory Fixed memory footprint after initialization
     */
    private final MpmcArrayQueue<WorkBatch> workBatchPool; // TODO: Consider wrapping this in
                                                           // CombinationQueue for
                                                           // better encapsulation.
    // TODO: Consider moving solver state to the Solver class to decouple queue management from
    // state coordination.
    private final SolverState solverState = new SolverState();

    public CombinationQueueArray(SolverConfiguration config) {
        final int numConsumers = (config.numThreads() + 1) / 2;
        
        if (numConsumers <= 0) {
            throw new IllegalArgumentException("Number of consumers must be positive.");
        }

        this.queues = new CombinationQueue[numConsumers];

        // The total number of batches that can be in-flight is the sum of all queue capacities
        // The pool must be at least this large to guarantee a recycled batch is never discarded
        int totalWorkQueueCapacity = 0;
        for (int i = 0; i < numConsumers; i++) {
            queues[i] = new CombinationQueue(config.queueSize());
            totalWorkQueueCapacity += queues[i].capacity();
        }
        // Set the recycle pool size to match the total work queue capacity
        this.workBatchPool = new MpmcArrayQueue<>(totalWorkQueueCapacity);

        // OPTIMIZATION: Pre-allocate the entire WorkBatch pool to prevent allocation in the hot
        // path.
        for (int i = 0; i < totalWorkQueueCapacity; i++) {
            if (!workBatchPool.offer(new WorkBatch(config))) {
                throw new IllegalStateException(
                        "Failed to pre-allocate the WorkBatch pool. Reconfiguration is required.");
            }
        }
    }

    /**
     * Returns the {@link #workBatchPool central pool} for recycled {@link WorkBatch} objects.
     *
     * <p>
     * Both {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys} use
     * this method to access the pool. Generators poll from it to get empty batches, and monkeys
     * offer to it to return processed batches.
     * </p>
     *
     * @return The thread-safe, multi-producer/multi-consumer queue used for pooling
     *         {@code WorkBatch} instances.
     * @see #workBatchPool
     * @see #CombinationQueueArray(int)
     * @see MpmcArrayQueue
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; returns a reference to an immutable field.
     */
    public MpmcArrayQueue<WorkBatch> getWorkBatchPool() {
        return workBatchPool;
    }

    /**
     * Returns the {@link CombinationQueue work queue} at the specified index.
     *
     * @param idx The index of the queue to retrieve.
     * @return The {@code CombinationQueue} at the specified index in the {@link #queues} array.
     * @see #CombinationQueueArray(int)
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} access.
     * @threading Thread-safe; returns a reference to an immutable field.
     */
    public CombinationQueue getQueue(int idx) {
        return queues[idx];
    }

    /**
     * Returns the {@link #queues entire array} of work queues.
     *
     * <p>
     * This is primarily used to enable work-stealing, where a {@link TestClickCombination monkey}
     * can iterate through other queues to find work if its own queue is empty.
     * </p>
     *
     * @return The array of all {@link CombinationQueue}s.
     * @see #CombinationQueueArray(int)
     * @since 2025.05 - Monkey Work-Stealing Introduction
     * @performance {@code O(1)} access.
     * @threading Thread-safe; returns a reference to an immutable field.
     */
    public CombinationQueue[] getAllQueues() {
        return queues;
    }

    /**
     * Returns the number of {@link CombinationQueue work queues} managed by this instance. This
     * corresponds to the number of {@link TestClickCombination monkeys} in the system.
     * 
     * @return The number of work queues.
     * @see #CombinationQueueArray(int)
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; returns a reference to an immutable field.
     * @memory Does not allocate.
     */
    public int getNumQueues() {
        return queues.length;
    }

    public SolverState getSolverState() {
        return solverState;
    }
}
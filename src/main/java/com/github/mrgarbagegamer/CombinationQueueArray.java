package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.queues.MpmcArrayQueue;

/**
 * A structure for managing work distribution and shared resources between
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
 * It also manages the application's lifecycle by tracking the number of active producers
 * ({@link #generatorsRemaining}) and signaling termination conditions via the
 * {@link #solutionFound} and {@link #generationComplete} flags.
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
     * @performance {@code O(1)} access of an individual queue, {@code O(queues.length)} for iterating
     *              through all queues.
     * @threading Thread-safe; immutable for read operations after construction.
     * @memory Minimal overhead of {@code queues.length * 4} bytes as an array of references.
     */
    private final CombinationQueue[] queues;
    /**
     * A counter for the number of active {@link CombinationGeneratorTask generators}.
     *
     * <p>
     * This {@link AtomicInteger atomic counter} is decremented when a root generator task completes.
     * When the count reaches zero, the {@link #generationComplete} flag is set, signaling to monkeys
     * that no new work will be produced.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * An {@code AtomicInteger} provides lock-free updates, avoiding contention. Currently, only one
     * main generator task decrements this counter. Future optimizations might replace this with a
     * simple flag set by that single task, removing the atomic operation overhead entirely.
     * </p>
     *
     * @see #generatorFinished()
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} for increment and decrement operations.
     * @threading Thread-safe; uses atomic operations for safe concurrent updates.
     * @memory Fixed memory footprint of 16 bytes for the atomic integer.
     */
    private final AtomicInteger generatorsRemaining; // TODO: Remove in favor of the generationComplete flag.
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
     * @performance {@code O(1)} for both offer and poll operations, {@code O(workBatchPool.capacity())}
     *              for {@link MpmcArrayQueue#size() size()}.
     * @threading Thread-safe through JCTools wizardry
     * @memory Fixed memory footprint after initialization
     */
    private final MpmcArrayQueue<WorkBatch> workBatchPool;
    /**
     * The name of the {@link TestClickCombination monkey} that found the solution. Written once when
     * {@link #solutionFound} is set.
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This field is {@code volatile} to ensure visibility across threads. It could piggyback on the
     * {@code volatile} write to {@link #solutionFound} for publication, but is kept as a separate
     * {@code volatile} field for simplicity. The overhead is negligible as it is written only once.
     * </p>
     *
     * @see #solutionFound(String, short[])
     * @see #getWinningMonkey()
     */
    private volatile String winningMonkey = null; // TODO: Consider piggybacking on the volatile write to solutionFound.
    /**
     * The click combination that solves the puzzle. Written once when {@link #solutionFound} is set.
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This field is {@code volatile} to ensure visibility across threads. It could piggyback on the
     * {@code volatile} write to {@link #solutionFound} for publication, but is kept as a separate
     * {@code volatile} field for simplicity. The overhead is negligible as it is written only once.
     * </p>
     *
     * @see #solutionFound(String, short[])
     * @see #getWinningCombination()
     */
    private volatile short[] winningCombination = null; // TODO: Consider piggybacking on the volatile write to solutionFound.

    /**
     * A {@code volatile} flag indicating that a solution has been found.
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * A {@code volatile boolean} is used instead of an {@code AtomicBoolean} because it provides the
     * necessary visibility guarantees for a single-writer/multi-reader scenario with less overhead. It
     * is read frequently in the hot path.
     * </p>
     *
     * <p>
     * While generators now rely on the {@link java.util.concurrent.ForkJoinPool ForkJoinPool}'s
     * cancellation mechanism, the monkeys still poll this flag to terminate work. A future
     * implementation could potentially unify this under a single cancellation mechanism, but this flag
     * remains a simple and effective solution.
     * </p>
     *
     * @see #getWinningCombination()
     * @see #getWinningMonkey()
     * @see #solutionFound(String, short[])
     * @since 2025.07 - Volatile Flag Implementation
     * @performance {@code O(1)} for reads and writes.
     * @threading Thread-safe; uses {@code volatile} for safe concurrent access.
     * @memory Fixed memory footprint of 1 byte as a {@code boolean}.
     */
    public volatile boolean solutionFound = false;
    /**
     * A volatile flag indicating that all {@link CombinationGeneratorTask producers} have completed.
     *
     * <p>
     * This is set to {@code true} when {@link #generatorsRemaining} reaches zero. It signals to
     * {@link TestClickCombination monkeys} that no new work will be added to the queues. A monkey can
     * safely terminate when this flag is {@code true} and all work queues are empty.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * A {@code volatile boolean} is used instead of an {@code AtomicBoolean} because it provides the
     * necessary visibility guarantees for a single-writer/multi-reader scenario with less overhead. It
     * is read frequently by monkeys to determine when to stop work.
     * </p>
     *
     * @see #generatorFinished()
     * @see CombinationGeneratorTask#computeRootSubtasks(CombinationGeneratorTask#GeneratorContext)
     * @see TestClickCombination#allQueuesEmpty()
     * @since 2025.07 - Volatile Flag Implementation
     * @performance {@code O(1)} for reads and writes.
     * @threading Thread-safe; uses {@code volatile} for safe concurrent access.
     * @memory Fixed memory footprint of 1 byte as a {@code boolean}.
     */
    public volatile boolean generationComplete = false;

    /**
     * Constructs the shared {@link #queues queue array} and its associated resources.
     *
     * <p>
     * This constructor initializes the entire communication and resource-sharing infrastructure. It
     * creates the array of work {@link CombinationQueue queues} and pre-allocates {@link #workBatchPool
     * the central} {@link WorkBatch} pool. The pool is sized to match the total
     * {@link CombinationQueue#getCapacity() capacity} of all work queues combined, which is a critical
     * invariant to ensure the recycling mechanism never fails.
     * </p>
     *
     * @param numConsumers  The number of {@link TestClickCombination monkeys}. This determines the
     *                      number of work queues to create.
     * @param numGenerators The (effective) number of {@link CombinationGeneratorTask generators}. This
     *                      initializes the {@link #generatorsRemaining completion counter}.
     * @throws IllegalStateException if the pre-allocation of the {@link #workBatchPool pool} fails,
     *                               indicating a configuration issue.
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(numConsumers)} queue initialization + {@code O(1)} counter setup +
     *              {@code O(numConsumers)} counter initialization +
     *              {@code O(numConsumers * CombinationQueue.QUEUE_SIZE)} pool pre-allocation =
     *              {@code O(numConsumers)} time complexity.
     * @threading Thread-safe due to instance isolation
     * @memory Allocates the array of queues, counters, and flags, and pre-allocates the pool.
     */
    public CombinationQueueArray(int numConsumers, int numGenerators) {
        this.queues = new CombinationQueue[numConsumers];
        this.generatorsRemaining = new AtomicInteger(numGenerators);

        // The total number of batches that can be in-flight is the sum of all queue capacities
        // The pool must be at least this large to guarantee a recycled batch is never discarded
        int totalWorkQueueCapacity = 0;
        for (int i = 0; i < numConsumers; i++)
        {
            queues[i] = new CombinationQueue();
            totalWorkQueueCapacity += queues[i].getCapacity();
        }
        // Set the recycle pool size to match the total work queue capacity
        this.workBatchPool = new MpmcArrayQueue<>(totalWorkQueueCapacity);

        // OPTIMIZATION: Pre-allocate the entire WorkBatch pool to prevent allocation in the hot path.
        // The BATCH_SIZE must match the one defined in CombinationGeneratorTask.
        final int BATCH_SIZE = CombinationGeneratorTask.BATCH_SIZE;
        for (int i = 0; i < totalWorkQueueCapacity; i++)
        {
            if (!workBatchPool.offer(new WorkBatch(BATCH_SIZE))) {
                throw new IllegalStateException("Failed to pre-allocate the WorkBatch pool. Reconfiguration is required.");
            }
        }
    }

    /**
     * Returns the {@link #workBatchPool central pool} for recycled {@link WorkBatch} objects.
     *
     * <p>
     * Both {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys} use
     * this method to access the pool. Generators poll from it to get empty batches, and monkeys offer
     * to it to return processed batches.
     * </p>
     *
     * @return The thread-safe, multi-producer/multi-consumer queue used for pooling {@code WorkBatch}
     *         instances.
     * @see #workBatchPool
     * @see #CombinationQueueArray(int, int)
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
     * @see #CombinationQueueArray(int, int)
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
     * This is primarily used to enable work-stealing, where a {@link TestClickCombination monkey} can
     * iterate through other queues to find work if its own queue is empty.
     * </p>
     *
     * @return The array of all {@link CombinationQueue}s.
     * @see #CombinationQueueArray(int, int)
     * @since 2025.05 - Monkey Work-Stealing Introduction
     * @performance {@code O(1)} access.
     * @threading Thread-safe; returns a reference to an immutable field.
     */
    public CombinationQueue[] getAllQueues() { 
        return queues; 
    }

    /**
     * Atomically decrements the {@link #generatorsRemaining} counter and sets the
     * {@link #generationComplete} flag if the counter reaches zero.
     *
     * <p>
     * This method is called by a {@link CombinationGeneratorTask generator} after it has finished
     * computing all of its subtasks to signal to {@link TestClickCombination monkeys} that no new work
     * will be produced.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is not in the hot path. It uses an {@code AtomicInteger} for lock-free updates. Since
     * only one root generator task calls this, a future optimization could be to remove the atomic
     * operation and have the main thread set the {@code generationComplete} flag directly.
     * </p>
     *
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} atomic decrement and check.
     * @threading Thread-safe; uses atomic operations for safe concurrent updates.
     * @memory Does not allocate.
     */
    public void generatorFinished() {
        // TODO: Remove in favor of the generationComplete flag.
        if (generatorsRemaining.decrementAndGet() == 0) {
            generationComplete = true;
        }
        // TODO: Log the time when generation completes for consistent reporting.
    }

    /**
     * Signals that a solution has been found.
     *
     * <p>
     * This method sets the {@link #solutionFound} flag to {@code true} and records the winning
     * combination and the name of the thread that found it. It is designed to be called by any
     * {@link TestClickCombination monkey}. A check ensures that only the first-found solution is
     * recorded.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is called only once. A simple check on the {@code solutionFound} flag prevents a race
     * condition where multiple monkeys find a solution simultaneously. A {@code volatile
     * boolean} is used for the flag over an {@code AtomicBoolean} to reduce overhead, as the
     * single-write nature of the event does not require atomic compare-and-set operations. The other
     * fields could piggyback on this write for visibility.
     * </p>
     *
     * @param monkeyName         The name of the monkey that found the solution.
     * @param winningCombination The combination that solves the puzzle.
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} check and set.
     * @threading Thread-safe; uses {@code volatile} for safe concurrent access.
     * @memory Does not allocate.
     */
    public void solutionFound(String monkeyName, short[] winningCombination) {
        if (solutionFound == false) {
            solutionFound = true;
            this.winningMonkey = monkeyName;
            this.winningCombination = winningCombination;
        }
        // TODO: Log the time of completion for consistent reporting.
    }

    /**
     * Returns the name of the {@link TestClickCombination monkey} that found the solution.
     *
     * @return The winning monkey's name, or {@code null} if no solution has been found.
     * @see #getWinningCombination()
     * @see #solutionFound(String, short[])
     * @since 2025.05.23 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; returns a reference to a {@code volatile} field.
     * @memory Does not allocate.
     */
    public String getWinningMonkey() { 
        return winningMonkey; 
    }
    
    /**
     * Returns the combination that solves the puzzle.
     *
     * @return The winning combination array, or {@code null} if no solution has been found.
     * @see #getWinningMonkey()
     * @see #solutionFound(String, short[])
     * @since 2025.05.23 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; returns a reference to a {@code volatile} field.
     * @memory Does not allocate.
     */
    public short[] getWinningCombination() { 
        return winningCombination; 
    }
}
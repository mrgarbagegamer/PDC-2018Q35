package com.github.mrgarbagegamer;

import java.lang.StableValue;
import java.util.function.Supplier;

import org.jctools.queues.MpmcArrayQueue;

// TODO: Fix up javadocs to reflect recent changes.
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
     * The singleton instance of this class.
     *
     * <p>
     * This field ensures that only one instance of {@code CombinationQueueArray} exists throughout
     * the application's lifecycle. It is initialized lazily via {@link #getInstance(int)} to allow
     * proper configuration with the number of consumers.
     * </p>
     *
     * @since 2025.10 - Singleton Enforcement
     * @performance {@code O(1)} access.
     * @threading Thread-safe; uses {@code volatile} for safe publication.
     * @memory Minimal footprint of 4 bytes as a reference.
     */
    private static final Supplier<CombinationQueueArray> INSTANCE = StableValue.supplier(() -> {
        final int numThreads = StartYourMonkeys.GlobalConfig.getNumThreads();
        final int numConsumers = (numThreads + 1) / 2;
        return new CombinationQueueArray(numConsumers);
    });

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
    /**
     * The start time of the program's operation, used for logging purposes. This timestamp is
     * initialized during {@link #CombinationQueueArray(int) construction}.
     * 
     * @see #endTime
     * @see #getStartTime()
     * @see StartYourMonkeys
     * @since 2025.10 - Elapsed Time Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe; immutable field.
     * @memory Minimal footprint of 8 bytes as a {@code long}.
     */
    private final long startTime;
    /**
     * The end time of the program's operation, used for logging purposes. This timestamp is set
     * when a {@link #solutionFound solution is found} or generation is {@link #generationComplete()
     * marked} as {@link #generationComplete complete}, otherwise remains {@code -1}.
     * 
     * <p>
     * This field piggybacks on the volatile writes to {@code solutionFound} and
     * {@code generationComplete} for visibility, saving the overhead of an additional volatile
     * write.
     * </p>
     * 
     * @see #startTime
     * @see #getEndTime()
     * @see StartYourMonkeys
     * @since 2025.10 - Elapsed Time Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe; piggybacks on volatile writes for safe publication.
     * @memory Minimal footprint of 8 bytes as a {@code long}.
     */
    private long endTime = -1L;
    /**
     * The name of the {@link TestClickCombination monkey} that found the solution. Written once
     * when {@link #solutionFound} is set.
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This field piggybacks on the {@code volatile} write to {@code solutionFound} for visibility,
     * saving the overhead of an additional {@code volatile} write.
     * </p>
     *
     * @see #solutionFound(String, short[])
     * @see #getWinningMonkey()
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; piggybacks on the {@code volatile} write to {@code solutionFound} for
     *            safe publication.
     * @memory Minimal footprint of 4 bytes as a reference.
     */
    private String winningMonkey = null;
    /**
     * The click combination that solves the puzzle. Written once when {@link #solutionFound} is
     * set.
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This field piggybacks on the {@code volatile} write to {@code solutionFound} for visibility,
     * saving the overhead of an additional {@code volatile} write.
     * </p>
     *
     * @see #solutionFound(String, short[])
     * @see #getWinningCombination()
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; piggybacks on the {@code volatile} write to {@code solutionFound} for
     *            safe publication.
     * @memory Minimal footprint of 4 bytes as a reference.
     */
    private short[] winningCombination = null;

    /**
     * A {@code volatile} flag indicating that a solution has been found.
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * A {@code volatile boolean} is used instead of an {@code AtomicBoolean} because it provides
     * the necessary visibility guarantees for a single-writer/multi-reader scenario with less
     * overhead. It is read frequently in the hot path.
     * </p>
     *
     * <p>
     * While generators now rely on the {@link java.util.concurrent.ForkJoinPool ForkJoinPool}'s
     * cancellation mechanism, the monkeys still poll this flag to terminate work. A future
     * implementation could potentially unify this under a single cancellation mechanism, but this
     * flag remains a simple and effective solution.
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
    private volatile boolean solutionFound = false;
    /**
     * A volatile flag indicating that all {@link CombinationGeneratorTask producers} have
     * completed.
     *
     * <p>
     * This is set to {@code true} when {@link #generationComplete() all generators have finished}.
     * It signals to {@link TestClickCombination monkeys} that no new work will be added to the
     * queues. A monkey can safely terminate when this flag is {@code true} and all work queues are
     * empty.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * A {@code volatile boolean} is used instead of an {@code AtomicBoolean} because it provides
     * the necessary visibility guarantees for a single-writer/multi-reader scenario with less
     * overhead. It is read frequently by monkeys to determine when to stop work.
     * </p>
     *
     * @see TestClickCombination#allQueuesEmpty()
     * @see TestClickCombination#run()
     * @since 2025.07 - Volatile Flag Implementation
     * @performance {@code O(1)} for reads and writes.
     * @threading Thread-safe; uses {@code volatile} for safe concurrent access.
     * @memory Fixed memory footprint of 1 byte as a {@code boolean}.
     */
    private volatile boolean generationComplete = false;

    /**
     * Constructs the shared {@link #queues queue array} and its associated resources. As a private
     * constructor, it enforces the singleton pattern via {@link #getInstance(int)}.
     *
     * <p>
     * This constructor initializes the entire communication and resource-sharing infrastructure. It
     * creates the array of work {@link CombinationQueue queues} and pre-allocates
     * {@link #workBatchPool the central} {@link WorkBatch} pool. The pool is sized to match the
     * total {@link CombinationQueue#getCapacity() capacity} of all work queues combined, which is a
     * critical invariant to ensure the recycling mechanism never fails. Finally, it starts the
     * timer for program execution by initializing the {@link #startTime} field.
     * </p>
     *
     * @param numConsumers The number of {@link TestClickCombination monkeys}. This determines the
     *                     number of work queues to create.
     * @throws IllegalArgumentException if {@code numConsumers} is not positive.
     * @throws IllegalStateException    if the pre-allocation of the {@link #workBatchPool pool}
     *                                  fails, indicating a configuration issue.
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(numConsumers)} queue initialization +
     *              {@code O(numConsumers * CombinationQueue.QUEUE_SIZE)} pool pre-allocation =
     *              {@code O(numConsumers)} time complexity.
     * @threading Thread-safe due to instance isolation.
     * @memory Allocates the array of queues, counters, and flags, and pre-allocates the pool.
     */
    private CombinationQueueArray(int numConsumers) {
        if (numConsumers <= 0) {
            throw new IllegalArgumentException("Number of consumers must be positive.");
        }

        this.queues = new CombinationQueue[numConsumers];

        // The total number of batches that can be in-flight is the sum of all queue capacities
        // The pool must be at least this large to guarantee a recycled batch is never discarded
        int totalWorkQueueCapacity = 0;
        for (int i = 0; i < numConsumers; i++) {
            queues[i] = new CombinationQueue();
            totalWorkQueueCapacity += queues[i].getCapacity();
        }
        // Set the recycle pool size to match the total work queue capacity
        this.workBatchPool = new MpmcArrayQueue<>(totalWorkQueueCapacity);

        // OPTIMIZATION: Pre-allocate the entire WorkBatch pool to prevent allocation in the hot
        // path.
        for (int i = 0; i < totalWorkQueueCapacity; i++) {
            if (!workBatchPool.offer(new WorkBatch())) {
                throw new IllegalStateException(
                        "Failed to pre-allocate the WorkBatch pool. Reconfiguration is required.");
            }
        }
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Returns the singleton instance of {@code CombinationQueueArray}, initializing it with the
     * specified number of consumers if it has not been created yet.
     *
     * <p>
     * This method enforces the singleton pattern, ensuring that only one instance exists. If the
     * instance has already been initialized, subsequent calls will return the existing instance
     * regardless of the {@code numConsumers} parameter. This allows the instance to be configured
     * once at application startup.
     * </p>
     *
     * @param numConsumers The number of {@link TestClickCombination monkeys}. Used only for
     *                     initialization if the instance does not yet exist.
     * @return The singleton instance of {@code CombinationQueueArray}.
     * @throws IllegalArgumentException if {@code numConsumers} is not positive (only on first
     *                                  call).
     * @throws IllegalStateException    if the pre-allocation of the {@link #workBatchPool pool}
     *                                  fails (only on first call).
     * @since 2025.10 - Singleton Enforcement
     * @performance {@code O(1)} for subsequent calls; {@code O(numConsumers)} for initialization.
     * @threading Thread-safe; uses double-checked locking for lazy initialization.
     * @memory Does not allocate unless initializing the singleton.
     */
    public static CombinationQueueArray getInstance() {
        return INSTANCE.get();
    }

    /**
     * Resets the singleton instance for testing purposes only.
     * 
     * <p>
     * This method is intended solely for use in unit tests to allow re-initialization of the
     * singleton instance between tests. It should not be used in production code.
     * </p>
     * 
     * @since 2025.11 - Testability Improvement
     * @performance {@code O(1)} operation.
     * @threading Not thread-safe; intended for single-threaded test environments only.
     * @memory Sets the singleton reference to {@code null}.
     */
    static void resetInstance() {
        // This is now more complex with StableValue, might need a different approach for tests
        // if re-initialization is truly needed. For now, this does nothing.
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

    /**
     * Marks the completion of the {@link CombinationGeneratorTask generators}. This method is
     * called by the system when the last generator finishes its work, marking the
     * {@link #generationComplete completion} flag as {@code true}.
     *
     * @see StartYourMonkeys#main(String[])
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} compare and set via {@code synchronized} block.
     * @threading Thread-safe; uses {@code synchronized} block with double-checked locking to ensure
     *            thread safety.
     * @memory Does not allocate.
     */
    public void generationComplete() {
        if (!generationComplete) {
            synchronized (this) {
                if (!generationComplete) {
                    generationComplete = true;
                    this.endTime = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * Signals that a solution has been found.
     *
     * <p>
     * This method sets the {@link #solutionFound} flag to {@code true} and records the winning
     * combination, the name of the thread that found it, and the {@link System#currentTimeMillis()
     * current time}. It is designed to be called by any {@link TestClickCombination monkey}. A
     * check ensures that only the first-found solution is recorded.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is called only once. A simple check on the {@code solutionFound} flag prevents a
     * race condition where multiple monkeys find a solution simultaneously. A {@code volatile
     * boolean} is used for the flag over an {@code AtomicBoolean} to reduce overhead, as the
     * single-write nature of the event does not require atomic compare-and-set operations. The
     * other fields could piggyback on this write for visibility.
     * </p>
     *
     * @param monkeyName         The name of the monkey that found the solution.
     * @param winningCombination The combination that solves the puzzle.
     * @see #endTime
     * @since 2025.05 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} check and set.
     * @threading Thread-safe; uses {@code synchronized} block with double-checked locking to ensure
     *            thread safety.
     * @memory Does not allocate.
     */
    public void solutionFound(String monkeyName, short[] winningCombination) {
        if (!solutionFound) {
            synchronized (this) {
                if (!solutionFound) {
                    this.winningMonkey = monkeyName;
                    this.winningCombination = winningCombination;
                    this.solutionFound = true;
                    this.endTime = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * Returns the {@link #winningMonkey name} of the {@link TestClickCombination monkey} that
     * solved the puzzle, or throws if {@link #isSolutionFound() no solution has been found yet}.
     *
     * @return The winning monkey's name.
     * @throws IllegalStateException if no solution has been found yet.
     * @see #getWinningCombination()
     * @see #solutionFound(String, short[])
     * @since 2025.05.23 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; piggybacks on the {@code volatile} write to {@code solutionFound} for
     *            visibility.
     * @memory Allocates an exception if called before a solution is found.
     */
    public String getWinningMonkey() {
        if (!solutionFound) {
            throw new IllegalStateException(
                    "A solution has not been found yet. Cannot get winning monkey.");
        }
        return winningMonkey;
    }

    /**
     * Returns a {@link Object#clone() clone} of the {@link #winningCombination combination that
     * solves the puzzle}, or throws if {@link #isSolutionFound() no solution has been found yet}.
     *
     * @return A clone of the winning combination array.
     * @throws IllegalStateException if no solution has been found yet.
     * @see #solutionFound
     * @see #getWinningMonkey()
     * @see #solutionFound(String, short[])
     * @since 2025.05.23 - Multiple {@code CombinationQueue}s
     * @performance {@code O(1)} retrieval.
     * @threading Thread-safe; piggybacks on the {@code volatile} write to {@code solutionFound} for
     *            visibility.
     * @memory Allocates an exception if called before a solution is found, and allocates a clone of
     *         the array when returning.
     */
    public short[] getWinningCombination() {
        if (!solutionFound) {
            throw new IllegalStateException(
                    "A solution has not been found yet. Cannot get winning combination.");
        }
        return winningCombination.clone();
    }

    /**
     * Returns the {@link #startTime start time} of the program's operation, used for logging
     * purposes. This timestamp is initialized during {@link #CombinationQueueArray(int)
     * construction}.
     * 
     * @return The start time in milliseconds since the epoch.
     * @see #endTime
     * @see #getEndTime()
     * @see StartYourMonkeys#main(String[])
     * @since 2025.10 - Elapsed Time Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe; returns a immutable field.
     * @memory Does not allocate.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns the {@link #endTime end time} of the program's operation, used for logging purposes.
     * This timestamp is set when a {@link #solutionFound(String, short[]) solution is found} or
     * generation is {@link #generationComplete() marked} as {@link #generationComplete complete}.
     * Otherwise, it remains {@code -1}.
     * 
     * @return The end time in milliseconds since the epoch, or {@code -1} if not finished.
     * @see #startTime
     * @see #getStartTime()
     * @see StartYourMonkeys#main(String[])
     * @since 2025.10 - Elapsed Time Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe; performs a read of a {@code volatile} field.
     * @memory Does not allocate.
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * An accessor for the {@link #generationComplete} flag, giving external classes read access to
     * the flag without direct exposure.
     * 
     * @return The current state of the {@code generationComplete} flag.
     * @see #generationComplete()
     * @see TestClickCombination#run()
     * @see TestClickCombination#allQueuesEmpty()
     * @since 2025.10 - {@code CombinationQueueArray} Field Encapsulation
     * @performance {@code O(1)} {@code volatile} read.
     * @threading Thread-safe; reads a {@code volatile} field.
     * @memory Does not allocate.
     */
    public boolean isGenerationComplete() {
        return generationComplete;
    }

    /**
     * An accessor for the {@link #solutionFound} flag, giving external classes read access to the
     * flag without direct exposure.
     * 
     * @return The current state of the {@code solutionFound} flag.
     * @see #solutionFound(String, short[])
     * @see TestClickCombination#run()
     * @since 2025.10 - {@code CombinationQueueArray} Field Encapsulation
     * @performance {@code O(1)} {@code volatile} read.
     * @threading Thread-safe; reads a {@code volatile} field.
     * @memory Does not allocate.
     */
    public boolean isSolutionFound() {
        return solutionFound;
    }
}
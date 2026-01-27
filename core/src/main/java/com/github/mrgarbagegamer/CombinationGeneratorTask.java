package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.StartYourMonkeys.GlobalConfig.USE_DUAL_MASKS;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.util.Unbox;

import it.unimi.dsi.fastutil.longs.LongList;

/**
 * A {@link RecursiveAction} that generates combinations of clicks for the Lights Out puzzle solver.
 *
 * <p>
 * This class is the "producer" in a producer-consumer pattern, using a {@link ForkJoinPool} to
 * recursively explore the solution space. After a recent refactor, it no longer generates
 * individual combinations. Instead, it creates compact {@link WorkBatch.WorkItem} objects that
 * describe a *range* of combinations (a shared prefix and a set of final clicks). These work items
 * are added to a {@link WorkBatch} and passed to a {@link CombinationQueue} for consumption by
 * {@link TestClickCombination "monkeys"}.
 * </p>
 *
 * <h2>Execution Model</h2>
 * <p>
 * The generation process is a tree-based, divide-and-conquer algorithm. Each task represents a
 * {@link #prefix} of clicks. The root task {@link #computeRootSubtasks(GeneratorContext) forks
 * subtasks} for each possible first click, and these subtasks recursively
 * {@link #computeIntermediateSubtasks(GeneratorContext) fork children} until the desired
 * {@link #NUM_CLICKS combination length} is reached. To optimize performance, this class implements
 * two key strategies:
 * <ul>
 * <li><b>Constraint Pruning:</b> At each branching point,
 * {@link #constraintCheck(int)} uses bitmasks to check if a path can possibly lead
 * to a valid solution, pruning entire branches early.</li>
 * <li><b>Range-Based Batching:</b> Leaf tasks now define a range of work with a single
 * {@link WorkBatch#addWork(short[], short, boolean)} call, offloading the final combination
 * enumeration to the monkeys. This significantly reduces CPU load on the generator threads.</li>
 * </ul>
 * </p>
 * 
 * <h2>Resource Management</h2>
 * <p>
 * To avoid performance degradation from excessive garbage collection, this class adheres to a
 * strict "don't allocate" policy in its hot paths. All critical resources, including
 * {@code short[]} {@code prefix} arrays and the tasks themselves, are recycled using
 * {@link ThreadLocal thread-local} pools managed by the {@link GeneratorContext GeneratorContext}.
 * This design reduces heap allocations to nearly zero during the main generation loop.
 * </p>
 * 
 * @see java.util.concurrent.ForkJoinTask
 * @since 2025.06 - Fork Join Refactor
 * @performance The theoretical complexity is {@code O(C(Grid.NUM_CELLS, numClicks))}. However,
 *              aggressive bitmask-based pruning and parallel execution significantly reduce the
 *              practical workload.
 * @threading Tasks are isolated by the {@code ForkJoinTask} framework. Shared resources are managed
 *            via a {@link GeneratorWorkerThread#context thread-local} {@link GeneratorContext
 *            GeneratorContext} to ensure thread safety and eliminate contention.
 * @algorithm A recursive, divide-and-conquer approach. Tasks form a generation tree where each node
 *            is a click prefix. Subtasks are {@link #computeIntermediateSubtasks(GeneratorContext)
 *            forked} until a {@link #NUM_CLICKS target length} is reached. Leaf tasks
 *            {@link #computeLeafCombinations(GeneratorContext) generate} work items, which are
 *            {@link #flushBatchFast(WorkBatch) queued} for validation.
 * @memory Object allocations are minimized through extensive use of {@link ArrayPool} and
 *         {@link TaskPool}, managed by a thread-local {@code GeneratorContext}.
 */
public class CombinationGeneratorTask extends RecursiveAction {
    /**
     * The pre-allocated size of the {@link ThreadLocal thread-local} resource pools in
     * {@link GeneratorContext}.
     * 
     * <p>
     * To avoid heap allocations in the hot path, this class relies on object pooling for both
     * {@code prefix} arrays (via {@link ArrayPool}) and {@code CombinationGeneratorTask} objects
     * (via {@link TaskPool}). This constant defines the capacity of those pools.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The pool size balances memory footprint against pool misses.
     * <ul>
     * <li><b>Larger pools:</b> Reduce the chance of a pool miss (which would force a new
     * allocation) at the cost of higher upfront memory usage.</li>
     * <li><b>Smaller pools:</b> Conserve memory but risk contention or misses if the task recursion
     * depth exceeds the pool capacity.</li>
     * </ul>
     * The value {@value} was chosen as a safe capacity that prevents pool misses under typical
     * conditions without excessive memory overhead.
     * </p>
     * 
     * @since 2025.06 - Array Pooling Introduction
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private static final int POOL_SIZE = 512;

    /**
     * The {@link Logger logger} for this class.
     * 
     * @see #flushAllPendingBatches()
     * @see Logger#debug(String, Object, Object)
     * @see Logger#info(String)
     * @see Logger#info(String, Object)
     * @see LogManager#getLogger()
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to the design of Log4j2.
     * @memory Fixed memory footprint of ~4 bytes as a reference.
     */
    private static final Logger logger = LogManager.getLogger(CombinationGeneratorTask.class);

    /**
     * A thread-safe collection of all active {@link GeneratorContext} instances.
     * 
     * <p>
     * This is the key to solving the final flush problem. Each time a {@link GeneratorContext
     * GeneratorContext} is {@link GeneratorContext#GeneratorContext() created} for a new thread, it
     * adds itself to this {@code static}, concurrent queue. When {@link #flushAllPendingBatches()}
     * is called, it can safely iterate over this queue to access every thread's context and flush
     * any remaining partial batches. We use a {@link ConcurrentLinkedQueue} to ensure thread-safe
     * additions and safe iteration without locking.
     * </p>
     *
     * @see ConcurrentLinkedQueue
     * @see ConcurrentLinkedQueue#ConcurrentLinkedQueue()
     * @see Queue
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} amortized insertion time; {@code O(n)} iteration time for flushing.
     * @threading Thread-safe due to the use of {@link ConcurrentLinkedQueue}.
     * @memory Grows with the number of generator threads; each entry has a minimal footprint of 4
     *         bytes as a reference.
     */
    private static final Queue<GeneratorContext> allContexts = new ConcurrentLinkedQueue<>();

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
     * performance penalty of multiple {@code ThreadLocal} lookups in the hot path. The context is
     * then passed as a parameter to downstream methods, providing fast, contention-free access to:
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
     * @threading This class is NOT thread-safe, but is intended to be used in a thread-local
     *            manner.
     * @memory Minimal memory footprint of two fixed-capacity pools and a batch reference.
     */
    static class GeneratorContext {
        /**
         * The name of the thread owning this context, for logging purposes.
         *
         * @see #allContexts
         * @see CombinationGeneratorTask#flushAllPendingBatches()
         * @see Thread#getName()
         * @since 2025.10 - Final Flush Refactor
         * @performance {@code O(1)} access time.
         * @threading Thread-safe due to immutability after initialization.
         * @memory Minimal memory footprint of ~20 bytes as a {@code String}.
         */
        private final String name;

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
        public String getName() {
            return name;
        }

        /**
         * Initializes a new {@link GeneratorContext} and registers it in the {@link #allContexts
         * global context list}.
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
         * @threading Thread-safe by nature of construction and use of a
         *            {@link ConcurrentLinkedQueue thread-safe queue}.
         * @memory Does not allocate, apart from the instance itself.
         */
        GeneratorContext(String name) {
            this.name = name;
            allContexts.add(this);
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
        final ArrayPool prefixArrayPool = new ArrayPool(POOL_SIZE);
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
        final TaskPool taskPool = new TaskPool(POOL_SIZE / 4);
        /**
         * The {@link WorkBatch} currently being filled by this thread.
         * 
         * <p>
         * Each generator thread works on its own batch, which is preserved across multiple tasks
         * executed by that thread. When the batch is full, it is flushed to a queue, and a new,
         * clean batch is obtained from a central pool. Storing the batch here, within the
         * {@link ThreadLocal thread-local} context, eliminates contention and simplifies batch
         * management.
         * </p>
         * 
         * @see WorkBatch#BATCH_SIZE
         * @see #getOrCreateBatch()
         * @since 2025.07 - {@code WorkBatch} Introduction
         * @performance {@code O(1)} access and update time.
         * @threading Not thread-safe. References to this batch should not be kept after flushing.
         * @memory Fixed footprint of ~4 bytes as a reference.
         */
        WorkBatch currentBatch = null;

        /**
         * Returns the {@link #currentBatch current} {@link WorkBatch} for this thread,
         * {@link #getNewBatchBlocking() obtaining} a new one from
         * {@link CombinationQueueArray#getWorkBatchPool() the central pool} if one doesn't exist.
         * 
         * <p>
         * This method ensures a generator task always has a valid batch to write to. It performs a
         * fast {@code null} check and, if needed, calls the blocking {@code getNewBatchBlocking()}
         * method to retrieve a recycled batch instance.
         * </p>
         * 
         * @return The current {@code WorkBatch}, or {@code null} if interrupted while obtaining a
         *         batch.
         * @since 2025.07 - {@code GeneratorContext} Introduction
         * @performance {@code O(1)} null check and amortized {@code O(1)} for batch retrieval.
         * @threading Not thread-safe. Each thread should manage its own context instance.
         */
        WorkBatch getOrCreateBatch() {
            if (currentBatch == null) {
                currentBatch = getNewBatchBlocking();
            }
            return currentBatch;
        }

        /**
         * Retrieves a new {@link WorkBatch} from the
         * {@link CombinationQueueArray#getWorkBatchPool() global pool}, blocking until one is
         * available.
         *
         * <p>
         * When a {@link TestClickCombination monkey} finishes with a {@code WorkBatch}, it returns
         * it to a central pool for recycling. This method retrieves a batch from that pool. It uses
         * a lightweight loop with a {@link org.jctools.queues.MpmcArrayQueue#relaxedPoll()
         * relaxedPoll()} to ensure the generator thread remains responsive to cancellation signals
         * from the {@link ForkJoinPool}, which a standard {@link Thread#onSpinWait()} call would
         * prevent.
         * </p>
         *
         * <p>
         * The loop now properly checks for thread interruption, allowing the {@link ForkJoinPool}
         * to shutdown generator threads when a solution is found. If the thread is interrupted
         * during the spin loop, the method returns {@code null} to signal shutdown. The thread also
         * {@link LockSupport#parkNanos(long) parks} briefly to reduce CPU usage during the wait.
         * </p>
         *
         * @return A clean, recycled {@code WorkBatch}, or {@code null} if the thread was
         *         interrupted.
         * @see CombinationQueueArray#getWorkBatchPool()
         * @see TestClickCombination#triggerGeneratorShutdown()
         * @since 2025.07 - {@code GeneratorContext} Introduction
         * @performance {@code O(1)} amortized access time, spinning if necessary.
         * @threading Thread-safe interactions with the global pool, but the method itself is not
         *            thread-safe.
         * @memory Does not allocate; reuses existing batches in the pool.
         */
        private WorkBatch getNewBatchBlocking() {
            WorkBatch batch;
            while ((batch = QUEUE_ARRAY.getWorkBatchPool().relaxedPoll()) == null) {
                // Check for thread interruption to allow proper shutdown when solution is found
                if (Thread.currentThread().isInterrupted()) {
                    return null; // Exit gracefully when interrupted
                }
                LockSupport.parkNanos(1);
                // NOTE: Thread.onSpinWait() can not be used here since it doesn't respond to
                // cancellation.
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
         * {@link CombinationGeneratorTask#flushBatchFast(WorkBatch) flushed}. It discards the
         * reference to the old batch and retrieves a new, clean one, ensuring the generator thread
         * can immediately start filling it.
         * </p>
         * 
         * @return The new {@code WorkBatch} or {@code null} if interrupted.
         * @see #getNewBatchBlocking()
         * @since 2025.07 - {@code GeneratorContext} Introduction
         * @performance {@code O(1)} amortized access time, spinning if necessary.
         * @threading Not thread-safe. Each thread should manage its own context instance.
         * @memory Does not allocate; reuses existing batches in the pool.
         */
        WorkBatch resetBatch() {
            return currentBatch = getNewBatchBlocking();
        }
    }

    // Static fields
    /**
     * The target length of the combinations to be generated.
     * 
     * <p>
     * This is a {@code static} field shared by all tasks. It is initialized by the root task and
     * defines the recursion depth for the generation process.
     * </p>
     * 
     * @see #prefix
     * @see #prefixLength
     * @see #createRootTask()
     * @see #compute()
     * @since 2025.06 - Fork Join Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private static final CombinationQueueArray QUEUE_ARRAY = CombinationQueueArray.getInstance();
    /**
     * The target length of the combinations to be generated, pulled from
     * {@link StartYourMonkeys.GlobalConfig}. Through the use of {@link StableValue StableValues}
     * and the ordering of class initializations, this value is guaranteed to be initialized before
     * any tasks are created and can be treated as a constant.
     *
     * @since 2025.12 - GlobalConfig Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private static final int NUM_CLICKS = StartYourMonkeys.GlobalConfig.getNumClicks();

    /**
     * The maximum index allowed for the first click, used for pruning.
     * 
     * <p>
     * This is a simple but effective pruning optimization. Since combinations are generated in
     * lexicographical order, we know that the first initially {@code true} cell must be toggled by
     * one of the clicks in the combination. This value is pre-calculated as the highest-indexed
     * cell adjacent to the first {@code true} cell, effectively pruning any combinations that start
     * with a click beyond this point. We pull this value from {@link StartYourMonkeys.GlobalConfig}
     * to ensure consistency with the puzzle configuration.
     * </p>
     *
     * @see #compute()
     * @see Grid#findFirstTrueAdjacents(Grid.ValueFormat)
     * @since 2025.06 - First Click Optimization
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private static final int MAX_FIRST_CLICK_INDEX = StartYourMonkeys.GlobalConfig.EVEN_CLICK_INDICES
            .get().getShort(StartYourMonkeys.GlobalConfig.EVEN_CLICK_INDICES.get().size() - 1);

    // Cached data between tasks
    /**
     * The sequence of clicks made so far, forming the base for sub-tasks or final combinations.
     * 
     * <p>
     * Each task is defined by its {@code prefix}. For an intermediate task, it will
     * {@link #computeIntermediateSubtasks(GeneratorContext) fork new tasks} by appending a new
     * click to this {@code prefix}. For a leaf task, it will
     * {@link #computeLeafCombinations(GeneratorContext) generate final combinations} by appending a
     * final click. The {@code short[]} holding the {@code prefix} is obtained from a pre-allocated
     * {@link ArrayPool} to prevent heap allocation. To prevent array resizing, we always allocate
     * arrays of size {@code NUM_CLICKS - 1}, tracking the current length of the {@code prefix} with
     * {@link #prefixLength}.
     * </p>
     * 
     * @see #recycleOwnResources(GeneratorContext)
     * @see ArrayPool
     * @see ArrayPool#get()
     * @see ArrayPool#put(short[])
     * @see GeneratorContext
     * @see GeneratorContext#prefixArrayPool
     * @since 2025.06 - Fork Join Refactor
     * @performance {@code O(1)} for adding clicks, {@code O(prefixLength)} for copying to subtasks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Fixed memory footprint of ~{@code numClicks * 2} bytes per task, using pooling to
     *         avoid allocations.
     */
    private short[] prefix;
    /**
     * The current length of the {@link #prefix} array.
     * 
     * <p>
     * This value determines the task's position in the generation tree (root, intermediate, or
     * leaf) and is used as an index for appending the next click, since {@code prefix} arrays are
     * always sized at {@code NUM_CLICKS - 1}.
     * </p>
     * 
     * @see #compute()
     * @see #prefix
     * @since 2025.06 - Fork Join Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private int prefixLength;
    /**
     * A bitmask representing the set of initially {@code true} cells toggled by the current
     * {@link #prefix}.
     * 
     * <p>
     * This is a critical field for early-stage pruning. Instead of recomputing which {@code true}
     * cells are affected by a {@code prefix}, each task inherits the state from its parent and
     * updates it incrementally with a single {@code OR} operation. This state is then used in
     * {@link #constraintCheck(int)} to determine if the current path is viable. A
     * value of {@code -1} indicates an uninitialized state, used only by the root task.
     * </p>
     * 
     * <p>
     * The use of a {@code long} limits this check to puzzles with 64 or fewer initially
     * {@code true} cells, but all puzzles simulated in this project fall under this limit.
     * </p>
     * 
     * @see #TRUE_CELL_MASKS
     * @see #skipConstraintsCheck
     * @see #constraintCheck(int)
     * @see #init(short[], int, long, boolean, boolean)
     * @see Grid#areAdjacent(short, short)
     * @see Grid#findTrueCells()
     * @since 2025.07 - Cached adjacency state introduction
     * @performance {@code O(prefixLength)} for initial computation, and {@code O(1)} for updates.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Minimal memory footprint of 8 bytes as a {@code long}.
     */
    private long currentAdjacenciesLower = -1;
    private long currentAdjacenciesUpper = -1;
    /**
     * A boolean indicating the parity of clicks affecting the first initially {@code true} cell in
     * the {@link #prefix}. {@code true} indicates an odd number of toggles, {@code false} indicates
     * even.
     * 
     * <p>
     * Like {@link #currentAdjacencies}, this value is incrementally built in the generator
     * threads to allow for {@code O(1)} checks in {@link #computeLeafCombinations(GeneratorContext)
     * leaf tasks}.
     * </p>
     * 
     * @see #computeIntermediateSubtasksConstraintPath(GeneratorContext)
     * @see #computeIntermediateSubtasksSkipPath(GeneratorContext)
     * @see #init(short[], int, long, boolean, boolean)
     * @since 2025.10 - Prefix Parity Pre-computation
     * @performance {@code O(1)} for updates during task initialization, which avoids a previously
     *              {@code O(prefixLength)} operation in the leaf task path.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Minimal memory footprint of 1 byte as a {@code boolean}.
     */
    private boolean isOdd;
    /**
     * A flag indicating that this task and all its descendants are guaranteed to satisfy the
     * constraints, allowing future checks to be skipped.
     * 
     * <p>
     * This flag is set to {@code true} by {@link #constraintCheck(int)} when a
     * {@link #prefix} is found to toggle every initially {@code true} cell. Because additional
     * clicks cannot "un-satisfy" this condition, all child tasks spawned from this point can
     * inherit this flag and bypass the expensive constraint check. This enables a much faster,
     * branch-free generation path in
     * {@link #computeIntermediateSubtasksSkipPath(GeneratorContext)}.
     * </p>
     * 
     * @see #currentAdjacencies
     * @see #computeIntermediateSubtasks(GeneratorContext)
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(1)} for checks and updates.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Minimal memory footprint of 1 byte as a {@code boolean}.
     */
    private boolean skipConstraintsCheck = false;

    /**
     * A {@code static final} cache of
     * {@link StartYourMonkeys.GlobalConfig#TRUE_CELL_MASKS}, used for quick adjacency
     * checks. Through the use of {@link StableValue StableValues} and the ordering of class
     * initializations, this array is guaranteed to be initialized before any tasks are created and
     * can be treated as a constant.
     * 
     * @since 2025.12 - GlobalConfig Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Fixed memory footprint of {@code Grid.NUM_CELLS * 8} bytes as a {@code long[]} array.
     */
    private static final LongList TRUE_CELL_MASKS_LOWER = StartYourMonkeys.GlobalConfig.TRUE_CELL_MASKS_LOWER.get();
    private static final LongList TRUE_CELL_MASKS_UPPER = StartYourMonkeys.GlobalConfig.TRUE_CELL_MASKS_UPPER.get();
    /**
     * A {@code static final} cache of {@link StartYourMonkeys.GlobalConfig#EXPECTED_MASK}, used for
     * quick pruning checks. Through the use of {@link StableValue StableValues} and the ordering of
     * class initializations, this value is guaranteed to be initialized before any tasks are
     * created and can be treated as a constant.
     *
     * @since 2025.12 - GlobalConfig Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 8 bytes as a {@code long}.
     */
    private static final long EXPECTED_MASK_LOWER = StartYourMonkeys.GlobalConfig.EXPECTED_MASK_LOWER.get();
    private static final long EXPECTED_MASK_UPPER = StartYourMonkeys.GlobalConfig.EXPECTED_MASK_UPPER.get();

    public static CombinationGeneratorTask createRootTask() {
        final CombinationGeneratorTask rootTask = new CombinationGeneratorTask();

        // Initialize instance fields
        rootTask.prefix = new short[NUM_CLICKS - 1];
        rootTask.prefixLength = 0;
        rootTask.currentAdjacenciesLower = -1;
        rootTask.currentAdjacenciesUpper = -1;

        return rootTask;
    }

    /**
     * A {@code protected} constructor for non-root tasks, used exclusively by the {@link TaskPool}.
     * 
     * <p>
     * To prevent heap allocations, new tasks are never created directly. Instead, they are recycled
     * from a {@link TaskPool}. This constructor is therefore made {@code protected} to enforce the
     * pooling pattern. Task state is configured via the {@link #init} method.
     * </p>
     * 
     * @since 2025.07 - Task Pool Introduction
     * @performance {@code O(1)} allocation.
     * @threading Isolated by the {@link java.util.concurrent.ForkJoinTask ForkJoinTask} framework.
     * @memory Allocates a {@code CombinationGeneratorTask} instance.
     */
    protected CombinationGeneratorTask() {}

    /**
     * The main computation method for the {@link RecursiveAction}.
     * 
     * <p>
     * This method acts as a dispatcher, determining the task's role based on its
     * {@link #prefixLength} and delegating to the appropriate computation path:
     * <ul>
     * <li><b>Root Task</b> ({@code prefixLength == 0}): Invokes
     * {@link #computeRootSubtasks(GeneratorContext)}.</li>
     * <li><b>Leaf Task</b> ({@code prefixLength == numClicks - 1}): Invokes
     * {@link #computeLeafCombinations(GeneratorContext)}.</li>
     * <li><b>Intermediate Task</b> (otherwise): Invokes
     * {@link #computeIntermediateSubtasks(GeneratorContext)}.</li>
     * </ul>
     * A {@code finally} block ensures that {@link #recycleOwnResources(GeneratorContext)} is always
     * called to return the task and its prefix array to their respective pools. The
     * {@link GeneratorContext context} is {@link ThreadLocal#get() fetched} once at the start to
     * minimize {@link ThreadLocal} access overhead.
     * </p>
     * 
     * @since 2025.06 - Work-stealing introduction
     * @performance {@code O(1)} context access, and {@code O(1)} dispatching.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate; uses pooled resources.
     */
    @Override
    protected void compute() {
        // Fetch the context from the custom ForkJoinWorkerThread subclass
        final GeneratorContext ctx = ((GeneratorWorkerThread) Thread.currentThread()).context;

        try {
            // Path for the root task
            if (prefixLength == 0)
                computeRootSubtasks(ctx);

            // Path for leaf tasks
            else if (prefixLength == NUM_CLICKS - 1) {
                computeLeafCombinations(ctx);
            }

            else {
                // Unified path for all intermediate (non-leaf, non-root) tasks
                computeIntermediateSubtasks(ctx);
            }
        } finally {
            // Self-cleanup: recycle our own resources
            recycleOwnResources(ctx);
        }
    }

    /**
     * Computes and forks the first level of subtasks from the root task.
     * 
     * <p>
     * This method iterates through all valid first clicks, creating and forking a new subtask for
     * each one. The range of iteration is limited by {@link #MAX_FIRST_CLICK_INDEX} as a pruning
     * optimization.
     * </p>
     * 
     * <h3>The {@code helpQuiesce()} Workaround</h3>
     * <p>
     * After forking all subtasks, this method calls {@link #helpQuiesce()}. This is a critical
     * workaround for a surprising behavior in {@link ForkJoinPool}:
     * {@link ForkJoinPool#awaitQuiescence(long, java.util.concurrent.TimeUnit)} does not wait for
     * tasks forked by the task it was invoked on. If we didn't block here, the main thread would
     * un-park prematurely, long before the search is complete.
     * </p>
     * 
     * <p>
     * Using {@code helpQuiesce()} forces the root task (and by extension, the main thread that
     * {@link ForkJoinPool#invoke(ForkJoinTask) invoked} it) to participate in work-stealing until
     * all its descendant tasks are complete. It's a strange quirk, but this is the correct way to
     * ensure the entire computation tree finishes before the program exits.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext}.
     * @see #recycleOwnResources(GeneratorContext)
     * @see StartYourMonkeys#main(String[])
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(N)} where {@code N} is the number of valid first clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate unless pools are empty.
     */
    private void computeRootSubtasks(GeneratorContext ctx) {
        final short start = 0;
        final short max = (short) (Math.min(Grid.NUM_CELLS - NUM_CLICKS, MAX_FIRST_CLICK_INDEX)
                + 1);

        for (short i = start; i < max; i++) {
            final long lowerMask = TRUE_CELL_MASKS_LOWER.getLong(i);
            
            final short[] newPrefix = buildPrefixWithNewValue(ctx, i);

            // Identify the parity of this root subtask:
            final boolean parity = (lowerMask & 1L) != 0;

            getAndForkSubtask(ctx, newPrefix, lowerMask, TRUE_CELL_MASKS_UPPER.getLong(i), false, parity);
        }

        helpQuiesce(); // Wait for all subtasks to complete before returning
        // This will ensure that the root task does not exit prematurely, keeping the main thread
        // parked
    }

    private short[] buildPrefixWithNewValue(GeneratorContext ctx, short newValue) {
        short[] newPrefix = ctx.prefixArrayPool.get();
        if (newPrefix == null)
            newPrefix = new short[NUM_CLICKS - 1]; // Safeguard if pool is empty
        System.arraycopy(this.prefix, 0, newPrefix, 0, this.prefixLength);
        newPrefix[this.prefixLength] = newValue;
        return newPrefix;
    }

    private void getAndForkSubtask(GeneratorContext ctx, short[] newPrefix, long newAdjacencyLower,
            long newAdjacencyUpper, boolean skipConstraints, boolean isOdd) {
        final CombinationGeneratorTask subtask = ctx.taskPool.get();
        subtask.init(newPrefix, this.prefixLength + 1, newAdjacencyLower, newAdjacencyUpper,
                skipConstraints, isOdd);

        // Fork the subtask - it will clean itself up
        subtask.fork();
    }

    /**
     * Initializes a recycled task with a new {@link #prefix} and state.
     * 
     * <p>
     * This method is central to the object pooling strategy. Instead of creating a new task, we
     * recycle an existing one from the {@link TaskPool} and re-initialize it with a new
     * {@code prefix} and state. It assigns the given parameters and calls {@link #reinitialize()}
     * to reset the {@link java.util.concurrent.ForkJoinTask ForkJoinTask} state, making the task
     * ready for re-submission.
     * </p>
     * 
     * <p>
     * This method was intentionally designed as a single, monomorphic call site. Previous versions
     * had multiple {@code init} overloads, which hindered JIT compiler optimizations. By
     * consolidating them into one method, we improve performance and allow for greater JIT
     * optimizations, at the small cost of the occasional unnecessary assignment.
     * </p>
     * 
     * @param prefix               The prefix {@code short[]} for the new task.
     * @param prefixLength         The length of the {@code prefix}.
     * @param parentAdjacencyState The {@link #currentAdjacencies} from the parent task.
     * @param skipConstraints      A flag indicating if constraint checks can be skipped.
     * @param prefixParity         The parity of the {@code prefix} affecting the first {@code true}
     *                             cell.
     * @since 2025.07 - Task Pool Introduction
     * @performance {@code O(1)} for assignments and reinitialization.
     * @threading Not thread-safe; must be called by only one thread at a time on a given task.
     * @memory Does not allocate; uses pooled resources.
     */
    public void init(short[] prefix, int prefixLength, long parentAdjacencyStateLower,
            long parentAdjacencyStateUpper, boolean skipConstraints, boolean prefixParity) {
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.skipConstraintsCheck = skipConstraints;
        this.isOdd = prefixParity;
        this.currentAdjacenciesLower = parentAdjacencyStateLower;
        reinitialize();

        if (USE_DUAL_MASKS.get()) { // Constant foldable by JIT (hopefully)
            // Update the upper state
            this.currentAdjacenciesUpper = parentAdjacencyStateUpper;
        }
    }

    // LEAF TASK PATH:
    /**
     * Defines a work range for a leaf task and adds it to a {@link WorkBatch}.
     *
     * <p>
     * This is the final, non-recursive step in the generation process. In the refactored design,
     * this method no longer generates individual combinations. Instead, it defines a
     * {@link WorkBatch.WorkItem} that represents all valid final combinations for the current
     * {@link #prefix}. This dramatically reduces the generator's workload.
     * </p>
     *
     * <h3>Algorithm</h3>
     * <ol>
     * <li><b>WorkItem Batching:</b> It creates a single {@link WorkBatch.WorkItem} representing the
     * {@code prefix} and the entire valid range of final clicks by calling
     * {@link WorkBatch#addWork(short[], short, boolean)}.</li>
     * </ol>
     * If the current batch is full, it is {@link #flushBatchFast(WorkBatch) flushed} before the new
     * work item is added. This bulk-processing approach dramatically reduces method call overhead
     * and loop-related costs, making it significantly more efficient than an iterative approach.
     * </p>
     *
     * @param ctx The thread-local {@link GeneratorContext}.
     * @see GeneratorContext#getOrCreateBatch()
     * @since 2025.07 - Splitting the Compute Method Into Paths
     * @performance {@code O(1)} for the start index lookup.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @algorithm Uses an {@code O(1)} lookup to find a starting index, then adds a single
     *            {@code WorkItem} to the batch representing the entire remaining range.
     * @memory Does not allocate; uses pooled resources.
     */
    private final void computeLeafCombinations(GeneratorContext ctx) {
        // REDESIGNED: Offload combination generation to the WorkBatch iterator.
        // This method now only defines the *range* of work.
        final short lastPrefixClick = (short) (this.prefix[this.prefixLength - 1] + 1);

        // 1. Add the work range to the batch.
        WorkBatch batch = ctx.getOrCreateBatch();
        if (batch == null)
            return; // Exit if interrupted

        // If the batch is full, flush it and get a new one.
        if (batch.isFull()) {
            if (!flushBatchFast(batch))
                return; // Interrupted
            batch = ctx.resetBatch();
            if (batch == null)
                return; // Interrupted
        }

        // Add the entire valid range as a single work item.
        batch.addWork(prefix, lastPrefixClick, this.isOdd);
    }

    /**
     * Dispatches to the appropriate intermediate subtask computation method based on the
     * {@link #skipConstraintsCheck} flag.
     * 
     * <p>
     * This method serves as a simple, fast dispatcher for all intermediate tasks (i.e., non-root,
     * non-leaf). Its sole purpose is to route control to one of two specialized computation paths,
     * which is a critical optimization for the JIT compiler.
     * </p>
     * 
     * <h3>Monomorphic Call Site Optimization</h3>
     * <p>
     * A key performance goal is to avoid branching inside the hot loops where subtasks are forked.
     * If a task's {@link #prefix} is already known to satisfy the
     * {@link #canPotentiallySatisfyConstraints constraint check}, all its descendants can skip that
     * check. Instead of handling this with an {@code if} statement inside a single, large method,
     * we use two separate methods:
     * <ul>
     * <li>{@link #computeIntermediateSubtasksConstraintPath(GeneratorContext)}: The "slow" path
     * that includes the pruning check.</li>
     * <li>{@link #computeIntermediateSubtasksSkipPath(GeneratorContext)}: The "fast" path that
     * omits the check entirely.</li>
     * </ul>
     * This dispatcher creates a <strong>monomorphic call site</strong> for each path. The JIT
     * compiler can more aggressively optimize these separate, branch-free methods than it could a
     * single method containing a conditional check, leading to significant performance gains. It
     * also simplifies the code for {@link #compute()}, designating a clear input method for
     * intermediate tasks without an additional conditional.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext}.
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(1)} for dispatching.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate.
     */
    private void computeIntermediateSubtasks(GeneratorContext ctx) {
        final short start = (short) (this.prefix[this.prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (NUM_CLICKS - this.prefixLength) + 1);

        if (skipConstraintsCheck) {
            computeIntermediateSubtasksSkipPath(ctx, start, max);
        } else {
            computeIntermediateSubtasksConstraintPath(ctx, start, max);
        }
    }

    // PURE HOT PATH 1:
    /**
     * Forks subtasks for an intermediate task, skipping the constraint check.
     * 
     * <p>
     * This is the "fast path" for intermediate tasks. It is invoked when the current task's
     * {@link #prefix} has already been proven to satisfy the
     * {@link #canPotentiallySatisfyConstraints constraint check}, meaning all its descendants are
     * also guaranteed to be on a valid path.
     * </p>
     * 
     * <p>
     * This method contains a highly optimized, branch-free loop. It iterates through all possible
     * next clicks, gets a recycled task and {@code prefix} from the pools,
     * {@link #init(short[], int, long, boolean, boolean) initializes them}, and {@link #fork()
     * forks} them for execution. Because no pruning checks are needed, the loop body is minimal and
     * extremely friendly to JIT compiler optimizations. A small safeguard exists to allocate a new
     * array if the {@link ArrayPool} is exhausted, though this is not expected in normal operation
     * and could be removed for a slight performance gain at the cost of robustness.
     * 
     * @param ctx The thread-local {@link GeneratorContext} containing the resource pools.
     * @see #skipConstraintsCheck
     * @see #computeIntermediateSubtasksConstraintPath(GeneratorContext)
     * @since 2025.08 - Specialized Subtask Paths
     * @performance {@code O(Grid.NUM_CELLS - numClicks + prefixLength - prefix[prefixLength - 1] + 1)}
     *              for iterating over possible next clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate unless pools are empty; uses pooled resources.
     */
    private void computeIntermediateSubtasksSkipPath(GeneratorContext ctx, short start, short max) {
        // Pure loop - no constraint checking, no mask loading, no conditionals
        for (short i = start; i < max; i++) {
            final long lowerMask = TRUE_CELL_MASKS_LOWER.getLong(i);

            final short[] newPrefix = buildPrefixWithNewValue(ctx, i);

            // Determine the parity of the new prefix based on the new click
            final boolean newPrefixParity = getNewPrefixParity(lowerMask);

            // All parameters are constants - perfect for JIT optimization
            getAndForkSubtask(ctx, newPrefix, -1L, -1L, true, newPrefixParity);
        }
    }

    private boolean getNewPrefixParity(long lowerMask) {
        return this.isOdd ^ ((lowerMask & 1L) != 0);
    }

    // PURE HOT PATH 2:
    /**
     * Forks subtasks for an intermediate task, performing a constraint check to prune invalid
     * branches.
     * 
     * <p>
     * This is the "slow path" for intermediate tasks. It is invoked when the current task's
     * {@link #prefix} has not yet been proven to satisfy all constraints. Before forking subtasks,
     * it performs a critical pruning step by calling
     * {@link #constraintCheck(int)}. If that check determines that no possible
     * descendant of this task can form a valid solution, the entire branch is pruned, saving a
     * massive amount of wasted computation.
     * </p>
     * 
     * <p>
     * If the path is viable, the method proceeds to fork subtasks in a tight loop. For each new
     * subtask, it incrementally updates the {@link #currentAdjacencies} with a single {@code OR}
     * operation and passes it down. It also propagates the {@link #skipConstraintsCheck} flag,
     * which may have been set to {@code true} inside {@code canPotentiallySatisfyConstraints},
     * allowing all subsequent children to use the "fast path" from this point forward. To keep the
     * loop free of conditionals, the adjacency state is calculated even if the check passes and
     * sets the skip flag, which is a minor trade-off for a more JIT-friendly method body. A similar
     * safeguard for the {@link ArrayPool} also exists here.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext} containing the resource pools.
     * @see #computeIntermediateSubtasksSkipPath(GeneratorContext)
     * @since 2025.08 - Specialized Subtask Paths
     * @performance {@code O(1)} for the early constraint check,
     *              <code>O({@link Grid#NUM_CELLS} - {@link #NUM_CLICKS} + {@link #prefixLength} -
     *              {@link #prefix}[prefixLength - 1] + 1)</code> for iterating over possible next
     *              clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate unless pools are empty; uses pooled resources.
     */
    private void computeIntermediateSubtasksConstraintPath(GeneratorContext ctx, short start,
            short max) {
        // Early constraint check - happens ONCE per task, not per iteration
        if (prefixLength >= 2 && !constraintCheck(start)) {
            return; // Skip this entire branch if constraints cannot be satisfied
        }

        // Pure loop - no conditionals inside, all branching resolved outside loop
        for (short i = start; i < max; i++) {
            final long lowerMask = TRUE_CELL_MASKS_LOWER.getLong(i);
            
            final short[] newPrefix = buildPrefixWithNewValue(ctx, i);

            // Determine the parity of the new prefix based on the new click
            final boolean newPrefixParity = getNewPrefixParity(lowerMask);

            // Update the adjacency state for the child task
            final long childAdjacenciesLower = this.currentAdjacenciesLower
                    | lowerMask;
            // TODO: Extract this to a separate method that checks if dual masks are enabled for
            // better JIT constant folding
            final long childAdjacenciesUpper = this.currentAdjacenciesUpper
                    | TRUE_CELL_MASKS_UPPER.getLong(i); // Only used if dual masks are enabled, otherwise
                                                // constant folded out

            // All parameters determined - perfect for JIT constant propagation
            getAndForkSubtask(ctx, newPrefix, childAdjacenciesLower, childAdjacenciesUpper,
                    this.skipConstraintsCheck, newPrefixParity);
        }
    }

    /**
     * Checks if the current {@link #prefix} and its descendants can potentially form a valid
     * solution.
     * 
     * <p>
     * This is the primary pruning mechanism in the generation tree. A valid solution must toggle
     * every initially {@code true} cell. This method uses bitmasks to determine if it's possible
     * for the current {@code prefix}, combined with any future clicks, to meet this condition. If
     * not, the entire branch of the search tree is abandoned, saving immense computation.
     * </p>
     * 
     * <h3>Algorithm</h3>
     * <p>
     * The check is performed in two stages, both using {@code O(1)} bitwise operations:
     * <ol>
     * <li><b>Direct Check:</b> It computes the {@code needed} bits by XORing the
     * {@link #currentAdjacencies} (which {@code true} cells are toggled by the current prefix)
     * with the {@link #EXPECTED_MASK} (all {@code true} cells). If {@code needed} is zero, the
     * constraint is already met. As an optimization, it sets {@link #skipConstraintsCheck} to
     * {@code true}, allowing all descendants to use a faster, check-free generation path.</li>
     * <li><b>Potential Check:</b> If the constraint is not yet met, it checks if it's still
     * <em>possible</em> to meet it. It uses a pre-computed {@link #SUFFIX_OR_MASKS} bitmask, which
     * represents all the {@code true} cells that <em>could</em> be toggled by any of the remaining
     * available clicks. If the bitwise {@code AND} of this "available" mask and the {@code needed}
     * mask equals the {@code needed} mask, it means a solution is still possible, and the path is
     * not pruned.</li>
     * </ol>
     * This allows the method to prune with certainty without needing to iterate through future
     * clicks.
     * </p>
     * 
     * @param startIdx The starting index for the next click, used to look up the correct suffix
     *                 mask.
     * @return {@code true} if this path is still viable, {@code false} if it should be pruned.
     * @see #TRUE_CELL_MASKS
     * @see Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)
     * @see Grid#findTrueCells(Grid.ValueFormat)
     * @see Grid.ValueFormat#Bitmask
     * @since 2025.07 - Splitting the Compute Method Into Paths
     * @performance {@code O(1)} due to the use of pre-computed bitmasks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate; uses pre-computed {@code static} bitmasks.
     */
    boolean constraintCheck(int startIdx) {
        if (USE_DUAL_MASKS.get()) { // Constant foldable by JIT (hopefully)
            return constraintCheckDualMask(startIdx);
        } else {
            return constraintCheckSingleMask(startIdx);
        }
    }

    private boolean constraintCheckSingleMask(int startIdx) {
        // cachedAdjacencyState can only be -1 for the root task (which skips this check)
        // Therefore, we can assume it is initialized here, saving a branch in our logic.

        // XOR with lower expected mask to find which bits need to be flipped
        final long needed = this.currentAdjacenciesLower ^ EXPECTED_MASK_LOWER;

        // If no bits need to be flipped, we're already good.
        // OPTIMIZATION: Skip future checks by setting skipConstraintsCheck to true
        if (needed == 0L) {
            return skipConstraintsCheck = true;
        }

        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        return (SUFFIX_MASKS_LOWER.getLong(startIdx) & needed) == needed;
    }

    private boolean constraintCheckDualMask(int startIdx) {
        // cachedAdjacencyState can only be -1 for the root task (which skips this check)
        // Therefore, we can assume it is initialized here, saving a branch in our logic.

        // XOR with expected masks to find which bits need to be flipped
        final long neededLower = this.currentAdjacenciesLower ^ EXPECTED_MASK_LOWER;
        final long neededUpper = this.currentAdjacenciesUpper ^ EXPECTED_MASK_UPPER;

        // If no bits need to be flipped, we're already good.
        // OPTIMIZATION: Skip future checks by setting skipConstraintsCheck to true
        if (neededLower == 0L && neededUpper == 0L) {
            return skipConstraintsCheck = true;
        }

        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        return (SUFFIX_MASKS_LOWER.getLong(startIdx) & neededLower) == neededLower
                && (SUFFIX_MASKS_UPPER.getLong(startIdx) & neededUpper) == neededUpper;
    }

    /**
     * An array of pre-computed "suffix OR" bitmasks for {@code O(1)} constraint checking.
     * 
     * <p>
     * This is a critical optimization for {@link #constraintCheck(int)}. The mask
     * at index {@code i} is the bitwise {@code OR} of all {@link #TRUE_CELL_MASKS} from
     * index {@code i} to the end of the grid.
     * </p>
     * <p>
     * In effect, {@code SUFFIX_OR_MASKS[i]} represents every {@code true} cell that can possibly be
     * toggled by <em>any</em> future click from index {@code i} onwards. This allows the constraint
     * check to determine if a solution is still possible in a single {@code O(1)} bitwise
     * operation, rather than iterating through all remaining clicks (though the use of a
     * {@code long} limits this optimization to puzzles with 64 or fewer {@code true} cells).
     * </p>
     * 
     * <p>
     * The array is initialized statically via
     * {@link StartYourMonkeys.GlobalConfig#SUFFIX_OR_MASKS}, ensuring it is ready before any tasks
     * are created.
     * </p>
     * 
     * @since 2025.07 - Suffix OR Masks Introduction
     * @performance {@code O(1)} for constraint checks, {@code O(Grid.NUM_CELLS)} for initial
     *              computation.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Fixed memory footprint of ~{@code 8 * Grid.NUM_CELLS} bytes as a {@code long} array.
     */
    private static final LongList SUFFIX_MASKS_LOWER = StartYourMonkeys.GlobalConfig.SUFFIX_MASKS_LOWER.get();
    private static final LongList SUFFIX_MASKS_UPPER = StartYourMonkeys.GlobalConfig.SUFFIX_MASKS_UPPER.get();

    /**
     * Flushes a {@link WorkBatch batch} of combinations to an available {@link CombinationQueue
     * queue}, employing a backoff strategy if all queues are temporarily full.
     * 
     * <p>
     * This method is a critical component of the producer-consumer architecture, ensuring that
     * generated combinations are efficiently transferred to {@link TestClickCombination monkeys}
     * for validation. It implements a robust mechanism to handle queue contention and backpressure
     * without dropping any combinations.
     * </p>
     * 
     * <h3>Algorithm</h3>
     * <p>
     * The method attempts to add the {@code WorkBatch} to a {@link ThreadLocalRandom#nextInt(int)
     * randomly selected queue} from the {@link #QUEUE_ARRAY}. To minimize contention, it tries each
     * queue once in a round-robin fashion. If all queues are full, the thread
     * {@link Thread#sleep(long, int) sleeps} briefly (0.5ms) to avoid busy-waiting, then retries.
     * This loop continues indefinitely until the batch is successfully enqueued or the thread is
     * interrupted. This "never drop a combination" rule is fundamental to the solver's correctness.
     * </p>
     * 
     * <h3>Future Optimizations</h3>
     * <p>
     * The current backpressure mechanism (sleeping) is simple but can be improved. Potential
     * enhancements include:
     * <ul>
     * <li>Using {@link ForkJoinPool.ManagedBlocker} to allow the pool to compensate for blocked
     * generator threads by spinning up other workers.</li>
     * <li>Implementing a more sophisticated blocking strategy (e.g., using a dedicated signal from
     * consumers) to wake generator threads only when queue space is available, avoiding unnecessary
     * sleeps. This could, however, increase complexity, potentially requiring a shift to a
     * different queue structure.</li>
     * </ul>
     * </p>
     * 
     * @param batch The {@link WorkBatch} containing generated combinations to be flushed.
     * @return {@code true} if the batch was successfully flushed, {@code false} if the thread was
     *         interrupted before flushing could complete.
     * @see CombinationQueue#add(WorkBatch)
     * @since 2025.06.08 - Fork Join Refactor
     * @performance {@code O(numQueues)} per attempt, with a brief sleep if all queues are full.
     * @threading Thread-safe due to local queue access and atomic operations within
     *            {@link CombinationQueue#add(WorkBatch)}.
     */
    private final boolean flushBatchFast(WorkBatch batch) {
        CombinationQueue[] queues = QUEUE_ARRAY.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);

        // Try each queue once and sleep if all are full
        while (true) {
            for (int i = 0; i < queues.length; i++) {
                if (queues[(startIdx + i) % queues.length].relaxedOffer(batch))
                    return true;
            }

            // If we reach here, all queues were full
            try {
                Thread.sleep(0, 500_000); // Sleep briefly (0.5ms) to avoid busy-waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                return false; // Exit if interrupted
            }
        }
    }

    /**
     * Recycles the {@link #prefix} and the {@code CombinationGeneratorTask} instance back to their
     * respective pools within the provided {@link GeneratorContext}.
     * 
     * <p>
     * This method is invoked by each {@code CombinationGeneratorTask} upon completion of its
     * {@link #compute()} method. It is a fundamental part of the object pooling strategy,
     * preventing excessive heap allocations and subsequent garbage collection overhead during the
     * high-volume generation process.
     * </p>
     * 
     * <p>
     * By returning objects to pools instead of allowing them to be garbage collected, the solver
     * maintains a low memory footprint and consistent performance, avoiding "stop-the-world"
     * pauses.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext} containing the {@link ArrayPool} and
     *            {@link TaskPool}.
     * @see ArrayPool
     * @see ArrayPool#put(short[])
     * @see GeneratorWorkerThread#context
     * @see TaskPool
     * @see TaskPool#put(CombinationGeneratorTask)
     * @since 2025.07 - Task Self-Recycling
     * @performance {@code O(1)} for returning resources.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation and thread-local {@code GeneratorContext}.
     */
    private void recycleOwnResources(GeneratorContext ctx) {
        // No ThreadLocal access needed - use passed context

        // Recycle prefix array to context pool
        ctx.prefixArrayPool.put(prefix);
        prefix = null;

        // Recycle task to context pool
        ctx.taskPool.put(this);
    }

    /**
     * Flushes any remaining pending {@link WorkBatch batches} from a single thread's
     * {@link GeneratorContext} to the {@link CombinationQueueArray}.
     * 
     * <p>
     * This method is called once by the main thread after all generator tasks have completed. Its
     * purpose is to ensure that any partially filled {@code WorkBatch}es, which might not have
     * reached {@link WorkBatch#BATCH_SIZE} and thus weren't flushed during normal operation, are
     * nonetheless processed.
     * </p>
     * 
     * <p>
     * It iterates through all known {@code GeneratorContext} instances (tracked in
     * {@link #allContexts}), checks if they have a {@link WorkBatch#isEmpty() non-empty} current
     * batch, and {@link #flushBatchBlocking(WorkBatch) flushes it} using a blocking strategy to
     * guarantee delivery. It also logs the start and completion of the flush process for
     * transparency.
     * </p>
     * 
     * @see GeneratorWorkerThread#context
     * @since 2025.06 - Flush Batches when Full (or on Completion)
     * @performance {@code O(numQueues)} for the flushing operation.
     * @threading Synchronized to prevent concurrent invocations.
     * @memory Does not allocate, apart from some logging overhead.
     */
    public static synchronized void flushAllPendingBatches() {
        logger.info(
                "Starting final, single-threaded flush of all pending batches from {} contexts...",
                Unbox.box(allContexts.size()));

        for (GeneratorContext ctx : allContexts) {
            WorkBatch batch = ctx.currentBatch;
            if (batch != null && !batch.isEmpty()) {
                logger.debug("Flushing final batch of size {} from {}.", Unbox.box(batch.size()),
                        ctx.getName());
                flushBatchBlocking(batch);
                ctx.currentBatch = null;
            }
        }

        logger.info("Final flush completed.");
        allContexts.clear();
    }

    /**
     * Flushes a {@link WorkBatch} to the {@link CombinationQueueArray}, {@link Thread#sleep(long)
     * blocking} if necessary until successful.
     * 
     * <p>
     * This method is similar to {@link #flushBatchFast(WorkBatch)}, but it employs a blocking
     * strategy to ensure that the batch is eventually flushed, even if all queues are temporarily
     * full. It is used during the final flush of pending batches to guarantee that no combinations
     * are lost.
     * </p>
     * 
     * @throws InterruptedException if the thread is interrupted while sleeping.
     * @see #flushAllPendingBatches()
     * @since 2025.10 - Guaranteed Flush for Final Batches
     * @performance {@code O(numQueues)} per attempt, with a brief sleep if all queues are full.
     * @threading Thread-safe due to local queue access and atomic operations within
     *            {@link CombinationQueue#add(WorkBatch)}.
     * @memory Does not allocate.
     */
    private static void flushBatchBlocking(WorkBatch batch) {
        CombinationQueue[] queues = QUEUE_ARRAY.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);

        while (true) {
            for (int i = 0; i < queues.length; i++) {
                if (queues[(startIdx + i) % queues.length].offer(batch))
                    return;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * A custom {@link ForkJoinWorkerThread} that holds a direct, {@code final} reference to its own
     * {@link GeneratorContext}.
     *
     * <p>
     * This optimization eliminates the need for a {@link ThreadLocal#get()} lookup in the hot path
     * of {@link #compute()}, as the context can be accessed directly from the current thread
     * instance. This provides a small but meaningful performance improvement by reducing access
     * overhead.
     * </p>
     *
     * @see ForkJoinWorkerThreadFactory
     * @since 2025.12 - Custom Worker Thread Optimization
     * @threading Instances are confined to their respective threads.
     */
    private static class GeneratorWorkerThread extends ForkJoinWorkerThread {
        /**
         * The {@link GeneratorContext} unique to this worker thread.
         * 
         * @since 2025.12 - Custom Worker Thread Optimization
         * @performance {@code final} reference for direct access.
         * @threading Thread-local confinement.
         * @memory One instance per worker thread.
         */
        final GeneratorContext context;

        /**
         * Constructs a new worker thread for the given pool, creating its unique
         * {@link GeneratorContext}.
         *
         * @param pool The pool this thread is joining.
         * @since 2025.12 - Custom Worker Thread Optimization
         * @performance {@code O(1)} for construction.
         * @threading Confined to the creating thread.
         * @memory Allocates one {@link GeneratorContext}.
         */
        GeneratorWorkerThread(ForkJoinPool pool) {
            super(pool);
            this.context = new GeneratorContext(this.getName());
        }
    }

    /**
     * A factory for creating {@link GeneratorWorkerThread} instances for the {@link ForkJoinPool}.
     *
     * @see ForkJoinWorkerThread
     * @since 2025.12 - Custom Worker Thread Optimization
     * @threading Stateless and thread-safe.
     */
    public static class GeneratorWorkerThreadFactory implements ForkJoinWorkerThreadFactory {
        /**
         * Creates and returns a new {@link GeneratorWorkerThread}.
         *
         * @param pool The pool in which the new thread will operate.
         * @return The new worker thread.
         * @since 2025.12 - Custom Worker Thread Optimization
         * @performance {@code O(1)} for thread creation.
         * @threading Thread-safe.
         * @memory Allocates one {@link GeneratorWorkerThread}.
         */
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new GeneratorWorkerThread(pool);
        }
    }
}
package com.github.mrgarbagegamer;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.util.Unbox;

/**
 * A {@link RecursiveAction} that generates combinations of clicks for the Lights Out puzzle solver.
 * 
 * <p>
 * This class is the "producer" in a producer-consumer pattern, using a {@link ForkJoinPool} to
 * recursively explore the solution space. It generates potential solutions (combinations of clicks)
 * as {@code short[]} arrays and hands them off in {@link WorkBatch} objects to a
 * {@link CombinationQueue} for consumption by {@link TestClickCombination "monkeys"}.
 * </p>
 * 
 * <h2>Execution Model</h2>
 * <p>
 * The generation process is a tree-based, divide-and-conquer algorithm. Each task represents a
 * {@link #prefix} of clicks. The root task {@link #computeRootSubtasks(GeneratorContext) forks
 * subtasks} for each possible first click, and these subtasks recursively
 * {@link #computeIntermediateSubtasks(GeneratorContext) fork children} until the desired
 * {@link #numClicks combination length} is reached. To optimize performance, this class implements
 * two key strategies:
 * <ul>
 * <li><b>Constraint Pruning:</b> At each branching point,
 * {@link #canPotentiallySatisfyConstraints(int)} uses bitmasks to check if a path can possibly lead
 * to a valid solution, pruning entire branches early.</li>
 * <li><b>Batching:</b> Leaf tasks group thousands of generated combinations into a
 * {@code WorkBatch} before {@link #flushBatchFast(WorkBatch) flushing} it to a queue. This
 * drastically reduces queue contention, a major bottleneck in previous designs.</li>
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
 *            via a {@link #context ThreadLocal} {@link GeneratorContext GeneratorContext} to ensure
 *            thread safety and eliminate contention.
 * @algorithm A recursive, divide-and-conquer approach. Tasks form a generation tree where each node
 *            is a click prefix. Subtasks are {@link #computeIntermediateSubtasks(GeneratorContext)
 *            forked} until a {@link #numClicks target length} is reached. Leaf tasks
 *            {@link #computeLeafCombinations(GeneratorContext) generate} final combinations, which
 *            are batched and {@link #flushBatchFast(WorkBatch) queued} for validation.
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
     * {@code prefix} arrays (via {@link ArrayPool}) and {@code CombinationGeneratorTask} objects (via
     * {@link TaskPool}). This constant defines the capacity of those pools.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The pool size balances memory footprint against pool misses.
     * <ul>
     * <li><b>Larger pools:</b> Reduce the chance of a pool miss (which would force a new allocation) at
     * the cost of higher upfront memory usage.</li>
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
    private static final Logger logger = LogManager.getLogger();
 
    /**
     * A {@link ThreadLocal} container for the {@link GeneratorContext}.
     * 
     * <p>
     * This provides each generator thread with its own isolated set of resources ({@link ArrayPool
     * array pools}, {@link TaskPool task pools}, and the current {@link WorkBatch}). Using a single
     * {@code ThreadLocal} to hold a context object is a key optimization that avoids the high cost of
     * multiple {@link ThreadLocal#get()} calls in the hot path. The context is fetched once at the
     * start of {@link #compute()} and then passed down as a parameter.
     * </p>
     * 
     * @since 2025.07 - {@code GeneratorContext} Introduction
     * @performance {@code O(BATCH_SIZE)} on creation (due to pre-allocation); {@code O(1)} access time.
     * @threading Thread-local; each thread has its own instance of the context obtained via
     *            {@code context.get()}.
     * @memory Fixed memory footprint of 4 bytes as a reference.
     */
    private static final ThreadLocal<GeneratorContext> context =
        ThreadLocal.withInitial(GeneratorContext::new);

    /**
     * A thread-safe collection of all active {@link GeneratorContext} instances.
     * 
     * <p>
     * This is the key to solving the final flush problem. Each time a {@link #context GeneratorContext}
     * is {@link GeneratorContext#GeneratorContext() created} for a new thread, it adds itself to this
     * {@code static}, concurrent queue. When {@link #flushAllPendingBatches()} is called, it can safely
     * iterate over this queue to access every thread's context and flush any remaining partial batches.
     * We use a {@link ConcurrentLinkedQueue} to ensure thread-safe additions and safe iteration without
     * locking.
     * </p>
     *
     * @see ConcurrentLinkedQueue
     * @see ConcurrentLinkedQueue#ConcurrentLinkedQueue()
     * @see Queue
     * @since 2025.10 - Final Flush Refactor
     * @performance {@code O(1)} amortized insertion time; {@code O(n)} iteration time for flushing.
     * @threading Thread-safe due to the use of {@link ConcurrentLinkedQueue}.
     * @memory Grows with the number of generator threads; each entry has a minimal footprint of 4 bytes
     *         as a reference.
     */
    private static final Queue<GeneratorContext> allContexts = new ConcurrentLinkedQueue<>();

    /**
     * A container for all {@link ThreadLocal thread-local} resources used by a generator.
     *
     * <p>
     * This class is a key part of the resource management strategy. It consolidates all expensive,
     * thread-specific objects into a single container. An instance of this context is stored in a
     * {@link #context ThreadLocal}, ensuring that each thread in the {@link ForkJoinPool} has its own
     * set of resource pools and a dedicated {@link WorkBatch}.
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
    private static class GeneratorContext {
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
        private final String threadName = Thread.currentThread().getName();

        /**
         * Gets the {@link #threadName name} of the thread owning this context. Used for logging purposes.
         * 
         * @return The name of the this context's thread.
         * @see CombinationGeneratorTask#flushAllPendingBatches()
         * @since 2025.10 - Final Flush Refactor
         * @performance {@code O(1)} access time.
         * @threading Thread-safe, as it returns an immutable field.
         * @memory Does not allocate; returns a reference to an existing {@code String}.
         */
        public String getThreadName() {
            return threadName;
        }
        
        /**
         * Initializes a new {@link GeneratorContext} and registers it in the {@link #allContexts global
         * context list}.
         * 
         * <p>
         * This constructor is meant to be called only by the {@link #context ThreadLocal's}
         * {@link ThreadLocal#withInitial(java.util.function.Supplier) initializer}, and
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
        private GeneratorContext()
        {
            allContexts.add(this);
        }

        /**
         * A {@link ThreadLocal thread-local} {@link ArrayPool pool} for recycling {@code short[]} arrays
         * used for {@code prefix}es.
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
         * Each generator thread works on its own batch, which is preserved across multiple tasks executed
         * by that thread. When the batch is full, it is flushed to a queue, and a new, clean batch is
         * obtained from a central pool. Storing the batch here, within the {@link ThreadLocal thread-local}
         * context, eliminates contention and simplifies batch management.
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
         * This method ensures a generator task always has a valid batch to write to. It performs a fast
         * {@code null} check and, if needed, calls the blocking {@code getNewBatchBlocking()} method to
         * retrieve a recycled batch instance.
         * </p>
         * 
         * @return The current {@code WorkBatch}, or {@code null} if interrupted while obtaining a batch.
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
         * Retrieves a new {@link WorkBatch} from the {@link CombinationQueueArray#getWorkBatchPool() global
         * pool}, spinning until one is available.
         *
         * <p>
         * When a {@link TestClickCombination monkey} finishes with a {@code WorkBatch}, it returns it to a
         * central pool for recycling. This method retrieves a batch from that pool. It uses a non-blocking,
         * busy-wait (spin) loop with a {@link org.jctools.queues.MpmcArrayQueue#relaxedPoll()
         * relaxedPoll()} to ensure the generator thread remains responsive to cancellation signals from the
         * {@link ForkJoinPool}, which a standard {@link Thread#onSpinWait()} call would prevent. The loop
         * also checks for interruption, allowing for a graceful shutdown when the pool is shutting down.
         * </p>
         *
         * <p>
         * The loop now properly checks for thread interruption, allowing the {@link ForkJoinPool} to
         * shutdown generator threads when a solution is found. If the thread is interrupted during the spin
         * loop, the method returns {@code null} to signal shutdown.
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
        private WorkBatch getNewBatchBlocking() {
            WorkBatch batch;
            while ((batch = queueArray.getWorkBatchPool().relaxedPoll()) == null) {
                // Check for thread interruption to allow proper shutdown when solution is found
                if (Thread.currentThread().isInterrupted()) {
                    return null; // Exit gracefully when interrupted
                }
                // NOTE: Thread.onSpinWait() can not be used here since it doesn't respond to cancellation.
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
         * {@link CombinationGeneratorTask#flushBatchFast(WorkBatch) flushed}. It discards the reference to
         * the old batch and retrieves a new, clean one, ensuring the generator thread can immediately start
         * filling it.
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
     * @see #CombinationGeneratorTask(int, CombinationQueueArray, short[], int)
     * @see #compute()
     * @since 2025.06 - Fork Join Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private static int numClicks;
    /**
     * The {@link CombinationQueueArray array of queues} used to distribute {@link WorkBatch} objects to
     * {@link TestClickCombination monkeys}.
     * 
     * <p>
     * To balance the workload, generators randomly select a {@link CombinationQueue queue} from this
     * array when {@link #flushBatchFast(WorkBatch) flushing} a batch. This {@code static} field is
     * initialized by the root task.
     * </p>
     * 
     * @see #CombinationGeneratorTask(int, CombinationQueueArray, short[], int)
     * @since 2025.06 - Fork Join Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 4 bytes as a reference.
     */
    private static CombinationQueueArray queueArray;
    /**
     * The maximum index allowed for the first click in any combination.
     * 
     * <p>
     * This is a simple but effective pruning optimization. Since combinations are generated in
     * lexicographical order, we know that the first initially {@code true} cell must be toggled by one
     * of the clicks in the combination. This value is pre-calculated as the highest-indexed cell
     * adjacent to the first {@code true} cell, effectively pruning any combinations that start with a
     * click beyond this point.
     * </p>
     * 
     * @see #compute()
     * @see Grid#findFirstTrueAdjacents(com.github.mrgarbagegamer.Grid.ValueFormat)
     * @since 2025.06 - First Click Optimization
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private static int maxFirstClickIndex;

    // Cached data between tasks
    /**
     * The sequence of clicks made so far, forming the base for sub-tasks or final combinations.
     * 
     * <p>
     * Each task is defined by its {@code prefix}. For an intermediate task, it will
     * {@link #computeIntermediateSubtasks(GeneratorContext) fork new tasks} by appending a new click to
     * this {@code prefix}. For a leaf task, it will {@link #computeLeafCombinations(GeneratorContext)
     * generate final combinations} by appending a final click. The {@code short[]} holding the
     * {@code prefix} is obtained from a pre-allocated {@link ArrayPool} to prevent heap allocation. To
     * prevent array resizing, we always allocate arrays of size {@link #numClicks}, tracking the
     * current length of the {@code prefix} with {@link #prefixLength}.
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
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Fixed memory footprint of ~{@code numClicks * 2} bytes per task, using pooling to avoid
     *         allocations.
     */
    private short[] prefix;
    /**
     * The current length of the {@link #prefix} array.
     * 
     * <p>
     * This value determines the task's position in the generation tree (root, intermediate, or
     * leaf) and is used as an index for appending the next click, since {@code prefix} arrays are always
     * sized to {@link #numClicks}.
     * </p>
     * 
     * @see #compute()
     * @see #prefix
     * @since 2025.06 - Fork Join Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    private int prefixLength;
    /**
     * A bitmask representing the set of initially {@code true} cells toggled by the current
     * {@link #prefix}.
     * 
     * <p>
     * This is a critical field for early-stage pruning. Instead of recomputing which {@code true} cells
     * are affected by a {@code prefix}, each task inherits the state from its parent and updates it
     * incrementally with a single {@code OR} operation. This state is then used in
     * {@link #canPotentiallySatisfyConstraints(int)} to determine if the current path is viable. A
     * value of {@code -1} indicates an uninitialized state, used only by the root task.
     * </p>
     * 
     * <p>
     * The use of a {@code long} limits this check to puzzles with 64 or fewer initially {@code true}
     * cells, but all puzzles simulated in this project fall under this limit.
     * </p>
     * 
     * @see #skipConstraintsCheck
     * @see #TRUE_CELL_ADJACENCY_MASKS
     * @see #canPotentiallySatisfyConstraints(int)
     * @see #ensureTrueCellMasks(short[])
     * @see #init(short[], int, long, boolean, boolean)
     * @see Grid#areAdjacent(short, short)
     * @see Grid#findTrueCells()
     * @since 2025.07 - Cached adjacency state introduction
     * @performance {@code O(prefixLength)} for initial computation, and {@code O(1)} for updates.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Minimal memory footprint of 8 bytes as a {@code long}.
     */
    private long cachedAdjacencyState = -1;
    /**
     * A boolean indicating the parity of clicks affecting the first initially {@code true} cell in the
     * {@link #prefix}.
     * 
     * <p>
     * Like {@link #cachedAdjacencyState}, this value is incrementally built in the generator threads to
     * allow for {@code O(1)} checks in {@link #computeLeafCombinations(GeneratorContext) leaf tasks}.
     * </p>
     * 
     * @see #computeIntermediateSubtasksConstraintPath(GeneratorContext)
     * @see #computeIntermediateSubtasksSkipPath(GeneratorContext)
     * @see #init(short[], int, long, boolean, boolean)
     * @since 2025.10 - Prefix Parity Pre-computation
     * @performance {@code O(1)} for updates during task initialization, which avoids a previously
     *              {@code O(prefixLength)} operation in the leaf task path.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Minimal memory footprint of 1 byte as a {@code boolean}.
     */
    private boolean prefixParity;
    /**
     * A flag indicating that this task and all its descendants are guaranteed to satisfy the
     * constraints, allowing future checks to be skipped.
     * 
     * <p>
     * This flag is set to {@code true} by {@link #canPotentiallySatisfyConstraints(int)} when a
     * {@link #prefix} is found to toggle every initially {@code true} cell. Because additional clicks
     * cannot "un-satisfy" this condition, all child tasks spawned from this point can inherit this flag
     * and bypass the expensive constraint check. This enables a much faster, branch-free generation
     * path in {@link #computeIntermediateSubtasksSkipPath(GeneratorContext)}.
     * </p>
     * 
     * @see #cachedAdjacencyState
     * @see #computeIntermediateSubtasks(GeneratorContext)
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(1)} for checks and updates.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Minimal memory footprint of 1 byte as a {@code boolean}.
     */
    private boolean skipConstraintsCheck = false;

    /**
     * A bitmask where each bit corresponds to an initially {@code true} cell, and all bits are set to
     * 1.
     * 
     * <p>
     * This mask represents the goal state for the {@link #canPotentiallySatisfyConstraints(int)} check.
     * A {@link #prefix} is considered to have directly satisfied the constraints when its
     * {@link #cachedAdjacencyState} is equal to this {@code targetMask}. It is computed once by the
     * root task and shared {@code static}ally to avoid re-computation in the hot path.
     * 
     * @see #SUFFIX_OR_MASKS
     * @see #ensureTrueCellMasks(short[])
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(trueCells.length)} for initial computation, and {@code O(1)} for checks.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Minimal memory footprint of 8 bytes as a {@code long}.
     */
    private static long targetMask;
    
    /**
     * The {@link ForkJoinPool} that executes the generator tasks.
     * 
     * <p>
     * A {@code static} reference to the pool is maintained so that a {@link TestClickCombination}
     * thread can signal a global shutdown by calling {@link ForkJoinPool#shutdownNow()} on it as soon
     * as a solution is found. This is the primary mechanism for terminating the search.
     * </p>
     * 
     * @see TestClickCombination#triggerGeneratorShutdown()
     * @since 2025.07 - Self-Cleanup and Cancellation Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to the use of a {@code static volatile} field.
     * @memory Minimal memory footprint of 4 bytes as a reference.
     */
    private static volatile ForkJoinPool generatorPool;

    /**
     * Sets the {@link ForkJoinPool} used to execute generator tasks.
     * 
     * <p>
     * This method is used to set the static {@link #generatorPool pool} field, allowing
     * {@link TestClickCombination monkeys} to signal cancellation when they find a solution. The pool
     * is best set once at the start of generation and left constant thereafter, as changing it
     * mid-generation could lead to inconsistent behavior.
     * </p>
     * 
     * @param pool the {@link ForkJoinPool} to use for executing generator tasks.
     * @see TestClickCombination#triggerGeneratorShutdown()
     * @since 2025.07.22 - Self-Cleanup and Cancellation Refactor
     * @performance {@code O(1)} assignment.
     * @threading Thread-safe due to the use of a {@code static volatile} field, though this method
     *            should be called by one thread only.
     * @memory Does not allocate.
     */
    public static void setForkJoinPool(ForkJoinPool pool) {
        CombinationGeneratorTask.generatorPool = pool;
    }

    /**
     * Gets the {@link ForkJoinPool} used to execute generator tasks.
     * 
     * <p>
     * This method provides access to the static {@link #generatorPool pool} field, allowing
     * {@link TestClickCombination monkeys} to signal cancellation when they find a solution.
     * </p>
     * 
     * @return the {@link ForkJoinPool} used for executing generator tasks.
     * @see TestClickCombination#triggerGeneratorShutdown()
     * @since 2025.07.22 - Self-Cleanup and Cancellation Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to the use of a {@code static volatile} field.
     * @memory Does not allocate.
     */
    public static ForkJoinPool getForkJoinPool() {
        return generatorPool;
    }

    // Root task constructor
    /**
     * Constructs the root task for combination generation.
     * 
     * <p>
     * This special constructor is called only once per puzzle run. It is responsible for initializing
     * all {@code static} fields that are shared across all generator tasks, including
     * {@link #numClicks}, {@link #queueArray}, and {@link #maxFirstClickIndex}. It also performs
     * critical one-time pre-computations, such as building the various bitmasks ({@link #targetMask},
     * {@link #TRUE_CELL_ADJACENCY_MASKS}, etc.) that are essential for performance.
     * </p>
     * 
     * @param numClicks          The target number of clicks per combination.
     * @param queueArray         The {@link CombinationQueueArray array of queues} for distributing
     *                           work.
     * @param trueCells          A {@code short[]} of the initially {@code true} cell indices.
     * @param maxFirstClickIndex The pre-calculated maximum index for the first click.
     * @throws IllegalArgumentException if parameters are invalid.
     * @since 2025.06 - Fork Join Refactor
     * @performance {@code O(trueCells.length)} for initialization, amortized over the entire generation
     *              process.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Does not allocate unless pools are empty (except for the root task).
     */
    public CombinationGeneratorTask(int numClicks, CombinationQueueArray queueArray, short[] trueCells,
            int maxFirstClickIndex) {
        // Check for valid inputs
        if (numClicks <= 0 || numClicks > Grid.NUM_CELLS) {
            throw new IllegalArgumentException("Invalid number of clicks: " + numClicks);
        }

        if (queueArray == null) {
            throw new IllegalArgumentException("Queue array must not be null.");
        }

        if (trueCells == null || trueCells.length == 0) {
            throw new IllegalArgumentException("True cells must be initialized before generating combinations.");
        }

        if (maxFirstClickIndex < 0 || maxFirstClickIndex >= Grid.NUM_CELLS) {
            throw new IllegalArgumentException("Invalid max first click index: " + maxFirstClickIndex);
        }

        // Set static fields
        CombinationGeneratorTask.numClicks = numClicks;
        CombinationGeneratorTask.queueArray = queueArray;
        CombinationGeneratorTask.maxFirstClickIndex = maxFirstClickIndex;
        ArrayPool.setNumClicks(numClicks); // Set the number of clicks for the array pool

        
        // Initialize the instance fields
        this.prefix = context.get().prefixArrayPool.get(); // FIX: Get the initial empty prefix from the thread-local context to avoid allocation.
        if (this.prefix == null) {
            this.prefix = new short[numClicks]; // Safeguard if pool is empty (though it shouldn't be)
        }
        this.prefixLength = 0;
        this.cachedAdjacencyState = -1; // Root task starts with no cached state

        // OPTIMIZATION: Pre-compute the target mask for the root task
        targetMask = (1L << trueCells.length) - 1; // All true

        // OPTIMIZATION: Pre-compute the true cell adjacency masks and suffix OR masks for constraint checks
        ensureTrueCellMasks(trueCells);

        // OPTIMIZATION: Pre-compute and cache the first true cell mask once per puzzle
        short firstTrueCell = trueCells[0];
        computeAdjacencyMaskFast(firstTrueCell);
    }

    /**
     * A bitmask representing the cells adjacent to the first initially {@code true} cell.
     * 
     * <p>
     * This mask is used in a lightweight, leaf-level pruning check inside
     * {@link #computeLeafCombinations(GeneratorContext)}. The check ensures that the first {@code true}
     * cell is toggled an odd number of times, which is a requirement for a valid solution. It is
     * {@link #computeAdjacencyMaskFast(short) pre-computed} once by the
     * {@link #CombinationGeneratorTask(int, CombinationQueueArray, short[], int) root task's
     * constructor}.
     * </p>
     * 
     * <p>
     * A {@code long[2]} array is used to represent the bitmask for all {@value Grid#NUM_CELLS} cells,
     * as a single {@code long} is insufficient. This allows for fast, cache-friendly bitwise operations
     * at the cost of requiring two checks per cell in the leaf task loop.
     * </p>
     * 
     * @see Grid#findAdjacents(short)
     * @see Grid#findFirstTrueCell(Grid.ValueFormat)
     * @see Grid.ValueFormat#Bitmask
     * @since 2025.06 - Revamped Odd Adjacency Check
     * @performance {@code O(Grid.findFirstTrueAdjacents().length)} for initial computation, and
     *              {@code O(1)} for checks.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Fixed memory footprint of ~16 bytes as two {@code long}s.
     */
    private static long[] FIRST_TRUE_ADJACENTS;
    /**
     * An array of {@link #computeAdjacencyMaskFast(short) pre-computed} indices of all clicks with odd
     * {@link #prefixParity parity}.
     *
     * @see Grid#findAdjacents(short)
     * @see Grid#findFirstTrueCell(Grid.ValueFormat)
     * @since 2025.10 - Pre-computed Parity Masks
     * @performance {@code O(ODD_CLICK_INDICES.length)} for iteration in leaf tasks.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Fixed memory footprint proportional to the number of odd parity clicks.
     */
    private static short[] ODD_CLICK_INDICES;
    /**
     * An array of {@link #computeAdjacencyMaskFast(short) pre-computed} indices of all clicks with even
     * {@link #prefixParity parity}.
     *
     * @see Grid#findAdjacents(short)
     * @see Grid#findFirstTrueCell(Grid.ValueFormat)
     * @since 2025.10 - Pre-computed Parity Masks
     * @performance {@code O(EVEN_CLICK_INDICES.length)} for iteration in leaf tasks.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Fixed memory footprint proportional to the number of even parity clicks.
     */
    private static short[] EVEN_CLICK_INDICES;
    
    /**
     * Computes and caches bitmasks and index arrays for parity checks.
     *
     * <p>
     * This is a one-time operation performed by the root task. It computes the
     * {@link #FIRST_TRUE_ADJACENTS} bitmask and then uses it to classify all
     * {@value Grid#NUM_CELLS} possible clicks into two groups: those with "odd" parity (they are
     * adjacent to the first {@code true} cell) and those with "even" parity (they are not).
     * </p>
     *
     * <p>
     * The indices of these clicks are stored in the {@link #ODD_CLICK_INDICES} and
     * {@link #EVEN_CLICK_INDICES} arrays. This pre-computation allows the
     * {@link #computeLeafCombinations(GeneratorContext) leaf generation} to iterate over a much
     * smaller, pre-filtered set of valid final clicks, significantly improving performance.
     * </p>
     *
     * @param firstTrueCell The index of the first true cell.
     * @see #CombinationGeneratorTask(int, CombinationQueueArray, short[], int)
     * @see Grid.ValueFormat#Bitmask
     * @since 2025.06 - Bitmasked Leaf Pruning Introduction
     * @performance {@code O(Grid.NUM_CELLS)} for initial computation.
     * @threading Single-threaded during root task initialization, no synchronization needed.
     * @memory Allocates fixed-size arrays for masks and indices.
     */
    private static void computeAdjacencyMaskFast(short firstTrueCell) {
        short[] adjacents = Grid.findAdjacents(firstTrueCell);
        long[] adjMask = new long[2];
        for (short adj : adjacents)
        {
            adjMask[adj >>> 6] |= (1L << (adj & 63));
        }
        FIRST_TRUE_ADJACENTS = adjMask;
    
        // Invert the logic: Instead of checking parity in the hot path, pre-calculate which clicks are
        // odd or even and iterate over a pre-filtered set.
        short[] oddIndices = new short[Grid.NUM_CELLS];
        short[] evenIndices = new short[Grid.NUM_CELLS];
        int oddCount = 0;
        int evenCount = 0;
    
        for (short i = 0; i < Grid.NUM_CELLS; i++)
        {
            // A click has "odd" parity if it is adjacent to the first true cell.
            boolean isOdd = (adjMask[i >>> 6] & (1L << (i & 63))) != 0;
            if (isOdd)
            {
                oddIndices[oddCount++] = i;
            }
            else
            {
                evenIndices[evenCount++] = i;
            }
        }
    
        // Trim the index arrays to their actual size to save memory.
        ODD_CLICK_INDICES = new short[oddCount];
        System.arraycopy(oddIndices, 0, ODD_CLICK_INDICES, 0, oddCount);
    
        EVEN_CLICK_INDICES = new short[evenCount];
        System.arraycopy(evenIndices, 0, EVEN_CLICK_INDICES, 0, evenCount);
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
     * This method acts as a dispatcher, determining the task's role based on its {@link #prefixLength}
     * and delegating to the appropriate computation path:
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
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Does not allocate; uses pooled resources.
     */
    @Override
    protected void compute() {
        // Single ThreadLocal access per task - pass context down to all methods
        final GeneratorContext ctx = context.get();
        
        try
        {
            // Path for the root task
            if (prefixLength == 0) computeRootSubtasks(ctx);

            // Path for leaf tasks
            else if (prefixLength == numClicks - 1)
            {
                computeLeafCombinations(ctx);
            }

            else
            {
                // Unified path for all intermediate (non-leaf, non-root) tasks
                computeIntermediateSubtasks(ctx);
            }
        }
        finally
        {
            // Self-cleanup: recycle our own resources
            recycleOwnResources(ctx);
        }
    }

    /**
     * Computes and forks the first level of subtasks from the root task.
     * 
     * <p>
     * This method iterates through all valid first clicks, creating and forking a new subtask for each
     * one. The range of iteration is limited by {@link #maxFirstClickIndex} as a pruning optimization.
     * </p>
     * 
     * <h3>The {@code helpQuiesce()} Workaround</h3>
     * <p>
     * After forking all subtasks, this method calls {@link #helpQuiesce()}. This is a critical
     * workaround for a surprising behavior in {@link ForkJoinPool}:
     * {@link ForkJoinPool#awaitQuiescence(long, java.util.concurrent.TimeUnit)} does not wait for tasks
     * forked by the task it was invoked on. If we didn't block here, the main thread would un-park
     * prematurely, long before the search is complete.
     * </p>
     * 
     * <p>
     * Using {@code helpQuiesce()} forces the root task (and by extension, the main thread that
     * {@link ForkJoinPool#invoke(ForkJoinTask) invoked} it) to participate in work-stealing until all
     * its descendant tasks are complete. It's a strange quirk, but this is the correct way to ensure
     * the entire computation tree finishes before the program exits.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext}.
     * @see #recycleOwnResources(GeneratorContext)
     * @see StartYourMonkeys#main(String[])
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(N)} where {@code N} is the number of valid first clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Does not allocate unless pools are empty.
     */
    private void computeRootSubtasks(GeneratorContext ctx) {
        final short start = 0;
        final short max = (short) (Math.min(Grid.NUM_CELLS - numClicks, maxFirstClickIndex) + 1);

        for (short i = start; i < max; i++)
        {
            // Use context pools directly - no more ThreadLocal calls
            short[] newPrefix = ctx.prefixArrayPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // Get recycled task from context pool
            CombinationGeneratorTask subtask = ctx.taskPool.get();

            // Identify the parity of this root subtask:
            final long maskValue = i < 64 ? FIRST_TRUE_ADJACENTS[0] : FIRST_TRUE_ADJACENTS[1];
            final boolean parity = (maskValue & (1L << (i & 63))) != 0;
            
            // Fix: Pass TRUE_CELL_ADJACENCY_MASKS[i] instead of 0L for correct initial state
            subtask.init(newPrefix, prefixLength + 1, TRUE_CELL_ADJACENCY_MASKS[i], false, parity);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
        
        helpQuiesce(); // Wait for all subtasks to complete before returning
        // This will ensure that the root task does not exit prematurely, keeping the main thread parked
    }
    
    /**
     * Initializes a recycled task with a new {@link #prefix} and state.
     * 
     * <p>
     * This method is central to the object pooling strategy. Instead of creating a new task, we recycle
     * an existing one from the {@link TaskPool} and re-initialize it with a new {@code prefix} and
     * state. It assigns the given parameters and calls {@link #reinitialize()} to reset the
     * {@link java.util.concurrent.ForkJoinTask ForkJoinTask} state, making the task ready for
     * re-submission.
     * </p>
     * 
     * <p>
     * This method was intentionally designed as a single, monomorphic call site. Previous versions had
     * multiple {@code init} overloads, which hindered JIT compiler optimizations. By consolidating them
     * into one method, we improve performance and allow for greater JIT optimizations, at the small
     * cost of the occasional unnecessary assignment.
     * </p>
     * 
     * @param prefix               The prefix {@code short[]} for the new task.
     * @param prefixLength         The length of the {@code prefix}.
     * @param parentAdjacencyState The {@link #cachedAdjacencyState} from the parent task.
     * @param skipConstraints      A flag indicating if constraint checks can be skipped.
     * @param prefixParity         The parity of the {@code prefix} affecting the first {@code true}
     *                             cell.
     * @since 2025.07 - Task Pool Introduction
     * @performance {@code O(1)} for assignments and reinitialization.
     * @threading Not thread-safe; must be called by only one thread at a time on a given task.
     * @memory Does not allocate; uses pooled resources.
     */
    public void init(short[] prefix, int prefixLength, long parentAdjacencyState, boolean skipConstraints,
            boolean prefixParity) {
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.cachedAdjacencyState = parentAdjacencyState;
        this.skipConstraintsCheck = skipConstraints;
        this.prefixParity = prefixParity;
        reinitialize();
    }

    // LEAF TASK PATH:
    /**
     * Generates and bulk-adds the final combinations for a leaf task.
     *
     * <p>
     * This is the final, non-recursive step in the generation process and a critical hot path. It
     * efficiently generates all valid final combinations by building on the task's {@link #prefix} and
     * bulk-adding them to the current {@link WorkBatch}.
     * </p>
     *
     * <h3>Algorithm and Optimizations</h3>
     * <p>
     * The method incorporates three key optimizations to maximize throughput:
     * <ol>
     * <li><b>Pre-filtered Clicks:</b> It selects a pre-filtered array of valid final clicks
     * ({@link #ODD_CLICK_INDICES} or {@link #EVEN_CLICK_INDICES}) based on the prefix's
     * {@link #prefixParity parity}, ensuring the first-true-cell constraint is met without runtime
     * checks.</li>
     * <li><b>Binary Search:</b> It uses {@link Arrays#binarySearch(short[], short)} to find the
     * starting index of valid final clicks in {@code O(log N)} time, avoiding a linear scan.</li>
     * <li><b>Bulk Batching:</b> Instead of adding combinations one by one, it calculates how many can
     * fit in the current batch and uses the high-performance
     * {@link WorkBatch#addBulk(short[], int, short[], int, int)} method to copy them in a single
     * operation.</li>
     * </ol>
     * This bulk-processing approach dramatically reduces method call overhead and loop-related costs,
     * making it significantly more efficient than an iterative approach.
     * </p>
     *
     * @param ctx The thread-local {@link GeneratorContext}.
     * @see #ODD_CLICK_INDICES
     * @see #EVEN_CLICK_INDICES
     * @see #flushBatchFast(WorkBatch)
     * @see GeneratorContext#getOrCreateBatch()
     * @since 2025.07 - Splitting the Compute Method Into Paths
     * @performance {@code O(log N)} binary search, where {@code N} is the number of valid final clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @algorithm Uses binary search to find the start index in a pre-filtered array of clicks, then
     *            adds combinations to the {@code WorkBatch} in bulk.
     * @memory Does not allocate; uses pooled resources and bulk-copies into the pre-allocated
     *         {@code WorkBatch} buffer.
     */
    private final void computeLeafCombinations(GeneratorContext ctx) {
        // ULTRA-OPTIMIZED: Use binary search and bulk-copying to avoid per-element overhead.
        final int pLen = prefixLength;
        final short lastPrefixClick = prefix[pLen - 1];

        // Select the correct pre-filtered index array based on the prefix's parity.
        final short[] validClicks = this.prefixParity ? EVEN_CLICK_INDICES : ODD_CLICK_INDICES;
        
        // 1. Find the starting index using binary search.
        int startIdx = Arrays.binarySearch(validClicks, (short) (lastPrefixClick + 1));
        if (startIdx < 0) {
            startIdx = -startIdx - 1; // If not found, binarySearch returns (-(insertion point) - 1)
        }

        WorkBatch batch = ctx.getOrCreateBatch();
        if (batch == null) return; // Exit if interrupted

        int remainingInSource = validClicks.length - startIdx;
        int currentOffset = startIdx;

        // 2. Bulk-add combinations in a loop until all are processed.
        while (remainingInSource > 0) {
            int spaceInBatch = batch.remainingCapacity();
            int toAdd = Math.min(remainingInSource, spaceInBatch);

            if (toAdd > 0) { // Potential deoptimization, but necessary safeguard.
                int added = batch.addBulk(prefix, pLen, validClicks, currentOffset, toAdd);
                currentOffset += added;
                remainingInSource -= added;
            }

            // If batch is full, flush and get a new one.
            if (remainingInSource > 0 && flushBatchFast(batch)) {
                batch = ctx.resetBatch();
                if (batch == null) return; // Exit if interrupted
            }
            else return; // Interrupted or done.
        }
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
     * A key performance goal is to avoid branching inside the hot loops where subtasks are forked. If a
     * task's {@link #prefix} is already known to satisfy the {@link #canPotentiallySatisfyConstraints
     * constraint check}, all its descendants can skip that check. Instead of handling this with an
     * {@code if} statement inside a single, large method, we use two separate methods:
     * <ul>
     * <li>{@link #computeIntermediateSubtasksConstraintPath(GeneratorContext)}: The "slow" path that
     * includes the pruning check.</li>
     * <li>{@link #computeIntermediateSubtasksSkipPath(GeneratorContext)}: The "fast" path that omits
     * the check entirely.</li>
     * </ul>
     * This dispatcher creates a <strong>monomorphic call site</strong> for each path. The JIT compiler
     * can more aggressively optimize these separate, branch-free methods than it could a single method
     * containing a conditional check, leading to significant performance gains. It also simplifies the
     * code for {@link #compute()}, designating a clear input method for intermediate tasks without an
     * additional conditional.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext}.
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(1)} for dispatching.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Does not allocate.
     */
    private void computeIntermediateSubtasks(GeneratorContext ctx) {
        if (skipConstraintsCheck) {
            computeIntermediateSubtasksSkipPath(ctx);
        } else {
            computeIntermediateSubtasksConstraintPath(ctx);
        }
    }
    
    // PURE HOT PATH 1:
    /**
     * Forks subtasks for an intermediate task, skipping the constraint check.
     * 
     * <p>
     * This is the "fast path" for intermediate tasks. It is invoked when the current task's
     * {@link #prefix} has already been proven to satisfy the {@link #canPotentiallySatisfyConstraints
     * constraint check}, meaning all its descendants are also guaranteed to be on a valid path.
     * </p>
     * 
     * <p>
     * This method contains a highly optimized, branch-free loop. It iterates through all possible next
     * clicks, gets a recycled task and {@code prefix} from the pools,
     * {@link #init(short[], int, long, boolean, boolean) initializes them}, and {@link #fork() forks} them for
     * execution. Because no pruning checks are needed, the loop body is minimal and extremely friendly
     * to JIT compiler optimizations. A small safeguard exists to allocate a new array if the
     * {@link ArrayPool} is exhausted, though this is not expected in normal operation and could be
     * removed for a slight performance gain at the cost of robustness.
     * 
     * @param ctx The thread-local {@link GeneratorContext} containing the resource pools.
     * @see #skipConstraintsCheck
     * @see #computeIntermediateSubtasksConstraintPath(GeneratorContext)
     * @since 2025.08 - Specialized Subtask Paths
     * @performance {@code O(Grid.NUM_CELLS - numClicks + prefixLength - prefix[prefixLength - 1] + 1)}
     *              for iterating over possible next clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Does not allocate unless pools are empty; uses pooled resources.
     */
    private void computeIntermediateSubtasksSkipPath(GeneratorContext ctx) {
        final short start = (short) (prefix[prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (numClicks - prefixLength) + 1);
        final ArrayPool prefixPool = ctx.prefixArrayPool;
        final TaskPool taskPool = ctx.taskPool;
        final boolean prefixParity = this.prefixParity; // Cache field read
        final long mask0 = FIRST_TRUE_ADJACENTS[0]; // Cache array elements to avoid repeated dereferences
        final long mask1 = FIRST_TRUE_ADJACENTS[1];
        
        // Pure loop - no constraint checking, no mask loading, no conditionals
        for (short i = start; i < max; i++)
        {
            short[] newPrefix = prefixPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;
            
            // Determine the parity of the new prefix based on the new click
            final long maskValue = (i < 64) ? mask0 : mask1;
            final boolean iAdj = (maskValue & (1L << (i & 63))) != 0;
            final boolean newPrefixParity = prefixParity ^ iAdj; // Update parity

            // All parameters are constants - perfect for JIT optimization
            CombinationGeneratorTask subtask = taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, -1L, true, newPrefixParity);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
    }
    
    // PURE HOT PATH 2:
    /**
     * Forks subtasks for an intermediate task, performing a constraint check to prune invalid branches.
     * 
     * <p>
     * This is the "slow path" for intermediate tasks. It is invoked when the current task's
     * {@link #prefix} has not yet been proven to satisfy all constraints. Before forking subtasks, it
     * performs a critical pruning step by calling {@link #canPotentiallySatisfyConstraints(int)}. If
     * that check determines that no possible descendant of this task can form a valid solution, the
     * entire branch is pruned, saving a massive amount of wasted computation.
     * </p>
     * 
     * <p>
     * If the path is viable, the method proceeds to fork subtasks in a tight loop. For each new
     * subtask, it incrementally updates the {@link #cachedAdjacencyState} with a single {@code OR}
     * operation and passes it down. It also propagates the {@link #skipConstraintsCheck} flag, which
     * may have been set to {@code true} inside {@code canPotentiallySatisfyConstraints}, allowing all
     * subsequent children to use the "fast path" from this point forward. To keep the loop free of
     * conditionals, the adjacency state is calculated even if the check passes and sets the skip flag,
     * which is a minor trade-off for a more JIT-friendly method body. A similar safeguard for the
     * {@link ArrayPool} also exists here.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext} containing the resource pools.
     * @see #computeIntermediateSubtasksSkipPath(GeneratorContext)
     * @see #ensureTrueCellMasks(short[])
     * @since 2025.08 - Specialized Subtask Paths
     * @performance {@code O(1)} for the early constraint check,
     *              <code>O({@link Grid#NUM_CELLS} - {@link #numClicks} + {@link #prefixLength} -
     *              {@link #prefix}[prefixLength - 1] + 1)</code> for iterating over possible next
     *              clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Does not allocate unless pools are empty; uses pooled resources.
     */
    private void computeIntermediateSubtasksConstraintPath(GeneratorContext ctx) {
        final short start = (short) (prefix[prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (numClicks - prefixLength) + 1);
        
        // Early constraint check - happens ONCE per task, not per iteration
        if (prefixLength >= 2 && !canPotentiallySatisfyConstraints(start))
        {
            return; // Skip this entire branch if constraints cannot be satisfied
        }
        
        // Pre-compute all loop-invariant values and cache array references
        final long currentAdjacencyState = this.cachedAdjacencyState;
        final ArrayPool prefixPool = ctx.prefixArrayPool;
        final TaskPool taskPool = ctx.taskPool;
        final long[] masks = TRUE_CELL_ADJACENCY_MASKS;
        final boolean skipConstraints = this.skipConstraintsCheck; // Cache field read
        final boolean prefixParity = this.prefixParity; // Cache field read
        final long mask0 = FIRST_TRUE_ADJACENTS[0]; // Cache array elements to avoid repeated dereferences
        final long mask1 = FIRST_TRUE_ADJACENTS[1];
        
        // Pure loop - no conditionals inside, all branching resolved outside loop
        for (short i = start; i < max; i++) // loops from prefix[prefixLength - 1] + 1 to Grid.NUM_CELLS - (numClicks - prefixLength) + 1
        {
            short[] newPrefix = prefixPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // Determine the parity of the new prefix based on the new click
            final long maskValue = (i < 64) ? mask0 : mask1;
            final boolean iAdj = (maskValue & (1L << (i & 63))) != 0;
            final boolean newPrefixParity = prefixParity ^ iAdj; // Update parity

            // No conditional - pure OR calculation every time
            long childAdjacencyState = currentAdjacencyState | masks[i];
            
            // All parameters determined - perfect for JIT constant propagation
            CombinationGeneratorTask subtask = taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, childAdjacencyState, skipConstraints, newPrefixParity);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
    }
    
    /**
     * Checks if the current {@link #prefix} and its descendants can potentially form a valid solution.
     * 
     * <p>
     * This is the primary pruning mechanism in the generation tree. A valid solution must toggle every
     * initially {@code true} cell. This method uses bitmasks to determine if it's possible for the
     * current {@code prefix}, combined with any future clicks, to meet this condition. If not, the
     * entire branch of the search tree is abandoned, saving immense computation.
     * </p>
     * 
     * <h3>Algorithm</h3>
     * <p>
     * The check is performed in two stages, both using {@code O(1)} bitwise operations:
     * <ol>
     * <li><b>Direct Check:</b> It computes the {@code needed} bits by XORing the
     * {@link #cachedAdjacencyState} (which {@code true} cells are toggled by the current prefix) with
     * the {@link #targetMask} (all {@code true} cells). If {@code needed} is zero, the constraint is
     * already met. As an optimization, it sets {@link #skipConstraintsCheck} to {@code true}, allowing
     * all descendants to use a faster, check-free generation path.</li>
     * <li><b>Potential Check:</b> If the constraint is not yet met, it checks if it's still
     * <em>possible</em> to meet it. It uses a pre-computed {@link #SUFFIX_OR_MASKS} bitmask, which
     * represents all the {@code true} cells that <em>could</em> be toggled by any of the remaining
     * available clicks. If the bitwise {@code AND} of this "available" mask and the {@code needed} mask
     * equals the {@code needed} mask, it means a solution is still possible, and the path is not
     * pruned.</li>
     * </ol>
     * This allows the method to prune with certainty without needing to iterate through future clicks.
     * </p>
     * 
     * @param startIdx The starting index for the next click, used to look up the correct suffix mask.
     * @return {@code true} if this path is still viable, {@code false} if it should be pruned.
     * @see #TRUE_CELL_ADJACENCY_MASKS
     * @see Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)
     * @see Grid#findTrueCells(Grid.ValueFormat)
     * @see Grid.ValueFormat#Bitmask
     * @since 2025.07 - Splitting the Compute Method Into Paths
     * @performance {@code O(1)} due to the use of pre-computed bitmasks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation.
     * @memory Does not allocate; uses pre-computed {@code static} bitmasks.
     */
    private boolean canPotentiallySatisfyConstraints(int startIdx) {   
        // OPTIMIZATION: Call ensureTrueCellMasks once in the root task to avoid a call here.

        // cachedAdjacencyState can only be -1 for the root task (which skips this check)
        // Therefore, we can assume it is initialized here, saving a branch in our logic.
        
        // Define the variables we'll use in this check.
        final long target = targetMask;
        final long currentAdjacencies = cachedAdjacencyState;
        final long needed = currentAdjacencies ^ target; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good.
        // OPTIMIZATION: Skip future checks by setting skipConstraintsCheck to true
        if (needed == 0L) 
        {
            skipConstraintsCheck = true;  // Set flag separately to avoid side effects
            return true;
        }
        
        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        long availableAdjacencies = SUFFIX_OR_MASKS[startIdx];
        
        return (availableAdjacencies & needed) == needed;
    }

    /**
     * An array of bitmasks where each mask represents which of the initially {@code true} cells a given
     * click is adjacent to.
     * 
     * <p>
     * This is a core data structure for {@link #canPotentiallySatisfyConstraints(int) pruning}.
     * {@code TRUE_CELL_ADJACENCY_MASKS[i]} is a {@code long} where the {@code k}-th bit is {@code 1} if
     * and only if clicking cell {@code i} toggles the {@code k}-th initially {@code true} cell.
     * </p>
     * <p>
     * This allows for {@code O(1)} updates to the {@link #cachedAdjacencyState} (e.g.,
     * {@code state ^= TRUE_CELL_ADJACENCY_MASKS[click]}) instead of expensive re-computation. It is
     * initialized once per puzzle run by {@link #ensureTrueCellMasks(short[])}. The use of a
     * {@code long} limits this optimization to puzzles with 64 or fewer {@code true} cells.
     * </p>
     * 
     * @see #canPotentiallySatisfyConstraints(int)
     * @see Grid#findAdjacents(short, Grid.ValueFormat, Grid.ValueFormat)
     * @see Grid#findTrueCells(Grid.ValueFormat)
     * @since 2025.07 - Bitmasked Adjacency Checks
     * @performance {@code O(1)} for adjacency checks, {@code O(trueCells.length)} for initial
     *              computation.
     * @threading Statically initialized once by {@link #ensureTrueCellMasks(short[])}
     * @memory Fixed memory footprint of ~{@code 8 * Grid.NUM_CELLS} bytes as a {@code long} array.
     */
    private static long[] TRUE_CELL_ADJACENCY_MASKS = null;
    /**
     * An array of pre-computed "suffix OR" bitmasks for {@code O(1)} constraint checking.
     * 
     * <p>
     * This is a critical optimization for {@link #canPotentiallySatisfyConstraints(int)}. The mask at
     * index {@code i} is the bitwise {@code OR} of all {@link #TRUE_CELL_ADJACENCY_MASKS} from index
     * {@code i} to the end of the grid.
     * </p>
     * <p>
     * In effect, {@code SUFFIX_OR_MASKS[i]} represents every {@code true} cell that can possibly be
     * toggled by <em>any</em> future click from index {@code i} onwards. This allows the constraint
     * check to determine if a solution is still possible in a single {@code O(1)} bitwise operation,
     * rather than iterating through all remaining clicks (though the use of a {@code long} limits this
     * optimization to puzzles with 64 or fewer {@code true} cells). It is initialized once per puzzle
     * run by {@link #ensureTrueCellMasks(short[])}.
     * </p>
     * 
     * @since 2025.07 - Suffix OR Masks Introduction
     * @performance {@code O(1)} for constraint checks, {@code O(Grid.NUM_CELLS)} for initial
     *              computation.
     * @threading Statically initialized once by {@link #ensureTrueCellMasks(short[])} and effectively
     *            {@code final} thereafter.
     * @memory Fixed memory footprint of ~{@code 8 * Grid.NUM_CELLS} bytes as a {@code long} array.
     */
    private static long[] SUFFIX_OR_MASKS = null;
    /**
     * A static adjacency matrix used to initialize {@link #TRUE_CELL_ADJACENCY_MASKS}.
     * 
     * <p>
     * {@code CLICK_ADJACENCY_MATRIX[i][j]} is {@code true} if clicking cell {@code i} toggles cell
     * {@code j}. This is a legacy structure used during the {@link #ensureTrueCellMasks(short[])
     * one-time computation} of the bitmasks. It is less efficient than the bitmask-based checks in
     * {@link Grid} but is retained for this setup step, since it pre-dates the bitmask optimizations.
     * </p>
     * 
     * @see #initClickAdjacencyMatrix()
     * @since 2025.07 - Bitmasked Adjacency Checks
     * @performance {@code O(1)} for adjacency checks, <code>O({@value Grid#NUM_CELLS}²)</code> for
     *              initial computation.
     * @threading Statically initialized once at class load time.
     * @memory Fixed memory footprint of ~<code>{@value Grid#NUM_CELLS}²</code> bytes as a
     *         {@code boolean} matrix.
     */
    private static final boolean[][] CLICK_ADJACENCY_MATRIX = initClickAdjacencyMatrix();

    /**
     * Initializes the {@code static} {@link #CLICK_ADJACENCY_MATRIX} upon class loading.
     * 
     * <p>
     * This method performs a one-time computation, iterating through every cell in the grid and using
     * {@link Grid#findAdjacents(short, Grid.ValueFormat)} to populate the adjacency matrix. It is
     * called only once when the class is loaded.
     * </p>
     * 
     * @return A {@code boolean[Grid.NUM_CELLS][Grid.NUM_CELLS} matrix representing cell adjacencies.
     * @since 2025.07 - Bitmasked Adjacency Checks
     * @performance {@code O(Grid.NUM_CELLS²)}, due to nested iteration over cells and their adjacents.
     * @threading Thread-safe as it is called once during class loading and creates an immutable result.
     * @memory Allocates a fixed-size adjacency matrix of {@code Grid.NUM_CELLS²} bytes.
     */
    private static boolean[][] initClickAdjacencyMatrix() {
        boolean[][] matrix = new boolean[Grid.NUM_CELLS][Grid.NUM_CELLS];
        for (short i = 0; i < Grid.NUM_CELLS; i++) {
            short[] adjacents = Grid.findAdjacents(i, Grid.ValueFormat.Index);
            if (adjacents != null) {
                for (short adj : adjacents) {
                    if (adj < Grid.NUM_CELLS) matrix[i][adj] = true;
                }
            }
        }
        return matrix;
    }

    /**
     * Initializes the static {@link #TRUE_CELL_ADJACENCY_MASKS} and {@link #SUFFIX_OR_MASKS} for a
     * given puzzle.
     * 
     * <p>
     * This method performs the one-time, expensive pre-computation of the bitmasks used for pruning. It
     * is called by the root task and uses double-checked locking to ensure the computation happens only
     * once, even if multiple threads somehow invoke it.
     * </p>
     * 
     * <h3>Algorithm</h3>
     * <p>
     * The process has two main steps:
     * <ol>
     * <li><b>Adjacency Masks:</b> It iterates through every possible click cell ({@code 0} to
     * {@value Grid#NUM_CELLS}). For each one, it builds a bitmask representing which of the puzzle's
     * {@code trueCells} it is adjacent to, using the {@link #CLICK_ADJACENCY_MATRIX}. The results are
     * stored in {@link #TRUE_CELL_ADJACENCY_MASKS}.</li>
     * <li><b>Suffix OR Masks:</b> It then iterates backwards over the newly created adjacency masks to
     * compute the {@link #SUFFIX_OR_MASKS}. Each entry {@code SUFFIX_OR_MASKS[i]} is the cumulative
     * bitwise {@code OR} of all adjacency masks from {@code i} to the end.</li>
     * </ol>
     * This pre-computation is what enables subsequent {@code O(1)}
     * {@link #canPotentiallySatisfyConstraints(int) constraint checks}.
     * </p>
     * 
     * @param trueCells The {@code short[]} of initially {@code true} cell indices for the current
     *                  puzzle.
     * @see Grid.ValueFormat#Bitmask
     * @since 2025.07 - Bitmasked Adjacency Checks
     * @threading Thread-safe due to a {@code synchronized} block with double-checked locking.
     * @memory Allocates two fixed-size arrays of ~<code>{@value Grid#NUM_CELLS} * 8</code> bytes each.
     */
    private static void ensureTrueCellMasks(short[] trueCells) {
        if (TRUE_CELL_ADJACENCY_MASKS == null | SUFFIX_OR_MASKS == null)
        {
            synchronized (CombinationGeneratorTask.class) 
            {
                if (TRUE_CELL_ADJACENCY_MASKS == null) 
                {
                    long[] masks = new long[Grid.NUM_CELLS]; // Create an array to store masks for each click cell
                    
                    for (int clickCell = 0; clickCell < Grid.NUM_CELLS; clickCell++) // For each cell in the grid
                    {
                        long mask = 0L; // Create a mask with all true cells set to 0
                        for (short i = 0; i < trueCells.length; i++) // For each true cell
                        {
                            if (CLICK_ADJACENCY_MATRIX[trueCells[i]][clickCell]) // If the true cell is adjacent to the click cell
                            {
                                mask |= (1L << i); // Add this true cell to the mask by OR-ing with the bit at position i
                            }
                        }
                        masks[clickCell] = mask; // Store the mask for this click cell in the long array.
                    }
                    
                    TRUE_CELL_ADJACENCY_MASKS = masks; // Assign the masks to the static field
                }
                if (SUFFIX_OR_MASKS == null) 
                {
                    // NEW: Pre-compute the suffix OR masks after the main masks are ready
                    long[] suffixMasks = new long[Grid.NUM_CELLS + 1]; // +1 for sentinel
                    for (int i = Grid.NUM_CELLS - 1; i >= 0; i--)
                    {
                        suffixMasks[i] = suffixMasks[i + 1] | TRUE_CELL_ADJACENCY_MASKS[i];
                    }
                    SUFFIX_OR_MASKS = suffixMasks;
                }
            }
        }
    }

    /**
     * Flushes a {@link WorkBatch batch} of combinations to an available {@link CombinationQueue queue},
     * employing a backoff strategy if all queues are temporarily full.
     * 
     * <p>
     * This method is a critical component of the producer-consumer architecture, ensuring that
     * generated combinations are efficiently transferred to {@link TestClickCombination monkeys} for
     * validation. It implements a robust mechanism to handle queue contention and backpressure without
     * dropping any combinations.
     * </p>
     * 
     * <h3>Algorithm</h3>
     * <p>
     * The method attempts to add the {@code WorkBatch} to a {@link ThreadLocalRandom#nextInt(int)
     * randomly selected queue} from the {@link #queueArray}. To minimize contention, it tries each
     * queue once in a round-robin fashion. If all queues are full, the thread
     * {@link Thread#sleep(long, int) sleeps} briefly (0.5ms) to avoid busy-waiting, then retries. This
     * loop continues indefinitely until the batch is successfully enqueued or the thread is
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
     * sleeps. This could, however, increase complexity, potentially requiring a shift to a different
     * queue structure.</li>
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
        CombinationQueue[] queues = queueArray.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);
        
        // Try each queue once and sleep if all are full
        while (true)
        {
            for (int i = 0; i < queues.length; i++) 
            {
                if (queues[(startIdx + i) % queues.length].add(batch)) return true;
            }

            // If we reach here, all queues were full
            try 
            {
                Thread.sleep(0, 500_000); // Sleep briefly (0.5ms) to avoid busy-waiting
            } catch (InterruptedException e) 
            {
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
     * {@link #compute()} method. It is a fundamental part of the object pooling strategy, preventing
     * excessive heap allocations and subsequent garbage collection overhead during the high-volume
     * generation process.
     * </p>
     * 
     * <p>
     * By returning objects to pools instead of allowing them to be garbage collected, the solver
     * maintains a low memory footprint and consistent performance, avoiding "stop-the-world" pauses.
     * </p>
     * 
     * @param ctx The thread-local {@link GeneratorContext} containing the {@link ArrayPool} and
     *            {@link TaskPool}.
     * @see #context
     * @see ArrayPool
     * @see ArrayPool#put(short[])
     * @see TaskPool
     * @see TaskPool#put(CombinationGeneratorTask)
     * @since 2025.07 - Task Self-Recycling
     * @performance {@code O(1)} for returning resources.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask} isolation
     *            and thread-local {@code GeneratorContext}.
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
     * purpose is to ensure that any partially filled {@code WorkBatch}es, which might not have reached
     * {@link WorkBatch#BATCH_SIZE} and thus weren't flushed during normal operation, are nonetheless
     * processed.
     * </p>
     * 
     * <p>
     * It iterates through all known {@code GeneratorContext} instances (tracked in
     * {@link #allContexts}), checks if they have a {@link WorkBatch#isEmpty() non-empty} current batch,
     * and {@link #flushBatchBlocking(WorkBatch) flushes it} using a blocking strategy to guarantee
     * delivery. It also logs the start and completion of the flush process for transparency.
     * </p>
     * 
     * @see #context
     * @since 2025.06 - Flush Batches when Full (or on Completion)
     * @performance {@code O(numQueues)} for the flushing operation.
     * @threading Synchronized to prevent concurrent invocations.
     * @memory Does not allocate, apart from some logging overhead.
     */
    public static synchronized void flushAllPendingBatches() {
        logger.info("Starting final, single-threaded flush of all pending batches from {} contexts...", Unbox.box(allContexts.size()));
        
        for (GeneratorContext ctx : allContexts) {
            WorkBatch batch = ctx.currentBatch;
            if (batch != null && !batch.isEmpty()) {
                logger.debug("Flushing final batch of size {} from {}.", Unbox.box(batch.size()), ctx.getThreadName());
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
     * This method is similar to {@link #flushBatchFast(WorkBatch)}, but it employs a blocking strategy
     * to ensure that the batch is eventually flushed, even if all queues are temporarily full. It is
     * used during the final flush of pending batches to guarantee that no combinations are lost.
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
        CombinationQueue[] queues = queueArray.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);
        
        while (true) {
            for (int i = 0; i < queues.length; i++) {
                if (queues[(startIdx + i) % queues.length].add(batch)) return;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

}
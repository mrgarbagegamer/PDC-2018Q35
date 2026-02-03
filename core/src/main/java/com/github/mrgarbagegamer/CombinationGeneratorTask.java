package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

import it.unimi.dsi.fastutil.longs.LongList;

// TODO: Update Javadoc
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
 * {@link #prefix} of clicks. The root task {@link #computeRootSubtasks(DefaultGeneratorContext)
 * forks subtasks} for each possible first click, and these subtasks recursively
 * {@link #computeIntermediateSubtasks(DefaultGeneratorContext) fork children} until the desired
 * {@link #numClicks combination length} is reached. To optimize performance, this class implements
 * two key strategies:
 * <ul>
 * <li><b>Constraint Pruning:</b> At each branching point, {@link #constraintCheck(int)} uses
 * bitmasks to check if a path can possibly lead to a valid solution, pruning entire branches
 * early.</li>
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
 * {@link ThreadLocal thread-local} pools managed by the {@link DefaultGeneratorContext
 * GeneratorContext}. This design reduces heap allocations to nearly zero during the main generation
 * loop.
 * </p>
 * 
 * @see java.util.concurrent.ForkJoinTask
 * @since 2025.06 - Fork Join Refactor
 * @performance The theoretical complexity is {@code O(C(Grid.NUM_CELLS, numClicks))}. However,
 *              aggressive bitmask-based pruning and parallel execution significantly reduce the
 *              practical workload.
 * @threading Tasks are isolated by the {@code ForkJoinTask} framework. Shared resources are managed
 *            via a {@link GeneratorWorkerThread#context thread-local}
 *            {@link DefaultGeneratorContext GeneratorContext} to ensure thread safety and eliminate
 *            contention.
 * @algorithm A recursive, divide-and-conquer approach. Tasks form a generation tree where each node
 *            is a click prefix. Subtasks are
 *            {@link #computeIntermediateSubtasks(DefaultGeneratorContext) forked} until a
 *            {@link #numClicks target length} is reached. Leaf tasks
 *            {@link #computeLeafCombinations(DefaultGeneratorContext) generate} work items, which
 *            are {@link #flushBatchFast(WorkBatch) queued} for validation.
 * @memory Object allocations are minimized through extensive use of {@link ArrayPool} and
 *         {@link TaskPool}, managed by a thread-local {@code GeneratorContext}.
 */
public class CombinationGeneratorTask extends RecursiveAction {
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
    private final CombinationQueueArray queueArray;
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
    private final int numClicks;

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
    private final int maxFirstClickIndex;

    // Cached data between tasks
    /**
     * The sequence of clicks made so far, forming the base for sub-tasks or final combinations.
     * 
     * <p>
     * Each task is defined by its {@code prefix}. For an intermediate task, it will
     * {@link #computeIntermediateSubtasks(DefaultGeneratorContext) fork new tasks} by appending a
     * new click to this {@code prefix}. For a leaf task, it will
     * {@link #computeLeafCombinations(DefaultGeneratorContext) generate final combinations} by
     * appending a final click. The {@code short[]} holding the {@code prefix} is obtained from a
     * pre-allocated {@link ArrayPool} to prevent heap allocation. To prevent array resizing, we
     * always allocate arrays of size {@code NUM_CLICKS - 1}, tracking the current length of the
     * {@code prefix} with {@link #prefixLength}.
     * </p>
     * 
     * @see #recycleOwnResources(DefaultGeneratorContext)
     * @see ArrayPool
     * @see ArrayPool#get()
     * @see ArrayPool#put(short[])
     * @see DefaultGeneratorContext
     * @see DefaultGeneratorContext#prefixArrayPool
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
     * {@link #constraintCheck(int)} to determine if the current path is viable. A value of
     * {@code -1} indicates an uninitialized state, used only by the root task.
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
     * Like {@link #currentAdjacencies}, this value is incrementally built in the generator threads
     * to allow for {@code O(1)} checks in {@link #computeLeafCombinations(DefaultGeneratorContext)
     * leaf tasks}.
     * </p>
     * 
     * @see #computeIntermediateSubtasksConstraintPath(DefaultGeneratorContext)
     * @see #computeIntermediateSubtasksSkipPath(DefaultGeneratorContext)
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
     * This flag is set to {@code true} by {@link #constraintCheck(int)} when a {@link #prefix} is
     * found to toggle every initially {@code true} cell. Because additional clicks cannot
     * "un-satisfy" this condition, all child tasks spawned from this point can inherit this flag
     * and bypass the expensive constraint check. This enables a much faster, branch-free generation
     * path in {@link #computeIntermediateSubtasksSkipPath(DefaultGeneratorContext)}.
     * </p>
     * 
     * @see #currentAdjacencies
     * @see #computeIntermediateSubtasks(DefaultGeneratorContext)
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(1)} for checks and updates.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Minimal memory footprint of 1 byte as a {@code boolean}.
     */
    private boolean skipConstraintsCheck = false;

    /**
     * A {@code static final} cache of {@link StartYourMonkeys.GlobalConfig#TRUE_CELL_MASKS}, used
     * for quick adjacency checks. Through the use of {@link StableValue StableValues} and the
     * ordering of class initializations, this array is guaranteed to be initialized before any
     * tasks are created and can be treated as a constant.
     * 
     * @since 2025.12 - GlobalConfig Refactor
     * @performance {@code O(1)} access time.
     * @threading Thread-safe due to immutability after initialization.
     * @memory Fixed memory footprint of {@code Grid.NUM_CELLS * 8} bytes as a {@code long[]} array.
     */
    private final LongList trueCellMasksLower;
    private final LongList trueCellMasksUpper;
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
    private final long expectedMaskLower;
    private final long expectedMaskUpper;
    private final boolean useDualMasks;

    public static CombinationGeneratorTask createRootTask(SolverConfiguration config,
            CombinationQueueArray queueArray) {
        final CombinationGeneratorTask rootTask = new CombinationGeneratorTask(config, queueArray);

        // Initialize instance fields
        rootTask.prefix = new short[rootTask.numClicks - 1];
        rootTask.prefixLength = 0;
        rootTask.currentAdjacenciesLower = -1;
        rootTask.currentAdjacenciesUpper = -1;

        return rootTask;
    }

    protected CombinationGeneratorTask(SolverConfiguration config,
            CombinationQueueArray queueArray) {
        this.queueArray = queueArray;
        this.numClicks = config.numClicks();
        this.maxFirstClickIndex = config.getEvenClickIndices()
                .getShort(config.getEvenClickIndices().size() - 1);
        this.trueCellMasksLower = config.getTrueCellMasksLower();
        this.trueCellMasksUpper = config.getTrueCellMasksUpper();
        this.expectedMaskLower = config.getExpectedMaskLower();
        this.expectedMaskUpper = config.getExpectedMaskUpper();
        this.suffixMasksLower = config.getSuffixMasksLower();
        this.suffixMasksUpper = config.getSuffixMasksUpper();
        this.useDualMasks = config.getUseDualMasks();
    }

    /**
     * The main computation method for the {@link RecursiveAction}.
     * 
     * <p>
     * This method acts as a dispatcher, determining the task's role based on its
     * {@link #prefixLength} and delegating to the appropriate computation path:
     * <ul>
     * <li><b>Root Task</b> ({@code prefixLength == 0}): Invokes
     * {@link #computeRootSubtasks(DefaultGeneratorContext)}.</li>
     * <li><b>Leaf Task</b> ({@code prefixLength == numClicks - 1}): Invokes
     * {@link #computeLeafCombinations(DefaultGeneratorContext)}.</li>
     * <li><b>Intermediate Task</b> (otherwise): Invokes
     * {@link #computeIntermediateSubtasks(DefaultGeneratorContext)}.</li>
     * </ul>
     * A {@code finally} block ensures that {@link #recycleOwnResources(DefaultGeneratorContext)} is
     * always called to return the task and its prefix array to their respective pools. The
     * {@link DefaultGeneratorContext context} is {@link ThreadLocal#get() fetched} once at the
     * start to minimize {@link ThreadLocal} access overhead.
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
        final GeneratorContext ctx = ((GeneratorThread) Thread.currentThread()).getContext();

        try {
            // Path for the root task
            if (this.prefixLength == 0)
                computeRootSubtasks(ctx);

            // Path for leaf tasks
            else if (this.prefixLength == this.numClicks - 1) {
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
     * each one. The range of iteration is limited by {@link #maxFirstClickIndex} as a pruning
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
     * @param ctx The thread-local {@link DefaultGeneratorContext}.
     * @see #recycleOwnResources(DefaultGeneratorContext)
     * @see StartYourMonkeys#main(String[])
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(N)} where {@code N} is the number of valid first clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate unless pools are empty.
     */
    private void computeRootSubtasks(GeneratorContext ctx) {
        final short start = 0;
        final short max = (short) (Math.min(Grid.NUM_CELLS - numClicks, maxFirstClickIndex) + 1);

        for (short i = start; i < max; i++) {
            final long lowerMask = this.trueCellMasksLower.getLong(i);

            final short[] newPrefix = buildPrefixWithNewValue(ctx, i);

            // Identify the parity of this root subtask:
            final boolean parity = (lowerMask & 1L) != 0;

            getAndForkSubtask(ctx, newPrefix, lowerMask, this.trueCellMasksUpper.getLong(i), false,
                    parity);
        }

        helpQuiesce(); // Wait for all subtasks to complete before returning
        // This will ensure that the root task does not exit prematurely, keeping the main thread
        // parked
    }

    private short[] buildPrefixWithNewValue(GeneratorContext ctx, short newValue) {
        short[] newPrefix = ctx.getArrayPool().get();
        if (newPrefix == null)
            newPrefix = new short[this.numClicks - 1]; // Safeguard if pool is empty
        System.arraycopy(this.prefix, 0, newPrefix, 0, this.prefixLength);
        newPrefix[this.prefixLength] = newValue;
        return newPrefix;
    }

    private void getAndForkSubtask(GeneratorContext ctx, short[] newPrefix, long newAdjacencyLower,
            long newAdjacencyUpper, boolean skipConstraints, boolean isOdd) {
        final CombinationGeneratorTask subtask = ctx.getTaskPool().get();
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

        if (this.useDualMasks) {
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
     * @param ctx The thread-local {@link DefaultGeneratorContext}.
     * @see DefaultGeneratorContext#getOrCreateBatch()
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
        WorkBatch batch = ctx.getCurrentBatch();
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
     * <li>{@link #computeIntermediateSubtasksConstraintPath(DefaultGeneratorContext)}: The "slow"
     * path that includes the pruning check.</li>
     * <li>{@link #computeIntermediateSubtasksSkipPath(DefaultGeneratorContext)}: The "fast" path
     * that omits the check entirely.</li>
     * </ul>
     * This dispatcher creates a <strong>monomorphic call site</strong> for each path. The JIT
     * compiler can more aggressively optimize these separate, branch-free methods than it could a
     * single method containing a conditional check, leading to significant performance gains. It
     * also simplifies the code for {@link #compute()}, designating a clear input method for
     * intermediate tasks without an additional conditional.
     * </p>
     * 
     * @param ctx The thread-local {@link DefaultGeneratorContext}.
     * @since 2025.08 - Reduced Branching Refactor
     * @performance {@code O(1)} for dispatching.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate.
     */
    private void computeIntermediateSubtasks(GeneratorContext ctx) {
        final short start = (short) (this.prefix[this.prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (this.numClicks - this.prefixLength) + 1);

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
     * @param ctx The thread-local {@link DefaultGeneratorContext} containing the resource pools.
     * @see #skipConstraintsCheck
     * @see #computeIntermediateSubtasksConstraintPath(DefaultGeneratorContext)
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
            final long lowerMask = this.trueCellMasksLower.getLong(i);

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
     * it performs a critical pruning step by calling {@link #constraintCheck(int)}. If that check
     * determines that no possible descendant of this task can form a valid solution, the entire
     * branch is pruned, saving a massive amount of wasted computation.
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
     * @param ctx The thread-local {@link DefaultGeneratorContext} containing the resource pools.
     * @see #computeIntermediateSubtasksSkipPath(DefaultGeneratorContext)
     * @since 2025.08 - Specialized Subtask Paths
     * @performance {@code O(1)} for the early constraint check,
     *              <code>O({@link Grid#NUM_CELLS} - {@link #numClicks} + {@link #prefixLength} -
     *              {@link #prefix}[prefixLength - 1] + 1)</code> for iterating over possible next
     *              clicks.
     * @threading Thread-safe due to {@link java.util.concurrent.ForkJoinTask ForkJoinTask}
     *            isolation.
     * @memory Does not allocate unless pools are empty; uses pooled resources.
     */
    private void computeIntermediateSubtasksConstraintPath(GeneratorContext ctx, short start,
            short max) {
        // Early constraint check - happens ONCE per task, not per iteration
        if (this.prefixLength >= 2 && !constraintCheck(start)) {
            return; // Skip this entire branch if constraints cannot be satisfied
        }

        // Pure loop - no conditionals inside, all branching resolved outside loop
        for (short i = start; i < max; i++) {
            final long lowerMask = this.trueCellMasksLower.getLong(i);

            final short[] newPrefix = buildPrefixWithNewValue(ctx, i);

            // Determine the parity of the new prefix based on the new click
            final boolean newPrefixParity = getNewPrefixParity(lowerMask);

            // Update the adjacency state for the child task
            final long childAdjacenciesLower = this.currentAdjacenciesLower | lowerMask;
            // TODO: Extract this to a separate method that checks if dual masks are enabled for
            // better JIT constant folding
            final long childAdjacenciesUpper = this.currentAdjacenciesUpper
                    | this.trueCellMasksUpper.getLong(i); // Only used if dual masks are enabled,
                                                          // otherwise constant folded out

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
     * {@link #currentAdjacencies} (which {@code true} cells are toggled by the current prefix) with
     * the {@link #EXPECTED_MASK} (all {@code true} cells). If {@code needed} is zero, the
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
        if (this.useDualMasks) {
            return constraintCheckDualMask(startIdx);
        } else {
            return constraintCheckSingleMask(startIdx);
        }
    }

    private boolean constraintCheckSingleMask(int startIdx) {
        // cachedAdjacencyState can only be -1 for the root task (which skips this check)
        // Therefore, we can assume it is initialized here, saving a branch in our logic.

        // XOR with lower expected mask to find which bits need to be flipped
        final long needed = this.currentAdjacenciesLower ^ this.expectedMaskLower;

        // If no bits need to be flipped, we're already good.
        // OPTIMIZATION: Skip future checks by setting skipConstraintsCheck to true
        if (needed == 0L) {
            return this.skipConstraintsCheck = true;
        }

        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        return (this.suffixMasksLower.getLong(startIdx) & needed) == needed;
    }

    private boolean constraintCheckDualMask(int startIdx) {
        // cachedAdjacencyState can only be -1 for the root task (which skips this check)
        // Therefore, we can assume it is initialized here, saving a branch in our logic.

        // XOR with expected masks to find which bits need to be flipped
        final long neededLower = this.currentAdjacenciesLower ^ this.expectedMaskLower;
        final long neededUpper = this.currentAdjacenciesUpper ^ this.expectedMaskUpper;

        // If no bits need to be flipped, we're already good.
        // OPTIMIZATION: Skip future checks by setting skipConstraintsCheck to true
        if (neededLower == 0L && neededUpper == 0L) {
            return this.skipConstraintsCheck = true;
        }

        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        return (this.suffixMasksLower.getLong(startIdx) & neededLower) == neededLower
                && (this.suffixMasksUpper.getLong(startIdx) & neededUpper) == neededUpper;
    }

    /**
     * An array of pre-computed "suffix OR" bitmasks for {@code O(1)} constraint checking.
     * 
     * <p>
     * This is a critical optimization for {@link #constraintCheck(int)}. The mask at index
     * {@code i} is the bitwise {@code OR} of all {@link #TRUE_CELL_MASKS} from index {@code i} to
     * the end of the grid.
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
    private final LongList suffixMasksLower;
    private final LongList suffixMasksUpper;

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
     * randomly selected queue} from the {@link #queueArray}. To minimize contention, it tries each
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
        CombinationQueue[] queues = queueArray.getAllQueues();
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
     * respective pools within the provided {@link DefaultGeneratorContext}.
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
     * @param ctx The thread-local {@link DefaultGeneratorContext} containing the {@link ArrayPool}
     *            and {@link TaskPool}.
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
        ctx.getArrayPool().put(this.prefix);
        this.prefix = null;

        // Recycle task to context pool
        ctx.getTaskPool().put(this);
    }
}
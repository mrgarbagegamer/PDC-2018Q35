package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.shorts.ShortList;

// TODO: Update Javadoc
// TODO: Consider a polymorphic approach to distinguish between root, intermediate, and leaf tasks
// TODO: Look at using getSurplusQueuedTaskCount() to determine if we should fork or compute
// directly
// TODO: Consider using a CountDownLatch instead of helpQuiesce() to park the main thread.
/**
 * A {@link RecursiveAction} that generates combinations of clicks for the Lights Out puzzle solver.
 *
 * <p>
 * This class is the "producer" in a producer-consumer pattern, using a {@link ForkJoinPool} to
 * recursively explore the solution space. After a recent refactor, it no longer generates
 * individual combinations. Instead, it creates compact {@link WorkBatch.WorkItem} objects that
 * describe a *range* of combinations (a shared prefix and a set of final clicks). These work items
 * are added to a {@link WorkBatch}, then enqueued into a {@link QueueStrategy} for consumption by
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
 *            are {@link GeneratorContext#flushCurrentBatch() flushed} to the queues for processing.
 * @memory Object allocations are minimized through extensive use of {@link TaskPool}, managed by a
 *         thread-local {@code GeneratorContext}.
 */
public class CombinationGeneratorTask extends RecursiveAction {

    private final short[] prefix;
    private final int numClicks;

    private int prefixLength;
    private long currentAdjacenciesLower = 0L;
    private long currentAdjacenciesUpper = 0L;
    private boolean isOdd;
    private boolean skipConstraintsCheck = false;

    private final SolverConfiguration solverConfig;
    private final LongList trueCellMasksLower;
    private final LongList trueCellMasksUpper;
    private final long expectedMaskLower;
    private final long expectedMaskUpper;
    private final LongList suffixMasksLower;
    private final LongList suffixMasksUpper;
    private final boolean useDualMasks;

    public static CombinationGeneratorTask createRootTask(SolverConfiguration solverConfig) {
        final CombinationGeneratorTask rootTask = new CombinationGeneratorTask(solverConfig);

        // Initialize instance fields
        rootTask.prefixLength = 0;

        return rootTask;
    }

    protected CombinationGeneratorTask(SolverConfiguration solverConfig) {
        this.solverConfig = mustNotBeNull(solverConfig, "config");
        this.numClicks = solverConfig.numClicks();
        this.prefix = new short[this.numClicks - 1];
        this.trueCellMasksLower = solverConfig.getTrueCellMasksLower();
        this.trueCellMasksUpper = solverConfig.getTrueCellMasksUpper();
        this.expectedMaskLower = solverConfig.getExpectedMaskLower();
        this.expectedMaskUpper = solverConfig.getExpectedMaskUpper();
        this.suffixMasksLower = solverConfig.getSuffixMasksLower();
        this.suffixMasksUpper = solverConfig.getSuffixMasksUpper();
        this.useDualMasks = solverConfig.getUseDualMasks();
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
     * A {@code finally} block ensures that {@link #recycleTask(DefaultGeneratorContext)} is always
     * called to return the task and its prefix array to their respective pools. The
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
        // Get the current GeneratorThread.
        final GeneratorThread currentThread = (GeneratorThread) Thread.currentThread();

        // Check for interruption of the current thread before doing any work.
        if (currentThread.isInterrupted()) {
            return; // Exit early if interrupted
        }

        // Fetch the context from the current thread once to avoid multiple accesses.
        final GeneratorContext ctx = currentThread.getContext();

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
            recycleTask(ctx);
        }
    }

    private void computeRootSubtasks(GeneratorContext ctx) {
        // Calculate the max.
        final ShortList evenClickIndices = this.solverConfig.getEvenClickIndices();
        final short lastEvenClick = evenClickIndices.getShort(evenClickIndices.size() - 1);
        final short max = (short) (Math.min(Grid.NUM_CELLS - this.numClicks, lastEvenClick) + 1);

        for (short i = 0; i < max; i++)
            getAndForkSubtask(ctx, i);

        helpQuiesce(); // Wait for all subtasks to complete before returning
        // This will ensure that the root task does not exit prematurely, keeping the main thread
        // parked
    }

    private void getAndForkSubtask(GeneratorContext ctx, short newValue) {
        CombinationGeneratorTask subtask = ctx.getTaskPool().get();
        if (subtask == null)
            subtask = new CombinationGeneratorTask(this.solverConfig);

        subtask.init(this, newValue);

        // Fork the subtask - it will clean itself up
        subtask.fork();
    }

    public void init(CombinationGeneratorTask parentTask, short newValue) {
        // Copy the parent's prefix to the subtask, then append the new value
        System.arraycopy(parentTask.prefix, 0, this.prefix, 0, parentTask.prefixLength);
        this.prefix[parentTask.prefixLength] = newValue;

        this.prefixLength = parentTask.prefixLength + 1;
        this.skipConstraintsCheck = parentTask.skipConstraintsCheck;
        this.isOdd = parentTask.getNewPrefixParity(newValue);

        if (!this.skipConstraintsCheck) {
            this.currentAdjacenciesLower = parentTask.currentAdjacenciesLower
                    | this.trueCellMasksLower.getLong(newValue);

            if (this.useDualMasks)
                this.currentAdjacenciesUpper = parentTask.currentAdjacenciesUpper
                        | this.trueCellMasksUpper.getLong(newValue);
        }

        reinitialize();
    }

    // LEAF TASK PATH:
    private final void computeLeafCombinations(GeneratorContext ctx) {
        // TODO: Add a lastPrefixClick helper method to reduce duplication.
        final short lastPrefixClick = (short) (this.prefix[this.prefixLength - 1] + 1);

        // 1. Add the work range to the batch.
        WorkBatch batch = ctx.getCurrentBatch();
        if (batch == null)
            return; // Exit if interrupted

        // If the batch is full, flush it and get a new one.
        if (batch.isFull()) {
            if (!ctx.flushCurrentBatch()) {
                // Exit if interrupted or if termination signal received during flush
                return;
            }
            batch = ctx.getCurrentBatch();
            if (batch == null)
                return; // Exit if interrupted
        }

        // Add the entire valid range as a single work item.
        batch.addWork(this.prefix, lastPrefixClick, this.isOdd);
    }

    private void computeIntermediateSubtasks(GeneratorContext ctx) {
        final short start = (short) (this.prefix[this.prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (this.numClicks - this.prefixLength) + 1);

        if (this.skipConstraintsCheck) {
            computeIntermediateSubtasksSkipPath(ctx, start, max);
        } else {
            computeIntermediateSubtasksConstraintPath(ctx, start, max);
        }
    }

    // PURE HOT PATH 1:
    private void computeIntermediateSubtasksSkipPath(GeneratorContext ctx, short start, short max) {
        // Pure loop - no constraint checking, no mask loading, no conditionals
        for (short i = start; i < max; i++)
            getAndForkSubtask(ctx, i);
    }

    private boolean getNewPrefixParity(short newValue) {
        final long lowerMask = this.trueCellMasksLower.getLong(newValue);
        return this.isOdd ^ ((lowerMask & 1L) != 0);
    }

    // PURE HOT PATH 2:
    private void computeIntermediateSubtasksConstraintPath(GeneratorContext ctx, short start,
            short max) {
        // Early constraint check - happens ONCE per task, not per iteration
        if (this.prefixLength >= 2 && !constraintCheck(start)) {
            return; // Skip this entire branch if constraints cannot be satisfied
        }

        // Pure loop - no conditionals inside, all branching resolved outside loop
        for (short i = start; i < max; i++)
            getAndForkSubtask(ctx, i);
    }

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
            this.skipConstraintsCheck = true;
            return true;
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
            this.skipConstraintsCheck = true;
            return true;
        }

        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        return (this.suffixMasksLower.getLong(startIdx) & neededLower) == neededLower
                && (this.suffixMasksUpper.getLong(startIdx) & neededUpper) == neededUpper;
    }

    private void recycleTask(GeneratorContext ctx) {
        // No ThreadLocal access needed - use passed context

        // Recycle task to context pool
        ctx.getTaskPool().put(this);
    }
}
package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TestClickCombination - [Worker Purpose - e.g., "ForkJoin recursive combination generator"]
 * 
 * <p>[High-level description of what this worker does in the algorithm.
 * Explain its role in the concurrent processing pipeline.]</p>
 * 
 * <h2>Execution Model</h2>
 * <p>[How this task executes - recursive subdivision, work-stealing, etc.
 * Include task granularity and splitting criteria.]</p>
 * 
 * <h2>Resource Management</h2>
 * <p>[How resources are acquired, used, and cleaned up. Pool usage patterns.]</p>
 * 
 * <h2>Performance Critical Paths</h2>
 * <p>[Identify hot paths and optimization focus areas. JIT considerations.]</p>
 * 
 * <h3>3/15 - 20% of documentation completed</h3>
 * 
 * @algorithm [Detailed algorithm description with complexity analysis]
 * @threading [Concurrency model and synchronization approach]
 * @performance [Performance characteristics and bottleneck analysis]
 * @see [Related worker classes and coordination mechanisms]
 */
public class TestClickCombination extends Thread
{
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);
    /**
     * A constant defining how often to log failed attempts. After this many failed combinations per
     * thread (not including combinations that fail the odd adjacency check), a debug log entry will be
     * made. This helps monitor progress without overwhelming the logs.
     * 
     * <p>
     * Logging is purely a diagnostic feature and does not affect the algorithm, but excessive logging
     * can block threads and degrade performance, even with asynchronous logging. Therefore, we log
     * only every N failures, where N is defined by this constant.
     * </p>
     * 
     * @since 2025.05.31 - Logging Threshold Introduction
     * @threading This constant is immutable and thread-safe.
     * @performance O(1) check per failed combination.
     * @memory Minimal memory impact as it's just a single integer.
     * @see #run()
     * @see CombinationMessage
     */
    private static final int LOG_EVERY_N_FAILURES = 100_000; // Log every N failures to avoid flooding the logs

    private final CombinationQueue combinationQueue;
    private final CombinationQueueArray queueArray;
    private final Grid puzzleGrid;

    /**
     * Lookup table mapping each possible click to a bitmask of true cells it affects. These are
     * semantically similar to the adjacency masks in {@link Grid}, but only for the true cells in the
     * current puzzle.
     * 
     * <p>
     * While generators have their own pruning logic, the vast majority of combinations that make their
     * way to monkeys are not valid due to odd adjacency violations, and we need to filter these out
     * before they reach the grid. To do this, we need a mechanism to quickly check what true cells a
     * click will affect, and we can use a bitmask for this purpose.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * We statically initialize this table once per puzzle in {@link #initializeLookupTable(short[])}
     * using double-checked locking to ensure thread safety. This avoids redundant computation and
     * allows all threads to share the same table without contention, which is crucial for performance.
     * Bitmasks are used to efficiently represent the affected true cells, and while a long can only store
     * 64 bits of data, all puzzles in this game have less than 64 initially true cells, letting us use
     * just one long per cell (and perform one bitwise operation per click).
     * </p>
     * 
     * @since 2025.06.30 - Bitmask Pre-computations
     * @threading The lookup table is initialized once per puzzle in a thread-safe manner using
     *             double-checked locking.
     * @performance O(1) lookup time for each cell in the combination.
     * @memory The lookup table is an array of 109 long values, taking up 872 bytes (109 * 8 bytes).
     * @see EXPECTED_MASK
     * @see #satisfiesOddAdjacency(short[], short[])
     */
    private static volatile long[] CLICK_TO_TRUE_CELL_MASK = null;
    /**
     * The expected mask for a valid combination of clicks, which is the bitmask where all true cells
     * are set to 1. This is computed once during initialization and used to validate combinations.
     * 
     * <p>
     * As part of the odd adjacency check in monkey threads, we need to ensure that the number of clicks
     * affecting each true cell is odd, which is done by XORing the mask for each click in
     * {@link #CLICK_TO_TRUE_CELL_MASK}. A valid combination would have all 1s in the mask up to the
     * number of true cells, but we can't know the value until we have the true cells. Recomputing the
     * mask every time we perform a check would be inefficient, so we compute it once during
     * initialization and store it here.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Bitmasks are used for their efficiency in representing the state of affected true cells as well
     * as their fast operations. By making this variable static and volatile, we ensure that only one
     * thread ever has to compute the mask, and all threads can read it without contention.
     * </p>
     * 
     * @since 2025.06.30 - Bitmask Pre-computations
     * @threading The expected mask is computed once per puzzle in a thread-safe manner using
     *             double-checked locking.
     * @performance O(1) lookup time.
     * @memory The expected mask is a single long value, taking up just 8 bytes.
     * @see CLICK_TO_TRUE_CELL_MASK
     * @see #satisfiesOddAdjacency(short[], short[])
     */
    private static volatile long EXPECTED_MASK = 0L;

    public TestClickCombination(String threadName, CombinationQueue combinationQueue,
                               CombinationQueueArray queueArray, Grid puzzleGrid)
    {
        super(threadName);
        this.combinationQueue = combinationQueue;
        this.queueArray = queueArray;
        this.puzzleGrid = puzzleGrid;

        // Initialize lookup table once for all threads
        short[] trueCells = puzzleGrid.findTrueCells(Grid.ValueFormat.Index); // Find all true cells in index format
        initializeLookupTable(trueCells);
    }

    // Initialize the static lookup table for fast odd adjacency checks
    private static void initializeLookupTable(short[] trueCells)
    {
        // Double-checked locking for thread-safe lazy initialization
        if (CLICK_TO_TRUE_CELL_MASK == null)
        {
            synchronized (TestClickCombination.class)
            {
                if (CLICK_TO_TRUE_CELL_MASK == null)
                {
                    long[] lookup = new long[Grid.NUM_CELLS]; // 109 possible clicks, single long for ≤64 bits

                    for (short clickCell = 0; clickCell < 109; clickCell++) // Generate all possible clicks in index format
                    {
                        for (int i = 0; i < trueCells.length; i++)
                        {
                            if (Grid.areAdjacent(trueCells[i], clickCell, Grid.ValueFormat.Index))
                            {
                                lookup[clickCell] |= (1L << i);
                            }
                        }
                    }

                    // Compute expected mask once - simplified since trueCells.length ≤ 64
                    long expectedMask = (1L << trueCells.length) - 1;

                    // Atomically publish the results
                    EXPECTED_MASK = expectedMask;
                    CLICK_TO_TRUE_CELL_MASK = lookup; // This must be last
                }
            }
        }
    }

    @Override
    public void run()
    {
        int failedCount = 0; // Count of failed attempts for logging
        short[] trueCells = puzzleGrid.findTrueCells();

        while (!queueArray.solutionFound)
        {
            WorkBatch workBatch = getWork();

            if (workBatch == null)
            {
                if (queueArray.solutionFound || (queueArray.generationComplete && allQueuesEmpty()))
                {
                    break; // Exit if solution found or generation is done and all queues are empty
                }
                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    logger.debug("Thread {} interrupted while waiting for work", getName());
                    break; // Exit on interruption (from pool shutdown)
                }
                continue; // Retry getting a combination
            }

            // OPTIMIZED: Process entire batch with reduced branching
            short[] combinationClicks;
            while ((combinationClicks = workBatch.poll()) != null && !queueArray.solutionFound)
            {
                if (satisfiesOddAdjacency(combinationClicks, trueCells))
                {
                    puzzleGrid.click(combinationClicks); // Apply the click combination to the grid

                    if (puzzleGrid.isSolved())
                    {
                        logger.info("Found the solution as the following click combination: {}",
                                   new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        queueArray.solutionFound(this.getName(), combinationClicks.clone());

                        // Trigger immediate shutdown of generator pool
                        triggerGeneratorShutdown();

                        // Don't recycle the winning batch
                        return;
                    }

                    // reset the grid for the next combination
                    puzzleGrid.initialize();

                    // Increment failed count and log if needed (removed debug check and solution check per feedback)
                    failedCount++;
                    if (failedCount == LOG_EVERY_N_FAILURES)
                    {
                        logger.debug("Tried and failed: {}", new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        failedCount = 0; // Reset the count after logging
                    }
                }
                // Note: Grid initialization not needed for invalid combinations since grid wasn't modified
            }

            // After processing, recycle the batch
            queueArray.getWorkBatchPool().offer(workBatch);
        }
    }

    /**
     * Triggers immediate shutdown of the generator pool when a solution is found.
     * This eliminates the need for generators to check cancellation flags.
     */
    private void triggerGeneratorShutdown()
    {
        // Access the generator pool from CombinationGeneratorTask if stored there,
        // or use a different mechanism to signal shutdown
        ForkJoinPool generatorPool = CombinationGeneratorTask.getForkJoinPool();
        if (generatorPool != null && !generatorPool.isShutdown())
        {
            logger.debug("Triggering generator pool shutdown from {}", getName());
            generatorPool.shutdownNow(); // Immediate shutdown with interruption
        }
    }

    /**
     * Gets a batch of work, first from the primary queue, then by stealing.
     */
    private WorkBatch getWork()
    {
        // Try my own queue first
        WorkBatch batch = combinationQueue.getWorkBatch();
        if (batch != null)
        {
            return batch;
        }

        // My queue is empty, try to steal
        CombinationQueue[] queues = queueArray.getAllQueues();
        for (int i = 0; i < queues.length; i++)
        {
            batch = queues[i].getWorkBatch();
            if (batch != null)
            {
                return batch;
            }
        }

        return null; // No work found anywhere
    }

    // FIX: Check queue size without consuming an item.
    private boolean allQueuesEmpty()
    {
        for (CombinationQueue q : queueArray.getAllQueues())
        {
            if (!q.isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    // OPTIMIZED: JIT-friendly bitmask-based odd adjacency check with inlining hints
    private final boolean satisfiesOddAdjacency(short[] combination, short[] trueCells)
    {
        // JIT OPTIMIZATION: Cache array references and length to encourage optimization
        final long[] masks = CLICK_TO_TRUE_CELL_MASK;
        final int combinationLength = combination.length;
        final long expectedMask = EXPECTED_MASK;
        
        long trueCellCounts = 0L;
        
        // JIT OPTIMIZATION: Use counted loop pattern that JIT prefers for unrolling
        // The final variables and predictable loop bounds encourage aggressive optimization
        for (int i = 0; i < combinationLength; i++)
        {
            // JIT OPTIMIZATION: Use local variable to avoid repeated array access
            final int click = combination[i];
            
            // JIT OPTIMIZATION: Single XOR operation instead of two
            trueCellCounts ^= masks[click];
        }
        
        // JIT OPTIMIZATION: Single comparison instead of two
        return trueCellCounts == expectedMask;
    }
}
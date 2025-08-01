package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestClickCombination extends Thread 
{
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);
    private static final int LOG_EVERY_N_FAILURES = 100_000; // Log every N failures to avoid flooding the logs

    private final CombinationQueue combinationQueue;
    private final CombinationQueueArray queueArray;
    private final Grid puzzleGrid;

    // Static lookup table initialization with pre-computed masks
    private static volatile long[] CLICK_TO_TRUE_CELL_MASK = null;
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
        boolean iSolvedIt = false;
        CombinationQueue[] queues = queueArray.getAllQueues();
        short[] trueCells = puzzleGrid.findTrueCells();

        // Consider removing the iSolvedIt check here, since the main loop will exit if a solution is found
        while (!iSolvedIt && !queueArray.solutionFound)
        {
            WorkBatch workBatch = getWork();

            if (workBatch == null)
            {
                if (queueArray.solutionFound || (queueArray.generationComplete && allQueuesEmpty(queues)))
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

    // TODO: Look at replacing the boolean return type with a workbatch and returning the batch if it finds one
    private boolean allQueuesEmpty(CombinationQueue[] queues)
    {
        for (CombinationQueue q : queues)
        {
            if (q.getWorkBatch() != null)
            {
                // This is not ideal as it consumes an item, but for end-of-work check it's a simple approach.
                // A better way would be a size() method, but MpmcArrayQueue size is not linearizable.
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
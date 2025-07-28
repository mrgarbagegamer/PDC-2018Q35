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
    private static volatile long[][] CLICK_TO_TRUE_CELL_MASK = null;
    private static volatile long EXPECTED_MASK = 0L;
    private static volatile long EXPECTED_MASK_1 = 0L; // For trueCells > 64
    
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
                    long[][] lookup = new long[Grid.NUM_CELLS][2]; // 109 possible clicks, 2 long values for 128 bits
                    
                    for (short clickCell = 0; clickCell < 109; clickCell++) // Generate all possible clicks in index format
                    {
                        for (int i = 0; i < trueCells.length; i++) 
                        {
                            if (Grid.areAdjacent(trueCells[i], clickCell, Grid.ValueFormat.Index))
                            {
                                int longIndex = i / 64;
                                int bitPosition = i % 64;
                                lookup[clickCell][longIndex] |= (1L << bitPosition);
                            }
                        }
                    }
                    
                    // Compute expected masks once
                    long expectedMask = trueCells.length > 64 ?
                        0xFFFFFFFFFFFFFFFFL : (1L << trueCells.length) - 1;
                    
                    long expectedMask1 = trueCells.length <= 64 ? 0L :
                        (trueCells.length >= 128 ? 0xFFFFFFFFFFFFFFFFL :
                         ((1L << (trueCells.length - 64)) - 1));
                    
                    // Atomically publish the results
                    EXPECTED_MASK = expectedMask;
                    EXPECTED_MASK_1 = expectedMask1;
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
            
            while (!workBatch.isEmpty()) 
            {
                // TODO: Consider removing the null check if we can guarantee workBatch.poll() never returns null (which it shouldn't if !isEmpty())
                // Also consider removing the solutionFound check here and relying on the main loop condition
                short[] combinationClicks = workBatch.poll(); // Get the next combination of clicks (in index format)
                if (combinationClicks == null || queueArray.solutionFound)
                {
                    break;
                }

                if (satisfiesOddAdjacency(combinationClicks, trueCells)) 
                {
                    for (short click : combinationClicks)
                    {
                        puzzleGrid.click(click);
                    }

                    iSolvedIt = puzzleGrid.isSolved();

                    if (iSolvedIt) 
                    {
                        logger.info("Found the solution as the following click combination: {}", 
                                   new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        queueArray.solutionFound(this.getName(), combinationClicks.clone());
                        
                        // Trigger immediate shutdown of generator pool
                        triggerGeneratorShutdown();
                        
                        // Don't recycle the winning batch
                        return;
                    }
                }
                else continue;

                if (!iSolvedIt)
                {
                    failedCount++;
                    // TODO: Consider removing the isDebugEnabled check, since the program is likely to be run in debug mode
                    if (failedCount == LOG_EVERY_N_FAILURES && logger.isDebugEnabled() && !queueArray.solutionFound) 
                    {
                        logger.debug("Tried and failed: {}", new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        failedCount = 0; // Reset the count after logging
                    }
                }

                // reset the grid for the next combination
                puzzleGrid.initialize();
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

    // OPTIMIZED: Ultra-fast bitmask-based odd adjacency check - eliminated branches
    private boolean satisfiesOddAdjacency(short[] combination, short[] trueCells)
    {
        if (trueCells.length == 0) return true;
        
        long trueCellCounts0 = 0L;
        long trueCellCounts1 = 0L;
        
        // OPTIMIZATION: Eliminate branch misprediction by always processing both masks
        // This trades a few extra XOR operations for eliminating branch misprediction penalty
        final long[][] masks = CLICK_TO_TRUE_CELL_MASK;
        for (int i = 0; i < combination.length; i++)
        {
            final int click = combination[i];
            trueCellCounts0 ^= masks[click][0];
            trueCellCounts1 ^= masks[click][1]; // Always process, mask will be 0 if not needed
        }
        
        // OPTIMIZATION: Use pre-computed expected masks to eliminate runtime calculations
        return trueCellCounts0 == EXPECTED_MASK && trueCellCounts1 == EXPECTED_MASK_1;
    }
}
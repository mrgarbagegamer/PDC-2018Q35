package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestClickCombination extends Thread 
{
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);
    private static final int LOG_EVERY_N_FAILURES = 100_000; // Log every N failures to avoid flooding the logs

    private final CombinationQueue combinationQueue;
    private final CombinationQueueArray queueArray;
    private final Grid puzzleGrid;

    // Make these static and initialize once
    private static volatile long[][] CLICK_TO_TRUE_CELL_MASK = null;
    private static volatile long EXPECTED_MASK = 0L;
    
    public TestClickCombination(String threadName, CombinationQueue combinationQueue, 
                               CombinationQueueArray queueArray, Grid puzzleGrid) 
    {
        super(threadName);
        this.combinationQueue = combinationQueue;
        this.queueArray = queueArray;
        this.puzzleGrid = puzzleGrid;
        
        // Initialize lookup table once for all threads
        int[] trueCells = puzzleGrid.findTrueCells();
        initializeLookupTable(trueCells);
    }

    private static void initializeLookupTable(int[] trueCells)
    {
        // Double-checked locking for thread-safe lazy initialization
        if (CLICK_TO_TRUE_CELL_MASK == null)
        {
            synchronized (TestClickCombination.class)
            {
                if (CLICK_TO_TRUE_CELL_MASK == null)
                {
                    long[][] lookup = new long[700][2];
                    
                    for (int clickCell = 0; clickCell < 700; clickCell++) 
                    {
                        for (int i = 0; i < trueCells.length; i++) 
                        {
                            if (Grid.areAdjacent(trueCells[i], clickCell))
                            {
                                int longIndex = i / 64;
                                int bitPosition = i % 64;
                                lookup[clickCell][longIndex] |= (1L << bitPosition);
                            }
                        }
                    }
                    
                    // Compute expected mask once
                    long expectedMask = trueCells.length >= 64 ? 
                        0xFFFFFFFFFFFFFFFFL : (1L << trueCells.length) - 1;
                    
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

        int[] trueCells = puzzleGrid.findTrueCells();

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
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    logger.error("Thread interrupted while waiting for new combinations", e);
                    break;
                }
                continue; // Retry getting a combination
            }
            
            while (!workBatch.isEmpty()) 
            {
                int[] combinationClicks = workBatch.poll();
                if (combinationClicks == null || queueArray.solutionFound)
                {
                    break;
                }

                if (satisfiesOddAdjacency(combinationClicks, trueCells)) 
                {
                    for (int click : combinationClicks)
                    {
                        puzzleGrid.click(click);
                    }

                    iSolvedIt = puzzleGrid.isSolved();

                    if (iSolvedIt) 
                    {
                        logger.info("Found the solution as the following click combination: {}", 
                                   new CombinationMessage(combinationClicks));
                        queueArray.solutionFound(this.getName(), combinationClicks);
                        // Do NOT recycle the batch containing the winning combination to avoid UAF on the winning array.
                        // Let it be garbage collected.
                        return;
                    }

                    // // Update firstTrueCell and adjacents cache if changed
                    // int newFirstTrueCell = puzzleGrid.findFirstTrueCell();
                    // if (newFirstTrueCell != firstTrueCell) 
                    // {
                    //     firstTrueCell = newFirstTrueCell;
                    //     firstTrueAdjacents = (firstTrueCell != -1) ? Grid.findAdjacents(firstTrueCell) : null; // Uncomment this line if you want to use more aggressive but less efficient pruning
                    // }

                    // // Prune if no remaining click can affect the first true cell
                    // boolean canAffect = false;
                    // for (int j = i + 1; j < combinationClicks.length; j++) 
                    // {
                    //     int nextClick = combinationClicks[j];
                    //     if (Grid.canAffectFirstTrueCell(firstTrueCell, nextClick)) 
                    //     {
                    //         canAffect = true;
                    //         break;
                    //     }
                    // }
                    // if (!canAffect) 
                    // {
                    //     break;
                    // }

                    // // Uncomment these lines to prune if no remaining click is in firstTrueAdjacents (if you want even more aggressive but less efficient pruning)
                    // if (firstTrueAdjacents != null) {
                    //     boolean hasTrueAdjacent = false;
                    //     for (int j = i + 1; j < combinationClicks.length; j++) {
                    //         int nextClick = combinationClicks[j];
                    //         for (int adj : firstTrueAdjacents) {
                    //             if (nextClick == adj) {
                    //                 hasTrueAdjacent = true;
                    //                 break;
                    //             }
                    //         }
                    //         if (hasTrueAdjacent) break; // Found a valid adjacent click                        
                    //     }
                    //     if (!hasTrueAdjacent) break;
                    // }
                }
                else continue;

                if (!iSolvedIt)
                {
                    failedCount++;
                    if (failedCount == LOG_EVERY_N_FAILURES && logger.isDebugEnabled() && !queueArray.solutionFound) 
                    {
                        logger.debug("Tried and failed: {}", new CombinationMessage(combinationClicks.clone()));
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

    // Ultra-fast bitmask-based odd adjacency check using static lookup
    private boolean satisfiesOddAdjacency(int[] combination, int[] trueCells) 
    {
        if (trueCells.length == 0) return true;
        
        long trueCellCounts0 = 0L;
        long trueCellCounts1 = 0L;
        
        // Use static lookup table
        for (int click : combination) 
        {
            trueCellCounts0 ^= CLICK_TO_TRUE_CELL_MASK[click][0];
            if (trueCells.length > 64)
            {
                trueCellCounts1 ^= CLICK_TO_TRUE_CELL_MASK[click][1];
            }
        }
        
        // Use static expected mask
        if (trueCells.length <= 64)
        {
            return trueCellCounts0 == EXPECTED_MASK;
        }
        else
        {
            long expectedMask1 = trueCells.length >= 128 ? 
                0xFFFFFFFFFFFFFFFFL : (1L << (trueCells.length - 64)) - 1;
            return trueCellCounts0 == 0xFFFFFFFFFFFFFFFFL && trueCellCounts1 == expectedMask1;
        }
    }
}
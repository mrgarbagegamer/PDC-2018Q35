package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestClickCombination extends Thread 
{
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);
    private static final int LOG_EVERY_N_FAILURES = 10000; // Log every N failures to avoid flooding the logs
    private static final int BATCH_SIZE = 50; // Size of work to take from my queue
    private static final int STEAL_SIZE = BATCH_SIZE / 5; // Size of the batch to steal from other queues

    private final CombinationQueue combinationQueue;
    private final CombinationQueueArray queueArray;
    private final Grid puzzleGrid;

    private final Deque<int[]> workBatch = new ArrayDeque<>(50); // Worker-local batch

    public TestClickCombination(String threadName, CombinationQueue combinationQueue, CombinationQueueArray queueArray, Grid puzzleGrid) 
    {
        super(threadName);
        this.combinationQueue = combinationQueue;
        this.queueArray = queueArray;
        this.puzzleGrid = puzzleGrid;
    }

    @Override
    public void run()
    {
        int failedCount = 0; // Count of failed attempts for logging
        boolean iSolvedIt = false;
        CombinationQueue[] queues = queueArray.getAllQueues();
        int myIndex = -1;
        for (int i = 0; i < queues.length; i++) 
        {
            if (queues[i] == this.combinationQueue) 
            {
                myIndex = i;
                break;
            }
        }

        int[] trueCells = puzzleGrid.findTrueCells();

        while (!iSolvedIt && !queueArray.isSolutionFound())
        {
            // Try to fill work batch from my primary queue
            int obtained = combinationQueue.drainToBatch(workBatch, BATCH_SIZE);
            
            // If my queue is empty, try work stealing from other queues
            if (obtained == 0) 
            {
                for (int i = 0; i < queues.length && workBatch.isEmpty(); i++) 
                {
                    if (i == myIndex) continue;
                    queues[i].drainToBatch(workBatch, STEAL_SIZE); // Steal smaller batches
                }
            }
            
            // If still no work, short sleep and continue
            if (workBatch.isEmpty()) 
            {
                if (combinationQueue.isSolutionFound() || combinationQueue.isGenerationComplete()) break;
                try 
                { 
                    Thread.sleep(0, 500_000); 
                } catch (InterruptedException e) 
                {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    logger.error("Thread interrupted while waiting for new combinations", e);
                    break;
                }
                continue; // Retry getting a combination
            }
            
            // Process the entire batch
            while (!workBatch.isEmpty()) 
            {
                int[] combinationClicks = workBatch.pollFirst();
                if (queueArray.isSolutionFound()) break;
                
                if (!satisfiesOddAdjacency(combinationClicks, trueCells)) 
                {
                    continue;
                }
                
                // int firstTrueCell = puzzleGrid.findFirstTrueCell();
                // int[] firstTrueAdjacents = (firstTrueCell != -1) ? Grid.findAdjacents(firstTrueCell) : null; // Uncomment this line if you want to use more aggressive but less efficient pruning

                for (int i = 0; (!iSolvedIt) && (!queueArray.isSolutionFound()) && (i < combinationClicks.length); i++) 
                {
                    int click = combinationClicks[i];
                    puzzleGrid.click(click);

                    // Early prune: too many trues left for remaining clicks
                    if (puzzleGrid.getTrueCount() > (combinationClicks.length - i - 1) * 6) 
                    {
                        break;
                    }

                    iSolvedIt = puzzleGrid.isSolved();

                    if (iSolvedIt) 
                    {
                        logger.info("Found the solution as the following click combination: {}", new CombinationMessage(combinationClicks));
                        queueArray.solutionFound(this.getName(), combinationClicks);
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

                if (!iSolvedIt)
                {
                    failedCount++;
                    if (failedCount == LOG_EVERY_N_FAILURES && logger.isDebugEnabled() && !queueArray.isSolutionFound()) 
                    {
                        logger.debug("Tried and failed: {}", new CombinationMessage(combinationClicks));
                        failedCount = 0; // Reset the count after logging
                    }
                }

                // reset the grid for the next combination
                puzzleGrid.initialize();
            }
            
            workBatch.clear(); // Clear for next batch
        }
    }

    private static boolean satisfiesOddAdjacency(int[] combination, int[] trueCells) 
    {
        for (int trueCell : trueCells) 
        {
            int count = 0;
            for (int click : combination) 
            {
                if (Grid.areAdjacent(trueCell, click)) count++;
            }
            if ((count & 1) == 0) return false;
        }
        return true;
    }
}
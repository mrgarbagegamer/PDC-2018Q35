package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestClickCombination extends Thread 
{
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);
    private static final int LOG_EVERY_N_FAILURES = 1000; // Log every N failures to avoid flooding the logs

    private final CombinationQueue combinationQueue;
    private final CombinationQueueArray queueArray;
    private final Grid puzzleGrid;

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

        while (!iSolvedIt && !queueArray.isSolutionFound())
        {
            int[] combinationClicks = this.combinationQueue.getClicksCombination();
            if (combinationClicks == null) 
            {
                for (int i = 0; i < queues.length; i++) 
                {
                    if (i == myIndex) continue; // Skip my own queue
                    combinationClicks = queues[i].getClicksCombination();
                    if (combinationClicks != null) break; // Found a combination in another queue
                }
            }

            if (combinationClicks == null) 
            {
                if (combinationQueue.isSolutionFound() || combinationQueue.isGenerationComplete()) break;
                try
                {
                    Thread.sleep(5); // Wait for new combinations to be added
                } catch (InterruptedException e) 
                {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    logger.error("Thread interrupted while waiting for new combinations", e);
                    break;
                }
                continue; // Retry getting a combination
            }

            int firstTrueCell = puzzleGrid.findFirstTrueCell();
            int[] firstTrueAdjacents = (firstTrueCell != -1) ? Grid.findAdjacents(firstTrueCell) : null; // Uncomment this line if you want to use more aggressive but less efficient pruning

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

                // Update firstTrueCell and adjacents cache if changed
                int newFirstTrueCell = puzzleGrid.findFirstTrueCell();
                if (newFirstTrueCell != firstTrueCell) 
                {
                    firstTrueCell = newFirstTrueCell;
                    firstTrueAdjacents = (firstTrueCell != -1) ? Grid.findAdjacents(firstTrueCell) : null; // Uncomment this line if you want to use more aggressive but less efficient pruning
                }

                // Prune if no remaining click can affect the first true cell
                boolean canAffect = false;
                for (int j = i + 1; j < combinationClicks.length; j++) 
                {
                    int nextClick = combinationClicks[j];
                    if (Grid.canAffectFirstTrueCell(firstTrueCell, nextClick)) 
                    {
                        canAffect = true;
                        break;
                    }
                }
                if (!canAffect) 
                {
                    break;
                }

                // Uncomment these lines to prune if no remaining click is in firstTrueAdjacents (if you want even more aggressive but less efficient pruning)
                if (firstTrueAdjacents != null) {
                    boolean hasTrueAdjacent = false;
                    for (int j = i + 1; j < combinationClicks.length; j++) {
                        int nextClick = combinationClicks[j];
                        for (int adj : firstTrueAdjacents) {
                            if (nextClick == adj) {
                                hasTrueAdjacent = true;
                                break;
                            }
                        }
                        if (hasTrueAdjacent) break; // Found a valid adjacent click                        
                    }
                    if (!hasTrueAdjacent) break;
                }
            }

            if (!iSolvedIt && !queueArray.isSolutionFound())
            {
                failedCount++;
                if (logger.isDebugEnabled() && failedCount == LOG_EVERY_N_FAILURES) 
                {
                    logger.debug("Tried and failed: {}", new CombinationMessage(combinationClicks));
                    failedCount = 0; // Reset the count after logging
                }
            }

            // reset the grid for the next combination
            puzzleGrid.initialize();
        }
    }
}

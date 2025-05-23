package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

public class TestClickCombination extends Thread 
{
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);

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
        boolean iSolvedIt = false;

        while(!iSolvedIt && !queueArray.isSolutionFound())
        {
            IntList combinationClicks = this.combinationQueue.getClicksCombination();
            if (combinationClicks == null) 
            {
                break; // No more combinations to process, exit the thread.
            }

            for (int i = 0; (!iSolvedIt) && (!queueArray.isSolutionFound()) && (i < combinationClicks.size()); i++) 
            {
                int click = combinationClicks.getInt(i);
                this.puzzleGrid.click(click);

                if (this.puzzleGrid.getTrueCount() > (combinationClicks.size() - i - 1) * 6) 
                {
                    // this means we have more true's than clicks left to process, so we can stop early
                    break;
                }

                iSolvedIt = this.puzzleGrid.isSolved();

                if (iSolvedIt) 
                {
                    logger.info("Found the solution as the following click combination: {}", combinationClicks);
                    queueArray.solutionFound(this.getName(), combinationClicks);
                    return;
                }

                IntSet firstTrueAdjacents = this.puzzleGrid.findFirstTrueAdjacentsAfter(click);
                if (firstTrueAdjacents == null || firstTrueAdjacents.isEmpty()) // Check if any true adjacents exist after the current click
                {
                    break;
                }
                else
                {
                    // Check if any remaining clicks are in the adjacents set
                    boolean hasTrueAdjacent = false;
                    for (int j = i + 1; j < combinationClicks.size(); j++) {
                        if (firstTrueAdjacents.contains(combinationClicks.getInt(j))) {
                            hasTrueAdjacent = true;
                            break;
                        }
                    }
                    if (!hasTrueAdjacent) 
                    {
                        break;
                    }
                }
            }

            if(!iSolvedIt && !queueArray.isSolutionFound())
            {
                logger.debug("Tried and failed: {}", combinationClicks);
            }

            // reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }
}

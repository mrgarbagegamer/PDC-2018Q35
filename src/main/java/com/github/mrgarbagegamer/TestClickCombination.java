package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

public class TestClickCombination extends Thread 
{
    private static final Logger logger = LogManager.getLogger(TestClickCombination.class);

    private CombinationQueue combinationQueue;
    private Grid puzzleGrid;

    public TestClickCombination(String threadName, CombinationQueue combinationQueue, Grid puzzleGrid) 
    {
        this.combinationQueue = combinationQueue;
        this.puzzleGrid = puzzleGrid;
        this.setName(threadName);
    }

    public void run() 
    {
        boolean iSolvedIt = false;

        while(!iSolvedIt && !this.combinationQueue.isItSolved())
        {
            IntList combinationClicks = this.combinationQueue.getClicksCombination();
            if (combinationClicks == null) 
            {
                break; // No more combinations to process, exit the thread.
            }

            for (int i = 0; (!iSolvedIt) && (!this.combinationQueue.isItSolved()) && (i < combinationClicks.size()); i++) 
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
                    this.combinationQueue.solutionFound(this.getName(), combinationClicks);
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

            if(!iSolvedIt && !this.combinationQueue.isItSolved())
            {
                logger.debug("Tried and failed: {}", combinationClicks); // Note for the future: Refactor this to use StringBuilders for formatted, garbage-free logging
            }

            // reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }
}

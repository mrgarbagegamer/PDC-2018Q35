package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.ArrayList;

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
            List<Click> combinationClicks = this.combinationQueue.getClicksCombination();

            for (int i = 0; (!iSolvedIt) && (!this.combinationQueue.isItSolved()) && (i < combinationClicks.size()); i++) 
            {
                Click click = combinationClicks.get(i);

                this.puzzleGrid.click(click.row, click.col);

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

                List<int[]> firstTrueAdjacents = this.puzzleGrid.findFirstTrueAdjacentsAfter(click.row, click.col);
                if (firstTrueAdjacents == null) // Check if any true adjacents exist after the current click
                {
                    break;
                }
                else
                {
                    List<String> adjList = new ArrayList<>();
                    for (int[] adj : firstTrueAdjacents) 
                    {
                        adjList.add(adj[0] + "," + adj[1]);
                    }

                    boolean hasTrueAdjacent = combinationClicks.subList(i + 1, combinationClicks.size()).stream()
                        .anyMatch(c -> adjList.contains(c.row + "," + c.col));

                    if (!hasTrueAdjacent) 
                    {
                        break;
                    }
                }
            }

            
            if(!iSolvedIt && !this.combinationQueue.isItSolved())
            {
                logger.debug("Tried and failed: {}", combinationClicks);
            }

            // reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }
}

package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

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
            if (combinationClicks == null) 
            {
                break; // No more combinations to process, exit the thread.
            }

            // Track the first true cell incrementally
            int[] firstTrueCell = this.puzzleGrid.findFirstTrueCell();
            Set<int[]> cachedFirstTrueAdjacents = null;
            int[] cachedFirstTrueCell = null;

            for (int i = 0; (!iSolvedIt) && (!this.combinationQueue.isItSolved()) && (i < combinationClicks.size()); i++) 
            {
                Click click = combinationClicks.get(i);
                Set<int[]> clickAdjacents = Grid.findAdjacents(click.row, click.col);

                // Check if click or its adjacents could affect the first true cell
                boolean mustRecompute = false;
                if (firstTrueCell == null) 
                {
                    mustRecompute = true;
                } else 
                {
                    if (click.row < firstTrueCell[0] || (click.row == firstTrueCell[0] && click.col <= firstTrueCell[1])) 
                    {
                        mustRecompute = true;
                    } else 
                    {
                        for (int[] adj : clickAdjacents) 
                        {
                            if (adj[0] < firstTrueCell[0] || (adj[0] == firstTrueCell[0] && adj[1] <= firstTrueCell[1])) 
                            {
                                mustRecompute = true;
                                break;
                            }
                        }
                    }
                }

                this.puzzleGrid.click(click.row, click.col);

                if (mustRecompute) 
                {
                    firstTrueCell = this.puzzleGrid.findFirstTrueCell();
                    // Invalidate the cached adjacents since the first true cell changed
                    cachedFirstTrueCell = null;
                    cachedFirstTrueAdjacents = null;
                }

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

                // Use the current firstTrueCell for pruning
                Set<int[]> firstTrueAdjacents = null;
                if (firstTrueCell != null) 
                {
                    // Only recompute adjacents if the first true cell changed
                    if (cachedFirstTrueCell == null || 
                        cachedFirstTrueCell[0] != firstTrueCell[0] || 
                        cachedFirstTrueCell[1] != firstTrueCell[1]) 
                    {
                        Set<int[]> adjacents = Grid.findAdjacents(firstTrueCell[0], firstTrueCell[1]);
                        Set<int[]> filteredAdjacents = new HashSet<>();
                        for (int[] adj : adjacents) 
                        {
                            if (adj[0] > click.row || (adj[0] == click.row && adj[1] > click.col)) 
                            {
                                filteredAdjacents.add(adj);
                            }
                        }
                        firstTrueAdjacents = filteredAdjacents.isEmpty() ? null : filteredAdjacents;
                        cachedFirstTrueCell = firstTrueCell.clone();
                        cachedFirstTrueAdjacents = firstTrueAdjacents;
                    } else 
                    {
                        firstTrueAdjacents = cachedFirstTrueAdjacents;
                    }
                }

                if (firstTrueAdjacents == null) // Check if any true adjacents exist after the current click
                {
                    break;
                }
                else
                {
                    Set<Integer> adjSet = new HashSet<>();
                    for (int[] adj : firstTrueAdjacents) 
                    {
                        adjSet.add((adj[0] << 8) | adj[1]); // Store the adjacents as an integer key (shifting row by 8 bits and ORing with col)
                    }

                    boolean hasTrueAdjacent = false;
                    for (int j = i + 1; j < combinationClicks.size(); j++) 
                    {
                        Click c = combinationClicks.get(j);
                        int key = (c.row << 8) | c.col;
                        if (adjSet.contains(key)) // If any click is in the adjSet, we can stop checking
                        { 
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
                logger.debug("Tried and failed: {}", combinationClicks);
            }

            // reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }
}

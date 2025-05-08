package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Grid puzzleGrid;
    private Set<Click> trueAdjacents;

    public CombinationGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks, Grid puzzleGrid) 
    {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.puzzleGrid = puzzleGrid;
        Set<int[]> trueAdjSet = puzzleGrid.findFirstTrueAdjacents();
        
        if (trueAdjSet != null) 
        {
            this.trueAdjacents = new HashSet<>();
            for (int[] adj : trueAdjSet) 
            {
                this.trueAdjacents.add(new Click(adj[0], adj[1]));
            }
        }
    }

    public void run() 
    {
        this.generateCombinationsIterative(this.possibleClicks, numClicks);
    }

    private void generateCombinationsIterative(List<Click> nodeList, int k) 
    {
        // Stack to hold the state of each level
        Deque<CombinationState> stack = new ArrayDeque<>();
        stack.push(new CombinationState(0, new ArrayList<>(k))); // Initial state

        while (!stack.isEmpty() && !this.combinationQueue.isItSolved()) 
        {
            CombinationState state = stack.pop();
            int start = state.start;
            List<Click> currentCombination = state.currentCombination;

            // If the combination size equals k, process it
            if (currentCombination.size() == k) 
            {
                // Add the valid combination to the queue
                this.combinationQueue.add(new ArrayList<>(currentCombination));
                continue;
            }

            // Add the next level of combinations to the stack
            for (int i = nodeList.size() - 1; i >= start; i--) 
            {
                currentCombination.add(nodeList.get(i)); // Add to the current combination

                // Determine if the new branch should be pruned
                if (currentCombination.size() < k) 
                {
                    stack.push(new CombinationState(i + 1, new ArrayList<>(currentCombination)));
                } 
                else if (trueAdjacents != null && currentCombination.size() == k) 
                {
                    boolean shouldPrune = true;
                    for (Click click : currentCombination) 
                    {
                        if (trueAdjacents.contains(click)) 
                        {
                            shouldPrune = false;
                            break;
                        }
                    }

                    if (shouldPrune) 
                    {
                        logger.debug("Skipping combination due to no true adjacents: {}", currentCombination);
                        currentCombination.remove(currentCombination.size() - 1);
                        break; // Skip this combination
                    } 
                    else 
                    {
                        this.combinationQueue.add(new ArrayList<>(currentCombination));
                    }
                }

                // Backtrack: Remove the last added element to restore the state
                currentCombination.remove(currentCombination.size() - 1);
            }
        }
    }

    // Helper class to represent the state of each level
    private static class CombinationState 
    {
        int start;
        List<Click> currentCombination;

        CombinationState(int start, List<Click> currentCombination) 
        {
            this.start = start;
            this.currentCombination = currentCombination;
        }
    }
}
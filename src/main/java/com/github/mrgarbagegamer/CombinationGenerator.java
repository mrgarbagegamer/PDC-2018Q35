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
        Click[] currentCombination = new Click[k]; // Fixed-size array for the combination
        stack.push(new CombinationState(0, 0, currentCombination)); // Initial state: start index = 0, size = 0

        while (!stack.isEmpty() && !this.combinationQueue.isItSolved()) 
        {
            CombinationState state = stack.pop();
            int start = state.start;
            int size = state.size;
            currentCombination = state.currentCombination;

            // If the combination size equals k, process it
            if (size == k) 
            {
                this.combinationQueue.add(List.of(currentCombination.clone())); // Add a copy to the queue
                continue;
            }

            // Add the next level of combinations to the stack
            for (int i = nodeList.size() - 1; i >= start; i--) 
            {
                currentCombination[size] = nodeList.get(i); // Add to the current combination

                // Determine if the new branch should be pruned
                if (size + 1 < k) 
                {
                    stack.push(new CombinationState(i + 1, size + 1, currentCombination.clone())); // Push the next state
                } 
                else if (trueAdjacents != null && size + 1 == k) 
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
                        logger.debug("Skipping combination due to no true adjacents: {}", List.of(currentCombination));
                        break;
                    } 
                    else 
                    {
                        this.combinationQueue.add(List.of(currentCombination.clone())); // Add a copy to the queue
                    }
                }
            }
        }
    }

    // Updated CombinationState class
    private static class CombinationState 
    {
        int start;
        int size;
        Click[] currentCombination;

        CombinationState(int start, int size, Click[] currentCombination) 
        {
            this.start = start;
            this.size = size;
            this.currentCombination = currentCombination;
        }
    }
}
package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        class State {
            int start;
            int size;
            int[] indices; // indices of selected elements

            State(int start, int size, int[] indices) {
                this.start = start;
                this.size = size;
                this.indices = indices;
            }
        }

        Deque<State> stack = new ArrayDeque<>();
        stack.push(new State(0, 0, new int[k]));

        while (!stack.isEmpty() && !this.combinationQueue.isItSolved()) 
        {
            State state = stack.pop();
            int start = state.start;
            int size = state.size;
            int[] indices = state.indices;

            if (size == k) 
            {
                Click[] combination = new Click[k];
                for (int j = 0; j < k; j++) {
                    combination[j] = nodeList.get(indices[j]);
                }
                this.combinationQueue.add(List.of(combination));
                continue;
            }

            for (int i = nodeList.size() - 1; i >= start; i--) 
            {
                int[] newIndices = indices.clone();
                newIndices[size] = i;

                if (size + 1 < k) 
                {
                    stack.push(new State(i + 1, size + 1, newIndices));
                } 
                else if (trueAdjacents != null && size + 1 == k) 
                {
                    Click[] combination = new Click[k];
                    for (int j = 0; j < k - 1; j++) {
                        combination[j] = nodeList.get(newIndices[j]);
                    }
                    combination[k - 1] = nodeList.get(i);

                    boolean shouldPrune = true;
                    for (Click click : combination) 
                    {
                        if (trueAdjacents.contains(click)) 
                        {
                            shouldPrune = false;
                            break;
                        }
                    }

                    if (shouldPrune) 
                    {
                        logger.debug("Skipping combination due to no true adjacents: {}", List.of(combination));
                        break;
                    } 
                    else 
                    {
                        this.combinationQueue.add(List.of(combination));
                    }
                }
                else if (size + 1 == k)
                {
                    Click[] combination = new Click[k];
                    for (int j = 0; j < k; j++) {
                        combination[j] = nodeList.get(newIndices[j]);
                    }
                    this.combinationQueue.add(List.of(combination));
                }
            }
        }
    }

    // // Only store start and size, not a full array
    // private static class CombinationState 
    // {
    //     int start;
    //     int size;

    //     CombinationState(int start, int size) 
    //     {
    //         this.start = start;
    //         this.size = size;
    //     }
    // }
}
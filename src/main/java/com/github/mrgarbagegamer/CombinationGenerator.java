package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Set<Click> trueAdjacents;
    private int firstClickStart, firstClickEnd;

    public CombinationGenerator(String threadName, CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks, Set<Click> trueAdjacents, int firstClickStart, int firstClickEnd) 
    {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.trueAdjacents = trueAdjacents;
        this.firstClickStart = firstClickStart;
        this.firstClickEnd = firstClickEnd;

        this.setName(threadName);
    }

    public void run() 
    {
        this.generateCombinationsIterative(this.possibleClicks, numClicks);
    }

    private void generateCombinationsIterative(List<Click> nodeList, int k) 
    {
        class State 
        {
            int start;
            int size;
            int[] indices; // indices of selected elements

            State(int start, int size, int[] indices) 
            {
                this.start = start;
                this.size = size;
                this.indices = indices;
            }
        }

        Deque<State> stack = new ArrayDeque<>();
        for (int i = firstClickStart; i < firstClickEnd; i++) 
        {
            int[] indices = new int[k];
            indices[0] = i;
            stack.push(new State(i + 1, 1, indices));
        }

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
        logger.info("Thread {} finished generating combinations for prefix range [{}-{})", getName(), firstClickStart, firstClickEnd);
        combinationQueue.generatorFinished();
    }
}
package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Set<Click> trueAdjacents;
    private int firstClickStart, firstClickEnd;
    private ConcurrentLinkedQueue<int[]> prefixQueue;
    private int prefixLength;

    public CombinationGenerator(String threadName, CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks, Set<Click> trueAdjacents, ConcurrentLinkedQueue<int[]> prefixQueue, int prefixLength) 
    {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.trueAdjacents = trueAdjacents;
        this.prefixQueue = prefixQueue;
        this.prefixLength = prefixLength;

        this.setName(threadName);
    }

    public void run() 
    {
        int[] prefix;
        while ((prefix = prefixQueue.poll()) != null && !combinationQueue.isItSolved()) {
            generateCombinationsWithPrefix(possibleClicks, numClicks, prefix);
        }
        combinationQueue.generatorFinished();
    }

    private void generateCombinationsWithPrefix(List<Click> nodeList, int k, int[] prefix) 
    {
        // Build the initial indices array
        int[] indices = new int[k];
        System.arraycopy(prefix, 0, indices, 0, prefix.length);

        class State 
        {
            int start;
            int size;
            int[] indices;

            State(int start, int size, int[] indices) 
            {
                this.start = start;
                this.size = size;
                this.indices = indices;
            }
        }

        Deque<State> stack = new ArrayDeque<>();
        stack.push(new State(prefix.length == 0 ? 0 : prefix[prefix.length - 1] + 1, prefix.length, indices));

        while (!stack.isEmpty() && !this.combinationQueue.isItSolved()) 
        {
            State state = stack.pop();
            int start = state.start;
            int size = state.size;
            int[] currIndices = state.indices;

            if (size == k) 
            {
                Click[] combination = new Click[k];
                for (int j = 0; j < k; j++) {
                    combination[j] = nodeList.get(currIndices[j]);
                }
                this.combinationQueue.add(List.of(combination));
                continue;
            }

            for (int i = nodeList.size() - 1; i >= start; i--) 
            {
                int[] newIndices = currIndices.clone();
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
    }
}
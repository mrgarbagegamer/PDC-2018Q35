package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Set<Click> trueAdjacents;
    private int firstClickStart, firstClickEnd;

    private static final int BATCH_SIZE = 1000; // Tune as needed

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
            int[] indices;

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

        List<List<Click>> batch = new ArrayList<>(BATCH_SIZE);

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
                batch.add(List.of(combination));
                if (batch.size() >= BATCH_SIZE) {
                    this.combinationQueue.addBatch(batch);
                    batch = new ArrayList<>(BATCH_SIZE);
                }
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
                        batch.add(List.of(combination));
                        if (batch.size() >= BATCH_SIZE) {
                            this.combinationQueue.addBatch(batch);
                            batch = new ArrayList<>(BATCH_SIZE);
                        }
                    }
                }
                else if (size + 1 == k)
                {
                    Click[] combination = new Click[k];
                    for (int j = 0; j < k; j++) {
                        combination[j] = nodeList.get(newIndices[j]);
                    }
                    batch.add(List.of(combination));
                    if (batch.size() >= BATCH_SIZE) {
                        this.combinationQueue.addBatch(batch);
                        batch = new ArrayList<>(BATCH_SIZE);
                    }
                }
            }
        }
        // Flush any remaining combinations in the batch
        if (!batch.isEmpty()) {
            this.combinationQueue.addBatch(batch);
        }
        logger.info("Thread {} finished generating combinations for prefix range [{}-{})", getName(), firstClickStart, firstClickEnd);
        combinationQueue.generatorFinished();
    }
}
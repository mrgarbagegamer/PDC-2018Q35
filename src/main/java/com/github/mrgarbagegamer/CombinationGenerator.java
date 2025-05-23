package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private final CombinationQueueArray queueArray;
    private final IntList possibleClicks;
    private final int numClicks;
    private final IntSet trueAdjacents;
    private final int firstClickStart, firstClickEnd;
    private final int numConsumers;
    private static final int BATCH_SIZE = 1000; // Tune as needed

    public CombinationGenerator(String threadName, CombinationQueueArray queueArray, IntList possibleClicks, int numClicks, IntSet trueAdjacents, int firstClickStart, int firstClickEnd, int numConsumers) 
    {
        super(threadName);
        this.queueArray = queueArray;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.trueAdjacents = trueAdjacents;
        this.firstClickStart = firstClickStart;
        this.firstClickEnd = firstClickEnd;
        this.numConsumers = numConsumers;
    }

    @Override
    public void run() 
    {
        generateCombinationsIterative(possibleClicks, numClicks);
    }

    private void generateCombinationsIterative(IntList nodeList, int k) 
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

        List<IntList> batch = new ArrayList<>(BATCH_SIZE);
        int roundRobinIdx = 0;

        while (!stack.isEmpty() && !queueArray.isSolutionFound()) 
        {
            State state = stack.pop();
            int start = state.start;
            int size = state.size;
            int[] indices = state.indices;

            if (size == k) 
            {
                IntList combination = new IntArrayList(k);
                for (int j = 0; j < k; j++)
                {
                    combination.add(nodeList.getInt(indices[j]));
                }
                batch.add(combination);
                if (batch.size() >= BATCH_SIZE) 
                {
                    // Round-robin distribute the batch
                    queueArray.getQueue(roundRobinIdx).addBatch(batch);
                    roundRobinIdx = (roundRobinIdx + 1) % numConsumers;
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
                    // Pruning logic: only add if at least one click is in trueAdjacents
                    boolean shouldPrune = true;
                    for (int j = 0; j < k - 1; j++) 
                    {
                        if (trueAdjacents.contains(nodeList.getInt(newIndices[j]))) 
                        {
                            shouldPrune = false;
                            break;
                        }
                    }
                    if (trueAdjacents.contains(nodeList.getInt(i))) 
                    {
                        shouldPrune = false;
                    }
                    if (shouldPrune) 
                    {
                        break;
                    } 
                    else 
                    {
                        IntArrayList combination = new IntArrayList(k);
                        for (int j = 0; j < k - 1; j++)
                        {
                            combination.add(nodeList.getInt(newIndices[j]));
                        }
                        combination.add(nodeList.getInt(i));
                        batch.add(combination);
                        if (batch.size() >= BATCH_SIZE) 
                        {
                            queueArray.getQueue(roundRobinIdx).addBatch(batch);
                            roundRobinIdx = (roundRobinIdx + 1) % numConsumers;
                            batch = new ArrayList<>(BATCH_SIZE);
                        }
                    }
                }
                else if (size + 1 == k) // fallback
                {
                    IntList combination = new IntArrayList(k);
                    for (int j = 0; j < k - 1; j++) 
                    {
                        combination.add(nodeList.getInt(newIndices[j]));
                    }
                    combination.add(nodeList.getInt(i));
                    batch.add(combination);
                    if (batch.size() >= BATCH_SIZE) 
                    {
                        queueArray.getQueue(roundRobinIdx).addBatch(batch);
                        roundRobinIdx = (roundRobinIdx + 1) % numConsumers;
                        batch = new ArrayList<>(BATCH_SIZE);
                    }
                }
            }
        }
        // Flush any remaining combinations in the batch
        if (!batch.isEmpty()) 
        {
            queueArray.getQueue(roundRobinIdx).addBatch(batch);
        }
        logger.info("Thread {} finished generating combinations for prefix range [{}-{})", getName(), firstClickStart, firstClickEnd);
        queueArray.generatorFinished();
    }
}
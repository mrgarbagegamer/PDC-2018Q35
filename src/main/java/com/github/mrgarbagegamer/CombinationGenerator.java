package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntList;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private final CombinationQueueArray queueArray;
    private final IntList possibleClicks;
    private final int numClicks;
    private final int[] trueAdjacents;
    private final int firstClickStart, firstClickEnd;
    private final int numConsumers;
    private static final int BATCH_SIZE = 2000; // Tune as needed

    public CombinationGenerator(String threadName, CombinationQueueArray queueArray, IntList possibleClicks, int numClicks, int[] trueAdjacents, int firstClickStart, int firstClickEnd, int numConsumers) 
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

        List<int[]> batch = new ArrayList<>(BATCH_SIZE);
        int roundRobinIdx = 0;
        int[] buffer = new int[k];

        while (!stack.isEmpty() && !queueArray.isSolutionFound()) 
        {
            State state = stack.pop();
            int start = state.start;
            int size = state.size;
            int[] indices = state.indices;

            if (size == k) 
            {
                addCombinationToBatch(nodeList, indices, buffer, batch, k);
                if (batch.size() >= BATCH_SIZE) 
                    roundRobinIdx = flushBatch(batch, roundRobinIdx);
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
                    if (!containsTrueAdjacent(nodeList, newIndices, k, trueAdjacents)) 
                        break;
                    addCombinationToBatch(nodeList, newIndices, buffer, batch, k);
                    if (batch.size() >= BATCH_SIZE) 
                        roundRobinIdx = flushBatch(batch, roundRobinIdx);
                }
                else if (size + 1 == k) 
                {
                    addCombinationToBatch(nodeList, newIndices, buffer, batch, k);
                    if (batch.size() >= BATCH_SIZE) 
                        roundRobinIdx = flushBatch(batch, roundRobinIdx);
                }
            }
        }
        // Flush any remaining combinations in the batch
        flushBatch(batch, roundRobinIdx);
        logger.info("Thread {} finished generating combinations for prefix range [{}-{})", getName(), firstClickStart, firstClickEnd);
        queueArray.generatorFinished();
    }
    
    private void addCombinationToBatch(IntList nodeList, int[] indices, int[] buffer, List<int[]> batch, int k) 
    {
        for (int j = 0; j < k; j++)
        { 
            buffer[j] = nodeList.getInt(indices[j]);
        }
        int[] combination = new int[k];
        System.arraycopy(buffer, 0, combination, 0, k);
        batch.add(combination);
    }

    private boolean containsTrueAdjacent(IntList nodeList, int[] indices, int k, int[] trueAdjacents) 
    {
        for (int j = 0; j < k; j++) 
        {
            int val = nodeList.getInt(indices[j]);
            for (int adj : trueAdjacents) 
            {
                if (val == adj) return true;
            }
        }
        return false;
    }

    private int flushBatch(List<int[]> batch, int roundRobinIdx)
    {
        while (!batch.isEmpty() && !queueArray.isSolutionFound()) 
        {
            boolean addedAny = false;
            for (int attempt = 0; attempt < numConsumers && !batch.isEmpty(); attempt++) 
            {
                int idx = (roundRobinIdx + attempt) % numConsumers;
                CombinationQueue queue = queueArray.getQueue(idx);
                int added = queue.addBatch(batch);
                if (added > 0) 
                {
                    batch.subList(0, added).clear();
                    roundRobinIdx = (idx + 1) % numConsumers;
                    addedAny = true;
                }
            }
            if (!addedAny) 
            {
                try 
                { 
                    Thread.sleep(5); 
                } 
                catch (InterruptedException e) 
                { 
                    Thread.currentThread().interrupt(); 
                    break; 
                }
            }
        }
        return roundRobinIdx;
    }
}
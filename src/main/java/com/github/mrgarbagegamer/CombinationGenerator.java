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

    private CombinationQueue combinationQueue;
    private IntList possibleClicks;
    private int numClicks;
    private IntSet trueAdjacents;
    private int firstClickStart, firstClickEnd;

    private static final int BATCH_SIZE = 1000; // Tune as needed

    public CombinationGenerator(String threadName, CombinationQueue combinationQueue, IntList possibleClicks, int numClicks, IntSet trueAdjacents, int firstClickStart, int firstClickEnd) 
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

        while (!stack.isEmpty() && !this.combinationQueue.isItSolved()) 
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
                    if (trueAdjacents.contains(nodeList.getInt(i))) // Review if this block is redundant or not (I think it is, but I don't want to remove it yet)
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
                            this.combinationQueue.addBatch(batch);
                            batch = new ArrayList<>(BATCH_SIZE);
                        }
                    }
                }
                else if (size + 1 == k) // This code would only run if trueAdjacents is null, which would never actually happen (since the puzzle we brute force is assumed to be unsolved), but it is kept for safety
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
                        this.combinationQueue.addBatch(batch);
                        batch = new ArrayList<>(BATCH_SIZE);
                    }
                }
            }
        }
        // Flush any remaining combinations in the batch
        if (!batch.isEmpty()) 
        {
            this.combinationQueue.addBatch(batch);
        }
        logger.info("Thread {} finished generating combinations for prefix range [{}-{})", getName(), firstClickStart, firstClickEnd);
        combinationQueue.generatorFinished();
    }
}
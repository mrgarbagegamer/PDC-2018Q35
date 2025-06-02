package com.github.mrgarbagegamer;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jctools.queues.MpmcArrayQueue;

public class CombinationQueue {
    private final int QUEUE_SIZE = 100_000;
    private final Queue<int[]> queue = new MpmcArrayQueue<>(QUEUE_SIZE);
    private final AtomicBoolean solutionFound;
    private final AtomicBoolean generationComplete;

    public CombinationQueue(AtomicBoolean solutionFound, AtomicBoolean generationComplete) {
        this.solutionFound = solutionFound;
        this.generationComplete = generationComplete;
    }

    public boolean add(int[] combinationClicks)
    {
        if (queue.offer(combinationClicks)) 
        {
            return true;
        }
        else 
        {
            return false;
        }
    }

    public int[] getClicksCombination() 
    {
        return queue.poll();
    }

    /**
     * Attempts to add as many elements from the batch as possible.
     * Returns the number of elements successfully added.
     */
    public int addBatch(List<int[]> batch)
    {
        int added = 0;
        for (int[] combination : batch) 
        {
            if (add(combination)) 
            {
                added++;
            } else 
            {
                break; // Stop at first failure (queue full or solution found)
            }
        }
        return added;
    }

    public boolean isSolutionFound() 
    {
        return solutionFound.get();
    }

    public boolean isGenerationComplete() 
    {
        return generationComplete.get();
    }

}
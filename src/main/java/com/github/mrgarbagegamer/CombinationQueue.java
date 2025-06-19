package com.github.mrgarbagegamer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MessagePassingQueue;

public class CombinationQueue 
{
    private final int QUEUE_SIZE = 100_000;
    private final MpmcArrayQueue<int[]> queue = new MpmcArrayQueue<>(QUEUE_SIZE);
    private final AtomicBoolean solutionFound;
    private final AtomicBoolean generationComplete;

    public CombinationQueue(AtomicBoolean solutionFound, AtomicBoolean generationComplete) 
    {
        this.solutionFound = solutionFound;
        this.generationComplete = generationComplete;
    }

    // Existing methods unchanged...
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
        return queue.relaxedPoll();
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

    // NEW: Batch operations using JCTools built-in methods
    
    /**
     * Efficiently fill queue using JCTools batch fill operation.
     * This should be much faster than individual relaxedOffer() calls.
     */
    public int fillFromBatch(List<int[]> batch) 
    {
        if (batch.isEmpty()) return 0;
        
        // Create a supplier that pulls from our batch
        final int[] batchIndex = {0}; // Mutable counter for lambda
        MessagePassingQueue.Supplier<int[]> supplier = () -> {
            if (batchIndex[0] < batch.size()) 
            {
                return batch.get(batchIndex[0]++);
            }
            return null; // Signal end of batch
        };
        
        // Use JCTools optimized fill operation
        int limit = Math.min(batch.size(), QUEUE_SIZE);
        return queue.fill(supplier, limit);
    }
    
    /**
     * Efficiently drain multiple combinations at once.
     * This should be much faster than individual relaxedPoll() calls.
     */
    public int drainToBatch(List<int[]> outputBatch, int maxElements) 
    {
        if (maxElements <= 0) return 0;
        
        // Create a consumer that adds to our output batch
        MessagePassingQueue.Consumer<int[]> consumer = outputBatch::add;
        
        // Use JCTools optimized drain operation
        return queue.drain(consumer, maxElements);
    }
    
    /**
     * Drain all available combinations efficiently
     */
    public int drainAllToBatch(List<int[]> outputBatch) 
    {
        MessagePassingQueue.Consumer<int[]> consumer = outputBatch::add;
        return queue.drain(consumer);
    }

    // Existing methods unchanged...
    public boolean isSolutionFound() 
    {
        return solutionFound.get();
    }

    public boolean isGenerationComplete() 
    {
        return generationComplete.get();
    }
}
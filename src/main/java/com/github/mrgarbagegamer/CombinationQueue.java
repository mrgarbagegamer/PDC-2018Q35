package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
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

    public boolean add(int[] combinationClicks)
    {
        if (queue.relaxedOffer(combinationClicks)) 
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

    /**
     * NEW: Adds combinations from an ArrayDeque rather than List
     * Returns the number of elements successfully added
     */
    public int addBatch(ArrayDeque<int[]> batch)
    {
        int added = 0;
        while (!batch.isEmpty()) 
        {
            int[] combination = batch.peekFirst(); // Don't remove until we know it was added
            if (add(combination)) 
            {
                batch.pollFirst(); // Only remove after successful add
                added++;
            } else 
            {
                break; // Stop at first failure (queue full or solution found)
            }
        }
        return added;
    }

    /**
     * Efficiently fill queue using JCTools batch fill operation with ArrayDeque.
     * This avoids ArrayList bounds checking overhead.
     */
    public int fillFromBatch(ArrayDeque<int[]> batch) 
    {
        if (batch.isEmpty()) return 0;
        
        // Create a supplier that pulls from our deque (O(1) removal from either end)
        MessagePassingQueue.Supplier<int[]> supplier = () -> {
            return batch.isEmpty() ? null : batch.pollFirst();
        };
        
        // Use JCTools optimized fill operation
        int limit = Math.min(batch.size(), QUEUE_SIZE);
        return queue.fill(supplier, limit);
    }
    
    /**
     * Legacy method for List compatibility
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
        int added = queue.fill(supplier, limit);
        
        // Remove the added elements from the batch
        if (added > 0) {
            batch.subList(0, added).clear();
        }
        
        return added;
    }
    
    /**
     * Efficiently drain multiple combinations at once using ArrayDeque.
     * This should be much faster than individual relaxedPoll() calls.
     */
    public int drainToBatch(ArrayDeque<int[]> outputBatch, int maxElements) 
    {
        if (maxElements <= 0) return 0;
        
        // Create a consumer that adds to our output deque
        MessagePassingQueue.Consumer<int[]> consumer = outputBatch::addLast;
        
        // Use JCTools optimized drain operation
        return queue.drain(consumer, maxElements);
    }

    /**
     * Drain all available combinations efficiently with ArrayDeque
     */
    public int drainAllToBatch(ArrayDeque<int[]> outputBatch) 
    {
        MessagePassingQueue.Consumer<int[]> consumer = outputBatch::addLast;
        return queue.drain(consumer);
    }

    // Original drain methods for List compatibility
    public int drainToBatch(List<int[]> outputBatch, int maxElements) 
    {
        if (maxElements <= 0) return 0;
        
        MessagePassingQueue.Consumer<int[]> consumer = outputBatch::add;
        return queue.drain(consumer, maxElements);
    }
    
    public int drainAllToBatch(List<int[]> outputBatch) 
    {
        MessagePassingQueue.Consumer<int[]> consumer = outputBatch::add;
        return queue.drain(consumer);
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
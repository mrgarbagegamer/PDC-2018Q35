package com.github.mrgarbagegamer;

import java.util.Deque;
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

    // NEW: WorkBatch integration methods

    /**
     * Efficiently drain combinations directly into WorkBatch.
     * Uses JCTools optimized drain operation with WorkBatch as consumer.
     */
    public int drainToWorkBatch(WorkBatch workBatch, int maxElements) 
    {
        if (maxElements <= 0 || workBatch.isFull()) return 0;
        
        // Limit by both requested amount and available space in WorkBatch
        int limit = Math.min(maxElements, workBatch.remainingCapacity());
        
        // Use JCTools optimized drain directly into WorkBatch
        return queue.drain(workBatch, limit);
    }

    /**
     * Drain all available combinations into WorkBatch.
     */
    public int drainAllToWorkBatch(WorkBatch workBatch) 
    {
        if (workBatch.isFull()) return 0;
        
        // Drain up to WorkBatch capacity
        return queue.drain(workBatch, workBatch.remainingCapacity());
    }

    // Keep existing Deque methods for backward compatibility
    private static final class DequeFirstSupplier<T> implements MessagePassingQueue.Supplier<T> 
    {
        private Deque<T> deque;

        void setDeque(Deque<T> deque) 
        {
            this.deque = deque;
        }

        @Override
        public T get() 
        {
            return deque.pollFirst();
        }
    }

    private static final class DequeLastConsumer<T> implements MessagePassingQueue.Consumer<T> 
    {
        private Deque<T> deque;

        void setDeque(Deque<T> deque) 
        {
            this.deque = deque;
        }

        @Override
        public void accept(T item) 
        {
            deque.offerLast(item);
        }
    }

    private static final ThreadLocal<DequeFirstSupplier<int[]>> SUPPLIER = ThreadLocal.withInitial(DequeFirstSupplier::new);
    private static final ThreadLocal<DequeLastConsumer<int[]>> CONSUMER = ThreadLocal.withInitial(DequeLastConsumer::new);

    /**
     * Legacy method - prefer drainToWorkBatch for better performance
     */
    public int drainToBatch(Deque<int[]> outputBatch, int maxElements) 
    {
        if (maxElements <= 0) return 0;
        
        DequeLastConsumer<int[]> consumer = CONSUMER.get();
        consumer.setDeque(outputBatch);
        
        return queue.drain(consumer, maxElements);
    }

    public int fillFromBatch(Deque<int[]> batch)
    {
        if (batch.isEmpty()) return 0;
        
        // Reuse supplier object
        DequeFirstSupplier<int[]> supplier = SUPPLIER.get();
        supplier.setDeque(batch);
        
        // Use JCTools optimized fill operation
        int limit = Math.min(batch.size(), QUEUE_SIZE);
        return queue.fill(supplier, limit);
    }

    public int drainAllToBatch(Deque<int[]> outputBatch) 
    {
        // Reuse consumer object
        DequeLastConsumer<int[]> consumer = CONSUMER.get();
        consumer.setDeque(outputBatch);

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
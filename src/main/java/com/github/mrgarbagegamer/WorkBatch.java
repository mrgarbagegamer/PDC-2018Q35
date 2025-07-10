package com.github.mrgarbagegamer;

import org.jctools.queues.MessagePassingQueue;

/**
 * High-performance circular buffer for worker thread batching.
 * Eliminates ArrayDeque overhead while maintaining the same semantics.
 */
public final class WorkBatch implements MessagePassingQueue.Consumer<int[]>, MessagePassingQueue.Supplier<int[]>
{
    private final int[][] buffer;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public WorkBatch(int capacity)
    {
        this.capacity = capacity;
        this.buffer = new int[capacity][];
    }

    /**
     * Adds a combination by copying its contents into a new pooled array.
     * This avoids the caller needing to clone.
     * @param source The source combination array.
     * @param length The number of elements to copy from the source.
     * @return true if the element was added, false if the batch is full.
     */
    public boolean add(int[] source, int length)
    {
        if (isFull())
        {
            return false;
        }
        // For now, we still clone here, but the key is that the generator doesn't.
        // A more advanced version would use an ArrayPool here.
        int[] newArr = new int[length];
        System.arraycopy(source, 0, newArr, 0, length);
        this.buffer[size++] = newArr;
        return true;
    }

    /**
     * Add combination to batch. Returns false if full.
     */
    public boolean add(int[] combination) 
    {
        if (size >= capacity) return false;
        
        buffer[tail] = combination;
        tail = (tail + 1) % capacity;
        size++;
        return true;
    }

    /**
     * Remove and return next combination. Returns null if empty.
     */
    public int[] poll() 
    {
        if (size == 0) return null;
        
        int[] result = buffer[head];
        buffer[head] = null; // Help GC
        head = (head + 1) % capacity;
        size--;
        return result;
    }

    /**
     * Check if batch is empty.
     */
    public boolean isEmpty() 
    {
        return size == 0;
    }

    /**
     * Get current batch size.
     */
    public int size() 
    {
        return size;
    }

    /**
     * Get remaining capacity.
     */
    public int remainingCapacity() 
    {
        return capacity - size;
    }

    /**
     * Clear all elements.
     */
    public void clear() 
    {
        while (!isEmpty()) 
        {
            poll();
        }
    }

    /**
     * MessagePassingQueue.Consumer implementation for JCTools integration.
     * This allows CombinationQueue to drain directly into WorkBatch.
     */
    @Override
    public void accept(int[] combination) 
    {
        add(combination);
    }

    /**
     * MessagePassingQueue.Supplier implementation for JCTools integration.
     * This allows CombinationQueue to poll directly from WorkBatch.
     */
    @Override
    public int[] get() 
    {
        return poll();
    }

    /**
     * Drain up to maxElements from this batch into another WorkBatch.
     * Used for work stealing between threads.
     */
    public int drainTo(WorkBatch targetBatch, int maxElements) 
    {
        int transferred = 0;
        while (transferred < maxElements && !this.isEmpty() && !targetBatch.isFull()) 
        {
            int[] combination = this.poll();
            if (combination != null && targetBatch.add(combination)) 
            {
                transferred++;
            } else 
            {
                // Put it back if target is full
                if (combination != null) 
                {
                    this.addFirst(combination);
                }
                break;
            }
        }
        return transferred;
    }

    /**
     * Check if batch is full.
     */
    public boolean isFull() 
    {
        return size >= capacity;
    }

    /**
     * Add to front of batch (for putting back elements).
     * Only used internally for work stealing edge cases.
     */
    private void addFirst(int[] combination) 
    {
        if (size >= capacity) return;
        
        head = (head - 1 + capacity) % capacity;
        buffer[head] = combination;
        size++;
    }
}
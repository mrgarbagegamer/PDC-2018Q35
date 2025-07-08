package com.github.mrgarbagegamer;

import org.jctools.queues.MessagePassingQueue;

/**
 * High-performance circular buffer for worker thread batching.
 * Eliminates ArrayDeque overhead while maintaining the same semantics.
 * This object is now pooled and recycled, with the assumption that only one thread will have access to the object at a time.
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
     * Adds a combination by copying its contents into the array at the tail of the buffer.
     * This avoids the caller needing to clone and prevents allocation of temporary objects.
     * @param source The source combination array.
     * @return true if the element was added, false if the batch is full.
     */
    public boolean add(int[] source) 
    {
        if (isFull())
        {
            return false;
        }
        if (buffer[tail] == null) buffer[tail] = new int[source.length];
        System.arraycopy(source, 0, buffer[tail], 0, source.length);
        tail = (tail + 1) % capacity;
        size++;
        return true;
    }

    /**
     * Remove and return next combination.
     * @return result if there is a valid combination in the array, null if the batch is empty.
     */
    public int[] poll() 
    {
        if (size == 0)
        {
            return null;
        }
        
        int[] result = buffer[head];
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
     * "Clears" the batch for reuse, making sure not to null the previous arrays (as this would force add() to create a new array).
     */
    public void clear() 
    {
        head = 0;
        tail = 0;
        size = 0;
    }

    /**
     * MessagePassingQueue.Consumer implementation for JCTools integration.
     */
    @Override
    public void accept(int[] combination) 
    {
        add(combination);
    }

    /**
     * MessagePassingQueue.Supplier implementation for JCTools integration.
     */
    @Override
    public int[] get() 
    {
        return poll();
    }

    /**
     * Check if batch is full.
     */
    public boolean isFull() 
    {
        return size >= capacity;
    }
}
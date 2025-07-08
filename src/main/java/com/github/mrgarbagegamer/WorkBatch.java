package com.github.mrgarbagegamer;

import org.jctools.queues.MessagePassingQueue;

/**
 * High-performance circular buffer for worker thread batching.
 * Eliminates ArrayDeque overhead while maintaining the same semantics.
 * This object is now pooled and recycled.
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
     * The generator is responsible for providing a new array instance.
     */
    public boolean add(int[] combination) 
    {
        if (size >= capacity)
        {
            return false;
        }
        
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
        if (size == 0)
        {
            return null;
        }
        
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
     * Clear all elements and reset pointers for recycling.
     * Nulls out references to help the GC.
     */
    public void clear() 
    {
        for (int i = 0; i < capacity; i++)
        {
            buffer[i] = null;
        }
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
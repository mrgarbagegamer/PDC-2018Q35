package com.github.mrgarbagegamer;


/**
 * High-performance pool for CombinationGeneratorTask arrays.
 * Uses simple circular buffer to eliminate ArrayDeque overhead.
 */
public final class SubtaskPool 
{
    private final CombinationGeneratorTask[][] taskArrays;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public SubtaskPool(int capacity) 
    {
        this.capacity = capacity;
        this.taskArrays = new CombinationGeneratorTask[capacity][];
    }

    /**
     * Get a task array from the pool.
     * Returns null if pool is empty.
     */
    public CombinationGeneratorTask[] get() 
    {
        if (size == 0) return null;
        
        CombinationGeneratorTask[] array = taskArrays[head];
        taskArrays[head] = null;
        head = (head + 1) % capacity;
        size--;
        return array;
    }

    /**
     * Return task array to pool if there's space.
     */
    public void put(CombinationGeneratorTask[] array) 
    {
        if (array == null || size >= capacity) return;
        
        // Clear the array before pooling
        for (int i = 0; i < array.length; i++) 
        {
            array[i] = null;
        }
        
        taskArrays[tail] = array;
        tail = (tail + 1) % capacity;
        size++;
    }

    /**
     * Check if pool is empty.
     */
    public boolean isEmpty() 
    {
        return size == 0;
    }

    /**
     * Get current pool size.
     */
    public int size() 
    {
        return size;
    }
}
package com.github.mrgarbagegamer;

/**
 * High-performance array pool using simple circular buffer.
 * Eliminates ArrayDeque overhead for array recycling.
 */
public final class ArrayPool 
{
    private final int[][] arrays;
    private final int maxSize;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public ArrayPool(int capacity, int maxSize) 
    {
        this.capacity = capacity;
        this.maxSize = maxSize;
        this.arrays = new int[capacity][];
    }

    /**
     * Get array of at least the specified size.
     * Returns null if pool is empty.
     */
    public int[] get(int minSize) 
    {
        if (size == 0) return null;
        
        int[] array = arrays[head];
        if (array != null && array.length >= minSize) 
        {
            arrays[head] = null;
            head = (head + 1) % capacity;
            size--;
            return array;
        }
        
        return null; // No suitable array found
    }

    /**
     * Return array to pool if there's space.
     */
    public void put(int[] array) 
    {
        if (array == null || size >= capacity || array.length > maxSize) return;
        
        arrays[tail] = array;
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
package com.github.mrgarbagegamer;

/**
 * High-performance array pool using simple circular buffer.
 * Eliminates ArrayDeque overhead for array recycling.
 */
public final class ArrayPool 
{
    private static int numClicks;
    private final short[][] arrays;
    private final int capacity;

    private boolean isPreallocated = false;

    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public ArrayPool(int capacity) 
    {
        this.capacity = capacity;
        this.arrays = new short[capacity][];
    }

    public static void setNumClicks(int numClicks) 
    {
        ArrayPool.numClicks = numClicks;
    }

    /**
     * Preallocate arrays of specified size.
     * Avoids allocation during runtime.
     */
    public void preallocate()
    {
        if (size > 0) return; // Avoid preallocation if pool is already in use
        for (int i = 0; i < capacity; i++) 
        {
            arrays[i] = new short[numClicks];
        }
        size = capacity; // Set size to capacity after preallocation
        head = 0; // Reset head to start of pool
        tail = 0; // Reset tail to start of pool
        isPreallocated = true; // Mark as preallocated
    }

    /**
     * Get array of at least the specified size.
     * Returns null if pool is empty.
     */
    public short[] get(int minSize) 
    {
        if (!isPreallocated) preallocate(); // Ensure preallocation if not done yet
        
        if (size == 0) return null;
        
        short[] array = arrays[head];
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
    public void put(short[] array) 
    {
        if (!isPreallocated) preallocate(); // Ensure preallocation if not done yet
        if (array == null || size >= capacity) return;
        
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
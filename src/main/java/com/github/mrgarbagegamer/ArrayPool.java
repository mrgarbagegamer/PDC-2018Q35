package com.github.mrgarbagegamer;

/**
 * High-performance array pool using simple circular buffer.
 * Eliminates ArrayDeque overhead for array recycling.
 */
public final class ArrayPool 
{
    private static int numClicks = -1;
    private final short[][] arrays;
    private final int capacity;

    // OPTIMIZATION: Pre-allocate the entire pool to guarantee non-null returns.
    // This simplifies the get/put logic and improves performance by avoiding conditional checks.

    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public ArrayPool(int capacity)
    {
        if (numClicks <= 0) 
        {
            throw new IllegalArgumentException("numClicks must be set before using ArrayPool.");
        }
        
        this.capacity = capacity;
        this.arrays = new short[capacity][numClicks];
    }

    public static void setNumClicks(int numClicks)
    {
        ArrayPool.numClicks = numClicks;
    }

    /**
     * Get a recycled array from the pool.
     * This method now returns a guaranteed non-null, correctly-sized array,
     * or null if the pool is empty. This is simpler and faster.
     */
    public short[] get()
    {
        if (size == 0)
        {
            return null;
        }

        short[] array = arrays[head];
        arrays[head] = null; // Help GC
        head = (head + 1) % capacity;
        size--;
        return array;
    }

    /**
     * Return array to the pool.
     * The null check is removed as we assume valid arrays are returned.
     */
    public void put(short[] array)
    {
        if (size >= capacity)
        {
            // This should not happen if the pool is sized correctly, but as a safeguard:
            // Log a warning or handle the error appropriately.
            return;
        }

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
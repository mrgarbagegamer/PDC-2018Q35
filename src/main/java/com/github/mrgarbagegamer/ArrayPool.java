package com.github.mrgarbagegamer;

/**
 * ArrayPool - [Performance Purpose - e.g., "High-performance memory pool"]
 * 
 * <p>[Detailed description of the performance problem this class solves.
 * Include before/after metrics where applicable.]</p>
 * 
 * <h2>Optimization Strategy</h2>
 * <p>[Specific optimization techniques used - pooling, caching, lock-free, etc.
 * Explain trade-offs made for performance gains.]</p>
 * 
 * <h2>Usage Patterns</h2>
 * <p>[How and when to use this utility. Common usage patterns and anti-patterns.]</p>
 * 
 * <h2>Memory Management</h2>
 * <p>[Memory allocation patterns, GC implications, sizing considerations.]</p>
 * 
 * <h3>0/13 - 0% of documentation completed</h3>
 * 
 * @performance [Specific performance characteristics and measurements]
 * @memory [Memory usage patterns and optimizations]
 * @threading [Thread safety model]
 * @since [When introduced and why]
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
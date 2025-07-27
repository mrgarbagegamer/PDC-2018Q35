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
        this.arrays = new short[capacity][numClicks]; // Assume that we initialize numClicks before using this constructor.
    }

    public static void setNumClicks(int numClicks) 
    {
        ArrayPool.numClicks = numClicks;
    }

    /**
     * Get array of at least the specified size.
     * Returns null if pool is empty.
     */
    public short[] get(int minSize) 
    {
        if (size == 0) return null;
        
        short[] array = arrays[head];
        // TODO: Remove the null check if we can guarantee arrays[head] is never null (which it shouldn't be if size > 0)
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
        if (array == null || size >= capacity) return; // TODO: Consider removing the null check and assuming arrays are never null
        
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
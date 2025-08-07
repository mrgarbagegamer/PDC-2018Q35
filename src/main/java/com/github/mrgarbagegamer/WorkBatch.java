package com.github.mrgarbagegamer;

import org.jctools.queues.MessagePassingQueue;

/**
 * WorkBatch - [Performance Purpose - e.g., "High-performance memory pool"]
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
 * <h3>0/18 - 0% of documentation completed</h3>
 * 
 * @performance [Specific performance characteristics and measurements]
 * @memory [Memory usage patterns and optimizations]
 * @threading [Thread safety model]
 * @since [When introduced and why]
 */
public final class WorkBatch implements MessagePassingQueue.Consumer<short[]>, MessagePassingQueue.Supplier<short[]>
{
    private final short[][] buffer;
    private final int capacity;
    private static int numClicks;
    private int head = 0;
    private int tail = 0;
    private int remainingCapacity; // Replacement for size to avoid deoptimizations

    public WorkBatch(int capacity)
    {
        this.capacity = capacity;
        this.remainingCapacity = capacity;
        this.buffer = new short[capacity][numClicks];
    }

    public static void setNumClicks(int numClicks)
    {
        WorkBatch.numClicks = numClicks;
    }

    /**
     * Adds a combination by copying its contents into the array at the tail of the buffer.
     * This avoids the caller needing to clone and prevents allocation of temporary objects.
     * @param source The source combination array.
     * @return true if the element was added, false if the batch is full.
     */
    public boolean add(short[] source)
    {
        if (remainingCapacity == 0) // Check remaining capacity instead of size to avoid deoptimizations
        {
            return false;
        }
        System.arraycopy(source, 0, buffer[tail], 0, source.length);
        tail = (tail + 1) % capacity;
        remainingCapacity--;
        return true;
    }

    /**
     * OPTIMIZED: Adds a combination by assembling it from a prefix and a final element.
     * Made final for JIT inlining and removed null check since pre-allocation ensures dest is never null.
     */
    public final boolean add(short[] prefix, int prefixLength, short lastElement)
    {
        if (remainingCapacity == 0) // Check remaining capacity instead of size to avoid deoptimizations
        {
            return false;
        }
        final short[] dest = buffer[tail];

        // OPTIMIZATION: Removed null check - pre-allocation ensures dest is never null
        // Use native System.arraycopy for optimal performance
        System.arraycopy(prefix, 0, dest, 0, prefixLength);
        dest[prefixLength] = lastElement;

        tail = (tail + 1) % capacity;
        remainingCapacity--;
        return true;
    }

    /**
     * Remove and return next combination.
     * @return result if there is a valid combination in the array, null if the batch is empty.
     */
    public short[] poll()
    {
        if (remainingCapacity == capacity) // Check remaining capacity instead of size to avoid deoptimizations
        {
            return null;
        }

        short[] result = buffer[head];
        head = (head + 1) % capacity;
        remainingCapacity++;
        return result;
    }

    /**
     * Check if batch is empty.
     */
    public boolean isEmpty()
    {
        return remainingCapacity == capacity;
    }

    /**
     * Get current batch size.
     */
    public int size()
    {
        return capacity - remainingCapacity; // Calculate size based on remaining capacity
    }

    /**
     * "Clears" the batch for reuse, making sure not to null the previous arrays (as this would force add() to create a new array).
     */
    public void clear()
    {
        head = 0;
        tail = 0;
        remainingCapacity = capacity; // Reset remaining capacity to full
    }

    /**
     * MessagePassingQueue.Consumer implementation for JCTools integration.
     */
    @Override
    public void accept(short[] combination)
    {
        add(combination);
    }

    /**
     * MessagePassingQueue.Supplier implementation for JCTools integration.
     */
    @Override
    public short[] get()
    {
        return poll();
    }

    /**
     * Check if batch is full.
     */
    public boolean isFull()
    {
        return remainingCapacity == 0;
    }
}
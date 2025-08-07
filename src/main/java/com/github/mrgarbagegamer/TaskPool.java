package com.github.mrgarbagegamer;

/**
 * TaskPool - [Performance Purpose - e.g., "High-performance memory pool"]
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
 * <h3>0/10 - 0% of documentation completed</h3>
 * 
 * @performance [Specific performance characteristics and measurements]
 * @memory [Memory usage patterns and optimizations]
 * @threading [Thread safety model]
 * @since [When introduced and why]
 */
public class TaskPool
{
    private final CombinationGeneratorTask[] arrays;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public TaskPool(int capacity)
    {
        this.capacity = capacity;
        this.arrays = new CombinationGeneratorTask[capacity];
    }

    /**
     * Get a CombinationGeneratorTask from the pool, returning a new one if the pool is empty.
     */
    public CombinationGeneratorTask get()
    {
        if (size == 0)
        {
            return new CombinationGeneratorTask(); // Return a new task if the pool is empty
        }

        CombinationGeneratorTask task = arrays[head];
        arrays[head] = null; // Help GC
        head = (head + 1) % capacity;
        size--;
        return task;
    }

    /**
     * Return a CombinationGeneratorTask to the pool if there's space.
     */
    public void put(CombinationGeneratorTask task)
    {
        if (task == null || size >= capacity)
        {
            return;
        }

        arrays[tail] = task;
        tail = (tail + 1) % capacity;
        size++;
    }

    /**
     * Check if the pool is empty.
     */
    public boolean isEmpty()
    {
        return size == 0;
    }

    /**
     * Get the current pool size.
     */
    public int size()
    {
        return size;
    }
}

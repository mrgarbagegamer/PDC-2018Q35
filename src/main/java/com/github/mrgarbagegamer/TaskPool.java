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
 * <h3>3/11 - ~27.3% of documentation completed</h3>
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

    /**
     * Constructs a TaskPool with the specified capacity and pre-allocates all tasks.
     * @param capacity the maximum number of tasks the pool can hold. Must be greater than 0.
     * @throws IllegalArgumentException if capacity is less than or equal to 0.
     * @since 2025.07.02 - Custom Generator Pools
     * @performance O(n) time complexity due to pre-allocation of {@link #arrays tasks array.}
     * @memory Allocates memory for the specified number of tasks upfront to minimize runtime allocations.
     * @see #arrays
     * @see #capacity
     * @see #get()
     * @see #put(CombinationGeneratorTask)
     */
    public TaskPool(int capacity){
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.arrays = new CombinationGeneratorTask[capacity];
    }

    /**
     * Gets a {@link CombinationGeneratorTask} from the pool or creates a new one if the pool is empty.
     * 
     * <p>
     * Note that, unlike {@link ArrayPool#get()}, this method will never return <code>null</code>. If
     * the pool is empty, it will create and return a new task (though this should be avoided where
     * possible).
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * We need to ensure that the get operation is as fast as possible, as it is on the hot path of
     * combination generation. By using a {@link #arrays circular buffer} and {@link #TaskPool(int)
     * pre-allocating} all tasks, we can achieve an O(1) time complexity for this method. We avoid a
     * null check on the returned task (as this should never happen if the pool is sized correctly and
     * null tasks are never returned to the pool), which further improves performance, keeping only the
     * {@link #size} check at the start for short-circuiting purposes. If extra performance is needed,
     * we could remove this check as well, but this would risk causing <code>size</code> to go negative
     * and allow for the return of null tasks.
     * </p>
     * 
     * @return A {@link CombinationGeneratorTask} from the pool or a new one if the pool is empty.
     * @since 2025.07.02 - Custom Generator Pools
     * @performance O(1) time complexity, as it involves simple arithmetic and array access.
     * @memory Only allocates if the pool is empty, otherwise reuses existing objects.
     * @see #put(CombinationGeneratorTask)
     * @see #isEmpty()
     */
    public CombinationGeneratorTask get() {
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
     * Returns a {@link CombinationGeneratorTask} to the {@link #tail} of the pool. Returns immediately
     * if the pool is full or the task is null.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The put operation is also on the hot path, so it needs to be as fast as possible. By using a
     * {@link #arrays circular buffer} and {@link #TaskPool(int) pre-allocating} all tasks, we can
     * achieve O(1) performance for this operation as well. Unlike {@link ArrayPool#put(short[])}, we
     * perform a null check on the task being returned to avoid adding null tasks to the pool, providing
     * a safeguard against potential bugs in the calling code. This check isn't strictly necessary for
     * performance, though, so if maximum speed is required and the calling code is trusted, it could be
     * removed. On the other hand, the {@link #size} check is essential to prevent overfilling the pool,
     * which could lead to memory corruption and unwanted behavior when polling tasks later.
     * </p>
     * 
     * @param task The {@link CombinationGeneratorTask} to return to the pool.
     * @since 2025.07.02 - Custom Generator Pools
     * @performance O(1) time complexity, as it involves simple arithmetic and array access.
     * @memory Reuses existing objects, minimizing allocations.
     * @see #size
     * @see #get()
     * @see #TaskPool(int)
     */
    public void put(CombinationGeneratorTask task) {
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

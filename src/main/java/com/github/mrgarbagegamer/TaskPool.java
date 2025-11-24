package com.github.mrgarbagegamer;

/**
 * A non-thread-safe, high-performance object pool for recycling {@link CombinationGeneratorTask}
 * instances.
 *
 * <h2>Architectural Role</h2>
 * <p>
 * In the {@link java.util.concurrent.ForkJoinPool ForkJoinPool} architecture used by this solver,
 * thousands of {@link CombinationGeneratorTask} objects are created for recursive sub-problems.
 * Allocating a new task for each fork operation creates immense pressure on the garbage collector,
 * becoming a significant performance bottleneck.
 * </p>
 * 
 * <p>
 * This class provides a simple and efficient solution by implementing an object pool. Instead of
 * creating new tasks, generator threads acquire pre-allocated tasks from the pool and return them
 * after use. This strategy drastically reduces allocation rates and GC overhead.
 * </p>
 *
 * <h2>Implementation Details</h2>
 * <p>
 * The pool is implemented as a simple, {@link #arrays array-backed} circular buffer, which provides
 * {@code O(1)} time complexity for both {@link #get()} and {@link #put(CombinationGeneratorTask)}
 * operations.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <strong>not</strong> thread-safe. It is designed to be used within a
 * {@link ThreadLocal} context, where each {@link CombinationGeneratorTask} thread owns its
 * exclusive instance of the pool. This design avoids the need for synchronization, maximizing
 * performance in the hot path.
 * </p>
 *
 * @see ArrayPool
 * @see WorkBatch
 * @since 2025.07 - {@code TaskPool} Introduction
 * @performance O(1) for both {@link #get()} and {@link #put(CombinationGeneratorTask)} operations.
 * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
 * @memory Fixed memory footprint based on the {@link #capacity} specified at construction.
 */
public class TaskPool {
    /**
     * The array of pooled tasks, managed as a circular buffer.
     * 
     * @see #capacity
     * @see #head
     * @see #size
     * @see #tail
     * @see #get()
     * @see #put(CombinationGeneratorTask)
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} accesses of elements in the array.
     * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
     * @memory Fixed memory footprint of ~{@code capacity × 4} bytes as an array of references.
     */
    private final CombinationGeneratorTask[] arrays;
    /**
     * The maximum number of tasks the pool can hold.
     * 
     * @see #arrays
     * @see #head
     * @see #size
     * @see #tail
     * @see #put(CombinationGeneratorTask)
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} access.
     * @threading Thread-safe as a {@code final} field.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private final int capacity;
    /**
     * The index of the next task to be retrieved from the pool.
     * 
     * <p>
     * The head index tracks where the next available task is located in the circular {@link #arrays
     * buffer}. It is incremented each time a task is obtained from the pool, wrapping around to the
     * start of the buffer when it reaches the end, implementing a circular buffer mechanism.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a circular buffer allows for efficient use of the pre-allocated tasks, minimizing memory
     * usage while still allowing for fast {@link #put(CombinationGeneratorTask) put()} and
     * {@code get()} operations. The head index is updated in constant time, ensuring that {@code get}
     * operations remain efficient even as the pool fills up and empties out.
     * </p>
     * 
     * <p>
     * We could use a {@code short} for the indices to save a few bytes of memory, but the performance
     * difference is negligible and using an {@code int} avoids potential overflow issues in
     * long-running applications (plus, Java treats arithmetic with {@code short} values weirdly). We
     * could also use a single pointer for both {@code head} and {@link #tail}, but that would
     * complicate the logic and force an extra arithmetic operation on each operation, which could
     * impact performance.
     * </p>
     * 
     * @see #capacity
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} access.
     * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private int head = 0;
    /**
     * The index where the next returned task will be placed.
     * 
     * <p>
     * The tail index tracks where the next returned array should be placed in the circular
     * {@link #arrays buffer}. It is incremented each time an array is returned to the pool, wrapping
     * around to the start of the buffer when it reaches the end, implementing a circular buffer
     * mechanism.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a circular buffer allows for efficient use of the pre-allocated array, minimizing memory
     * usage while still allowing for fast {@code put} and {@link #get() get} operations. The tail index
     * is updated in constant time, ensuring that {@code put} operations remain efficient even as the
     * pool fills up and empties out.
     * </p>
     * 
     * <p>
     * We could use a {@code short} for the indices to save a few bytes of memory, but the performance
     * difference is negligible and using an {@code int} avoids potential overflow issues in
     * long-running applications (plus, Java treats arithmetic with {@code short} values weirdly). We
     * could also use a single pointer for both {@link #head} and tail, but that would complicate the
     * logic and force an extra arithmetic operation on each operation, which could impact performance.
     * </p>
     * 
     * @see #capacity
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} access.
     * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private int tail = 0;
    /**
     * The current number of tasks available in the pool.
     * 
     * <p>
     * The {@code size} field tracks how many tasks are currently stored in the pool. It is
     * incremented each time a task is returned to the pool via
     * {@link #put(CombinationGeneratorTask) put} and decremented each time a task is obtained from
     * the pool via {@link #get() get}. This field is crucial for ensuring that we do not exceed the
     * pool's {@link #capacity} and for determining if the pool is empty.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The {@code size} field is updated in constant time during both {@code put} and get
     * operations, ensuring that these operations remain efficient. It is also used to quickly check
     * if the pool is empty (via {@link #isEmpty()}).
     * </p>
     * 
     * <p>
     * We could use a {@code short} for the {@code size} to save a few bytes of memory, but the
     * performance difference is negligible and using an {@code int} avoids potential overflow
     * issues in long-running applications (plus, Java treats arithmetic with {@code short} values
     * weirdly). We could avoid the {@code size} field entirely by using the {@link #head} and
     * {@link #tail} indices to calculate the size on-the-fly (or by storing the remaining
     * capacity), but that would complicate the logic and add extra arithmetic operations to the hot
     * path of both {@code put} and {@code get} operations, which could impact performance. We also
     * don't anticipate the {@code size} coming down to 0, so the deoptimization risk is minimal.
     * </p>
     * 
     * @since 2025.07 - Custom Generator Pools
     * @performance O(1) access and updates.
     * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private int size = 0;

    /**
     * Constructs a {@code TaskPool} with the specified {@code capacity} and pre-allocates tasks.
     *
     * @param capacity The maximum number of tasks the pool can hold. Must be greater than 0.
     * @throws IllegalArgumentException if capacity is not positive.
     * @see #arrays
     * @see #capacity
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(capacity)} pre-allocation of tasks.
     * @threading Thread-safe by nature of construction.
     * @memory Allocates a {@code CombinationGeneratorTask[capacity]}.
     */
    public TaskPool(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.arrays = new CombinationGeneratorTask[capacity];
        
        // Pre-allocate all tasks
        for (int i = 0; i < capacity; i++) {
            this.arrays[i] = new CombinationGeneratorTask();
        }
        
        this.size = capacity;
    }

    /**
     * Retrieves a task from the pool.
     * 
     * <p>
     * If the pool is empty, a new {@link CombinationGeneratorTask} is created to prevent stalls rather
     * than returning {@code null}. This fallback allocation is a performance anti-pattern and indicates
     * that the pool may be undersized for the current workload.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This operation is on the hot path of combination generation and must be extremely fast. The
     * implementation uses a circular buffer for {@code O(1)} complexity. A {@code null} check on the
     * retrieved task is avoided, as the pool should never contain {@code null}s if used correctly. The
     * initial {@code size} check provides a fast path for the empty-pool case. Removing it could risk
     * {@code size} becoming negative and returning a {@code null} task, so it is kept as a safety
     * measure.
     * </p>
     *
     * @return A recycled or newly created {@link CombinationGeneratorTask}.
     * @see #isEmpty()
     * @see #put(CombinationGeneratorTask)
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} time complexity.
     * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
     * @memory Only allocates if the pool is empty, otherwise reuses existing tasks.
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
     * Returns a task to the pool for recycling.
     * 
     * <p>
     * If the pool is full, the task is discarded and will be garbage collected. This indicates that the
     * pool may be oversized.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This operation is also on the hot path. A {@code null} check is performed as a safeguard against
     * programming errors, though it could be removed in trusted-caller scenarios for a marginal speed
     * gain. The check against {@code capacity} is essential to prevent overwriting tasks in the
     * circular buffer, which could lead to corruption.
     * </p>
     *
     * @param task The {@link CombinationGeneratorTask} to return to the pool.
     * @see #size
     * @see #TaskPool(int)
     * @see #get()
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} array access and update.
     * @memory Does not allocate.
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
     * Checks if the pool is empty.
     *
     * @return {@code true} if the pool is empty, {@code false} otherwise.
     * @see #size
     * @see #get()
     * @see #put(CombinationGeneratorTask)
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} field access and comparison.
     * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
     * @memory Does not allocate.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the current number of available tasks in the pool.
     *
     * @return The number of tasks in the pool.
     * @see #size
     * @see #get()
     * @see #isEmpty()
     * @see #put(CombinationGeneratorTask)
     * @since 2025.07 - {@code TaskPool} Introduction
     * @performance {@code O(1)} field access.
     * @threading Not thread-safe; intended for use in a {@link ThreadLocal} context.
     * @memory Does not allocate.
     */
    public int size() {
        return size;
    }
}

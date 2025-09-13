package com.github.mrgarbagegamer;

/**
 * TaskPool - A high performance pool for pre-allocated {@link CombinationGeneratorTask} instances.
 * 
 * <p>
 * The golden rule of JVM optimizations is the following: <b>Don't allocate.</b> Allocations and
 * deallocations (GC) are expensive operations that can significantly impact performance, especially
 * in high-throughput or low-latency applications. By reusing objects through pooling mechanisms, we
 * can minimize the overhead associated with frequent allocations and deallocations, leading to more
 * efficient memory usage and improved application performance. This class implements a simple
 * object pool for {@link CombinationGeneratorTask} instances, allowing for the reuse of these
 * objects instead of creating new ones each time they are needed.
 * </p>
 * 
 * <h2>Optimization Strategy</h2>
 * <p>
 * This pool uses a circular buffer to manage the pre-allocated tasks, allowing for O(1) time
 * complexity for both {@link #get()} and {@link #put(CombinationGeneratorTask)} operations. The
 * pool is initialized with a fixed capacity, and tasks are reused as they are returned to the pool
 * after use. This approach minimizes memory allocations and helps to reduce GC pressure, leading to
 * better performance in scenarios where tasks are frequently created and destroyed.
 * </p>
 * 
 * <h2>Usage Patterns</h2>
 * <p>
 * This class should be used in scenarios where {@link CombinationGeneratorTask} instances are
 * frequently created and destroyed, such as in multi-threaded combination generation tasks. Each
 * thread should have its own instance of TaskPool to avoid concurrency issues, as this class is not
 * thread-safe. Tasks should be obtained from the pool using {@link #get()} and returned to the pool
 * using {@link #put(CombinationGeneratorTask)} when they are no longer needed. Do not retain a
 * reference to a task after returning to the pool, since another thread could obtain it via
 * stealing.
 * </p>
 * 
 * <h2>Memory Management</h2>
 * <p>
 * The pool pre-allocates a fixed number of tasks at construction time, which helps to minimize
 * runtime allocations. The size of the pool should be chosen based on the expected workload and
 * memory constraints of the application. If the pool is exhausted (i.e., all tasks are in use), the
 * {@link #get()} method will create and return a new task, though this should be avoided where
 * possible.
 * </p>
 * 
 * <h3>9/11 - ~81.8% of documentation completed</h3>
 * 
 * @since 2025.07.11 - TaskPool Introduction
 * @threading This class is <b>not</b> thread-safe. Each thread should have its own instance of
 *            TaskPool to avoid concurrency issues. Queues should be the only point of inter-thread
 *            communication.
 * @performance The pool uses a circular buffer to achieve O(1) time complexity for both get and put
 *              operations. Pre-allocating tasks minimizes runtime allocations, reducing GC pressure
 *              and improving performance in high-throughput scenarios.
 * @memory Pre-allocates a fixed number of tasks at construction time to minimize runtime
 *         allocations. The size of the pool should be chosen based on the expected workload and
 *         memory usage patterns of the application.
 * @see ArrayPool
 * @see WorkBatch
 */
public class TaskPool
{
    /**
     * The array of pre-allocated tasks forming the pool. Implemented as a circular buffer.
     * 
     * @since 2025.07.11 - TaskPool Introduction
     * @threading This field is <b>not</b> thread-safe. Each thread should have its own instance of
     *            TaskPool to avoid concurrency issues.
     * @performance O(1) time complexity for get and put operations due to circular buffer
     *              implementation.
     * @memory Pre-allocated at {@link #TaskPool(int) construction time} to minimize runtime
     *         allocations.
     * @see #capacity
     * @see #head
     * @see #size
     * @see #tail
     * @see #get()
     * @see #put(CombinationGeneratorTask)
     */
    private final CombinationGeneratorTask[] arrays;
    /**
     * The maximum number of tasks the pool can hold.
     * 
     * @since 2025.07.11 - TaskPool Introduction
     * @threading This field is immutable after construction, making it inherently thread-safe.
     * @performance Constant time access.
     * @memory Allocated once at construction time, minimal memory footprint.
     * @see #arrays
     * @see #head
     * @see #size
     * @see #tail
     * @see #put(CombinationGeneratorTask)
     */
    private final int capacity;
    /**
     * The index of the next task to be {@link #get() retrieved} from the pool.
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
     * usage while still allowing for fast {@link #put(CombinationGeneratorTask) put} and get
     * operations. The head index is updated in constant time, ensuring that get operations remain
     * efficient even as the pool fills up and empties out.
     * </p>
     * 
     * <p>
     * We could use a short for the indices to save a few bytes of memory, but the performance
     * difference is negligible and using an int avoids potential overflow issues in long-running
     * applications (plus, Java treats arithmetic with short values weirdly). We could also use a single
     * pointer for both head and {@link #tail}, but that would complicate the logic and force an extra
     * arithmetic operation on each operation, which could impact performance.
     * </p>
     * 
     * @since 2025.07.11 - TaskPool Introduction
     * @threading This field is not thread-safe, as it is intended to be used within a single thread
     *            context.
     * @performance O(1) for both put and get operations.
     * @memory Minimal additional memory overhead (single int).
     * @see #capacity
     */
    private int head = 0;
    /**
     * The tail index for the next {@link #put(CombinationGeneratorTask) returned} task.
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
     * usage while still allowing for fast put and {@link #get() get} operations. The tail index is
     * updated in constant time, ensuring that put operations remain efficient even as the pool fills up
     * and empties out.
     * </p>
     * 
     * <p>
     * We could use a short for the indices to save a few bytes of memory, but the performance
     * difference is negligible and using an int avoids potential overflow issues in long-running
     * applications (plus, Java treats arithmetic with short values weirdly). We could also use a single
     * pointer for both {@link #head} and tail, but that would complicate the logic and force an extra
     * arithmetic operation on each operation, which could impact performance.
     * </p>
     * 
     * @since 2025.07.11 - TaskPool Introduction
     * @threading This field is not thread-safe, as it is intended to be used within a single thread
     *            context.
     * @performance O(1) for both put and get operations.
     * @memory Minimal additional memory overhead (single int).
     * @see #capacity
     */
    private int tail = 0;
    /**
     * The current number of tasks in the pool.
     * 
     * <p>
     * The size field tracks how many tasks are currently stored in the pool. It is incremented each
     * time a task is returned to the pool via {@link #put(CombinationGeneratorTask) put} and decremented each time a
     * task is obtained from the pool via {@link #get() get}. This field is crucial for ensuring that
     * we do not exceed the pool's {@link #capacity} and for determining if the pool is empty.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The size field is updated in constant time during both put and get operations, ensuring that
     * these operations remain efficient. It is also used to quickly check if the pool is empty (via
     * {@link #isEmpty()}).
     * </p>
     * 
     * <p>
     * We could use a short for the size to save a few bytes of memory, but the performance difference
     * is negligible and using an int avoids potential overflow issues in long-running applications
     * (plus, Java treats arithmetic with short values weirdly). We could avoid the size field entirely
     * by using the {@link #head} and {@link #tail} indices to calculate the size on-the-fly (or by
     * taking a {@link WorkBatch#remainingCapacity} approach and storing the remaining capacity), but
     * that would complicate the logic and add extra arithmetic operations to the hot path of both put
     * and get operations, which could impact performance. We also don't anticipate the size coming down
     * to 0, so the deoptimization risk is minimal.
     * </p>
     * 
     * @since 2025.07.02 - Custom Generator Pools
     * @threading The field is not thread-safe, as it is intended to be used within a single thread
     *            context.
     * @performance O(1) for both put and get operations.
     * @memory Minimal additional memory overhead (single int).
     */
    private int size = 0;

    /**
     * Constructs a TaskPool with the specified capacity and pre-allocates all tasks.
     * @param capacity the maximum number of tasks the pool can hold. Must be greater than 0.
     * @throws IllegalArgumentException if capacity is less than or equal to 0.
     * @since 2025.07.11 - TaskPool Introduction
     * @performance O(n) time complexity due to pre-allocation of {@link #arrays tasks array.}
     * @memory Allocates memory for the specified number of tasks upfront to minimize runtime allocations.
     * @see #arrays
     * @see #capacity
     * @see #get()
     * @see #put(CombinationGeneratorTask)
     */
    public TaskPool(int capacity) {
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
     * @since 2025.07.11 - TaskPool Introduction
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
     * @since 2025.07.11 - TaskPool Introduction
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

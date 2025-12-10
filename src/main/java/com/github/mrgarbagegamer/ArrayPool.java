package com.github.mrgarbagegamer;

/**
 * A high-performance, non-thread-safe object pool for recycling fixed-size {@code short[]} arrays.
 *
 * <p>
 * This pool is a critical component of the memory management strategy for the
 * {@link CombinationGeneratorTask}. Its primary purpose is to eliminate memory allocation on the
 * hot path by pre-allocating and reusing {@code prefix} arrays used during generation. The
 * fundamental rule of high-performance JVM tuning is to <strong>avoid allocations</strong>, and
 * this class is a key tool for adhering to that principle.
 * </p>
 *
 * <h2>Design and Implementation</h2>
 * <p>
 * The pool is implemented as a simple circular buffer using a 2D array, which provides {@code O(1)}
 * {@link #get()} and {@link #put(short[])} operations with minimal overhead. This design was chosen
 * for its simplicity and raw performance over more complex, and potentially higher-overhead,
 * lock-free data structures.
 * </p>
 *
 * <h2>Usage and Thread Safety</h2>
 * <p>
 * This class is <strong>not thread-safe</strong> and is designed to be used exclusively within a
 * {@link ThreadLocal} context. Each generator must own a dedicated instance of {@code ArrayPool} to
 * prevent race conditions.
 * </p>
 *
 * @see CombinationGeneratorTask
 * @see TaskPool
 * @since 2025.07 - Custom Generator Pools
 * @performance {@code O(1)} for both {@link #get()} and {@link #put(short[])} operations. The
 *              constructor is {@code O(capacity)} due to pre-allocation.
 * @threading This class is explicitly not thread-safe. All access must be confined to a single
 *            thread.
 * @memory Pre-allocated at construction to avoid runtime allocation, with a memory footprint
 *         determined by the {@link #capacity} and {@link #numClicks} fields.
 */
public final class ArrayPool {
    /**
     * The internal buffer storing the pre-allocated {@code short[]} arrays.
     *
     * <p>
     * This 2D array acts as the backing store for the circular buffer. It is allocated once at
     * construction with a fixed {@link #capacity}, and its arrays are recycled via the
     * {@link #head} and {@link #tail} pointers.
     * </p>
     *
     * @see #capacity
     * @see #numClicks
     * @see #ArrayPool(int)
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} access for {@link #get() get}/{@link #put(short[]) put} operations.
     * @threading Not thread-safe; must be confined to a single thread.
     * @memory Fixed memory footprint of ~{@code capacity * numClicks * 2} bytes as a
     *         {@code short[][]}.
     */
    private final short[][] arrays;
    /**
     * The maximum number of arrays the pool can hold.
     *
     * <p>
     * The {@code capacity} is set at {@link #ArrayPool(int) construction} and determines the
     * {@link #arrays buffer}'s memory footprint. A larger {@code capacity} reduces the chance of
     * the pool running out of arrays (which would return {@code null}), but increases initial
     * memory usage. The optimal size depends on the workload and the expected depth of the
     * recursive tasks.
     * </p>
     *
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} initialization and access.
     * @threading Thread-safe; immutable after construction.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private final int capacity;

    /**
     * The index of the next available array to be retrieved by {@link #get()}.
     *
     * <p>
     * This pointer advances through the circular buffer as arrays are dispensed. Using separate
     * {@code head} and {@link #tail} pointers was chosen over a single-pointer-with-size design to
     * remove arithmetic from the hot path of {@code get}/{@link #put(short[]) put} operations.
     * </p>
     *
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} access and update.
     * @threading Not thread-safe; must be confined to a single thread.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private int head = 0;
    /**
     * The index where the next recycled array will be placed by {@link #put(short[])}.
     *
     * <p>
     * This pointer advances through the {@link #arrays circular buffer} as arrays are returned.
     * Using separate {@link #head} and {@code tail} pointers was chosen over a
     * single-pointer-with-size design to remove arithmetic from the hot path of
     * {@link #get()}/{@code put} operations.
     * </p>
     *
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} access and update.
     * @threading Not thread-safe; must be confined to a single thread.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private int tail = 0;
    /**
     * The current number of arrays available in the pool.
     *
     * <p>
     * This value is decremented by {@link #get()} and incremented by {@link #put(short[])}. It is
     * used to check if the pool {@link #isEmpty() is empty} or full. While the size could be
     * calculated from the {@link #head} and {@link #tail} pointers, storing it explicitly makes the
     * checks trivial and avoids arithmetic in the hot path.
     * </p>
     *
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} access and update.
     * @threading Not thread-safe; must be confined to a single thread.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private int size = 0;

    /**
     * Constructs a new {@code ArrayPool} and pre-allocates its internal array buffer.
     *
     * <p>
     * All arrays are allocated upfront to prevent runtime allocation overhead. This constructor
     * will fail if {@link #numClicks} has not been set, ensuring a fail-fast approach to
     * configuration errors.
     * </p>
     *
     * @param capacity The maximum number of arrays the pool can hold. Must be positive.
     * @throws IllegalArgumentException if {@code capacity} is not positive or if {@link #numClicks}
     *                                  has not been set.
     * @see #arrays
     * @see #setNumClicks(int)
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(capacity)} pre-allocation of arrays.
     * @memory Allocates a {@code short[capacity][numClicks]} and an {@code ArrayPool} instance.
     */
    public ArrayPool(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0.");
        }

        this.capacity = capacity;
        final int numClicks = StartYourMonkeys.GlobalConfig.getNumClicks();
        this.arrays = new short[capacity][numClicks - 1];
        // Pre-allocated arrays are immediately available
        this.size = capacity;
    }

    /**
     * Retrieves an array from the pool.
     *
     * <p>
     * This is an {@code O(1)} operation. To improve performance on the hot path, this method
     * assumes the caller will handle a {@code null} return gracefully. The slot previously holding
     * the array is {@code null}ed out to assist the garbage collector, a micro-optimization that
     * can be beneficial in long-running, high-allocation scenarios.
     * </p>
     *
     * @return A pre-allocated {@code short[]} array, or {@code null} if the pool is empty.
     * @see #isEmpty()
     * @see #put(short[])
     * @see #size()
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} retrieval and field updates.
     * @memory Does not allocate.
     */
    public short[] get() {
        if (size == 0) {
            return null;
        }

        short[] array = arrays[head];
        arrays[head] = null; // Help GC
        head = (head + 1) % capacity;
        size--;
        return array;
    }

    /**
     * Returns an array to the pool, making it available for reuse.
     *
     * <p>
     * This is an {@code O(1)} operation. If the pool is already at full capacity, this method
     * returns immediately and the given array is effectively dropped. This can happen if arrays are
     * generated faster than they are consumed, but a properly sized pool should prevent this.
     * </p>
     *
     * @param array The {@code short[]} array to return to the pool. It is assumed to be
     *              non-{@code null} and of the {@link #numClicks correct size}.
     * @see #ArrayPool(int)
     * @see #get()
     * @see #setNumClicks(int)
     * @see #size()
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} insertion and field updates.
     * @memory Does not allocate.
     */
    public void put(short[] array) {
        if (size >= capacity) {
            // This should not happen if the pool is sized correctly, but as a safeguard:
            // Log a warning or handle the error appropriately.
            return;
        }

        arrays[tail] = array;
        tail = (tail + 1) % capacity;
        size++;
    }

    /**
     * Checks if the pool contains no available arrays.
     *
     * @return {@code true} if the pool is empty, {@code false} otherwise.
     * @see #get()
     * @see #size()
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} retrieval and comparison.
     * @memory Does not allocate.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of arrays currently available in the pool.
     *
     * @return The current number of arrays in the pool.
     * @see #get()
     * @see #isEmpty()
     * @see #put(short[])
     * @since 2025.07 - Custom Generator Pools
     * @performance {@code O(1)} retrieval.
     * @memory Does not allocate.
     */
    public int size() {
        return size;
    }
}
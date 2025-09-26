package com.github.mrgarbagegamer;

/**
 * A high performance array pool for pre-allocated combination arrays. Each array is of fixed size
 * {@link #numClicks}.
 * 
 * <p>
 * The golden rule of JVM optimizations is the following: <b>Don't allocate.</b> Allocations and
 * deallocations are expensive operations that can lead to frequent garbage collection pauses and
 * performance degradation. {@link CombinationGeneratorTask} is a particularly worrying case, as the
 * fork/join framework involves creating and destroying many tiny tasks and objects. Prefix arrays
 * are a prime candidate for pooling, as they are frequently allocated and deallocated during the
 * process of generation. By pre-allocating and pooling these arrays, we can reduce the number of
 * allocations on the hot path to zero and amortize the allocation cost over multiple operations,
 * which significantly improves performance.
 * </p>
 * 
 * <h2>Optimization Strategy</h2>
 * <p>
 * Rather than implementing a complex lock-free queue or another advanced data structure, we use a
 * simple circular buffer to manage the pool of arrays. This approach is straightforward, minimizes
 * contention, and is easy to implement, though it requires us to isolate pools by thread. We could
 * directly implement this 2D array into the {@link CombinationGeneratorTask} class, but that would
 * lead to a lot of boilerplate code and make the class harder to read and maintain.
 * </p>
 * 
 * <h2>Usage Patterns</h2>
 * <p>
 * This pool is designed to be isolated by thread, giving each thread its own instance of the pool.
 * Avoidance of contention is a key design goal, as the pool is used in a highly concurrent
 * environment where multiple threads may be generating combinations simultaneously. Make sure to
 * set {@link #numClicks} before initializing any objects of this class by using the
 * {@link #setNumClicks(int)} method. Avoid passing arrays that have been handled by other threads,
 * as this can lead to unexpected behavior and bugs.
 * </p>
 * 
 * <h2>Memory Management</h2>
 * <p>
 * This pool is designed to minimize memory allocations and deallocations on the hot path by
 * pre-allocating all arrays upfront and reusing them. This approach reduces the pressure on the
 * garbage collector, at the cost of a larger initial memory footprint. All arrays should be of the
 * same size, which is determined by the {@code static} field {@link #numClicks}, which must be set
 * before using the pool (else an exception will be thrown).
 * </p>
 * 
 * @see CombinationGeneratorTask
 * @since 2025.07.02 - Custom Generator Pools
 * @performance {@code O(1)} operations for both {@link #get()} and {@link #put(short[])} methods.
 * @threading This class is <b>not</b> thread-safe. Each thread should have its own instance of
 *            {@link ArrayPool} to avoid contention. The pool is designed to be used in a
 *            {@link java.lang.ThreadLocal thread-local} context, with all arrays being handled by
 *            the same thread.
 * @memory Preallocated at construction, with a fixed size determined by the {@link #capacity} and
 *         {@link #numClicks} fields.
 */
public final class ArrayPool {
    /**
     * The size of each array in the pool. This field must be set <b>before</b> using the
     * {@link ArrayPool} class, as pre-allocation depends on this value.
     * 
     * <p>
     * For JVM optimization purposes, it's most beneficial to perform all object allocations at the
     * start of the program and reuse them throughout the program's lifetime. This avoids the overhead
     * of frequent allocations and deallocations, which can lead to performance degradation due to
     * garbage collection. However, we can't allocate arrays of variable size, so we need some field to
     * determine the size of each array in the pool. This field fills that role.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This field is made static to ensure that it is shared across all instances of the
     * {@code ArrayPool} class. Ideally, we would make this field {@code final}, but that would require
     * us to set it in a {@code static} block, either through a system property or a configuration file,
     * which is not ideal for our use case (though we could pursue this in the future). Instead, we
     * provide {@link #setNumClicks(int) a setter} method to let users set this value and throw an
     * exception if it is not set before creating an instance of the {@code ArrayPool}.
     * </p>
     * 
     * <p>
     * Since {@code numClicks} is restricted in range to a number between {@code 1} and {@code 109}, we
     * could technically use a {@code byte} or {@code short} to store this value, but we use an
     * {@code int} for simplicity (and because Java does weird stuff to {@code byte}s/{@code short}s
     * during arithmetic operations).
     * </p>
     * 
     * @see #ArrayPool(int)
     * @see #setNumClicks(int)
     * @since 2025.07.24 - {@code ArrayPool} Pre-allocation
     * @performance {@code O(1)} access and modification, since it's a {@code static} field.
     * @memory The field itself is a primitive {@code int}, so it has a negligible memory footprint.
     */
    private static int numClicks = -1;
    /**
     * An array of pre-allocated {@code short} arrays, each of size {@link #numClicks}.
     * 
     * <p>
     * We need a structure that can internally manage multiple arrays of the same size, one with quick
     * accesses and minimal overhead. The 2D array structure is chosen for its simplicity and performance.
     * </p>
     * 
     * @see #capacity
     * @see #numClicks
     * @see #ArrayPool(int)
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} access time for both getting and putting arrays.
     * @memory The pool is pre-allocated with a fixed capacity, which is determined by the
     *         {@link #capacity} parameter, and holds arrays of size {@link #numClicks}.
     * @optimization Preallocates arrays to avoid frequent allocations and deallocations.
     */
    private final short[][] arrays;
    /**
     * The maximum number of arrays the pool can hold.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The capacity of the pool is a crucial parameter that determines how many arrays can be
     * {@link #ArrayPool(int) pre-allocated} and managed by the pool. A larger {@code capacity} reduces
     * the likelihood of contention and improves performance, but it also increases the memory footprint
     * of the pool. A smaller {@code capacity} saves memory but may lead to more frequent allocations
     * and deallocations, which can degrade performance. The optimal {@code capacity} depends on the
     * specific use case and workload. Try to size the pool appropriately based on the expected number
     * of concurrent threads and frequency of array usage, and lean towards a larger pool to minimize
     * the risk of contention.
     * </p>
     * 
     * @see #arrays
     * @see #ArrayPool(int)
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} access time, as it's a {@code final} field.
     * @memory The field itself is a primitive {@code int}, so it has a negligible memory footprint.
     */
    private final int capacity;

    /**
     * The {@code head} index for {@link #get()} operations.
     * 
     * <p>
     * The {@code head} index tracks where the next available array is located in the circular
     * {@link #arrays buffer}. It is incremented each time an array is obtained from the pool, wrapping
     * around to the start of the buffer when it reaches the end, implementing a circular buffer
     * mechanism.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a circular buffer allows for efficient use of the pre-allocated array, minimizing memory
     * usage while still allowing for fast {@link #put(short[]) put} and {@code get} operations. The
     * {@code head} index is updated in constant time, ensuring that {@code get} operations remain
     * efficient even as the pool fills up and empties out.
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
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} for {@link #get()} operations.
     * @threading The index is not thread-safe, as it is intended to be used within a single thread
     *            context.
     * @memory Minimal additional memory overhead (single {@code int}).
     */
    private int head = 0;
    /**
     * The {@code tail} index for {@link #put(short[])} operations.
     * 
     * <p>
     * The {@code tail} index tracks where the next returned array should be placed in the circular
     * {@link #arrays buffer}. It is incremented each time an array is returned to the pool, wrapping
     * around to the start of the buffer when it reaches the end, implementing a circular buffer
     * mechanism.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a circular buffer allows for efficient use of the pre-allocated array, minimizing memory
     * usage while still allowing for fast {@code put} and {@link #get() get} operations. The
     * {@code tail} index is updated in constant time, ensuring that {@code put} operations remain
     * efficient even as the pool fills up and empties out.
     * </p>
     * 
     * <p>
     * We could use a {@code short} for the indices to save a few bytes of memory, but the performance
     * difference is negligible and using an {@code int} avoids potential overflow issues in
     * long-running applications (plus, Java treats arithmetic with {@code short} values weirdly). We
     * could also use a single pointer for both {@link #head} and {@code tail}, but that would
     * complicate the logic and force an extra arithmetic operation on each operation, which could
     * impact performance.
     * </p>
     * 
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} for {@code put} operations.
     * @threading The index is not thread-safe, as it is intended to be used within a single thread
     *            context.
     * @memory Minimal additional memory overhead (single {@code int}).
     */
    private int tail = 0;
    /**
     * The current number of arrays in the pool.
     * 
     * <p>
     * The {@code size} field tracks how many arrays are currently stored in the pool. It is incremented
     * each time an array is returned to the pool via {@link #put(short[]) put} and decremented each
     * time an array is obtained from the pool via {@link #get() get}. This field is crucial for
     * ensuring that we do not exceed the pool's {@link #capacity} and for determining if the pool is
     * empty.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The {@code size} field is updated in constant time during both {@code put} and {@code get}
     * operations, ensuring that these operations remain efficient. It is also used to quickly check if
     * the pool is empty (via {@link #isEmpty()}).
     * </p>
     * 
     * <p>
     * We could use a {@code short} for the size to save a few bytes of memory, but the performance
     * difference is negligible and using an {@code int} avoids potential overflow issues in
     * long-running applications (plus, Java treats arithmetic with {@code short} values weirdly). We
     * could avoid the {@code size} field entirely by using the {@link #head} and {@link #tail} indices
     * to calculate the size on-the-fly (or by taking a {@link WorkBatch#remainingCapacity} approach and
     * storing the remaining capacity), but that would complicate the logic and add extra arithmetic
     * operations to the hot path of both {@code put} and {@code get} operations, which could impact
     * performance. We also don't anticipate the {@code size} coming down to {@code 0}, so the
     * deoptimization risk is minimal.
     * </p>
     * 
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} for both {@code put} and {@code get} operations.
     * @threading The field is not thread-safe, as it is intended to be used within a single thread
     *            context.
     * @memory Minimal additional memory overhead (single {@code int}).
     */
    private int size = 0;

    /**
     * Constructs a new {@code ArrayPool} with the specified {@code capacity}. Pre-allocates all arrays
     * to a size of {@link #numClicks}.
     * 
     * <p>
     * Validation is performed to ensure that {@link #numClicks} has been set and that the
     * {@code capacity} is greater than zero. If these conditions are not met, an
     * {@link java.lang.IllegalArgumentException IllegalArgumentException} will be thrown.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The constructor pre-allocates all arrays in the pool to avoid allocations on the hot path. This
     * ensures that both the {@link #get()} and {@link #put(short[])} methods can operate in
     * {@code O(1)} time without any additional overhead.
     * </p>
     * 
     * @param capacity the maximum number of arrays the pool can hold. Must be greater than zero.
     * @throws IllegalArgumentException if {@link #numClicks} is not set or if {@code capacity} is less
     *                                  than or equal to zero.
     * @see #arrays
     * @see #setNumClicks(int)
     * @since 2025.07.02 - Custom Generator Pools
     * @performance <code>O({@link #capacity})</code> time complexity for the constructor due to
     *              pre-allocation of arrays.
     * @memory Allocates a fixed amount of memory upfront based on the {@code capacity} and
     *         {@link #numClicks}.
     * @optimization Pre-allocates all arrays to avoid allocations on the hot path.
     */
    public ArrayPool(int capacity) {
        if (numClicks <= 0) {
            throw new IllegalArgumentException("numClicks must be set before using ArrayPool.");
        } else if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0.");
        }
        
        this.capacity = capacity;
        this.arrays = new short[capacity][numClicks];
    }

    /**
     * Sets the size of each array in the pool. This method must be called before creating any instances
     * of the class, as the pool relies on this value for {@link #ArrayPool(int) pre-allocation}.
     * 
     * @param numClicks the size of each array in the pool. Must be greater than zero.
     * @throws IllegalArgumentException if {@code numClicks} is less than or equal to zero.
     * @see #numClicks
     * @see #ArrayPool(int)
     * @since 2025.07.24 - {@code ArrayPool} Pre-allocation
     * @performance {@code O(1)} time complexity for setting the value.
     * @threading This method is not thread-safe. It should be called once during application
     *            initialization, before any threads start using the pool.
     * @memory Does not allocate any additional memory, as it only sets a static field. However, future
     *         allocations of {@code ArrayPool} instances will depend on this value.
     */
    public static void setNumClicks(int numClicks) {
        if (numClicks <= 0) {
            throw new IllegalArgumentException("numClicks must be greater than 0.");
        }
        ArrayPool.numClicks = numClicks;
    }

    /**
     * Obtains an array from the {@link #head} of the pool, returning {@code null} if the pool is empty.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * We need to ensure that the {@code get} operation is as fast as possible, as it is on the hot path
     * of combination generation. By using a {@link #arrays circular buffer} and {@link #ArrayPool(int)
     * pre-allocating} all arrays, we can achieve {@code O(1)} performance for this operation. We avoid
     * a {@code null} check on the returned array (as this should never happen if the pool is sized
     * correctly and {@code null} arrays aren't passed to the {@code put} method), which further
     * improves performance, keeping only the {@link #size} check at the start of the method for
     * short-circuiting purposes. If extra performance is needed, we could remove this check as well,
     * but that would risk causing {@code size} to go negative.
     * </p>
     * 
     * @return A pre-allocated {@code short} array of size {@link #numClicks}, or {@code null} if the
     *         pool is empty.
     * @see #size
     * @see #isEmpty()
     * @see #put(short[])
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} time complexity, as it involves simple arithmetic and array access.
     * @memory No additional memory allocation occurs during this operation, as arrays are
     *         pre-allocated.
     * @optimization Removes {@code null} checks by ensuring the pool is pre-allocated and properly
     *               managed.
     */
    public short[] get() {
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
     * Returns an array to the {@link #tail} of the pool. Returns immediately if the pool is full.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The {@code put} operation is also on the hot path, so it needs to be as fast as possible. By
     * using a {@link #arrays circular buffer} and {@link #ArrayPool(int) pre-allocating} all arrays, we
     * can achieve {@code O(1)} performance for this operation as well. We avoid a {@code null} check on
     * the input array to improve performance, assuming that the user will not pass {@code null} arrays
     * (as this would indicate a bug in the calling code). We do, however, check if the pool is full to
     * prevent overwriting existing arrays and incrementing the {@link #size} beyond the
     * {@link #capacity}. If extra performance is needed, we could remove this check as well, but since
     * tasks are stolen between threads, a full pool is a real possibility.
     * </p>
     * 
     * @param array A short array of size {@link #numClicks} to return to the pool. This array should
     *              not be {@code null} and should not be used by other threads.
     * @see #size
     * @see #ArrayPool(int)
     * @see #get()
     * @see #setNumClicks(int)
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} time complexity, as it involves simple arithmetic and array access.
     * @memory No additional memory allocation occurs during this operation, as the input array is added
     *         to the pool.
     * @optimization Removes {@code null} checks by assuming well-formed input and ensuring the pool is
     *               properly managed.
     */
    public void put(short[] array) {
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
     * Checks if the pool is empty.
     * 
     * @return {@code true} if the pool is empty, {@code false} otherwise.
     * @see #get()
     * @see #size()
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} time complexity, as it involves a simple comparison.
     * @threading Not thread-safe. Should be used within a single thread context.
     * @memory No additional memory allocation occurs during this operation.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Gets the current number of arrays in the pool.
     * 
     * @return The current number of arrays in the pool.
     * @see #get()
     * @see #isEmpty()
     * @see #put(short[])
     * @since 2025.07.02 - Custom Generator Pools
     * @performance {@code O(1)} time complexity, as it involves a simple field access.
     * @threading Not thread-safe. Should be used within a single thread context.
     * @memory No additional memory allocation occurs during this operation.
     */
    public int size() {
        return size;
    }
}
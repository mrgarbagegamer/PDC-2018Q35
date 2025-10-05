package com.github.mrgarbagegamer;

import org.jctools.queues.MessagePassingQueue;

/**
 * A high-performance, reusable container for batching puzzle combinations
 * 
 * <p>
 * This class is a cornerstone for the solver's performance architecture. It functions as a custom,
 * fixed-size circular buffer that groups thousands of individual {@code short[]} puzzle
 * combinations into a single object. This batching strategy is crucial for reducing contention on
 * {@link CombinationQueue combination queues} and minimizing synchronization overhead between
 * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}.
 * </p>
 * 
 * <h2>Architecture Role and Performance Impact</h2>
 * <p>
 * In high-performance computing, frequent queue operations can become a major bottleneck. This was
 * the case in earlier versions of this solver, where generators enqueued millions of individual
 * combinations, consuming 30-40% of CPU time in queue management alone.
 * </p>
 * 
 * <p>
 * {@code WorkBatch} fundamentally solves this problem by changing the unit of work transfer.
 * Instead of individual arrays, generators now fill a {@code WorkBatch} and enqueue it once.
 * Monkeys dequeue the entire batch and process its contents. This approach reduces the number of
 * queue operations by several orders of magnitude (from millions to hundreds), effectively
 * eliminating the queue as a bottleneck and dramatically improving cache locality.
 * </p>
 * 
 * <h2>Memory Management</h2>
 * <p>
 * To achieve near-zero garbage collection pressure in the hot-path, {@code WorkBatch} instances are
 * designed to be reusable. The internal {@link #buffer} is pre-allocated once at construction.
 * After a monkey finishes processing a batch, the batch object is returned to the a central pool
 * managed by {@link CombinationQueueArray}, making it available for generators to use again. This
 * pooling strategy, combined with the pre-allocated buffer, nearly eliminates runtime memory
 * allocation for combination data.
 * </p>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <strong>not</strong> thread-safe. An instance of {@code WorkBatch} must only be
 * accessed by a single thread at a time. The architecture enforces this by design: a generator
 * thread owns a batch while filling it, and a monkey owns it after dequeuing it. Queues are the
 * sole mechanism for safely transferring ownership between threads.
 * </p>
 * 
 * @see ArrayPool
 * @see TaskPool
 * @since 2025.07 - {@code WorkBatch} Introduction
 * @performance {@code O(1)} for poll operations and simple state checks, {@code O(numClicks)} for
 *              add operations due to array copying.
 * @memory Fixed memory usage based on the {@link #capacity capacity} and {@link #numClicks number
 *         of clicks}, minimizing dynamic allocations.
 * @threading This class is <b>not</b> thread-safe, as each instance of {@code WorkBatch} is
 *            intended to be used by one thread at a time. Queues should be the only point of
 *            inter-thread communication.
 */
public final class WorkBatch implements MessagePassingQueue.Consumer<short[]>, MessagePassingQueue.Supplier<short[]> {
    /**
     * The pre-allocated circular buffer storing the {@code short[]} combinations.
     *
     * <p>
     * This buffer is the core of the {@code WorkBatch}. It is allocated once at construction and
     * continuously reused to store combination data. This strategy is fundamental to the solver's
     * low-allocation approach, as it avoids the immense garbage collection pressure that would result
     * from creating new arrays for billions of combinations.
     * </p>
     * 
     * @see #WorkBatch(int)
     * @see #add(short[])
     * @see #poll()
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} for {@code poll} operations, {@code O(numClicks)} for {@code add}
     *              operations due to array copying.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Fixed memory footprint of ~{@code capacity × numClicks × 2} bytes as a {@code short[][]}.
     */
    private final short[][] buffer;
    /**
     * The maximum number of combinations this batch can hold.
     *
     * <p>
     * Batch capacity is a critical tuning parameter. A larger capacity reduces the frequency of
     * expensive queue operations but increases memory footprint and may introduce latency if batches
     * take too long to fill. A smaller capacity has the opposite effect. The optimal value balances
     * these trade-offs to maximize throughput.
     * </p>
     * 
     * @see #buffer
     * @see #WorkBatch(int)
     * @see #add(short[])
     * @see #isFull()
     * @see CombinationGeneratorTask#BATCH_SIZE
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} field access.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Fixed memory footprint of 4 bytes as an {@code int}.
     */
    private final int capacity;
    /**
     * The number of clicks (elements) in each combination array.
     *
     * <p>
     * This {@code static} value determines the size of the inner arrays within the {@link #buffer}. It
     * must be configured via {@link #setNumClicks(int)} once at application startup before any
     * {@code WorkBatch} instances are created. This ensures that all pre-allocated buffers have the
     * correct dimensions for the target puzzle.
     * </p>
     * 
     * @see #buffer
     * @see #add(short[], int, short)
     * @see #setNumClicks(int)
     * @since 2025.07 - NPE in {@code WorkBatch} Operations Fix
     * @performance {@code O(1)} for access.
     * @threading Thread-safe after initialization.
     * @memory Fixed memory footprint of 4 bytes as an {@code int}.
     */
    private static int numClicks;
    /**
     * The index of the next combination to be read from the circular {@link #buffer}.
     *
     * <p>
     * This pointer advances when {@link #poll()} is called and wraps around the buffer, enabling
     * efficient, continuous reads without reallocating memory. Using separate {@code head} and
     * {@link #tail} integer pointers is a deliberate choice for performance, as it avoids the
     * arithmetic overhead that a single-pointer implementation would require on every
     * {@code add}/{@code poll} operation.
     * </p>
     *
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} for {@code poll} operations.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Fixed memory footprint of 4 bytes as an {@code int}.
     */
    private int head = 0;
    /**
     * The index of the next available slot for writing into the circular {@link #buffer}.
     *
     * <p>
     * This pointer advances when an item is added via {@link #add(short[])} or
     * {@link #add(short[], int, short)} and wraps around the buffer. Using an {@code int} instead of a
     * {@code short} avoids potential overflow issues and unusual JVM arithmetic handling for shorts,
     * while the use of a separate tail pointer (from {@link #head}) minimizes computational overhead in
     * the hot path.
     * </p>
     *
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} for {@code add} operations.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Fixed memory footprint of 4 bytes as an {@code int}.
     */
    private int tail = 0;
    /**
     * Tracks the number of available slots in the batch.
     *
     * <p>
     * This counter is used instead of a traditional {@code size} field. Checking against remaining
     * capacity (e.g., {@code remainingCapacity == 0}) is often more favorable to JIT compiler
     * optimizations and branch prediction than checking a variable size. It decrements on
     * {@link #add(short[], int, short) add} and increments on {@link #poll() poll}.
     * </p>
     * 
     * @see #capacity
     * @see #add(short[])
     * @see #poll()
     * @since 2025.07 - Remaining Capacity Tracking
     * @performance {@code O(1)} updates and comparisons.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Fixed memory footprint of 4 bytes as an {@code int}.
     */
    private int remainingCapacity; // Replacement for size to avoid deoptimizations

    /**
     * Constructs a new {@code WorkBatch} with a pre-allocated {@link #buffer internal buffer}.
     *
     * <p>
     * The {@code buffer} size is determined by the specified {@code capacity} and the static
     * {@link #numClicks} value, which must be set via {@link #setNumClicks(int)} before calling this
     * constructor.
     * </p>
     *
     * @param capacity The maximum number of combinations the batch can hold.
     * @throws IllegalArgumentException if capacity is not a positive integer.
     * @throws IllegalStateException    if {@link #numClicks} has not been set to a positive value.
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(capacity × numClicks)} for initial buffer allocation, {@code O(1)} for
     *              construction.
     * @threading Thread-safe during construction; the resulting instance is not thread-safe.
     * @memory Allocates a {@code short[capacity][numClicks]}.
     */
    public WorkBatch(int capacity) {
        if (numClicks <= 0) {
            throw new IllegalStateException("numClicks must be set to a positive value before creating WorkBatch instances.");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be a positive integer.");
        }

        this.capacity = capacity;
        this.remainingCapacity = capacity;
        this.buffer = new short[capacity][numClicks];
    }

    /**
     * Sets the {@code static} {@link #numClicks number of clicks} for all {@code WorkBatch} instances.
     *
     * <p>
     * This method must be called once during application initialization before any batches are created.
     * It ensures all {@link #WorkBatch(int) pre-allocated} {@link #buffer buffers} have the correct
     * dimensions for the puzzle being solved.
     * </p>
     *
     * @param numClicks The number of elements in each combination array.
     * @since 2025.07 - Pre-allocation of {@code WorkBatch} Buffers
     * @performance {@code O(1)} for field assignment.
     * @threading Not thread-safe (at the moment); must be called once before any instances are created.
     * @memory Does not allocate.
     */
    public static void setNumClicks(int numClicks) {
        WorkBatch.numClicks = numClicks;
    }

    /**
     * Adds a combination to the batch by {@link System#arraycopy(Object, int, Object, int, int)
     * copying} it into the {@link #buffer internal buffer}.
     *
     * <p>
     * This method is a hot path, optimized to be allocation-free. It uses {@code System.arraycopy()} to
     * transfer the combination data into the {@link #WorkBatch(int) pre-allocated} {@code buffer},
     * avoiding the overhead of creating new array objects.
     * </p>
     *
     * @param source The combination array to add. The caller must ensure this is not {@code null}.
     * @return {@code true} if the combination was added, or {@code false} if the batch is full.
     * @see #isFull()
     * @see #setNumClicks(int)
     * @see CombinationGeneratorTask
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(numClicks)} array copying, {@code O(1)} capacity checks and pointer
     *              updates.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate; reuses the pre-allocated arrays in {@code buffer}.
     */
    public boolean add(short[] source) {
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
     * Adds a combination by assembling it directly into the {@link #buffer internal buffer} from a
     * {@code prefix} and a final element.
     *
     * <p>
     * This highly optimized method is a critical performance enhancement for the
     * {@link CombinationGeneratorTask}. It allows the generator to construct the final combination at
     * the leaf nodes of its recursion tree without allocating a temporary array. Instead, the
     * {@code prefix} is {@link System#arraycopy(Object, int, Object, int, int) copied} and the last
     * element is appended directly into the target slot in the {@code buffer}, eliminating an
     * intermediate array copy and allocation.
     * </p>
     *
     * @param prefix       The prefix of the combination. The caller must ensure this is not
     *                     {@code null}.
     * @param prefixLength The length of the prefix to copy.
     * @param lastElement  The final element to append to the combination.
     * @return {@code true} if the combination was added, or {@code false} if the batch is full.
     * @see #isFull()
     * @see #setNumClicks(int)
     * @see CombinationGeneratorTask
     * @since 2025.07 - Assembling Combinations in the {@code WorkBatch}
     * @performance {@code O(1)} array access in {@link #buffer}, {@code O(prefixLength)} array copy.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate; reuses the pre-allocated arrays in {@code buffer}.
     */
    public final boolean add(short[] prefix, int prefixLength, short lastElement) {
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
     * Retrieves the next combination from the batch without allocating a new array.
     *
     * <p>
     * This method returns a direct reference to the array within the internal {@link #buffer}. The
     * returned array is not {@code null}ed out in the buffer and will be overwritten by subsequent
     * {@link #add(short[], int, short) add} operations once the circular buffer wraps around. This is a
     * key performance feature, but it means the caller must process the returned array before it is
     * overwritten.
     * </p>
     *
     * @return The next combination array, or {@code null} if the batch is empty.
     * @see #buffer
     * @see #add(short[], int, short)
     * @see #clear()
     * @see #isEmpty()
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} for polling operations.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     * @memory The memory footprint is fixed based on the capacity and {@link #numClicks}, minimizing
     *         dynamic allocations. The method does not allocate any new arrays, instead reusing the
     *         pre-allocated arrays in {@link #buffer}.
     */
    public short[] poll() {
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
     * Checks if the batch contains no combinations.
     *
     * @return {@code true} if the batch is empty, {@code false} otherwise.
     * @see #capacity
     * @see #remainingCapacity
     * @see #isFull()
     * @see #size()
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} retrievals and comparison.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate.
     */
    public boolean isEmpty() {
        return remainingCapacity == capacity;
    }

    /**
     * Returns the number of combinations currently stored in the batch.
     *
     * <p>
     * This is calculated from the {@link #capacity} and {@link #remainingCapacity} to avoid the
     * potential for JIT deoptimizations associated with a separate, mutable {@code size} field.
     * </p>
     *
     * @return The number of combinations in the batch.
     * @see #isEmpty()
     * @see #isFull()
     * @since 2025.07 - Remaining Capacity Tracking
     * @performance {@code O(1)} calculation.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate.
     */
    public int size() {
        return capacity - remainingCapacity; // Calculate size based on remaining capacity
    }

    /**
     * Resets the batch to an empty state, making it ready for reuse.
     *
     * <p>
     * This {@code O(1)} operation simply resets the internal pointers and counters. It does not modify
     * the underlying {@link #buffer} array, allowing the batch to be efficiently recycled without any
     * deallocation or reallocation.
     * </p>
     * 
     * @see #WorkBatch(int)
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} resets.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate or deallocate.
     */
    public void clear() {
        head = 0;
        tail = 0;
        remainingCapacity = capacity; // Reset remaining capacity to full
    }

    /**
     * Legacy JCTools integration method.
     *
     * <p>
     * This was used in a previous architecture where individual combinations were drained from a queue
     * into a {@code WorkBatch}. It is now unused since entire {@code WorkBatch} objects are enqueued
     * directly.
     * </p>
     *
     * @param combination The combination to add.
     * @deprecated This method is no longer used in the current batch-based queuing architecture.
     * @see org.jctools.queues.MessagePassingQueue
     * @see org.jctools.queues.MessagePassingQueue.Consumer
     * @see org.jctools.queues.MessagePassingQueue.Consumer#accept(Object)
     * @since 2025.07 - {@code WorkBatch} Introduction
     * @performance {@code O(1)} call to {@link #add(short[])}.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate; reuses the pre-allocated arrays in {@link #buffer}.
     */
    @Override
    public void accept(short[] combination) {
        // TODO: Remove Consumer interface and this method from the class, since it is no longer used.
        add(combination);
    }

    /**
     * Legacy JCTools integration method.
     *
     * <p>
     * This was used in a previous architecture where a {@code WorkBatch} was used to fill a queue with
     * individual combinations. It is now unused since entire {@code WorkBatch} objects are enqueued
     * directly.
     * </p>
     *
     * @return The next combination from the batch.
     * @deprecated This method is no longer used in the current batch-based queuing architecture.
     * @see org.jctools.queues.MessagePassingQueue
     * @see org.jctools.queues.MessagePassingQueue.Supplier
     * @see org.jctools.queues.MessagePassingQueue.Supplier#get()
     * @since 2025.07 - {@code WorkBatch} Integration in Generators
     * @performance {@code O(1)} call to {@link #poll()}.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate; reuses the pre-allocated arrays in {@link #buffer}.
     */
    @Override
    public short[] get() {
        // TODO: Remove Supplier interface and this method from the class, since it is no longer used.
        return poll();
    }

    /**
     * Checks if the batch has reached its {@link #capacity maximum capacity}.
     *
     * @return {@code true} if the batch is full, {@code false} otherwise.
     * @see #capacity
     * @see #isEmpty()
     * @see #size()
     * @since 2025.07 - Remaining Capacity Tracking
     * @performance {@code O(1)} retrievals and comparison.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate.
     */
    public boolean isFull() {
        return remainingCapacity == 0;
    }
}
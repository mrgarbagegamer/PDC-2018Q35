package com.github.mrgarbagegamer;

import java.util.Arrays;

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
public final class WorkBatch {
    /**
     * The default {@link #capacity} for a batch. This serves as the target number of combinations to
     * store in a batch before flushing it to a {@link CombinationQueue}. This field has been moved from
     * {@link CombinationGeneratorTask} to here to centralize configuration related to batching.
     * 
     * <p>
     * Batching is a critical optimization that amortizes the high cost of concurrent queue operations.
     * Instead of enqueuing millions of individual combinations, generators group them into large
     * batches, reducing queue contention by several orders of magnitude.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The batch size is a trade-off:
     * <ul>
     * <li><b>Larger batches:</b> Reduce queue-related overhead and improve throughput by allowing
     * generators and {@link TestClickCombination monkeys} to work uninterrupted for longer. However,
     * they increase memory footprint and can lead to work-distribution latency.</li>
     * <li><b>Smaller batches:</b> Provide a more even flow of work to monkeys, but increase the
     * frequency of high-contention queue operations, which can become a bottleneck.</li>
     * </ul>
     * A value of {@value} was found to be a good balance for the development system.
     * </p>
     * 
     * @since 2025.10 - Reorganize Constants for Clarity
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Minimal memory footprint of 4 bytes as an {@code int}.
     */
    public static final int BATCH_SIZE = 8000;
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
     * @see #BATCH_SIZE
     * @see #WorkBatch(int)
     * @see #add(short[])
     * @see #isFull()
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
     * Constructs a new {@code WorkBatch} with a pre-allocated {@link #buffer internal buffer}. Uses the
     * default {@link #BATCH_SIZE} as the {@link #capacity}.
     *
     * <p>
     * The {@code buffer} size is determined by the specified {@code BATCH_SIZE} and the {@code static}
     * {@link #numClicks} value, which must be set via {@link #setNumClicks(int)} before calling this
     * constructor.
     * </p>
     * 
     * @throws IllegalStateException if {@link #numClicks} has not been set to a positive value.
     * @see #WorkBatch(int)
     * @since 2025.10 - Reorganize Constants for Clarity
     * @performance {@code O(BATCH_SIZE × numClicks)} for initial buffer allocation, {@code O(1)} for
     *              construction.
     * @threading Thread-safe during construction; the resulting instance is not thread-safe.
     * @memory Allocates a {@code short[BATCH_SIZE][numClicks]}.
     */
    public WorkBatch() {
        if (numClicks <= 0) {
            throw new IllegalStateException("numClicks must be set to a positive value before creating WorkBatch instances.");
        }

        this.capacity = BATCH_SIZE;
        this.remainingCapacity = capacity;
        this.buffer = new short[BATCH_SIZE][numClicks];
    }

    /**
     * Constructs a new {@code WorkBatch} with a pre-allocated {@link #buffer internal buffer}, using
     * the inputted {@code capacity}.
     *
     * <p>
     * The {@code buffer} size is determined by the specified {@code capacity} and the {@code static}
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
     * @throws IllegalArgumentException if {@code numClicks} is not a positive integer.
     * @since 2025.07 - Pre-allocation of {@code WorkBatch} Buffers
     * @performance {@code O(1)} for field assignment.
     * @threading Not thread-safe (at the moment); must be called once before any instances are created.
     * @memory Does not allocate.
     */
    public static void setNumClicks(int numClicks) {
        if (numClicks <= 0) {
            throw new IllegalArgumentException("numClicks must be a positive integer.");
        }
        WorkBatch.numClicks = numClicks;
    }

    /**
     * Resets the {@code static} {@link #numClicks number of clicks} to zero.
     * 
     * <p>
     * This method is intended for testing purposes only. It allows tests to reset the static state of
     * the {@code WorkBatch} class between test cases.
     * </p>
     * 
     * @see #setNumClicks(int)
     * @since 2025.11 - Testing Support
     * @performance {@code O(1)} for field assignment.
     * @threading Not thread-safe; intended for single-threaded test setups only.
     * @memory Does not allocate.
     */
    static void resetNumClicks() {
        numClicks = 0;
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
     * Adds a bulk of combinations to the batch from a source array of final clicks.
     *
     * <p>
     * This is a high-performance method designed for the leaf-generation hot path. It takes a common
     * {@code prefix} and a source array of {@code lastElements} and copies them into the internal
     * buffer in a tight loop. This is significantly faster than adding them one by one, as it avoids
     * repeated capacity checks and method call overhead.
     * </p>
     *
     * @param prefix The common prefix for all combinations.
     * @param prefixLength The length of the prefix.
     * @param lastElements The array of final clicks to append.
     * @param offset The starting offset in the {@code lastElements} array.
     * @param count The number of combinations to add, never less than 1.
     * @return The number of combinations successfully added.
     * @since 2025.10 - Bulk Add Optimization
     * @performance {@code O(count × prefixLength)} for array copying, {@code O(1)} for capacity checks
     *              outside the loop.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate; reuses the pre-allocated arrays in {@code buffer}.
     */
    public int addBulk(short[] prefix, int prefixLength, short[] lastElements, int offset, int count) {
        int added = 0;
        for (int i = 0; i < count; i++) {
            // Optimization: Remove the capacity check from inside the loop to prevent de-optimizations
            final short[] dest = buffer[tail];
            System.arraycopy(prefix, 0, dest, 0, prefixLength);
            dest[prefixLength] = lastElements[offset + i];
            tail = (tail + 1) % capacity;
            remainingCapacity--;
            added++;
        }
        return added;
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
     * Returns the number of additional combinations that can be added to the batch before it is
     * {@link #isFull() full}.
     * 
     * @return The {@link #remainingCapacity remaining capacity} of the batch.
     * @see #capacity
     * @see #isEmpty()
     * @since 2025.10 - Bulk Add Optimization
     * @performance {@code O(1)} field access.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate.
     */
    public int remainingCapacity() {
        return remainingCapacity;
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

    /**
     * Returns the {@link #capacity maximum number of combinations} this batch can hold. Often, this is
     * the same as {@link #BATCH_SIZE the batch size}.
     * 
     * @return The batch capacity.
     * @see #WorkBatch(int)
     * @see #isEmpty()
     * @see #isFull()
     * @see #size()
     * @since 2025.10 - Reorganize Constants for Clarity
     * @performance {@code O(1)} access time.
     * @threading Thread-safe; returns a {@code final} field.
     * @memory Does not allocate.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Returns a {@link String} representation of the batch for debugging purposes.
     * 
     * @return A string summarizing the batch's state, including its size, capacity, and the first
     *         combination (if not empty).
     * @see Arrays#toString(short[])
     * @since 2025.11 - WorkBatch toString Introduction
     * @performance {@code O(numClicks)} for converting the first combination to a string.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Allocates a small string for the representation.
     */
    @Override
    public String toString() {
        return "WorkBatch{size=" + size() + ", capacity=" + capacity + ", firstCombo=" + (isEmpty() ? "null" : Arrays.toString(buffer[head])) + "}";
    }

    /**
     * Compares this batch to another for equality based on their contents. Two batches are equal if they
     * have the same size, capacity, and identical combinations in the same order.
     * 
     * @param obj The object to compare against.
     * @return {@code true} if the batches have the same size, capacity, and identical combinations in
     *         the same order; {@code false} otherwise.
     * @see Arrays#equals(short[], short[])
     * @since 2025.11 - WorkBatch equals Implementation
     * @performance {@code O(n × numClicks)} in the worst case, where {@code n} is the number of
     *              combinations in the batch.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WorkBatch)) {
            return false;
        }
        WorkBatch other = (WorkBatch) obj;
        if (this.size() != other.size() || this.capacity != other.capacity) {
            return false;
        }
        for (int i = 0; i < this.size(); i++) {
            short[] thisCombo = this.buffer[(this.head + i) % this.capacity];
            short[] otherCombo = other.buffer[(other.head + i) % other.capacity];
            if (!Arrays.equals(thisCombo, otherCombo)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes a hash code for the batch based on its contents, size, and capacity.
     * 
     * @return The computed hash code.
     * @see Arrays#hashCode(short[])
     * @since 2025.11 - WorkBatch hashcode Implementation
     * @performance {@code O(n × numClicks)}, where {@code n} is the number of combinations in the
     *              batch.
     * @threading Not thread-safe; must be accessed by only one thread at a time.
     * @memory Does not allocate.
     */
    @Override
    public int hashCode() {
        int result = Integer.hashCode(size());
        result = 31 * result + Integer.hashCode(capacity);
        for (int i = 0; i < size(); i++) {
            short[] combo = buffer[(head + i) % capacity];
            result = 31 * result + Arrays.hashCode(combo);
        }
        return result;
    }
}
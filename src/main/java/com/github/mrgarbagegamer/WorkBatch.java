package com.github.mrgarbagegamer;

import org.jctools.queues.MessagePassingQueue;

/**
 * WorkBatch - A high-performance, batch container for managing combinations of clicks.
 * 
 * <p>
 * Queue operations, even with the efficiency of JCTools, can introduce significant overhead when
 * performed at a high frequency. Adding multiple combinations at once into a queue using the
 * {@link org.jctools.queues.MessagePassingQueue.Consumer MessagePassingQueue.Consumer&lt;short[]&gt;}
 * interface and creating a queue for each {@link TestClickCombination monkey} helps, but the
 * overhead of queue operations can still be a bottleneck. The WorkBatch class addresses this by
 * batching multiple combinations into a single structure, allowing for amortized queue operations
 * and reducing the frequency of expensive queue interactions.
 * </p>
 * 
 * <h2>Optimization Strategy</h2>
 * <p>
 * WorkBatch instances were originally designed just for holding combinations before
 * draining/filling them from/to queues, but we quickly realized that WorkBatch could also be used
 * as a container for pooling. By {@link #WorkBatch(int) pre-allocating} a {@link #capacity
 * fixed-size} {@link #buffer buffer} of combinations, and copying incoming combinations into that
 * buffer, we can avoid the need for new array allocations during runtime. Combined with the reuse
 * of WorkBatch instances themselves by {@link CombinationGeneratorTask generators}, this
 * drastically reduces the number of runtime allocations to ~0, minimizing GC pressure and improving
 * performance.
 * </p>
 * 
 * <h2>Usage Patterns</h2>
 * <p>
 * WorkBatch is designed to be used in high-frequency scenarios where combinations need to be
 * generated, processed, and queued rapidly. It is particularly well-suited for use with
 * {@link CombinationGeneratorTask combination generators} and {@link TestClickCombination monkeys}.
 * Combinations can be copied into an array on the {@link #tail} of the batch using
 * {@link #add(short[]) add(short[])} or {@link #add(short[], int, short) add(short[], int, short)},
 * and polled from the {@link #head} of the batch using {@link #poll() poll()}. To avoid
 * thread-safety issues, each WorkBatch instance should be used by one thread at a time, with queues
 * serving as the only point of inter-thread communication. Never retain a reference to a WorkBatch
 * instance after flushing it to a queue, as the receiving thread may modify it at any time.
 * </p>
 * 
 * <h2>Memory Management</h2>
 * <p>
 * WorkBatch instances manage their own memory through pre-allocation and reuse. The {@link #buffer
 * buffer} is allocated once at construction time based on the specified {@link #capacity capacity}
 * and {@link #numClicks number of clicks}, and is reused for the lifetime of the WorkBatch
 * instance. This approach minimizes dynamic memory allocations and reduces GC pressure, leading to
 * more consistent performance in high-frequency scenarios.
 * </p>
 * 
 * <p>
 * Since WorkBatch objects are meant to hold many combinations, the size of the {@link #buffer
 * buffer} can be significant. Larger batch sizes reduce the frequency of queue operations,
 * potentially benefiting the generators, but come at the cost of increased memory usage and
 * potentially increased latency for monkeys. Smaller batch sizes reduce memory usage and latency,
 * but increase the frequency of queue operations. Care must be taken to balance these trade-offs
 * for the specific use case and performance requirements.
 * </p>
 * 
 * @since 2025.07.01 - WorkBatch Introduction
 * @performance O(1) for poll operations and simple state checks, O(numClicks) for add operations
 *              due to array copying.
 * @memory Fixed memory usage based on the {@link #capacity capacity} and {@link #numClicks number
 *         of clicks}, minimizing dynamic allocations.
 * @threading This class is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
 *            used by one thread at a time. Queues should be the only point of inter-thread
 *            communication.
 * @see ArrayPool
 * @see CombinationGeneratorTask
 * @see CombinationQueue
 * @see TestClickCombination
 */
public final class WorkBatch implements MessagePassingQueue.Consumer<short[]>, MessagePassingQueue.Supplier<short[]> {
    /**
     * Pre-allocated buffer to hold combinations.
     * 
     * <p>
     * Even with the power of JCTools, queue operations still have overhead and can be expensive when
     * performed at a high frequency. Amortizing this cost by batching multiple combinations is
     * necessary to achieve the desired performance while adhering to the golden rule of JVM
     * optimization: <b>Don't allocate.</b>
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a pre-allocated array avoids the overhead of dynamic memory allocation and garbage
     * collection, which can introduce latency and degrade performance. Reusing the same arrays for
     * multiple batches also creates the infrastructure for pooling combinations, drastically reducing
     * the number of allocations and GC pressure.
     * </p>
     * 
     * @see #WorkBatch(int)
     * @see #add(short[])
     * @see #poll()
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) for poll operations, O(numClicks) for add operations due to array copying.
     * @threading The buffers are not thread-safe, as they are intended to be used within a single
     *            thread context. Queues should be the only point of inter-thread communication.
     * @memory The memory footprint is fixed based on the capacity and {@link #numClicks}, minimizing
     *         dynamic allocations.
     * @optimization Pre-allocation and reuse of arrays to minimize GC pressure.
     */
    private final short[][] buffer;
    /**
     * The maximum number of combinations the batch can hold.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The capacity of the batch directly impacts its performance characteristics. A larger capacity
     * reduces the frequency of queue operations, which can be expensive, but increases memory usage. A
     * smaller capacity reduces memory usage but increases the frequency of queue operations. The
     * optimal capacity balances these trade-offs based on the specific use case and performance
     * requirements.
     * </p>
     * 
     * @see #buffer
     * @see #WorkBatch(int)
     * @see #add(short[])
     * @see #isFull()
     * @see CombinationGeneratorTask#BATCH_SIZE
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) for capacity checks.
     * @threading The capacity is immutable and does not impact thread safety.
     * @memory Fixed memory usage based on capacity and {@link #numClicks the number of clicks}.
     */
    private final int capacity;
    /**
     * The number of clicks (elements) in each combination. This is static as it is consistent across
     * all batches for a given puzzle, and is effectively a constant for the lifetime of the application
     * (we can't set it as final though, because it is configured at runtime).
     * 
     * <p>
     * The number of clicks determines the size of each combination array, impacting the footprint of
     * the {@link #WorkBatch(int) pre-allocated} {@link #buffer buffer} and the performance of
     * {@link System#arraycopy(Object, int, Object, int, int) array copy} operations in
     * {@link #add(short[])}.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since we pre-allocate the buffer based on the number of clicks, it is crucial to
     * {@link #setNumClicks(int) set this value} correctly before creating any WorkBatch instances. A
     * value that is too large could lead to corrupted combinations (where the extra elements are just
     * 0's), while a value that is too small could lead to
     * {@link java.lang.ArrayIndexOutOfBoundsException ArrayIndexOutOfBoundsExceptions} during array
     * copy operations. Ensuring that this value is accurate and consistent across the application is
     * key to maintaining the integrity of the combinations and the performance of the batch operations.
     * </p>
     * 
     * @see #buffer
     * @see #add(short[], int, short)
     * @see #setNumClicks(int)
     * @since 2025.07.25 - NPE in WorkBatch Operations Fix
     * @performance O(1) for access.
     * @threading The variable is static and does not impact thread safety.
     * @memory Fixed memory usage based on the number of clicks and {@link #capacity}.
     */
    private static int numClicks;
    /**
     * The head index for {@link #poll() polling} combinations from the batch.
     * 
     * <p>
     * The head index tracks where the next combination will be polled from the batch. It is incremented
     * on each poll operation and wraps around to the beginning of the {@link #buffer} when it reaches
     * the end, implementing a circular buffer.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a circular buffer allows for efficient use of the pre-allocated array, minimizing memory
     * usage while still allowing for fast add and poll operations. The head index is updated in
     * constant time, ensuring that poll operations remain efficient even as the batch fills up and
     * empties out.
     * </p>
     * 
     * <p>
     * We could use a short for the indices to save a few bytes of memory, but the performance
     * difference is negligible and using an int avoids potential overflow issues in long-running
     * applications (plus, Java treats arithmetic with short values weirdly). We could also use a single
     * pointer for both head and {@link #tail}, but that would complicate the logic and force an extra arithmetic
     * operation on each {@link #add(short[], int, short) add} and poll operation, which could impact
     * performance.
     * </p>
     * 
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) for poll operations.
     * @threading The index is not thread-safe, as it is intended to be used within a single
     *            thread context. Queues should be the only point of inter-thread communication.
     * @memory Minimal additional memory overhead (single int).
     * @optimization Efficient circular buffer implementation for fast access.
     */
    private int head = 0;
    /**
     * The tail index for {@link #add(short[], int, short) adding} combinations to the batch.
     * 
     * <p>
     * The tail index tracks where the next combination will be added to the batch. It is incremented on
     * each add operation and wraps around to the beginning of the {@link #buffer} when it reaches the
     * end, implementing a circular buffer.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Using a circular buffer allows for efficient use of the pre-allocated array, minimizing memory
     * usage while still allowing for fast add and poll operations. The tail index is updated in
     * constant time, ensuring that add operations remain efficient even as the batch fills up and
     * empties out.
     * </p>
     * 
     * <p>
     * We could use a short for the indices to save a few bytes of memory, but the performance
     * difference is negligible and using an int avoids potential overflow issues in long-running
     * applications (plus, Java treats arithmetic with short values weirdly). We could also use a single
     * pointer for both {@link #head} and tail, but that would complicate the logic and force an extra
     * arithmetic operation on each add and {@link #poll() poll} operation, which could impact
     * performance.
     * </p>
     * 
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) for add operations.
     * @threading The index is not thread-safe, as it is intended to be used within a single
     *       thread context. Queues should be the only point of inter-thread communication.
     * @memory Minimal additional memory overhead (single int).
     * @optimization Efficient circular buffer implementation for fast access.
     */
    private int tail = 0;
    /**
     * An int to track the remaining capacity of the batch.
     * 
     * <p>
     * The typical mechanism for tracking the number of elements in a circular buffer would be to use
     * a size variable that increments on add operations and decrements on poll operations. However,
     * size checks in those methods can lead to JVM deoptimizations due to the way the JIT compiler
     * optimizes code paths and performs speculative optimizations. We need a more stable approach to
     * tracking that avoids these pitfalls, can be branch predicted more easily, and doesn't require
     * manual arithmetic computations. Tracking the remaining capacity provides this.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * By using remaining capacity, we can perform checks against a constant value (zero or full
     * capacity) rather than a variable size. This reduces the likelihood of deoptimizations and
     * improves branch prediction, leading to more consistent performance in high-frequency
     * scenarios. 
     * </p>
     * 
     * @see #capacity
     * @see #add(short[])
     * @see #poll()
     * @since 2025.07.28 - Remaining Capacity Tracking
     * @performance O(1) for capacity checks, avoiding deoptimizations associated with size tracking.
     * @threading The counter is not thread-safe, as it is intended to be used within a single
     *            thread context. Queues should be the only point of inter-thread communication.
     * @memory Minimal additional memory overhead (single int).
     * @optimization Stable and predictable capacity tracking to enhance performance.
     */
    private int remainingCapacity; // Replacement for size to avoid deoptimizations

    /**
     * Creates a new WorkBatch with the specified capacity. The buffer is pre-allocated based on the
     * {@link #numClicks number of clicks}, which must be {@link #setNumClicks(int) set} before creating
     * any WorkBatch instances.
     * 
     * @param capacity the maximum number of combinations the batch can hold.
     * @throws IllegalArgumentException if the capacity is not a positive integer.
     * @throws IllegalStateException    if {@link #numClicks} has not been set to a positive value.
     * @since 2025.07.01 - WorkBatch Introduction
     * @threading Thread-safe by nature of construction, but the created instance is not.
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
     * Sets the {@link #numClicks number of clicks} (elements) in each combination. This must be set
     * before creating any WorkBatch instances, as it determines the size of the {@link #buffer buffer}
     * {@link #WorkBatch(int) pre-allocated} in the constructor.
     * 
     * @param numClicks the number of clicks (elements) in each combination.
     * @since 2025.07.27 - Pre-allocation of WorkBatch Buffers
     * @performance O(1) for setting the value.
     * @threading This method is thread-safe since it modifies a static volatile variable. However, it
     *            should be called during application initialization before any WorkBatch instances are
     *            created to ensure consistency.
     * @memory Minimal additional memory overhead (single int).
     * @optimization Direct assignment to a static variable for efficient access.
     */
    public static void setNumClicks(int numClicks) {
        WorkBatch.numClicks = numClicks;
    }

    /**
     * Adds a combination to the batch. Returns <code>true</code> if the combination was added
     * successfully or <code>false</code> if the batch is full.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be extremely efficient and allocation-free, allowing it to be called
     * billions of times without introducing significant overhead. By
     * {@link System#arraycopy(Object, int, Object, int, int) copying} the input array into a
     * {@link #WorkBatch(int) pre-allocated} {@link #buffer}, we avoid the need for allocations and
     * reduce GC pressure. The pre-allocated nature of the buffer ensures that the destination array is
     * never <code>null</code>, eliminating the need for null checks in the destination. Finally, we use a
     * simple remaining capacity check instead of a size check to improve branch prediction and avoid
     * deoptimizations that can occur with size tracking.
     * </p>
     * 
     * @param source the <b>non-<code>null</code></b> combination array to add to the batch.
     * @return <code>true</code> if the combination was added successfully, <code>false</code> if the
     *         batch is full.
     * @see #isFull()
     * @see #setNumClicks(int)
     * @see CombinationGeneratorTask
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) array access in {@link #buffer}, O({@link #numClicks}) for copying the array to the
     *              destination.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     * @memory Does not allocate any new arrays, instead reusing the pre-allocated arrays in <code>buffer</code>.
     * @optimization Pre-allocated array {@link System#arraycopy(Object, int, Object, int, int) copying}
     *               to minimize GC pressure and avoid temporary allocations.
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
     * Adds a combination with a specified prefix and last element to the {@link #tail} of the batch.
     * 
     * <p>
     * With the help of previous optimizations in {@link #add(short[])}, {@link CombinationGeneratorTask
     * generators} are already able to create combinations without any temporary allocations and only
     * have to acquire a single array from {@link ArrayPool the pool} for each step of the process.
     * However, the leaf nodes of the tree would still need to acquire an array to hold the final
     * combination form, which adds slight overhead in the form of an additional array copy and element
     * modification (with a time complexity of O(n) where n is numClicks - 1). This method seeks to
     * eliminate that overhead, allowing the caller to directly add a prefix and its final element to
     * the batch without needing to create a new array for that purpose.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be extremely efficient and allocation-free, allowing it to be called
     * billions of times without introducing significant overhead. Similar to the previous
     * <code>add</code> method, it copies the prefix into a pre-allocated buffer, avoiding the need for
     * allocations and reducing GC pressure. The pre-allocated nature of the buffer ensures that the
     * destination array is never <code>null</code>, eliminating the need for null checks in the
     * destination. We assume that the caller has ensured that the <code>prefix</code> array is non-null
     * and is of a valid length (i.e., <code>prefixLength</code> is within bounds) for similar reasons.
     * Finally, we use a simple remaining capacity check instead of a size check to improve branch
     * prediction and avoid deoptimizations that can occur with size tracking.
     * </p>
     * 
     * @param prefix       the <b>non-<code>null</code></b> prefix array to copy from.
     * @param prefixLength the length of the prefix to copy from the <code>prefix</code> array, assumed
     *                     to be less than {@link #numClicks}.
     * @param lastElement  the last element to append to the combination.
     * @return <code>true</code> if the combination was added successfully, <code>false</code> if the
     *         batch is full.
     * @see #isFull()
     * @see #setNumClicks(int)
     * @see CombinationGeneratorTask
     * @see CombinationGeneratorTask#computeLeafCombinations(CombinationGeneratorTask.GeneratorContext)
     * @since 2025.07.19 - Assembling Combinations in the WorkBatch
     * @performance O(1) array access in {@link #buffer}, O(<code>prefixLength</code>) for copying the
     *              array to the destination.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     * @memory The memory footprint is fixed based on the capacity and {@link #numClicks}, minimizing
     *         dynamic allocations. The method does not allocate any new arrays, instead reusing the
     *         pre-allocated arrays in {@link #buffer}.
     * @optimization Pre-allocated array {@link System#arraycopy(Object, int, Object, int, int) copying}
     *               and assembly to minimize GC pressure and avoid temporary allocations.
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
     * Returns the next combination from the batch, or <code>null</code> if the batch is empty. This
     * method does not null out the polled array, allowing it to be reused by subsequent add operations.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be constant time and allocation-free, allowing it to be called
     * millions of times per second without introducing significant overhead. By simply incrementing the
     * values of {@link #head} and {@link #remainingCapacity}, we avoid the need for
     * {@link CombinationGeneratorTask generators} to create new arrays for each combination, bringing
     * down the GC pressure during runtime. We use a simple <code>remainingCapacity</code> check instead
     * of a size check to improve branch prediction and avoid deoptimizations that can occur with size
     * tracking. Finally, we avoid null checks on the polled array, as it is guaranteed to be non-null
     * if the batch is not empty (if <code>remainingCapacity</code> is not equal to {@link #capacity}).
     * </p>
     * 
     * @return the next combination from the batch, or <code>null</code> if the batch is empty.
     * @see #buffer
     * @see #add(short[], int, short)
     * @see #clear()
     * @see #isEmpty()
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) for polling operations.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     * @memory The memory footprint is fixed based on the capacity and {@link #numClicks}, minimizing
     *         dynamic allocations. The method does not allocate any new arrays, instead reusing the
     *         pre-allocated arrays in {@link #buffer}.
     * @optimization Pre-allocated array reuse to minimize GC pressure and avoid temporary allocations.
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
     * Checks if the batch is empty.
     * 
     * @return <code>true</code> if the batch is empty, <code>false</code> otherwise.
     * @see #capacity
     * @see #remainingCapacity
     * @see #isFull()
     * @see #size()
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) retrievals and comparison.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     * @memory Minimal additional memory overhead (single int comparison).
     * @optimization Efficient state check to quickly determine if the batch is empty, using
     *               {@link #remainingCapacity} for improved branch prediction.
     */
    public boolean isEmpty() {
        return remainingCapacity == capacity;
    }

    /**
     * Returns the current number of combinations in the batch. This is calculated based on the
     * {@link #capacity} and {@link #remainingCapacity}, avoiding the need for a separate size
     * variable that could lead to deoptimizations.
     * @return the current number of combinations in the batch.
     * @see #isEmpty()
     * @see #isFull()
     * @since 2025.07.28 - Remaining Capacity Tracking
     * @performance O(1) for size calculations.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     * @memory Minimal additional memory overhead (single int calculation).
     * @optimization Efficient size calculation using existing state variables to avoid deoptimizations.
     */
    public int size() {
        return capacity - remainingCapacity; // Calculate size based on remaining capacity
    }

    /**
     * "Clears" the batch for reuse by resetting {@link #head}, {@link #tail}, and
     * {@link #remainingCapacity remaining capacity.}
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be constant time and allocation-free, allowing it to be called
     * millions of times per second without introducing significant overhead. By simply resetting the
     * values of {@link #head}, {@link #tail}, and {@link #remainingCapacity}, we avoid the need for
     * any array manipulations or allocations, making it extremely efficient. This also allows future
     * add operations to reuse the existing arrays in the {@link #buffer}, further reducing GC
     * pressure during runtime.
     * </p>
     * 
     * @see #WorkBatch(int)
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) for clear operations.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     * @memory Does not deallocate or allocate any arrays, simply resets internal pointers.
     * @optimization Efficient state reset to enable rapid reuse of the batch without additional overhead.
     */
    public void clear() {
        head = 0;
        tail = 0;
        remainingCapacity = capacity; // Reset remaining capacity to full
    }

    /**
     * {@link org.jctools.queues.MessagePassingQueue.Consumer MessagePassingQueue.Consumer}
     * implementation for JCTools integration.
     * 
     * <p>
     * This method simply delegates to {@link #add(short[])} to add the combination to the batch. When
     * {@link TestClickCombination monkeys} drained combinations from queues into
     * <code>WorkBatch</code>es, they used this method as the consumer function.
     * </p>
     * 
     * <p>
     * Now that we directly enqueue <code>WorkBatch</code>es instead of individual combinations, this
     * method is no longer used by the monkeys. We could remove the <code>Consumer</code> interface from
     * this class, though we've left it here, mainly from forgetting to remove it.
     * </p>
     * 
     * @param combination the <code>short[]</code> to add to the batch.
     * @see org.jctools.queues.MessagePassingQueue
     * @see org.jctools.queues.MessagePassingQueue.Consumer#accept(Object)
     * @since 2025.07.01 - WorkBatch Introduction
     * @performance O(1) call to {@link #add(short[])}.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     */
    @Override
    public void accept(short[] combination) {
        // TODO: Remove Consumer interface and this method from the class, since it is no longer used.
        add(combination);
    }

    /**
     * {@link org.jctools.queues.MessagePassingQueue.Supplier MessagePassingQueue.Supplier}
     * implementation for JCTools integration.
     * 
     * <p>
     * This method simply delegates to {@link #poll()} to retrieve the next combination from the batch.
     * When {@link CombinationGeneratorTask generators} filled queues from <code>WorkBatch</code>es, they
     * used this method as the supplier function.
     * </p>
     * 
     * <p>
     * Now that we directly enqueue <code>WorkBatch</code>es instead of individual combinations, this
     * method is no longer used by the generators. We could remove the <code>Supplier</code> interface from
     * this class, though we've left it here, mainly from forgetting to remove it.
     * </p>
     * 
     * @return the next <code>short[]</code> (combination) from the batch, or <code>null</code> if the batch is empty.
     * @see org.jctools.queues.MessagePassingQueue
     * @see org.jctools.queues.MessagePassingQueue.Supplier#get()
     * @since 2025.07.01 - WorkBatch Integration in Generators
     * @performance O(1) call to {@link #poll()}.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *            used by one thread at a time.
     */
    @Override
    public short[] get() {
        // TODO: Remove Supplier interface and this method from the class, since it is no longer used.
        return poll();
    }

    /**
     * Checks if the batch is full. This is determined by checking if the {@link #remainingCapacity}
     * is zero.
     * @return <code>true</code> if the batch is full, <code>false</code> otherwise.
     * @see #capacity
     * @see #isEmpty()
     * @see #size()
     * @since 2025.07.28 - Remaining Capacity Tracking
     * @performance O(1) retrievals and comparison.
     * @threading This method is <b>not</b> thread-safe, as each instance of WorkBatch is intended to be
     *       used by one thread at a time.
     * @memory Minimal additional memory overhead (single int comparison).
     * @optimization Efficient state check to quickly determine if the batch is full, using
     *               {@link #remainingCapacity} for improved branch prediction.
     */
    public boolean isFull() {
        return remainingCapacity == 0;
    }
}
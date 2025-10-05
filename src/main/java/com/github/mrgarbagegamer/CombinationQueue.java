package com.github.mrgarbagegamer;

import org.jctools.queues.MpmcArrayQueue;

/**
 * A thin, type-safe wrapper around a JCTools {@link MpmcArrayQueue} designed exclusively for
 * transferring {@link WorkBatch} objects between producer and consumer threads.
 *
 * <h2>Architectural Role</h2>
 * <p>
 * This class serves as a high-performance communication channel between
 * {@link CombinationGeneratorTask} producer threads and {@link TestClickCombination} consumer
 * threads ("monkeys"). Its sole purpose is to enqueue and dequeue {@link WorkBatch} instances,
 * which is the cornerstone of the solver's low-contention batching architecture. By batching
 * thousands of combinations into a single queue operation, it dramatically reduces synchronization
 * overhead.
 * </p>
 *
 * <h2>Implementation Details</h2>
 * <p>
 * It is implemented as a thin wrapper over {@code MpmcArrayQueue} to provide a clear,
 * domain-specific API. The queue is bounded and operates in a lock-free manner, using relaxed
 * (non-blocking) offers and polls for maximum throughput.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe for multiple producers and multiple consumers, as guaranteed by the
 * underlying JCTools queue. However, the {@link WorkBatch} objects passed through it are
 * <strong>not</strong> thread-safe. A safe hand-off protocol is required, where producers do not
 * modify a batch after it has been offered to the queue.
 * </p>
 *
 * @see CombinationQueueArray
 * @since 2025.04 - Multi-threaded Architecture Introduction
 * @performance {@code O(1)} amortized for enqueue/dequeue operations and field accesses.
 * @threading Thread-safe via JCTools "magic".
 * @algorithm Lock-free, bounded MPMC queue with relaxed semantics using JCTools wizardry.
 * @memory Fixed memory overhead for the queue structure.
 */
public class CombinationQueue {
    /**
     * The fixed capacity of the queue.
     *
     * <p>
     * This size represents a trade-off:
     * <ul>
     * <li><b>Larger Queue:</b> Reduces the chance of a {@link CombinationGeneratorTask generator}
     * failing to enqueue a new batch, but increases the solver's overall memory footprint.</li>
     * <li><b>Smaller Queue:</b> Minimizes memory overhead but increases the risk of
     * {@link TestClickCombination monkeys} becoming starved for work if generators are temporarily
     * blocked.</li>
     * </ul>
     * A small size is chosen because the queue holds large {@link WorkBatch} objects, making memory a
     * primary consideration.
     * </p>
     * 
     * <p>
     * Note that, per {@link MpmcArrayQueue#MpmcArrayQueue(int)}, the capacity will be rounded up to the
     * next power of two if it is not already.
     * </p>
     * 
     * @see MpmcArrayQueue#capacity()
     * @since 2025.07 - Enqueuing {@link WorkBatch} Objects
     * @performance {@code O(1)} access time.
     * @threading Thread-safe; immutable field.
     * @memory Minimal footprint of 4 bytes as an {@code int}.
     */
    private final int QUEUE_SIZE = 16;
    /**
     * The underlying {@link MpmcArrayQueue JCTools queue} that holds {@link WorkBatch} objects.
     * 
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} amortized for enqueue/dequeue operations, {@code O(1)} capacity access,
     *              and {@code O(QUEUE_SIZE)} size access.
     * @threading Thread-safe via JCTools "magic".
     * @memory Fixed memory overhead for the queue structure.
     */
    private final MpmcArrayQueue<WorkBatch> queue;

    /**
     * Constructs a new {@code CombinationQueue} with a {@link #QUEUE_SIZE fixed capacity}.
     * 
     * @see #queue
     * @see MpmcArrayQueue
     * @see MpmcArrayQueue#MpmcArrayQueue(int)
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} creation and assignment.
     * @threading Thread-safe via JCTools "magic".
     * @memory Allocates memory for the underlying queue structure.
     */
    public CombinationQueue() {
        queue = new MpmcArrayQueue<>(QUEUE_SIZE);
    }

    /**
     * Returns the fixed capacity of the queue.
     *
     * @return the queue's capacity.
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} field access.
     * @threading Thread-safe; returns an immutable field.
     * @memory Does not allocate.
     */
    public int getCapacity() {
        return QUEUE_SIZE;
    }

    /**
     * Offers a {@link WorkBatch} to the queue using a non-blocking, relaxed operation.
     *
     * <p>
     * This method may occasionally fail (return {@code false}) even if the queue is not full, which is
     * an acceptable trade-off for higher throughput.
     * </p>
     *
     * @param workBatch the batch to add.
     * @return {@code true} if the batch was successfully added, {@code false} otherwise.
     * @see MpmcArrayQueue#relaxedOffer(Object)
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} amortized enqueue operation.
     * @threading Thread-safe via JCTools "magic".
     * @memory Does not allocate; uses pre-allocated queue slots.
     */
    public boolean add(WorkBatch workBatch) {
        return queue.relaxedOffer(workBatch);
    }

    /**
     * Retrieves a {@link WorkBatch} from the queue using a non-blocking, relaxed operation.
     *
     * <p>
     * This method may occasionally fail (return {@code null}) even if the queue is not empty, which is
     * an acceptable trade-off for higher throughput.
     * </p>
     *
     * @return a {@link WorkBatch} if one was available, or {@code null} otherwise.
     * @see MpmcArrayQueue#relaxedPoll()
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} amortized dequeue operation.
     * @threading Thread-safe via JCTools "magic".
     * @memory Does not allocate; returns references to existing objects.
     */
    public WorkBatch getWorkBatch() {
        return queue.relaxedPoll();
    }

    /**
     * Checks if the queue is empty.
     *
     * <p>
     * Note: In a concurrent environment, the result of this call is only a snapshot in time and may be
     * immediately outdated.
     * </p>
     *
     * @return {@code true} if the queue was empty at the time of the call, {@code false} otherwise.
     * @see MpmcArrayQueue#isEmpty()
     * @since 2025.07 - Enqueuing {@code WorkBatch} Objects
     * @performance {@code O(1)} call to queue method.
     * @threading Thread-safe via JCTools "magic". May be stale immediately after return.
     * @memory Does not allocate.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
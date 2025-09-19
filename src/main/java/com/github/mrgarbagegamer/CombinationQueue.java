package com.github.mrgarbagegamer;

import org.jctools.queues.MpmcArrayQueue;

/**
 * CombinationQueue - A high-performance, thread-safe queue for managing {@link WorkBatch batches}
 * of click combinations to be tested by {@link TestClickCombination worker threads (monkeys)}.
 * 
 * <p>
 * Parallelism is key to efficiently solving this puzzle, and an efficient way to do that is to
 * divide the operational roles into {@link CombinationGeneratorTask generators} and
 * {@link TestClickCombination monkeys}. However, this separation of concerns requires a robust and
 * efficient way to exchange work between these two roles. This class serves as the conduit for this
 * exchange, holding batches of click combinations for the monkeys to process.
 * </p>
 * 
 * <p>
 * This queue is implemented using the {@link org.jctools.queues.MpmcArrayQueue MpmcArrayQueue} from the JCTools library, which is
 * designed for high-throughput, low-latency concurrent access. The queue is bounded to a fixed size
 * to prevent unbounded memory growth, and it uses non-blocking operations to minimize contention
 * between threads. This design choice ensures that both the generators and monkeys can operate
 * efficiently without being bottlenecked by synchronization overhead.
 * </p>
 * 
 * <h2>Architecture Role</h2>
 * <p>
 * The CombinationQueue acts as a bridge between the {@link CombinationGeneratorTask generators} and
 * the {@link TestClickCombination monkeys}. Generators produce {@link WorkBatch batches} of work
 * and enqueue them into a queue, while monkeys dequeue these batches and process them. This
 * decoupling allows both components to operate independently and at their own pace, improving the
 * overall throughput and responsiveness of the system.
 * </p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>
 * The CombinationQueue is optimized for high performance in a multi-threaded environment. By using a
 * bounded, lock-free queue, it minimizes the overhead associated with thread synchronization. The use
 * of batches instead of individual combinations further reduces the frequency of enqueue and dequeue
 * operations, amortizing the overhead across multiple combinations. This design is particularly
 * effective in scenarios with high contention, as it allows multiple threads to operate
 * concurrently with minimal interference.
 * </p>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * The CombinationQueue is inherently thread-safe due to its use of the MpmcArrayQueue, which is
 * designed for concurrent access by multiple producers and consumers. The non-blocking operations
 * used for enqueuing and dequeuing ensure that threads can operate without waiting on locks or
 * other synchronization mechanisms.
 * </p>
 * 
 * <p>
 * Though the queue itself is thread-safe, it is important to note that the {@link WorkBatch} objects
 * enqueued in the queue are not. Care should be taken to ensure that these objects are handed off
 * between threads in a safe manner, preventing a thread from modifying a WorkBatch after it enqueues it.
 * </p>
 * 
 * @threading Thread-safe via JCTools magic.
 * @performance O(1) amortized for enqueue/dequeue operations and field accesses.
 * @algorithm Lock-free MPMC queue using JCTools wizardry.
 * @since 2025.04.01 - Multi-threaded Architecture Introduction
 * @see CombinationGeneratorTask
 * @see TestClickCombination
 * @see WorkBatch
 */
public class CombinationQueue {
    /**
     * The fixed capacity of the queue.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * A smaller queue size is chosen to minimize memory overhead while still allowing
     * effective batching of work. This size is a balance between memory usage and the need to
     * keep the {@link TestClickCombination monkeys} fed with work. Higher queue sizes increase
     * the likelihood of empty spots in the queue for {@link CombinationGeneratorTask generators} to
     * fill, but also increase memory usage. Lower sizes reduce memory usage, but may lead to starved
     * monkeys if the generators cannot keep up.
     * </p>
     * 
     * @since 2025.07.07 - Enqueuing WorkBatch Objects
     * @performance O(1) time complexity for capacity access.
     * @optimization Small, fixed-size queue to minimize memory overhead while batching work.
     * @see org.jctools.queues.MpmcArrayQueue#capacity()
     */
    private final int QUEUE_SIZE = 16;
    /**
     * The underlying MPMC queue from the JCTools library, used for its high performance and thread-safe
     * characteristics. This queue holds {@link WorkBatch} objects, each containing multiple click
     * combinations to be processed by {@link TestClickCombination worker threads (monkeys)}.
     * 
     * @since 2025.07.07 - Enqueuing WorkBatch Objects
     * @threading Thread-safe via JCTools magic.
     * @performance O(1) amortized time complexity for enqueue/dequeue operations, O(1) capacity access,
     *              and O(n) size access.
     * @optimization Uses a bounded, lock-free queue to minimize memory overhead and contention.
     * @see org.jctools.queues.MpmcArrayQueue
     * @see WorkBatch
     */
    private final MpmcArrayQueue<WorkBatch> queue;

    /**
     * Constructs a new CombinationQueue with a fixed capacity of {@link #QUEUE_SIZE}.
     * 
     * @since 2025.07.07 - Enqueuing WorkBatch Objects
     * @performance O(1) time complexity.
     * @optimization Uses a small, fixed-size queue to minimize memory overhead while batching work.
     * @threading Thread-safe via JCTools magic.
     * @see #queue
     * @see org.jctools.queues.MpmcArrayQueue
     * @see org.jctools.queues.MpmcArrayQueue#MpmcArrayQueue(int)
     */
    public CombinationQueue() {
        queue = new MpmcArrayQueue<>(QUEUE_SIZE);
    }

    /**
     * Gets the capacity of the queue.
     * 
     * @return the fixed capacity of the queue.
     * @since 2025.07.08 - Matching Sized Queues for Generators and Monkeys
     * @threading Thread-safe, since it only reads a final field.
     * @performance O(1) time complexity.
     * @optimization Simple field access with minimal overhead.
     * @see #QUEUE_SIZE
     * @see #CombinationQueue()
     * @see org.jctools.queues.MpmcArrayQueue#capacity()
     */
    public int getCapacity() {
        return QUEUE_SIZE;
    }

    /**
     * Offers a {@link WorkBatch} to the queue. Returns <code>true</code> if the batch was added, or
     * <code>false</code> if the queue is full.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is non-blocking and uses a relaxed offer {@link MpmcArrayQueue#relaxedOffer(Object)
     * as defined by} the JCTools library. This method trades perfect accuracy for speed, meaning it may
     * occasionally fail to add an item (and return <code>false</code>) even if the queue is not full.
     * This is acceptable in our use case, as the {@link CombinationGeneratorTask generator} can simply
     * try to add the batch to a new queue or retry later.
     * </p>
     * 
     * @param workBatch the {@link WorkBatch} to add to the queue.
     * @return <code>true</code> if the batch was added successfully, or <code>false</code> if
     *         unsuccessful (the queue is full or the relaxed offer failed).
     * @since 2025.07.07 - Enqueuing WorkBatch Objects
     * @threading Thread-safe via JCTools magic.
     * @performance O(1) amortized time complexity.
     * @optimization Relaxed offer for speed, accepting occasional false negatives. Enqueues batches
     *               instead of combinations to amortize overhead across more combinations.
     * @see #getWorkBatch()
     * @see MpmcArrayQueue#relaxedOffer(Object)
     * @see WorkBatch
     */
    public boolean add(WorkBatch workBatch) {
        return queue.relaxedOffer(workBatch);
    }

    /**
     * Attempts to retrieve a {@link WorkBatch} from the queue.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is non-blocking and uses a relaxed poll {@link MpmcArrayQueue#relaxedPoll() as
     * defined by} the JCTools library. This method trades perfect accuracy for speed, meaning it may
     * occasionally return <code>null</code> even if the queue is not empty. This is acceptable in our
     * use case, as the {@link TestClickCombination monkeys} can try to poll from a new queue or retry
     * later.
     * </p>
     * 
     * @return the next {@link WorkBatch} from the queue, or <code>null</code> if the queue is empty or
     *         the relaxed poll failed.
     * @since 2025.07.07 - Enqueuing WorkBatch Objects
     * @threading Thread-safe via JCTools magic.
     * @performance O(1) amortized time complexity.
     * @optimization Relaxed poll for speed, accepting occasional false negatives. Polls batches instead
     *               of combinations to amortize overhead across more combinations.
     * @see #add(WorkBatch)
     * @see MpmcArrayQueue#relaxedPoll()
     * @see WorkBatch
     */
    public WorkBatch getWorkBatch() {
        return queue.relaxedPoll();
    }

    /**
     * Checks the queue for emptiness. Note that due to the concurrent nature of the queue,
     * this is only a snapshot in time and may not reflect the state immediately after the call.
     * 
     * @return <code>true</code> if the queue is empty at the time of the call, otherwise <code>false</code>.
     * @since 2025.08.02 - Class Formatting Cleanup
     * @threading Thread-safe via JCTools magic.
     * @performance O(1) time complexity.
     * @optimization Simple check with minimal overhead.
     * @see org.jctools.queues.MpmcArrayQueue#isEmpty()
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
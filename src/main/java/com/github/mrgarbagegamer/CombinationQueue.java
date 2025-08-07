package com.github.mrgarbagegamer;

import org.jctools.queues.MpmcArrayQueue;

/**
 * CombinationQueue - [Primary Purpose in Algorithm]
 * 
 * <p>[Detailed description of role in the overall puzzle-solving architecture.
 * Explain the "what" and "why" - what problem this class solves and why this 
 * approach was chosen.]</p>
 * 
 * <h2>Architecture Role</h2>
 * <p>[How this class fits into the overall system. What classes depend on it,
 * what it depends on, and the data flow.]</p>
 * 
 * <h2>Performance Characteristics</h2>
 * <p>[Key performance properties, bottlenecks, and optimization strategies.
 * Include complexity analysis for critical methods.]</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>[Concurrency model, synchronization approach, and usage patterns.]</p>
 * 
 * <h3>0/8 - 0% of documentation completed</h3>
 * 
 * @performance [Overall performance characteristics]
 * @threading [Thread safety guarantees]  
 * @algorithm [High-level algorithm description]
 * @since [Version when introduced/major changes]
 * @see [Related classes in the architecture]
 */
public class CombinationQueue 
{
    // This queue now holds entire batches of work, not individual combinations.
    // A smaller capacity is fine as it represents larger work units.
    // OPTIMIZATION: Drastically reduce queue size to lower memory footprint for pre-allocation.
    private final int QUEUE_SIZE = 16;
    private final MpmcArrayQueue<WorkBatch> queue;

    public CombinationQueue() 
    {
        queue = new MpmcArrayQueue<>(QUEUE_SIZE);
    }

    public int getCapacity()
    {
        return QUEUE_SIZE;
    }

    /**
     * Adds a full WorkBatch to the queue for a worker to process.
     * @param workBatch The batch of combinations to add.
     * @return true if the batch was successfully added.
     */
    public boolean add(WorkBatch workBatch)
    {
        return queue.relaxedOffer(workBatch);
    }

    /**
     * Retrieves a WorkBatch from the queue.
     * @return A WorkBatch to be processed, or null if the queue is empty.
     */
    public WorkBatch getWorkBatch() 
    {
        return queue.relaxedPoll();
    }

    public boolean isEmpty() 
    {
        return queue.isEmpty();
    }
}
package com.github.mrgarbagegamer;

import org.jctools.queues.MpmcArrayQueue;

public class CombinationQueue 
{
    // This queue now holds entire batches of work, not individual combinations.
    // A smaller capacity is fine as it represents larger work units.
    private final int QUEUE_SIZE = 256; 
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
}
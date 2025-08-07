package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.queues.MpmcArrayQueue;

/**
 * CombinationQueueArray - [Performance Purpose - e.g., "High-performance memory pool"]
 * 
 * <p>[Detailed description of the performance problem this class solves.
 * Include before/after metrics where applicable.]</p>
 * 
 * <h2>Optimization Strategy</h2>
 * <p>[Specific optimization techniques used - pooling, caching, lock-free, etc.
 * Explain trade-offs made for performance gains.]</p>
 * 
 * <h2>Usage Patterns</h2>
 * <p>[How and when to use this utility. Common usage patterns and anti-patterns.]</p>
 * 
 * <h2>Memory Management</h2>
 * <p>[Memory allocation patterns, GC implications, sizing considerations.]</p>
 * 
 * <h3>0/16 - 0% of documentation completed</h3>
 * 
 * @performance [Specific performance characteristics and measurements]
 * @memory [Memory usage patterns and optimizations]
 * @threading [Thread safety model]
 * @since [When introduced and why]
 */
public class CombinationQueueArray 
{
    private final CombinationQueue[] queues;
    private final AtomicInteger generatorsRemaining;
    // REPLACED: The int[] pool is gone.
    // NEW: Central pool for recycled WorkBatch objects.
    private final MpmcArrayQueue<WorkBatch> workBatchPool;
    private volatile String winningMonkey = null;
    private volatile short[] winningCombination = null;

    public volatile boolean solutionFound = false;
    public volatile boolean generationComplete = false;

    public CombinationQueueArray(int numConsumers, int numGenerators)
    {
        this.queues = new CombinationQueue[numConsumers];
        this.generatorsRemaining = new AtomicInteger(numGenerators);

        // The total number of batches that can be in-flight is the sum of all queue capacities
        // The pool must be at least this large to guarantee a recycled batch is never discarded
        int totalWorkQueueCapacity = 0;
        for (int i = 0; i < numConsumers; i++)
        {
            queues[i] = new CombinationQueue();
            totalWorkQueueCapacity += queues[i].getCapacity();
        }
        // Set the recycle pool size to match the total work queue capacity
        this.workBatchPool = new MpmcArrayQueue<>(totalWorkQueueCapacity);

        // OPTIMIZATION: Pre-allocate the entire WorkBatch pool to prevent allocation in the hot path.
        // The BATCH_SIZE must match the one defined in CombinationGeneratorTask.
        final int BATCH_SIZE = CombinationGeneratorTask.BATCH_SIZE;
        for (int i = 0; i < totalWorkQueueCapacity; i++)
        {
            workBatchPool.relaxedOffer(new WorkBatch(BATCH_SIZE));
        }
    }

    // NEW: Accessors for the WorkBatch pool
    public MpmcArrayQueue<WorkBatch> getWorkBatchPool()
    {
        return workBatchPool;
    }

    public CombinationQueue getQueue(int idx) 
    { 
        return queues[idx]; 
    }

    public CombinationQueue[] getAllQueues() 
    { 
        return queues; 
    }

    public void generatorFinished() 
    {
        if (generatorsRemaining.decrementAndGet() == 0) 
        {
            generationComplete = true;
        }
    }

    public void solutionFound(String monkeyName, short[] winningCombination)
    {
        if (solutionFound == false) 
        {
            solutionFound = true;
            this.winningMonkey = monkeyName;
            this.winningCombination = winningCombination;
        }
    }

    public String getWinningMonkey() 
    { 
        return winningMonkey; 
    }
    
    public short[] getWinningCombination() 
    { 
        return winningCombination; 
    }
}
package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.queues.MpmcArrayQueue;

public class CombinationQueueArray 
{
    private final CombinationQueue[] queues;
    private final AtomicInteger generatorsRemaining;
    // REPLACED: The int[] pool is gone.
    // NEW: Central pool for recycled WorkBatch objects.
    private final MpmcArrayQueue<WorkBatch> workBatchPool;
    private volatile String winningMonkey = null;
    private volatile int[] winningCombination = null;

    public volatile boolean solutionFound = false;
    public volatile boolean generationComplete = false;

    public CombinationQueueArray(int numConsumers, int numGenerators) 
    {
        this.queues = new CombinationQueue[numConsumers];
        this.generatorsRemaining = new AtomicInteger(numGenerators);
        // Initialize with a capacity for a few batches per thread.
        this.workBatchPool = new MpmcArrayQueue<>(numConsumers * 4); 
        for (int i = 0; i < numConsumers; i++) queues[i] = new CombinationQueue();
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

    public void solutionFound(String monkeyName, int[] winningCombination)
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
    
    public int[] getWinningCombination() 
    { 
        return winningCombination; 
    }
}
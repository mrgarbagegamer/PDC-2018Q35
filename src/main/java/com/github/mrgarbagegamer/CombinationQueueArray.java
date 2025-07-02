package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.AtomicInteger;

public class CombinationQueueArray 
{
    private final CombinationQueue[] queues;
    private final AtomicInteger generatorsRemaining;
    private volatile String winningMonkey = null;
    private volatile int[] winningCombination = null;

    public volatile boolean solutionFound = false;
    public volatile boolean generationComplete = false;

    public CombinationQueueArray(int numConsumers, int numGenerators) 
    {
        this.queues = new CombinationQueue[numConsumers];
        this.generatorsRemaining = new AtomicInteger(numGenerators);
        for (int i = 0; i < numConsumers; i++) queues[i] = new CombinationQueue();
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
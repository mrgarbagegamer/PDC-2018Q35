package com.github.mrgarbagegamer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CombinationQueueArray 
{
    private final CombinationQueue[] queues;
    private final AtomicBoolean solutionFound = new AtomicBoolean(false);
    private final AtomicBoolean[] generationCompleteFlags;
    private final AtomicInteger generatorsRemaining;
    private volatile String winningMonkey = null;
    private volatile int[] winningCombination = null;

    public CombinationQueueArray(int numConsumers, int numGenerators) 
    {
        this.queues = new CombinationQueue[numConsumers];
        this.generationCompleteFlags = new AtomicBoolean[numConsumers];
        for (int i = 0; i < numConsumers; i++) 
        {
            generationCompleteFlags[i] = new AtomicBoolean(false);
            queues[i] = new CombinationQueue(solutionFound, generationCompleteFlags[i]);
        }
        this.generatorsRemaining = new AtomicInteger(numGenerators);
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
            for (AtomicBoolean flag : generationCompleteFlags) flag.set(true);
        }
    }

    public boolean isSolutionFound() 
    { 
        return solutionFound.get(); 
    }

    public void solutionFound(String monkeyName, int[] winningCombination)
    {
        if (solutionFound.compareAndSet(false, true)) 
        {
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
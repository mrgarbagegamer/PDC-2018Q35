package com.github.mrgarbagegamer;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.MpmcArrayQueue;

public class CombinationQueue 
{
    private final int QUEUE_SIZE = 100000;
    private Queue<List<Click>> combinationQueue = new MpmcArrayQueue<>(QUEUE_SIZE);
    private volatile boolean solutionFound = false;
    private String winningMonkey = null;
    private List<Click> winningCombination = null;

    private AtomicInteger generatorsRemaining;
    private volatile boolean generationComplete = false;

    public void setNumGenerators(int numGenerators) 
    {
        this.generatorsRemaining = new AtomicInteger(numGenerators);
    }

    public void generatorFinished() 
    {
        if (generatorsRemaining.decrementAndGet() == 0) 
        {
            generationComplete = true;
        }
    }

    public boolean isGenerationComplete() 
    {
        return generationComplete;
    }

    public boolean isItSolved() 
    {
        return this.solutionFound;
    }

    public void solutionFound(String monkeyName, List<Click> winningCombination) 
    {
        this.solutionFound = true;
        this.winningMonkey = monkeyName;
        this.winningCombination = winningCombination;

        // Log the solution
        Logger logger = LogManager.getLogger(CombinationQueue.class);
        logger.info("{} - Found the solution as the following click combination: {}", monkeyName, winningCombination);
    }

    public String getWinningMonkey()
    {
        return this.winningMonkey;
    }

    public List<Click> getWinningCombination()
    {
        return this.winningCombination;
    }

    public boolean add(List<Click> combinationClicks) 
    {
        while (!this.solutionFound) 
        {
            try 
            {
                if (this.combinationQueue.offer(combinationClicks)) // Non-blocking add
                {
                    return true; // Successfully added
                }
                else 
                {
                    Thread.sleep(5); // Retry after a short delay
                }
            } 
            catch (InterruptedException e) 
            {
                Thread.currentThread().interrupt(); // Restore interrupted status
                return false; // Indicate failure due to interruption
            }
        }
        return false; // If solution is found, stop adding
    }

    public List<Click> getClicksCombination() 
    {
        List<Click> combinationClicks = null;

        while (combinationClicks == null && !this.solutionFound) 
        {
            combinationClicks = this.combinationQueue.poll(); // Non-blocking poll
            if (combinationClicks == null) 
            {
                if (isGenerationComplete()) 
                {
                    return null; // No more combinations will be generated
                }
                try 
                {
                    Thread.sleep(5); // Avoid busy-waiting
                } 
                catch (InterruptedException e) 
                {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }
        }
        return combinationClicks;
    }
}
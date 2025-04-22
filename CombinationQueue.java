import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CombinationQueue {
    private BlockingQueue<List<Click>> combinationQueue;
    private volatile boolean solutionFound = false;
    private String winningMonkey = null;
    private List<Click> winningCombination = null;

    // Constructor to set the maximum size of the queue
    public CombinationQueue(int maxSize) {
        this.combinationQueue = new LinkedBlockingQueue<>(maxSize); // Bounded queue
    }

    synchronized boolean isItSolved() 
    {
        return this.solutionFound;
    }

    synchronized void solutionFound(String monkeyName, List<Click> winningCombination) {
        this.solutionFound = true;
        this.winningMonkey = monkeyName;
        this.winningCombination = winningCombination;
    }

    public String getWinningMonkey() {
        return this.winningMonkey;
    }

    public List<Click> getWinningCombination() {
        return this.winningCombination;
    }

    synchronized boolean add(List<Click> combinationClicks) {
        try {
            this.combinationQueue.put(combinationClicks);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    synchronized List<Click> getClicksCombination(CombinationGenerator combinationGenerator) {
        while (true) {
            try {
                // Check if the queue is empty and generation is complete
                if (combinationQueue.isEmpty()) 
                {
                    if (combinationGenerator.isGenerationComplete()) 
                    {
                        return null; // No more combinations to process
                    }
                    // Wait briefly to allow other threads to add combinations
                    wait(5); // Avoid busy-waiting
                    continue;
                }

                // Wait for an element to become available in the queue
                return this.combinationQueue.take();
            } catch (InterruptedException e) 
            {
                Thread.currentThread().interrupt(); // Restore interrupted status
                return null;
            }
        }
    }
}
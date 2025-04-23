import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CombinationQueue {
    private BlockingQueue<List<Click>> combinationQueue;
    private volatile boolean solutionFound = false;
    private String winningMonkey = null;
    private List<Click> winningCombination = null;
    private volatile boolean generationComplete = false; // New field

    // Constructor to set the maximum size of the queue
    public CombinationQueue(int maxSize) {
        this.combinationQueue = new LinkedBlockingQueue<>(maxSize); // Bounded queue
    }

    public boolean isItSolved() 
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

    public boolean add(List<Click> combinationClicks) {
        try {
            this.combinationQueue.put(combinationClicks); // Blocking operation outside synchronized block
            synchronized (this)
            {
                notifyAll(); // Notify waiting threads that a new combination is available
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    List<Click> getClicksCombination() {
        while (true) {
            try {
                synchronized (this) {
                    // Check if the queue is empty and generation is complete
                    if (combinationQueue.isEmpty()) {
                        if (this.generationComplete) {
                            return null; // No more combinations to process
                        }
                        // Wait briefly for new combinations to be added
                        wait(5); // Wait for 5ms before re-checking
                        continue;
                    }
                }

                // Retrieve the next combination from the queue
                return this.combinationQueue.take(); // Blocking operation outside synchronized block
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    public synchronized void markGenerationComplete()
    {
        this.generationComplete = true;
        notifyAll(); // Notify waiting threads that generation is complete
    }

    public synchronized boolean isGenerationComplete() 
    {
        return this.generationComplete;
    }
}
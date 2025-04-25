import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class CombinationQueue {
    private final Queue<List<Click>> combinationQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean solutionFound = new AtomicBoolean(false);
    private final AtomicBoolean generationComplete = new AtomicBoolean(false);
    private volatile String winningMonkey = null;
    private volatile List<Click> winningCombination = null;
    private static final Logger logger = Logger.getLogger(TestClickCombination.class.getName());

    public boolean isItSolved() {
        return solutionFound.get();
    }

    public void solutionFound(String monkeyName, List<Click> winningCombination) {
        solutionFound.set(true);
        this.winningMonkey = monkeyName;
        this.winningCombination = winningCombination;
    }

    public String getWinningMonkey() {
        return this.winningMonkey;
    }

    public List<Click> getWinningCombination() {
        return this.winningCombination;
    }

    public void add(List<Click> combinationClicks) {
        combinationQueue.add(combinationClicks); // Non-blocking operation
        // print queue size after adding a new combination
        synchronized (this) {
            notifyAll(); // Notify waiting threads that a new combination is available
        }
    }

    public List<Click> getClicksCombination() {
        while (true) {
            synchronized (this) {
                // Check if the queue is empty and generation is complete
                if (combinationQueue.isEmpty()) {
                    if (generationComplete.get()) {
                        return null; // No more combinations to process
                    }
                    try {
                        wait(5); // Wait briefly for new combinations to be added
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }
                // Retrieve the next combination from the queue
                return combinationQueue.poll(); // Non-blocking operation
            }
        }
    }

    public void markGenerationComplete() {
        generationComplete.set(true);
        synchronized (this) {
            notifyAll(); // Notify waiting threads that generation is complete
        }
    }

    public boolean isGenerationComplete() {
        return generationComplete.get();
    }
}
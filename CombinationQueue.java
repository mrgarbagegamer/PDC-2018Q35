import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CombinationQueue {
    private BlockingQueue<List<Click>> combinationQueue;
    private volatile boolean solutionFound = false;
    private String winningMonkey = null;
    private List<Click> winningCombination = null;

    // Constructor to set the maximum size of the queue
    public CombinationQueue(int maxSize) {
        this.combinationQueue = new LinkedBlockingQueue<>(maxSize); // Bounded queue
    }

    boolean isItSolved() {
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

    boolean add(List<Click> combinationClicks) {
        try {
            return this.combinationQueue.offer(combinationClicks, 1, TimeUnit.MILLISECONDS); // Waits up to 1 milliseconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    List<Click> getClicksCombination() {
        try {
            // Blocks if the queue is empty until an element becomes available
            return this.combinationQueue.poll(1, TimeUnit.MILLISECONDS); // Waits up to 1 millisecond
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            return null;
        }
    }
}
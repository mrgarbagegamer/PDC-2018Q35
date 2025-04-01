import java.util.List;
import java.util.Queue;
import java.util.LinkedList;

public class CombinationQueue {
    private Queue<List<Click>> combinationQueue = new LinkedList<>();
    private boolean solutionFound = false;
    private String winningMonkey = null;
    private List<Click> winningCombination = null;


    boolean isItSolved() {
        return this.solutionFound;
    }

    synchronized void solutionFound(String monkeyName, List<Click> winningCombination) {
        this.solutionFound = true;
        this.winningMonkey = monkeyName;
        this.winningCombination = winningCombination;
    }

    public String getWinningMonkey(){
        return this.winningMonkey;
    }

    public List<Click> getWinningCombination(){
        return this.winningCombination;
    }

    synchronized boolean add(List<Click> combinationClicks) {
        boolean success = this.combinationQueue.add(combinationClicks);
        this.notifyAll();

        return success;
    }

    synchronized List<Click> getClicksCombination() {
        List<Click> combinationClicks = null;

        while (combinationClicks == null) {
            combinationClicks = this.combinationQueue.poll();

            try {
                System.out.printf("%s - Empty queue waiting...\n", Thread.currentThread().getName());
                wait();
            } catch (InterruptedException e) {
                // do nothing
            }
        }

        return combinationClicks;
    }
}
import java.util.List;
import java.util.Queue;
import java.util.LinkedList;

public class CombinationQueue {
    private Queue<String> combinationFileQueue = new LinkedList<>();
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

    public String getWinningMonkey() {
        return this.winningMonkey;
    }

    public List<Click> getWinningCombination() {
        return this.winningCombination;
    }

    synchronized boolean add(String newClickCombinationsFile) {
        boolean success = this.combinationFileQueue.add(newClickCombinationsFile);
        this.notifyAll();

        return success;
    }

    synchronized String getClicksCombinationFile() {
        String clickCombinationFile = null;

        while (clickCombinationFile == null) {
            clickCombinationFile = this.combinationFileQueue.poll();

            try {
                System.out.printf("%s - Empty queue waiting...\n", Thread.currentThread().getName());
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return clickCombinationFile;
    }
}
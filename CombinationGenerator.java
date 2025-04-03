import java.util.List;
import java.util.ArrayList;

public class CombinationGenerator extends Thread {
    private CombinationQueue combinationQueue;
    private int numClicks;
    private List<Click> possibleClicks;

    public CombinationGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks) {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
    }

    private void generateCombinationsHelper(List<Click> possibleClicks, int k, int start, List<Click> currentCombination){
        if (currentCombination.size() == k) {
            this.combinationQueue.add(currentCombination);
            return;
        }

        for (int i = start; i < possibleClicks.size(); i++) {
            currentCombination.add(possibleClicks.get(i));
            generateCombinationsHelper(possibleClicks, k, i + 1, currentCombination);
            currentCombination.remove(currentCombination.size() - 1); // Backtrack
        }
    }

    public void run() {
        while (!this.combinationQueue.isItSolved()) {
            List<Click> currentCombination = new ArrayList<>();
            generateCombinationsHelper(this.possibleClicks, this.numClicks, 0, currentCombination);
        }
    }
}

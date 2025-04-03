import java.util.List;
import java.util.ArrayList;

public class CombinationGenerator extends Thread {
    private CombinationQueue combinationQueue;
    private int numClicks;
    private List<List<Click>> allCombinationsList;
    private List<Click> possibleClicks;

    public CombinationGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks) {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;

        this.allCombinationsList = new ArrayList<>();
    }

   public List<List<Click>> generateCombinations(List<Click> possibleClicks, int k) {
        List<List<Click>> clickCombinations = new ArrayList<>();

        generateCombinationsHelper(possibleClicks, k, 0, new ArrayList<>(), clickCombinations);

        return clickCombinations;
    }

    private void generateCombinationsHelper(List<Click> possibleClicks, int k, int start, List<Click> currentCombination, List<List<Click>> combinationsFound) {
        if (currentCombination.size() == k) {
            combinationsFound.add(new ArrayList<>(currentCombination));
            return;
        }

        for (int i = start; i < possibleClicks.size(); i++) {
            currentCombination.add(possibleClicks.get(i));
            generateCombinationsHelper(possibleClicks, k, i + 1, currentCombination, combinationsFound);
            currentCombination.remove(currentCombination.size() - 1); // Backtrack
        }
    }

    public void run() {
        List<Click>
        generateCombinationsHelper(this.possibleClicks, k, 0, new ArrayList<>(), clickCombinations);
    }
}

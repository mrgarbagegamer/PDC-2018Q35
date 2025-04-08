import java.util.ArrayList;
import java.util.List;

public class CombinationGenerator extends Thread {
    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Grid puzzleGrid;

    public CombinationGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks, Grid puzzleGrid) {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.puzzleGrid = puzzleGrid;
    }

    public void run() {
        this.generateCombinations(this.possibleClicks, numClicks, 0, new ArrayList<>());
    }

    private void generateCombinations(List<Click> nodeList, int k, int start, List<Click> currentCombination) {
        // check if problem has been solved
        if (this.combinationQueue.isItSolved()) {
            return;
        }

        if (currentCombination.size() == k) {
            
            boolean hasTrueAdjacent = false;
            ArrayList<Integer[]> trueAdjacents = puzzleGrid.findTrueAdjacents();
            for (Click click : currentCombination) {
                // Check if any of the cells in the combination is a true adjacent
                for (Integer[] adj : trueAdjacents) {
                    if (click.row == adj[0] && click.col == adj[1]) {
                        hasTrueAdjacent = true;
                        break;
                    }
                }
                if (hasTrueAdjacent) {
                    break;
                }
            }
            if (!hasTrueAdjacent) { // Check if any cell in the combination contains a true adjacent and return if not
                return;
            }
            this.combinationQueue.add(new ArrayList<>(currentCombination));
            return;
        }

        for (int i = start; i < nodeList.size() && !this.combinationQueue.isItSolved(); i++) {
            currentCombination.add(nodeList.get(i));
            generateCombinations(nodeList, k, i + 1, currentCombination);
            currentCombination.remove(currentCombination.size() - 1);
        }
    }
}
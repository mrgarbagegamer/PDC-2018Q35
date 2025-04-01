import java.util.ArrayList;
import java.util.List;

public class CombinationGenerator extends Thread {
    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;

    public CombinationGenerator( CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks) {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
    }

    public void run(){
        this.generateCombinations(this.possibleClicks, numClicks, 0, new ArrayList<>());
    }

    private void generateCombinations(List<Click> nodeList, int k, int start, List<Click> currentCombination) {
        // check if problem has been solved
        if( this.combinationQueue.isItSolved() ){ return; }

        if (currentCombination.size() == k) {
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
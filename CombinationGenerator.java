import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = Logger.getLogger(CombinationGenerator.class.getName());
    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Grid puzzleGrid;
    private Set<String> trueAdjacents;

    public CombinationGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks, Grid puzzleGrid) 
    {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.puzzleGrid = puzzleGrid;
        Set<Integer[]> trueAdjSet = puzzleGrid.findFirstTrueAdjacents();

        if (trueAdjSet != null) {
            this.trueAdjacents = new HashSet<>();
            for (Integer[] adj : trueAdjSet) {
                this.trueAdjacents.add(adj[0] + "," + adj[1]);
            }
        }
    }

    public void run() 
    {
        ForkJoinPool customPool = new ForkJoinPool(24); // Limit to 24 threads
        customPool.submit(() ->
            IntStream.range(0, possibleClicks.size()).parallel().forEach(index -> {
                if (!this.combinationQueue.isItSolved()) { // Check if solution is found
                    Click click = possibleClicks.get(index);
                    List<Click> currentCombination = new ArrayList<>();
                    currentCombination.add(click);
                    generateCombinations(possibleClicks, numClicks, index + 1, currentCombination);
                }
            })
        ).join();
        customPool.shutdown();

        // Mark generation as complete
        combinationQueue.markGenerationComplete();
    }

    private boolean generateCombinations(List<Click> nodeList, int k, int start, List<Click> currentCombination) 
    {
        // Check if the problem has been solved
        if (this.combinationQueue.isItSolved()) 
        {
            return false; // Stop further exploration
        }

        if (nodeList.size() - start < k - currentCombination.size()) 
        {
            return false; // Not enough elements left to form a combination of size k
        }

        if (currentCombination.size() == k) 
        {
            if (trueAdjacents == null || !hasTrueAdjacent(currentCombination)) 
            {
                if (logger.isLoggable(Level.FINE)) 
                {
                    logger.fine("No true adjacents found for combination: " + currentCombination);
                }
                return false; // Prune this branch
            }
            this.combinationQueue.add(new ArrayList<>(currentCombination));
            return true; // Continue exploring
        }

        for (int i = start; i < nodeList.size() && !this.combinationQueue.isItSolved(); i++) 
        {
            currentCombination.add(nodeList.get(i));
            boolean shouldContinue = generateCombinations(nodeList, k, i + 1, currentCombination);
            currentCombination.remove(currentCombination.size() - 1);

            if (!shouldContinue) 
            {
                break;
            }
        }

        return true; // Continue exploring
    }

    private boolean hasTrueAdjacent(List<Click> currentCombination) 
    {
        for (Click click : currentCombination) 
        {
            // Check if the current click exists in trueAdjacents
            if (trueAdjacents.contains(click.row + "," + click.col)) 
            {
                return true; // Found a true adjacent
            }
        }
        return false; // No true adjacents found
    }
}
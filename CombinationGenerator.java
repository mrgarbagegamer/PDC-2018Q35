import java.util.ArrayList;
import java.util.List;
import java.util.Date; // Used for debug line
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

public class CombinationGenerator extends Thread 
{
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
        
        if (trueAdjSet != null) 
        {
            this.trueAdjacents = new HashSet<>();
            for (Integer[] adj : trueAdjSet) 
            {
                this.trueAdjacents.add(adj[0] + "," + adj[1]);
            }
        }
    }

    public void run() 
    {
        ForkJoinPool customPool = new ForkJoinPool(16); // Limit to 8 threads
        customPool.submit(() -> 
            possibleClicks.parallelStream().forEach(click -> {
                List<Click> currentCombination = new ArrayList<>();
                currentCombination.add(click);
                generateCombinations(possibleClicks, numClicks, possibleClicks.indexOf(click) + 1, currentCombination);
            })
        ).join();
        customPool.shutdown();
    }

    private boolean generateCombinations(List<Click> nodeList, int k, int start, List<Click> currentCombination) 
    {
        // Check if the problem has been solved
        if (this.combinationQueue.isItSolved()) 
        {
            return false; // Stop further exploration
        }

        if (currentCombination.size() == k) 
        {
            // Skip combinations with no true adjacents
            if (trueAdjacents == null || !currentCombination.stream().anyMatch(click -> 
                trueAdjacents.contains(click.row + "," + click.col))) 
            {
                Date date = new Date(); // Debug line
                System.out.println("Skipping combination due to no true adjacents: " + currentCombination + " Time: " + date); // Debug line
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
}
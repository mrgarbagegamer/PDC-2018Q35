import java.util.ArrayList;
import java.util.List;
import java.util.Date; // Used for debug line
import java.util.HashSet;
import java.util.Set;

public class CombinationGenerator extends Thread 
{
    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Grid puzzleGrid;
    private ArrayList<Integer[]> trueAdjacents;

    public CombinationGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks, Grid puzzleGrid) 
    {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.puzzleGrid = puzzleGrid;
        this.trueAdjacents = puzzleGrid.findFirstTrueAdjacents();
    }

    public void run() 
    {
        this.generateCombinations(this.possibleClicks, numClicks, 0, new ArrayList<>());
    }

    private boolean generateCombinations(List<Click> nodeList, int k, int start, List<Click> currentCombination) // Returns false to break the previous layer of iteration if no true adjacents are found 
    {
        // Check if the problem has been solved
        if (this.combinationQueue.isItSolved()) 
        {
            return false; // Stop further exploration
        }

        if (currentCombination.size() == k) 
        {
            // Skip combinations with no true adjacents
            if (trueAdjacents == null) 
            {
                return false; // Prune this branch
            }

            Set<String> trueAdjSet = new HashSet<>();
            for (Integer[] adj : trueAdjacents) 
            {
                trueAdjSet.add(adj[0] + "," + adj[1]);
            }

            boolean hasTrueAdjacent = currentCombination.stream().anyMatch(click -> 
                trueAdjSet.contains(click.row + "," + click.col)
            );

            if (!hasTrueAdjacent) 
            {
                Date date = new Date(); // Debug line
                System.out.println("Skipping combination due to no true adjacents: " + currentCombination + " Time: " + date); // Debug line
                return false; // Stop exploring further combinations in this layer
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
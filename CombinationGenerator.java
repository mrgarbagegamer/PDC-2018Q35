import java.util.ArrayList;
import java.util.List;
import java.util.Date; // Used for debug line

public class CombinationGenerator extends Thread 
{
    private CombinationQueue combinationQueue;
    private List<Click> possibleClicks;
    private int numClicks;
    private Grid puzzleGrid;

    public CombinationGenerator(CombinationQueue combinationQueue, List<Click> possibleClicks, int numClicks, Grid puzzleGrid) 
    {
        this.combinationQueue = combinationQueue;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.puzzleGrid = puzzleGrid;
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
            boolean hasTrueAdjacent = false;
            ArrayList<Integer[]> trueAdjacents = puzzleGrid.findFirstTrueAdjacents();
            for (Click click : currentCombination) 
            {
                // Check if any of the cells in the combination is a first true adjacent
                for (Integer[] adj : trueAdjacents) 
                {
                    if (click.row == adj[0] && click.col == adj[1]) 
                    {
                        hasTrueAdjacent = true;
                        break;
                    }
                }
                if (hasTrueAdjacent) 
                {
                    break;
                }
            }
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

        // If there are no first true adjacents after the cell that is at the end of nodelist, we can stop exploring this branch:
        if (currentCombination.size() > 0) 
        {
            Click lastClick = currentCombination.get(currentCombination.size() - 1);
            ArrayList<Integer[]> firstTrueAdjacents = puzzleGrid.findFirstTrueAdjacentsAfter(lastClick.row, lastClick.col);
            if (firstTrueAdjacents == null) 
            {
                return false; // Stop exploring this branch
            }
        }

        return true; // Continue exploring
    }
}
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class TestClickCombination extends Thread 
{
    private CombinationQueue combinationQueue;
    private Grid puzzleGrid;
    private CombinationGenerator combinationGenerator; // Reference to CombinationGenerator

    public TestClickCombination(String threadName, CombinationQueue combinationQueue, Grid puzzleGrid, CombinationGenerator combinationGenerator) 
    {
        this.combinationQueue = combinationQueue;
        this.puzzleGrid = puzzleGrid;
        this.combinationGenerator = combinationGenerator; // Initialize reference
        this.setName(threadName);
    }

    public void run() 
    {
        boolean iSolvedIt = false;

        while (!iSolvedIt && !this.combinationQueue.isItSolved()) 
        {
            List<Click> combinationClicks = this.combinationQueue.getClicksCombination(combinationGenerator);

            if (combinationClicks == null) 
            {
                // Exit if the queue is empty and generation is complete
                if (combinationGenerator.isGenerationComplete()) 
                {
                    System.out.println(this.getName() + " - No more combinations to process. Exiting.");
                    break;
                }
                continue;
            }

            for (int i = 0; (!iSolvedIt) && (!this.combinationQueue.isItSolved()) && (i < combinationClicks.size()); i++) 
            {
                Click click = combinationClicks.get(i);

                this.puzzleGrid.click(click.row, click.col);

                if (this.puzzleGrid.getTrueCount() > (combinationClicks.size() - i - 1) * 6) 
                {
                    break;
                }

                iSolvedIt = this.puzzleGrid.isSolved();

                if (iSolvedIt) 
                {
                    System.out.printf("%s - Found the solution as the following click combination:\n[%s]\n", this.getName(), combinationClicks);
                    this.combinationQueue.solutionFound(this.getName(), combinationClicks);
                    return;
                }

                Set<Integer[]> firstTrueAdjacents = this.puzzleGrid.findFirstTrueAdjacentsAfter(click.row, click.col);
                if (firstTrueAdjacents == null) 
                {
                    break;
                } else 
                {
                    Set<String> adjSet = new HashSet<>();
                    for (Integer[] adj : firstTrueAdjacents) 
                    {
                        adjSet.add(adj[0] + "," + adj[1]);
                    }

                    boolean hasTrueAdjacent = combinationClicks.subList(i + 1, combinationClicks.size()).stream()
                        .anyMatch(c -> adjSet.contains(c.row + "," + c.col));

                    if (!hasTrueAdjacent) 
                    {
                        break;
                    }
                }
            }

            if (!iSolvedIt && !this.combinationQueue.isItSolved()) {
                System.out.printf("%s - Tried and failed: [%s]\n", this.getName(), combinationClicks);
            }

            // Reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }
}

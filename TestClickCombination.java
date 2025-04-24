import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestClickCombination extends Thread 
{
    private static final Logger logger = Logger.getLogger(TestClickCombination.class.getName());
    private CombinationQueue combinationQueue;
    private Grid puzzleGrid;

    public TestClickCombination(String threadName, CombinationQueue combinationQueue, Grid puzzleGrid) 
    {
        this.combinationQueue = combinationQueue;
        this.puzzleGrid = puzzleGrid;
        this.setName(threadName);
    }

    public void run() 
    {
        boolean iSolvedIt = false;

        while (!iSolvedIt && !this.combinationQueue.isItSolved()) 
        {
            List<Click> combinationClicks = this.combinationQueue.getClicksCombination();

            if (combinationClicks == null) 
            {
                // Exit if the queue is empty and generation is complete
                if (combinationQueue.isGenerationComplete()) 
                {
                    logger.info(this.getName() + " - No more combinations to process. Exiting.");
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
                    logger.info(this.getName() + " - Found the solution as the following click combination: " + combinationClicks);
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

            if (!iSolvedIt && !this.combinationQueue.isItSolved() && logger.isLoggable(Level.FINE)) 
            {
                logger.fine(this.getName() + " - Tried and failed: " + combinationClicks);
            }

            // Reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }
}

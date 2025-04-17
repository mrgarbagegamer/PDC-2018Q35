import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TestClickCombination extends Thread 
{
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

        while(!iSolvedIt && !this.combinationQueue.isItSolved())
        {
            List<Click> combinationClicks = this.combinationQueue.getClicksCombination();

            for (int i = 0; (!iSolvedIt) && (!this.combinationQueue.isItSolved()) && (i < combinationClicks.size()); i++) 
            {
                Click click = combinationClicks.get(i);

                this.puzzleGrid.click(click.row, click.col);

                if (this.puzzleGrid.getTrueCount() > (combinationClicks.size() - i - 1) * 6) 
                {
                    // this means we have more true's than clicks left to process, so we can stop early
                    break;
                }
                
                
                iSolvedIt = this.puzzleGrid.isSolved();

                if(iSolvedIt)
                {
                    System.out.printf("%s - Found the solution as the following click combination:\n[%s]\n", this.getName(), combinationClicks);
                    this.combinationQueue.solutionFound(this.getName(), combinationClicks);
                    return;
                }

                ArrayList<Integer[]> firstTrueAdjacents = this.puzzleGrid.findFirstTrueAdjacentsAfter(click.row, click.col);
                if (firstTrueAdjacents == null) // Check if any true adjacents exist after the current click
                {
                    break;
                }
                else
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

            
            if(!iSolvedIt && !this.combinationQueue.isItSolved())
            {
                System.out.printf("%s - Tried and failed: [%s]\n", this.getName(), combinationClicks);
            }

            // reset the grid for the next combination
            this.puzzleGrid.initialize();
        }
    }

}

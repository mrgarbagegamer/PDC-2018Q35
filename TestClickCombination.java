import java.util.List;
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
        boolean solved = false;

        while(!solved)
        {
            List<Click> combinationClicks = this.combinationQueue.getClicksCombination();

            for (int i = 0; (i < combinationClicks.size()) && (!solved); i++) 
            {
                Click click = combinationClicks.get(i);

                this.puzzleGrid.click(click.row, click.col);

                if (this.puzzleGrid.getTrueCount() > (combinationClicks.size() - i - 1) * 6) 
                {
                    // this means we have more true's than clicks left to process, so we can stop early
                    break;
                }
                
                solved = this.puzzleGrid.isSolved();

                if(solved)
                {
                    this.combinationQueue.solutionFound(this.getName(), combinationClicks);
                }

                Set<Integer[]> firstTrueAdjacents = this.puzzleGrid.findFirstTrueAdjacentsAfter(click.row, click.col);
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

            
            if(!solved)
            {
                System.out.printf("%s - Tried and failed: [%s]\n", this.getName(), combinationClicks);
            }
            
        }
    }

}

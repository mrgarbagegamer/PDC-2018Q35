import java.util.List;
import java.util.ArrayList;

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

                ArrayList<Integer[]> trueAdjacents = this.puzzleGrid.findTrueAdjacentsAfter(click.row, click.col);
                if (trueAdjacents == null) // Check if any true adjacents exist after the current click
                {
                    break;
                }
                else
                {
                    boolean hasTrueAdjacent = false;
                    for (Click c : combinationClicks.subList(i + 1, combinationClicks.size())) // iterate through all remaining true adjacents to see if any are in the combination
                    {
                        for (Integer[] adj : trueAdjacents) 
                        {
                            if (c.row == adj[0] && c.col == adj[1]) 
                            {
                                hasTrueAdjacent = true;
                                break;
                            }
                        }
                    }
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

import java.util.ArrayList;
import java.util.List;
import java.util.Date; // Used for debug line
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
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
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new CombinationTask(possibleClicks, numClicks, 0, new ArrayList<>()));
    }

    private class CombinationTask extends RecursiveTask<Boolean> 
    {
        private List<Click> nodeList;
        private int k;
        private int start;
        private List<Click> currentCombination;

        public CombinationTask(List<Click> nodeList, int k, int start, List<Click> currentCombination) 
        {
            this.nodeList = nodeList;
            this.k = k;
            this.start = start;
            this.currentCombination = currentCombination;
        }

        @Override
        protected Boolean compute()
        {
            // Check if the problem has been solved
            if (combinationQueue.isItSolved()) 
            {
                return false;
            }

            // Base case: If the combination size is k
            if (currentCombination.size() == k) 
            {
                if (trueAdjacents == null || currentCombination.stream().noneMatch(click -> trueAdjacents.contains(click.row + "," + click.col))) 
                {
                    // System.out.println("Skipping combination due to no true adjacents: " + currentCombination + " Time: " + (new Date())); // Debug line
                    return false; // Prune this branch
                }
                combinationQueue.add(new ArrayList<>(currentCombination));
                return true;
            }

            // Parallelize recursive calls
            List<CombinationTask> subTasks = new ArrayList<>();
            for (int i = start; i < nodeList.size(); i++) 
            {
                currentCombination.add(nodeList.get(i));
                CombinationTask task = new CombinationTask(nodeList, k, i + 1, new ArrayList<>(currentCombination));
                subTasks.add(task);
                currentCombination.remove(currentCombination.size() - 1);
            }

            // Invoke all subtasks in parallel
            invokeAll(subTasks);

            // Check if any subtask returned false (indicating termination)
            return subTasks.stream().allMatch(RecursiveTask::join);
        }
    }
}
import java.util.List;
import java.util.Queue;
import java.util.LinkedList;
import java.util.ArrayList;

public class CombinationQueue 
{
    public Queue<List<Click>> combinationQueue = new LinkedList<>();
    private boolean solutionFound = false;
    private String winningMonkey = null;
    private List<Click> winningCombination = null;

    private static final int MAX_SIZE = 50000000;
    private static final int WAIT_MS = 5;

    boolean isItSolved() 
    {
        return this.solutionFound;
    }

    synchronized void solutionFound(String monkeyName, List<Click> winningCombination) 
    {
        this.solutionFound = true;
        this.winningMonkey = monkeyName;
        this.winningCombination = winningCombination;
    }

    public String getWinningMonkey()
    {
        return this.winningMonkey;
    }

    public List<Click> getWinningCombination()
    {
        return this.winningCombination;
    }

    synchronized boolean add(List<Click> combinationClicks) 
    {
        while( this.combinationQueue.size() == MAX_SIZE )
        {
            this.notifyAll();

            try 
            {
                wait(WAIT_MS);
            } catch (InterruptedException e) 
            {
                e.printStackTrace();
            }
        }

        synchronized (combinationQueue) {
            combinationQueue.add(new ArrayList<>(combinationClicks));
            System.out.printf("Added combination to the queue. Queue size is now %d\n", combinationQueue.size());
        }

        this.notifyAll();

        return true;
    }

    synchronized List<Click> getClicksCombination() 
    {
        List<Click> combinationClicks = null;

        while (combinationClicks == null) 
        {
            combinationClicks = this.combinationQueue.poll();

            try 
            {
                wait();
            } catch (InterruptedException e) 
            {
                e.printStackTrace();
            }
        }

        this.notifyAll();

        return combinationClicks;
    }
}
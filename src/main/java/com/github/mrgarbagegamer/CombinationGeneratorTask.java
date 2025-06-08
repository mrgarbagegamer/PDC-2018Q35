package com.github.mrgarbagegamer;

import java.util.concurrent.RecursiveAction;
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntList;

public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 2000;

    private final IntList possibleClicks;
    private final int numClicks;
    private final int[] prefix;
    private final int prefixLength;
    private final CombinationQueueArray queueArray;
    private final int numConsumers;
    private final int[] trueCells;

    public CombinationGeneratorTask(IntList possibleClicks, int numClicks, int[] prefix, int prefixLength,
                                   CombinationQueueArray queueArray, int numConsumers, int[] trueCells) 
    {
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.queueArray = queueArray;
        this.numConsumers = numConsumers;
        this.trueCells = trueCells;
    }

    @Override
    protected void compute() 
    { 
        if (prefixLength < numClicks - 1) // TODO: Add in the finalFirstTrueAdjacent bound check for extra optimization 
        { // TODO: Add logging for debugging (taking inspiration from CombinationGenerator.java and the previous commit of StartYourMonkeys.java)
            List<CombinationGeneratorTask> subtasks = new ArrayList<>();
            // The next index must be greater than the last used index in prefix (or 0 if prefix is empty)
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size() - (numClicks - prefixLength) + 1;
            for (int i = start; i < max; i++) 
            {
                int[] newPrefix = new int[prefixLength + 1];
                System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
                newPrefix[prefixLength] = i; // Add the next index to the prefix

                subtasks.add(new CombinationGeneratorTask(possibleClicks, numClicks, newPrefix, prefixLength + 1, queueArray, numConsumers, trueCells));
            }
            invokeAll(subtasks);
        } else 
        {
            // At prefix length numClicks-1, generate the last click and submit the full combination
            List<int[]> batch = new ArrayList<>(BATCH_SIZE);
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size();
            for (int i = start; i < max; i++) 
            {
                int[] combination = new int[numClicks];
                System.arraycopy(prefix, 0, combination, 0, prefixLength);

                for (int j = 0; j < prefixLength; j++) 
                {
                    combination[j] = possibleClicks.getInt(combination[j]);
                }

                combination[prefixLength] = possibleClicks.getInt(i); // Add the last click

                // Prune using quickOddAdjacency on the first true cell
                if (trueCells != null && trueCells.length > 0 &&
                    !quickOddAdjacency(combination, trueCells[0])) 
                {
                    continue;
                }

                batch.add(combination);
                if (batch.size() >= BATCH_SIZE) 
                {
                    flushBatch(batch);
                }
            }
            flushBatch(batch);
        }
    }

    private void flushBatch(List<int[]> batch) 
    {
        int roundRobinIdx = 0;
        while (!batch.isEmpty() && !queueArray.isSolutionFound()) 
        {
            boolean addedAny = false;
            for (int attempt = 0; attempt < numConsumers && !batch.isEmpty(); attempt++) 
            {
                int idx = (roundRobinIdx + attempt) % numConsumers;
                int added = queueArray.getQueue(idx).addBatch(batch);
                if (added > 0) 
                {
                    batch.subList(0, added).clear();
                    roundRobinIdx = (idx + 1) % numConsumers;
                    addedAny = true;
                }
            }
            if (!addedAny) 
            {
                try 
                { 
                    Thread.sleep(5); 
                } catch (InterruptedException e) 
                { 
                    Thread.currentThread().interrupt(); 
                    break; 
                }
            }
        }
    }

    private static boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        int count = 0;
        for (int click : combination) 
        {
            if (Grid.areAdjacent(firstTrueCell, click)) count++;
        }
        return (count & 1) == 1;
    }
}
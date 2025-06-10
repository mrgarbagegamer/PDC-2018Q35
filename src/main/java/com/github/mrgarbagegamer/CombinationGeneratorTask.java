package com.github.mrgarbagegamer;

import java.util.concurrent.RecursiveAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import it.unimi.dsi.fastutil.ints.IntList;

public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 2000;
    private static final int POOL_SIZE = 4096;
    
    // Thread-local pool for integer arrays
    private static final ThreadLocal<ArrayList<int[]>> intArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayList<>(POOL_SIZE));
    
    private final IntList possibleClicks;
    private final int numClicks;
    private final int[] prefix;
    private final int prefixLength;
    private final CombinationQueueArray queueArray;
    private final int numConsumers;
    private final int[] trueCells;
    
    // Added field to track cancellation within a subtask hierarchy
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CombinationGeneratorTask parent;

    public CombinationGeneratorTask(IntList possibleClicks, int numClicks, int[] prefix, int prefixLength,
                                   CombinationQueueArray queueArray, int numConsumers, int[] trueCells) 
    {
        this(possibleClicks, numClicks, prefix, prefixLength, queueArray, numConsumers, trueCells, null);
    }
    
    // Private constructor to handle parent relationship
    private CombinationGeneratorTask(IntList possibleClicks, int numClicks, int[] prefix, int prefixLength,
                                   CombinationQueueArray queueArray, int numConsumers, int[] trueCells,
                                   CombinationGeneratorTask parent) 
    {
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.queueArray = queueArray;
        this.numConsumers = numConsumers;
        this.trueCells = trueCells;
        this.parent = parent;
    }

    // Check if this task or any parent task has been cancelled
    private boolean isTaskCancelled() {
        if (cancelled.get()) return true;
        if (queueArray.isSolutionFound()) {
            cancelled.set(true);
            return true;
        }
        if (parent != null && parent.isTaskCancelled()) {
            cancelled.set(true);
            return true;
        }
        return false;
    }

    @Override
    protected void compute() 
    {
        // Check for cancellation before starting work
        if (isTaskCancelled()) {
            return;
        }
        
        if (prefixLength < numClicks - 1)
        { 
            List<CombinationGeneratorTask> subtasks = new ArrayList<>();
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size() - (numClicks - prefixLength) + 1;
            
            for (int i = start; i < max; i++) 
            {
                // Check for solution found at regular intervals
                if (i % 100 == 0 && isTaskCancelled()) {
                    break;
                }
                
                // Replace with pooled array
                int[] newPrefix = getIntArray(prefixLength + 1);
                System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
                newPrefix[prefixLength] = i;

                // Create subtask with this as parent
                subtasks.add(new CombinationGeneratorTask(
                    possibleClicks, numClicks, newPrefix, prefixLength + 1, 
                    queueArray, numConsumers, trueCells, this));
            }
            
            if (!subtasks.isEmpty()) {
                try {
                    invokeAll(subtasks);
                    // Add recycling of arrays after subtasks complete
                    for (CombinationGeneratorTask task : subtasks) {
                        recycleIntArray(task.prefix);
                    }
                } catch (CancellationException ce) {
                    // Task was cancelled, just return
                    return;
                }
            }
        } 
        else 
        {
            List<int[]> batch = new ArrayList<>(BATCH_SIZE);
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size();
            
            for (int i = start; i < max; i++) 
            {
                // Check for solution found at regular intervals
                if (i % 100 == 0 && isTaskCancelled()) {
                    // Recycle all arrays in the batch and return
                    for (int[] arr : batch) {
                        recycleIntArray(arr);
                    }
                    batch.clear();
                    return;
                }
                
                int[] combination = getIntArray(numClicks);
                
                for (int j = 0; j < prefixLength; j++) 
                {
                    combination[j] = possibleClicks.getInt(prefix[j]);
                }

                combination[prefixLength] = possibleClicks.getInt(i);

                if (trueCells != null && trueCells.length > 0 &&
                    !quickOddAdjacency(combination, trueCells[0])) 
                {
                    recycleIntArray(combination);
                    continue;
                }

                batch.add(combination);
                if (batch.size() >= BATCH_SIZE) 
                {
                    flushBatch(batch);
                    
                    // Check if solution was found during flush
                    if (isTaskCancelled()) {
                        return;
                    }
                }
            }
            
            // Final flush if needed
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }
        }
    }

    private void flushBatch(List<int[]> batch) 
    {
        int roundRobinIdx = 0;
        while (!batch.isEmpty()) 
        {
            // Check for cancellation first
            if (isTaskCancelled()) {
                // Recycle all arrays still in the batch
                for (int[] arr : batch) {
                    recycleIntArray(arr);
                }
                batch.clear();
                return;
            }
            
            boolean addedAny = false;
            for (int attempt = 0; attempt < numConsumers && !batch.isEmpty(); attempt++) 
            {
                // Re-check cancellation within the inner loop
                if (isTaskCancelled()) break;
                
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
        
        // Any remaining arrays should be recycled
        for (int[] arr : batch) {
            recycleIntArray(arr);
        }
        batch.clear();
    }

    // Thread-local array pooling methods - unchanged
    private static int[] getIntArray(int size) {
        ArrayList<int[]> pool = intArrayPool.get();
        for (int i = 0; i < pool.size(); i++) {
            int[] arr = pool.get(i);
            if (arr.length == size) {
                pool.remove(i);
                return arr;
            }
        }
        return new int[size];
    }

    private static void recycleIntArray(int[] arr) {
        ArrayList<int[]> pool = intArrayPool.get();
        if (pool.size() < POOL_SIZE) {
            pool.add(arr);
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
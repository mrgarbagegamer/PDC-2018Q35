package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import it.unimi.dsi.fastutil.ints.IntList;

// TODO: Reformat all lines to place curly brackets on different lines than the method signature (for consistency with the rest of the codebase)
public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 2000;
    private static final int POOL_SIZE = 4096;
    
    // Size-specific pools for better performance
    private static final ThreadLocal<ArrayDeque<int[]>> prefixArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(POOL_SIZE / 2));
    private static final ThreadLocal<ArrayDeque<int[]>> combinationArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(POOL_SIZE / 2));

    private final IntList possibleClicks;
    private final int numClicks;
    private final int[] prefix;
    private final int prefixLength;
    private final CombinationQueueArray queueArray;
    private final int numConsumers;
    private final int[] trueCells;
    private final int maxFirstClickIndex;
    
    // Added field to track cancellation within a subtask hierarchy
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CombinationGeneratorTask parent;

    public CombinationGeneratorTask(IntList possibleClicks, int numClicks, int[] prefix, int prefixLength,
                                   CombinationQueueArray queueArray, int numConsumers, int[] trueCells,
                                   int maxFirstClickIndex) 
    {
        this(possibleClicks, numClicks, prefix, prefixLength, queueArray, numConsumers, trueCells, maxFirstClickIndex, null);
    }
    
    // Private constructor to handle parent relationship
    private CombinationGeneratorTask(IntList possibleClicks, int numClicks, int[] prefix, int prefixLength,
                                   CombinationQueueArray queueArray, int numConsumers, int[] trueCells,
                                   int maxFirstClickIndex, CombinationGeneratorTask parent) 
    {
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.queueArray = queueArray;
        this.numConsumers = numConsumers;
        this.trueCells = trueCells;
        this.parent = parent;
        this.maxFirstClickIndex = maxFirstClickIndex;
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

            if (prefixLength == 0) max = Math.min(max, maxFirstClickIndex + 1);
            
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
                    queueArray, numConsumers, trueCells, maxFirstClickIndex, this));
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
        if (batch.isEmpty()) return;
        
        // Try to add the entire batch to one queue first (reduces contention)
        int startQueue = ThreadLocalRandom.current().nextInt(numConsumers);
        CombinationQueue queue = queueArray.getQueue(startQueue);
        int added = queue.addBatch(batch);
        
        if (added == batch.size()) {
            batch.clear();
            return;
        }
        
        // Remove successfully added items
        if (added > 0) {
            batch.subList(0, added).clear();
        }
        
        // Fall back to round-robin for remainder, but with exponential backoff
        int backoffMs = 1;
        while (!batch.isEmpty() && !isTaskCancelled()) 
        {
            boolean addedAny = false;
            
            for (int attempt = 0; attempt < numConsumers && !batch.isEmpty(); attempt++) 
            {
                int idx = (startQueue + attempt) % numConsumers;
                added = queueArray.getQueue(idx).addBatch(batch);
                if (added > 0) 
                {
                    batch.subList(0, added).clear();
                    addedAny = true;
                    backoffMs = 1; // Reset backoff on success
                    break;
                }
            }
            
            if (!addedAny) 
            {
                try { 
                    Thread.sleep(Math.min(backoffMs, 16)); // Exponential backoff up to 16ms
                    backoffMs = Math.min(backoffMs * 2, 16);
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt();
                    break; 
                }
            }
        }
        
        // Recycle remaining arrays
        for (int[] arr : batch) {
            recycleIntArray(arr);
        }
        batch.clear();
    }

    // Thread-local array pooling methods - corrected to be non-static
    private int[] getIntArray(int size) {
        if (size < numClicks) {  // Prefix arrays (smaller than full combinations)
            ArrayDeque<int[]> pool = prefixArrayPool.get();
            int[] arr = pool.pollFirst();
            if (arr != null && arr.length >= size) {
                return arr;
            }
            // Return smaller array to pool for future use
            if (arr != null && arr.length < size) {
                pool.offerFirst(arr);
            }
        } else {  // Full combination arrays (size == numClicks)
            ArrayDeque<int[]> pool = combinationArrayPool.get();
            int[] arr = pool.pollFirst();
            if (arr != null && arr.length >= size) {
                return arr;
            }
            // Return smaller array to pool for future use  
            if (arr != null && arr.length < size) {
                pool.offerFirst(arr);
            }
        }
        return new int[size];
    }

    private void recycleIntArray(int[] arr) {
        if (arr.length < numClicks) {  // Prefix arrays
            ArrayDeque<int[]> pool = prefixArrayPool.get();
            if (pool.size() < POOL_SIZE / 2) {
                pool.offerFirst(arr);
            }
        } else {  // Full combination arrays (arr.length == numClicks)
            ArrayDeque<int[]> pool = combinationArrayPool.get();
            if (pool.size() < POOL_SIZE / 2) {
                pool.offerFirst(arr);
            }
        }
    }

    // Add these fields to cache adjacents for the current firstTrueCell
    private static volatile BitSet FIRST_TRUE_ADJACENTS_BITSET = null;
    private static volatile int CACHED_FIRST_TRUE_CELL = -1;

    private static boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        // Lazy initialization of adjacents BitSet
        if (CACHED_FIRST_TRUE_CELL != firstTrueCell) {
            int[] adjacents = Grid.findAdjacents(firstTrueCell);
            FIRST_TRUE_ADJACENTS_BITSET = new BitSet(700); // Adjust size as needed for your grid
            for (int adj : adjacents) {
                FIRST_TRUE_ADJACENTS_BITSET.set(adj);
            }
            CACHED_FIRST_TRUE_CELL = firstTrueCell;
        }
        
        int count = 0;
        // O(1) adjacency check per click
        for (int click : combination) {
            if (FIRST_TRUE_ADJACENTS_BITSET.get(click)) {
                count++;
            }
        }
        return (count & 1) == 1;
    }
}
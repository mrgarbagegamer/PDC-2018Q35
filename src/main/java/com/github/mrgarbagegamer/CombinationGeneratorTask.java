package com.github.mrgarbagegamer;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

import it.unimi.dsi.fastutil.ints.IntList;

// TODO: Reformat all lines to place curly brackets on different lines than the method signature (for consistency with the rest of the codebase)
public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 4000; // Increase from 2000 to reduce flush overhead
    private static final int POOL_SIZE = 2048; // Reduced since we're using more efficient pools
    
    // Replace ArrayDeque pools with custom high-performance pools
    private static final ThreadLocal<ArrayPool> prefixArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayPool(POOL_SIZE / 4, 32)); // Smaller arrays
    private static final ThreadLocal<ArrayPool> combinationArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayPool(POOL_SIZE / 4, 64)); // Full combination arrays
        
    // Custom subtask pool instead of nested ArrayDeques
    private static final ThreadLocal<SubtaskPool> subtaskPool = 
        ThreadLocal.withInitial(() -> new SubtaskPool(32));

    // ThreadLocal batch for each worker thread
    private static final ThreadLocal<WorkBatch> THREAD_BATCH = 
        ThreadLocal.withInitial(() -> new WorkBatch(BATCH_SIZE));

    private final IntList possibleClicks;
    private final int numClicks;
    private final int[] prefix;
    private final int prefixLength;
    private final CombinationQueueArray queueArray;
    private final int numConsumers;
    private final int[] trueCells;
    private final int maxFirstClickIndex;
    
    // Added field to track cancellation within a subtask hierarchy
    private volatile boolean cancelled = false; // Use a volatile boolean instead of an AtomicBoolean to achieve the same goal without temporary object creation (and with greater efficiency)
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
    private boolean isTaskCancelled() 
    {
        if (cancelled) return true;
        if (queueArray.isSolutionFound()) return cancelled = true;
        if (parent != null && parent.isTaskCancelled()) return cancelled = true;
        return false;
    }

    @Override
    protected void compute()
    {
        // Check for cancellation before starting work
        if (isTaskCancelled()) return;
        
        if (prefixLength < numClicks - 1) 
        {
            // Before creating subtasks, check if this prefix path can possibly lead to a solution
            if (prefixLength >= 2) 
            {
                // Check all true cells, not just the first one
                if (trueCells != null && trueCells.length > 0) 
                {
                    boolean canPotentiallySatisfyAll = true;
                    
                    for (int trueCell : trueCells) 
                    {
                        // Count adjacents from the prefix
                        int adjacentCount = 0;
                        for (int j = 0; j < prefixLength; j++) 
                        {
                            int cell = possibleClicks.getInt(prefix[j]);
                            if (Grid.areAdjacent(trueCell, cell))
                            {
                                adjacentCount++;
                            }
                        }
                        
                        // Check if we could potentially satisfy odd adjacency
                        boolean needsOdd = (adjacentCount & 1) == 0;
                        
                        // If we need an odd number of adjacents, check if it's possible
                        if (needsOdd) 
                        {
                            boolean foundPossible = false;
                            // Check if any remaining position could be adjacent
                            for (int i = prefix[prefixLength-1] + 1; i < possibleClicks.size(); i++)
                            {
                                int cell = possibleClicks.getInt(i);
                                if (Grid.areAdjacent(trueCell, cell))
                                {
                                    foundPossible = true;
                                    break;
                                }
                            }
                            
                            if (!foundPossible) 
                            {
                                canPotentiallySatisfyAll = false;
                                break;
                            }
                        }
                    }
                    
                    // Skip this entire branch if it can't possibly satisfy constraints
                    if (!canPotentiallySatisfyAll) 
                    {
                        return;
                    }
                }
            }
        
            // Use simple array instead of Deque for subtasks
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size() - (numClicks - prefixLength) + 1;

            if (prefixLength == 0) max = Math.min(max, maxFirstClickIndex + 1);
            
            int numSubtasks = max - start;
            if (numSubtasks <= 0) return;
            
            // Get subtask array from pool or allocate new one
            CombinationGeneratorTask[] subtasks = getSubtaskArray(numSubtasks);
            int subtaskCount = 0;
            
            for (int i = start; i < max; i++) 
            {
                // Check for solution found at regular intervals
                if (i % 100 == 0 && isTaskCancelled()) 
                {
                    putSubtaskArray(subtasks);
                    return;
                }
                
                // Get prefix array from pool
                int[] newPrefix = getIntArray(prefixLength + 1);
                System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
                newPrefix[prefixLength] = i;

                // Create subtask with this as parent
                subtasks[subtaskCount++] = new CombinationGeneratorTask(
                    possibleClicks, numClicks, newPrefix, prefixLength + 1, 
                    queueArray, numConsumers, trueCells, maxFirstClickIndex, this);
            }
            
            if (subtaskCount > 0) 
            {
                try 
                {
                    List<CombinationGeneratorTask> taskList = Arrays.asList(subtasks).subList(0, subtaskCount);
                    invokeAll(taskList);

                    // Recycle prefix arrays after subtasks complete
                    for (CombinationGeneratorTask subtask : taskList) putIntArray(subtask.prefix);
                } catch (CancellationException ce) 
                {
                    // Task was cancelled, just return
                    
                } 
                finally 
                {
                    putSubtaskArray(subtasks);
                }
            } 
            else putSubtaskArray(subtasks);
        } 
        else 
        {
            // Direct generation without intermediate arrays for leaf level
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size();
            
            // Reuse a single combination array
            int[] combination = getIntArray(numClicks);
            
            // Copy prefix once
            for (int j = 0; j < prefixLength; j++) combination[j] = possibleClicks.getInt(prefix[j]);
            
            WorkBatch batch = getBatch();
            
            for (int i = start; i < max; i++) 
            {
                if (i % 100 == 0 && isTaskCancelled()) 
                {
                    putIntArray(combination);
                    return;
                }
                
                combination[prefixLength] = possibleClicks.getInt(i);
                
                if (trueCells != null && trueCells.length > 0 &&
                    !quickOddAdjacency(combination, trueCells[0])) 
                {
                    continue;
                }
                
                // Only clone when adding to batch
                int[] clone = combination.clone();
                batch.add(clone);
                
                if (batch.size() >= BATCH_SIZE) 
                {
                    flushBatch(batch);
                    batch.clear(); // Clear after flushing
                    if (isTaskCancelled()) 
                    {
                        putIntArray(combination);
                        return;
                    }
                }
            }
            putIntArray(combination);
            // Don't flush partial batches - they'll be flushed by the main thread later
        }
    }

    // Extract shared flushing logic
    private static void flushBatchHelper(WorkBatch batch, CombinationQueueArray queueArray, boolean checkCancellation)
    {
        if (batch == null || batch.isEmpty()) return;
        
        CombinationQueue[] queues = queueArray.getAllQueues();
        int numQueues = queues.length;
        int startQueue = ThreadLocalRandom.current().nextInt(numQueues);
        
        while (!batch.isEmpty()) 
        {
            boolean addedAny = false;
            
            for (int attempt = 0; attempt < numQueues && !batch.isEmpty(); attempt++) 
            {
                int idx = (startQueue + attempt) % numQueues;
                CombinationQueue queue = queues[idx];
                
                // Use the new batch fill operation instead of individual adds
                int added = queue.fillFromWorkBatch(batch);
                
                if (added > 0) // The queue has successfully accepted some combinations from the batch
                {
                    addedAny = true;
                    startQueue = (idx + 1) % numQueues;
                    break;
                }
            }
            
            if (!addedAny) 
            {
                try 
                { 
                    Thread.sleep(1); 
                } catch (InterruptedException e) 
                { 
                    Thread.currentThread().interrupt();
                    break; 
                }
            }

            // Only check cancellation if requested (for task flushing, not final flush)
            if (checkCancellation && queueArray.isSolutionFound()) break;
        }
    }

    // Simplified flushBatch method
    private void flushBatch(WorkBatch batch) 
    {
        if (batch.isEmpty()) return;
        
        flushBatchHelper(batch, queueArray, true);

        // Removed UAF: Never recycle arrays that have been added to the queue
    }
    
    // Method to flush all pending batches from worker threads
    public static void flushAllPendingBatches(CombinationQueueArray queueArray, ForkJoinPool pool) 
    {
        // Submit a small task to each worker thread to flush its batch
        pool.submit(() -> {
            WorkBatch batch = THREAD_BATCH.get();
            if (batch != null && !batch.isEmpty()) 
            {
                flushBatchHelper(batch, queueArray, false);
                batch.clear();
            }
            return null;
        }).join(); // Wait for completion
    }

    private WorkBatch getBatch() 
    {
        WorkBatch batch = THREAD_BATCH.get();
        if (batch.size() >= BATCH_SIZE) 
        {
            flushBatch(batch);
            batch.clear();
        }
        return batch;
    }
    
    // High-performance array pooling using custom pools
    private int[] getIntArray(int size) 
    {
        if (size < numClicks) // Prefix arrays (smaller than full combinations)
        {
            ArrayPool pool = prefixArrayPool.get();
            int[] arr = pool.get(size);
            if (arr != null) return arr;
        } 
        else // Full combination arrays (size == numClicks)
        {
            ArrayPool pool = combinationArrayPool.get();
            int[] arr = pool.get(size);
            if (arr != null) return arr;
        }
        return new int[size]; // Allocate a new array if no suitable one was found
    }

    private void putIntArray(int[] arr) 
    {
        if (arr == null) return;
        
        if (arr.length < numClicks) // Prefix arrays
        {
            ArrayPool pool = prefixArrayPool.get();
            pool.put(arr);
        } 
        else // Full combination arrays
        {  
            ArrayPool pool = combinationArrayPool.get();
            pool.put(arr);
        }
    }

    // High-performance subtask array pooling
    private CombinationGeneratorTask[] getSubtaskArray(int minSize) 
    {
        SubtaskPool pool = subtaskPool.get();
        CombinationGeneratorTask[] array = pool.get();
        
        if (array != null && array.length >= minSize) return array;
        
        // Put back if too small and allocate new one
        if (array != null) pool.put(array);
        
        return new CombinationGeneratorTask[Math.max(minSize, 64)]; // Pre-size for typical use
    }

    private void putSubtaskArray(CombinationGeneratorTask[] array) 
    {
        if (array == null) return;
        
        SubtaskPool pool = subtaskPool.get();
        pool.put(array);
    }

    // Add these fields to cache adjacents for the current firstTrueCell
    private static volatile BitSet FIRST_TRUE_ADJACENTS_BITSET = null;
    private static volatile int CACHED_FIRST_TRUE_CELL = -1;

    private static boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        // Lazy initialization of adjacents BitSet
        if (CACHED_FIRST_TRUE_CELL != firstTrueCell) 
        {
            int[] adjacents = Grid.findAdjacents(firstTrueCell);
            FIRST_TRUE_ADJACENTS_BITSET = new BitSet(700); // Adjust size as needed for your grid
            for (int adj : adjacents) FIRST_TRUE_ADJACENTS_BITSET.set(adj);
            CACHED_FIRST_TRUE_CELL = firstTrueCell;
        }
        
        int count = 0;
        // O(1) adjacency check per click
        for (int click : combination) 
        {
            if (FIRST_TRUE_ADJACENTS_BITSET.get(click)) count++;
        }
        return (count & 1) == 1;
    }
}
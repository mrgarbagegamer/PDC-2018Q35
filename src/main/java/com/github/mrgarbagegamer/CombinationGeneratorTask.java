package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import it.unimi.dsi.fastutil.ints.IntList;

// TODO: Reformat all lines to place curly brackets on different lines than the method signature (for consistency with the rest of the codebase)
public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 4000; // Increase from 2000 to reduce flush overhead
    private static final int POOL_SIZE = 4096;
    
    // Size-specific pools for better performance
    private static final ThreadLocal<Deque<int[]>> prefixArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(POOL_SIZE / 2));
    private static final ThreadLocal<Deque<int[]>> combinationArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(POOL_SIZE / 2));

    // Add an ArrayList CombinationGeneratorTask pool for thread-safe reuse
    private static final ThreadLocal<Deque<Deque<CombinationGeneratorTask>>> SUBTASK_DEQUE_POOL = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(16));

    // ThreadLocal batch for each worker thread
    private static final ThreadLocal<Deque<int[]>> THREAD_BATCH = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(BATCH_SIZE));

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
            // Before creating subtasks, check if this prefix path can possibly lead to a solution
            if (prefixLength >= 2) 
            {
                // Check all true cells, not just the first one
                if (trueCells != null && trueCells.length > 0) 
                {
                    boolean canPotentiallySatisfyAll = true;
                    
                    for (int trueCell : trueCells) {
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
        
            // Use pooled ArrayDeque for subtasks
            Deque<CombinationGeneratorTask> subtasks = getSubtaskDeque();
            
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size() - (numClicks - prefixLength) + 1;

            if (prefixLength == 0) max = Math.min(max, maxFirstClickIndex + 1);
            
            for (int i = start; i < max; i++) 
            {
                // Check for solution found at regular intervals
                if (i % 100 == 0 && isTaskCancelled()) {
                    recycleSubtaskDeque(subtasks);
                    return;
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
            
            if (!subtasks.isEmpty()) 
            {
                try 
                {
                    invokeAll(subtasks);
                    // Add recycling of prefix arrays after subtasks complete (prefix arrays can be reused safely since they are not used past generation)
                    for (CombinationGeneratorTask task : subtasks) recycleIntArray(task.prefix);
                } catch (CancellationException ce) 
                {
                    // Task was cancelled, just return
                    return;
                } 
                finally 
                {
                    recycleSubtaskDeque(subtasks);
                }
            } else recycleSubtaskDeque(subtasks);
        } 
        else 
        {
            // Direct generation without intermediate arrays for leaf level
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size();
            
            // Reuse a single combination array
            int[] combination = getIntArray(numClicks);
            
            // Copy prefix once
            for (int j = 0; j < prefixLength; j++) {
                combination[j] = possibleClicks.getInt(prefix[j]);
            }
            
            Deque<int[]> batch = getBatch();
            
            for (int i = start; i < max; i++) {
                if (i % 100 == 0 && isTaskCancelled()) {
                    return;
                }
                
                combination[prefixLength] = possibleClicks.getInt(i);
                
                if (trueCells != null && trueCells.length > 0 &&
                    !quickOddAdjacency(combination, trueCells[0])) {
                    continue;
                }
                
                // Only clone when adding to batch
                int[] clone = combination.clone();
                batch.add(clone);
                
                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(batch);
                    batch.clear(); // Clear after flushing
                    if (isTaskCancelled()) {
                        return;
                    }
                }
            }
            
            // Don't flush partial batches - they'll be flushed by the main thread later
        }
    }

    // Extract shared flushing logic
    private static void flushBatchHelper(Deque<int[]> batch, CombinationQueueArray queueArray, boolean checkCancellation)
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
                int added = queue.fillFromBatch(batch);
                
                if (added > 0) // The queue has successfully accepted some combinations from the batch
                {
                    addedAny = true;
                    startQueue = (idx + 1) % numQueues;
                    break;
                }
            }
            
            if (!addedAny) 
            {
                try { 
                    Thread.sleep(1); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt();
                    break; 
                }
            }

            // Only check cancellation if requested (for task flushing, not final flush)
            if (checkCancellation && queueArray.isSolutionFound()) 
            {
                break;
            }
        }
    }

    // Simplified flushBatch method
    private void flushBatch(Deque<int[]> batch) 
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
            Deque<int[]> batch = THREAD_BATCH.get();
            if (batch != null && !batch.isEmpty()) 
            {
                flushBatchHelper(batch, queueArray, false);
                batch.clear();
            }
            return null;
        }).join(); // Wait for completion
    }
    
    private Deque<int[]> getBatch() 
    {
        Deque<int[]> batch = THREAD_BATCH.get();
        if (batch.size() >= BATCH_SIZE) 
        {
            flushBatch(batch);
            batch.clear();
        }
        return batch;
    }
    
    // Thread-local array pooling methods - corrected to be non-static
    private int[] getIntArray(int size) 
    {
        if (size < numClicks) // Prefix arrays (smaller than full combinations)
        {
            Deque<int[]> pool = prefixArrayPool.get();
            int[] arr = pool.pollFirst();
            if (arr != null && arr.length >= size) return arr;

            // Return smaller array to pool for future use
            if (arr != null && arr.length < size) pool.offerLast(arr);
        } else // Full combination arrays (size == numClicks)
        {
            Deque<int[]> pool = combinationArrayPool.get();
            int[] arr = pool.pollFirst();
            if (arr != null && arr.length >= size) return arr;

            // Return smaller array to pool for future use  
            if (arr != null && arr.length < size) pool.offerLast(arr);
        }
        return new int[size]; // Allocate a new array if no suitable one was found.
    }

    private void recycleIntArray(int[] arr) 
    {
        if (arr.length < numClicks) // Prefix arrays (arr.length < numClicks)
        {
            Deque<int[]> pool = prefixArrayPool.get();
            if (pool.size() < POOL_SIZE / 2) pool.offerLast(arr);
        } else // Full combination arrays (arr.length == numClicks) 
        {  
            Deque<int[]> pool = combinationArrayPool.get();
            if (pool.size() < POOL_SIZE / 2) pool.offerLast(arr);
        }
    }

    // ArrayList pool management methods
    private Deque<CombinationGeneratorTask> getSubtaskDeque() 
    {
        Deque<Deque<CombinationGeneratorTask>> pool = SUBTASK_DEQUE_POOL.get();
        Deque<CombinationGeneratorTask> deque = pool.pollFirst();
        if (deque == null) return new ArrayDeque<>(64); // Pre-sized for typical subtask count

        deque.clear(); // Clear contents before reuse (potentially replace this with an invokeAll)
        return deque;
    }

    private void recycleSubtaskDeque(Deque<CombinationGeneratorTask> deque) 
    {
        if (deque == null) return;

        deque.clear(); // Clear contents before recycling (potentially replace this with an invokeAll)
        Deque<Deque<CombinationGeneratorTask>> pool = SUBTASK_DEQUE_POOL.get();
        if (pool.size() < 16) pool.offerLast(deque);
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
package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ForkJoinPool;

import it.unimi.dsi.fastutil.ints.IntList;

// TODO: Reformat all lines to place curly brackets on different lines than the method signature (for consistency with the rest of the codebase)
public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 4000; // Increase from 2000 to reduce flush overhead
    private static final int POOL_SIZE = 4096;

    private static final boolean DEBUG_MODE = true; // Set to true for debugging purposes
    
    // Size-specific pools for better performance
    private static final ThreadLocal<ArrayDeque<int[]>> prefixArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(POOL_SIZE / 2));
    private static final ThreadLocal<ArrayDeque<int[]>> combinationArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(POOL_SIZE / 2));

    // Add an ArrayList CombinationGeneratorTask pool for thread-safe reuse
    private static final ThreadLocal<ArrayDeque<List<CombinationGeneratorTask>>> SUBTASK_LIST_POOL = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(16));

    // ThreadLocal batch for each worker thread - CHANGED FROM ArrayList TO ArrayDeque
    private static final ThreadLocal<ArrayDeque<int[]>> THREAD_BATCH = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(BATCH_SIZE));

    // Pool for batch deques to avoid deque creation overhead
    private static final ThreadLocal<ArrayDeque<ArrayDeque<int[]>>> BATCH_DEQUE_POOL = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(16));

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
            // Enhanced pruning at special levels of the search tree
            if (prefixLength >= 2 && trueCells != null && trueCells.length > 0) 
            {
                // Special case: we're at the N-2 level of the search tree (about to generate leaf nodes)
                // At this level, we can do more aggressive pruning
                if (prefixLength == numClicks - 2) {
                    // We need to count adjacents for each true cell
                    int[] adjacentCounts = new int[trueCells.length]; // TODO: Grab from a pool rather than allocating a new array
                    boolean[] needsOdd = new boolean[trueCells.length];
                    
                    // Calculate current adjacent count for each true cell
                    for (int tcIdx = 0; tcIdx < trueCells.length; tcIdx++) {
                        int trueCell = trueCells[tcIdx];
                        for (int j = 0; j < prefixLength; j++) {
                            int cell = possibleClicks.getInt(prefix[j]);
                            if (Grid.areAdjacent(trueCell, cell)) {
                                adjacentCounts[tcIdx]++;
                        }
                    }
                    // If count is even, we need an odd number of additional adjacents
                    needsOdd[tcIdx] = (adjacentCounts[tcIdx] & 1) == 0;
                }
                
                // Determine remaining valid positions
                int start = prefix[prefixLength - 1] + 1;
                int remainingPositions = possibleClicks.size() - start;
                
                // If we only have 1 or 2 remaining positions, we can do extremely precise pruning
                if (remainingPositions <= 2) {
                    // Get all remaining positions
                    int[] remainingCells = new int[remainingPositions];
                    for (int i = 0; i < remainingPositions; i++) {
                        remainingCells[i] = possibleClicks.getInt(start + i);
                    }
                    
                    // Check if the remaining positions can satisfy all true cells
                    for (int tcIdx = 0; tcIdx < trueCells.length; tcIdx++) {
                        int trueCell = trueCells[tcIdx];
                        
                        // Check each remaining position
                        int possibleAdjacents = 0;
                        for (int cell : remainingCells) {
                            if (Grid.areAdjacent(trueCell, cell)) {
                                possibleAdjacents++;
                            }
                        }
                        
                        // If we need odd but have even possibles (or vice versa), this prefix is invalid
                        boolean hasValidCompletion = (possibleAdjacents % 2 == 1) == needsOdd[tcIdx];
                        if (!hasValidCompletion) {
                            return; // No valid completion possible, prune this branch
                        }
                    }
                }
            }
            
            // Standard pruning for all other prefix levels
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
        
        // Use pooled ArrayList instead of new ArrayList<>()
        List<CombinationGeneratorTask> subtasks = getSubtaskList();
        
        try {
            int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
            int max = possibleClicks.size() - (numClicks - prefixLength) + 1;

            if (prefixLength == 0) max = Math.min(max, maxFirstClickIndex + 1);
            
            for (int i = start; i < max; i++) 
            {
                // Check for solution found at regular intervals
                if (i % 100 == 0 && isTaskCancelled()) {
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
            
            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
                
                // FIX: ONLY recycle prefix arrays - these are safe because they are only used for task creation
                // and are not shared with consumers
                for (CombinationGeneratorTask task : subtasks) {
                    recycleIntArray(task.prefix); // Safe to recycle prefixes after subtasks complete
                }
            }
        } finally {
            recycleSubtaskList(subtasks);
        }
    } 
    else 
    {
        // Direct generation without intermediate arrays for leaf level
        int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        int max = possibleClicks.size();
        
        // Reuse a single combination array - This is our template, not shared with consumers
        int[] combination = getIntArray(numClicks);
        
        // Copy prefix once
        for (int j = 0; j < prefixLength; j++) {
            combination[j] = possibleClicks.getInt(prefix[j]);
        }
        
        // Get a fresh batch for this task's combinations
        ArrayDeque<int[]> batch = getBatchDeque();
        
        try {
            for (int i = start; i < max; i++) {
                // FIXED: Don't recycle on cancellation - simply return and let finally block handle cleanup
                if (i % 100 == 0 && isTaskCancelled()) {
                    return; // Just return, handle cleanup in finally block
                }
                
                combination[prefixLength] = possibleClicks.getInt(i);
                
                if (trueCells != null && trueCells.length > 0 &&
                    !quickOddAdjacency(combination, trueCells[0])) {
                    continue;
                }
                
                // Create new arrays for consumers - this correctly establishes ownership transfer
                int[] clone = new int[numClicks]; // Always create new arrays for combinations sent to consumers
                System.arraycopy(combination, 0, clone, 0, numClicks);
                batch.addLast(clone);
                
                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(batch);
                    // Create a new batch deque instead of clearing
                    batch = getBatchDeque();
                    if (isTaskCancelled()) {
                        return; // Just return, handle cleanup in finally block
                    }
                }
            }
            
            // Only flush if we actually added combinations
            if (!batch.isEmpty()) {
                flushBatch(batch);
            } else {
                recycleBatchDeque(batch);
            }
        } finally {
            // SAFE: Always recycle the template array at the end - it's not shared with consumers
            recycleIntArray(combination);
            
            // If batch is now empty (after a possible flush), recycle it
            if (batch != null && batch.isEmpty()) {
                recycleBatchDeque(batch);
            }
        }
    }
}

    // Extract shared flushing logic - UPDATED FOR ArrayDeque
    private static void flushBatchHelper(ArrayDeque<int[]> batch, CombinationQueueArray queueArray, boolean checkCancellation) 
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
                
                // Use the batch fill operation with ArrayDeque
                int added = queue.fillFromBatch(batch);
                
                if (added > 0) 
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

    // Simplified flushBatch method - UPDATED FOR ArrayDeque
    private void flushBatch(ArrayDeque<int[]> batch) 
    {
        if (batch.isEmpty()) return;
        
        flushBatchHelper(batch, queueArray, true);
        
        // Recycle the batch deque if it's now empty
        if (batch.isEmpty()) {
            recycleBatchDeque(batch);
        }
    }
    
    // Method to flush all pending batches from worker threads - UPDATED FOR ArrayDeque
    public static void flushAllPendingBatches(CombinationQueueArray queueArray, ForkJoinPool pool) 
    {
        // Submit a small task to each worker thread to flush its batch
        pool.submit(() -> {
            ArrayDeque<int[]> batch = THREAD_BATCH.get();
            if (batch != null && !batch.isEmpty()) 
            {
                flushBatchHelper(batch, queueArray, false);
                // Create a fresh batch for this thread after flushing
                ArrayDeque<int[]> newBatch = getEmptyBatchDeque();
                THREAD_BATCH.set(newBatch);
            }
            return null;
        }).join(); // Wait for completion
    }
    
    // Get a fresh batch deque - create a NEW one EVERY time
    private ArrayDeque<int[]> getBatchDeque() 
    {
        ArrayDeque<int[]> currentBatch = THREAD_BATCH.get();
        
        // If the batch has items, flush them and create a new batch
        if (!currentBatch.isEmpty()) 
        {
            flushBatch(currentBatch);
        }
        
        // Always create a fresh batch - no recycling, no shared state
        ArrayDeque<int[]> newBatch = new ArrayDeque<>(BATCH_SIZE);
        THREAD_BATCH.set(newBatch);
        return newBatch;
    }
    
    // Get a fresh empty batch deque from pool or create new
    private static ArrayDeque<int[]> getEmptyBatchDeque() {
        ArrayDeque<ArrayDeque<int[]>> pool = BATCH_DEQUE_POOL.get();
        ArrayDeque<int[]> deque = pool.pollFirst();
        if (deque == null) {
            return new ArrayDeque<>(BATCH_SIZE);
        }
        return deque;
    }
    
    // Return an empty batch deque to the pool
    private static void recycleBatchDeque(ArrayDeque<int[]> deque) 
    {
        if (deque == null) return;
        if (!deque.isEmpty()) return; // Don't recycle non-empty deques
        
        ArrayDeque<ArrayDeque<int[]>> pool = BATCH_DEQUE_POOL.get();
        if (pool.size() < 16) 
        {
            pool.offerFirst(deque);
        }
    }
    
    // Thread-local array pooling methods - DISABLED FOR STABILITY
    private int[] getIntArray(int size) {
        return new int[size]; // Always create new arrays
    }

    private void recycleIntArray(int[] arr) {
        // Do nothing - no more recycling
    }

    // ArrayList pool management methods
    private List<CombinationGeneratorTask> getSubtaskList() {
        ArrayDeque<List<CombinationGeneratorTask>> pool = SUBTASK_LIST_POOL.get();
        List<CombinationGeneratorTask> list = pool.pollFirst();
        if (list == null) {
            return new ArrayList<>(64); // Pre-sized for typical subtask count
        }
        list.clear(); // Ensure it's empty for reuse
        return list;
    }

    private void recycleSubtaskList(List<CombinationGeneratorTask> list) {
        if (list == null) return;
        list.clear(); // Clear contents before recycling
        ArrayDeque<List<CombinationGeneratorTask>> pool = SUBTASK_LIST_POOL.get();
        if (pool.size() < 16) { // Limit pool size
            pool.offerFirst(list);
        }
    }

    // Add these fields to cache adjacents for the current firstTrueCell
    private static volatile BitSet FIRST_TRUE_ADJACENTS_BITSET = null;
    private static volatile int CACHED_FIRST_TRUE_CELL = -1;
    private static final Object BITSET_LOCK = new Object();

    private static boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        // Thread-safe lazy initialization
        if (CACHED_FIRST_TRUE_CELL != firstTrueCell) {
            synchronized(BITSET_LOCK) {
                if (CACHED_FIRST_TRUE_CELL != firstTrueCell) {
                    int[] adjacents = Grid.findAdjacents(firstTrueCell);
                    BitSet newBitSet = new BitSet(700);
                    for (int adj : adjacents) {
                        newBitSet.set(adj);
                    }
                    FIRST_TRUE_ADJACENTS_BITSET = newBitSet;
                    CACHED_FIRST_TRUE_CELL = firstTrueCell;
                }
            }
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
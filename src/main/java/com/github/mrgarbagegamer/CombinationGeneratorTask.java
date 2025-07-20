package com.github.mrgarbagegamer;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

// TODO: Reformat all lines to place curly brackets on different lines than the method signature (for consistency with the rest of the codebase)
public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 8000; // The maximum size of a batch before it is flushed to the queue. Batches will try to be flushed when they reach the FLUSH_THRESHOLD, but will always be flushed when they reach this size.
    private static final int FLUSH_THRESHOLD = (int) (BATCH_SIZE * 0.5); // The threshold at which we flush the batch to the queue. Rather than setting this to a fixed value, we set it to a proportion of the batch size to allow for more efficient flushing without excessive overhead.
    private static final int POOL_SIZE = 2048; // Reduced since we're using more efficient pools
    
    // Keep existing thread-local pools
    private static final ThreadLocal<ArrayPool> prefixArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayPool(POOL_SIZE / 4, 32));
    
    private static final ThreadLocal<TaskPool> taskPool =
        ThreadLocal.withInitial(() -> new TaskPool(128));

    // Thread-local variable to hold the current batch being processed. This batch should be released whenever we flush it to the queue and replaced by a new batch.
    private static final ThreadLocal<WorkBatch> batchHolder = 
        ThreadLocal.withInitial(() -> new WorkBatch(BATCH_SIZE));

    // Instance fields remain the same
    private int numClicks;
    private int[] prefix;
    private int prefixLength;
    private CombinationQueueArray queueArray;
    private int numConsumers;
    private int[] trueCells;
    private int maxFirstClickIndex;
    
    // Added field to track cancellation within a subtask hierarchy
    private volatile boolean cancelled = false; // Use a volatile boolean instead of an AtomicBoolean to achieve the same goal without temporary object creation (and with greater efficiency)
    private CombinationGeneratorTask parent;

    // Constructors remain the same
    public CombinationGeneratorTask(int numClicks, int[] prefix, int prefixLength,
                                   CombinationQueueArray queueArray, int numConsumers, int[] trueCells,
                                   int maxFirstClickIndex) 
    {
        this.init(numClicks, prefix, prefixLength, queueArray, numConsumers, trueCells, maxFirstClickIndex, null);
    }

    public CombinationGeneratorTask() {}
    
    public void init(int numClicks, int[] prefix, int prefixLength,
                     CombinationQueueArray queueArray, int numConsumers, int[] trueCells,
                     int maxFirstClickIndex, CombinationGeneratorTask parent) 
    {
        this.numClicks = numClicks;
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.queueArray = queueArray;
        this.numConsumers = numConsumers;
        this.trueCells = trueCells;
        this.parent = parent;
        this.maxFirstClickIndex = maxFirstClickIndex;
        reinitialize();
        propagateParentCancellation();
    }

    // Ultra-small cancellation check - made final for guaranteed inlining
    private final boolean isTaskCancelled() 
    {
        return queueArray.solutionFound && (cancelled = true);
    }

    /**
     * Propagate cancellation based on the parent task's state.
     * This method is called at the start of compute() to ensure that if the parent task is cancelled, we stop right then and there.
     */
    private void propagateParentCancellation()
    {
        if (parent != null && parent.cancelled) this.cancelled = true;
    }

    /**
     * Main compute method - split into smaller methods to enable JIT inlining.
     * The original large method was marked as "too big" by the JIT compiler.
     */
    @Override
    protected void compute()
    {
        // Check for cancellation before starting work
        if (isTaskCancelled()) return;
        
        if (prefixLength < numClicks - 1) 
        {
            // Handle recursive subtask creation for intermediate levels
            computeSubtasks();
        } 
        else 
        {
            // Handle direct combination generation for leaf level
            computeLeafCombinations();
        }

        // We don't flush partial batches anymore; they will either be flushed when the batch is full or when the final task completes.
    }

    /**
     * Handles subtask creation and execution for non-leaf nodes.
     * Split from main compute() to enable JIT inlining of this hot path.
     */

    private void computeSubtasks()
    {
        // Before creating subtasks, check if this prefix path can possibly lead to a solution
        if (prefixLength >= 2 && !canPotentiallySatisfyConstraints()) 
        {
            return; // Early pruning - skip this entire branch
        }

        // Calculate subtask range
        int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        int max = Grid.NUM_CELLS - (numClicks - prefixLength) + 1;
        if (prefixLength == 0) max = Math.min(max, maxFirstClickIndex + 1);
        
        int numSubtasks = max - start;
        if (numSubtasks <= 0) return;
        
        // Create and execute subtasks
        createAndExecuteSubtasks(start, max, numSubtasks);
    }

    // OPTIMIZATION 1: Split the most critical path into micro-methods
    private void computeLeafCombinations()
    {
        int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        
        // Call the ultra-optimized hot loop, passing the prefix directly.
        generateCombinationsHotPath(start, prefix, batchHolder.get());
    }

    // OPTIMIZATION 2: Create an ultra-lightweight version of the hot loop
    private final void generateCombinationsHotPath(int start, int[] prefix, WorkBatch batch)
    {
        final int pLen = prefix.length;
        final boolean hasTrue = trueCells != null && trueCells.length > 0;
        final int firstTrue = hasTrue ? trueCells[0] : -1;

        // build mask once
        final long[] mask = hasTrue
            ? (ADJACENCY_MASK_CACHE_FAST[firstTrue & 15] != null
                && CACHED_TRUE_CELLS_FAST[firstTrue & 15] == firstTrue
                  ? ADJACENCY_MASK_CACHE_FAST[firstTrue & 15]
                  : computeAdjacencyMaskFast(firstTrue))
            : null;

        // compute prefix-only parity ONCE
        int prefixParity = 0;
        if (hasTrue)
        {
            // O(pLen) parity calculation BEFORE the loop rather than an O(pLen) check in each iteration
            for (int j = 0; j < pLen; j++)
            {
                int c = prefix[j];
                if ((mask[c >>> 6] & (1L << (c & 63))) != 0)
                {
                    prefixParity ^= 1;
                }
            }
        }

        for (int i = start; i < Grid.NUM_CELLS; i++)
        {
            if ((i & 255) == 0 && queueArray.solutionFound) return;

            // single bit‐test now
            if (hasTrue)
            {
                boolean iAdj = (mask[i >>> 6] & (1L << (i & 63))) != 0;
                // we need (prefixParity ^ iAdj) == 1  ⇔  iAdj != (prefixParity==1)
                if (iAdj == (prefixParity == 1)) // O(1) check
                {
                    continue; // Skip this iteration if the parity condition is not met
                }
            }

            if (!batch.add(prefix, i))
            {
                if (flushBatchFast(batch))
                {
                    batch = getNewBatch();
                    batchHolder.set(batch);
                    batch.add(prefix, i);
                }
            }
        }
    }

    // OPTIMIZATION 4: Streamlined batch flushing
    private final boolean flushBatchFast(WorkBatch batch) 
    {
        if (batch.isEmpty()) return false;
        
        CombinationQueue[] queues = queueArray.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);
        
        // Try each queue once
        for (int i = 0; i < queues.length; i++) 
        {
            if (queues[(startIdx + i) % queues.length].add(batch)) return true;
        }
        
        return false;
    }

    // OPTIMIZATION 5: Simplified mask caching with reduced contention
    private static final long[][] ADJACENCY_MASK_CACHE_FAST = new long[16][];
    private static final int[] CACHED_TRUE_CELLS_FAST = new int[16];
    
    private static long[] computeAdjacencyMaskFast(int firstTrueCell) 
    {
        int cacheIdx = firstTrueCell & 15;
        
        // Simple double-check without heavy synchronization
        if (CACHED_TRUE_CELLS_FAST[cacheIdx] == firstTrueCell) 
        {
            return ADJACENCY_MASK_CACHE_FAST[cacheIdx];
        }
        
        synchronized (CombinationGeneratorTask.class) 
        {
            if (CACHED_TRUE_CELLS_FAST[cacheIdx] != firstTrueCell) 
            {
                int[] adjacents = Grid.findAdjacents(firstTrueCell);
                long[] mask = new long[2];
                
                for (int adj : adjacents) 
                {
                    mask[adj >>> 6] |= (1L << (adj & 63));
                }
                
                ADJACENCY_MASK_CACHE_FAST[cacheIdx] = mask;
                CACHED_TRUE_CELLS_FAST[cacheIdx] = firstTrueCell;
            }
        }
        
        return ADJACENCY_MASK_CACHE_FAST[cacheIdx];
    }

    // Keep existing constraint checking logic unchanged
    private static long[] TRUE_CELL_ADJACENCY_MASKS = null;
    private static long[] SUFFIX_OR_MASKS = null;
    private static final boolean[][] CLICK_ADJACENCY_MATRIX = initClickAdjacencyMatrix(); // Stored in index format

    private static boolean[][] initClickAdjacencyMatrix() 
    {
        boolean[][] matrix = new boolean[Grid.NUM_CELLS][Grid.NUM_CELLS];
        for (int i = 0; i < Grid.NUM_CELLS; i++) 
        {
            int[] adjacents = Grid.findAdjacents(i, Grid.ValueFormat.Index);
            if (adjacents != null) 
            {
                for (int adj : adjacents) 
                {
                    if (adj < Grid.NUM_CELLS) matrix[i][adj] = true;
                }
            }
        }
        return matrix;
    }

    // Lazy initialization of true cell masks when first needed
    private static void ensureTrueCellMasks(int[] trueCells) 
    {
        if ((TRUE_CELL_ADJACENCY_MASKS == null | SUFFIX_OR_MASKS == null) && trueCells != null) 
        {
            synchronized (CombinationGeneratorTask.class) 
            {
                if (TRUE_CELL_ADJACENCY_MASKS == null) 
                {
                    long[] masks = new long[Grid.NUM_CELLS]; // Create an array to store masks for each click cell
                    
                    for (int clickCell = 0; clickCell < Grid.NUM_CELLS; clickCell++) // For each cell in the grid
                    {
                        long mask = 0L; // Create a mask with all true cells set to 0
                        for (int i = 0; i < trueCells.length; i++) // For each true cell
                        {
                            if (CLICK_ADJACENCY_MATRIX[trueCells[i]][clickCell]) // If the true cell is adjacent to the click cell
                            {
                                mask |= (1L << i); // Add this true cell to the mask by OR-ing with the bit at position i
                            }
                        }
                        masks[clickCell] = mask; // Store the mask for this click cell in the long array.
                    }
                    
                    TRUE_CELL_ADJACENCY_MASKS = masks; // Assign the masks to the static field
                }
                if (SUFFIX_OR_MASKS == null) 
                {
                    // NEW: Pre-compute the suffix OR masks after the main masks are ready
                    long[] suffixMasks = new long[Grid.NUM_CELLS + 1]; // +1 for sentinel
                    for (int i = Grid.NUM_CELLS - 1; i >= 0; i--)
                    {
                        suffixMasks[i] = suffixMasks[i + 1] | TRUE_CELL_ADJACENCY_MASKS[i];
                    }
                    SUFFIX_OR_MASKS = suffixMasks;
                }
            }
        }
    }

    /**
     * Ultra-fast constraint checking using pre-computed bitmasks.
     * Uses incremental state tracking to avoid recomputing XORs.
     */
    private boolean canPotentiallySatisfyConstraints()
    {
        if (trueCells == null || trueCells.length == 0) return true;
        
        // Ensure masks are initialized
        ensureTrueCellMasks(trueCells);
        
        // Use cached adjacency state from parent if available
        long currentAdjacencies;
        if (parent != null && parent.cachedAdjacencyState != -1) 
        {
            // Incrementally update from parent's state
            currentAdjacencies = parent.cachedAdjacencyState ^ TRUE_CELL_ADJACENCY_MASKS[prefix[prefixLength - 1]];
        }
        else 
        {
            // Compute from scratch (only for root tasks)
            currentAdjacencies = 0L;
            for (int j = 0; j < prefixLength; j++) 
            {
                currentAdjacencies ^= TRUE_CELL_ADJACENCY_MASKS[prefix[j]];
            }
        }
        
        // Cache for child tasks
        this.cachedAdjacencyState = currentAdjacencies;
        
        // Check what we need to achieve: all bits should be 1 (odd adjacency for all true cells)
        long targetMask = (1L << trueCells.length) - 1;
        long needed = currentAdjacencies ^ targetMask; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good
        if (needed == 0L) return true;
        
        // Use pre-computed suffix masks
        int startIdx = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        long availableAdjacencies = SUFFIX_OR_MASKS[startIdx];
        
        // Check if available clicks can satisfy all needed adjacencies
        return (availableAdjacencies & needed) == needed; // If at least one click can satisfy each needed adjacency, return true
    }

    // Add field to cache adjacency state
    private long cachedAdjacencyState = -1;

    // Keep existing subtask creation logic unchanged but make key methods final
    private void createAndExecuteSubtasks(int start, int max, int numSubtasks)
    {
        // Split into two phases to reduce call depth
        CombinationGeneratorTask[] subtasks = createSubtaskArray(start, max, numSubtasks);
        executeSubtaskArray(subtasks, numSubtasks);
    }

    // Separate method to reduce inlining depth
    private CombinationGeneratorTask[] createSubtaskArray(int start, int max, int numSubtasks)
    {
        CombinationGeneratorTask[] subtasks = new CombinationGeneratorTask[numSubtasks];
        int subtaskCount = 0;
        TaskPool pool = taskPool.get();
        
        for (int i = start; i < max; i++) 
        {
            // Reduce check frequency here too
            if ((i & 511) == 0 && isTaskCancelled()) return Arrays.copyOf(subtasks, subtaskCount);
            
            // Get prefix array from pool
            int[] newPrefix = getIntArray(prefixLength + 1);
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // Get a recycled task from the pool and initialize it
            CombinationGeneratorTask subtask = pool.get();
            subtask.init(numClicks, newPrefix, prefixLength + 1, 
                         queueArray, numConsumers, trueCells, maxFirstClickIndex, this);
            subtasks[subtaskCount++] = subtask;
        }
        
        return subtasks;
    }

    /**
     * Executes the created subtasks using invokeAll and handles cleanup.
     * Final piece of the subtask execution pipeline.
     */
    private void executeSubtaskArray(CombinationGeneratorTask[] subtasks, int subtaskCount)
    {
        if (subtaskCount > 0) 
        {
            try 
            {
                invokeAll(subtasks);

                // Recycle prefix arrays and tasks after subtasks complete
                TaskPool pool = taskPool.get();
                for (CombinationGeneratorTask subtask : subtasks) 
                {
                    if (subtask == null) continue;
                    putIntArray(subtask.prefix);
                    pool.put(subtask);
                }
            } catch (CancellationException ce) 
            {
                // Task was cancelled
            }
        }
    }

    // REMOVED: This method is no longer needed.
    // private int[] getCombinationArray()
    // {
    //     // Get a recycled combination array or create a new one if necessary
    //     int[] combination = combinationBuilder.get();
    //     if (combination == null) 
    //     {
    //         combination = new int[numClicks];
    //         combinationBuilder.set(combination); // Store the new array for future use.
    //     }
    //     return combination;
    // }

    // NEW: Gets a recycled or new WorkBatch.
    private WorkBatch getNewBatch()
    {
        WorkBatch batch = queueArray.getWorkBatchPool().poll();
        if (batch != null) return batch;
        return new WorkBatch(BATCH_SIZE);
    }

    private int[] getIntArray(int size) 
    {
        if (size < numClicks)
        {
            ArrayPool pool = prefixArrayPool.get();
            int[] arr = pool.get(size);
            if (arr != null) return arr;
        } 
        return new int[size];
    }

    private void putIntArray(int[] arr) 
    {
        if (arr == null) return;
        
        if (arr.length < numClicks) 
        {
            ArrayPool pool = prefixArrayPool.get();
            pool.put(arr);
        }
    }

    // Keep existing static methods unchanged
    public static void flushAllPendingBatches(CombinationQueueArray queueArray, ForkJoinPool pool) 
    {
        pool.submit(() -> {
            WorkBatch batch = batchHolder.get();
            if (batch != null && !batch.isEmpty()) 
            {
                flushBatchHelper(batch, queueArray, false, !queueArray.solutionFound);
                batchHolder.remove();
            }
        }).join();
    }

    private static boolean flushBatchHelper(WorkBatch batch, CombinationQueueArray queueArray, boolean checkCancellation, boolean forceFlush) 
    {
        if (batch == null || batch.isEmpty()) return false;
        
        CombinationQueue[] queues = queueArray.getAllQueues();
        int numQueues = queues.length;
        int startQueue = ThreadLocalRandom.current().nextInt(numQueues);

        // Try to offer the entire batch to a queue.
        while (true) 
        {   
            for (int attempt = 0; attempt < numQueues; attempt++) 
            {
                int idx = (startQueue + attempt) % numQueues;
                CombinationQueue queue = queues[idx];
                
                if (queue.add(batch)) return true;
            }
            if (forceFlush)
            {
                try 
                { 
                    Thread.sleep(1); 
                } catch (InterruptedException e) 
                { 
                    Thread.currentThread().interrupt();
                    return false; // Exit if interrupted 
                }
            }
            else
            {
                // If not forcing flush, we can break after one attempt
                return false; // No queue accepted the batch
            }
            // Only check cancellation if requested (for task flushing, not final flush)
            if (checkCancellation && queueArray.solutionFound) return false;
        }
    }
}
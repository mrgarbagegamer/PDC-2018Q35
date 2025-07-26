package com.github.mrgarbagegamer;

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
    private static final ThreadLocal<ArrayPool> prefixArrayPool = ThreadLocal.withInitial(() -> new ArrayPool(POOL_SIZE / 4));
    
    private static final ThreadLocal<TaskPool> taskPool =
        ThreadLocal.withInitial(() -> new TaskPool(128));

    // Thread-local variable to hold the current batch being processed. This batch should be released whenever we flush it to the queue and replaced by a new batch.
    private static final ThreadLocal<WorkBatch> batchHolder = 
        ThreadLocal.withInitial(() -> new WorkBatch(BATCH_SIZE));

    // Static fields
    private static int numClicks;
    private static CombinationQueueArray queueArray;
    private static short[] trueCells;
    private static int maxFirstClickIndex;

    // Cached data between tasks
    private short[] prefix;
    private int prefixLength;
    private long cachedAdjacencyState = -1; // -1 means uncomputed

    private static volatile ForkJoinPool generatorPool;

    public static void setForkJoinPool(ForkJoinPool pool) 
    {
        CombinationGeneratorTask.generatorPool = pool;
    }

    public static ForkJoinPool getForkJoinPool() 
    {
        return generatorPool;
    }

    // Root task constructor
    public CombinationGeneratorTask(int numClicks, CombinationQueueArray queueArray, 
                                   short[] trueCells, int maxFirstClickIndex) 
    {
        // Initialize the instance fields
        this.prefix = new short[0];
        this.prefixLength = 0;
        this.cachedAdjacencyState = -1; // Root task starts with no cached state

        // Set static fields
        CombinationGeneratorTask.numClicks = numClicks;
        CombinationGeneratorTask.queueArray = queueArray;
        CombinationGeneratorTask.trueCells = trueCells;
        CombinationGeneratorTask.maxFirstClickIndex = maxFirstClickIndex;
        ArrayPool.setNumClicks(numClicks); // Set the number of clicks for the array pool
        WorkBatch.setNumClicks(numClicks); // Set the number of clicks for the work batch

        if (trueCells == null || trueCells.length == 0) throw new IllegalArgumentException("True cells must be initialized before generating combinations.");
    }

    public CombinationGeneratorTask() {}
    
    // Reinitialize method to reset instance fields
    public void init(short[] prefix, int prefixLength, long parentAdjacencyState) 
    {
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.cachedAdjacencyState = parentAdjacencyState;
        reinitialize();
    }

    @Override
    protected void compute()
    {
        try 
        {
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
        }
        finally
        {
            // Self-cleanup: recycle our own resources
            recycleOwnResources();
        }
    }

    private void computeSubtasks()
    {
        // Early pruning check (keep this for performance)
        if (prefixLength >= 2 && !canPotentiallySatisfyConstraints()) 
        {
            return; // Early pruning - skip this entire branch
        }

        // Calculate subtask range
        int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        int max = Grid.NUM_CELLS - (numClicks - prefixLength) + 1;
        if (prefixLength == 0) max = Math.min(max, maxFirstClickIndex + 1);
        
        int numSubtasks = max - start;
        if (numSubtasks <= 0) return; // TODO: Look at removing this check for performance (if possible)
        
        // Fork subtasks directly without array collection
        forkSubtasks(start, max);
    }

    private void forkSubtasks(int start, int max)
    {
        TaskPool pool = taskPool.get();

        for (short i = (short) start; i < max; i++) 
        {
            // Remove cancellation check - let pool shutdown handle interruption
            
            
            // Get prefix array from pool
            short[] newPrefix = getShortArray(prefixLength + 1);
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = (short) i;

            // Calculate adjacency state for child
            long childAdjacencyState = cachedAdjacencyState;
            ensureTrueCellMasks(trueCells);
            if (cachedAdjacencyState == -1) 
            {
                // Root task - compute from scratch
                childAdjacencyState = 0L;
                for (int j = 0; j <= prefixLength; j++) 
                {
                    childAdjacencyState ^= TRUE_CELL_ADJACENCY_MASKS[newPrefix[j]];
                }
            }
            else 
            {
                // Incremental update
                childAdjacencyState ^= TRUE_CELL_ADJACENCY_MASKS[i];
            }

            // Get a recycled task from the pool and initialize it
            CombinationGeneratorTask subtask = pool.get();
            subtask.init(newPrefix, prefixLength + 1, childAdjacencyState);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }

        if (cachedAdjacencyState == -1) // If this is the root task, we need to wait for quiescence so the main thread does not exit prematurely
        {
            helpQuiesce();
        }
    }

    private void computeLeafCombinations()
    {
        // TODO: Look at removing the prefixLength check for performance (since we check it in compute()
        int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        generateCombinationsHotPath(start, prefix, batchHolder.get());
    }

    private final void generateCombinationsHotPath(int start, short[] prefix, WorkBatch batch)
    {
        final int pLen = prefix.length;
        final int firstTrue = trueCells[0]; // Assume trueCells is not empty and contains at least one true cell

        // Simplified mask retrieval - eliminate complex conditional
        final int cacheIdx = firstTrue & 15;
        final long[] mask = (CACHED_TRUE_CELLS_FAST[cacheIdx] == firstTrue) 
                            ? ADJACENCY_MASK_CACHE_FAST[cacheIdx]
                            : computeAdjacencyMaskFast(firstTrue);

        // TODO: Consider calculating prefix parity either in a more efficient way or by passing down pre-computations

        // compute prefix-only parity ONCE
        int prefixParity = 0;
        // O(pLen) parity calculation BEFORE the loop rather than an O(pLen) check in each iteration
        for (int j = 0; j < pLen; j++)
        {
            int c = prefix[j];
            if ((mask[c >>> 6] & (1L << (c & 63))) != 0)
            {
                prefixParity ^= 1;
            }
        }

        for (int i = start; i < Grid.NUM_CELLS; i++)
        {
            // Remove periodic cancellation check - pool shutdown handles interruption

            boolean iAdj = (mask[i >>> 6] & (1L << (i & 63))) != 0;
            // we need (prefixParity ^ iAdj) == 1  ⇔  iAdj != (prefixParity==1)
            if (iAdj == (prefixParity == 1)) // O(1) check
            {
                continue; // Skip this iteration if the parity condition is not met
            }

            if (!batch.add(prefix, (short) i))
            {
                if (flushBatchFast(batch))
                {
                    batch = getNewBatch();
                    batchHolder.set(batch);
                    batch.add(prefix, (short) i);
                }
            }
        }
    }

    private final boolean flushBatchFast(WorkBatch batch) 
    {   
        CombinationQueue[] queues = queueArray.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);
        
        // Try each queue once
        for (int i = 0; i < queues.length; i++) 
        {
            if (queues[(startIdx + i) % queues.length].add(batch)) return true;
        }
        
        return false;
    }

    private void recycleOwnResources()
    {
        // Recycle our prefix array
        if (prefix != null) // TODO: Remove this check if we can guarantee prefix is never null (which it shouldn't be)
        {
            putShortArray(prefix);
            prefix = null;
        }
        
        // Recycle ourselves back to the pool
        TaskPool pool = taskPool.get();
        pool.put(this);
    }

    // Constraint checking and mask logic unchanged
    private static final long[][] ADJACENCY_MASK_CACHE_FAST = new long[16][];
    private static final short[] CACHED_TRUE_CELLS_FAST = new short[16];
    
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
                short[] adjacents = Grid.findAdjacents((short) firstTrueCell);
                long[] mask = new long[2];
                
                for (short adj : adjacents) 
                {
                    mask[adj >>> 6] |= (1L << (adj & 63));
                }
                
                ADJACENCY_MASK_CACHE_FAST[cacheIdx] = mask;
                CACHED_TRUE_CELLS_FAST[cacheIdx] = (short) firstTrueCell;
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
        for (short i = 0; i < Grid.NUM_CELLS; i++) 
        {
            short[] adjacents = Grid.findAdjacents(i, Grid.ValueFormat.Index);
            if (adjacents != null) 
            {
                for (short adj : adjacents) 
                {
                    if (adj < Grid.NUM_CELLS) matrix[i][adj] = true;
                }
            }
        }
        return matrix;
    }

    // Lazy initialization of true cell masks when first needed
    private static void ensureTrueCellMasks(short[] trueCells) 
    {
        if (TRUE_CELL_ADJACENCY_MASKS == null | SUFFIX_OR_MASKS == null) // Assume that trueCells is not null and has been initialized
        {
            synchronized (CombinationGeneratorTask.class) 
            {
                if (TRUE_CELL_ADJACENCY_MASKS == null) 
                {
                    long[] masks = new long[Grid.NUM_CELLS]; // Create an array to store masks for each click cell
                    
                    for (int clickCell = 0; clickCell < Grid.NUM_CELLS; clickCell++) // For each cell in the grid
                    {
                        long mask = 0L; // Create a mask with all true cells set to 0
                        for (short i = 0; i < trueCells.length; i++) // For each true cell
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
     * Assumes that trueCells are initialized and non-empty.
     */
    private boolean canPotentiallySatisfyConstraints()
    {   
        // Ensure masks are initialized
        ensureTrueCellMasks(trueCells);
        
        // Use cached adjacency state
        long currentAdjacencies = cachedAdjacencyState;
        if (currentAdjacencies == -1) 
        {
            // Compute from scratch (should only happen for root tasks)
            currentAdjacencies = 0L;
            for (int j = 0; j < prefixLength; j++) 
            {
                currentAdjacencies ^= TRUE_CELL_ADJACENCY_MASKS[prefix[j]];
            }
            this.cachedAdjacencyState = currentAdjacencies;
        }
        
        long targetMask = (1L << trueCells.length) - 1;
        long needed = currentAdjacencies ^ targetMask; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good
        if (needed == 0L) return true;
        
        // Use pre-computed suffix masks
        int startIdx = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        long availableAdjacencies = SUFFIX_OR_MASKS[startIdx];
        
        return (availableAdjacencies & needed) == needed;
    }

    private WorkBatch getNewBatch()
    {
        WorkBatch batch = queueArray.getWorkBatchPool().poll();
        if (batch != null) return batch;
        return new WorkBatch(BATCH_SIZE);
    }

    private short[] getShortArray(int size) 
    {
        if (size < numClicks) // TODO: Remove this check if we can guarantee that size is never larger than numClicks
        {
            ArrayPool pool = prefixArrayPool.get();
            short[] arr = pool.get(size);
            if (arr != null) return arr;
        } 
        return new short[size];
    }

    private void putShortArray(short[] arr) 
    {
        if (arr == null) return; // TODO: Remove this check if we can guarantee that arr is never null
        
        if (arr.length < numClicks) // TODO: Remove this check to allow for more efficient recycling
        {
            ArrayPool pool = prefixArrayPool.get();
            pool.put(arr);
        }
    }

    public static void flushAllPendingBatches(CombinationQueueArray queueArray, ForkJoinPool pool) 
    {
        if (queueArray.solutionFound || pool.isShutdown()) return;
        
        try 
        {
            pool.submit(() -> {
                WorkBatch batch = batchHolder.get();
                if (batch != null && !batch.isEmpty()) 
                {
                    flushBatchHelper(batch, queueArray, false, !queueArray.solutionFound);
                    batchHolder.remove();
                }
            }).join();
        } catch (Exception e) 
        {
            // Do nothing, just return.
        }
    }

    private static boolean flushBatchHelper(WorkBatch batch, CombinationQueueArray queueArray, boolean checkCancellation, boolean forceFlush) 
    {
        if (batch == null || batch.isEmpty()) return false; // TODO: Remove this check if we can guarantee batch is never null or empty
        
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
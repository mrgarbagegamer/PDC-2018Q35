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
    
    // REPLACE: Multiple ThreadLocals with single context
    private static final ThreadLocal<GeneratorContext> context = 
        ThreadLocal.withInitial(GeneratorContext::new);
    // Generator context consolidates all per-thread resources
    private static class GeneratorContext 
    {
        final ArrayPool prefixArrayPool = new ArrayPool(POOL_SIZE / 4);
        final TaskPool taskPool = new TaskPool(128);
        WorkBatch currentBatch = new WorkBatch(BATCH_SIZE);
        
        WorkBatch getOrCreateBatch() 
        {
            if (currentBatch == null) 
            {
                currentBatch = getNewBatch();
            }
            return currentBatch;
        }
        
        private WorkBatch getNewBatch() 
        {
            WorkBatch batch = queueArray.getWorkBatchPool().poll();
            if (batch != null) return batch;
            return new WorkBatch(BATCH_SIZE);
        }

        // Handle the flushing of the current batch
        WorkBatch resetBatch()
        {
            return currentBatch = getNewBatch();
        }
    }

    // Static fields
    private static int numClicks;
    private static CombinationQueueArray queueArray;
    private static short[] trueCells;
    private static int maxFirstClickIndex;

    // Cached data between tasks
    private short[] prefix;
    private int prefixLength;
    private long cachedAdjacencyState = -1; // -1 means uncomputed
    
    // OPTIMIZATION: Cache the first true cell adjacency mask per task
    private static long[] cachedFirstTrueMask = null;

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
        this.prefix = new short[0]; // TODO: Consider grabbing an array from the pool instead (since the ArrayPool will allocate a new array if the first element is of the wrong size (and this will always be the case))
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
        
        // OPTIMIZATION: Pre-compute and cache the first true cell mask once per puzzle
        final int firstTrue = trueCells[0];
        final int cacheIdx = firstTrue & 15;
        cachedFirstTrueMask = (CACHED_TRUE_CELLS_FAST[cacheIdx] == firstTrue)
                            ? ADJACENCY_MASK_CACHE_FAST[cacheIdx]
                            : computeAdjacencyMaskFast(firstTrue);
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
        // Single ThreadLocal access per task - pass context down to all methods
        final GeneratorContext ctx = context.get();
        
        try
        {
            if (prefixLength < numClicks - 1)
            {
                // Handle recursive subtask creation for intermediate levels
                computeSubtasks(ctx);
            }
            else
            {
                // Handle direct combination generation for leaf level
                computeLeafCombinations(ctx);
            }
        }
        finally
        {
            // Self-cleanup: recycle our own resources
            recycleOwnResources(ctx);
        }
    }

    private void computeSubtasks(GeneratorContext ctx)
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
        
        // Fork subtasks directly without array collection
        forkSubtasks(ctx, start, max);
    }

    private void forkSubtasks(GeneratorContext ctx, int start, int max)
    {
        // No ThreadLocal access needed - use passed context
        ensureTrueCellMasks(trueCells); // Ensure masks are initialized before forking subtasks

        for (short i = (short) start; i < max; i++) 
        {
            // Use context pools directly - no more ThreadLocal calls
            // Always get a standard, full-sized array to maximize pool hit rate.
            // This directly addresses the allocation hotspot identified in profiling.
            short[] newPrefix = ctx.prefixArrayPool.get(numClicks);
            if (newPrefix == null) newPrefix = new short[numClicks];
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = (short) i;

            // Calculate adjacency state for child
            long childAdjacencyState = cachedAdjacencyState;

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

            // Get recycled task from context pool
            CombinationGeneratorTask subtask = ctx.taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, childAdjacencyState);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }

        if (cachedAdjacencyState == -1) // If this is the root task, we need to wait for quiescence so the main thread does not exit prematurely
        {
            helpQuiesce();
        }
    }

    private final void computeLeafCombinations(GeneratorContext ctx) // Absorbed the logic from generateCombinationsHotPath into here
    {
        // ULTRA-OPTIMIZED: Pre-compute all loop-invariant values and cache array references
        final int start = prefix[prefixLength - 1] + 1;
        final int pLen = prefixLength;
        final long[] mask = cachedFirstTrueMask;
        final long mask0 = mask[0]; // Cache array elements to avoid repeated dereferences
        final long mask1 = mask[1];

        // Use context batch directly
        WorkBatch batch = ctx.getOrCreateBatch();

        // OPTIMIZED: Compute prefix parity with cached mask values
        int prefixParity = 0;
        for (int j = 0; j < pLen; j++)
        {
            final int c = prefix[j];
            final long maskValue = (c < 64) ? mask0 : mask1;
            final int bitPos = c & 63;
            if ((maskValue & (1L << bitPos)) != 0)
            {
                prefixParity ^= 1;
            }
        }

        // OPTIMIZED: Pre-compute parity condition to avoid repeated calculation
        final boolean needsOddParity = (prefixParity == 1);

        // ULTRA-OPTIMIZED: Tight loop with minimal branching and cached values
        for (int i = start; i < Grid.NUM_CELLS; i++)
        {
            // Use cached mask values instead of array access
            final long maskValue = (i < 64) ? mask0 : mask1;
            final int bitPos = i & 63;
            final boolean iAdj = (maskValue & (1L << bitPos)) != 0;
            
            if (iAdj == needsOddParity)
            {
                continue; // Skip if parity condition not met
            }

            if (!batch.add(prefix, pLen, (short) i))
            {
                if (flushBatchFast(batch)) // TODO: Make sure to handle the case where the batch is not flushed (idk how, but we have to do so to avoid dropped combinations)
                {
                    ctx.resetBatch();
                    batch = ctx.currentBatch;
                    batch.add(prefix, pLen, (short) i);
                }
            }
        }
    }

    private final boolean flushBatchFast(WorkBatch batch) 
    {   
        CombinationQueue[] queues = queueArray.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);
        
        // Try each queue once and sleep if all are full
        while (true)
        {
            for (int i = 0; i < queues.length; i++) 
            {
                if (queues[(startIdx + i) % queues.length].add(batch)) return true;
            }

            // If we reach here, all queues were full
            try 
            {
                Thread.sleep(0, 500_000); // Sleep briefly (0.5ms) to avoid busy-waiting
            } catch (InterruptedException e) 
            {
                Thread.currentThread().interrupt(); // Restore interrupt status
                return false; // Exit if interrupted
            }
        }
    }

    private void recycleOwnResources(GeneratorContext ctx)
    {
        // No ThreadLocal access needed - use passed context
        
        // Recycle prefix array to context pool
        ctx.prefixArrayPool.put(prefix);
        prefix = null;
        
        // Recycle task to context pool
        ctx.taskPool.put(this);
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

    // TODO: Rework this method so it actually flushes all pending batches, since we currently just handle the batch for one context
    public static void flushAllPendingBatches(CombinationQueueArray queueArray, ForkJoinPool pool) 
    {
        if (queueArray.solutionFound || pool.isShutdown()) return;
        
        try 
        {
            pool.submit(() -> {
                // Single ThreadLocal access per flush operation
                final GeneratorContext ctx = context.get();
                WorkBatch batch = ctx.currentBatch;
                if (batch != null && !batch.isEmpty()) 
                {
                    flushBatchHelper(batch, queueArray, false, !queueArray.solutionFound);
                    ctx.resetBatch(); // Reset rather than remove ThreadLocal
                }
            }).join();
        } catch (Exception e) 
        {
            // Do nothing, just return.
        }
    }

    private static boolean flushBatchHelper(WorkBatch batch, CombinationQueueArray queueArray, boolean checkCancellation, boolean forceFlush) 
    {
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
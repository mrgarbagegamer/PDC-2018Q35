package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

public class CombinationGeneratorTask extends RecursiveAction
{
    public static final int BATCH_SIZE = 8000; // The maximum size of a batch before it is flushed to the queue. Batches will try to be flushed when they reach the FLUSH_THRESHOLD, but will always be flushed when they reach this size.
    // private static final int FLUSH_THRESHOLD = (int) (BATCH_SIZE * 0.5); // The threshold at which we flush the batch to the queue. Rather than setting this to a fixed value, we set it to a proportion of the batch size to allow for more efficient flushing without excessive overhead. // TODO: Consider re-adding the FLUSH_THRESHOLD if needed
    private static final int POOL_SIZE = 2048; // Reduced since we're using more efficient pools

    // REPLACE: Multiple ThreadLocals with single context
    private static final ThreadLocal<GeneratorContext> context =
        ThreadLocal.withInitial(GeneratorContext::new);

    // Generator context consolidates all per-thread resources
    private static class GeneratorContext
    {
        final ArrayPool prefixArrayPool = new ArrayPool(POOL_SIZE / 4);
        final TaskPool taskPool = new TaskPool(128);
        WorkBatch currentBatch = null;

        WorkBatch getOrCreateBatch()
        {
            if (currentBatch == null)
            {
                currentBatch = getNewBatchBlocking();
            }
            return currentBatch;
        }

        // OPTIMIZATION: This is now a blocking call. It will wait until a batch is
        // available, creating backpressure on the producers instead of allocating.
        private WorkBatch getNewBatchBlocking()
        {
            WorkBatch batch;
            while ((batch = queueArray.getWorkBatchPool().relaxedPoll()) == null)
            {
                // NOTE: Thread.onSpinWait() can not be used here since it doesn't respond to cancellation.
            }
            batch.clear(); // Ensure the recycled batch is clean before use
            return batch;
        }

        // Handle the flushing of the current batch
        WorkBatch resetBatch()
        {
            return currentBatch = getNewBatchBlocking();
        }
    }

    // Static fields
    private static int numClicks;
    private static CombinationQueueArray queueArray;
    private static int maxFirstClickIndex;

    // Cached data between tasks
    private short[] prefix;
    private int prefixLength;
    private long cachedAdjacencyState = -1; // -1 means root task, -2 means constraints are skipped
    private boolean skipConstraintsCheck = false; // If a prefix is known to satisfy constraints, we can skip checks for its children

    private static long targetMask; // We can cache the target mask for the current task to avoid recomputing it in constraint checks
    
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
        // Check for valid inputs
        if (numClicks <= 0 || numClicks > Grid.NUM_CELLS)
        {
            throw new IllegalArgumentException("Invalid number of clicks: " + numClicks);
        }

        if (queueArray == null)
        {
            throw new IllegalArgumentException("Queue array must not be null.");
        }

        if (trueCells == null || trueCells.length == 0)
        {
            throw new IllegalArgumentException("True cells must be initialized before generating combinations.");
        }

        if (maxFirstClickIndex < 0 || maxFirstClickIndex >= Grid.NUM_CELLS)
        {
            throw new IllegalArgumentException("Invalid max first click index: " + maxFirstClickIndex);
        }

        // Set static fields
        CombinationGeneratorTask.numClicks = numClicks;
        CombinationGeneratorTask.queueArray = queueArray;
        CombinationGeneratorTask.maxFirstClickIndex = maxFirstClickIndex;
        ArrayPool.setNumClicks(numClicks); // Set the number of clicks for the array pool

        
        // Initialize the instance fields
        this.prefix = context.get().prefixArrayPool.get(); // FIX: Get the initial empty prefix from the thread-local context to avoid allocation.
        if (this.prefix == null)
        {
            this.prefix = new short[numClicks]; // Safeguard if pool is empty (though it shouldn't be)
        }
        this.prefixLength = 0;
        this.cachedAdjacencyState = -1; // Root task starts with no cached state

        // OPTIMIZATION: Pre-compute the target mask for the root task
        targetMask = (1L << trueCells.length) - 1; // All true

        // OPTIMIZATION: Pre-compute the true cell adjacency masks and suffix OR masks for constraint checks
        ensureTrueCellMasks(trueCells);

        // OPTIMIZATION: Pre-compute and cache the first true cell mask once per puzzle
        final int firstTrue = trueCells[0];
        final int cacheIdx = firstTrue & 15;
        cachedFirstTrueMask = (CACHED_TRUE_CELLS_FAST[cacheIdx] == firstTrue)
                            ? ADJACENCY_MASK_CACHE_FAST[cacheIdx]
                            : computeAdjacencyMaskFast(firstTrue);
    }

    // TODO: Check if you need a cache in the first place, since it seems like we only need the value for the first true cell
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

    public CombinationGeneratorTask() {}

    @Override
    protected void compute()
    {
        // Single ThreadLocal access per task - pass context down to all methods
        final GeneratorContext ctx = context.get();
        
        try
        {
            // Path for the root task
            if (prefixLength == 0) computeRootSubtasks(ctx);

            // Path for leaf tasks
            else if (prefixLength == numClicks - 1)
            {
                computeLeafCombinations(ctx);
            }

            else
            {
                // Unified path for all intermediate (non-leaf, non-root) tasks
                computeIntermediateSubtasks(ctx);
            }
        }
        finally
        {
            // Self-cleanup: recycle our own resources
            recycleOwnResources(ctx);
        }
    }

    // ROOT TASK PATH:
    // Compute and fork the subtasks for the root task, then await quiescence
    private void computeRootSubtasks(GeneratorContext ctx)
    {
        final short start = 0;
        final short max = (short) (Math.min(Grid.NUM_CELLS - numClicks, maxFirstClickIndex) + 1);

        for (short i = start; i < max; i++)
        {
            // Use context pools directly - no more ThreadLocal calls
            short[] newPrefix = ctx.prefixArrayPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // Get recycled task from context pool
            CombinationGeneratorTask subtask = ctx.taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, 0L, false);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
        
        helpQuiesce(); // Wait for all subtasks to complete before returning
        // This will ensure that the root task does not exit prematurely, keeping the main thread parked
    }
    // MONOMORPHIC: Single init method to avoid polymorphic call sites
    public void init(short[] prefix, int prefixLength, long parentAdjacencyState, boolean skipConstraints)
    {
        this.prefix = prefix;
        this.prefixLength = prefixLength;
        this.cachedAdjacencyState = parentAdjacencyState;
        this.skipConstraintsCheck = skipConstraints;
        reinitialize();
    }

    // LEAF TASK PATH:
    // Compute combinations for the leaf task
    private final void computeLeafCombinations(GeneratorContext ctx)
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
        // TODO: Consider tracking prefix parity per task and passing down the values to avoid recomputing and to condense this method
        boolean prefixParity = false; // Track parity of the prefix
        for (int j = 0; j < pLen; j++)
        {
            final int c = prefix[j];
            final long maskValue = (c < 64) ? mask0 : mask1;
            final int bitPos = c & 63;
            if ((maskValue & (1L << bitPos)) != 0)
            {
                prefixParity ^= true; // Toggle parity (XOR with true is the same as toggling)
            }
        }

        // TODO: Consider replacing the parity check with the constraints check again and/or skipping if the prefix is known to satisfy constraints

        // ULTRA-OPTIMIZED: Tight loop with minimal branching and cached values
        for (int i = start; i < Grid.NUM_CELLS; i++)
        {
            // Use cached mask values instead of array access
            final long maskValue = (i < 64) ? mask0 : mask1;
            final int bitPos = i & 63;
            final boolean iAdj = (maskValue & (1L << bitPos)) != 0;
            
            if (iAdj == prefixParity)
            {
                continue; // Skip if parity condition not met
            }

            if (!batch.add(prefix, pLen, (short) i))
            {
                if (flushBatchFast(batch))
                {
                    ctx.resetBatch();
                    batch = ctx.currentBatch;
                    batch.add(prefix, pLen, (short) i);
                }
            }
        }
    }
    
    // PURE HOT PATH SEPARATION: Dispatch once per task, not per loop iteration
    private void computeIntermediateSubtasks(GeneratorContext ctx)
    {
        if (skipConstraintsCheck) {
            computeIntermediateSubtasksSkipPath(ctx);
        } else {
            computeIntermediateSubtasksConstraintPath(ctx);
        }
    }
    
    // PURE HOT PATH 1: Zero branches, zero conditionals - for descendants of constraint-satisfied tasks
    private void computeIntermediateSubtasksSkipPath(GeneratorContext ctx)
    {
        final short start = (short) (prefix[prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (numClicks - prefixLength) + 1);
        final ArrayPool prefixPool = ctx.prefixArrayPool;
        final TaskPool taskPool = ctx.taskPool;
        
        // Pure loop - no constraint checking, no mask loading, no conditionals
        for (short i = start; i < max; i++)
        {
            short[] newPrefix = prefixPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // All parameters are constants - perfect for JIT optimization
            CombinationGeneratorTask subtask = taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, -1L, true);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
    }
    
    // PURE HOT PATH 2: Zero branches in loop, pure constraint logic
    private void computeIntermediateSubtasksConstraintPath(GeneratorContext ctx)
    {
        final short start = (short) (prefix[prefixLength - 1] + 1);
        final short max = (short) (Grid.NUM_CELLS - (numClicks - prefixLength) + 1);
        
        // Early constraint check - happens ONCE per task, not per iteration
        if (prefixLength >= 2 && !canPotentiallySatisfyConstraints(start))
        {
            return; // Skip this entire branch if constraints cannot be satisfied
        }
        
        // Pre-compute all loop-invariant values and cache array references
        final long currentAdjacencyState = this.cachedAdjacencyState;
        final ArrayPool prefixPool = ctx.prefixArrayPool;
        final TaskPool taskPool = ctx.taskPool;
        final long[] masks = TRUE_CELL_ADJACENCY_MASKS;
        final boolean skipConstraints = this.skipConstraintsCheck; // Cache field read
        
        // Pure loop - no conditionals inside, all branching resolved outside loop
        for (short i = start; i < max; i++)
        {
            short[] newPrefix = prefixPool.get();
            if (newPrefix == null) newPrefix = new short[numClicks]; // Safeguard if pool is empty
            
            System.arraycopy(prefix, 0, newPrefix, 0, prefixLength);
            newPrefix[prefixLength] = i;

            // No conditional - pure XOR calculation every time
            long childAdjacencyState = currentAdjacencyState ^ masks[i];
            
            // All parameters determined - perfect for JIT constant propagation
            CombinationGeneratorTask subtask = taskPool.get();
            subtask.init(newPrefix, prefixLength + 1, childAdjacencyState, skipConstraints);
            
            // Fork the subtask - it will clean itself up
            subtask.fork();
        }
    }
    /**
     * Ultra-fast constraint checking using pre-computed bitmasks.
     * Uses incremental state tracking to avoid recomputing XORs.
     * Assumes that trueCells are initialized and non-empty.
     */
    private boolean canPotentiallySatisfyConstraints(int startIdx)
    {   
        // OPTIMIZATION: Call ensureTrueCellMasks once in the root task to avoid a call here.

        // cachedAdjacencyState can only be -1 for the root task (which skips this check)
        // Therefore, we can assume it is initialized here, saving a branch in our logic.
        
        // Define the variables we'll use in this check.
        final long target = targetMask;
        final long currentAdjacencies = cachedAdjacencyState;
        final long needed = currentAdjacencies ^ target; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good.
        // OPTIMIZATION: Skip future checks by setting skipConstraintsCheck to true
        if (needed == 0L) 
        {
            skipConstraintsCheck = true;  // Set flag separately to avoid side effects
            return true;
        }
        
        // Else, check if any of the available adjacencies can satisfy the needed bits
        // Use the pre-computed suffix OR masks for fast checking
        long availableAdjacencies = SUFFIX_OR_MASKS[startIdx];
        
        return (availableAdjacencies & needed) == needed;
    }

    // Keep existing constraint checking logic unchanged
    private static long[] TRUE_CELL_ADJACENCY_MASKS = null;
    private static long[] SUFFIX_OR_MASKS = null;
    private static final boolean[][] CLICK_ADJACENCY_MATRIX = initClickAdjacencyMatrix(); // Stored in index format

    // This method is used to initialize the click adjacency matrix, and is static
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
    // This method is called once in the root task's constructor
    private static void ensureTrueCellMasks(short[] trueCells) 
    {
        if (TRUE_CELL_ADJACENCY_MASKS == null | SUFFIX_OR_MASKS == null)
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
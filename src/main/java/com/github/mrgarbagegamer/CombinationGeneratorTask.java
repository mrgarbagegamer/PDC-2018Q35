package com.github.mrgarbagegamer;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

import it.unimi.dsi.fastutil.ints.IntList;

// TODO: Reformat all lines to place curly brackets on different lines than the method signature (for consistency with the rest of the codebase)
public class CombinationGeneratorTask extends RecursiveAction 
{
    private static final int BATCH_SIZE = 8000; // The maximum size of a batch before it is flushed to the queue. Batches will try to be flushed when they reach the FLUSH_THRESHOLD, but will always be flushed when they reach this size.
    private static final int FLUSH_THRESHOLD = (int) (BATCH_SIZE * 0.5); // The threshold at which we flush the batch to the queue. Rather than setting this to a fixed value, we set it to a proportion of the batch size to allow for more efficient flushing without excessive overhead.
    private static final int POOL_SIZE = 2048; // Reduced since we're using more efficient pools
    
    // Replace ArrayDeque pools with custom high-performance pools
    private static final ThreadLocal<ArrayPool> prefixArrayPool = 
        ThreadLocal.withInitial(() -> new ArrayPool(POOL_SIZE / 4, 32));
    
    // Pool for CombinationGeneratorTask objects to reduce allocation churn.
    private static final ThreadLocal<TaskPool> taskPool =
        ThreadLocal.withInitial(() -> new TaskPool(128)); // Start with a reasonable pool size

    // Thread-local variable to hold the current batch being processed. This batch should be released whenever we flush it to the queue and replaced by a new batch.
    private static final ThreadLocal<WorkBatch> batchHolder = 
        ThreadLocal.withInitial(() -> new WorkBatch(BATCH_SIZE));

    // Thread-local array for final combination building. We avoid initializing it until needed so the array can be perfectly sized for numClicks.
    private static final ThreadLocal<int[]> combinationBuilder =
        new ThreadLocal<int[]>();


    private IntList possibleClicks;
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

    // Public constructor for the root task
    public CombinationGeneratorTask(IntList possibleClicks, int numClicks, int[] prefix, int prefixLength,
                                   CombinationQueueArray queueArray, int numConsumers, int[] trueCells,
                                   int maxFirstClickIndex) 
    {
        this.init(possibleClicks, numClicks, prefix, prefixLength, queueArray, numConsumers, trueCells, maxFirstClickIndex, null);
    }

    // Default constructor for pool creation
    public CombinationGeneratorTask() {}
    
    // "init" method to re-initialize a recycled task
    public void init(IntList possibleClicks, int numClicks, int[] prefix, int prefixLength,
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
        reinitialize();
        propagateParentCancellation();
    }

    // Cache-friendly cancellation checking
    private boolean isTaskCancelled() 
    {
        // Only check solution found in hot paths
        if (queueArray.solutionFound) return cancelled = true; // Set cancelled if the solution is found.
        return false;
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
        int max = possibleClicks.size() - (numClicks - prefixLength) + 1;
        if (prefixLength == 0) max = Math.min(max, maxFirstClickIndex + 1);
        
        int numSubtasks = max - start;
        if (numSubtasks <= 0) return;
        
        // Create and execute subtasks
        createAndExecuteSubtasks(start, max, numSubtasks);
    }

    // Add these static fields for pre-computed adjacency data
    private static long[] TRUE_CELL_ADJACENCY_MASKS = null;
    // NEW: Add a field for the pre-computed suffix OR masks
    private static long[] SUFFIX_OR_MASKS = null;
    private static final boolean[][] CLICK_ADJACENCY_MATRIX = initClickAdjacencyMatrix();
    private static final int GRID_SIZE = 700; // Adjust for your grid

    private static boolean[][] initClickAdjacencyMatrix() 
    {
        boolean[][] matrix = new boolean[GRID_SIZE][GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) 
        {
            int[] adjacents = Grid.findAdjacents(i);
            if (adjacents != null) 
            {
                for (int adj : adjacents) 
                {
                    if (adj < GRID_SIZE) matrix[i][adj] = true;
                }
            }
        }
        return matrix;
    }

    // Lazy initialization of true cell masks when first needed
    private static void ensureTrueCellMasks(IntList possibleClicks, int[] trueCells) 
    {
        if (TRUE_CELL_ADJACENCY_MASKS == null && trueCells != null) 
        {
            synchronized (CombinationGeneratorTask.class) 
            {
                if (TRUE_CELL_ADJACENCY_MASKS == null) 
                {
                    long[] masks = new long[GRID_SIZE]; // Create an array to store masks for each click cell
                    
                    for (int clickCell = 0; clickCell < GRID_SIZE; clickCell++) // For each cell in the grid
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

                    // NEW: Pre-compute the suffix OR masks after the main masks are ready
                    int numPossibleClicks = possibleClicks.size();
                    long[] suffixMasks = new long[numPossibleClicks + 1]; // +1 for sentinel
                    for (int i = numPossibleClicks - 1; i >= 0; i--)
                    {
                        int clickIndex = possibleClicks.getInt(i);
                        if (clickIndex < GRID_SIZE)
                        {
                            suffixMasks[i] = suffixMasks[i + 1] | TRUE_CELL_ADJACENCY_MASKS[clickIndex];
                        }
                        else
                        {
                            suffixMasks[i] = suffixMasks[i + 1];
                        }
                    }
                    SUFFIX_OR_MASKS = suffixMasks;
                }
            }
        }
    }

    /**
     * Ultra-fast constraint checking using pre-computed bitmasks.
     * This should be small enough for C2 inlining.
     */
    private boolean canPotentiallySatisfyConstraints()
    {
        if (trueCells == null || trueCells.length == 0) return true;
        
        // Ensure masks are initialized
        ensureTrueCellMasks(possibleClicks, trueCells);
        
        // Compute current adjacency state using bitmasks
        long currentAdjacencies = 0L; // Create a mask with all true cells set to 0
        
        for (int j = 0; j < prefixLength; j++) // For each click in the prefix
        {
            int clickIndex = possibleClicks.getInt(prefix[j]); // Get the packed int corresponding to the click
            if (clickIndex < GRID_SIZE) 
            {
                currentAdjacencies ^= TRUE_CELL_ADJACENCY_MASKS[clickIndex]; // Toggle the affected true cells by XOR-ing the mask generated on initialization
            }
        }
        
        // Check what we need to achieve: all bits should be 1 (odd adjacency for all true cells)
        long targetMask = (1L << trueCells.length) - 1;
        long needed = currentAdjacencies ^ targetMask; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good
        if (needed == 0L) return true;
        
        // Check if remaining clicks can provide the needed adjacencies
        int startIdx = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        
        // OPTIMIZED: Replace the expensive loop with a single array lookup
        long availableAdjacencies = SUFFIX_OR_MASKS[startIdx];
        
        // Check if available clicks can satisfy all needed adjacencies
        return (availableAdjacencies & needed) == needed; // If at least one click can satisfy each needed adjacency, return true
    }

    /**
     * Creates subtasks and manages their execution.
     * Separated from computeSubtasks() to keep methods small and inlinable.
     */
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
            subtask.init(possibleClicks, numClicks, newPrefix, prefixLength + 1, 
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
                // Task was cancelled, just return
            } 
            finally 
            {
                // Removed subtask array pooling - now directly allocated
            }
        } 
        // Removed else branch - no longer needed
    }

    /**
     * Handles direct combination generation for leaf-level tasks.
     * Split from main compute() to enable better JIT optimization of this hot path.
     */
    private void computeLeafCombinations()
    {
        // Direct generation without intermediate arrays for leaf level
        int start = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        int max = possibleClicks.size();

        int[] combination = getCombinationArray();
        
        // Copy prefix once
        for (int j = 0; j < prefixLength; j++) 
        {
            combination[j] = possibleClicks.getInt(prefix[j]);
        }
        
        // Get the thread batch for this task.
        WorkBatch batch = batchHolder.get();

        generateCombinations(start, max, combination, batch);
    }

    /**
     * Core combination generation loop extracted for better JIT optimization.
     * This is one of the hottest paths in the entire application.
     */
    private void generateCombinations(int start, int max, int[] combination, WorkBatch batch)
    {
        for (int i = start; i < max; i++) 
        {
            // Check for cancellation at regular intervals
            if ((i & 127) == 0 && isTaskCancelled()) 
            {
                return;
            }
            
            combination[prefixLength] = possibleClicks.getInt(i);
            
            // Apply pruning filter - skip combinations that can't satisfy odd adjacency
            if (trueCells != null && trueCells.length > 0 &&
                !quickOddAdjacency(combination, trueCells[0])) 
            {
                continue;
            }
            
            // The batch is the recycled unit, so we copy the combination into the batch's internal array.
            // This avoids creating new arrays for each combination.
            batch.add(combination);
            
            // Flush the batch if it reaches the threshold
            if (batch.size() >= FLUSH_THRESHOLD) 
            {
                if (flushBatch(batch)) 
                {
                    // If the flush succeeds, we need to get a new batch.
                    batch = getNewBatch();
                    batchHolder.set(batch); // Update the thread-local batch holder
                }
            }
        }
    }

    private int[] getCombinationArray()
    {
        // Get a recycled combination array or create a new one if necessary
        int[] combination = combinationBuilder.get();
        if (combination == null) 
        {
            combination = new int[numClicks];
            combinationBuilder.set(combination); // Store the new array for future use.
        }
        return combination;
    }

    // NEW: Gets a recycled or new WorkBatch.
    private WorkBatch getNewBatch()
    {
        WorkBatch batch = queueArray.getWorkBatchPool().poll();
        if (batch != null) return batch;
        return new WorkBatch(BATCH_SIZE);
    }

    private static boolean flushBatchHelper(WorkBatch batch, CombinationQueueArray queueArray, boolean checkCancellation, boolean forceFlush) 
    {
        if (batch == null || batch.isEmpty())
        {
            return false;
        }
        
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

    private boolean flushBatch(WorkBatch batch) 
    {
        if (batch == null || batch.isEmpty()) 
        {
            return false; // Nothing to flush
        }
        
        // Use the helper method to flush the batch to the queues
        return flushBatchHelper(batch, queueArray, true, batch.isFull() ? true : false);
    }
    
    /**
     * Flush all pending generator thread batches to the queues.
     * This method is called when generation is complete or when a solution is found.
     * It ensures that all pending batches are processed and added to the queues.
     * @param queueArray - the CombinationQueueArray to which batches will be flushed.
     * @param pool - the ForkJoinPool used for processing batches.
     */
    public static void flushAllPendingBatches(CombinationQueueArray queueArray, ForkJoinPool pool) 
    {
        pool.submit(() -> {
            WorkBatch batch = batchHolder.get();
            if (batch != null && !batch.isEmpty()) 
            {
                flushBatchHelper(batch, queueArray, false, !queueArray.solutionFound); // Only force flush the batch if a solution hasn't been found yet

                // Clear the thread-local batch holder (this isn't strictly necessary, but doing it helps avoid a potential memory leak or race condition)
                batchHolder.remove();
            }
        }).join(); // Wait for completion to ensure all batches are flushed
    }

    private int[] getIntArray(int size) 
    {
        // Only pool prefix arrays. Combination arrays are cloned.
        if (size < numClicks)
        {
            ArrayPool pool = prefixArrayPool.get();
            int[] arr = pool.get(size);
            if (arr != null)
            {
                return arr;
            }
        } 
        return new int[size];
    }

    private void putIntArray(int[] arr) 
    {
        if (arr == null) return;
        
        if (arr.length < numClicks) // Prefix arrays
        {
            ArrayPool pool = prefixArrayPool.get();
            pool.put(arr);
        } 
        // Do not pool full combination arrays.
    }

    // Add these fields to cache adjacents for the current firstTrueCell
    private static volatile BitSet FIRST_TRUE_ADJACENTS_BITSET = null;
    private static volatile int CACHED_FIRST_TRUE_CELL = -1;

    // TODO: Consider how useful this method is and look at moving satisfiesOddAdjacency to CombinationGeneratorTask
    private static boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        // Skip lazy initialization if it causes inlining issues
        if (CACHED_FIRST_TRUE_CELL != firstTrueCell) 
        {
            updateAdjacencyCache(firstTrueCell); // Extract to separate method
        }
        
        int count = 0;
        
        // O(1) adjacency check per click
        for (int click : combination) 
        {
            if (FIRST_TRUE_ADJACENTS_BITSET.get(click)) count++;
        }
        return (count & 1) == 1;
    }

    // Extract cache update to separate method to keep quickOddAdjacency small
    private static void updateAdjacencyCache(int firstTrueCell)
    {
        synchronized (CombinationGeneratorTask.class)
        {
            int[] adjacents = Grid.findAdjacents(firstTrueCell);
            FIRST_TRUE_ADJACENTS_BITSET = new BitSet(700);
            for (int adj : adjacents) FIRST_TRUE_ADJACENTS_BITSET.set(adj);
            CACHED_FIRST_TRUE_CELL = firstTrueCell;
        }
    }
}
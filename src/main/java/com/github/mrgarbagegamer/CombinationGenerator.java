package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.MpmcArrayQueue;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private static final int BATCH_SIZE = 2000; // Tune as needed
    private static final int FLUSH_THRESHOLD = (int) (BATCH_SIZE / 2);
    private static final int POOL_SIZE = 4096; // Tune as needed

    private static int[] TRUE_CELLS = null;

    private final CombinationQueueArray queueArray;
    private final int numClicks;
    private final int firstClickStart, firstClickEnd;
    private final int numConsumers;
    

    // Generator-local pools
    private final Deque<int[]> indicesPool = new ArrayDeque<>(POOL_SIZE);
    private final Deque<CombinationState> statePool = new ArrayDeque<>(POOL_SIZE);

    private final MpmcArrayQueue<WorkBatch> workBatchPool;

    private int roundRobinIdx = 0;

    public CombinationGenerator(String threadName, CombinationQueueArray queueArray, int numClicks, int firstClickStart, int firstClickEnd, int numConsumers, int[] trueCells) 
    {
        super(threadName);
        this.queueArray = queueArray;
        this.numClicks = numClicks;
        this.firstClickStart = firstClickStart;
        this.firstClickEnd = firstClickEnd;
        this.numConsumers = numConsumers;

        // Lazy static initialization of true cells
        if (TRUE_CELLS == null)
        {
            TRUE_CELLS = trueCells;
        }
        
        this.workBatchPool = queueArray.getWorkBatchPool();
    }

    @Override
    public void run() 
    {
        generateCombinationsIterative(numClicks);
    }

    private int[] getIndices(int k) {
        int[] arr = indicesPool.pollFirst();
        if (arr == null || arr.length != k) return new int[k];
        return arr;
    }
    private void recycleIndices(int[] arr) {
        if (indicesPool.size() < POOL_SIZE) indicesPool.offerFirst(arr);
    }
    private CombinationState getState(int start, int size, int[] indices) {
        CombinationState s = statePool.pollFirst();
        if (s == null) return new CombinationState(start, size, indices);
        s.start = start;
        s.size = size;
        s.indices = indices;
        return s;
    }
    private void recycleState(CombinationState s) {
        if (statePool.size() < POOL_SIZE) statePool.offerFirst(s);
    }

    private WorkBatch getWorkBatch() 
    {
        WorkBatch batch = workBatchPool.poll();
        if (batch == null) return new WorkBatch(BATCH_SIZE);
        return batch;
    }

    private void generateCombinationsIterative(int k)
    {
        
        Deque<CombinationState> stack = new ArrayDeque<>();
        for (int i = firstClickStart; i < firstClickEnd; i++) 
        {
            int[] indices = getIndices(k);
            indices[0] = i;
            stack.push(getState(i + 1, 1, indices));
        }

        WorkBatch batch = getWorkBatch();
        int[] buffer = new int[k];

        while (!stack.isEmpty() && !queueArray.solutionFound) 
        {
            CombinationState state = stack.pop();
            int start = state.start;
            int size = state.size;
            int[] indices = state.indices;

            // TODO: Look at removing this section (it should never be reached in practice, but it makes the bytecode longer and stifles inlining efforts)
            if (size == k) 
            {
                for (int j = 0; j < k; j++) buffer[j] = indices[j];
                if (TRUE_CELLS != null && TRUE_CELLS.length > 0 && !quickOddAdjacency(buffer, TRUE_CELLS[0])) 
                {
                    // If we have true cells and the first adjacent is not satisfied, skip this combination
                    recycleIndices(indices);
                    recycleState(state);
                    continue; // Skip this combination
                }

                batch.add(buffer);

                recycleIndices(indices);
                recycleState(state);
                if (batch.size() >= FLUSH_THRESHOLD) 
                    if (flushBatch(batch, roundRobinIdx)) batch = getWorkBatch(); // Get a new batch if we flushed
                continue;
            }

            if (size >= 2 && !canPotentiallySatisfyConstraints(size, indices)) 
            {
                // If we can't potentially satisfy constraints, skip this state
                recycleIndices(indices);
                recycleState(state);
                continue;
            }

            for (int i = Grid.NUM_CELLS - 1; i >= start; i--) 
            {
                int[] newIndices = getIndices(k);
                System.arraycopy(indices, 0, newIndices, 0, size);
                newIndices[size] = i;

                if (size + 1 < k) 
                {
                    stack.push(getState(i + 1, size + 1, newIndices));
                }
                else if (size + 1 == k) 
                {
                    for (int j = 0; j < k; j++) buffer[j] = newIndices[j];
                    if (TRUE_CELLS != null && TRUE_CELLS.length > 0 && !quickOddAdjacency(buffer, TRUE_CELLS[0])) 
                    {
                        // If we have true cells and the first adjacent is not satisfied, skip this combination
                        recycleIndices(newIndices);
                        recycleState(state);
                        continue; // Skip this combination
                    }

                    batch.add(buffer);
                    recycleIndices(newIndices);
                    if (batch.size() >= FLUSH_THRESHOLD)
                    {
                        if (flushBatch(batch, roundRobinIdx)) batch = getWorkBatch();
                    }
                }
            }
            recycleIndices(indices);
            recycleState(state);
        }
        // Flush any remaining combinations in the batch
        flushBatch(batch, roundRobinIdx);
        logger.info("Thread {} finished generating combinations for prefix range [{}-{})", getName(), firstClickStart, firstClickEnd);
    }

    // Add these static fields for pre-computed adjacency data
    private static long[] TRUE_CELL_ADJACENCY_MASKS = null;
    // NEW: Add a field for the pre-computed suffix OR masks
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
            synchronized (CombinationGenerator.class) 
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
     * This should be small enough for C2 inlining.
     */
    private boolean canPotentiallySatisfyConstraints(int prefixLength, int[] prefix)
    {
        if (TRUE_CELLS == null || TRUE_CELLS.length == 0) return true;
        
        // Ensure masks are initialized
        ensureTrueCellMasks(TRUE_CELLS);
        
        // Compute current adjacency state using bitmasks
        long currentAdjacencies = 0L; // Create a mask with all true cells set to 0
        
        for (int j = 0; j < prefixLength; j++) // For each click in the prefix
        {
            currentAdjacencies ^= TRUE_CELL_ADJACENCY_MASKS[prefix[j]]; // Toggle the affected true cells by XOR-ing the mask generated on initialization
        }
        
        // Check what we need to achieve: all bits should be 1 (odd adjacency for all true cells)
        long targetMask = (1L << TRUE_CELLS.length) - 1;
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
    
    private boolean flushBatch(WorkBatch batch, int roundRobinIdx)
    {
        while (true) 
        {
            for (int attempt = 0; attempt < numConsumers && !batch.isEmpty(); attempt++) 
            {
                int idx = (roundRobinIdx + attempt) % numConsumers;
                CombinationQueue queue = queueArray.getQueue(idx);
                if (queue.add(batch)) return true;
            }
            if (batch.isFull())
            {
                try 
                { 
                    Thread.sleep(1); 
                } 
                catch (InterruptedException e) 
                { 
                    Thread.currentThread().interrupt(); 
                    break; 
                }
            }
            else return false;
            if (queueArray.solutionFound) return false;
        }
        return false;
    }

    // Cache multiple adjacency mmasks to reduce synchronization
    private static volatile long[][] ADJACENCY_MASK_CACHE = new long[16][];
    private static volatile int[] CACHED_TRUE_CELLS = new int[16];
    private static volatile int CACHE_SIZE = 0;

    private static boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        long[] mask = getCachedAdjacencyMask(firstTrueCell);
        
        int count = 0;
        // Unroll the loop for better performance on small combinations
        int length = combination.length;
        for (int i = 0; i < length; i++)
        {
            int click = combination[i];
            int longIndex = click >>> 6; // Faster than click / 64
            int bitPosition = click & 63; // Faster than click % 64
            if (longIndex < mask.length && (mask[longIndex] & (1L << bitPosition)) != 0) 
            {
                count++;
            }
        }
        return (count & 1) == 1;
    }

    private static long[] getCachedAdjacencyMask(int firstTrueCell)
    {
        // Try cache first
        for (int i = 0; i < CACHE_SIZE; i++)
        {
            if (CACHED_TRUE_CELLS[i] == firstTrueCell)
            {
                return ADJACENCY_MASK_CACHE[i];
            }
        }
        
        // Cache miss - compute and store
        return computeAndCacheAdjacencyMask(firstTrueCell);
    }
    
    private static long[] computeAndCacheAdjacencyMask(int firstTrueCell)
    {
        synchronized (CombinationGenerator.class)
        {
            // Double-check after acquiring lock
            for (int i = 0; i < CACHE_SIZE; i++)
            {
                if (CACHED_TRUE_CELLS[i] == firstTrueCell)
                {
                    return ADJACENCY_MASK_CACHE[i];
                }
            }
            
            // Compute new mask
            int[] adjacents = Grid.findAdjacents(firstTrueCell);
            long[] mask = new long[2]; // 109 bits, 2 longs
            
            for (int adj : adjacents) 
            {
                int longIndex = adj >>> 6;
                int bitPosition = adj & 63;
                mask[longIndex] |= (1L << bitPosition);
            }
            
            // Add to cache (with simple replacement if full)
            int cacheIndex = CACHE_SIZE < 16 ? CACHE_SIZE++ : 
                            (firstTrueCell & 15); // Simple hash for replacement
            
            ADJACENCY_MASK_CACHE[cacheIndex] = mask;
            CACHED_TRUE_CELLS[cacheIndex] = firstTrueCell;
            
            return mask;
        }
    }
}

class CombinationState 
{
    int start, size;
    int[] indices;

    CombinationState(int start, int size, int[] indices) 
    {
        this.start = start;
        this.size = size;
        this.indices = indices;
    }
}
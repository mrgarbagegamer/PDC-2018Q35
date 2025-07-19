package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

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
    

    // Generator-local pools
    private final ArrayPool indicesPool = new ArrayPool(POOL_SIZE, 64); // Pool for indices arrays
    private final Deque<CombinationState> statePool = new ArrayDeque<>(POOL_SIZE); // TODO: Make a StatePool class similar to the TaskPool class of CombinationGeneratorTask

    private final MpmcArrayQueue<WorkBatch> workBatchPool;

    public CombinationGenerator(String threadName, CombinationQueueArray queueArray, int numClicks, int firstClickStart, int firstClickEnd, int[] trueCells) 
    {
        super(threadName);
        this.queueArray = queueArray;
        this.numClicks = numClicks;
        this.firstClickStart = firstClickStart;
        this.firstClickEnd = firstClickEnd;

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
        int[] arr = indicesPool.get(k);
        if (arr == null) return new int[k];
        return arr;
    }
    private void recycleIndices(int[] arr) {
        if (indicesPool.size() < POOL_SIZE) indicesPool.put(arr);
    }
    private CombinationState getState(int start, int size, int[] indices, long adjacencyMask) {
        CombinationState s = statePool.pollFirst();
        if (s == null) return new CombinationState(start, size, indices, -1);
        s.start = start;
        s.size = size;
        s.indices = indices;
        s.adjacencies = adjacencyMask;
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
            stack.push(getState(i + 1, 1, indices, -1));
        }

        WorkBatch batch = getWorkBatch();
        int[] buffer = new int[k];

        while (!stack.isEmpty() && !queueArray.solutionFound) 
        {
            CombinationState state = stack.pop();
            int start = state.start;
            int size = state.size;
            int[] indices = state.indices;

            if (size >= 2 && !canPotentiallySatisfyConstraints(state)) 
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
                    stack.push(getState(i + 1, size + 1, newIndices, state.adjacencies));
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
                    
                    if (!batch.add(buffer))
                    {
                        if (flushBatch(batch)) 
                        {
                            batch = getWorkBatch();
                            batch.add(buffer);
                        }
                    }

                    recycleIndices(newIndices);
                }
            }
            recycleIndices(indices);
            recycleState(state);
        }
        // Flush any remaining combinations in the batch
        while (!flushBatch(batch));
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
     * Uses incremental state tracking to avoid recomputing XORs.
     */
    private boolean canPotentiallySatisfyConstraints(CombinationState state)
    {
        if (TRUE_CELLS == null || TRUE_CELLS.length == 0) return true;
        
        int prefixLength = state.size;
        int[] prefix = state.indices;
        
        // Ensure masks are initialized
        ensureTrueCellMasks(TRUE_CELLS);
        
        // Use cached adjacency state from parent if available
        long currentAdjacencies;
        if (state.adjacencies != -1)
        {
            // Incrementally update from parent's state
            currentAdjacencies = state.adjacencies ^ TRUE_CELL_ADJACENCY_MASKS[prefix[prefixLength - 1]];
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
        state.adjacencies = currentAdjacencies;
        
        // Check what we need to achieve: all bits should be 1 (odd adjacency for all true cells)
        long targetMask = (1L << TRUE_CELLS.length) - 1;
        long needed = currentAdjacencies ^ targetMask; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good
        if (needed == 0L) return true;
        
        // Use pre-computed suffix masks

        int startIdx = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        long availableAdjacencies = SUFFIX_OR_MASKS[startIdx];
        
        // Check if available clicks can satisfy all needed adjacencies
        return (availableAdjacencies & needed) == needed; // If at least one click can satisfy each needed adjacency, return true
    }
    
    private final boolean flushBatch(WorkBatch batch)
    {
        if (batch.isEmpty()) return false;

        CombinationQueue[] queues = queueArray.getAllQueues();
        int startIdx = ThreadLocalRandom.current().nextInt(queues.length);

        for (int i = 0; i < queues.length; i++)
        {
            if (queues[(startIdx + i) % queues.length].add(batch)) return true;
        }

        return false;
    }

    // Cache multiple adjacency mmasks to reduce synchronization
    private static final long[][] ADJACENCY_MASK_CACHE_FAST = new long[16][];
    private static final int[] CACHED_TRUE_CELLS_FAST = new int[16];

    private static final boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        // Get mask with minimal overhead
        long[] mask = ADJACENCY_MASK_CACHE_FAST[firstTrueCell & 15];
        if (mask == null || CACHED_TRUE_CELLS_FAST[firstTrueCell & 15] != firstTrueCell) 
        {
            mask = computeAdjacencyMaskFast(firstTrueCell);
        }

        // Count adjacencies with unrolled checks for small arrays
        int count = 0;
        int length = combination.length;

        for (int i = 0; i < length; i++)
        {
            int click = combination[i];
            if ((mask[click >>> 6] & (1L << (click & 63))) != 0) count++;
        }

        return (count & 1) == 1; 
    }

    private static long[] computeAdjacencyMaskFast(int firstTrueCell)
    {
        int cacheIdx = firstTrueCell & 15;

        // Simple double-check without heavy synchronization
        if (CACHED_TRUE_CELLS_FAST[cacheIdx] == firstTrueCell) 
        {
            return ADJACENCY_MASK_CACHE_FAST[cacheIdx];
        }

        synchronized (CombinationGenerator.class)
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
}

class CombinationState 
{
    int start, size;
    int[] indices;
    long adjacencies = -1;

    CombinationState(int start, int size, int[] indices, long adjacencyMask) 
    {
        this.start = start;
        this.size = size;
        this.indices = indices;
        this.adjacencies = adjacencyMask;
    }
}
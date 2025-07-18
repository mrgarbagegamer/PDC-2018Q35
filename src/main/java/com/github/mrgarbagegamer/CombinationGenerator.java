package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.MpmcArrayQueue;

import it.unimi.dsi.fastutil.ints.IntList;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private static final int BATCH_SIZE = 2000; // Tune as needed
    private static final int FLUSH_THRESHOLD = (int) (BATCH_SIZE / 2);
    private static final int POOL_SIZE = 4096; // Tune as needed

    // Static caches for each grid type
    private static int[] TRUE_CELLS;
    private static int[] FIRST_TRUE_ADJACENTS;
    private static GridType CURRENT_GRID_TYPE = null;

    private final CombinationQueueArray queueArray;
    private final IntList possibleClicks;
    private final int numClicks;
    private final int firstClickStart, firstClickEnd;
    private final int numConsumers;
    

    // Generator-local pools
    private final Deque<int[]> indicesPool = new ArrayDeque<>(POOL_SIZE);
    private final Deque<CombinationState> statePool = new ArrayDeque<>(POOL_SIZE);

    private final MpmcArrayQueue<WorkBatch> workBatchPool;

    private int roundRobinIdx = 0;

    public CombinationGenerator(String threadName, CombinationQueueArray queueArray, IntList possibleClicks, int numClicks, int firstClickStart, int firstClickEnd, int numConsumers, GridType gridType) 
    {
        super(threadName);
        this.queueArray = queueArray;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.firstClickStart = firstClickStart;
        this.firstClickEnd = firstClickEnd;
        this.numConsumers = numConsumers;

        // Lazy static initialization for the selected grid type
        synchronized (CombinationGenerator.class) 
        {
            if (CURRENT_GRID_TYPE != gridType) 
            {
                Grid baseGrid;
                switch (gridType) 
                {
                    case GRID13: baseGrid = new Grid13(); break;
                    case GRID22: baseGrid = new Grid22(); break;
                    case GRID35: baseGrid = new Grid35(); break;
                    default: throw new IllegalArgumentException("Unknown grid type");
                }
                TRUE_CELLS = baseGrid.findTrueCells();
                FIRST_TRUE_ADJACENTS = baseGrid.findFirstTrueAdjacents();
                CURRENT_GRID_TYPE = gridType;
            }
            workBatchPool = queueArray.getWorkBatchPool();
        }
    }

    @Override
    public void run() 
    {
        generateCombinationsIterative(possibleClicks, numClicks);
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

    private void generateCombinationsIterative(IntList nodeList, int k)
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

            if (size == k) 
            {
                for (int j = 0; j < k; j++) buffer[j] = nodeList.getInt(indices[j]);
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

            for (int i = nodeList.size() - 1; i >= start; i--) 
            {
                int[] newIndices = getIndices(k);
                System.arraycopy(indices, 0, newIndices, 0, size);
                newIndices[size] = i;

                if (size + 1 < k) 
                {
                    stack.push(getState(i + 1, size + 1, newIndices));
                } 
                else if (FIRST_TRUE_ADJACENTS != null && size + 1 == k) 
                {
                    for (int j = 0; j < k; j++) buffer[j] = nodeList.getInt(newIndices[j]);
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
                else if (size + 1 == k) 
                {
                    for (int j = 0; j < k; j++) buffer[j] = nodeList.getInt(newIndices[j]);
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
    private static long[][] TRUE_CELL_ADJACENCY_MASKS = initTrueCellMasks();
    private static final boolean[][] CLICK_ADJACENCY_MATRIX = initClickAdjacencyMatrix();
    private static final int GRID_SIZE = 615; // Adjust for your grid

    private static long[][] initTrueCellMasks() 
    {
        // This will be set when the first generator is initialized with actual trueCells
        return null;
    }

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
    private static void ensureTrueCellMasks(int[] trueCells) 
    {
        if (TRUE_CELL_ADJACENCY_MASKS == null && trueCells != null) 
        {
            synchronized (CombinationGenerator.class) 
            {
                if (TRUE_CELL_ADJACENCY_MASKS == null) 
                {
                    // TODO: Look at replacing the 2D array with a 1D array of long[] for better memory efficiency (the longs will be packed into a single long for each click cell)
                    long[][] masks = new long[GRID_SIZE][]; // Create an array to store masks for each click cell
                    
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
                        masks[clickCell] = new long[] { mask }; // Store the mask for this click cell in a single-element long array
                    }
                    
                    TRUE_CELL_ADJACENCY_MASKS = masks; // Assign the masks to the static field
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
            int clickIndex = possibleClicks.getInt(prefix[j]); // Get the packed int corresponding to the click
            if (clickIndex < GRID_SIZE && TRUE_CELL_ADJACENCY_MASKS[clickIndex] != null) 
            {
                currentAdjacencies ^= TRUE_CELL_ADJACENCY_MASKS[clickIndex][0]; // Toggle the affected true cells by XOR-ing the mask generated on initialization
            }
        }
        
        // Check what we need to achieve: all bits should be 1 (odd adjacency for all true cells)
        long targetMask = (1L << TRUE_CELLS.length) - 1;
        long needed = currentAdjacencies ^ targetMask; // XOR with target to find which bits need to be flipped
        
        // If no bits need to be flipped, we're already good
        if (needed == 0L) return true;
        
        // Check if remaining clicks can provide the needed adjacencies
        int startIdx = (prefixLength == 0) ? 0 : (prefix[prefixLength - 1] + 1);
        int maxIdx = possibleClicks.size();
        
        long availableAdjacencies = 0L; // Create a mask with all true cells set to 0
        
        for (int i = startIdx; i < maxIdx; i++) // For each remaining possible click
        {
            int clickIndex = possibleClicks.getInt(i); // Get the packed int corresponding to the click
            if (clickIndex < GRID_SIZE && TRUE_CELL_ADJACENCY_MASKS[clickIndex] != null) 
            {
                availableAdjacencies |= TRUE_CELL_ADJACENCY_MASKS[clickIndex][0]; // Add the mask for this click to the available adjacencies
            }
        }
        
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

    private static boolean quickOddAdjacency(int[] combination, int firstTrueCell) 
    {
        int count = 0;
        for (int click : combination) 
        {
            if (Grid.areAdjacent(firstTrueCell, click)) count++;
        }
        return (count & 1) == 1;
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
package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntList;

public class CombinationGenerator extends Thread 
{
    private static final Logger logger = LogManager.getLogger(CombinationGenerator.class);

    private static final int BATCH_SIZE = 2000; // Tune as needed
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

    private void generateCombinationsIterative(IntList nodeList, int k)
    {
        
        Deque<CombinationState> stack = new ArrayDeque<>();
        for (int i = firstClickStart; i < firstClickEnd; i++) 
        {
            int[] indices = getIndices(k);
            indices[0] = i;
            stack.push(getState(i + 1, 1, indices));
        }

        WorkBatch batch = new WorkBatch(BATCH_SIZE);
        int roundRobinIdx = 0;
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

                addCombinationToBatch(nodeList, indices, buffer, batch, k);

                recycleIndices(indices);
                recycleState(state);
                if (batch.size() >= BATCH_SIZE) 
                    roundRobinIdx = flushBatch(batch, roundRobinIdx);
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

                    addCombinationToBatch(nodeList, newIndices, buffer, batch, k);

                    recycleIndices(newIndices);
                    if (batch.size() >= BATCH_SIZE) roundRobinIdx = flushBatch(batch, roundRobinIdx);
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

                    addCombinationToBatch(nodeList, newIndices, buffer, batch, k);
                    recycleIndices(newIndices);
                    if (batch.size() >= BATCH_SIZE) roundRobinIdx = flushBatch(batch, roundRobinIdx);
                }
            }
            recycleIndices(indices);
            recycleState(state);
        }
        // Flush any remaining combinations in the batch
        flushBatch(batch, roundRobinIdx);
        logger.info("Thread {} finished generating combinations for prefix range [{}-{})", getName(), firstClickStart, firstClickEnd);
    }
    
    private void addCombinationToBatch(IntList nodeList, int[] indices, int[] buffer, WorkBatch batch, int k) 
    {
        for (int j = 0; j < k; j++)
        { 
            buffer[j] = nodeList.getInt(indices[j]);
        }
        int[] combination = new int[k];
        System.arraycopy(buffer, 0, combination, 0, k);
        batch.add(combination);
    }

    private int flushBatch(WorkBatch batch, int roundRobinIdx)
    {
        while (!batch.isEmpty() && !queueArray.solutionFound) 
        {
            boolean addedAny = false;
            for (int attempt = 0; attempt < numConsumers && !batch.isEmpty(); attempt++) 
            {
                int idx = (roundRobinIdx + attempt) % numConsumers;
                CombinationQueue queue = queueArray.getQueue(idx);
                int added = queue.fillFromWorkBatch(batch);
                if (added > 0) 
                {
                    roundRobinIdx = (idx + 1) % numConsumers;
                    addedAny = true;
                }
            }
            if (!addedAny) 
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
        }
        return roundRobinIdx;
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
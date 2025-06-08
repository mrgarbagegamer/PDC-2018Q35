package com.github.mrgarbagegamer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;

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
    private final int prefixStart, prefixEnd; // Instead of firstClick, secondClick
    private final int numConsumers;
    

    // Generator-local pools
    private final Deque<int[]> indicesPool = new ArrayDeque<>(POOL_SIZE);
    private final Deque<CombinationState> statePool = new ArrayDeque<>(POOL_SIZE);

    public CombinationGenerator(String threadName, CombinationQueueArray queueArray, IntList possibleClicks, int numClicks, int prefixStart, int prefixEnd, int numConsumers, GridType gridType) 
    {
        super(threadName);
        this.queueArray = queueArray;
        this.possibleClicks = possibleClicks;
        this.numClicks = numClicks;
        this.prefixStart = prefixStart;
        this.prefixEnd = prefixEnd;
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
        int safeEnd = Math.min(prefixEnd, possibleClicks.size());
        for (int i = prefixStart; i < safeEnd && !queueArray.isSolutionFound(); i++) {
            for (int j = i + 1; j < possibleClicks.size() && !queueArray.isSolutionFound(); j++) {
                generateCombinationsIterative(i, j, possibleClicks, numClicks);
            }
        }
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

    // Add this overload for two fixed prefix clicks
    private void generateCombinationsIterative(int firstClick, int secondClick, IntList nodeList, int k)
    {
        Deque<CombinationState> stack = new ArrayDeque<>();
        int[] initialIndices = getIndices(k);
        initialIndices[0] = firstClick;
        initialIndices[1] = secondClick;
        stack.push(getState(secondClick, 2, initialIndices));

        List<int[]> batch = new ArrayList<>(BATCH_SIZE);
        int roundRobinIdx = 0;
        int[] buffer = new int[k];

        while (!stack.isEmpty() && !queueArray.isSolutionFound()) 
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
                    recycleIndices(indices);
                    recycleState(state);
                    continue;
                }
                addCombinationToBatch(nodeList, indices, buffer, batch, k);
                recycleIndices(indices);
                recycleState(state);
                if (batch.size() >= BATCH_SIZE) roundRobinIdx = flushBatch(batch, roundRobinIdx);
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
                        recycleIndices(newIndices);
                        recycleState(state);
                        continue;
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
                        recycleIndices(newIndices);
                        recycleState(state);
                        continue;
                    }
                    addCombinationToBatch(nodeList, newIndices, buffer, batch, k);
                    recycleIndices(newIndices);
                    if (batch.size() >= BATCH_SIZE) roundRobinIdx = flushBatch(batch, roundRobinIdx);
                }
            }
            recycleIndices(indices);
            recycleState(state);
        }
        flushBatch(batch, roundRobinIdx);
        logger.info("Thread {} finished generating combinations with prefix [{}, {}]", getName(), Grid.indexToPacked(firstClick), Grid.indexToPacked(secondClick));
    }
    
    private void addCombinationToBatch(IntList nodeList, int[] indices, int[] buffer, List<int[]> batch, int k) 
    {
        for (int j = 0; j < k; j++)
        { 
            buffer[j] = nodeList.getInt(indices[j]);
        }
        int[] combination = new int[k];
        System.arraycopy(buffer, 0, combination, 0, k);
        batch.add(combination);
    }

    private int flushBatch(List<int[]> batch, int roundRobinIdx)
    {
        while (!batch.isEmpty() && !queueArray.isSolutionFound()) 
        {
            boolean addedAny = false;
            for (int attempt = 0; attempt < numConsumers && !batch.isEmpty(); attempt++) 
            {
                int idx = (roundRobinIdx + attempt) % numConsumers;
                CombinationQueue queue = queueArray.getQueue(idx);
                int added = queue.addBatch(batch);
                if (added > 0) 
                {
                    batch.subList(0, added).clear();
                    roundRobinIdx = (idx + 1) % numConsumers;
                    addedAny = true;
                }
            }
            if (!addedAny) 
            {
                try 
                { 
                    Thread.sleep(5); 
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
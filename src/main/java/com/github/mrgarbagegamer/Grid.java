package com.github.mrgarbagegamer;

import java.util.BitSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

public abstract class Grid {
    // constants
    public static final int NUM_ROWS = 7;
    public static final int ODD_NUM_COLS = 15;
    public static final int EVEN_NUM_COLS = 16;
    public static final int[] ROW_OFFSETS = {0, 16, 31, 47, 62, 78, 93};
    public static final int NUM_CELLS = 109;

    // Only BitSet for true cells
    BitSet trueCells = new BitSet(NUM_CELLS);
    int trueCellsCount = 0;

    private static final int[][] adjacencyArray = new int[NUM_ROWS * 100 + EVEN_NUM_COLS][];

    int firstTrueCell = -1; // Track the first true cell, initialized to -1 (no true cells)
    boolean recalculationNeeded = false; // Flag to indicate if a recalculation of the first true cell is needed

    static 
    {
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            for (int col = 0; col < (row % 2 == 0 ? EVEN_NUM_COLS : ODD_NUM_COLS); col++) 
            {
                int cell = row * 100 + col;
                IntList adjSet = computeAdjacents(row, col);
                int[] adjArr = new int[adjSet.size()];
                int idx = 0;
                for (IntIterator it = adjSet.iterator(); it.hasNext();) 
                {
                    adjArr[idx++] = it.nextInt();
                }
                adjacencyArray[cell] = adjArr;
            }
        }
    }

    public static IntList computeAdjacents(int row, int col) 
    {
        IntList affectedPieces = new IntArrayList(6);

        if (row % 2 == 0) // even rows with 16 columns
        { 
            affectedPieces.add((row - 1) * 100 + (col - 1));
            affectedPieces.add((row - 1) * 100 + col);
            affectedPieces.add(row * 100 + (col - 1));
            affectedPieces.add(row * 100 + (col + 1));
            affectedPieces.add((row + 1) * 100 + (col - 1));
            affectedPieces.add((row + 1) * 100 + col);
        } else // odd rows with 15 columns
        { 
            affectedPieces.add((row - 1) * 100 + col);
            affectedPieces.add((row - 1) * 100 + (col + 1));
            affectedPieces.add(row * 100 + (col - 1));
            affectedPieces.add(row * 100 + (col + 1));
            affectedPieces.add((row + 1) * 100 + col);
            affectedPieces.add((row + 1) * 100 + (col + 1));
        }

        // Remove out-of-bounds cells
        affectedPieces.removeIf(key -> {
            int r = key / 100, c = key % 100;
            return r < 0 || r >= NUM_ROWS || c < 0 || c >= ((r % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS);
        });

        return affectedPieces;
    }

    public static int[] findAdjacents(int row, int col) 
    {
        return adjacencyArray[row * 100 + col];
    }

    public static int[] findAdjacents(int cell) 
    {
        return adjacencyArray[cell];
    }

    // --- Packed int <-> compact BitSet index conversion ---
    public final static int packedToIndex(int packed) 
    {
        int row = packed / 100;
        int col = packed % 100;
        return ROW_OFFSETS[row] + col;
    }

    public final static int indexToPacked(int index) 
    {
        if (index < 16) return 0 * 100 + index;
        if (index < 31) return 1 * 100 + (index - 16);
        if (index < 47) return 2 * 100 + (index - 31);
        if (index < 62) return 3 * 100 + (index - 47);
        if (index < 78) return 4 * 100 + (index - 62);
        if (index < 93) return 5 * 100 + (index - 78);
        if (index < 109) return 6 * 100 + (index - 93);
        throw new IllegalArgumentException("Invalid BitSet index: " + index);
    }
    // ------------------------------------------------------

    public Grid() 
    {
        initialize();
    }

    abstract void initialize();

    public int[] findTrueCells() // Return an array of packed integers representing the true cells
    {
        int[] trueCellsArray = new int[trueCellsCount];
        int idx = 0;
        for (int i = trueCells.nextSetBit(0); i >= 0 && idx < trueCellsCount; i = trueCells.nextSetBit(i + 1)) 
        {
            trueCellsArray[idx++] = indexToPacked(i);
        }
        return trueCellsArray;
    }

    public int findFirstTrueCell() // Return the first element in the trueCells BitSet
    {
        if (trueCellsCount == 0)
        {
            return -1;
        }
        
        if (!recalculationNeeded && firstTrueCell != -1) 
        {
            return firstTrueCell; // Return cached value if recalculation is not needed
        }

        // Find and return the first true cell in the BitSet
        firstTrueCell = indexToPacked(trueCells.nextSetBit(0));
        recalculationNeeded = false; // Reset the recalculation flag after updating the first true cell
        return firstTrueCell;
    }

    public void click(int cell) 
    {
        int[] affectedPieces = findAdjacents(cell);
        for (int piece : affectedPieces) 
        {
            int bitIdx = packedToIndex(piece);
            boolean currentState = trueCells.get(bitIdx);
            if (currentState) 
            {
                if (piece == firstTrueCell) recalculationNeeded = true;
                trueCells.clear(bitIdx);
                trueCellsCount--;
            } else 
            {
                if (piece < firstTrueCell) firstTrueCell = piece;
                trueCells.set(bitIdx);
                trueCellsCount++;
            }
        }
    }

    public void click(int row, int col) 
    {
        int[] affectedPieces = findAdjacents(row, col);
        for (int piece : affectedPieces) 
        {
            int bitIdx = packedToIndex(piece);
            boolean currentState = trueCells.get(bitIdx);
            if (currentState) 
            {
                if (piece == firstTrueCell) recalculationNeeded = true;
                trueCells.clear(bitIdx);
                trueCellsCount--;
            } else 
            {
                if (piece < firstTrueCell) firstTrueCell = piece;
                trueCells.set(bitIdx);
                trueCellsCount++;
            }
        }
    }

    public int[] findFirstTrueAdjacents() 
    {
        int firstTrueCell = findFirstTrueCell();
        if (firstTrueCell == -1) return null;
        int[] trueAdjacents = findAdjacents(firstTrueCell);
        return (trueAdjacents.length == 0) ? null : trueAdjacents;
    }

    public int[] findFirstTrueAdjacentsAfter(int cell) 
    {
        int[] firstTrueAdjacents = findFirstTrueAdjacents();
        if (firstTrueAdjacents == null) return null;
        
        // Perform binary search to find the index of the first adjacent cell greater than 'cell'
        int index = -1;
        int low = 0, high = firstTrueAdjacents.length - 1;
        while (low <= high) 
        {
            int mid = (low + high) / 2;
            if (firstTrueAdjacents[mid] > cell) 
            {
                index = mid; // Found a candidate, but keep searching left for the first one
                high = mid - 1;
            } else 
            {
                low = mid + 1; // Search right
            }
        }
        // If the index is found, return the subarray starting from that index
        int[] result = new int[firstTrueAdjacents.length - index];
        System.arraycopy(firstTrueAdjacents, index, result, 0, result.length);
        return result;
    }

    public boolean isSolved() 
    {
        return trueCells.nextSetBit(0) == -1;
    }

    public int getTrueCount() // Returns the count of true cells
    {
        return trueCellsCount;
    }

    public Grid clone() 
    {
        try 
        {
            Grid newGrid = this.getClass().getDeclaredConstructor().newInstance();
            newGrid.trueCells = new BitSet(NUM_CELLS);
            newGrid.trueCells.or(this.trueCells);
            newGrid.trueCellsCount = this.trueCellsCount;
            newGrid.firstTrueCell = this.firstTrueCell;
            return newGrid;
        } catch (Exception e) 
        {
            throw new RuntimeException("Failed to clone Grid", e);
        }
    }

    /**
     * Returns true if the click could possibly affect or create a new first true cell.
     * - If there are no true cells, any click can create one.
     * - If the first true cell is the top-left cell (0,0), only its adjacents can affect it.
     * - If the click is before or equal to the first true cell (by packed int order), it can create a new one.
     * - If the click is adjacent to the first true cell, it can affect it.
     */
    public static boolean canAffectFirstTrueCell(int firstTrueCell, int clickCell) 
    {
        if (firstTrueCell == -1) return true; // No true cells, any click can create one
        if (clickCell <= firstTrueCell) return true; // packed int order: row * 100 + col

        // Edge case: first true cell is top-left (0,0)
        if (firstTrueCell == 0) 
        {
            int[] adj = findAdjacents(0);
            if (adj == null || adj.length == 0) return false; // No adjacents, can't affect
            
            // Binary search for the click cell in the adjacents
            int low = 0, high = adj.length - 1;
            while (low <= high) 
            {
                int mid = (low + high) / 2;
                if (adj[mid] == clickCell) 
                {
                    return true; // Click cell is adjacent to the first true cell
                } else if (adj[mid] < clickCell) 
                {
                    low = mid + 1; // Search right
                } else 
                {
                    high = mid - 1; // Search left
                }
            }
            return false; // Click cell is not adjacent to the first true cell
        }

        // General case: check adjacency
        int[] adj = findAdjacents(firstTrueCell);
        if (adj == null || adj.length == 0) return false; // No adjacents, can't affect

        // perform binary search to find if clickCell is adjacent
        int low = 0, high = adj.length - 1;
        while (low <= high) 
        {
            int mid = (low + high) / 2;
            if (adj[mid] == clickCell) 
            {
                return true; // Click cell is adjacent to the first true cell
            } else if (adj[mid] < clickCell) 
            {
                low = mid + 1; // Search right
            } else 
            {
                high = mid - 1; // Search left
            }
        }
        return false; // Click cell is not adjacent to the first true cell
    }

    // Optional: Print grid as text using BitSet only
    public void printGrid() 
    {
        Logger logger = LogManager.getLogger(Grid.class);
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            StringBuilder sb = new StringBuilder();
            if (row % 2 != 0) sb.append(" ");
            int cols = (row % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS;
            for (int col = 0; col < cols; col++) 
            {
                int bitIdx = packedToIndex(row * 100 + col);
                sb.append(trueCells.get(bitIdx) ? "1 " : "0 ");
            }
            logger.info(sb.toString());
        }
    }
}
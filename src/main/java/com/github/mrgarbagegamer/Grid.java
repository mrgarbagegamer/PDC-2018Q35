package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public abstract class Grid {
    // constants
    public static final int NUM_ROWS = 7;
    public static final int LAST_ROW = (NUM_ROWS - 1);

    // odd column values
    public static final int ODD_NUM_COLS = 15;
    public static final int LAST_ODD_COL = (ODD_NUM_COLS - 1);

    // even column values
    public static final int EVEN_NUM_COLS = 16;
    public static final int LAST_EVEN_COL = (EVEN_NUM_COLS - 1);

    // Initializing the grid of seven rows with alternating columns of 16 and 15
    boolean[][] grid = new boolean[][] 
    {
        new boolean[Grid.EVEN_NUM_COLS],
        new boolean[Grid.ODD_NUM_COLS],
        new boolean[Grid.EVEN_NUM_COLS],
        new boolean[Grid.ODD_NUM_COLS],
        new boolean[Grid.EVEN_NUM_COLS],
        new boolean[Grid.ODD_NUM_COLS],
        new boolean[Grid.EVEN_NUM_COLS]
    };

    // Use IntSet for true cells
    public IntSet trueCells = new IntOpenHashSet();

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
                IntSet adjSet = computeAdjacents(row, col);
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

    public static IntSet computeAdjacents(int row, int col) 
    {
        IntSet affectedPieces = new IntOpenHashSet();

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

    public Grid() 
    {
        initialize();
    }

    abstract void initialize();

    void copyColumnValues(boolean[] source, boolean[] target) 
    {
        for (int i = 0; i < source.length; i++) 
        {
            target[i] = source[i];
        }
    }

    public IntSet findTrueCells() 
    {
        IntSet trueCellsSet = new IntOpenHashSet();
        // Iterate through the trueCells IntSet and add the true cells to the new IntSet
        for (IntIterator iter = trueCells.iterator(); iter.hasNext();) 
        {
            int key = iter.nextInt();
            trueCellsSet.add(key);
        }
        return trueCellsSet;
    }

    public int findFirstTrueCell()
    {
        // Return the first element in the trueCells IntSet
        if (trueCells.isEmpty()) 
        {
            return -1; // No true cells found
        }

        else if (!recalculationNeeded && firstTrueCell != -1) 
        {
            return firstTrueCell; // Return cached value if recalculation is not needed
        }

        // Iterate through the trueCells IntSet and find the first true cell (comparing the values to determine the first one)
        firstTrueCell = Integer.MAX_VALUE;
        for (IntIterator iter = trueCells.iterator(); iter.hasNext();) 
        {
            int key = iter.nextInt();
            if (key < firstTrueCell) 
            {
                firstTrueCell = key;
            }
        }
        if (firstTrueCell == Integer.MAX_VALUE) firstTrueCell = -1; // If no true cells were found, set to -1
        recalculationNeeded = false; // Reset the recalculation flag after updating the first true cell
        return firstTrueCell;
    }

    public void click(int cell) 
    {
        int[] affectedPieces = findAdjacents(cell);

        // Flip the state of the affected pieces (if the cell is true, remove it from the trueCells map, otherwise add it)

        for (int i = 0; i < affectedPieces.length; i++) 
        {
            int piece = affectedPieces[i];
            int pieceRow = piece / 100;
            int pieceCol = piece % 100;
            boolean currentState = grid[pieceRow][pieceCol];

            // Toggle the state
            grid[pieceRow][pieceCol] = !currentState;

            // Update the trueCells IntSet
            if (currentState) 
            {
                if (piece == firstTrueCell) recalculationNeeded = true; // If the first true cell is affected, mark recalculation as needed
                trueCells.remove(piece);
            } else 
            {
                if (piece < firstTrueCell) firstTrueCell = piece; // Update first true cell if the new piece is less than the current first true cell
                trueCells.add(piece);
            }
        }
    }

    public void click(int row, int col) // Identical method with different declaration for backwards compatibility
    {
        int[] affectedPieces = findAdjacents(row, col);

        for (int i = 0; i < affectedPieces.length; i++) 
        {
            int piece = affectedPieces[i];
            int pieceRow = piece / 100;
            int pieceCol = piece % 100;
            boolean currentState = grid[pieceRow][pieceCol];

            // Toggle the state
            grid[pieceRow][pieceCol] = !currentState;

            // Update the trueCells IntSet
            if (currentState) 
            {
                if (piece == firstTrueCell) recalculationNeeded = true; // If the first true cell is affected, mark recalculation as needed
                trueCells.remove(piece);
            } else 
            {
                if (piece < firstTrueCell) firstTrueCell = piece; // Update first true cell if the new piece is less than the current first true cell
                trueCells.add(piece);
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

    public boolean after(int[] x, int[] y) // Returns true if x is after y and false otherwise, deprecated because packed ints are used instead of int[]
    {
        if (x[0] > y[0]) 
        {
            return true;
        } else if (x[0] == y[0]) 
        {
            return x[1] > y[1];
        }
        return false;
    }

    public int[] findFirstTrueAdjacentsAfter(int cell) 
    {
        int[] firstTrueAdjacents = findFirstTrueAdjacents();
        if (firstTrueAdjacents == null) return null;
        
        int count = 0;
        for (int adj : firstTrueAdjacents) 
        {
            if (adj > cell) count++;
        }

        if (count == 0) return null;
        int[] filtered = new int[count];
        int idx = 0;

        for (int adj : firstTrueAdjacents) 
        {
            if (adj > cell) 
            {
                filtered[idx++] = adj;
            }
        }
        return filtered;

    }

    public boolean isSolved() 
    {
        boolean isSolved = false;

        // Check if the size of trueCells is 0, meaning no cells are true
        if (trueCells.size() == 0) 
        {
            isSolved = true;
        }
        return isSolved;
    }

    public void printGrid() 
    {
        Logger logger = LogManager.getLogger(Grid.class);

        for (int i = 0; i <= 6; i++) 
        {
            StringBuilder row = new StringBuilder();

            if (i % 2 == 0) 
            {
                for (int j = 0; j <= 15; j++) 
                {
                    row.append(grid[i][j] ? "1 " : "0 ");
                }
            } else 
            {
                row.append(" ");
                for (int j = 0; j <= 14; j++) 
                {
                    row.append(grid[i][j] ? "1 " : "0 ");
                }
            }

            logger.info(row.toString());
        }
    }

    public boolean[][] getGrid() 
    {
        return grid;
    }

    public int getTrueCount() 
    {
        return trueCells.size();
    }

    public Grid clone() 
    {
        try 
        {
            Grid newGrid = this.getClass().getDeclaredConstructor().newInstance();

            // Copy grid values
            for (int row = 0; row < NUM_ROWS; row++) 
            {
                System.arraycopy(this.grid[row], 0, newGrid.grid[row], 0, this.grid[row].length);
            }
            
            newGrid.trueCells = new IntOpenHashSet();

            // Add the true cells to the new grid's IntSet
            newGrid.trueCells.addAll(this.trueCells);
            newGrid.firstTrueCell = this.firstTrueCell; // Copy the first true cell

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
            for (int adjacent : adj) 
            {
                if (adjacent == clickCell) return true; // Click is adjacent to the first true cell
            }
            return false;
        }

        // General case: check adjacency
        int[] adj = findAdjacents(firstTrueCell);
        if (adj == null || adj.length == 0) return false; // No adjacents, can't affect
        for (int adjacent : adj) 
        {
            if (adjacent == clickCell) return true; // Click is adjacent to the first true cell
        }
        return false;
    }
}
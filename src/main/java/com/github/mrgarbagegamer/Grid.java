package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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

    // Use IntSet for adjacents
    private static final Int2ObjectOpenHashMap<IntSet> adjacencyMap = new Int2ObjectOpenHashMap<>(); // Future note: Consider using Int2ObjectOpenHashMap with a value type of IntSet for better performance

    static 
    {
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            for (int col = 0; col < (row % 2 == 0 ? EVEN_NUM_COLS : ODD_NUM_COLS); col++) 
            {
                IntSet adjacents = computeAdjacents(row, col);
                adjacencyMap.put(row * 100 + col, adjacents);
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

    public static IntSet findAdjacents(int row, int col) 
    {
        return adjacencyMap.get(row * 100 + col);
    }

    public static IntSet findAdjacents(int cell) 
    {
        return adjacencyMap.get(cell);
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
        for (int key : trueCells) 
        {
            int row = key / 100;
            int col = key % 100;
            trueCellsSet.add(row * 100 + col);
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

        // Iterate through the trueCells IntSet and find the first true cell (comparing the values to determine the first one)
        int firstTrueCell = Integer.MAX_VALUE;
        for (int key : trueCells) 
        {
            if (key < firstTrueCell) 
            {
                firstTrueCell = key;
            }
        }
        return firstTrueCell;
    }

    public void click(int cell) 
    {
        IntSet affectedPieces = findAdjacents(cell);

        // Flip the state of the affected pieces (if the cell is true, remove it from the trueCells map, otherwise add it)
        // for (int[] piece : affectedPieces) 
        // {
        //     int pieceRow = piece[0];
        //     int pieceCol = piece[1];
        //     int key = pieceRow * 100 + pieceCol;
        //     boolean currentState = grid[pieceRow][pieceCol];

        //     // Toggle the state
        //     grid[pieceRow][pieceCol] = !currentState;

        //     // Update the trueCells map
        //     if (currentState) 
        //     {
        //         trueCells.remove(key);
        //     } else 
        //     {
        //         int[] cell = {pieceRow, pieceCol};
        //         trueCells.putIfAbsent(key, cell);
        //     }
        // }

        for (int piece : affectedPieces) 
        {
            int pieceRow = piece / 100;
            int pieceCol = piece % 100;
            boolean currentState = grid[pieceRow][pieceCol];

            // Toggle the state
            grid[pieceRow][pieceCol] = !currentState;

            // Update the trueCells IntSet
            if (currentState) 
            {
                trueCells.remove(piece);
            } else 
            {
                trueCells.add(piece);
            }
        }
    }

    public void click(int row, int col) // Identical method with different declaration for backwards compatibility
    {
        IntSet affectedPieces = findAdjacents(row, col);

        for (int piece : affectedPieces) 
        {
            int pieceRow = piece / 100;
            int pieceCol = piece % 100;
            boolean currentState = grid[pieceRow][pieceCol];

            // Toggle the state
            grid[pieceRow][pieceCol] = !currentState;

            // Update the trueCells IntSet
            if (currentState) 
            {
                trueCells.remove(piece);
            } else 
            {
                trueCells.add(piece);
            }
        }
    }

    public IntSet findFirstTrueAdjacents() 
    {
        int firstTrueCell = findFirstTrueCell();
        IntSet trueAdjacents = findAdjacents(firstTrueCell);

        if (trueAdjacents.size() == 0) 
        {
            return null;
        }

        return trueAdjacents;
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

    public IntSet findFirstTrueAdjacentsAfter(int cell) 
    {
        IntSet firstTrueAdjacents = findFirstTrueAdjacents();
        IntSet filteredAdjacents = new IntOpenHashSet();

        if (firstTrueAdjacents == null) 
        {
            return null;
        }
        
        for (int adj : firstTrueAdjacents) 
        {
            // Compare if adjacent cell is after the clicked cell
            if (adj > cell)
            {
                filteredAdjacents.add(adj);
            }
        }

        if (filteredAdjacents.size() > 0)
        {
            return filteredAdjacents;
        }
        return null;
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

            return newGrid;
        } catch (Exception e) 
        {
            throw new RuntimeException("Failed to clone Grid", e);
        }
    }
}
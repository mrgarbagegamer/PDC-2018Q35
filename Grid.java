import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
    boolean[][] grid = new boolean[][] {
            new boolean[Grid.ODD_NUM_COLS],
            new boolean[Grid.EVEN_NUM_COLS],
            new boolean[Grid.ODD_NUM_COLS],
            new boolean[Grid.EVEN_NUM_COLS],
            new boolean[Grid.ODD_NUM_COLS],
            new boolean[Grid.EVEN_NUM_COLS],
            new boolean[Grid.ODD_NUM_COLS]
    };

    public Map<Integer, ArrayList<Integer[]>> trueCells = new HashMap<>();

    private static final Map<Integer, ArrayList<Integer[]>> adjacencyMap = new HashMap<>();

    static 
    {
        for (int row = 0; row < NUM_ROWS; row++) 
        {
            for (int col = 0; col < (row % 2 == 0 ? EVEN_NUM_COLS : ODD_NUM_COLS); col++) 
            {
                ArrayList<Integer[]> adjacents = computeAdjacents(row, col);
                adjacencyMap.put(row * 100 + col, adjacents);
            }
        }
    }

    public static ArrayList<Integer[]> computeAdjacents(int row, int col) {
        ArrayList<Integer[]> affectedPieces = new ArrayList<>();

        if (row % 2 == 0) { // even rows with 16 columns
            affectedPieces.add(new Integer[] { row - 1, col - 1 });
            affectedPieces.add(new Integer[] { row - 1, col });
            affectedPieces.add(new Integer[] { row, col - 1 });
            affectedPieces.add(new Integer[] { row, col + 1 });
            affectedPieces.add(new Integer[] { row + 1, col - 1 });
            affectedPieces.add(new Integer[] { row + 1, col });
        } else { // odd rows with 15 columns
            affectedPieces.add(new Integer[] { row - 1, col });
            affectedPieces.add(new Integer[] { row - 1, col + 1 });
            affectedPieces.add(new Integer[] { row, col - 1 });
            affectedPieces.add(new Integer[] { row, col + 1 });
            affectedPieces.add(new Integer[] { row + 1, col });
            affectedPieces.add(new Integer[] { row + 1, col + 1 });
        }

        affectedPieces.removeIf(piece -> piece[0] < 0 || piece[0] >= Grid.NUM_ROWS || piece[1] < 0 || piece[1] >= ((piece[0] % 2 == 0) ? Grid.EVEN_NUM_COLS : Grid.ODD_NUM_COLS));
        return affectedPieces;
    }

    public static ArrayList<Integer[]> findAdjacents(int row, int col) {
        return adjacencyMap.get(row * 100 + col);
    }

    public Grid() {
        initialize();
    }

    abstract void initialize();

    void copyColumnValues(boolean[] source, boolean[] target) {
        for (int i = 0; i < source.length; i++) {
            target[i] = source[i];
        }
    }

    public ArrayList<Integer[]> findTrueCells() {
        ArrayList<Integer[]> trueCellsList = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<Integer[]>> entry : trueCells.entrySet()) {
            trueCellsList.addAll(entry.getValue());
        }
        return trueCellsList;
    }

    private Integer[] findFirstTrueCell()
    {
        // Return the first element in the trueCells map
        for (Map.Entry<Integer, ArrayList<Integer[]>> entry : trueCells.entrySet()) 
        {
            if (!entry.getValue().isEmpty()) 
            {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    public void click(int row, int col) {
        ArrayList<Integer[]> affectedPieces = findAdjacents(row, col);

        // Flip the state of the affected pieces (if the cell is true, remove it from the trueCells map, otherwise add it)
        for (Integer[] piece : affectedPieces) {
            int pieceRow = piece[0];
            int pieceCol = piece[1];
            boolean currentState = grid[pieceRow][pieceCol];

            // Toggle the state
            grid[pieceRow][pieceCol] = !currentState;

            // Update the trueCells map
            if (currentState) {
                trueCells.get(pieceRow * 100 + pieceCol).remove(piece);
                if (trueCells.get(pieceRow * 100 + pieceCol).isEmpty()) {
                    trueCells.remove(pieceRow * 100 + pieceCol);
                }
            } else {
                trueCells.computeIfAbsent(pieceRow * 100 + pieceCol, k -> new ArrayList<>()).add(piece);
            }
        }
    }

    public ArrayList<Integer[]> findFirstTrueAdjacents() 
    {
        Integer[] firstTrueCell = findFirstTrueCell();
        ArrayList<Integer[]> trueAdjacents = findAdjacents(firstTrueCell[0], firstTrueCell[1]);

        if (trueAdjacents.size() == 0) 
        {
            return null;
        }

        return trueAdjacents;
    }

    public boolean after(Integer[] x, Integer[] y) // Returns true if x is after y and false otherwise
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

    public ArrayList<Integer[]> findFirstTrueAdjacentsAfter(int row, int col) 
    {
        Integer[] cell = {row, col};
        ArrayList<Integer[]> firstTrueAdjacents = findFirstTrueAdjacents();
        ArrayList<Integer[]> filteredAdjacents = new ArrayList<>();

        for (Integer[] adj : firstTrueAdjacents) 
        {
            // Compare if adjacent cell is after the clicked cell
            if (this.after(adj, cell))
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
        for (int i = 0; i <= 6; i++) 
        {
            if (i % 2 == 0) 
            {
                for (int j = 0; j <= 15; j++) 
                {
                    if (grid[i][j]) 
                    {
                        System.out.print("1 ");
                    } else 
                    {
                        System.out.print("0 ");
                    }
                }
            } else 
            {
                System.out.print(" ");
                for (int j = 0; j <= 14; j++) 
                {
                    if (grid[i][j]) 
                    {
                        System.out.print("1 ");
                    } else 
                    {
                        System.out.print("0 ");
                    }
                }
            }
            System.out.println();
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

    public Grid clone() {
        try {
            Grid newGrid = this.getClass().getDeclaredConstructor().newInstance();

            // Copy grid values
            for (int row = 0; row < NUM_ROWS; row++) {
                for (int col = 0; col < this.grid[row].length; col++) {
                    newGrid.grid[row][col] = this.grid[row][col];
                }
            }

            // Copy trueCells map
            for (Map.Entry<Integer, ArrayList<Integer[]>> entry : this.trueCells.entrySet()) {
                newGrid.trueCells.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            return newGrid;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone Grid", e);
        }
    }
}
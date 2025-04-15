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

    int trueCount = 0;
    
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

    private ArrayList<Integer[]> findTrueCells() {
        if (this.trueCount == 0)
        {
            return null;
        }
        boolean cycledThroughAllCells = false;
        ArrayList<Integer[]> trueCells = new ArrayList<Integer[]>();
        for (int row = 0; (row < grid.length) && (!cycledThroughAllCells); row++) 
        {
            for (int col = 0; (col < grid[row].length) && (!cycledThroughAllCells); col++) 
            {
                if (grid[row][col]) 
                {
                    trueCells.add(new Integer[] { row, col });
                    if (trueCells.size() == this.trueCount)
                    {
                        cycledThroughAllCells = true;
                        break;
                    }
                }
            }
        }

        return trueCells;
    }

    private Integer[] findFirstTrueCell()
    {
        if (this.trueCount == 0)
        {
            return null;
        }
        
        for (int row = 0; row < grid.length; row++) 
        {
            for (int col = 0; col < grid[row].length; col++) 
            {
                if (grid[row][col]) 
                {
                    Integer[] firstTrue = new Integer[] { row, col };
                    return firstTrue;
                }
            }
        }
        return null;
    }

    public void click(int row, int col) {
        ArrayList<Integer[]> affectedPieces = findAdjacents(row, col);

        for (Integer[] piece : affectedPieces) {
            int affectedRow = piece[0];
            int affectedCol = piece[1];
            if (grid[affectedRow][affectedCol]) 
            {
                trueCount--;
            } else {
                trueCount++;
            }

            grid[affectedRow][affectedCol] = !grid[affectedRow][affectedCol];
        }
    }

    public ArrayList<Integer[]> findFirstTrueAdjacents() 
    {
        Integer[] firstTrueCell = findFirstTrueCell();
        ArrayList<Integer[]> trueAdjacents = new ArrayList<>();

        ArrayList<Integer[]> adjacents = findAdjacents(firstTrueCell[0], firstTrueCell[1]);
        for (Integer[] adj : adjacents) 
        {
            trueAdjacents.add(adj);
        }

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

        if (trueCount == 0) 
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
        return trueCount;
    }
}
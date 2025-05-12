package com.github.mrgarbagegamer;

import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Grid {
    public static final int NUM_ROWS = 7;
    public static final int ODD_NUM_COLS = 15;
    public static final int EVEN_NUM_COLS = 16;

    // Grid state
    boolean[][] grid = new boolean[][] {
        new boolean[EVEN_NUM_COLS],
        new boolean[ODD_NUM_COLS],
        new boolean[EVEN_NUM_COLS],
        new boolean[ODD_NUM_COLS],
        new boolean[EVEN_NUM_COLS],
        new boolean[ODD_NUM_COLS],
        new boolean[EVEN_NUM_COLS]
    };

    // Track number of true cells
    protected int trueCount = 0;

    private static final java.util.Map<Integer, Set<int[]>> adjacencyMap = new java.util.HashMap<>();

    static {
        for (int row = 0; row < NUM_ROWS; row++) {
            for (int col = 0; col < (row % 2 == 0 ? EVEN_NUM_COLS : ODD_NUM_COLS); col++) {
                Set<int[]> adjacents = computeAdjacents(row, col);
                adjacencyMap.put(row * 100 + col, adjacents);
            }
        }
    }

    public static Set<int[]> computeAdjacents(int row, int col) {
        HashSet<int[]> affectedPieces = new HashSet<>();
        if (row % 2 == 0) {
            affectedPieces.add(new int[] { row - 1, col - 1 });
            affectedPieces.add(new int[] { row - 1, col });
            affectedPieces.add(new int[] { row, col - 1 });
            affectedPieces.add(new int[] { row, col + 1 });
            affectedPieces.add(new int[] { row + 1, col - 1 });
            affectedPieces.add(new int[] { row + 1, col });
        } else {
            affectedPieces.add(new int[] { row - 1, col });
            affectedPieces.add(new int[] { row - 1, col + 1 });
            affectedPieces.add(new int[] { row, col - 1 });
            affectedPieces.add(new int[] { row, col + 1 });
            affectedPieces.add(new int[] { row + 1, col });
            affectedPieces.add(new int[] { row + 1, col + 1 });
        }
        affectedPieces.removeIf(piece -> piece[0] < 0 || piece[0] >= NUM_ROWS || piece[1] < 0 || piece[1] >= ((piece[0] % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS));
        return affectedPieces;
    }

    public static Set<int[]> findAdjacents(int row, int col) {
        return adjacencyMap.get(row * 100 + col);
    }

    public Grid() {
        initialize();
    }

    abstract void initialize();

    void copyColumnValues(boolean[] source, boolean[] target) {
        System.arraycopy(source, 0, target, 0, source.length);
    }

    public Set<int[]> findTrueCells() {
        HashSet<int[]> trueCellsList = new HashSet<>();
        for (int row = 0; row < NUM_ROWS; row++) {
            int cols = (row % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS;
            for (int col = 0; col < cols; col++) {
                if (grid[row][col]) {
                    trueCellsList.add(new int[]{row, col});
                }
            }
        }
        return trueCellsList;
    }

    public int[] findFirstTrueCell() {
        for (int row = 0; row < NUM_ROWS; row++) {
            int cols = (row % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS;
            for (int col = 0; col < cols; col++) {
                if (grid[row][col]) {
                    return new int[]{row, col};
                }
            }
        }
        return null;
    }

    public void click(int row, int col) {
        Set<int[]> affectedPieces = findAdjacents(row, col);
        for (int[] piece : affectedPieces) {
            int pieceRow = piece[0];
            int pieceCol = piece[1];
            boolean wasTrue = grid[pieceRow][pieceCol];
            grid[pieceRow][pieceCol] = !wasTrue;
            if (wasTrue) {
                trueCount--;
            } else {
                trueCount++;
            }
        }
    }

    public Set<int[]> findFirstTrueAdjacents() {
        int[] firstTrueCell = findFirstTrueCell();
        if (firstTrueCell == null) return null;
        Set<int[]> trueAdjacents = findAdjacents(firstTrueCell[0], firstTrueCell[1]);
        if (trueAdjacents.isEmpty()) return null;
        return trueAdjacents;
    }

    public Set<int[]> findFirstTrueAdjacentsAfter(int row, int col) {
        int[] cell = {row, col};
        Set<int[]> firstTrueAdjacents = findFirstTrueAdjacents();
        Set<int[]> filteredAdjacents = new HashSet<>();
        if (firstTrueAdjacents == null) return null;
        for (int[] adj : firstTrueAdjacents) {
            if (adj[0] > cell[0] || (adj[0] == cell[0] && adj[1] > cell[1])) {
                filteredAdjacents.add(adj);
            }
        }
        return filteredAdjacents.isEmpty() ? null : filteredAdjacents;
    }

    public boolean isSolved() {
        return trueCount == 0;
    }

    public void printGrid() {
        Logger logger = LogManager.getLogger(Grid.class);
        for (int i = 0; i <= 6; i++) {
            StringBuilder row = new StringBuilder();
            if (i % 2 == 0) {
                for (int j = 0; j <= 15; j++) {
                    row.append(grid[i][j] ? "1 " : "0 ");
                }
            } else {
                row.append(" ");
                for (int j = 0; j <= 14; j++) {
                    row.append(grid[i][j] ? "1 " : "0 ");
                }
            }
            logger.info(row.toString());
        }
    }

    public boolean[][] getGrid() {
        return grid;
    }

    public int getTrueCount() {
        return trueCount;
    }

    public Grid clone() {
        try {
            Grid newGrid = this.getClass().getDeclaredConstructor().newInstance();
            for (int row = 0; row < NUM_ROWS; row++) {
                System.arraycopy(this.grid[row], 0, newGrid.grid[row], 0, this.grid[row].length);
            }
            newGrid.trueCount = this.trueCount;
            return newGrid;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone Grid", e);
        }
    }
}
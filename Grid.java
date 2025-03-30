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

    // Initializing the grid of seven rows with alernating columns of 16 and 15
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

    public Grid() {
        initialize();
    }

    abstract void initialize();

    void copyColumnValues(boolean[] source, boolean[] target) {
        for (int i = 0; i < source.length; i++) {
            target[i] = source[i];
        }
    }

    public void click(int row, int col) {
        int[][] affectedPieces = new int[6][2];

        if (row % 2 == 0) // even rows with 16 columns
        {
            // given a cell is (2, 7)

            affectedPieces[0][0] = row - 1;
            affectedPieces[0][1] = col - 1;
            // (row - 1, col - 1) (1, 6)

            affectedPieces[1][0] = row - 1;
            affectedPieces[1][1] = col;
            // (row - 1, col) (1, 7)

            affectedPieces[2][0] = row;
            affectedPieces[2][1] = col - 1;
            // (row, col - 1) (2, 6)

            affectedPieces[3][0] = row;
            affectedPieces[3][1] = col + 1;
            // (row, col + 1) (2, 8)

            affectedPieces[4][0] = row + 1;
            affectedPieces[4][1] = col - 1;
            // (row + 1, col - 1) (3, 6)

            affectedPieces[5][0] = row + 1;
            affectedPieces[5][1] = col;
            // (row + 1, col) (3, 7)

            // [[1, 6], [1, 7], [2, 6], [2, 8], [3, 6], [3, 7]]

        } else // odd rows with 15 columns
        {
            // given a cell is (3, 7)

            affectedPieces[0][0] = row - 1;
            affectedPieces[0][1] = col;
            // (row - 1, col) (2, 7)

            affectedPieces[1][0] = row - 1;
            affectedPieces[1][1] = col + 1;
            // (row - 1, col + 1) (2, 8)

            affectedPieces[2][0] = row;
            affectedPieces[2][1] = col - 1;
            // (row, col - 1) (3, 6)

            affectedPieces[3][0] = row;
            affectedPieces[3][1] = col + 1;
            // (row, col + 1) (3, 8)

            affectedPieces[4][0] = row + 1;
            affectedPieces[4][1] = col;
            // (row + 1, col) (4, 7)

            affectedPieces[5][0] = row + 1;
            affectedPieces[5][1] = col + 1;
            // (row + 1, col + 1) (4, 8)
        }

        for (int i = 0; i < affectedPieces.length; i++) {
            int affectedRow = affectedPieces[i][0];
            int affectedCol = affectedPieces[i][1];

            if ((affectedRow >= 0 && affectedRow < Grid.NUM_ROWS) && 
                (affectedCol >= 0 && affectedCol < grid[affectedRow].length)) {
                if (grid[affectedRow][affectedCol]) {
                    trueCount--;
                } else {
                    trueCount++;
                }

                grid[affectedRow][affectedCol] = !grid[affectedRow][affectedCol];
            }
        }
    }

    public boolean isSolved() {
        boolean isSolved = false;

        if (trueCount == 0)
        {
            isSolved = true;
        }

        return isSolved;
    }

    public void printGrid() {
        for (int i = 0; i <= 6; i++) {
            if (i % 2 == 0) {
                for (int j = 0; j <= 15; j++) {
                    if (grid[i][j]) {
                        System.out.print("1 ");
                    } else {
                        System.out.print("0 ");
                    }
                }
            } else {
                System.out.print(" ");
                for (int j = 0; j <= 14; j++) {
                    if (grid[i][j]) {
                        System.out.print("1 ");
                    } else {
                        System.out.print("0 ");
                    }
                }
            }
            System.out.println();
        }
    }

    public boolean[][] getGrid() {
        return grid;
    }

    public int getTrueCount() {
        return trueCount;
    }
}
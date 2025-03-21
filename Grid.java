public class Grid
{
    private boolean[][] grid = new boolean[][]{new boolean[16], new boolean[15], new boolean[16], new boolean[15], new boolean[16], new boolean[15], new boolean[16]};
    private int numRows = 6;
    private int numCols = 15;
    private int clickCount = 0;

    public Grid()
    {
        initialize();
    }

    public void initialize()
    {
        // Initialize for Q13/Kermit
        grid[0] = new boolean[16];
        grid[1] = new boolean[15];
        grid[2] = new boolean[16];
        grid[3] = new boolean[15];
        grid[4] = new boolean[16];
        grid[5] = new boolean[15];
        grid[6] = new boolean[16];

        // One step away from solution:
        grid[2][13] = true;
        grid[2][14] = true;
        grid[3][12] = true;
        grid[3][14] = true;
        grid[4][13] = true;
        grid[4][14] = true;

        // Two steps away from solution:
        /* grid[2][11] = true;
        grid[2][12] = true;
        grid[2][13] = true;
        grid[2][14] = true;
        grid[3][10] = true;
        grid[3][14] = true;
        grid[4][11] = true;
        grid[4][12] = true;
        grid[4][13] = true;
        grid[4][14] = true; */

        // Three steps away from solution:
        /* grid[2][9] = true;
        grid[2][10] = true;
        grid[2][11] = true;
        grid[2][12] = true;
        grid[2][13] = true;
        grid[2][14] = true;
        grid[3][8] = true;
        grid[3][14] = true;
        grid[4][9] = true;
        grid[4][10] = true;
        grid[4][11] = true;
        grid[4][12] = true;
        grid[4][13] = true;
        grid[4][14] = true; */

        // Four steps away from solution:
        /* grid[2][7] = true;
        grid[2][8] = true;
        grid[2][9] = true;
        grid[2][10] = true;
        grid[2][11] = true;
        grid[2][12] = true;
        grid[2][13] = true;
        grid[2][14] = true;
        grid[3][6] = true;
        grid[3][14] = true;
        grid[4][7] = true;
        grid[4][8] = true;
        grid[4][9] = true;
        grid[4][10] = true;
        grid[4][11] = true;
        grid[4][12] = true;
        grid[4][13] = true;
        grid[4][14] = true; */

        // Five steps away from solution:
        /* grid[2][5] = true;
        grid[2][6] = true;
        grid[2][7] = true;
        grid[2][8] = true;
        grid[2][9] = true;
        grid[2][10] = true;
        grid[2][11] = true;
        grid[2][12] = true;
        grid[2][13] = true;
        grid[2][14] = true;
        grid[3][4] = true;
        grid[3][14] = true;
        grid[4][5] = true;
        grid[4][6] = true;
        grid[4][7] = true;
        grid[4][8] = true;
        grid[4][9] = true;
        grid[4][10] = true;
        grid[4][11] = true;
        grid[4][12] = true;
        grid[4][13] = true;
        grid[4][14] = true; */

        // Six steps away from solution:
        /* grid[2][3] = true;
        grid[2][4] = true;
        grid[2][5] = true;
        grid[2][6] = true;
        grid[2][7] = true;
        grid[2][8] = true;
        grid[2][9] = true;
        grid[2][10] = true;
        grid[2][11] = true;
        grid[2][12] = true;
        grid[2][13] = true;
        grid[2][14] = true;
        grid[3][2] = true;
        grid[3][14] = true;
        grid[4][3] = true;
        grid[4][4] = true;
        grid[4][5] = true;
        grid[4][6] = true;
        grid[4][7] = true;
        grid[4][8] = true;
        grid[4][9] = true;
        grid[4][10] = true;
        grid[4][11] = true;
        grid[4][12] = true;
        grid[4][13] = true;
        grid[4][14] = true; */

        // Seven steps away from solution:
        /* grid[2][1] = true;
        grid[2][2] = true;
        grid[2][3] = true;
        grid[2][4] = true;
        grid[2][5] = true;
        grid[2][6] = true;
        grid[2][7] = true;
        grid[2][8] = true;
        grid[2][9] = true;
        grid[2][10] = true;
        grid[2][11] = true;
        grid[2][12] = true;
        grid[2][13] = true;
        grid[2][14] = true;
        grid[3][0] = true;
        grid[3][14] = true;
        grid[4][1] = true;
        grid[4][2] = true;
        grid[4][3] = true;
        grid[4][4] = true;
        grid[4][5] = true;
        grid[4][6] = true;
        grid[4][7] = true;
        grid[4][8] = true;
        grid[4][9] = true;
        grid[4][10] = true;
        grid[4][11] = true;
        grid[4][12] = true;
        grid[4][13] = true;
        grid[4][14] = true; */

        clickCount = 0;
    }

    public void click(int row, int col)
    {
        clickCount++;
        int[][] affectedPieces = new int[6][2];

        if (row % 2 == 0)
        {
            numCols = 16;
            affectedPieces[0][0] = row - 1;
            affectedPieces[0][1] = col - 1;
            affectedPieces[1][0] = row - 1;
            affectedPieces[1][1] = col;
            affectedPieces[2][0] = row;
            affectedPieces[2][1] = col - 1;
            affectedPieces[3][0] = row;
            affectedPieces[3][1] = col + 1;
            affectedPieces[4][0] = row + 1;
            affectedPieces[4][1] = col - 1;
            affectedPieces[5][0] = row + 1;
            affectedPieces[5][1] = col;
        }
        else
        {
            numCols = 15;
            affectedPieces[0][0] = row - 1;
            affectedPieces[0][1] = col;
            affectedPieces[1][0] = row - 1;
            affectedPieces[1][1] = col + 1;
            affectedPieces[2][0] = row;
            affectedPieces[2][1] = col - 1;
            affectedPieces[3][0] = row;
            affectedPieces[3][1] = col + 1;
            affectedPieces[4][0] = row + 1;
            affectedPieces[4][1] = col;
            affectedPieces[5][0] = row + 1;
            affectedPieces[5][1] = col + 1;
        }

        for (int i = 0; i < 6; i++)
        {
            int first = affectedPieces[i][0];
            int second = affectedPieces[i][1];

            if ((first >= 0 && first <= numRows) && (second >= 0 && second <= numCols))
            {
                grid[first][second] = !grid[first][second];
            }
        }
    }

    public boolean isSolved()
    {
        boolean solved = true;

        for (int i = 0; i <= 6; i += 2)
        {
            for (int j = 0; j <= 15; j++)
            {
                if (grid[i][j] == true)
                {
                    solved = false;
                }
            }
        }

        for (int i = 1; i <= 5; i += 2)
        {
            for (int j = 0; j <= 14; j++)
            {
                if (grid[i][j] == true)
                {
                    solved = false;
                }
            }
        }

        return solved;
    }

    public int getClickCount()
    {
        return clickCount;
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
                    }
                    else
                    {
                        System.out.print("0 ");
                    }
                }
            }
            else
            {
                System.out.print(" ");
                for (int j = 0; j <= 14; j++)
                {
                    if (grid[i][j])
                    {
                        System.out.print("1 ");
                    }
                    else
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
}
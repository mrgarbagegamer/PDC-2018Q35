public class Grid
{
    private boolean[][] grid = new boolean[][]{new boolean[16], new boolean[15], new boolean[16], new boolean[15], new boolean[16], new boolean[15], new boolean[16]};
    private int numRows = 6;
    private int numCols = 15;
    private int clickCount = 0;
    private int trueCount = 0;

    public Grid()
    {
        initialize();
    }

    public void initialize()
    {
        // Initialize for Q22/Shrek
        grid[0] = new boolean[16];
        grid[1] = new boolean[15];
        grid[2] = new boolean[16];
        grid[3] = new boolean[15];
        grid[4] = new boolean[16];
        grid[5] = new boolean[15];
        grid[6] = new boolean[16];

        for (int i = 0; i <= 6; i += 6)
        {
            grid[i][1] = true;
            grid[i][2] = true;
            grid[i][4] = true;
            grid[i][5] = true;
            grid[i][7] = true;
            grid[i][8] = true;
            grid[i][10] = true;
            grid[i][11] = true;
            grid[i][13] = true;
            grid[i][14] = true;
        }
        
        grid[0][1] = true;
        grid[0][2] = true;
        grid[0][4] = true;
        grid[0][5] = true;
        grid[0][7] = true;
        grid[0][8] = true;
        grid[0][10] = true;
        grid[0][11] = true;
        grid[0][13] = true;
        grid[0][14] = true;

        for (int i = 1; i <= 5; i += 2)
        {
            grid[i][0] = true;
            grid[i][2] = true;
            grid[i][3] = true;
            grid[i][5] = true;
            grid[i][6] = true;
            grid[i][8] = true;
            grid[i][9] = true;
            grid[i][11] = true;
            grid[i][12] = true;
            grid[i][14] = true;
        }
        
        trueCount = 50;
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
                if (grid[first][second])
                {
                    trueCount--;
                }
                else
                {
                    trueCount++;
                }
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

    public int getTrueCount()
    {
        return trueCount;
    }
}
package com.github.mrgarbagegamer;
public class Grid13 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q13/Kermit
        
        // reset the trueCells BitSet and set all cells to false
        for (int row = 0; row < Grid.NUM_ROWS; row++) 
        {
            System.arraycopy(row % 2 == 0 ? Grid.ZERO_ROW_EVEN : Grid.ZERO_ROW_ODD, 0, this.grid[row], 0, this.grid[row].length);
        }
        this.trueCells.clear();

        for (int row = 2; row <= 4; row += 2)
        {
            for (int col = 1; col <= 14; col++)
            {
                this.grid[row][col] = true;
                int cell = row * 100 + col;
                // Add the true cells to the trueCells BitSet
                this.trueCells.set(cell);
            }
        }

        this.grid[3][0] = true;
        this.trueCells.set(300);

        this.grid[3][14] = true;
        this.trueCells.set(314);

        firstTrueCell = 201; // Set the first true cell to 201 (row 2, col 1)
        trueCellsCount = 30;

        // this.click(3,1);
        // this.click(3,3);
        // this.click(3,5);
        // this.click(3,7);
        // this.click(3,9);
        // this.click(3,11);
        // this.click(3,13);
    }
}
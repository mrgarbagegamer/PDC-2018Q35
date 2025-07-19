package com.github.mrgarbagegamer;

public class Grid13 extends Grid 
{
    
    void initialize() 
    {
        // Initialize for Q13/Kermit
        
        // set all cells to their initial state
        gridState[0] = -6917317925703516160L;
        gridState[1] = 8191L;

        firstTrueCell = 32; // Set the first true cell to bit index 32 (row 2, col 1)
        trueCellsCount = 30;
        recalculationNeeded = false;

        // Initial clicks for Q13 (pre computed in index format to avoid recalculations)
        // this.click(48); // row 3, col 0
        // this.click(50); // row 3, col 2
        // this.click(52); // row 3, col 4
        // this.click(54); // row 3, col 6
        // this.click(56); // row 3, col 8
        // this.click(58); // row 3, col 10
        // this.click(60); // row 3, col 12
    }
}
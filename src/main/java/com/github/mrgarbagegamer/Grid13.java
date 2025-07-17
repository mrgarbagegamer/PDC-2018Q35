package com.github.mrgarbagegamer;

public class Grid13 extends Grid 
{
    
    void initialize() 
    {
        // Initialize for Q13/Kermit
        
        // set all cells to their initial state
        gridState[0] = -6917317925703516160L;
        gridState[1] = 8191L;

        firstTrueCell = 18; // Set the first true cell to bit index 18 (row 2, col 1)
        trueCellsCount = 30;
        recalculationNeeded = false;

        // this.click(3,1);
        // this.click(3,3);
        // this.click(3,5);
        // this.click(3,7);
        // this.click(3,9);
        // this.click(3,11);
        // this.click(3,13);
    }
}
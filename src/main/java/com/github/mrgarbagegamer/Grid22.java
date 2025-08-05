package com.github.mrgarbagegamer;

public class Grid22 extends Grid 
{   
    void initialize() 
    {
        // Initialize for Q22/Shrek

        // set all cells to their initial state
        gridState[0] = 3293960916490350006L;
        gridState[1] = 15078939901952L;

        firstTrueCell = 1; // Set the first true cell to bit index 1 (row 0, col 1)
        trueCellsCount = 50;
        recalculationNeeded = false;

        // Initial clicks for Q22 (pre computed in index format to avoid recalculations)
        // this.click((short)17); // row 1, col 1
        // this.click((short)20); // row 1, col 4
        // this.click((short)23); // row 1, col 7
        // this.click((short)26); // row 1, col 10
        // this.click((short)29); // row 1, col 13
        // this.click((short)48); // row 3, col 1
        // this.click((short)51); // row 3, col 4
        // this.click((short)54); // row 3, col 7
        // this.click((short)57); // row 3, col 10
        // this.click((short)60); // row 3, col 13
        // this.click((short)79); // row 5, col 1
        // this.click((short)82); // row 5, col 4
        // this.click((short)85); // row 5, col 7
        // this.click((short)88); // row 5, col 10
        // this.click((short)91); // row 5, col 13
    }
}
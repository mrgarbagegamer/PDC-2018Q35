package com.github.mrgarbagegamer;

import jdk.incubator.vector.LongVector;

public class Grid22 extends Grid
{
    // Static final array to avoid allocation on every initialize() call
    private static final long[] INITIAL_STATE = {3293960916490350006L, 15078939901952L};
    
    @Override
    void initialize()
    {
        // Initialize for Q22/Shrek

        // set all cells to their initial state
        gridState = LongVector.fromArray(SPECIES, INITIAL_STATE, 0);

        firstTrueCell = 1; // Set the first true cell to bit index 1 (row 0, col 1)
        trueCellsCount = 50;
        recalculationNeeded = false;

        // Initial clicks for Q22 (pre computed in index format to avoid recalculations)
        // this.click(17); // row 1, col 1
        // this.click(20); // row 1, col 4
        // this.click(23); // row 1, col 7
        // this.click(26); // row 1, col 10
        // this.click(29); // row 1, col 13
        // this.click(48); // row 3, col 1
        // this.click(51); // row 3, col 4
        // this.click(54); // row 3, col 7
        // this.click(57); // row 3, col 10
        // this.click(60); // row 3, col 13
        // this.click(79); // row 5, col 1
        // this.click(82); // row 5, col 4
        // this.click(85); // row 5, col 7
        // this.click(88); // row 5, col 10
        // this.click(91); // row 5, col 13
    }
}
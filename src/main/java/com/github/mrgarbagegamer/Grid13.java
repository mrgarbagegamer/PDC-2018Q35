package com.github.mrgarbagegamer;

import jdk.incubator.vector.LongVector;

public class Grid13 extends Grid
{
    // Static final array to avoid allocation on every initialize() call
    private static final long[] INITIAL_STATE = {-6917317925703516160L, 8191L};
    
    @Override
    void initialize()
    {
        // Initialize for Q13/Kermit
        
        // set all cells to their initial state
        gridState = LongVector.fromArray(SPECIES, INITIAL_STATE, 0);

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
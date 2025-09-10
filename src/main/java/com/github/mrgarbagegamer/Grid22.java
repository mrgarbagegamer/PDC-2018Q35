package com.github.mrgarbagegamer;

/**
 * Grid22 - [Configuration Purpose - e.g., "Q35 puzzle initial state"]
 * 
 * <p>[What this configuration represents and why it exists as a separate class.]</p>
 * 
 * <h2>Configuration Details</h2>
 * <p>[Specific configuration values and their meaning in the domain context.]</p>
 * 
 * <h2>Initialization Strategy</h2>
 * <p>[How values are computed/determined. Pre-computation rationale.]</p>
 * 
 * <h3>1/2 - 50% of documentation completed</h3>
 * 
 * @algorithm [If complex initialization logic is involved]
 * @since [When this configuration was introduced]
 * @see [Related configuration classes]
 */
public class Grid22 extends Grid 
{   
    /**
     * Initializes the grid to the specific configuration for Q22/Shrek.
     * 
     * <p>
     * This method sets the initial state of the grid, directly assigning pre-computed
     * values to the {@link #gridState} array. The proper values for {@link #firstTrueCell}
     * and {@link #trueCellsCount} are also set to reflect the initial configuration, and
     * the {@link #recalculationNeeded} flag is set to false, indicating that no further
     * recalculation is necessary at this point.
     * </p>
     * 
     * <p>
     * Commented out are the clicks that make up the solution to the Q22 puzzle, which
     * can be uncommented to test lower click counts. These clicks are pre-computed to
     * avoid unnecessary recalculations during initialization.
     * </p>
     * 
     * @since 2025.03.29 - Concrete Class Introduction
     * @see #getGridState()
     */
    void initialize() {
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
package com.github.mrgarbagegamer;

/**
 * Grid13 - [Configuration Purpose - e.g., "Q35 puzzle initial state"]
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
public class Grid13 extends Grid 
{
    /**
     * Initializes the grid to the specific configuration for Q13/Kermit.
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
     * Commented out are the clicks that make up the solution to the Q13 puzzle, which
     * can be uncommented to test lower click counts. These clicks are pre-computed to
     * avoid unnecessary recalculations during initialization.
     * </p>
     * 
     * @since 2025.04.15 - Adjacent Skipping Optimization
     * @see #getGridState()
     */
    void initialize() {
        // Initialize for Q13/Kermit
        
        // set all cells to their initial state
        gridState[0] = -6917317925703516160L;
        gridState[1] = 8191L;

        firstTrueCell = 32; // Set the first true cell to bit index 32 (row 2, col 1)
        trueCellsCount = 30;
        recalculationNeeded = false;

        // Initial clicks for Q13 (pre computed in index format to avoid recalculations)
        // this.click((short)48); // row 3, col 0
        // this.click((short)50); // row 3, col 2
        // this.click((short)52); // row 3, col 4
        // this.click((short)54); // row 3, col 6
        // this.click((short)56); // row 3, col 8
        // this.click((short)58); // row 3, col 10
        // this.click((short)60); // row 3, col 12
    }
}
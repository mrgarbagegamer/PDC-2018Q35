package com.github.mrgarbagegamer;

/**
 * A concrete {@link Grid} that provides the initial puzzle state for Q22 ("Shrek").
 *
 * <p>
 * This class defines the starting configuration for the 2018 PDC Q22 puzzle. Its sole purpose is to
 * load a pre-computed bitmask representing the initial layout of {@code true} cells into the
 * {@link #gridState} array.
 * </p>
 *
 * <h2>Architectural Role</h2>
 * <p>
 * As a concrete implementation of the abstract {@link Grid}, this class represents one of the
 * specific problems the solver is designed to tackle. The initial state is loaded via the
 * {@link #initialize()} method, which uses a hardcoded bitmask for maximum performance, avoiding
 * any computational overhead at runtime.
 * </p>
 *
 * <h2>Puzzle Details</h2>
 * <p>
 * The Q22 grid is initialized with 50 {@code true} cells. A known 15-click solution is included in
 * the source code, which can be uncommented for validation or testing of the solver's logic.
 * </p>
 *
 * @see Grid13
 * @see Grid35
 * @since 2025.03 - Concrete Class Introduction
 * @performance {@code O(1)} for initialization and most operations.
 * @threading This class is <b>not</b> thread-safe. Each thread should use its own instance of
 *            {@code Grid22} to avoid concurrency issues.
 * @memory No allocations after initialization (except for methods that explicitly create new
 *         objects).
 */
public class Grid22 extends Grid {
    /**
     * Loads the pre-computed state for the Q22 puzzle.
     *
     * <p>
     * This method directly assigns the bitmask representing the puzzle's initial state to the
     * {@link #gridState} array. It also sets cached values for the first {@code true} cell and the
     * total count of {@code true} cells for efficient processing.
     * </p>
     *
     * <p>
     * The commented-out clicks represent the known 15-click solution and can be used for testing or
     * validation purposes.
     * </p>
     *
     * @see #getGridState()
     * @since 2025.03 - Concrete Class Introduction
     * @performance {@code O(1)} - Direct assignment of pre-computed values.
     * @threading Not thread-safe; mutates instance state.
     * @memory Does not allocate.
     */
    public void initialize() {
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
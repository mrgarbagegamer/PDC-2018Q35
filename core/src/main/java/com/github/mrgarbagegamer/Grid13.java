package com.github.mrgarbagegamer;

// TODO: Update Javadocs
/**
 * A concrete {@link Grid} that provides the initial puzzle state for Q13 ("Kermit").
 *
 * <p>
 * This class defines the starting configuration for the 2018 PDC Q13 puzzle. Its sole purpose is to
 * load a pre-computed bitmask representing the initial layout of {@code true} cells into the
 * {@link #gridState} array.
 * </p>
 *
 * <h2>Architectural Role</h2>
 * <p>
 * As a concrete implementation of the {@code abstract} {@link Grid}, this class represents one of
 * the specific problems the solver is designed to tackle. The initial state is loaded via the
 * {@link #initialize()} method, which uses a hardcoded bitmask for maximum performance, avoiding
 * any computational overhead at runtime.
 * </p>
 *
 * <h2>Puzzle Details</h2>
 * <p>
 * The Q13 grid is initialized with 30 {@code true} cells. A known 7-click solution is included in
 * the source code, which can be uncommented for validation or testing of the solver's logic.
 * </p>
 *
 * @see Grid22
 * @see Grid35
 * @since 2025.04 - Adjacent Skipping Optimization
 * @performance {@code O(1)} for initialization and most operations.
 * @threading This class is <b>not</b> thread-safe. Each thread should use its own instance of
 *            {@code Grid13} to avoid concurrency issues.
 * @memory No allocations after initialization (except for methods that explicitly create new
 *         objects).
 */
public class Grid13 extends Grid {
    /**
     * Default constructor for {@code Grid13}.
     * 
     * @since 2026.01 - Copy Constructor Standardization
     * @performance {@code O(1)} initialization.
     * @threading Thread-safe; each instance is independent.
     * @memory Allocates a new {@code Grid13} instance.
     */
    public Grid13() {
        super(-6917317925703516160L, 8191L, 30, (short) 32);

        // Initial clicks for Q13 (pre computed in index format to avoid recalculations)
        // this.click((short)48); // row 3, col 0
        // this.click((short)50); // row 3, col 2
        // this.click((short)52); // row 3, col 4
        // this.click((short)54); // row 3, col 6
        // this.click((short)56); // row 3, col 8
        // this.click((short)58); // row 3, col 10
        // this.click((short)60); // row 3, col 12
    }

    /**
     * Copy constructor for {@code Grid13}. Since this constructor does not limit the type of the
     * {@code other} parameter to {@code Grid13}, it can be used to copy any subclass of
     * {@link Grid}.
     *
     * <p>
     * Initializes a new {@code Grid13} instance with the state of another {@code Grid} instance.
     * This constructor delegates to the {@link Grid#Grid(Grid) superclass copy constructor} to copy
     * the shared state.
     * </p>
     *
     * @param other The {@code Grid13} instance to copy.
     * @see #copy()
     * @see Grid#Grid(Grid)
     * @since 2026.01 - Copy Constructor Standardization
     * @performance {@code O(1)} delegation.
     * @threading Creates a new independent instance based on the state at the time of copying (not
     *            subject to concurrent modifications). The resulting instance is thread-safe, but
     *            the copying process itself is not.
     * @memory Allocates a new {@code Grid13} instance.
     */
    public Grid13(Grid other) { super(other); }

    @Override
    public Grid13 copy() { return new Grid13(this); }
}
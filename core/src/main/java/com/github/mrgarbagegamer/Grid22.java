package com.github.mrgarbagegamer;

// TODO: Update Javadocs
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
     * Default constructor for {@code Grid22}.
     * 
     * @since 2026.01 - Copy Constructor Standardization
     * @performance {@code O(1)} initialization.
     * @threading Thread-safe; each instance is independent.
     * @memory Allocates a new {@code Grid22} instance.
     */
    public Grid22() { super(3293960916490350006L, 15078939901952L, 50, (short) 1); }

    /**
     * Copy constructor for {@code Grid22}. Since this constructor does not limit the type of the
     * {@code other} parameter to {@code Grid22}, it can be used to copy any subclass of
     * {@link Grid}.
     * 
     * <p>
     * Initializes a new {@code Grid22} instance with the state of another {@code Grid} instance.
     * This constructor delegates to the {@link Grid#Grid(Grid) superclass copy constructor} to copy
     * the shared state.
     * </p>
     *
     * @param other The {@code Grid22} instance to copy.
     * @see #copy()
     * @see Grid#Grid(Grid)
     * @since 2026.01 - Copy Constructor Standardization
     * @performance {@code O(1)} delegation.
     * @threading Creates a new independent instance based on the state at the time of copying (not
     *            subject to concurrent modifications). The resulting instance is thread-safe, but
     *            the copying process itself is not.
     * @memory Allocates a new {@code Grid22} instance.
     */
    public Grid22(Grid other) { super(other); }

    @Override
    public Grid22 copy() { return new Grid22(this); }
}
package com.github.mrgarbagegamer;

import java.util.Arrays;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;

// TODO: Add documentation to the new methods and modify existing Javadoc accordingly
/**
 * A structure that represents the core hexagonal grid for a "Lights Out" style puzzle.
 *
 * <p>
 * This abstract class provides a high-performance, bitmask-based representation of a
 * {@value #NUM_CELLS}-cell hexagonal grid. It is designed for a specific variant of the puzzle
 * where clicking a cell toggles the state of its <strong>adjacent cells only</strong>, not the cell
 * itself. The primary goal is to turn off all the lights on the grid.
 * </p>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * As the foundational data structure, {@code Grid} defines the puzzle's state and core operations.
 * It is extended by concrete implementations such as {@link Grid13}, {@link Grid22}, and
 * {@link Grid35}, which provide specific initial puzzle configurations.
 * </p>
 *
 * <p>
 * {@link TestClickCombination Worker threads ("monkeys")} interact with cloned instances of this
 * class to test solutions. In contrast, {@link CombinationGeneratorTask generators} perform
 * lighter, more frequent checks and have limited direct interaction with this class. This
 * separation ensures that optimizations to this class primarily benefit the state-intensive work of
 * the monkeys.
 * </p>
 *
 * <h2>Performance Characteristics</h2>
 * <p>
 * The grid state is stored in a {@code long[2]} array, treated as a 128-bit bitmask to represent
 * the {@value #NUM_CELLS} cells. This approach minimizes memory footprint and allows for extremely
 * fast state manipulation using bitwise operations. Adjacency information is pre-computed into
 * {@link #ADJACENCY_MASKS}, enabling {@code O(1)} complexity for the critical {@code click}
 * operation.
 * </p>
 *
 * <p>
 * Several alternatives were evaluated:
 * </p>
 * <ul>
 * <li><b>{@link java.util.BitSet}:</b> Incurs unacceptable overhead from object headers and
 * indirect memory access compared to a primitive array.</li>
 * <li><b>Panama/Vector API:</b> Showed promise for 128-bit operations but suffered from excessive
 * memory allocation (4+ allocations per lanewise operation in tested JDK versions), making it
 * unsuitable for the hot path.</li>
 * </ul>
 * The primitive {@code long[]} array was ultimately chosen as the most performant solution on the
 * modern JVM, despite the complexity of managing two separate {@code long}s.
 *
 * <h2>Future Optimizations</h2>
 * <p>
 * Further performance gains are likely limited by the JVM itself. Potential avenues for exploration
 * include off-heap memory storage or the availability of true 128-bit primitives in a future Java
 * version (e.g., via Project Valhalla), which would simplify the bitmask logic to a single
 * {@code long}.
 * </p>
 *
 * <h2>Static Initialization</h2>
 * <p>
 * A key performance feature is the extensive use of a {@code static} initializer block. This block
 * runs only once when the class is loaded and pre-computes several critical data structures:
 * </p>
 * <ul>
 * <li>{@link #ADJACENCY_MASKS}: Bitmasks for every cell, allowing a {@code click} to be a simple
 * XOR operation.</li>
 * <li>{@link #adjacencyArray}: A legacy structure providing adjacent cell indices for algorithms
 * that require iteration.</li>
 * <li>{@link #ADJACENCY_CACHE}: A boolean matrix for {@code O(1)} adjacency checks between any two
 * cells.</li>
 * <li>{@link #PACKED_TO_INDEX_CACHE}: A lookup table for fast conversion from human-readable
 * {@link ValueFormat#PackedInt} to the internal {@link ValueFormat#Index}.</li>
 * </ul>
 * This pre-computation offloads complex calculations from the performance-critical runtime paths.
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <strong>not</strong> thread-safe. Each instance is designed to be used by a single
 * thread. State-modifying methods like {@link #click(short)} are unsynchronized to maximize
 * performance. Static members are effectively immutable after initialization and are safe to be
 * shared across threads.
 * </p>
 * 
 * @since 2025.03 - Initial Creation
 * @performance Critical operations like {@link #click(short)} and state checks are {@code O(1)} due
 *              to extensive pre-computation. The majority of computational complexity is handled
 *              once in a static initializer.
 * @threading Not thread-safe. Instances of {@code Grid} must not be shared between threads without
 *            external synchronization.
 * @algorithm Uses a bitmask ({@code long[2]}) to represent the grid state. Clicks are performed
 *            using pre-computed adjacency masks and bitwise XOR operations. Adjacency lookups and
 *            format conversions are accelerated by statically initialized caches.
 */
public abstract class Grid {
    /**
     * Defines the different formats used to represent a cell's location on the grid.
     *
     * <p>
     * To balance performance, memory usage, and readability, the solver uses multiple ways to
     * identify a cell. This {@code enum} provides a type-safe way to distinguish between them and
     * prevents the use of "magic numbers," improving maintainability.
     * </p>
     * 
     * @since 2025.07 - {@code ValueFormat} Enum Introduction
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as an immutable {@code enum}.
     * @memory Minimal memory overhead as a singleton per {@code enum} constant.
     */
    public enum ValueFormat {
        /**
         * A human-readable format where a cell's location is encoded as {@code (row * 100 + col)}.
         *
         * <p>
         * For example, the cell at row 3, column 5 is represented as {@code 305}. This format is
         * intuitive and simplifies adjacency arithmetic, but it is not as memory-efficient as
         * {@link #Index}. It is primarily used for debugging, configuration, and initial adjacency
         * calculations during static initialization. While cell identifiers are stored as
         * {@code short}s, the "PackedInt" name is retained for historical consistency.
         * </p>
         *
         * @see #indexToPacked(short)
         * @see #packedToIndex(short)
         * @since 2025.07 - {@code ValueFormat} Enum Introduction
         * @performance {@code O(1)} access time.
         * @threading Thread-safe as an immutable {@code enum}.
         * @memory Minimal memory overhead as a singleton per enum constant.
         */
        PackedInt,
        /**
         * A zero-based index from {@code 0} to {@code 108}, representing a cell's position in the
         * flattened grid.
         *
         * <p>
         * This is the primary format used in many performance-critical code paths, such as in
         * caches and generator tasks. It offers a compact and efficient way to iterate over cells
         * and is the native format for most internal data structures (along with {@link #Bitmask}).
         * However, it is less intuitive for humans to read compared to {@link #PackedInt}.
         * </p>
         *
         * @see #indexToPacked(short)
         * @see #packedToIndex(short)
         * @since 2025.07 - {@code ValueFormat} Enum Introduction
         * @performance {@code O(1)} access time.
         * @threading Thread-safe as an immutable {@code enum}.
         * @memory Minimal memory overhead as a singleton per enum constant.
         */
        Index,
        /**
         * A format representing the entire grid state as a bitmask. This format is not used to
         * identify individual cells but is included for completeness.
         *
         * <p>
         * The {@link #gridState grid state} is stored internally as a {@code long[2]} bitmask. This
         * {@code enum} value signifies operations or data related to this internal representation,
         * such as the {@link #click(long[])} method. Methods that accept or return a single cell
         * value will typically throw an {@link IllegalArgumentException} if this format is
         * specified.
         * </p>
         *
         * @see #gridState
         * @see #clearBit(int)
         * @see #click(long[])
         * @see #getBit(int)
         * @see #getGridState()
         * @see #setBit(int)
         * @since 2025.07 - {@code ValueFormat} Enum Introduction
         * @performance {@code O(1)} access time.
         * @threading Thread-safe as an immutable {@code enum}.
         * @memory Minimal memory overhead as a singleton per enum constant.
         */
        Bitmask
    }

    /**
     * The number of rows in the hexagonal grid.
     * 
     * @see #EVEN_NUM_COLS
     * @see #NUM_CELLS
     * @see #ODD_NUM_COLS
     * @see #ROW_OFFSETS
     * @since 2025.03 - Grid Definition
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    public static final int NUM_ROWS = 7;
    /**
     * The number of columns in the odd-indexed rows of the grid (rows 1, 3, 5).
     * 
     * @see #EVEN_NUM_COLS
     * @see #NUM_CELLS
     * @see #NUM_ROWS
     * @see #ROW_OFFSETS
     * @since 2025.03 - Grid Definition
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    public static final int ODD_NUM_COLS = 15;
    /**
     * The number of columns in the even-indexed rows of the grid (rows 0, 2, 4, 6).
     * 
     * @see #NUM_CELLS
     * @see #NUM_ROWS
     * @see #ODD_NUM_COLS
     * @see #ROW_OFFSETS
     * @since 2025.03 - Grid Definition
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    public static final int EVEN_NUM_COLS = 16;
    /**
     * Pre-computed offsets for the starting index of each row in the flattened grid.
     *
     * <p>
     * This array is used to accelerate the conversion from {@link ValueFormat#PackedInt} to
     * {@link ValueFormat#Index} by providing an {@code O(1)} lookup for the base index of any given
     * row. For example, {@code ROW_OFFSETS[2]} gives the index of the first cell in row 2.
     * </p>
     *
     * @see #EVEN_NUM_COLS
     * @see #NUM_ROWS
     * @see #ODD_NUM_COLS
     * @see #computePackedToIndex(short)
     * @see ValueFormat
     * @since 2025.06 - {@link java.util.BitSet BitSet} Grid State
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 14 bytes (7 shorts) as a {@code short[]}.
     */
    public static final short[] ROW_OFFSETS = {0, 16, 31, 47, 62, 78, 93}; // TODO: Replace with a
                                                                           // ShortImmutableList to
                                                                           // ensure immutability
    /**
     * The total number of cells in the grid.
     *
     * <p>
     * Calculated from the number of rows and their respective column counts: <code>(4 rows ×
     * {@value #EVEN_NUM_COLS} cols) + (3 rows × {@value #ODD_NUM_COLS} cols) =
     * {@value #NUM_CELLS}</code> total cells. This constant is used to define the size of arrays
     * and loop bounds throughout the class.
     * </p>
     * 
     * @see #gridState
     * @see #NUM_ROWS
     * @see #ROW_OFFSETS
     * @see #trueCellsCount
     * @since 2025.04 - Static Block Initialization
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    public static final int NUM_CELLS = 109;

    /**
     * The state of the grid, represented as a 109-bit {@link ValueFormat#Bitmask bitmask} split
     * across two {@code long} values.
     *
     * <p>
     * A {@code 1} at a given bit position indicates the cell is {@code true} while a {@code 0}
     * indicates it is {@code false} The first {@code long} ({@code gridState[0]}) stores the state
     * for cells 0-63, and the second ({@code gridState[1]}) stores the state for cells 64-108.
     * </p>
     *
     * <p>
     * This primitive array representation was chosen over {@link java.util.BitSet} and the Vector
     * API to eliminate object allocation overhead and ensure cache-friendliness in the hot path.
     * While Java's lack of a 128-bit primitive adds complexity, this approach provides the highest
     * performance on the modern JVM for the solver's workload.
     * </p>
     *
     * @see #clearBit(int)
     * @see #getBit(int)
     * @see #getGridState()
     * @see #setBit(int)
     * @see #setGridState(long, long)
     * @see #setGridState(long, long, int, short)
     * @see #toString()
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} accesses and modifications using bitwise operations.
     * @threading Not thread-safe. Instances of {@code Grid} must not be shared between threads
     *            without external synchronization.
     * @memory Fixed memory footprint of 16 bytes (2 {@code long}s) per instance.
     */
    private final long[] gridState; // TODO: Consider breaking into two separate longs for clarity
                                    // and optimization (saving an object header + indirection
                                    // costs)

    private final long initialState0;
    private final long initialState1;
    private final int initialTrueCellsCount;
    private final short initialFirstTrueCell;
    /**
     * A cached count of the number of "on" ({@code true}) cells in the grid.
     *
     * <p>
     * This value is updated lazily. It is only recalculated by methods like {@link #getTrueCount()}
     * or {@link #findFirstTrueCell(ValueFormat)} when the {@link #recalculationNeeded} flag is
     * {@code true}. This makes {@link #isSolved()} checks instantaneous in most cases.
     * </p>
     *
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} accesses and {@code O(1)} {@link #getTrueCount() recalculations}.
     * @threading Not thread-safe. Instances of {@code Grid} must not be shared between threads
     *            without external synchronization.
     * @memory Fixed memory footprint of 4 bytes as a primitive {@code int}.
     */
    private int trueCellsCount = 0;
    /**
     * A cached index of the first "on" ({@code true}) cell in the grid, in
     * {@link ValueFormat#Index} format.
     *
     * <p>
     * This value is critical for pruning the search space in the {@link CombinationGeneratorTask},
     * as any valid solution must interact with the first {@code true} cell. It is updated lazily
     * whenever {@link #findFirstTrueCell()} is called and {@link #recalculationNeeded} is
     * {@code true}. A value of {@code -1} indicates no cells are {@code true}, provided that
     * {@code recalculationNeeded} is {@code false}.
     * </p>
     *
     * @since 2025.07 - First True Cell Caching
     * @performance {@code O(1)} access and {@code O(1)} {@link #findFirstTrueCell()
     *              recalculations}.
     * @threading Not thread-safe. Instances of {@code Grid} must not be shared between threads
     *            without external synchronization.
     * @memory Fixed memory footprint of 2 bytes as a primitive {@code short}.
     */
    private short firstTrueCell = -1;
    /**
     * A dirty flag indicating that the {@link #gridState grid state} has been modified and cached
     * values are stale.
     *
     * <p>
     * This flag is set to {@code true} by any {@code click} operation. It signals to accessor
     * methods like {@link #getTrueCount()} and {@link #findFirstTrueCell()} that they must
     * recompute the {@link #trueCellsCount} and {@link #firstTrueCell} caches before returning a
     * value. It is reset to {@code false} after the caches are updated.
     * </p>
     * 
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} access.
     * @threading Not thread-safe. Instances of {@code Grid} must not be shared between threads
     *            without external synchronization.
     * @memory Fixed memory footprint of 1 byte as a primitive {@code boolean}.
     */
    private boolean recalculationNeeded = false;

    /**
     * Pre-computed {@link ValueFormat#Bitmask bitmasks} representing the result of clicking each
     * cell.
     *
     * <p>
     * Each entry {@code ADJACENCY_MASKS[i]} is a {@code long[2]} bitmask where the set bits
     * correspond to the cells adjacent to cell {@code i}. This is the core optimization that makes
     * the {@link #click(short[]) click} operation an {@code O(1)} bitwise XOR, as it eliminates the
     * need for runtime adjacency lookups.
     * </p>
     *
     * <p>
     * This array is populated once in a static initializer block when the class is loaded. Though a
     * click could only affect cells in one of the two {@code long}s, we store the full
     * {@code long[2]} for simplicity and to avoid conditional logic in the hot path.
     * </p>
     *
     * @see #NUM_CELLS
     * @since 2025.04 - Static Block Initialization
     * @performance {@code O(1)} access time.
     * @threading Thread-safe as a {@code static final} constant after class initialization.
     * @memory Fixed memory footprint of ~{@code NUM_CELLS × 2 × 8} bytes as a {@code long[][]}.
     */
    private static final long[][] ADJACENCY_MASKS = new long[NUM_CELLS][2];
    /**
     * A pre-computed table storing the adjacent cells for each cell as an array of indices.
     *
     * <p>
     * Each entry {@code adjacencyArray[i]} contains a {@code short[]} listing the neighbors of cell
     * {@code i} in {@link ValueFormat#Index} format. This structure provides {@code O(1)} lookups
     * for the {@link #findAdjacents(short, ValueFormat, ValueFormat)} method and serves as a legacy
     * alternative to the bitmask-based approach, used in parts of the code that require iterating
     * over neighbors.
     * </p>
     *
     * @see #NUM_CELLS
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.05 - Adjacency Storage Optimization
     * @performance {@code O(1)} lookup time.
     * @threading Thread-safe as a {@code static final} constant after class initialization.
     * @memory Fixed memory footprint as a {@code short[][]}.
     */
    private static final short[][] adjacencyArray = new short[NUM_CELLS][]; // Index format
    /**
     * A pre-computed boolean matrix for {@code O(1)} adjacency checks between any two cells.
     *
     * <p>
     * An entry {@code ADJACENCY_CACHE[i][j]} is {@code true} if cell {@code i} and cell {@code j}
     * are adjacent. This cache is used by {@link #areAdjacent(short, short)} to provide
     * near-instantaneous lookups. While not used in most hot paths, it is a useful utility for
     * specific algorithmic checks.
     * </p>
     *
     * @see #NUM_CELLS
     * @since 2025.06 - {@code O(1)} Adjacency Check
     * @performance {@code O(1)} lookup time.
     * @threading Thread-safe as a {@code static final} constant after class initialization.
     */
    private static final boolean[][] ADJACENCY_CACHE = new boolean[NUM_CELLS][NUM_CELLS]; // Index
                                                                                          // format
    /**
     * A pre-computed lookup table to accelerate {@link #packedToIndex(short) conversion} from
     * {@link ValueFormat#PackedInt} to {@link ValueFormat#Index}.
     *
     * <p>
     * The index of the array corresponds to a {@code PackedInt} value, and the element at that
     * index is the corresponding {@code Index} value. This provides an {@code O(1)} conversion,
     * avoiding the arithmetic typically required.
     * </p>
     *
     * @see #NUM_CELLS
     * @see #computePackedToIndex(short)
     * @see ValueFormat
     * @since 2025.06 - {@code PackedInt} to {@code Index} Precomputation
     * @performance {@code O(1)} lookup time.
     * @threading Thread-safe as a {@code static final} constant after class initialization.
     */
    private static final short[] PACKED_TO_INDEX_CACHE = new short[(NUM_ROWS - 1) * 100
            + EVEN_NUM_COLS];

    // We don't necessarily need to worry too much about how optimized this block
    // is, since it's only run once at startup.
    static {
        for (short cell = 0; cell < NUM_CELLS; cell++) {
            ShortList adjSet = computeAdjacents(cell, ValueFormat.Index, ValueFormat.Index);
            short[] adjArr = new short[adjSet.size()];
            int idx = 0;

            // Initialize bitmask for this cell
            long[] mask = new long[2];
            for (ShortIterator it = adjSet.iterator(); it.hasNext();) {
                short adjacent = it.nextShort();
                adjArr[idx++] = adjacent;

                // Fill legacy adjacency cache
                ADJACENCY_CACHE[cell][adjacent] = true;
                ADJACENCY_CACHE[adjacent][cell] = true;

                // Build bitmask for this adjacency
                int longIndex = adjacent / 64;
                int bitPosition = adjacent % 64;
                mask[longIndex] |= (1L << bitPosition);
            }

            adjacencyArray[cell] = adjArr;
            ADJACENCY_MASKS[cell] = mask;
            PACKED_TO_INDEX_CACHE[computePackedToIndex(cell)] = cell;
        }
    }

    /**
     * Computes the hexagonally adjacent cells for a given cell, supporting flexible input and
     * output formats.
     *
     * <p>
     * This method is primarily used during the {@code static} initialization block to build the
     * {@link #adjacencyArray} and {@link #ADJACENCY_MASKS}. It handles the complex adjacency rules
     * of a hexagonal grid, which vary based on a cell's row and column parity, unlike a simple
     * rectangular grid.
     * </p>
     *
     * <h3>Algorithm Details</h3>
     * <p>
     * Adjacency calculations are performed using the {@link ValueFormat#PackedInt} format
     * internally due to its straightforward arithmetic for determining relative positions. The
     * algorithm dynamically adds potential neighbors based on the cell's row parity (even or odd).
     * </p>
     * <ul>
     * <li><b>Even Rows:</b> Cells {@code (row-1, col-1)}, {@code (row-1, col)},
     * {@code (row, col-1)}, {@code (row, col+1)}, {@code (row+1, col-1)},
     * {@code (row+1, col)}.</li>
     * <li><b>Odd Rows:</b> Cells {@code (row-1, col)}, {@code (row-1, col+1)},
     * {@code (row, col-1)}, {@code (row, col+1)}, {@code (row+1, col)},
     * {@code (row+1, col+1)}.</li>
     * </ul>
     * <p>
     * After identifying potential neighbors, the method filters out any cells that fall outside the
     * grid boundaries.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * The method's complexity is {@code O(1)} because the number of potential neighbors is small
     * and constant (at most 6). It uses FastUtil's {@link ShortArrayList} to efficiently store
     * results without boxing overhead. While format conversions add minor overhead, this is
     * acceptable as the method's primary use case is during the one-time {@code static}
     * initialization.
     * </p>
     *
     * @param cell         The cell for which to find adjacents, in the specified
     *                     {@code inputFormat}.
     * @param inputFormat  The {@link ValueFormat} of the input {@code cell}.
     * @param outputFormat The {@link ValueFormat} in which the adjacent cells should be returned.
     * @return A {@link ShortList} of adjacent cells in the specified {@code outputFormat}.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is used for
     *                                  {@code inputFormat} or {@code outputFormat}, as it is not
     *                                  suitable for single-cell representation.
     * @throws NullPointerException     if {@code inputFormat} or {@code outputFormat} is
     *                                  {@code null}.
     * @see #findAdjacents(short, ValueFormat, ValueFormat)
     * @see ShortArrayList#ShortArrayList(int)
     * @see ShortList
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity due to constant number of potential neighbors.
     * @threading Thread-safe; does not modify instance state.
     * @memory Allocates a new {@code ShortArrayList} of capacity 6 for the result.
     */
    public static ShortList computeAdjacents(short cell, ValueFormat inputFormat,
            ValueFormat outputFormat) {
        ShortList affectedPieces = new ShortArrayList(6);

        // We need to handle different formats for adjacency
        switch (inputFormat) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        case Index:
            // Convert the cell to packed int format
            cell = indexToPacked((short) cell);
        case PackedInt:
            // If the cell is in packed int format, we can directly compute adjacents
            break;
        case null:
            throw new NullPointerException("Input format cannot be null.");
        }

        int row = cell / 100;

        if (row % 2 == 0) // even rows with 16 columns
        {
            affectedPieces.add((short) (cell - 101)); // (row - 1, col - 1)
            affectedPieces.add((short) (cell - 100)); // (row - 1, col)
            affectedPieces.add((short) (cell - 1)); // (row, col - 1)
            affectedPieces.add((short) (cell + 1)); // (row, col + 1)
            affectedPieces.add((short) (cell + 99)); // (row + 1, col - 1)
            affectedPieces.add((short) (cell + 100)); // (row + 1, col)
        } else // odd rows with 15 columns
        {
            affectedPieces.add((short) (cell - 100)); // (row - 1, col)
            affectedPieces.add((short) (cell - 99)); // (row - 1, col + 1)
            affectedPieces.add((short) (cell - 1)); // (row, col - 1)
            affectedPieces.add((short) (cell + 1)); // (row, col + 1)
            affectedPieces.add((short) (cell + 100)); // (row + 1, col)
            affectedPieces.add((short) (cell + 101)); // (row + 1, col + 1)
        }

        // Remove out-of-bounds cells
        affectedPieces.removeIf(key -> {
            int r = key / 100, c = key % 100;
            return r < 0 || r >= NUM_ROWS || c < 0
                    || c >= ((r % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS);
        });

        switch (outputFormat) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        case Index:
            // Convert packed int to index
            affectedPieces.replaceAll(Grid::packedToIndex);
            break;
        case PackedInt:
            // Already in packed int format, no conversion needed
            break;
        case null:
            throw new NullPointerException("Output format cannot be null.");
        }

        return affectedPieces;
    }

    /**
     * Convenience overload for {@link #computeAdjacents(short, ValueFormat, ValueFormat)} that
     * assumes the input and output formats are the same.
     *
     * @param cell   The cell for which to find adjacents.
     * @param format The {@link ValueFormat} for both the input cell and the output adjacent cells.
     * @return A {@link ShortList} of adjacent cells in the specified {@code format}.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is used.
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.07 - Format Support
     * @performance Delegates to the main implementation; {@code O(1)} complexity.
     * @threading Thread-safe; delegates to a
     *            {@link #computeAdjacents(short, ValueFormat, ValueFormat) thread-safe method}.
     * @memory Allocates a new {@code ShortArrayList} with a capacity of 6 for the result.
     */
    public static ShortList computeAdjacents(short cell, ValueFormat format) {
        return computeAdjacents(cell, format, format);
    }

    /**
     * Convenience overload for {@link #computeAdjacents(short, ValueFormat)} that assumes
     * {@link ValueFormat#Index} for both input and output.
     *
     * @param cell The cell for which to find adjacents, in {@link ValueFormat#Index} format.
     * @return A {@link ShortList} of adjacent cells in {@link ValueFormat#Index} format.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is implicitly used (though
     *                                  unlikely with this overload).
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.07 - Format Support
     * @performance Delegates to the main implementation; {@code O(1)} complexity.
     * @threading Thread-safe; delegates to a {@link #computeAdjacents(short, ValueFormat)
     *            thread-safe method}.
     * @memory Allocates a new {@link ShortArrayList} with a capacity of 6 for the result.
     */
    public static ShortList computeAdjacents(short cell) {
        return computeAdjacents(cell, ValueFormat.Index);
    }

    /**
     * Finds the hexagonally adjacent cells for a given cell, supporting flexible input and output
     * formats.
     *
     * <p>
     * This method retrieves {@link #computeAdjacents(short, ValueFormat, ValueFormat) pre-computed}
     * adjacency data from the {@link #adjacencyArray}. It handles format conversions as needed,
     * making it versatile for different use cases, such as debugging, logging, or specific
     * algorithmic checks.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * The core lookup from {@code adjacencyArray} is an {@code O(1)} operation. However, if the
     * {@code outputFormat} is not {@link ValueFormat#Index}, the method incurs additional overhead
     * for converting each adjacent cell's format. This makes the method's complexity {@code O(k)}
     * where {@code k} is the number of adjacent cells (at most 6). The presence of {@code switch}
     * statements also limits JIT inlining opportunities.
     * </p>
     * <p>
     * Though specialized caches for each format could solve these problems, since this method is
     * not on the critical performance path, the overhead of format conversion and branching is
     * deemed acceptable. For hot paths requiring adjacency information, direct access to
     * {@link #ADJACENCY_MASKS} or {@code adjacencyArray} in {@link ValueFormat#Index} is preferred.
     * </p>
     *
     * @param cell         The cell for which to find adjacents.
     * @param inputFormat  The {@link ValueFormat} of the input {@code cell}.
     * @param outputFormat The {@link ValueFormat} in which the adjacent cells should be returned.
     * @return A {@code short[]} of adjacent cells in the specified {@code outputFormat}.
     * @throws IllegalArgumentException       if {@link ValueFormat#Bitmask} is used for
     *                                        {@code inputFormat} or {@code outputFormat}.
     * @throws NullPointerException           if {@code inputFormat} or {@code outputFormat} is
     *                                        {@code null}.
     * @throws ArrayIndexOutOfBoundsException if the input {@code cell} is out of bounds for the
     *                                        specified {@code inputFormat}.
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} for {@link ValueFormat#Index} output, otherwise {@code O(k)} where
     *              {@code k} is the number of adjacent cells (max 6).
     * @threading Thread-safe; accesses immutable, pre-computed {@code static} data.
     * @memory Allocates a new {@code short[]} for the result only if format conversion is
     *         necessary.
     */
    public static short[] findAdjacents(short cell, ValueFormat inputFormat,
            ValueFormat outputFormat) {
        short[] result;
        switch (inputFormat) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        case PackedInt:
            // Convert packed int to index
            cell = packedToIndex(cell);
        case Index:
            // Already in index format, no conversion needed.
            result = adjacencyArray[cell];
            break;
        case null:
            throw new NullPointerException("Input format cannot be null.");
        }
        switch (outputFormat) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        case Index:
            // Already in index format, no conversion needed
            break;
        case PackedInt:
            // Convert index to packed int format
            short[] packedResult = new short[result.length];
            for (short i = 0; i < result.length; i++) {
                packedResult[i] = indexToPacked(result[i]);
            }
            return packedResult;
        case null:
            throw new NullPointerException("Output format cannot be null.");
        }

        return result;
    }

    /**
     * Convenience overload for {@link #findAdjacents(short, ValueFormat, ValueFormat)} that assumes
     * the input and output formats are the same.
     *
     * @param cell   The cell for which to find adjacents.
     * @param format The {@link ValueFormat} for both the input cell and the output adjacent cells.
     * @return A {@code short[]} of adjacent cells in the specified {@code format}.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is used.
     * @see #findAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.07 - Format Support
     * @performance Delegates to the main implementation; {@code O(k)} complexity where {@code k} is
     *              the number of adjacent cells.
     * @threading Thread-safe; delegates to a {@link #findAdjacents(short, ValueFormat, ValueFormat)
     *            thread-safe method}.
     * @memory Allocates a new {@code short[]} for the result only if format conversion is
     *         necessary.
     */
    public static short[] findAdjacents(short cell, ValueFormat format) {
        return findAdjacents(cell, format, format);
    }

    /**
     * Convenience overload for {@link #findAdjacents(short, ValueFormat)} that assumes
     * {@link ValueFormat#Index} for both input and output.
     *
     * @param cell The cell for which to find adjacents, in {@link ValueFormat#Index} format.
     * @return A {@code short[]} of adjacent cells in {@link ValueFormat#Index} format.
     * @see #findAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity as it directly returns a reference to a pre-computed
     *              array.
     * @threading Thread-safe; delegates to a {@link #findAdjacents(short, ValueFormat) thread-safe
     *            method}.
     * @memory Does not allocate; returns a reference to an existing {@code short[]}.
     */
    public static short[] findAdjacents(short cell) {
        return findAdjacents(cell, ValueFormat.Index);
    }

    /**
     * Computes the {@link #packedToIndex(short) conversion} from {@link ValueFormat#PackedInt} to
     * {@link ValueFormat#Index} format.
     *
     * <p>
     * This method performs the necessary arithmetic to determine the correct {@code Index} for a
     * given {@code PackedInt} value, taking into account the varying row lengths of the hexagonal
     * grid. It extracts the row and column from the {@code PackedInt} and uses the
     * {@link #ROW_OFFSETS} array to calculate the flattened index.
     * </p>
     *
     * <p>
     * This method is primarily used during {@code static} initialization to populate the
     * {@link #PACKED_TO_INDEX_CACHE}. It does not perform bounds checking, assuming valid input
     * during its intended use.
     * </p>
     *
     * @param packed The cell in {@link ValueFormat#PackedInt} format.
     * @return The cell in {@link ValueFormat#Index} format.
     * @since 2025.06 - {@link ValueFormat#PackedInt} to {@link ValueFormat#Index} Precomputation
     * @performance {@code O(1)} complexity due to direct arithmetic and a single array lookup.
     * @threading Thread-safe; does not modify instance state.
     * @memory Does not allocate.
     */
    private static short computePackedToIndex(short packed) {
        short row = (short) (packed / 100);
        short col = (short) (packed % 100);
        return (short) (ROW_OFFSETS[row] + col);
    }

    /**
     * Converts a cell from {@link ValueFormat#PackedInt} to {@link ValueFormat#Index} format using
     * a pre-computed cache.
     *
     * <p>
     * This method leverages the {@link #PACKED_TO_INDEX_CACHE} for {@code O(1)} lookups. If a value
     * is not found in the cache (which should only happen for {@code packed = 0} during initial
     * {@code static} setup), it is computed on-the-fly using {@link #computePackedToIndex(short)}.
     * </p>
     *
     * <h3>Optimization Rationale</h3>
     * <p>
     * The cache is partially populated during {@code static} initialization to avoid a full
     * pre-computation of all possible {@code PackedInt} values, which would increase startup time
     * for a conversion that is not frequently used in performance-critical paths. A TODO comment
     * indicates a potential future optimization to fully pre-compute the cache, which would further
     * reduce method size and improve JIT inlining chances.
     * </p>
     *
     * @param packed The cell in {@link ValueFormat#PackedInt} format.
     * @return The cell in {@link ValueFormat#Index} format.
     * @throws IllegalArgumentException if the input {@code packed} value is out of bounds.
     * @see #PACKED_TO_INDEX_CACHE
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} lookup.
     * @threading Thread-safe; accesses immutable, pre-computed {@code static} data.
     * @memory Does not allocate.
     */
    public final static short packedToIndex(short packed) {
        if (packed >= 0 && packed < PACKED_TO_INDEX_CACHE.length) {
            // TODO: Explicitly pre-compute the entire cache in the static block to avoid on-the-fly
            // calculations.
            if (PACKED_TO_INDEX_CACHE[packed] == 0 && packed != 0) {
                // If the cache is not initialized, compute it
                PACKED_TO_INDEX_CACHE[packed] = computePackedToIndex(packed);
            }
            return PACKED_TO_INDEX_CACHE[packed];
        }
        throw new IllegalArgumentException("Invalid packed int: " + packed);
    }

    /**
     * Converts a cell from {@link ValueFormat#Index} to {@link ValueFormat#PackedInt} format.
     *
     * <p>
     * This method uses a series of conditional checks to determine the correct row and column for
     * the given {@code Index}, leveraging the grid's alternating row lengths. It constructs the
     * {@code PackedInt} value by combining the calculated row and column.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is {@code O(1)} in complexity due to the fixed number of comparisons. It is
     * declared {@code final} to encourage JIT inlining, as it is a pure function with no side
     * effects. While a cache could be implemented (similar to {@link #packedToIndex(short)}), it is
     * not deemed necessary given that this conversion is not frequently called in
     * performance-critical paths.
     * </p>
     *
     * @param index The cell in {@link ValueFormat#Index} format.
     * @return The cell in {@link ValueFormat#PackedInt} format.
     * @throws IllegalArgumentException if the input {@code index} is out of bounds (0-108).
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} comparisons and computation.
     * @threading Thread-safe; does not modify instance state.
     * @memory Does not allocate.
     */
    public final static short indexToPacked(short index) {
        if (index < 0 || index >= NUM_CELLS) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }

        if (index < 16)
            return (short) (0 * 100 + index);
        if (index < 31)
            return (short) (1 * 100 + (index - 16));
        if (index < 47)
            return (short) (2 * 100 + (index - 31));
        if (index < 62)
            return (short) (3 * 100 + (index - 47));
        if (index < 78)
            return (short) (4 * 100 + (index - 62));
        if (index < 93)
            return (short) (5 * 100 + (index - 78));
        else
            return (short) (6 * 100 + (index - 93));
    }

    /**
     * Constructs a new {@code Grid} instance and initializes it to a specific starting state. We
     * initialize the {@link #gridState grid state} here rather than at declaration to allow the
     * {@link #Grid(Grid) copy constructor} to create deep copies efficiently, avoiding the need for
     * a separate cloning method.
     *
     * <p>
     * As {@code Grid} is an {@code abstract} class, this constructor is implicitly called by
     * concrete subclass constructors (e.g., {@link Grid13}, {@link Grid22}, {@link Grid35}). It
     * ensures that the grid is properly set up by invoking the abstract {@link #initialize()}
     * method, which subclasses must implement to define their unique initial puzzle configurations.
     * </p>
     *
     * @see #initialize()
     * @since 2025.03 - Abstract {@code Grid} Introduction
     * @performance {@code O(1)} complexity for construction, plus the complexity of
     *              {@link #initialize()}.
     * @threading Thread-safe; each instance is independent.
     * @memory Allocates a new {@code Grid} instance and a new {@code long[2]} array for
     *         {@link #gridState}.
     */
    protected Grid(long initialState0, long initialState1) {
        this.initialState0 = initialState0;
        this.initialState1 = initialState1;
        this.gridState = new long[] {initialState0, initialState1};
        this.initialFirstTrueCell = findFirstTrueCell();
        this.initialTrueCellsCount = getTrueCount();
    }

    protected Grid(long initialState0, long initialState1, int initialTrueCellsCount,
            short initialFirstTrueCell) {
        this.initialState0 = initialState0;
        this.initialState1 = initialState1;
        this.gridState = new long[] {initialState0, initialState1};
        this.initialFirstTrueCell = initialFirstTrueCell;
        this.initialTrueCellsCount = initialTrueCellsCount;
        this.firstTrueCell = initialFirstTrueCell;
        this.trueCellsCount = initialTrueCellsCount;
    }

    /**
     * Constructs a new {@code Grid} instance as a deep copy of another {@code Grid}. This copy
     * constructor duplicates the internal state, ensuring that modifications to the new instance do
     * not affect the original. As an {@code abstract} class, {@code Grid} cannot be instantiated,
     * so this constructor is meant to be called by concrete subclass copy constructors.
     * 
     * <p>
     * While a {@link Object#clone() clone()} method could be implemented, it would introduce
     * complexity with subclassing and type safety. Standard cloning convention requires acquiring
     * an instance of the exact runtime type by calling {@code super.clone()}, but doing so would
     * cause the resulting {@link #gridState} to reference the same array as the original, leading
     * to shared mutable state. Fields declared as {@code final} cannot be mutated within a
     * {@code clone()} method, so the reference cannot be corrected before returning.
     * </p>
     * 
     * <p>
     * Just calling {@link #Grid() new Grid()} to create a new instance would not work either, as
     * {@code Grid} is {@code abstract} and cannot be instantiated directly. The only other
     * alternatives involve using reflection (which is inefficient and still not type-safe) or
     * requiring each subclass to implement its own {@code clone()} method (violating the DRY
     * principle and risking inconsistent behavior).
     * </p>
     * 
     * <p>
     * Our original design used the reflective approach, requiring a
     * {@code Grid newGrid = this.getClass().getDeclaredConstructor().newInstance()} call. However,
     * this was inefficient and complicated error handling. A copy constructor like this carries all
     * of the benefits of cloning without the downsides, providing a clear and efficient way to
     * duplicate {@code Grid} instances.
     * </p>
     * 
     * @param other The {@code Grid} instance to copy.
     * @see #copy()
     * @see Cloneable
     * @since 2026.01 - Copy Constructor for {@code Grid} Cloning
     * @performance {@code O(1)} copying of primitive fields and {@code O(n)} cloning of the
     *              {@code gridState}.
     * @threading Creates a new independent instance based on the state at the time of copying (not
     *            subject to concurrent modifications). The resulting instance is thread-safe, but
     *            the copying process itself is not.
     * @memory Allocates a new {@code Grid} instance and a new {@code long[2]} array for
     *         {@link #gridState}.
     */
    protected Grid(Grid other) {
        this.gridState = other.gridState.clone();
        this.initialState0 = other.initialState0;
        this.initialState1 = other.initialState1;
        this.initialFirstTrueCell = other.initialFirstTrueCell;
        this.initialTrueCellsCount = other.initialTrueCellsCount;
        this.trueCellsCount = other.trueCellsCount;
        this.firstTrueCell = other.firstTrueCell;
        this.recalculationNeeded = other.recalculationNeeded;
    }

    /**
     * Creates and returns a deep copy of this {@code Grid} instance.
     *
     * <p>
     * This abstract method forces concrete subclasses to implement their own copying logic,
     * typically by invoking a copy constructor. This approach avoids the pitfalls of
     * {@link Cloneable} and reflection, ensuring type safety and proper state duplication.
     * </p>
     *
     * @return A deep copy of this {@code Grid} instance.
     * @see #Grid(Grid)
     * @since 2026.01 - Copy Method Standardization
     * @performance {@code O(1)} complexity.
     * @threading Creates a new independent instance based on the state at the time of copying (not
     *            subject to concurrent modifications). The resulting instance is thread-safe, but
     *            the copying process itself is not.
     * @memory Allocates a new {@code Grid} instance.
     */
    public abstract Grid copy();

    /**
     * Initializes the {@code Grid} instance to a specific starting state.
     *
     * <p>
     * This {@code abstract} method must be implemented by concrete subclasses to define their
     * unique initial puzzle configurations. It is called by the {@link #Grid() constructor} during
     * instance creation, ensuring the {@link #gridState grid state} is properly set up from the
     * outset.
     * </p>
     *
     * @see Grid13
     * @see Grid22
     * @see Grid35
     * @since 2025.03 - Abstract {@code Grid} Introduction
     * @performance Implementation-dependent.
     * @threading Not thread-safe; this method modifies the instance's {@link #gridState}.
     */
    public void initialize() {
        setGridState(initialState0, initialState1, initialTrueCellsCount, initialFirstTrueCell);
    }

    /**
     * Sets the bit at the specified {@link ValueFormat#Index index} in the {@link #gridState grid
     * state} to {@code true}.
     *
     * <p>
     * This is a low-level helper method primarily used during grid initialization. It updates the
     * {@link #gridState bitmask} and increments {@link #trueCellsCount} if the bit was previously
     * {@code false}. For performance-critical click operations, {@link #click(short[])} is used
     * instead, which leverages pre-computed {@link #ADJACENCY_MASKS}.
     * </p>
     *
     * @param index The {@link ValueFormat#Index index} of the bit to set (0-108).
     * @throws IndexOutOfBoundsException (Implicitly) if the index is out of bounds for the
     *                                   {@code long[]} array.
     * @see #clearBit(int)
     * @see #getBit(int)
     * @since 2025.07 - Bitmasked {@code Grid} State
     * @performance {@code O(1)} bitwise operation.
     * @threading Not thread-safe; modifies {@link #gridState} and {@link #trueCellsCount}.
     * @memory Does not allocate.
     */
    protected void setBit(int index) {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        if ((gridState[longIndex] & (1L << bitPosition)) == 0) {
            gridState[longIndex] |= (1L << bitPosition);
            trueCellsCount++;
        }
    }

    /**
     * Clears the bit at the specified {@link ValueFormat#Index index} in the {@link #gridState grid
     * state} to {@code false}.
     *
     * <p>
     * This is a low-level helper method, analogous to {@link #setBit(int)}. It updates the
     * {@link #gridState bitmask} and decrements {@link #trueCellsCount} if the bit was previously
     * {@code true}. It is not used in performance-critical paths, which prefer
     * {@link #click(short[])}.
     * </p>
     *
     * @param index The {@link ValueFormat#Index index} of the bit to clear (0-108).
     * @throws IndexOutOfBoundsException (Implicitly) if the index is out of bounds for the
     *                                   {@code long[]} array.
     * @see #getBit(int)
     * @since 2025.07 - Bitmasked {@code Grid} State
     * @performance {@code O(1)} bitwise operation.
     * @threading Not thread-safe; modifies {@link #gridState} and {@link #trueCellsCount}.
     * @memory Does not allocate.
     */
    protected void clearBit(int index) {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        if ((gridState[longIndex] & (1L << bitPosition)) != 0) {
            gridState[longIndex] &= ~(1L << bitPosition);
            trueCellsCount--;
        }
    }

    /**
     * Checks the state of the bit at the specified {@link ValueFormat#Index index} in the
     * {@link #gridState grid state}.
     *
     * <p>
     * This method is used to determine if a specific cell is {@code true} (on) or {@code false}
     * (off). It is utilized internally for operations like {@link #toString()} or when iterating to
     * find {@code true} cells.
     * </p>
     *
     * @param index The {@link ValueFormat#Index index} of the bit to check (0-108).
     * @return {@code true} if the bit is set (cell is "on"), {@code false} otherwise.
     * @throws IndexOutOfBoundsException (Implicitly) if the index is out of bounds for the
     *                                   {@code long[]} array.
     * @see #clearBit(int)
     * @see #findTrueCells()
     * @see #findTrueCells(ValueFormat)
     * @see #setBit(int)
     * @since 2025.07 - Bitmasked {@code Grid} State
     * @performance {@code O(1)} bitwise operation.
     * @threading Not thread-safe; reads potentially mutable {@link #gridState}.
     * @memory Does not allocate.
     */
    protected boolean getBit(int index) {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        return (gridState[longIndex] & (1L << bitPosition)) != 0;
    }

    /**
     * Sets the entire {@link #gridState grid bitmask} to the specified values. This method can be
     * used by subclasses to initialize the grid to a specific state, bypassing individual bit
     * manipulations and ensuring the consistency of related fields.
     * 
     * <p>
     * This overload allows setting the grid state along with the {@link #trueCellsCount} and
     * {@link #firstTrueCell} in one operation, useful when the complete state is known upfront.
     * </p>
     * 
     * @param state0         The first {@code long} representing bits 0-63 of the grid.
     * @param state1         The second {@code long} representing bits 64-108 of the grid.
     * @param trueCellsCount The total number of {@code true} cells in the grid.
     * @param firstTrueCell  The index of the first {@code true} cell in the grid.
     * @see #setGridState(long, long)
     * @since 2026.01 - Grid Encapsulation Improvements
     * @performance {@code O(1)} assignment operations.
     * @threading Not thread-safe; modifies instance state.
     * @memory Does not allocate.
     */
    protected final void setGridState(long state0, long state1, int trueCellsCount,
            short firstTrueCell) {
        this.gridState[0] = state0;
        this.gridState[1] = state1;
        this.trueCellsCount = trueCellsCount;
        this.firstTrueCell = firstTrueCell;
        this.recalculationNeeded = false; // State is now consistent
    }

    /**
     * Sets the entire {@link #gridState grid bitmask} to the specified values. This method can be
     * used by subclasses to initialize the grid to a specific state, bypassing individual bit
     * manipulations.
     * 
     * <p>
     * This overload only sets the {@link #gridState} and marks the grid as needing recalculation of
     * {@link #trueCellsCount} and {@link #firstTrueCell}. It is useful when one knows the state
     * they want to set, but doesn't have the derived values readily available.
     * </p>
     * 
     * @param state0 The first {@code long} representing bits 0-63 of the grid.
     * @param state1 The second {@code long} representing bits 64-108 of the grid.
     * @see #setGridState(long, long, int, short)
     * @since 2026.01 - Grid Encapsulation Improvements
     * @performance {@code O(1)} assignment operations.
     * @threading Not thread-safe; modifies instance state.
     * @memory Does not allocate.
     */
    protected final void setGridState(long state0, long state1) {
        this.gridState[0] = state0;
        this.gridState[1] = state1;
        this.recalculationNeeded = true; // Mark for recalculation
    }

    /**
     * Scans the {@link #gridState grid bitmask} and returns an array of all {@code true} cells in
     * the requested {@link ValueFormat format}.
     *
     * <p>
     * This method is used by {@link CombinationGeneratorTask generators} and
     * {@link TestClickCombination monkeys} to extract the current state of the grid for pruning or
     * processing. It iterates through the grid's bits and collects the indices of all set bits.
     * </p>
     *
     * <h3>Algorithm Details</h3>
     * <p>
     * The method iterates from {@code 0} to {@value #NUM_CELLS}-1, checking each bit's state using
     * {@link #getBit(int)}. It populates a {@code short[]} which is pre-sized based on
     * {@link #trueCellsCount} to avoid dynamic resizing. The iteration stops early once all
     * expected {@code true} cells are found.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method has a worst-case complexity of {@code O(NUM_CELLS)}. While a more optimized
     * approach could use {@link Long#numberOfTrailingZeros(long)} to find set bits directly, this
     * method is not on a critical performance path (as it's mainly used for initial state
     * extraction or debugging) and the current implementation is clear and sufficient. The
     * {@link #trueCellsCount} is assumed to be accurately maintained by {@link #setBit(int)} and
     * {@link #clearBit(int)}.
     * </p>
     *
     * @param format The desired output format ({@link ValueFormat#Index} or
     *               {@link ValueFormat#PackedInt}).
     * @return A {@code short[]} of {@code true} cells in the specified format.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is provided, as it is not
     *                                  suitable for representing individual cells.
     * @throws NullPointerException     if {@code format} is {@code null}.
     * @see #findFirstTrueCell(ValueFormat)
     * @see #getGridState()
     * @since 2025.07 - Format Support
     * @performance {@code O(NUM_CELLS)} in the worst case.
     * @threading Not thread-safe; reads the mutable {@link #gridState}.
     * @memory Allocates a new {@code short[]} for the result.
     */
    public short[] findTrueCells(ValueFormat format) {
        short[] trueCellsArray = new short[getTrueCount()];
        int idx = 0;

        // Internally, we iterate over bit indices (0-108)
        for (short i = 0; i < NUM_CELLS && idx < trueCellsCount; i++) {
            if (getBit(i))
                trueCellsArray[idx++] = i;
        }

        switch (format) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing true cells (since that's just the Grid).");
        case Index:
            // Already in index format, no conversion needed
            break;
        case PackedInt:
            // Convert index to packed int format
            for (int i = 0; i < trueCellsCount; i++) {
                trueCellsArray[i] = (short) indexToPacked(trueCellsArray[i]);
            }
            break;
        case null:
            throw new NullPointerException("Format cannot be null.");
        }

        return trueCellsArray;
    }

    /**
     * Scans the {@link #gridState grid bitmask} and returns an array of all {@code true} cells in
     * {@link ValueFormat#Index Index} format.
     *
     * <p>
     * This is a convenience overload of {@link #findTrueCells(ValueFormat)} that defaults to the
     * {@link ValueFormat#Index} format, providing a simpler API for the most common use case.
     * </p>
     *
     * @return A {@code short[]} of {@code true} cells in {@link ValueFormat#Index} format.
     * @see #findTrueCells(ValueFormat)
     * @see #getTrueCount()
     * @since 2025.04 - Adjacency Optimizations
     * @performance {@code O(NUM_CELLS)} in the worst case.
     * @threading Not thread-safe; reads mutable state.
     * @memory Allocates a new {@code short[]} for the result.
     */
    public final short[] findTrueCells() {
        short[] trueCellsArray = new short[getTrueCount()];
        int idx = 0;

        // Internally, we iterate over bit indices (0-108)
        for (short i = 0; i < NUM_CELLS && idx < trueCellsCount; i++) {
            if (getBit(i))
                trueCellsArray[idx++] = i;
        }

        return trueCellsArray;
    }

    /**
     * Scans the {@link #gridState grid} and returns the first {@code true} cell in the requested
     * {@link ValueFormat format}.
     *
     * <p>
     * This method is crucial for optimizing the solution search. A core property of the puzzle is
     * that any valid solution must interact with the first {@code true} cell in the initial grid
     * state. This method provides a fast way to identify that cell, enabling early pruning of
     * invalid combinations.
     * </p>
     *
     * <h3>Algorithm Details</h3>
     * <p>
     * The method employs highly optimized {@link Long#numberOfTrailingZeros(long)} and
     * {@link Long#bitCount(long)} intrinsics to efficiently determine the first {@code true} bit
     * and the total count of {@code true} bits in the {@code long[2]} {@link #gridState}.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method implements a lazy recalculation strategy for {@link #firstTrueCell} and
     * {@link #trueCellsCount}. These values are only recomputed if the {@link #recalculationNeeded}
     * flag is {@code true}, ensuring {@code O(1)} performance in most cases. If no {@code true}
     * cells exist, it short-circuits to return -1 immediately.
     * </p>
     *
     * @param format The desired output format ({@link ValueFormat#Index} or
     *               {@link ValueFormat#PackedInt}).
     * @return The first {@code true} cell in the specified format, or -1 if no {@code true} cell is
     *         found.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is used, as it is not
     *                                  suitable for single-cell representation.
     * @throws NullPointerException     if {@code format} is {@code null}.
     * @see #click(short[])
     * @see #findTrueCells(ValueFormat)
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity due to lazy evaluation and highly optimized intrinsics.
     * @threading Not thread-safe due to lazy evaluation of mutable cached fields.
     * @algorithm Uses {@link Long#numberOfTrailingZeros(long)} and {@link Long#bitCount(long)}
     *            intrinsics.
     * @memory Does not allocate.
     */
    public final short findFirstTrueCell(ValueFormat format) {
        if (!recalculationNeeded && trueCellsCount == 0) {
            return -1;
        }

        if (recalculationNeeded) {
            // Find first true cell using bit operations
            if (gridState[0] != 0L) {
                firstTrueCell = (short) Long.numberOfTrailingZeros(gridState[0]);
            } else if (gridState[1] != 0L) {
                firstTrueCell = (short) (64 + Long.numberOfTrailingZeros(gridState[1]));
            } else {
                firstTrueCell = -1;
            }

            // Recalculate true cells count
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);

            recalculationNeeded = false;
        }
        if (firstTrueCell == -1)
            return -1;
        // Convert the result to the desired format
        switch (format) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        case Index:
            // Already in index format, no conversion needed
            break;
        case PackedInt:
            // Convert index to packed int format
            return indexToPacked(firstTrueCell);
        case null:
            throw new NullPointerException("Format cannot be null.");
        }
        return firstTrueCell;
    }

    /**
     * Scans the {@link #gridState grid} and returns the first {@code true} cell in
     * {@link ValueFormat#Index Index} format.
     *
     * <p>
     * This is a convenience overload of {@link #findFirstTrueCell(ValueFormat)} that strips the
     * format conversion, providing a simpler API for the most common use case where the generator
     * requires the index.
     * </p>
     *
     * @return The first {@code true} cell in {@link ValueFormat#Index} format, or -1 if no
     *         {@code true} cell is found.
     * @see #findTrueCells()
     * @since 2025.04 - Adjacency Optimizations
     * @performance {@code O(1)} complexity due to lazy evaluation and optimized intrinsics.
     * @threading Not thread-safe due to lazy evaluation of mutable cached fields.
     * @algorithm Uses {@link Long#numberOfTrailingZeros(long)} and {@link Long#bitCount(long)}
     *            intrinsics.
     * @memory Does not allocate.
     */
    public final short findFirstTrueCell() {
        if (!recalculationNeeded && trueCellsCount == 0) {
            return -1;
        }

        if (recalculationNeeded) {
            // Find first true cell using bit operations
            if (gridState[0] != 0L) {
                firstTrueCell = (short) (Long.numberOfTrailingZeros(gridState[0]));
            } else if (gridState[1] != 0L) {
                firstTrueCell = (short) (64 + Long.numberOfTrailingZeros(gridState[1]));
            } else {
                firstTrueCell = -1;
            }

            // Recalculate true cells count
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);

            recalculationNeeded = false;
        }
        return firstTrueCell;
    }

    /**
     * Simulates a click on the {@code Grid} at the specified cell, supporting
     * {@link ValueFormat#Index} or {@link ValueFormat#PackedInt} formats.
     *
     * <p>
     * A click toggles the state of its adjacent cells (excluding itself). This operation is
     * performed by XORing the {@link #gridState grid state} with a pre-computed
     * {@link #ADJACENCY_MASKS adjacency mask} corresponding to the clicked cell.
     * </p>
     *
     * <p>
     * The {@link #recalculationNeeded} flag is set to {@code true} to ensure that subsequent calls
     * to {@link #findFirstTrueCell(ValueFormat)} or {@link #getTrueCount()} will recompute the
     * cached {@link #firstTrueCell} and {@link #trueCellsCount} values. This lazy evaluation avoids
     * unnecessary recalculations on every click.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method achieves {@code O(1)} complexity due to the direct bitwise XOR operations on the
     * {@code long[2]} {@link #gridState} using pre-computed masks. While the grid spans 109 cells,
     * requiring two {@code long} values for the bitmask, the implementation avoids conditional
     * branching based on cell position for simplicity and consistent performance.
     * </p>
     *
     * @param cell   The cell to click, in the specified {@code format}.
     * @param format The {@link ValueFormat} of the input {@code cell} (Index or PackedInt).
     * @throws IllegalArgumentException       if {@link ValueFormat#Bitmask} is used, as it is not
     *                                        suitable for single-cell representation.
     * @throws ArrayIndexOutOfBoundsException if the {@code cell} is out of bounds (implicitly
     *                                        checked by array accesses).
     * @see #click(short[])
     * @since 2025.07 - Format Support
     * @deprecated As of 2025.07, replaced by {@link #click(short[])} for bulk operations.
     *             Single-click operations are no longer on the hot path.
     * @performance {@code O(1)} complexity due to bitwise operations and pre-computed masks.
     * @threading Not thread-safe; modifies the instance's {@link #gridState}.
     * @memory Does not allocate.
     */
    @Deprecated
    public void click(short cell, ValueFormat format) {
        switch (format) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Unsupported format: Bitmask must be a long[] of length 1 or 2.");
        case PackedInt:
            // Convert packed int to index format
            cell = packedToIndex(cell);
        case Index:
            // If the cell is in index format, we can directly use it
            // XOR the grid state with the pre-computed adjacency mask
            gridState[0] ^= ADJACENCY_MASKS[cell][0];
            gridState[1] ^= ADJACENCY_MASKS[cell][1];

            // Mark for recalculation of first true cell
            recalculationNeeded = true;
            break;
        case null:
            throw new NullPointerException("Format cannot be null.");
        }
    }

    /**
     * Simulates a click on the {@code Grid} at the specified cell, assuming
     * {@link ValueFormat#Index} format.
     *
     * <p>
     * This is a highly optimized, {@code final} method designed for performance-critical paths. It
     * directly applies the pre-computed adjacency mask to the {@link #gridState grid state} using
     * bitwise XOR.
     * </p>
     *
     * <p>
     * The {@link #recalculationNeeded} flag is set to {@code true} to trigger lazy recomputation of
     * {@link #firstTrueCell} and {@link #trueCellsCount}.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is {@code O(1)} in complexity. It is declared {@code final} to encourage JIT
     * inlining and avoids any format-checking overhead by requiring the {@link ValueFormat#Index}
     * format. This method has largely been superseded by {@link #click(short[])} for bulk
     * operations in {@link TestClickCombination}, as single-click operations are no longer the
     * primary hot path.
     * </p>
     *
     * @param cell The cell to click, in {@link ValueFormat#Index} format (0-108).
     * @throws ArrayIndexOutOfBoundsException if the {@code cell} is out of bounds (implicitly
     *                                        checked by array accesses).
     * @see #areAdjacent(short, short, ValueFormat)
     * @see #click(short, ValueFormat)
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.07 - Inlining Improvements
     * @deprecated As of 2025.07, replaced by {@link #click(short[])} for bulk operations.
     *             Single-click operations are no longer on the hot path.
     * @performance {@code O(1)} retrieval and bitwise operations.
     * @threading Not thread-safe; modifies the instance's {@link #gridState}.
     * @memory Does not allocate.
     */
    @Deprecated
    public final void click(short cell) {
        // XOR the grid state with the pre-computed adjacency mask
        gridState[0] ^= ADJACENCY_MASKS[cell][0];
        gridState[1] ^= ADJACENCY_MASKS[cell][1];

        // Mark for recalculation of first true cell and count
        recalculationNeeded = true;
    }

    /**
     * Simulates a click on the {@code Grid} at the specified row and column.
     *
     * <p>
     * This is a convenience {@code final} method that converts the row and column into a
     * {@link ValueFormat#PackedInt} value, then converts it to {@link ValueFormat#Index} format,
     * before delegating to the core {@link #click(short)} method.
     * </p>
     *
     * @param row The row of the cell to click.
     * @param col The column of the cell to click.
     * @throws ArrayIndexOutOfBoundsException if the cell is out of bounds (implicitly checked by
     *                                        array accesses).
     * @see #click(short, ValueFormat)
     * @since 2025.03 - Initial Creation
     * @deprecated As of 2025.07, replaced by {@link #click(short[])} for bulk operations.
     *             Single-click operations are no longer on the hot path.
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe; modifies the instance's {@link #gridState}.
     * @memory Does not allocate.
     */
    @Deprecated
    public final void click(short row, short col) {
        // Convert packed int to index format first
        short cell = packedToIndex((short) (row * 100 + col));

        // XOR the grid state with the pre-computed adjacency mask
        gridState[0] ^= ADJACENCY_MASKS[cell][0];
        gridState[1] ^= ADJACENCY_MASKS[cell][1];

        // Mark for recalculation of first true cell and count
        recalculationNeeded = true;
    }

    /**
     * Applies a pre-computed bitmask to the {@link #gridState grid state}.
     *
     * <p>
     * This method provides a direct way to modify the grid state by XORing it with an external
     * bitmask. It is intended for advanced scenarios where the caller has already calculated the
     * cumulative effect of one or more clicks as a bitmask.
     * </p>
     *
     * <p>
     * The {@link #recalculationNeeded} flag is set to {@code true} to trigger lazy recomputation of
     * {@link #firstTrueCell} and {@link #trueCellsCount}.
     * </p>
     *
     * @param bitmask The bitmask (a {@code long[2]} array) representing the changes to apply to the
     *                grid.
     * @throws IllegalArgumentException if the {@code bitmask} array is not of length 2.
     * @see #click(short[])
     * @since 2025.07 - Click Format Support
     * @performance {@code O(1)} bitwise operations.
     * @threading Not thread-safe; modifies the instance's {@link #gridState}.
     * @memory Does not allocate.
     */
    public void click(long[] bitmask) {
        if (bitmask.length != 2) {
            throw new IllegalArgumentException("Bitmask must be of length 2.");
        }
        gridState[0] ^= bitmask[0];
        gridState[1] ^= bitmask[1];

        // Mark for recalculation of first true cell
        recalculationNeeded = true;
    }

    /**
     * Simulates clicks on multiple cells in the {@code Grid}.
     *
     * <p>
     * This method efficiently processes an array of cells (in {@link ValueFormat#Index} format) by
     * iteratively applying their corresponding {@link #ADJACENCY_MASKS} to the {@link #gridState
     * grid state} using bitwise XOR operations. This is the primary method for applying click
     * combinations in bulk, particularly within {@link TestClickCombination monkeys}.
     * </p>
     *
     * <p>
     * The {@link #recalculationNeeded} flag is set to {@code true} once after all clicks are
     * applied, ensuring lazy recomputation of {@link #firstTrueCell} and {@link #trueCellsCount}.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is a hot-path operation with {@code O(cells.length)} complexity. It is declared
     * {@code final} to encourage JIT inlining and assumes valid input to minimize branching and
     * checks within the loop. We avoid unrolling the loop to delegate the responsibility to the JIT
     * compiler. Vectorization is also not applicable here, due to the non-predictable array
     * accesses.
     * </p>
     *
     * @param cells An array of cells (in {@link ValueFormat#Index} format) to click.
     * @throws ArrayIndexOutOfBoundsException if any {@code cell} in the array is out of bounds.
     * @throws NullPointerException           if the {@code cells} array is {@code null}.
     * @see #click(short)
     * @since 2025.07 - Bulk Clicks
     * @performance {@code O(cells.length)} for iterating over {@code cells}.
     * @threading Not thread-safe; modifies the instance's {@link #gridState}.
     * @memory Does not allocate.
     */
    public final void click(short[] cells) {
        for (short cell : cells) {
            gridState[0] ^= ADJACENCY_MASKS[cell][0];
            gridState[1] ^= ADJACENCY_MASKS[cell][1];
        }
        recalculationNeeded = true;
    }

    /**
     * Simulates clicks on multiple cells in the {@code Grid}, with a distinct final click.
     * 
     * <p>
     * This method extends the bulk click functionality by allowing a separate final click to be
     * applied after processing a sequence of prefix clicks. This is particularly useful in
     * scenarios where a {@link WorkBatch.WorkItem WorkItem} is tested, and a final click needs to
     * be applied to complete the combination.
     * </p>
     * 
     * <p>
     * The {@link #recalculationNeeded} flag is set to {@code true} once after all clicks are
     * applied, ensuring lazy recomputation of {@link #firstTrueCell} and {@link #trueCellsCount}.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is a hot-path operation with {@code O(prefix.length + 1)} complexity. It is
     * declared {@code final} to encourage JIT inlining and assumes valid input to minimize
     * branching and checks within the loop. We avoid unrolling the loop to delegate the
     * responsibility to the JIT compiler. Vectorization is also not applicable here, due to the
     * non-predictable array accesses.
     * </p>
     * 
     * @param prefix     An array of cells (in {@link ValueFormat#Index} format) to click before the
     *                   final click.
     * @param finalClick The final cell (in {@link ValueFormat#Index} format) to click after the
     *                   {@code prefix}.
     * @throws ArrayIndexOutOfBoundsException if any {@code cell} in the array or the
     *                                        {@code finalClick} is out of bounds.
     * @throws NullPointerException           if the {@code prefix} array is {@code null}.
     * @see #click(short)
     * @see #click(short[])
     * @since 2025.11 - Avoid Arraycopy in WorkItem Processing
     * @performance {@code O(prefix.length + 1)} for iterating over {@code prefix} and applying the
     *              final click.
     * @threading Not thread-safe; modifies the instance's {@link #gridState}.
     * @memory Does not allocate.
     */
    public final void click(short[] prefix, short finalClick) {
        for (short cell : prefix) {
            gridState[0] ^= ADJACENCY_MASKS[cell][0];
            gridState[1] ^= ADJACENCY_MASKS[cell][1];
        }
        gridState[0] ^= ADJACENCY_MASKS[finalClick][0];
        gridState[1] ^= ADJACENCY_MASKS[finalClick][1];
        recalculationNeeded = true;
    }

    /**
     * Returns an array of adjacent cells to the {@link #findFirstTrueCell() first true cell} in the
     * requested {@link ValueFormat format}.
     *
     * <p>
     * This method leverages the puzzle property that any solution must interact with the first
     * {@code true} cell. It provides the generator with the adjacent cells of this critical point,
     * enabling focused exploration of the search space.
     * </p>
     * 
     * <p>
     * {@link ValueFormat#Bitmask Bitmask} formats are not supported for this method. While it's
     * technically possible to represent small sets of values as bitmasks (e.g., a {@code long[2]}
     * or a {@code short[7]}), the overhead of conversion and potential for wasted space makes it
     * impractical for this context.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * The operation is {@code O(1)} because it involves a simple lookup of the first {@code true}
     * cell and then retrieving its pre-computed adjacents. While caching the result could be
     * considered, the small, fixed size of the adjacency list (at most 6 elements) makes the
     * current approach efficient enough without the overhead of cache management and defensive
     * copying.
     * </p>
     *
     * @param format The desired output format ({@link ValueFormat#Index} or
     *               {@link ValueFormat#PackedInt}).
     * @return A {@code short[]} of adjacent cells to the first {@code true} cell, or {@code null}
     *         if no {@code true} cell exists.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is used, as it is not
     *                                  suitable for representing individual cells.
     * @throws NullPointerException     if {@code format} is {@code null}.
     * @see #findAdjacents(short, ValueFormat)
     * @see #findFirstTrueCell()
     * @since 2025.04 - First True Adjacents Method Creation
     * @performance {@code O(1)} operations and conversion due to fixed-size adjacency lists.
     * @threading Not thread-safe; depends on the result of non-thread-safe methods.
     * @memory Allocates a new {@code short[]} for the result.
     */
    public short[] findFirstTrueAdjacents(ValueFormat format) {
        if (format == ValueFormat.Bitmask) {
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for this operation.");
        } else if (format == null) {
            throw new NullPointerException("Format cannot be null.");
        }

        short firstTrueCell = findFirstTrueCell(format);
        if (firstTrueCell == -1)
            return null;
        short[] trueAdjacents = findAdjacents(firstTrueCell, format);

        if (trueAdjacents == null || trueAdjacents.length == 0)
            return null;

        return trueAdjacents;
    }

    /**
     * Convenience overload for {@link #findFirstTrueAdjacents(ValueFormat)} that assumes
     * {@link ValueFormat#Index} for the output format.
     *
     * @return A {@code short[]} of adjacent cells to the first {@code true} cell in
     *         {@link ValueFormat#Index} format, or {@code null} if no {@code true} cell exists.
     * @see #findFirstTrueAdjacents(ValueFormat)
     * @since 2025.07 - Format Support
     * @performance Delegates to the main implementation; {@code O(1)} complexity.
     * @threading Not thread-safe.
     * @memory Allocates a new {@code short[]} for the result.
     */
    public short[] findFirstTrueAdjacents() {
        return findFirstTrueAdjacents(ValueFormat.Index);
    }

    /**
     * Returns an array of adjacent cells to the {@link #findFirstTrueCell() first true cell} that
     * have an index greater than the specified {@code cell}.
     *
     * <p>
     * This method is a specialized pruning helper for the generator. It identifies potential
     * subsequent clicks by filtering the adjacents of the first {@code true} cell, considering only
     * those that appear after a given {@code cell} in the flattened grid order. This helps to
     * establish bounds for combination generation.
     * </p>
     *
     * <h3>Algorithm Details</h3>
     * <p>
     * The method first retrieves all adjacents of the first {@code true} cell using
     * {@link #findFirstTrueAdjacents(ValueFormat)}. It then performs a binary search on this sorted
     * list to find the first adjacent cell whose index is greater than the provided {@code cell}. A
     * subarray containing only these subsequent adjacents is then returned, with optional format
     * conversion.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * The complexity is dominated by the binary search and array copy, resulting in an effectively
     * constant time operation {@code O(log(k) + m)}, where {@code k} is the number of adjacents
     * (max 6) and {@code m} is the number of remaining adjacents to copy. Given the small size of
     * {@code k}, this method is highly efficient. Caching the result is generally not beneficial
     * due to the dynamic nature of the {@code cell} parameter and the small performance gains from
     * avoiding an array copy.
     * </p>
     *
     * @param cell         The reference cell (in {@code inputFormat}) after which to find
     *                     adjacents.
     * @param inputFormat  The {@link ValueFormat} of the input {@code cell}.
     * @param outputFormat The {@link ValueFormat} of the output adjacent cells.
     * @return An array of adjacent cells that appear after the specified {@code cell}, or
     *         {@code null} if no {@code true} cell exists or no such adjacents are found.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is used for any format.
     * @throws NullPointerException     if {@code format} is {@code null}.
     * @see #findAdjacents(short)
     * @see #findFirstTrueAdjacents(ValueFormat)
     * @see #findFirstTrueCell()
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} effectively, due to small, fixed-size lists.
     * @threading Not thread-safe; relies on non-thread-safe methods.
     * @memory Allocates a new {@code short[]} for the result.
     */
    public short[] findFirstTrueAdjacentsAfter(short cell, ValueFormat inputFormat,
            ValueFormat outputFormat) {
        if (inputFormat == ValueFormat.Bitmask || outputFormat == ValueFormat.Bitmask) {
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        } else if (inputFormat == null || outputFormat == null) {
            throw new NullPointerException("Formats cannot be null.");
        }
        short[] firstTrueAdjacents = findFirstTrueAdjacents(inputFormat);
        if (firstTrueAdjacents == null)
            return null; // TODO: Consider replacing this with an empty array for consistency.

        // Binary search to find the index of the first adjacent cell greater than 'cell'
        int index = -1;
        int low = 0, high = firstTrueAdjacents.length - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (firstTrueAdjacents[mid] > cell) {
                index = mid; // Found a candidate, but keep searching left for the first one
                high = mid - 1;
            } else {
                low = mid + 1; // Search right
            }
        }

        // If no adjacent cell greater than 'cell' is found, return null
        if (index == -1)
            return null;

        // If the index is found, return the subarray starting from that index
        short[] result = new short[firstTrueAdjacents.length - index];
        System.arraycopy(firstTrueAdjacents, index, result, 0, result.length);

        // Convert the result to the desired output format
        if (outputFormat == inputFormat) {
            return result; // No conversion needed
        } else if (outputFormat == ValueFormat.PackedInt && inputFormat == ValueFormat.Index) {
            for (int i = 0; i < result.length; i++) {
                result[i] = indexToPacked(result[i]);
            }
        } else if (outputFormat == ValueFormat.Index && inputFormat == ValueFormat.PackedInt) {
            for (int i = 0; i < result.length; i++) {
                result[i] = packedToIndex(result[i]);
            }
        }

        return result;
    }

    /**
     * Checks if the grid is completely solved (i.e., all cells are {@code false}).
     *
     * <p>
     * This method determines if the puzzle is solved by checking if the {@link #getTrueCount()} is
     * zero. It leverages the lazy evaluation of {@link #trueCellsCount} to ensure efficient and
     * up-to-date checks without redundant calculations.
     * </p>
     *
     * @return {@code true} if the grid is solved (no {@code true} cells), {@code false} otherwise.
     * @see #recalculationNeeded
     * @since 2025.03 - Initial Creation
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe; calls the non-thread-safe {@link #getTrueCount()}.
     * @memory Does not allocate.
     */
    public boolean isSolved() {
        return getTrueCount() == 0;
    }

    /**
     * Returns the count of {@code true} cells in the grid.
     *
     * <p>
     * This method implements a lazy evaluation strategy. If the grid state has been modified
     * (indicated by {@link #recalculationNeeded}), it recalculates the count using highly optimized
     * bitwise operations ({@link Long#bitCount(long)}); otherwise, it returns the cached
     * {@link #trueCellsCount}.
     * </p>
     *
     * @return The number of {@code true} cells in the grid.
     * @since 2025.03 - Dynamic True Cell Count Tracking
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe due to lazy evaluation of a mutable field.
     * @memory Does not allocate.
     */
    public final int getTrueCount() {
        if (recalculationNeeded) {
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);
            recalculationNeeded = false;
        }
        return trueCellsCount;
    }

    /**
     * Determines if a prospective click on a {@code clickCell} can affect or create a new
     * {@link #findFirstTrueCell() first true cell} in the grid.
     *
     * <p>
     * This method is a crucial pruning helper for the {@link CombinationGeneratorTask generator}.
     * Since any valid solution must ultimately toggle the puzzle's {@code first true cell},
     * combinations whose initial clicks cannot influence this state can be discarded early.
     * </p>
     *
     * <h3>Scenarios Considered:</h3>
     * <ul>
     * <li>If there are no {@code true} cells ({@code firstTrueCell == -1}), any click can create
     * one, so it returns {@code true}.</li>
     * <li>If the {@code clickCell} is at or before the {@code firstTrueCell} in flattened grid
     * order, it is considered capable of affecting it, returning {@code true}.</li>
     * <li>If the {@code clickCell} is adjacent to the {@code firstTrueCell}, it can directly affect
     * its state, returning {@code true}.</li>
     * </ul>
     * If none of these conditions are met, the click cannot affect or create a new
     * {@code firstTrueCell}, and the method returns {@code false}.
     *
     * @param firstTrueCell The current {@code first true cell} in the specified {@code format}.
     * @param clickCell     The cell being considered for a click, in the specified {@code format}.
     * @param format        The {@link ValueFormat} of both {@code firstTrueCell} and
     *                      {@code clickCell}.
     * @return {@code true} if the click can affect or create a new {@code first true cell},
     *         {@code false} otherwise.
     * @throws IllegalArgumentException if {@link ValueFormat#Bitmask} is used.
     * @throws NullPointerException     if {@code format} is {@code null}.
     * @see #areAdjacent(short, short, ValueFormat)
     * @see #findFirstTrueCell(ValueFormat)
     * @since 2025.07 - Format and Adjacency Optimizations
     * @performance {@code O(1)} comparisons and method call.
     * @threading Thread-safe; relies only on immutable static data and input parameters.
     * @memory Does not allocate.
     */
    public static boolean canAffectFirstTrueCell(short firstTrueCell, short clickCell,
            ValueFormat format) {
        if (format == ValueFormat.Bitmask) {
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        } else if (format == null) {
            throw new NullPointerException("Format cannot be null.");
        }

        if (firstTrueCell == -1)
            return true; // No true cells, any click can create one
        if (clickCell <= firstTrueCell)
            return true; // packed int order: row * 100 + col

        return Grid.areAdjacent(firstTrueCell, clickCell, format);
    }

    /**
     * Determines if two cells are adjacent to each other in the grid.
     *
     * <p>
     * This method leverages the pre-computed {@link #ADJACENCY_CACHE} for {@code O(1)} lookups,
     * providing a highly efficient way to check adjacency. Adjacency is critical for the puzzle's
     * mechanics, as clicks only affect neighboring cells.
     * </p>
     *
     * <h3>Performance Considerations</h3>
     * <p>
     * The core lookup in {@link #ADJACENCY_CACHE} is extremely fast. The primary performance
     * consideration is the overhead of format conversion if {@link ValueFormat#PackedInt} is used.
     * For optimal performance in hot paths, it is recommended to use {@link ValueFormat#Index}
     * directly.
     * </p>
     *
     * @param cellA  The first cell to check.
     * @param cellB  The second cell to check.
     * @param format The {@link ValueFormat} of both input cells ({@link ValueFormat#Index} or
     *               {@link ValueFormat#PackedInt}).
     * @return {@code true} if the cells are adjacent, {@code false} otherwise.
     * @throws IllegalArgumentException       if {@link ValueFormat#Bitmask} is used for any format.
     * @throws NullPointerException           if {@code format} is {@code null}.
     * @throws ArrayIndexOutOfBoundsException if either cell is out of bounds.
     * @see #areAdjacent(short, short)
     * @see #canAffectFirstTrueCell(short, short, ValueFormat)
     * @since 2025.07 - Index Format Usage
     * @performance {@code O(1)} lookup in the cache, plus conversion overhead if applicable.
     * @threading Thread-safe; accesses immutable, pre-computed {@code static} data.
     * @memory Does not allocate.
     */
    public static boolean areAdjacent(short cellA, short cellB, ValueFormat format) {
        // Convert both cells to index format if necessary
        switch (format) {
        case Bitmask:
            throw new IllegalArgumentException(
                    "Bitmask format is not supported for representing a single cell.");
        case PackedInt:
            cellA = packedToIndex(cellA);
            cellB = packedToIndex(cellB);
        case Index:
            // Already in index format, no conversion needed
            break;
        case null:
            throw new NullPointerException("Format cannot be null.");
        }
        return ADJACENCY_CACHE[cellA][cellB];
    }

    /**
     * Convenience overload for {@link #areAdjacent(short, short, ValueFormat)} that assumes
     * {@link ValueFormat#Index} for both input cells.
     *
     * @param cellA The first cell to check, in {@link ValueFormat#Index} format.
     * @param cellB The second cell to check, in {@link ValueFormat#Index} format.
     * @return {@code true} if the cells are adjacent, {@code false} otherwise.
     * @throws ArrayIndexOutOfBoundsException if either cell is out of bounds.
     * @since 2025.06 - Odd Adjacency Pruning
     * @performance {@code O(1)} lookup.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    public static boolean areAdjacent(short cellA, short cellB) {
        return areAdjacent(cellA, cellB, ValueFormat.Index);
    }

    /**
     * Returns a copy of the current {@link #gridState grid state} as a {@code long[2]} bitmask.
     *
     * <p>
     * This method provides direct access to a snapshot of the grid's internal state. While it
     * involves an allocation, it is useful for debugging, logging, or scenarios requiring immutable
     * copies of the grid state without exposing the internal array directly.
     * </p>
     *
     * @return A copy of the current grid state as a {@code long[2]} bitmask.
     * @see #click(long[])
     * @see ValueFormat#Bitmask
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} complexity (fixed-size array copy).
     * @threading The returned array is thread-safe as it is a new, independent copy.
     * @memory Allocates a new {@code long[2]} array.
     */
    public long[] getGridState() {
        return gridState.clone();
    }

    public static ShortList invertCombination(ShortList clicks) {
        final ShortList inverted = new ShortArrayList(NUM_CELLS - clicks.size());
        for (short click = 0; click < NUM_CELLS; click++) {
            if (!clicks.contains(click)) {
                inverted.add(click);
            }
        }

        return inverted;
    }

    /**
     * Inverts a given combination of clicks, returning the complement set of clicks (i.e., all
     * cells not included in the original combination).
     * 
     * @param clicks An array of clicked cells in {@link ValueFormat#Index} format.
     * @return A new array containing the inverted combination of clicks.
     * @throws NullPointerException if the {@code clicks} array is {@code null}.
     * @see ValueFormat#Index
     * @since 2025.12 - Global Configuration Refactor
     * @performance {@code O(NUM_CELLS)} due to iteration over all cells.
     * @threading Thread-safe; does not modify any instance state.
     * @memory Allocates a new {@link ShortArrayList} and resulting {@code short[]} array.
     */
    public static short[] invertCombination(short[] clicks) {
        return invertCombination(ShortList.of(clicks)).toShortArray();
    }

    /**
     * Returns a {@link java.lang.String String} representation of the current grid state in a
     * human-readable format.
     * 
     * <p>
     * Cells are represented by '1' for {@code true} (on) and '0' for {@code false} (off). Rows are
     * indented to visually reflect the hexagonal layout, matching the format used in the original
     * PDC puzzle description. This method is primarily for debugging and visualization.
     * </p>
     * 
     * @return A string representation of the grid state.
     * @see #EVEN_NUM_COLS
     * @see #NUM_ROWS
     * @see #ODD_NUM_COLS
     * @see #getBit(int)
     * @see #indexToPacked(short)
     * @see #packedToIndex(short)
     * @see ValueFormat#Index
     * @see ValueFormat#PackedInt
     * @see java.lang.StringBuilder
     * @see java.lang.System#lineSeparator()
     * @since 2025.11 - toString Method Addition
     * @performance {@code O(NUM_CELLS)} due to iteration over all cells. Not performance-critical.
     * @threading Not thread-safe; iterates over the mutable grid state.
     * @memory Allocates a new {@link StringBuilder} and {@link String} for the grid representation.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < NUM_ROWS; row++) {
            if (row % 2 != 0)
                sb.append(" ");
            int cols = (row % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS;
            for (int col = 0; col < cols; col++) {
                int bitIdx = packedToIndex((short) (row * 100 + col));
                sb.append(getBit(bitIdx) ? "1 " : "0 ");
            }
            if (row < NUM_ROWS - 1)
                sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Compares this {@code Grid} instance to another object for equality. As subclasses of Grid
     * only impact initialization behavior and not the core grid state, this method only compares
     * the {@link #gridState grid state} arrays of both instances.
     * 
     * @param obj The object to compare with this instance.
     * @return {@code true} if the other object is a {@code Grid} with an identical
     *         {@link #gridState grid state}; {@code false} otherwise.
     * @see Arrays#equals(long[], long[])
     * @see Object#equals(Object)
     * @since 2025.11 - equals Method Addition
     * @performance {@code O(1)} complexity due to fixed-size array comparison.
     * @threading Not thread-safe; reads the mutable {@link #gridState}.
     * @memory Does not allocate.
     */
    @Override
    public boolean equals(Object obj) {
        // Following the Effective Java recipe for equals

        if (obj == this)
            return true;
        if (!(obj instanceof Grid))
            return false;
        Grid other = (Grid) obj;

        // Since firstTrueCell and trueCellsCount are lazily evaluated and derived from gridState,
        // we only
        // need to compare gridState arrays.
        return Arrays.equals(other.gridState, this.gridState);
    }

    /**
     * Computes the hash code for this {@code Grid} instance, delegating to the
     * {@link java.util.Arrays#hashCode(long[])} utility method.
     * 
     * <p>
     * This method generates a hash code based on the {@link #gridState grid state}, ignoring the
     * concrete class type or other, lazily evaluated fields.
     * </p>
     * 
     * @return The hash code for this {@code Grid} instance.
     * @see #equals(Object)
     * @see java.lang.Object#hashCode()
     * @since 2025.11 - hashCode Method Addition
     * @performance {@code O(1)} complexity due to fixed-size array hashing.
     * @threading Not thread-safe; reads the mutable {@link #gridState}.
     * @memory Does not allocate.
     */
    @Override
    public final int hashCode() {
        return Arrays.hashCode(gridState);
    }

    private static final class CustomGrid extends Grid {
        private CustomGrid(Builder builder) {
            super(builder.initialState0, builder.initialState1);
        }

        private CustomGrid(Grid other) {
            super(other);
        }

        @Override
        public Grid copy() {
            return new CustomGrid(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long initialState0 = 0L;
        private long initialState1 = 0L;

        protected Builder self() {
            return this;
        }

        public Builder setInitialState(long state0, long state1) {
            this.initialState0 = state0;
            this.initialState1 = state1;
            return self();
        }

        public Builder click(short cell) {
            initialState0 ^= ADJACENCY_MASKS[cell][0];
            initialState1 ^= ADJACENCY_MASKS[cell][1];
            return self();
        }

        public Builder click(short cell1, short cell2) {
            click(cell1);
            click(cell2);
            return self();
        }

        public Builder click(short cell1, short cell2, short cell3) {
            click(cell1);
            click(cell2);
            click(cell3);
            return self();
        }

        public Builder click(short cell1, short cell2, short cell3, short cell4) {
            click(cell1);
            click(cell2);
            click(cell3);
            click(cell4);
            return self();
        }

        public Builder click(short cell1, short cell2, short cell3, short cell4, short cell5) {
            click(cell1);
            click(cell2);
            click(cell3);
            click(cell4);
            click(cell5);
            return self();
        }

        public Builder click(short... cells) {
            for (short cell : cells) {
                click(cell);
            }
            return self();
        }

        public Builder click(int cell) {
            return click((short) cell);
        }

        public Builder click(int cell1, int cell2) {
            return click((short) cell1, (short) cell2);
        }

        public Builder click(int cell1, int cell2, int cell3) {
            return click((short) cell1, (short) cell2, (short) cell3);
        }

        public Builder click(int cell1, int cell2, int cell3, int cell4) {
            return click((short) cell1, (short) cell2, (short) cell3, (short) cell4);
        }

        public Builder click(int cell1, int cell2, int cell3, int cell4, int cell5) {
            return click((short) cell1, (short) cell2, (short) cell3, (short) cell4,
                    (short) cell5);
        }

        public Builder click(int... cells) {
            for (int cell : cells) {
                click((short) cell);
            }
            return self();
        }

        public Builder from(Grid other) {
            this.initialState0 = other.gridState[0];
            this.initialState1 = other.gridState[1];
            return self();
        }

        public Builder from(long[] bitmask) {
            if (bitmask.length != 2) {
                throw new IllegalArgumentException("Bitmask must be of length 2.");
            }
            this.initialState0 = bitmask[0];
            this.initialState1 = bitmask[1];
            return self();
        }

        public Builder toggle(short cell) {
            if (cell < 64) {
                initialState0 ^= (1L << cell);
            } else {
                initialState1 ^= (1L << (cell - 64));
            }

            return self();
        }

        public Builder toggle(short cell1, short cell2) {
            toggle(cell1);
            toggle(cell2);
            return self();
        }

        public Builder toggle(short cell1, short cell2, short cell3) {
            toggle(cell1);
            toggle(cell2);
            toggle(cell3);
            return self();
        }

        public Builder toggle(short cell1, short cell2, short cell3, short cell4) {
            toggle(cell1);
            toggle(cell2);
            toggle(cell3);
            toggle(cell4);
            return self();
        }

        public Builder toggle(short cell1, short cell2, short cell3, short cell4, short cell5) {
            toggle(cell1);
            toggle(cell2);
            toggle(cell3);
            toggle(cell4);
            toggle(cell5);
            return self();
        }

        public Builder toggle(short... cells) {
            for (short cell : cells) {
                toggle(cell);
            }
            return self();
        }

        public Builder toggle(int cell) {
            return toggle((short) cell);
        }

        public Builder toggle(int cell1, int cell2) {
            return toggle((short) cell1, (short) cell2);
        }

        public Builder toggle(int cell1, int cell2, int cell3) {
            return toggle((short) cell1, (short) cell2, (short) cell3);
        }

        public Builder toggle(int cell1, int cell2, int cell3, int cell4) {
            return toggle((short) cell1, (short) cell2, (short) cell3, (short) cell4);
        }

        public Builder toggle(int cell1, int cell2, int cell3, int cell4, int cell5) {
            return toggle((short) cell1, (short) cell2, (short) cell3, (short) cell4,
                    (short) cell5);
        }

        public Builder toggle(int... cells) {
            for (int cell : cells) {
                toggle((short) cell);
            }
            return self();
        }

        public Builder clear() {
            setInitialState(0L, 0L);
            return self();
        }

        public Grid build() {
            return new CustomGrid(self());
        }
    }
}
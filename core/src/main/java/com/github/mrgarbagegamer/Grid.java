package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortImmutableList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortUnaryOperator;

// TODO: Add documentation to the new methods and modify existing Javadoc accordingly
// TODO: Use byte collections instead of short collections for Index format
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
    /*
     * TODO: Enhance the ValueFormat enum to make it more usable for data conversions and to avoid
     * the need for switch statements on an enum.
     */
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
         * and is the native format for most internal data structures. However, it is less intuitive
         * for humans to read compared to {@link #PackedInt}.
         * </p>
         *
         * @see #indexToPacked(short)
         * @see #packedToIndex(short)
         * @since 2025.07 - {@code ValueFormat} Enum Introduction
         * @performance {@code O(1)} access time.
         * @threading Thread-safe as an immutable {@code enum}.
         * @memory Minimal memory overhead as a singleton per enum constant.
         */
        Index;

        static ShortList replaceAllSafely(ShortList list, ShortUnaryOperator operator) {
            ShortList result = new ShortArrayList(list);
            result.replaceAll(operator);
            return new ShortImmutableList(result);
        }

        static ShortList indexListToPackedList(ShortList indexList) {
            return replaceAllSafely(indexList, Grid::indexToPacked);
        }

        static ShortList packedListToIndexList(ShortList packedList) {
            return replaceAllSafely(packedList, Grid::packedToIndex);
        }
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
    public static final ShortImmutableList ROW_OFFSETS = ShortImmutableList.of((short) 0,
            (short) 16, (short) 31, (short) 47, (short) 62, (short) 78, (short) 93);
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

    private long lowerState, upperState;

    // TODO: Consider using a GridState to house the initial state variables
    private final long initialLowerState, initialUpperState;

    /**
     * Pre-computed bitmasks representing the result of clicking each cell.
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
    // TODO: Consider preventing mutability of adjacencyArray through a structure like a
    // List<ShortImmutableList>
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
    // TODO: Consider a different structure for better immutability
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
    // TODO: Consider using a Short2ShortMap for greater flexibility and potential immutability
    private static final short[] PACKED_TO_INDEX_CACHE = new short[(NUM_ROWS - 1) * 100
            + EVEN_NUM_COLS];

    // We don't necessarily need to worry too much about how optimized this block
    // is, since it's only run once at startup.
    // TODO: Break this static initializer into a few methods for better organization
    static {
        // 1. Populate the packed-to-index lookup cache first:
        for (short cell = 0; cell < NUM_CELLS; cell++) {
            PACKED_TO_INDEX_CACHE[indexToPacked(cell)] = cell;
        }

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
        }
    }

    // TODO: Make this private after refactoring GridTest
    static final ShortList computeAdjacents(short cell, ValueFormat inputFormat,
            ValueFormat outputFormat) {
        ShortList affectedPieces = new ShortArrayList(6);

        // We need to handle different formats for adjacency
        switch (inputFormat) {
            case Index -> cell = indexToPacked(cell);
            case PackedInt -> {} // Already in PackedInt format, no conversion needed
            case null -> throw new NullPointerException("Input format cannot be null.");
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
            case Index -> affectedPieces.replaceAll(Grid::packedToIndex);
            case PackedInt -> {} // Already in PackedInt format, no conversion needed
            case null -> throw new NullPointerException("Output format cannot be null.");
        }

        return affectedPieces;
    }

    static final ShortList computeAdjacents(short cell, ValueFormat format) {
        return computeAdjacents(cell, format, format);
    }

    static final ShortList computeAdjacents(short cell) {
        return computeAdjacents(cell, ValueFormat.Index);
    }

    public static final ShortList findAdjacents(short cell) {
        checkArgument(cell >= 0 && cell < NUM_CELLS, "cell must be in range [0, %s], but was %s",
                NUM_CELLS - 1, cell);
        return ShortList.of(adjacencyArray[cell].clone());
    }

    public static final ShortList findAdjacents(short cell, ValueFormat inputFormat,
            ValueFormat outputFormat) {
        final short index = switch (inputFormat) {
            case PackedInt -> packedToIndex(cell);
            case Index -> cell;
            case null -> throw new NullPointerException("inputFormat must not be null");
        };

        final ShortList outputList = findAdjacents(index);

        return switch (outputFormat) {
            case Index -> outputList;
            case PackedInt -> ValueFormat.indexListToPackedList(outputList);
            case null -> throw new NullPointerException("outputFormat must not be null");
        };
    }

    public static final ShortList findAdjacents(short cell, ValueFormat format) {
        return findAdjacents(cell, format, format);
    }

    public static final short packedToIndex(short packed) {
        checkArgument(packed >= 0 && packed < PACKED_TO_INDEX_CACHE.length,
                "packed must be in range [0, %s], but was %s", PACKED_TO_INDEX_CACHE.length - 1,
                packed);
        return PACKED_TO_INDEX_CACHE[packed];
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
    public static final short indexToPacked(short index) {
        if (index < 0 || index >= NUM_CELLS) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }

        if (index < ROW_OFFSETS.getShort(1))
            return (short) (0 * 100 + index);
        if (index < ROW_OFFSETS.getShort(2))
            return (short) (1 * 100 + (index - ROW_OFFSETS.getShort(1)));
        if (index < ROW_OFFSETS.getShort(3))
            return (short) (2 * 100 + (index - ROW_OFFSETS.getShort(2)));
        if (index < ROW_OFFSETS.getShort(4))
            return (short) (3 * 100 + (index - ROW_OFFSETS.getShort(3)));
        if (index < ROW_OFFSETS.getShort(5))
            return (short) (4 * 100 + (index - ROW_OFFSETS.getShort(4)));
        if (index < ROW_OFFSETS.getShort(6))
            return (short) (5 * 100 + (index - ROW_OFFSETS.getShort(5)));
        else
            return (short) (6 * 100 + (index - ROW_OFFSETS.getShort(6)));
    }

    protected Grid(long initialLowerState, long initialUpperState) {
        this.initialLowerState = initialLowerState;
        this.initialUpperState = initialUpperState;
        this.lowerState = initialLowerState;
        this.upperState = initialUpperState;
    }

    protected Grid(Grid other) {
        this.lowerState = other.lowerState;
        this.upperState = other.upperState;
        this.initialLowerState = other.initialLowerState;
        this.initialUpperState = other.initialUpperState;
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

    // TODO: Update Javadocs
    public final void initialize() {
        this.lowerState = this.initialLowerState;
        this.upperState = this.initialUpperState;
    }

    public final void click(short cell) {
        // TODO: Consider adding a bounds check on cell
        this.lowerState ^= ADJACENCY_MASKS[cell][0];
        this.upperState ^= ADJACENCY_MASKS[cell][1];
    }

    public final void click(short cell, ValueFormat format) {
        short index = switch (format) {
            case PackedInt -> packedToIndex(cell);
            case Index -> cell;
            case null -> throw new NullPointerException("format must not be null");
        };

        this.click(index);
    }

    public final void click(short[] cells) {
        for (short cell : cells)
            this.click(cell);
    }

    public final void click(short[] prefix, short finalClick) {
        this.click(prefix);
        this.click(finalClick);
    }

    // TODO: Delete this method completely after restructuring GridTest
    final void click(long[] bitmask) {
        checkArgument(bitmask.length == 2, "bitmask must be of length 2, was %s", bitmask.length);
        this.lowerState ^= bitmask[0];
        this.upperState ^= bitmask[1];
    }

    public final boolean isSolved() { return this.lowerState == 0L && this.upperState == 0L; }

    /**
     * Determines if a prospective click on a {@code clickCell} can affect or create a new first
     * true cell in the grid.
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
     * @throws NullPointerException if {@code format} is {@code null}.
     * @see #areAdjacent(short, short, ValueFormat)
     * @since 2025.07 - Format and Adjacency Optimizations
     * @performance {@code O(1)} comparisons and method call.
     * @threading Thread-safe; relies only on immutable static data and input parameters.
     * @memory Does not allocate.
     */
    public static final boolean canAffectFirstTrueCell(short firstTrueCell, short clickCell,
            ValueFormat format) {
        mustNotBeNull(format, "format");

        if (firstTrueCell == -1)
            return true; // No true cells, any click can create one
        if (clickCell <= firstTrueCell)
            return true; // packed int order: row * 100 + col

        return Grid.areAdjacent(firstTrueCell, clickCell, format);
    }

    public static final boolean areAdjacent(short cellA, short cellB) {
        // TODO: Add a bounds check on cellA and cellB
        return ADJACENCY_CACHE[cellA][cellB];
    }

    public static final boolean areAdjacent(short cellA, short cellB, ValueFormat format) {
        return switch (format) {
            case PackedInt -> areAdjacent(packedToIndex(cellA), packedToIndex(cellB));
            case Index -> areAdjacent(cellA, cellB);
            case null -> throw new NullPointerException("Format cannot be null.");
        };
    }

    public final GridState toGridState() { return new GridState(this.lowerState, this.upperState); }

    public static Grid withInitial(GridState state) { return builder().from(state).build(); }

    public static final ShortList invertCombination(ShortList clicks) {
        final ShortList inverted = new ShortArrayList(NUM_CELLS - clicks.size());
        for (short click = 0; click < NUM_CELLS; click++) {
            if (!clicks.contains(click)) {
                inverted.add(click);
            }
        }

        return new ShortImmutableList(inverted); // Make an immutable copy.
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
    public static final short[] invertCombination(short[] clicks) {
        return invertCombination(new ShortImmutableList(clicks)).toShortArray();
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
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < NUM_ROWS; row++) {
            if (row % 2 != 0)
                sb.append(" ");
            int cols = (row % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS;
            for (int col = 0; col < cols; col++) {
                int bitIdx = packedToIndex((short) (row * 100 + col));
                sb.append(this.getBit(bitIdx) ? "1 " : "0 ");
            }
            if (row < NUM_ROWS - 1)
                sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    private boolean getBit(int index) {
        int bitPosition = index % 64;
        return switch ((index / 64)) {
            case 0 -> (this.lowerState & (1L << bitPosition)) != 0;
            case 1 -> (this.upperState & (1L << bitPosition)) != 0;
            default -> throw new IndexOutOfBoundsException("Index " + index + " is out of bounds");
        };
    }

    @Override
    public final boolean equals(@Nullable Object obj) {
        // Following the Effective Java recipe for equals
        return obj == this || (obj instanceof Grid other && this.lowerState == other.lowerState
                && this.upperState == other.upperState);
    }

    @Override
    public final int hashCode() {
        return 31 * Long.hashCode(this.lowerState) + Long.hashCode(this.upperState);
    }

    private static void checkIndex(short index) {
        checkArgument(index >= 0 && index < Grid.NUM_CELLS, "index %s is out of bounds [0, %s]",
                index, Grid.NUM_CELLS - 1);
    }

    private static final class CustomGrid extends Grid {
        private CustomGrid(Builder builder) { super(builder.lowerState, builder.upperState); }

        private CustomGrid(Grid other) { super(other); }

        @Override
        public Grid copy() { return new CustomGrid(this); }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private long lowerState = 0L;
        private long upperState = 0L;

        private Builder() {}

        public Builder setInitialState(long lowerState, long upperState) {
            checkArgument(((upperState >>> 45) == 0L),
                    "upperState mask %s has bits set at or above index 45",
                    Long.toBinaryString(upperState));

            this.lowerState = lowerState;
            this.upperState = upperState;
            return this;
        }

        public Builder click(short cell) {
            checkIndex(cell);
            this.lowerState ^= ADJACENCY_MASKS[cell][0];
            this.upperState ^= ADJACENCY_MASKS[cell][1];
            return this;
        }

        public Builder click(short cell1, short cell2) {
            this.click(cell1);
            this.click(cell2);
            return this;
        }

        public Builder click(short cell1, short cell2, short cell3) {
            this.click(cell1);
            this.click(cell2);
            this.click(cell3);
            return this;
        }

        public Builder click(short cell1, short cell2, short cell3, short... cells) {
            this.click(cell1);
            this.click(cell2);
            this.click(cell3);

            for (short cell : cells) {
                this.click(cell);
            }
            return this;
        }

        public Builder from(Grid other) {
            mustNotBeNull(other, "other");

            return this.setInitialState(other.lowerState, other.upperState);
        }

        public Builder from(long[] bitmask) {
            mustNotBeNull(bitmask, "bitmask");
            checkArgument(bitmask.length == 2, "bitmask must be of length 2, was %s",
                    bitmask.length);

            return this.setInitialState(bitmask[0], bitmask[1]);
        }

        public Builder from(GridState state) {
            mustNotBeNull(state, "state");
            return this.setInitialState(state.lowerState(), state.upperState());
        }

        public Builder toggle(short cell) {
            checkIndex(cell);

            if (cell < 64)
                this.lowerState ^= (1L << cell);
            else
                this.upperState ^= (1L << (cell - 64));

            return this;
        }

        public Builder toggle(short cell1, short cell2) {
            this.toggle(cell1);
            this.toggle(cell2);
            return this;
        }

        public Builder toggle(short cell1, short cell2, short cell3) {
            this.toggle(cell1);
            this.toggle(cell2);
            this.toggle(cell3);
            return this;
        }

        public Builder toggle(short cell1, short cell2, short cell3, short... cells) {
            this.toggle(cell1);
            this.toggle(cell2);
            this.toggle(cell3);

            for (short cell : cells) {
                this.toggle(cell);
            }
            return this;
        }

        public Builder clear() { return this.setInitialState(0L, 0L); }

        public Grid build() { return new CustomGrid(this); }
    }
}
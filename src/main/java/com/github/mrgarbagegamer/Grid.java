package com.github.mrgarbagegamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortList;

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
 * @see CombinationGeneratorTask
 * @see Grid13
 * @see Grid22
 * @see Grid35
 * @see TestClickCombination
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
     * Format specification for cell values in the grid.
     * 
     * <p>
     * The grid can represent cell values in three different formats, each with its own advantages and
     * disadvantages. The {@link #PackedInt PackedInt} format is human-readable and easy to work with
     * for adjacency calculations, but it isn't the most efficient for storage. The {@link #Index Index}
     * format is more memory-efficient and is easier for generators to work with, but it isn't as easily
     * understood by humans. The {@link #Bitmask Bitmask} format allows for compact storage and fast
     * operations using bitwise logic, but it is the least human-readable and requires more complex
     * logic to work with.
     * </p>
     * 
     * <p>
     * To streamline the codebase and clarify the operations used, we defined these formats as an enum,
     * letting us switch between them easily and avoid magic numbers. This also facilitates future
     * expansions or modifications to the formats, as we can simply add new enum values and update the
     * relevant methods accordingly, improving maintainability.
     * </p>
     * 
     * @since 2025.07 - ValueFormat Enum Introduction
     * @performance {@code O(1)} access time.
     * @threading This enum is immutable and therefore thread-safe.
     * @optimization Using an enum improves code clarity and maintainability by avoiding "magic numbers" for
     *               format types.
     */
    public enum ValueFormat {
        /**
         * A format where each cell is represented as a packed integer, with the row and column encoded as
         * {@code row * 100 + col}. For example, the cell at row 3, column 5 would be represented as
         * 305.
         * 
         * <p>
         * This format is human-readable and easy to work with for adjacency calculations, as the row and
         * column can be easily extracted using simple arithmetic. However, it isn't the most efficient for
         * storage, as it uses more bits than necessary to represent the cell values.
         * </p>
         * 
         * <p>
         * It's worth noting that, as of July 24th, 2025, most of our codebase has been updated to use
         * {@code short}s instead of {@code int}s for cell values, due to the grid's size fitting
         * comfortably within the range of a {@code short}. However, the naming has persisted for
         * clarity and consistency, as the format's structure remains unchanged. "PackedShort" would be more
         * accurate, but it would require modifying a significant portion of the codebase, which may not be
         * worth the effort at this time.
         * </p>
         * 
         * <h3>Performance Considerations</h3>
         * <p>
         * While this format is easy to understand and work with, it does incur some overhead in terms of
         * storage and computation. Each packed integer requires more bits than necessary, and operations
         * like adjacency checks require additional arithmetic to extract the row and column.
         * </p>
         * 
         * <p>
         * Our codebase uses this format primarily for human-readable outputs and for operations that
         * benefit from the clarity of row and column representation (e.g., adjacency calculations).
         * However, we avoid using it in performance-critical paths where efficiency is paramount.
         * </p>
         * 
         * @see #indexToPacked(short)
         * @see #packedToIndex(short)
         * @since 2025.07 - ValueFormat Enum Introduction
         * @performance {@code O(1)} access time.
         * @threading This enum value is an immutable constant and is thread-safe.
         * @optimization This format is used for human-readable output and for clarity in adjacency
         *               calculations. It is avoided in performance-critical paths, which prefer the
         *               {@link #Index} format.
         */
        PackedInt,
        /**
         * A format where each cell is represented as a zero-based index, ranging from {@code 0} to
         * {@code 108} for our {@value Grid#NUM_CELLS}-cell grid. The index is calculated based on the
         * cell's position in a flattened array representation of the grid.
         * 
         * <p>
         * This format is more memory-efficient than {@link #PackedInt PackedInt}, as it uses the minimum
         * number of bits necessary to represent the cell values. It is also easier for generators to work
         * with, as they can simply iterate over the indices to generate combinations. However, it isn't as
         * easily understood by humans, as the row and column information is not directly encoded in the
         * index.
         * </p>
         * 
         * <h3>Performance Considerations</h3>
         * <p>
         * In comparison with the {@code PackedInt} format, this format is more efficient in terms of
         * storage, as it uses fewer bits to represent the cell values. However, operations that require
         * multiple cell accesses and/or modifications may benefit from using the {@link #Bitmask Bitmask}
         * format instead, as it can allow for operations in parallel.
         * </p>
         * 
         * @see #indexToPacked(short)
         * @see #packedToIndex(short)
         * @since 2025.07 - ValueFormat Enum Introduction
         * @performance {@code O(1)} access time.
         * @threading This enum value is an immutable constant and is thread-safe.
         * @optimization This format provides a compact, efficient representation for iterating through
         *               cells, making it ideal for generator tasks.
         */
        Index,
        /**
         * A format where the entire grid state is represented as a bitmask, with each bit corresponding to
         * a cell in the grid. A bit value of 1 indicates that the cell is "on" ({@code true}), while a bit
         * value of 0 indicates that the cell is "off" ({@code false}).
         * 
         * <p>
         * This format allows for compact storage and fast operations using bitwise logic, as multiple cell
         * states can be manipulated in parallel. However, it is the least human-readable of the three
         * formats, as the individual cell states are not directly visible. It also requires more complex
         * logic to work with, as operations like adjacency checks and cell clicks need to be implemented
         * using bitwise operations.
         * </p>
         * 
         * <h3>Performance Considerations</h3>
         * <p>
         * While this format carries several efficiency advantages, it is rarely used in our current
         * codebase due to its trade-offs. Further complicating matters is Java's lack of a native 128-bit
         * primitive type, which would be ideal for representing our 109-cell grid in a single bitmask.
         * Without such a type, we are forced to split the grid state across two 64-bit {@code long}s, which
         * adds complexity and slightly diminishes the performance benefits of using a bitmask.
         * </p>
         * 
         * <p>
         * Despite this, we could still see some benefits from using this format in certain scenarios.
         * Combinations of clicks could be represented as bitmasks, allowing for fast application to the
         * grid state using two bitwise OR operations. Our current codebase hasn't explored this avenue yet,
         * but it remains a potential optimization for the future to squeeze out additional performance,
         * especially for operations involving very long click combinations.
         * </p>
         * 
         * @see #gridState
         * @see #clearBit(int)
         * @see #click(long[])
         * @see #getBit(int)
         * @see #setBit(int)
         * @since 2025.07 - ValueFormat Enum Introduction
         * @performance {@code O(1)} access time.
         * @threading This enum value is an immutable constant and is thread-safe.
         * @optimization This format is primarily for internal grid state representation and is not exposed
         *               in most public methods due to its complexity and Java's lack of a native 128-bit
         *               primitive type.
         */
        Bitmask
    }
    
    /**
     * The number of rows in the grid. This is a constant value of {@value #NUM_ROWS}, as the grid is
     * defined to have {@value #NUM_ROWS} rows.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This constant is used throughout the class to define array sizes and loop bounds, ensuring
     * consistency and preventing magic numbers in the code. It is crucial for maintaining the integrity
     * of grid operations and ensuring that all methods operate within the valid range of rows.
     * </p>
     * 
     * @see #EVEN_NUM_COLS
     * @see #NUM_CELLS
     * @see #ODD_NUM_COLS
     * @see #ROW_OFFSETS
     * @since 2025.03 - Grid Definition
     * @performance {@code O(1)} access time.
     * @threading This constant is immutable and thread-safe.
     * @optimization Using a named constant improves code clarity and maintainability.
     */
    public static final int NUM_ROWS = 7;
    /**
     * The number of columns in odd-indexed rows of the grid. This is a constant value of
     * {@value #ODD_NUM_COLS}, as defined by the grid's structure.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This constant is used throughout the class to define array sizes and loop bounds, ensuring
     * consistency and preventing magic numbers in the code. It is crucial for maintaining the integrity
     * of grid operations and ensuring that all methods operate within the valid range of columns.
     * </p>
     * 
     * @see #EVEN_NUM_COLS
     * @see #NUM_CELLS
     * @see #NUM_ROWS
     * @see #ROW_OFFSETS
     * @since 2025.03 - Grid Definition
     * @performance {@code O(1)} access time.
     * @threading This constant is immutable and thread-safe.
     * @optimization Using a named constant improves code clarity and maintainability.
     */
    public static final int ODD_NUM_COLS = 15;
    /**
     * The number of columns in even-indexed rows of the grid. This is a constant value of
     * {@value #EVEN_NUM_COLS}, as defined by the grid's structure.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This constant is used throughout the class to define array sizes and loop bounds, ensuring
     * consistency and preventing magic numbers in the code. It is crucial for maintaining the integrity
     * of grid operations and ensuring that all methods operate within the valid range of columns.
     * </p>
     * 
     * @see #NUM_CELLS
     * @see #NUM_ROWS
     * @see #ODD_NUM_COLS
     * @see #ROW_OFFSETS
     * @since 2025.03 - Grid Definition
     * @performance {@code O(1)} access time.
     * @threading This constant is immutable and thread-safe.
     * @optimization Using a named constant improves code clarity and maintainability.
     */
    public static final int EVEN_NUM_COLS = 16;
    /**
     * Offsets for each row in the grid, used to convert between {@link ValueFormat#PackedInt PackedInt}
     * and {@link ValueFormat#Index Index} formats.
     * 
     * <p>
     * {@code PackedInt} format is human-readable and easy to work with for adjacency calculations, but
     * it isn't the most efficient for storage. {@code Index} format is more memory-efficient and is
     * easier for generators to work with, but it isn't as easily understood by humans. The offsets
     * bridge the gap between these two formats, allowing us to convert between them without losing
     * information.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Lookup time for the offsets is {@code O(1)} since they are stored in a static array. Testing has
     * shown that using a static array is more efficient than calculating the offsets at runtime, at
     * least for {@code PackedInt} -> {@code Index} conversions.
     * </p>
     * 
     * @see #EVEN_NUM_COLS
     * @see #NUM_ROWS
     * @see #ODD_NUM_COLS
     * @see #computePackedToIndex(short)
     * @see ValueFormat
     * @since 2025.06 - BitSet Grid State
     * @performance {@code O(1)} lookup time.
     * @threading This array is immutable after static initialization and is thread-safe.
     * @optimization Using a pre-computed static array for conversions avoids runtime calculations.
     */
    public static final short[] ROW_OFFSETS = {0, 16, 31, 47, 62, 78, 93};
    /**
     * The total number of cells in the grid. This is a constant value of {@value #NUM_CELLS}, derived
     * from the grid's structure of {@value #NUM_ROWS} rows with alternating counts of
     * {@value #EVEN_NUM_COLS} and {@value #ODD_NUM_COLS} columns.
     * 
     * <p>
     * The grid consists of {@value #NUM_ROWS} rows, where even-indexed rows (0, 2, 4, 6) contain
     * {@value #EVEN_NUM_COLS} columns each, and odd-indexed rows (1, 3, 5) contain
     * {@value #EVEN_NUM_COLS} columns each. This results in a total of 4 * {@value #EVEN_NUM_COLS} + 3
     * * {@value #ODD_NUM_COLS} = {@value NUM_CELLS} cells.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This constant is used throughout the class to define array sizes and loop bounds, ensuring
     * consistency and preventing magic numbers in the code. It is crucial for maintaining the integrity
     * of grid operations and ensuring that all methods operate within the valid range of cells.
     * </p>
     * 
     * @see #EVEN_NUM_COLS
     * @see #gridState
     * @see #NUM_ROWS
     * @see #ODD_NUM_COLS
     * @see #ROW_OFFSETS
     * @see #trueCellsCount
     * @since 2025.04 - Static Block Initialization
     * @performance {@code O(1)} access time.
     * @threading This constant is immutable and thread-safe.
     * @optimization Using a named constant improves code clarity and maintainability.
     */
    public static final int NUM_CELLS = 109;

    /**
     * The current state of the grid as an array of two{@code long}s. Each long contains a bitmask, with
     * the first representing the first 64 cells and the second representing the last 45 cells.
     * 
     * <p>
     * The core part of the grid state is, well, the grid state. We need something that can efficiently
     * represent the state of the grid, allowing for fast access and manipulation of each cell while
     * also keeping memory usage low. The bitmask approach fits both of these requirements, making
     * operations very efficient.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * The fundamental rule of JVM optimization: <b>Don't allocate</b>. To achieve a throughput of tens
     * of billions of {@code click}s per second, we need to avoid unnecessary allocations and use
     * primitive types wherever possible. This takes most collections off the table.
     * </p>
     * 
     * <p>
     * An array of {@code boolean}s seems like a good fit, but a {@code boolean} takes up 1 byte at
     * minimum in Java (with arrays using extra padding), which would require 109 bytes for the grid. In
     * addition, clicks act on adjacent cells, meaning that each operation would require 6 lookups and 6
     * updates, which is suboptimal.
     * </p>
     * 
     * <p>
     * {@link java.util.BitSet BitSets} are a good fit, but they incur some overhead in terms of object
     * headers and the need to perform up to 6 XOR operations per click. Their internals give way to the
     * best implementation: a bitmask. We can make custom masks to represent click operations, turning
     * clicks into a single operation (technically two because of size limitations) and storing our grid
     * in an array that takes up just 128 bits (or 16 bytes).
     * </p>
     * 
     * <p>
     * There could be some further optimizations in the future, but we're running against the limits of
     * Java as a whole. There is no 128-bit primitive type, so we can't use that. LongVectors seemed
     * promising, but the Vector API allocates TERRIBLY (we're talking 4 allocations per lanewise
     * operation, all from the internal non-overridable logic of the API). Until we can find something
     * that can represent a 128-bit value without allocations, we will stick with this approach.
     * </p>
     * 
     * @see #clearBit(int)
     * @see #getBit(int)
     * @see #getGridState()
     * @see #printGrid()
     * @see #setBit(int)
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} access and update time.
     * @threading Not thread-safe. Access must be synchronized externally.
     * @optimization Uses a primitive {@code long[]} bitmask to avoid object allocation overhead,
     *               enabling extremely fast, cache-friendly state manipulation via bitwise operations.
     */
    protected final long[] gridState = new long[2];
    /**
     * Cached value of the number of cells that are currently {@code true} (on) in the grid. Used for
     * quick {@link #isSolved() solution checks} and to optimize certain operations. Updated whenever
     * {@link #getTrueCount()} is called and {@link #recalculationNeeded} is {@code true}.
     * 
     * @see #getTrueCount()
     * @see #isSolved()
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} access. The value is updated lazily.
     * @threading Not thread-safe. This field is mutated by {@link #click(short)} and read by
     *            {@link #getTrueCount()}.
     * @optimization Caches the number of "on" cells to make {@link #isSolved()} checks instantaneous.
     *               The count is recalculated only when necessary, controlled by
     *               {@link #recalculationNeeded}.
     */
    protected int trueCellsCount = 0;
    /**
     * A cached index of the first cell that is currently {@code true} (on) in the grid. Stored in
     * {@link ValueFormat#Index Index} format and used to optimize constraint satisfaction checks and
     * certain operations. Updated whenever {@link #findFirstTrueCell(ValueFormat)} is called and
     * {@link #recalculationNeeded} is {@code true}.
     * 
     * @see #findFirstTrueCell()
     * @since 2025.07 - First True Cell Caching
     * @performance {@code O(1)} access. The value is updated lazily.
     * @threading Not thread-safe. This field is mutated by {@link #click(short)} and read by
     *            {@link #findFirstTrueCell()}.
     * @optimization Caches the index of the first "on" cell, a critical optimization for pruning the
     *               search space in the generator. Recalculated only when needed via
     *               {@link #recalculationNeeded}.
     */
    protected short firstTrueCell = -1;
    /**
     * A flag indicating whether a recalculation of {@link #trueCellsCount} and/or
     * {@link #firstTrueCell} is needed. Set to {@code true} whenever the grid state is modified
     * (e.g., via a click operation) and reset to {@code false} after the next call to
     * {@link #getTrueCount()} or {@link #findFirstTrueCell(ValueFormat) findFirstTrueCell()}.
     * 
     * @see #firstTrueCell
     * @see #trueCellsCount
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} access.
     * @threading Not thread-safe. This flag coordinates lazy recalculations.
     * @optimization Implements a lazy evaluation strategy for {@link #trueCellsCount} and
     *               {@link #firstTrueCell}, ensuring that expensive recalculations are only performed
     *               when the state has been modified.
     */
    protected boolean recalculationNeeded = false;

    /**
     * Adjacency masks for each cell in the grid. These are filled in during the {@code static} block of
     * {@link Grid this class} and are used to quickly determine the adjacent cells for a given cell in
     * the grid.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Pre-computing these masks allows for {@code O(1)} lookups when determining the adjacent cells for
     * a given cell, avoiding the need to compute adjacencies on-the-fly. This is crucial, as
     * {@link TestClickCombination monkeys} perform tens of billions of {@code click}s per second (as of
     * writing this), and we need to ensure that adjacency checks are as fast as possible.
     * </p>
     * 
     * <p>
     * We standardize the masks to use 2 {@code long}s for each cell since we have {@value #NUM_CELLS}
     * cells, which fits comfortably within 128 bits. Though a click could only affect cells in the
     * range of one of these {@code long}s, we use two for simplicity and to avoid additional complexity
     * in the hot path. The indices are standardized to follow the {@link ValueFormat#Index Index}
     * format, consistent with other fields in this class.
     * </p>
     * 
     * @see #NUM_CELLS
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.04 - Static Block Initialization
     * @performance {@code O(1)} lookup time.
     * @threading This array is immutable after {@code static} initialization and is thread-safe.
     * @optimization Pre-computing adjacency masks is the core optimization that makes
     *               {@link #click(short)} an {@code O(1)} operation. Each mask represents the bitwise
     *               change required to toggle all cells adjacent to a given cell.
     */
    private static final long[][] ADJACENCY_MASKS = new long[NUM_CELLS][2];
    
    // Legacy support for existing code that expects adjacency arrays
    /**
     * Adjacency array for each cell in the grid. Each entry contains a {@code short[]} of the indices
     * of adjacent cells. These are filled in during the {@code static} block of {@code Grid}'s
     * initialization, and are used to quickly retrieve results with the
     * {@link #findAdjacents(short, ValueFormat, ValueFormat) findAdjacents()} method.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Pre-computing these arrays allows for {@code O(1)} lookups when determining the adjacent cells
     * for a given cell, avoiding the need to compute adjacencies on-the-fly. Previously, we used this
     * array in the {@link #click(short, ValueFormat) click()} method, but we have since moved to using
     * the {@link #ADJACENCY_MASKS adjacency masks} for better performance. The array is retained for
     * use in the {@link #findAdjacents(short, ValueFormat, ValueFormat) findAdjacents()} method.
     * </p>
     * 
     * @see #NUM_CELLS
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.05 - Adjacency Storage Optimization
     * @performance {@code O(1)} lookup time.
     * @threading This array is immutable after {@code static} initialization and is thread-safe.
     * @optimization Pre-computing adjacency lists in this array allows the
     *               {@link #findAdjacents(short, ValueFormat, ValueFormat) findAdjacents()} methods to
     *               be fast lookups rather than expensive computations.
     */
    private static final short[][] adjacencyArray = new short[NUM_CELLS][]; // Index format
    /**
     * A 2D {@code boolean} array representing adjacency between cells in the grid. This is a legacy
     * structure, primarily used for quick adjacency checks between two cells in
     * {@link #areAdjacent(short, short, ValueFormat) areAdjacent()}. It is filled in during the static
     * block of Grid's initialization and is not used in performance-critical paths.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This structure allows for {@code O(1)} adjacency checks between two cells, which is useful for
     * certain operations. However, it is not used in the hot path of the application, as most
     * operations use the {@link #ADJACENCY_MASKS adjacency masks} or the {@link #adjacencyArray
     * adjacency array} for better performance. The 2D array is retained for legacy support and for use
     * in specialized operations.
     * </p>
     * 
     * @see #NUM_CELLS
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.06 - {@code O(1)} Adjacency Check
     * @performance {@code O(1)} lookup time.
     * @threading This array is immutable after static initialization and is thread-safe.
     * @optimization A pre-computed {@code boolean} matrix that provides a near-instantaneous way to
     *               check if two cells are adjacent, used by {@link #areAdjacent(short, short)}.
     */
    private static final boolean[][] ADJACENCY_CACHE = new boolean[NUM_CELLS][NUM_CELLS]; // Index format
    /**
     * A cache for converting {@link ValueFormat#PackedInt PackedInt} values to {@link ValueFormat#Index
     * Index} values. This is filled in during the {@code static} block of {@code Grid}'s initialization
     * and is used to quickly convert between these two formats without needing to compute the
     * conversion on-the-fly.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Pre-computing this cache allows for {@code O(1)} lookups when converting from {@code PackedInt}
     * to {@code Index} format, avoiding the need to compute the conversion on-the-fly. This is useful
     * for operations that need to work with both formats, as it allows for quick conversions without
     * incurring the cost of calculations.
     * </p>
     * 
     * @see #NUM_CELLS
     * @see #computePackedToIndex(short)
     * @see ValueFormat
     * @since 2025.06 - {@code PackedInt} to {@code Index} Precomputation
     * @performance {@code O(1)} lookup time.
     * @threading This array is immutable after {@code static} initialization and is thread-safe.
     * @optimization A cache to accelerate the conversion from the human-readable
     *               {@link ValueFormat#PackedInt PackedInt} format to the performance-oriented
     *               {@link ValueFormat#Index Index} format.
     */
    private static final short[] PACKED_TO_INDEX_CACHE = new short[(NUM_ROWS - 1) * 100 + EVEN_NUM_COLS];

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
     * Determines the hexagonally adjacent cells for a cell in the grid. This overload allows the user
     * to specify the input and output formats, providing flexibility in how the static block for this
     * class is built.
     * 
     * <p>
     * Unlike a traditional grid, a cell in a hexagonal grid has up to 6 adjacent cells. The cells
     * adjacent to a given cell aren't just based on the row and column, but also on the row's parity
     * (even or odd), making calculations much more complex than a simple rectangular grid. This problem
     * is exacerbated by the fact that the Grid is represented in a flattened, bitmask format, doing
     * away with the concept of rows and columns entirely for memory efficiency. Since {@code click}
     * operations affect the adjacent cells, we need some way to determine which cells are adjacent to a
     * given cell. This method provides that functionality.
     * </p>
     * 
     * <h3>Algorithm Details</h3> For a given cell {@code n} (in {@link ValueFormat#PackedInt} format)
     * in an even row, the adjacent cells include:
     * <ul>
     * <li>{@code n - 101} (row - 1, col - 1)</li>
     * <li>{@code n - 100} (row - 1, col)</li>
     * <li>{@code n - 1} (row, col - 1)</li>
     * <li>{@code n + 1} (row, col + 1)</li>
     * <li>{@code n + 99} (row + 1, col - 1)</li>
     * <li>{@code n + 100} (row + 1, col)</li>
     * </ul>
     * 
     * For a cell in an odd row, the adjacent cells include:
     * <ul>
     * <li>{@code n - 100} (row - 1, col)</li>
     * <li>{@code n - 99} (row - 1, col + 1)</li>
     * <li>{@code n - 1} (row, col - 1)</li>
     * <li>{@code n + 1} (row, col + 1)</li>
     * <li>{@code n + 100} (row + 1, col)</li>
     * <li>{@code n + 101} (row + 1, col + 1)</li>
     * </ul>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Internal computations are performed in {@code PackedInt} format for efficiency, as it allows for
     * straightforward arithmetic operations to determine adjacency. The method has to convert between
     * formats as needed, which adds some overhead, but this is offset by our use of the method in the
     * {@code static} block to pre-compute an {@link #adjacencyArray adjacency table}. Future calls to
     * find adjacents can then use this pre-computed table for {@code O(1)} lookups.
     * </p>
     * <p>
     * Since cells in different grid positions can have a different number of valid adjacent cells (due
     * to edges and corners), we use a dynamic list ({@code ShortList}) to store the results, letting
     * the calling method handle conversion to a fixed-size array if needed. Fastutil's primitive
     * collections are used to avoid boxing overhead and allow for efficient storage and retrieval of
     * short values.
     * </p>
     * 
     * @param cell         A short representing the cell in the grid, expected to be in the same format
     *                     as inputFormat.
     * @param inputFormat  The {@link ValueFormat} of the input cell, which can either be
     *                     {@link ValueFormat#Index Index} or {@link ValueFormat#PackedInt PackedInt}.
     * @param outputFormat The {@link ValueFormat} of the output cell, which can either be
     *                     {@link ValueFormat#Index Index} or {@link ValueFormat#PackedInt PackedInt}.
     * @return affectedPieces A {@code ShortList} of adjacent cells in the specified output format,
     *         containing up to 6 items.
     * @throws IllegalArgumentException if the input or output format is {@link ValueFormat#Bitmask},
     *                                  since we cannot represent a single cell in that format.
     * @see #findAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
     * @see ShortList
     * @since 2025.07 - Format Support
     * @performance Overall {@code O(1)} complexity. The number of adjacent cells is small and constant.
     * @threading This method is thread-safe as it is a pure function with no side effects.
     * @memory Uses a {@code ShortList} from FastUtil to avoid boxing primitive {@code short} values,
     *         reducing garbage collection pressure compared to a standard {@code ArrayList<Short>}.
     * @optimization Internal calculations are performed in {@link ValueFormat#PackedInt} for simpler
     *               arithmetic. The use of {@code ShortList} avoids an initial pass to count valid
     *               neighbors before creating a perfectly sized array.
     */
    public static ShortList computeAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat) {
        ShortList affectedPieces = new ShortArrayList(6);

        // We need to handle different formats for adjacency 
        switch (inputFormat) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Convert the cell to packed int format
                cell = indexToPacked((short) cell);
            case PackedInt:
                // If the cell is in packed int format, we can directly compute adjacents
                break;
        }
        
        int row = cell / 100;

        if (row % 2 == 0) // even rows with 16 columns
        { 
            affectedPieces.add((short) (cell - 101)); // (row - 1, col - 1)
            affectedPieces.add((short) (cell - 100)); // (row - 1, col)
            affectedPieces.add((short) (cell - 1));   // (row, col - 1)
            affectedPieces.add((short) (cell + 1));   // (row, col + 1)
            affectedPieces.add((short) (cell + 99));  // (row + 1, col - 1)
            affectedPieces.add((short) (cell + 100)); // (row + 1, col)
        } else // odd rows with 15 columns
        { 
            affectedPieces.add((short) (cell - 100)); // (row - 1, col)
            affectedPieces.add((short) (cell - 99));  // (row - 1, col + 1)
            affectedPieces.add((short) (cell - 1));   // (row, col - 1)
            affectedPieces.add((short) (cell + 1));   // (row, col + 1)
            affectedPieces.add((short) (cell + 100)); // (row + 1, col)
            affectedPieces.add((short) (cell + 101)); // (row + 1, col + 1)
        }

        // Remove out-of-bounds cells
        affectedPieces.removeIf(key -> {
            int r = key / 100, c = key % 100;
            return r < 0 || r >= NUM_ROWS || c < 0 || c >= ((r % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS);
        });

        switch (outputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Convert packed int to index
                affectedPieces.replaceAll(Grid::packedToIndex);
                break;
            case PackedInt:
                // Already in packed int format, no conversion needed
                break;
        }

        return affectedPieces;
    }

    /**
     * Overload of {@link #computeAdjacents(short, ValueFormat, ValueFormat) computeAdjacents()} that
     * assumes the input and output formats are the same. This exists purely for convenience, as it
     * simply calls the main method with the same format for both parameters.
     * 
     * @param cell   a {@code short} representing the cell in the grid, expected to be in the same
     *               format as {@code format}.
     * @param format the {@link ValueFormat} of both the input and output cell, which can either be
     *               {@link ValueFormat#Index Index} or {@link ValueFormat#PackedInt PackedInt}.
     * @return A {@code ShortList} of adjacent cells in the specified format, containing up to 6
     *         items.
     * @throws IllegalArgumentException if the input or output format is Bitmask, since we cannot
     *                                  represent a single cell in that format.
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @see ShortList
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity. Delegates to the main implementation.
     * @threading This method is thread-safe.
     * @memory Creates a new {@code ShortList} for the result.
     * @optimization Convenience overload.
     */
    public static ShortList computeAdjacents(short cell, ValueFormat format) {
        return computeAdjacents(cell, format, format);
    }

    /**
     * Overload of {@link #computeAdjacents(short, ValueFormat) computeAdjacents()} that assumes that
     * the format is {@link ValueFormat#Index Index}. This exists purely for convenience, as it simply
     * calls the main method with {@code Index} for both parameters.
     * 
     * @param cell a {@code short} representing the cell in the grid, expected to be in
     *             {@link ValueFormat#Index Index} format.
     * @return A {@code ShortList} of adjacent cells in {@link ValueFormat#Index Index} format,
     *         containing up to 6 items.
     * @throws IllegalArgumentException if the input or output format is Bitmask, since we cannot
     *                                  represent a single cell in that format.
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @see ShortList
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity. Delegates to the main implementation.
     * @threading This method is thread-safe.
     * @memory Creates a new {@code ShortList} for the result.
     * @optimization Convenience overload with default format.
     */
    public static ShortList computeAdjacents(short cell) {
        return computeAdjacents(cell, ValueFormat.Index);
    }

    /**
     * Finds the hexagonally adjacent cells for a given cell in the grid, parsing inputs and outputting
     * in the requested formats.
     * 
     * <p>
     * Since our grid is hexagonal, each cell can have up to 6 adjacent cells, depending on the cell's
     * position and the grid's boundaries. This method provides a way to retrieve those adjacent cells
     * in various formats, making it versatile for different use cases.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * While this method does pull from a {@link #adjacencyArray pre-computed adjacency array} for
     * {@code O(1)} lookups, it is forced to convert each of the results to the requested output format,
     * which adds some overhead and makes the method {@code O(adjacencyArray[cell].length)} in
     * complexity (with the length being 6 at most). In addition, the large {@code switch} statements
     * for format handling limit this method's ability to be inlined by the JVM.
     * </p>
     * 
     * <p>
     * A potential area for optimization would be to pre-compute adjacency arrays for each format
     * combination, but this method is designed to be called infrequently (if at all) during the
     * lifetime of the program, so the added complexity and memory usage may not be worth it.
     * </p>
     * 
     * @param cell         A {@code short} representing the cell in the grid, expected to be in the same
     *                     format as {@code inputFormat}.
     * @param inputFormat  The {@link ValueFormat} of the input cell, which can either be
     *                     {@link ValueFormat#Index Index} or {@link ValueFormat#PackedInt PackedInt}.
     * @param outputFormat The {@link ValueFormat} of the output cell, which can either be
     *                     {@link ValueFormat#Index Index} or {@link ValueFormat#PackedInt PackedInt}.
     * @return A {@code short[]} of adjacent cells in the specified output format, containing up to 6
     *         items.
     * @throws IllegalArgumentException       if the input or output format is
     *                                        {@link ValueFormat#Bitmask Bitmask}, since we cannot
     *                                        represent a single cell in that format.
     * @throws ArrayIndexOutOfBoundsException if the input cell is out of bounds for the specified input
     *                                        format (implicitly checked by array accesses).
     * @see #adjacencyArray
     * @see #computeAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat)
     * @see ValueFormat
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} for {@link ValueFormat#Index} output,
     *              {@code O(adjacencyArray[cell].length)} for other formats due to conversion.
     * @threading This method is thread-safe, as it relies only on immutable, pre-computed
     *            {@code static} data.
     * @memory Allocates a new {@code short[]} for the result only if format conversion is necessary.
     * @optimization The primary lookup from {@link #adjacencyArray} is an {@code O(1)} operation. The
     *               main performance consideration is the potential overhead of format conversion.
     */
    public static short[] findAdjacents(short cell, ValueFormat inputFormat, ValueFormat outputFormat) {
        short[] result;
        switch (inputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                // Convert packed int to index
                cell = packedToIndex(cell);
            case Index:
                // Already in index format, no conversion needed.
                result = adjacencyArray[cell];
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + inputFormat);
        }
        switch (outputFormat) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                short[] packedResult = new short[result.length];
                for (short i = 0; i < result.length; i++) 
                {
                    packedResult[i] = indexToPacked(result[i]);
                }
                return packedResult;
            default:
                throw new IllegalArgumentException("Unsupported format: " + outputFormat);
        }

        return result;
    }

    /**
     * Overload of {@link #findAdjacents(short, ValueFormat, ValueFormat) findAdjacents()} that assumes
     * the input and output formats are the same. This exists purely for convenience, as it simply calls
     * the main method with the same format for both parameters.
     * 
     * @param cell   a {@code short} representing the cell in the grid, expected to be in the same
     *               format as {@code format}.
     * @param format the {@link ValueFormat} of both the input and output cell, which can either be
     *               {@link ValueFormat#Index Index} or {@link ValueFormat#PackedInt PackedInt}.
     * @return A {@code short[]} of adjacent cells in the specified format, containing up to 6 items.
     * @throws IllegalArgumentException if the input or output format is Bitmask, since we cannot
     *                                  represent a single cell in that format.
     * @see #findAdjacents(short)
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity. Delegates to the main implementation.
     * @threading This method is thread-safe.
     * @memory May allocate a new {@code short[]} if format conversion occurs.
     * @optimization Convenience overload.
     */
    public static short[] findAdjacents(short cell, ValueFormat format) {
        return findAdjacents(cell, format, format);
    }

    /**
     * Overload of {@link #findAdjacents(short, ValueFormat) findAdjacents()} that assumes that the
     * format is {@link ValueFormat#Index Index}. This exists purely for convenience, as it simply calls
     * the main method with {@code Index} for both parameters.
     * 
     * @param cell a {@code short} representing the cell in the grid, expected to be in
     *             {@link ValueFormat#Index Index} format.
     * @return A {@code short[]} of adjacent cells in {@link ValueFormat#Index Index} format, containing
     *         up to 6 items.
     * @throws IllegalArgumentException if the input or output format is Bitmask, since we cannot
     *                                  represent a single cell in that format.
     * @see #findAdjacents(short, ValueFormat)
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} adjacency lookup.
     * @threading This method is thread-safe.
     * @memory Returns a reference to a statically allocated array, avoiding new allocations.
     * @optimization The most direct and efficient way to get adjacents, as no format conversion is
     *               needed.
     */
    public static short[] findAdjacents(short cell) {
        return findAdjacents(cell, ValueFormat.Index);
    }

    /**
     * Computes the conversion from {@link ValueFormat#PackedInt PackedInt} format to
     * {@link ValueFormat#Index Index} format. This method performs the necessary arithmetic to
     * determine the correct {@code Index} for a given {@code PackedInt} value.
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * The conversion is based on the structure of the grid, which has rows of varying lengths. The
     * first row has {@value #EVEN_NUM_COLS} columns, the second row has {@value #ODD_NUM_COLS} columns,
     * and this pattern continues to alternate. To convert a {@code PackedInt} value (which encodes the
     * row and column) to an {@code Index} value (which is a single {@code short} representing the
     * cell's position in a flattened array), we need to account for the varying row lengths.
     * </p>
     * 
     * <p>
     * After obtaining the row and column from the {@code PackedInt} value, we use a {@link #ROW_OFFSETS
     * pre-defined array of row offsets} to determine the starting index of the row in the flattened
     * array. We then add the column to this offset to get the final index.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be used by the {@code static} block of the {@code Grid} class to
     * pre-compute a {@link #PACKED_TO_INDEX_CACHE cache} for fast lookups. While the method itself is
     * {@code O(1)} in complexity, it is not intended to be called frequently during the program's
     * execution. Instead, it serves as a utility for initializing the cache, which can then be used for
     * {@code O(1)} lookups in performance-critical paths. The method does not perform any bounds
     * checking on the input packed value, as it is assumed that the static block will only call it with
     * valid values.
     * </p>
     * 
     * @param packed the {@code short} representing the cell in {@link ValueFormat#PackedInt PackedInt}
     *               format.
     * @return A {@code short} representing the cell in {@link ValueFormat#Index Index} format.
     * @throws ArrayIndexOutOfBoundsException (implicitly) if the input packed value is out of bounds
     *                                        (less than 0 or greater than 615).
     * @since 2025.06 - PackedInt to Index Precomputation
     * @performance {@code O(1)} complexity due to direct arithmetic and a single array lookup.
     * @threading This method is thread-safe as it is a pure function.
     * @memory Does not allocate memory.
     * @optimization Relies on the pre-computed {@link #ROW_OFFSETS} for fast calculation.
     */
    private static short computePackedToIndex(short packed) {
        short row = (short) (packed / 100);
        short col = (short) (packed % 100);
        return (short) (ROW_OFFSETS[row] + col);
    }

    /**
     * Converts a cell from {@link ValueFormat#PackedInt PackedInt} format to {@link ValueFormat#Index
     * Index} format. This method uses a cache to speed up conversions for commonly used values, and
     * {@link #computePackedToIndex(short) calculates} new values as needed.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be efficient, using a cache to store previously computed conversions
     * for fast lookups. If a value is not found in the cache, it is computed on-the-fly using the
     * {@link #computePackedToIndex(short) computePackedToIndex()} method.
     * </p>
     * 
     * <p>
     * A potential area for optimization would be to explicitly compute the entire cache in the
     * {@code static} block to avoid the need for on-the-fly calculations. Doing this would increase the
     * startup time slightly, but would eliminate the need for any calculations during runtime, allowing
     * us to remove the check for uncached values and make this method smaller, improving its chances of
     * being inlined by the JVM. We've chosen not to do this for now, since conversions from the
     * human-friendly {@code PackedInt} format to the memory efficient {@code Index} format are not a
     * common operation in performance-critical paths (we try to use {@code Index} internally as much as
     * possible), but the option remains open for the future.
     * </p>
     * 
     * @param packed A {@code short} representing the cell in {@link ValueFormat#PackedInt PackedInt}
     *               format.
     * @return A {@code short} representing the cell in {@link ValueFormat#Index Index} format.
     * @throws IllegalArgumentException if the input packed value is out of bounds (less than 0 or
     *                                  greater than 615).
     * @see #PACKED_TO_INDEX_CACHE
     * @see #computePackedToIndex(short)
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity due to direct cache lookup.
     * @threading This method is thread-safe. The cache is populated statically.
     * @memory Does not allocate memory.
     * @optimization Provides an {@code O(1)} alternative to the arithmetic conversion. Declared
     *               {@code final} to encourage JIT inlining.
     */
    public final static short packedToIndex(short packed) {
        if (packed >= 0 && packed < PACKED_TO_INDEX_CACHE.length) {
            // TODO: Explicitly pre-compute the entire cache in the static block to avoid on-the-fly calculations.
            if (PACKED_TO_INDEX_CACHE[packed] == 0 && packed != 0) {
                // If the cache is not initialized, compute it
                PACKED_TO_INDEX_CACHE[packed] = computePackedToIndex(packed);
            }
            return PACKED_TO_INDEX_CACHE[packed];
        }
        throw new IllegalArgumentException("Invalid packed int: " + packed);
    }

    /**
     * Converts a cell from {@link ValueFormat#Index Index} format to {@link ValueFormat#PackedInt
     * PackedInt} format. This method uses a series of conditional checks to determine the correct row
     * and column for the given {@code Index}.
     * 
     * <p>
     * Note that this method does not explicitly check if the input {@code Index} is greater than
     * {@code 0} for performance. Negative indices may return a value, but they are not valid packed int
     * values.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be efficient, using a series of conditional checks to quickly
     * determine the correct row and column for the given index. If extra performance is needed, we
     * could cache the results of this method (a similar approach is taken for
     * {@link #packedToIndex(short)}), but since this method is not frequently called in
     * performance-critical paths, the added complexity and memory usage may not be worth it.
     * </p>
     * 
     * @param index A {@code short} representing the cell in {@link ValueFormat#Index Index} format.
     * @return A {@code short} representing the cell in {@link ValueFormat#PackedInt PackedInt} format.
     * @throws IllegalArgumentException if the input index is out of bounds (less than 0 or greater than
     *                                  108).
     * @see #packedToIndex(short)
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity. The series of comparisons is constant time.
     * @threading This method is thread-safe as it is a pure function.
     * @memory Does not allocate memory.
     * @optimization A branch-based approach that is friendly to modern CPU branch predictors. Declared
     *               {@code final} to encourage JIT inlining.
     */
    public final static short indexToPacked(short index) {
        if (index < 16) return  (short) (0 * 100 + index);
        if (index < 31) return  (short) (1 * 100 + (index - 16));
        if (index < 47) return  (short) (2 * 100 + (index - 31));
        if (index < 62) return  (short) (3 * 100 + (index - 47));
        if (index < 78) return  (short) (4 * 100 + (index - 62));
        if (index < 93) return  (short) (5 * 100 + (index - 78));
        if (index < 109) return (short) (6 * 100 + (index - 93));
        throw new IllegalArgumentException("Invalid index: " + index);
    }

    /**
     * Constructs a new {@code Grid} instance and initializes it to a specific starting state by calling
     * the {@link #initialize()} method.
     * 
     * <p>
     * Since {@link Grid} is an abstract class, this constructor is called implicitly via a subclass's
     * constructor. The {@link #initialize()} method is abstract and must be implemented by subclasses
     * to define their own initial configurations.
     * </p>
     * 
     * @see #initialize()
     * @since 2025.03 - Abstract {@code Grid} Introduction
     * @threading The constructor is thread-safe as it operates on a new instance.
     */
    public Grid() {
        initialize();
    }

    /**
     * Initializes the {@code Grid} to a specific starting state. This method is abstract and must be
     * implemented by subclasses to define their own initial configurations.
     * 
     * <p>
     * This method is called in the {@link #Grid() constructor} of the {@link Grid} class, ensuring that
     * any subclass will have its {@link #gridState grid state} properly initialized upon instantiation.
     * The specific implementation of this method will depend on the requirements of the subclass.
     * </p>
     * 
     * @see Grid13
     * @see Grid22
     * @see Grid35
     * @since 2025.03 - Abstract {@code Grid} Introduction
     * @threading Not thread-safe. This method modifies the instance's state.
     */
    abstract void initialize();

    // Core bitmask operations

    /**
     * Sets the bit at the specified index in the {@link #gridState grid state}. We assume that the
     * index is in {@link ValueFormat#Index Index} format ({@code 0}-{@code 108}) to save time on format
     * checks.
     * 
     * <p>
     * While this method can be used to toggle a cell in the grid, we pre-compute
     * {@link #ADJACENCY_MASKS adjacency masks} for each cell to perform clicks more efficiently. As
     * such, this method is not used in any method of the codebase.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * For performance reasons, this method does not perform bounds checking on the index. It is the
     * caller's responsibility to ensure that the index is within the valid range
     * ({@code 0}-{@code 108}).
     * </p>
     * <p>
     * This method checks internally if the bit is already set before setting it, but this move is not
     * necessary for performance. Removing the check would be more efficient, but we don't utilize this
     * method for any performance-critical paths.
     * </p>
     * 
     * @param index The index of the bit to set ({@code 0}-{@code 108}).
     * @throws IndexOutOfBoundsException if the index is out of bounds ({@code 0}-{@code 127}).
     * @see #clearBit(int)
     * @see #getBit(int)
     * @since 2025.07 - Bitmasked {@code Grid} State
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe. Modifies the instance's {@link #gridState}.
     * @optimization This is a low-level helper. For performance reasons, it does not perform bounds
     *               checking; the caller is responsible for providing a valid index.
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
     * Clears the bit at the specified index in the {@link #gridState grid state}. We assume that the
     * index is in {@link ValueFormat#Index Index} format ({@code 0}-{@code 108}) to save time on format
     * checks.
     * 
     * <p>
     * While this method can be used to clear a cell in the grid, we pre-compute {@link #ADJACENCY_MASKS
     * adjacency masks} for each cell to perform clicks more efficiently. As such, this method is not
     * used in any method of the codebase.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * For performance reasons, this method does not perform bounds checking on the index. It is the
     * caller's responsibility to ensure that the index is within the valid range
     * ({@code 0}-{@code 108}).
     * </p>
     * 
     * @param index The index of the bit to clear ({@code 0}-{@code 108}).
     * @throws IndexOutOfBoundsException if the index is out of bounds (0-127).
     * @see #getBit(int)
     * @see #setBit(int)
     * @since 2025.07 - Bitmasked {@code Grid} State
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe. Modifies the instance's {@link #gridState}.
     * @optimization Low-level helper. The caller is responsible for providing a valid index.
     **/
    protected void clearBit(int index) {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        if ((gridState[longIndex] & (1L << bitPosition)) != 0)
        {
            gridState[longIndex] &= ~(1L << bitPosition);
            trueCellsCount--;
        }
    }

    /**
     * Extracts the bit at the specified index from the {@link #gridState grid state}. We assume that
     * the index is in {@link ValueFormat#Index Index} format ({@code 0}-{@code 108}) to save time on
     * format checks.
     * 
     * <p>
     * This method is used to check if a specific cell in the grid is {@code true} (active) or
     * {@code false} (inactive). We internally use this method for finding {@code true} cells in the
     * grid, which is crucial for {@link #findTrueCells() outputting a list of true cells} or
     * {@link #printGrid() printing the grid state.}
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * For performance reasons, this method does not perform bounds checking on the index. It is the
     * caller's responsibility to ensure that the index is within the valid range
     * ({@code 0}-{@code 108}).
     * </p>
     * 
     * @param index The index of the bit to check ({@code 0}-{@code 108}).
     * @return true if the bit is set, false otherwise.
     * @throws IndexOutOfBoundsException if the index is out of bounds (0-127).
     * @see #clearBit(int)
     * @see #setBit(int)
     * @since 2025.07 - Bitmasked {@code Grid} State
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe, as it reads potentially mutable state.
     * @optimization Low-level helper. The caller is responsible for providing a valid index.
     */
    protected boolean getBit(int index) {
        int longIndex = index / 64;
        int bitPosition = index % 64;
        return (gridState[longIndex] & (1L << bitPosition)) != 0;
    }

    /**
     * Scans the {@link #gridState grid bitmask} and returns an array of all {@code true} cells.
     * Converts the result to the requested format ({@link ValueFormat#Index} or
     * {@link ValueFormat#PackedInt}).
     * 
     * <p>
     * For {@link CombinationGeneratorTask generator} and {@link TestClickCombination monkey}
     * efficiency, we perform several checks to prune combinations early. Many of these checks involve
     * the initial state of the grid, so we need a way to conveniently extract the {@code true} bits
     * from the bitmask and return them in a format that can be used for processing. This method
     * provides that functionality, allowing for the efficient scanning of the grid state.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * We use the {@link #getBit(int index)} method to check each bit in the grid state, iterating
     * across the values in the range 0-108 (inclusive). For each bit that is set, we add its index to
     * an array that is pre-sized to the number of {@code true} cells in the grid. To avoid scanning the
     * entire grid after finding the requisite number of {@code true} cells, we use a counter to track
     * the index in the array and exit the for loop early when the array is full.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method isn't expected to be called frequently, as it is primarily used for obtaining the
     * initial grid state. For this reason, we avoid {@link #recalculationNeeded checking for
     * recalculation} or {@link #getTrueCount() recalculating the true cells count}, assuming the
     * {@code true} count is correctly maintained by the {@link #setBit(int index)} and
     * {@link #clearBit(int index)} methods. This avoids overhead, though it does allow for the
     * possibility of an incorrectly sized array or missing {@code true} cells. A future update could
     * target this.
     * </p>
     * 
     * <p>
     * Another area for potential optimization is in the extraction of {@code true} cells. We could
     * create two temporary {@code long}s to store the grid state, use the
     * {@link Long#numberOfTrailingZeros(long)} method to find the first 1 in our bitmask, add the index
     * to the array and turn off the bit (by ANDing with 0) and repeat until we have found all true
     * cells. This would allow us to avoid the overhead of iterating over the entire grid state, but it
     * would require additional complexity in the code that is unneeded at the moment.
     * </p>
     * 
     * @param format the desired output format (Index or PackedInt).
     * @return a {@code short[]} of {@code true} cells in the specified format.
     * @throws IllegalArgumentException if the format is {@link ValueFormat#Bitmask Bitmask}, (since
     *                                  that would just be the Grid itself). Use {@link #getGridState()}
     *                                  instead to fulfill that purpose.
     * @see #findFirstTrueCell(ValueFormat)
     * @see #findTrueCells()
     * @since 2025.07 - Format Support
     * @performance <code>O({@value #NUM_CELLS})</code>. A more optimized version could use
     *              {@link Long#numberOfTrailingZeros(long)} to find set bits directly, but this method
     *              is not on a critical performance path.
     * @threading Not thread-safe, as it reads the mutable {@link #gridState}.
     * @memory Allocates a new {@code short[]} for the results.
     * @optimization Creates a perfectly sized array based on the cached {@link #trueCellsCount} to
     *               avoid resizing. Stops iterating as soon as all {@code true} cells have been found.
     */
    public short[] findTrueCells(ValueFormat format) {
        short[] trueCellsArray = new short[trueCellsCount];
        int idx = 0;
        
        // Internally, we iterate over bit indices (0-108)
        for (short i = 0; i < NUM_CELLS && idx < trueCellsCount; i++) 
        {
            if (getBit(i)) trueCellsArray[idx++] = i;
        }
        
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing true cells (since that's just the Grid).");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                for (int i = 0; i < trueCellsCount; i++) 
                {
                    trueCellsArray[i] = (short) indexToPacked(trueCellsArray[i]);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }

        return trueCellsArray;
    }

    /**
     * Scans the {@link #gridState grid bitmask} and returns an array of all {@code true} cells in
     * {@link ValueFormat#Index Index} format (0-108).
     * 
     * <p>
     * This method is a convenience overload of {@link #findTrueCells(ValueFormat format)} that defaults
     * to the {@code Index} format, allowing for better inlining and performance if this method is on
     * the hot path.
     * </p>
     * 
     * @return An array of {@code true} cells in the specified format.
     * @see #findFirstTrueCell(ValueFormat)
     * @see #findTrueCells(ValueFormat)
     * @since 2025.04 - Adjacency Optimizations
     * @performance <code>O({@value #NUM_CELLS})</code>.
     * @threading Not thread-safe.
     * @memory Allocates a new {@code short[]} for the results.
     * @optimization A {@code final} convenience overload that defaults to the most common format.
     */
    public final short[] findTrueCells() {
        short[] trueCellsArray = new short[trueCellsCount];
        int idx = 0;
        
        // Internally, we iterate over bit indices (0-108)
        for (short i = 0; i < NUM_CELLS && idx < trueCellsCount; i++) 
        {
            if (getBit(i)) trueCellsArray[idx++] = i;
        }
        
        return trueCellsArray;
    }

    /**
     * Scans the {@link #gridState grid} and returns the first {@code true} cell. Converts the result to
     * the requested format ({@link ValueFormat#Index} or {@link ValueFormat#PackedInt}). If no
     * {@code true} cell is found, returns -1.
     * 
     * <p>
     * Since {@code click}s are limited in scope to the adjacents of one cell, we can derive an
     * important property of the solution: It must toggle the first {@code true} cell in the initial
     * grid state. We can therefore optimize the search for the solution by pruning combinations that
     * violate this condition, making it important to have a fast way to find the first {@code true}
     * cell in the grid. While one could use the {@code findTrueCells()} method to find the first
     * {@code true} cell, this method is optimized for that purpose.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * Java contains a built-in method for finding the first set bit in a {@code long}, called
     * {@link Long#numberOfTrailingZeros(long)}. We can use this method to grab the first {@code true}
     * cell and update the {@link #firstTrueCell} field. We also update {@link #trueCellsCount} field
     * with another method, {@link Long#bitCount(long)}, which counts the number of set bits in a
     * {@code long}.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * If no changes have been made to the grid state since the last call to this method, we'd want to
     * save on recalculating the value of the first true cell and the count of {@code true} cells. We
     * can do this by checking the {@link #recalculationNeeded recalculationNeeded} flag and updating
     * the values if it is {@code true}. In the case where the flag is {@code false} and the count of
     * true cells is {@code 0}, we can short-circuit the method to avoid unnecessary calculations,
     * returning {@code -1} immediately.
     * </p>
     * 
     * <p>
     * The methods used to find the first {@code true} cell and count the number of true cells are
     * {@code O(1)} due to some clever programming on the Java developers' part. Reinventing the wheel
     * would be a waste of time (and likely less efficient), so we avoid that effort.
     * </p>
     * 
     * @param format the desired output format ({@link ValueFormat#Index Index} or
     *               {@link ValueFormat#PackedInt PackedInt}).
     * @return the first {@code true} cell in the specified format, or -1 if no {@code true} cell is
     *         found.
     * @throws IllegalArgumentException if the format is {@link ValueFormat#Bitmask Bitmask} (since
     *                                  bitmasks aren't supported for single values).
     * @see #click(short[])
     * @see #click(short, ValueFormat)
     * @see #findFirstTrueCell()
     * @see #findTrueCells(ValueFormat)
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe due to lazy evaluation of cached fields.
     * @algorithm Uses the highly optimized {@link Long#numberOfTrailingZeros(long)} and
     *            {@link Long#bitCount(long)} intrinsics for recalculation.
     * @optimization Implements lazy recalculation. The cached {@link #firstTrueCell} and
     *               {@link #trueCellsCount} are only recomputed if the {@link #recalculationNeeded}
     *               flag is set.
     */
    public short findFirstTrueCell(ValueFormat format) {
        if (!recalculationNeeded && trueCellsCount == 0) 
        {
            return -1;
        }

        if (recalculationNeeded)
        {
            // Find first true cell using bit operations
            if (gridState[0] != 0L) 
            {
                firstTrueCell = (short) Long.numberOfTrailingZeros(gridState[0]);
            } else if (gridState[1] != 0L) 
            {
                firstTrueCell = (short) (64 + Long.numberOfTrailingZeros(gridState[1]));
            } else 
            {
                firstTrueCell = -1;
            }

            // Recalculate true cells count
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);
            
            recalculationNeeded = false;
        }
        if (firstTrueCell == -1) return -1;
        // Convert the result to the desired format
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                return indexToPacked(firstTrueCell);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        return firstTrueCell;
    }

    /**
     * Scans the {@link #gridState grid} and returns the first {@code true} cell in
     * {@link ValueFormat#Index Index} format ({@code 0}-{@code 108}). If no {@code true} cell is found,
     * returns -1.
     * 
     * <p>
     * This method is a convenience overload of {@link #findFirstTrueCell(ValueFormat format)} that
     * defaults to the {@code Index} format, allowing for better inlining and performance if this method
     * is on the hot path.
     * </p>
     * 
     * @return The first {@code true} cell in {@code Index} format, or -1 if no {@code true} cell is
     *         found.
     * @see #click(short[])
     * @see #click(short, ValueFormat)
     * @see #findFirstTrueCell(ValueFormat)
     * @see #findTrueCells()
     * @since 2025.04 - Adjacency Optimizations
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe.
     * @algorithm Delegates to the main implementation, using {@link ValueFormat#Index}.
     * @optimization A {@code final} convenience overload for the most common use case.
     */
    public final short findFirstTrueCell() {
        if (!recalculationNeeded && trueCellsCount == 0) 
        {
            return -1;
        }

        if (recalculationNeeded)
        {
            // Find first true cell using bit operations
            if (gridState[0] != 0L) 
            {
                firstTrueCell = (short) (Long.numberOfTrailingZeros(gridState[0]));
            } else if (gridState[1] != 0L) 
            {
                firstTrueCell = (short) (64 + Long.numberOfTrailingZeros(gridState[1]));
            } else 
            {
                firstTrueCell = -1;
            }

            // Recalculate true cells count
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);
            
            recalculationNeeded = false;
        }
        return firstTrueCell;
    }

    /**
     * Simulates a click on the {@code Grid} at the specified cell, which can be in any of the supported
     * {@link ValueFormat formats} ({@link ValueFormat#Index Index} or {@link ValueFormat#PackedInt
     * PackedInt}).
     * 
     * <p>
     * A click toggles the state of its adjacent cells (excluding itself), so we can perform a click
     * simply by XORing the {@link #gridState grid state} with a pre-computed {@link #ADJACENCY_MASKS
     * adjacency mask.}
     * </p>
     * 
     * <p>
     * Setting the {@link #recalculationNeeded recalculationNeeded} flag to {@code true} ensures that
     * the next call to {@link #findFirstTrueCell(ValueFormat) findFirstTrueCell()} will recalculate the
     * {@link #firstTrueCell first true cell} and the {@link #trueCellsCount count of true cells}. This
     * avoids unnecessary recalculations on every click, which is crucial for performance in
     * high-frequency scenarios.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since the Grid has {@link #NUM_CELLS 109 cells}, we can't use a single 64-bit {@code long} for
     * the grid state, meaning that we have to use two {@code long}s, and thus two adjacency masks per
     * cell. Though we could strategically use a single {@code long} as a bitmask if the adjacent cells
     * are localized to the same {@code long}, we choose to use two{@code long}s for simplicity and to
     * avoid branching.
     * </p>
     * 
     * @param cell   the cell to click, in the specified format.
     * @param format the format of the cell ({@code Index} or {@code PackedInt}).
     * @throws IllegalArgumentException       if the format is {@link ValueFormat#Bitmask Bitmask}
     *                                        (since bitmasks aren't supported for single values) or
     *                                        another unsupported format.
     * @throws ArrayIndexOutOfBoundsException if the cell is out of bounds (implicitly checked by the
     *                                        array accesses).
     * @see #click(short)
     * @see #click(short[])
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe. Modifies the instance's {@link #gridState}.
     * @optimization The core of this operation is two bitwise XORs against the pre-computed
     *               {@link #ADJACENCY_MASKS}, which is extremely fast. It also lazily marks the state
     *               for recalculation.
     */
    public void click(short cell, ValueFormat format) {
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Unsupported format: Bitmask must be a long[] of length 1 or 2.");
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
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Simulates a click on the {@code Grid} at the specified cell. We assume that the cell is in
     * {@link ValueFormat#Index {@code Index}} format ({@code 0}-{@code 108}) to save time on format
     * checks.
     * 
     * <p>
     * A click toggles the state of its adjacent cells (excluding itself), so we can perform a click
     * simply by XORing the {@link #gridState grid state} with a pre-computed {@link #ADJACENCY_MASKS
     * adjacency mask.}
     * </p>
     * 
     * <p>
     * Setting the {@link #recalculationNeeded recalculationNeeded} flag to {@code true} ensures that
     * the next call to {@link #findFirstTrueCell(ValueFormat) findFirstTrueCell()} will recalculate the
     * {@link #firstTrueCell first true cell} and the {@link #trueCellsCount count of true cells}. This
     * avoids unnecessary recalculations on every click, which is crucial for performance in
     * high-frequency scenarios.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since the Grid has {@link #NUM_CELLS 109 cells}, we can't use a single 64-bit {@code long} for
     * the grid state, meaning that we have to use two {@code long}s, and thus two adjacency masks per
     * cell. Though we could strategically use a single {@code long} as a bitmask if the adjacent cells
     * are localized to the same {@code long}, we choose to use two{@code long}s for simplicity and to
     * avoid branching.
     * </p>
     * 
     * @param cell The cell to click, in {@link ValueFormat#Index Index} format ({@code 0}-{@code 108}).
     * @throws ArrayIndexOutOfBoundsException if the cell is out of bounds (implicitly checked by the
     *                                        array accesses).
     * @see #areAdjacent(short, short, ValueFormat)
     * @see #click(short, ValueFormat)
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.07 - Inlining Improvements
     * @deprecated As of 2025.07, this has been replaced by {@link #click(short[])} for bulk operations
     *             in {@link TestClickCombination}. Single-click operations are no longer on the hot
     *             path.
     * @performance {@code O(1)} complexity. This is a hot-path method.
     * @threading Not thread-safe. Modifies the instance's {@link #gridState}.
     * @optimization This minimal implementation is declared {@code final} to encourage JIT inlining. It
     *               avoids any format-checking overhead by requiring the {@link ValueFormat#Index}
     *               format.
     */
    public final void click(short cell) {
        // XOR the grid state with the pre-computed adjacency mask
        gridState[0] ^= ADJACENCY_MASKS[cell][0];
        gridState[1] ^= ADJACENCY_MASKS[cell][1];
        
        // Mark for recalculation of first true cell and count
        recalculationNeeded = true;
    }

    /**
     * Specialized click method for the {@link ValueFormat#PackedInt PackedInt} format. Made
     * {@code final} to encourage inlining.
     * 
     * @param row The row of the cell to click.
     * @param col The column of the cell to click.
     * @throws ArrayIndexOutOfBoundsException if the cell is out of bounds (implicitly checked by the
     *                                        array accesses).
     * @see #click(short, ValueFormat)
     * @since 2025.03 - Initial Creation
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe. Modifies the instance's {@link #gridState}.
     * @optimization A {@code final} convenience overload that handles {@link ValueFormat#PackedInt}
     *               conversion before performing the click. Designed to be inlined.
     */
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
     * Specialized click method for the {@link ValueFormat#Bitmask Bitmask} format.
     * 
     * <p>
     * A click toggles the state of its adjacent cells (excluding itself), so we can perform a click by
     * XORing the {@link #gridState grid state} with the provided bitmask.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is built for scenarios where the caller has pre-computed the adjacency bitmask for a
     * cell and wants to apply it directly, avoiding the overhead of format conversions or lookups.
     * While our code does not currently leverage this method, it is provided for completeness and
     * potential future use cases.
     * </p>
     * 
     * <p>
     * To optimize performance further, we could make the method {@code final} to encourage inlining,
     * but we leave it as-is for now to allow for potential overrides in subclasses.
     * </p>
     * 
     * @param bitmask The bitmask representing the adjacents to toggle, as a {@code long[]} of length 2.
     * @throws IllegalArgumentException if the bitmask is not of length 2.
     * @see #click(short)
     * @see #click(short, ValueFormat)
     * @since 2025.07 - Click Format Support
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe. Modifies the instance's {@link #gridState}.
     * @optimization Directly applies a pre-computed bitmask, avoiding any lookup overhead.
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
     * Simulates a click on multiple cells in the {@code Grid}. We assume that the cells are in the
     * {@link ValueFormat#Index {@code Index}} format ({@code 0}-{@code 108}) and the array is
     * non-{@code null} to save on format checks.
     * 
     * <p>
     * A click simply toggles the state of its adjacent cells (excluding itself), so we can perform a
     * click by XORing the {@link #gridState grid state} with the pre-computed {@link #ADJACENCY_MASKS
     * adjacency masks}. As opposed to the single cell {@link #click(short cell)} method, this method
     * allows for bulk operations on combinations of cells, saving on the overhead of multiple method
     * calls.
     * </p>
     * 
     * <p>
     * Setting the {@link #recalculationNeeded recalculationNeeded} flag to {@code true} ensures that
     * the next call to {@link #findFirstTrueCell(ValueFormat) findFirstTrueCell()} will recalculate the
     * {@link #firstTrueCell first true cell} and the {@link #trueCellsCount count of true cells}. This
     * avoids unnecessary recalculations on every click, which is crucial for performance in
     * high-frequency scenarios.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * Since the Grid has {@link #NUM_CELLS 109 cells}, we can't use a single 64-bit {@code long} for
     * the grid state, meaning that we have to use two {@code long}s, and thus two adjacency masks per
     * cell. Though we could strategically use a single {@code long} as a bitmask if the adjacent cells
     * are localized to the same {@code long}, we choose to use two {@code long}s for simplicity and to
     * avoid branching.
     * </p>
     * <p>
     * We avoid unrolling the loop here, since the JVM can handle that for us and it would deliver
     * mediocre performance gains at best. Vectorization is also not applicable here, since we perform
     * array accesses in a non-predictable manner.
     * </p>
     * 
     * @param cells The array of cells to click, in Index format ({@code 0}-{@code 108}).
     * @throws IllegalArgumentException if the clicks are out of bounds (implicitly checked by the array
     *                                  accesses).
     * @throws NullPointerException     if the cells array is {@code null} (implicitly thrown by the for-each
     *                                  loop).
     * @see #areAdjacent(short, short, ValueFormat)
     * @see #computeAdjacents(short, ValueFormat, ValueFormat)
     * @since 2025.07 - Bulk Clicks
     * @performance {@code O(cells.length)}. This is a hot-path method for worker threads.
     * @threading Not thread-safe. Modifies the instance's {@link #gridState}.
     * @optimization This method is the primary replacement for single-cell clicks. It processes an
     *               array of clicks in a tight loop. It is {@code final} to encourage JIT inlining and
     *               assumes valid input to avoid branching and checks inside the loop.
     */
    public final void click(short[] cells) {
        for (short cell : cells) {
            // XOR the grid state with the pre-computed adjacency mask
            gridState[0] ^= ADJACENCY_MASKS[cell][0];
            gridState[1] ^= ADJACENCY_MASKS[cell][1];
        }
        
        // Mark for recalculation of first true cell and count
        recalculationNeeded = true;
    }

    /**
     * Returns an array of adjacents of the {@link #findFirstTrueCell() first true cell} in the
     * requested {@link ValueFormat format}. If no {@code true} cell exists, returns {@code null}.
     * 
     * <p>
     * Since clicks are limited in scope to the adjacents of one cell (excluding itself), we can derive
     * an important property of the solution: It must contain at least one click on the adjacents of the
     * first {@code true} cell in the initial grid state. This may seem obvious, but it allows us to
     * establish bounds for generation, limiting the maximum cell that needs to be considered for the
     * first click to the last adjacent of the first {@code true} cell. This method gives us an easy way
     * to extract and test those adjacents.
     * </p>
     * 
     * <p>
     * As a note, we currently prevent the use of {@link ValueFormat#Bitmask Bitmask} formats here,
     * since bitmasks aren't well-suited for representing small sets of values. If we were to support
     * them, we would either need to create a {@code long[]} of length 2 (to match the internal grid
     * size) or a {@code short[]} with a length of 7 to cover 116 bits (the closest multiple of 16 above
     * 109). The former would likely require a new method due to the incompatible return type (unless we
     * made the return type {@code Object}, which would be messy), while the latter would waste space
     * and require additional conversions. Given the limited utility of bitmasks in this context, we opt
     * to exclude them for now.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be efficient, performing a simple lookup of the first {@code true}
     * cell and then retrieving its adjacents. The operation is {@code O(1)} due to the fixed size of
     * the adjacency list (up to 6 elements).
     * </p>
     * 
     * <p>
     * To optimize performance further, we could consider caching the result of the operation if the
     * state has not changed since the last call, but given the simplicity of the operation and the
     * small size of the adjacency list, the current implementation is sufficient. If the first
     * {@code true} cell were to change, we would need to invalidate the cache anyway, requiring an
     * allocation of a new array; we would also need to clone the array before returning it to avoid
     * external modifications, which would negate the performance benefits of caching in the first
     * place.
     * </p>
     * 
     * @param format The desired output format ({@link ValueFormat#Index Index} or
     *               {@link ValueFormat#PackedInt PackedInt}).
     * @return A {@code short[]} of adjacent cells in the requested format, or {@code null} if no true
     *         cell exists.
     * @throws IllegalArgumentException if the format is {@link ValueFormat#Bitmask Bitmask}.
     * @see #findAdjacents(short, ValueFormat)
     * @since 2025.04 - First True Adjacents Method Creation
     * @performance {@code O(1)} for {@link ValueFormat#Index},
     *              <code>O({@link #adjacencyArray}[cell].length)</code> for others.
     * @threading Not thread-safe, as it depends on the result of non-thread-safe methods.
     * @memory Allocates a new {@code short[]} for the result.
     * @optimization Leverages the lazy evaluation of {@link #findFirstTrueCell()} and the pre-computed
     *               {@link #adjacencyArray}.
     */
    public short[] findFirstTrueAdjacents(ValueFormat format) {
        if (format == ValueFormat.Bitmask) {
            throw new IllegalArgumentException("Bitmask format is not supported for this operation.");
        }
        
        short firstTrueCell = findFirstTrueCell(format);
        if (firstTrueCell == -1) return null;
        short[] trueAdjacents = findAdjacents(firstTrueCell, format);

        if (trueAdjacents == null || trueAdjacents.length == 0) return null;
        
        return trueAdjacents;
    }

    /**
     * Overload of {@link #findFirstTrueAdjacents(ValueFormat)} that assumes the output format is
     * {@link ValueFormat#Index Index}. This exists purely for convenience, as it simply calls the main
     * method with the {@code Index} format.
     * 
     * @return A {@code short[]} of adjacent cells in {@link ValueFormat#Index Index} format, or
     *         {@code null} if no {@code true} cell exists.
     * @since 2025.07 - Format Support
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe.
     * @memory Allocates a new {@code short[]}.
     * @optimization Convenience overload.
     */
    public short[] findFirstTrueAdjacents() {
        return findFirstTrueAdjacents(ValueFormat.Index);
    }

    /**
     * Returns an array of adjacents of the {@link #findFirstTrueCell() first true cell} that come after
     * the specified cell, in the requested {@link ValueFormat format}. If no {@code true} cell exists,
     * or if no {@code true} adjacents after the specified cell exist, returns {@code null}.
     * 
     * <p>
     * Since clicks are limited in scope to the adjacents of one cell (excluding itself), we can derive
     * an important property of the solution: It must contain at least one click on the adjacents of the
     * first {@code true} cell in the initial grid state. This may seem obvious, but it allows us to
     * establish bounds for generation, limiting the maximum cell that needs to be considered for the
     * first click to the last adjacent of the first {@code true} cell. This method gives us an easy way
     * to extract and test those adjacents.
     * </p>
     * 
     * <h3>Algorithm Details</h3>
     * <p>
     * We first {@link #findFirstTrueAdjacents(ValueFormat) find the adjacents of the first true cell}
     * in the specified input format. If no {@code true} cell exists, we return {@code null}. We then
     * convert the input cell to {@link ValueFormat#Index index} format if necessary, using the
     * {@link #packedToIndex(short)} method for {@link ValueFormat#PackedInt packed int} format. We then
     * perform a binary search on the array of adjacents to find the first adjacent cell that is greater
     * than the specified cell. If no such adjacent exists, we return {@code null}. If we find such an
     * adjacent, we create a new array containing all adjacents from that point onward. Finally, we
     * convert the result to the desired output format if necessary and return it.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method is designed to be efficient, performing a logarithmic search on a small array of
     * adjacents and linear copying the resulting subarray. For greater efficiency, we could consider
     * caching the result of the operation or figuring out some way to avoid an array copy, but given
     * the small size of the adjacency list (up to 6 elements) and the infrequent nature of this
     * operation, the current implementation is sufficient.
     * </p>
     * 
     * @param cell         the cell after which to find adjacents, in the specified input format.
     * @param inputFormat  the {@link ValueFormat format} of the input cell ({@link ValueFormat#Index
     *                     Index} or {@link ValueFormat#PackedInt PackedInt}).
     * @param outputFormat the {@link ValueFormat format} of the output array ({@link ValueFormat#Index
     *                     Index} or {@link ValueFormat#PackedInt PackedInt}).
     * @return An array of adjacent cells after the specified cell, in the requested output format, or
     *         {@code null} if no true cell exists or if no adjacents after the specified cell exist.
     * @throws IllegalArgumentException if either format is {@link ValueFormat#Bitmask Bitmask} (since
     *                                  bitmasks aren't supported for single values) or anything else.
     * @see #findAdjacents(short)
     * @see #findFirstTrueAdjacents(ValueFormat)
     * @see #findFirstTrueCell()
     * @since 2025.07 - Format Support
     * @performance <code>O(log({@link #adjacencyArray}[cell].length) + m)}</code> where {@code m} is
     *              the number of remaining adjacents to copy. Effectively constant time.
     * @threading Not thread-safe.
     * @algorithm Performs a binary search on the sorted adjacency list of the first {@code true} cell
     *            to find the subset of neighbors that appear after the given cell.
     * @memory Allocates a new {@code short[]} for the result.
     * @optimization A specialized method for the generator to efficiently prune the search space by
     *               only considering relevant subsequent clicks.
     */
    public short[] findFirstTrueAdjacentsAfter(short cell, ValueFormat inputFormat, ValueFormat outputFormat) {
        short[] firstTrueAdjacents = findFirstTrueAdjacents(inputFormat);
        if (firstTrueAdjacents == null) return null;

        // Convert the input cell to index format if necessary
        switch (inputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                cell = packedToIndex(cell);
            case Index:
                // Already in index format, no conversion needed
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + inputFormat);
        }
        
        // Binary search to find the index of the first adjacent cell greater than 'cell'
        int index = -1;
        int low = 0, high = firstTrueAdjacents.length - 1;
        while (low <= high) 
        {
            int mid = (low + high) / 2;
            if (firstTrueAdjacents[mid] > cell) 
            {
                index = mid; // Found a candidate, but keep searching left for the first one
                high = mid - 1;
            } else 
            {
                low = mid + 1; // Search right
            }
        }

        // If no adjacent cell greater than 'cell' is found, return null
        if (index == -1) return null;

        // If the index is found, return the subarray starting from that index
        short[] result = new short[firstTrueAdjacents.length - index];
        System.arraycopy(firstTrueAdjacents, index, result, 0, result.length);

        // Convert the result to the desired output format
        switch (outputFormat)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case Index:
                // Already in index format, no conversion needed
                break;
            case PackedInt:
                // Convert index to packed int format
                for (int i = 0; i < result.length; i++) 
                {
                    result[i] = indexToPacked(result[i]);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + outputFormat);
        }

        return result;
    }

    /**
     * Checks if the grid is completely solved (i.e., all cells are {@code false}). We use the
     * {@link #getTrueCount()} method to determine if there are any true cells remaining rather than
     * directly checking the {@link #gridState grid state} or the {@link #trueCellsCount true cells
     * count} to ensure that any necessary recalculations are performed before making the check while
     * avoiding code duplication.
     * 
     * @return {@code true} if the grid is solved (no {@code true} cells), {@code false} otherwise.
     * @see #recalculationNeeded
     * @since 2025.03 - Initial Creation
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe, as it calls the non-thread-safe {@link #getTrueCount()}.
     */
    public boolean isSolved() {
        return getTrueCount() == 0;
    }

    /**
     * Returns the count of {@code true} cells in the grid. If the grid state has changed since the last
     * calculation, it recalculates the count using {@link Long#bitCount(long) bitwise operations}.
     * 
     * @return the number of true cells in the grid.
     * @see #recalculationNeeded
     * @since 2025.03 - Dynamic True Cell Count Tracking
     * @performance {@code O(1)} complexity.
     * @threading Not thread-safe due to lazy evaluation of a mutable field.
     * @optimization Implements lazy evaluation; the count is only recalculated using fast bitwise
     *               operations when the {@link #recalculationNeeded} flag is true.
     */
    public int getTrueCount() {
        if (recalculationNeeded) {
            trueCellsCount = Long.bitCount(gridState[0]) + Long.bitCount(gridState[1]);
            recalculationNeeded = false;
        }
        return trueCellsCount;
    }

    /**
     * Creates and returns a deep copy of this {@code Grid} instance.
     * 
     * <p>
     * The cloned instance has its own copy of the grid state, ensuring that modifications to
     * the clone do not affect the original instance and vice versa. The method uses reflection to
     * create a new instance of the same class, allowing for proper cloning of subclasses without
     * needing to create overrides for each subclass.
     * </p>
     * 
     * @return A deep copy of this {@code Grid} instance.
     * @throws RuntimeException if the cloning process fails due to reflection issues.
     * @see #firstTrueCell
     * @see #gridState
     * @see #recalculationNeeded
     * @see #trueCellsCount
     * @see java.lang.Object#clone()
     * @see java.lang.Cloneable
     * @since 2025.03 - Cloning Introduction
     * @performance {@code O(1)} complexity.
     * @threading Thread-safe, as it returns a new, independent instance.
     * @memory Allocates a new {@code Grid} object and a new {@code long[2]} for its state.
     */
    public Grid clone() {
        try {
            Grid newGrid = this.getClass().getDeclaredConstructor().newInstance();
            // Copy bitmask state
            newGrid.gridState[0] = this.gridState[0];
            newGrid.gridState[1] = this.gridState[1];
            newGrid.trueCellsCount = this.trueCellsCount;
            newGrid.firstTrueCell = this.firstTrueCell;
            newGrid.recalculationNeeded = this.recalculationNeeded;
            return newGrid;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone Grid", e);
        }
    }

    /**
     * Determines if a click on a specified cell can affect or create a new first {@code true} cell in
     * the grid.
     * 
     * <p>
     * Since the solution must toggle the first {@code true} cell in the initial grid state, we can
     * optimize our search for the solution by pruning combinations whose first click cannot affect or
     * create a new first {@code true} cell. This method provides the logic to determine that.
     * </p>
     * 
     * <p>
     * There are several scenarios that this program considers:
     * <ul>
     * <li>If there are no {@code true} cells, any click can create one. In the context of our solver,
     * we shouldn't see this case happen too often, but it's included for short-circuiting
     * purposes.</li>
     * <li>If the first {@code true} cell is the top-left cell (0,0), only its adjacents can affect
     * it.</li>
     * <li>If the click is before or equal to the first {@code true} cell, it can create a new one.</li>
     * <li>If the click is adjacent to the first {@code true} cell, it can affect it.</li>
     * </ul>
     * If none of these conditions are met, the click cannot affect or create a new first {@code true}
     * cell.
     * </p>
     * 
     * @param firstTrueCell the first {@code true} cell in the specified format.
     * @param clickCell     the cell to be clicked, in the specified format.
     * @param format        the {@link ValueFormat format} of both cells ({@link ValueFormat#Index
     *                      Index} or {@link ValueFormat#PackedInt PackedInt}).
     * @return {@code true} if the click can affect or create a new first {@code true} cell,
     *         {@code false} otherwise.
     * @throws IllegalArgumentException if the format is {@link ValueFormat#Bitmask Bitmask} (since
     *                                  bitmasks aren't supported for single values) or another
     *                                  unsupported format.
     * @see #areAdjacent(short, short, ValueFormat)
     * @see #findAdjacents(short, ValueFormat)
     * @see #findFirstTrueCell(ValueFormat)
     * @since 2025.07 - Format and Adjacency Optimizations
     * @performance <code>O(log({@link #adjacencyArray}[firstTrueCell].length))</code>. Effectively
     *              constant time.
     * @threading This method is thread-safe as it is a pure function.
     * @optimization A specialized pruning helper for the generator. Uses an efficient binary search on
     *               the pre-sorted adjacency list.
     */
    public static boolean canAffectFirstTrueCell(short firstTrueCell, short clickCell, ValueFormat format) {
        if (firstTrueCell == -1) return true; // No true cells, any click can create one
        if (clickCell <= firstTrueCell) return true; // packed int order: row * 100 + col

        // Convert both cells to index format if necessary
        switch (format) 
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                firstTrueCell = packedToIndex(firstTrueCell);
                clickCell = packedToIndex(clickCell);
            case Index:
                // Already in index format, no conversion needed
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        
        // Edge case: first true cell is top-left (0,0)
        if (firstTrueCell == 0) 
        {
            short[] adj = findAdjacents((short) 0);
            if (adj == null || adj.length == 0) return false; // No adjacents, can't affect
            
            // Binary search for the click cell in the adjacents
            int low = 0, high = adj.length - 1;
            while (low <= high) 
            {
                int mid = (low + high) / 2;
                if (adj[mid] == clickCell) 
                {
                    return true; // Click cell is adjacent to the first true cell
                } else if (adj[mid] < clickCell) 
                {
                    low = mid + 1; // Search right
                } else 
                {
                    high = mid - 1; // Search left
                }
            }
            return false; // Click cell is not adjacent to the first true cell
        }

        // General case: check adjacency
        short[] adj = findAdjacents(firstTrueCell);
        if (adj == null || adj.length == 0) return false; // No adjacents, can't affect
        
        // perform binary search to find if clickCell is adjacent
        int low = 0, high = adj.length - 1;
        while (low <= high) 
        {
            int mid = (low + high) / 2;
            if (adj[mid] == clickCell) 
            {
                return true; // Click cell is adjacent to the first true cell
            } else if (adj[mid] < clickCell) 
            {
                low = mid + 1; // Search right
            } else 
            {
                high = mid - 1; // Search left
            }
        }
        return false; // Click cell is not adjacent to the first true cell
    }

    /**
     * Determines if two cells are adjacent to each other in the grid. Leverages the pre-computed
     * {@link #ADJACENCY_CACHE adjacency cache} for {@code O(1)} lookups.
     * 
     * <p>
     * Two cells are considered adjacent if they share a common edge or vertex. In a hexagonal grid,
     * each cell can have up to 6 adjacent cells, depending on its position in the grid. Since clicks
     * only affect adjacent cells, we rely on adjacency calculations to optimize our search for the
     * solution to a state, factoring these calculations into much of our logic. Therefore, we need a
     * {@code public} facing method to quickly determine adjacency between any two cells. This method
     * fills that role.
     * </p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method performs {@code O(1)} lookups in a pre-computed adjacency cache, making it extremely
     * fast. The overhead of converting between formats is likely larger than the lookup itself, so this
     * method should be used judiciously in performance-critical paths, or redesigned to assume a single
     * format if necessary.
     * </p>
     * 
     * @param cellA  the first cell to check, in the specified format.
     * @param cellB  the second cell to check, in the specified format.
     * @param format the {@link ValueFormat format} of both cells ({@link ValueFormat#Index Index} or
     *               {@link ValueFormat#PackedInt PackedInt}).
     * @return {@code true} if the cells are adjacent, {@code false} otherwise.
     * @throws IllegalArgumentException       if the format is {@link ValueFormat#Bitmask Bitmask}
     *                                        (since bitmasks aren't supported for single values) or
     *                                        another unsupported format.
     * @throws ArrayIndexOutOfBoundsException if either cell is out of bounds (implicitly checked by the
     *                                        array access).
     * @see #ADJACENCY_CACHE
     * @see #areAdjacent(short, short)
     * @see #canAffectFirstTrueCell(short, short, ValueFormat)
     * @since 2025.07 - Index Format Usage
     * @performance {@code O(1)} complexity.
     * @threading Thread-safe, as it only reads from immutable static data.
     * @optimization Directly uses the {@link #ADJACENCY_CACHE} for an instantaneous lookup after
     *               handling any necessary format conversion.
     */
    public static boolean areAdjacent(short cellA, short cellB, ValueFormat format) {
        // Convert both cells to index format if necessary
        switch (format)
        {
            case Bitmask:
                throw new IllegalArgumentException("Bitmask format is not supported for representing a single cell.");
            case PackedInt:
                cellA = packedToIndex(cellA);
                cellB = packedToIndex(cellB);
            case Index:
                // Already in index format, no conversion needed
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        return ADJACENCY_CACHE[cellA][cellB];
    }

    /**
     * Overload of {@link #areAdjacent(short, short, ValueFormat)} that assumes the input format is
     * {@link ValueFormat#Index Index}. This exists purely for convenience, as it simply calls the main
     * method with the {@code Index} format.
     * 
     * @param cellA the first cell to check, in {@link ValueFormat#Index Index} format (0-108).
     * @param cellB the second cell to check, in {@link ValueFormat#Index Index} format (0-108).
     * @return {@code true} if the cells are adjacent, {@code false} otherwise.
     * @throws ArrayIndexOutOfBoundsException if either cell is out of bounds (implicitly checked by the
     *                                        array access).
     * @since 2025.06 - Odd Adjacency Pruning
     * @performance {@code O(1)} lookup.
     * @threading Thread-safe.
     * @optimization The most direct way to check adjacency, assuming {@link ValueFormat#Index} format.
     */
    public static boolean areAdjacent(short cellA, short cellB) {
        return areAdjacent(cellA, cellB, ValueFormat.Index);
    }

    /**
     * Returns a copy of the current {@link #gridState grid state} as a bitmask (array of two
     * {@code long}s).
     * 
     * <p>
     * This method exists to provide direct access to the grid's internal state. While no current code
     * paths leverage this method (in part due to the allocation it requires), it may be useful for
     * debugging, logging, or future features that require direct manipulation or inspection of the grid
     * state.
     * </p>
     * 
     * @return A copy of the current grid state as a bitmask (array of two {@code long}s).
     * @see #click(long[])
     * @see ValueFormat#Bitmask
     * @since 2025.07 - Bitmasked Grid State
     * @performance {@code O(1)} complexity.
     * @threading The output is thread-safe, since it is a copy of the internal state.
     * @memory Allocates a new {@code long[2]} array to prevent external modification of the instance's
     *         internal state.
     */
    public long[] getGridState() {
        return gridState.clone();
    }

    /**
     * Prints the current grid state to the {@link LogManager#getLogger() logger} in a human-readable
     * format, with '1' representing {@code true} cells and '0' representing {@code false} cells. Rows
     * are indented to reflect the hexagonal layout, matching the format used in the PDC puzzle code.
     * 
     * @see #EVEN_NUM_COLS
     * @see #NUM_ROWS
     * @see #ODD_NUM_COLS
     * @see #getBit(int)
     * @see #indexToPacked(short)
     * @see #packedToIndex(short)
     * @see ValueFormat#Index
     * @see ValueFormat#PackedInt
     * @see java.lang.StringBuilder
     * @see org.apache.logging.log4j.Logger
     * @see org.apache.logging.log4j.LogManager
     * @since 2025.03 - Grid Printing Method Creation
     * @performance <code>O({@value Grid#NUM_CELLS})</code>. This is a debugging method and is not
     *              performance-critical.
     * @threading Not thread-safe, as it iterates over the mutable grid state.
     * @memory Allocates a new {@link StringBuilder} for each row.
     * @optimization Uses a {@link StringBuilder} for efficient string concatenation within each row.
     */
    public void printGrid() {
        Logger logger = LogManager.getLogger();
        for (int row = 0; row < NUM_ROWS; row++) {
            StringBuilder sb = new StringBuilder();
            if (row % 2 != 0) sb.append(" ");
            int cols = (row % 2 == 0) ? EVEN_NUM_COLS : ODD_NUM_COLS;
            for (int col = 0; col < cols; col++) {
                int bitIdx = packedToIndex((short) (row * 100 + col));
                sb.append(getBit(bitIdx) ? "1 " : "0 ");
            }
            logger.info(sb.toString());
        }
    }
}
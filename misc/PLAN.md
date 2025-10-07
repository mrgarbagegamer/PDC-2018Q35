# AI Prompt for Javadoc Revision (SIGMA Method)

## Scope

**Your Task:** As an Orchestrator, your primary role is to manage the revision of Javadoc comments for a high-performance Java-based "Lights Out" puzzle solver. You will achieve this by breaking down the task and delegating the actual editing to subtasks.

**Primary Goal:** Improve the clarity, consistency, and conciseness of existing documentation while preserving the original technical tone and intent.

**Your Behavior:**
- You will identify all relevant Java source files in the project that require documentation revision.
- For each file, you will create a separate subtask, delegating the responsibility of revising its Javadoc comments.
- You will provide each subtask with the full context and guidance necessary to perform its work independently.
- You will monitor the completion of each subtask.

**Subtask Behavior (for the delegated models):**
- **Revise Javadoc:** The subtask will review and rewrite existing Javadoc comments (`/** ... */`) for a single designated Java file to improve clarity and conciseness.
- **Preserve Information:** The subtask will retain and summarize key information, including design decisions, performance optimizations, and algorithmic details.

**Non-Behaviors (What all models MUST NOT do):**
- **Do NOT edit any Java code.** This is a documentation-only task.
- **Do NOT process the `test.java` file** or any non-source files.
- **Do NOT add or remove Javadoc comments wholesale.** The focus is on revising *existing* comments.

## Intent

I am the author of this codebase and have already written the initial Javadoc comments. I want to leverage your language capabilities to refine them. My goal is to make the documentation more polished and professional for future maintainers and for my own reference. I will be reviewing the changes from each subtask to ensure they meet my standards before they are applied.

## Guidance

**1. Core Principles for Revision:**
- **Tone:** Maintain the existing technical and informative tone. You are refining my writing, not replacing it.
- **Summarize & Clarify:** Your main goal is to communicate the same information more effectively. Condense verbose sections and clarify complex points, as shown in the example below.
- **Tag Consistency:** While revising, ensure Javadoc tags remain in a logical order, but prioritize the quality of the comment text over rigid tag sorting.

**2. Key Context Summary:**
- **Project:** A brute-force solver for a hexagonal Lights Out puzzle.
- **Core Architecture:** Uses ForkJoinPool for work-stealing and a custom batching system (`WorkBatch`) to minimize queue contention.
- **Performance-Critical:** The code is heavily optimized. Key techniques include:
    - **Bitmask State:** Grid state is represented by a `long[2]` array for ultra-fast `XOR` operations.
    - **Object Pooling:** `ArrayPool`, `TaskPool`, and `WorkBatch` pools are used to minimize GC pressure.
    - **ThreadLocal Context:** A `GeneratorContext` consolidates per-thread resources.
- **Click Rule:** A crucial puzzle variant is that clicking a cell toggles its neighbors, *not* the cell itself.

**3. Example of an Excellent Revision (focus on `Grid.java` class-level docs):**

*This example demonstrates how to take a lengthy, detailed comment and distill it into a clearer, more concise, and better-structured version without losing critical information.*

*   **Original Javadoc (Verbose and less structured):**
    ```java
    /**
     * Grid - Abstract class representing our hexagonal Lights Out grid.
     * 
     * <p>
     * Our puzzle is a hexagonal grid of lights, where each light can be either on or off. Clicking a
     * cell flips the state of that cell and its adjacent cells, with the goal of the puzzle being to
     * turn off all the lights. Building a high-performance solver for this puzzle requires an efficient
     * representation of the grid and fast operations for clicking cells and checking the grid state.
     * This class aims to provide that functionality.
     * </p>
     * 
     * <h2>Static Initialization</h2>
     * <p>
     * A key component to the performance of this class and its methods is the static initialization
     * block, which pre-computes adjacency masks and arrays for each cell in the grid, allowing for O(1)
     * lookups during runtime. This block is executed once when the class is loaded, allowing each
     * instance of the Grid to share the benefits without incurring the cost repeatedly.
     * </p>
     * 
     * <p>
     * The static block consists of a for loop that iterates across each cell in the grid (0-108 in
     * {@link ValueFormat#Index Index} format). For each cell, it
     * {@link #computeAdjacents(short, ValueFormat, ValueFormat) computes the adjacent cells} as a
     * {@link ShortList}, which then gets converted to a short array and stored in the
     * {@link #adjacencyArray adjacency array} for use in the
     * {@link #findAdjacents(short, ValueFormat, ValueFormat) findAdjacents()} method.
     * {@link #ADJACENCY_CACHE A 2D table} is created from this data, allowing for easy adjacency checks
     * between two values. We also build a bitmask that provides another form of adjacency
     * representation, storing it in the {@link #ADJACENCY_MASKS adjacency masks} array for use in the
     * {@link #click(short, ValueFormat) click()} method. Finally, we populate
     * {@link #PACKED_TO_INDEX_CACHE a cache} for {@link ValueFormat#PackedInt PackedInt} to
     * {@link ValueFormat#Index Index} conversions. All of these structures are then statically assigned
     * as the block moves to the next cell.
     * </p>
     * 
     * <h2>Architecture Role</h2>
     * <p>
     * The Grid class serves as the foundational representation of the puzzle's state and operations,
     * providing the infrastructure for efficient manipulation and querying of the grid. It is designed
     * to be extended by concrete implementations that define specific {@link #gridState initial states}
     * and {@link #initialize() initialization procedures} for each puzzle (e.x. Q13, Q22, Q35).
     * </p>
     * 
     * <p>
     * {@link TestClickCombination Monkeys} utilize this class to test various click combinations, with
     * each receiving its own instance of a Grid to operate on. However, re-initializing the grid state
     * can be costly, so it is recommended to prune combinations before testing against the grid.
     * {@link CombinationGeneratorTask Generators} are designed to produce combinations efficiently and
     * thus perform light checks before passing them to the monkeys, limiting the interactions they have
     * with this class. This separation of concerns allows for a more focused codebase and clarifies
     * areas of bottlenecks; optimizing this class will primarily benefit the monkeys rather than the
     * generators.
     * </p>
     * 
     * <h2>Performance Characteristics</h2>
     * <p>
     * We aim for O(1) complexity for core operations like {@link #click(short, ValueFormat) clicking a
     * cell} and {@link #isSolved() checking for the solved state}, with most of the complexity hidden
     * in the static initialization block. Due to the nature of objects in Java (which allocate on the
     * heap rather than the stack), primitive types and arrays are used wherever possible to minimize
     * overhead, with bitmasks allowing for compact storage and fast operations. Sadly, Java lacks a
     * native 128-bit primitive type, making every operation on our 109-cell grid require 2
     * modifications or polls rather than 1. The Vector API seemed promising, but it allocates terribly
     * (4 allocations per lanewise operation), incurring massive overhead. {@link java.util.BitSet
     * BitSets} were considered, but they incur unnecessary overhead in terms of object headers, making
     * them less efficient than our custom bitmask approach.
     * </p>
     * 
     * <p>
     * Future optimizations could include exploring off-heap storage options or finding ways to densely
     * pack the grid state into a single primitive type, but these would require some heavy ingenuity.
     * Unless Project Valhalla magically gets released soon, we are likely nearing the limits of what
     * Java can do, potentially forcing us to look at other languages to push performance further.
     * </p>
     * 
     * <h2>Thread Safety</h2>
     * <p>
     * This class is <b>not</b> thread-safe. Each instance of a Grid should only be accessed from a
     * single thread or with proper synchronization. The static members are immutable after
     * initialization and are thus safe to share across threads, but instance members like
     * {@link #click(long[])} and {@link #gridState} could have weird side effects if accessed
     * concurrently. We trade off thread-safety and potentially lower memory usage for performance, as
     * avoiding synchronization and locks allows for faster operations in the hot path.
     * </p>
     */
    ```

*   **Improved Javadoc (Your Goal - Concise, Clear, and Well-Formatted):**
    ```java
    /**
     * A structure that represents the core hexagonal grid for the Lights Out-style puzzle.
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
     * {@link #ADJACENCY_MASKS}, enabling O(1) complexity for the critical {@code click}
     * operation.
     * </p>
     *
     * <p>
     * Several alternatives were evaluated:
     * </p>
     * <ul>
     * <li><b>{@link java.util.BitSet}</b>: Incurs unacceptable overhead from object headers and 
     * indirect memory access compared to a primitive array.</li>
     * <li><b>Panama/Vector API</b>: Showed promise for 128-bit operations but suffered from excessive 
     * memory allocation (4+ allocations per lanewise operation in tested JDK versions), making it
     * unsuitable for the hot path.</li>
     * </ul>
     * The primitive {@code long[]} array was ultimately chosen as the most performant solution on the
     * modern JVM, despite the complexity of managing two separate {@code long}s.
     * 
     * <h2>Future Optimizations</h2>
     * <p>
     * Further performance gains are likely limited by the JVM itself. Potential aveneus for exploration
     * include off-heap memory storage or the availability of true 128-bit primitives in a future Java
     * version (e.g., via Project Valhalla), which would simplify the bitmask logic to a single
     * {@code long}.
     * </p>
     *
     * <h2>Thread Safety</h2>
     * <p>
     * This class is <strong>not</strong> thread-safe. Each instance is designed to be used by a
     * single thread. State-modifying methods like {@link #click(short)} are unsynchronized to maximize
     * performance. Static members are effectively immutable after initialization and are safe to be
     * shared across threads.
     * </p>
     */
    ```

## Monitor

I will review the Javadoc revisions proposed by each subtask. I will be checking for:
- Adherence to the "Non-Behaviors."
- Preservation of the original tone and critical technical details.
- Improved clarity and conciseness, as demonstrated in the example.

## Autonomy

As the Orchestrator, your first step is to list the files and begin creating subtasks. Each subtask will have all the necessary context from this prompt to begin its work. A subtask's success is confirmed when I approve its proposed Javadoc revisions.
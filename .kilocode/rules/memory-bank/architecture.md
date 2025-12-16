# Architecture Overview

## Core Design

The solver is built on a **producer-consumer** architecture to maximize parallelism and efficiently search the vast solution space. This design separates the work of generating potential solutions from the work of testing them.

- **Producers**: `com.github.mrgarbagegamer.CombinationGeneratorTask` instances running within a `java.util.concurrent.ForkJoinPool`.
- **Consumers**: `com.github.mrgarbagegamer.TestClickCombination` instances (referred to as "monkeys"), each running in its own thread.
- **Communication**: A custom queueing system, managed by the `com.github.mrgarbagegamer.CombinationQueueArray` singleton, acts as the high-performance bounded buffer between producers and consumers. The unit of exchange is the `WorkBatch`.

## Key Components

### 1. `StartYourMonkeys.GlobalConfig`

- **Path**: `src/main/java/com/github/mrgarbagegamer/StartYourMonkeys.java`
- **Role**: The central, immutable, single source of truth for all startup and derived configurations.
- **Implementation**:
  - An inner class within `StartYourMonkeys` that uses the Java 25 `StableValue` API for thread-safe, lazy initialization.
  - It holds two types of data:
    - **Core Configuration**: `NUM_CLICKS`, `NUM_THREADS`, `BASE_GRID`. These are set once at application startup.
    - **Derived Configuration**: `TRUE_CELLS`, `CLICK_TO_TRUE_CELL_MASK`, `EXPECTED_MASK`, `ODD_CLICK_INDICES`, `EVEN_CLICK_INDICES`. These values are computed lazily and safely on first access using `StableValue.supplier`.
  - This class eliminates the need for manual configuration passing and `volatile` fields throughout the application, simplifying component initialization and improving performance.

### 2. `StartYourMonkeys.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/StartYourMonkeys.java`
- **Role**: The main application entry point and orchestrator.
- **Responsibilities**:
  - Parses command-line arguments.
  - **Initializes the `GlobalConfig` with the core configuration.**
  - Initializes the `CombinationQueueArray` singleton (which now pulls its config from `GlobalConfig`).
  - Sets up and starts the consumer thread pool ("monkeys").
  - Configures and starts the `ForkJoinPool` for the producers.
  - Submits the root `CombinationGeneratorTask` to begin the search.
  - Manages graceful shutdown and reports the final result.

### 3. `Grid.java` (and its subclasses)

- **Path**: `src/main/java/com/github/mrgarbagegamer/Grid.java`
- **Role**: The foundational data structure representing the hexagonal puzzle grid.
- **Implementation**:
  - An `abstract` class with concrete implementations for each puzzle (`Grid13`, `Grid22`, `Grid35`).
  - Uses a `long[2]` array as a 128-bit bitmask to represent the state of the 109 cells, enabling extremely fast, cache-friendly state manipulations via bitwise operations.
  - Adjacency information is pre-computed into static caches and masks (`ADJACENCY_MASKS`, `ADJACENCY_CACHE`) during class loading to make the critical `click` operation an `O(1)` XOR.
  - **Not thread-safe**: Each `Grid` instance is designed to be used by a single thread.

### 4. `CombinationGeneratorTask.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/CombinationGeneratorTask.java`
- **Role**: The "producer" that generates click combinations.
- **Implementation**:
  - A `java.util.concurrent.RecursiveAction` that runs within a `ForkJoinPool`.
  - Recursively breaks down the problem of finding `k` clicks among `n` cells.
  - **Simplified by `GlobalConfig`**: No longer holds configuration data. It relies entirely on the refactored, self-configuring `WorkBatch` API and `GlobalConfig`.
  - Employs significant pruning logic to avoid exploring invalid branches of the search tree.
  - Generates compact `WorkItem` objects that describe a *range* of combinations and submits them in a `WorkBatch`.

### 5. `TestClickCombination.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/TestClickCombination.java`
- **Role**: The "consumer" or "monkey" that validates solutions.
- **Implementation**:
  - A `Thread` that runs in a loop, pulling `WorkBatch` objects from its dedicated queue.
  - **Hot Path Optimization**: No longer has `volatile` fields. It caches derived data (like `MASKS` and `EXPECTED`) from `GlobalConfig` into `static final` fields at class load time. This allows the JVM to perform constant-folding optimizations in the critical validation loop.
  - Its constructor is now parameter-less and its initialization is greatly simplified.
  - For each `WorkItem`, it calculates the parity mask of the prefix *once* and then iterates through the range of final clicks, performing a cheap, optimized odd-adjacency check for each one.
  - If a solution is found, it signals the `CombinationQueueArray` to stop the entire process.

### 6. `CombinationQueueArray.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/CombinationQueueArray.java`
- **Role**: A singleton that orchestrates work distribution and state management.
- **Implementation**:
  - **Modernized Singleton**: The singleton instance is now managed by a `StableValue.supplier` for clean, thread-safe, and parameter-less initialization via `getInstance()`. The old double-checked locking and `volatile` field have been removed.
  - Holds an array of `CombinationQueue` instances, one for each consumer thread, to partition work and reduce contention.
  - Manages a central, pre-allocated pool (`workBatchPool`) of `WorkBatch` objects to eliminate GC pressure by recycling them.
  - Controls the application lifecycle through `volatile` flags (`solutionFound`, `generationComplete`).

### 7. `WorkBatch.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/WorkBatch.java`
- **Role**: A reusable container for batching work items.
- **Implementation**:
  - A container for `WorkItem` objects, where each `WorkItem` represents a range of combinations.
  - **Automatic Initialization**: No longer requires manual setup. It initializes its `ODD/EVEN_CLICK_INDICES` arrays as `static final` constants by pulling from `GlobalConfig` at class load time.
  - Implements the `Iterable` interface, providing a custom, allocation-free iterator that assembles the final combinations on-the-fly for the consumer.
  - Instances are recycled via a central pool in `CombinationQueueArray` to achieve near-zero GC pressure.

## Data Flow

1. **`StartYourMonkeys`** parses arguments and **initializes `GlobalConfig`**.
2. `StartYourMonkeys` calls **`CombinationQueueArray.getInstance()`**, which lazily initializes the singleton, pulling its required configuration from `GlobalConfig`.
3. **`TestClickCombination`** threads are started. At class load time, they cache necessary derived data from `GlobalConfig` into `static final` fields. The threads then block, waiting for work.
4. `StartYourMonkeys` submits the root **`CombinationGeneratorTask`** to the `ForkJoinPool`.
5. The `CombinationGeneratorTask` recursively forks. Producers poll for an empty `WorkBatch` from the `CombinationQueueArray`'s central pool.
6. Producers fill a `WorkBatch` with `WorkItem` ranges and offer it to one of the work queues.
7. `TestClickCombination` threads wake up, consume the batches, and use the batch's iterator to efficiently test all logical combinations described by the `WorkItem`s, using their pre-cached constants for maximum performance.
8. After processing, the consumer clears the `WorkBatch` and returns it to the central `workBatchPool` for reuse.
9. The process continues until a solution is found or all combinations have been tested.

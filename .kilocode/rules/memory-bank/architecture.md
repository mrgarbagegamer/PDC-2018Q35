# Architecture Overview

## Core Design

The solver is built on a **producer-consumer** architecture to maximize parallelism and efficiently search the vast solution space. This design separates the work of generating potential solutions from the work of testing them.

- **Producers**: `com.github.mrgarbagegamer.CombinationGeneratorTask` instances running within a `java.util.concurrent.ForkJoinPool`.
- **Consumers**: `com.github.mrgarbagegamer.TestClickCombination` instances (referred to as "monkeys"), each running in its own thread.
- **Communication**: A custom queueing system, managed by the `com.github.mrgarbagegamer.CombinationQueueArray` singleton, acts as the high-performance bounded buffer between producers and consumers. The unit of exchange is the `WorkBatch`.

## Key Components

### 1. `StartYourMonkeys.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/StartYourMonkeys.java`
- **Role**: The main application entry point and orchestrator.
- **Responsibilities**:
  - Parses command-line arguments.
  - Initializes the `Grid` instance for the selected puzzle.
  - Initializes the `CombinationQueueArray` singleton.
  - Sets up and starts the consumer thread pool ("monkeys").
  - Configures and starts the `ForkJoinPool` for the producers.
  - Submits the root `CombinationGeneratorTask` to begin the search.
  - Manages graceful shutdown and reports the final result.

### 2. `Grid.java` (and its subclasses)

- **Path**: `src/main/java/com/github/mrgarbagegamer/Grid.java`
- **Role**: The foundational data structure representing the hexagonal puzzle grid.
- **Implementation**:
  - An `abstract` class with concrete implementations for each puzzle (`Grid13`, `Grid22`, `Grid35`).
  - Uses a `long[2]` array as a 128-bit bitmask to represent the state of the 109 cells, enabling extremely fast, cache-friendly state manipulations via bitwise operations.
  - Adjacency information is pre-computed into static caches and masks (`ADJACENCY_MASKS`, `ADJACENCY_CACHE`) during class loading to make the critical `click` operation an `O(1)` XOR.
  - **Not thread-safe**: Each `Grid` instance is designed to be used by a single thread.

### 3. `CombinationGeneratorTask.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/CombinationGeneratorTask.java`
- **Role**: The "producer" that generates click combinations.
- **Implementation**:
  - A `java.util.concurrent.RecursiveAction` that runs within a `ForkJoinPool`.
  - Recursively breaks down the problem of finding `k` clicks among `n` cells.
  - Employs significant pruning logic to avoid exploring invalid branches of the search tree.
  - **Leaf Generation Optimization**: The hot path for leaf-level combination generation has been redesigned to offload work from the producer to the consumer. Instead of generating and copying millions of individual combinations, the generator now creates a compact {@link com.github.mrgarbagegamer.WorkBatch.WorkItem WorkItem} that describes a *range* of combinations (a common prefix plus a range of final clicks). This drastically reduces the producer's workload.
  - These {@code WorkItem}s are added to a {@code WorkBatch}, which is then submitted to the {@code CombinationQueueArray}.

### 4. `TestClickCombination.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/TestClickCombination.java`
- **Role**: The "consumer" or "monkey" that validates solutions.
- **Implementation**:
  - A `Thread` that runs in a loop, pulling `WorkBatch` objects from its dedicated queue in `CombinationQueueArray`.
  - The `WorkBatch` is now an `Iterable` of `WorkItem`s. The monkey iterates through these `WorkItem`s.
  - For each `WorkItem`, it calculates the parity mask of the prefix *once* and then iterates through the range of final clicks, performing a cheap, optimized odd-adjacency check for each one.
  - Only if the cheap check passes does it assemble the full combination and test it against the grid. This avoids millions of expensive grid operations.
  - If a solution is found, it signals the `CombinationQueueArray` to stop the entire process.

### 5. `CombinationQueue.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/CombinationQueue.java`
- **Role**: A thin, high-performance, type-safe wrapper for a work queue.
- **Implementation**:
  - Wraps a JCTools `MpmcArrayQueue` (Multi-Producer Multi-Consumer).
  - Designed to transfer `WorkBatch` objects between producers and consumers.
  - Uses relaxed (non-blocking) offers and polls for maximum throughput.
  - The queue is bounded and lock-free.

### 6. `CombinationQueueArray.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/CombinationQueueArray.java`
- **Role**: A singleton that orchestrates work distribution and state management.
- **Implementation**:
  - Holds an array of `CombinationQueue` instances, one for each consumer thread, to partition work and reduce contention.
  - Manages a central, pre-allocated pool (`workBatchPool`) of `WorkBatch` objects to eliminate GC pressure by recycling them.
  - Controls the application lifecycle through `volatile` flags (`solutionFound`, `generationComplete`) to signal termination to all threads.
  - Records the final solution and performance metrics.

### 7. `WorkBatch.java`

- **Path**: `src/main/java/com/github/mrgarbagegamer/WorkBatch.java`
- **Role**: A reusable container for batching combinations.
- **Implementation**:
  - A container for {@link com.github.mrgarbagegamer.WorkBatch.WorkItem} objects. Each {@code WorkItem} represents a range of combinations (a shared prefix and a set of final clicks).
  - Implements the {@link java.lang.Iterable} interface, providing a custom, allocation-free iterator that assembles the final combinations on-the-fly for the consumer.
  - This design offloads the final enumeration of combinations from the producer to the consumer, significantly reducing the producer's workload.
  - Instances are pre-allocated and recycled via a central pool in `CombinationQueueArray` to achieve near-zero GC pressure.
  - **Not thread-safe**: Ownership is transferred between threads exclusively via the queue system.

## Data Flow

1. `StartYourMonkeys` initializes all components, including the `CombinationQueueArray` singleton.
2. `TestClickCombination` threads start and block, waiting for work on their dedicated `CombinationQueue`.
3. `StartYourMonkeys` submits the root `CombinationGeneratorTask` to the `ForkJoinPool`.
4. The `CombinationGeneratorTask` recursively forks. Producers poll for an empty `WorkBatch` from the central `workBatchPool`.
5. Producers fill a `WorkBatch` with `WorkItem` ranges and offer it to one of the work queues.
6. `TestClickCombination` threads wake up, consume the batches, and use the batch's iterator to efficiently test all logical combinations described by the `WorkItem`s.
7. After processing, the consumer clears the `WorkBatch` and returns it to the central `workBatchPool` for reuse.
8. The process continues until a solution is found (and `solutionFound` is flagged) or all combinations have been tested (and `generationComplete` is flagged).

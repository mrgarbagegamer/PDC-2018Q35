# JMH Benchmark Suite Proposal (Revised)

This document outlines the plan to integrate the Java Microbenchmark Harness (JMH) into the `pdc-2018q35` project. The goal is to replace manual, coarse-grained timing with precise, reproducible microbenchmarks for critical hot paths.

## 1. Directory Structure: Multi-Module Maven Project

The project uses a **multi-module Maven structure** with three modules:

```text
pdc-2018q35/                 <-- Root project (parent POM)
  pom.xml                    <-- Parent POM with <modules> declaration
  core/                      <-- Core solver module
    pom.xml                  <-- Core module POM
    src/
      main/java/com/github/mrgarbagegamer/
        StartYourMonkeys.java
        Grid.java
        CombinationGeneratorTask.java
        TestClickCombination.java
        WorkBatch.java
        ... (all core solver classes)
      test/java/
        ... (JUnit tests)
  benchmarks/                <-- JMH benchmark module
    pom.xml                  <-- Benchmark module POM
    src/
      main/java/com/github/mrgarbagegamer/
        GridBenchmark.java
        MonkeyBenchmark.java
        GeneratorBenchmark.java
        GeneratorThroughputBenchmark.java
        WorkBatchBenchmark.java
        QueueBenchmark.java
        PoolBenchmark.java
```

### Key Design Decisions

1. **Same Package Structure**: Benchmarks use `com.github.mrgarbagegamer` (not `benchmarks` subpackage) to access package-private methods in core classes.
2. **Dependency**: `benchmarks/pom.xml` depends on the `core` module artifact.
3. **Build Simplification**: Running `mvn clean install` at the root builds both modules in the correct order.

## 2. Maven Configuration

### Parent POM (`pom.xml`)

The root `pom.xml` declares:

```xml
<modules>
    <module>core</module>
    <module>benchmarks</module>
</modules>
```

This ensures Maven builds modules in dependency order (`core` before `benchmarks`).

### Benchmarks Module (`benchmarks/pom.xml`)

The `benchmarks/pom.xml`:

- Declares parent as the root project
- Depends on `pdc-2018q35-core` (the core module artifact)
- Includes JMH dependencies (`jmh-core`, `jmh-generator-annprocess`)
- Uses `maven-shade-plugin` to generate the executable `benchmarks.jar`

### Build & Run Commands

**From root directory:**

```bash
# Build all modules
mvn clean install -DskipTests

# Run all benchmarks
java -jar benchmarks/target/benchmarks.jar

# Run specific benchmark
java -jar benchmarks/target/benchmarks.jar GridBenchmark

# Run with JMH options (e.g., fewer iterations for quick testing)
java -jar benchmarks/target/benchmarks.jar -wi 2 -i 3 -f 1
```

**From benchmarks directory:**

```bash
# Build only benchmarks (requires core already installed)
mvn clean package

# Run benchmarks
java -jar target/benchmarks.jar
```

### JMH CLI Options Reference

| Option | Description | Example |
| --- | --- | --- |
| `-wi N` | Warmup iterations | `-wi 3` |
| `-i N` | Measurement iterations | `-i 5` |
| `-f N` | Number of forks | `-f 3` |
| `-t N` | Number of threads | `-t 4` |
| `-prof <profiler>` | Enable profiler | `-prof gc` (GC stats) |
| `-rf <format>` | Result format | `-rf json` |
| `-rff <file>` | Result file | `-rff results.json` |

## 3. Benchmark Classes Design

### A. `GridBenchmark`

**Goal:** Measure the raw performance of critical `Grid` operations.

- **State:** A pre-initialized `Grid35` instance.
- **Benchmarks:**
  - `click_short_array`: Measure `grid.click(short[])`.
  - `click_short_array_short_val`: Measure the critical `grid.click(short[], short)` overload.
  - `initialize`: Measure `grid.initialize()` (resetting state).
  - `isSolved`: Measure the solution check.
  - `getTrueCount`: Measure population count (considering the dirty flag optimization).

### B. `MonkeyBenchmark`

**Goal:** Measure the "hot path" of the consumer threads (`TestClickCombination`).

- **State:** Pre-computed `prefix` array, `finalClicks` array, and `MASKS` loaded.
- **Benchmarks:**
  - `buildParityMask`: Measure the loop that XORs the prefix mask.
  - `satisfiesOddAdjacency`: Measure the critical `(prefixMask ^ mask) == EXPECTED` check.
  - `simulateLogging`: Measure the overhead of constructing a log message (e.g., `CombinationMessage` creation and string formatting) to quantify logging cost.

### C. `QueueBenchmark`

**Goal:** Measure contention and throughput of the queue system.

- **Use Case:** Compare JCTools `MpmcArrayQueue` vs. other implementations (like Conversant Disrupter) in the future.
- **Setup:** Use JMH's `@Group` and `@GroupThreads` to simulate multiple producers and consumers.
- **Benchmarks:**
  - `producerConsumerThroughput`:
    - `group("q") @GroupThreads(8)` Producers calling `queue.add()`.
    - `group("q") @GroupThreads(8)` Consumers calling `queue.poll()`.

### D. `PoolBenchmark`

**Goal:** Measure the overhead of the custom object pools.

- **State:** `ArrayPool` and `TaskPool` instances.
- **Benchmarks:**
  - `arrayPool_get_put`: Measure the round-trip latency of getting and returning an array.
  - `taskPool_get_put`: Measure the round-trip latency of getting and returning a task.

### E. `GeneratorBenchmark`

**Goal:** Measure the pruning logic of `CombinationGeneratorTask`.

#### Design Considerations

The `CombinationGeneratorTask` presents unique benchmarking challenges:

1. **Root Task State**: The root task has `cachedAdjacencyState = -1`, which is an invalid state for `canPotentiallySatisfyConstraints()`. This method is designed to be called only by intermediate tasks.

2. **Two-Path Pruning Check**: The `canPotentiallySatisfyConstraints()` method has distinct execution paths:
   - **Fast Path (`needed == 0L`)**: When the prefix already toggles all required true cells, the method sets `skipConstraintsCheck = true` and returns immediately. This is a ~3-operation path.
   - **Slow Path (`needed != 0L`)**: Performs a `SUFFIX_OR_MASKS[startIdx]` lookup and bitwise comparison to determine if remaining cells can satisfy constraints.

3. **Forking Side Effects**: The `computeIntermediateSubtasksSkipPath()` and `computeIntermediateSubtasksConstraintPath()` methods call `subtask.fork()`, making them unsuitable for microbenchmarking. Each iteration would spawn real ForkJoinPool tasks, polluting results with pool scheduling overhead and contention.

#### Benchmark Design

- **State Setup:**
  - Create a task via `CombinationGeneratorTask.createRootTask()`, then manually modify it to represent an intermediate task state using the `init()` method.
  - For fast-path: Set `cachedAdjacencyState` equal to `EXPECTED_MASK` (all constraints satisfied).
  - For slow-path: Set `cachedAdjacencyState` to a partial mask (some constraints not yet satisfied).

- **Benchmarks:**
  - `canPotentiallySatisfyConstraints_FastPath`: Measure the early-exit path when `needed == 0L`. This isolates the cost of the XOR check and flag setting.
  - `canPotentiallySatisfyConstraints_SlowPath`: Measure the full pruning check including the suffix mask lookup and comparison.

#### Non-Benchmarkable Components

The following methods are **not suitable for microbenchmarking** due to their reliance on the ForkJoinPool infrastructure:

- `computeIntermediateSubtasksSkipPath()`: Contains `subtask.fork()` calls.
- `computeIntermediateSubtasksConstraintPath()`: Contains `subtask.fork()` calls.
- `computeRootSubtasks()`: Contains both `subtask.fork()` and `helpQuiesce()` calls.

### E.2 `GeneratorThroughputBenchmark` (Macro-Benchmark Alternative)

Since the forking methods cannot be isolated as microbenchmarks, we propose a **macro-benchmark** that measures end-to-end generator throughput. This provides a holistic view of generator performance changes.

#### Design Philosophy

Unlike microbenchmarks that isolate individual operations, this benchmark runs the **entire producer-consumer pipeline** for a fixed, short search problem. This captures:

- Fork/join scheduling overhead
- Object pool efficiency (ArrayPool, TaskPool)
- Work batch throughput
- Queue contention between generators and consumers

#### Configuration Strategy

To keep benchmark iterations short (seconds, not hours), we use a **smaller puzzle configuration**:

| Parameter | Production Value | Benchmark Value | Rationale |
| --- | --- | --- | --- |
| `numClicks` | 17 | 7 | Dramatically smaller search space |
| `numThreads` | 16 | 8 | Controlled, reproducible thread count |
| `puzzle` | Grid35 | Grid13 | Simplest puzzle, fastest initialization |

With `numClicks=7` on Grid13, the solution is found quickly, making each benchmark iteration fast while still exercising the full code path.

#### Macrobenchmark Design

```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(3)
public class GeneratorThroughputBenchmark {
    
    @Setup(Level.Trial)
    public void initializeGlobalConfig() {
        // Initialize GlobalConfig with benchmark parameters
        // Note: Need to handle StableValue re-initialization across forks
    }
    
    @Setup(Level.Iteration)
    public void resetRuntimeState() {
        // Reset CombinationQueueArray state
        // Clear any previous solution flags
    }
    
    @Benchmark
    public long runFullPipeline() {
        // 1. Create ForkJoinPool with benchmark thread count
        // 2. Start monkey threads
        // 3. Invoke root CombinationGeneratorTask
        // 4. Wait for completion (solution found or exhausted)
        // 5. Return elapsed time from CombinationQueueArray
    }
    
    @TearDown(Level.Iteration)
    public void shutdownResources() {
        // Shutdown ForkJoinPool
        // Stop monkey threads
    }
}
```

#### Key Metrics

The macro-benchmark captures multiple metrics simultaneously:

1. **Total Runtime**: End-to-end time from `ForkJoinPool.invoke()` to solution/completion.
2. **Throughput Proxy**: Since `CombinationQueueArray` tracks timing via `getStartTime()`/`getEndTime()`, we get consistent internal measurements.
3. **Solution Consistency**: Verifies that the same solution is found across iterations (sanity check).

#### Challenges and Mitigations

| Challenge | Mitigation |
| --------- | ---------- |
| **StableValue Re-initialization**: `GlobalConfig` uses `StableValue.setOrThrow()` which only allows one initialization | Run each JMH fork as a fresh JVM (`@Fork(3)`) so each fork gets a clean state |
| **Thread Startup Overhead**: Spinning up threads pollutes timing | Use `Level.Iteration` setup to pre-create threads; measure only the `invoke()` portion |
| **Solution Found Early Exit**: Fast solution discovery yields short iterations | Use a puzzle with more clicks or explicitly target "exhaustive" runs where no solution exists |
| **Queue State Carryover**: Previous iteration's state affects next | Call `CombinationQueueArray.getInstance()` reset logic or ensure singleton supports reset |

#### Use Cases

1. **Regression Detection**: Run before/after code changes to detect overall throughput regressions.
2. **Thread Scaling Analysis**: Vary `numThreads` parameter to analyze scaling efficiency.
3. **Pool Tuning**: Adjust `POOL_SIZE` in `CombinationGeneratorTask` and measure impact.
4. **Queue Configuration Comparison**: Swap `MpmcArrayQueue` for alternatives and compare throughput.

#### Output Example

```text
Benchmark                                           Mode  Cnt    Score    Error  Units
GeneratorThroughputBenchmark.runFullPipeline          ss    5  847.234 ± 23.456  ms/op
GeneratorThroughputBenchmark.runFullPipeline:gc.time  ss    5   12.000 ±  2.345  ms
```

This design complements the microbenchmarks by providing a "big picture" view of generator performance, while the microbenchmarks (`canPotentiallySatisfyConstraints_*`) isolate specific hot paths.

### F. `WorkBatchBenchmark`

**Goal:** Measure the overhead of the custom batching system.

- **Benchmarks:**
  - `addWork`: Measure adding a work item.
  - `iterate`: Measure iterating over a full batch using the zero-allocation iterator.

## 4. Execution Plan

1. **Setup:** Create the `benchmarks/` directory and its `pom.xml`.
2. **Implementation:** Implement the benchmark classes iteratively.
3. **Verification:** Run the benchmarks to ensure they execute without errors.
4. **Documentation:** Add a `README.md` inside `benchmarks/` explaining the workflow.

## 5. Next Steps

Upon approval, I will switch to **Code Mode** to implement this structure.

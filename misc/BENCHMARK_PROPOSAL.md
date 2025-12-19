# JMH Benchmark Suite Proposal (Revised)

This document outlines the plan to integrate the Java Microbenchmark Harness (JMH) into the `pdc-2018q35` project. The goal is to replace manual, coarse-grained timing with precise, reproducible microbenchmarks for critical hot paths.

## 1. Directory Structure: Separate Module

To ensure proper IDE support (especially in VS Code) and follow JMH best practices, we will create a standalone `benchmarks` module. This avoids polluting the main project's classpath and build configuration.

```text
pdc-2018q35/
  pom.xml (Main project - will need to be installed locally first)
  src/
    ...
  benchmarks/               <-- New Directory
    pom.xml                 <-- Dedicated Benchmark POM
    src/
      main/
        java/
          com/
            github/
              mrgarbagegamer/
                benchmarks/
                  GridBenchmark.java
                  MonkeyBenchmark.java
                  GeneratorBenchmark.java
                  WorkBatchBenchmark.java
                  QueueBenchmark.java
                  PoolBenchmark.java
```

## 2. Maven Configuration (`benchmarks/pom.xml`)

The `benchmarks/pom.xml` will:

- Depend on the main `pdc-2018q35` project (version `1.0-SNAPSHOT`).
- Include JMH dependencies (`jmh-core`, `jmh-generator-annprocess`).
- Use the `maven-shade-plugin` to generate the executable `benchmarks.jar`.

**Usage:**

1. Run `mvn install` in the root directory to install the main project to your local repository.
2. Navigate to `benchmarks/`.
3. Run `mvn clean package`.
4. Run `java -jar target/benchmarks.jar`.

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

- **Benchmarks:**
  - `canPotentiallySatisfyConstraints`: Measure the bitmask pruning logic.
  - `computeIntermediateSubtasks_SkipPath`: Measure the "fast path" loop logic.

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

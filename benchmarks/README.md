# JMH Benchmark Suite for PDC-2018Q35

This directory contains Java Microbenchmark Harness (JMH) benchmarks for the PDC-2018Q35 "Lights Out" puzzle solver. These benchmarks provide precise, reproducible measurements of critical hot paths in the solver's producer-consumer architecture.

## Quick Start

```bash
# From root directory: Build all modules
mvn clean install -DskipTests

# Run all benchmarks
java -jar benchmarks/target/benchmarks.jar

# Run specific benchmark
java -jar benchmarks/target/benchmarks.jar GridBenchmark

# Run with custom options (fewer iterations for quick testing)
java -jar benchmarks/target/benchmarks.jar -wi 2 -i 3 -f 1
```

## Directory Structure

This project uses a **multi-module Maven structure**:

```text
pdc-2018q35/                 <-- Root project (parent POM)
  pom.xml                    <-- Parent POM with <modules> declaration
  core/                      <-- Core solver module
    pom.xml
    src/main/java/com/github/mrgarbagegamer/
      StartYourMonkeys.java
      Grid.java
      CombinationGeneratorTask.java
      TestClickCombination.java
      ...
  benchmarks/                <-- JMH benchmark module (THIS DIRECTORY)
    pom.xml
    src/main/java/com/github/mrgarbagegamer/
      GridBenchmark.java
      MonkeyBenchmark.java
      GeneratorBenchmark.java
      QueueBenchmark.java
      QueueArrayBenchmark.java
      CompositeBenchmark.java
      GlobalConfigBenchmark.java
      ScalabilityBenchmark.java
      PoolBenchmark.java
      WorkBatchBenchmark.java
```

### Design Decisions

- **Same Package Structure**: Benchmarks use `com.github.mrgarbagegamer` to access package-private methods
- **Dependency**: `benchmarks/pom.xml` depends on the `core` module artifact
- **Build Simplification**: Running `mvn clean install` at root builds both modules in correct order

---

## Benchmark Coverage

### Existing Benchmarks (Original)

| Benchmark | Component | Coverage |
| --------- | --------- | -------- |
| [`GridBenchmark`](src/main/java/com/github/mrgarbagegamer/GridBenchmark.java) | [`Grid`](../core/src/main/java/com/github/mrgarbagegamer/Grid.java) | click operations, state management, caching |
| [`GeneratorBenchmark`](src/main/java/com/github/mrgarbagegamer/GeneratorBenchmark.java) | [`CombinationGeneratorTask`](../core/src/main/java/com/github/mrgarbagegamer/CombinationGeneratorTask.java) | Pruning fast/slow paths |
| [`MonkeyBenchmark`](src/main/java/com/github/mrgarbagegamer/MonkeyBenchmark.java) | [`TestClickCombination`](../core/src/main/java/com/github/mrgarbagegamer/TestClickCombination.java) | Parity mask building, adjacency checks, logging |
| [`PoolBenchmark`](src/main/java/com/github/mrgarbagegamer/PoolBenchmark.java) | [`ArrayPool`](../core/src/main/java/com/github/mrgarbagegamer/ArrayPool.java), [`TaskPool`](../core/src/main/java/com/github/mrgarbagegamer/TaskPool.java) | get/put/roundtrip |
| [`WorkBatchBenchmark`](src/main/java/com/github/mrgarbagegamer/WorkBatchBenchmark.java) | [`WorkBatch`](../core/src/main/java/com/github/mrgarbagegamer/WorkBatch.java) | add/iterate operations |

### New Benchmarks (2025.12)

#### Tier 1: Critical

1. **[`QueueBenchmark`](src/main/java/com/github/mrgarbagegamer/QueueBenchmark.java)** (Extended)
   - **Central Pool Benchmarks**: Tests asymmetric producer/consumer access
   - `producerPattern_pollEmpty()` - Producers polling empty batches
   - `consumerPattern_offerEmpty()` - Consumers offering cleared batches
   - `producerSide_poll()` / `consumerSide_offer()` - Mixed 4+4 workload

2. **[`QueueArrayBenchmark`](src/main/java/com/github/mrgarbagegamer/QueueArrayBenchmark.java)** (New)
   - **Work Distribution**: Tests queue selection and work-stealing
   - `randomQueueSelection()` - Cost of random queue selection
   - `producerQueueSelection_roundRobinRetry()` - Producer flush with retry
   - `consumerWorkStealing_linearScan()` - Consumer work-stealing pattern
   - `workStealing_allEmptyWorstCase()` - Worst-case empty queue scanning
   - `retryOverhead_multipleAttempts()` - Spurious failure retry overhead

#### Tier 2: High Value

1. **[`CompositeBenchmark`](src/main/java/com/github/mrgarbagegamer/CompositeBenchmark.java)** (New)
   - **Realistic Workflows**: Validates micro-optimizations in composite operations
   - `generatorLeaf_addWorkChain()` - Generator's batch fill workflow
   - `consumerProcessing_iterateAndCheck()` - Consumer's complete processing cycle
   - `producerFullCycle()` - Producer's array pool + batch add cycle

2. **[`GlobalConfigBenchmark`](src/main/java/com/github/mrgarbagegamer/GlobalConfigBenchmark.java)** (New)
   - **StableValue Validation**: Measures `GlobalConfig` access overhead
   - `access_CLICK_TO_TRUE_CELL_MASK()` - Most frequently accessed field
   - `access_EXPECTED_MASK()` - Used in every adjacency check
   - `hotPathAccess_generatorConstraint()` - Multiple field access pattern
   - `concurrentAccess_MASKS()` - 8-thread concurrent access
   - `baseline_staticFinalAccess()` - Baseline for performance comparison

#### Tier 3: Nice to Have

1. **[`ScalabilityBenchmark`](src/main/java/com/github/mrgarbagegamer/ScalabilityBenchmark.java)** (New)
   - **Threading Analysis**: Characterizes scaling from 1 to 16 threads
   - `poolThroughput_N_threads()` - Central pool scaling (1, 2, 4, 8, 16)
   - `queueArrayThroughput_N_threads()` - Queue array scaling (1, 2, 4, 8, 16)

---

## Architecture Reality Check

### Producer-Consumer Flow

**Producers** (Generators):

1. `relaxedPoll()` empty batch from **central pool**
2. Fill batch with `WorkItems`
3. `offer()` batch to a **monkey's queue** (NOT back to central pool)

**Consumers** (Monkeys):

1. `getWorkBatch()` from their queue (or steal from others)
2. Process batch
3. `clear()` batch
4. `offer()` emptied batch BACK to **central pool**

**Key Point**: This is an **asymmetric workload**. Producers only poll from the central pool; consumers only offer to it.

### Queue Operations

All queue operations use **relaxed variants** (`relaxedPoll()`, `add()`) that may spuriously return null/false even when the queue is not empty/full. This is a performance optimization that requires retry logic in production code.

---

## Benchmark Specifications

### 1. GridBenchmark

**Goal**: Measure raw performance of critical `Grid` operations.

**Benchmarks**:

- `click_short_array`: Measure `grid.click(short[])`
- `click_short_array_short_val`: Measure `grid.click(short[], short)` overload
- `initialize`: Measure `grid.initialize()` (reset)
- `click_and_isSolved`: Measure combined click + solve check
- `getTrueCount_Clean`: Measure cached path
- `getTrueCount_Dirty`: Measure recalculation path

### 2. GeneratorBenchmark

**Goal**: Measure pruning logic of `CombinationGeneratorTask`.

**Benchmarks**:

- `canPotentiallySatisfyConstraints_FastPath`: Early-exit when `needed == 0L`
- `canPotentiallySatisfyConstraints_SlowPath`: Full pruning check with suffix mask lookup

**Design Note**: Cannot benchmark forking methods (`computeIntermediateSubtasks*`) due to `ForkJoinPool` side effects. These contain `subtask.fork()` calls that would spawn real tasks and pollute results.

### 3. MonkeyBenchmark

**Goal**: Measure the "hot path" of consumer threads (`TestClickCombination`).

**Benchmarks**:

- `buildParityMask`: Measure XOR accumulation loop
- `satisfiesOddAdjacency`: Measure `(prefixMask ^ mask) == EXPECTED` check
- `simulateLoggingMessage`: Measure log message construction overhead
- `simulateLoggingMessage_formatTo`: Measure formatting cost on background thread

### 4. QueueBenchmark (Extended)

**Goal**: Measure queue contention and throughput.

**Original Benchmarks**:

- `addToQueue` / `getFromQueue`: 4+4 thread throughput test

**New Benchmarks (Tier 1)**:

- `producerPattern_pollEmpty`: 8-thread producer polling pattern
- `consumerPattern_offerEmpty`: 8-thread consumer offering pattern
- `producerSide_poll` / `consumerSide_offer`: 4+4 mixed asymmetric workload

### 5. QueueArrayBenchmark (New - Tier 1)

**Goal**: Measure work distribution patterns.

**Benchmarks**:

- `randomQueueSelection`: Cost of `ThreadLocalRandom.nextInt(queues.length)`
- `producerQueueSelection_roundRobinRetry`: Mirrors `CombinationGeneratorTask.flushBatchFast()`
- `consumerWorkStealing_linearScan`: Mirrors `TestClickCombination.getWork()`
- `workStealing_allEmptyWorstCase`: Measures failed work-stealing overhead
- `retryOverhead_multipleAttempts`: Measures retry loop for spurious failures

### 6. CompositeBenchmark (New - Tier 2)

**Goal**: Validate micro-optimizations in realistic sequences.

**Benchmarks**:

- `generatorLeaf_addWorkChain`: Simulates generator's leaf workflow (check full → add work)
- `consumerProcessing_iterateAndCheck`: Simulates consumer's workflow (iterate → mask → check)
- `producerFullCycle`: Full cycle (pool get → copy → add → pool put)

### 7. GlobalConfigBenchmark (New - Tier 2)

**Goal**: Validate `StableValue` refactoring performance.

**Benchmarks**:

- `access_CLICK_TO_TRUE_CELL_MASK`: Most frequently accessed (every `buildParityMask`)
- `access_EXPECTED_MASK`: Used in every adjacency check
- `hotPathAccess_generatorConstraint`: Simulates generator constraint checking
- `concurrentAccess_MASKS`: 8-thread concurrent access (contention test)
- `baseline_staticFinalAccess`: Baseline comparison for overhead measurement

**Constraint**: Cannot benchmark "first access" (StableValue limitation). Only subsequent accesses are measured.

### 8. ScalabilityBenchmark (New - Tier 3)

**Goal**: Characterize scaling with thread count.

**Benchmarks**:

- `poolThroughput_1thread` through `poolThroughput_16threads`: Central pool scaling
- `queueArrayThroughput_1thread` through `queueArrayThroughput_16threads`: Queue array scaling

**Questions Answered**:

- At what thread count does throughput plateau?
- Where does contention start degrading performance?
- Is 16 threads optimal, or should we use more/fewer?

### 9. PoolBenchmark

**Goal**: Measure object pooling overhead.

**Benchmarks**:

- `arrayPool_roundtrip`: Get + put cycle for `ArrayPool`
- `taskPool_roundtrip`: Get + put cycle for `TaskPool`
- `arrayPool_get` / `arrayPool_put`: Isolated operations
- `taskPool_get` / `taskPool_put`: Isolated operations

### 10. WorkBatchBenchmark

**Goal**: Measure custom batching system.

**Benchmarks**:

- `addWork`: Add work item to batch
- `addWorkFullBatch`: Attempt to add to full batch (should return false)
- `iterate`: Zero-allocation iterator over full batch

---

## Why NOT These Benchmarks

### ❌ Full End-to-End Integration

**Reason**: `CombinationGeneratorTask` and `TestClickCombination` are too tightly coupled to `ForkJoinPool` lifecycle, thread-local state, and configuration. Mocking is impractical without changing production code. Instead, use **composite** benchmarks which simulate realistic workflows in isolation.

### ❌ ForkJoinPool Task Forking

**Reason**: Task creation is heavily pooled (`TaskPool.get()`), and actual forking behavior is tied to `ForkJoinPool` scheduling. The existing `PoolBenchmark.taskPool_roundtrip()` already covers task allocation.

### ❌ Throughput (combinations/second)

**Reason**: The code doesn't track throughput metrics internally. Adding counters would change production behavior, violating the constraint of not modifying core classes for benchmarks.

---

## JMH CLI Options Reference

| Option | Description | Example |
| --- | --- | --- |
| `-wi N` | Warmup iterations | `-wi 3` |
| `-i N` | Measurement iterations | `-i 5` |
| `-f N` | Number of forks | `-f 3` |
| `-t N` | Number of threads | `-t 4` |
| `-prof <profiler>` | Enable profiler | `-prof gc` (GC stats), `-prof async` (flame graph) |
| `-rf <format>` | Result format | `-rf json`, `-rf csv` |
| `-rff <file>` | Result file | `-rff results.json` |

### Examples

```bash
# Quick test (fewer iterations)
java -jar benchmarks/target/benchmarks.jar GridBenchmark.click -wi 2 -i 3 -f 1

# With GC profiling
java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof gc

# Save results to JSON
java -jar benchmarks/target/benchmarks.jar -rf json -rff results.json

# Run specific benchmark pattern
java -jar benchmarks/target/benchmarks.jar ".*Queue.*"
```

---

## Success Metrics

### Central Pool Benchmarks

- [ ] Measured throughput (ops/µs) at 1, 8, 16 threads for both producer-poll and consumer-offer patterns
- [ ] Identified if asymmetric access causes contention
- [ ] Documented pool size recommendations

### Queue Array Benchmarks

- [ ] Quantified work-stealing overhead
- [ ] Measured queue selection latency with retry logic
- [ ] Assessed impact of relaxed operation spurious failures

### Composite Benchmarks

- [ ] Confirmed micro-optimizations hold in realistic workflows
- [ ] Identified any unexpected regressions
- [ ] Documented realistic operation costs

### GlobalConfig Benchmarks

- [ ] Validated StableValue performance vs. baseline
- [ ] Confirmed no contention under concurrent access
- [ ] Documented refactoring effectiveness

### Scalability Benchmarks

- [ ] Identified optimal thread count
- [ ] Located scalability plateau
- [ ] Characterized contention behavior

---

## Implementation Notes

### JVM Arguments

All benchmarks use aggressive JVM tuning matching production:

- G1GC with adaptive IHOP and large heap (2-8GB)
- Vectorization support (AVX2)
- Inlining tuning (large thresholds for hot methods)
- TLAB optimization (512KB per thread)
- Compiler thread tuning (16 threads, critical priority)

These are specified in each benchmark's `@Fork` annotation.

### Thread Safety

- **Grid**: NOT thread-safe. Each benchmark uses thread-local instances.
- **Pools**: Thread-safe via lock-free structures.
- **Queues**: Lock-free (JCTools `MpmcArrayQueue`).
- **GlobalConfig**: Thread-safe via `StableValue` API.

### Design Principles

1. **No Production Code Changes**: All benchmarks work with existing API
2. **No Complex Mocking**: Components tested in isolation or realistic simulation
3. **Realistic Patterns**: Benchmarks mirror actual runtime behavior
4. **Account for Relaxed Operations**: Tests include retry logic where applicable

---

## Troubleshooting

### Benchmark Doesn't Run

```bash
# Ensure core module is built first
cd ..
mvn clean install -pl core -DskipTests
cd benchmarks
mvn clean package
```

### OutOfMemoryError

Increase heap size in `@Fork` annotation or via command line:

```bash
java -Xmx16g -jar benchmarks/target/benchmarks.jar
```

### GlobalConfig Not Initialized

`GlobalConfig` uses `StableValue.setOrThrow()` which allows only one initialization per JVM. If you see errors, ensure each `@Fork` creates a fresh JVM (which is the default).

### Slow Benchmark Iteration Time

Reduce iterations for quick testing:

```bash
java -jar benchmarks/target/benchmarks.jar -wi 1 -i 2 -f 1
```

---

## Future Work

### Potential Additions

1. **Profiler Integration**: Add `-prof async:output=flamegraph` for flame graph analysis
2. **Regression Tracking**: Automate baseline comparison with `jmh-compare`
3. **CI Integration**: Run subset of benchmarks on PRs to detect regressions
4. **Memory Benchmarks**: Track allocations with `-prof GC.alloc`

### Known Limitations

1. Cannot benchmark "first access" of `GlobalConfig` fields (StableValue constraint)
2. Cannot isolate `ForkJoinPool` task forking (relies on scheduler side effects)
3. No end-to-end integration benchmark (too tightly coupled to production lifecycle)

---

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples)
- [Aleksey Shipilëv's Blog](https://shipilev.net/) - JMH best practices

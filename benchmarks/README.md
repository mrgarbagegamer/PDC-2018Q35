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
      MonkeyBenchmark.java (UPDATED 2026.01)
      GeneratorBenchmark.java
      QueueBenchmark.java
      QueueArrayBenchmark.java
      CompositeBenchmark.java (UPDATED 2026.01)
      AllocationBenchmark.java (NEW 2026.01)
      PoolSizingSensitivityBenchmark.java (NEW 2026.01)
      BatchSizeSensitivityBenchmark.java (NEW 2026.01)
      WorkStealingBenchmark.java (NEW 2026.01)
      WorkBatchBenchmark.java
```

### Design Decisions

- **Same Package Structure**: Benchmarks use `com.github.mrgarbagegamer` to access package-private methods
- **Dependency**: `benchmarks/pom.xml` depends on the `core` module artifact
- **Build Simplification**: Running `mvn clean package` at root builds both modules in correct order

---

## Benchmark Coverage

### Core Component Benchmarks (Original)

| Benchmark | Component | Coverage |
| --------- | --------- | -------- |
| [`GridBenchmark`](src/main/java/com/github/mrgarbagegamer/GridBenchmark.java) | [`Grid`](../core/src/main/java/com/github/mrgarbagegamer/Grid.java) | click operations, state management, caching |
| [`GeneratorBenchmark`](src/main/java/com/github/mrgarbagegamer/GeneratorBenchmark.java) | [`CombinationGeneratorTask`](../core/src/main/java/com/github/mrgarbagegamer/CombinationGeneratorTask.java) | Pruning fast/slow paths |
| [`MonkeyBenchmark`](src/main/java/com/github/mrgarbagegamer/MonkeyBenchmark.java) (UPDATED 2026.01) | [`TestClickCombination`](../core/src/main/java/com/github/mrgarbagegamer/TestClickCombination.java) | Varied prefix patterns, parity mask, adjacency checks, full validation |
| [`WorkBatchBenchmark`](src/main/java/com/github/mrgarbagegamer/WorkBatchBenchmark.java) | [`WorkBatch`](../core/src/main/java/com/github/mrgarbagegamer/WorkBatch.java) | add/iterate operations |

### New & Enhanced Benchmarks (2026.01)

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

#### Additional Benchmarks (2026.01 Enhancements)

1. **[`AllocationBenchmark`](src/main/java/com/github/mrgarbagegamer/AllocationBenchmark.java)** (NEW)
    - **Allocation Detection**: Validates "near-zero allocation" claim
    - Tests array pool, task pool, batch operations, and exhaustion scenarios
    - Run with `-prof gc` to measure allocation rates

2. **[`PoolSizingSensitivityBenchmark`](src/main/java/com/github/mrgarbagegamer/PoolSizingSensitivityBenchmark.java)** (NEW)
    - **Pool Tuning**: Tests sizes [64, 128, 256, 512, 1024]
    - Identifies optimal pool size minimizing allocations and memory waste

3. **[`BatchSizeSensitivityBenchmark`](src/main/java/com/github/mrgarbagegamer/BatchSizeSensitivityBenchmark.java)** (NEW)
    - **Batch Range Tuning**: Tests final-click ranges [10, 25, 50, 100]
    - Analyzes iteration latency and cache effects vs range size

4. **[`WorkStealingBenchmark`](src/main/java/com/github/mrgarbagegamer/WorkStealingBenchmark.java)** (NEW)
    - **Load Balancing**: Analyzes work-stealing patterns and contention
    - Tests preferred queue hit, stealing, and starvation scenarios

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

### 3. MonkeyBenchmark (Updated 2026.01)

**Goal**: Measure the "hot path" of consumer threads (`TestClickCombination`) with realistic prefix patterns.

**Benchmarks**:

- `buildParityMask_VariedPrefixes`: Measure XOR accumulation loop with diverse bit patterns
- `satisfiesOddAdjacency`: Measure `(prefixMask ^ mask) == EXPECTED` check
- `fullMonkeyValidation`: Complete workflow testing multiple final clicks against a prefix

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

### 7. WorkBatchBenchmark

**Goal**: Measure custom batching system.

**Benchmarks**:

- `addWork`: Add work item to batch
- `addWorkFullBatch`: Attempt to add to full batch (should return false)
- `iterate`: Zero-allocation iterator over full batch

### 8. AllocationBenchmark (New 2026.01)

**Goal**: Validates "near-zero allocation" design goal by profiling memory patterns.

**Benchmarks**:

- `arrayPoolCycle()`: Array pool get/put effectiveness
- `taskPoolCycle()`: Task pool recycling patterns
- `workBatchAddWorkCycle()`: Batch operation allocation
- `workBatchIterationAlloc()`: Iterator on-the-fly assembly (zero-alloc)
- `poolExhaustionStress()`: Fallback allocation rates when pools depleted

**Run with**: `-prof gc` to measure allocation rates

### 9. PoolSizingSensitivityBenchmark (New 2026.01)

**Goal**: Optimize pool size configuration.

**Parameterized Configuration**: Tests sizes [64, 128, 256, 512, 1024]

**Benchmarks**:

- `poolGetPutCycle()`: Basic pool operation latency per size
- `poolStressTest()`: Sustained load allocation patterns

### 10. BatchSizeSensitivityBenchmark (New 2026.01)

**Goal**: Optimize final-click batch range size.

**Parameterized Configuration**: Tests ranges [10, 25, 50, 100]

**Benchmarks**:

- `batchValidationVaryingRanges()`: Full batch iteration with varied final-click counts

### 11. WorkStealingBenchmark (New 2026.01)

**Goal**: Analyze consumer load balancing and work-stealing effectiveness.

**Benchmarks**:

- `workSteal_PreferredQueueHit()`: Fast path when own queue has work
- `workSteal_ScanAndSteal()`: Work-stealing with steady-state refill
- `workSteal_FullScanStarvation()`: Worst case when all queues empty

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
| `-prof <profiler>` | Enable profiler | `-prof gc`, `-prof perfnorm`, `-prof perfasm`, `-prof async` |
| `-rf <format>` | Result format | `-rf json`, `-rf csv` |
| `-rff <file>` | Result file | `-rff results.json` |

### Examples

```bash
# Quick test (fewer iterations)
java -jar benchmarks/target/benchmarks.jar GridBenchmark.click -wi 2 -i 3 -f 1

# With GC profiling
java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof gc

# With perfnorm (performance counters, requires Linux perf)
java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof perfnorm

# Save results to JSON
java -jar benchmarks/target/benchmarks.jar -rf json -rff results.json

# Run specific benchmark pattern
java -jar benchmarks/target/benchmarks.jar ".*Queue.*"
```

---

## Profiling Recommendations by Profiler Type

This section documents which benchmarks benefit most from specific profilers and which profilers are appropriate for each benchmark.

### `perfnorm` (Performance Counters)

**Requirements**: Linux with perf support, elevated privileges, or kernel perf event permissions.

**Best For**: Measuring CPU-level metrics like cycles, branch prediction, cache behavior, and instruction-level parallelism.

#### Tier 1: Critical Hot Paths (Highest Priority)

These benchmarks execute billions of times during solving and represent the innermost algorithmic loops:

1. **[`MonkeyBenchmark`](src/main/java/com/github/mrgarbagegamer/MonkeyBenchmark.java)**
   - `buildParityMask_VariedPrefixes()` - XOR-based mask computation in tight loop
   - `satisfiesOddAdjacency()` - Constraint validation check
   - **Why**: Tests the consumer's validation inner loop. Perfnorm reveals branch prediction misses, cache line behavior of bitmask operations, and tight loop efficiency.
   - **Command**: `java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof perfnorm`
   - **Key Metrics**: Cycles/op, branch-misses/cycles, cache-references, cache-misses

2. **[`GridBenchmark`](src/main/java/com/github/mrgarbagegamer/GridBenchmark.java)**
   - `click_and_isSolved()` - Combined click operation + state check
   - **Why**: Simulates the actual validation loop interaction between XOR state mutation and truth count recalculation. Perfnorm reveals data dependency chains and memory latency patterns.
   - **Command**: `java -jar benchmarks/target/benchmarks.jar GridBenchmark.click_and_isSolved -prof perfnorm`
   - **Key Metrics**: Cycles/op, L1/L2/L3 cache behavior, memory stalls

3. **[`CompositeBenchmark`](src/main/java/com/github/mrgarbagegamer/CompositeBenchmark.java)**
   - `consumerProcessing_iterateAndCheck()` - Full consumer validation workflow
   - **Why**: Measures realistic producer-consumer handoff. Perfnorm reveals cache coherency overhead and thread contention.
   - **Command**: `java -jar benchmarks/target/benchmarks.jar CompositeBenchmark.consumerProcessing_iterateAndCheck -prof perfnorm`
   - **Key Metrics**: Cycles/op, LLC-load-misses, memory-loads-aux

#### Tier 2: Producer & Distribution (High Priority)

4. **[`GeneratorBenchmark`](src/main/java/com/github/mrgarbagegamer/GeneratorBenchmark.java)**
   - `canPotentiallySatisfyConstraints_SlowPath()` - Full pruning logic
   - **Why**: Producer depends on fast pruning. Perfnorm reveals whether suffix mask lookups hit L1 cache and whether method is being properly inlined.
   - **Command**: `java -jar benchmarks/target/benchmarks.jar GeneratorBenchmark.canPotentiallySatisfyConstraints_SlowPath -prof perfnorm`
   - **Key Metrics**: Cycles/op, branch-misses/cycles, instructions/cycle

5. **[`WorkStealingBenchmark`](src/main/java/com/github/mrgarbagegamer/WorkStealingBenchmark.java)**
   - `workSteal_ScanAndSteal()` - Work-stealing consumer pattern
   - **Why**: Shows cost of queue scanning under consumer contention. Perfnorm reveals atomic instruction latency and memory fence overhead.
   - **Command**: `java -jar benchmarks/target/benchmarks.jar WorkStealingBenchmark.workSteal_ScanAndSteal -prof perfnorm`
   - **Key Metrics**: Cycles/op, atomic-ops, memory-operations

### `perfasm` (Assembly Analysis)

**Requirements**: Linux with perf support, `hsdis` (HotSpot Disassembler) binary available.

**Best For**: Understanding JIT compilation decisions, inlining behavior, and examining generated assembly code.

#### Recommended Use Cases

1. **[`GeneratorBenchmark`](src/main/java/com/github/mrgarbagegamer/GeneratorBenchmark.java)**
   - **Why**: Validate that pruning methods are fully inlined and compiled efficiently
   - **Command**: `java -jar benchmarks/target/benchmarks.jar GeneratorBenchmark -prof perfasm`
   - **Look For**: Method inlining, loop unrolling, and vectorization opportunities

2. **[`MonkeyBenchmark`](src/main/java/com/github/mrgarbagegamer/MonkeyBenchmark.java) - `buildParityMask_VariedPrefixes()`**
   - **Why**: Inspect the tight XOR loop and verify vectorization
   - **Command**: `java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark.buildParityMask_VariedPrefixes -prof perfasm`
   - **Look For**: SIMD instructions (AVX2), loop structure, branch predictions

### `gc` (Garbage Collection & Allocation)

**Requirements**: Standard JVM, no special kernel permissions needed.

**Best For**: Measuring allocation rates, GC frequency, and validating "zero-allocation" design claims.

#### Recommended Use Cases

1. **[`AllocationBenchmark`](src/main/java/com/github/mrgarbagegamer/AllocationBenchmark.java)** ⭐ Primary
   - **Why**: Specifically designed to validate allocation patterns
   - **Command**: `java -jar benchmarks/target/benchmarks.jar AllocationBenchmark -prof gc`
   - **Look For**: Allocation rates (bytes/op), GC.alloc.rate.norm, churn rates

2. **[`WorkBatchBenchmark`](src/main/java/com/github/mrgarbagegamer/WorkBatchBenchmark.java) - `workBatchIterationAlloc()`**
   - **Why**: On-the-fly iterator should allocate zero bytes
   - **Command**: `java -jar benchmarks/target/benchmarks.jar WorkBatchBenchmark.workBatchIterationAlloc -prof gc`
   - **Look For**: `GC.alloc.rate` should be 0 B/op or minimal

3. **[`CompositeBenchmark`](src/main/java/com/github/mrgarbagegamer/CompositeBenchmark.java) - `producerFullCycle()`**
   - **Why**: Validate that array pool cycle produces minimal allocations
   - **Command**: `java -jar benchmarks/target/benchmarks.jar CompositeBenchmark.producerFullCycle -prof gc`
   - **Look For**: Allocation rate consistent with pool reuse

### `jfr` (Java Flight Recorder)

**Requirements**: Standard JVM with Java Flight Recorder support (JDK 11+).

**Best For**: Detailed event tracing, method profiling, and identifying lock contention.

#### Recommended Use Cases

1. **[`QueueBenchmark`](src/main/java/com/github/mrgarbagegamer/QueueBenchmark.java)** - Group suites
   - **Why**: Detect lock contention in multi-threaded producer-consumer patterns
   - **Command**: `java -jar benchmarks/target/benchmarks.jar QueueBenchmark -prof jfr`
   - **Look For**: Thread wait times, contention events

### No Profiler (Baseline)

**When to Use**: Establishing baseline performance without profiler overhead.

#### Benchmarks to Run Without Profilers

- **`PoolSizingSensitivityBenchmark`** - Needs clean runs to measure pool size sensitivity
- **`BatchSizeSensitivityBenchmark`** - Needs clean runs to measure batch range sensitivity
- **`QueueArrayBenchmark`** - General throughput baseline (run without profilers first)

**Commands**:
```bash
java -jar benchmarks/target/benchmarks.jar PoolSizingSensitivityBenchmark
java -jar benchmarks/target/benchmarks.jar BatchSizeSensitivityBenchmark
```

---

## Profiling Strategy & Execution Plan

### Phase 1: Establish Consumer Hot Path Characteristics (Session 1)

Run these in sequence to understand consumer performance:

```bash
# Baseline performance (no profiler overhead)
java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark

# Performance counters - identify cycle/instruction characteristics
java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof perfnorm

# Assembly inspection (if available) - verify inlining & vectorization
java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof perfasm
```

**Duration**: ~4m30s total (1m30s * 3 with profiler overhead)

### Phase 2: Producer & Grid Performance (Session 2)

```bash
# Generator pruning performance
java -jar benchmarks/target/benchmarks.jar GeneratorBenchmark -prof perfnorm

# Grid click operations
java -jar benchmarks/target/benchmarks.jar GridBenchmark -prof perfnorm

# Grid allocation patterns
java -jar benchmarks/target/benchmarks.jar GridBenchmark -prof gc
```

**Duration**: ~7 minutes total (1m + 3m + 3m with profiler overhead)

### Phase 3: Allocation Validation (Session 3)

```bash
# Comprehensive allocation patterns
java -jar benchmarks/target/benchmarks.jar AllocationBenchmark -prof gc
```

**Duration**: ~2m30s total

### Phase 4: Queue & Contention Analysis (Session 4)

```bash
# Queue contention under asymmetric load
java -jar benchmarks/target/benchmarks.jar QueueBenchmark -prof perfnorm

# Work-stealing patterns
java -jar benchmarks/target/benchmarks.jar WorkStealingBenchmark -prof perfnorm

# Lock contention detection
java -jar benchmarks/target/benchmarks.jar QueueBenchmark -prof jfr
```

**Duration**: ~7 minutes total (2m30s + 2m + 2m30s with profiler overhead)

### Phase 5: Sensitivity & Tuning (Session 5+)

```bash
# Pool size optimization
java -jar benchmarks/target/benchmarks.jar PoolSizingSensitivityBenchmark

# Batch range optimization
java -jar benchmarks/target/benchmarks.jar BatchSizeSensitivityBenchmark

# Composite workflow validation
java -jar benchmarks/target/benchmarks.jar CompositeBenchmark -prof perfnorm
```

**Duration**: ~9m30s minutes total (5m + 2m30s + 2m with profiler overhead)


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

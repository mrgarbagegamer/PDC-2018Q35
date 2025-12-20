package com.github.mrgarbagegamer;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * {@link Benchmark}s for the pruning logic in {@link CombinationGeneratorTask}.
 *
 * <p>
 * This class focuses on micro-benchmarking the
 * {@link CombinationGeneratorTask#canPotentiallySatisfyConstraints(int)} method,
 * which is a critical hot path in the combination generation process. The
 * benchmarks are designed to measure the two distinct execution paths within
 * this method:
 * <ul>
 * <li><b>Fast Path:</b> The early-exit scenario where the prefix has already
 * satisfied all constraints ({@code needed == 0L}).</li>
 * <li><b>Slow Path:</b> The full check that involves a suffix mask lookup to
 * determine if remaining clicks can satisfy the constraints.</li>
 * </ul>
 * </p>
 *
 * @see CombinationGeneratorTask
 * @since 2025.12 - JMH Benchmarking
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgsAppend = {
    "--enable-preview",
    "-XX:+UseG1GC",
    "-Xms2g", "-Xmx8g",
    "-XX:GCTimeRatio=19",
    "-XX:MaxInlineSize=70", "-XX:FreqInlineSize=650",
    "-XX:InlineSmallCode=5000", "-XX:MaxInlineLevel=20",
    "-XX:CompileThreshold=5000", "-XX:Tier3CompileThreshold=1000", "-XX:Tier4CompileThreshold=7500",
    "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableVectorSupport", "-XX:+EnableVectorReboxing", "-XX:+EnableVectorAggressiveReboxing",
    "-XX:MaxVectorSize=32", "-XX:+AlignVector",
    "-XX:+UseTLAB", "-XX:TLABSize=512k", "-XX:+ResizeTLAB", "-XX:TLABWasteTargetPercent=5",
    "-XX:+AlwaysPreTouch",
    "-XX:+EliminateAllocations", "-XX:+EliminateAutoBox", "-XX:EliminateAllocationArraySizeLimit=128",
    "-XX:MaxGCPauseMillis=100", "-XX:G1NewSizePercent=40", "-XX:G1MaxNewSizePercent=80",
    "-XX:G1HeapRegionSize=16m", "-XX:G1MixedGCCountTarget=4", "-XX:+G1UseAdaptiveIHOP",
    "-XX:+UseThreadPriorities", "-XX:+UseCriticalCompilerThreadPriority",
    "-XX:+UseDynamicNumberOfCompilerThreads", "-XX:CICompilerCount=16",
    "-XX:PerMethodTrapLimit=200", "-XX:PerBytecodeTrapLimit=8", "-XX:PerMethodRecompilationCutoff=800",
    "-XX:+UseCountLeadingZerosInstruction", "-XX:+UseCountTrailingZerosInstruction"
})
public class GeneratorBenchmark {

    private CombinationGeneratorTask taskFastPath;
    private CombinationGeneratorTask taskSlowPath;

    /**
     * Sets up the benchmark state by creating and initializing two distinct
     * {@link CombinationGeneratorTask} instances.
     *
     * <p>
     * This setup method prepares the necessary state for benchmarking both the fast
     * and slow paths of the pruning logic:
     * <ol>
     * <li><b>Global Configuration:</b> Initializes {@link StartYourMonkeys.GlobalConfig}
     * with standard puzzle parameters (17 clicks, Grid35). This is a prerequisite
     * for any task functionality.</li>
     * <li><b>Fast Path Task:</b> Creates a task and manually initializes it to simulate
     * an intermediate state where all constraints are already met. This is achieved by
     * setting its {@code cachedAdjacencyState} to the {@code EXPECTED_MASK}.</li>
     * <li><b>Slow Path Task:</b> Creates a task and initializes it with a partial
     * {@code cachedAdjacencyState}, ensuring that the full suffix-mask check is
     * triggered.</li>
     * </ol>
     * The tasks are created via {@link CombinationGeneratorTask#createRootTask()} and then
     * mutated using the {@code init()} method to bypass the normal {@code ForkJoinPool}
     * execution and isolate the method under test.
     * </p>
     */
    @Setup(Level.Trial)
    public void setup() {
        // 1. Initialize GlobalConfig, which is required by CombinationGeneratorTask
        // We need to do this carefully as it's designed to be initialized once.
        // JMH forks will create new JVMs, so this is safe.
        if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
            StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
        }

        // 2. Create and configure a task for the fast-path benchmark
        taskFastPath = CombinationGeneratorTask.createRootTask();
        // Manually create a prefix array. In a real scenario, this is pooled.
        short[] prefixFast = new short[StartYourMonkeys.GlobalConfig.getNumClicks() - 1];
        prefixFast[0] = 1;
        prefixFast[1] = 2;
        // To trigger the fast path, we set the cached state to the expected final mask
        long expectedMask = StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();
        taskFastPath.init(prefixFast, 2, expectedMask, false, false);

        // 3. Create and configure a task for the slow-path benchmark
        taskSlowPath = CombinationGeneratorTask.createRootTask();
        // Manually create a prefix array.
        short[] prefixSlow = new short[StartYourMonkeys.GlobalConfig.getNumClicks() - 1];
        prefixSlow[0] = 1;
        prefixSlow[1] = 2;
        // To trigger the slow path, we set a partial adjacency state
        long partialMask = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get()[1];
        taskSlowPath.init(prefixSlow, 2, partialMask, false, false);
    }

    /**
     * Benchmarks the "fast path" of the constraint check, where the prefix has
     * already satisfied all constraints.
     *
     * <p>
     * This benchmark measures the performance of the early-exit condition in
     * {@code canPotentiallySatisfyConstraints}. In this scenario, the method should
     * perform a single XOR operation, a comparison, set a boolean flag, and return
     * {@code true}. It isolates the cost of the quickest possible success case.
     * </p>
     *
     * @return The result of the constraint check (always {@code true}).
     */
    @Benchmark
    public boolean canPotentiallySatisfyConstraints_FastPath() {
        // The start index (5) is arbitrary but must be a valid index.
        return taskFastPath.canPotentiallySatisfyConstraints(5);
    }

    /**
     * Benchmarks the "slow path" of the constraint check, where the prefix has
     * not yet satisfied all constraints.
     *
     * <p>
     * This benchmark measures the full pruning logic, including the XOR to find
     * {@code needed} bits, the lookup of the suffix OR mask from the
     * {@code SUFFIX_OR_MASKS} array, and the final bitwise AND and comparison. This
     * represents the more common and computationally intensive path.
     * </p>
     *
     * @return The result of the constraint check.
     */
    @Benchmark
    public boolean canPotentiallySatisfyConstraints_SlowPath() {
        // The start index (5) is arbitrary but must be a valid index.
        return taskSlowPath.canPotentiallySatisfyConstraints(5);
    }
}
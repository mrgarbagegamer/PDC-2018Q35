package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.BenchmarkUtils.generateRandomPrefix;
import static com.github.mrgarbagegamer.util.BenchmarkUtils.setupGlobalConfig;

import java.util.Random;
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
 * {@link Benchmark}s for the fast pruning logic in {@link TestClickCombination}.
 * 
 * <p>
 * This class focuses on micro-benchmarking the hot-path methods used for validating combinations:
 * <ul>
 * <li><b>buildParityMask(short[]):</b> Computes the XOR sum of adjacency masks for a prefix.</li>
 * <li><b>satisfiesOddAdjacency(long, short):</b> Checks if a full combination satisfies the
 * constraint.</li>
 * </ul>
 * </p>
 * 
 * <p>
 * These benchmarks now use varied prefixes with different bit patterns to reflect realistic
 * validation patterns, rather than pre-computed masks that hide the actual cost.
 * </p>
 * 
 * <p>
 * These benchmarks take ~1m30s in total to run (3 forks x (5 warmup + 5 measurement) x 3
 * benchmarks).
 * </p>
 *
 * <h2>Profiling Recommendations</h2>
 *
 * <p>
 * <b>TIER 1: CRITICAL HOT PATH</b> - These benchmarks represent the consumer's innermost validation
 * loops, executed billions of times during solving. Ideal for detailed performance analysis:
 * </p>
 *
 * <ul>
 * <li><b>perfnorm (PRIMARY):</b> Identifies branch mispredictions, cache behavior, and loop
 * efficiency.
 * 
 * <pre>
 * java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof perfnorm
 * </pre>
 * 
 * Key metrics: cycles/op, branch-misses/cycles, cache-references, cache-misses</li>
 *
 * <li><b>perfasm:</b> Inspect JIT compilation, method inlining, and vectorization.
 * 
 * <pre>
 * java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark -prof perfasm
 * </pre>
 * 
 * Look for: SIMD instructions (AVX2), loop unrolling, branch strategy</li>
 *
 * <li><b>Baseline (no profiler):</b> Clean metrics without profiler overhead.
 * 
 * <pre>
 * java -jar benchmarks/target/benchmarks.jar MonkeyBenchmark
 * </pre>
 * 
 * </li>
 * </ul>
 *
 * @since 2025.12 - JMH Benchmarking
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgsAppend = {"--enable-preview", "-XX:+UseG1GC", "-Xms2g", "-Xmx8g",
        "-XX:GCTimeRatio=19", "-XX:MaxInlineSize=70", "-XX:FreqInlineSize=650",
        "-XX:InlineSmallCode=5000", "-XX:MaxInlineLevel=20", "-XX:CompileThreshold=5000",
        "-XX:Tier3CompileThreshold=1000", "-XX:Tier4CompileThreshold=7500",
        "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableVectorSupport", "-XX:+EnableVectorReboxing",
        "-XX:+EnableVectorAggressiveReboxing", "-XX:MaxVectorSize=32", "-XX:+AlignVector",
        "-XX:+UseTLAB", "-XX:TLABSize=512k", "-XX:+ResizeTLAB", "-XX:TLABWasteTargetPercent=5",
        "-XX:+AlwaysPreTouch", "-XX:+EliminateAllocations", "-XX:+EliminateAutoBox",
        "-XX:EliminateAllocationArraySizeLimit=128", "-XX:MaxGCPauseMillis=100",
        "-XX:G1NewSizePercent=40", "-XX:G1MaxNewSizePercent=80", "-XX:G1HeapRegionSize=16m",
        "-XX:G1MixedGCCountTarget=4", "-XX:+G1UseAdaptiveIHOP", "-XX:+UseThreadPriorities",
        "-XX:+UseCriticalCompilerThreadPriority", "-XX:+UseDynamicNumberOfCompilerThreads",
        "-XX:CICompilerCount=16", "-XX:PerMethodTrapLimit=200", "-XX:PerBytecodeTrapLimit=8",
        "-XX:PerMethodRecompilationCutoff=800", "-XX:+UseCountLeadingZerosInstruction",
        "-XX:+UseCountTrailingZerosInstruction"})
public class MonkeyBenchmark {

    private static long[] MASKS;
    private static long EXPECTED;

    private short[][] diversePrefixes;
    private long[] diverseMasks;
    private short[] testFinalClicks;
    private long invocationCounter;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize GlobalConfig to ensure static fields are populated
        setupGlobalConfig();
        random = new Random(42);

        MASKS = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
        EXPECTED = StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();

        // Generate diverse prefixes with different bit patterns
        diversePrefixes = new short[100][];
        testFinalClicks = new short[100];
        for (int p = 0; p < 100; p++) {
            diversePrefixes[p] = generateRandomPrefix(16, random);
            testFinalClicks[p] = (short) (diversePrefixes[p][diversePrefixes[p].length - 1] + 1);
        }

        // Build parity masks for satisfiesOddAdjacency benchmark
        diverseMasks = new long[diversePrefixes.length];
        for (int p = 0; p < diversePrefixes.length; p++) {
            long mask = 0;
            for (short click : diversePrefixes[p]) {
                mask ^= MASKS[click];
            }
            diverseMasks[p] = mask;
        }

        invocationCounter = 0;
    }

    /**
     * Benchmarks parity mask computation with a varied prefix. Uses different prefixes each
     * invocation to reflect realistic scenario where prefixes have diverse bit patterns, avoiding
     * branch prediction bias from repetitive access patterns.
     */
    @Benchmark
    public long buildParityMask_VariedPrefixes() {
        // Cycle through diverse prefixes using a simple counter to avoid setup overhead
        int prefixIndex = (int) ((invocationCounter++) % diversePrefixes.length);
        short[] prefix = diversePrefixes[prefixIndex];
        long mask = 0;
        for (short click : prefix) {
            mask ^= MASKS[click];
        }
        return mask;
    }

    /**
     * Benchmarks the satisfiesOddAdjacency check with pre-computed masks. This measures the
     * validation check in isolation, which is the innermost loop operation.
     */
    @Benchmark
    public boolean satisfiesOddAdjacency() {
        int prefixIndex = (int) ((invocationCounter++) % diversePrefixes.length);
        long prefixMask = diverseMasks[prefixIndex];
        short finalClick = testFinalClicks[prefixIndex % testFinalClicks.length];
        return (prefixMask ^ MASKS[finalClick]) == EXPECTED;
    }

    /**
     * Full monkey validation workflow: compute parity mask, then check multiple final clicks. This
     * represents the actual hot path where a prefix is validated against several potential final
     * clicks.
     */
    @Benchmark
    public int fullMonkeyValidation() {
        int prefixIndex = (int) ((invocationCounter++) % diversePrefixes.length);
        short[] prefix = diversePrefixes[prefixIndex];
        long prefixMask = 0;

        // Build mask once for the prefix
        for (short click : prefix) {
            prefixMask ^= MASKS[click];
        }

        // Check multiple final clicks (simulating the range iteration)
        int validCount = 0;
        for (int i = prefix[prefix.length - 1] + 1; i < testFinalClicks.length; i++) {
            short finalClick = testFinalClicks[(prefixIndex + i) % testFinalClicks.length];
            if ((prefixMask ^ MASKS[finalClick]) == EXPECTED) {
                validCount++;
            }
        }

        return validCount;
    }
}

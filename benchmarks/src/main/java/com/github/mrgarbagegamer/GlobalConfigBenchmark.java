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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks access patterns for GlobalConfig after initialization.
 * Validates that StableValue suppliers have minimal overhead in the hot path.
 *
 * NOTE: Cannot benchmark first-access (StableValue limitation), only subsequent.
 *
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
public class GlobalConfigBenchmark {
    
    @Setup(Level.Trial)
    public void setup() {
        // Initialize once per JVM (StableValue constraint)
        if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
            StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
        }
        
        // Force lazy initialization of all derived config
        StartYourMonkeys.GlobalConfig.TRUE_CELLS.get();
        StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
        StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();
        StartYourMonkeys.GlobalConfig.ODD_CLICK_INDICES.get();
        StartYourMonkeys.GlobalConfig.EVEN_CLICK_INDICES.get();
        StartYourMonkeys.GlobalConfig.SUFFIX_OR_MASKS.get();
        StartYourMonkeys.GlobalConfig.ODD_START_INDICES.get();
        StartYourMonkeys.GlobalConfig.EVEN_START_INDICES.get();
    }
    
    /**
     * Measures cost of accessing CLICK_TO_TRUE_CELL_MASK (most frequently accessed).
     * This is called in every TestClickCombination.buildParityMask() iteration.
     */
    @Benchmark
    public long[] access_CLICK_TO_TRUE_CELL_MASK() {
        return StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
    }
    
    /**
     * Measures cost of accessing EXPECTED_MASK (used in every adjacency check).
     */
    @Benchmark
    public long access_EXPECTED_MASK() {
        return StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();
    }
    
    /**
     * Simulates hot-path access pattern: multiple fields in sequence.
     * Mimics what happens in generator constraint checking.
     */
    @Benchmark
    public long hotPathAccess_generatorConstraint() {
        long[] masks = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
        long expected = StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();
        long[] suffixMasks = StartYourMonkeys.GlobalConfig.SUFFIX_OR_MASKS.get();
        
        // Simulate a constraint check
        long current = masks[5] | masks[10];
        long needed = current ^ expected;
        boolean valid = (suffixMasks[15] & needed) == needed;
        
        return valid ? 1L : 0L;
    }
    
    /**
     * Tests concurrent access from multiple threads (8 threads).
     * Validates that StableValue doesn't introduce contention.
     */
    @Benchmark
    @Threads(8)
    public long[] concurrentAccess_MASKS() {
        return StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
    }
    
    /**
     * Measures overhead vs. direct static final field access (baseline).
     * Compares GlobalConfig access to the old volatile-field approach.
     */
    private static final long[] BASELINE_MASKS = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
    
    @Benchmark
    public long[] baseline_staticFinalAccess() {
        return BASELINE_MASKS;
    }
}

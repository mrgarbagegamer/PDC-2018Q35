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

@State(Scope.Thread)
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
public class MonkeyBenchmark {

    // Mirroring the static fields in TestClickCombination for the benchmark
    private static long[] MASKS;
    private static long EXPECTED;

    private short[] prefix;
    private short finalClick;
    private long prefixMask;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize GlobalConfig to ensure static fields are populated
        // We use dummy values since we only care about the derived static data (masks)
        // Check if initialized to avoid re-initialization error if running multiple benchmarks
        try {
            if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
                StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
            }
        } catch (Exception e) {
            // Ignore if already initialized or other issues, we'll try to pull values anyway
        }

        MASKS = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
        EXPECTED = StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();

        prefix = new short[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
        finalClick = 9;
        
        // Pre-calculate mask for satisfiesOddAdjacency benchmark
        prefixMask = 0;
        for (short click : prefix) {
            prefixMask ^= MASKS[click];
        }
    }

    @Benchmark
    public long buildParityMask() {
        long mask = 0;
        for (short click : prefix) {
            mask ^= MASKS[click];
        }
        return mask;
    }

    @Benchmark
    public boolean satisfiesOddAdjacency() {
        return (prefixMask ^ MASKS[finalClick]) == EXPECTED;
    }
}
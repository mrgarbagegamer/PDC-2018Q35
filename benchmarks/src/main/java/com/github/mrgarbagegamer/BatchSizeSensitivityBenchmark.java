package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.BenchmarkUtils.createFullWorkBatch;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks the impact of varying final-click ranges within a {@link WorkBatch}.
 * 
 * <p>
 * While batches themselves are always filled before sending to queues, the actual work described by
 * each work item varies. Specifically, each work item defines a range of final clicks to test. This
 * benchmark measures how validation performance scales with different final click ranges,
 * simulating the iteration patterns that consumers experience.
 * </p>
 * 
 * <p>
 * These benchmarks take ~2m30s in total to run (3 forks x (5 warmup + 5 measurement) x 1 benchmark
 * x 5 ranges).
 * </p>
 * 
 * @since 2025.12 - Batch Range Analysis
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
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
public class BatchSizeSensitivityBenchmark {

    @Param({"32", "64", "128", "256", "512"})
    public int batchSize;

    private WorkBatch batch;
    private long[] masks;
    private long expected;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        setupGlobalConfig();
        random = new Random(42);

        masks = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
        expected = StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();

        // Fill a batch for iteration
        batch = createFullWorkBatch(random);
    }

    /**
     * Measures validation latency over a full batch with varying final-click ranges. Larger ranges
     * require checking more potential final clicks per work item, simulating higher consumer load.
     */
    @Benchmark
    public long batchValidationVaryingRanges() {
        long validCount = 0;

        for (WorkBatch.WorkItem item : batch) {
            short[] finalClicks = item.getFinalClicks();
            short[] prefix = item.getPrefix();
            int start = item.getStart();
            long prefixMask = 0;

            // Build prefix mask once
            for (short click : prefix) {
                prefixMask ^= masks[click];
            }

            // Validate range of final clicks (size varies by parameter)
            for (int i = start; i < finalClicks.length; i++) {
                short finalClick = finalClicks[i];
                if ((prefixMask ^ masks[finalClick]) == expected) {
                    validCount++;
                }
            }
        }

        return validCount;
    }
}

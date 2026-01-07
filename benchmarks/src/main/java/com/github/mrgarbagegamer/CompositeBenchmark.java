package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.BenchmarkUtils.createFullWorkBatch;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks realistic sequences of operations as they occur in production. Validates that
 * micro-benchmark optimizations hold in composite workflows.
 * 
 * <p>
 * These benchmarks take ~2m in total to run (3 forks x (5 warmup + 5 measurement) x 4 benchmarks).
 * </p>
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
public class CompositeBenchmark {

    private ArrayPool arrayPool;
    private WorkBatch fullBatch;
    private WorkBatch workBatch;
    private CombinationQueue queue;
    private short[] testPrefix;
    private short lastClick;
    private long[] masks;
    private long expected;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        setupGlobalConfig();
        random = new Random(42);

        arrayPool = new ArrayPool(512);
        queue = new CombinationQueue();
        workBatch = new WorkBatch();
        fullBatch = createFullWorkBatch(random);

        testPrefix = generateRandomPrefix(16, random);
        lastClick = (short) (testPrefix[testPrefix.length - 1] + 1);

        // Cache masks for benchmark
        masks = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
        expected = StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();
    }

    /**
     * Simulates a generator's leaf workflow:
     * 
     * <ol>
     * <li>Check if batch is full</li>
     * <li>If full, "flush" (clear) the batch</li>
     * <li>Add work item to batch</li>
     * </ol>
     */
    @Benchmark
    public boolean generatorLeaf_addWorkChain() {
        boolean wasFull = workBatch.isFull();

        if (wasFull) {
            // Simulate "resetting" a new batch after flush
            workBatch.clear();
        }
        return workBatch.addWork(testPrefix, lastClick, true);
    }

    /**
     * Simulates a consumer's processing workflow:
     * 
     * <ol>
     * <li>Iterate batch</li>
     * <li>Build parity mask for each item's prefix</li>
     * <li>Check adjacency for each item's final clicks</li>
     * </ol>
     */
    @Benchmark
    public long consumerProcessing_iterateAndCheck(Blackhole bh) {
        long checkCount = 0;
        // Simulate consumption
        for (WorkBatch.WorkItem item : fullBatch) {
            short[] finalClicks = item.getFinalClicks();
            int start = item.getStart();
            short[] prefix = item.getPrefix();

            // Build parity mask (mimic TestClickCombination.buildParityMask)
            long mask = 0;
            for (short click : prefix) {
                mask ^= masks[click];
            }

            // Check all final clicks
            for (int i = start; i < finalClicks.length; i++) {
                short finalClick = finalClicks[i];
                if ((mask ^ masks[finalClick]) == expected) {
                    checkCount++;
                }
            }
        }

        return checkCount;
    }

    /**
     * Full producer workflow (excluding pruning checks and task creation): get array → create
     * prefix → add to batch → return array.
     */
    @Benchmark
    public void producerFullCycle() {
        // Get from pool
        short[] prefix = arrayPool.get();
        if (prefix == null) {
            prefix = new short[16];
        }

        // Fill prefix (simulated)
        System.arraycopy(testPrefix, 0, prefix, 0, testPrefix.length);

        // Add to batch
        workBatch.addWork(prefix, lastClick, false);

        // Return to pool
        arrayPool.put(prefix);
    }

    /**
     * Realistic queue round-trip: producer offers batch, consumer polls and validates. This
     * simulates the actual producer-consumer handoff pattern with offer/poll.
     */
    @Benchmark
    public long queueRoundTripWithValidation() {
        // Simulate producer: fill partial batch
        WorkBatch batch = new WorkBatch();
        int itemsAdded = 0;
        while (itemsAdded < 4 && batch.addWork(testPrefix, lastClick, false)) {
            itemsAdded++;
        }

        // Simulate producer: offer to queue
        boolean offered = queue.add(batch);
        if (!offered) {
            return 0; // Queue full, back off
        }

        // Simulate consumer: poll from queue
        WorkBatch polledBatch = queue.getWorkBatch();
        if (polledBatch == null) {
            return 0;
        }

        // Simulate consumer: iterate and validate
        long validCount = 0;
        for (WorkBatch.WorkItem item : polledBatch) {
            short[] finalClicks = item.getFinalClicks();
            int start = item.getStart();
            short[] prefix = item.getPrefix();

            long prefixMask = 0;
            for (short click : prefix) {
                prefixMask ^= masks[click];
            }

            // Validate all final clicks
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

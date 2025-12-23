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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks realistic sequences of operations as they occur in production.
 * Validates that micro-benchmark optimizations hold in composite workflows.
 *
 * @since 2025.12 - JMH Benchmarking
 */
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
public class CompositeBenchmark {
    
    private ArrayPool arrayPool;
    private WorkBatch fullBatch;
    private WorkBatch workBatch;
    private short[] testPrefix;
    private short lastClick;
    
    @Setup(Level.Trial)
    public void setup() {
        if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
            StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
        }
        
        arrayPool = new ArrayPool(512);
        workBatch = new WorkBatch();
        fullBatch = new WorkBatch();
        
        testPrefix = new short[16];
        for (short i = 0; i < 16; i++) {
            testPrefix[i] = i;
        }
        lastClick = 16;

        // Pre-fill fullBatch
        while (!fullBatch.isFull()) {
            fullBatch.addWork(testPrefix, lastClick, false);
        }
    }
    
    /**
     * Simulates a generator's leaf workflow:
     * 1. Check if batch is full
     * 2. Add work to batch
     * 3. If full after add, prepare for flush
     */
    @Benchmark
    public boolean generatorLeaf_addWorkChain() {
        boolean wasFull = workBatch.isFull();
        boolean added = false;
        
        if (!wasFull) {
            added = workBatch.addWork(testPrefix, lastClick, false);
        }
        
        return added;
    }
    
    /**
     * Simulates a consumer's processing workflow:
     * 1. Iterate batch
     * 2. Build parity mask for each item's prefix
     * 3. Check adjacency for a sample final click
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
            long[] masks = StartYourMonkeys.GlobalConfig.CLICK_TO_TRUE_CELL_MASK.get();
            for (short click : prefix) {
                mask ^= masks[click];
            }
            
            // Simulate one adjacency check
            short finalClick = finalClicks[start];
            boolean valid = (mask ^ masks[finalClick]) == StartYourMonkeys.GlobalConfig.EXPECTED_MASK.get();
            
            if (valid) {
                checkCount++;
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
}

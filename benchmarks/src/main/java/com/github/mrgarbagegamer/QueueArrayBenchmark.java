package com.github.mrgarbagegamer;

import java.util.concurrent.ThreadLocalRandom;
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
 * Benchmarks for work distribution patterns in the CombinationQueueArray.
 * Tests queue selection, work-stealing, and load distribution.
 *
 * NOTE: Queues use relaxed operations that may spuriously fail even when not full/empty.
 * 
 * @since 2025.12 - JMH Benchmarking
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
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
public class QueueArrayBenchmark {
    
    private CombinationQueue[] queues;
    private CombinationQueue[] emptyQueues;
    private WorkBatch sharedBatch;
    private ThreadLocalRandom random;
    private WorkBatch[] batchInventory;
    
    @Setup(Level.Trial)
    public void setup() {
        if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
            StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
        }
        
        // Create queue array matching production (8 queues for 16 threads)
        int numQueues = 8;
        queues = new CombinationQueue[numQueues];
        for (int i = 0; i < numQueues; i++) {
            queues[i] = new CombinationQueue();
        }

        // Create empty queues for worst-case benchmark
        emptyQueues = new CombinationQueue[numQueues];
        for (int i = 0; i < numQueues; i++) {
            emptyQueues[i] = new CombinationQueue();
        }
        
        sharedBatch = new WorkBatch();

        batchInventory = new WorkBatch[100];
        for (int i = 0; i < 100; i++) {
            batchInventory[i] = new WorkBatch();
        }
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() {
        random = ThreadLocalRandom.current();
    }

    @Setup(Level.Iteration)
    public void setupWorkStealingState() {
        // Drain queues[0] to ensure it is empty
        while (queues[0].getWorkBatch() != null) {
            // Drain
        }

        // Populate queues[1] with batches from batchInventory (fill to ~half capacity, e.g., 8 items)
        // First drain it to ensure clean state
        while (queues[1].getWorkBatch() != null) {
            // Drain
        }

        for (int i = 0; i < 8; i++) {
            queues[1].add(batchInventory[i]);
        }
    }
    
    /**
     * Measures the cost of random queue selection (what producers do).
     */
    @Benchmark
    public CombinationQueue randomQueueSelection() {
        int idx = random.nextInt(queues.length);
        return queues[idx];
    }
    
    /**
     * Simulates the producer's flush pattern: random start + round-robin retry.
     * Mirrors CombinationGeneratorTask.flushBatchFast() logic.
     * Measures latency of finding an available queue.
     */
    @Benchmark
    public boolean producerQueueSelection_roundRobinRetry() {
        int startIdx = random.nextInt(queues.length);
        
        // Try each queue once (like flushBatchFast)
        for (int i = 0; i < queues.length; i++) {
            int idx = (startIdx + i) % queues.length;
            if (queues[idx].add(sharedBatch)) {
                return true;
            }
        }
        return false; // All queues full (would sleep in real code)
    }
    
    /**
     * Simulates the consumer's work-stealing pattern: check own queue first, then scan others.
     * Mirrors TestClickCombination.getWork() logic.
     * Measures cost of scanning all queues when primary is empty.
     */
    @Benchmark
    public WorkBatch consumerWorkStealing_linearScan() {
        // Try preferred queue first (simulated as queue 0)
        WorkBatch batch = queues[0].getWorkBatch();
        if (batch != null) {
            return batch;
        }
        
        // Try to steal from others (linear scan like TestClickCombination.getWork())
        for (int i = 1; i < queues.length; i++) {
            batch = queues[i].getWorkBatch();
            if (batch != null) {
                // Maintain steady state by adding a random batch back to queues[1]
                queues[1].add(batchInventory[random.nextInt(batchInventory.length)]);
                return batch;
            }
        }
        return null;
    }
    
    /**
     * Measures the worst-case: scanning all empty queues.
     * Identifies overhead of failed work-stealing attempt.
     * Important: queues may spuriously return null even if not empty.
     */
    @Benchmark
    public WorkBatch workStealing_allEmptyWorstCase() {
        // Try preferred queue first (guaranteed to be empty)
        WorkBatch batch = emptyQueues[0].getWorkBatch();
        if (batch != null) {
            return batch;
        }

        // Now scan all others (all guaranteed empty)
        for (int i = 0; i < emptyQueues.length; i++) {
            batch = emptyQueues[i].getWorkBatch();
            if (batch != null) {
                return batch;
            }
        }
        return null;
    }
    
    /**
     * Tests retry overhead when relaxed operations spuriously fail.
     * Simulates the sleep-and-retry pattern in flushBatchFast().
     */
    @Benchmark
    public int retryOverhead_multipleAttempts() {
        int attempts = 0;
        boolean success = false;
        
        // Try up to 10 times (mimics retry logic)
        while (attempts < 10 && !success) {
            int startIdx = random.nextInt(queues.length);
            for (int i = 0; i < queues.length && !success; i++) {
                if (queues[(startIdx + i) % queues.length].add(sharedBatch)) {
                    success = true;
                }
            }
            attempts++;
        }
        
        return attempts;
    }
}

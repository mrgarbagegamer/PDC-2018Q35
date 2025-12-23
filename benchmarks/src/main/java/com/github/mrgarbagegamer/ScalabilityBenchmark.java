package com.github.mrgarbagegamer;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.MpmcArrayQueue;
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
 * Characterizes how performance scales with increasing thread counts.
 * Identifies contention points and optimal thread configurations.
 *
 * @since 2025.12 - JMH Benchmarking
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
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
public class ScalabilityBenchmark {
    
    private MpmcArrayQueue<WorkBatch> centralPool;
    private CombinationQueue[] queues;
    private WorkBatch sharedBatch;
    
    @Setup(Level.Trial)
    public void setup() {
        if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
            StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
        }
        
        // Central pool
        int poolSize = 8 * 16;
        centralPool = new MpmcArrayQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            centralPool.offer(new WorkBatch());
        }
        
        // Queue array
        queues = new CombinationQueue[8];
        for (int i = 0; i < 8; i++) {
            queues[i] = new CombinationQueue();
        }
        
        sharedBatch = new WorkBatch();
    }
    
    /**
     * Central pool throughput scaling: 1, 2, 4, 8, 16 threads.
     */
    @Benchmark
    @Threads(1)
    public WorkBatch poolThroughput_1thread() {
        WorkBatch batch = centralPool.relaxedPoll();
        if (batch != null) {
            centralPool.offer(batch);
        }
        return batch;
    }
    
    @Benchmark
    @Threads(2)
    public WorkBatch poolThroughput_2threads() {
        WorkBatch batch = centralPool.relaxedPoll();
        if (batch != null) {
            centralPool.offer(batch);
        }
        return batch;
    }
    
    @Benchmark
    @Threads(4)
    public WorkBatch poolThroughput_4threads() {
        WorkBatch batch = centralPool.relaxedPoll();
        if (batch != null) {
            centralPool.offer(batch);
        }
        return batch;
    }
    
    @Benchmark
    @Threads(8)
    public WorkBatch poolThroughput_8threads() {
        WorkBatch batch = centralPool.relaxedPoll();
        if (batch != null) {
            centralPool.offer(batch);
        }
        return batch;
    }
    
    @Benchmark
    @Threads(16)
    public WorkBatch poolThroughput_16threads() {
        WorkBatch batch = centralPool.relaxedPoll();
        if (batch != null) {
            centralPool.offer(batch);
        }
        return batch;
    }
    
    /**
     * Queue array throughput scaling.
     */
    @Benchmark
    @Threads(1)
    public boolean queueArrayThroughput_1thread() {
        return queues[0].add(sharedBatch);
    }
    
    @Benchmark
    @Threads(2)
    public boolean queueArrayThroughput_2threads() {
        int idx = ThreadLocalRandom.current().nextInt(queues.length);
        return queues[idx].add(sharedBatch);
    }
    
    @Benchmark
    @Threads(4)
    public boolean queueArrayThroughput_4threads() {
        int idx = ThreadLocalRandom.current().nextInt(queues.length);
        return queues[idx].add(sharedBatch);
    }
    
    @Benchmark
    @Threads(8)
    public boolean queueArrayThroughput_8threads() {
        int idx = ThreadLocalRandom.current().nextInt(queues.length);
        return queues[idx].add(sharedBatch);
    }
    
    @Benchmark
    @Threads(16)
    public boolean queueArrayThroughput_16threads() {
        int idx = ThreadLocalRandom.current().nextInt(queues.length);
        return queues[idx].add(sharedBatch);
    }
}

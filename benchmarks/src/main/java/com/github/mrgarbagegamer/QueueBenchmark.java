package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.BenchmarkUtils.setupGlobalConfig;

import java.util.concurrent.TimeUnit;

import org.jctools.queues.MpmcArrayQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;

/**
 * Benchmarks for the CombinationQueue class, focusing on throughput and contention scenarios
 * typical in producer-consumer patterns.
 * 
 * <p>
 * These benchmarks take ~2m30s in total to run (3 forks x (5 warmup + 5 measurement) x 3 groups of
 * benchmarks).
 * </p>
 */
@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
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
public class QueueBenchmark {

    private CombinationQueue queue;
    private WorkBatch sharedBatch;
    private MpmcArrayQueue<WorkBatch> centralPool;
    private WorkBatch[] preallocatedBatches;

    @Setup(Level.Trial)
    public void setup() {
        queue = new CombinationQueue();
        // Initialize GlobalConfig for WorkBatch creation (if not already initialized)
        setupGlobalConfig();
        sharedBatch = new WorkBatch();

        // Initialize central pool (Tier 1 benchmark)
        int poolSize = 8 * 16; // 8 queues * 16 capacity each
        centralPool = new MpmcArrayQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            centralPool.offer(new WorkBatch());
        }

        // Pre-allocate batches for consumer-side benchmarks
        preallocatedBatches = new WorkBatch[1000];
        for (int i = 0; i < preallocatedBatches.length; i++) {
            preallocatedBatches[i] = new WorkBatch();
        }
    }

    @Benchmark
    @Group("throughput")
    @GroupThreads(4)
    public boolean addToQueue(Control control) {
        return queue.add(sharedBatch);
    }

    @Benchmark
    @Group("throughput")
    @GroupThreads(4)
    public WorkBatch getFromQueue(Control control) {
        return queue.getWorkBatch();
    }

    /**
     * Simulates producer pattern: continuously poll empty batches. Producers ONLY poll from the
     * central pool (never offer back to it). Uses relaxedPoll() to match production behavior.
     */
    @Benchmark
    @Group("centralPoolContention")
    @GroupThreads(8)
    public WorkBatch producerPattern_pollEmpty() {
        return centralPool.relaxedPoll();
    }

    /**
     * Simulates consumer pattern: continuously offer cleared batches. Consumers poll from their
     * queues and ONLY offer back to the central pool.
     */
    @Benchmark
    @Group("centralPoolContention")
    @GroupThreads(8)
    public void consumerPattern_offerEmpty() {
        int idx = Thread.currentThread().hashCode() % preallocatedBatches.length;
        WorkBatch batch = preallocatedBatches[idx];
        batch.clear();
        centralPool.offer(batch);
    }

    /**
     * Realistic mixed workload: 4 producers polling, 4 consumers offering. This simulates actual
     * runtime: producers drain the pool, consumers refill it.
     */
    @Benchmark
    @Group("mixedPoolAccess")
    @GroupThreads(4)
    public WorkBatch producerSide_poll() {
        WorkBatch batch = centralPool.relaxedPoll();
        if (batch != null) {
            batch.clear();
        }
        return batch;
    }

    @Benchmark
    @Group("mixedPoolAccess")
    @GroupThreads(4)
    public void consumerSide_offer() {
        int idx = Thread.currentThread().hashCode() % preallocatedBatches.length;
        WorkBatch batch = preallocatedBatches[idx];
        centralPool.offer(batch);
    }
}
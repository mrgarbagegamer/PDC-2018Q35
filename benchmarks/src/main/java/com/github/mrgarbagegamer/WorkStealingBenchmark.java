package com.github.mrgarbagegamer;

// import static com.github.mrgarbagegamer.util.BenchmarkUtils.setupGlobalConfig;

// import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

// import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
// import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
// import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

// TODO: Modify benchmarks as necessary to reflect recent changes to codebase
/**
 * Benchmarks the work-stealing patterns used by {@link TestClickCombination monkeys} to obtain work
 * from the queue system.
 * 
 * <p>
 * Each monkey first tries its own {@link CombinationQueue preferred queue} and then attempts to
 * steal work from other queues if its own is empty. This benchmark measures the performance of
 * different scenarios: successful first steal, cascading steals, and complete starvation.
 * </p>
 * 
 * <p>
 * These benchmarks take ~2m in total to run (3 forks x (5 warmup + 5 measurement) x 3 benchmarks).
 * </p>
 *
 * <h2>Profiling Recommendations</h2>
 *
 * <p>
 * <b>TIER 2: PRODUCER & DISTRIBUTION</b> - Shows cost of queue scanning under consumer contention:
 * </p>
 *
 * <ul>
 * <li><b>perfnorm:</b> Reveals atomic instruction latency and memory fence overhead.
 * 
 * <pre>
 * java -jar benchmarks/target/benchmarks.jar WorkStealingBenchmark.workSteal_ScanAndSteal -prof perfnorm
 * </pre>
 * 
 * Key metrics: cycles/op, atomic-ops, memory-operations</li>
 *
 * <li><b>Baseline (no profiler):</b>
 * 
 * <pre>
 * java -jar benchmarks/target/benchmarks.jar WorkStealingBenchmark
 * </pre>
 * 
 * </li>
 * </ul>
 *
 * @since 2025.12 - Work Stealing Analysis
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
public class WorkStealingBenchmark {

    // private CombinationQueue[] queues;
    // private CombinationQueue[] emptyQueues;
    // private WorkBatch[] batchInventory;
    // private ThreadLocalRandom random;

    // @Setup(Level.Trial)
    // public void setup() {
    //     setupGlobalConfig();

    //     // Create queue array matching production (8 queues for 16 threads)
    //     int numQueues = 8;
    //     queues = new CombinationQueue[numQueues];
    //     for (int i = 0; i < numQueues; i++) {
    //         queues[i] = new CombinationQueue();
    //     }

    //     // Create empty queues for worst-case benchmark
    //     emptyQueues = new CombinationQueue[numQueues];
    //     for (int i = 0; i < numQueues; i++) {
    //         emptyQueues[i] = new CombinationQueue();
    //     }

    //     // Populate batch inventory for steady-state scenarios
    //     batchInventory = new WorkBatch[100];
    //     for (int i = 0; i < 100; i++) {
    //         batchInventory[i] = new WorkBatch();
    //     }

    //     random = ThreadLocalRandom.current();
    // }

    // @Setup(Level.Iteration)
    // public void setupIteration() {
    //     // Before each iteration, populate queue[1] with batches to allow stealing
    //     for (int i = 0; i < 8; i++) {
    //         queues[1].offer(batchInventory[i % batchInventory.length]);
    //     }
    // }

    // /**
    //  * Benchmarks successful work retrieval from the preferred queue. This is the fast path where a
    //  * monkey finds work immediately in its own queue without stealing.
    //  */
    // @Benchmark
    // public WorkBatch workSteal_PreferredQueueHit() {
    //     return queues[0].relaxedPoll();
    // }

    // /**
    //  * Benchmarks the work-stealing pattern: preferred queue empty, must scan other queues to find
    //  * work. The benchmark maintains steady state by refilling queue[1] after a successful steal,
    //  * simulating the actual producer-consumer balance.
    //  */
    // @Benchmark
    // public WorkBatch workSteal_ScanAndSteal() {
    //     // Try preferred queue (queue 0 is empty in this scenario)
    //     WorkBatch batch = queues[0].relaxedPoll();
    //     if (batch != null) {
    //         return batch;
    //     }

    //     // Scan other queues to steal (queue 1 has work)
    //     for (int i = 1; i < queues.length; i++) {
    //         batch = queues[i].relaxedPoll();
    //         if (batch != null) {
    //             // Replenish queue[1] with a new batch to maintain steady state
    //             queues[1].relaxedOffer(batchInventory[random.nextInt(batchInventory.length)]);
    //             return batch;
    //         }
    //     }

    //     return null;
    // }

    // /**
    //  * Benchmarks the worst-case scenario: all queues are empty, resulting in a full linear scan
    //  * with no successful steal. This measures the cost of work starvation checks.
    //  */
    // @Benchmark
    // public int workSteal_FullScanStarvation() {
    //     int queuesScanned = 0;

    //     // Try preferred queue
    //     if (emptyQueues[0].relaxedPoll() == null) {
    //         queuesScanned++;
    //     }

    //     // Scan all others (all guaranteed empty in this scenario)
    //     for (int i = 1; i < emptyQueues.length; i++) {
    //         if (emptyQueues[i].relaxedPoll() == null) {
    //             queuesScanned++;
    //         }
    //     }

    //     return queuesScanned;
    // }
}

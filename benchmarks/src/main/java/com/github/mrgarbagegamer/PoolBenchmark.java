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
public class PoolBenchmark {

    private ArrayPool arrayPool;
    private TaskPool taskPool;
    private short[] dummyArray;
    private CombinationGeneratorTask dummyTask;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize GlobalConfig for ArrayPool sizing
        try {
            if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
                StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
            }
        } catch (Exception e) {
            // Ignore
        }

        // Use a large capacity to minimize boundary effects in single-sided benchmarks
        arrayPool = new ArrayPool(100);
        taskPool = new TaskPool(100);
        
        // Size the array at numClicks - 1 to match typical usage in CombinationGeneratorTask
        dummyArray = new short[15];
        dummyTask = new CombinationGeneratorTask(); 
    }

    @Benchmark
    public void arrayPool_roundtrip() {
        short[] array = arrayPool.get();
        if (array != null) {
            arrayPool.put(array);
        }
    }

    @Benchmark
    public void taskPool_roundtrip() {
        CombinationGeneratorTask task = taskPool.get();
        if (task != null) {
            taskPool.put(task);
        }
    }
    
    @Benchmark
    public short[] arrayPool_get() {
        return arrayPool.get();
    }
    
    @Benchmark
    public void arrayPool_put() {
        arrayPool.put(dummyArray);
    }

    @Benchmark
    public CombinationGeneratorTask taskPool_get() {
        return taskPool.get();
    }

    @Benchmark
    public void taskPool_put() {
        taskPool.put(dummyTask);
    }
}
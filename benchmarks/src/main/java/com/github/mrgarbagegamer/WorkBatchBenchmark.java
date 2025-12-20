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

@State(Scope.Benchmark)
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
public class WorkBatchBenchmark {

    private WorkBatch fullBatch;
    private WorkBatch emptyBatch;
    private short[] prefix;
    private short lastPrefixClick;

    @Setup(Level.Trial)
    public void setup() {
        if (!StartYourMonkeys.GlobalConfig.isInitialized()) {
            StartYourMonkeys.GlobalConfig.initialize(17, 16, new Grid35());
        }

        prefix = new short[WorkBatch.getNumClicks() - 1];
        for (short i = 0; i < prefix.length; i++) {
            prefix[i] = i;
        }
        lastPrefixClick = (short) (prefix.length - 1);

        fullBatch = new WorkBatch();
        while (fullBatch.addWork(prefix, lastPrefixClick, false)) {
            // Fill the batch to capacity
        }

        emptyBatch = new WorkBatch();
    }

    @Setup(Level.Iteration)
    public void resetEmptyBatch() {
        emptyBatch.clear();
    }

    @Benchmark
    public boolean addWork() {
        return emptyBatch.addWork(prefix, lastPrefixClick, false);
    }

    @Benchmark
    public boolean addWorkFullBatch() {
        return fullBatch.addWork(prefix, lastPrefixClick, false); // Should be false
    }

    @Benchmark
    public void iterate(Blackhole bh) {
        for (WorkBatch.WorkItem item : fullBatch) {
            bh.consume(item);
        }
    }
}
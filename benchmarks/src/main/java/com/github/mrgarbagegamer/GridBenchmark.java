package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.BenchmarkUtils.generateRandomCombination;
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

/**
 * {@link Benchmark}s for the {@link Grid} implementations.
 * 
 * <p>
 * These benchmarks take ~3m in total to run (3 forks x (5 warmup + 5 measurement) x 6 benchmarks).
 * </p>
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
public class GridBenchmark {

    private Grid grid;
    private short[] combination;
    private short finalClick;
    private short[] prefix;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        setupGlobalConfig();
        random = new Random(42);

        grid = new Grid35();
        // A dummy combination for testing click performance
        combination = generateRandomCombination(9, random);
        prefix = new short[8];
        System.arraycopy(combination, 0, prefix, 0, 8);
        finalClick = combination[8];
    }

    @Benchmark
    public void click_short_array() {
        // Measure the raw cost of applying a combination
        grid.click(combination);
    }

    @Benchmark
    public void click_short_array_short_val() {
        // Measure the cost of the split click (prefix + final)
        grid.click(prefix, finalClick);
    }

    @Benchmark
    public void initialize() {
        // Measure the cost of resetting the grid
        grid.initialize();
    }

    @Benchmark
    public boolean click_and_isSolved() {
        // This represents the Monkey's hot loop: apply moves, then check state.
        // This forces the lazy recalculation of trueCellsCount.
        grid.click(prefix, finalClick);
        boolean solved = grid.isSolved();

        // Reset state by initializing.
        grid.initialize();

        return solved;
    }

    @Benchmark
    public int getTrueCount_Clean() {
        // Since we don't modify the grid in this benchmark, the dirty flag remains false
        // (after the first call). This measures the cached path.
        return grid.getTrueCount();
    }

    @Benchmark
    public int getTrueCount_Dirty() {
        // This measures the cost of a click PLUS the cost of recalculation.
        grid.click(combination); // Dirty it
        int count = grid.getTrueCount(); // Recalculate

        // Reset state by clicking again (XOR undo)
        grid.click(combination);

        return count;
    }
}
package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.mrgarbagegamer.queues.QueueStrategies.JCToolsQueueStrategy;
import com.google.common.base.MoreObjects;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongImmutableList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.shorts.ShortList;

// TODO: Refactor this class to simplify the design and reduce the number of parameters, as well as
// potentially performing eager initialization of some fields.
// TODO: Add class-level Javadoc
public final class SolverConfiguration {
    private final int numClicks;
    private final int numThreads;
    private final int batchSize;
    private final int taskPoolSize;
    private final int queueSize;
    private final Grid baseGrid;
    private final SolutionHandler solutionHandler;
    private final Function<Class<?>, Logger> loggerFunction;
    private final GeneratorFactoryProvider generatorFactoryProvider;
    private final IntFunction<Queue<GeneratorContext>> registryQueueFunction;
    private final BiFunction<SolverConfiguration, SolverState, QueueStrategy> queueStrategyFactory;

    private SolverConfiguration(Builder builder) {
        this.numClicks = builder.numClicks;
        this.numThreads = builder.numThreads;
        this.batchSize = mustBePositive(builder.batchSize, "batchSize");
        this.taskPoolSize = mustBePositive(builder.taskPoolSize, "taskPoolSize");
        this.queueSize = mustBePositive(builder.queueSize, "queueSize");
        this.baseGrid = mustNotBeNull(builder.baseGrid, "baseGrid").copy();
        this.solutionHandler = mustNotBeNull(builder.solutionHandler, "solutionHandler");
        this.loggerFunction = mustNotBeNull(builder.loggerFunction, "loggerFunction");
        this.generatorFactoryProvider = mustNotBeNull(builder.generatorFactoryProvider,
                "generatorFactoryProvider");
        this.registryQueueFunction = mustNotBeNull(builder.registryQueueFunction,
                "registryQueueFunction");
        this.queueStrategyFactory = mustNotBeNull(builder.queueStrategyFactory,
                "queueStrategyFactory");
    }

    public static Builder builder() { return new Builder(); }

    public static SolverConfiguration forPuzzle(int numClicks, int numThreads, int puzzleNumber) {
        return builder().numClicks(numClicks).numThreads(numThreads)
                .baseGrid(createGridForPuzzle(puzzleNumber)).build();
    }

    public static SolverConfiguration forPuzzle(int numClicks, int numThreads) {
        return builder().numClicks(numClicks).numThreads(numThreads).build();
    }

    public static SolverConfiguration forPuzzle(int numClicks) {
        return builder().numClicks(numClicks).build();
    }

    public static Grid createGridForPuzzle(int puzzleNumber) {
        return switch (puzzleNumber) {
            case 13 -> new Grid13();
            case 22 -> new Grid22();
            case 35 -> new Grid35();
            default -> throw new IllegalArgumentException(
                    "Unsupported puzzle number: " + puzzleNumber);
        };
    }

    public int numClicks() { return this.numClicks; }

    public int numThreads() { return this.numThreads; }

    public int batchSize() { return this.batchSize; }

    public int taskPoolSize() { return this.taskPoolSize; }

    public int queueSize() { return this.queueSize; }

    public Grid baseGrid() { return this.baseGrid.copy(); }

    SolutionHandler solutionHandler() { return this.solutionHandler; }

    Queue<GeneratorContext> getRegistryQueue() {
        return this.registryQueueFunction.apply(this.numThreads() / 2);
    }

    boolean getUseDualMasks() { return this.baseGrid().getTrueCount() > 64; }

    LongList getTrueCellMasksLower() {
        return computeTrueCellMasksLower(ShortList.of(this.baseGrid().findTrueCells()));
    }

    LongList getTrueCellMasksUpper() {
        return computeTrueCellMasksUpper(ShortList.of(this.baseGrid().findTrueCells()),
                this.getUseDualMasks());
    }

    long getExpectedMaskLower() {
        return computeExpectedMaskLower(ShortList.of(this.baseGrid().findTrueCells()));
    }

    long getExpectedMaskUpper() {
        return computeExpectedMaskUpper(ShortList.of(this.baseGrid().findTrueCells()),
                this.getUseDualMasks());
    }

    ShortList getOddClickIndices() {
        return ShortList.of(this.baseGrid().findFirstTrueAdjacents());
    }

    ShortList getEvenClickIndices() { return Grid.invertCombination(this.getOddClickIndices()); }

    LongList getSuffixMasksLower() { return computeSuffixMasksLower(this.getTrueCellMasksLower()); }

    LongList getSuffixMasksUpper() {
        return computeSuffixMasksUpper(this.getTrueCellMasksUpper(), this.getUseDualMasks());
    }

    IntList getOddStartIndices() { return computeStartIndices(this.getOddClickIndices()); }

    IntList getEvenStartIndices() { return computeStartIndices(this.getEvenClickIndices()); }

    Logger getLogger(Class<?> clazz) { return this.loggerFunction.apply(clazz); }

    GeneratorFactory getGeneratorFactory(QueueStrategy queueStrategy, SolverState solverState,
            ContextRegistry registry) {
        return this.generatorFactoryProvider.create(this, queueStrategy, registry);
    }

    QueueStrategy getQueueStrategy(SolverState solverState) {
        return this.queueStrategyFactory.apply(this, solverState);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof SolverConfiguration other)
            return this.numClicks == other.numClicks && this.numThreads == other.numThreads
                    && this.batchSize == other.batchSize && this.taskPoolSize == other.taskPoolSize
                    && this.queueSize == other.queueSize && this.baseGrid.equals(other.baseGrid)
                    && this.solutionHandler.equals(other.solutionHandler)
                    && this.loggerFunction.equals(other.loggerFunction)
                    && this.generatorFactoryProvider.equals(other.generatorFactoryProvider)
                    && this.registryQueueFunction.equals(other.registryQueueFunction)
                    && this.queueStrategyFactory.equals(other.queueStrategyFactory);
        return false;
    }

    @Override
    public int hashCode() {
        int result = this.numClicks;
        result = 31 * result + this.numThreads;
        result = 31 * result + this.batchSize;
        result = 31 * result + this.taskPoolSize;
        result = 31 * result + this.queueSize;
        result = 31 * result + this.baseGrid.hashCode();
        result = 31 * result + this.solutionHandler.hashCode();
        result = 31 * result + this.loggerFunction.hashCode();
        result = 31 * result + this.generatorFactoryProvider.hashCode();
        result = 31 * result + this.registryQueueFunction.hashCode();
        result = 31 * result + this.queueStrategyFactory.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("numClicks", numClicks)
                .add("numThreads", numThreads).add("batchSize", batchSize)
                .add("taskPoolSize", taskPoolSize).add("queueSize", queueSize)
                .add("baseGrid", baseGrid).toString();
    }

    @FunctionalInterface
    public interface SolutionHandler {
        void handleSolution(short[] prefix, short finalClick, SolverState solverState,
                ForkJoinPool generatorPool, Logger logger);
    }

    @FunctionalInterface
    public interface GeneratorFactoryProvider {
        GeneratorFactory create(SolverConfiguration config, QueueStrategy queueStrategy,
                ContextRegistry registry);
    }

    public static class Builder {
        private int numClicks = 17;
        // TODO: Update the unit tests that manually specified the numThreads to 2 to use the
        // default.
        private int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), 2);
        private Grid baseGrid = new Grid35();
        private int batchSize = 256;
        private int taskPoolSize = 128;
        private int queueSize = 16;
        private SolutionHandler solutionHandler = SolverConfiguration::defaultSolutionHandling;
        private Function<Class<?>, Logger> loggerFunction = LogManager::getLogger;
        private GeneratorFactoryProvider generatorFactoryProvider = GeneratorFactory::ofDefault;
        private IntFunction<Queue<GeneratorContext>> registryQueueFunction = _ -> new ConcurrentLinkedQueue<>();
        private BiFunction<SolverConfiguration, SolverState, QueueStrategy> queueStrategyFactory = JCToolsQueueStrategy::multiSingle;

        public Builder numClicks(int numClicks) {
            checkArgument(numClicks > 0 && numClicks <= Grid.NUM_CELLS,
                    "numClicks must be in the range [1, %s], was %s", Grid.NUM_CELLS, numClicks);
            this.numClicks = numClicks;
            return this;
        }

        public Builder numThreads(int numThreads) {
            checkArgument(numThreads > 1, "numThreads must be greater than 1, was %s", numThreads);
            checkArgument(numThreads % 2 == 0,
                    "numThreads must be even to ensure generators and monkeys are balanced, was %s",
                    numThreads);
            this.numThreads = numThreads;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = mustBePositive(batchSize, "batchSize");
            return this;
        }

        public Builder taskPoolSize(int taskPoolSize) {
            this.taskPoolSize = mustBePositive(taskPoolSize, "taskPoolSize");
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = mustBePositive(queueSize, "queueSize");
            return this;
        }

        public Builder baseGrid(Grid baseGrid) {
            // TODO: Look at using another mechanism for copying the base grid to avoid reliance on
            // an abstract method.
            this.baseGrid = mustNotBeNull(baseGrid, "baseGrid").copy();
            return this;
        }

        public Builder solutionHandler(SolutionHandler solutionHandler) {
            this.solutionHandler = mustNotBeNull(solutionHandler, "solutionHandler");
            return this;
        }

        public Builder loggerFunction(Function<Class<?>, Logger> loggerFunction) {
            this.loggerFunction = mustNotBeNull(loggerFunction, "loggerFunction");
            return this;
        }

        public Builder generatorFactoryProvider(GeneratorFactoryProvider generatorFactoryProvider) {
            this.generatorFactoryProvider = mustNotBeNull(generatorFactoryProvider,
                    "generatorFactoryProvider");
            return this;
        }

        public Builder registryQueueFunction(
                IntFunction<Queue<GeneratorContext>> registryQueueFunction) {
            this.registryQueueFunction = mustNotBeNull(registryQueueFunction,
                    "registryQueueFunction");
            return this;
        }

        public Builder queueStrategyFactory(
                BiFunction<SolverConfiguration, SolverState, QueueStrategy> queueStrategyFactory) {
            this.queueStrategyFactory = mustNotBeNull(queueStrategyFactory, "queueStrategyFactory");
            return this;
        }

        public SolverConfiguration build() { return new SolverConfiguration(this); }
    }

    private static LongList computeTrueCellMasksLower(ShortList trueCells) {
        // Trim this array to only the lower 64 bits, then generate masks
        return generateTrueCellMasks(sublist(trueCells, 0, 64));
    }

    private static ShortList sublist(ShortList list, int fromIndex, int toIndex) {
        return list.subList(fromIndex, Math.min(toIndex, list.size()));
    }

    private static LongList generateTrueCellMasks(ShortList trimmedTrueCells) {
        // Create a list with an initial capacity of Grid.NUM_CELLS
        final LongList masks = new LongArrayList(Grid.NUM_CELLS);

        for (short cell = 0; cell < Grid.NUM_CELLS; cell++) {
            long mask = 0L;
            // Build mask for this cell: if this cell is adjacent to the jth true cell,
            // set the jth bit of the mask.
            for (int j = 0; j < trimmedTrueCells.size(); j++) {
                if (Grid.areAdjacent(cell, trimmedTrueCells.getShort(j))) {
                    mask |= (1L << j);
                }
            }
            masks.add(mask);
        }
        return new LongImmutableList(masks); // Return an immutable list
    }

    private static LongList computeTrueCellMasksUpper(ShortList trueCells, boolean useDualMasks) {
        // Let this method short-circuit if not using dual masks
        return useDualMasks ? generateTrueCellMasks(sublist(trueCells, 64, trueCells.size()))
                : new LongImmutableList(new long[Grid.NUM_CELLS]);
    }

    private static long computeExpectedMaskLower(ShortList trueCells) {
        return (1L << trueCells.size()) - 1;
    }

    private static long computeExpectedMaskUpper(ShortList trueCells, boolean useDualMasks) {
        return useDualMasks ? (1L << (trueCells.size() - 64)) - 1 : 0L;
    }

    private static LongList computeSuffixMasksLower(LongList trueCellMasksLower) {
        return computeSuffixMasks(trueCellMasksLower);
    }

    private static LongList computeSuffixMasksUpper(LongList trueCellMasksUpper,
            boolean useDualMasks) {
        return useDualMasks ? computeSuffixMasks(trueCellMasksUpper)
                : new LongImmutableList(new long[Grid.NUM_CELLS]);
    }

    private static LongList computeSuffixMasks(LongList trueCellMasks) {
        // Create a list with an initial capacity of Grid.NUM_CELLS
        final LongList suffixMasks = new LongArrayList(new long[Grid.NUM_CELLS]);

        // Compute suffix masks: iterate from the last cell to the first
        for (short cell = (short) (Grid.NUM_CELLS - 1); cell >= 0; cell--) {
            if (cell == Grid.NUM_CELLS - 1) {
                // The last cell's suffix mask is just its own true cell mask
                suffixMasks.set(cell, trueCellMasks.getLong(cell));
            } else {
                // The suffix mask for the current cell is the bitwise OR of the next
                // cell's suffix mask and the current cell's true cell mask
                suffixMasks.set(cell, suffixMasks.getLong(cell + 1) | trueCellMasks.getLong(cell));
            }
        }
        return new LongImmutableList(suffixMasks); // Return an immutable list
    }

    private static IntList computeStartIndices(ShortList clickIndices) {
        // Create a list with an initial capacity of Grid.NUM_CELLS
        final IntList startIndices = new IntArrayList(Grid.NUM_CELLS);
        int startIdx = 0; // index of the first click in clickIndices that is strictly > 'lastClick'

        // Iterate through all possible values of 'lastClick', from 0 to Grid.NUM_CELLS - 1
        for (short lastClick = 0; lastClick < Grid.NUM_CELLS; lastClick++) {
            // Advance startIdx until clickIndices.getShort(startIdx) > lastClick
            while (startIdx < clickIndices.size() && clickIndices.getShort(startIdx) <= lastClick) {
                startIdx++;
            }
            startIndices.add(startIdx);
        }

        return new IntImmutableList(startIndices);
    }

    private static void defaultSolutionHandling(short[] prefix, short finalClick,
            SolverState solverState, ForkJoinPool generatorPool, Logger logger) {
        // Default implementation: log the solution
        final short[] winningCombination = Arrays.copyOf(prefix, prefix.length + 1);
        winningCombination[prefix.length] = finalClick;
        solverState.markSolutionFound(winningCombination);
        logger.info("Found the solution as the following click combination: {}",
                new CombinationMessage(winningCombination.clone(), Grid.ValueFormat.Index));

        // Trigger the generator shutdown:
        if (generatorPool != null && !generatorPool.isShutdown()) {
            logger.debug("Triggering generator pool shutdown...");
            generatorPool.shutdownNow(); // Immediate shutdown without waiting
        }
    }
}

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
public record SolverConfiguration(int numClicks, int numThreads, int batchSize, int taskPoolSize,
        int queueSize, Grid baseGrid, SolutionHandler solutionHandler,
        Function<Class<?>, Logger> loggerFunction,
        GeneratorFactoryProvider generatorFactoryProvider, // Changed type
        Queue<GeneratorContext> registryQueue, QueueStrategyFactory queueStrategyFactory) {

    public SolverConfiguration(int numClicks, int numThreads, int batchSize, int taskPoolSize,
            int queueSize, Grid baseGrid, SolutionHandler solutionHandler,
            Function<Class<?>, Logger> loggerFunction,
            GeneratorFactoryProvider generatorFactoryProvider, // Changed type
            Queue<GeneratorContext> registryQueue, QueueStrategyFactory queueStrategyFactory) {
        checkArgument(numClicks > 0 && numClicks <= Grid.NUM_CELLS,
                "numClicks must be in range [1, %s], was %s", Grid.NUM_CELLS, numClicks);
        checkArgument(numThreads > 1, "numThreads must be greater than 1, was %s", numThreads);

        this.numClicks = numClicks;
        this.numThreads = numThreads;
        this.batchSize = mustBePositive(batchSize, "batchSize");
        this.taskPoolSize = mustBePositive(taskPoolSize, "taskPoolSize");
        this.queueSize = mustBePositive(queueSize, "queueSize");
        this.baseGrid = mustNotBeNull(baseGrid, "baseGrid").copy();
        this.solutionHandler = mustNotBeNull(solutionHandler, "solutionHandler");
        this.loggerFunction = mustNotBeNull(loggerFunction, "loggerFunction");
        this.generatorFactoryProvider = mustNotBeNull(generatorFactoryProvider,
                "generatorFactoryProvider");
        this.registryQueue = mustNotBeNull(registryQueue, "registryQueue");
        this.queueStrategyFactory = mustNotBeNull(queueStrategyFactory, "queueStrategyFactory");
    }

    private SolverConfiguration(Builder builder) {
        // One big constructor call (making sure to use requireNonNullElse for the derived fields)

        // Define the fields to be used:
        final int numClicks = builder.numClicks;
        final int numThreads = builder.numThreads;
        final int batchSize = builder.batchSize;
        final int taskPoolSize = builder.taskPoolSize;
        final int queueSize = builder.queueSize;
        final Grid baseGrid = builder.baseGrid;
        final SolutionHandler solutionHandler = builder.solutionHandler;
        final Function<Class<?>, Logger> loggerFunction = builder.loggerFunction;
        final GeneratorFactoryProvider generatorFactoryProvider = builder.generatorFactoryProvider;
        final Queue<GeneratorContext> registryQueue = builder.registryQueue;
        final QueueStrategyFactory queueStrategyFactory = builder.queueStrategyFactory;

        this(numClicks, numThreads, batchSize, taskPoolSize, queueSize, baseGrid,
                solutionHandler, loggerFunction, generatorFactoryProvider, registryQueue,
                queueStrategyFactory);
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

    @Override
    public Grid baseGrid() {
        return this.baseGrid.copy(); // Defensive copy to maintain immutability
    }

    public boolean getUseDualMasks() { return this.baseGrid().getTrueCount() > 64; }

    public LongList getTrueCellMasksLower() {
        return computeTrueCellMasksLower(ShortList.of(this.baseGrid().findTrueCells()));
    }

    public LongList getTrueCellMasksUpper() {
        return computeTrueCellMasksUpper(ShortList.of(this.baseGrid().findTrueCells()),
                this.getUseDualMasks());
    }

    public long getExpectedMaskLower() {
        return computeExpectedMaskLower(ShortList.of(this.baseGrid().findTrueCells()));
    }

    public long getExpectedMaskUpper() {
        return computeExpectedMaskUpper(ShortList.of(this.baseGrid().findTrueCells()),
                this.getUseDualMasks());
    }

    public ShortList getOddClickIndices() {
        return ShortList.of(this.baseGrid().findFirstTrueAdjacents());
    }

    public ShortList getEvenClickIndices() {
        return Grid.invertCombination(this.getOddClickIndices());
    }

    public LongList getSuffixMasksLower() {
        return computeSuffixMasksLower(this.getTrueCellMasksLower());
    }

    public LongList getSuffixMasksUpper() {
        return computeSuffixMasksUpper(this.getTrueCellMasksUpper(), this.getUseDualMasks());
    }

    public IntList getOddStartIndices() { return computeStartIndices(this.getOddClickIndices()); }

    public IntList getEvenStartIndices() { return computeStartIndices(this.getEvenClickIndices()); }

    public Logger getLogger(Class<?> clazz) { return this.loggerFunction.apply(clazz); }

    public GeneratorFactory getGeneratorFactory(QueueStrategy queueStrategy,
            SolverState solverState, ContextRegistry registry) {
        return this.generatorFactoryProvider.create(this, queueStrategy, registry);
    }

    public QueueStrategy getQueueStrategy(SolverState solverState) {
        return this.queueStrategyFactory.create(this, solverState);
    }

    @FunctionalInterface
    public interface SolutionHandler {
        void handleSolution(short[] prefix, short finalClick, SolverState solverState,
                ForkJoinPool generatorPool, Logger logger);
    }

    // Replace BiFunction with a TriFunction-style interface
    @FunctionalInterface
    public interface GeneratorFactoryProvider {
        GeneratorFactory create(SolverConfiguration config, QueueStrategy queueStrategy,
                ContextRegistry registry);
    }

    // TODO: Consider just using a BiFunction
    /**
     * A factory interface for {@link #create(SolverConfiguration, SolverState) creating}
     * {@link QueueStrategy} instances based on the provided {@link SolverConfiguration} and
     * {@link SolverState}. This allows for flexible instantiation of different queue strategies
     * that may require access to the configuration and state at creation time.
     * 
     * @see SolverConfiguration#getQueueStrategy(SolverState)
     * @see SolverConfiguration.Builder#queueStrategyFactory(QueueStrategyFactory)
     * @since 2026.02 - Queue Injection Refactor
     * @threading Thread-safe (since it should be stateless and only used for instantiation).
     */
    @FunctionalInterface
    public interface QueueStrategyFactory {
        /**
         * Creates a new {@link QueueStrategy} instance based on the provided
         * {@link SolverConfiguration} and {@link SolverState}.
         * 
         * @param config      the solver configuration to use for creating the queue strategy
         * @param solverState the solver state to use for creating the queue strategy
         * @return a new instance of {@link QueueStrategy} configured according to the provided
         *         configuration and state
         * @see SolverConfiguration#getQueueStrategy(SolverState)
         * @see SolverConfiguration.Builder#queueStrategyFactory(QueueStrategyFactory)
         * @since 2026.02 - Queue Injection Refactor
         * @threading Thread-safe (since it should be stateless and only used for instantiation).
         * @memory Allocates a new {@link QueueStrategy} instance.
         */
        QueueStrategy create(SolverConfiguration config, SolverState solverState);
    }

    public static class Builder {
        private int numClicks = 17;
        private int numThreads = Runtime.getRuntime().availableProcessors();
        private Grid baseGrid = new Grid35();
        private int batchSize = 256;
        private int taskPoolSize = 128;
        private int queueSize = 16;
        private SolutionHandler solutionHandler = SolverConfiguration::defaultSolutionHandling;
        private Function<Class<?>, Logger> loggerFunction = LogManager::getLogger;
        private GeneratorFactoryProvider generatorFactoryProvider = GeneratorFactory::ofDefault;
        private Queue<GeneratorContext> registryQueue = new ConcurrentLinkedQueue<>();
        private QueueStrategyFactory queueStrategyFactory = JCToolsQueueStrategy::multiSingle;

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

        public Builder loggerFunction(Logger logger) {
            mustNotBeNull(logger, "logger");
            return loggerFunction(clazz -> logger);
        }

        public Builder generatorFactoryProvider(GeneratorFactoryProvider generatorFactoryProvider) {
            this.generatorFactoryProvider = mustNotBeNull(generatorFactoryProvider,
                    "generatorFactoryProvider");
            return this;
        }

        public Builder generatorFactoryProvider(GeneratorFactory generatorFactory) {
            mustNotBeNull(generatorFactory, "generatorFactory");
            return generatorFactoryProvider((config, queueStrategy, registry) -> generatorFactory);
        }

        public Builder registryQueue(Queue<GeneratorContext> registryQueue) {
            this.registryQueue = mustNotBeNull(registryQueue, "registryQueue");
            return this;
        }

        public Builder queueStrategyFactory(QueueStrategyFactory queueStrategyFactory) {
            this.queueStrategyFactory = mustNotBeNull(queueStrategyFactory, "queueStrategyFactory");
            return this;
        }

        public Builder queueStrategyFactory(QueueStrategy queueStrategy) {
            mustNotBeNull(queueStrategy, "queueStrategy");
            return queueStrategyFactory((config, solverState) -> queueStrategy);
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

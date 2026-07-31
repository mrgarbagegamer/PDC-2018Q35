package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullIntList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullLongList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.copyOfNonNullShortList;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustBePositive;
import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNullElse;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

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
        int queueSize, Grid baseGrid, Supplier<Boolean> useDualMasks,
        Supplier<LongList> trueCellMasksLower, Supplier<LongList> trueCellMasksUpper,
        Supplier<Long> expectedMaskLower, Supplier<Long> expectedMaskUpper,
        Supplier<ShortList> oddClickIndices, Supplier<ShortList> evenClickIndices,
        Supplier<LongList> suffixMasksLower, Supplier<LongList> suffixMasksUpper,
        Supplier<IntList> oddStartIndices, Supplier<IntList> evenStartIndices,
        SolutionHandler solutionHandler, Function<Class<?>, Logger> loggerFunction,
        GeneratorFactoryProvider generatorFactoryProvider, // Changed type
        Queue<GeneratorContext> registryQueue, QueueStrategyFactory queueStrategyFactory) {

    // Internal static predicates and consumers for validation
    private static void ensureValidIndex(short cell) {
        // TODO: Debate whether an IndexOutOfBoundsException is more appropriate:
        checkArgument(cell >= 0 && cell < Grid.NUM_CELLS, "Index %s is out of bounds [0, %s)", cell,
                Grid.NUM_CELLS);
    }

    private static void ensureUniqueAndAscending(ShortList list) {
        // A list of size 0 or 1 is trivially valid
        if (list.size() <= 1) {
            return;
        }
        short previous = list.getShort(0);
        for (int i = 1; i < list.size(); i++) {
            short current = list.getShort(i);
            if (current <= previous) {
                if (current == previous) {
                    throw new IllegalArgumentException(
                            "List contains duplicate element: " + current);
                } else {
                    throw new IllegalArgumentException(
                            "List is not in ascending order: " + current + " < " + previous);
                }
            }
            previous = current;
        }
    }

    private static void ensureUpperMaskValid(long mask) {
        // Check that no bits from 45 onwards (zero-indexed) are toggled on, since
        // the total number of cells can't be greater than 109.
        checkArgument((mask >>> 45) == 0L, "Upper mask %s has bits set at or above index 45",
                Long.toBinaryString(mask));
    }

    private static Supplier<ShortList> trustedSupplier(short[] array) {
        // Since the array is trusted, we can directly wrap it without defensive copying
        final ShortList list = ShortList.of(array);
        return () -> list;
    }

    private static ShortList arrayToFastList(short[] array) {
        mustNotBeNull(array, "array");
        return switch (array.length) {
            case 0 -> ShortList.of();
            case 1 -> ShortList.of(array[0]);
            default -> ShortList.of(array.clone());
        };
    }

    private static LongList arrayToFastList(long[] array) {
        mustNotBeNull(array, "array");
        return switch (array.length) {
            case 0 -> LongList.of();
            case 1 -> LongList.of(array[0]);
            default -> LongList.of(array.clone());
        };
    }

    private static IntList arrayToFastList(int[] array) {
        mustNotBeNull(array, "array");
        return switch (array.length) {
            case 0 -> IntList.of();
            case 1 -> IntList.of(array[0]);
            default -> IntList.of(array.clone());
        };
    }

    public SolverConfiguration(int numClicks, int numThreads, int batchSize, int taskPoolSize,
            int queueSize, Grid baseGrid, Supplier<Boolean> useDualMasks,
            Supplier<LongList> trueCellMasksLower, Supplier<LongList> trueCellMasksUpper,
            Supplier<Long> expectedMaskLower, Supplier<Long> expectedMaskUpper,
            Supplier<ShortList> oddClickIndices, Supplier<ShortList> evenClickIndices,
            Supplier<LongList> suffixMasksLower, Supplier<LongList> suffixMasksUpper,
            Supplier<IntList> oddStartIndices, Supplier<IntList> evenStartIndices,
            SolutionHandler solutionHandler, Function<Class<?>, Logger> loggerFunction,
            GeneratorFactoryProvider generatorFactoryProvider, // Changed type
            Queue<GeneratorContext> registryQueue, QueueStrategyFactory queueStrategyFactory) {
        checkArgument(numClicks > 0 && numClicks <= Grid.NUM_CELLS,
                "numClicks must be in range [1, %s], was %s", Grid.NUM_CELLS, numClicks);
        checkArgument(numThreads > 1, "numThreads must be greater than 1, was %s", numThreads);

        // We can't validate the values of the Supplier parameters here, else we'd be forcing
        // their evaluation at construction time. Instead, we rely on the Builder to perform
        // validation at build-time.

        // Note that null checks are not performed on Supplier parameters, since
        // LazyConstant.of() will handle that for us.

        this.numClicks = numClicks;
        this.numThreads = numThreads;
        this.batchSize = mustBePositive(batchSize, "batchSize");
        this.taskPoolSize = mustBePositive(taskPoolSize, "taskPoolSize");
        this.queueSize = mustBePositive(queueSize, "queueSize");
        this.baseGrid = mustNotBeNull(baseGrid, "baseGrid").copy();
        this.useDualMasks = LazyConstant.of(useDualMasks);
        this.trueCellMasksLower = LazyConstant.of(trueCellMasksLower);
        this.trueCellMasksUpper = LazyConstant.of(trueCellMasksUpper);
        this.expectedMaskLower = LazyConstant.of(expectedMaskLower);
        this.expectedMaskUpper = LazyConstant.of(expectedMaskUpper);
        this.oddClickIndices = LazyConstant.of(oddClickIndices);
        this.evenClickIndices = LazyConstant.of(evenClickIndices);
        this.suffixMasksLower = LazyConstant.of(suffixMasksLower);
        this.suffixMasksUpper = LazyConstant.of(suffixMasksUpper);
        this.oddStartIndices = LazyConstant.of(oddStartIndices);
        this.evenStartIndices = LazyConstant.of(evenStartIndices);
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
        final Supplier<Boolean> useDualMasks = requireNonNullElse(builder.useDualMasks,
                () -> baseGrid.getTrueCount() > 64);
        final Supplier<LongList> trueCellMasksLower = requireNonNullElse(builder.trueCellMasksLower,
                () -> computeTrueCellMasksLower(ShortList.of(baseGrid.findTrueCells())));
        final Supplier<LongList> trueCellMasksUpper = requireNonNullElse(builder.trueCellMasksUpper,
                () -> computeTrueCellMasksUpper(ShortList.of(baseGrid.findTrueCells()),
                        useDualMasks.get()));
        final Supplier<Long> expectedMaskLower = requireNonNullElse(builder.expectedMaskLower,
                () -> computeExpectedMaskLower(ShortList.of(baseGrid.findTrueCells())));
        final Supplier<Long> expectedMaskUpper = requireNonNullElse(builder.expectedMaskUpper,
                () -> computeExpectedMaskUpper(ShortList.of(baseGrid.findTrueCells()),
                        useDualMasks.get()));
        final Supplier<ShortList> oddClickIndices = requireNonNullElse(builder.oddClickIndices,
                trustedSupplier(baseGrid.findFirstTrueAdjacents()));
        final Supplier<ShortList> evenClickIndices = requireNonNullElse(builder.evenClickIndices,
                () -> Grid.invertCombination(oddClickIndices.get()));
        final Supplier<LongList> suffixMasksLower = requireNonNullElse(builder.suffixMasksLower,
                () -> computeSuffixMasksLower(trueCellMasksLower.get()));
        final Supplier<LongList> suffixMasksUpper = requireNonNullElse(builder.suffixMasksUpper,
                () -> computeSuffixMasksUpper(trueCellMasksUpper.get(), useDualMasks.get()));
        final Supplier<IntList> oddStartIndices = requireNonNullElse(builder.oddStartIndices,
                () -> computeStartIndices(oddClickIndices.get()));
        final Supplier<IntList> evenStartIndices = requireNonNullElse(builder.evenStartIndices,
                () -> computeStartIndices(evenClickIndices.get()));
        final SolutionHandler solutionHandler = builder.solutionHandler;
        final Function<Class<?>, Logger> loggerFunction = builder.loggerFunction;
        final GeneratorFactoryProvider generatorFactoryProvider = builder.generatorFactoryProvider;
        final Queue<GeneratorContext> registryQueue = builder.registryQueue;
        final QueueStrategyFactory queueStrategyFactory = builder.queueStrategyFactory;

        this(numClicks, numThreads, batchSize, taskPoolSize, queueSize, baseGrid, useDualMasks,
                trueCellMasksLower, trueCellMasksUpper, expectedMaskLower, expectedMaskUpper,
                oddClickIndices, evenClickIndices, suffixMasksLower, suffixMasksUpper,
                oddStartIndices, evenStartIndices, solutionHandler, loggerFunction,
                generatorFactoryProvider, registryQueue, queueStrategyFactory);
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

        // Derived (yet overridable at build-time) fields
        private @Nullable Supplier<Boolean> useDualMasks;
        private @Nullable Supplier<LongList> trueCellMasksLower;
        private @Nullable Supplier<LongList> trueCellMasksUpper;
        private @Nullable Supplier<Long> expectedMaskLower;
        private @Nullable Supplier<Long> expectedMaskUpper;
        private @Nullable Supplier<ShortList> oddClickIndices;
        private @Nullable Supplier<ShortList> evenClickIndices;
        private @Nullable Supplier<LongList> suffixMasksLower;
        private @Nullable Supplier<LongList> suffixMasksUpper;
        private @Nullable Supplier<IntList> oddStartIndices;
        private @Nullable Supplier<IntList> evenStartIndices;

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

        public Builder useDualMasks(boolean useDualMasks) {
            this.useDualMasks = () -> useDualMasks;
            return this;
        }

        public Builder useDualMasks(Supplier<Boolean> useDualMasks) {
            this.useDualMasks = mustNotBeNull(useDualMasks, "useDualMasks");
            return this;
        }

        public Builder trueCellMasksLower(List<Long> trueCellMasksLower) {
            LongList copy = copyOfNonNullLongList(trueCellMasksLower, "trueCellMasksLower");

            // The list must have exactly Grid.NUM_CELLS elements
            checkArgument(copy.size() == Grid.NUM_CELLS,
                    "trueCellMasksLower must have exactly %s elements, but contains %s",
                    Grid.NUM_CELLS, copy.size());

            // Delegate to the supplier overload with a trusted supplier
            return trueCellMasksLower(() -> copy);
        }

        public Builder trueCellMasksLower(Supplier<LongList> trueCellMasksLower) {
            this.trueCellMasksLower = mustNotBeNull(trueCellMasksLower, "trueCellMasksLower");
            return this;
        }

        public Builder trueCellMasksLower(long[] trueCellMasksLower) {
            // Delegate to the LongList overload for validation
            return trueCellMasksLower(arrayToFastList(trueCellMasksLower));
        }

        public Builder trueCellMasksUpper(List<Long> trueCellMasksUpper) {
            LongList copy = copyOfNonNullLongList(trueCellMasksUpper, "trueCellMasksUpper");

            // Two conditions must be met:
            // - The list must have exactly Grid.NUM_CELLS elements
            checkArgument(copy.size() == Grid.NUM_CELLS,
                    "trueCellMasksUpper must have exactly %s elements, but contains %s",
                    Grid.NUM_CELLS, copy.size());

            // - The list's bitcount must be no greater than 45 (since there are at most 109 true
            // cells, and the upper mask can only have bits for true cells 65 to 109)
            for (int i = 0; i < copy.size(); i++) {
                ensureUpperMaskValid(copy.getLong(i));
            }

            // Delegate to the supplier overload with a trusted supplier
            return trueCellMasksUpper(() -> copy);
        }

        public Builder trueCellMasksUpper(Supplier<LongList> trueCellMasksUpper) {
            this.trueCellMasksUpper = mustNotBeNull(trueCellMasksUpper, "trueCellMasksUpper");
            return this;
        }

        public Builder trueCellMasksUpper(long[] trueCellMasksUpper) {
            // Delegate to the LongList overload for validation
            return trueCellMasksUpper(arrayToFastList(trueCellMasksUpper));
        }

        public Builder expectedMaskLower(long expectedMaskLower) {
            this.expectedMaskLower = () -> expectedMaskLower;
            return this;
        }

        public Builder expectedMaskLower(Supplier<Long> expectedMaskLower) {
            this.expectedMaskLower = mustNotBeNull(expectedMaskLower, "expectedMaskLower");
            return this;
        }

        public Builder expectedMaskUpper(long expectedMaskUpper) {
            ensureUpperMaskValid(expectedMaskUpper);
            return expectedMaskUpper(() -> expectedMaskUpper);
        }

        public Builder expectedMaskUpper(Supplier<Long> expectedMaskUpper) {
            this.expectedMaskUpper = mustNotBeNull(expectedMaskUpper, "expectedMaskUpper");
            return this;
        }

        public Builder oddClickIndices(List<Short> oddClickIndices) {
            ShortList copy = copyOfNonNullShortList(oddClickIndices, "oddClickIndices");

            // Confirm that the size is valid
            if (copy.size() < 2 || copy.size() > 6) {
                throw new IllegalArgumentException(
                        "oddClickIndices must contain between 2 and 6 elements");
            }

            // Indexed for-loop to avoid implicit boxing/unboxing overhead
            for (int i = 0; i < copy.size(); i++) {
                ensureValidIndex(copy.getShort(i));
            }

            // Validate that elements are unique and in ascending order
            ensureUniqueAndAscending(copy);

            // Delegate to the supplier overload with a trusted supplier
            return oddClickIndices(() -> copy);
        }

        public Builder oddClickIndices(Supplier<ShortList> oddClickIndices) {
            this.oddClickIndices = mustNotBeNull(oddClickIndices, "oddClickIndices");
            return this;
        }

        public Builder oddClickIndices(short[] oddClickIndices) {
            // Delegate to the ShortList overload for validation
            return oddClickIndices(arrayToFastList(oddClickIndices));
        }

        public Builder evenClickIndices(List<Short> evenClickIndices) {
            ShortList copy = copyOfNonNullShortList(evenClickIndices, "evenClickIndices");

            // Confirm that the size is valid
            if (copy.size() < (Grid.NUM_CELLS - 6) || copy.size() > (Grid.NUM_CELLS - 2)) {
                throw new IllegalArgumentException("evenClickIndices must contain between "
                        + (Grid.NUM_CELLS - 6) + " and " + (Grid.NUM_CELLS - 2) + " elements");
            }

            // Ensure that all indices are valid
            for (int i = 0; i < copy.size(); i++) {
                ensureValidIndex(copy.getShort(i));
            }

            // Validate that elements are unique and in ascending order
            ensureUniqueAndAscending(copy);

            // Delegate to the supplier overload with a trusted supplier
            return evenClickIndices(() -> copy);
        }

        public Builder evenClickIndices(Supplier<ShortList> evenClickIndices) {
            this.evenClickIndices = mustNotBeNull(evenClickIndices, "evenClickIndices");
            return this;
        }

        public Builder evenClickIndices(short[] evenClickIndices) {
            // Delegate to the ShortList overload for validation
            return evenClickIndices(arrayToFastList(evenClickIndices));
        }

        public Builder suffixMasksLower(List<Long> suffixMasksLower) {
            LongList copy = copyOfNonNullLongList(suffixMasksLower, "suffixMasksLower");

            // The list must satisfy two conditions;
            // - It must have exactly Grid.NUM_CELLS elements
            checkArgument(copy.size() == Grid.NUM_CELLS,
                    "suffixMasksLower must have exactly %s elements", Grid.NUM_CELLS);

            // - Each element of the list must have a bitcount that is less than or equal to that
            // of the preceding mask (to ensure proper suffix mask behavior)
            for (int i = 1; i < copy.size(); i++) {
                long previousMask = copy.getLong(i - 1);
                long currentMask = copy.getLong(i);
                checkArgument(Long.bitCount(currentMask) <= Long.bitCount(previousMask),
                        "Lower suffix mask at index %s has a bitcount (%s) greater than that of the previous mask (%s)",
                        i, Long.bitCount(currentMask), Long.bitCount(previousMask));
            }

            // Delegate to the supplier overload with a trusted supplier
            return suffixMasksLower(() -> copy);
        }

        public Builder suffixMasksLower(Supplier<LongList> suffixMasksLower) {
            this.suffixMasksLower = mustNotBeNull(suffixMasksLower, "suffixMasksLower");
            return this;
        }

        public Builder suffixMasksLower(long[] suffixMasksLower) {
            // Delegate to the LongList overload for validation
            return suffixMasksLower(arrayToFastList(suffixMasksLower));
        }

        public Builder suffixMasksUpper(List<Long> suffixMasksUpper) {
            LongList copy = copyOfNonNullLongList(suffixMasksUpper, "suffixMasksUpper");

            // The list must satisfy three conditions:
            // - The list must have exactly Grid.NUM_CELLS elements
            checkArgument(copy.size() == Grid.NUM_CELLS,
                    "suffixMasksUpper must have exactly %s elements", Grid.NUM_CELLS);

            // - Each element of the list must have a bitcount that is less than or equal to that
            // of the preceding mask (to ensure proper suffix mask behavior)
            for (int i = 1; i < copy.size(); i++) {
                long previousMask = copy.getLong(i - 1);
                long currentMask = copy.getLong(i);
                checkArgument(Long.bitCount(currentMask) <= Long.bitCount(previousMask),
                        "Upper suffix mask at index %s has a bitcount (%s) greater than that of the previous mask (%s)",
                        i, Long.bitCount(currentMask), Long.bitCount(previousMask));
            }

            // - Each element of the list must have a bitcount no greater than 45
            for (int i = 0; i < copy.size(); i++) {
                ensureUpperMaskValid(copy.getLong(i));
            }

            // Delegate to the supplier overload with a trusted supplier
            return suffixMasksUpper(() -> copy);
        }

        public Builder suffixMasksUpper(Supplier<LongList> suffixMasksUpper) {
            this.suffixMasksUpper = mustNotBeNull(suffixMasksUpper, "suffixMasksUpper");
            return this;
        }

        public Builder suffixMasksUpper(long[] suffixMasksUpper) {
            // Delegate to the LongList overload for validation
            return suffixMasksUpper(arrayToFastList(suffixMasksUpper));
        }

        public Builder oddStartIndices(List<Integer> oddStartIndices) {
            IntList copy = copyOfNonNullIntList(oddStartIndices, "oddStartIndices");

            // The list must satisfy 3 conditions:
            // - It must have exactly Grid.NUM_CELLS elements
            checkArgument(copy.size() == Grid.NUM_CELLS,
                    "oddStartIndices must have exactly %s elements", Grid.NUM_CELLS);

            // - Each element must be in the range from 0 to oddClickIndices.size() (which is at
            // most 6).
            for (int i = 0; i < copy.size(); i++) {
                int index = copy.getInt(i);
                checkArgument(index >= 0 && index <= 6,
                        "oddStartIndex at index %s is out of range: %s (must be between 0 and 6)",
                        i, index);
            }

            // - Each element must be non-decreasing:
            for (int i = 1; i < copy.size(); i++) {
                checkArgument(copy.getInt(i) >= copy.getInt(i - 1),
                        "oddStartIndex at index %s (%s) is less than the previous index (%s)", i,
                        copy.getInt(i), copy.getInt(i - 1));
            }

            // Delegate to the supplier overload with a trusted supplier
            return oddStartIndices(() -> copy);
        }

        public Builder oddStartIndices(Supplier<IntList> oddStartIndices) {
            this.oddStartIndices = mustNotBeNull(oddStartIndices, "oddStartIndices");
            return this;
        }

        public Builder oddStartIndices(int[] oddStartIndices) {
            // Delegate to the IntList overload for validation
            return oddStartIndices(arrayToFastList(oddStartIndices));
        }

        public Builder evenStartIndices(List<Integer> evenStartIndices) {
            IntList copy = copyOfNonNullIntList(evenStartIndices, "evenStartIndices");

            // The list must satisfy 3 conditions:
            // - It must have exactly Grid.NUM_CELLS elements
            checkArgument(copy.size() == Grid.NUM_CELLS,
                    "evenStartIndices must have exactly %s elements", Grid.NUM_CELLS);

            // - Each element must be in the range from 0 to evenClickIndices.size() (which is at
            // most 103).
            for (int i = 0; i < copy.size(); i++) {
                int index = copy.getInt(i);
                checkArgument(index >= 0 && index <= 103,
                        "evenStartIndex at index %s is out of range: %s (must be between 0 and 103)",
                        i, index);
            }

            // - Each element must be non-decreasing:
            for (int i = 1; i < copy.size(); i++) {
                checkArgument(copy.getInt(i) >= copy.getInt(i - 1),
                        "evenStartIndex at index %s (%s) is less than the previous index (%s)", i,
                        copy.getInt(i), copy.getInt(i - 1));
            }

            // Delegate to the supplier overload with a trusted supplier
            return evenStartIndices(() -> copy);
        }

        public Builder evenStartIndices(Supplier<IntList> evenStartIndices) {
            this.evenStartIndices = mustNotBeNull(evenStartIndices, "evenStartIndices");
            return this;
        }

        public Builder evenStartIndices(int[] evenStartIndices) {
            // Delegate to the IntList overload for validation
            return evenStartIndices(arrayToFastList(evenStartIndices));
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

    // TODO: Consider deprecating the auto-generated getters for the Suppliers in favor of their
    // get__() counterparts.
}

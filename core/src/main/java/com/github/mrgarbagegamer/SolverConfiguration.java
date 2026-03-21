package com.github.mrgarbagegamer;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.mrgarbagegamer.queues.JCToolsQueueStrategy;

import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.longs.LongImmutableList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.shorts.ShortConsumer;
import it.unimi.dsi.fastutil.shorts.ShortImmutableList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortLists;
import it.unimi.dsi.fastutil.shorts.ShortPredicate;

// TODO: Add class-level Javadoc
public record SolverConfiguration(int numClicks, int numThreads, int batchSize, int arrayPoolSize,
        int taskPoolSize, int queueSize, Grid baseGrid, Supplier<ShortList> trueCells,
        Supplier<Boolean> useDualMasks, Supplier<LongList> trueCellMasksLower,
        Supplier<LongList> trueCellMasksUpper, Supplier<Long> expectedMaskLower,
        Supplier<Long> expectedMaskUpper, Supplier<ShortList> oddClickIndices,
        Supplier<ShortList> evenClickIndices, Supplier<LongList> suffixMasksLower,
        Supplier<LongList> suffixMasksUpper, Supplier<IntList> oddStartIndices,
        Supplier<IntList> evenStartIndices, SolutionHandler solutionHandler,
        Function<Class<?>, Logger> loggerFunction,
        GeneratorFactoryProvider generatorFactoryProvider, // Changed type
        Queue<GeneratorContext> registryQueue, QueueStrategyFactory queueStrategyFactory) {

    // Internal static predicates and consumers for validation
    private static final ShortPredicate VALID_INDEX_PREDICATE = cell -> cell >= 0
            && cell < Grid.NUM_CELLS;
    private static final ShortConsumer ENSURE_VALID_INDEX = cell -> {
        if (!VALID_INDEX_PREDICATE.test(cell)) {
            throw new IllegalArgumentException("trueCells contains an invalid cell index: " + cell);
        }
    };

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

    private static final LongPredicate VALID_UPPER_MASK_PREDICATE = mask -> Long
            .bitCount(mask) <= 45;
    private static final LongConsumer ENSURE_VALID_UPPER_MASK = mask -> {
        if (!VALID_UPPER_MASK_PREDICATE.test(mask)) {
            throw new IllegalArgumentException(
                    "Upper mask contains an invalid mask: " + Long.toBinaryString(mask));
        }
    };

    private static final Predicate<List<?>> LIST_SIZE_MATCHES_GRID_PREDICATE = list -> list
            .size() == Grid.NUM_CELLS;

    private static Supplier<ShortList> defensiveSupplier(ShortList list) {
        return switch (list) {
            case final ShortImmutableList immutable -> () -> immutable;
            case final ShortLists.Singleton singleton -> () -> singleton;
            default -> () -> new ShortImmutableList(list);
        };
    }

    @SuppressWarnings("unused")
    private static Supplier<ShortList> defensiveSupplier(short[] array) {
        return defensiveSupplier(arrayToFastList(array));
    }

    private static Supplier<LongList> defensiveSupplier(LongList list) {
        return switch (list) {
            case final LongImmutableList immutable -> () -> immutable;
            case final LongLists.Singleton singleton -> () -> singleton;
            default -> () -> new LongImmutableList(list);
        };
    }

    @SuppressWarnings("unused")
    private static Supplier<LongList> defensiveSupplier(long[] array) {
        return defensiveSupplier(arrayToFastList(array));
    }

    private static Supplier<IntList> defensiveSupplier(IntList list) {
        return switch (list) {
            case final IntImmutableList immutable -> () -> immutable;
            case final IntLists.Singleton singleton -> () -> singleton;
            default -> () -> new IntImmutableList(list);
        };
    }

    @SuppressWarnings("unused")
    private static Supplier<IntList> defensiveSupplier(int[] array) {
        return defensiveSupplier(arrayToFastList(array));
    }

    private static Supplier<ShortList> trustedSupplier(ShortList list) {
        // Since the list is trusted, we can directly wrap it without defensive copying
        return () -> list;
    }

    private static Supplier<ShortList> trustedSupplier(short[] array) {
        // Since the array is trusted, we can directly wrap it without defensive copying
        return trustedSupplier(ShortList.of(array));
    }

    private static Supplier<LongList> trustedSupplier(LongList list) {
        // Since the list is trusted, we can directly wrap it without defensive copying
        return () -> list;
    }

    @SuppressWarnings("unused")
    private static Supplier<LongList> trustedSupplier(long[] array) {
        // Since the array is trusted, we can directly wrap it without defensive copying
        return trustedSupplier(LongList.of(array));
    }

    private static Supplier<IntList> trustedSupplier(IntList list) {
        // Since the list is trusted, we can directly wrap it without defensive copying
        return () -> list;
    }

    @SuppressWarnings("unused")
    private static Supplier<IntList> trustedSupplier(int[] array) {
        // Since the array is trusted, we can directly wrap it without defensive copying
        return trustedSupplier(IntList.of(array));
    }

    private static ShortList arrayToFastList(short[] array) {
        return switch (array.length) {
            case 0 -> ShortList.of();
            case 1 -> ShortList.of(array[0]);
            default -> ShortList.of(array.clone());
        };
    }

    private static LongList arrayToFastList(long[] array) {
        return switch (array.length) {
            case 0 -> LongList.of();
            case 1 -> LongList.of(array[0]);
            default -> LongList.of(array.clone());
        };
    }

    private static IntList arrayToFastList(int[] array) {
        return switch (array.length) {
            case 0 -> IntList.of();
            case 1 -> IntList.of(array[0]);
            default -> IntList.of(array.clone());
        };
    }

    public SolverConfiguration(int numClicks, int numThreads, int batchSize, int arrayPoolSize,
            int taskPoolSize, int queueSize, Grid baseGrid, Supplier<ShortList> trueCells,
            Supplier<Boolean> useDualMasks, Supplier<LongList> trueCellMasksLower,
            Supplier<LongList> trueCellMasksUpper, Supplier<Long> expectedMaskLower,
            Supplier<Long> expectedMaskUpper, Supplier<ShortList> oddClickIndices,
            Supplier<ShortList> evenClickIndices, Supplier<LongList> suffixMasksLower,
            Supplier<LongList> suffixMasksUpper, Supplier<IntList> oddStartIndices,
            Supplier<IntList> evenStartIndices, SolutionHandler solutionHandler,
            Function<Class<?>, Logger> loggerFunction,
            GeneratorFactoryProvider generatorFactoryProvider, // Changed type
            Queue<GeneratorContext> registryQueue, QueueStrategyFactory queueStrategyFactory) {
        // TODO: Consider importing Guava's Preconditions for validation
        if (numClicks <= 0 || numClicks > Grid.NUM_CELLS) {
            throw new IllegalArgumentException(
                    "numClicks must be in the range 1 to " + Grid.NUM_CELLS);
        } else if (numThreads <= 1) {
            throw new IllegalArgumentException("numThreads must be greater than 1");
        } else if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        } else if (arrayPoolSize <= 0) {
            throw new IllegalArgumentException("arrayPoolSize must be positive");
        } else if (taskPoolSize <= 0) {
            throw new IllegalArgumentException("taskPoolSize must be positive");
        } else if (queueSize <= 0) {
            throw new IllegalArgumentException("queueSize must be positive");
        }

        // We can't validate the values of the Supplier parameters here, else we'd be forcing
        // their evaluation at construction time. Instead, we rely on the Builder to perform
        // validation at build-time.

        // Note that null checks are not performed on Supplier parameters, since
        // StableValue.supplier() will handle that for us.

        this.numClicks = numClicks;
        this.numThreads = numThreads;
        this.batchSize = batchSize;
        this.arrayPoolSize = arrayPoolSize;
        this.taskPoolSize = taskPoolSize;
        this.queueSize = queueSize;
        this.baseGrid = requireNonNull(baseGrid); // Avoid a copy, in hopes that the caller
                                                  // maintains immutability
        this.trueCells = StableValue.supplier(trueCells);
        this.useDualMasks = StableValue.supplier(useDualMasks);
        this.trueCellMasksLower = StableValue.supplier(trueCellMasksLower);
        this.trueCellMasksUpper = StableValue.supplier(trueCellMasksUpper);
        this.expectedMaskLower = StableValue.supplier(expectedMaskLower);
        this.expectedMaskUpper = StableValue.supplier(expectedMaskUpper);
        this.oddClickIndices = StableValue.supplier(oddClickIndices);
        this.evenClickIndices = StableValue.supplier(evenClickIndices);
        this.suffixMasksLower = StableValue.supplier(suffixMasksLower);
        this.suffixMasksUpper = StableValue.supplier(suffixMasksUpper);
        this.oddStartIndices = StableValue.supplier(oddStartIndices);
        this.evenStartIndices = StableValue.supplier(evenStartIndices);
        this.solutionHandler = requireNonNull(solutionHandler);
        this.loggerFunction = requireNonNull(loggerFunction);
        this.generatorFactoryProvider = requireNonNull(generatorFactoryProvider);
        this.registryQueue = requireNonNull(registryQueue);
        this.queueStrategyFactory = requireNonNull(queueStrategyFactory);
    }

    private SolverConfiguration(Builder builder) {
        // One big constructor call (making sure to use requireNonNullElse for the derived fields)

        // Define the fields to be used:
        final int numClicks = builder.numClicks;
        final int numThreads = builder.numThreads;
        final int batchSize = builder.batchSize;
        final int arrayPoolSize = builder.arrayPoolSize;
        final int taskPoolSize = builder.taskPoolSize;
        final int queueSize = builder.queueSize;
        final Grid baseGrid = builder.baseGrid;
        final Supplier<ShortList> trueCells = requireNonNullElse(builder.trueCells,
                trustedSupplier(baseGrid.findTrueCells()));
        final Supplier<Boolean> useDualMasks = requireNonNullElse(builder.useDualMasks,
                () -> baseGrid.getTrueCount() > 64);
        final Supplier<LongList> trueCellMasksLower = requireNonNullElse(builder.trueCellMasksLower,
                trustedSupplier(computeTrueCellMasksLower(trueCells.get())));
        final Supplier<LongList> trueCellMasksUpper = requireNonNullElse(builder.trueCellMasksUpper,
                () -> computeTrueCellMasksUpper(trueCells.get(), useDualMasks.get()));
        final Supplier<Long> expectedMaskLower = requireNonNullElse(builder.expectedMaskLower,
                () -> computeExpectedMaskLower(trueCells.get()));
        final Supplier<Long> expectedMaskUpper = requireNonNullElse(builder.expectedMaskUpper,
                () -> computeExpectedMaskUpper(trueCells.get(), useDualMasks.get()));
        final Supplier<ShortList> oddClickIndices = requireNonNullElse(builder.oddClickIndices,
                trustedSupplier(baseGrid.findFirstTrueAdjacents()));
        final Supplier<ShortList> evenClickIndices = requireNonNullElse(builder.evenClickIndices,
                trustedSupplier(Grid.invertCombination(oddClickIndices.get())));
        final Supplier<LongList> suffixMasksLower = requireNonNullElse(builder.suffixMasksLower,
                () -> computeSuffixMasksLower(trueCellMasksLower.get()));
        final Supplier<LongList> suffixMasksUpper = requireNonNullElse(builder.suffixMasksUpper,
                () -> computeSuffixMasksUpper(trueCellMasksUpper.get(), useDualMasks.get()));
        final Supplier<IntList> oddStartIndices = requireNonNullElse(builder.oddStartIndices,
                () -> computeStartIndices(oddClickIndices.get()));
        final Supplier<IntList> evenStartIndices = requireNonNullElse(builder.evenStartIndices,
                () -> computeStartIndices(evenClickIndices.get()));
        final SolutionHandler solutionHandler = requireNonNullElse(builder.solutionHandler,
                SolverConfiguration::defaultSolutionHandling);
        final Function<Class<?>, Logger> loggerFunction = requireNonNullElse(builder.loggerFunction,
                LogManager::getLogger);
        final GeneratorFactoryProvider generatorFactoryProvider = requireNonNullElse(
                builder.generatorFactoryProvider, GeneratorFactory::ofDefault);
        final Queue<GeneratorContext> registryQueue = requireNonNullElse(builder.registryQueue,
                new ConcurrentLinkedQueue<>());
        final QueueStrategyFactory queueStrategyFactory = requireNonNullElse(
                builder.queueStrategyFactory, JCToolsQueueStrategy::multiSingle);

        this(numClicks, numThreads, batchSize, arrayPoolSize, taskPoolSize, queueSize, baseGrid,
                trueCells, useDualMasks, trueCellMasksLower, trueCellMasksUpper, expectedMaskLower,
                expectedMaskUpper, oddClickIndices, evenClickIndices, suffixMasksLower,
                suffixMasksUpper, oddStartIndices, evenStartIndices, solutionHandler,
                loggerFunction, generatorFactoryProvider, registryQueue, queueStrategyFactory);
    }

    public static Builder builder() {
        return new Builder();
    }

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
        return baseGrid.copy(); // Defensive copy to maintain immutability
    }

    public ShortList getTrueCells() {
        return trueCells.get();
    }

    public boolean getUseDualMasks() {
        return useDualMasks.get();
    }

    public LongList getTrueCellMasksLower() {
        return trueCellMasksLower.get();
    }

    public LongList getTrueCellMasksUpper() {
        return trueCellMasksUpper.get();
    }

    public long getExpectedMaskLower() {
        return expectedMaskLower.get();
    }

    public long getExpectedMaskUpper() {
        return expectedMaskUpper.get();
    }

    public ShortList getOddClickIndices() {
        return oddClickIndices.get();
    }

    public ShortList getEvenClickIndices() {
        return evenClickIndices.get();
    }

    public LongList getSuffixMasksLower() {
        return suffixMasksLower.get();
    }

    public LongList getSuffixMasksUpper() {
        return suffixMasksUpper.get();
    }

    public IntList getOddStartIndices() {
        return oddStartIndices.get();
    }

    public IntList getEvenStartIndices() {
        return evenStartIndices.get();
    }

    public Logger getLogger(Class<?> clazz) {
        return loggerFunction.apply(clazz);
    }

    public GeneratorFactory getGeneratorFactory(QueueStrategy queueStrategy,
            SolverState solverState, ContextRegistry registry) {
        return generatorFactoryProvider.create(this, queueStrategy, registry); // Pass 'this' here
    }

    public QueueStrategy getQueueStrategy(SolverState solverState) {
        return queueStrategyFactory.create(this, solverState); // Pass 'this' here
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
        private int arrayPoolSize = 512;
        private int taskPoolSize = 128;
        private int queueSize = 16;

        // Derived (yet overridable at build-time) fields
        private Supplier<ShortList> trueCells;
        private Supplier<Boolean> useDualMasks;
        private Supplier<LongList> trueCellMasksLower;
        private Supplier<LongList> trueCellMasksUpper;
        private Supplier<Long> expectedMaskLower;
        private Supplier<Long> expectedMaskUpper;
        private Supplier<ShortList> oddClickIndices;
        private Supplier<ShortList> evenClickIndices;
        private Supplier<LongList> suffixMasksLower;
        private Supplier<LongList> suffixMasksUpper;
        private Supplier<IntList> oddStartIndices;
        private Supplier<IntList> evenStartIndices;
        private SolutionHandler solutionHandler;
        private Function<Class<?>, Logger> loggerFunction;
        private GeneratorFactoryProvider generatorFactoryProvider;
        private Queue<GeneratorContext> registryQueue;
        private QueueStrategyFactory queueStrategyFactory;

        private static ShortList shortListToFastList(List<Short> list) {
            return switch (list.size()) {
                case 0 -> ShortList.of();
                case 1 -> ShortList.of(list.get(0));
                default -> new ShortImmutableList(list);
            };
        }

        private static LongList longListToFastList(List<Long> list) {
            return switch (list.size()) {
                case 0 -> LongList.of();
                case 1 -> LongList.of(list.get(0));
                default -> new LongImmutableList(list);
            };
        }

        private static IntList intListToFastList(List<Integer> list) {
            return switch (list.size()) {
                case 0 -> IntList.of();
                case 1 -> IntList.of(list.get(0));
                default -> new IntImmutableList(list);
            };
        }

        public Builder numClicks(int numClicks) {
            if (numClicks <= 0 || numClicks > Grid.NUM_CELLS) {
                throw new IllegalArgumentException(
                        "numClicks must be in the range 1 to " + Grid.NUM_CELLS);
            }
            this.numClicks = numClicks;
            return this;
        }

        public Builder numThreads(int numThreads) {
            if (numThreads <= 1) {
                throw new IllegalArgumentException("numThreads must be greater than 1");
            } else if (numThreads % 2 != 0) {
                throw new IllegalArgumentException(
                        "numThreads must be even to ensure generators and monkeys are balanced");
            }
            this.numThreads = numThreads;
            return this;
        }

        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be positive");
            }
            this.batchSize = batchSize;
            return this;
        }

        public Builder arrayPoolSize(int arrayPoolSize) {
            if (arrayPoolSize <= 0) {
                throw new IllegalArgumentException("arrayPoolSize must be positive");
            }
            this.arrayPoolSize = arrayPoolSize;
            return this;
        }

        public Builder taskPoolSize(int taskPoolSize) {
            if (taskPoolSize <= 0) {
                throw new IllegalArgumentException("taskPoolSize must be positive");
            }
            this.taskPoolSize = taskPoolSize;
            return this;
        }

        public Builder queueSize(int queueSize) {
            if (queueSize <= 0) {
                throw new IllegalArgumentException("queueSize must be positive");
            }
            this.queueSize = queueSize;
            return this;
        }

        public Builder baseGrid(Grid baseGrid) {
            this.baseGrid = requireNonNull(baseGrid);
            return this;
        }

        public Builder trueCells(ShortList trueCells) {
            // TODO: Consider importing Guava's Preconditions for validation
            // Ensure that the input is valid
            if (trueCells.size() == 0) {
                throw new IllegalArgumentException("trueCells cannot be empty");
            } else if (trueCells.size() > Grid.NUM_CELLS) {
                throw new IllegalArgumentException(
                        "trueCells cannot contain more than " + Grid.NUM_CELLS + " elements");
            } else {
                trueCells.forEach(ENSURE_VALID_INDEX);
            }

            // Validate that elements are unique and in ascending order
            ensureUniqueAndAscending(trueCells);

            // Delegate to the supplier overload with a defensive supplier
            return trueCells(defensiveSupplier(trueCells));
        }

        public Builder trueCells(Supplier<ShortList> trueCells) {
            this.trueCells = requireNonNull(trueCells);
            return this;
        }

        public Builder trueCells(short[] trueCells) {
            // Delegate to the ShortList overload for validation
            return trueCells(arrayToFastList(trueCells));
        }

        public Builder trueCells(List<Short> trueCells) {
            // Delegate to the ShortList overload for validation
            return trueCells(shortListToFastList(trueCells));
        }

        public Builder useDualMasks(boolean useDualMasks) {
            this.useDualMasks = () -> useDualMasks;
            return this;
        }

        public Builder useDualMasks(Supplier<Boolean> useDualMasks) {
            this.useDualMasks = requireNonNull(useDualMasks);
            return this;
        }

        public Builder trueCellMasksLower(LongList trueCellMasksLower) {
            // The list must have exactly Grid.NUM_CELLS elements
            LIST_SIZE_MATCHES_GRID_PREDICATE.test(trueCellMasksLower);

            // Delegate to the supplier overload with a defensive supplier
            return trueCellMasksLower(defensiveSupplier(trueCellMasksLower));
        }

        public Builder trueCellMasksLower(Supplier<LongList> trueCellMasksLower) {
            this.trueCellMasksLower = requireNonNull(trueCellMasksLower);
            return this;
        }

        public Builder trueCellMasksLower(long[] trueCellMasksLower) {
            // Delegate to the LongList overload for validation
            return trueCellMasksLower(arrayToFastList(trueCellMasksLower));
        }

        public Builder trueCellMasksLower(List<Long> trueCellMasksLower) {
            // Delegate to the LongList overload for validation
            return trueCellMasksLower(longListToFastList(trueCellMasksLower));
        }

        public Builder trueCellMasksUpper(LongList trueCellMasksUpper) {
            // Two conditions must be met:
            // - The list must have exactly Grid.NUM_CELLS elements
            LIST_SIZE_MATCHES_GRID_PREDICATE.test(trueCellMasksUpper);

            // - The list's bitcount must be no greater than 45 (since there are at most 109 true
            // cells, and the upper mask can only have bits for true cells 65 to 109)
            trueCellMasksUpper.forEach(ENSURE_VALID_UPPER_MASK);

            // Delegate to the supplier overload with a defensive supplier
            return trueCellMasksUpper(defensiveSupplier(trueCellMasksUpper));
        }

        public Builder trueCellMasksUpper(Supplier<LongList> trueCellMasksUpper) {
            this.trueCellMasksUpper = requireNonNull(trueCellMasksUpper);
            return this;
        }

        public Builder trueCellMasksUpper(long[] trueCellMasksUpper) {
            // Delegate to the LongList overload for validation
            return trueCellMasksUpper(arrayToFastList(trueCellMasksUpper));
        }

        public Builder trueCellMasksUpper(List<Long> trueCellMasksUpper) {
            // Delegate to the LongList overload for validation
            return trueCellMasksUpper(longListToFastList(trueCellMasksUpper));
        }

        public Builder expectedMaskLower(long expectedMaskLower) {
            this.expectedMaskLower = () -> expectedMaskLower;
            return this;
        }

        public Builder expectedMaskLower(Supplier<Long> expectedMaskLower) {
            this.expectedMaskLower = requireNonNull(expectedMaskLower);
            return this;
        }

        public Builder expectedMaskUpper(long expectedMaskUpper) {
            // The upper mask must have a bitcount no greater than 45:
            VALID_UPPER_MASK_PREDICATE.test(expectedMaskUpper);
            return expectedMaskUpper(() -> expectedMaskUpper);
        }

        public Builder expectedMaskUpper(Supplier<Long> expectedMaskUpper) {
            this.expectedMaskUpper = requireNonNull(expectedMaskUpper);
            return this;
        }

        public Builder oddClickIndices(ShortList oddClickIndices) {
            // Confirm that the size is valid
            if (oddClickIndices.size() < 2 || oddClickIndices.size() > 6) {
                throw new IllegalArgumentException(
                        "oddClickIndices must contain between 2 and 6 elements");
            }

            // Ensure that all indices are valid
            oddClickIndices.forEach(ENSURE_VALID_INDEX);

            // Validate that elements are unique and in ascending order
            ensureUniqueAndAscending(oddClickIndices);

            // Delegate to the supplier overload with a defensive supplier
            return oddClickIndices(defensiveSupplier(oddClickIndices));
        }

        public Builder oddClickIndices(Supplier<ShortList> oddClickIndices) {
            this.oddClickIndices = requireNonNull(oddClickIndices);
            return this;
        }

        public Builder oddClickIndices(short[] oddClickIndices) {
            // Delegate to the ShortList overload for validation
            return oddClickIndices(arrayToFastList(oddClickIndices));
        }

        public Builder oddClickIndices(List<Short> oddClickIndices) {
            // Delegate to the ShortList overload for validation
            return oddClickIndices(shortListToFastList(oddClickIndices));
        }

        public Builder evenClickIndices(ShortList evenClickIndices) {
            // Confirm that the size is valid
            if (evenClickIndices.size() < (Grid.NUM_CELLS - 6)
                    || evenClickIndices.size() > (Grid.NUM_CELLS - 2)) {
                throw new IllegalArgumentException("evenClickIndices must contain between "
                        + (Grid.NUM_CELLS - 6) + " and " + (Grid.NUM_CELLS - 2) + " elements");
            }

            // Ensure that all indices are valid
            evenClickIndices.forEach(ENSURE_VALID_INDEX);

            // Validate that elements are unique and in ascending order
            ensureUniqueAndAscending(evenClickIndices);

            // Delegate to the supplier overload with a defensive supplier
            return evenClickIndices(defensiveSupplier(evenClickIndices));
        }

        public Builder evenClickIndices(Supplier<ShortList> evenClickIndices) {
            this.evenClickIndices = requireNonNull(evenClickIndices);
            return this;
        }

        public Builder evenClickIndices(short[] evenClickIndices) {
            // Delegate to the ShortList overload for validation
            return evenClickIndices(arrayToFastList(evenClickIndices));
        }

        public Builder evenClickIndices(List<Short> evenClickIndices) {
            // Delegate to the ShortList overload for validation
            return evenClickIndices(shortListToFastList(evenClickIndices));
        }

        public Builder suffixMasksLower(LongList suffixMasksLower) {
            // The list must satisfy two conditions;
            // - It must have exactly Grid.NUM_CELLS elements
            LIST_SIZE_MATCHES_GRID_PREDICATE.test(suffixMasksLower);

            // - Each element of the list must have a bitcount that is less than or equal to that
            // of the preceding mask (to ensure proper suffix mask behavior)
            final LongConsumer ensureValidSuffixMask = new LongConsumer() {
                private long previousMask = Long.MAX_VALUE;

                @Override
                public void accept(long mask) {
                    if (Long.bitCount(mask) > Long.bitCount(previousMask)) {
                        throw new IllegalArgumentException(
                                "suffixMasksLower contains an invalid suffix mask: "
                                        + Long.toBinaryString(mask)
                                        + " (bitcount greater than that of the previous mask: "
                                        + Long.toBinaryString(previousMask) + ")");
                    }
                    previousMask = mask;
                }
            };
            suffixMasksLower.forEach(ensureValidSuffixMask);

            // Delegate to the supplier overload with a defensive supplier
            return suffixMasksLower(defensiveSupplier(suffixMasksLower));
        }

        public Builder suffixMasksLower(Supplier<LongList> suffixMasksLower) {
            this.suffixMasksLower = requireNonNull(suffixMasksLower);
            return this;
        }

        public Builder suffixMasksLower(long[] suffixMasksLower) {
            // Delegate to the LongList overload for validation
            return suffixMasksLower(arrayToFastList(suffixMasksLower));
        }

        public Builder suffixMasksLower(List<Long> suffixMasksLower) {
            // Delegate to the LongList overload for validation
            return suffixMasksLower(longListToFastList(suffixMasksLower));
        }

        public Builder suffixMasksUpper(LongList suffixMasksUpper) {
            // The list must satisfy three conditions:
            // - The list must have exactly Grid.NUM_CELLS elements
            LIST_SIZE_MATCHES_GRID_PREDICATE.test(suffixMasksUpper);

            // - Each element of the list must have a bitcount that is less than or equal to that
            // of the preceding mask (to ensure proper suffix mask behavior)
            final LongConsumer ensureValidSuffixMask = new LongConsumer() {
                private long previousMask = Long.MAX_VALUE;

                @Override
                public void accept(long mask) {
                    if (Long.bitCount(mask) > Long.bitCount(previousMask)) {
                        throw new IllegalArgumentException(
                                "suffixMasksUpper contains an invalid suffix mask: "
                                        + Long.toBinaryString(mask)
                                        + " (bitcount greater than that of the previous mask: "
                                        + Long.toBinaryString(previousMask) + ")");
                    }
                    previousMask = mask;
                }
            };
            suffixMasksUpper.forEach(ensureValidSuffixMask);

            // - Each element of the list must have a bitcount no greater than 45
            suffixMasksUpper.forEach(ENSURE_VALID_UPPER_MASK);

            // Delegate to the supplier overload with a defensive supplier
            return suffixMasksUpper(defensiveSupplier(suffixMasksUpper));
        }

        public Builder suffixMasksUpper(Supplier<LongList> suffixMasksUpper) {
            this.suffixMasksUpper = requireNonNull(suffixMasksUpper);
            return this;
        }

        public Builder suffixMasksUpper(long[] suffixMasksUpper) {
            // Delegate to the LongList overload for validation
            return suffixMasksUpper(arrayToFastList(suffixMasksUpper));
        }

        public Builder suffixMasksUpper(List<Long> suffixMasksUpper) {
            // Delegate to the LongList overload for validation
            return suffixMasksUpper(longListToFastList(suffixMasksUpper));
        }

        public Builder oddStartIndices(IntList oddStartIndices) {
            // The list must satisfy 3 conditions:
            // - It must have exactly Grid.NUM_CELLS elements
            LIST_SIZE_MATCHES_GRID_PREDICATE.test(oddStartIndices);

            // - Each element must be in the range from 0 to oddClickIndices.size() (which is at
            // most 6).
            final IntConsumer ensureValidStartIndex = index -> {
                if (index < 0 || index > 6) {
                    throw new IllegalArgumentException(
                            "oddStartIndices contains an invalid start index: " + index);
                }
            };
            oddStartIndices.forEach(ensureValidStartIndex);

            // - Each element must be non-decreasing:
            final IntConsumer ensureNonDecreasing = new IntConsumer() {
                private int previousIndex = -1;

                @Override
                public void accept(int index) {
                    if (index < previousIndex) {
                        throw new IllegalArgumentException(
                                "oddStartIndices contains a decreasing start index: " + index);
                    }
                    previousIndex = index;
                }
            };
            oddStartIndices.forEach(ensureNonDecreasing);

            // Delegate to the supplier overload with a defensive supplier
            return oddStartIndices(defensiveSupplier(oddStartIndices));
        }

        public Builder oddStartIndices(Supplier<IntList> oddStartIndices) {
            this.oddStartIndices = requireNonNull(oddStartIndices);
            return this;
        }

        public Builder oddStartIndices(int[] oddStartIndices) {
            // Delegate to the IntList overload for validation
            return oddStartIndices(arrayToFastList(oddStartIndices));
        }

        public Builder oddStartIndices(List<Integer> oddStartIndices) {
            // Delegate to the IntList overload for validation
            return oddStartIndices(intListToFastList(oddStartIndices));
        }

        public Builder evenStartIndices(IntList evenStartIndices) {
            // The list must satisfy 3 conditions:
            // - It must have exactly Grid.NUM_CELLS elements
            LIST_SIZE_MATCHES_GRID_PREDICATE.test(evenStartIndices);

            // - Each element must be in the range from 0 to evenClickIndices.size() (which is at
            // most 103).
            final IntConsumer ensureValidStartIndex = index -> {
                if (index < 0 || index > 103) {
                    throw new IllegalArgumentException(
                            "evenStartIndices contains an invalid start index: " + index);
                }
            };
            evenStartIndices.forEach(ensureValidStartIndex);

            // - Each element must be non-decreasing:
            final IntConsumer ensureNonDecreasing = new IntConsumer() {
                private int previousIndex = -1;

                @Override
                public void accept(int index) {
                    if (index < previousIndex) {
                        throw new IllegalArgumentException(
                                "evenStartIndices contains a decreasing start index: " + index);
                    }
                    previousIndex = index;
                }
            };
            evenStartIndices.forEach(ensureNonDecreasing);

            // Delegate to the supplier overload with a defensive supplier
            return evenStartIndices(defensiveSupplier(evenStartIndices));
        }

        public Builder evenStartIndices(Supplier<IntList> evenStartIndices) {
            this.evenStartIndices = requireNonNull(evenStartIndices);
            return this;
        }

        public Builder evenStartIndices(int[] evenStartIndices) {
            // Delegate to the IntList overload for validation
            return evenStartIndices(arrayToFastList(evenStartIndices));
        }

        public Builder evenStartIndices(List<Integer> evenStartIndices) {
            // Delegate to the IntList overload for validation
            return evenStartIndices(intListToFastList(evenStartIndices));
        }

        public Builder solutionHandler(SolutionHandler solutionHandler) {
            this.solutionHandler = requireNonNull(solutionHandler);
            return this;
        }

        public Builder loggerFunction(Function<Class<?>, Logger> loggerFunction) {
            this.loggerFunction = requireNonNull(loggerFunction);
            return this;
        }

        public Builder loggerFunction(Logger logger) {
            return loggerFunction(clazz -> requireNonNull(logger));
        }

        public Builder generatorFactoryProvider(GeneratorFactoryProvider generatorFactoryProvider) {
            this.generatorFactoryProvider = requireNonNull(generatorFactoryProvider);
            return this;
        }

        public Builder generatorFactoryProvider(GeneratorFactory generatorFactory) {
            return generatorFactoryProvider(
                    (config, queueStrategy, registry) -> requireNonNull(generatorFactory));
        }

        public Builder registryQueue(Queue<GeneratorContext> registryQueue) {
            this.registryQueue = requireNonNull(registryQueue);
            return this;
        }

        public Builder queueStrategyFactory(QueueStrategyFactory queueStrategyFactory) {
            this.queueStrategyFactory = requireNonNull(queueStrategyFactory);
            return this;
        }

        public Builder queueStrategyFactory(QueueStrategy queueStrategy) {
            // TODO: Update this (and all) requireNonNull checks to include the name for better
            // debugging
            requireNonNull(queueStrategy);
            return queueStrategyFactory((config, solverState) -> queueStrategy);
        }

        public Builder reset() {
            // Reset all fields of this builder to their default values.
            this.numClicks = 17;
            this.numThreads = Runtime.getRuntime().availableProcessors();
            this.baseGrid = new Grid35();
            this.batchSize = 256;
            this.arrayPoolSize = 512;
            this.taskPoolSize = 128;
            this.queueSize = 16;
            this.trueCells = null;
            this.useDualMasks = null;
            this.trueCellMasksLower = null;
            this.trueCellMasksUpper = null;
            this.expectedMaskLower = null;
            this.expectedMaskUpper = null;
            this.oddClickIndices = null;
            this.evenClickIndices = null;
            this.suffixMasksLower = null;
            this.suffixMasksUpper = null;
            this.oddStartIndices = null;
            this.evenStartIndices = null;
            this.solutionHandler = null;
            this.loggerFunction = null;
            this.generatorFactoryProvider = null;
            this.registryQueue = null;
            this.queueStrategyFactory = null;
            return this;
        }

        public SolverConfiguration build() {
            return new SolverConfiguration(this);
        }
    }

    private static LongList computeTrueCellMasksLower(ShortList trueCells) {
        // Trim this array to only the lower 64 bits, then generate masks
        return generateTrueCellMasks(sublist(trueCells, 0, 64));
    }

    private static ShortList sublist(ShortList list, int fromIndex, int toIndex) {
        return list.subList(fromIndex, Math.min(toIndex, list.size()));
    }

    private static LongList generateTrueCellMasks(ShortList trimmedTrueCells) {
        final long[] masks = new long[Grid.NUM_CELLS];
        for (short cell = 0; cell < Grid.NUM_CELLS; cell++) {
            long mask = 0L;
            for (int j = 0; j < trimmedTrueCells.size(); j++) {
                if (Grid.areAdjacent(cell, trimmedTrueCells.getShort(j))) {
                    mask |= (1L << j);
                }
            }
            masks[cell] = mask;
        }
        return LongList.of(masks);
    }

    private static LongList computeTrueCellMasksUpper(ShortList trueCells, boolean useDualMasks) {
        // Let this method short-circuit if not using dual masks
        return useDualMasks ? generateTrueCellMasks(sublist(trueCells, 64, trueCells.size()))
                : LongList.of(new long[Grid.NUM_CELLS]);
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
                : LongList.of(new long[Grid.NUM_CELLS]);
    }

    // Extracted to prevent code duplication
    private static LongList computeSuffixMasks(LongList trueCellMasks) {
        final long[] suffixMasks = new long[Grid.NUM_CELLS];
        for (short cell = (short) (Grid.NUM_CELLS - 1); cell >= 0; cell--) {
            if (cell == Grid.NUM_CELLS - 1) {
                suffixMasks[cell] = trueCellMasks.getLong(cell);
            } else {
                suffixMasks[cell] = suffixMasks[cell + 1] | trueCellMasks.getLong(cell);
            }
        }
        return LongList.of(suffixMasks);
    }

    private static IntList computeStartIndices(ShortList clickIndices) {
        final int[] startIndices = new int[Grid.NUM_CELLS];
        int clickIdx = 0;

        for (short lastClick = 0; lastClick < Grid.NUM_CELLS; lastClick++) {
            while (clickIdx < clickIndices.size() && clickIndices.getShort(clickIdx) <= lastClick) {
                clickIdx++;
            }
            startIndices[lastClick] = clickIdx;
        }

        return IntList.of(startIndices);
    }

    private static void defaultSolutionHandling(short[] prefix, short finalClick,
            SolverState solverState, ForkJoinPool generatorPool, Logger logger) {
        // Default implementation: log the solution
        final short[] winningCombination = new short[prefix.length + 1];
        System.arraycopy(prefix, 0, winningCombination, 0, prefix.length);
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

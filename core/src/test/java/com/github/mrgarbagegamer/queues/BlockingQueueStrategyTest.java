package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredBlocking;
import static com.github.mrgarbagegamer.queues.QueueUtils.newBoundedImmutableQueueList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.SolverStateBridge;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueStrategies.BlockingQueueStrategy;

class BlockingQueueStrategyTest {
    private static final int DEFAULT_QUEUE_CAPACITY = 16;
    private static final int DEFAULT_NUM_THREADS = 4;

    private static SolverConfiguration createValidConfig(int numThreads, int queueSize) {
        return SolverConfiguration.builder().numThreads(numThreads).queueSize(queueSize).build();
    }

    private static SolverConfiguration createValidConfig(int numThreads) {
        return createValidConfig(numThreads, DEFAULT_QUEUE_CAPACITY);
    }

    private static SolverConfiguration createValidConfig() {
        return createValidConfig(DEFAULT_NUM_THREADS);
    }

    private static SolverState createValidState(SolverConfiguration config) {
        return SolverStateBridge.createInstance(config);
    }

    private static List<ArrayBlockingQueue<WorkBatch>> createValidQueueList(int numQueues,
            int queueCapacity) {
        return newBoundedImmutableQueueList(numQueues, queueCapacity, ArrayBlockingQueue::new);
    }

    private static List<ArrayBlockingQueue<WorkBatch>> createValidQueueList(int numQueues) {
        return createValidQueueList(numQueues, 16);
    }

    @Nested
    class StaticFactoryTests {
        record StaticFactoryTestCase(
                BiFunction<SolverConfiguration, SolverState, BlockingQueueStrategy<?, ?>> factory) {
            static Named<StaticFactoryTestCase> of(String name,
                    BiFunction<SolverConfiguration, SolverState, BlockingQueueStrategy<?, ?>> factory) {
                return Named.of(name, new StaticFactoryTestCase(factory));
            }

            BlockingQueueStrategy<?, ?> apply(SolverConfiguration config, SolverState state) {
                return factory.apply(config, state);
            }
        }

        private static Stream<Named<StaticFactoryTestCase>> provideStaticFactoryTestCases() {
            return Stream.of(
                    StaticFactoryTestCase.of("singleSingle", BlockingQueueStrategy::singleSingle),
                    StaticFactoryTestCase.of("singleMulti", BlockingQueueStrategy::singleMulti),
                    StaticFactoryTestCase.of("multiSingle", BlockingQueueStrategy::multiSingle),
                    StaticFactoryTestCase.of("multiMulti", BlockingQueueStrategy::multiMulti));
        }

        @ParameterizedTest
        @MethodSource("provideStaticFactoryTestCases")
        void givenNullConfig_thenThrowNullPointerException(StaticFactoryTestCase testCase) {
            final var config = createValidConfig();
            final var state = createValidState(config);

            assertThatNullPointerException().isThrownBy(() -> testCase.apply(null, state));
        }

        @ParameterizedTest
        @MethodSource("provideStaticFactoryTestCases")
        void givenNullSolverState_thenThrowNullPointerException(StaticFactoryTestCase testCase) {
            final var config = createValidConfig();

            assertThatNullPointerException().isThrownBy(() -> testCase.apply(config, null));
        }

        @ParameterizedTest
        @MethodSource("provideStaticFactoryTestCases")
        void givenValidConfigAndState_thenCreateStrategy(StaticFactoryTestCase testCase) {
            final var config = createValidConfig();
            final var state = createValidState(config);

            final var strategy = testCase.apply(config, state);
            assertThat(strategy).isNotNull();
        }
    }

    @Nested
    class BuilderTests {

        private static BlockingQueueStrategy.Builder<ArrayBlockingQueue<WorkBatch>, ArrayBlockingQueue<WorkBatch>> createValidBuilder(
                int numGtmQueues, int numMtgQueues, int numThreads) {
            final var config = createValidConfig(numThreads);
            final var state = createValidState(config);
            final var gtmQueues = createValidQueueList(numGtmQueues);
            final var mtgQueues = createValidQueueList(numMtgQueues);

            return BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state);
        }

        private static BlockingQueueStrategy.Builder<ArrayBlockingQueue<WorkBatch>, ArrayBlockingQueue<WorkBatch>> createValidBuilder(
                int numGtmQueues, int numMtgQueues) {
            return createValidBuilder(numGtmQueues, numMtgQueues, DEFAULT_NUM_THREADS);
        }

        @Nested
        class BuilderMethodTests {
            private static List<ArrayBlockingQueue<WorkBatch>> createEmptyQueueList() {
                return List.of();
            }

            private static List<ArrayBlockingQueue<WorkBatch>> createQueueListWithNull() {
                return Arrays.asList(new ArrayBlockingQueue<WorkBatch>(16), null);
            }

            private static List<ArrayBlockingQueue<WorkBatch>> createValidQueueList() {
                return BlockingQueueStrategyTest.createValidQueueList(2);
            }

            @Test
            void givenNullGtmQueues_thenThrowNullPointerException() {
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState(config);

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(null, mtgQueues, config, state));
            }

            @Test
            void givenEmptyGtmQueues_thenThrowIllegalArgumentException() {
                final var gtmQueues = createEmptyQueueList();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState(config);

                assertThatIllegalArgumentException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenGtmQueuesContainingNull_thenThrowNullPointerException() {
                final var gtmQueues = createQueueListWithNull();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState(config);

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenNullMtgQueues_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState(config);

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, null, config, state));
            }

            @Test
            void givenEmptyMtgQueues_thenThrowIllegalArgumentException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createEmptyQueueList();
                final var config = createValidConfig();
                final var state = createValidState(config);

                assertThatIllegalArgumentException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenMtgQueuesContainingNull_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createQueueListWithNull();
                final var config = createValidConfig();
                final var state = createValidState(config);

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenNullConfig_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState(config);

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, null, state));
            }

            @Test
            void givenNullSolverState_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, null));
            }

            @Test
            void givenValidArguments_thenCreateBuilder() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState(config);

                final var builder = BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config,
                        state);
                assertThat(builder).isNotNull();
            }
        }

        @Nested
        class TopologyConfigurationTests {

            @Test
            void givenMultipleGtmQueues_whenAsSingleSingle_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 1);

                assertThatIllegalStateException().isThrownBy(builder::asSingleSingle)
                        .withMessageContaining("gtmQueues must contain exactly 1 queue")
                        .withMessageContaining("single-single configuration");
            }

            @Test
            void givenMultipleMtgQueues_whenAsSingleSingle_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(1, 2);

                assertThatIllegalStateException().isThrownBy(builder::asSingleSingle)
                        .withMessageContaining("mtgQueues must contain exactly 1 queue")
                        .withMessageContaining("single-single configuration");
            }

            @Test
            void givenSingleGtmQueueAndSingleMtgQueue_whenAsSingleSingle_thenSucceed() {
                final var builder = createValidBuilder(1, 1);

                assertThatNoException().isThrownBy(builder::asSingleSingle);
            }

            @Test
            void givenMultipleGtmQueues_whenAsSingleMulti_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 1);

                assertThatIllegalStateException().isThrownBy(builder::asSingleMulti)
                        .withMessageContaining("gtmQueues must contain exactly 1 queue")
                        .withMessageContaining("single-multi configuration");
            }

            @Test
            void givenSingleMtgQueue_whenAsSingleMulti_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(1, 1);

                assertThatIllegalStateException().isThrownBy(builder::asSingleMulti)
                        .withMessageContaining("mtgQueues must contain more than 1 queue")
                        .withMessageContaining("single-multi configuration");
            }

            @Test
            void givenSingleGtmQueueAndMultipleMtgQueues_whenAsSingleMulti_thenSucceed() {
                final var builder = createValidBuilder(1, 2);

                assertThatNoException().isThrownBy(builder::asSingleMulti);
            }

            @Test
            void givenSingleGtmQueue_whenAsMultiSingle_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(1, 2);

                assertThatIllegalStateException().isThrownBy(builder::asMultiSingle)
                        .withMessageContaining("gtmQueues must contain more than 1 queue")
                        .withMessageContaining("multi-single configuration");
            }

            @Test
            void givenMultipleMtgQueues_whenAsMultiSingle_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2);

                assertThatIllegalStateException().isThrownBy(builder::asMultiSingle)
                        .withMessageContaining("mtgQueues must contain exactly 1 queue")
                        .withMessageContaining("multi-single configuration");
            }

            @Test
            void givenMultipleGtmQueuesAndSingleMtgQueue_whenAsMultiSingle_thenSucceed() {
                final var builder = createValidBuilder(2, 1);

                assertThatNoException().isThrownBy(builder::asMultiSingle);
            }

            @Test
            void givenSingleGtmQueue_whenAsMultiMulti_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(1, 2);

                assertThatIllegalStateException().isThrownBy(builder::asMultiMulti)
                        .withMessageContaining("gtmQueues must contain more than 1 queue")
                        .withMessageContaining("multi-multi configuration");
            }

            @Test
            void givenSingleMtgQueue_whenAsMultiMulti_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 1);

                assertThatIllegalStateException().isThrownBy(builder::asMultiMulti)
                        .withMessageContaining("mtgQueues must contain more than 1 queue")
                        .withMessageContaining("multi-multi configuration");
            }

            @Test
            void givenMultipleGtmQueuesAndMultipleMtgQueues_whenAsMultiMulti_thenSucceed() {
                final var builder = createValidBuilder(2, 2);

                assertThatNoException().isThrownBy(builder::asMultiMulti);
            }
        }

        @Nested
        class BuildMethodTests {
            @Test
            void givenUnsetGeneratorPollSelector_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2)
                        .generatorOfferSelector(preferredBlocking())
                        .monkeyPollSelector(preferredBlocking())
                        .monkeyOfferSelector(preferredBlocking());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("generatorPollSelector must be set");
            }

            @Test
            void givenUnsetGeneratorOfferSelector_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredBlocking())
                        .monkeyPollSelector(preferredBlocking())
                        .monkeyOfferSelector(preferredBlocking());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("generatorOfferSelector must be set");
            }

            @Test
            void givenUnsetMonkeyPollSelector_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredBlocking())
                        .generatorOfferSelector(preferredBlocking())
                        .monkeyOfferSelector(preferredBlocking());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("monkeyPollSelector must be set");
            }

            @Test
            void givenUnsetMonkeyOfferSelector_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredBlocking())
                        .generatorOfferSelector(preferredBlocking())
                        .monkeyPollSelector(preferredBlocking());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("monkeyOfferSelector must be set");
            }

            @Test
            void givenAllRequiredSelectorsSet_thenBuildStrategy() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredBlocking())
                        .generatorOfferSelector(preferredBlocking())
                        .monkeyPollSelector(preferredBlocking())
                        .monkeyOfferSelector(preferredBlocking());

                final var strategy = builder.build();
                assertThat(strategy).isNotNull();
            }

            @Test
            void givenTopologySelected_thenBuildStrategy() {
                final var builder = createValidBuilder(2, 2).asMultiMulti();

                final var strategy = builder.build();
                assertThat(strategy).isNotNull();
            }
        }

        @Nested
        class PreallocateQueuesTests {
            @Test
            void givenNegativeBatchesPerQueue_thenThrowIllegalArgumentException() {
                final var builder = createValidBuilder(2, 2);

                assertThatIllegalArgumentException().isThrownBy(() -> builder.preallocateQueues(-1))
                        .withMessageContaining("batchesPerQueue must be positive");
            }

            @Test
            void givenZeroBatchesPerQueue_thenThrowIllegalArgumentException() {
                final var builder = createValidBuilder(2, 2);

                assertThatIllegalArgumentException().isThrownBy(() -> builder.preallocateQueues(0))
                        .withMessageContaining("batchesPerQueue must be positive");
            }

            @Test
            void givenPositiveBatchesPerQueue_thenSetBatchesPerQueue() {
                final var builder = createValidBuilder(2, 2);

                assertThatNoException().isThrownBy(() -> builder.preallocateQueues(5));
            }
        }

        @Nested
        class ValidationWiringTests {
            // Only one test per validator class, since the individual validator tests can cover the
            // edge cases without combinatorial expansions.

            // QueueListValidator wiring:
            @Test
            void givenDuplicateGtmQueues_thenBubbleUpIllegalStateException() {
                final var queue = new ArrayBlockingQueue<WorkBatch>(16);
                final var gtmQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16), queue, queue);
                final var mtgQueues = createValidQueueList(2);
                final var config = createValidConfig();
                final var state = createValidState(config);

                final var builder = BlockingQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiMulti();

                assertThatIllegalStateException().isThrownBy(builder::build).withMessageContaining(
                        "gtmQueue at index 1 is the same as gtmQueue at index 2");
            }

            // MetadataValidator wiring:
            @Test
            void givenGtmQueuesWithInconsistentBoundedness_thenBubbleUpIllegalStateException() {
                final ArrayBlockingQueue<WorkBatch> boundedQueue = new ArrayBlockingQueue<>(16);
                final LinkedBlockingQueue<WorkBatch> unboundedQueue = new LinkedBlockingQueue<>();
                final List<BlockingQueue<WorkBatch>> gtmQueues = List.of(boundedQueue,
                        unboundedQueue);
                final var mtgQueues = createValidQueueList(2);
                final var config = createValidConfig();
                final var state = createValidState(config);

                final var builder = BlockingQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiMulti();

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("gtmQueue at index 1 has different boundedness");
            }

            // Selector validation wiring:
            @Test
            void givenExclusiveSelectorWithMultipleThreadsAndSpscQueue_thenBubbleUpIllegalStateException() {
                final List<ArrayBlockingQueue<WorkBatch>> gtmQueues = createValidQueueList(2);
                final List<PushPullBlockingQueue<WorkBatch>> mtgQueues = List
                        .of(new PushPullBlockingQueue<>(16));
                final var config = createValidConfig(4);
                final var state = createValidState(config);

                final var builder = BlockingQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiSingle();

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("mtgQueue is single-producer");
            }

            // QueuePreallocator wiring:
            @Test
            void givenNonEmptyMtgQueueAndPreallocation_thenBubbleUpIllegalStateException() {
                final List<ArrayBlockingQueue<WorkBatch>> gtmQueues = createValidQueueList(2);
                final List<ArrayBlockingQueue<WorkBatch>> mtgQueues = createValidQueueList(2);
                final var config = createValidConfig();
                final var state = createValidState(config);

                // Add a batch to one of the mtgQueues to make it non-empty:
                mtgQueues.getLast().add(new WorkBatch(config));

                final var builder = BlockingQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiMulti()
                        .preallocateQueues(5);

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("mtgQueue at index 1 is not empty");
            }
        }
    }

    @Nested
    class EndToEndWiringTests {

        BlockingQueueStrategy<?, ?> strategy;
        WorkBatch batch;

        @BeforeEach
        void setup() {
            final SolverConfiguration config = createValidConfig();
            final SolverState state = createValidState(config);

            // Create a strategy with the builder and without preallocation so the generatorPoll
            // test doesn't hang on strategy.monkeyOffer()
            strategy = BlockingQueueStrategy
                    .builder(createValidQueueList(1), createValidQueueList(1), config, state)
                    .asSingleSingle().build();
            batch = new WorkBatch(config);
        }

        @Test
        void givenBatchOfferedToSingleSingleStrategyByGenerator_whenMonkeyPoll_thenReturnBatch() {
            strategy.generatorOffer(batch, 0);

            WorkBatch result = strategy.monkeyPoll(0);

            assertThat(result).isSameAs(batch);
        }

        @Test
        void givenBatchOfferedToSingleSingleStrategyByMonkey_whenGeneratorPoll_thenReturnBatch() {
            strategy.monkeyOffer(batch, 0);

            WorkBatch result = strategy.generatorPoll(0);

            assertThat(result).isSameAs(batch);
        }
    }
}

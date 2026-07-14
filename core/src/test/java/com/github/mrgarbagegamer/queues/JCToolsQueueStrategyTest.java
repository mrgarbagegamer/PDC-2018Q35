package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.QueueSelectors.preferredJCTools;
import static com.github.mrgarbagegamer.queues.QueueUtils.newBoundedImmutableQueueList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueStrategies.JCToolsQueueStrategy;

class JCToolsQueueStrategyTest {
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

    private static SolverState createValidState() { return new SolverState(); }

    private static List<MpmcArrayQueue<WorkBatch>> createValidQueueList(int numQueues,
            int queueCapacity) {
        return newBoundedImmutableQueueList(numQueues, queueCapacity, MpmcArrayQueue::new);
    }

    private static List<MpmcArrayQueue<WorkBatch>> createValidQueueList(int numQueues) {
        return createValidQueueList(numQueues, DEFAULT_QUEUE_CAPACITY);
    }

    @Nested
    class StaticFactoryTests {
        private record StaticFactoryTestCase(
                BiFunction<SolverConfiguration, SolverState, JCToolsQueueStrategy<?, ?>> factory) {
            static Named<StaticFactoryTestCase> of(String name,
                    BiFunction<SolverConfiguration, SolverState, JCToolsQueueStrategy<?, ?>> factory) {
                return Named.of(name, new StaticFactoryTestCase(factory));
            }

            JCToolsQueueStrategy<?, ?> apply(SolverConfiguration config, SolverState state) {
                return factory.apply(config, state);
            }
        }

        private static Stream<Named<StaticFactoryTestCase>> provideStaticFactoryTestCases() {
            return Stream.of(
                    StaticFactoryTestCase.of("singleSingle", JCToolsQueueStrategy::singleSingle),
                    StaticFactoryTestCase.of("singleMulti", JCToolsQueueStrategy::singleMulti),
                    StaticFactoryTestCase.of("multiSingle", JCToolsQueueStrategy::multiSingle),
                    StaticFactoryTestCase.of("multiMulti", JCToolsQueueStrategy::multiMulti));
        }

        @ParameterizedTest
        @MethodSource("provideStaticFactoryTestCases")
        void givenNullConfig_thenThrowNullPointerException(StaticFactoryTestCase testCase) {
            final var state = createValidState();

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
            final var state = createValidState();

            final var strategy = testCase.apply(config, state);
            assertThat(strategy).isNotNull();
        }
    }

    @Nested
    class BuilderTests {

        private static JCToolsQueueStrategy.Builder<MpmcArrayQueue<WorkBatch>, MpmcArrayQueue<WorkBatch>> createValidBuilder(
                int numGtmQueues, int numMtgQueues, int numThreads) {
            final SolverConfiguration config = createValidConfig(numThreads);
            final SolverState state = createValidState();
            final var gtmQueues = createValidQueueList(numGtmQueues);
            final var mtgQueues = createValidQueueList(numMtgQueues);

            return JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, config, state);
        }

        private static JCToolsQueueStrategy.Builder<MpmcArrayQueue<WorkBatch>, MpmcArrayQueue<WorkBatch>> createValidBuilder(
                int numGtmQueues, int numMtgQueues) {
            return createValidBuilder(numGtmQueues, numMtgQueues, DEFAULT_NUM_THREADS);
        }

        @Nested
        class BuilderMethodTests {
            private static List<MpmcArrayQueue<WorkBatch>> createEmptyQueueList() {
                return List.of();
            }

            private static List<MpmcArrayQueue<WorkBatch>> createQueueListWithNull() {
                return Arrays.asList(new MpmcArrayQueue<>(DEFAULT_QUEUE_CAPACITY), null);
            }

            private static List<MpmcArrayQueue<WorkBatch>> createValidQueueList() {
                return JCToolsQueueStrategyTest.createValidQueueList(2);
            }

            @Test
            void givenNullGtmQueues_thenThrowNullPointerException() {
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState();

                assertThatNullPointerException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(null, mtgQueues, config, state));
            }

            @Test
            void givenEmptyGtmQueues_thenThrowIllegalArgumentException() {
                final var gtmQueues = createEmptyQueueList();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState();

                assertThatIllegalArgumentException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenGtmQueuesContainingNull_thenThrowNullPointerException() {
                final var gtmQueues = createQueueListWithNull();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState();

                assertThatNullPointerException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenNullMtgQueues_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState();

                assertThatNullPointerException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(gtmQueues, null, config, state));
            }

            @Test
            void givenEmptyMtgQueues_thenThrowIllegalArgumentException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createEmptyQueueList();
                final var config = createValidConfig();
                final var state = createValidState();

                assertThatIllegalArgumentException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenMtgQueuesContainingNull_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createQueueListWithNull();
                final var config = createValidConfig();
                final var state = createValidState();

                assertThatNullPointerException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenNullConfig_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createValidQueueList();
                final var state = createValidState();

                assertThatNullPointerException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, null, state));
            }

            @Test
            void givenNullSolverState_thenThrowNullPointerException() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();

                assertThatNullPointerException().isThrownBy(
                        () -> JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, config, null));
            }

            @Test
            void givenValidArguments_thenCreateBuilder() {
                final var gtmQueues = createValidQueueList();
                final var mtgQueues = createValidQueueList();
                final var config = createValidConfig();
                final var state = createValidState();

                final var builder = JCToolsQueueStrategy.builder(gtmQueues, mtgQueues, config,
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
                        .generatorOfferSelector(preferredJCTools())
                        .monkeyPollSelector(preferredJCTools())
                        .monkeyOfferSelector(preferredJCTools());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("generatorPollSelector must be set");
            }

            @Test
            void givenUnsetGeneratorOfferSelector_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredJCTools())
                        .monkeyPollSelector(preferredJCTools())
                        .monkeyOfferSelector(preferredJCTools());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("generatorOfferSelector must be set");
            }

            @Test
            void givenUnsetMonkeyPollSelector_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredJCTools())
                        .generatorOfferSelector(preferredJCTools())
                        .monkeyOfferSelector(preferredJCTools());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("monkeyPollSelector must be set");
            }

            @Test
            void givenUnsetMonkeyOfferSelector_thenThrowIllegalStateException() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredJCTools())
                        .generatorOfferSelector(preferredJCTools())
                        .monkeyPollSelector(preferredJCTools());

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("monkeyOfferSelector must be set");
            }

            @Test
            void givenAllRequiredSelectorsSet_thenBuildStrategy() {
                final var builder = createValidBuilder(2, 2)
                        .generatorPollSelector(preferredJCTools())
                        .generatorOfferSelector(preferredJCTools())
                        .monkeyPollSelector(preferredJCTools())
                        .monkeyOfferSelector(preferredJCTools());

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
        class ValidationWiringTests {
            // Only one test per validator class, since the individual validator tests can cover the
            // edge cases without combinatorial expansions.

            // QueueListValidator wiring:
            @Test
            void givenDuplicateGtmQueues_thenBubbleUpIllegalStateException() {
                final var queue = new MpmcArrayQueue<WorkBatch>(DEFAULT_QUEUE_CAPACITY);
                final var gtmQueues = List.of(new MpmcArrayQueue<WorkBatch>(DEFAULT_QUEUE_CAPACITY),
                        queue, queue);
                final var mtgQueues = createValidQueueList(2);
                final var config = createValidConfig();
                final var state = createValidState();

                final var builder = JCToolsQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiMulti();

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining(
                                "gtmQueue at index 1 is the same as gtmQueue at index 2");
            }

            // MetadataValidator wiring:
            @Test
            void givenGtmQueuesWithInconsistentAccessMode_thenBubbleUpIllegalStateException() {
                final MpmcArrayQueue<WorkBatch> boundedQueue = new MpmcArrayQueue<>(
                        DEFAULT_QUEUE_CAPACITY);
                final SpscArrayQueue<WorkBatch> unboundedQueue = new SpscArrayQueue<>(
                        DEFAULT_QUEUE_CAPACITY);
                final List<MessagePassingQueue<WorkBatch>> gtmQueues = List.of(boundedQueue,
                        unboundedQueue);
                final var mtgQueues = createValidQueueList(2);
                final var config = createValidConfig();
                final var state = createValidState();

                final var builder = JCToolsQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiMulti();

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("gtmQueue at index 1 has different access mode");
            }

            // Selector validation wiring:
            @Test
            void givenExclusiveSelectorWithMultipleThreadsAndSpscQueue_thenBubbleUpIllegalStateException() {
                final List<MpmcArrayQueue<WorkBatch>> gtmQueues = createValidQueueList(2);
                final List<SpscArrayQueue<WorkBatch>> mtgQueues = List
                        .of(new SpscArrayQueue<>(DEFAULT_QUEUE_CAPACITY));
                final var config = createValidConfig();
                final var state = createValidState();

                final var builder = JCToolsQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiSingle();

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("mtgQueue is single-producer");
            }

            // QueuePreallocator wiring:
            @Test
            void givenNonEmptyMtgQueueAndPreallocation_thenBubbleUpIllegalStateException() {
                final List<MpmcArrayQueue<WorkBatch>> gtmQueues = createValidQueueList(2);
                final List<MpmcArrayQueue<WorkBatch>> mtgQueues = createValidQueueList(2);
                final var config = createValidConfig();
                final var state = createValidState();

                // Add a batch to one of the mtgQueues to make it non-empty:
                mtgQueues.getLast().add(new WorkBatch(config));

                final var builder = JCToolsQueueStrategy
                        .builder(gtmQueues, mtgQueues, config, state).asMultiMulti()
                        .preallocateQueues(5);

                assertThatIllegalStateException().isThrownBy(builder::build)
                        .withMessageContaining("mtgQueue at index 1 is not empty");
            }
        }

        @Nested
        class EndToEndWiringTests {
            JCToolsQueueStrategy<?, ?> strategy;
            WorkBatch batch;

            @BeforeEach
            void setup() {
                final SolverConfiguration config = createValidConfig();
                final SolverState state = createValidState();

                // Create a strategy with the builder and without preallocation so the generatorPoll
                // test doesn't hang on strategy.monkeyOffer()
                strategy = JCToolsQueueStrategy
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
}

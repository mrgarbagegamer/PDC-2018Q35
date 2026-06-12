package com.github.mrgarbagegamer.queues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;

@ExtendWith(MockitoExtension.class)
class BlockingQueueStrategyTest {
    private static SolverConfiguration createValidConfig(int numThreads, int queueSize) {
        return SolverConfiguration.builder().numThreads(numThreads).queueSize(queueSize).build();
    }

    private static SolverConfiguration createValidConfig(int numThreads) {
        return createValidConfig(numThreads, 16);
    }

    private static SolverConfiguration createValidConfig() { return createValidConfig(4); }

    private static SolverState createValidState() { return new SolverState(); }

    @Nested
    class StaticFactoryTests {
        private record StaticFactoryTestCase(
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
        @Nested
        class BuilderMethodTests {

            @Test
            void givenNullGtmQueues_thenThrowNullPointerException() {
                final var config = createValidConfig();
                final var state = createValidState();
                final var mtgQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(null, mtgQueues, config, state));
            }

            @Test
            void givenEmptyGtmQueues_thenThrowIllegalArgumentException() {
                final var config = createValidConfig();
                final var state = createValidState();
                final var gtmQueues = List.<ArrayBlockingQueue<WorkBatch>>of();
                final var mtgQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));

                assertThatIllegalArgumentException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenGtmQueuesContainingNull_thenThrowNullPointerException() {
                final var config = createValidConfig();
                final var state = createValidState();
                final var gtmQueues = Arrays.asList(new ArrayBlockingQueue<WorkBatch>(16), null);
                final var mtgQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenNullMtgQueues_thenThrowNullPointerException() {
                final var config = createValidConfig();
                final var state = createValidState();
                final var gtmQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, null, config, state));
            }

            @Test
            void givenEmptyMtgQueues_thenThrowIllegalArgumentException() {
                final var config = createValidConfig();
                final var state = createValidState();
                final var gtmQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));
                final var mtgQueues = List.<ArrayBlockingQueue<WorkBatch>>of();

                assertThatIllegalArgumentException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenMtgQueuesContainingNull_thenThrowNullPointerException() {
                final var config = createValidConfig();
                final var state = createValidState();
                final var gtmQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));
                final var mtgQueues = Arrays.asList(new ArrayBlockingQueue<WorkBatch>(16), null);

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state));
            }

            @Test
            void givenNullConfig_thenThrowNullPointerException() {
                final var state = createValidState();
                final var gtmQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));
                final var mtgQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, null, state));
            }

            @Test
            void givenNullSolverState_thenThrowNullPointerException() {
                final var config = createValidConfig();
                final var gtmQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));
                final var mtgQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));

                assertThatNullPointerException().isThrownBy(
                        () -> BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, null));
            }

            @Test
            void givenValidArguments_thenCreateBuilder() {
                final var config = createValidConfig();
                final var state = createValidState();
                final var gtmQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));
                final var mtgQueues = List.of(new ArrayBlockingQueue<WorkBatch>(16));

                final var builder = BlockingQueueStrategy.builder(gtmQueues, mtgQueues, config, state);
                assertThat(builder).isNotNull();
            }
        }
    }
}

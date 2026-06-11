package com.github.mrgarbagegamer.queues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;

@ExtendWith(MockitoExtension.class)
class BlockingQueueStrategyTest {
    @Nested
    class StaticFactoryTests {

        private static SolverConfiguration createValidConfig() {
            return SolverConfiguration.builder().numThreads(4).queueSize(16).build();
        }

        private static SolverState createValidState() { return new SolverState(); }

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

    // TODO: Write tests for the builder of BlockingQueueStrategy
}

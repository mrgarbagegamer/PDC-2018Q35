package com.github.mrgarbagegamer.queues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.SolverStateBridge;
import com.github.mrgarbagegamer.WorkBatch;

@ExtendWith(MockitoExtension.class)
public class ContinuationPredicatesTest {
    private static SolverState createState(boolean solutionFound, boolean generationComplete) {
        SolverState state = SolverStateBridge.createDefaultInstance();
        if (solutionFound) {
            state.markSolutionFound(new short[2]);
        }
        if (generationComplete) {
            state.markGenerationComplete();
        }
        return state;
    }

    @Nested
    class NeverTerminateTests {
        @Test
        void givenNeverTerminate_thenReturnTrue() {
            BooleanSupplier neverTerminate = ContinuationPredicates.neverTerminate();

            assertThat(neverTerminate.getAsBoolean()).isTrue();
        }
    }

    @Nested
    class ForGeneratorTests {
        @Test
        void givenNullState_thenThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ContinuationPredicates.forGenerator(null));
        }

        @Test
        void givenSolutionNotFound_thenReturnTrue() {
            SolverState state = createState(false, false);
            BooleanSupplier predicate = ContinuationPredicates.forGenerator(state);

            assertThat(predicate.getAsBoolean()).isTrue();
        }

        @Test
        void givenSolutionFound_thenReturnFalse() {
            SolverState state = createState(true, false);
            BooleanSupplier predicate = ContinuationPredicates.forGenerator(state);

            assertThat(predicate.getAsBoolean()).isFalse();
        }
    }

    @Nested
    class ForMonkeyTests {
        record ReturnValueTestCase(List<?> queues) {
            static Named<ReturnValueTestCase> noSolutionGenNotComplete(String scenario,
                    List<?> queues) {
                return Named.of(scenario, new ReturnValueTestCase(queues));
            }

            static Named<ReturnValueTestCase> solutionFound(String scenario, List<?> queues) {
                return Named.of(scenario, new ReturnValueTestCase(queues));
            }

            static Named<ReturnValueTestCase> genCompleteOneNonEmpty(String scenario,
                    List<?> queues) {
                return Named.of(scenario, new ReturnValueTestCase(queues));
            }

            static Named<ReturnValueTestCase> genCompleteAllEmpty(String scenario, List<?> queues) {
                return Named.of(scenario, new ReturnValueTestCase(queues));
            }
        }

        private static Stream<Named<ReturnValueTestCase>> provideNoSolutionGenNotCompleteCases() {
            return Stream.of(
                    ReturnValueTestCase.noSolutionGenNotComplete("JCTools single-queue",
                            List.of(jctoolsQueue(false))),
                    ReturnValueTestCase.noSolutionGenNotComplete("Blocking single-queue",
                            List.of(blockingQueue(false))),
                    ReturnValueTestCase.noSolutionGenNotComplete("JCTools multi-queue",
                            List.of(jctoolsQueue(false), jctoolsQueue(false))),
                    ReturnValueTestCase.noSolutionGenNotComplete("Blocking multi-queue",
                            List.of(blockingQueue(false), blockingQueue(false))));
        }

        private static Stream<Named<ReturnValueTestCase>> provideSolutionFoundCases() {
            return Stream.of(
                    ReturnValueTestCase.solutionFound("JCTools single-queue",
                            List.of(jctoolsQueue(true))),
                    ReturnValueTestCase.solutionFound("Blocking single-queue",
                            List.of(blockingQueue(true))),
                    ReturnValueTestCase.solutionFound("JCTools multi-queue",
                            List.of(jctoolsQueue(true), jctoolsQueue(false))),
                    ReturnValueTestCase.solutionFound("Blocking multi-queue",
                            List.of(blockingQueue(true), blockingQueue(false))));
        }

        private static Stream<Named<ReturnValueTestCase>> provideGenCompleteOneNonEmptyCases() {
            return Stream.of(
                    ReturnValueTestCase.genCompleteOneNonEmpty("JCTools single-queue",
                            List.of(jctoolsQueue(false))),
                    ReturnValueTestCase.genCompleteOneNonEmpty("Blocking single-queue",
                            List.of(blockingQueue(false))),
                    ReturnValueTestCase.genCompleteOneNonEmpty("JCTools multi-queue",
                            List.of(jctoolsQueue(true), jctoolsQueue(false))),
                    ReturnValueTestCase.genCompleteOneNonEmpty("Blocking multi-queue",
                            List.of(blockingQueue(true), blockingQueue(false))));
        }

        private static Stream<Named<ReturnValueTestCase>> provideGenCompleteAllEmptyCases() {
            return Stream.of(
                    ReturnValueTestCase.genCompleteAllEmpty("JCTools single-queue",
                            List.of(jctoolsQueue(true))),
                    ReturnValueTestCase.genCompleteAllEmpty("Blocking single-queue",
                            List.of(blockingQueue(true))),
                    ReturnValueTestCase.genCompleteAllEmpty("JCTools multi-queue",
                            List.of(jctoolsQueue(true), jctoolsQueue(true))),
                    ReturnValueTestCase.genCompleteAllEmpty("Blocking multi-queue",
                            List.of(blockingQueue(true), blockingQueue(true))));
        }

        private static MessagePassingQueue<WorkBatch> jctoolsQueue(boolean empty) {
            MessagePassingQueue<WorkBatch> queue = mock();
            lenient().when(queue.isEmpty()).thenReturn(empty);
            return queue;
        }

        private static BlockingQueue<WorkBatch> blockingQueue(boolean empty) {
            BlockingQueue<WorkBatch> queue = mock();
            lenient().when(queue.isEmpty()).thenReturn(empty);
            return queue;
        }

        @Test
        void givenNullState_thenThrowNullPointerException() {
            assertThatNullPointerException().isThrownBy(
                    () -> ContinuationPredicates.forMonkey(null, List.of(jctoolsQueue(false))));
        }

        @Test
        void givenNullListOfQueues_thenThrowNullPointerException() {
            assertThatNullPointerException().isThrownBy(
                    () -> ContinuationPredicates.forMonkey(createState(false, false), null));
        }

        @Test
        void givenListOfQueuesContainingNull_thenThrowNullPointerException() {
            final List<MessagePassingQueue<WorkBatch>> queues = Arrays.asList(jctoolsQueue(false),
                    null);

            assertThatNullPointerException().isThrownBy(
                    () -> ContinuationPredicates.forMonkey(createState(false, false), queues));
        }

        @Test
        void givenEmptyListOfQueues_thenThrowIllegalArgumentException() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> ContinuationPredicates.forMonkey(createState(false, false), List.of()));
        }

        @Test
        void givenListOfQueuesWithMixedTypes_thenThrowIllegalArgumentException() {
            final List<Object> queues = List.of(jctoolsQueue(false), blockingQueue(false));

            assertThatIllegalArgumentException().isThrownBy(
                    () -> ContinuationPredicates.forMonkey(createState(false, false), queues));
        }

        @ParameterizedTest
        @MethodSource("provideNoSolutionGenNotCompleteCases")
        void givenNoSolutionAndGenerationNotComplete_thenReturnTrue(ReturnValueTestCase testCase) {
            SolverState state = createState(false, false);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(state, testCase.queues());

            assertThat(predicate.getAsBoolean()).isTrue();
        }

        @ParameterizedTest
        @MethodSource("provideSolutionFoundCases")
        void givenSolutionFound_thenReturnFalse(ReturnValueTestCase testCase) {
            SolverState state = createState(true, false);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(state, testCase.queues());

            assertThat(predicate.getAsBoolean()).isFalse();
        }

        @ParameterizedTest
        @MethodSource("provideSolutionFoundCases")
        void givenSolutionFound_thenDoNotCheckGenerationComplete(ReturnValueTestCase testCase) {
            // Mocked so we can verify that generationComplete() isn't called
            SolverState mockState = mock();
            when(mockState.solutionFound()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            boolean _ = predicate.getAsBoolean();

            verify(mockState, never()).generationComplete();
        }

        @ParameterizedTest
        @MethodSource("provideSolutionFoundCases")
        void givenSolutionFound_thenDoNotCheckQueues(ReturnValueTestCase testCase) {
            // Mocked so we can verify that the queues are not checked
            SolverState mockState = mock();
            when(mockState.solutionFound()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            boolean _ = predicate.getAsBoolean();

            verifyNoInteractions(testCase.queues().toArray());
        }

        @ParameterizedTest
        @MethodSource("provideGenCompleteOneNonEmptyCases")
        void givenGenerationCompleteWithAtLeastOneNonEmptyQueue_thenReturnTrue(
                ReturnValueTestCase testCase) {
            SolverState state = createState(false, true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(state, testCase.queues());

            assertThat(predicate.getAsBoolean()).isTrue();
        }

        @ParameterizedTest
        @MethodSource("provideGenCompleteAllEmptyCases")
        void givenGenerationCompleteWithAllEmptyQueues_thenReturnFalse(
                ReturnValueTestCase testCase) {
            SolverState state = createState(false, true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(state, testCase.queues());

            assertThat(predicate.getAsBoolean()).isFalse();
        }
    }
}

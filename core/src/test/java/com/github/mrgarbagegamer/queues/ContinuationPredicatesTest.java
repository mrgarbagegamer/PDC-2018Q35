package com.github.mrgarbagegamer.queues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;

@ExtendWith(MockitoExtension.class)
public class ContinuationPredicatesTest {
    @Mock
    private SolverState mockState;

    @Nested
    class NeverTerminateTests {
        @Test
        public void givenNeverTerminate_thenReturnTrue() {
            BooleanSupplier neverTerminate = ContinuationPredicates.neverTerminate();

            assertThat(neverTerminate.getAsBoolean()).isTrue();
        }
    }

    @Nested
    class ForGeneratorTests {
        @Test
        public void givenNullState_thenThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ContinuationPredicates.forGenerator(null));
        }

        @Test
        public void givenSolutionNotFound_thenReturnTrue() {
            when(mockState.solutionFound()).thenReturn(false);

            BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);

            assertThat(predicate.getAsBoolean()).isTrue();
        }

        @Test
        public void givenSolutionFound_thenReturnFalse() {
            when(mockState.solutionFound()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);

            assertThat(predicate.getAsBoolean()).isFalse();
        }
    }

    @Nested
    class ForMonkeyTests {
        @Mock
        private MessagePassingQueue<WorkBatch> mockJCToolsQueue;

        @Mock
        private BlockingQueue<WorkBatch> mockBlockingQueue;

        private record ReturnValueTestCase(List<?> queues) {
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
            when(queue.isEmpty()).thenReturn(empty);
            return queue;
        }

        private static BlockingQueue<WorkBatch> blockingQueue(boolean empty) {
            BlockingQueue<WorkBatch> queue = mock();
            when(queue.isEmpty()).thenReturn(empty);
            return queue;
        }

        @Test
        public void givenNullState_thenThrowNullPointerException() {
            assertThatNullPointerException().isThrownBy(
                    () -> ContinuationPredicates.forMonkey(null, List.of(mockJCToolsQueue)));
        }

        @Test
        public void givenNullListOfQueues_thenThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ContinuationPredicates.forMonkey(mockState, null));
        }

        @Test
        public void givenListOfQueuesContainingNull_thenThrowNullPointerException() {
            final var queues = Arrays.asList(mockJCToolsQueue, null);

            assertThatNullPointerException()
                    .isThrownBy(() -> ContinuationPredicates.forMonkey(mockState, queues));
        }

        @Test
        public void givenEmptyListOfQueues_thenThrowIllegalArgumentException() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ContinuationPredicates.forMonkey(mockState, List.of()));
        }

        @Test
        public void givenListOfQueuesWithMixedTypes_thenThrowIllegalArgumentException() {
            final var queues = List.of(mockJCToolsQueue, mockBlockingQueue);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ContinuationPredicates.forMonkey(mockState, queues));
        }

        @ParameterizedTest
        @MethodSource("provideNoSolutionGenNotCompleteCases")
        public void givenNoSolutionAndGenerationNotComplete_thenReturnTrue(
                ReturnValueTestCase testCase) {
            when(mockState.solutionFound()).thenReturn(false);
            when(mockState.generationComplete()).thenReturn(false);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            assertThat(predicate.getAsBoolean()).isTrue();

            verify(mockState).solutionFound();
            verify(mockState).generationComplete();
        }

        @ParameterizedTest
        @MethodSource("provideSolutionFoundCases")
        public void givenSolutionFound_thenReturnFalse(ReturnValueTestCase testCase) {
            when(mockState.solutionFound()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            assertThat(predicate.getAsBoolean()).isFalse();

            verify(mockState).solutionFound();
        }

        @ParameterizedTest
        @MethodSource("provideSolutionFoundCases")
        public void givenSolutionFound_thenDoNotCheckGenerationComplete(
                ReturnValueTestCase testCase) {
            when(mockState.solutionFound()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            predicate.getAsBoolean();

            verify(mockState, never()).generationComplete();
        }

        @ParameterizedTest
        @MethodSource("provideSolutionFoundCases")
        public void givenSolutionFound_thenDoNotCheckQueues(ReturnValueTestCase testCase) {
            when(mockState.solutionFound()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            predicate.getAsBoolean();

            verifyNoInteractions(testCase.queues().toArray());
        }

        @ParameterizedTest
        @MethodSource("provideGenCompleteOneNonEmptyCases")
        public void givenGenerationCompleteWithAtLeastOneNonEmptyQueue_thenReturnTrue(
                ReturnValueTestCase testCase) {
            when(mockState.solutionFound()).thenReturn(false);
            when(mockState.generationComplete()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            assertThat(predicate.getAsBoolean()).isTrue();

            verify(mockState).solutionFound();
            verify(mockState).generationComplete();
        }

        @ParameterizedTest
        @MethodSource("provideGenCompleteAllEmptyCases")
        public void givenGenerationCompleteWithAllEmptyQueues_thenReturnFalse(
                ReturnValueTestCase testCase) {
            when(mockState.solutionFound()).thenReturn(false);
            when(mockState.generationComplete()).thenReturn(true);

            BooleanSupplier predicate = ContinuationPredicates.forMonkey(mockState,
                    testCase.queues());

            assertThat(predicate.getAsBoolean()).isFalse();

            verify(mockState).solutionFound();
            verify(mockState).generationComplete();
        }
    }
}

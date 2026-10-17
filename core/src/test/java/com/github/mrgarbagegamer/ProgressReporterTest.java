package com.github.mrgarbagegamer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgressReporterTest {

    @Mock
    private Logger logger;

    @Nested
    class TaskConstructionTests {

        @Test
        void givenNullTracker_whenConstructed_thenThrowsNPE() {
            SolverState state = SolverStateBridge.createDefaultInstance();
            assertThatNullPointerException().isThrownBy(
                    () -> new ProgressReporter.ProgressReporterTask(null, logger, state));
        }

        @Test
        void givenNullLogger_whenConstructed_thenThrowsNPE() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();
            assertThatNullPointerException().isThrownBy(
                    () -> new ProgressReporter.ProgressReporterTask(tracker, null, state));
        }

        @Test
        void givenNullSolverState_whenConstructed_thenThrowsNPE() {
            ProgressTracker tracker = new ProgressTracker();
            assertThatNullPointerException().isThrownBy(
                    () -> new ProgressReporter.ProgressReporterTask(tracker, logger, null));
        }

        @Test
        void givenValidInputs_whenConstructed_thenSucceeds() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();

            assertThatNoException().isThrownBy(
                    () -> new ProgressReporter.ProgressReporterTask(tracker, logger, state));
        }
    }

    @Nested
    class TaskExecutionTests {

        @Test
        void givenZeroForkedTasks_whenRun_thenDoesNotLog() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();
            var task = new ProgressReporter.ProgressReporterTask(tracker, logger, state);

            task.run();

            verifyNoInteractions(logger);
        }

        @Test
        void givenSolutionFound_whenRun_thenSuppressesLogging() {
            ProgressTracker tracker = new ProgressTracker();
            tracker.addForked(10L);

            SolverState state = SolverStateBridge.createDefaultInstance();
            state.markSolutionFound(new short[] {1, 2});

            var task = new ProgressReporter.ProgressReporterTask(tracker, logger, state);

            task.run();

            verifyNoInteractions(logger);
        }

        @Test
        void givenGenerationComplete_whenRun_thenSuppressesLogging() {
            ProgressTracker tracker = new ProgressTracker();
            tracker.addForked(10L);

            SolverState state = SolverStateBridge.createDefaultInstance();
            state.markGenerationComplete();

            var task = new ProgressReporter.ProgressReporterTask(tracker, logger, state);

            task.run();

            verifyNoInteractions(logger);
        }

        @Test
        void givenForkedTasksAndActiveSearch_whenRun_thenLogsProgress() {
            ProgressTracker tracker = new ProgressTracker();
            tracker.addForked(10L);
            tracker.addCompleted(5L);

            SolverState state = SolverStateBridge.createDefaultInstance();
            var task = new ProgressReporter.ProgressReporterTask(tracker, logger, state);

            task.run();

            // This may need to be updated if I change the logging level
            verify(logger).info(anyString(), eq(tracker.formatProgress()));
        }
    }

    @Nested
    class ReporterConstructionTests {
        @Test
        void givenNullTracker_whenStart_thenThrowsNPE() {
            ProgressTracker tracker = null;
            SolverState state = SolverStateBridge.createDefaultInstance();

            assertThatNullPointerException()
                    .isThrownBy(() -> ProgressReporter.start(tracker, logger, state));
        }

        @Test
        void givenNullLogger_whenStart_thenThrowsNPE() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();

            assertThatNullPointerException()
                    .isThrownBy(() -> ProgressReporter.start(tracker, null, state));
        }

        @Test
        void givenNullSolverState_whenStart_thenThrowsNPE() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = null;

            assertThatNullPointerException()
                    .isThrownBy(() -> ProgressReporter.start(tracker, logger, state));
        }

        @Test
        void givenValidInputs_whenStart_thenSucceeds() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();

            // Ensure that we don't hold a handle to the reporter for too long.
            try (ProgressReporter reporter = ProgressReporter.start(tracker, logger, state)) {
                assertThat(reporter).isNotNull();
            }
        }
    }

    @Nested
    class LifecycleTests {

        @Test
        void givenNewReporter_whenIsRunning_thenReturnsTrue() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();

            try (ProgressReporter reporter = ProgressReporter.start(tracker, logger, state)) {
                assertThat(reporter.isRunning()).isTrue();
            }
        }

        @Test
        void givenClosedReporter_whenIsRunning_thenReturnsFalse() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();
            ProgressReporter reporter = ProgressReporter.start(tracker, logger, state);

            reporter.close();

            assertThat(reporter.isRunning()).isFalse();
        }

        // Smoke test for toString():

        @Test
        void givenNonNullReporter_whenToString_thenDoesNotThrow() {
            ProgressTracker tracker = new ProgressTracker();
            SolverState state = SolverStateBridge.createDefaultInstance();

            try (ProgressReporter reporter = ProgressReporter.start(tracker, logger, state)) {
                // Avoid asserting anything about the string to avoid brittle tests:
                assertThat(reporter.toString()).isNotEmpty();
            }
        }
    }
}

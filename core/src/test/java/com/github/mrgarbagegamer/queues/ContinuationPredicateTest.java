package com.github.mrgarbagegamer.queues;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

import org.jctools.queues.MessagePassingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;

@ExtendWith(MockitoExtension.class)
public class ContinuationPredicateTest {

    @Mock
    private SolverState mockState;

    @Mock
    private MessagePassingQueue<WorkBatch> mockJCToolsQueue;

    @Mock
    private BlockingQueue<WorkBatch> mockBlockingQueue;

    // ===== Tests for neverTerminate() =====

    @Test
    public void givenNeverTerminate_whenGetAsBoolean_thenReturnTrue() {
        // Given
        BooleanSupplier neverTerminate = ContinuationPredicates.neverTerminate();

        // When & Then
        assertTrue(neverTerminate, "Expected neverTerminate to return true");
    }

    // ===== Tests for forGenerator(SolverState) =====

    @Test
    public void givenNullState_whenForGenerator_thenThrowNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forGenerator(null);
        }, "Expected forGenerator to throw NullPointerException when given null");
    }

    @Test
    public void givenSolutionNotFound_whenForGenerator_thenReturnTrue() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);

        // Then
        assertTrue(predicate, "Expected forGenerator to return true for initial state");
    }

    @Test
    public void givenSolutionFound_whenForGenerator_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);

        // Then
        assertFalse(predicate,
                "Expected forGenerator to return false when solution is already found");
    }

    @Test
    public void givenSolutionNotFoundThenFound_whenForGenerator_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(false).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);

        // Then
        assumeTrue(predicate, "Expected forGenerator to return true for initial state");
        assertFalse(predicate,
                "Expected forGenerator to return false after generation is marked complete");
    }

    // ===== Tests for forMonkeyJCTools(SolverState, MessagePassingQueue) - single queue =====

    @Test
    public void givenNullState_whenForMonkeyJCToolsSingleQueue_thenThrowNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(null, mockJCToolsQueue);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void givenNullQueue_whenForMonkeyJCToolsSingleQueue_thenThrowNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(mockState,
                    (MessagePassingQueue<WorkBatch>) null);
        }, "Expected NullPointerException when queue is null");
    }

    @Test
    public void givenNoSolutionAndGenerationNotComplete_whenForMonkeyJCToolsSingleQueue_thenReturnTrue() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void givenSolutionFound_whenForMonkeyJCToolsSingleQueue_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);

        // Then
        assertFalse(predicate, "Expected predicate to return false when solution is found");
    }

    @Test
    public void givenGenerationCompleteAndQueueNotEmpty_whenForMonkeyJCToolsSingleQueue_thenReturnTrue() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockJCToolsQueue.isEmpty()).thenReturn(false);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when generation complete but queue not empty");
    }

    @Test
    public void givenGenerationCompleteAndQueueEmpty_whenForMonkeyJCToolsSingleQueue_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockJCToolsQueue.isEmpty()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);

        // Then
        assertFalse(predicate,
                "Expected predicate to return false when generation complete and queue empty");
    }

    // ===== Tests for forMonkeyJCTools(SolverState, List) - multiple queues =====

    @Test
    public void givenNullState_whenForMonkeyJCToolsList_thenThrowNullPointerException() {
        // Given
        List<MessagePassingQueue<WorkBatch>> queues = List.of(mockJCToolsQueue);

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(null, queues);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void givenNullQueues_whenForMonkeyJCToolsList_thenThrowNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(mockState,
                    (List<? extends MessagePassingQueue<WorkBatch>>) null);
        }, "Expected NullPointerException when queues list is null");
    }

    @Test
    public void givenEmptyQueues_whenForMonkeyJCToolsList_thenThrowIllegalArgumentException() {
        // Given
        List<MessagePassingQueue<WorkBatch>> emptyList = List.of();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(mockState, emptyList);
        }, "Expected IllegalArgumentException when queues list is empty");
    }

    @Test
    public void givenNoSolutionAndGenerationNotComplete_whenForMonkeyJCToolsList_thenReturnTrue() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);
        List<MessagePassingQueue<WorkBatch>> queues = List.of(mockJCToolsQueue);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void givenSolutionFound_whenForMonkeyJCToolsList_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(true);
        List<MessagePassingQueue<WorkBatch>> queues = List.of(mockJCToolsQueue);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);

        // Then
        assertFalse(predicate, "Expected predicate to return false when solution is found");
    }

    @Test
    public void givenGenerationCompleteAndOneQueueNotEmpty_whenForMonkeyJCToolsList_thenReturnTrue() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);

        MessagePassingQueue<WorkBatch> queue1 = mock();
        MessagePassingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(false);

        List<MessagePassingQueue<WorkBatch>> queues = List.of(queue1, queue2);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when at least one queue is not empty");
    }

    @Test
    public void givenGenerationCompleteAndAllQueuesEmpty_whenForMonkeyJCToolsList_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);

        MessagePassingQueue<WorkBatch> queue1 = mock();
        MessagePassingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(true);

        List<MessagePassingQueue<WorkBatch>> queues = List.of(queue1, queue2);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);

        // Then
        assertFalse(predicate,
                "Expected predicate to return false when all queues are empty and generation complete");
    }

    // ===== Tests for forMonkeyBlocking(SolverState, BlockingQueue) - single queue =====

    @Test
    public void givenNullState_whenForMonkeyBlockingSingleQueue_thenThrowNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(null, mockBlockingQueue);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void givenNullQueue_whenForMonkeyBlockingSingleQueue_thenThrowNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(mockState, (BlockingQueue<WorkBatch>) null);
        }, "Expected NullPointerException when queue is null");
    }

    @Test
    public void givenNoSolutionAndGenerationNotComplete_whenForMonkeyBlockingSingleQueue_thenReturnTrue() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void givenSolutionFound_whenForMonkeyBlockingSingleQueue_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);

        // Then
        assertFalse(predicate, "Expected predicate to return false when solution is found");
    }

    @Test
    public void givenGenerationCompleteAndQueueNotEmpty_whenForMonkeyBlockingSingleQueue_thenReturnTrue() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockBlockingQueue.isEmpty()).thenReturn(false);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when generation complete but queue not empty");
    }

    @Test
    public void givenGenerationCompleteAndQueueEmpty_whenForMonkeyBlockingSingleQueue_thenReturnFalse() {
        // Given
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockBlockingQueue.isEmpty()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);

        // Then
        assertFalse(predicate,
                "Expected predicate to return false when generation complete and queue empty");
    }

    // ===== Tests for forMonkeyBlocking(SolverState, List) - multiple queues =====

    @Test
    public void givenNullState_whenForMonkeyBlockingList_thenThrowNullPointerException() {
        // Given
        List<BlockingQueue<WorkBatch>> queues = List.of(mockBlockingQueue);

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(null, queues);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void givenNullQueues_whenForMonkeyBlockingList_thenThrowNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(mockState,
                    (List<? extends BlockingQueue<WorkBatch>>) null);
        }, "Expected NullPointerException when queues list is null");
    }

    @Test
    public void givenEmptyQueues_whenForMonkeyBlockingList_thenThrowIllegalArgumentException() {
        // Given
        List<BlockingQueue<WorkBatch>> emptyList = List.of();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(mockState, emptyList);
        }, "Expected IllegalArgumentException when queues list is empty");
    }

    @Test
    public void givenNoSolutionAndGenerationNotComplete_whenForMonkeyBlockingList_thenReturnTrue() {
        // Given
        List<BlockingQueue<WorkBatch>> queues = List.of(mockBlockingQueue);
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void givenSolutionFound_whenForMonkeyBlockingList_thenReturnFalse() {
        // Given
        List<BlockingQueue<WorkBatch>> queues = List.of(mockBlockingQueue);
        when(mockState.solutionFound()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);

        // Then
        assertFalse(predicate, "Expected predicate to return false when solution is found");
    }

    @Test
    public void givenGenerationCompleteAndOneQueueNotEmpty_whenForMonkeyBlockingList_thenReturnTrue() {
        // Given
        BlockingQueue<WorkBatch> queue1 = mock();
        BlockingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(false);
        List<BlockingQueue<WorkBatch>> queues = List.of(queue1, queue2);
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);

        // Then
        assertTrue(predicate,
                "Expected predicate to return true when at least one queue is not empty");
    }

    @Test
    public void givenGenerationCompleteAndAllQueuesEmpty_whenForMonkeyBlockingList_thenReturnFalse() {
        // Given
        BlockingQueue<WorkBatch> queue1 = mock();
        BlockingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(true);
        List<BlockingQueue<WorkBatch>> queues = List.of(queue1, queue2);
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);

        // When
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);

        // Then
        assertFalse(predicate,
                "Expected predicate to return false when all queues are empty and generation complete");
    }
}

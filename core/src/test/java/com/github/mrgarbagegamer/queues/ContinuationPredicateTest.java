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
    public void testNeverTerminateTrue() {
        BooleanSupplier neverTerminate = ContinuationPredicates.neverTerminate();
        assertTrue(neverTerminate.getAsBoolean(), "Expected neverTerminate to return true");
    }

    // ===== Tests for forGenerator(SolverState) =====

    @Test
    public void testForGeneratorFailFast() {
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forGenerator(null);
        }, "Expected forGenerator to throw NullPointerException when given null");
    }

    @Test
    public void testForGeneratorInitiallyTrue() {
        when(mockState.solutionFound()).thenReturn(false);

        BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);
        assertTrue(predicate, "Expected forGenerator to return true for initial state");
    }

    @Test
    public void testForGeneratorInitiallyFalse() {
        when(mockState.solutionFound()).thenReturn(true);

        BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);
        assertFalse(predicate,
                "Expected forGenerator to return false when solution is already found");
    }

    @Test
    public void testForGeneratorEventuallyFalse() {
        when(mockState.solutionFound()).thenReturn(false).thenReturn(true);

        BooleanSupplier predicate = ContinuationPredicates.forGenerator(mockState);
        assumeTrue(predicate, "Expected forGenerator to return true for initial state");
        assertFalse(predicate,
                "Expected forGenerator to return false after generation is marked complete");
    }

    // ===== Tests for forMonkeyJCTools(SolverState, MessagePassingQueue) - single queue =====

    @Test
    public void testForMonkeyJCToolsSingleQueueNullState() {
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(null, mockJCToolsQueue);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void testForMonkeyJCToolsSingleQueueNullQueue() {
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(mockState,
                    (MessagePassingQueue<WorkBatch>) null);
        }, "Expected NullPointerException when queue is null");
    }

    @Test
    public void testForMonkeyJCToolsSingleQueueReturnsTrueWhenNoSolutionAndGenerationNotComplete() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);

        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void testForMonkeyJCToolsSingleQueueReturnsFalseWhenSolutionFound() {
        when(mockState.solutionFound()).thenReturn(true);

        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);
        assertFalse(predicate, "Expected predicate to return false when solution is found");
    }

    @Test
    public void testForMonkeyJCToolsSingleQueueReturnsTrueWhenGenerationCompleteButQueueNotEmpty() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockJCToolsQueue.isEmpty()).thenReturn(false);

        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);
        assertTrue(predicate,
                "Expected predicate to return true when generation complete but queue not empty");
    }

    @Test
    public void testForMonkeyJCToolsSingleQueueReturnsFalseWhenGenerationCompleteAndQueueEmpty() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockJCToolsQueue.isEmpty()).thenReturn(true);

        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState,
                mockJCToolsQueue);
        assertFalse(predicate,
                "Expected predicate to return false when generation complete and queue empty");
    }

    // ===== Tests for forMonkeyJCTools(SolverState, List) - multiple queues =====

    @Test
    public void testForMonkeyJCToolsListNullState() {
        List<MessagePassingQueue<WorkBatch>> queues = List.of(mockJCToolsQueue);
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(null, queues);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void testForMonkeyJCToolsListNullQueues() {
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(mockState,
                    (List<? extends MessagePassingQueue<WorkBatch>>) null);
        }, "Expected NullPointerException when queues list is null");
    }

    @Test
    public void testForMonkeyJCToolsListEmptyThrows() {
        List<MessagePassingQueue<WorkBatch>> emptyList = List.of();
        assertThrows(IllegalArgumentException.class, () -> {
            ContinuationPredicates.forMonkeyJCTools(mockState, emptyList);
        }, "Expected IllegalArgumentException when queues list is empty");
    }

    @Test
    public void testForMonkeyJCToolsListReturnsTrueWhenNoSolutionAndGenerationNotComplete() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);

        List<MessagePassingQueue<WorkBatch>> queues = List.of(mockJCToolsQueue);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void testForMonkeyJCToolsListReturnsFalseWhenSolutionFound() {
        when(mockState.solutionFound()).thenReturn(true);

        List<MessagePassingQueue<WorkBatch>> queues = List.of(mockJCToolsQueue);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);
        assertFalse(predicate, "Expected predicate to return false when solution is found");
    }

    @Test
    public void testForMonkeyJCToolsListReturnsTrueWhenGenerationCompleteAndOneQueueNotEmpty() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);

        MessagePassingQueue<WorkBatch> queue1 = mock();
        MessagePassingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(false);

        List<MessagePassingQueue<WorkBatch>> queues = List.of(queue1, queue2);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);
        assertTrue(predicate,
                "Expected predicate to return true when at least one queue is not empty");
    }

    @Test
    public void testForMonkeyJCToolsListReturnsFalseWhenGenerationCompleteAndAllQueuesEmpty() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);

        MessagePassingQueue<WorkBatch> queue1 = mock();
        MessagePassingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(true);

        List<MessagePassingQueue<WorkBatch>> queues = List.of(queue1, queue2);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyJCTools(mockState, queues);
        assertFalse(predicate,
                "Expected predicate to return false when all queues are empty and generation complete");
    }

    // ===== Tests for forMonkeyBlocking(SolverState, BlockingQueue) - single queue =====

    @Test
    public void testForMonkeyBlockingSingleQueueNullState() {
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(null, mockBlockingQueue);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void testForMonkeyBlockingSingleQueueNullQueue() {
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(mockState, (BlockingQueue<WorkBatch>) null);
        }, "Expected NullPointerException when queue is null");
    }

    @Test
    public void testForMonkeyBlockingSingleQueueReturnsTrueWhenNoSolutionAndGenerationNotComplete() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void testForMonkeyBlockingSingleQueueReturnsFalseWhenSolutionFound() {
        when(mockState.solutionFound()).thenReturn(true);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);
        assertFalse(predicate, "Expected predicate to return false when solution is found");
    }

    @Test
    public void testForMonkeyBlockingSingleQueueReturnsTrueWhenGenerationCompleteButQueueNotEmpty() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockBlockingQueue.isEmpty()).thenReturn(false);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);
        assertTrue(predicate,
                "Expected predicate to return true when generation complete but queue not empty");
    }

    @Test
    public void testForMonkeyBlockingSingleQueueReturnsFalseWhenGenerationCompleteAndQueueEmpty() {
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        when(mockBlockingQueue.isEmpty()).thenReturn(true);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState,
                mockBlockingQueue);
        assertFalse(predicate,
                "Expected predicate to return false when generation complete and queue empty");
    }

    // ===== Tests for forMonkeyBlocking(SolverState, List) - multiple queues =====

    @Test
    public void testForMonkeyBlockingListNullState() {
        List<BlockingQueue<WorkBatch>> queues = List.of(mockBlockingQueue);
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(null, queues);
        }, "Expected NullPointerException when state is null");
    }

    @Test
    public void testForMonkeyBlockingListNullQueues() {
        assertThrows(NullPointerException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(mockState,
                    (List<? extends BlockingQueue<WorkBatch>>) null);
        }, "Expected NullPointerException when queues list is null");
    }

    @Test
    public void testForMonkeyBlockingListEmptyThrows() {
        List<BlockingQueue<WorkBatch>> emptyList = List.of();
        assertThrows(IllegalArgumentException.class, () -> {
            ContinuationPredicates.forMonkeyBlocking(mockState, emptyList);
        }, "Expected IllegalArgumentException when queues list is empty");
    }

    @Test
    public void testForMonkeyBlockingListReturnsTrueWhenNoSolutionAndGenerationNotComplete() {
        List<BlockingQueue<WorkBatch>> queues = List.of(mockBlockingQueue);
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(false);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);
        assertTrue(predicate,
                "Expected predicate to return true when no solution found and generation not complete");
    }

    @Test
    public void testForMonkeyBlockingListReturnsFalseWhenSolutionFound() {
        List<BlockingQueue<WorkBatch>> queues = List.of(mockBlockingQueue);
        when(mockState.solutionFound()).thenReturn(true);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);
        assertFalse(predicate.getAsBoolean(),
                "Expected predicate to return false when solution is found");
    }

    @Test
    public void testForMonkeyBlockingListReturnsTrueWhenGenerationCompleteAndOneQueueNotEmpty() {
        BlockingQueue<WorkBatch> queue1 = mock();
        BlockingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(false);
        List<BlockingQueue<WorkBatch>> queues = List.of(queue1, queue2);
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);
        assertTrue(predicate,
                "Expected predicate to return true when at least one queue is not empty");
    }

    @Test
    public void testForMonkeyBlockingListReturnsFalseWhenGenerationCompleteAndAllQueuesEmpty() {
        BlockingQueue<WorkBatch> queue1 = mock();
        BlockingQueue<WorkBatch> queue2 = mock();
        when(queue1.isEmpty()).thenReturn(true);
        when(queue2.isEmpty()).thenReturn(true);
        List<BlockingQueue<WorkBatch>> queues = List.of(queue1, queue2);
        when(mockState.solutionFound()).thenReturn(false);
        when(mockState.generationComplete()).thenReturn(true);
        BooleanSupplier predicate = ContinuationPredicates.forMonkeyBlocking(mockState, queues);
        assertFalse(predicate,
                "Expected predicate to return false when all queues are empty and generation complete");
    }
}

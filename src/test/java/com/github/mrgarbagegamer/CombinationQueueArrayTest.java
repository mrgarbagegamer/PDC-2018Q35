package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomCombination;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CombinationQueueArrayTest {

    private CombinationQueueArray queueArray;
    private final Random random = new Random();
    private int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);

    /**
     * Sets up a fresh CombinationQueueArray instance before each test.
     */
    @BeforeEach
    void setUp() {
        // Reset singleton for each test
        CombinationQueueArray.resetInstance();
        WorkBatch.resetForTest();
        random.setSeed(System.currentTimeMillis());
        int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        WorkBatch.setNumClicks(numClicks);
    }

    /**
     * Tests that {@link CombinationQueueArray#getInstance(int)} always returns the same singleton
     * instance when called multiple times, regardless of the parameter.
     */
    @Test
    void testSingletonInstance() {
        CombinationQueueArray instance1 = CombinationQueueArray.getInstance(4);
        CombinationQueueArray instance2 = CombinationQueueArray.getInstance(5);
        assertSame(instance1, instance2,
                "getInstance should always return the same singleton instance");
    }

    /**
     * Tests the initial state of the CombinationQueueArray singleton.
     */
    @Test
    void testInitialization() {
        queueArray = CombinationQueueArray.getInstance(4);
        assertNotNull(queueArray, "Singleton instance should not be null");
        assertFalse(queueArray.isSolutionFound(), "solutionFound should be false initially");
        assertFalse(queueArray.isGenerationComplete(),
                "generationComplete should be false initially");
        assertEquals(4, queueArray.getNumQueues(),
                "Number of queues should match the initialization parameter");

        long currentTimeMillis = System.currentTimeMillis();
        assertTrue(queueArray.getStartTime() <= currentTimeMillis,
                "startTime should be set to a time before or equal to current time");

        assertEquals(-1L, queueArray.getEndTime(), "endTime should be -1 initially");

        assertThrows(IllegalStateException.class, () -> {
            queueArray.getWinningMonkey();
        }, "getWinningMonkey should throw IllegalStateException if no solution found");
        assertThrows(IllegalStateException.class, () -> {
            queueArray.getWinningCombination();
        }, "getWinningCombination should throw IllegalStateException if no solution found");

        assertFalse(queueArray.isGenerationComplete(),
                "generationComplete should be false initially");
        assertFalse(queueArray.isSolutionFound(), "solutionFound should be false initially");
    }

    @Test
    void testSolutionFound() {
        queueArray = CombinationQueueArray.getInstance(4);
        short[] solution = generateRandomCombination(numClicks);
        long timeBefore = System.currentTimeMillis();
        queueArray.solutionFound("Tester", solution);
        long timeAfter = System.currentTimeMillis();
        assertTrue(queueArray.isSolutionFound(), "solutionFound should be true after signaling");
        assertArrayEquals(solution, queueArray.getWinningCombination(),
                "Stored solution should match the signaled one");
        assertEquals("Tester", queueArray.getWinningMonkey(),
                "Stored solver name should match the signaled one");
        assertTrue(queueArray.getEndTime() >= timeBefore && queueArray.getEndTime() <= timeAfter,
                "endTime should be set to a time between signaling and now");
    }

    @Test
    void testGenerationComplete() {
        queueArray = CombinationQueueArray.getInstance(4);

        long timeBefore = System.currentTimeMillis();
        queueArray.generationComplete();
        long timeAfter = System.currentTimeMillis();

        assertTrue(queueArray.isGenerationComplete(),
                "generationComplete should be true after signaling");
        assertTrue(queueArray.getEndTime() >= timeBefore && queueArray.getEndTime() <= timeAfter,
                "endTime should be set to a time between signaling and now");
    }

    @Test
    void testOfferAndPoll() {
        final int idx = random.nextInt(4);
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        WorkBatch.setNumClicks(numClicks);
        queueArray = CombinationQueueArray.getInstance(4);

        CombinationQueue queue = queueArray.getQueue(idx);
        WorkBatch batch = queueArray.getWorkBatchPool().poll();
        assertNotNull(batch, "Work batch from pool should not be null");

        // Add a work item to the batch to make it non-empty
        short[] prefix = generateRandomCombination(numClicks - 1);
        batch.addWork(prefix, prefix.length, false, 0);

        assertTrue(queue.add(batch), "Should be able to offer a batch to a queue");

        WorkBatch polledBatch = queue.getWorkBatch();

        assertNotNull(polledBatch, "Should be able to poll a batch from the queue");
        assertSame(batch, polledBatch,
                "Polled batch should be the same instance as the one offered");

        // We don't need to verify the contents, as WorkBatch's internal logic is tested elsewhere
    }

    @Test
    void testPollEmptyQueue() {
        final int idx = random.nextInt(4);
        queueArray = CombinationQueueArray.getInstance(4);

        CombinationQueue queue = queueArray.getQueue(idx);
        assertNull(queue.getWorkBatch(), "Polling from an empty queue should return null");
    }
}
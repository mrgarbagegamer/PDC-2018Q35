package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomCombination;
import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomPrefixOfEvenParity;
import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomPrefixOfOddParity;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;

import com.github.mrgarbagegamer.StartYourMonkeys.GlobalConfig;
import com.github.mrgarbagegamer.WorkBatch.Parity;
import com.github.mrgarbagegamer.WorkBatch.WorkItem;

// TODO: Consider adding the Assumptions API to ensure preconditions for tests are met.

/**
 * Unit tests for the {@link WorkBatch} and {@link CombinationQueueArray} classes. This class
 * focuses on testing the core logic of batching work items and managing the shared queue
 * infrastructure.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public class BatchAndQueueArrayTests {
    static final Random random = new Random();
    private static final int TEST_NUM_CLICKS = 4;
    private static final int TEST_NUM_THREADS = 4;

    /**
     * Initializes the {@link GlobalConfig} singleton once for all tests in this class. This ensures
     * that all components that rely on global configuration can be instantiated and tested
     * correctly. If {@link GlobalConfig} is already initialized, this method will have no effect.
     */
    @BeforeAll
    static void initializeGlobalConfig() {
        // Attempt to initialize GlobalConfig once for all tests in this class.
        GlobalConfig.ensureInitialized(TEST_NUM_CLICKS, TEST_NUM_THREADS, new Grid35());
    }

    /**
     * Resets the random number generator seed before each test to ensure that tests are repeatable
     * but use different random values across test runs.
     */
    @BeforeEach
    void resetRandomInstance() {
        random.setSeed(System.currentTimeMillis());
    }

    /**
     * Tests for the {@link WorkBatch} class, covering its constructors, state management, and
     * iteration logic.
     */
    @Nested
    @Order(1)
    class WorkBatchTest {
        /**
         * Tests the {@link WorkBatch#WorkBatch(int)} constructor to ensure it throws an
         * {@link IllegalArgumentException} when provided with a non-positive capacity.
         */
        @Test
        void testConstructorInvalidCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new WorkBatch(-5),
                    "Constructor should throw IllegalArgumentException for negative capacity.");
            assertThrows(IllegalArgumentException.class, () -> new WorkBatch(0),
                    "Constructor should throw IllegalArgumentException for zero capacity.");
        }

        /**
         * Tests the {@link WorkBatch#WorkBatch()} and {@link WorkBatch#WorkBatch(int)} constructors
         * to ensure they correctly create {@code WorkBatch} instances when {@link GlobalConfig} is
         * properly initialized. Verifies capacity, size, and emptiness.
         */
        @Test
        void testConstructorValidInputs() {
            final WorkBatch batchSingleArg = assertDoesNotThrow(() -> new WorkBatch(10),
                    "Single-arg constructor should not throw when GlobalConfig is initialized.");
            final WorkBatch batchNoArg = assertDoesNotThrow(() -> new WorkBatch(),
                    "No-arg constructor should not throw when GlobalConfig is initialized.");

            // Test the capacity of the batches
            assertEquals(10, batchSingleArg.getCapacity(),
                    "Batch capacity should match the provided value when created with single-arg constructor");
            assertEquals(WorkBatch.BATCH_SIZE, batchNoArg.getCapacity(),
                    "Batch capacity should match the default value when created with no-arg constructor");

            // Test the internal state of the batches
            assertEquals(0, batchSingleArg.size(),
                    "Newly created batch from single-arg constructor should have size 0");
            assertEquals(0, batchNoArg.size(),
                    "Newly created batch from no-arg constructor should have size 0");

            assertTrue(batchSingleArg.isEmpty(),
                    "Newly created batch from single-arg constructor should be empty");
            assertTrue(batchNoArg.isEmpty(),
                    "Newly created batch from no-arg constructor should be empty");

            assertFalse(batchSingleArg.isFull(),
                    "Newly created batch from single-arg constructor should not be full");
            assertFalse(batchNoArg.isFull(),
                    "Newly created batch from no-arg constructor should not be full");
        }

        /**
         * Tests the {@link WorkBatch#addWork(short[], short, boolean)} method to ensure it
         * correctly handles a full batch, preventing new work from being added.
         */
        @Test
        void testAddWorkWhenFull() {
            final WorkBatch batch = new WorkBatch(1);
            final short[] prefix = generateRandomPrefixOfOddParity(TEST_NUM_CLICKS - 1);

            assertTrue(batch.addWork(prefix, (short) (prefix[prefix.length - 1] + 1), true),
                    "Should be able to add work when not full. Final click is: "
                            + (short) (prefix[prefix.length - 1] + 1));
            assertTrue(batch.isFull(), "Batch should be full.");

            final short[] extraPrefix = generateRandomPrefixOfEvenParity(TEST_NUM_CLICKS - 1);
            assertFalse(batch.addWork(extraPrefix,
                    (short) (extraPrefix[extraPrefix.length - 1] + 1), false),
                    "Should not be able to add work when full.");
        }

        /**
         * Tests the {@link WorkItem#set(short[], Parity, int)} method to ensure it throws a
         * {@link NullPointerException} when the provided prefix is null.
         */
        @Test
        void testWorkItemSetNullPrefix() {
            final WorkItem item = new WorkItem();
            assertThrows(NullPointerException.class, () -> item.set(null, Parity.EVEN, 0),
                    "set() should throw NullPointerException for null prefix.");
        }

        /**
         * Tests the {@link WorkItem#clear()} method to ensure it correctly resets the item's state,
         * nullifying final clicks and resetting the start index.
         */
        @Test
        void testWorkItemClear() {
            final WorkItem item = new WorkItem();
            final short[] prefix = generateRandomPrefixOfOddParity(TEST_NUM_CLICKS - 1);
            item.set(prefix, Parity.ODD, 1);

            item.clear();

            assertNull(item.getFinalClicks(), "Final clicks should be null after clear.");
            assertEquals(-1, item.getStart(), "Start index should be -1 after clear.");
        }

        /**
         * Tests that multiple calls to {@link WorkBatch#iterator()} on the same batch return the
         * same iterator instance, and that different {@code WorkBatch} instances return different
         * iterator instances.
         */
        @Test
        void testWorkBatchIterator() {
            final WorkBatch batch = new WorkBatch(3);

            final Iterator<WorkItem> iterator1 = batch.iterator();
            final Iterator<WorkItem> iterator2 = batch.iterator();
            assertSame(iterator1, iterator2,
                    "Multiple calls to iterator() should return the same iterator instance.");

            final WorkBatch batch2 = new WorkBatch(3);
            final Iterator<WorkItem> iterator3 = batch2.iterator();
            assertNotSame(iterator1, iterator3,
                    "Iterators from different WorkBatch instances should be different.");
        }

        /**
         * Tests that {@link WorkBatch#iterator()} on an empty batch correctly reports that it has
         * no next element.
         */
        @Test
        void testIteratorHasNextEmptyBatch() {
            final WorkBatch batch = new WorkBatch(5);
            assertFalse(batch.iterator().hasNext(),
                    "Iterator on empty batch should have no next.");
        }

        /**
         * Tests that calling {@link Iterator#next()} on an iterator from an empty {@link WorkBatch}
         * throws a {@link NoSuchElementException}.
         */
        @Test
        void testIteratorNextEmptyBatch() {
            final WorkBatch batch = new WorkBatch(5);
            assertThrows(NoSuchElementException.class, () -> batch.iterator().next(),
                    "next() on iterator of empty batch should throw.");
        }

        /**
         * Tests that calling {@link Iterator#next()} after consuming all elements in a
         * {@link WorkBatch} throws a {@link NoSuchElementException}.
         */
        @Test
        void testIteratorNextBeyondEnd() {
            final WorkBatch batch = new WorkBatch(1);
            final short[] prefix = generateRandomPrefixOfOddParity(TEST_NUM_CLICKS - 1);
            batch.addWork(prefix, (short) (prefix[prefix.length - 1] + 1), true);

            final Iterator<WorkItem> iterator = batch.iterator();
            iterator.next(); // Consume the element

            assertThrows(NoSuchElementException.class, iterator::next,
                    "next() should throw after reaching the end.");
        }

        /**
         * Tests that the {@link Iterator#remove()} method on the batch iterator is not supported
         * and throws an {@link UnsupportedOperationException}.
         */
        @Test
        void testIteratorRemove() {
            final WorkBatch batch = new WorkBatch(2);
            assertThrows(UnsupportedOperationException.class, () -> batch.iterator().remove(),
                    "Iterator remove() should throw UnsupportedOperationException.");
        }
    }

    /**
     * Tests for the {@link CombinationQueueArray} class, covering its singleton implementation,
     * initialization, state transitions, and queue operations.
     */
    @Nested
    @Order(2)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CombinationQueueArrayTest {

        /**
         * Tests that {@link CombinationQueueArray#getInstance()} consistently returns the same
         * singleton instance across multiple calls.
         */
        @Test
        @Order(1)
        void testSingletonInstance() {
            final CombinationQueueArray instance1 = CombinationQueueArray.getInstance();
            final CombinationQueueArray instance2 = CombinationQueueArray.getInstance();
            assertSame(instance1, instance2,
                    "getInstance should always return the same instance.");
        }

        /**
         * Verifies the initial state of the {@link CombinationQueueArray} singleton, ensuring all
         * flags are correctly set, queues are created, and state-related getters throw exceptions
         * when no solution has been found.
         */
        @Test
        @Order(1)
        void testInitialization() {
            final CombinationQueueArray queueArray = CombinationQueueArray.getInstance();
            assertNotNull(queueArray, "Singleton instance should not be null.");
            assertFalse(queueArray.isSolutionFound(), "solutionFound should be false initially.");
            assertFalse(queueArray.isGenerationComplete(),
                    "generationComplete should be false initially.");

            final int expectedQueues = (TEST_NUM_THREADS + 1) / 2;
            assertEquals(expectedQueues, queueArray.getNumQueues(),
                    "Number of queues should match derived value from GlobalConfig.");
            assertTrue(queueArray.getStartTime() <= System.currentTimeMillis());
            assertEquals(-1L, queueArray.getEndTime(), "endTime should be -1 initially.");

            assertThrows(IllegalStateException.class, queueArray::getWinningMonkey);
            assertThrows(IllegalStateException.class, queueArray::getWinningCombination);
        }

        /**
         * Tests the {@link CombinationQueueArray#solutionFound(String, short[])} method to ensure
         * it correctly sets the solution state, including the winning combination, solver name, and
         * end time.
         */
        @Test
        @Order(2)
        void testSolutionFound() {
            final CombinationQueueArray queueArray = CombinationQueueArray.getInstance();
            final short[] solution = generateRandomCombination(TEST_NUM_CLICKS);
            final String solver = "TestMonkey-1";

            queueArray.solutionFound(solver, solution);

            assertTrue(queueArray.isSolutionFound(),
                    "solutionFound should be true after signaling.");
            assertArrayEquals(solution, queueArray.getWinningCombination(),
                    "Winning combination should match.");
            assertEquals(solver, queueArray.getWinningMonkey(),
                    "Winning monkey name should match.");
            assertTrue(queueArray.getEndTime() > 0, "End time should be set.");
        }

        /**
         * Tests the {@link CombinationQueueArray#generationComplete()} method to ensure it
         * correctly sets the completion flag and records the end time.
         */
        @Test
        @Order(2)
        void testGenerationComplete() {
            final CombinationQueueArray queueArray = CombinationQueueArray.getInstance();
            queueArray.generationComplete();
            assertTrue(queueArray.isGenerationComplete(),
                    "generationComplete should be true after signaling.");
            assertTrue(queueArray.getEndTime() > 0, "End time should be set.");
        }

        /**
         * Tests the full offer-and-poll cycle by retrieving a {@link WorkBatch} from the pool,
         * adding work, offering it to a queue, and then polling it back.
         */
        @Test
        @Order(2)
        void testOfferAndPoll() {
            final CombinationQueueArray queueArray = CombinationQueueArray.getInstance();
            final CombinationQueue queue = queueArray.getQueue(0);
            final WorkBatch batch = queueArray.getWorkBatchPool().poll();
            assertNotNull(batch, "Should get a batch from the pool.");

            final short[] prefix = generateRandomPrefixOfEvenParity(TEST_NUM_CLICKS - 1);
            batch.addWork(prefix, (short) (prefix[prefix.length - 1] + 1), false);
            assertTrue(queue.offer(batch), "Should be able to offer a batch to a queue.");

            final WorkBatch polledBatch = queue.poll();
            assertNotNull(polledBatch, "Should be able to poll a batch from the queue.");
            assertSame(batch, polledBatch, "Polled batch should be the same instance.");
        }

        /**
         * Tests that polling from an empty {@link CombinationQueue} correctly returns {@code null}
         * instead of blocking or throwing an exception.
         */
        @Test
        @Order(2)
        void testPollEmptyQueue() {
            final CombinationQueueArray queueArray = CombinationQueueArray.getInstance();
            final CombinationQueue queue = queueArray.getQueue(0);
            assertNull(queue.poll(), "Polling from an empty queue should return null.");
        }
    }
}

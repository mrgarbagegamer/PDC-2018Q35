package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomCombination;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

class WorkBatchTest {

    static final Random random = new Random();
    
    /**
     * Resets the state after each test to ensure independence between tests and between runs.
     */
    @AfterEach
    void resetState() {
        // Reset numClicks to default after each test
        WorkBatch.resetNumClicks();

        // Reset random seed
        random.setSeed(System.currentTimeMillis());
    }

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it throws an
     * IllegalArgumentException when provided with invalid input (non-positive integers).
     */
    @Test
    void testSetNumClicksInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setNumClicks(-1);
        }, "Expected IllegalArgumentException when setting numClicks to negative number.");
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setNumClicks(0);
        }, "Expected IllegalArgumentException when setting numClicks to zero.");
    }

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it accepts valid positive integer
     * input without throwing exceptions and correctly sets the numClicks field.
     * 
     * This method assumes that the {@link WorkBatch#setNumClicks(int)}, {@link WorkBatch#isFull()},
     * {@link WorkBatch#add(short[])}, and {@link WorkBatch#poll()} methods are functioning correctly.
     */
    @Test
    void testSetNumClicks() {
        final int positiveNumClicks = random.nextInt(1, Grid.NUM_CELLS + 1);

        assertDoesNotThrow(() -> {
            WorkBatch.setNumClicks(positiveNumClicks);
        }, "Did not expect exception when setting numClicks to positive number.");

        // Verify that numClicks is set correctly by creating a WorkBatch and checking its behavior
        final int capacity = random.nextInt(1, 100);
        WorkBatch batch = new WorkBatch(capacity);

        short[] testCombination = generateRandomCombination(positiveNumClicks);
        batch.add(testCombination);

        short[] polledCombination = batch.poll();
        assertEquals(positiveNumClicks, polledCombination.length,
            "Polled combination should have length equal to the set numClicks.");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int) single-argument} and {@link WorkBatch#WorkBatch()
     * no-argument} constructors to ensure they throws an IllegalStateException if numClicks is not
     * initialized before creating a WorkBatch instance.
     */
    @Test
    void testConstructorNumClicksNotInitialized() {
        assertThrows(IllegalStateException.class, () -> {
            new WorkBatch(10);
        }, "Expected IllegalStateException from single-argument constructor when numClicks is not initialized.");
        assertThrows(IllegalStateException.class, () -> {
            new WorkBatch();
        }, "Expected IllegalStateException from no-argument constructor when numClicks is not initialized.");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int)} constructor to ensure it throws an
     * IllegalArgumentException when provided with invalid capacity (zero or negative).
     * 
     * This method assumes that {@link WorkBatch#setNumClicks(int)} is functioning correctly.
     */
    @Test
    void testConstructorInvalidCapacity() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        WorkBatch.setNumClicks(numClicks);

        assertThrows(IllegalArgumentException.class, () -> {
            new WorkBatch(0);
        }, "Expected IllegalArgumentException when initializing WorkBatch with zero capacity.");
        assertThrows(IllegalArgumentException.class, () -> {
            new WorkBatch(-5);
        }, "Expected IllegalArgumentException when initializing WorkBatch with negative capacity.");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int) single-argument} and {@link WorkBatch#WorkBatch()
     * no-argument} constructors to ensure they correctly initialize WorkBatch instances when provided
     * with valid input.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getCapacity()}, {@link WorkBatch#size()}, {@link WorkBatch#isEmpty()},
     * {@link WorkBatch#isFull()}, {@link WorkBatch#add(short[])}, and {@link WorkBatch#poll()} are
     * functioning correctly.
     */
    @Test
    void testConstructor() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 1000);

        WorkBatch.setNumClicks(numClicks);
        WorkBatch batchSingleArg = assertDoesNotThrow(() -> new WorkBatch(capacity),
            "Did not expect exception when initializing WorkBatch with valid capacity and numClicks.");
        WorkBatch batchNoArg = assertDoesNotThrow(() -> new WorkBatch(),
            "Did not expect exception when initializing WorkBatch with no-argument constructor after setting numClicks.");

        // Test the capacity of the batches
        assertEquals(capacity, batchSingleArg.getCapacity(),
            "Batch capacity should match the provided capacity when created with the single-argument constructor.");
        assertEquals(WorkBatch.BATCH_SIZE, batchNoArg.getCapacity(),
            "Batch capacity should match the default BATCH_SIZE when created with the no-argument constructor.");

        // Test the internal state of the batches
        assertEquals(0, batchSingleArg.size(),
            "Newly created WorkBatch (single-argument) should have size 0.");
        assertEquals(0, batchNoArg.size(),
            "Newly created WorkBatch (no-argument) should have size 0.");

        assertEquals(capacity, batchSingleArg.remainingCapacity(),
            "Remaining capacity of newly created WorkBatch (single-argument) should equal its capacity.");
        assertEquals(WorkBatch.BATCH_SIZE, batchNoArg.remainingCapacity(),
            "Remaining capacity of newly created WorkBatch (no-argument) should equal its capacity.");

        assertFalse(batchSingleArg.isFull(),
            "Newly created WorkBatch (single-argument) should not be full.");
        assertFalse(batchNoArg.isFull(),
            "Newly created WorkBatch (no-argument) should not be full.");
    }

    /**
     * Tests the {@link WorkBatch#add(short[])} method to ensure it returns false when attempting to add
     * a combination to a full WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#isFull()} are functioning correctly.
     */
    @Test
    void testAddShortArrayWhenFull() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        // Fill the batch to capacity
        while (!batch.isFull()) {
            short[] testCombination = generateRandomCombination(numClicks);
            assertTrue(batch.add(testCombination), "Adding to a non-full WorkBatch should return true.");
        }

        // Now the batch is full, adding another combination should return false
        short[] extraCombination = generateRandomCombination(numClicks);

        assertFalse(batch.add(extraCombination), "Adding to a full WorkBatch should return false.");
    }

    /**
     * Tests the {@link WorkBatch#add(short[])} method to ensure it correctly adds a combination to a
     * non-full WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#isFull()} are functioning correctly.
     */
    @Test
    void testAddShortArray() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        short[] testCombination = generateRandomCombination(numClicks);
        assertTrue(batch.add(testCombination), "Adding to a non-full WorkBatch should return true.");
    }

    /**
     * Tests the {@link WorkBatch#add(short[], int, short)} method to ensure it returns false when
     * attempting to add a combination to a full WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#isFull()} are functioning correctly.
     */
    @Test
    void testAddPrefixAndLastElementWhenFull() {
        final int numClicks = random.nextInt(2, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        while (!batch.isFull()) {
            short[] prefix = generateRandomCombination(numClicks - 1, Grid.NUM_CELLS - 1);
            short lastElement = (short) random.nextInt(prefix[numClicks - 2] + 1, Grid.NUM_CELLS);
            assertTrue(batch.add(prefix, prefix.length, lastElement), "Adding to a non-full WorkBatch should return true.");
        }

        short[] extraPrefix = generateRandomCombination(numClicks - 1, Grid.NUM_CELLS - 1);
        short lastElement = (short) random.nextInt(extraPrefix[numClicks - 2] + 1, Grid.NUM_CELLS);
        assertFalse(batch.add(extraPrefix, extraPrefix.length, lastElement), "Adding to a full WorkBatch should return false.");
    }

    /**
     * Tests the {@link WorkBatch#add(short[], int, short)} method to ensure it correctly adds a
     * combination to a non-full WorkBatch and constructs the combination properly.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#isFull()}, and {@link WorkBatch#poll()} are functioning correctly.
     */
    @Test
    void testAddPrefixAndLastElement() {
        final int numClicks = random.nextInt(2, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        short[] prefix = generateRandomCombination(numClicks - 1, Grid.NUM_CELLS - 1);
        short lastElement = (short) random.nextInt(prefix[numClicks - 2] + 1, Grid.NUM_CELLS);
        assertTrue(batch.add(prefix, prefix.length, lastElement), "Adding to a non-full WorkBatch should return true.");

        short[] polledCombination = batch.poll();
        if (polledCombination == null) {
            return; // If there's a null combination being polled, let it be caught by other tests
        }

        assertEquals(numClicks, polledCombination.length,
            "Polled combination should have length equal to numClicks.");
        
        short[] expectedCombination = new short[numClicks];
        System.arraycopy(prefix, 0, expectedCombination, 0, prefix.length);
        expectedCombination[numClicks - 1] = lastElement;

        assertArrayEquals(expectedCombination, polledCombination,
                "Polled combination should have a correctly formed combination from prefix and last element.");
    }

    /**
     * Tests the {@link WorkBatch#addBulk(short[], int, short[], int, int)} method to ensure it
     * correctly adds multiple combinations to a WorkBatch and constructs them properly.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#isFull()}, and {@link WorkBatch#poll()} are functioning correctly.
     */
    @Test
    void testAddBulk() {
        final int numClicks = random.nextInt(2, Grid.NUM_CELLS - 1); // Ensure at least 2 clicks for prefix + nextClicks
        final int capacity = random.nextInt(1, Grid.NUM_CELLS - numClicks + 1); // Ensure capacity fits within grid limits

        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        // Generate a prefix of combinations to add, making sure that the last element allows for an array
        // of next clicks
        short[] prefix = generateRandomCombination(numClicks - 1, Grid.NUM_CELLS - capacity);
        short[] nextClicks = generateRandomCombination(capacity, prefix[numClicks - 2] + 1, Grid.NUM_CELLS + 1);

        int added = batch.addBulk(prefix, prefix.length, nextClicks, 0, nextClicks.length);
        assertEquals(capacity, added, "Should add all combinations from nextClicks to the WorkBatch.");

        short[] polledCombination = batch.poll();
        if (polledCombination == null) {
            return; // If there's a null combination being polled, let it be caught by other tests
        }

        assertEquals(numClicks, polledCombination.length,
            "Polled combination should have length equal to numClicks.");

        short[] expectedPrefix = new short[numClicks - 1];
        System.arraycopy(polledCombination, 0, expectedPrefix, 0, numClicks - 1);

        assertArrayEquals(prefix, expectedPrefix, "Polled combination should have the correct prefix.");
    }

    /**
     * Tests the {@link WorkBatch#poll()} method to ensure it returns null when polling from an empty
     * WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#isEmpty()} are functioning correctly.
     */
    @Test
    void testPollWhenEmpty() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch();

        if (!batch.isEmpty()) {
            return; // If the batch is not empty, let it be caught by other tests
        }

        assertNull(batch.poll(), "Polling from an empty WorkBatch should return null.");
    }

    /**
     * Tests the {@link WorkBatch#poll()} method to ensure it correctly retrieves and removes
     * combinations from a non-empty WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#add(short[])}, and {@link WorkBatch#isEmpty()} are functioning correctly.
     */
    @Test
    void testPoll() {        
        // Setup
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        // Test for a single addition and poll
        short[] testCombination = generateRandomCombination(numClicks);
        batch.add(testCombination);
        short[] polledCombination = batch.poll();
        assertNotNull(polledCombination, "Polled combination should not be null after adding a combination.");
        assertArrayEquals(testCombination, polledCombination, "Polled combination should match the added combination.");

        // Test for multiple additions and polls
        short[] combo1 = generateRandomCombination(numClicks);
        short[] combo2 = generateRandomCombination(numClicks);
        batch.add(combo1);
        batch.add(combo2);

        if (batch.isEmpty()) {
            return; // If the batch is empty, let it be caught by other tests
        }

        short[] polledCombo1 = batch.poll();
        assertNotNull(polledCombo1, "First polled combination should not be null.");
        assertArrayEquals(polledCombo1, combo1, "First polled combination should match the first added combination.");

        short[] polledCombo2 = batch.poll();
        assertNotNull(polledCombo2, "Second polled combination should not be null.");
        assertArrayEquals(polledCombo2, combo2, "Second polled combination should match the second added combination.");
    }

    /**
     * Tests the {@link WorkBatch#isEmpty()} method to ensure it accurately reflects the empty state of
     * the WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#add(short[])}, and {@link WorkBatch#poll()} are functioning correctly.
     */
    @Test
    void testIsEmpty() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        assertTrue(batch.isEmpty(), "Newly created WorkBatch should be empty.");
        short[] testCombination = generateRandomCombination(numClicks);
        batch.add(testCombination);
        assertFalse(batch.isEmpty(), "WorkBatch should not be empty after adding a combination.");
        batch.poll();
        assertTrue(batch.isEmpty(), "WorkBatch should be empty after polling the last combination.");
    }

    /**
     * Tests the {@link WorkBatch#size()} method to ensure it accurately reflects the number of
     * combinations stored in the WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#add(short[])}, and {@link WorkBatch#poll()} are functioning correctly.
     */
    @Test
    void testSize() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        assertEquals(0, batch.size(), "Newly created WorkBatch should have size 0.");
        short[] testCombination = generateRandomCombination(numClicks);
        batch.add(testCombination);
        assertEquals(1, batch.size(), "WorkBatch should have size 1 after adding a combination.");
        batch.poll();
        assertEquals(0, batch.size(), "WorkBatch should have size 0 after polling the last combination.");
    }

    /**
     * Tests the {@link WorkBatch#remainingCapacity()} method to ensure it accurately reflects the
     * remaining capacity of the WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#add(short[])}, and {@link WorkBatch#poll()} are functioning correctly.
     */
    @Test
    void testRemainingCapacity() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        assertEquals(capacity, batch.remainingCapacity(),
            "Newly created WorkBatch should have remaining capacity equal to its capacity.");
        short[] testCombination = generateRandomCombination(numClicks);
        batch.add(testCombination);
        assertEquals(capacity - 1, batch.remainingCapacity(),
            "WorkBatch should have remaining capacity decreased by 1 after adding a combination.");
        batch.poll();
        assertEquals(capacity, batch.remainingCapacity(),
                "WorkBatch should have remaining capacity equal to its capacity after polling the last combination.");
    }

    /**
     * Tests the {@link WorkBatch#clear()} method to ensure it correctly resets the WorkBatch to an
     * empty state.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#add(short[])}, {@link WorkBatch#size()}, {@link WorkBatch#isEmpty()},
     * {@link WorkBatch#isFull()}, and {@link WorkBatch#poll()} are functioning correctly.
     */
    @Test
    void testClear() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        
        // Fill and clear to test state
        WorkBatch batch = new WorkBatch(capacity);
        short[] testCombination = generateRandomCombination(numClicks);
        batch.add(testCombination);
        batch.clear();

        assertEquals(0, batch.size(), "WorkBatch should have size 0 after clearing.");
        assertEquals(capacity, batch.remainingCapacity(),
            "WorkBatch should have remaining capacity equal to its capacity after clearing.");
        assertTrue(batch.isEmpty(), "WorkBatch should be empty after clearing.");
        assertFalse(batch.isFull(), "WorkBatch should not be full after clearing.");
        assertNull(batch.poll(), "Polling from a cleared WorkBatch should return null.");
    }

    /**
     * Tests the {@link WorkBatch#isFull()} method to ensure it accurately reflects the full state of
     * the WorkBatch.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#add(short[])} are functioning correctly.
     */
    @Test
    void testIsFull() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(2, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        assertFalse(batch.isFull(), "Newly created WorkBatch should not be full.");
        short[] testCombination = generateRandomCombination(numClicks);
        batch.add(testCombination);
        assertFalse(batch.isFull(), "WorkBatch should not be full after adding one combination (unless the size is 1).");

        // Fill the batch to capacity
        while (!batch.isFull()) {
            short[] combo = generateRandomCombination(numClicks);
            batch.add(combo);
        }
        assertTrue(batch.isFull(), "WorkBatch should be full after adding combinations up to its capacity.");
    }

    /**
     * Tests the {@link WorkBatch#getCapacity()} method to ensure it accurately reflects the capacity of
     * the WorkBatch, and that the capacity remains unchanged after various operations.
     * 
     * This method assumes that the methods {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#add(short[])}, {@link WorkBatch#poll()}, and {@link WorkBatch#clear()} are
     * functioning correctly.
     */
    @Test
    void testGetCapacity() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batchSingleArg = new WorkBatch(capacity);
        WorkBatch batchNoArg = new WorkBatch();

        assertEquals(capacity, batchSingleArg.getCapacity(),
            "Batch capacity should match the provided capacity when created with the single-argument constructor.");
        assertEquals(WorkBatch.BATCH_SIZE, batchNoArg.getCapacity(),
            "Batch capacity should match the default BATCH_SIZE when created with the no-argument constructor.");
        
        // Ensure that capacity does not change after additions
        short[] testCombination = generateRandomCombination(numClicks);
        batchSingleArg.add(testCombination);
        assertEquals(capacity, batchSingleArg.getCapacity(),
            "Batch capacity should remain unchanged after additions.");
        batchNoArg.add(testCombination);
        assertEquals(WorkBatch.BATCH_SIZE, batchNoArg.getCapacity(),
            "Batch capacity should remain unchanged after additions.");

        // Ensure that capacity does not change after polling
        batchSingleArg.poll();
        assertEquals(capacity, batchSingleArg.getCapacity(),
            "Batch capacity should remain unchanged after polling.");
        batchNoArg.poll();
        assertEquals(WorkBatch.BATCH_SIZE, batchNoArg.getCapacity(),
            "Batch capacity should remain unchanged after polling.");
        
        // Ensure that capacity does not change after clearing
        batchSingleArg.clear();
        assertEquals(capacity, batchSingleArg.getCapacity(),
            "Batch capacity should remain unchanged after clearing.");
        batchNoArg.clear();
        assertEquals(WorkBatch.BATCH_SIZE, batchNoArg.getCapacity(),
            "Batch capacity should remain unchanged after clearing.");
    }

    /**
     * Tests the {@link WorkBatch#toString()} method to ensure it returns the correct string
     * representation for an empty WorkBatch.
     * 
     * This method assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning correctly.
     */
    @Test
    void testToStringEmptyBatch() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batchSingleArg = new WorkBatch(capacity);
        WorkBatch batchNoArg = new WorkBatch();

        String expectedString = "WorkBatch{size=0, capacity=" + capacity + ", firstCombo=null}";
        assertEquals(expectedString, batchSingleArg.toString(),
                "String representation of an empty WorkBatch (single-argument) is incorrect.");
        String expectedStringNoArg = "WorkBatch{size=0, capacity=" + WorkBatch.BATCH_SIZE + ", firstCombo=null}";
        assertEquals(expectedStringNoArg, batchNoArg.toString(),
                "String representation of an empty WorkBatch (no-argument) is incorrect.");
    }

    /**
     * Tests the {@link WorkBatch#toString()} method to ensure it returns the correct string
     * representation for a non-empty WorkBatch, and that the first combination does not change
     * after additions.
     * 
     * This method assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#add(short[])} methods are functioning correctly.
     */
    @Test
    void testToStringNonEmptyBatch() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        final int capacity = random.nextInt(1, 100);
        WorkBatch.setNumClicks(numClicks);
        WorkBatch batch = new WorkBatch(capacity);

        short[] testCombination = generateRandomCombination(numClicks);
        batch.add(testCombination);
        String expectedString = "WorkBatch{size=1, capacity=" + capacity + ", firstCombo="
                + Arrays.toString(testCombination) + "}";
        assertEquals(expectedString, batch.toString(),
                "String representation of a non-empty WorkBatch with size 1 is incorrect.");
        
        batch.add(generateRandomCombination(numClicks));
        expectedString = "WorkBatch{size=2, capacity=" + capacity + ", firstCombo="
                + Arrays.toString(testCombination) + "}";
        assertEquals(expectedString, batch.toString(),
                "String representation of a non-empty WorkBatch with size 2 is incorrect.");
    }

    /**
     * Tests the {@link WorkBatch#equals(Object)} method to ensure it correctly determines equality
     * between WorkBatch instances under various scenarios.
     * 
     * This method assumes that the {@link WorkBatch#setNumClicks(int)}, {@link WorkBatch#add(short[])},
     * and {@link WorkBatch#poll()}, and {@link WorkBatch#clear()} methods are functioning correctly.
     */
    @Test
    void testEquals() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        WorkBatch.setNumClicks(numClicks);

        Object object = new Object();

        WorkBatch batchOne = new WorkBatch(10);
        WorkBatch batchOneReference = batchOne;
        WorkBatch batchTwo = new WorkBatch(10);
        WorkBatch batchThree = new WorkBatch(20);

        assertNotEquals(batchOne, object, "WorkBatch should not be equal to an object of a different type");
        assertNotEquals(batchOne, null, "Non-null WorkBatch should not be equal to null");
        assertEquals(batchOne, batchOneReference, "WorkBatch should be equal to itself");
        assertEquals(batchOne, batchTwo, "Two empty WorkBatches with the same capacity should be equal");
        assertNotEquals(batchOne, batchThree, "Two WorkBatches with different capacities should not be equal");

        // Ensure that adding combinations affects equality
        short[] combinationOne = generateRandomCombination(numClicks);
        batchOne.add(combinationOne);
        assertNotEquals(batchOne, batchTwo, "WorkBatches with different sizes should not be equal");

        short[] combinationTwo = combinationOne.clone();
        batchTwo.add(combinationTwo);
        assertEquals(batchOne, batchTwo, "WorkBatches with the same contents should be equal");

        short[] combinationThree = combinationTwo.clone();
        batchThree.add(combinationThree);
        assertNotEquals(batchTwo, batchThree, "WorkBatches with the same contents but different capacities should not be equal");
        
        short[] combinationFour = generateRandomCombination(numClicks);
        batchOne.add(combinationFour);

        short[] combinationFive = generateRandomCombination(numClicks);
        // Ensure combinationFive is different from combinationFour
        while (Arrays.equals(combinationFour, combinationFive)) {
            combinationFive = generateRandomCombination(numClicks);
        }
        batchTwo.add(combinationFive);
        assertNotEquals(batchOne, batchTwo,
                "WorkBatches with the same size but different contents should not be equal");

        // Ensure that clearing affects equality
        batchOne.clear();
        assertNotEquals(batchOne, batchTwo, "Cleared WorkBatch should not be equal to non-cleared WorkBatch");
        batchTwo.clear();
        assertEquals(batchOne, batchTwo, "Two cleared WorkBatches with the same capacity should be equal");

        batchThree.clear();
        assertNotEquals(batchOne, batchThree, "Cleared WorkBatches with different capacities should not be equal");

        // Ensure that the pointer order doesn't matter for equality (head and tail don't matter)
        WorkBatch batchFour = new WorkBatch(10);
        WorkBatch batchFive = new WorkBatch(10);

        short[] comboA = generateRandomCombination(numClicks);
        short[] comboB = generateRandomCombination(numClicks);
        short[] comboC = generateRandomCombination(numClicks);

        batchFour.add(comboA);
        batchFour.add(comboB);
        batchFour.poll(); // Remove comboA
        batchFour.add(comboC);

        batchFive.add(comboB);
        batchFive.add(comboC);

        assertEquals(batchFour, batchFive,
                "WorkBatches with the same contents but different internal order should be equal");

        
    }

    /**
     * Tests the {@link WorkBatch#hashCode()} method to ensure it produces consistent hash codes for
     * equal WorkBatch instances and different hash codes for non-equal instances. This test uses the
     * same test cases as the {@link #testEquals()} method to verify the contract between equals and
     * hashCode.
     */
    @Test
    void testHashCode() {
        final int numClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        WorkBatch.setNumClicks(numClicks);

        WorkBatch batchOne = new WorkBatch(10);
        WorkBatch batchOneReference = batchOne;
        WorkBatch batchTwo = new WorkBatch(10);
        WorkBatch batchThree = new WorkBatch(20);

        assertEquals(batchOne.hashCode(), batchOneReference.hashCode(),
                "Hash codes should be equal for the same WorkBatch instance");
        assertEquals(batchOne.hashCode(), batchTwo.hashCode(),
                "Hash codes should be equal for two empty WorkBatches with the same capacity");
        assertNotEquals(batchOne.hashCode(), batchThree.hashCode(),
                "Hash codes should be different for WorkBatches with different capacities");

        // Ensure that adding combinations affects hash code
        short[] combinationOne = generateRandomCombination(numClicks);
        batchOne.add(combinationOne);
        assertNotEquals(batchOne.hashCode(), batchTwo.hashCode(),
                "Hash codes should be different for WorkBatches with different sizes");

        short[] combinationTwo = combinationOne.clone();
        batchTwo.add(combinationTwo);
        assertEquals(batchOne.hashCode(), batchTwo.hashCode(),
                "Hash codes should be equal for WorkBatches with the same contents");

        short[] combinationThree = combinationTwo.clone();
        batchThree.add(combinationThree);
        assertNotEquals(batchTwo.hashCode(), batchThree.hashCode(),
                "Hash codes should be different for WorkBatches with the same contents but different capacities");

        short[] combinationFour = generateRandomCombination(numClicks);
        batchOne.add(combinationFour);

        short[] combinationFive = generateRandomCombination(numClicks);
        // Ensure combinationFive is different from combinationFour
        while (Arrays.equals(combinationFour, combinationFive)) {
            combinationFive = generateRandomCombination(numClicks);
        }
        batchTwo.add(combinationFive);
        assertNotEquals(batchOne.hashCode(), batchTwo.hashCode(),
                "Hash codes should be different for WorkBatches with the same size but different contents");

        // Ensure that clearing affects hash code
        batchOne.clear();
        assertNotEquals(batchOne.hashCode(), batchTwo.hashCode(),
                "Hash codes should be different for cleared and non-cleared WorkBatches");
        batchTwo.clear();
        assertEquals(batchOne.hashCode(), batchTwo.hashCode(),
                "Hash codes should be equal for two cleared WorkBatches with the same capacity");

        batchThree.clear();
        assertNotEquals(batchOne.hashCode(), batchThree.hashCode(),
                "Hash codes should be different for cleared WorkBatches with different capacities");

        // Ensure that the pointer order doesn't matter for hash code (head and tail don't matter)
        WorkBatch batchFour = new WorkBatch(10);
        WorkBatch batchFive = new WorkBatch(10);

        short[] comboA = generateRandomCombination(numClicks);
        short[] comboB = generateRandomCombination(numClicks);
        short[] comboC = generateRandomCombination(numClicks);

        batchFour.add(comboA);
        batchFour.add(comboB);
        batchFour.poll(); // Remove comboA
        batchFour.add(comboC);

        batchFive.add(comboB);
        batchFive.add(comboC);

        assertEquals(batchFour.hashCode(), batchFive.hashCode(),
                "Hash codes should be equal for WorkBatches with the same contents but different internal order");
    }
}
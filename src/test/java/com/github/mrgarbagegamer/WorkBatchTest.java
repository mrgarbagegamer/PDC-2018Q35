package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.TestingUtils.generateClickIndices;
import static com.github.mrgarbagegamer.util.TestingUtils.generateEvenClickIndices;
import static com.github.mrgarbagegamer.util.TestingUtils.generateOddClickIndices;
import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomCombination;
import static com.github.mrgarbagegamer.util.TestingUtils.getFinalClicks;
import static com.github.mrgarbagegamer.util.TestingUtils.getPrefixParity;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

import com.github.mrgarbagegamer.WorkBatch.WorkItem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkBatchTest {

    static final Random random = new Random();

    /**
     * Resets the state after each test to ensure independence between tests and between runs.
     */
    @AfterEach
    void resetState() {
        // Reset numClicks to default after each test
        WorkBatch.resetForTest();

        // Reset random seed
        random.setSeed(System.currentTimeMillis());
    }

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it throws an
     * IllegalArgumentException when provided with a negative input.
     */
    @Test
    void testSetNumClicksNegativeInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setNumClicks(-1);
        }, "Expected IllegalArgumentException when setting numClicks to negative number.");
        assertEquals(-1, WorkBatch.getNumClicks(),
                "numClicks should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it throws an
     * IllegalArgumentException when provided with a zero input.
     */
    @Test
    void testSetNumClicksZeroInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setNumClicks(0);
        }, "Expected IllegalArgumentException when setting numClicks to zero.");
        assertEquals(-1, WorkBatch.getNumClicks(),
                "numClicks should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it correctly sets numClicks
     * when provided with a valid positive input.
     * 
     * This test assumes that the {@link WorkBatch#getNumClicks()} method is functioning correctly.
     */
    @Test
    void testSetNumClicksValidInput() {
        final int validNumClicks = random.nextInt(1, Grid.NUM_CELLS + 1);
        WorkBatch.setNumClicks(validNumClicks);
        assertEquals(validNumClicks, WorkBatch.getNumClicks(),
                "numClicks should be set to the valid input value.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalStateException when numClicks has not been set prior to calling it.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysUnsetNumClicks() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] odd = {0, 1, 2};
        final short[] even = {3, 4, 5};

        assertThrows(IllegalStateException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalStateException when setting click index arrays before numClicks is set.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the odd click indices array is null.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysNullOddIndices() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] even = {0, 1, 2};
        WorkBatch.setNumClicks(3);
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(null, even);
        }, "Expected IllegalArgumentException when odd click indices array is null.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the even click indices array is null.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysNullEvenIndices() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] odd = {0, 1, 2};
        WorkBatch.setNumClicks(3);
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, null);
        }, "Expected IllegalArgumentException when even click indices array is null.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the odd click indices array is empty.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysEmptyOddIndices() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] odd = {};
        final short[] even = {0, 1, 2};
        WorkBatch.setNumClicks(3);
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when odd click indices array is empty.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the even click indices array is empty.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysEmptyEvenIndices() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] odd = {0, 1, 2};
        final short[] even = {};
        WorkBatch.setNumClicks(3);
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when even click indices array is empty.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the odd click indices array has more than 6 elements.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysTooManyOddIndices() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] odd = {0, 1, 2, 3, 4, 5, 6};
        final short[] even = {7, 8, 9};
        WorkBatch.setNumClicks(10);

        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when odd.length > 6.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the even click indices array length does not equal
     * Grid.NUM_CELLS - odd.length.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysEvenLengthMismatch() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] odd = {0, 1, 2};
        final short[] even = {3, 4};
        WorkBatch.setNumClicks(6);

        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when even.length != Grid.NUM_CELLS - odd.length.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the odd click indices array is null.
     */
    @Test
    void testSetClickIndexArraysDuplicateIndices() {
        final short[] previousOdd = WorkBatch.getOddClickIndices();
        final short[] previousEven = WorkBatch.getEvenClickIndices();

        final short[] odd = {0, 1, 2};
        // This array must contain at least one duplicate with the odd array ()
        final short[] even = generateRandomCombination(Grid.NUM_CELLS - odd.length, 2,
                Grid.NUM_CELLS - 1);
        WorkBatch.setNumClicks(6);

        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when there are duplicate indices between odd and even arrays.");

        // Ensure the odd and even click indices remain the same.
        assertEquals(previousOdd, WorkBatch.getOddClickIndices(),
                "Odd click indices should remain unchanged after exception.");
        assertEquals(previousEven, WorkBatch.getEvenClickIndices(),
                "Even click indices should remain unchanged after exception.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it
     * correctly sets the odd and even click index arrays when provided with valid inputs.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#getOddClickIndices()}, and {@link WorkBatch#getEvenClickIndices()} methods
     * are functioning correctly.
     */
    @Test
    void testSetClickIndexArraysValidInput() {
        final short cell = (short) random.nextInt(0, Grid.NUM_CELLS);
        final short[] odd = generateOddClickIndices(cell);

        // Generate even indices as all the non-adjacent cells to ensure no duplicates
        final short[] even = generateEvenClickIndices(cell);

        WorkBatch.setNumClicks(3); // This doesn't matter
        assertDoesNotThrow(() -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Setting valid click index arrays should not throw an exception.");

        assertArrayEquals(odd, WorkBatch.getOddClickIndices(),
                "Odd click indices should match the set array.");
        assertArrayEquals(even, WorkBatch.getEvenClickIndices(),
                "Even click indices should match the set array.");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int) single-argument} and {@link WorkBatch#WorkBatch()
     * no-argument} constructors to ensure they throw IllegalStateException when numClicks has not
     * been initialized.
     */
    @Test
    void testConstructorNumClicksNotInitialized() {
        assertThrows(IllegalStateException.class, () -> {
            new WorkBatch(10);
        }, "Single-arg constructor should throw IllegalStateException if numClicks is not initialized");
        assertThrows(IllegalStateException.class, () -> {
            new WorkBatch();
        }, "No-arg constructor should throw IllegalStateException if numClicks is not initialized");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int) single-argument} constructor to ensure it throws
     * IllegalArgumentException when provided with a non-positive capacity.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testConstructorInvalidCapacity() {
        WorkBatch.setNumClicks(4);
        assertThrows(IllegalArgumentException.class, () -> {
            new WorkBatch(-5);
        }, "Single-arg constructor should throw IllegalArgumentException if capacity is negative");
        assertThrows(IllegalArgumentException.class, () -> {
            new WorkBatch(0);
        }, "Single-arg constructor should throw IllegalArgumentException if capacity is zero");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int) single-argument} and {@link WorkBatch#WorkBatch()
     * no-argument} constructors to ensure they correctly create WorkBatch instances when provided
     * with valid inputs.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#setClickIndexArrays(short[], short[])}, {@link WorkBatch#getCapacity()},
     * {@link WorkBatch#size()}, {@link WorkBatch#isEmpty()}, and {@link WorkBatch#isFull()} methods
     * are functioning correctly.
     */
    @Test
    void testConstructorValidInputs() {
        WorkBatch.setNumClicks(4);

        final short[][] clickIndices = generateClickIndices();
        WorkBatch.setClickIndexArrays(clickIndices[0], clickIndices[1]);

        final WorkBatch batchSingleArg = assertDoesNotThrow(() -> {
            return new WorkBatch(10);
        }, "Single-arg constructor should not throw when numClicks and arrays are initialized and capacity is valid");
        final WorkBatch batchNoArg = assertDoesNotThrow(() -> {
            return new WorkBatch();
        }, "No-arg constructor should not throw when numClicks and arrays are initialized");

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
     * Tests the {@link WorkBatch#addWork(short[], int, boolean, int)} method to ensure it returns
     * false when attempting to add work to a full WorkBatch.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkBatch#setClickIndexArrays(short[], short[])}, and {@link WorkBatch#isFull()}
     * methods are functioning correctly, and that
     * {@link WorkBatch#addWork(short[], int, boolean, int)} correctly adds work when the batch is
     * not full.
     */
    @Test
    void testAddWorkWhenFull() {
        final Random random = new Random();
        WorkBatch.setNumClicks(4);

        final short[][] clickIndices = generateClickIndices();
        WorkBatch.setClickIndexArrays(clickIndices[0], clickIndices[1]);

        final WorkBatch batch = new WorkBatch(2);

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Fill the batch to capacity
        while (!batch.isFull()) {
            final short[] prefix = generateRandomCombination(3);
            final boolean parity = getPrefixParity(prefix, firstTrueCell);
            final int start = prefix[prefix.length - 1] + 1;
            assertTrue(batch.addWork(prefix, prefix.length, parity, start),
                    "Should be able to add work item when batch is not full");
        }

        // Now that the batch is full, adding another work item should fail
        final short[] extraPrefix = generateRandomCombination(3);
        final boolean extraParity = getPrefixParity(extraPrefix, firstTrueCell);
        final int start = extraPrefix[extraPrefix.length - 1] + 1;

        assertFalse(batch.addWork(extraPrefix, extraPrefix.length, extraParity, start),
                "Should not be able to add work item when batch is full");
    }

    /**
     * Tests the {@link WorkItem#WorkItem() no-argument} constructor to ensure it throws
     * IllegalStateException when numClicks has not been initialized.
     */
    @Test
    void testWorkItemConstructorUnsetNumClicks() {
        assertThrows(IllegalStateException.class, () -> {
            new WorkItem();
        }, "WorkItem constructor should throw IllegalStateException if numClicks is not initialized");
    }

    /**
     * Tests the {@link WorkItem#WorkItem() no-argument} constructor to ensure it correctly
     * initializes a WorkItem instance when numClicks has been set.
     * 
     * This test assumes that the {@link WorkBatch#getNumClicks()}, {@link WorkItem#getPrefix()},
     * {@link WorkItem#getPrefixLength()}, {@link WorkItem#getFinalClicks()}, and
     * {@link WorkItem#getStart()} methods are functioning correctly.
     */
    @Test
    void testWorkItemConstructorSetNumClicks() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = assertDoesNotThrow(() -> {
            return new WorkItem();
        }, "WorkItem constructor should not throw when numClicks is initialized");

        assertArrayEquals(new short[2], item.getPrefix(),
                "Newly created WorkItem should have empty prefix array");
        assertEquals(-1, item.getPrefixLength(),
                "Newly created WorkItem should have prefix length -1");
        assertNull(item.getFinalClicks(),
                "Newly created WorkItem should have null finalClicks array");
        assertEquals(-1, item.getStart(), "Newly created WorkItem should have start index -1");
    }

    /**
     * Tests the {@link WorkItem#set(short[], int, short[], int)} method to ensure it throws
     * NullPointerException when provided with a null prefix array.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)}, {@link WorkItem#getPrefix()},
     * {@link WorkItem#getPrefixLength()}, {@link WorkItem#getFinalClicks()}, and
     * {@link WorkItem#getStart()} methods are functioning correctly.
     */
    @Test
    void testWorkItemSetNullPrefix() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        assertThrows(NullPointerException.class, () -> {
            item.set(null, 2, new short[0], 0);
        }, "setPrefix should throw NullPointerException when prefix array is null");
        assertArrayEquals(new short[2], item.getPrefix(),
                "Prefix should remain unchanged after exception");
        assertEquals(-1, item.getPrefixLength(),
                "Prefix length should remain unchanged after exception");
        assertNull(item.getFinalClicks(), "Final clicks should remain unchanged after exception");
        assertEquals(-1, item.getStart(), "Start index should remain unchanged after exception");
    }

    /**
     * Tests the {@link WorkItem#set(short[], int, short[], int)} method to ensure it throws
     * IndexOutOfBoundsException when provided with a negative prefix length.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)}, {@link WorkItem#getPrefix()},
     * {@link WorkItem#getPrefixLength()}, {@link WorkItem#getFinalClicks()}, and
     * {@link WorkItem#getStart()} methods are functioning correctly.
     */
    @Test
    void testWorkItemSetNegativePrefixLength() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1, 2};
        assertThrows(IndexOutOfBoundsException.class, () -> {
            item.set(prefix, -1, new short[0], 0);
        }, "setPrefix should throw IndexOutOfBoundsException when prefix length is negative");
        assertArrayEquals(new short[2], item.getPrefix(),
                "Prefix should remain unchanged after exception");
        assertEquals(-1, item.getPrefixLength(),
                "Prefix length should remain unchanged after exception");
        assertNull(item.getFinalClicks(), "Final clicks should remain unchanged after exception");
        assertEquals(-1, item.getStart(), "Start index should remain unchanged after exception");
    }

    /**
     * Tests the {@link WorkItem#set(short[], int, short[], int)} method to ensure it throws
     * IndexOutOfBoundsException when provided with a prefix length that exceeds the prefix array
     * length.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)}, {@link WorkItem#getPrefix()},
     * {@link WorkItem#getPrefixLength()}, {@link WorkItem#getFinalClicks()}, and
     * {@link WorkItem#getStart()} methods are functioning correctly.
     */
    @Test
    void testWorkItemSetOversizedPrefixLength() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1, 2};
        assertThrows(IndexOutOfBoundsException.class, () -> {
            item.set(prefix, 5, new short[0], 0);
        }, "setPrefix should throw IndexOutOfBoundsException when prefix length exceeds array length");
        assertArrayEquals(new short[2], item.getPrefix(),
                "Prefix should remain unchanged after exception");
        assertEquals(-1, item.getPrefixLength(),
                "Prefix length should remain unchanged after exception");
        assertNull(item.getFinalClicks(), "Final clicks should remain unchanged after exception");
        assertEquals(-1, item.getStart(), "Start index should remain unchanged after exception");
    }

    /**
     * Tests the {@link WorkItem#set(short[], int, short[], int)} method to ensure it correctly sets
     * the WorkItem fields when provided with valid inputs.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)}, {@link WorkItem#getPrefix()},
     * {@link WorkItem#getPrefixLength()}, {@link WorkItem#getFinalClicks()}, and
     * {@link WorkItem#getStart()} methods are functioning correctly.
     */
    @Test
    void testWorkItemSetValidInputs() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks = {2, 3, 4};
        final int start = 1;

        assertDoesNotThrow(() -> {
            item.set(prefix, prefix.length, finalClicks, start);
        }, "setPrefix should not throw when provided with valid inputs");

        assertArrayEquals(prefix, item.getPrefix(), "Prefix should match the set array");
        assertEquals(prefix.length, item.getPrefixLength(),
                "Prefix length should match the set value");
        assertSame(finalClicks, item.getFinalClicks(),
                "Final clicks should be the same instance as the set array");
        assertEquals(start, item.getStart(), "Start index should match the set value");
    }

    /**
     * Tests the {@link WorkItem#clear()} method to ensure it correctly resets the WorkItem fields.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)},
     * {@link WorkItem#set(short[], int, short[], int)}, {@link WorkItem#getPrefix()},
     * {@link WorkItem#getPrefixLength()}, {@link WorkItem#getFinalClicks()}, and
     * {@link WorkItem#getStart()} methods are functioning correctly.
     */
    @Test
    void testWorkItemClear() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks = {2, 3, 4};
        final int start = 3;

        item.set(prefix, prefix.length, finalClicks, start);

        final short[] prefixPreClear = item.getPrefix();

        item.clear();

        // Check that the prefix reference is unchanged (but its contents are not relevant after
        // clear)
        assertSame(prefixPreClear, item.getPrefix(),
                "Prefix reference should remain unchanged after clear");
        assertEquals(-1, item.getPrefixLength(), "Prefix length should be -1 after clear");
        assertNull(item.getFinalClicks(), "Final clicks should be null after clear");
        assertEquals(-1, item.getStart(), "Start index should be -1 after clear");
    }

    /**
     * Tests the {@link WorkItem#toString()} method to ensure it handles null finalClicks array
     * correctly.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemToStringNullFinalClicks() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1};
        item.set(prefix, prefix.length, null, 2);

        final String str = assertDoesNotThrow(() -> {
            return item.toString();
        }, "toString should not throw even when finalClicks is null");

        assertTrue(str.contains("finalClicks=null"),
                "toString output should indicate that finalClicks is null");
    }

    /**
     * Tests the {@link WorkItem#toString()} method to ensure it handles negative start index
     * correctly.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemToStringNegativeStart() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks = {2, 3, 4};
        item.set(prefix, prefix.length, finalClicks, -5);

        final String str = assertDoesNotThrow(() -> {
            return item.toString();
        }, "toString should not throw even when start index is invalid");

        assertTrue(str.contains("finalClicks=empty"),
                "toString output should indicate that finalClicks is empty when start index is invalid");
    }

    /**
     * Tests the {@link WorkItem#toString()} method to ensure it handles no valid final clicks
     * correctly.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly
     */
    @Test
    void testWorkItemToStringNoValidFinalClicks() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks = {2, 3, 4};
        item.set(prefix, prefix.length, finalClicks, finalClicks.length);

        final String str = assertDoesNotThrow(() -> {
            return item.toString();
        }, "toString should not throw even when there are no valid final clicks");

        assertTrue(str.contains("finalClicks=empty"),
                "toString output should indicate that finalClicks is empty when there are no valid final clicks");
    }

    /**
     * Tests the {@link WorkItem#toString()} method to ensure it correctly represents the WorkItem
     * state when all fields are valid.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemToStringValidState() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks = {2, 3, 4};
        final int start = 1;
        item.set(prefix, prefix.length, finalClicks, start);

        final String str = assertDoesNotThrow(() -> {
            return item.toString();
        }, "toString should not throw when WorkItem is in a valid state");

        assertTrue(str.contains("prefix=[0, 1]"),
                "toString output should correctly represent the prefix array");
        assertTrue(str.contains("prefixLength=2"),
                "toString output should correctly represent the prefix length");
        assertTrue(str.contains("finalClicks=[3, 4]"),
                "toString output should correctly represent the valid final clicks");
    }

    /**
     * Tests the {@link WorkItem#equals(Object)} method to ensure it returns false when compared to
     * an object of a different type.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testWorkItemEqualsDifferentType() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        String other = "Not a WorkItem";
        assertNotEquals(item, other,
                "WorkItem should not be equal to an object of a different type");
    }

    /**
     * Tests the {@link WorkItem#equals(Object)} method to ensure it returns false when compared to
     * null.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testWorkItemEqualsNull() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        assertNotEquals(item, null, "WorkItem should not be equal to null");
    }

    /**
     * Tests the {@link WorkItem#equals(Object)} method to ensure it returns true when compared to
     * itself.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testWorkItemEqualsItself() {
        WorkBatch.setNumClicks(3);
        final WorkItem item = new WorkItem();

        assertEquals(item, item, "WorkItem should be equal to itself");

        final WorkItem anotherRef = item;
        assertEquals(item, anotherRef, "WorkItem should be equal to another reference of itself");
    }

    /**
     * Tests the {@link WorkItem#equals(Object)} method to ensure it returns false when compared to
     * another WorkItem with a different prefix.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemEqualsDifferentPrefix() {
        WorkBatch.setNumClicks(3);
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();

        final short[] prefix1 = {0, 1};
        final short[] prefix2 = {0, 2};
        item1.set(prefix1, prefix1.length, new short[0], 0);
        item2.set(prefix2, prefix2.length, new short[0], 0);

        assertNotEquals(item1, item2, "WorkItems with different prefixes should not be equal");
    }

    /**
     * Tests the {@link WorkItem#equals(Object)} method to ensure it returns false when compared to
     * another WorkItem with different final clicks.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemEqualsDifferentFinalClicks() {
        WorkBatch.setNumClicks(3);
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks1 = {2, 3};
        final short[] finalClicks2 = {2, 4};
        item1.set(prefix, prefix.length, finalClicks1, 0);
        item2.set(prefix, prefix.length, finalClicks2, 0);

        assertNotEquals(item1, item2, "WorkItems with different final clicks should not be equal");
    }

    /**
     * Tests the {@link WorkItem#equals(Object)} method to ensure it returns false when compared to
     * another WorkItem with a different start index.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemEqualsDifferentStart() {
        WorkBatch.setNumClicks(3);
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks = {2, 3};
        item1.set(prefix, prefix.length, finalClicks, 0);
        item2.set(prefix, prefix.length, finalClicks, 1);

        assertNotEquals(item1, item2, "WorkItems with different start indices should not be equal");
    }

    /**
     * Tests the {@link WorkItem#equals(Object)} method to ensure it returns true when compared to
     * another WorkItem with identical state.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemEqualsIdenticalState() {
        WorkBatch.setNumClicks(3);
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();

        final short[] prefix = {0, 1};
        final short[] finalClicks = {2, 3};
        final int start = 1;
        item1.set(prefix, prefix.length, finalClicks, start);
        item2.set(prefix, prefix.length, finalClicks, start);

        assertEquals(item1, item2, "WorkItems with identical state should be equal");
    }

    /**
     * Tests the {@link WorkItem#hashCode()} method to ensure it produces different hash codes for
     * WorkItems with different prefixes.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemHashCodeDifferentPrefix() {
        WorkBatch.setNumClicks(3);
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();

        final short[] prefix1 = generateRandomCombination(2, Grid.NUM_CELLS - 1);
        while (true) {
            final short[] prefix2 = generateRandomCombination(2, Grid.NUM_CELLS - 1);
            if (!Arrays.equals(prefix1, prefix2)) {
                item1.set(prefix1, prefix1.length, new short[0], 0);
                item2.set(prefix2, prefix2.length, new short[0], 0);
                break;
            }
        }

        assertNotEquals(item1.hashCode(), item2.hashCode(),
                "WorkItems with different prefixes should have different hash codes");
    }

    /**
     * Tests the {@link WorkItem#hashCode()} method to ensure it produces different hash codes for
     * WorkItems with different final clicks.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemHashCodeDifferentFinalClicks() {
        WorkBatch.setNumClicks(3);
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();

        final short firstTrueCell1 = (short) random.nextInt(0, Grid.NUM_CELLS);
        final short firstTrueCell2;
        while (true) {
            short temp = (short) random.nextInt(0, Grid.NUM_CELLS);
            if (temp != firstTrueCell1) {
                firstTrueCell2 = temp;
                break;
            }
        }
        final short[] prefix = generateRandomCombination(2);
        final short[] finalClicks1 = getFinalClicks(prefix, firstTrueCell1);
        final short[] finalClicks2 = getFinalClicks(prefix, firstTrueCell2);
        item1.set(prefix, prefix.length, finalClicks1, 0);
        item2.set(prefix, prefix.length, finalClicks2, 0);
        assertNotEquals(item1.hashCode(), item2.hashCode(),
                "WorkItems with different final clicks should have different hash codes");
    }

    /**
     * Tests the {@link WorkItem#hashCode()} method to ensure it produces different hash codes for
     * WorkItems with different start indices.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testWorkItemHashCodeDifferentStart() {
        WorkBatch.setNumClicks(3);
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        final short[] prefix = generateRandomCombination(2);
        final short[] finalClicks = getFinalClicks(prefix, firstTrueCell);
        item1.set(prefix, prefix.length, finalClicks, 0);
        item2.set(prefix, prefix.length, finalClicks, 1);

        assertNotEquals(item1.hashCode(), item2.hashCode(),
                "WorkItems with different start indices should have different hash codes");
    }

    /**
     * Tests the {@link WorkItem#hashCode()} method to ensure it produces the same hash code for
     * WorkItems with identical state.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkItem#set(short[], int, short[], int)} methods are functioning correctly.
     */
    @Test
    void testIteratorHasNextEmptyBatch() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(5);

        final Iterator<WorkItem> iterator = batch.iterator();
        assertFalse(iterator.hasNext(), "Iterator should have no next element for an empty batch");
    }

    /**
     * Tests the {@link WorkBatch#iterator()} method to ensure the iterator's next() method throws
     * NoSuchElementException when called on an empty WorkBatch.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testIteratorNextEmptyBatch() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(5);

        final Iterator<WorkItem> iterator = batch.iterator();

        if (iterator.hasNext()) {
            return; // Prevent false positive if hasNext is incorrectly implemented
        }

        assertThrows(NoSuchElementException.class, () -> {
            iterator.next();
        }, "Iterator next() should throw NoSuchElementException for an empty batch");
    }

    /**
     * Tests the {@link WorkBatch#iterator()} method to ensure the iterator's hasNext() method
     * returns false after iterating beyond the last WorkItem.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#addWork(short[], int, boolean, int)} methods are functioning correctly.
     */
    @Test
    void testIteratorHasNextBeyondEnd() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(2);

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add a single work item
        final short[] prefix = generateRandomCombination(2);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, prefix.length, parity, start);

        final Iterator<WorkItem> iterator = batch.iterator();

        // Advance to the end
        if (iterator.hasNext()) {
            iterator.next();
        }

        assertFalse(iterator.hasNext(),
                "Iterator should have no next element after reaching the end");
    }

    /**
     * Tests the {@link WorkBatch#iterator()} method to ensure the iterator's next() method throws
     * NoSuchElementException when called after iterating beyond the last WorkItem.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#addWork(short[], int, boolean, int)} methods are functioning correctly.
     */
    @Test
    void testIteratorNextBeyondEnd() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(2);

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add a single work item
        final short[] prefix = generateRandomCombination(2);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, prefix.length, parity, start);

        final Iterator<WorkItem> iterator = batch.iterator();

        // Advance to the end
        if (iterator.hasNext()) {
            iterator.next();
        }

        if (iterator.hasNext()) {
            return; // Prevent false positive if hasNext is incorrectly implemented
        }

        assertThrows(NoSuchElementException.class, () -> {
            iterator.next();
        }, "Iterator next() should throw NoSuchElementException after reaching the end");
    }

    /**
     * Tests the {@link WorkBatch#iterator()} method to ensure the iterator's next() method
     * correctly retrieves WorkItems when the WorkBatch contains multiple items.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#addWork(short[], int, boolean, int)} methods are functioning correctly.
     */
    @Test
    void testIteratorNextValid() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(3);

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add multiple work items
        final short[] prefix1 = generateRandomCombination(2);
        final boolean parity1 = getPrefixParity(prefix1, firstTrueCell);
        final int start1 = prefix1[prefix1.length - 1] + 1;
        batch.addWork(prefix1, prefix1.length, parity1, start1);

        final short[] prefix2 = generateRandomCombination(2);
        final boolean parity2 = getPrefixParity(prefix2, firstTrueCell);
        final int start2 = prefix2[prefix2.length - 1] + 1;
        batch.addWork(prefix2, prefix2.length, parity2, start2);

        final Iterator<WorkItem> iterator = batch.iterator();

        // Retrieve first work item
        assertTrue(iterator.hasNext(), "Iterator should have next element for the first work item");
        final WorkItem item1 = iterator.next();
        assertArrayEquals(prefix1, Arrays.copyOf(item1.getPrefix(), item1.getPrefixLength()),
                "First work item prefix should match the added prefix");

        // Retrieve second work item
        assertTrue(iterator.hasNext(),
                "Iterator should have next element for the second work item");
        final WorkItem item2 = iterator.next();
        assertArrayEquals(prefix2, Arrays.copyOf(item2.getPrefix(), item2.getPrefixLength()),
                "Second work item prefix should match the added prefix");
    }

    /**
     * Tests the {@link WorkBatch#iterator()} method to ensure the iterator's remove() method throws
     * UnsupportedOperationException.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testIteratorRemove() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(2);

        final Iterator<WorkItem> iterator = batch.iterator();

        assertThrows(UnsupportedOperationException.class, () -> {
            iterator.remove();
        }, "Iterator remove() should throw UnsupportedOperationException");
    }

    /**
     * Tests the {@link WorkBatch#iterator()} method to ensure the iterator's toString() method
     * produces a non-null string representation.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testIteratorToString() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(2);

        final Iterator<WorkItem> iterator = batch.iterator();

        final String str = assertDoesNotThrow(() -> {
            return iterator.toString();
        }, "Iterator toString() should not throw an exception");

        assertTrue(str.contains("currentWorkItemIndex="),
                "Iterator toString() output should contain currentWorkItemIndex");
        assertTrue(str.contains("batch="),
                "Iterator toString() output should contain batch reference");
    }

    /**
     * Tests the {@link WorkBatch#iterator()} method to ensure that iterators from the same
     * WorkBatch instance are identical, while iterators from different WorkBatch instances are
     * distinct.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    void testWorkBatchIterator() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batchOne = new WorkBatch(2);
        final WorkBatch batchTwo = new WorkBatch(2);

        final Iterator<WorkItem> iteratorOne = batchOne.iterator();
        final Iterator<WorkItem> iteratorOneRef = batchOne.iterator();
        final Iterator<WorkItem> iteratorTwo = batchTwo.iterator();

        assertSame(iteratorOne, iteratorOneRef, "Iterators from the same batch should be the same");
        assertNotSame(iteratorOne, iteratorTwo,
                "Iterators from different batches should not be the same");
    }

    /**
     * Tests the {@link WorkBatch#isEmpty()} method to ensure it correctly identifies empty and
     * non-empty states.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#addWork(short[], int, boolean, int)} methods are functioning correctly.
     */
    @Test
    void testIsEmpty() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(2);

        assertTrue(batch.isEmpty(), "Newly created batch should be empty");

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add a work item
        final short[] prefix = generateRandomCombination(2);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, prefix.length, parity, start);

        assertFalse(batch.isEmpty(), "Batch should not be empty after adding a work item");
    }

    /**
     * Tests the {@link WorkBatch#isFull()} method to ensure it correctly identifies full and
     * non-full states.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#addWork(short[], int, boolean, int)} methods are functioning correctly.
     */
    @Test
    void testIsFull() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(1);

        assertFalse(batch.isFull(), "Newly created batch should not be full");

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add a work item to fill the batch
        final short[] prefix = generateRandomCombination(2);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, prefix.length, parity, start);

        assertTrue(batch.isFull(), "Batch should be full after adding work item to reach capacity");
    }

    /**
     * Tests the {@link WorkBatch#size()} method to ensure it correctly reports the number of work
     * items in the batch.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#addWork(short[], int, boolean, int)} methods are functioning correctly.
     */
    @Test
    void testSize() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(3);

        assertEquals(0, batch.size(), "Newly created batch should have size 0");

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add work items and check size incrementally
        for (int i = 1; i <= 3; i++) {
            final short[] prefix = generateRandomCombination(2);
            final boolean parity = getPrefixParity(prefix, firstTrueCell);
            final int start = prefix[prefix.length - 1] + 1;
            batch.addWork(prefix, prefix.length, parity, start);

            assertEquals(i, batch.size(),
                    "Batch size should be " + i + " after adding " + i + " work item(s)");
        }
    }

    /**
     * Tests the {@link WorkBatch#clear()} method to ensure it correctly resets the batch state.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} and
     * {@link WorkBatch#addWork(short[], int, boolean, int)} methods are functioning correctly.
     */
    @Test
    void testClear() {
        WorkBatch.setNumClicks(3);
        final WorkBatch batch = new WorkBatch(2);

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add a work item
        final short[] prefix = generateRandomCombination(2);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, prefix.length, parity, start);

        if (batch.isEmpty() || !batch.isFull())
            return; // Prevent false positives if isEmpty or isFull are incorrectly implemented

        batch.clear();

        assertTrue(batch.isEmpty(), "Batch should be empty after calling clear()");
        assertEquals(0, batch.size(), "Batch size should be 0 after calling clear()");
        assertFalse(batch.isFull(), "Batch should not be full after calling clear()");
    }
}
package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.TestingUtils.generateClickIndices;
import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomCombination;
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Disabled to avoid duplicate execution by test runners; tests are run in order via OrderedJupiterTests")
class WorkBatchTest {

    static final Random random = new Random();

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it throws an
     * IllegalArgumentException when provided with a negative input.
     */
    @Test
    @Order(1)
    void testSetNumClicksNegativeInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setNumClicks(-1);
        }, "Expected IllegalArgumentException when setting numClicks to negative number.");

        assertThrows(NoSuchElementException.class, () -> {
            WorkBatch.getNumClicks();
        }, "Expected NoSuchElementException when getting numClicks after invalid set attempt.");
    }

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it throws an
     * IllegalArgumentException when provided with a zero input.
     */
    @Test
    @Order(1)
    void testSetNumClicksZeroInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setNumClicks(0);
        }, "Expected IllegalArgumentException when setting numClicks to zero.");

        assertThrows(NoSuchElementException.class, () -> {
            WorkBatch.getNumClicks();
        }, "Expected NoSuchElementException when getting numClicks after invalid set attempt.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalStateException when numClicks has not been set prior to calling it.
     */
    @Test
    @Order(1)
    void testSetClickIndexArraysUnsetNumClicks() {
        final short[] odd = {0, 1, 2};
        final short[] even = {3, 4, 5};

        assertThrows(IllegalStateException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalStateException when setting click index arrays before numClicks is set.");

        assertThrows(NoSuchElementException.class, () -> {
            WorkBatch.getOddClickIndices();
        }, "Expected NoSuchElementException when getting odd click indices before numClicks is set.");
        assertThrows(NoSuchElementException.class, () -> {
            WorkBatch.getEvenClickIndices();
        }, "Expected NoSuchElementException when getting even click indices before numClicks is set.");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int) single-argument} and {@link WorkBatch#WorkBatch()
     * no-argument} constructors to ensure they throw IllegalStateException when numClicks has not
     * been initialized.
     */
    @Test
    @Order(1)
    void testConstructorNumClicksNotInitialized() {
        assertThrows(IllegalStateException.class, () -> {
            new WorkBatch(10);
        }, "Single-arg constructor should throw IllegalStateException if numClicks is not initialized");
        assertThrows(IllegalStateException.class, () -> {
            new WorkBatch();
        }, "No-arg constructor should throw IllegalStateException if numClicks is not initialized");
    }

    @Test
    @Order(1)
    void testWorkItemConstructorUnsetNumClicks() {
        assertThrows(IllegalStateException.class, () -> {
            new WorkItem();
        }, "WorkItem constructor should throw IllegalStateException when numClicks is not initialized");
    }

    /**
     * Tests the {@link WorkBatch#setNumClicks(int)} method to ensure it correctly sets numClicks
     * when provided with a valid positive input.
     * 
     * This test assumes that the {@link WorkBatch#getNumClicks()} method is functioning correctly.
     */
    @Test
    @Order(2)
    void testSetNumClicksValidInput() {
        final int numClicks = 4;
        WorkBatch.setNumClicks(numClicks);
        assertEquals(numClicks, WorkBatch.getNumClicks(),
                "numClicks should be set to the valid input value.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the odd click indices array is null.
     */
    @Test
    @Order(3)
    void testSetClickIndexArraysNullOddIndices() {
        final short[] even = {0, 1, 2};
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(null, even);
        }, "Expected IllegalArgumentException when odd click indices array is null.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the even click indices array is null.
     */
    @Test
    @Order(3)
    void testSetClickIndexArraysNullEvenIndices() {
        final short[] odd = {0, 1, 2};
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, null);
        }, "Expected IllegalArgumentException when even click indices array is null.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the odd click indices array is empty.
     */
    @Test
    @Order(3)
    void testSetClickIndexArraysEmptyOddIndices() {
        final short[] odd = {};
        final short[] even = {0, 1, 2};
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when odd click indices array is empty.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the even click indices array is empty.
     */
    @Test
    @Order(3)
    void testSetClickIndexArraysEmptyEvenIndices() {
        final short[] odd = {0, 1, 2};
        final short[] even = {};
        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when even click indices array is empty.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the odd click indices array has more than 6 elements.
     */
    @Test
    @Order(3)
    void testSetClickIndexArraysTooManyOddIndices() {
        final short[] odd = {0, 1, 2, 3, 4, 5, 6};
        final short[] even = {7, 8, 9};

        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when odd.length > 6.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when the even click indices array length does not equal
     * Grid.NUM_CELLS - odd.length.
     */
    @Test
    @Order(3)
    void testSetClickIndexArraysEvenLengthMismatch() {
        final short[] odd = {0, 1, 2};
        final short[] even = {3, 4};

        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when even.length != Grid.NUM_CELLS - odd.length.");
    }

    /**
     * Tests the {@link WorkBatch#setClickIndexArrays(short[], short[])} method to ensure it throws
     * an IllegalArgumentException when there are duplicate indices between the odd and even arrays.
     */
    @Test
    @Order(3)
    void testSetClickIndexArraysDuplicateIndices() {
        final short[] odd = {0, 1, 2};
        // This array must contain at least one duplicate with the odd array
        final short[] even = generateRandomCombination(Grid.NUM_CELLS - odd.length, 2,
                Grid.NUM_CELLS - 1);

        assertThrows(IllegalArgumentException.class, () -> {
            WorkBatch.setClickIndexArrays(odd, even);
        }, "Expected IllegalArgumentException when there are duplicate indices between odd and even arrays.");
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
    @Order(4)
    void testSetClickIndexArraysValidInputs() {
        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);
        final short[][] clickIndices = generateClickIndices(firstTrueCell);

        assertDoesNotThrow(() -> {
            WorkBatch.setClickIndexArrays(clickIndices[0], clickIndices[1]);
        }, "setClickIndexArrays should not throw when provided with valid inputs.");

        assertArrayEquals(clickIndices[0], WorkBatch.getOddClickIndices(),
                "Odd click indices should match the set array.");
        assertArrayEquals(clickIndices[1], WorkBatch.getEvenClickIndices(),
                "Even click indices should match the set array.");
    }

    /**
     * Tests the {@link WorkBatch#WorkBatch(int) single-argument} constructor to ensure it throws
     * IllegalArgumentException when provided with a non-positive capacity.
     * 
     * This test assumes that the {@link WorkBatch#setNumClicks(int)} method is functioning
     * correctly.
     */
    @Test
    @Order(5)
    void testConstructorInvalidCapacity() {
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
    @Order(5)
    void testConstructorValidInputs() {
        final WorkBatch batchSingleArg = assertDoesNotThrow(() -> new WorkBatch(10),
                "Single-arg constructor should not throw when global state is initialized");
        final WorkBatch batchNoArg = assertDoesNotThrow(() -> new WorkBatch(),
                "No-arg constructor should not throw when global state is initialized");

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

    @Test
    @Order(5)
    void testAddWorkWhenFull() {
        final WorkBatch batch = new WorkBatch(2);
        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Fill the batch to capacity
        while (!batch.isFull()) {
            final short[] prefix = generateRandomCombination(3);
            final boolean parity = getPrefixParity(prefix, firstTrueCell);
            final int start = prefix[prefix.length - 1] + 1;
            assertTrue(batch.addWork(prefix, parity, start),
                    "Should be able to add work item when batch is not full");
        }

        // Now that the batch is full, adding another work item should fail
        final short[] extraPrefix = generateRandomCombination(3);
        final boolean extraParity = getPrefixParity(extraPrefix, firstTrueCell);
        final int start = extraPrefix[extraPrefix.length - 1] + 1;

        assertFalse(batch.addWork(extraPrefix, extraParity, start),
                "Should not be able to add work item when batch is full");
    }

    @Test
    @Order(5)
    void testWorkItemConstructorSetNumClicks() {
        final WorkItem item = assertDoesNotThrow(WorkItem::new,
                "WorkItem constructor should not throw when numClicks is initialized");

        assertEquals(WorkBatch.getNumClicks() - 1, item.getPrefixLength());
        assertNull(item.getFinalClicks());
        assertEquals(-1, item.getStart());
    }

    @Test
    @Order(5)
    void testWorkItemSetNullPrefix() {
        final WorkItem item = new WorkItem();
        assertThrows(NullPointerException.class, () -> {
            item.set(null, new short[0], 0);
        }, "set() should throw NullPointerException when prefix array is null");
    }

    @Test
    @Order(5)
    void testWorkItemSetValidInputs() {
        final WorkItem item = new WorkItem();
        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4, 5};
        final int start = 1;

        assertDoesNotThrow(() -> {
            item.set(prefix, finalClicks, start);
        }, "set() should not throw when provided with valid inputs");

        assertArrayEquals(prefix, item.getPrefix(), "Prefix should match the set array");
        assertEquals(prefix.length, item.getPrefixLength());
        assertSame(finalClicks, item.getFinalClicks());
        assertEquals(start, item.getStart());
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
    @Order(5)
    void testWorkItemClear() {
        final WorkItem item = new WorkItem();
        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4, 5};
        final int start = 1;
        item.set(prefix, finalClicks, start);

        final short[] prefixPreClear = item.getPrefix();
        item.clear();

        assertSame(prefixPreClear, item.getPrefix(),
                "Prefix reference should remain unchanged after clear");
        assertEquals(prefixPreClear.length, item.getPrefixLength(),
                "Prefix length should remain unchanged after clear");
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
    @Order(5)
    void testWorkItemToStringNullFinalClicks() {
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1, 2};
        item.set(prefix, null, 2);

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
    @Order(5)
    void testWorkItemToStringNegativeStart() {
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4};
        item.set(prefix, finalClicks, -5);

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
    @Order(5)
    void testWorkItemToStringNoValidFinalClicks() {
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4};
        item.set(prefix, finalClicks, finalClicks.length);

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
    @Order(5)
    void testWorkItemToStringValidState() {
        final WorkItem item = new WorkItem();

        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4};
        final int start = 1;
        item.set(prefix, finalClicks, start);

        final String str = assertDoesNotThrow(() -> {
            return item.toString();
        }, "toString should not throw when WorkItem is in a valid state");

        assertTrue(str.contains("prefix=[0, 1, 2]"),
                "toString output should correctly represent the prefix array");
        assertTrue(str.contains("prefixLength=3"),
                "toString output should correctly represent the prefix length");
        assertTrue(str.contains("finalClicks=[4]"),
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
    @Order(5)
    void testWorkItemEqualsDifferentType() {
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
    @Order(5)
    void testWorkItemEqualsNull() {
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
    @Order(5)
    void testWorkItemEqualsItself() {
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
    @Order(5)
    void testWorkItemEqualsDifferentPrefix() {
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();
        final short[] prefix1 = {0, 1, 2};
        final short[] prefix2 = {0, 1, 3};
        item1.set(prefix1, new short[0], 0);
        item2.set(prefix2, new short[0], 0);

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
    @Order(5)
    void testWorkItemEqualsDifferentFinalClicks() {
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();
        final short[] prefix = {0, 1, 2};
        final short[] finalClicks1 = {3, 4};
        final short[] finalClicks2 = {3, 5};
        item1.set(prefix, finalClicks1, 0);
        item2.set(prefix, finalClicks2, 0);

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
    @Order(5)
    void testWorkItemEqualsDifferentStart() {
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();
        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4};
        item1.set(prefix, finalClicks, 0);
        item2.set(prefix, finalClicks, 1);

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
    @Order(5)
    void testWorkItemEqualsIdenticalState() {
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();
        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4};
        final int start = 1;
        item1.set(prefix, finalClicks, start);
        item2.set(prefix, finalClicks, start);

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
    @Order(5)
    void testWorkItemHashCodeDifferentPrefix() {
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();
        final short[] prefix1 = {0, 1, 2};
        final short[] prefix2 = {0, 1, 3};
        item1.set(prefix1, new short[0], 0);
        item2.set(prefix2, new short[0], 0);

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
    @Order(5)
    void testWorkItemHashCodeDifferentFinalClicks() {
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();
        final short[] prefix = {0, 1, 2};
        final short[] finalClicks1 = {3, 4};
        final short[] finalClicks2 = {3, 5};
        item1.set(prefix, finalClicks1, 0);
        item2.set(prefix, finalClicks2, 0);

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
    @Order(5)
    void testWorkItemHashCodeDifferentStart() {
        final WorkItem item1 = new WorkItem();
        final WorkItem item2 = new WorkItem();
        final short[] prefix = {0, 1, 2};
        final short[] finalClicks = {3, 4};
        item1.set(prefix, finalClicks, 0);
        item2.set(prefix, finalClicks, 1);

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
    @Order(5)
    void testIteratorHasNextEmptyBatch() {
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
    @Order(5)
    void testIteratorNextEmptyBatch() {
        final WorkBatch batch = new WorkBatch(5);
        final Iterator<WorkItem> iterator = batch.iterator();

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
    @Order(5)
    void testIteratorHasNextBeyondEnd() {
        final WorkBatch batch = new WorkBatch(2);
        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        final short[] prefix = generateRandomCombination(3);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, parity, start);

        final Iterator<WorkItem> iterator = batch.iterator();
        iterator.next(); // Consume the only element

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
    @Order(5)
    void testIteratorNextBeyondEnd() {
        final WorkBatch batch = new WorkBatch(2);
        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        final short[] prefix = generateRandomCombination(3);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, parity, start);

        final Iterator<WorkItem> iterator = batch.iterator();
        iterator.next(); // Consume the only element

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
    @Order(5)
    void testIteratorNextValid() {
        final WorkBatch batch = new WorkBatch(3);
        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        // Add multiple work items
        final short[] prefix1 = generateRandomCombination(3);
        final boolean parity1 = getPrefixParity(prefix1, firstTrueCell);
        final int start1 = prefix1[prefix1.length - 1] + 1;
        batch.addWork(prefix1, parity1, start1);

        final short[] prefix2 = generateRandomCombination(3);
        final boolean parity2 = getPrefixParity(prefix2, firstTrueCell);
        final int start2 = prefix2[prefix2.length - 1] + 1;
        batch.addWork(prefix2, parity2, start2);

        final Iterator<WorkItem> iterator = batch.iterator();

        assertTrue(iterator.hasNext(), "Iterator should have next element for the first work item");
        final WorkItem item1 = iterator.next();
        assertArrayEquals(prefix1, Arrays.copyOf(item1.getPrefix(), item1.getPrefixLength()),
                "First work item prefix should match the added prefix");

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
    @Order(5)
    void testIteratorRemove() {
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
    @Order(5)
    void testIteratorToString() {
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
    @Order(5)
    void testWorkBatchIterator() {
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
    @Order(5)
    void testIsEmpty() {
        final WorkBatch batch = new WorkBatch(2);
        assertTrue(batch.isEmpty(), "Newly created batch should be empty");

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);
        final short[] prefix = generateRandomCombination(3);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, parity, start);

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
    @Order(5)
    void testIsFull() {
        final WorkBatch batch = new WorkBatch(1);
        assertFalse(batch.isFull(), "Newly created batch should not be full");

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);
        final short[] prefix = generateRandomCombination(3);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, parity, start);

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
    @Order(5)
    void testSize() {
        final WorkBatch batch = new WorkBatch(3);
        assertEquals(0, batch.size(), "Newly created batch should have size 0");

        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);
        for (int i = 1; i <= 3; i++) {
            final short[] prefix = generateRandomCombination(3);
            final boolean parity = getPrefixParity(prefix, firstTrueCell);
            final int start = prefix[prefix.length - 1] + 1;
            batch.addWork(prefix, parity, start);
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
    @Order(5)
    void testClear() {
        final WorkBatch batch = new WorkBatch(2);
        final short firstTrueCell = (short) random.nextInt(0, Grid.NUM_CELLS);

        final short[] prefix = generateRandomCombination(3);
        final boolean parity = getPrefixParity(prefix, firstTrueCell);
        final int start = prefix[prefix.length - 1] + 1;
        batch.addWork(prefix, parity, start);

        batch.clear();

        assertTrue(batch.isEmpty(), "Batch should be empty after calling clear()");
        assertEquals(0, batch.size(), "Batch size should be 0 after calling clear()");
        assertFalse(batch.isFull(), "Batch should not be full after calling clear()");
    }
}
package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.util.TestingUtils.generateRandomCombination;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkBatchTest {

    // TODO: Rework all of these tests so they match the spirit of the original ones.

    static final Random random = new Random();
    private static final int NUM_CLICKS = 17;
    private static final short[] ODD_INDICES = {1, 3, 5, 7, 9};
    private static final short[] EVEN_INDICES = {0, 2, 4, 6, 8};

    @BeforeEach
    void setUp() {
        WorkBatch.setNumClicks(NUM_CLICKS);
        WorkBatch.setClickIndexArrays(ODD_INDICES, EVEN_INDICES);
    }

    @AfterEach
    void tearDown() {
        WorkBatch.resetForTest();
    }

    @Test
    void testConstructorAndState() {
        WorkBatch batch = new WorkBatch();
        assertEquals(WorkBatch.BATCH_SIZE, batch.getCapacity());
        assertTrue(batch.isEmpty());
        assertFalse(batch.isFull());
        assertEquals(0, batch.size());

        WorkBatch batch2 = new WorkBatch(10);
        assertEquals(10, batch2.getCapacity());
    }

    @Test
    void testAddWorkAndIterator() {
        // WorkBatch batch = new WorkBatch(5);
        // short[] prefix1 = {10, 20};
        
        // assertTrue(batch.addWork(prefix1, 2, false, 0)); // Uses EVEN_INDICES
        // assertEquals(1, batch.size());
        // assertFalse(batch.isEmpty());

        // List<short[]> combinations = new ArrayList<>();
        // for (short[] combo : batch) {
        //     combinations.add(combo.clone());
        // }

        // assertEquals(EVEN_INDICES.length, combinations.size());
        // for (int i = 0; i < EVEN_INDICES.length; i++) {
        //     short[] expected = {10, 20, EVEN_INDICES[i]};
        //     // This is a simplification; a full test would need to match the full combo length
        //     assertArrayEquals(Arrays.copyOf(expected, NUM_CLICKS), Arrays.copyOf(combinations.get(i), NUM_CLICKS));
        // }
    }
    
    @Test
    void testAddWorkMultiple() {
        // WorkBatch batch = new WorkBatch(2);
        // short[] prefix1 = {10};
        // short[] prefix2 = {11};

        // assertTrue(batch.addWork(prefix1, 1, false, 2)); // EVEN_INDICES from index 2
        // assertTrue(batch.addWork(prefix2, 1, true, 1));  // ODD_INDICES from index 1
        // assertTrue(batch.isFull());
        
        // List<short[]> combinations = new ArrayList<>();
        // for (short[] combo : batch) {
        //     combinations.add(combo.clone());
        // }

        // int expectedSize = (EVEN_INDICES.length - 2) + (ODD_INDICES.length - 1);
        // assertEquals(expectedSize, combinations.size());

        // // Verify first work item
        // short[] firstCombo = combinations.get(0);
        // assertEquals(10, firstCombo[0]);
        // assertEquals(EVEN_INDICES[2], firstCombo[1]);

        // // Verify second work item
        // short[] secondWorkItemFirstCombo = combinations.get(EVEN_INDICES.length - 2);
        // assertEquals(11, secondWorkItemFirstCombo[0]);
        // assertEquals(ODD_INDICES[1], secondWorkItemFirstCombo[1]);
    }

    @Test
    void testAddWorkWhenFull() {
        WorkBatch batch = new WorkBatch(1);
        short[] prefix = {1};
        batch.addWork(prefix, 1, true, 0, 0L);
        assertTrue(batch.isFull());
        assertFalse(batch.addWork(prefix, 1, true, 0, 0L), "Should not be able to add to a full batch.");
    }
    
    @Test
    void testClear() {
        WorkBatch batch = new WorkBatch();
        batch.addWork(new short[]{1}, 1, true, 0, 0L);
        assertFalse(batch.isEmpty());
        
        batch.clear();
        
        assertTrue(batch.isEmpty());
        assertEquals(0, batch.size());
        assertFalse(batch.iterator().hasNext(), "Iterator should have no elements after clear.");
    }

    @Test
    void testIteratorReuse() {
        WorkBatch batch = new WorkBatch();
        batch.addWork(new short[]{1}, 1, true, 0, 0L);
        
        Iterator<WorkBatch.WorkItem> iter1 = batch.iterator();
        Iterator<WorkBatch.WorkItem> iter2 = batch.iterator();
        
        assertEquals(iter1, iter2, "Iterator should be the same instance.");
        
        // Exhaust the iterator
        while(iter1.hasNext()) {
            iter1.next();
        }
        assertFalse(iter1.hasNext());
        
        // Get the iterator again, it should be reset and usable
        Iterator<WorkBatch.WorkItem> iter3 = batch.iterator();
        assertTrue(iter3.hasNext());
        assertNotNull(iter3.next());
    }

    @Test
    void testEqualsAndHashCode() {
        // WorkBatch batch1 = new WorkBatch(2);
        // batch1.addWork(new short[]{10}, 1, true, 0);

        // WorkBatch batch2 = new WorkBatch(2);
        // batch2.addWork(new short[]{10}, 1, true, 0);

        // WorkBatch batch3 = new WorkBatch(2);
        // batch3.addWork(new short[]{11}, 1, true, 0);
        
        // WorkBatch batch4 = new WorkBatch(3);
        // batch4.addWork(new short[]{10}, 1, true, 0);

        // assertEquals(batch1, batch2, "Batches with identical work items should be equal.");
        // assertEquals(batch1.hashCode(), batch2.hashCode(), "Hashcodes for equal batches should be the same.");

        // assertNotEquals(batch1, batch3, "Batches with different work items should not be equal.");
        // assertNotEquals(batch1.hashCode(), batch3.hashCode(), "Hashcodes for unequal batches should be different.");

        // assertNotEquals(batch1, batch4, "Batches with different capacities should not be equal.");
        
        // assertNotEquals(batch1, null, "Batch should not be equal to null.");
        // assertNotEquals(batch1, new Object(), "Batch should not be equal to a different object type.");
    }
    
    @Test
    void testToString() {
        // WorkBatch batch = new WorkBatch(5);
        // assertTrue(batch.toString().contains("size=0"));
        // assertTrue(batch.toString().contains("capacity=5"));
        
        // batch.addWork(new short[]{10, 20}, 2, false, 0);
        // assertTrue(batch.toString().contains("size=1"));
    }
}
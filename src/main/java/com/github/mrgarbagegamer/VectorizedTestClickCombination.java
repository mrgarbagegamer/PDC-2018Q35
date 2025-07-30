package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Vectorized version of TestClickCombination using Java 24+ Vector API
 * Optimizes the XOR-heavy satisfiesOddAdjacency operation with SIMD instructions
 */
public class VectorizedTestClickCombination extends Thread 
{
    private static final Logger logger = LogManager.getLogger(VectorizedTestClickCombination.class);
    private static final int LOG_EVERY_N_FAILURES = 100_000;
    
    // Vector API species for 64-bit long operations (AVX2/AVX-512 optimized)
    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;
    private static final int VECTOR_LENGTH = SPECIES.length(); // 4 for AVX2, 8 for AVX-512

    private final CombinationQueue combinationQueue;
    private final CombinationQueueArray queueArray;
    private final Grid puzzleGrid;

    // Vectorized lookup tables - organized for SIMD access patterns
    private static volatile long[][][] VECTORIZED_CLICK_MASKS = null; // [cellIndex][vectorIndex][laneIndex]
    private static volatile LongVector EXPECTED_VECTOR_0;
    private static volatile LongVector EXPECTED_VECTOR_1;
    private static volatile int TRUE_CELLS_LENGTH = 0;
    
    public VectorizedTestClickCombination(String threadName, CombinationQueue combinationQueue, 
                                         CombinationQueueArray queueArray, Grid puzzleGrid) 
    {
        super(threadName);
        this.combinationQueue = combinationQueue;
        this.queueArray = queueArray;
        this.puzzleGrid = puzzleGrid;
        
        // Initialize vectorized lookup table once for all threads
        short[] trueCells = puzzleGrid.findTrueCells(Grid.ValueFormat.Index);
        initializeVectorizedLookupTable(trueCells);
    }

    /**
     * Initialize vectorized lookup tables optimized for SIMD operations
     * Pre-computes masks in vector-friendly layout for maximum throughput
     */
    private static void initializeVectorizedLookupTable(short[] trueCells)
    {
        if (VECTORIZED_CLICK_MASKS == null)
        {
            synchronized (VectorizedTestClickCombination.class)
            {
                if (VECTORIZED_CLICK_MASKS == null)
                {
                    TRUE_CELLS_LENGTH = trueCells.length;
                    
                    // Calculate vector layout: how many vectors needed for all true cells
                    int vectorsNeeded = (trueCells.length + VECTOR_LENGTH - 1) / VECTOR_LENGTH;
                    
                    // Pre-compute vectorized masks: [cellIndex][vectorIndex][laneIndex]
                    long[][][] vectorMasks = new long[Grid.NUM_CELLS][vectorsNeeded][VECTOR_LENGTH];
                    
                    for (short clickCell = 0; clickCell < Grid.NUM_CELLS; clickCell++)
                    {
                        for (int i = 0; i < trueCells.length; i++) 
                        {
                            if (Grid.areAdjacent(trueCells[i], clickCell, Grid.ValueFormat.Index))
                            {
                                int vectorIndex = i / VECTOR_LENGTH;
                                int laneIndex = i % VECTOR_LENGTH;
                                vectorMasks[clickCell][vectorIndex][laneIndex] = 1L;
                            }
                        }
                    }
                    
                    // Create expected result vectors (all 1s for positions with true cells)
                    long[] expectedArray0 = new long[VECTOR_LENGTH];
                    long[] expectedArray1 = new long[VECTOR_LENGTH];
                    
                    // Fill expected vectors based on true cell count
                    int firstVectorCells = Math.min(trueCells.length, VECTOR_LENGTH);
                    for (int i = 0; i < firstVectorCells; i++) {
                        expectedArray0[i] = 1L;
                    }
                    
                    if (trueCells.length > VECTOR_LENGTH) {
                        int secondVectorCells = Math.min(trueCells.length - VECTOR_LENGTH, VECTOR_LENGTH);
                        for (int i = 0; i < secondVectorCells; i++) {
                            expectedArray1[i] = 1L;
                        }
                    }
                    
                    // Atomically publish results
                    EXPECTED_VECTOR_0 = LongVector.fromArray(SPECIES, expectedArray0, 0);
                    EXPECTED_VECTOR_1 = vectorsNeeded > 1 ? 
                        LongVector.fromArray(SPECIES, expectedArray1, 0) : 
                        LongVector.zero(SPECIES);
                    VECTORIZED_CLICK_MASKS = vectorMasks;
                }
            }
        }
    }

    @Override
    public void run()
    {
        int failedCount = 0;
        boolean iSolvedIt = false;
        CombinationQueue[] queues = queueArray.getAllQueues();
        short[] trueCells = puzzleGrid.findTrueCells();

        while (!iSolvedIt && !queueArray.solutionFound)
        {
            WorkBatch workBatch = getWork();

            if (workBatch == null)
            {
                if (queueArray.solutionFound || (queueArray.generationComplete && allQueuesEmpty(queues)))
                {
                    break;
                }
                try { Thread.sleep(1); }
                catch (InterruptedException e) 
                {
                    Thread.currentThread().interrupt();
                    logger.debug("Thread {} interrupted while waiting for work", getName());
                    break;
                }
                continue;
            }
            
            // Process batch with vectorized validation
            short[] combinationClicks;
            while ((combinationClicks = workBatch.poll()) != null && !queueArray.solutionFound)
            {
                if (vectorizedSatisfiesOddAdjacency(combinationClicks, trueCells))
                {
                    puzzleGrid.click(combinationClicks);

                    if (puzzleGrid.isSolved())
                    {
                        logger.info("Found the solution as the following click combination: {}",
                                   new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        queueArray.solutionFound(this.getName(), combinationClicks.clone());
                        
                        triggerGeneratorShutdown();
                        return;
                    }

                    puzzleGrid.initialize();
                    
                    failedCount++;
                    if (failedCount == LOG_EVERY_N_FAILURES)
                    {
                        logger.debug("Tried and failed: {}", new CombinationMessage(combinationClicks.clone(), Grid.ValueFormat.Index));
                        failedCount = 0;
                    }
                }
            }

            queueArray.getWorkBatchPool().offer(workBatch);
        }
    }

    /**
     * SIMD-optimized odd adjacency validation using Vector API
     * Processes multiple true cells simultaneously with vectorized XOR operations
     * 
     * Expected performance: 15-25% faster than scalar version
     */
    private final boolean vectorizedSatisfiesOddAdjacency(short[] combination, short[] trueCells)
    {        
        // Calculate how many vectors we need
        int vectorsNeeded = (TRUE_CELLS_LENGTH + VECTOR_LENGTH - 1) / VECTOR_LENGTH; // TODO: Target this for staticization
        
        // Initialize accumulator vectors
        LongVector accumulator0 = LongVector.zero(SPECIES);
        LongVector accumulator1 = vectorsNeeded > 1 ? LongVector.zero(SPECIES) : null; // TODO: Consider always using two vectors for consistency
        
        // Vectorized XOR accumulation across all clicks
        final int combinationLength = combination.length;
        for (int i = 0; i < combinationLength; i++)
        {
            final int click = combination[i];
            final long[][] clickVectorMasks = VECTORIZED_CLICK_MASKS[click];
            
            // Load and XOR first vector
            LongVector maskVector0 = LongVector.fromArray(SPECIES, clickVectorMasks[0], 0); // TODO: Look at creating the needed LongVectors in initializeVectorizedLookupTable()
            accumulator0 = accumulator0.lanewise(VectorOperators.XOR, maskVector0);
            
            // Load and XOR second vector if needed
            if (accumulator1 != null && vectorsNeeded > 1) {
                LongVector maskVector1 = LongVector.fromArray(SPECIES, clickVectorMasks[1], 0);
                accumulator1 = accumulator1.lanewise(VectorOperators.XOR, maskVector1);
            }
        }
        
        // Compare with expected vectors using SIMD comparison
        var comparison0 = accumulator0.compare(VectorOperators.EQ, EXPECTED_VECTOR_0);
        boolean result = comparison0.allTrue();
        
        if (accumulator1 != null) {
            var comparison1 = accumulator1.compare(VectorOperators.EQ, EXPECTED_VECTOR_1);
            result = result && comparison1.allTrue();
        }
        
        return result;
    }

    // Reuse existing helper methods from base class
    private void triggerGeneratorShutdown()
    {
        ForkJoinPool generatorPool = CombinationGeneratorTask.getForkJoinPool();
        if (generatorPool != null && !generatorPool.isShutdown())
        {
            logger.debug("Triggering generator pool shutdown from {}", getName());
            generatorPool.shutdownNow();
        }
    }

    private WorkBatch getWork()
    {
        WorkBatch batch = combinationQueue.getWorkBatch();
        if (batch != null) return batch;

        CombinationQueue[] queues = queueArray.getAllQueues();
        for (int i = 0; i < queues.length; i++)
        {
            batch = queues[i].getWorkBatch();
            if (batch != null) return batch;
        }
        return null;
    }

    private boolean allQueuesEmpty(CombinationQueue[] queues)
    {
        for (CombinationQueue q : queues)
        {
            if (q.getWorkBatch() != null) return false;
        }
        return true;
    }
}
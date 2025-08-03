package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GranularityController 
{
    private static final Logger logger = LogManager.getLogger(GranularityController.class);

    // Tuned pressure thresholds for observed 300-500 task workload
    private static final double LOW_PRESSURE_THRESHOLD = 
        Double.parseDouble(System.getProperty("granularity.pressure.low", "50.0"));
    private static final double HIGH_PRESSURE_THRESHOLD = 
        Double.parseDouble(System.getProperty("granularity.pressure.high", "200.0"));
    
    // Bounded constraint checking rates [0.2, 0.6]
    private static final double HIGH_PRESSURE_CONSTRAINT_RATE = 0.6;  // More strict
    private static final double LOW_PRESSURE_CONSTRAINT_RATE = 0.2;   // More lenient
    private static final double NORMAL_CONSTRAINT_RATE = 0.4;         // Balanced
    
    public enum PressureLevel { LOW, NORMAL, HIGH }
    
    // Static pressure monitoring since we assess the same pool
    private static volatile PressureLevel cachedPressure = PressureLevel.NORMAL;
    private static final AtomicLong lastPressureCheck = new AtomicLong(0);
    private static final long PRESSURE_CHECK_INTERVAL = 5_000_000; // 5ms in nanoseconds
    
    public static PressureLevel getCurrentPressure(ForkJoinPool pool) 
    {
        long now = System.nanoTime();
        if (now - lastPressureCheck.get() > PRESSURE_CHECK_INTERVAL) 
        {
            if (lastPressureCheck.compareAndSet(lastPressureCheck.get(), now)) 
            {
                // Only one thread updates the cached pressure
                cachedPressure = assessQueuePressure(pool);
            }
        }
        return cachedPressure;
    }

    public static PressureLevel assessQueuePressure(ForkJoinPool pool) 
    {
        if (pool == null) return PressureLevel.NORMAL;
        
        try 
        {
            long queuedTasks = pool.getQueuedTaskCount();
            
            if (queuedTasks < LOW_PRESSURE_THRESHOLD) return PressureLevel.LOW;
            if (queuedTasks > HIGH_PRESSURE_THRESHOLD) return PressureLevel.HIGH;
            return PressureLevel.NORMAL;
        }
        catch (Exception e) 
        {
            logger.warn("Pressure assessment failed, using NORMAL", e);
            return PressureLevel.NORMAL;
        }
    }
    
    // Mathematically verified constraint checking logic
    public static boolean shouldPerformConstraintCheck(PressureLevel pressure, int prefixLength, int numClicks) 
    {
        double baseProbability = switch (pressure) 
        {
            case HIGH -> HIGH_PRESSURE_CONSTRAINT_RATE;     // 0.6
            case LOW -> LOW_PRESSURE_CONSTRAINT_RATE;       // 0.2
            case NORMAL -> NORMAL_CONSTRAINT_RATE;          // 0.4
        };
        
        // Bound invertedDepthFactor to [0,1]
        double maxDepth = Math.max(4.0, numClicks * 0.3);
        double invertedDepthFactor = Math.min(1.0, Math.max(0.0, (maxDepth - prefixLength) / maxDepth));
        
        // Guaranteed to be in range [baseProbability, baseProbability + 0.2]
        double adjustedProbability = baseProbability + (invertedDepthFactor * 0.2);
        
        boolean shouldCheck = ThreadLocalRandom.current().nextDouble() < adjustedProbability;
        GranularityMetrics.recordConstraintDecision(shouldCheck);
        return shouldCheck;
    }
    
    public static int getAdaptiveLeafLevel(PressureLevel pressure, int numClicks) 
    {
        return switch (pressure) 
        {
            case HIGH -> numClicks - 2;  // Expand leaf generation under high pressure
            case LOW, NORMAL -> numClicks - 1;  // Standard behavior
        };
    }
}
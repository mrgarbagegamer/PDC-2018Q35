package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GranularityController 
{
    private static final Logger logger = LogManager.getLogger(GranularityController.class);

    public enum PressureLevel { STARVED, NORMAL, SATURATED }
    
    // Work queue pressure thresholds based on total capacity percentages
    // With 8 threads × 16 capacity = 128 total capacity
    private static int totalCapacity = -1;
    private static int starvationThreshold = -1;   // 25% of capacity (work starvation)
    private static int saturationThreshold = -1;  // 75% of capacity (work saturation)
    
    // Bounded constraint checking rates [0.2, 0.6]
    private static final double SATURATED_PRESSURE_CONSTRAINT_RATE = 0.6;  // More strict (worker saturation)
    private static final double STARVED_PRESSURE_CONSTRAINT_RATE = 0.2;   // More lenient (workers starvation)
    private static final double NORMAL_CONSTRAINT_RATE = 0.4;         // Balanced
    
    // Cached work queue size updated by metrics logger thread
    private static volatile int cachedWorkQueueSize = 0;
    private static volatile PressureLevel cachedPressure = PressureLevel.NORMAL;
    
    public static void initializeWithCapacity(int totalCapacity)
    {
        GranularityController.totalCapacity = totalCapacity;
        GranularityController.cachedWorkQueueSize = totalCapacity; // Initialize to full capacity (since queues are pre-filled)
        GranularityController.starvationThreshold = (int) (totalCapacity * 0.25);   // 25%
        GranularityController.saturationThreshold = (int) (totalCapacity * 0.75);  // 75%
        
        logger.info("Initialized work queue pressure thresholds: STARVED={}% ({}), SATURATED={}% ({}), Total Capacity={}",
                   25, starvationThreshold, 75, saturationThreshold, totalCapacity);
    }
    
    public static void updateCachedWorkQueueSize(int currentSize)
    {
        cachedWorkQueueSize = currentSize;
        cachedPressure = assessWorkQueuePressure();
    }
    
    public static PressureLevel getCurrentPressure(ForkJoinPool pool)
    {
        return cachedPressure;
    }

    private static PressureLevel assessWorkQueuePressure()
    {
        if (totalCapacity == -1) return PressureLevel.NORMAL; // Not initialized
        
        // STARVED work queue = STARVED pressure (workers starving, give them work)
        // SATURATED work queue = SATURATED pressure (workers have plenty of work, increase granularity)
        if (cachedWorkQueueSize < starvationThreshold) return PressureLevel.STARVED;   // <25% = workers starving!
        if (cachedWorkQueueSize > saturationThreshold) return PressureLevel.SATURATED;   // >75% = workers saturated!
        return PressureLevel.NORMAL;
    }
    
    // Mathematically verified constraint checking logic
    public static boolean shouldPerformConstraintCheck(PressureLevel pressure, int prefixLength, int numClicks) 
    {
        double baseProbability = switch (pressure) 
        {
            case SATURATED -> SATURATED_PRESSURE_CONSTRAINT_RATE;     // 0.6 (worker saturation, give them valid combinations)
            case STARVED -> STARVED_PRESSURE_CONSTRAINT_RATE;       // 0.2 (worker starvation, can handle coarse granularity)
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
            case SATURATED -> numClicks - 1;  // SATURATED pressure = worker saturation, use fine granularity
            case STARVED -> numClicks - 2;   // STARVED pressure = worker starvation, can handle coarse granularity
            case NORMAL -> numClicks - 1; // Standard behavior
        };
    }
}
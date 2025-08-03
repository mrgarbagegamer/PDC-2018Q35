package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GranularityMetrics
{
    private static final Logger logger = LogManager.getLogger(GranularityMetrics.class);
    
    // Minimal metrics collection to avoid overhead
    private static final AtomicLong totalConstraintDecisions = new AtomicLong();
    private static final AtomicLong constraintChecksPerformed = new AtomicLong();
    
    // Scheduled logging to avoid hot path overhead
    private static final ScheduledExecutorService scheduler = createScheduler();
    private static volatile ForkJoinPool monitoredPool = null;
    
    private static ScheduledExecutorService createScheduler()
    {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "GranularityMetrics-Logger");
                t.setDaemon(true); // Don't keep JVM alive
                return t;
            }
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
    
    public static void startPeriodicLogging(ForkJoinPool pool, CombinationQueueArray queueArray)
    {
        monitoredPool = pool;
        
        // Initialize GranularityController with queue capacity
        GranularityController.initializeWithCapacity(queueArray.getTotalCapacity());
        
        // More frequent updates (50ms) to keep pressure assessment current
        scheduler.scheduleAtFixedRate(() -> {
            try
            {
                if (monitoredPool != null && !monitoredPool.isShutdown())
                {
                    int currentWorkQueueSize = queueArray.getTotalSize();
                    
                    // Update cached work queue size in GranularityController
                    GranularityController.updateCachedWorkQueueSize(currentWorkQueueSize);
                    
                    GranularityController.PressureLevel pressure =
                        GranularityController.getCurrentPressure(monitoredPool);
                    
                    logger.debug("Pressure: {} | Generator Queue: {} combos | Work Queues: {} batches | Constraint Rate: {}%",
                                pressure, monitoredPool.getQueuedTaskCount(), currentWorkQueueSize, getConstraintCheckRate());
                }
            }
            catch (Exception e)
            {
                // Silently ignore to avoid disrupting the main computation
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }
    
    public static void stopPeriodicLogging()
    {
        monitoredPool = null;
        scheduler.shutdown();
    }
    
    public static void recordConstraintDecision(boolean performed)
    {
        totalConstraintDecisions.incrementAndGet();
        if (performed)
        {
            constraintChecksPerformed.incrementAndGet();
        }
    }
    
    public static double getConstraintCheckRate()
    {
        long total = totalConstraintDecisions.get();
        double rate = total > 0 ? constraintChecksPerformed.get() * 100.0 / total : 0.0;
        return Math.round(rate * 100.0) / 100.0; // Round to 2 decimal places
    }
    
    // Reset metrics for testing
    public static void reset()
    {
        totalConstraintDecisions.set(0);
        constraintChecksPerformed.set(0);
    }
}
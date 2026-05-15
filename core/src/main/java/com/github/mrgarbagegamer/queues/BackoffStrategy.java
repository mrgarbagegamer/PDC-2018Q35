package com.github.mrgarbagegamer.queues;

import java.util.concurrent.locks.LockSupport;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

/**
 * A strategy for backing off when a thread is contending for a resource.
 * 
 * <p>
 * This {@link FunctionalInterface functional interface} defines a single method,
 * {@link #backoff()}, which is called by a {@link QueueSelector selector} when a thread is
 * contending for a resource and the previous attempt to acquire the resource has failed.
 * Implementations of this interface can define various backoff strategies, such as
 * {@link #sleep(long, int) sleeping} for a certain amount of time, {@link #yield() yielding} the
 * thread, or using a more complex algorithm to determine the backoff time.
 * </p>
 * 
 * <p>
 * It is recommended, though not required, that implementations of this interface be stateless and
 * reusable, as they may be called multiple times by the same or different threads. Thread safety is
 * an important consideration for implementations that maintain state, as they may be accessed by
 * multiple threads concurrently. Finally, object allocation inside the {@link #backoff()} method
 * should be avoided at all costs, as it may lead to increased garbage collection overhead and
 * reduced performance.
 * </p>
 * 
 * @since 2026.02 - Queue Injection Refactor
 * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
 */
@ExcludeFromGeneratedCoverage
@FunctionalInterface
public interface BackoffStrategy {

    /**
     * Performs the backoff action.
     * 
     * <p>
     * This method is called by a {@link QueueSelector selector} when a thread is contending for a
     * resource and the previous attempt to acquire the resource has failed. It is important to
     * avoid object allocation within this method to minimize garbage collection overhead and
     * maintain performance.
     * </p>
     * 
     * @throws InterruptedException if the thread is interrupted while performing the backoff
     *                              action.
     * @since 2026.02 - Queue Injection Refactor
     * @threading Should be thread-safe if stateful, otherwise reusable and stateless.
     * @memory Should not allocate.
     */
    void backoff() throws InterruptedException;

    /**
     * Creates a {@code BackoffStrategy} that {@link Thread#sleep(long, int) sleeps} for the
     * specified amount of time.
     * 
     * @param millis the number of milliseconds to sleep.
     * @param nanos  the number of nanoseconds to sleep, in addition to the milliseconds.
     * @return A {@code BackoffStrategy} that sleeps for the specified amount of time.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} lambda creation.
     * @threading Thread-safe and reusable.
     * @memory Allocates a lambda for the backoff strategy.
     */
    static BackoffStrategy sleep(long millis, int nanos) {
        return () -> Thread.sleep(millis, nanos);
    }

    /**
     * Creates a {@code BackoffStrategy} that performs no operation.
     * 
     * @return A {@code BackoffStrategy} that performs no operation.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} lambda creation.
     * @threading Thread-safe and reusable.
     * @memory Allocates a lambda for the backoff strategy.
     */
    static BackoffStrategy noOp() { return () -> {}; }

    /**
     * Creates a {@code BackoffStrategy} that {@link Thread#yield() yields} the current thread.
     * 
     * @return A {@code BackoffStrategy} that yields the current thread.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} lambda creation.
     * @threading Thread-safe and reusable.
     * @memory Allocates a lambda for the backoff strategy.
     */
    static BackoffStrategy yield() { return Thread::yield; }

    /**
     * Creates a {@code BackoffStrategy} that {@link LockSupport#parkNanos(long) parks} the current
     * thread for the specified number of nanoseconds.
     * 
     * @param nanos the number of nanoseconds to park the thread.
     * @return A {@code BackoffStrategy} that parks the current thread for the specified number of
     *         nanoseconds.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} lambda creation.
     * @threading Thread-safe and reusable.
     * @memory Allocates a lambda for the backoff strategy.
     */
    static BackoffStrategy parkNanos(long nanos) { return () -> LockSupport.parkNanos(nanos); }

    /**
     * Creates a {@code BackoffStrategy} that calls {@link Thread#onSpinWait()} to indicate that the
     * current thread is in a spin-wait loop.
     * 
     * @return A {@code BackoffStrategy} that calls {@link Thread#onSpinWait()}.
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} lambda creation.
     * @threading Thread-safe and reusable.
     * @memory Allocates a lambda for the backoff strategy.
     */
    static BackoffStrategy onSpinWait() { return Thread::onSpinWait; }
}

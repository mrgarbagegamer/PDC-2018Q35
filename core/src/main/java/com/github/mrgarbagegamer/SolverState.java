package com.github.mrgarbagegamer;

// TODO: Consider allowing injectability of a Clock or similar for easier testing and potential
// future features.
// TODO: Consider using a CountDownLatch or similar to replace the generationComplete flag.
/**
 * A class representing the state of the solver, handling timing, {@link #generationComplete()
 * generation completion}, and {@link #solutionFound() solution found} status.
 * 
 * <h2>Architectural Role</h2>
 * <p>
 * In the previous queue design for this codebase, solver state was encapsulated in the
 * {@code CombinationQueueArray} class, which was responsible for managing the state of the solver
 * in addition to its primary responsibility of orchestrating communication betwen
 * {@link CombinationGeneratorTask generators} and {@link TestClickCombination monkeys}. This design
 * led to a tight coupling between the solver state and the queue implementation, making it
 * difficult to manage the solver state independently of the queue. Extracting the solver state into
 * its own class allows for better separation of concerns, making the codebase more modular and
 * easier to maintain.
 * </p>
 * 
 * <p>
 * Currently, this class uses two {@code long} fields to track the {@link #getStartTime() start
 * time} and {@link #getEndTime() end time} of the solver, using {@link System#currentTimeMillis()}
 * to capture these times. This allows for somewhat accurate timing of the solver's execution,
 * though it limits mocking and testing capabilities. In the future, we may want to consider
 * allowing injectability of a {@link java.time.Clock Clock} (or {@link java.time.InstantSource
 * InstantSource}, for maximum flexibility) and taking the start and end times as
 * {@link java.time.Instant Instant} objects instead of raw milliseconds, which would allow for more
 * accurate timing and easier testing.
 * </p>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * This class is designed to be thread-safe, as it will be accessed and modified by multiple threads
 * in the solver. The flags for {@code solutionFound} and {@code generationComplete} are marked as
 * {@code volatile} to ensure visibility across threads, and the methods that modify these flags use
 * double-checked locking to ensure that only one thread can {@link #markSolutionFound(short[]) mark
 * a solution as found} or {@link #markGenerationComplete() generation as complete} at a time,
 * preventing race conditions.
 * </p>
 * 
 * @see StartYourMonkeys.Solver
 * @since 2026.02 - Queue Injection Refactor
 * @performance {@code O(1)} for all operations.
 * @threading Thread-safe.
 * @memory Does not allocate after construction.
 */
public final class SolverState {
    /**
     * The timestamp indicating when the solver started. This is initialized during
     * {@link #SolverState() construction} and is fixed for the lifetime of the solver state.
     *
     * @see #endTime
     * @see #getStartTime()
     * @see StartYourMonkeys.Solver#reportResults()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe by nature of being {@code final}.
     * @memory Fixed memory footprint of {@code 8} bytes as a primitive {@code long}.
     */
    private final long startTime;

    /**
     * The timestamp indicating when the solver finished.
     *
     * <p>
     * This is initialized to {@code -1L} to indicate that the solver is still running, and is
     * updated when either a {@link #markSolutionFound(short[]) solution is found} or the generation
     * phase is {@link #markGenerationComplete() complete}. This allows for tracking the total
     * execution time of the solver from start to finish, regardless of how it ends. A
     * {@code volatile long} is used instead of an {@link java.util.concurrent.atomic.AtomicLong
     * AtomicLong} for simplicity and to minimize overhead, as updates to this field are
     * {@code synchronized} and based on the {@link #solutionFound} and {@link #generationComplete}
     * flags, which are also {@code volatile}.
     * </p>
     * 
     * <p>
     * Technically, this field could have its {@code volatile} modifier removed, piggybacking on the
     * {@code volatile} flags for visibility guarantees. However, marking it as {@code volatile}
     * provides a clear signal to readers that this field is accessed and modified across threads,
     * and it ensures that updates to this field are immediately visible to all threads without
     * needing to rely on the synchronization of the flags. Given that reads and writes to this
     * field are relatively infrequent (only when a solution is found or generation completes), the
     * performance impact of marking it as {@code volatile} is negligible, and the clarity benefits
     * outweigh the minimal overhead.
     * </p>
     *
     * @see #startTime
     * @see #getEndTime()
     * @see StartYourMonkeys.Solver#reportResults()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe as a {@code volatile long}.
     * @memory Fixed memory footprint of {@code 8} bytes as a primitive {@code long}.
     */
    private volatile long endTime = -1L;

    /**
     * The thread that successfully found the solution.
     * 
     * <p>
     * This is set by the first thread (probably a {@link TestClickCombination monkey}) to call
     * {@link #markSolutionFound(short[])} and is used for logging purposes at the end of the
     * solver's execution (e.g. to log which thread found the solution). This field is marked as
     * {@code volatile} to ensure that once a thread sets this field, all other threads will see the
     * updated value immediately, allowing for accurate logging of the winning thread even if other
     * threads are still running or have already completed their tasks.
     * </p>
     *
     * @see #getWinningThread()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe as a {@code volatile} reference.
     * @memory Fixed memory footprint of {@code 4} bytes for the reference.
     */
    private volatile Thread winningThread;

    /**
     * The combination generated by the {@link #winningThread} that successfully solved the puzzle,
     * stored in {@link Grid.ValueFormat#Index} format. Like {@code winningThread}, this field is
     * set by the first thread to call {@link #markSolutionFound(short[])} and is used for logging
     * purposes at the end of the solver's execution.
     * 
     * @see #getWinningCombination()
     * @see Grid#indexToPacked(short)
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe as a {@code volatile} reference.
     * @memory Fixed memory footprint of {@code 4} bytes for the reference.
     */
    private volatile short[] winningCombination;

    /**
     * A flag indicating whether a solution has been found by any thread.
     * 
     * <p>
     * This flag is set to {@code true} by the first thread (always a {@link TestClickCombination
     * monkey}) to call {@link #markSolutionFound(short[])}, and is used to signal to all threads
     * that a solution has been found, allowing them to stop processing further combinations. It is
     * recommended that threads check this flag periodically during their processing to allow for a
     * timely shutdown once a solution is found. A {@code volatile boolean} is used instead of an
     * {@link java.util.concurrent.atomic.AtomicBoolean AtomicBoolean} for simplicity and to
     * minimize overhead, as updates to this flag are {@code synchronized} and based on the first
     * thread to find a solution.
     * </p>
     *
     * @see #solutionFound()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe as a {@code volatile boolean}.
     * @memory Fixed memory footprint of {@code 1} byte as a primitive {@code boolean}.
     */
    private volatile boolean solutionFound;

    /**
     * A flag indicating whether the {@link CombinationGeneratorTask generators} have finished
     * generating all combinations.
     * 
     * <p>
     * This flag is set to {@code true} by the first thread to call
     * {@link #markGenerationComplete()}, and is used to signal to the {@link TestClickCombination
     * monkeys} that no more combinations will be generated, allowing them to exit once they have
     * processed all remaining combinations in the queue(s). Similar to {@code solutionFound}, a
     * {@code volatile boolean} is used for simplicity and to minimize overhead, as updates to this
     * flag are {@code synchronized} and based on the first thread to mark generation as complete.
     * </p>
     * 
     * <p>
     * A potential future improvement for this flag would be to replace it with a more robust
     * synchronization mechanism, such as a {@link java.util.concurrent.CountDownLatch
     * CountDownLatch} or similar, to track when all generators have completed their work. This
     * would allow for more precise coordination between generators and monkeys and simplify the
     * logic for blocking the main solver thread. We leave this as a todo for now.
     * </p>
     *
     * @see #generationComplete()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe as a {@code volatile boolean}.
     * @memory Fixed memory footprint of {@code 1} byte as a primitive {@code boolean}.
     */
    private volatile boolean generationComplete;

    /**
     * Constructs a new {@code SolverState} and initializes the {@link #getStartTime() start time}
     * to the {@link System#currentTimeMillis() current system time in milliseconds}.
     *
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field initialization.
     * @threading Thread-safe by nature of construction.
     * @memory Does not allocate other than the object itself.
     */
    public SolverState() { this.startTime = System.currentTimeMillis(); }

    /**
     * Marks that a solution has been found by the {@link Thread#currentThread() current thread} and
     * records the winning combination. This method uses double-checked locking to ensure that only
     * the first thread to find a solution can set the {@code solutionFound} flag and update the
     * related fields, preventing race conditions.
     * 
     * <p>
     * Note that a defensive copy of the {@code combination} array is not made here, as it is
     * assumed that the caller will not modify the array after passing it to this method. If there
     * is a possibility of the caller modifying the array, a defensive copy should be made to ensure
     * thread safety and data integrity.
     * </p>
     *
     * @param combination the combination that solved the puzzle, in {@link Grid.ValueFormat#Index}
     *                    format
     * @see #getEndTime()
     * @see #getWinningThread()
     * @see #getWinningCombination()
     * @see #solutionFound()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} for checking the flag, acquiring the lock, and setting fields.
     * @threading Thread-safe. Uses double-checked locking on {@code volatile} fields.
     * @memory Does not allocate.
     */
    public void markSolutionFound(short[] combination) {
        if (!solutionFound) {
            synchronized (this) {
                if (!solutionFound) {
                    // TODO: Create a defensive copy of the combination array for safety.
                    // Set the time first to ensure accurate timing
                    this.endTime = System.currentTimeMillis();
                    this.winningCombination = combination;
                    this.winningThread = Thread.currentThread();
                    this.solutionFound = true;
                }
            }
        }
    }

    /**
     * Marks that the generation phase is complete, indicating that no more combinations will be
     * produced by the {@link CombinationGeneratorTask generators}. This method uses double-checked
     * locking to ensure that only the first thread to mark generation as complete can set the
     * {@code generationComplete} flag and update the {@code endTime}, preventing race conditions.
     * 
     * <p>
     * Note that this method immediately sets the {@code endTime} when marking generation as
     * complete, irrespective of the emptiness of the queues or the state of the
     * {@link TestClickCombination monkeys}. This is a current limitation of the design, as it does
     * not account for the time taken by monkeys to process the final batches of combinations after
     * generation completes. A potential future improvement would be to implement a more robust
     * synchronization mechanism (e.g. a {@link java.util.concurrent.CountDownLatch CountDownLatch})
     * to track when all monkeys have finished processing, allowing for more accurate timing of the
     * solver's execution from start to finish. We leave this as a todo for now.
     * </p>
     *
     * @see #endTime
     * @see #generationComplete
     * @see #markSolutionFound(short[])
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} for checking the flag, acquiring the lock, and setting fields.
     * @threading Thread-safe. Uses double-checked locking on {@code volatile} fields.
     * @memory Does not allocate.
     */
    public void markGenerationComplete() {
        if (!generationComplete) {
            synchronized (this) {
                if (!generationComplete) {
                    // TODO: Since the monkeys still have to test the final batches, maybe we should
                    // use a CountDownLatch or something to track when all monkeys are done?
                    this.endTime = System.currentTimeMillis();
                    this.generationComplete = true;
                }
            }
        }
    }

    /**
     * {@return the time the solver started, in milliseconds}
     *
     * @see #SolverState()
     * @see #getEndTime()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe by nature of immutability.
     * @memory Does not allocate.
     */
    public long getStartTime() { return startTime; }

    /**
     * {@return the time the solver ended, in milliseconds, or {@code -1L} if the solver is still
     * running}
     * 
     * @see #SolverState()
     * @see #getStartTime()
     * @see #markGenerationComplete()
     * @see #markSolutionFound(short[])
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile long}.
     * @memory Does not allocate.
     */
    public long getEndTime() { return endTime; }

    /**
     * {@return the {@link Thread} that found the solution, or {@code null} if no solution has been
     * found}
     * 
     * @see #getWinningCombination()
     * @see #markSolutionFound(short[])
     * @see #solutionFound()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile} reference.
     * @memory Does not allocate.
     */
    public Thread getWinningThread() { return winningThread; }

    /**
     * {@return the combination that solved the puzzle, in {@link Grid.ValueFormat#Index} format, or
     * {@code null} if no solution has been found}
     * 
     * @see #getWinningThread()
     * @see #markSolutionFound(short[])
     * @see #solutionFound()
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile} reference.
     * @memory Does not allocate.
     */
    public short[] getWinningCombination() { return winningCombination; }

    /**
     * {@return {@code true} if a solution has been found by any thread, {@code false} otherwise}
     * 
     * @see #markSolutionFound(short[])
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile boolean}.
     * @memory Does not allocate.
     */
    public boolean solutionFound() { return solutionFound; }

    /**
     * {@return {@code true} if the generation phase is complete and no more combinations will be
     * produced by the {@link CombinationGeneratorTask generators}, {@code false} otherwise}
     * 
     * @since 2026.02 - Queue Injection Refactor
     * @performance {@code O(1)} field access.
     * @threading Thread-safe read of a {@code volatile boolean}.
     * @memory Does not allocate.
     */
    public boolean generationComplete() { return generationComplete; }
}

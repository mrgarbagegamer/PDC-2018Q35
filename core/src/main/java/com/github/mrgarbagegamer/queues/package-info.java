/**
 * Provides a flexible, high-performance queueing subsystem for communication between
 * {@link com.github.mrgarbagegamer.CombinationGeneratorTask generators} and
 * {@link com.github.mrgarbagegamer.TestClickCombination monkeys}.
 * 
 * <h2>Architectural Role</h2>
 * <p>
 * This package implements the communication backbone of the solver's producer-consumer
 * architecture. It abstracts the complex details of work distribution, queue selection, and
 * contention management behind the unified {@link com.github.mrgarbagegamer.QueueStrategy
 * QueueStrategy} interface. By decoupling the core solver logic from specific queue
 * implementations, this subsystem allows for extensive experimentation and performance tuning
 * across different hardware configurations.
 * </p>
 * 
 * <h2>Problems Solved</h2>
 * <p>
 * In a highly concurrent brute-force solver, the mechanism for passing
 * {@link com.github.mrgarbagegamer.WorkBatch WorkBatches} between generators and monkeys is a
 * critical performance bottleneck. Hardcoding a single queue implementation or topology (e.g., a
 * single shared queue vs. thread-local queues) limits the solver's ability to scale optimally on
 * different core counts or architectures, and impedes the ability to properly test or benchmark
 * specific components of the code. Furthermore, different high-performance queue libraries (like
 * JCTools or Conversant) have varying APIs, access constraints (e.g., SPSC vs. MPMC), and capacity
 * requirements.
 * </p>
 * 
 * <p>
 * This package solves these problems by providing a modular framework that can adapt to various
 * queue types and topologies while enforcing strict validation at startup to prevent subtle
 * concurrency bugs.
 * </p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 * <li><b>Strategies:</b> {@link JCToolsQueueStrategy} and {@link BlockingQueueStrategy} implement
 * the high-level {@code QueueStrategy} interface, managing lists of queues for both the
 * generator-to-monkey (GTM) and monkey-to-generator (MTG) directions.</li>
 * 
 * <li><b>Selectors:</b> {@link QueueSelector} implementations (found in {@link QueueSelectors})
 * dictate <i>how</i> threads interact with the lists of queues. Strategies range from simple
 * exclusive access to complex work-stealing or round-robin approaches.</li>
 * 
 * <li><b>Wrappers and Markers:</b> To handle the diverse APIs of different queue libraries, queues
 * are wrapped (e.g., via {@link JCToolsWrappers}) to provide a consistent interface.
 * {@link QueueMarkers} attach metadata about a queue's {@link QueueMarkers.AccessMode access mode}
 * and {@link QueueMarkers.Boundedness boundedness}.</li>
 * 
 * <li><b>Validation:</b> {@link QueueUtils} performs rigorous startup validation, ensuring that the
 * chosen queues, selectors, and thread counts are compatible (e.g., preventing multiple producers
 * from writing to an {@link QueueMarkers.AccessMode.SPSC SPSC} queue).</li>
 * 
 * <li><b>Contention Management:</b> {@link BackoffStrategy} defines how threads behave when they
 * encounter contention or empty/full queues, while {@link ContinuationPredicates} control graceful
 * termination.</li>
 * </ul>
 * 
 * <h2>Performance and Extensibility</h2>
 * <p>
 * The subsystem is designed to have minimal overhead on the hot path. Abstractions like wrappers
 * rely heavily on JVM method inlining and JIT compilation to mostly eliminate virtual dispatch
 * overhead. Validation is strictly confined to the initialization phase. This design allows
 * developers to easily introduce new queue implementations or selection strategies to further
 * optimize the solver without risking the integrity of the core algorithm.
 * </p>
 * 
 * @since 2026.02 - Queue Injection Refactor
 */
package com.github.mrgarbagegamer.queues;
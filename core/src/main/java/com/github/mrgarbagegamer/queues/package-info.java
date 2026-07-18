/**
 * Provides a flexible, high-performance queuing subsystem for communication between generators and
 * monkeys.
 * 
 * <h2>Architectural Role</h2>
 * <p>
 * This package implements the communication backbone of the solver's producer-consumer
 * architecture. It abstracts the complex details of work distribution, queue selection, and
 * contention management behind the unified {@link com.github.mrgarbagegamer.QueueStrategy
 * QueueStrategy} interface. By decoupling the core solver logic from specific queue implementations
 * and topologies, this subsystem allows for extensive experimentation and performance tuning across
 * different hardware configurations.
 * 
 * <h2>Problems Solved</h2>
 * <p>
 * In a highly concurrent brute-force solver, the mechanism for passing
 * {@link com.github.mrgarbagegamer.WorkBatch} objects between generators and monkeys is a critical
 * performance bottleneck. Hardcoding a single queue implementation or topology (e.g., a single
 * shared queue vs. thread-local queues) limits the solver's ability to scale optimally on different
 * core counts or architectures, and impedes the ability to properly test or benchmark specific
 * components of the code. Furthermore, different high-performance queue libraries (like JCTools or
 * Conversant) have varying APIs, access constraints (e.g., SPSC vs. MPMC), and capacity
 * requirements.
 * 
 * <p>
 * This package solves these problems by providing a modular framework that adapts to various queue
 * types and topologies while enforcing strict validation at startup to prevent subtle concurrency
 * bugs (such as multiple threads offering to a Single-Producer Single-Consumer queue).
 * 
 * <h2>Public API and Topologies</h2>
 * <p>
 * To ensure ease of maintenance and support future refactoring, all internal logic and
 * implementation details are fully encapsulated. The sole public entrypoint is
 * {@link QueueStrategies}, which provides static factory methods and builders for configuring and
 * instantiating {@code QueueStrategy} implementations:
 * <ul>
 * <li>{@link QueueStrategies.BlockingQueueStrategy}: Backed by traditional
 * {@link java.util.concurrent.BlockingQueue} implementations (e.g., JDK or Conversant Disruptor
 * queues).
 * 
 * <li>{@link QueueStrategies.JCToolsQueueStrategy}: Backed by lock-free JCTools
 * {@link org.jctools.queues.MessagePassingQueue} implementations, optimized for low-overhead
 * concurrent access.
 * </ul>
 * 
 * <p>
 * Both strategies support four primary topologies to configure queue routing:
 * <ul>
 * <li><b>Single-Single:</b> One generator-to-monkey queue and one monkey-to-generator queue.
 * <li><b>Single-Multi:</b> One generator-to-monkey queue and multiple monkey-to-generator queues.
 * <li><b>Multi-Single:</b> Multiple generator-to-monkey queues and one monkey-to-generator queue.
 * <li><b>Multi-Multi:</b> Multiple generator-to-monkey queues and multiple monkey-to-generator
 * queues.
 * </ul>
 * 
 * <h2>Performance and Extensibility</h2>
 * <p>
 * The subsystem is designed for minimal overhead on the hot path by confining validation to the
 * initialization phase. This allows developers to easily swap in or benchmark new queue
 * configurations via {@code QueueStrategies} without affecting the core algorithm.
 * 
 * @since 2026.02 - Queue Injection Refactor
 */
@org.jspecify.annotations.NullUnmarked
package com.github.mrgarbagegamer.queues;
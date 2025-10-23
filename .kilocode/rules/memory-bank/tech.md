# Technology Stack

## Core Technologies

- **Language**: Java 25
- **Build & Dependency Management**: Apache Maven

## Key Dependencies

- **`it.unimi.dsi:fastutil`**: A library providing type-specific maps, sets, lists, and queues with a small memory footprint and fast access and insertion. It is used where performance with primitive collections is critical.

- **`org.apache.logging.log4j:log4j-bom`**: The Log4j 2 Bill of Materials is used to manage versions for the logging framework. The application is configured for high-performance asynchronous logging to prevent I/O from becoming a bottleneck in the concurrent hot paths.

- **`com.lmax:disruptor`**: A high-performance inter-thread messaging library. While not used directly for the main queue, its principles influence the low-contention design, and it's included as a dependency for the asynchronous logging feature of Log4j 2.

- **`org.jctools:jctools-core`**: Java Concurrency Tools provide a set of high-performance concurrent data structures. The `MpmcArrayQueue` is the foundation of the solver's lock-free, low-contention producer-consumer communication channel (`CombinationQueue`).

## Development & Tooling

- **JDK Version**: The project is configured to compile with Java 25.
- **Javadoc Generation**: The `maven-javadoc-plugin` is heavily configured with custom tags (`@performance`, `@threading`, etc.) to generate rich internal documentation.
- **CI/CD**: A GitHub Actions workflow (`.github/workflows/publish-javadocs.yml`) is set up to automatically build and publish the Javadocs to GitHub Pages.

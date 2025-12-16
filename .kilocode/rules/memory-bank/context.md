# Context

- **Current Focus**: Finalizing documentation after a major architectural refactoring.
- **Recent Changes**:
  - **Introduced `StartYourMonkeys.GlobalConfig`**: A new inner class that acts as a central, immutable source of truth for all configuration. It uses the Java 25 `StableValue` API for thread-safe, lazy initialization of both core and derived configuration values.
  - **Refactored All Core Components**:
    - `TestClickCombination` now caches derived data from `GlobalConfig` into `static final` fields, enabling significant JIT optimizations in its hot path.
    - `CombinationQueueArray` was modernized to use a `StableValue.supplier` for its singleton instance, eliminating the old double-checked locking pattern.
    - `WorkBatch` and `CombinationGeneratorTask` were simplified to pull all configuration directly from `GlobalConfig`, removing the need for manual parameter passing.
  - This refactoring has simplified the codebase, improved performance, and increased type safety.
  - Updated `architecture.md` to reflect the new data flow and component roles.
- **Next Steps**: Final review of all documentation and prepare for merge.

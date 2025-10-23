# Context

- **Current Focus**: Performance optimization of the combination generator.
- **Recent Changes**:
  - Refactored the leaf generation logic in `CombinationGeneratorTask.java` to improve performance.
  - Replaced the iterative, one-by-one combination creation with a bulk-processing approach.
  - Introduced a `WorkBatch.addBulk()` method to leverage `System.arraycopy()` for faster batch filling.
  - Implemented `Arrays.binarySearch()` in `computeLeafCombinations` to quickly find the valid range of final clicks, eliminating a linear scan.
  - Updated `architecture.md` to document the new leaf generation strategy.
- **Next Steps**: Verify the performance improvements and ensure the solver's correctness has not been affected.

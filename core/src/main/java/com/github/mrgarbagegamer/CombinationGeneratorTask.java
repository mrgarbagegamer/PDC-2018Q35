package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.concurrent.RecursiveAction;

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.shorts.ShortList;

// TODO: Update Javadoc
// TODO: Look at using getSurplusQueuedTaskCount() to determine if we should fork or compute
// directly
public sealed abstract class CombinationGeneratorTask extends RecursiveAction {

    final short[] prefix;
    final int numClicks;

    int prefixLength;
    boolean isOdd;

    final LongList trueCellMasksLower;

    public static CombinationGeneratorTask createRootTask(SolverConfiguration solverConfig) {
        return new RootTask(solverConfig);
    }

    static IntermediateTask createIntermediateTask(SolverConfiguration solverConfig) {
        return new IntermediateTask(solverConfig);
    }

    static LeafTask createLeafTask(SolverConfiguration solverConfig) {
        return new LeafTask(solverConfig);
    }

    CombinationGeneratorTask(SolverConfiguration solverConfig) {
        mustNotBeNull(solverConfig, "config");
        this.numClicks = solverConfig.numClicks();
        this.prefix = new short[this.numClicks - 1];
        this.trueCellMasksLower = solverConfig.getTrueCellMasksLower();
    }

    private void initBaseTask(CombinationGeneratorTask parentTask, short newValue) {
        System.arraycopy(parentTask.prefix, 0, this.prefix, 0, parentTask.prefixLength);
        this.prefix[parentTask.prefixLength] = newValue;
        this.prefixLength = parentTask.prefixLength + 1;
        this.isOdd = parentTask.getNewPrefixParity(newValue);
        reinitialize();
    }

    @Override
    protected final void compute() {
        // Get the current GeneratorThread.
        final GeneratorThread currentThread = (GeneratorThread) Thread.currentThread();

        // Check for interruption of the current thread before doing any work.
        if (currentThread.isInterrupted()) {
            return; // Exit early if interrupted
        }

        // TODO: Evaluate whether it makes sense to get the context here instead of in the methods
        // Fetch the context from the current thread once to avoid multiple accesses.
        final GeneratorContext ctx = currentThread.getContext();

        try {
            this.computeTask(ctx);
        } finally {
            this.recycleSelf(ctx);
        }
    }

    abstract void computeTask(GeneratorContext ctx);

    void recycleSelf(GeneratorContext ctx) {} // Default implementation is a no-op.

    final short getStartClick() { return (short) (this.prefix[this.prefixLength - 1] + 1); }

    final short getMaxClick() {
        return (short) (Grid.NUM_CELLS - (this.numClicks - this.prefixLength) + 1);
    }

    final boolean getNewPrefixParity(short newValue) {
        final long lowerMask = this.trueCellMasksLower.getLong(newValue);
        return this.isOdd ^ ((lowerMask & 1L) != 0);
    }

    // =========================================================================
    // Specialized Subclasses
    // =========================================================================

    static final class RootTask extends CombinationGeneratorTask {

        private final SolverConfiguration solverConfig;

        RootTask(SolverConfiguration solverConfig) {
            super(solverConfig);
            this.prefixLength = 0;
            this.solverConfig = solverConfig;
        }

        @Override
        void computeTask(GeneratorContext ctx) {
            final ShortList evenClickIndices = this.solverConfig.getEvenClickIndices();
            final short lastEvenClick = evenClickIndices.getShort(evenClickIndices.size() - 1);
            final short max = (short) Math.min(getMaxClick(), lastEvenClick + 1);

            if (this.numClicks == 2) {
                forkLeaves(ctx, max);
            } else {
                forkIntermediates(ctx, max);
            }
        }

        private void forkLeaves(GeneratorContext ctx, short max) {
            for (short i = 0; i < max; i++) {
                LeafTask subtask = ctx.getLeafTask();
                subtask.init(this, i);
                subtask.fork();
            }
        }

        private void forkIntermediates(GeneratorContext ctx, short max) {
            for (short i = 0; i < max; i++) {
                IntermediateTask subtask = ctx.getIntermediateTask();
                subtask.init(this, i);
                subtask.fork();
            }
        }
    }

    static final class IntermediateTask extends CombinationGeneratorTask {

        private long currentAdjacenciesLower = 0L;
        private long currentAdjacenciesUpper = 0L;
        private boolean skipConstraintsCheck = false;

        private final LongList trueCellMasksUpper;
        private final long expectedMaskLower;
        private final long expectedMaskUpper;
        private final LongList suffixMasksLower;
        private final LongList suffixMasksUpper;
        private final boolean useDualMasks;

        private IntermediateTask(SolverConfiguration solverConfig) {
            super(solverConfig);
            this.trueCellMasksUpper = solverConfig.getTrueCellMasksUpper();
            this.expectedMaskLower = solverConfig.getExpectedMaskLower();
            this.expectedMaskUpper = solverConfig.getExpectedMaskUpper();
            this.suffixMasksLower = solverConfig.getSuffixMasksLower();
            this.suffixMasksUpper = solverConfig.getSuffixMasksUpper();
            this.useDualMasks = solverConfig.getUseDualMasks();
        }

        void init(RootTask parentTask, short newValue) {
            this.skipConstraintsCheck = false;
            this.currentAdjacenciesLower = this.trueCellMasksLower.getLong(newValue);
            if (this.useDualMasks)
                this.currentAdjacenciesUpper = this.trueCellMasksUpper.getLong(newValue);
            super.initBaseTask(parentTask, newValue);
        }

        void init(IntermediateTask parentTask, short newValue) {
            this.skipConstraintsCheck = parentTask.skipConstraintsCheck;
            if (!this.skipConstraintsCheck) {
                this.currentAdjacenciesLower = parentTask.currentAdjacenciesLower
                        | this.trueCellMasksLower.getLong(newValue);

                if (this.useDualMasks) {
                    this.currentAdjacenciesUpper = parentTask.currentAdjacenciesUpper
                            | this.trueCellMasksUpper.getLong(newValue);
                }
            }
            super.initBaseTask(parentTask, newValue);
        }

        @Override
        void computeTask(GeneratorContext ctx) {
            final short start = getStartClick();

            if (!this.skipConstraintsCheck && this.prefixLength >= 2 && !constraintCheck(start)) {
                return;
            }

            if (this.prefixLength == this.numClicks - 2)
                forkLeaves(ctx, start);
            else
                forkIntermediates(ctx, start);
        }

        private void forkLeaves(GeneratorContext ctx, short start) {
            final short max = getMaxClick();
            for (short i = start; i < max; i++) {
                LeafTask subtask = ctx.getLeafTask();
                subtask.init(this, i);
                subtask.fork();
            }
        }

        private void forkIntermediates(GeneratorContext ctx, short start) {
            final short max = getMaxClick();
            for (short i = start; i < max; i++) {
                IntermediateTask subtask = ctx.getIntermediateTask();
                subtask.init(this, i);
                subtask.fork();
            }
        }

        private boolean constraintCheck(int startIdx) {
            return this.useDualMasks ? constraintCheckDualMask(startIdx)
                    : constraintCheckSingleMask(startIdx);
        }

        private boolean constraintCheckSingleMask(int startIdx) {
            final long needed = this.currentAdjacenciesLower ^ this.expectedMaskLower;

            if (needed == 0L) {
                this.skipConstraintsCheck = true;
                return true;
            }

            return (this.suffixMasksLower.getLong(startIdx) & needed) == needed;
        }

        private boolean constraintCheckDualMask(int startIdx) {
            final long neededLower = this.currentAdjacenciesLower ^ this.expectedMaskLower;
            final long neededUpper = this.currentAdjacenciesUpper ^ this.expectedMaskUpper;

            if (neededLower == 0L && neededUpper == 0L) {
                this.skipConstraintsCheck = true;
                return true;
            }

            return (this.suffixMasksLower.getLong(startIdx) & neededLower) == neededLower
                    && (this.suffixMasksUpper.getLong(startIdx) & neededUpper) == neededUpper;
        }

        @Override
        void recycleSelf(GeneratorContext ctx) { ctx.recycleIntermediateTask(this); }
    }

    static final class LeafTask extends CombinationGeneratorTask {

        private LeafTask(SolverConfiguration solverConfig) { super(solverConfig); }

        void init(CombinationGeneratorTask parentTask, short newValue) {
            super.initBaseTask(parentTask, newValue);
        }

        @Override
        void computeTask(GeneratorContext ctx) {
            final short lastPrefixClick = getStartClick();

            WorkBatch batch = ctx.getCurrentBatch();
            if (batch == null) {
                return; // Exit if interrupted
            }

            if (batch.isFull()) {
                if (!ctx.flushCurrentBatch()) {
                    return; // Exit if interrupted or termination signal received
                }
                batch = ctx.getCurrentBatch();
                if (batch == null) {
                    return;
                }
            }

            batch.addWork(this.prefix, lastPrefixClick, this.isOdd);
        }

        @Override
        void recycleSelf(GeneratorContext ctx) { ctx.recycleLeafTask(this); }
    }
}
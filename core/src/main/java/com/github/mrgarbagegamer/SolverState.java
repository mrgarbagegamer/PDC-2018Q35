package com.github.mrgarbagegamer;

// TODO: Write Javadocs for this class.
// TODO: Consider allowing injectability of a Clock or similar for easier testing and potential
// future features.
public final class SolverState {
    private final long startTime;
    private volatile long endTime = -1L;
    private volatile Thread winningThread;
    private volatile short[] winningCombination;
    private volatile boolean solutionFound;
    private volatile boolean generationComplete;

    public SolverState() {
        this.startTime = System.currentTimeMillis();
    }

    public void markSolutionFound(short[] combination) {
        if (!solutionFound) {
            synchronized (this) {
                if (!solutionFound) {
                    // Set the time first to ensure accurate timing
                    this.endTime = System.currentTimeMillis();
                    this.winningCombination = combination;
                    this.winningThread = Thread.currentThread();
                    this.solutionFound = true;
                }
            }
        }
    }

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

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public Thread getWinningThread() {
        return winningThread;
    }

    public short[] getWinningCombination() {
        return winningCombination;
    }

    public boolean solutionFound() {
        return solutionFound;
    }

    public boolean generationComplete() {
        return generationComplete;
    }
}

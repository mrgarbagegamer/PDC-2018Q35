package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

public final class SolverStateBridge {
    private SolverStateBridge() { throw utilityClassError("SolverStateBridge"); }

    public static SolverState createInstance(SolverConfiguration config) {
        return new SolverState(config);
    }
}

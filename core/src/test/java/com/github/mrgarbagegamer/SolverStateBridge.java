package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;

import java.time.InstantSource;

public final class SolverStateBridge {
    private SolverStateBridge() { throw utilityClassError("SolverStateBridge"); }

    public static SolverState createInstance(InstantSource instantSource) {
        return new SolverState(instantSource);
    }

    public static SolverState createInstance(SolverServices services) {
        return createInstance(services.instantSource());
    }

    public static SolverState createDefaultInstance() {
        return createInstance(SolverServices.defaultServices());
    }
}

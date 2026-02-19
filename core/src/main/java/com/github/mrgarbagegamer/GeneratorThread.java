package com.github.mrgarbagegamer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

// TODO: Add Javadoc
public abstract class GeneratorThread extends ForkJoinWorkerThread {

    protected GeneratorThread(String name, ForkJoinPool pool) {
        super(pool);
        this.setName(name);
    }

    public abstract GeneratorContext getContext();
}

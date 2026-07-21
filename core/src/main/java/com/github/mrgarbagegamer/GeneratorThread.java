package com.github.mrgarbagegamer;

import static com.github.mrgarbagegamer.internal.ValidationUtils.mustNotBeNull;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

import org.jspecify.annotations.NullMarked;

// TODO: Add Javadoc
@NullMarked
public abstract class GeneratorThread extends ForkJoinWorkerThread {

    protected GeneratorThread(String name, ForkJoinPool pool) {
        super(mustNotBeNull(pool, "pool"));
        this.setName(mustNotBeNull(name, "name"));
    }

    public abstract GeneratorContext getContext();
}

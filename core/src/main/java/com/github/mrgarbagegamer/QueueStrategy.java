package com.github.mrgarbagegamer;

public interface QueueStrategy {

    WorkBatch generatorPoll(int generatorId) throws InterruptedException;

    void generatorOffer(WorkBatch batch, int generatorId) throws InterruptedException;

    WorkBatch monkeyPoll(int consumerId) throws InterruptedException;

    void monkeyOffer(WorkBatch batch, int consumerId) throws InterruptedException;
}

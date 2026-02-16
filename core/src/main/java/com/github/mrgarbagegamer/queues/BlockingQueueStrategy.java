package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.wrap;
import static com.github.mrgarbagegamer.queues.BlockingQueueWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors.EXCLUSIVE;
import static com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors.PREFERRED;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.ensureMultiConsumerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.ensureMultiProducerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.preallocateInto;
import static com.github.mrgarbagegamer.queues.QueueUtils.BlockingQueueUtils.validateArguments;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Stream;

import com.conversantmedia.util.concurrent.DisruptorBlockingQueue;
import com.conversantmedia.util.concurrent.PushPullBlockingQueue;
import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.WorkBatch;

public class BlockingQueueStrategy implements QueueStrategy {
    private final List<? extends BlockingQueue<WorkBatch>> gtmQueues;
    private final List<? extends BlockingQueue<WorkBatch>> mtgQueues;

    // Generators poll from mtgQueues, offer to gtmQueues
    private final QueueSelector<? extends BlockingQueue<WorkBatch>> generatorPollSelector;
    private final QueueSelector<? extends BlockingQueue<WorkBatch>> generatorOfferSelector;

    // Monkeys poll from gtmQueues, offer to mtgQueues
    private final QueueSelector<? extends BlockingQueue<WorkBatch>> monkeyPollSelector;
    private final QueueSelector<? extends BlockingQueue<WorkBatch>> monkeyOfferSelector;

    // Backoff strategies for generators and monkeys:
    private final BackoffStrategy generatorBackoff;
    private final BackoffStrategy monkeyBackoff;

    public BlockingQueueStrategy(List<? extends BlockingQueue<WorkBatch>> gtmQueues,
            List<? extends BlockingQueue<WorkBatch>> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends BlockingQueue<WorkBatch>> generatorPollSelector,
            QueueSelector<? extends BlockingQueue<WorkBatch>> generatorOfferSelector,
            QueueSelector<? extends BlockingQueue<WorkBatch>> monkeyPollSelector,
            QueueSelector<? extends BlockingQueue<WorkBatch>> monkeyOfferSelector,
            BackoffStrategy generatorBackoffStrategy, BackoffStrategy monkeyBackoffStrategy) {
        final int generatorCount = config.numThreads() / 2;
        final int monkeyCount = config.numThreads() / 2;
        final int standardQueueSize = config.queueSize();

        validateArguments(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, standardQueueSize, generatorCount,
                monkeyCount);

        this.gtmQueues = List.copyOf(gtmQueues);
        this.mtgQueues = List.copyOf(mtgQueues);
        this.generatorPollSelector = generatorPollSelector;
        this.generatorOfferSelector = generatorOfferSelector;
        this.monkeyPollSelector = monkeyPollSelector;
        this.monkeyOfferSelector = monkeyOfferSelector;
        this.generatorBackoff = generatorBackoffStrategy;
        this.monkeyBackoff = monkeyBackoffStrategy;

        preallocateInto(mtgQueues, config);
    }

    @Override
    public WorkBatch generatorPoll(int generatorId) throws InterruptedException {
        return generatorPollSelector.poll(generatorId, mtgQueues, generatorBackoff);
    }

    @Override
    public void generatorOffer(WorkBatch batch, int generatorId) throws InterruptedException {
        generatorOfferSelector.offer(batch, generatorId, gtmQueues, generatorBackoff);
    }

    @Override
    public WorkBatch monkeyPoll(int monkeyId) throws InterruptedException {
        return monkeyPollSelector.poll(monkeyId, gtmQueues, monkeyBackoff);
    }

    @Override
    public void monkeyOffer(WorkBatch batch, int monkeyId) throws InterruptedException {
        monkeyOfferSelector.offer(batch, monkeyId, mtgQueues, monkeyBackoff);
    }

    // Static factory methods for common configurations:
    private static final BackoffStrategy DEFAULT_BACKOFF = BackoffStrategy.noOp();

    // Single-Single Queue Strategy:
    // Generator poll - EXCLUSIVE (each generator polls from a single mtgQueue)
    // Generator offer - EXCLUSIVE (each generator offers to a single gtmQueue)
    // Monkey poll - EXCLUSIVE (each monkey polls from a single gtmQueue)
    // Monkey offer - EXCLUSIVE (each monkey offers to a single mtgQueue)

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        return new BlockingQueueStrategy(List.of(wrap(gtmQueue)), List.of(wrap(mtgQueue)), config,
                EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, generatorBackoff, monkeyBackoff);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config) {
        return singleSingle(gtmQueue, mtgQueue, config, DEFAULT_BACKOFF, DEFAULT_BACKOFF);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleSingle(
            SolverConfiguration config, int queueSize) {
        return singleSingle(new DisruptorBlockingQueue<>(queueSize),
                new DisruptorBlockingQueue<>(queueSize), config);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleSingle(
            SolverConfiguration config) {
        return singleSingle(config, config.queueSize());
    }

    // Single-Multi Queue Strategy:
    // Generator poll - Configurable strategy (can only be EXCLUSIVE if all mtgQueues support
    // multi-consumer, or if there is only one generator)
    // Generator offer - EXCLUSIVE (each generator offers to a single gtmQueue)
    // Monkey poll - EXCLUSIVE (each monkey polls from a single gtmQueue)
    // Monkey offer - Configurable strategy (can only be EXCLUSIVE if all mtgQueues support
    // multi-producer, or if there is only one monkey)

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleMulti(Q gtmQueue,
            List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final BlockingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<? extends BlockingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);

        // Ensure that the provided selectors meet the requirements for a single-multi strategy (as
        // described above).
        if (generatorPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedMtgQueues, "mtg");
        }
        if (monkeyOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedMtgQueues, "mtg");
        }

        return new BlockingQueueStrategy(List.of(wrappedGtmQueue), wrapAll(wrappedMtgQueues),
                config, generatorPollSelector, EXCLUSIVE, EXCLUSIVE, monkeyOfferSelector,
                generatorBackoff, monkeyBackoff);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy singleMulti(Q gtmQueue,
            List<? extends Q> mtgQueues, SolverConfiguration config) {
        return singleMulti(gtmQueue, mtgQueues, config, PREFERRED, PREFERRED, DEFAULT_BACKOFF,
                DEFAULT_BACKOFF);
    }

    public static BlockingQueueStrategy singleMulti(SolverConfiguration config, int queueSize) {
        // The gtmQueue should have a capacity equal to the total capacity of the mtgQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numMonkeys = config.numThreads() / 2;
        final DisruptorBlockingQueue<WorkBatch> gtmQueue = new DisruptorBlockingQueue<>(
                queueSize * numMonkeys);
        final List<PushPullBlockingQueue<WorkBatch>> mtgQueues = Stream
                .generate(() -> new PushPullBlockingQueue<WorkBatch>(queueSize)).limit(numMonkeys)
                .toList();
        return singleMulti(gtmQueue, mtgQueues, config);
    }

    public static BlockingQueueStrategy singleMulti(SolverConfiguration config) {
        return singleMulti(config, config.queueSize());
    }

    // Multi-Single Queue Strategy:
    // Generator poll - EXCLUSIVE (each generator polls from a single mtgQueue)
    // Generator offer - Configurable strategy (can only be EXCLUSIVE if all mtgQueues support
    // multi-producer, or if there is only one generator)
    // Monkey poll - Configurable strategy (can only be EXCLUSIVE if all mtgQueues support
    // multi-consumer, or if there is only one monkey)
    // Monkey offer - EXCLUSIVE (each monkey offers to a single mtgQueue)

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<? extends BlockingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final BlockingQueue<WorkBatch> wrappedMtgQueue = wrap(mtgQueue);

        // Ensure that the provided selectors meet the requirements for a multi-single strategy (as
        // described above).
        if (generatorOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedGtmQueues, "gtm");
        }

        return new BlockingQueueStrategy(wrappedGtmQueues, List.of(wrappedMtgQueue), config,
                EXCLUSIVE, generatorOfferSelector, monkeyPollSelector, EXCLUSIVE, generatorBackoff,
                monkeyBackoff);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config) {
        return multiSingle(gtmQueues, mtgQueue, config, PREFERRED, PREFERRED, DEFAULT_BACKOFF,
                DEFAULT_BACKOFF);
    }

    public static BlockingQueueStrategy multiSingle(SolverConfiguration config, int queueSize) {
        final int numGenerators = config.numThreads() / 2;
        final List<PushPullBlockingQueue<WorkBatch>> gtmQueues = Stream
                .generate(() -> new PushPullBlockingQueue<WorkBatch>(queueSize))
                .limit(numGenerators).toList();
        final DisruptorBlockingQueue<WorkBatch> mtgQueue = new DisruptorBlockingQueue<>(
                queueSize * numGenerators);
        return multiSingle(gtmQueues, mtgQueue, config);
    }

    public static BlockingQueueStrategy multiSingle(SolverConfiguration config) {
        return multiSingle(config, config.queueSize());
    }

    // Multi-Multi Queue Strategy:
    // Generator poll - Configurable strategy (can only be EXCLUSIVE if all mtgQueues support
    // multi-consumer, or if there is only one generator)
    // Generator offer - Configurable strategy (can only be EXCLUSIVE if all gtmQueues support
    // multi-producer, or if there is only one generator)
    // Monkey poll - Configurable strategy (can only be EXCLUSIVE if all gtmQueues support
    // multi-consumer, or if there is only one monkey)
    // Monkey offer - Configurable strategy (can only be EXCLUSIVE if all mtgQueues support
    // multi-producer, or if there is only one monkey)

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<? extends BlockingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final List<? extends BlockingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);

        // Ensure that the provided selectors meet the requirements for a multi-multi strategy (as
        // described above).
        if (generatorPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedMtgQueues, "mtg");
        }
        if (generatorOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedMtgQueues, "mtg");
        }

        return new BlockingQueueStrategy(gtmQueues, mtgQueues, config, generatorPollSelector,
                generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, generatorBackoff,
                monkeyBackoff);
    }

    public static <Q extends BlockingQueue<WorkBatch>> BlockingQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config) {
        return multiMulti(gtmQueues, mtgQueues, config, PREFERRED, PREFERRED, PREFERRED, PREFERRED,
                DEFAULT_BACKOFF, DEFAULT_BACKOFF);
    }

    public static BlockingQueueStrategy multiMulti(SolverConfiguration config, int queueSize) {
        final int numGenerators = config.numThreads() / 2;
        final List<PushPullBlockingQueue<WorkBatch>> gtmQueues = Stream
                .generate(() -> new PushPullBlockingQueue<WorkBatch>(queueSize))
                .limit(numGenerators).toList();
        final List<PushPullBlockingQueue<WorkBatch>> mtgQueues = Stream
                .generate(() -> new PushPullBlockingQueue<WorkBatch>(queueSize))
                .limit(numGenerators).toList();
        return multiMulti(gtmQueues, mtgQueues, config);
    }

    public static BlockingQueueStrategy multiMulti(SolverConfiguration config) {
        return multiMulti(config, config.queueSize());
    }
}

package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrap;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.BIASED_SEQUENTIAL;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.EXCLUSIVE;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.PREFERRED;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.ensureMultiConsumerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.ensureMultiProducerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.preallocateInto;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.validateArguments;

import java.util.List;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.WorkBatch;

public class JCToolsQueueStrategy implements QueueStrategy {
    private final List<? extends MessagePassingQueue<WorkBatch>> gtmQueues;
    private final List<? extends MessagePassingQueue<WorkBatch>> mtgQueues;

    // Generators poll from mtgQueues, offer to gtmQueues
    private final QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorPollSelector;
    private final QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorOfferSelector;

    // Monkeys poll from gtmQueues, offer to mtgQueues
    private final QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyPollSelector;
    private final QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyOfferSelector;

    // Backoff strategies for generators and monkeys:
    private final BackoffStrategy generatorBackoff;
    private final BackoffStrategy monkeyBackoff;

    public JCToolsQueueStrategy(List<? extends MessagePassingQueue<WorkBatch>> gtmQueues,
            List<? extends MessagePassingQueue<WorkBatch>> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorPollSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorOfferSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyPollSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyOfferSelector,
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
    private static final BackoffStrategy DEFAULT_GENERATOR_BACKOFF = BackoffStrategy.sleep(0,
            500_000);
    private static final BackoffStrategy DEFAULT_MONKEY_BACKOFF = BackoffStrategy.sleep(1, 0);

    // Single-Single Queue Strategy:
    // Generator poll - EXCLUSIVE (each generator polls from a single mtgQueue)
    // Generator offer - EXCLUSIVE (each generator offers to a single gtmQueue)
    // Monkey poll - EXCLUSIVE (each monkey polls from a single gtmQueue)
    // Monkey offer - EXCLUSIVE (each monkey offers to a single mtgQueue)

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        return new JCToolsQueueStrategy(List.of(wrap(gtmQueue)), List.of(wrap(mtgQueue)), config,
                EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, generatorBackoff, monkeyBackoff);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config) {
        return singleSingle(gtmQueue, mtgQueue, config, DEFAULT_GENERATOR_BACKOFF,
                DEFAULT_MONKEY_BACKOFF);
    }

    public static JCToolsQueueStrategy singleSingle(SolverConfiguration config, int queueSize) {
        return singleSingle(new MpmcArrayQueue<>(queueSize), new MpmcArrayQueue<>(queueSize),
                config);
    }

    public static JCToolsQueueStrategy singleSingle(SolverConfiguration config) {
        return singleSingle(config, config.queueSize());
    }

    // Single-Multi Queue Strategy:
    // Generator poll - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-consumer, or if there is only one generator)
    // Generator offer - EXCLUSIVE (each generator offers to a single gtmQueue)
    // Monkey poll - EXCLUSIVE (each monkey polls from a single gtmQueue)
    // Monkey offer - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-producer, or if there is only one monkey)

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleMulti(
            Q gtmQueue, List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final MessagePassingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<? extends MessagePassingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);

        // Ensure that the provided selectors meet the requirements for a single-multi strategy (as
        // described above).
        if (generatorPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedMtgQueues, "mtg");
        }
        if (monkeyOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedMtgQueues, "mtg");
        }

        return new JCToolsQueueStrategy(List.of(wrappedGtmQueue), wrapAll(wrappedMtgQueues), config,
                generatorPollSelector, EXCLUSIVE, EXCLUSIVE, monkeyOfferSelector, generatorBackoff,
                monkeyBackoff);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleMulti(
            Q gtmQueue, List<? extends Q> mtgQueues, SolverConfiguration config) {
        return singleMulti(gtmQueue, mtgQueues, config, BIASED_SEQUENTIAL, BIASED_SEQUENTIAL,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF);
    }

    public static JCToolsQueueStrategy singleMulti(SolverConfiguration config, int queueSize) {
        // The gtmQueue should have a capacity equal to the total capacity of the mtgQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numMonkeys = config.numThreads() / 2;
        final MpmcArrayQueue<WorkBatch> gtmQueue = new MpmcArrayQueue<>(queueSize * numMonkeys);
        final List<MpmcArrayQueue<WorkBatch>> mtgQueues = Stream
                .generate(() -> new MpmcArrayQueue<WorkBatch>(queueSize)).limit(numMonkeys)
                .toList();
        return singleMulti(gtmQueue, mtgQueues, config);
    }

    public static JCToolsQueueStrategy singleMulti(SolverConfiguration config) {
        return singleMulti(config, config.queueSize());
    }

    // Multi-Single Queue Strategy:
    // Generator poll - EXCLUSIVE (each generator polls from a single mtgQueue)
    // Generator offer - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-producer, or if there is only one generator)
    // Monkey poll - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-consumer, or if there is only one monkey)
    // Monkey offer - EXCLUSIVE (each monkey offers to a single mtgQueue)

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<? extends MessagePassingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final MessagePassingQueue<WorkBatch> wrappedMtgQueue = wrap(mtgQueue);

        // Ensure that the provided selectors meet the requirements for a multi-single strategy (as
        // described above).
        if (generatorOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedGtmQueues, "gtm");
        }

        return new JCToolsQueueStrategy(wrappedGtmQueues, List.of(wrappedMtgQueue), config,
                EXCLUSIVE, generatorOfferSelector, monkeyPollSelector, EXCLUSIVE, generatorBackoff,
                monkeyBackoff);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config) {
        return multiSingle(gtmQueues, mtgQueue, config, BIASED_SEQUENTIAL, BIASED_SEQUENTIAL,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF);
    }

    public static JCToolsQueueStrategy multiSingle(SolverConfiguration config, int queueSize) {
        // The mtgQueue should have a capacity equal to the total capacity of the gtmQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numGenerators = config.numThreads() / 2;
        final List<MpmcArrayQueue<WorkBatch>> gtmQueues = Stream
                .generate(() -> new MpmcArrayQueue<WorkBatch>(queueSize)).limit(numGenerators)
                .toList();
        final MpmcArrayQueue<WorkBatch> mtgQueue = new MpmcArrayQueue<>(queueSize * numGenerators);
        return multiSingle(gtmQueues, mtgQueue, config);
    }

    public static JCToolsQueueStrategy multiSingle(SolverConfiguration config) {
        return multiSingle(config, config.queueSize());
    }

    // Multi-Multi Queue Strategy:

    // Generator poll - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-consumer, or if there is only one generator)
    // Generator offer - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-producer, or if there is only one generator)
    // Monkey poll - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-consumer, or if there is only one monkey)
    // Monkey offer - Configurable strategy (can only be EXCLUSIVE if the mtgQueues all support
    // multi-producer, or if there is only one monkey)

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, BackoffStrategy generatorBackoff,
            BackoffStrategy monkeyBackoff) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<? extends MessagePassingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final List<? extends MessagePassingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);

        // Ensure that the provided selectors meet the requirements for a multi-multi strategy (as
        // described above).
        if (generatorPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedMtgQueues, "mtg");
        }
        if (generatorOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedMtgQueues, "mtg");
        }
        if (monkeyPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedGtmQueues, "gtm");
        }
        if (monkeyOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedGtmQueues, "gtm");
        }

        return new JCToolsQueueStrategy(wrappedGtmQueues, wrappedMtgQueues, config,
                generatorPollSelector, generatorOfferSelector, monkeyPollSelector,
                monkeyOfferSelector, generatorBackoff, monkeyBackoff);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config) {
        return multiMulti(gtmQueues, mtgQueues, config, PREFERRED, PREFERRED, PREFERRED, PREFERRED,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF);
    }

    public static JCToolsQueueStrategy multiMulti(SolverConfiguration config, int queueSize) {
        final int numThreads = config.numThreads();
        final List<SpscArrayQueue<WorkBatch>> gtmQueues = Stream
                .generate(() -> new SpscArrayQueue<WorkBatch>(queueSize)).limit(numThreads)
                .toList();
        final List<SpscArrayQueue<WorkBatch>> mtgQueues = Stream
                .generate(() -> new SpscArrayQueue<WorkBatch>(queueSize)).limit(numThreads)
                .toList();
        return multiMulti(gtmQueues, mtgQueues, config);
    }

    public static JCToolsQueueStrategy multiMulti(SolverConfiguration config) {
        return multiMulti(config, config.queueSize());
    }
}

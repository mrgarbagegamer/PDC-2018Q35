package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forGenerator;
import static com.github.mrgarbagegamer.queues.ContinuationPredicates.forMonkeyJCTools;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrap;
import static com.github.mrgarbagegamer.queues.JCToolsWrappers.wrapAll;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.BIASED_SEQUENTIAL;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.EXCLUSIVE;
import static com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors.PREFERRED;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.ensureMultiConsumerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.ensureMultiProducerSupport;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.preallocateInto;
import static com.github.mrgarbagegamer.queues.QueueUtils.JCToolsUtils.validateArguments;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import com.github.mrgarbagegamer.QueueStrategy;
import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.SolverState;
import com.github.mrgarbagegamer.WorkBatch;

// TODO: Add Javadocs for the class and its methods
// TODO: Write unit tests for the class.
public class JCToolsQueueStrategy implements QueueStrategy {
    private final List<MessagePassingQueue<WorkBatch>> gtmQueues;
    private final List<MessagePassingQueue<WorkBatch>> mtgQueues;

    // Generators poll from mtgQueues, offer to gtmQueues
    private final QueueSelector<MessagePassingQueue<WorkBatch>> generatorPollSelector;
    private final QueueSelector<MessagePassingQueue<WorkBatch>> generatorOfferSelector;

    // Monkeys poll from gtmQueues, offer to mtgQueues
    private final QueueSelector<MessagePassingQueue<WorkBatch>> monkeyPollSelector;
    private final QueueSelector<MessagePassingQueue<WorkBatch>> monkeyOfferSelector;

    // Backoff strategies for generators and monkeys:
    private final BackoffStrategy generatorBackoff;
    private final BackoffStrategy monkeyBackoff;

    // Continuation predicates — checked before each backoff in the selectors
    private final BooleanSupplier generatorShouldContinue;
    private final BooleanSupplier monkeyShouldContinue;

    @SuppressWarnings("unchecked")
    public JCToolsQueueStrategy(List<? extends MessagePassingQueue<WorkBatch>> gtmQueues,
            List<? extends MessagePassingQueue<WorkBatch>> mtgQueues, SolverConfiguration config,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorPollSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> generatorOfferSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyPollSelector,
            QueueSelector<? extends MessagePassingQueue<WorkBatch>> monkeyOfferSelector,
            BackoffStrategy generatorBackoffStrategy, BackoffStrategy monkeyBackoffStrategy,
            BooleanSupplier generatorShouldContinue, BooleanSupplier monkeyShouldContinue) {
        final int generatorCount = config.numThreads() / 2;
        final int monkeyCount = config.numThreads() / 2;
        final int standardQueueSize = config.queueSize();

        validateArguments(gtmQueues, mtgQueues, generatorPollSelector, generatorOfferSelector,
                monkeyPollSelector, monkeyOfferSelector, standardQueueSize, generatorCount,
                monkeyCount);

        this.gtmQueues = List.copyOf(gtmQueues);
        this.mtgQueues = List.copyOf(mtgQueues);
        this.generatorPollSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) generatorPollSelector;
        this.generatorOfferSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) generatorOfferSelector;
        this.monkeyPollSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) monkeyPollSelector;
        this.monkeyOfferSelector = (QueueSelector<MessagePassingQueue<WorkBatch>>) monkeyOfferSelector;
        this.generatorBackoff = generatorBackoffStrategy;
        this.monkeyBackoff = monkeyBackoffStrategy;
        this.generatorShouldContinue = requireNonNull(generatorShouldContinue,
                "generatorShouldContinue must not be null");
        this.monkeyShouldContinue = requireNonNull(monkeyShouldContinue,
                "monkeyShouldContinue must not be null");

        preallocateInto(mtgQueues, config);
    }

    @Override
    public WorkBatch generatorPoll(int generatorId) {
        return generatorPollSelector.poll(generatorId, mtgQueues, generatorBackoff,
                generatorShouldContinue);
    }

    @Override
    public boolean generatorOffer(WorkBatch batch, int generatorId) {
        return generatorOfferSelector.offer(batch, generatorId, gtmQueues, generatorBackoff,
                generatorShouldContinue);
    }

    @Override
    public WorkBatch monkeyPoll(int monkeyId) {
        return monkeyPollSelector.poll(monkeyId, gtmQueues, monkeyBackoff, monkeyShouldContinue);
    }

    @Override
    public boolean monkeyOffer(WorkBatch batch, int monkeyId) {
        return monkeyOfferSelector.offer(batch, monkeyId, mtgQueues, monkeyBackoff,
                monkeyShouldContinue);
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
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        final MessagePassingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<MessagePassingQueue<WorkBatch>> wrappedMtgQueues = List.of(wrap(mtgQueue));

        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, wrappedGtmQueue);

        return new JCToolsQueueStrategy(List.of(wrappedGtmQueue), wrappedMtgQueues, config,
                EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, generatorBackoff, monkeyBackoff,
                generatorShouldContinue, monkeyShouldContinue);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleSingle(
            Q gtmQueue, Q mtgQueue, SolverConfiguration config, SolverState solverState) {
        return singleSingle(gtmQueue, mtgQueue, config, DEFAULT_GENERATOR_BACKOFF,
                DEFAULT_MONKEY_BACKOFF, solverState);
    }

    public static JCToolsQueueStrategy singleSingle(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        return singleSingle(new MpmcArrayQueue<>(queueSize), new MpmcArrayQueue<>(queueSize),
                config, solverState);
    }

    public static JCToolsQueueStrategy singleSingle(SolverConfiguration config,
            SolverState solverState) {
        return singleSingle(config, config.queueSize(), solverState);
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
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final MessagePassingQueue<WorkBatch> wrappedGtmQueue = wrap(gtmQueue);
        final List<MessagePassingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState, wrappedGtmQueue);

        // Ensure that the provided selectors meet the requirements for a single-multi strategy (as
        // described above).
        if (generatorPollSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiConsumerSupport(wrappedMtgQueues, "mtg");
        }
        if (monkeyOfferSelector == EXCLUSIVE && config.numThreads() > 2) {
            ensureMultiProducerSupport(wrappedMtgQueues, "mtg");
        }

        return new JCToolsQueueStrategy(List.of(wrappedGtmQueue), wrappedMtgQueues, config,
                generatorPollSelector, EXCLUSIVE, EXCLUSIVE, monkeyOfferSelector, generatorBackoff,
                monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy singleMulti(
            Q gtmQueue, List<? extends Q> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return singleMulti(gtmQueue, mtgQueues, config, BIASED_SEQUENTIAL, BIASED_SEQUENTIAL,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF, solverState);
    }

    public static JCToolsQueueStrategy singleMulti(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        // The gtmQueue should have a capacity equal to the total capacity of the mtgQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numMonkeys = config.numThreads() / 2;
        final MpmcArrayQueue<WorkBatch> gtmQueue = new MpmcArrayQueue<>(queueSize * numMonkeys);
        final List<MpmcArrayQueue<WorkBatch>> mtgQueues = Stream
                .generate(() -> new MpmcArrayQueue<WorkBatch>(queueSize)).limit(numMonkeys)
                .toList();
        return singleMulti(gtmQueue, mtgQueues, config, solverState);
    }

    public static JCToolsQueueStrategy singleMulti(SolverConfiguration config,
            SolverState solverState) {
        return singleMulti(config, config.queueSize(), solverState);
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
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<MessagePassingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final MessagePassingQueue<WorkBatch> wrappedMtgQueue = wrap(mtgQueue);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState,
                wrappedGtmQueues);

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
                monkeyBackoff, generatorShouldContinue, monkeyShouldContinue);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiSingle(
            List<? extends Q> gtmQueues, Q mtgQueue, SolverConfiguration config,
            SolverState solverState) {
        return multiSingle(gtmQueues, mtgQueue, config, BIASED_SEQUENTIAL, BIASED_SEQUENTIAL,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF, solverState);
    }

    public static JCToolsQueueStrategy multiSingle(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        // The mtgQueue should have a capacity equal to the total capacity of the gtmQueues (the
        // passed queueSize) to ensure that the in-flight batch limit is consistent.
        final int numGenerators = config.numThreads() / 2;
        final List<MpmcArrayQueue<WorkBatch>> gtmQueues = Stream
                .generate(() -> new MpmcArrayQueue<WorkBatch>(queueSize)).limit(numGenerators)
                .toList();
        final MpmcArrayQueue<WorkBatch> mtgQueue = new MpmcArrayQueue<>(queueSize * numGenerators);
        return multiSingle(gtmQueues, mtgQueue, config, solverState);
    }

    public static JCToolsQueueStrategy multiSingle(SolverConfiguration config,
            SolverState solverState) {
        return multiSingle(config, config.queueSize(), solverState);
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
            BackoffStrategy monkeyBackoff, SolverState solverState) {
        // Wrap the queues now so the validations below can check the correct types (e.g. whether
        // they support multi-producer or multi-consumer).
        final List<MessagePassingQueue<WorkBatch>> wrappedGtmQueues = wrapAll(gtmQueues);
        final List<MessagePassingQueue<WorkBatch>> wrappedMtgQueues = wrapAll(mtgQueues);
        final BooleanSupplier generatorShouldContinue = forGenerator(solverState);
        final BooleanSupplier monkeyShouldContinue = forMonkeyJCTools(solverState,
                wrappedGtmQueues);

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

        return new JCToolsQueueStrategy(wrappedGtmQueues, wrappedMtgQueues, config,
                generatorPollSelector, generatorOfferSelector, monkeyPollSelector,
                monkeyOfferSelector, generatorBackoff, monkeyBackoff, generatorShouldContinue,
                monkeyShouldContinue);
    }

    public static <Q extends MessagePassingQueue<WorkBatch>> JCToolsQueueStrategy multiMulti(
            List<? extends Q> gtmQueues, List<? extends Q> mtgQueues, SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(gtmQueues, mtgQueues, config, PREFERRED, PREFERRED, PREFERRED, PREFERRED,
                DEFAULT_GENERATOR_BACKOFF, DEFAULT_MONKEY_BACKOFF, solverState);
    }

    public static JCToolsQueueStrategy multiMulti(SolverConfiguration config, int queueSize,
            SolverState solverState) {
        final int numThreads = config.numThreads();
        final List<SpscArrayQueue<WorkBatch>> gtmQueues = Stream
                .generate(() -> new SpscArrayQueue<WorkBatch>(queueSize)).limit(numThreads)
                .toList();
        final List<SpscArrayQueue<WorkBatch>> mtgQueues = Stream
                .generate(() -> new SpscArrayQueue<WorkBatch>(queueSize)).limit(numThreads)
                .toList();
        return multiMulti(gtmQueues, mtgQueues, config, solverState);
    }

    public static JCToolsQueueStrategy multiMulti(SolverConfiguration config,
            SolverState solverState) {
        return multiMulti(config, config.queueSize(), solverState);
    }
}

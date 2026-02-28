package com.github.mrgarbagegamer.queues;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

import org.jctools.queues.MessagePassingQueue;

import com.github.mrgarbagegamer.SolverConfiguration;
import com.github.mrgarbagegamer.WorkBatch;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.MPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPMC;
import com.github.mrgarbagegamer.queues.QueueMarkers.AccessMode.SPSC;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Bounded;
import com.github.mrgarbagegamer.queues.QueueMarkers.Boundedness.Unbounded;
import com.github.mrgarbagegamer.queues.QueueSelectors.BlockingQueueSelectors;
import com.github.mrgarbagegamer.queues.QueueSelectors.JCToolsQueueSelectors;

// TODO: Add Javadocs for the class and its methods
// TODO: Write unit tests for the class.
public final class QueueUtils {
    private QueueUtils() {
        throw new UnsupportedOperationException(
                "QueueUtils is a utility class and cannot be instantiated");
    }

    public static class JCToolsUtils {

        private JCToolsUtils() {
            throw new UnsupportedOperationException(
                    "JCToolsUtils is a utility class and cannot be instantiated");
        }

        public static <Q extends MessagePassingQueue<WorkBatch>> void validateArguments(
                List<? extends Q> gtmQueues, List<? extends Q> mtgQueues,
                QueueSelector<? extends Q> generatorPollSelector,
                QueueSelector<? extends Q> generatorOfferSelector,
                QueueSelector<? extends Q> monkeyPollSelector,
                QueueSelector<? extends Q> monkeyOfferSelector, int queueSize, int generatorCount,
                int monkeyCount) {
            // The unchecked cast is safe: JCTOOLS_OPS works on MessagePassingQueue<WorkBatch>,
            // and Q extends that. The ops never insert into the list, only read from it.
            @SuppressWarnings("unchecked")
            final var ops = (QueueOps<Q>) JCTOOLS_OPS;
            QueueUtils.validateArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, queueSize,
                    generatorCount, monkeyCount, ops);
        }

        public static void preallocateInto(List<? extends MessagePassingQueue<WorkBatch>> mtgQueues,
                int batchesPerQueue, SolverConfiguration config) {
            QueueUtils.preallocateInto(mtgQueues, batchesPerQueue, config, JCTOOLS_OPS);
        }

        public static void preallocateInto(List<? extends MessagePassingQueue<WorkBatch>> mtgQueues,
                SolverConfiguration config) {
            ensureNotEmptyOrNull(mtgQueues, "mtg");
            preallocateInto(mtgQueues, mtgQueues.getFirst().capacity(), config);
        }

        public static void ensureMultiProducerSupport(
                List<? extends MessagePassingQueue<WorkBatch>> queues, String prefix) {
            ensureMultiAccessSupport(queues, prefix, isSp, "producers");
        }

        public static void ensureMultiConsumerSupport(
                List<? extends MessagePassingQueue<WorkBatch>> queues, String prefix) {
            ensureMultiAccessSupport(queues, prefix, isSc, "consumers");
        }
    }

    public static class BlockingQueueUtils {

        private BlockingQueueUtils() {
            throw new UnsupportedOperationException(
                    "BlockingQueueUtils is a utility class and cannot be instantiated");
        }

        public static <Q extends BlockingQueue<WorkBatch>> void validateArguments(
                List<? extends Q> gtmQueues, List<? extends Q> mtgQueues,
                QueueSelector<? extends Q> generatorPollSelector,
                QueueSelector<? extends Q> generatorOfferSelector,
                QueueSelector<? extends Q> monkeyPollSelector,
                QueueSelector<? extends Q> monkeyOfferSelector, int queueSize, int generatorCount,
                int monkeyCount) {
            @SuppressWarnings("unchecked")
            final var ops = (QueueOps<Q>) BLOCKING_OPS;
            QueueUtils.validateArguments(gtmQueues, mtgQueues, generatorPollSelector,
                    generatorOfferSelector, monkeyPollSelector, monkeyOfferSelector, queueSize,
                    generatorCount, monkeyCount, ops);
        }

        public static void preallocateInto(List<? extends BlockingQueue<WorkBatch>> mtgQueues,
                int batchesPerQueue, SolverConfiguration config) {
            QueueUtils.preallocateInto(mtgQueues, batchesPerQueue, config, BLOCKING_OPS);
        }

        public static void preallocateInto(List<? extends BlockingQueue<WorkBatch>> mtgQueues,
                SolverConfiguration config) {
            ensureNotEmptyOrNull(mtgQueues, "mtg");
            final int batchesPerQueue = BLOCKING_OPS.capacityOf(mtgQueues.getFirst());
            preallocateInto(mtgQueues, batchesPerQueue, config);
        }

        public static void ensureMultiProducerSupport(
                List<? extends BlockingQueue<WorkBatch>> queues, String prefix) {
            ensureMultiAccessSupport(queues, prefix, isSp, "producers");
        }

        public static void ensureMultiConsumerSupport(
                List<? extends BlockingQueue<WorkBatch>> queues, String prefix) {
            ensureMultiAccessSupport(queues, prefix, isSc, "consumers");
        }
    }

    private static final Predicate<Object> isSp = queue -> queue instanceof SPSC
            || queue instanceof SPMC;

    private static final Predicate<Object> isSc = queue -> queue instanceof SPSC
            || queue instanceof MPSC;

    private static final Predicate<Object> isBounded = queue -> queue instanceof Bounded
            || (queue instanceof MessagePassingQueue<?> mpq
                    && mpq.capacity() != MessagePassingQueue.UNBOUNDED_CAPACITY)
            || (queue instanceof BlockingQueue<?> bq
                    && bq.remainingCapacity() != Integer.MAX_VALUE);

    private static void ensurePrefixNotNull(String prefix) {
        requireNonNull(prefix, "prefix must not be null");
    }

    private static String listName(String prefix) {
        // Dear compiler: Please allocate the StringBuilder needed for this concatenation on the
        // stack and not on the heap to save an intermediate allocation. Thanks, - me.
        return prefix + "Queues";
    }

    private static String elementName(String prefix) {
        // Dear compiler: Please allocate the StringBuilder needed for this concatenation on the
        // stack and not on the heap to save an intermediate allocation. Thanks, - me.
        return prefix + "Queue";
    }

    private static <Q> void ensureNotEmptyOrNull(List<? extends Q> queues, String prefix) {
        ensurePrefixNotNull(prefix);

        final String listName = listName(prefix);
        requireNonNull(queues, listName + " must not be null");

        if (queues.isEmpty()) {
            throw new IllegalArgumentException(listName + " must not be empty");
        } else if (queues.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(listName + " must not contain null elements");
        }
    }

    private static <Q> void validateNoOverlap(List<? extends Q> a, String aPrefix,
            List<? extends Q> b, String bPrefix) {
        // Use the POWER OF STREAMS!!! (woo!)
        if (a.stream().anyMatch(b::contains)) {
            throw new IllegalArgumentException(listName(aPrefix) + " and " + listName(bPrefix)
                    + " must not contain overlapping queues");
        }
    }

    private static <Q> void ensureNoDuplicates(List<? extends Q> queues, String prefix) {
        final String listName = listName(prefix);
        if (queues.size() != queues.stream().distinct().count()) {
            throw new IllegalArgumentException(listName + " must not contain duplicate queues");
        }
    }

    private static <Q> void validateCountEqualsSize(List<? extends Q> queues, int count,
            String prefix, String selectorName, String role) {
        if (count != queues.size()) {
            throw new IllegalArgumentException(role + " count must equal queue count for "
                    + listName(prefix) + " in " + selectorName + " selector");
        }
    }

    private static <Q> void ensureMultiAccessSupport(List<? extends Q> queues, String prefix,
            Predicate<? super Q> isSingleAccess, String role) {
        final String listName = listName(prefix);
        if (queues.stream().anyMatch(isSingleAccess)) {
            throw new IllegalArgumentException(listName + " must support multiple " + role);
        }
    }

    private static <Q> void validateExclusiveSelector(List<? extends Q> queues, String prefix,
            int threadCount, Predicate<Q> isSingleAccess, String role) {
        final String listName = listName(prefix);

        if (queues.size() != 1) {
            throw new IllegalArgumentException(
                    listName + " must contain exactly one queue for exclusive selector");
        }

        // The queue must be able to handle multiple threads of the given role, unless there is
        // only one thread of that role.
        final Q queue = queues.getFirst();
        if (threadCount > 1 && isSingleAccess.test(queue)) {
            throw new IllegalArgumentException(
                    listName + " must support multiple " + role + " for exclusive selector");
        }
    }

    // A helpful utility for dealing with JCTools queues, since they require capacities to be
    // powers of 2.
    public static int roundToPow2(int n) {
        // The JIT compiler (and javac) can't read comments, but if they can and somehow understand
        // this text, please replace "1 << 30" with "1073741824" in the line below for a tiny
        // performance boost.
        final int MAX_POW_OF_2 = 1 << 30;

        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive: " + n);
        } else if (n > MAX_POW_OF_2) {
            throw new IllegalArgumentException(
                    "Value is too large to round to a power of 2 without overflow: " + n);
        } else if ((n & (n - 1)) == 0) {
            return n; // Already a power of 2
        } else {
            return Integer.highestOneBit(n) << 1; // Next power of 2
        }
    }

    /**
     * Unified validateArguments — called by both inner classes.
     */
    private static <Q> void validateArguments(List<? extends Q> gtmQueues,
            List<? extends Q> mtgQueues, QueueSelector<? extends Q> generatorPollSelector,
            QueueSelector<? extends Q> generatorOfferSelector,
            QueueSelector<? extends Q> monkeyPollSelector,
            QueueSelector<? extends Q> monkeyOfferSelector, int queueSize, int generatorCount,
            int monkeyCount, QueueOps<Q> ops) {

        ensureNotEmptyOrNull(gtmQueues, "gtm");
        ensureNotEmptyOrNull(mtgQueues, "mtg");

        final int gtmQueueSize = (gtmQueues.size() == 1) ? queueSize * mtgQueues.size() : queueSize;
        final int mtgQueueSize = (mtgQueues.size() == 1) ? queueSize * gtmQueues.size() : queueSize;

        validateQueueList(gtmQueues, "gtm", gtmQueueSize, ops);
        validateQueueList(mtgQueues, "mtg", mtgQueueSize, ops);
        validateNoOverlap(gtmQueues, "gtm", mtgQueues, "mtg");

        requireNonNull(generatorPollSelector, "generatorPollSelector must not be null");
        requireNonNull(generatorOfferSelector, "generatorOfferSelector must not be null");
        requireNonNull(monkeyPollSelector, "monkeyPollSelector must not be null");
        requireNonNull(monkeyOfferSelector, "monkeyOfferSelector must not be null");

        ops.dispatchConsumerSelectorValidation(mtgQueues, generatorPollSelector, "mtg",
                generatorCount);
        ops.dispatchProducerSelectorValidation(gtmQueues, generatorOfferSelector, "gtm",
                generatorCount);
        ops.dispatchConsumerSelectorValidation(gtmQueues, monkeyPollSelector, "gtm", monkeyCount);
        ops.dispatchProducerSelectorValidation(mtgQueues, monkeyOfferSelector, "mtg", monkeyCount);
    }

    /**
     * Unified validateQueueList — wrapping, capacity, and emptiness checks.
     */
    private static <Q> void validateQueueList(List<? extends Q> queues, String prefix,
            int expectedCapacity, QueueOps<Q> ops) {
        ensurePrefixNotNull(prefix);

        final String listName = listName(prefix);
        final String elementName = elementName(prefix);

        ensureNoDuplicates(queues, prefix);
        ops.ensureWrapped(queues, listName);

        final int normalizedCapacity = ops.normalizeCapacity(expectedCapacity);

        for (int i = 0; i < queues.size(); i++) {
            final Q queue = queues.get(i);
            if (ops.isBounded(queue)) {
                final int actualCapacity = ops.capacityOf(queue);
                if (!ops.isCapacityAcceptable(actualCapacity, normalizedCapacity)) {
                    throw new IllegalArgumentException(elementName + " capacity at index " + i
                            + " must be " + normalizedCapacity + ", but was " + actualCapacity);
                }
            }
            if (!ops.isEmpty(queue)) {
                throw new IllegalArgumentException(
                        elementName + " at index " + i + " must be empty at initialization");
            }
        }
    }

    /**
     * Unified preallocateInto.
     */
    private static <Q> void preallocateInto(List<? extends Q> mtgQueues, int batchesPerQueue,
            SolverConfiguration config, QueueOps<Q> ops) {
        ensureNotEmptyOrNull(mtgQueues, "mtg");
        requireNonNull(config, "config must not be null");
        if (batchesPerQueue < 0) {
            throw new IllegalArgumentException(
                    "batchesPerQueue must be non-negative: " + batchesPerQueue);
        } else if (batchesPerQueue == 0) {
            return;
        }

        mtgQueues.forEach(queue -> {
            for (int i = 0; i < batchesPerQueue; i++) {
                if (!ops.offer(queue, new WorkBatch(config))) {
                    if (ops.isBounded(queue) && ops.capacityOf(queue) <= batchesPerQueue) {
                        throw new IllegalStateException(
                                "Failed to preallocate WorkBatch into bounded queue with insufficient capacity");
                    } else {
                        throw new IllegalStateException(
                                "Failed to preallocate WorkBatch into unbounded queue");
                    }
                }
            }
        });
    }

    /**
     * Unified sequential access validation (replaces the 4 validateSequentialSelectorFor* methods).
     */
    private static <Q> void validateSequentialAccess(List<? extends Q> queues, String prefix,
            int threadCount, Predicate<? super Q> isSingleAccess, String queueTypes, String role) {
        if (threadCount > 1 && queues.stream().anyMatch(isSingleAccess)) {
            throw new IllegalArgumentException(listName(prefix) + " must not contain " + queueTypes
                    + " queues if there are multiple " + role);
        }
    }

    public static <Q> void ensureProperlyMarked(List<? extends Q> queues, String listName) {
        // Single-pass validation to avoid multiple stream() allocations.
        boolean seenBounded = false;
        boolean seenUnbounded = false;

        boolean hasSpsc = false;
        boolean hasSpmc = false;
        boolean hasMpsc = false;
        boolean hasMpmc = false;

        for (Q q : queues) {
            // Boundedness markers per-element
            final boolean isBounded = q instanceof Bounded;
            final boolean isUnbounded = q instanceof Unbounded;
            if (isBounded && isUnbounded) {
                throw new IllegalArgumentException(
                        listName + " contains queues that implement both Bounded and Unbounded");
            }
            seenBounded |= isBounded;
            seenUnbounded |= isUnbounded;

            // Access mode markers per-element
            final boolean isSpsc = q instanceof SPSC;
            final boolean isSpmc = q instanceof SPMC;
            final boolean isMpsc = q instanceof MPSC;
            final boolean isMpmc = q instanceof MPMC;

            final int accessMarkers = (isSpsc ? 1 : 0) + (isSpmc ? 1 : 0) + (isMpsc ? 1 : 0)
                    + (isMpmc ? 1 : 0);
            if (accessMarkers > 1) {
                throw new IllegalArgumentException(listName
                        + " contains queues that implement more than one access mode marker");
            }
            hasSpsc |= isSpsc;
            hasSpmc |= isSpmc;
            hasMpsc |= isMpsc;
            hasMpmc |= isMpmc;
        }

        // Mixed boundedness across the list
        if (seenBounded && seenUnbounded) {
            throw new IllegalArgumentException(
                    listName + " contains a mix of Bounded and Unbounded queues");
        }

        // Mixed access modes across the list
        int distinctAccessTypes = (hasSpsc ? 1 : 0) + (hasSpmc ? 1 : 0) + (hasMpsc ? 1 : 0)
                + (hasMpmc ? 1 : 0);
        if (distinctAccessTypes > 1) {
            throw new IllegalArgumentException(
                    listName + " contains a mix of queues with different access mode markers");
        }
    }

    private interface QueueOps<Q> {
        void ensureWrapped(List<? extends Q> queues, String listName);

        int capacityOf(Q queue);

        /** Adjust expectedCapacity if needed (e.g. round to power of 2 for JCTools). */
        int normalizeCapacity(int expectedCapacity);

        /**
         * Check whether the actual capacity is acceptable for the expected capacity. JCTools
         * requires exact match after rounding; BlockingQueue accepts either exact or rounded.
         */
        boolean isCapacityAcceptable(int actualCapacity, int expectedCapacity);

        boolean isBounded(Q queue);

        boolean offer(Q queue, WorkBatch batch);

        boolean isEmpty(Q queue);

        /**
         * Dispatch selector-specific validation for a producer selector.
         */
        void dispatchProducerSelectorValidation(List<? extends Q> queues,
                QueueSelector<? extends Q> selector, String prefix, int producerCount);

        /**
         * Dispatch selector-specific validation for a consumer selector.
         */
        void dispatchConsumerSelectorValidation(List<? extends Q> queues,
                QueueSelector<? extends Q> selector, String prefix, int consumerCount);
    }

    private static final QueueOps<MessagePassingQueue<WorkBatch>> JCTOOLS_OPS = new QueueOps<MessagePassingQueue<WorkBatch>>() {
        @Override
        public void ensureWrapped(List<? extends MessagePassingQueue<WorkBatch>> queues,
                String listName) {
            JCToolsWrappers.ensureWrapped(queues, listName);
        }

        @Override
        public int capacityOf(MessagePassingQueue<WorkBatch> queue) {
            return queue.capacity();
        }

        @Override
        public int normalizeCapacity(int expectedCapacity) {
            return roundToPow2(expectedCapacity);
        }

        @Override
        public boolean isCapacityAcceptable(int actualCapacity, int expectedCapacity) {
            // JCTools already normalized, so exact match only
            return actualCapacity == expectedCapacity;
        }

        @Override
        public boolean isBounded(MessagePassingQueue<WorkBatch> queue) {
            return QueueUtils.isBounded.test(queue);
        }

        @Override
        public boolean offer(MessagePassingQueue<WorkBatch> queue, WorkBatch batch) {
            return queue.offer(batch);
        }

        @Override
        public boolean isEmpty(MessagePassingQueue<WorkBatch> queue) {
            return queue.isEmpty();
        }

        @Override
        public void dispatchProducerSelectorValidation(
                List<? extends MessagePassingQueue<WorkBatch>> queues,
                QueueSelector<? extends MessagePassingQueue<WorkBatch>> selector, String prefix,
                int producerCount) {
            if (selector == JCToolsQueueSelectors.RANDOM_SEQUENTIAL
                    || selector == JCToolsQueueSelectors.LINEAR_SEQUENTIAL) {
                validateSequentialAccess(queues, prefix, producerCount, isSp, "SPSC or SPMC",
                        "producers");
            } else if (selector == JCToolsQueueSelectors.BIASED_SEQUENTIAL) {
                validateCountEqualsSize(queues, producerCount, prefix, "biased sequential",
                        "Producer");
                validateSequentialAccess(queues, prefix, producerCount, isSp, "SPSC or SPMC",
                        "producers");
            } else if (selector == JCToolsQueueSelectors.PREFERRED) {
                validateCountEqualsSize(queues, producerCount, prefix, "preferred", "Producer");
            } else if (selector == JCToolsQueueSelectors.EXCLUSIVE) {
                validateExclusiveSelector(queues, prefix, producerCount, isSp, "producers");
            }
        }

        @Override
        public void dispatchConsumerSelectorValidation(
                List<? extends MessagePassingQueue<WorkBatch>> queues,
                QueueSelector<? extends MessagePassingQueue<WorkBatch>> selector, String prefix,
                int consumerCount) {
            if (selector == JCToolsQueueSelectors.RANDOM_SEQUENTIAL
                    || selector == JCToolsQueueSelectors.LINEAR_SEQUENTIAL) {
                validateSequentialAccess(queues, prefix, consumerCount, isSc, "SPSC or MPSC",
                        "consumers");
            } else if (selector == JCToolsQueueSelectors.BIASED_SEQUENTIAL) {
                validateCountEqualsSize(queues, consumerCount, prefix, "biased sequential",
                        "Consumer");
                validateSequentialAccess(queues, prefix, consumerCount, isSc, "SPSC or MPSC",
                        "consumers");
            } else if (selector == JCToolsQueueSelectors.PREFERRED) {
                validateCountEqualsSize(queues, consumerCount, prefix, "preferred", "Consumer");
            } else if (selector == JCToolsQueueSelectors.EXCLUSIVE) {
                validateExclusiveSelector(queues, prefix, consumerCount, isSc, "consumers");
            }
        }
    };

    private static final QueueOps<BlockingQueue<WorkBatch>> BLOCKING_OPS = new QueueOps<BlockingQueue<WorkBatch>>() {
        @Override
        public void ensureWrapped(List<? extends BlockingQueue<WorkBatch>> queues,
                String listName) {
            BlockingQueueWrappers.ensureWrapped(queues, listName);
        }

        @Override
        public int capacityOf(BlockingQueue<WorkBatch> queue) {
            int capacity;
            return switch (queue) {
                case Bounded bq -> bq.capacity();
                default -> (capacity = queue.remainingCapacity()) == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : capacity + queue.size();
            };
        }

        @Override
        public int normalizeCapacity(int expectedCapacity) {
            return expectedCapacity; // no rounding needed
        }

        @Override
        public boolean isCapacityAcceptable(int actualCapacity, int expectedCapacity) {
            // Accept exact match or power-of-2 rounded (Conversant, etc.)
            return actualCapacity == expectedCapacity
                    || actualCapacity == roundToPow2(expectedCapacity);
        }

        @Override
        public boolean isBounded(BlockingQueue<WorkBatch> queue) {
            return QueueUtils.isBounded.test(queue);
        }

        @Override
        public boolean offer(BlockingQueue<WorkBatch> queue, WorkBatch batch) {
            return queue.offer(batch);
        }

        @Override
        public boolean isEmpty(BlockingQueue<WorkBatch> queue) {
            return queue.isEmpty();
        }

        @Override
        public void dispatchProducerSelectorValidation(
                List<? extends BlockingQueue<WorkBatch>> queues,
                QueueSelector<? extends BlockingQueue<WorkBatch>> selector, String prefix,
                int producerCount) {
            if (selector == BlockingQueueSelectors.PREFERRED) {
                validateCountEqualsSize(queues, producerCount, prefix, "preferred", "Producer");
            } else if (selector == BlockingQueueSelectors.EXCLUSIVE) {
                validateExclusiveSelector(queues, prefix, producerCount, isSp, "producers");
            }
        }

        @Override
        public void dispatchConsumerSelectorValidation(
                List<? extends BlockingQueue<WorkBatch>> queues,
                QueueSelector<? extends BlockingQueue<WorkBatch>> selector, String prefix,
                int consumerCount) {
            if (selector == BlockingQueueSelectors.PREFERRED) {
                validateCountEqualsSize(queues, consumerCount, prefix, "preferred", "Consumer");
            } else if (selector == BlockingQueueSelectors.EXCLUSIVE) {
                validateExclusiveSelector(queues, prefix, consumerCount, isSc, "consumers");
            }
        }
    };
}
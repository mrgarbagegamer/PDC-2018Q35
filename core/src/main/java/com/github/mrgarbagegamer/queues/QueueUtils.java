package com.github.mrgarbagegamer.queues;

import static com.github.mrgarbagegamer.internal.ValidationUtils.utilityClassError;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.github.mrgarbagegamer.internal.ExcludeFromGeneratedCoverage;

final class QueueUtils {

    @ExcludeFromGeneratedCoverage
    private QueueUtils() { throw utilityClassError("QueueUtils"); }

    // List creation utility methods:

    static <Q> List<Q> newImmutableQueueList(int listSize, Supplier<? extends Q> elementSupplier) {
        return Stream.generate(elementSupplier).limit(listSize).collect(toUnmodifiableList());
    }

    static <Q> List<Q> newBoundedImmutableQueueList(int listSize, int queueCapacity,
            IntFunction<? extends Q> elementConstructor) {
        return newImmutableQueueList(listSize, () -> elementConstructor.apply(queueCapacity));
    }
}

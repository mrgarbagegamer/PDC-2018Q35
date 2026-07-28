package com.github.mrgarbagegamer.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.google.errorprone.annotations.RestrictedApi;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortLists;

// TODO: Write Javadocs
@ExcludeFromGeneratedCoverage
public final class ValidationUtils {
    private ValidationUtils() { utilityClassError("ValidationUtils"); }

    public static <T> T mustNotBeNull(@Nullable T obj, String fieldName) {
        return checkNotNull(obj, "%s must not be null", fieldName);
    }

    // Method to ensure a list isn't empty:
    public static <T extends @Nullable Object> List<T> mustNotBeEmpty(@Nullable List<T> list,
            String fieldName) {
        List<T> checkedList = mustNotBeNull(list, fieldName);
        checkArgument(!checkedList.isEmpty(), "%s must not be empty", fieldName);
        return checkedList; // We won't return List.copyOf here, since this method is meant to
                            // validate.
    }

    private static <T> T mustNotContainNullElements(@Nullable T element, String listName,
            int index) {
        return checkNotNull(element, "%s must not contain null elements (null element at index %s)",
                listName, index);
    }

    public static <T> List<T> copyOfNonNullList(@Nullable List<? extends @Nullable T> list,
            String fieldName) {
        List<? extends @Nullable T> checkedList = mustNotBeNull(list, fieldName);
        for (int i = 0; i < checkedList.size(); i++)
            mustNotContainNullElements(checkedList.get(i), fieldName, i);
        return List.copyOf(checkedList);
    }

    public static ShortList copyOfNonNullShortList(@Nullable List<? extends @Nullable Short> list,
            String fieldName) {
        List<? extends @Nullable Short> checkedList = mustNotBeNull(list, fieldName);

        // 1. Fast-path: all empty lists (whether ShortLists.EMPTY_LIST, empty ShortArrayList, etc.)
        if (checkedList.isEmpty())
            return ShortList.of();

        // 2. Fast-path: input is of type ShortLists.Singleton (doesn't use an array, so immutable)
        if (checkedList instanceof ShortLists.Singleton singleton)
            return singleton;

        // 3. Fast-path: input is already a fastutil ShortList (no boxing/null checks required)
        if (checkedList instanceof ShortList shortList)
            return ShortList.of(shortList.toShortArray());

        // 4. Fallback: generic List<Short> requiring null validation and unboxing
        short[] array = new short[checkedList.size()];
        for (int i = 0; i < checkedList.size(); i++)
            array[i] = mustNotContainNullElements(checkedList.get(i), fieldName, i);

        return ShortList.of(array);
    }

    public static IntList copyOfNonNullIntList(@Nullable List<? extends @Nullable Integer> list,
            String fieldName) {
        List<? extends @Nullable Integer> checkedList = mustNotBeNull(list, fieldName);

        if (checkedList.isEmpty())
            return IntList.of();

        if (checkedList instanceof IntLists.Singleton singleton)
            return singleton;

        if (checkedList instanceof IntList intList)
            return IntList.of(intList.toIntArray());

        int[] array = new int[checkedList.size()];
        for (int i = 0; i < checkedList.size(); i++)
            array[i] = mustNotContainNullElements(checkedList.get(i), fieldName, i);

        return IntList.of(array);
    }

    public static LongList copyOfNonNullLongList(@Nullable List<? extends @Nullable Long> list,
            String fieldName) {
        List<? extends @Nullable Long> checkedList = mustNotBeNull(list, fieldName);

        if (checkedList.isEmpty())
            return LongList.of();

        if (checkedList instanceof LongLists.Singleton singleton)
            return singleton;

        if (checkedList instanceof LongList longList)
            return LongList.of(longList.toLongArray());

        long[] array = new long[checkedList.size()];
        for (int i = 0; i < checkedList.size(); i++)
            array[i] = mustNotContainNullElements(checkedList.get(i), fieldName, i);

        return LongList.of(array);
    }

    // TODO: Broadly implement this method in the codebase.
    @RestrictedApi(explanation = "This method is intended for internal use only, and should not be called by external code.", allowedOnPath = ".*/src/(main|test)/java/com/github/mrgarbagegamer/(internal|queues)/.*\\.java")
    @SuppressWarnings("DoNotCallSuggester")
    public static void utilityClassError(String className) {
        throw new AssertionError(className + " is a utility class and cannot be instantiated");
    }

    public static <T> T mustBeSet(@Nullable T parameter, String parameterName) {
        checkState(parameter != null, "%s must be set before building.", parameterName);
        return parameter;
    }

    public static int mustBePositive(int value, String fieldName) {
        checkArgument(value > 0, "%s must be positive, was: %s", fieldName, value);
        return value;
    }
}

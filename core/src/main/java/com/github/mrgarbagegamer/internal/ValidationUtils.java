package com.github.mrgarbagegamer.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import com.google.errorprone.annotations.RestrictedApi;

// TODO: Write Javadocs
@ExcludeFromGeneratedCoverage
public final class ValidationUtils {
    private ValidationUtils() { utilityClassError("ValidationUtils"); }

    public static <T> T mustNotBeNull(T obj, String fieldName) {
        return checkNotNull(obj, "%s must not be null", fieldName);
    }

    // Method to ensure a list isn't empty:
    public static <T> List<T> mustNotBeEmpty(List<T> list, String fieldName) {
        mustNotBeNull(list, fieldName);
        checkArgument(!list.isEmpty(), "%s must not be empty", fieldName);
        return list; // We won't return List.copyOf here, since this method is meant to validate.
    }

    public static <T> List<T> copyOfNonNullList(List<? extends T> list, String fieldName) {
        mustNotBeNull(list, fieldName);
        for (int i = 0; i < list.size(); i++) {
            checkNotNull(list.get(i),
                    "%s must not contain null elements (null element at index %s)", fieldName, i);
        }
        return List.copyOf(list);
    }

    // TODO: Broadly implement this method in the codebase.
    @RestrictedApi(explanation = "This method is intended for internal use only, and should not be called by external code.", allowedOnPath = ".*/src/(main|test)/java/com/github/mrgarbagegamer/(internal|queues)/.*\\.java")
    @SuppressWarnings("DoNotCallSuggester")
    public static void utilityClassError(String className) {
        throw new AssertionError(className + " is a utility class and cannot be instantiated");
    }

    public static <T> T mustBeSet(T parameter, String parameterName) {
        checkState(parameter != null, "%s must be set before building.", parameterName);
        return parameter;
    }

    public static int mustBePositive(int value, String fieldName) {
        checkArgument(value > 0, "%s must be positive, was: %s", fieldName, value);
        return value;
    }
}

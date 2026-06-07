package com.github.mrgarbagegamer.internal;

import static java.util.Objects.requireNonNull;

import java.util.List;

// TODO: Write Javadocs
@ExcludeFromGeneratedCoverage
public final class ValidationUtils {
    private ValidationUtils() { utilityClassError("ValidationUtils"); }

    public static <T> T mustNotBeNull(T obj, String fieldName) {
        return requireNonNull(obj, fieldName + " must not be null");
    }

    // Method to ensure a list isn't empty:
    public static <T> List<T> mustNotBeEmpty(List<T> list, String fieldName) {
        if (mustNotBeNull(list, fieldName).isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return list; // We won't return List.copyOf here, since this method is meant to validate.
    }

    public static <T> List<T> copyOfNonNullList(List<? extends T> list, String fieldName) {
        mustNotBeNull(list, fieldName);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                throw new NullPointerException(
                        "%s must not contain null elements (null element at index %d)"
                                .formatted(fieldName, i));
            }
        }
        return List.copyOf(list);
    }

    // TODO: Broadly implement this method in the codebase.
    public static void utilityClassError(String className) {
        throw new AssertionError(className + " is a utility class and cannot be instantiated");
    }

    public static <T> T mustBeSet(T parameter, String parameterName) {
        if (parameter == null) {
            throw new IllegalStateException(parameterName + " must be set before building.");
        }
        return parameter;
    }

    public static int mustBePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, was: " + value);
        }
        return value;
    }
}

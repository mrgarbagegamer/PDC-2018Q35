package com.github.mrgarbagegamer.internal;

import static java.util.Objects.requireNonNull;

import java.util.List;

@ExcludeFromGeneratedCoverage
public final class ValidationUtils {
    private ValidationUtils() {
        throw new AssertionError("ValidationUtils is a utility class and cannot be instantiated");
    }

    public static <T> T mustNotBeNull(T obj, String fieldName) {
        return requireNonNull(obj, fieldName + " must not be null");
    }

    public static <T> List<T> copyOfNonNullList(List<? extends T> list, String fieldName) {
        for (int i = 0; i < requireNonNull(list, fieldName + " must not be null").size(); i++) {
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
}

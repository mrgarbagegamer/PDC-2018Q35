package com.github.mrgarbagegamer.internal;

import static java.util.Objects.requireNonNull;

@ExcludeFromGeneratedCoverage
public final class ValidationUtils {
    private ValidationUtils() {
        throw new AssertionError("ValidationUtils is a utility class and cannot be instantiated");
    }

    public static <T> T mustNotBeNull(T obj, String fieldName) {
        return requireNonNull(obj, fieldName + " must not be null");
    }
}

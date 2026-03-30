package com.github.mrgarbagegamer.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation doesn't do anything in the code, but it prevents noninstantiable constructors or
 * other internal methods from being flagged in coverage reports.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface ExcludeFromGeneratedCoverage {}

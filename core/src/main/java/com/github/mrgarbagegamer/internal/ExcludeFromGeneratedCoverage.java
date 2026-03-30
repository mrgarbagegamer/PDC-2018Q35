package com.github.mrgarbagegamer.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation doesn't do anything in the code, but it prevents JaCoCo from generating coverage
 * reports for the annotated element (as it ignores any element with an annotation contained the
 * keyword "Generated"). Since VS Code's Test Runner for Java uses JaCoCo for code coverage, this
 * annotation can be used to exclude certain elements from the coverage report.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.TYPE})
public @interface ExcludeFromGeneratedCoverage {}

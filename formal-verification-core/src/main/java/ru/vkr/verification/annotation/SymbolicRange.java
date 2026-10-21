package ru.vkr.verification.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Overrides the symbolic numeric range of one parameter (inclusive). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
public @interface SymbolicRange {
    long min();
    long max();
}

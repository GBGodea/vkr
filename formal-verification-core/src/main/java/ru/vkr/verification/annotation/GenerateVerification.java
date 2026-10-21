package ru.vkr.verification.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Opts a static method into generation. Bounds are assumptions, not application limits. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateVerification {
    long min() default 0;
    long max() default 1_000_000_000_000L;
}

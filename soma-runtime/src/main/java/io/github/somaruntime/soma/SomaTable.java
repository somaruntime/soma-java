package io.github.somaruntime.soma;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a schema type whose records are owned by a generated SOMA Table. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SomaTable {

    /** Returns the initial capacity hint. */
    long defaultCapacity() default 16L;
}

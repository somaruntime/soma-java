package io.github.somaruntime.soma.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 public detached row carrier 及其 generated columnar table。 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface SomaTable {
    String name() default "";

    int defaultCapacity() default -1;
}

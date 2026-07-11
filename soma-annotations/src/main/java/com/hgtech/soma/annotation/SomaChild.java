package com.hgtech.soma.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 parent-owned dense/keyed child table field。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface SomaChild {
    String name() default "";
    int initialCapacity() default -1;
}

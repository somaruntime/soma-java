package com.hgtech.soma.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把 instance field 纳入 SOMA schema。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface SomaField {
    String name() default "";

    SomaSemantic semantic() default SomaSemantic.NONE;
}

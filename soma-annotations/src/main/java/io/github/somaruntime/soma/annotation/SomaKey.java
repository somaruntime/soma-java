package io.github.somaruntime.soma.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 table field 是稳定 logical primary key。 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface SomaKey {
    String name() default "";

    SomaSemantic semantic() default SomaSemantic.NONE;
}

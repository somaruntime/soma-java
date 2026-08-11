package io.github.somaruntime.soma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AnnotationContractTest {

    @Test
    void annotationsHaveTheExactJava8Contract() throws Exception {
        assertAnnotation(SomaSchema.class, ElementType.PACKAGE);
        assertAnnotation(SomaTable.class, ElementType.TYPE);
        assertAnnotation(SomaValue.class, ElementType.TYPE);
        assertAnnotation(SomaField.class, ElementType.FIELD);
        assertAnnotation(SomaKey.class, ElementType.FIELD);
        assertAnnotation(SomaIndex.class, ElementType.FIELD);

        Method defaultCapacity = SomaTable.class.getDeclaredMethod("defaultCapacity");
        assertEquals(int.class, defaultCapacity.getReturnType());
        assertEquals(16, defaultCapacity.getDefaultValue());
        assertEquals(1, SomaTable.class.getDeclaredMethods().length);
        assertEquals(0, SomaSchema.class.getDeclaredMethods().length);
        assertEquals(0, SomaValue.class.getDeclaredMethods().length);
        assertEquals(0, SomaField.class.getDeclaredMethods().length);
        assertEquals(0, SomaKey.class.getDeclaredMethods().length);
        assertEquals(0, SomaIndex.class.getDeclaredMethods().length);
    }

    private static void assertAnnotation(
            Class<? extends java.lang.annotation.Annotation> annotation,
            ElementType target) {
        assertNotNull(annotation.getAnnotation(Documented.class));
        assertNull(annotation.getAnnotation(Inherited.class));
        assertEquals(
                RetentionPolicy.CLASS,
                annotation.getAnnotation(Retention.class).value());
        assertArrayEquals(
                new ElementType[]{target},
                annotation.getAnnotation(Target.class).value());
    }
}

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIgnore;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaValue;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

public final class AnnotationContractConsumer {
    private AnnotationContractConsumer() {
    }

    public static void main(String[] args) throws Exception {
        assertAnnotation(SomaSchema.class, ElementType.PACKAGE);
        assertAnnotation(SomaValue.class, ElementType.TYPE);
        assertAnnotation(SomaField.class, ElementType.FIELD);
        assertAnnotation(SomaIgnore.class, ElementType.FIELD);
        assertAnnotation(SomaKey.class, ElementType.FIELD);
        assertAnnotation(SomaOptional.class, ElementType.FIELD);
        assertAnnotation(SomaTable.class, ElementType.TYPE);

        require("".equals(SomaTable.class.getMethod("name").getDefaultValue()),
                "SomaTable.name default");
        require(Integer.valueOf(-1).equals(
                SomaTable.class.getMethod("defaultCapacity").getDefaultValue()),
                "SomaTable.defaultCapacity default");

        require("".equals(SomaField.class.getMethod("name").getDefaultValue()),
                "SomaField.name default");
        require(SomaSemantic.NONE.equals(
                SomaField.class.getMethod("semantic").getDefaultValue()),
                "SomaField.semantic default");
        require("".equals(SomaKey.class.getMethod("name").getDefaultValue()),
                "SomaKey.name default");
        require(SomaSemantic.NONE.equals(
                SomaKey.class.getMethod("semantic").getDefaultValue()),
                "SomaKey.semantic default");
        require(SomaSchema.class.getMethod("name").getDefaultValue() == null,
                "SomaSchema.name must be required");
        require(SomaSchema.class.getMethod("generatedPackage").getDefaultValue() == null,
                "SomaSchema.generatedPackage must be required");
        require(SomaSchema.class.getMethod("version").getDefaultValue() == null,
                "SomaSchema.version must be required");
        require(Arrays.equals(
                new SomaSemantic[] {
                    SomaSemantic.NONE,
                    SomaSemantic.DATE,
                    SomaSemantic.TIME,
                    SomaSemantic.DATE_TIME
                },
                SomaSemantic.values()),
                "SomaSemantic order");
    }

    private static void assertAnnotation(
            Class<?> annotationType, ElementType expectedTarget) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        require(retention != null && retention.value() == RetentionPolicy.SOURCE,
                annotationType.getName() + " retention");
        Target target = annotationType.getAnnotation(Target.class);
        require(target != null
                        && target.value().length == 1
                        && target.value()[0] == expectedTarget,
                annotationType.getName() + " target");
        require(annotationType.isAnnotation(), annotationType.getName() + " annotation kind");
        require(annotationType.getAnnotation(Documented.class) != null,
                annotationType.getName() + " documented");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

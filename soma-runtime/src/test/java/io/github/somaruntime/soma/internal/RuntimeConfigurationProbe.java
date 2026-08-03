package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaSchema;
import io.github.somaruntime.soma.SomaTable;
import io.github.somaruntime.soma.SomaValue;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;

public final class RuntimeConfigurationProbe {
    private RuntimeConfigurationProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new AssertionError("one probe mode is required");
        }
        String mode = arguments[0];
        if ("class-load".equals(mode)) {
            Class.forName("io.github.somaruntime.soma.SomaConfiguration");
            expectState("UNFROZEN");
        } else if ("observe".equals(mode)) {
            expectState("UNFROZEN");
            if (SomaRuntimeAccess.effectiveMemoryBudgetBytes() != -1L) {
                throw new AssertionError("unfrozen runtime must not expose a budget");
            }
            expectState("UNFROZEN");
        } else if ("build-only".equals(mode)) {
            SomaConfiguration.builder().build();
            expectState("UNFROZEN");
        } else if ("configure-first".equals(mode)) {
            ForkJoinPool pool = new ForkJoinPool(2);
            SomaConfiguration configuration = SomaConfiguration.builder()
                    .parallelExecutor(pool)
                    .memoryBudgetBytes(4096L)
                    .compression(SomaCompression.OFF)
                    .build();
            SomaRuntimeAccess.configure(configuration);
            expectState("EXPLICIT");
            if (SomaRuntimeAccess.effectiveMemoryBudgetBytes() != 4096L
                    || SomaRuntimeAccess.effectiveCompression() != SomaCompression.OFF) {
                throw new AssertionError("explicit values were not retained");
            }
            expectFailure(
                    SomaFailureCode.CONFIGURATION_FROZEN,
                    new CheckedAction() {
                        @Override
                        public void run() {
                            SomaRuntimeAccess.configure(SomaConfiguration.builder().build());
                        }
                    });
            pool.shutdown();
        } else if ("default-first".equals(mode)) {
            SomaRuntimeAccess.freezeForRuntimeAccess();
            expectState("DEFAULT");
            if (SomaRuntimeAccess.effectiveMemoryBudgetBytes() <= 0L
                    || SomaRuntimeAccess.effectiveCompression() != SomaCompression.AUTO) {
                throw new AssertionError("automatic defaults are invalid");
            }
            expectFailure(
                    SomaFailureCode.CONFIGURATION_FROZEN,
                    new CheckedAction() {
                        @Override
                        public void run() {
                            SomaRuntimeAccess.configure(SomaConfiguration.builder().build());
                        }
                    });
        } else if ("null-config".equals(mode)) {
            expectFailure(
                    SomaFailureCode.INVALID_ARGUMENT,
                    new CheckedAction() {
                        @Override
                        public void run() {
                            SomaRuntimeAccess.configure(null);
                        }
                    });
            expectState("UNFROZEN");
        } else if ("invalid-builder".equals(mode)) {
            expectIllegalArgument(new CheckedAction() {
                @Override
                public void run() {
                    SomaConfiguration.builder().memoryBudgetBytes(0L);
                }
            });
            expectIllegalArgument(new CheckedAction() {
                @Override
                public void run() {
                    SomaConfiguration.builder().parallelExecutor(null);
                }
            });
            expectIllegalArgument(new CheckedAction() {
                @Override
                public void run() {
                    SomaConfiguration.builder().compression(null);
                }
            });
            expectState("UNFROZEN");
        } else if ("constructors".equals(mode)) {
            assertNoPublicConstructor(SomaConfiguration.class);
            assertNoPublicConstructor(SomaConfiguration.Builder.class);
            assertNoPublicConstructor(SomaOperationException.class);
        } else if ("public-surface".equals(mode)) {
            assertAnnotation(SomaSchema.class, ElementType.PACKAGE, 0);
            assertAnnotation(SomaTable.class, ElementType.TYPE, 1);
            assertAnnotation(SomaValue.class, ElementType.TYPE, 0);
            assertAnnotation(SomaField.class, ElementType.FIELD, 0);
            assertAnnotation(SomaKey.class, ElementType.FIELD, 0);
            assertAnnotation(SomaIndex.class, ElementType.FIELD, 0);
            Method defaultCapacity = SomaTable.class.getDeclaredMethod("defaultCapacity");
            if (defaultCapacity.getReturnType() != Long.TYPE
                    || !Long.valueOf(16L).equals(defaultCapacity.getDefaultValue())) {
                throw new AssertionError("unexpected @SomaTable signature");
            }
            assertEnumConstants(SomaCompression.class, "AUTO", "OFF");
            assertEnumConstants(
                    SomaOperation.class,
                    "CONFIGURE", "RESERVE", "ADD", "FIND", "GET",
                    "UPDATE", "REMOVE", "QUERY");
            assertEnumConstants(
                    SomaFailureCode.class,
                    "INVALID_ARGUMENT", "DUPLICATE_KEY", "MISSING_KEY",
                    "MISSING_RELATION_SIDE", "NULL_VALUE_UNSUPPORTED",
                    "RESOURCE_LIMIT_EXCEEDED", "ARITHMETIC_OVERFLOW",
                    "CONCURRENT_GROUP_OPERATION", "REENTRANT_GROUP_OPERATION",
                    "NESTED_PARALLEL_OPERATION", "CONFIGURATION_FROZEN",
                    "PARALLEL_EXECUTOR_UNAVAILABLE", "OPERATION_CANCELLED",
                    "PIPELINE_ALREADY_CONSUMED", "CALLBACK_SCOPE_VIOLATION",
                    "CALLBACK_FAILED");
            assertPublicStaticMethod(
                    SomaConfiguration.class,
                    "builder",
                    SomaConfiguration.Builder.class);
            assertPublicMethod(
                    SomaConfiguration.Builder.class,
                    "parallelExecutor",
                    SomaConfiguration.Builder.class,
                    ForkJoinPool.class);
            assertPublicMethod(
                    SomaConfiguration.Builder.class,
                    "memoryBudgetBytes",
                    SomaConfiguration.Builder.class,
                    Long.TYPE);
            assertPublicMethod(
                    SomaConfiguration.Builder.class,
                    "compression",
                    SomaConfiguration.Builder.class,
                    SomaCompression.class);
            assertPublicMethod(
                    SomaConfiguration.Builder.class,
                    "build",
                    SomaConfiguration.class);
        } else if ("environment".equals(mode)) {
            System.out.println(Runtime.getRuntime().maxMemory());
        } else {
            throw new AssertionError("unknown probe mode: " + mode);
        }
    }

    private static void expectState(String expected) {
        String actual = SomaRuntimeAccess.configurationState();
        if (!expected.equals(actual)) {
            throw new AssertionError("expected state " + expected + " but was " + actual);
        }
    }

    private static void expectFailure(
            SomaFailureCode code,
            CheckedAction action) {
        try {
            action.run();
            throw new AssertionError("expected structured failure " + code);
        } catch (SomaOperationException exception) {
            if (exception.code() != code || exception.operation() != SomaOperation.CONFIGURE) {
                throw new AssertionError("unexpected structured failure", exception);
            }
        }
    }

    private static void expectIllegalArgument(CheckedAction action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期的 builder-local rejection。
        }
    }

    private static void assertNoPublicConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                throw new AssertionError(type.getName() + " has a public constructor");
            }
        }
    }

    private static void assertAnnotation(
            Class<?> type,
            ElementType expectedTarget,
            int expectedMemberCount) {
        Retention retention = type.getAnnotation(Retention.class);
        Target target = type.getAnnotation(Target.class);
        if (!type.isAnnotation()
                || retention == null
                || retention.value() != RetentionPolicy.CLASS
                || target == null
                || target.value().length != 1
                || target.value()[0] != expectedTarget
                || type.getAnnotation(Documented.class) == null
                || type.getAnnotation(Inherited.class) != null
                || type.getDeclaredMethods().length != expectedMemberCount) {
            throw new AssertionError("unexpected annotation surface: " + type.getName());
        }
    }

    private static void assertEnumConstants(Class<? extends Enum<?>> type, String... expected) {
        Object[] constants = type.getEnumConstants();
        String[] actual = new String[constants.length];
        for (int index = 0; index < constants.length; index++) {
            actual[index] = ((Enum<?>) constants[index]).name();
        }
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(
                    "unexpected enum constants for " + type.getName()
                    + ": " + Arrays.toString(actual));
        }
    }

    private static void assertPublicStaticMethod(
            Class<?> owner,
            String name,
            Class<?> returnType,
            Class<?>... parameterTypes) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)
                || !Modifier.isStatic(modifiers)
                || method.getReturnType() != returnType) {
            throw new AssertionError("unexpected public static method: " + method);
        }
    }

    private static void assertPublicMethod(
            Class<?> owner,
            String name,
            Class<?> returnType,
            Class<?>... parameterTypes) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)
                || Modifier.isStatic(modifiers)
                || method.getReturnType() != returnType) {
            throw new AssertionError("unexpected public method: " + method);
        }
    }

    private interface CheckedAction {
        void run();
    }
}

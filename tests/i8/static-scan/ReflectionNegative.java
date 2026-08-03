package com.example.soma.i8;

/** Static-only negative fixture for the I8 reflection boundary scanner. */
final class ReflectionNegative {
    private void pluralEntryPoints(Class<?> type) throws Exception {
        type.getMethod("one");
        type.getMethods();
        type.getDeclaredMethod("two");
        type.getDeclaredMethods();
        type.getField("three");
        type.getFields();
        type.getDeclaredField("four");
        type.getDeclaredFields();
        type.getConstructor();
        type.getConstructors();
        type.getDeclaredConstructor();
        type.getDeclaredConstructors();
        // The fixture is static-only; these Java 9+ names exercise the trust-boundary scanner.
        // setAccessible(true);
        // trySetAccessible();
        // method.invoke(target);
    }
}

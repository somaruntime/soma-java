package io.github.somaruntime.soma.internal;

/** One PLAIN chunk with one exact Java array per physical leaf. */
final class PlainChunk {

    private final boolean[][] booleans;
    private final byte[][] bytes;
    private final short[][] shorts;
    private final char[][] chars;
    private final int[][] ints;
    private final long[][] longs;
    private final float[][] floats;
    private final double[][] doubles;
    private final Object[][] references;

    PlainChunk(GeneratedTableLayout layout, int rows) {
        booleans = booleanArrays(layout.kindCount(GeneratedTableLayout.BOOLEAN), rows);
        bytes = byteArrays(layout.kindCount(GeneratedTableLayout.BYTE), rows);
        shorts = shortArrays(layout.kindCount(GeneratedTableLayout.SHORT), rows);
        chars = charArrays(layout.kindCount(GeneratedTableLayout.CHAR), rows);
        ints = intArrays(layout.kindCount(GeneratedTableLayout.INT), rows);
        longs = longArrays(layout.kindCount(GeneratedTableLayout.LONG), rows);
        floats = floatArrays(layout.kindCount(GeneratedTableLayout.FLOAT), rows);
        doubles = doubleArrays(layout.kindCount(GeneratedTableLayout.DOUBLE), rows);
        references = referenceArrays(layout.kindCount(GeneratedTableLayout.REFERENCE), rows);
    }

    private PlainChunk(
            boolean[][] booleans,
            byte[][] bytes,
            short[][] shorts,
            char[][] chars,
            int[][] ints,
            long[][] longs,
            float[][] floats,
            double[][] doubles,
            Object[][] references) {
        this.booleans = booleans;
        this.bytes = bytes;
        this.shorts = shorts;
        this.chars = chars;
        this.ints = ints;
        this.longs = longs;
        this.floats = floats;
        this.doubles = doubles;
        this.references = references;
    }

    PlainChunk copy() {
        return new PlainChunk(
                clone(booleans), clone(bytes), clone(shorts), clone(chars),
                clone(ints), clone(longs), clone(floats), clone(doubles),
                clone(references));
    }

    boolean[] booleans(int slot) { return booleans[slot]; }
    byte[] bytes(int slot) { return bytes[slot]; }
    short[] shorts(int slot) { return shorts[slot]; }
    char[] chars(int slot) { return chars[slot]; }
    int[] ints(int slot) { return ints[slot]; }
    long[] longs(int slot) { return longs[slot]; }
    float[] floats(int slot) { return floats[slot]; }
    double[] doubles(int slot) { return doubles[slot]; }
    Object[] references(int slot) { return references[slot]; }

    int booleanCount() { return booleans.length; }
    int byteCount() { return bytes.length; }
    int shortCount() { return shorts.length; }
    int charCount() { return chars.length; }
    int intCount() { return ints.length; }
    int longCount() { return longs.length; }
    int floatCount() { return floats.length; }
    int doubleCount() { return doubles.length; }
    int referenceCount() { return references.length; }

    private static boolean[][] booleanArrays(int count, int rows) {
        boolean[][] result = new boolean[count][];
        for (int index = 0; index < count; index++) result[index] = new boolean[rows];
        return result;
    }

    private static byte[][] byteArrays(int count, int rows) {
        byte[][] result = new byte[count][];
        for (int index = 0; index < count; index++) result[index] = new byte[rows];
        return result;
    }

    private static short[][] shortArrays(int count, int rows) {
        short[][] result = new short[count][];
        for (int index = 0; index < count; index++) result[index] = new short[rows];
        return result;
    }

    private static char[][] charArrays(int count, int rows) {
        char[][] result = new char[count][];
        for (int index = 0; index < count; index++) result[index] = new char[rows];
        return result;
    }

    private static int[][] intArrays(int count, int rows) {
        int[][] result = new int[count][];
        for (int index = 0; index < count; index++) result[index] = new int[rows];
        return result;
    }

    private static long[][] longArrays(int count, int rows) {
        long[][] result = new long[count][];
        for (int index = 0; index < count; index++) result[index] = new long[rows];
        return result;
    }

    private static float[][] floatArrays(int count, int rows) {
        float[][] result = new float[count][];
        for (int index = 0; index < count; index++) result[index] = new float[rows];
        return result;
    }

    private static double[][] doubleArrays(int count, int rows) {
        double[][] result = new double[count][];
        for (int index = 0; index < count; index++) result[index] = new double[rows];
        return result;
    }

    private static Object[][] referenceArrays(int count, int rows) {
        Object[][] result = new Object[count][];
        for (int index = 0; index < count; index++) result[index] = new Object[rows];
        return result;
    }

    private static boolean[][] clone(boolean[][] source) {
        boolean[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static byte[][] clone(byte[][] source) {
        byte[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static short[][] clone(short[][] source) {
        short[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static char[][] clone(char[][] source) {
        char[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static int[][] clone(int[][] source) {
        int[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static long[][] clone(long[][] source) {
        long[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static float[][] clone(float[][] source) {
        float[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static double[][] clone(double[][] source) {
        double[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }

    private static Object[][] clone(Object[][] source) {
        Object[][] result = source.clone();
        for (int i = 0; i < result.length; i++) result[i] = result[i].clone();
        return result;
    }
}

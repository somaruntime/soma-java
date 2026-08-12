package io.github.somaruntime.examples.simulation.engine.api;

/** Detached primitive frame consumed by UI only. */
public final class SimulationSnapshot {
    private final long tick;
    private final int width;
    private final int height;
    private final float[] grass;
    private final float[] x;
    private final float[] y;
    private final float[] direction;
    private final boolean[] searching;

    public SimulationSnapshot(
            long tick, int width, int height, float[] grass,
            float[] x, float[] y, float[] direction, boolean[] searching) {
        if (width <= 0 || height <= 0
                || grass == null || x == null || y == null
                || direction == null || searching == null
                || grass.length != Math.multiplyExact(width, height)
                || x.length != y.length || x.length != direction.length
                || x.length != searching.length) {
            throw new IllegalArgumentException("invalid simulation snapshot shape");
        }
        this.tick = tick;
        this.width = width;
        this.height = height;
        this.grass = grass.clone();
        this.x = x.clone();
        this.y = y.clone();
        this.direction = direction.clone();
        this.searching = searching.clone();
    }

    public long tick() { return tick; }
    public int width() { return width; }
    public int height() { return height; }
    public float[] grass() { return grass.clone(); }
    public float[] x() { return x.clone(); }
    public float[] y() { return y.clone(); }
    public float[] direction() { return direction.clone(); }
    public boolean[] searching() { return searching.clone(); }
    public float grassAt(int cellId) { return grass[cellId]; }
    public float xAt(int index) { return x[index]; }
    public float yAt(int index) { return y[index]; }
    public float directionAt(int index) { return direction[index]; }
    public boolean searchingAt(int index) { return searching[index]; }
    public int population() { return x.length; }
}

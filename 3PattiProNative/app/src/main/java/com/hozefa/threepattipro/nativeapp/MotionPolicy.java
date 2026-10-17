package com.hozefa.threepattipro.nativeapp;

/** One policy for custom drawing, view motion and platform dialog transitions. */
final class MotionPolicy {
    enum Mode { FULL, REDUCED, OFF }
    static Mode resolve(String preference, float animatorScale, float transitionScale, float windowScale) {
        if ("off".equals(preference) || !positive(animatorScale) || !positive(transitionScale) || !positive(windowScale)) return Mode.OFF;
        return "reduced".equals(preference) ? Mode.REDUCED : Mode.FULL;
    }
    private static boolean positive(float value) { return Float.isFinite(value) && value > 0; }
    static long duration(Mode mode, long full) { return mode == Mode.OFF ? 0 : mode == Mode.REDUCED ? Math.min(120, full) : full; }
    static String points(int value, boolean signed) { return (signed && value >= 0 ? "+" : "") + value; }
    static int interpolate(int from, int to, float fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        return (int)Math.round(from + ((double)to - from) * f);
    }
}

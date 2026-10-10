package dev.halfagod.client;

import net.neoforged.neoforge.client.event.ViewportEvent;

/** A short, decaying camera shake for big story moments. */
public final class CameraShake {
    private static int remaining;
    private static int total;
    private static float strength;
    private static long seed;

    private CameraShake() {
    }

    public static void start(int ticks, float power) {
        remaining = total = Math.max(1, ticks);
        strength = power;
        seed = System.nanoTime();
    }

    public static void tick() {
        if (remaining > 0) {
            remaining--;
        }
    }

    public static void apply(ViewportEvent.ComputeCameraAngles e) {
        if (remaining <= 0) {
            return;
        }
        double t = (total - remaining) + e.getPartialTick();
        float fade = remaining / (float) total;
        float amp = strength * fade * fade;
        double s = seed % 1000;
        e.setYaw(e.getYaw() + (float) (Math.sin(t * 2.1 + s) * 1.6 * amp));
        e.setPitch(e.getPitch() + (float) (Math.sin(t * 2.9 + s * 1.3) * 1.2 * amp));
        e.setRoll(e.getRoll() + (float) (Math.sin(t * 1.7 + s * 0.7) * 2.2 * amp));
    }
}

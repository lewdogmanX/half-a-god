package dev.halfagod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * When someone raises the Eclipse nearby: the light drains out of the world for a few seconds and a black sun in a ring
 * of gold hangs at the top of the screen.
 */
public final class EclipseOverlay {
    private static int total;
    private static int left;

    private EclipseOverlay() {
    }

    public static void start(int ticks) {
        total = Math.max(1, ticks);
        left = total;
    }

    public static void tick() {
        if (left > 0) {
            left--;
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (left <= 0 || Minecraft.getInstance().options.hideGui) {
            return;
        }
        float done = total - left + delta.getGameTimeDeltaPartialTick(false);
        float strength = Math.min(1f, Math.min(done / 10f, (left) / 20f)); // in over half a second, out over a second
        int w = g.guiWidth();
        int h = g.guiHeight();
        // the light goes out: darker at the edges
        int a = (int) (strength * 110);
        g.fill(0, 0, w, h, a << 24);
        int edge = (int) (strength * 90);
        for (int i = 0; i < 24; i++) {
            int ea = edge * (24 - i) / 24;
            g.fill(0, i * 2, w, i * 2 + 2, ea << 24);
            g.fill(0, h - i * 2 - 2, w, h - i * 2, ea << 24);
            g.fill(i * 2, 0, i * 2 + 2, h, ea << 24);
            g.fill(w - i * 2 - 2, 0, w - i * 2, h, ea << 24);
        }
        // the black sun in its ring of gold
        int cx = w / 2;
        int cy = 34;
        int r = 16;
        int ring = ((int) (strength * 230) << 24) | 0xE8B84A;
        int corona = ((int) (strength * 90) << 24) | 0xF2D27A;
        int black = ((int) (strength * 255) << 24);
        disc(g, cx, cy, r + 5, corona);
        disc(g, cx, cy, r + 2, ring);
        disc(g, cx, cy, r, black);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
        }
    }
}

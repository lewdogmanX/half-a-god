package dev.halfagod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * A slim reputation bar. By default it sits at the top centre of the screen, away from the hotbar where other mods
 * draw (Tough As Nails, stamina and rolling bars), and steps down below any boss bars. The tier name shows briefly
 * under it whenever your reputation changes.
 */
public final class RepHud {
    private static final String[] TIERS = {"Wicked", "Fallen", "Wavering", "Good", "Saint"};
    private static final int[] TIER_COLORS = {0xFF9E2A2A, 0xFFD2483A, 0xFFB8B2A6, 0xFF6DCB5B, 0xFFF2C14E};
    private static final int BAR_H = 4;

    private static boolean known;
    private static double target;
    private static double shown;
    private static double lastDelta;
    private static int deltaTicks;
    private static int labelTicks;
    private static int lastTier = -1;
    /** lowest point of the boss bars drawn this frame */
    private static int bossBottom;

    private RepHud() {
    }

    public static void set(double value, double delta) {
        if (!known) {
            shown = value;
            labelTicks = 80;
        }
        known = true;
        target = value;
        if (Math.abs(delta) >= 0.25) {
            lastDelta = delta;
            deltaTicks = 60;
            labelTicks = Math.max(labelTicks, 60);
        }
        int t = tierIndex(value);
        if (lastTier != -1 && t != lastTier) {
            labelTicks = 120;
        }
        lastTier = t;
    }

    public static void tick() {
        shown += (target - shown) * 0.15;
        if (Math.abs(target - shown) < 0.05) {
            shown = target;
        }
        if (deltaTicks > 0) deltaTicks--;
        if (labelTicks > 0) labelTicks--;
    }

    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        bossBottom = Math.max(bossBottom, e.getY() + e.getIncrement());
    }

    private static int tierIndex(double v) {
        if (v >= 60) return 4;
        if (v >= 20) return 3;
        if (v > -20) return 2;
        if (v > -60) return 1;
        return 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        int boss = bossBottom;
        bossBottom = 0;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!known || player == null || mc.options.hideGui || player.isSpectator() || !ClientConfig.SHOW_BAR.get()) {
            return;
        }
        int w = ClientConfig.WIDTH.get();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int x;
        int y;
        switch (ClientConfig.POSITION.get()) {
            case TOP_LEFT -> {
                x = 6;
                y = 6;
            }
            case TOP_RIGHT -> {
                x = sw - w - 6;
                y = 6;
            }
            case BOTTOM_LEFT -> {
                x = 6;
                y = sh - 14;
            }
            case BOTTOM_RIGHT -> {
                x = sw - w - 6;
                y = sh - 14;
            }
            default -> {
                x = sw / 2 - w / 2;
                y = Math.max(4, boss + 2);
            }
        }
        x += ClientConfig.OFFSET_X.get();
        y += ClientConfig.OFFSET_Y.get();
        boolean below = ClientConfig.POSITION.get() == ClientConfig.Position.TOP_CENTER
                || ClientConfig.POSITION.get() == ClientConfig.Position.TOP_LEFT || ClientConfig.POSITION.get() == ClientConfig.Position.TOP_RIGHT;
        draw(g, mc.font, x, y, w, below);
    }

    private static void draw(GuiGraphics g, Font font, int x, int y, int w, boolean labelBelow) {
        double v = Math.max(-100, Math.min(100, shown));
        int tier = tierIndex(target);
        int tc = TIER_COLORS[tier];

        // track: dark frame, faint tier zones, a centre notch
        g.fill(x - 1, y - 1, x + w + 1, y + BAR_H + 1, 0xC0000000);
        for (int i = 0; i < w; i++) {
            int c = TIER_COLORS[tierIndex(-100 + 200.0 * i / (w - 1))];
            g.fill(x + i, y, x + i + 1, y + BAR_H, (0x40 << 24) | (c & 0xFFFFFF));
        }
        int centre = x + w / 2;
        int px = x + (int) Math.round((v + 100) / 200.0 * (w - 1));
        // fill from the centre to where you stand
        g.fillGradient(Math.min(centre, px), y, Math.max(centre, px) + 1, y + BAR_H, brighten(tc), tc);
        g.fill(centre, y, centre + 1, y + BAR_H, 0x80FFFFFF);
        // marker
        g.fill(px - 1, y - 2, px + 2, y + BAR_H + 2, 0xFF000000);
        g.fill(px, y - 1, px + 1, y + BAR_H + 1, 0xFFFFFFFF);

        if (ClientConfig.SHOW_NUMBER.get()) {
            String num = (v > 0 ? "+" : "") + Math.round(v);
            g.drawString(font, num, x + w + 4, y - 2, 0xFFB8B2A6, true);
        }

        // the tier's name for a moment after a change, small, under (or over) the bar
        if (labelTicks > 0) {
            int alpha = (int) (255 * Math.min(1.0, labelTicks / 15.0));
            String label = TIERS[tier];
            if (deltaTicks > 0) {
                label += "  " + (lastDelta > 0 ? "+" : "") + (Math.abs(lastDelta) >= 1 ? String.valueOf(Math.round(lastDelta)) : String.format("%.1f", lastDelta));
            }
            int lw = font.width(label);
            int ly = labelBelow ? y + BAR_H + 3 : y - 11;
            g.pose().pushPose();
            float s = 0.75f;
            g.pose().translate(x + w / 2f - lw * s / 2f, ly, 0);
            g.pose().scale(s, s, 1);
            g.drawString(font, label, 0, 0, (Math.max(alpha, 5) << 24) | (tc & 0xFFFFFF), true);
            g.pose().popPose();
        }
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 60);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 60);
        int b = Math.min(255, (argb & 0xFF) + 60);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}

package dev.halfagod.client;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * The reputation bar. Sits beside the hotbar on the side away from the offhand slot, clear of the
 * attack indicator, vanilla bars, Tough As Nails' thirst/temperature, Mebahel's quest tracker and potion icons.
 */
public final class RepHud {
    private static final String[] TIERS = {"Wicked", "Fallen", "Wavering", "Good", "Saint"};
    private static final int[] TIER_COLORS = {0xFF8E1B1B, 0xFFD2483A, 0xFFB8B2A6, 0xFF6DCB5B, 0xFFF2C14E};
    private static final int W = 98;
    private static final int H = 24;

    private static boolean known;
    private static double target;
    private static double shown;
    private static double lastDelta;
    private static int deltaTicks;
    private static int pulseTicks;
    private static int lastTier = -1;

    private RepHud() {
    }

    public static void set(double value, double delta) {
        if (!known) {
            shown = value;
        }
        known = true;
        target = value;
        if (Math.abs(delta) >= 0.25) {
            lastDelta = delta;
            deltaTicks = 70;
        }
        int t = tierIndex(value);
        if (lastTier != -1 && t != lastTier) {
            pulseTicks = 40;
        }
        lastTier = t;
    }

    public static void tick() {
        shown += (target - shown) * 0.15;
        if (Math.abs(target - shown) < 0.05) {
            shown = target;
        }
        if (deltaTicks > 0) deltaTicks--;
        if (pulseTicks > 0) pulseTicks--;
    }

    private static int tierIndex(double v) {
        if (v >= 60) return 4;
        if (v >= 20) return 3;
        if (v > -20) return 2;
        if (v > -60) return 1;
        return 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!known || player == null || mc.options.hideGui || player.isSpectator() || !ClientConfig.SHOW_BAR.get()) {
            return;
        }
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int[] pos = position(mc, player, sw, sh);
        int x = pos[0] + ClientConfig.OFFSET_X.get();
        int y = pos[1] + ClientConfig.OFFSET_Y.get();
        draw(g, mc.font, x, y);
    }

    private static int[] position(Minecraft mc, Player player, int sw, int sh) {
        ClientConfig.Anchor anchor = ClientConfig.ANCHOR.get();
        boolean offhandRight = player.getMainArm() == HumanoidArm.LEFT && !player.getOffhandItem().isEmpty();
        boolean offhandLeft = player.getMainArm() == HumanoidArm.RIGHT && !player.getOffhandItem().isEmpty();
        boolean indicator = mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR;
        // attack indicator sits on the side opposite the offhand
        boolean indicatorRight = player.getMainArm() == HumanoidArm.RIGHT;

        int rightX = sw / 2 + 91 + 6 + (offhandRight ? 29 : 0) + (indicator && indicatorRight ? 22 : 0);
        int leftX = sw / 2 - 91 - 6 - W - (offhandLeft ? 29 : 0) - (indicator && !indicatorRight ? 22 : 0);
        int bottomY = sh - H;

        switch (anchor) {
            case BOTTOM_RIGHT:
                return new int[]{rightX, bottomY};
            case BOTTOM_LEFT:
                return new int[]{leftX, bottomY};
            case TOP_RIGHT:
                return new int[]{sw - W - 4, 56};
            case TOP_LEFT:
                return new int[]{4, sh / 3};
            default:
                break;
        }
        if (rightX + W <= sw - 2) {
            return new int[]{rightX, bottomY};
        }
        if (leftX >= 2) {
            return new int[]{leftX, bottomY};
        }
        // very narrow window: below the potion-effect icons on the right
        return new int[]{sw - W - 4, 56};
    }

    private static void draw(GuiGraphics g, Font font, int x, int y) {
        double v = Math.max(-100, Math.min(100, shown));
        int tier = tierIndex(target);
        int tc = TIER_COLORS[tier];

        // panel
        int glow = pulseTicks > 0 ? (int) (0x60 * (pulseTicks / 40.0)) : 0;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, (Math.min(0xFF, 0x50 + glow) << 24) | (tc & 0xFFFFFF));
        g.fillGradient(x, y, x + W, y + H, 0xD0181410, 0xE00C0A08);
        g.fill(x, y, x + W, y + 1, 0x22FFFFFF);

        // title row: emblem, tier, number
        String emblem = switch (tier) {
            case 4 -> "✦";
            case 3 -> "✧";
            case 2 -> "◇";
            case 1 -> "☠";
            default -> "☠";
        };
        g.drawString(font, emblem, x + 4, y + 3, tc, true);
        g.drawString(font, TIERS[tier], x + 14, y + 3, tc, true);
        if (ClientConfig.SHOW_NUMBER.get()) {
            String num = (v > 0 ? "+" : "") + Math.round(v);
            g.drawString(font, num, x + W - 4 - font.width(num), y + 3, 0xFF9C958A, true);
        }

        // track: five tier zones, Wicked on the left, Saint on the right
        int bx = x + 4;
        int by = y + 15;
        int bw = W - 8;
        int bh = 5;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        for (int i = 0; i < bw; i++) {
            double at = -100 + 200.0 * i / (bw - 1);
            int c = TIER_COLORS[tierIndex(at)];
            g.fill(bx + i, by, bx + i + 1, by + bh, (0x55 << 24) | (c & 0xFFFFFF));
        }
        // fill from the centre to the current value
        int centre = bx + bw / 2;
        int px = bx + (int) Math.round((v + 100) / 200.0 * (bw - 1));
        int fx1 = Math.min(centre, px);
        int fx2 = Math.max(centre, px) + 1;
        g.fillGradient(fx1, by, fx2, by + bh, brighten(tc), tc);
        // zone ticks
        for (int z : new int[]{-60, -20, 20, 60}) {
            int zx = bx + (int) Math.round((z + 100) / 200.0 * (bw - 1));
            g.fill(zx, by, zx + 1, by + bh, 0x66000000);
        }
        g.fill(centre, by - 1, centre + 1, by + bh + 1, 0xAAFFFFFF);
        // marker
        g.fill(px - 1, by - 2, px + 2, by + bh + 2, 0xFF000000);
        g.fill(px, by - 1, px + 1, by + bh + 1, 0xFFFFFFFF);

        // a floating "+6" / "-6" after a change
        if (deltaTicks > 0) {
            String d = (lastDelta > 0 ? "+" : "") + (Math.abs(lastDelta) >= 1 ? String.valueOf(Math.round(lastDelta)) : String.format("%.1f", lastDelta));
            int alpha = (int) (255 * Math.min(1.0, deltaTicks / 20.0));
            int col = (alpha << 24) | (lastDelta > 0 ? 0xF2C14E : 0xD2483A);
            int rise = (70 - deltaTicks) / 6;
            g.drawString(font, d, x + W / 2 - font.width(d) / 2, y - 10 - rise, col, true);
        }
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 60);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 60);
        int b = Math.min(255, (argb & 0xFF) + 60);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}

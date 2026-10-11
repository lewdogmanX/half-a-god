package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A dream: the screen goes dark, a faint picture breathes in the middle, and a few lines come up one at a time.
 * Click to see the rest; click again (or wait) to wake up.
 */
public final class DreamScreen extends Screen {
    private final String title;
    private final String motif;
    private final List<String> lines = new ArrayList<>();
    private final long opened = Util.getMillis();
    private long revealAllAt = -1;

    public DreamScreen(String json) {
        super(Component.literal("A dream"));
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        this.title = o.has("title") ? o.get("title").getAsString() : "";
        this.motif = o.has("motif") ? o.get("motif").getAsString() : "eye";
        JsonArray a = o.has("lines") ? o.getAsJsonArray("lines") : new JsonArray();
        for (JsonElement e : a) {
            lines.add(e.getAsString());
        }
    }

    @Override
    protected void init() {
        if (minecraft != null && Util.getMillis() - opened < 200) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMBIENT_CAVE.value(), 0.6f, 0.6f));
        }
    }

    private float t() {
        return (Util.getMillis() - opened) / 1000f;
    }

    /** when line i starts to appear (seconds after opening) */
    private float start(int i) {
        float normal = 1.4f + i * 2.8f;
        if (revealAllAt >= 0) {
            return Math.min(normal, (revealAllAt - opened) / 1000f);
        }
        return normal;
    }

    private float allShownAt() {
        return start(lines.size() - 1) + 1.2f;
    }

    @Override
    public void tick() {
        if (!lines.isEmpty() && t() > allShownAt() + 14f) {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        float fade = Math.min(1f, t() / 0.8f);
        g.fill(0, 0, width, height, ((int) (fade * 255) << 24));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        float t = t();
        // the picture, faint and breathing
        float breathe = 1f + 0.04f * (float) Math.sin(t * 1.3f);
        float alpha = Math.min(1f, t / 2.5f) * 0.42f;
        int size = Math.min(width, height) / 3;
        drawMotif(g, motif, width / 2, height / 2 - size / 4, (int) (size * breathe), alpha, t);
        // the title, very small, at the top
        if (!title.isEmpty()) {
            int a = (int) (Math.min(1f, t / 1.5f) * 0x90);
            g.drawCenteredString(font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.ITALIC), width / 2, 18, (a << 24) | 0xB8A888);
        }
        // the lines, one at a time
        int w = Math.min(320, width - 40);
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        int total = 0;
        for (String l : lines) {
            List<FormattedCharSequence> split = font.split(Component.literal(l), w);
            wrapped.add(split);
            total += split.size() * 11 + 8;
        }
        int y = Math.max(height / 2 + 10, height - 40 - total);
        for (int i = 0; i < wrapped.size(); i++) {
            float k = Math.max(0f, Math.min(1f, (t - start(i)) / 1.2f));
            if (k <= 0.02f) {
                y += wrapped.get(i).size() * 11 + 8;
                continue;
            }
            int a = (int) (k * 255);
            for (FormattedCharSequence line : wrapped.get(i)) {
                g.drawString(font, line, width / 2 - font.width(line) / 2, y, (a << 24) | 0xE8DCC4, false);
                y += 11;
            }
            y += 8;
        }
        if (t > allShownAt() + 2f) {
            int a = (int) (Math.min(1f, (t - allShownAt() - 2f) / 1.5f) * 0x70);
            g.drawCenteredString(font, "click to wake", width / 2, height - 16, (a << 24) | 0x8A7A60);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (t() < allShownAt()) {
            revealAllAt = Util.getMillis();
        } else {
            onClose();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            return mouseClicked(0, 0, 0);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ the pictures
    /** Draws a motif as a grid of soft blocks: each cell is lit by a little function of where it is. */
    static void drawMotif(GuiGraphics g, String motif, int cx, int cy, int size, float alpha, float t) {
        int n = 40;
        int cell = Math.max(1, size / n);
        int x0 = cx - n * cell / 2, y0 = cy - n * cell / 2;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float u = (i + 0.5f) / n * 2 - 1, v = (j + 0.5f) / n * 2 - 1;
                int rgb = shade(motif, u, v, t);
                if (rgb == 0) {
                    continue;
                }
                int a = (int) (alpha * 255) & 0xFF;
                g.fill(x0 + i * cell, y0 + j * cell, x0 + (i + 1) * cell, y0 + (j + 1) * cell, (a << 24) | (rgb & 0xFFFFFF));
            }
        }
    }

    private static final int GOLD = 0xF2C14E, GOLD_D = 0xB0761E, IRON = 0x6A625C, RED = 0x8E1B1B, PALE = 0xE8DCC4, BLUE = 0x4A6A8A;

    private static int shade(String motif, float u, float v, float t) {
        float r = (float) Math.sqrt(u * u + v * v);
        double a = Math.atan2(v, u);
        switch (motif) {
            case "sun" -> {
                if (r < 0.42f) {
                    return u < 0 ? GOLD : IRON;
                }
                int ray = (int) Math.round((a + Math.PI) / (Math.PI * 2) * 12) % 12;
                double ang = ray * Math.PI * 2 / 12 - Math.PI;
                double da = Math.abs(Math.IEEEremainder(a - ang, Math.PI * 2));
                if (r < 0.9f && da < 0.11 * (1 - (r - 0.42) / 0.5)) {
                    return Math.cos(ang) < 0 ? GOLD_D : (ray % 3 == 0 ? 0 : IRON);
                }
                return 0;
            }
            case "eye" -> {
                float lid = 0.5f * (1 - u * u);
                if (Math.abs(u) > 1 || Math.abs(v) > lid) {
                    return 0;
                }
                float dx = u - 0.08f * (float) Math.sin(t * 0.4f);
                float ir = (float) Math.sqrt(dx * dx + v * v);
                if (ir < 0.12f) {
                    return 0x100808;
                }
                if (ir < 0.3f) {
                    return GOLD;
                }
                return Math.abs(v) > lid - 0.06f ? GOLD_D : PALE;
            }
            case "chains" -> {
                for (int k = -1; k <= 1; k++) {
                    float lx = u - k * 0.6f, ly = v * (k == 0 ? 1.8f : 1f) / (k == 0 ? 1f : 1.8f);
                    float lr = (float) Math.sqrt(lx * lx / 0.36f + ly * ly / 0.09f);
                    if (lr > 0.75f && lr < 1f) {
                        return IRON;
                    }
                }
                return 0;
            }
            case "crown" -> {
                if (v > 0.35f || v < -0.6f || Math.abs(u) > 0.75f) {
                    return 0;
                }
                if (v > 0.05f) {
                    return v > 0.25f ? GOLD_D : GOLD;
                }
                float tooth = Math.abs(((u + 0.75f) % 0.375f) / 0.375f - 0.5f) * 2; // 1 at the gaps, 0 at the points
                return v > -0.6f + tooth * 0.65f ? (Math.abs(u) < 0.06f && v < -0.2f ? RED : GOLD) : 0;
            }
            case "flame" -> {
                float wob = 0.06f * (float) Math.sin(t * 3 + v * 6);
                float w = (float) ((v + 0.9f) / 1.8f);
                float half = (float) (Math.sin(Math.min(1, Math.max(0, w)) * Math.PI * 0.95) * 0.5f * Math.sqrt(Math.max(0, w)));
                if (v < -0.9f || v > 0.8f || Math.abs(u - wob * (1 - w)) > half) {
                    return 0;
                }
                return Math.abs(u) < half * 0.45f && w > 0.4f ? 0xFFE08A : w > 0.55f ? GOLD : 0xD0581E;
            }
            case "bell" -> {
                if (v < -0.75f || v > 0.6f) {
                    return Math.abs(u) < 0.08f && v > 0.6f && v < 0.8f ? GOLD_D : 0;
                }
                float half = 0.18f + (v + 0.75f) * 0.42f;
                if (v > 0.45f) {
                    half += 0.1f;
                }
                return Math.abs(u) < half ? (u < -half * 0.4f ? GOLD : GOLD_D) : 0;
            }
            case "drop" -> {
                float vv = v + 0.15f;
                float half = vv < 0 ? Math.max(0, (vv + 0.8f) * 0.5f) : (float) Math.sqrt(Math.max(0, 0.36f - vv * vv));
                return Math.abs(u) < half && v > -0.95f && v < 0.75f ? (u < -half * 0.4f && vv > 0 ? 0xB83A3A : RED) : 0;
            }
            case "wave" -> {
                for (int k = 0; k < 3; k++) {
                    float yy = -0.4f + k * 0.4f + 0.12f * (float) Math.sin(u * 5 + t + k);
                    if (Math.abs(v - yy) < 0.05f) {
                        return k == 1 ? PALE : BLUE;
                    }
                }
                return 0;
            }
            default -> {
                return r < 0.5f ? IRON : 0;
            }
        }
    }
}

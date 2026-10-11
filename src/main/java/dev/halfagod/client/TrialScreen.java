package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Trial, as you see it from the dock: you're taken in the night (a few dark lines), then the court speaks one line
 * at a time (click or wait), then you plead, then the verdict. The court stays in view: the words go in a panel along
 * the bottom of the screen, with the court's name on a small banner at the top. Esc doesn't get you out of court.
 */
public final class TrialScreen extends Screen {
    private static final int PAPER = 0xE8EDE0C2;
    private static final int INK = 0xFF3B2A1A;
    private static final int RED_INK = 0xFF8E1B1B;
    private static final int GOLD = 0xFFC9A55A;

    private JsonObject data;
    private final List<String> kidnap = new ArrayList<>();
    private final List<String[]> lines = new ArrayList<>();
    private JsonObject verdict;
    /** 0 taken in the night, 1 the court speaks, 2 plead, 3 the verdict */
    private int phase;
    private int shown;
    private long phaseStart = Util.getMillis();
    private long lastLine = Util.getMillis();
    private boolean pleaded;
    private int panelX, panelY, panelW, panelH;

    public TrialScreen(String json) {
        super(Component.literal("The Court of the Watch"));
        read(json, true);
    }

    /** a new copy of the court's script (a resend, or the verdict) */
    public void update(String json) {
        read(json, false);
        rebuild();
    }

    private void read(String json, boolean first) {
        data = JsonParser.parseString(json).getAsJsonObject();
        kidnap.clear();
        lines.clear();
        if (data.has("kidnap")) {
            data.getAsJsonArray("kidnap").forEach(e -> kidnap.add(e.getAsString()));
        }
        for (JsonElement e : data.getAsJsonArray("lines")) {
            JsonArray a = e.getAsJsonArray();
            lines.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
        }
        verdict = data.has("verdict") ? data.getAsJsonObject("verdict") : null;
        boolean resume = data.has("resume") && data.get("resume").getAsBoolean();
        if (verdict != null) {
            setPhase(3);
            shown = lines.size();
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(verdict.get("guilty").getAsBoolean()
                        ? SoundEvents.ANVIL_LAND : SoundEvents.BELL_BLOCK, 0.7f, 0.6f));
            }
        } else if (resume) {
            setPhase(2);
            shown = lines.size();
        } else if (first) {
            setPhase(kidnap.isEmpty() ? 1 : 0);
            shown = 0;
        }
    }

    private void setPhase(int p) {
        phase = p;
        phaseStart = Util.getMillis();
        lastLine = phaseStart;
    }

    private float since() {
        return (Util.getMillis() - phaseStart) / 1000f;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        // a panel along the bottom, about two fifths of the screen: the court stays in view above it
        panelW = Math.min(520, width - 20);
        panelH = Math.max(100, Math.min(160, height * 2 / 5));
        panelX = (width - panelW) / 2;
        panelY = height - panelH - 6;
        if (phase == 2 && !pleaded) {
            JsonArray pleas = data.getAsJsonArray("pleas");
            int cols = pleas.size() > 2 && panelW >= 440 ? Math.min(4, pleas.size()) : 2;
            int bw = (panelW - 24 - (cols - 1) * 8) / cols;
            int rows = (pleas.size() + cols - 1) / cols;
            for (int i = 0; i < pleas.size(); i++) {
                JsonObject p = pleas.get(i).getAsJsonObject();
                String id = p.get("id").getAsString();
                int bx = panelX + 12 + (i % cols) * (bw + 8);
                int by = panelY + panelH - 8 - rows * 22 + (i / cols) * 22;
                Button b = Button.builder(Component.literal(p.get("label").getAsString()), btn -> plead(id)).bounds(bx, by, bw, 20).build();
                if (p.has("hint")) {
                    b.setTooltip(Tooltip.create(Component.literal(p.get("hint").getAsString())));
                }
                addRenderableWidget(b);
            }
        } else if (phase == 3) {
            addRenderableWidget(Button.builder(Component.literal("Continue"), btn -> {
                PacketDistributor.sendToServer(new Payloads.Plea("continue"));
                onClose();
            }).bounds(panelX + panelW - 112, panelY + panelH - 28, 100, 20).build());
        }
    }

    private void plead(String id) {
        pleaded = true;
        PacketDistributor.sendToServer(new Payloads.Plea(id));
        rebuild();
    }

    @Override
    public void tick() {
        if (phase == 0 && since() > 1.4f + kidnap.size() * 2.6f + 1.5f) {
            setPhase(1);
        } else if (phase == 1) {
            if (shown == 0 || Util.getMillis() - lastLine > 3800) {
                next();
            }
        }
    }

    private void next() {
        if (phase == 0) {
            setPhase(1);
            return;
        }
        if (phase != 1) {
            return;
        }
        if (shown < lines.size()) {
            shown++;
            lastLine = Util.getMillis();
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.5f));
            }
        }
        if (shown >= lines.size()) {
            setPhase(2);
            rebuild();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        if (phase == 0) {
            g.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        // no darkening and no blur: the court is the background. A soft shade behind the panel, then dark wood and paper
        g.fillGradient(0, panelY - 30, width, height, 0x00000000, 0x70000000);
        g.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xD02A1C11);
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY - 2, GOLD);
        g.fill(panelX - 3, panelY + panelH + 2, panelX + panelW + 3, panelY + panelH + 3, GOLD);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PAPER);
        // the banner at the top
        String head = "THE COURT OF THE WATCH";
        String sub = sub();
        int bw = Math.max(font.width(head), font.width(sub)) + 28;
        int bx = width / 2 - bw / 2;
        g.fill(bx, 4, bx + bw, 30, 0xB0140D08);
        g.fill(bx, 30, bx + bw, 31, GOLD);
    }

    private String sub() {
        return (data.has("act") && !data.get("act").getAsString().isEmpty() ? data.get("act").getAsString() + "  ·  " : "")
                + "day " + (data.has("day") ? data.get("day").getAsLong() : 0);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (phase == 0) {
            renderKidnap(g);
            return;
        }
        String head = "THE COURT OF THE WATCH";
        g.drawString(font, head, width / 2 - font.width(head) / 2, 8, GOLD, false);
        String sub = sub();
        g.drawString(font, Component.literal(sub).withStyle(ChatFormatting.ITALIC), width / 2 - font.width(sub) / 2, 19, 0xFFB8A27A, false);
        int ix = panelX + 12, iw = panelW - 24;
        // what's taking up the bottom of the panel (the pleas, the verdict), and so where the transcript has to stop
        int rows = 0;
        if (phase == 2 && !pleaded) {
            JsonArray pleas = data.getAsJsonArray("pleas");
            int cols = pleas.size() > 2 && panelW >= 440 ? Math.min(4, pleas.size()) : 2;
            rows = (pleas.size() + cols - 1) / cols;
        }
        int top = panelY + 8;
        int bottom = phase == 3 ? top : phase == 2 ? panelY + panelH - 8 - rows * 22 - (pleaded ? 6 : 16) : panelY + panelH - 16;
        if (phase != 3) {
            // the transcript: the newest line at the bottom, the ones before it fading upwards
            List<FormattedCharSequence> out = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            int n = phase == 1 ? shown : lines.size();
            for (int i = 0; i < n; i++) {
                String[] l = lines.get(i);
                boolean quiet = l[1].startsWith("(") || l[1].contains("cannot be found");
                boolean latest = i == n - 1;
                Component c = Component.empty().append(Component.literal(l[0] + ": ").withStyle(latest ? ChatFormatting.DARK_RED : ChatFormatting.RESET)
                                .withStyle(ChatFormatting.BOLD))
                        .append(quiet ? Component.literal(l[1]).withStyle(ChatFormatting.ITALIC) : Component.literal(l[1]));
                for (FormattedCharSequence seq : font.split(c, iw)) {
                    out.add(seq);
                    colors.add(quiet ? 0xFF6B5638 : latest ? INK : 0xFF7A6448);
                }
                if (!latest) {
                    out.add(FormattedCharSequence.EMPTY);
                    colors.add(INK);
                }
            }
            int maxLines = Math.max(1, (bottom - top) / 10);
            int from = Math.max(0, out.size() - maxLines);
            int y = top + Math.max(0, maxLines - (out.size() - from)) * 10;
            g.enableScissor(panelX, top, panelX + panelW, bottom);
            for (int i = from; i < out.size(); i++) {
                g.drawString(font, out.get(i), ix, y, colors.get(i), false);
                y += 10;
            }
            g.disableScissor();
        }
        if (phase == 1) {
            String hint = "click to go on  ·  " + shown + " / " + lines.size();
            g.drawString(font, hint, panelX + panelW - 8 - font.width(hint), panelY + panelH - 12, 0xFF9A8662, false);
        } else if (phase == 2 && !pleaded) {
            g.drawString(font, "HOW DO YOU PLEAD?", ix, bottom + 3, RED_INK, false);
        } else if (phase == 2) {
            g.drawString(font, Component.literal("The jury is out...").withStyle(ChatFormatting.ITALIC), ix, panelY + panelH - 16, 0xFF6B5638, false);
        } else if (phase == 3 && verdict != null) {
            boolean guilty = verdict.get("guilty").getAsBoolean();
            String big = guilty ? "GUILTY" : "FREE TO GO";
            g.pose().pushPose();
            g.pose().translate(ix, panelY + 10, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, big, 0, 0, guilty ? RED_INK : 0xFF2E6B2E, false);
            g.pose().popPose();
            int vy = panelY + 34;
            for (JsonElement e : verdict.getAsJsonArray("lines")) {
                JsonArray a = e.getAsJsonArray();
                Component c = Component.empty().append(Component.literal(a.get(0).getAsString() + ": ").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(a.get(1).getAsString()));
                for (FormattedCharSequence seq : font.split(c, iw - 110)) {
                    g.drawString(font, seq, ix, vy, INK, false);
                    vy += 10;
                }
            }
        }
    }

    private void renderKidnap(GuiGraphics g) {
        float t = since();
        int w = Math.min(320, width - 40);
        int y = height / 2 - kidnap.size() * 14;
        for (int i = 0; i < kidnap.size(); i++) {
            float k = Math.max(0f, Math.min(1f, (t - 1.0f - i * 2.6f) / 1.2f));
            for (FormattedCharSequence line : font.split(Component.literal(kidnap.get(i)), w)) {
                if (k > 0.02f) {
                    g.drawString(font, line, width / 2 - font.width(line) / 2, y, ((int) (k * 255) << 24) | 0xE8DCC4, false);
                }
                y += 11;
            }
            y += 10;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (phase <= 1) {
            next();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) && phase <= 1) {
            next();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

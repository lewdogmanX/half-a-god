package dev.halfagod.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Talking to someone: their name, what they say, and your answers, on a paper panel along the bottom of the screen so
 * you can still see them (and the room) above it. The same look as the court's transcript.
 */
public final class TalkScreen extends Screen {
    private static final int INK = 0xFF3B2A1A;
    private static final int RED_INK = 0xFF8E1B1B;
    private static final int GOLD = 0xFFC9A55A;

    private JsonObject data;
    private int panelX, panelY, panelW, panelH;
    private final List<FormattedCharSequence> text = new ArrayList<>();
    private int cols;
    private boolean answered;

    public TalkScreen(JsonObject data) {
        super(Component.literal(data.get("name").getAsString()));
        this.data = data;
    }

    public void update(JsonObject o) {
        data = o;
        answered = false;
        rebuild();
    }

    private int paper() {
        return switch (data.has("style") ? data.get("style").getAsString() : "") {
            case "prison" -> 0xEAD7D2C6;
            case "church" -> 0xEAF2E6C8;
            default -> 0xE8EDE0C2;
        };
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        panelW = Math.min(500, width - 20);
        int iw = panelW - 24;
        text.clear();
        for (JsonElement e : data.getAsJsonArray("lines")) {
            text.addAll(font.split(Component.literal(e.getAsString()), iw));
            text.add(FormattedCharSequence.EMPTY);
        }
        if (!text.isEmpty()) {
            text.remove(text.size() - 1);
        }
        var options = data.getAsJsonArray("options");
        cols = options.size() > 3 ? 2 : 1;
        int rows = (options.size() + cols - 1) / cols;
        int maxText = Math.max(3, (height / 2 - 40 - rows * 22) / 10);
        while (text.size() > maxText) {
            text.remove(0);
        }
        panelH = 26 + text.size() * 10 + 8 + rows * 22 + 6;
        panelX = (width - panelW) / 2;
        panelY = height - panelH - 6;
        int bw = (iw - (cols - 1) * 8) / cols;
        for (int i = 0; i < options.size(); i++) {
            JsonObject o = options.get(i).getAsJsonObject();
            String id = o.get("id").getAsString();
            int bx = panelX + 12 + (i % cols) * (bw + 8);
            int by = panelY + panelH - 6 - rows * 22 + (i / cols) * 22;
            Button b = Button.builder(Component.literal(o.get("label").getAsString()), btn -> pick(id)).bounds(bx, by, bw, 20).build();
            if (o.has("hint") && !o.get("hint").getAsString().isEmpty()) {
                b.setTooltip(Tooltip.create(Component.literal(o.get("hint").getAsString())));
            }
            addRenderableWidget(b);
        }
    }

    private void pick(String option) {
        if (answered) {
            return;
        }
        answered = true;
        PacketDistributor.sendToServer(new Payloads.Act("talk", data.get("id").getAsString(), option));
        // the server answers with the next part of the conversation, or nothing (and this closes)
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (!answered) {
            PacketDistributor.sendToServer(new Payloads.Act("talk_closed", data.get("id").getAsString(), ""));
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, panelY - 30, width, height, 0x00000000, 0x70000000);
        g.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xD02A1C11);
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY - 2, GOLD);
        g.fill(panelX - 3, panelY + panelH + 2, panelX + panelW + 3, panelY + panelH + 3, GOLD);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, paper());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int ix = panelX + 12;
        g.drawString(font, Component.literal(data.get("name").getAsString()).withStyle(ChatFormatting.BOLD), ix, panelY + 8, RED_INK, false);
        String title = data.has("title") ? data.get("title").getAsString() : "";
        if (!title.isEmpty()) {
            g.drawString(font, Component.literal(title).withStyle(ChatFormatting.ITALIC),
                    panelX + panelW - 12 - font.width(title), panelY + 8, 0xFF8A7656, false);
        }
        g.fill(ix, panelY + 20, panelX + panelW - 12, panelY + 21, 0x606B5638);
        int y = panelY + 26;
        for (FormattedCharSequence s : text) {
            g.drawString(font, s, ix, y, INK, false);
            y += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

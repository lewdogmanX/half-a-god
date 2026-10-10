package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.net.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** A decision with three or four answers. Closing it is allowed: it comes back later (/halfagod choose). */
public final class ChoiceScreen extends Screen {
    private final JsonObject choice;
    private final String id;
    private List<FormattedCharSequence> prompt = List.of();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public ChoiceScreen(String json) {
        super(Component.literal("A choice"));
        this.choice = JsonParser.parseString(json).getAsJsonObject();
        this.id = choice.get("id").getAsString();
    }

    @Override
    protected void init() {
        JsonArray options = choice.getAsJsonArray("options");
        panelW = Math.min(360, width - 40);
        prompt = font.split(Component.literal(choice.get("text").getAsString()), panelW - 24);
        int buttonsH = options.size() * 24;
        panelH = 46 + prompt.size() * 10 + 10 + buttonsH + 18;
        panelX = (width - panelW) / 2;
        panelY = Math.max(10, (height - panelH) / 2);
        int by = panelY + 46 + prompt.size() * 10 + 10;
        for (int i = 0; i < options.size(); i++) {
            JsonObject o = options.get(i).getAsJsonObject();
            final int index = i;
            String label = o.get("label").getAsString();
            Button b = Button.builder(Component.literal(label), btn -> pick(index))
                    .bounds(panelX + 12, by + i * 24, panelW - 24, 20).build();
            if (o.has("hint")) {
                b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(o.get("hint").getAsString())));
            }
            addRenderableWidget(b);
        }
    }

    private void pick(int index) {
        PacketDistributor.sendToServer(new Payloads.PickChoice(id, index));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF7A5A2A);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0201810, 0xF0100C08);
        String speaker = choice.has("speaker") ? choice.get("speaker").getAsString() : "";
        String title = choice.has("title") ? choice.get("title").getAsString() : "A choice";
        g.drawCenteredString(font, Component.literal(title).withColor(0xF2C14E), width / 2, panelY + 10, 0xFFF2C14E);
        if (!speaker.isEmpty()) {
            g.drawCenteredString(font, Component.literal(speaker).withColor(0xB8B2A6), width / 2, panelY + 24, 0xFFB8B2A6);
        }
        int ty = panelY + 46;
        for (FormattedCharSequence line : prompt) {
            g.drawString(font, line, panelX + 12, ty, 0xFFE8E0D0, false);
            ty += 10;
        }
        g.drawCenteredString(font, Component.literal("Press Esc to decide later (/halfagod choose)").withColor(0x6E675C), width / 2, panelY + panelH - 12, 0xFF6E675C);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

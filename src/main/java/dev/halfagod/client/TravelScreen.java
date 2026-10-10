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

/** The lantern road (Good) or the smugglers' tunnels (Evil): pick a village to travel to. */
public final class TravelScreen extends Screen {
    private final JsonObject data;
    private List<FormattedCharSequence> text = List.of();
    private int px, py, pw, ph;

    public TravelScreen(String json) {
        super(Component.literal("Travel"));
        this.data = JsonParser.parseString(json).getAsJsonObject();
    }

    @Override
    protected void init() {
        JsonArray d = data.getAsJsonArray("destinations");
        pw = Math.min(300, width - 40);
        text = font.split(Component.literal(data.get("text").getAsString()), pw - 24);
        ph = 40 + text.size() * 10 + 8 + Math.max(1, d.size()) * 24 + 30;
        px = (width - pw) / 2;
        py = Math.max(8, (height - ph) / 2);
        int by = py + 40 + text.size() * 10 + 8;
        for (int i = 0; i < d.size(); i++) {
            JsonObject o = d.get(i).getAsJsonObject();
            final int index = i;
            String label = o.get("name").getAsString() + "  (" + o.get("distance").getAsInt() + " blocks)";
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                PacketDistributor.sendToServer(new Payloads.PickTravel(index));
                onClose();
            }).bounds(px + 12, by + i * 24, pw - 24, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Stay here"), b -> onClose())
                .bounds(px + pw / 2 - 40, py + ph - 26, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        boolean evil = data.get("cost").getAsInt() > 0;
        int edge = evil ? 0xFF6E2A2A : 0xFF7A5A2A;
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, edge);
        g.fillGradient(px, py, px + pw, py + ph, 0xF0201810, 0xF0100C08);
        g.drawCenteredString(font, data.get("title").getAsString(), width / 2, py + 12, evil ? 0xFFD2483A : 0xFFF2C14E);
        int y = py + 34;
        for (FormattedCharSequence line : text) {
            g.drawString(font, line, px + 12, y, 0xFFE8E0D0, false);
            y += 10;
        }
        if (data.getAsJsonArray("destinations").isEmpty()) {
            g.drawCenteredString(font, "You don't know any other villages yet.", width / 2, y + 12, 0xFF9C958A);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

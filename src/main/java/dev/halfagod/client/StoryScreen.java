package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.HalfAGod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Operator-only overview of the whole story: every act, every quest, where each one leads. */
public final class StoryScreen extends Screen {
    private record Row(boolean header, String text, JsonObject quest) {
    }

    private final boolean op;
    private final List<JsonObject> acts = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private JsonObject selected;
    private EditBox search;
    private Button startButton;
    private int listScroll;
    private int detailScroll;
    private List<FormattedCharSequence> detail = List.of();
    private int listX, listY, listW, listH, detX, detW;

    public StoryScreen(boolean op) {
        super(Component.literal("Half a God - the whole story"));
        this.op = op;
        load();
    }

    private void load() {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager()
                .getResource(ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "story/index.json"));
        if (res.isEmpty()) {
            return;
        }
        try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement a : root.getAsJsonArray("acts")) {
                acts.add(a.getAsJsonObject());
            }
        } catch (Exception e) {
            HalfAGod.LOG.error("Could not read the story index", e);
        }
    }

    @Override
    protected void init() {
        listX = 10;
        listY = 40;
        listW = Math.max(150, width * 2 / 5);
        listH = height - listY - 10;
        detX = listX + listW + 12;
        detW = width - detX - 12;
        search = new EditBox(font, listX, 18, listW, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search quests..."));
        search.setResponder(s -> rebuild());
        addRenderableWidget(search);
        startButton = Button.builder(Component.literal("Start this quest"), b -> {
            if (selected != null && minecraft != null && minecraft.player != null) {
                minecraft.player.connection.sendCommand("halfagod jump " + selected.get("id").getAsString());
                onClose();
            }
        }).bounds(width - 12 - 120, height - 30, 120, 20).build();
        startButton.visible = false;
        addRenderableWidget(startButton);
        rebuild();
        select(selected);
    }

    private void rebuild() {
        rows.clear();
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        for (JsonObject act : acts) {
            List<Row> matches = new ArrayList<>();
            for (JsonElement e : act.getAsJsonArray("quests")) {
                JsonObject quest = e.getAsJsonObject();
                String title = quest.get("title").getAsString();
                if (q.isEmpty() || title.toLowerCase(Locale.ROOT).contains(q) || quest.get("id").getAsString().contains(q)) {
                    matches.add(new Row(false, title, quest));
                }
            }
            if (!matches.isEmpty()) {
                rows.add(new Row(true, act.get("name").getAsString() + "  (" + matches.size() + ")", null));
                rows.addAll(matches);
            }
        }
        listScroll = 0;
    }

    private void select(JsonObject quest) {
        selected = quest;
        detailScroll = 0;
        if (startButton != null) {
            startButton.visible = op && quest != null;
        }
        if (quest == null) {
            detail = font.split(Component.literal("Pick a quest on the left to read it. Gold quests are the main story, blue are the big arcs, green are the reputation road, grey are side quests and Mebahel's own questlines."), Math.max(60, detW - 8));
            return;
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        int w = Math.max(60, detW - 8);
        out.addAll(font.split(Component.literal(quest.get("title").getAsString()).withColor(0xF2C14E), w));
        out.addAll(font.split(Component.literal(quest.get("id").getAsString()).withColor(0x6E675C), w));
        out.add(FormattedCharSequence.EMPTY);
        out.addAll(font.split(Component.literal(quest.get("summary").getAsString()).withColor(0xE8E0D0), w));
        section(out, "What you do", quest.getAsJsonArray("goals"), w, 0xB8D8F0);
        section(out, "Needs", quest.getAsJsonArray("needs"), w, 0xD8B8B8);
        section(out, "Leads to", quest.getAsJsonArray("leads"), w, 0xB8F0B8);
        detail = out;
    }

    private void section(List<FormattedCharSequence> out, String name, JsonArray items, int w, int color) {
        if (items == null || items.isEmpty()) {
            return;
        }
        out.add(FormattedCharSequence.EMPTY);
        out.addAll(font.split(Component.literal(name).withColor(0xF2C14E), w));
        for (JsonElement e : items) {
            out.addAll(font.split(Component.literal("- " + e.getAsString()).withColor(color), w));
        }
    }

    private int color(JsonObject quest) {
        return switch (quest.has("kind") ? quest.get("kind").getAsString() : "") {
            case "main" -> 0xFFF2C14E;
            case "arc" -> 0xFF7FB8F0;
            case "road" -> 0xFF8FD68A;
            case "letter" -> 0xFFE0A0F0;
            default -> 0xFFB8B2A6;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawString(font, "The whole story (operators only)", listX, 6, 0xFFF2C14E, true);
        // list
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF3A2E1E);
        g.fill(listX, listY, listX + listW, listY + listH, 0xC0100C08);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY + 2 - listScroll;
        for (Row r : rows) {
            if (y > listY - 12 && y < listY + listH) {
                if (r.header()) {
                    g.drawString(font, r.text(), listX + 4, y + 1, 0xFFFFFFFF, true);
                } else {
                    boolean sel = r.quest() == selected;
                    boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + 11;
                    if (sel || hover) {
                        g.fill(listX + 1, y, listX + listW - 1, y + 11, sel ? 0x60F2C14E : 0x30FFFFFF);
                    }
                    g.drawString(font, font.plainSubstrByWidth(r.text(), listW - 16), listX + 12, y + 2, color(r.quest()), false);
                }
            }
            y += r.header() ? 14 : 11;
        }
        g.disableScissor();
        // detail
        int dh = height - listY - (op ? 40 : 10);
        g.fill(detX - 1, listY - 1, detX + detW + 1, listY + dh + 1, 0xFF3A2E1E);
        g.fill(detX, listY, detX + detW, listY + dh, 0xC0100C08);
        g.enableScissor(detX, listY, detX + detW, listY + dh);
        int dy = listY + 4 - detailScroll;
        for (FormattedCharSequence line : detail) {
            g.drawString(font, line, detX + 4, dy, 0xFFFFFFFF, false);
            dy += 10;
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int y = listY + 2 - listScroll;
            for (Row r : rows) {
                int h = r.header() ? 14 : 11;
                if (!r.header() && my >= y && my < y + h) {
                    select(r.quest());
                    return true;
                }
                y += h;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < detX) {
            int total = 0;
            for (Row r : rows) total += r.header() ? 14 : 11;
            listScroll = (int) Math.max(0, Math.min(Math.max(0, total - listH + 4), listScroll - sy * 22));
        } else {
            int total = detail.size() * 10;
            detailScroll = (int) Math.max(0, Math.min(Math.max(0, total - (height - listY - 50)), detailScroll - sy * 20));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

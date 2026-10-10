package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.HalfAGod;
import dev.halfagod.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Your journey, drawn on parchment. Each act is a winding road; every quest you've reached is a wax seal on it
 * (red when done, gold where you are now). Side stories branch off above and below. Nothing you haven't reached is
 * shown: the road simply fades out ahead of you. A Codex tab holds what you've learned about people, places and the cult.
 */
public final class StoryMapScreen extends Screen {
    // parchment palette
    private static final int PAPER = 0xFFE9D8B4;
    private static final int PAPER_DARK = 0xFFD8C298;
    private static final int INK = 0xFF3B2A1A;
    private static final int INK_SOFT = 0xFF6B5638;
    private static final int INK_FAINT = 0x806B5638;
    private static final int WAX = 0xFF8E1B1B;
    private static final int WAX_DARK = 0xFF5E1010;
    private static final int GOLD = 0xFFE0A93A;
    private static final int GOLD_DARK = 0xFF9A6A18;
    private static final int FRAME = 0xFF2A1C10;

    private static final int COL_W = 124;
    private static final int SLOT_H = 84;
    private static final int ROW_H = 34;
    private static final int R = 11;
    private static final int TOP = 34;
    private static final int BOTTOM = 30;

    public static boolean openingClassic;
    private static StoryMapScreen open;
    private static JsonObject pendingStates;

    private record Node(String id, String title, String lane, int col, int row, String summary, List<String> goals,
                        ItemStack icon, boolean choice) {
    }

    private record Chapter(String id, String name, String subtitle, List<Node> nodes, List<String[]> edges,
                           Map<String, String> laneNames, Map<String, Integer> laneColors) {
    }

    private record Placed(Node node, int x, int y, boolean above) {
    }

    private record Lore(String category, String title, String unlock, String text) {
    }

    private final Screen parent;
    private final List<Chapter> chapters = new ArrayList<>();
    private final List<Lore> codex = new ArrayList<>();
    private final Map<String, Set<String>> preds = new HashMap<>();
    private final Map<String, String> titles = new HashMap<>();
    private Map<String, String> states = new HashMap<>();
    private boolean statesKnown;
    private int chapter = -1;
    private boolean showCodex;
    private int codexPick;
    private int codexScroll;
    private double panX;
    private double panY;
    private float zoom = 1f;
    private Placed hovered;
    private Node selected;
    private boolean dragging;
    private double dragDist;
    private int tick;
    private List<Placed> layout = List.of();
    private int layoutChapter = -2;
    private Button codexButton;

    public StoryMapScreen(Screen parent) {
        super(Component.literal("Your Journey"));
        this.parent = parent;
        load();
    }

    // ------------------------------------------------------------------ data
    private void load() {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager()
                    .getResource(ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "story/map.json"));
            if (res.isPresent()) {
                try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (JsonElement ce : root.getAsJsonArray("chapters")) {
                        JsonObject c = ce.getAsJsonObject();
                        Map<String, String> laneNames = new LinkedHashMap<>();
                        Map<String, Integer> laneColors = new HashMap<>();
                        for (JsonElement le : c.getAsJsonArray("lanes")) {
                            JsonObject l = le.getAsJsonObject();
                            laneNames.put(l.get("id").getAsString(), l.get("name").getAsString());
                            laneColors.put(l.get("id").getAsString(), l.get("color").getAsInt());
                        }
                        List<Node> nodes = new ArrayList<>();
                        for (JsonElement ne : c.getAsJsonArray("nodes")) {
                            JsonObject n = ne.getAsJsonObject();
                            List<String> goals = new ArrayList<>();
                            n.getAsJsonArray("goals").forEach(g -> goals.add(g.getAsString()));
                            var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(n.get("icon").getAsString()));
                            Node node = new Node(n.get("id").getAsString(), n.get("title").getAsString(), n.get("lane").getAsString(),
                                    n.get("col").getAsInt(), n.get("row").getAsInt(), n.get("summary").getAsString(), goals,
                                    new ItemStack(item == Items.AIR ? Items.PAPER : item), n.get("choice").getAsBoolean());
                            nodes.add(node);
                            titles.put(node.id(), node.title());
                        }
                        List<String[]> edges = new ArrayList<>();
                        for (JsonElement ee : c.getAsJsonArray("edges")) {
                            JsonArray a = ee.getAsJsonArray();
                            edges.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsString()});
                            preds.computeIfAbsent(a.get(1).getAsString(), k -> new HashSet<>()).add(a.get(0).getAsString());
                        }
                        chapters.add(new Chapter(c.get("id").getAsString(), c.get("name").getAsString(), c.get("subtitle").getAsString(),
                                nodes, edges, laneNames, laneColors));
                    }
                }
            }
            Optional<Resource> lore = Minecraft.getInstance().getResourceManager()
                    .getResource(ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "story/codex.json"));
            if (lore.isPresent()) {
                try (Reader r = new InputStreamReader(lore.get().open(), StandardCharsets.UTF_8)) {
                    for (JsonElement e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("entries")) {
                        JsonObject o = e.getAsJsonObject();
                        codex.add(new Lore(o.get("category").getAsString(), o.get("title").getAsString(), o.get("unlock").getAsString(),
                                o.get("text").getAsString()));
                    }
                }
            }
        } catch (Exception e) {
            HalfAGod.LOG.error("Could not read the story map", e);
        }
    }

    public static void receive(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (open != null) {
            open.applyStates(o);
        } else {
            pendingStates = o;
        }
    }

    private void applyStates(JsonObject o) {
        Map<String, String> s = new HashMap<>();
        o.getAsJsonObject("states").entrySet().forEach(e -> s.put(e.getKey(), e.getValue().getAsString()));
        states = s;
        statesKnown = true;
        layoutChapter = -2;
        if (chapter < 0) {
            chapter = currentChapter();
            centreOnNow();
        }
    }

    private String state(String id) {
        return states.getOrDefault(id, "locked");
    }

    private boolean doneOrActive(String id) {
        String s = state(id);
        return s.startsWith("done") || s.equals("active") || s.equals("failed");
    }

    /** Only what you've reached: done, on now, or ready to start right after something you've done. */
    private boolean visible(Node n) {
        if (doneOrActive(n.id())) {
            return true;
        }
        if (state(n.id()).equals("open")) {
            for (String p : preds.getOrDefault(n.id(), Set.of())) {
                if (state(p).startsWith("done")) {
                    return true;
                }
            }
        }
        return false;
    }

    private int currentChapter() {
        int best = 0;
        int bestScore = -1;
        for (int ci = 0; ci < chapters.size(); ci++) {
            for (Node n : chapters.get(ci).nodes()) {
                String s = state(n.id());
                int score = s.equals("active") ? (n.lane().equals("main") ? 2000 : 1000) + ci : s.startsWith("done") ? ci : -1;
                if (score > bestScore) {
                    bestScore = score;
                    best = ci;
                }
            }
        }
        return best;
    }

    private boolean chapterVisible(int ci) {
        for (Node n : chapters.get(ci).nodes()) {
            if (visible(n)) {
                return true;
            }
        }
        return ci == chapter;
    }

    // ------------------------------------------------------------------ layout
    private List<Placed> layout() {
        if (layoutChapter == chapter) {
            return layout;
        }
        layoutChapter = chapter;
        List<Placed> out = new ArrayList<>();
        if (chapter < 0 || chapter >= chapters.size()) {
            return layout = out;
        }
        Chapter c = chapters.get(chapter);
        // slots: main road in the middle, side roads alternating above and below
        Map<String, Integer> slot = new HashMap<>();
        int k = 0;
        for (String lane : c.laneNames().keySet()) {
            if (lane.equals("main")) {
                slot.put(lane, 0);
                continue;
            }
            boolean any = false;
            for (Node n : c.nodes()) {
                if (n.lane().equals(lane) && visible(n)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                continue;
            }
            k++;
            slot.put(lane, (k % 2 == 1 ? -1 : 1) * ((k + 1) / 2));
        }
        for (Node n : c.nodes()) {
            if (!visible(n) || !slot.containsKey(n.lane())) {
                continue;
            }
            int s = slot.get(n.lane());
            int x = 70 + n.col() * COL_W;
            double wave = Math.sin(n.col() * 0.85 + (n.lane().hashCode() % 7)) * 16;
            int y = s * SLOT_H + (s < 0 ? -n.row() * ROW_H : n.row() * ROW_H) + (int) wave;
            out.add(new Placed(n, x, y, s < 0));
        }
        return layout = out;
    }

    private void centreOnNow() {
        Placed target = null;
        for (Placed p : layout()) {
            if (state(p.node().id()).equals("active") && (target == null || p.node().lane().equals("main"))) {
                target = p;
            }
        }
        if (target == null) {
            for (Placed p : layout()) {
                if (target == null || p.x() > target.x()) {
                    target = p;
                }
            }
        }
        if (target == null) {
            panX = 0;
            panY = 0;
            return;
        }
        panX = target.x() - (width / 2.0) / zoom;
        panY = target.y() - ((height - TOP - BOTTOM) / 2.0 - 30) / zoom;
    }

    private int sx(double x) {
        return (int) Math.round((x - panX) * zoom);
    }

    private int sy(double y) {
        return (int) Math.round(TOP + (y - panY) * zoom);
    }

    // ------------------------------------------------------------------ screen
    @Override
    protected void init() {
        open = this;
        if (!statesKnown) {
            PacketDistributor.sendToServer(new Payloads.RequestMap(false));
        }
        if (pendingStates != null) {
            applyStates(pendingStates);
            pendingStates = null;
        }
        layoutChapter = -2;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width - 58, height - 24, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Quest list"), b -> openClassic()).bounds(width - 58 - 76, height - 24, 72, 18).build());
        codexButton = Button.builder(Component.literal(showCodex ? "Journey" : "Codex"), b -> {
            showCodex = !showCodex;
            b.setMessage(Component.literal(showCodex ? "Journey" : "Codex"));
        }).bounds(width - 58 - 76 - 64, height - 24, 60, 18).build();
        addRenderableWidget(codexButton);
    }

    @Override
    public void removed() {
        if (open == this) {
            open = null;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void openClassic() {
        try {
            Class<?> c = Class.forName("net.mebahelrpgrquest.client.gui.QuestMapScreen");
            Screen classic = (Screen) c.getConstructor(Screen.class).newInstance(this);
            openingClassic = true;
            Minecraft.getInstance().setScreen(classic);
        } catch (Throwable t) {
            HalfAGod.LOG.warn("Could not open Mebahel's quest map: {}", t.toString());
        } finally {
            openingClassic = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        tick++;
        drawParchment(g);
        if (showCodex) {
            drawCodex(g, mx, my);
        } else {
            drawJourney(g, mx, my);
        }
        drawHeader(g, mx, my);
        // footer strip
        g.fill(0, height - BOTTOM, width, height, 0xE0241810);
        g.fill(0, height - BOTTOM, width, height - BOTTOM + 1, 0xFF6B5638);
        if (!showCodex) {
            legend(g, 8, height - 19);
        }
    }

    private void drawParchment(GuiGraphics g) {
        g.fill(0, 0, width, height, PAPER);
        // soft stains, fixed per screen size so they don't flicker
        long seed = 1234567L;
        for (int i = 0; i < 70; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((seed >>> 33) % Math.max(1, width));
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int y = (int) ((seed >>> 33) % Math.max(1, height));
            int r = 6 + (int) ((seed >>> 40) % 22);
            disc(g, x, y, r, 0x0C5A3A10);
        }
        // darkened edges
        int e = 26;
        g.fillGradient(0, 0, width, e, 0x50402A10, 0x00402A10);
        g.fillGradient(0, height - e, width, height, 0x00402A10, 0x50402A10);
        for (int i = 0; i < e; i++) {
            int a = (int) (0x50 * (1 - i / (double) e));
            g.fill(i, 0, i + 1, height, (a << 24) | 0x402A10);
            g.fill(width - 1 - i, 0, width - i, height, (a << 24) | 0x402A10);
        }
        g.fill(0, 0, width, 2, FRAME);
        g.fill(0, 0, 2, height, FRAME);
        g.fill(width - 2, 0, width, height, FRAME);
    }

    private void drawHeader(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP - 4, 0xE0241810);
        g.fill(0, TOP - 4, width, TOP - 3, 0xFF6B5638);
        if (showCodex) {
            g.drawString(font, "Codex: what you've learned", 10, 10, 0xFFF2D49A, false);
            return;
        }
        int x = 8;
        for (int i = 0; i < chapters.size(); i++) {
            if (!statesKnown || !chapterVisible(i)) {
                continue;
            }
            Chapter c = chapters.get(i);
            String label = c.name();
            int w = font.width(label) + 14;
            boolean sel = i == chapter;
            boolean hover = mx >= x && mx < x + w && my >= 6 && my < 24;
            g.fill(x, 6, x + w, 24, sel ? 0xFF6B4A22 : hover ? 0xFF4A3218 : 0xFF332312);
            if (sel) {
                g.fill(x, 22, x + w, 24, GOLD);
            }
            g.drawString(font, label, x + 7, 11, sel ? 0xFFFFE6B0 : 0xFFC8B08A, false);
            x += w + 4;
        }
        if (chapter >= 0 && chapter < chapters.size()) {
            Chapter c = chapters.get(chapter);
            String sub = c.subtitle();
            g.drawString(font, sub, width - 10 - font.width(sub), 11, 0xFFF2D49A, false);
        }
    }

    private void drawJourney(GuiGraphics g, int mx, int my) {
        if (!statesKnown || chapter < 0 || chapter >= chapters.size()) {
            g.drawCenteredString(font, statesKnown ? "The map is blank." : "Unrolling the map...", width / 2, height / 2, INK_SOFT);
            return;
        }
        List<Placed> placed = layout();
        Chapter c = chapters.get(chapter);
        Map<String, Placed> at = new HashMap<>();
        for (Placed p : placed) {
            at.put(p.node().id(), p);
        }
        g.enableScissor(0, TOP - 3, width, height - BOTTOM);
        // roads
        for (String[] e : c.edges()) {
            Placed a = at.get(e[0]);
            Placed b = at.get(e[1]);
            if (a == null || b == null) {
                continue;
            }
            boolean walked = state(a.node().id()).startsWith("done") && doneOrActive(b.node().id());
            trail(g, a.x(), a.y(), b.x(), b.y(), walked ? INK_SOFT : INK_FAINT, walked);
        }
        // the road ahead fades out after the last place you've reached on the main road
        Placed last = null;
        for (Placed p : placed) {
            if (p.node().lane().equals("main") && (last == null || p.x() > last.x())) {
                last = p;
            }
        }
        if (last != null && !state(last.node().id()).equals("locked")) {
            for (int i = 1; i <= 14; i++) {
                double t = i / 14.0;
                int x = last.x() + (int) (t * 110);
                int y = last.y() + (int) (Math.sin(t * 3) * 10);
                int alpha = (int) (0x90 * (1 - t));
                dot(g, sx(x), sy(y), (alpha << 24) | 0x6B5638);
            }
            g.drawString(font, "?", sx(last.x() + 118), sy(last.y() + 6) - 4, 0x606B5638, false);
        }
        // side-road names, written where each road starts
        Set<String> named = new HashSet<>();
        for (Placed p : placed) {
            String lane = p.node().lane();
            if (lane.equals("main") || named.contains(lane)) {
                continue;
            }
            Placed first = p;
            for (Placed q : placed) {
                if (q.node().lane().equals(lane) && q.x() < first.x()) {
                    first = q;
                }
            }
            named.add(lane);
            int col = c.laneColors().getOrDefault(lane, 0x6B5638);
            String nm = c.laneNames().getOrDefault(lane, lane);
            int ly = first.above() ? sy(first.y()) - R - 26 : sy(first.y()) + R + 18;
            g.drawString(font, nm, sx(first.x()) - 6, ly, 0xFF000000 | darken(col), false);
        }
        // seals
        hovered = null;
        for (Placed p : placed) {
            int x = sx(p.x());
            int y = sy(p.y());
            if (x < -60 || x > width + 60 || y < TOP - 40 || y > height + 40) {
                continue;
            }
            int r = Math.max(7, Math.round(R * zoom));
            boolean hover = (mx - x) * (mx - x) + (my - y) * (my - y) <= (r + 2) * (r + 2) && my > TOP && my < height - BOTTOM;
            if (hover) {
                hovered = p;
            }
            seal(g, p, x, y, r, hover);
        }
        g.disableScissor();
        // what you're doing now (or the quest you clicked)
        drawCard(g);
    }

    private static int darken(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 6 / 10;
        int gg = ((rgb >> 8) & 0xFF) * 6 / 10;
        int b = (rgb & 0xFF) * 6 / 10;
        return (r << 16) | (gg << 8) | b;
    }

    private void trail(GuiGraphics g, int ax, int ay, int bx, int by, int color, boolean solid) {
        double dx = bx - ax;
        double dy = by - ay;
        double len = Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(2, (int) (len * zoom / (solid ? 4 : 6)));
        // a gentle curve: control point pushed sideways from the midpoint
        double cx = (ax + bx) / 2.0 - dy * 0.15;
        double cy = (ay + by) / 2.0 + dx * 0.08;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = (1 - t) * (1 - t) * ax + 2 * (1 - t) * t * cx + t * t * bx;
            double y = (1 - t) * (1 - t) * ay + 2 * (1 - t) * t * cy + t * t * by;
            dot(g, sx(x), sy(y), color);
        }
    }

    private void dot(GuiGraphics g, int x, int y, int color) {
        int s = Math.max(1, Math.round(zoom * 1.5f));
        g.fill(x - s / 2, y - s / 2, x - s / 2 + s, y - s / 2 + s, color);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    private void seal(GuiGraphics g, Placed p, int x, int y, int r, boolean hover) {
        Node n = p.node();
        String s = state(n.id());
        boolean done = s.startsWith("done");
        boolean active = s.equals("active");
        boolean failed = s.equals("failed");
        if (active) {
            double pulse = (Math.sin(tick / 7.0) + 1) / 2;
            disc(g, x, y, r + 4 + (int) (pulse * 3), (0x30 + (int) (pulse * 0x30)) << 24 | 0xE0A93A);
        }
        if (n == selected) {
            disc(g, x, y, r + 3, 0x90FFFFFF);
        }
        disc(g, x + 1, y + 2, r, 0x40000000); // shadow
        int rim = done ? WAX_DARK : active ? GOLD_DARK : failed ? 0xFF555555 : INK_SOFT;
        int fill = done ? WAX : active ? GOLD : failed ? 0xFF8A8A8A : PAPER_DARK;
        disc(g, x, y, r, hover ? 0xFFFFF2D0 : rim);
        disc(g, x, y, r - 2, fill);
        // the quest's icon pressed into the seal
        g.pose().pushPose();
        float sc = (r - 2) * 2 / 16f * 0.8f;
        g.pose().translate(x - 8 * sc, y - 8 * sc, 0);
        g.pose().scale(sc, sc, 1);
        g.renderFakeItem(n.icon(), 0, 0);
        g.pose().popPose();
        // outcome marks on decisions
        if (s.equals("done_spare")) {
            disc(g, x + r - 2, y - r + 2, 3, 0xFF5FAF5F);
        } else if (s.equals("done_kill")) {
            disc(g, x + r - 2, y - r + 2, 3, 0xFF2A0808);
        }
        // the name, inked beside the seal
        if (zoom >= 0.7f) {
            String title = font.plainSubstrByWidth(n.title(), (int) (COL_W * zoom) - 6);
            int tw = font.width(title);
            int ty = p.above() ? y - r - 11 : y + r + 4;
            g.drawString(font, title, x - tw / 2, ty, active ? 0xFF7A3A00 : done ? INK : INK_SOFT, false);
        }
        if (active) {
            // "you are here" flag
            int fx = x + r + 2;
            int fy = y - r - 10;
            g.fill(fx, fy, fx + 1, fy + 12, INK);
            g.fill(fx + 1, fy, fx + 9, fy + 5, 0xFFB02020);
        }
    }

    private void drawCard(GuiGraphics g) {
        Node n = selected;
        boolean now = false;
        if (n == null) {
            for (Placed p : layout()) {
                if (state(p.node().id()).equals("active") && (n == null || p.node().lane().equals("main"))) {
                    n = p.node();
                    now = true;
                }
            }
        }
        if (n == null) {
            return;
        }
        int w = Math.min(260, width / 2);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal(now ? "Now: " + n.title() : n.title()).withColor(0x7A3A00), w - 16));
        String s = state(n.id());
        String status = switch (s) {
            case "done" -> "Done";
            case "done_spare" -> "Done: you showed mercy";
            case "done_kill" -> "Done: you chose the blade";
            case "active" -> "You're on this";
            case "open" -> "Ready to start";
            case "failed" -> "Failed: it will start again";
            default -> "";
        };
        lines.addAll(font.split(Component.literal(status).withColor(s.startsWith("done") ? 0x2E6B2E : 0x8A5A10), w - 16));
        lines.addAll(font.split(Component.literal(n.summary()).withColor(0x3B2A1A), w - 16));
        if (!s.startsWith("done") && !n.goals().isEmpty()) {
            for (String goal : n.goals()) {
                lines.addAll(font.split(Component.literal("• " + goal).withColor(0x4A3A6A), w - 16));
            }
        }
        int maxLines = Math.max(4, (height - TOP - BOTTOM - 20) / 10);
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int h = lines.size() * 10 + 12;
        int x = width - w - 10;
        int y = height - BOTTOM - h - 8;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF6B4A22);
        g.fill(x, y, x + w, y + h, 0xF6F2E4C4);
        int ly = y + 6;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 8, ly, 0xFF3B2A1A, false);
            ly += 10;
        }
    }

    private void legend(GuiGraphics g, int x, int y) {
        x = legendItem(g, x, y, WAX, "Done");
        x = legendItem(g, x, y, GOLD, "You are here");
        legendItem(g, x, y, PAPER_DARK, "Ready to start");
    }

    private int legendItem(GuiGraphics g, int x, int y, int color, String label) {
        disc(g, x + 4, y + 4, 4, color);
        g.drawString(font, label, x + 12, y, 0xFFE0C89A, false);
        return x + 22 + font.width(label);
    }

    private void drawCodex(GuiGraphics g, int mx, int my) {
        List<Lore> known = unlockedLore();
        int listW = Math.min(170, width / 3);
        int top = TOP + 6;
        int bottom = height - BOTTOM - 6;
        g.fill(10, top, 10 + listW, bottom, 0x30402A10);
        g.enableScissor(10, top, 10 + listW, bottom);
        int y = top + 4 - codexScroll;
        String cat = "";
        for (int i = 0; i < known.size(); i++) {
            Lore l = known.get(i);
            if (!l.category().equals(cat)) {
                cat = l.category();
                y += 4;
                g.drawString(font, cat, 16, y, 0xFF7A3A00, false);
                y += 12;
            }
            boolean sel = i == codexPick;
            boolean hover = mx >= 10 && mx < 10 + listW && my >= y - 1 && my < y + 10;
            if (sel || hover) {
                g.fill(12, y - 1, 8 + listW, y + 10, sel ? 0x50B07020 : 0x25402A10);
            }
            g.drawString(font, font.plainSubstrByWidth(l.title(), listW - 20), 22, y, INK, false);
            y += 12;
        }
        int locked = codex.size() - known.size();
        if (locked > 0) {
            g.drawString(font, locked + " more to discover", 16, y + 6, INK_SOFT, false);
        }
        g.disableScissor();
        if (known.isEmpty()) {
            return;
        }
        Lore l = known.get(Math.max(0, Math.min(codexPick, known.size() - 1)));
        int tx = 30 + listW;
        int tw = width - tx - 24;
        g.drawString(font, l.title(), tx, top + 6, 0xFF7A3A00, false);
        g.fill(tx, top + 18, tx + Math.min(tw, font.width(l.title()) + 30), top + 19, INK_SOFT);
        int ty = top + 26;
        for (FormattedCharSequence line : font.split(Component.literal(l.text()), tw)) {
            g.drawString(font, line, tx, ty, INK, false);
            ty += 11;
        }
    }

    private List<Lore> unlockedLore() {
        List<Lore> out = new ArrayList<>();
        for (String cat : new String[]{"People", "Places", "The Cult"}) {
            for (Lore l : codex) {
                if (l.category().equals(cat) && doneOrActive(l.unlock())) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (showCodex) {
            List<Lore> known = unlockedLore();
            int y = TOP + 10 - codexScroll;
            String cat = "";
            for (int i = 0; i < known.size(); i++) {
                if (!known.get(i).category().equals(cat)) {
                    cat = known.get(i).category();
                    y += 16;
                }
                if (mx >= 10 && mx < 10 + Math.min(170, width / 3) && my >= y - 1 && my < y + 10) {
                    codexPick = i;
                    return true;
                }
                y += 12;
            }
            return false;
        }
        if (my < TOP) {
            int x = 8;
            for (int i = 0; i < chapters.size(); i++) {
                if (!statesKnown || !chapterVisible(i)) {
                    continue;
                }
                int w = font.width(chapters.get(i).name()) + 14;
                if (mx >= x && mx < x + w) {
                    chapter = i;
                    selected = null;
                    layoutChapter = -2;
                    centreOnNow();
                    return true;
                }
                x += w + 4;
            }
            return false;
        }
        dragging = button == 0 && my < height - BOTTOM;
        dragDist = 0;
        return dragging;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            panX -= dx / zoom;
            panY -= dy / zoom;
            dragDist += Math.abs(dx) + Math.abs(dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            if (dragDist < 4) {
                selected = hovered == null ? null : hovered.node();
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sxAmount, double syAmount) {
        if (showCodex) {
            codexScroll = (int) Math.max(0, codexScroll - syAmount * 20);
            return true;
        }
        if (hasControlDown()) {
            double wx = panX + mx / zoom;
            double wy = panY + (my - TOP) / zoom;
            zoom = (float) Math.max(0.55, Math.min(1.8, zoom * (syAmount > 0 ? 1.12 : 1 / 1.12)));
            panX = wx - mx / zoom;
            panY = wy - (my - TOP) / zoom;
        } else if (hasShiftDown()) {
            panY -= syAmount * 30 / zoom;
        } else {
            panX -= (syAmount + sxAmount) * 50 / zoom;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!showCodex && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            int dir = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            for (int i = chapter + dir; i >= 0 && i < chapters.size(); i += dir) {
                if (chapterVisible(i)) {
                    chapter = i;
                    selected = null;
                    layoutChapter = -2;
                    centreOnNow();
                    break;
                }
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

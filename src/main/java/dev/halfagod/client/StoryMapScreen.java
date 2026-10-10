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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The story map: one page per act. Inside an act, quests run left to right in story order, in lanes (the main story,
 * each side arc, the reputation road, Mebahel's questlines, side quests), so you can see what you're doing, what
 * comes next and what runs alongside it.
 */
public final class StoryMapScreen extends Screen {
    private static final int NODE_W = 120;
    private static final int NODE_H = 22;
    private static final int COL_W = 146;
    private static final int ROW_H = 30;
    private static final int LANE_PAD = 18;
    private static final int GUTTER = 112;
    private static final int TOP = 40;
    private static final int BOTTOM = 26;
    private static final int PANEL_W = 184;

    /** set while the map deliberately opens Mebahel's own map, so it isn't swapped again */
    public static boolean openingClassic;
    private static StoryMapScreen open;
    private static JsonObject pendingStates;

    private record Lane(String id, String name, int color, int rows, int y, int height) {
    }

    private record Node(String id, String title, String lane, int col, int row, String summary, List<String> goals,
                        ItemStack icon, boolean choice, int x, int y) {
    }

    private record Chapter(String id, String name, String subtitle, List<Lane> lanes, List<Node> nodes, List<String[]> edges,
                           int width, int height) {
    }

    private final Screen parent;
    private final List<Chapter> chapters = new ArrayList<>();
    private Map<String, String> states = new HashMap<>();
    private boolean statesKnown;
    private boolean op;
    private boolean revealAll;
    private int chapter;
    private double panX;
    private double panY;
    private float zoom = 1f;
    private Node selected;
    private Node hovered;
    private boolean dragging;
    private double dragDist;
    private List<FormattedCharSequence> detail = List.of();
    private int detailScroll;
    private final Map<String, Set<String>> preds = new HashMap<>();
    private Button revealButton;
    private int tick;

    public StoryMapScreen(Screen parent) {
        super(Component.literal("The Story"));
        this.parent = parent;
        load();
    }

    // ------------------------------------------------------------------ data
    private void load() {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager()
                .getResource(ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "story/map.json"));
        if (res.isEmpty()) {
            return;
        }
        try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement ce : root.getAsJsonArray("chapters")) {
                JsonObject c = ce.getAsJsonObject();
                List<Lane> lanes = new ArrayList<>();
                Map<String, Lane> laneById = new HashMap<>();
                int y = 0;
                for (JsonElement le : c.getAsJsonArray("lanes")) {
                    JsonObject l = le.getAsJsonObject();
                    int rows = l.get("rows").getAsInt();
                    int h = LANE_PAD + rows * ROW_H + 6;
                    Lane lane = new Lane(l.get("id").getAsString(), l.get("name").getAsString(), l.get("color").getAsInt(), rows, y, h);
                    lanes.add(lane);
                    laneById.put(lane.id(), lane);
                    y += h + 4;
                }
                List<Node> nodes = new ArrayList<>();
                int maxX = 0;
                for (JsonElement ne : c.getAsJsonArray("nodes")) {
                    JsonObject n = ne.getAsJsonObject();
                    Lane lane = laneById.get(n.get("lane").getAsString());
                    int col = n.get("col").getAsInt();
                    int row = n.get("row").getAsInt();
                    int nx = col * COL_W;
                    int ny = lane.y() + LANE_PAD + row * ROW_H;
                    List<String> goals = new ArrayList<>();
                    for (JsonElement g : n.getAsJsonArray("goals")) {
                        goals.add(g.getAsString());
                    }
                    var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(n.get("icon").getAsString()));
                    nodes.add(new Node(n.get("id").getAsString(), n.get("title").getAsString(), lane.id(), col, row,
                            n.get("summary").getAsString(), goals, new ItemStack(item == Items.AIR ? Items.PAPER : item),
                            n.get("choice").getAsBoolean(), nx, ny));
                    maxX = Math.max(maxX, nx + NODE_W);
                }
                List<String[]> edges = new ArrayList<>();
                for (JsonElement ee : c.getAsJsonArray("edges")) {
                    JsonArray a = ee.getAsJsonArray();
                    edges.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsString()});
                    preds.computeIfAbsent(a.get(1).getAsString(), k -> new HashSet<>()).add(a.get(0).getAsString());
                }
                chapters.add(new Chapter(c.get("id").getAsString(), c.get("name").getAsString(), c.get("subtitle").getAsString(),
                        lanes, nodes, edges, maxX, y));
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
        op = o.has("op") && o.get("op").getAsBoolean();
        statesKnown = true;
        if (revealButton != null) {
            revealButton.visible = op;
        }
        if (selected == null) {
            jumpToCurrent();
        }
        select(selected);
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
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width - 64, height - 22, 58, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Mebahel's map"), b -> openClassic()).bounds(width - 64 - 92, height - 22, 88, 18).build());
        revealButton = Button.builder(Component.literal("Reveal all"), b -> {
            revealAll = !revealAll;
            b.setMessage(Component.literal(revealAll ? "Hide unreached" : "Reveal all"));
            select(selected);
        }).bounds(width - 64 - 92 - 84, height - 22, 80, 18).build();
        revealButton.visible = op;
        addRenderableWidget(revealButton);
        select(selected);
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

    private int bodyLeft() {
        return GUTTER;
    }

    private int bodyRight() {
        return selected != null ? width - PANEL_W - 8 : width - 8;
    }

    private Chapter current() {
        return chapters.isEmpty() ? null : chapters.get(Math.max(0, Math.min(chapter, chapters.size() - 1)));
    }

    private String state(String id) {
        return states.getOrDefault(id, "locked");
    }

    private boolean reached(Node n) {
        if (revealAll || !"locked".equals(state(n.id()))) {
            return true;
        }
        for (String p : preds.getOrDefault(n.id(), Set.of())) {
            String s = state(p);
            if (s.startsWith("done") || s.equals("active")) {
                return true;
            }
        }
        return false;
    }

    /** Opens the act you're in and centres on what you're doing now. */
    private void jumpToCurrent() {
        Node best = null;
        int bestChapter = 0;
        int bestScore = -1;
        for (int ci = 0; ci < chapters.size(); ci++) {
            for (Node n : chapters.get(ci).nodes()) {
                String s = state(n.id());
                int score = s.equals("active") ? (n.lane().equals("main") ? 1000 : 800) + ci : s.startsWith("done") ? ci * 2 : -1;
                if (score > bestScore) {
                    bestScore = score;
                    best = n;
                    bestChapter = ci;
                }
            }
        }
        chapter = bestChapter;
        if (best != null) {
            centreOn(best);
        } else {
            panX = 0;
            panY = 0;
        }
    }

    private void centreOn(Node n) {
        double viewW = (bodyRight() - bodyLeft()) / zoom;
        double viewH = (height - TOP - BOTTOM) / zoom;
        panX = n.x() + NODE_W / 2.0 - viewW / 2;
        panY = n.y() + NODE_H / 2.0 - viewH / 2;
        clampPan();
    }

    private void clampPan() {
        Chapter c = current();
        if (c == null) {
            return;
        }
        double viewW = (bodyRight() - bodyLeft()) / zoom;
        double viewH = (height - TOP - BOTTOM) / zoom;
        panX = Math.max(-20, Math.min(Math.max(-20, c.width() - viewW + 30), panX));
        panY = Math.max(-6, Math.min(Math.max(-6, c.height() - viewH + 10), panY));
    }

    private int sx(double wx) {
        return (int) Math.round(bodyLeft() + (wx - panX) * zoom);
    }

    private int sy(double wy) {
        return (int) Math.round(TOP + (wy - panY) * zoom);
    }

    // ------------------------------------------------------------------ drawing
    /** Everything except the buttons is drawn here; Screen.render then draws the buttons on top. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xF0120E0B, 0xF0080606);
        drawMap(g, mx, my);
    }

    private void drawMap(GuiGraphics g, int mx, int my) {
        tick++;
        Chapter c = current();
        drawTabs(g, mx, my);
        if (c == null) {
            g.drawCenteredString(font, "The story map is missing from this build.", width / 2, height / 2, 0xFFB8B2A6);
            return;
        }
        hovered = null;
        int top = TOP;
        int bottom = height - BOTTOM;
        int left = bodyLeft();
        int right = bodyRight();

        // lanes (bands across the body) and their names in the gutter
        g.enableScissor(0, top, right, bottom);
        for (int i = 0; i < c.lanes().size(); i++) {
            Lane l = c.lanes().get(i);
            int y1 = sy(l.y());
            int y2 = sy(l.y() + l.height());
            g.fill(left, y1, right, y2, i % 2 == 0 ? 0x14FFFFFF : 0x0AFFFFFF);
            g.fill(left, y1, left + 2, y2, 0xFF000000 | l.color());
        }
        g.disableScissor();

        g.enableScissor(left, top, right, bottom);
        g.pose().pushPose();
        Map<String, Node> byId = new HashMap<>();
        for (Node n : c.nodes()) {
            byId.put(n.id(), n);
        }
        for (String[] e : c.edges()) {
            Node a = byId.get(e[0]);
            Node b = byId.get(e[1]);
            if (a != null && b != null && reached(a) && reached(b)) {
                drawEdge(g, a, b, e[2]);
            }
        }
        for (Node n : c.nodes()) {
            int x = sx(n.x());
            int y = sy(n.y());
            int w = Math.round(NODE_W * zoom);
            int h = Math.round(NODE_H * zoom);
            if (x > right || x + w < left || y > bottom || y + h < top) {
                continue;
            }
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h && mx >= left && mx < right && my >= top && my < bottom;
            if (hover) {
                hovered = n;
            }
            drawNode(g, n, x, y, w, h, hover);
        }
        g.pose().popPose();
        g.disableScissor();

        // gutter with lane names (drawn over the body edge)
        g.fill(0, top, left, bottom, 0xF0100C0A);
        g.fill(left - 1, top, left, bottom, 0xFF3A2E1E);
        g.enableScissor(0, top, left, bottom);
        for (Lane l : c.lanes()) {
            int y1 = sy(l.y());
            g.fill(6, y1 + 5, 9, y1 + 13, 0xFF000000 | l.color());
            for (FormattedCharSequence line : font.split(Component.literal(l.name()), left - 18)) {
                g.drawString(font, line, 13, y1 + 5, 0xFF000000 | l.color(), false);
                y1 += 10;
            }
        }
        g.disableScissor();

        // chapter heading
        g.drawString(font, c.name() + ": " + c.subtitle(), 8, 6, 0xFFF2C14E, true);
        String hint = statesKnown ? "Drag to move  ·  Scroll  ·  Ctrl+scroll to zoom  ·  Click a quest" : "Reading your story...";
        g.drawString(font, hint, width - 8 - font.width(hint), 6, 0xFF6E675C, false);

        drawLegend(g);
        if (selected != null) {
            drawPanel(g);
        }
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        int x = 8;
        int y = 22;
        for (int i = 0; i < chapters.size(); i++) {
            Chapter c = chapters.get(i);
            String label = c.name();
            int w = font.width(label) + 12;
            boolean sel = i == chapter;
            boolean hover = mx >= x && mx < x + w && my >= y - 3 && my < y + 12;
            boolean active = false;
            for (Node n : c.nodes()) {
                if ("active".equals(state(n.id()))) {
                    active = true;
                    break;
                }
            }
            g.fill(x, y - 3, x + w, y + 12, sel ? 0xFF3A2E1E : hover ? 0x40FFFFFF : 0x20FFFFFF);
            if (sel) {
                g.fill(x, y + 11, x + w, y + 12, 0xFFF2C14E);
            }
            g.drawString(font, label, x + 6, y, sel ? 0xFFF2C14E : 0xFFB8B2A6, false);
            if (active) {
                g.fill(x + w - 4, y - 1, x + w - 2, y + 1, 0xFFF2C14E);
            }
            x += w + 3;
        }
    }

    private void drawEdge(GuiGraphics g, Node a, Node b, String kind) {
        int color = switch (kind) {
            case "spare" -> 0xFF6DCB5B;
            case "kill" -> 0xFFD2483A;
            case "letter" -> 0xFFB070E0;
            case "unlocks" -> 0xFF7FB8F0;
            case "needs" -> 0xFF5A554C;
            default -> 0xFF8A7F6A;
        };
        boolean lit = state(a.id()).startsWith("done") && !state(b.id()).equals("locked");
        if (!lit) {
            color = (color & 0x00FFFFFF) | 0x80000000;
        }
        int x1 = sx(a.x() + NODE_W);
        int y1 = sy(a.y() + NODE_H / 2.0);
        int x2 = sx(b.x());
        int y2 = sy(b.y() + NODE_H / 2.0);
        int t = Math.max(1, Math.round(zoom));
        if (x2 > x1 + 6) {
            int mid = x1 + Math.max(4, (x2 - x1) / 2);
            hline(g, x1, mid, y1, t, color);
            vline(g, mid, y1, y2, t, color);
            hline(g, mid, x2, y2, t, color);
            // arrowhead
            g.fill(x2 - 3, y2 - 2, x2 - 1, y2 + 2 + t - 1, color);
        } else {
            // a step that runs alongside: go round underneath
            int out = x1 + 6;
            int in = x2 - 6;
            int below = Math.max(y1, y2) + Math.round(NODE_H * zoom / 2f) + 3;
            hline(g, x1, out, y1, t, color);
            vline(g, out, y1, below, t, color);
            hline(g, in, out, below, t, color);
            vline(g, in, below, y2, t, color);
            hline(g, in, x2, y2, t, color);
        }
    }

    private static void hline(GuiGraphics g, int xa, int xb, int y, int t, int c) {
        g.fill(Math.min(xa, xb), y, Math.max(xa, xb) + t, y + t, c);
    }

    private static void vline(GuiGraphics g, int x, int ya, int yb, int t, int c) {
        g.fill(x, Math.min(ya, yb), x + t, Math.max(ya, yb) + t, c);
    }

    private void drawNode(GuiGraphics g, Node n, int x, int y, int w, int h, boolean hover) {
        String s = state(n.id());
        boolean known = reached(n);
        int bg;
        int border;
        int text;
        switch (s.startsWith("done") ? "done" : s) {
            case "done" -> {
                bg = 0xFF1C261B;
                border = 0xFF5FAF5F;
                text = 0xFFB8D8B0;
            }
            case "active" -> {
                bg = 0xFF3A2E14;
                double pulse = (Math.sin(tick / 6.0) + 1) / 2;
                border = 0xFF000000 | lerp(0xB08A30, 0xFFE07A, pulse);
                text = 0xFFFFE9A8;
            }
            case "open" -> {
                bg = 0xFF2A2620;
                border = 0xFFE8E0D0;
                text = 0xFFF0EAE0;
            }
            case "failed" -> {
                bg = 0xFF2A1614;
                border = 0xFFD2483A;
                text = 0xFFE0B0A8;
            }
            default -> {
                bg = 0xFF161412;
                border = 0xFF3A3530;
                text = known ? 0xFF8A8278 : 0xFF5A554C;
            }
        }
        if (n == selected) {
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x60F2C14E);
        }
        if ("active".equals(s)) {
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, (0x30 << 24) | (border & 0xFFFFFF));
        }
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, hover ? 0xFFFFFFFF : border);
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, 0x18FFFFFF);

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(zoom, zoom, 1);
        if (known) {
            g.renderFakeItem(n.icon(), 3, 3);
        } else {
            g.drawString(font, "?", 8, 7, 0xFF5A554C, false);
        }
        String title = known ? n.title() : "???";
        g.drawString(font, font.plainSubstrByWidth(title, NODE_W - 30), 22, 7, text, false);
        if (s.startsWith("done")) {
            g.drawString(font, "✔", NODE_W - 9, 7, 0xFF6DCB5B, false);
        } else if (n.choice() && known) {
            // a small diamond marks a decision
            g.fill(NODE_W - 7, 9, NODE_W - 5, 13, 0xFFE07F6F);
            g.fill(NODE_W - 8, 10, NODE_W - 4, 12, 0xFFE07F6F);
        }
        g.pose().popPose();
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (gg << 8) | bb;
    }

    private void drawLegend(GuiGraphics g) {
        int x = 8;
        int y = height - 16;
        x = legend(g, x, y, 0xFF5FAF5F, "Done");
        x = legend(g, x, y, 0xFFF2C14E, "You're on it");
        x = legend(g, x, y, 0xFFE8E0D0, "Ready to start");
        legend(g, x, y, 0xFF3A3530, "Not yet");
    }

    private int legend(GuiGraphics g, int x, int y, int color, String label) {
        g.fill(x, y + 1, x + 7, y + 8, color);
        g.drawString(font, label, x + 10, y, 0xFF9C958A, false);
        return x + 16 + font.width(label);
    }

    private void drawPanel(GuiGraphics g) {
        int x = width - PANEL_W - 4;
        int top = TOP;
        int bottom = height - BOTTOM - 2;
        g.fill(x - 1, top - 1, width - 3, bottom + 1, 0xFF3A2E1E);
        g.fill(x, top, width - 4, bottom, 0xF0181410);
        g.enableScissor(x, top, width - 4, bottom);
        int y = top + 6 - detailScroll;
        for (FormattedCharSequence line : detail) {
            g.drawString(font, line, x + 6, y, 0xFFFFFFFF, false);
            y += 10;
        }
        g.disableScissor();
    }

    private void select(Node n) {
        selected = n;
        detailScroll = 0;
        if (n == null) {
            detail = List.of();
            return;
        }
        int w = PANEL_W - 14;
        List<FormattedCharSequence> out = new ArrayList<>();
        boolean known = reached(n);
        String s = state(n.id());
        out.addAll(font.split(Component.literal(known ? n.title() : "???").withColor(0xF2C14E), w));
        String status = switch (s) {
            case "done" -> "Done";
            case "done_spare" -> "Done: you showed mercy";
            case "done_kill" -> "Done: you chose the blade";
            case "active" -> "You're on this now";
            case "open" -> "Ready to start";
            case "failed" -> "Failed (it will start again)";
            default -> "Not reached yet";
        };
        out.addAll(font.split(Component.literal(status).withColor(s.startsWith("done") ? 0x6DCB5B : s.equals("active") ? 0xF2C14E : 0x9C958A), w));
        out.add(FormattedCharSequence.EMPTY);
        if (!known) {
            out.addAll(font.split(Component.literal("You haven't reached this part of the story yet.").withColor(0x9C958A), w));
        } else {
            out.addAll(font.split(Component.literal(n.summary()).withColor(0xE8E0D0), w));
            if (!n.goals().isEmpty()) {
                out.add(FormattedCharSequence.EMPTY);
                out.addAll(font.split(Component.literal("What you do").withColor(0xF2C14E), w));
                for (String goal : n.goals()) {
                    out.addAll(font.split(Component.literal("• " + goal).withColor(0xB8D8F0), w));
                }
            }
            if (n.choice()) {
                out.add(FormattedCharSequence.EMPTY);
                out.addAll(font.split(Component.literal("A decision: spare or strike. It changes what comes next.").withColor(0xE07F6F), w));
            }
        }
        detail = out;
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        // tabs
        int x = 8;
        for (int i = 0; i < chapters.size(); i++) {
            int w = font.width(chapters.get(i).name()) + 12;
            if (mx >= x && mx < x + w && my >= 19 && my < 34) {
                chapter = i;
                selected = null;
                panX = 0;
                panY = 0;
                for (Node n : current().nodes()) {
                    if ("active".equals(state(n.id()))) {
                        centreOn(n);
                        break;
                    }
                }
                return true;
            }
            x += w + 3;
        }
        if (selected != null && mx >= width - PANEL_W - 4) {
            return true;
        }
        dragging = button == 0 && my >= TOP && my < height - BOTTOM && mx >= bodyLeft();
        dragDist = 0;
        return dragging;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            panX -= dx / zoom;
            panY -= dy / zoom;
            dragDist += Math.abs(dx) + Math.abs(dy);
            clampPan();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            if (dragDist < 4) {
                select(hovered);
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sxAmount, double syAmount) {
        if (selected != null && mx >= width - PANEL_W - 4) {
            detailScroll = (int) Math.max(0, Math.min(Math.max(0, detail.size() * 10 - (height - TOP - BOTTOM - 20)), detailScroll - syAmount * 20));
            return true;
        }
        if (hasControlDown()) {
            double wx = panX + (mx - bodyLeft()) / zoom;
            double wy = panY + (my - TOP) / zoom;
            zoom = (float) Math.max(0.5, Math.min(1.6, zoom * (syAmount > 0 ? 1.12 : 1 / 1.12)));
            panX = wx - (mx - bodyLeft()) / zoom;
            panY = wy - (my - TOP) / zoom;
        } else if (hasShiftDown()) {
            panX -= syAmount * 40 / zoom;
        } else {
            panY -= syAmount * 30 / zoom;
            panX -= sxAmount * 40 / zoom;
        }
        clampPan();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_LEFT && chapter > 0) {
            chapter--;
            selected = null;
            jumpWithin();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT && chapter < chapters.size() - 1) {
            chapter++;
            selected = null;
            jumpWithin();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void jumpWithin() {
        panX = 0;
        panY = 0;
        for (Node n : current().nodes()) {
            if ("active".equals(state(n.id()))) {
                centreOn(n);
                return;
            }
        }
    }
}

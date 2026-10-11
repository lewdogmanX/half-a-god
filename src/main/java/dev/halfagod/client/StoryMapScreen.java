package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.halfagod.HalfAGod;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Your journey, drawn as an old map. Each act is a page: the main story is a broad road through the middle, and the side
 * stories run alongside it above and below. Every quest you've reached is a wax seal on the road (red when done, gold
 * where you are now, a paper token when it's ready to start). Nothing you haven't reached is drawn; past the last place
 * you've been the paper fogs over. Click a seal to open its page in the journal on the right. The Codex is a book of
 * what you've learned about people, places and the cult.
 */
public final class StoryMapScreen extends Screen {
    // ---- palette
    private static final int INK = 0xFF3B2A1A;
    private static final int INK_SOFT = 0xFF6B5638;
    private static final int PAPER = 0xFFE9D8B4;
    private static final int PAPER_LIGHT = 0xFFF4E9CF;
    private static final int LEATHER = 0xFF2A1C11;
    private static final int LEATHER_HI = 0xFF3B2918;
    private static final int GILT = 0xFFC9A55A;
    private static final int GILT_BRIGHT = 0xFFF2D49A;
    private static final int RED_INK = 0xFF8E1B1B;
    private static final int MERCY = 0xFF4F9A4F;
    private static final int BLADE = 0xFF3A0A0A;

    // ---- world layout (map units, 1:1 with GUI pixels at zoom 1)
    private static final int COL_W = 132;
    private static final int ROW_H = 52;
    private static final int LANE_GAP = 28;
    private static final int R = 12;

    // ---- screen layout
    private static final int TOP = 46;
    private static final int FOOT = 24;

    public static boolean openingClassic;
    private static StoryMapScreen open;
    private static JsonObject pendingStates;

    private record Node(String id, String title, String lane, String group, int col, int row, String summary, List<String> goals,
                        List<Integer> stages, ItemStack icon, boolean choice, int chapter) {
    }

    private record Chapter(String id, String name, String subtitle, List<Node> nodes, List<String[]> edges,
                           Map<String, String> laneNames, Map<String, Integer> laneColors) {
    }

    private record Placed(Node node, double x, double y) {
    }

    private record Sign(String text, double x, double y, int color) {
    }

    private record Deco(int kind, double x, double y) {
    }

    private record Lore(String category, String title, String unlock, String text) {
    }

    private record Hit(int x1, int y1, int x2, int y2, Runnable click, List<Component> tip) {
    }

    private final Screen parent;
    private final List<Chapter> chapters = new ArrayList<>();
    private final List<Lore> codex = new ArrayList<>();
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, Set<String>> preds = new HashMap<>();
    private final Map<String, Set<String>> succs = new HashMap<>();

    private Map<String, String> states = new HashMap<>();
    private Map<String, Integer> stages = new HashMap<>();
    private double rep;
    private boolean statesKnown;
    private int chapter = -1;

    // layout of the current page
    private final List<Placed> placed = new ArrayList<>();
    private final Map<String, Placed> placedById = new HashMap<>();
    private final List<Sign> signs = new ArrayList<>();
    private final List<Deco> decos = new ArrayList<>();
    private double minX, maxX, minY, maxY;
    private boolean layoutDirty = true;

    // camera
    private double camX, camY, tgtX, tgtY;
    private float zoom = 1f;
    private boolean dragging;
    private double dragDist;

    // journal page
    private Node selected;
    private Node shownNode;
    private float panelAnim;
    private int panelScroll;
    private int panelContentH;

    // codex
    private boolean showCodex;
    private int codexPick = -1;
    private int codexScroll;
    private final Set<String> seenLore = new HashSet<>();

    // frame state
    private final List<Hit> hits = new ArrayList<>();
    private Placed hoveredSeal;
    private long lastFrame;
    private float time;
    private long lastRefresh;

    public StoryMapScreen(Screen parent) {
        super(Component.literal("Your Journey"));
        this.parent = parent;
        load();
        loadSeen();
    }

    // =================================================================== data
    private void load() {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager()
                    .getResource(ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "story/map.json"));
            if (res.isPresent()) {
                try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (JsonElement ce : root.getAsJsonArray("chapters")) {
                        JsonObject c = ce.getAsJsonObject();
                        int ci = chapters.size();
                        Map<String, String> laneNames = new LinkedHashMap<>();
                        Map<String, Integer> laneColors = new HashMap<>();
                        for (JsonElement le : c.getAsJsonArray("lanes")) {
                            JsonObject l = le.getAsJsonObject();
                            laneNames.put(l.get("id").getAsString(), l.get("name").getAsString());
                            laneColors.put(l.get("id").getAsString(), l.get("color").getAsInt());
                        }
                        List<Node> list = new ArrayList<>();
                        for (JsonElement ne : c.getAsJsonArray("nodes")) {
                            JsonObject n = ne.getAsJsonObject();
                            List<String> goals = new ArrayList<>();
                            n.getAsJsonArray("goals").forEach(g -> goals.add(g.getAsString()));
                            List<Integer> st = new ArrayList<>();
                            if (n.has("stages")) {
                                n.getAsJsonArray("stages").forEach(g -> st.add(g.getAsInt()));
                            }
                            ResourceLocation iconId = ResourceLocation.tryParse(n.get("icon").getAsString());
                            var item = iconId == null ? Items.PAPER : BuiltInRegistries.ITEM.get(iconId);
                            Node node = new Node(n.get("id").getAsString(), n.get("title").getAsString(), n.get("lane").getAsString(),
                                    n.has("group") ? n.get("group").getAsString() : "",
                                    n.get("col").getAsInt(), n.get("row").getAsInt(), n.get("summary").getAsString(), goals, st,
                                    new ItemStack(item == Items.AIR ? Items.PAPER : item), n.get("choice").getAsBoolean(), ci);
                            list.add(node);
                            nodes.put(node.id(), node);
                        }
                        List<String[]> edges = new ArrayList<>();
                        for (JsonElement ee : c.getAsJsonArray("edges")) {
                            JsonArray a = ee.getAsJsonArray();
                            String from = a.get(0).getAsString(), to = a.get(1).getAsString();
                            edges.add(new String[]{from, to, a.get(2).getAsString()});
                            preds.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
                            succs.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
                        }
                        chapters.add(new Chapter(c.get("id").getAsString(), c.get("name").getAsString(), c.get("subtitle").getAsString(),
                                list, edges, laneNames, laneColors));
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

    private static Path seenFile() {
        return FMLPaths.CONFIGDIR.get().resolve("half_a_god-codex-read.txt");
    }

    private void loadSeen() {
        try {
            if (Files.exists(seenFile())) {
                seenLore.addAll(Files.readAllLines(seenFile(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // the "new" marks just show again
        }
    }

    private void markSeen(String title) {
        if (seenLore.add(title)) {
            try {
                Files.write(seenFile(), seenLore, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // not important
            }
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
        Map<String, Integer> st = new HashMap<>();
        if (o.has("stages")) {
            o.getAsJsonObject("stages").entrySet().forEach(e -> st.put(e.getKey(), e.getValue().getAsInt()));
        }
        boolean changed = !s.equals(states);
        states = s;
        stages = st;
        rep = o.has("rep") ? o.get("rep").getAsDouble() : 0;
        if (changed) {
            layoutDirty = true;
        }
        if (!statesKnown) {
            statesKnown = true;
            showChapter(currentChapter(), true);
        }
    }

    private String state(String id) {
        return states.getOrDefault(id, "locked");
    }

    private boolean done(String id) {
        return state(id).startsWith("done");
    }

    private boolean reached(String id) {
        String s = state(id);
        return s.startsWith("done") || s.equals("active") || s.equals("failed");
    }

    /** Only what you've reached: done, on now, or ready to start right after something you've done. */
    private boolean visible(Node n) {
        if (reached(n.id())) {
            return true;
        }
        if (state(n.id()).equals("open")) {
            for (String p : preds.getOrDefault(n.id(), Set.of())) {
                if (done(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean chapterReached(int ci) {
        for (Node n : chapters.get(ci).nodes()) {
            if (visible(n)) {
                return true;
            }
        }
        return false;
    }

    /** The quest you're on in the main story (or any quest you're on). */
    private Node nowNode() {
        Node best = null;
        for (Chapter c : chapters) {
            for (Node n : c.nodes()) {
                if (state(n.id()).equals("active")) {
                    if (best == null || (n.lane().equals("main") && (!best.lane().equals("main") || n.chapter() >= best.chapter()))) {
                        best = n;
                    }
                }
            }
        }
        return best;
    }

    private int currentChapter() {
        Node now = nowNode();
        if (now != null) {
            return now.chapter();
        }
        int best = 0;
        for (int ci = 0; ci < chapters.size(); ci++) {
            if (chapterReached(ci) && !chapters.get(ci).id().equals("anytime")) {
                best = ci;
            }
        }
        return best;
    }

    private String currentGoal(Node n) {
        if (n.goals().isEmpty()) {
            return "";
        }
        int stage = stages.getOrDefault(n.id(), 0);
        for (int i = 0; i < n.goals().size(); i++) {
            int s = i < n.stages().size() ? n.stages().get(i) : 0;
            if (s == stage) {
                return n.goals().get(i);
            }
        }
        return n.goals().get(0);
    }

    // =================================================================== layout
    private void relayout() {
        layoutDirty = false;
        placed.clear();
        placedById.clear();
        signs.clear();
        decos.clear();
        if (chapter < 0 || chapter >= chapters.size()) {
            return;
        }
        Chapter c = chapters.get(chapter);
        List<Node> vis = new ArrayList<>();
        for (Node n : c.nodes()) {
            if (visible(n)) {
                vis.add(n);
            }
        }
        // columns and rows are squeezed to what you can see, so the page has no holes
        TreeSet<Integer> gridCols = new TreeSet<>(), mebCols = new TreeSet<>();
        Map<String, TreeSet<Integer>> laneRows = new HashMap<>();
        for (Node n : vis) {
            (n.lane().equals("mebahel") ? mebCols : gridCols).add(n.col());
            laneRows.computeIfAbsent(n.lane(), k -> new TreeSet<>()).add(n.row());
        }
        List<Integer> gc = new ArrayList<>(gridCols), mc = new ArrayList<>(mebCols);
        Map<String, double[]> laneY = new HashMap<>();
        int mainRows = laneRows.containsKey("main") ? laneRows.get("main").size() : 1;
        double mainTop = -(mainRows - 1) * ROW_H / 2.0;
        laneY.put("main", new double[]{mainTop, 1});
        double aboveEdge = mainTop - ROW_H / 2.0, belowEdge = mainTop + (mainRows - 1) * ROW_H + ROW_H / 2.0;
        double above = 0, below = 0;
        List<String> order = new ArrayList<>(c.laneNames().keySet());
        order.remove("main");
        if (order.remove("mebahel")) {
            order.add("mebahel"); // Mebahel's questlines run furthest out
        }
        for (String lane : order) {
            TreeSet<Integer> rows = laneRows.get(lane);
            if (rows == null) {
                continue;
            }
            double band = rows.size() * ROW_H + LANE_GAP;
            if (above <= below) {
                laneY.put(lane, new double[]{aboveEdge - LANE_GAP - above - ROW_H / 2.0, -1});
                above += band;
            } else {
                laneY.put(lane, new double[]{belowEdge + LANE_GAP + below + ROW_H / 2.0, 1});
                below += band;
            }
        }
        for (Node n : vis) {
            double[] ly = laneY.get(n.lane());
            if (ly == null) {
                continue;
            }
            int rowIx = laneRows.get(n.lane()).headSet(n.row()).size();
            int colIx = n.lane().equals("mebahel") ? mc.indexOf(n.col()) : gc.indexOf(n.col());
            double x = colIx * COL_W;
            double y = ly[0] + ly[1] * rowIx * ROW_H + Math.sin(colIx * 0.9 + Math.floorMod(n.lane().hashCode(), 7)) * 5;
            Placed p = new Placed(n, x, y);
            placed.add(p);
            placedById.put(n.id(), p);
        }
        // signposts at the start of every side road (and every one of Mebahel's questlines)
        Map<String, Placed> firstOf = new LinkedHashMap<>();
        for (Placed p : placed) {
            Node n = p.node();
            if (n.lane().equals("main")) {
                continue;
            }
            String key = n.lane().equals("mebahel") ? "mebahel|" + n.group() : n.lane();
            Placed f = firstOf.get(key);
            if (f == null || p.x() < f.x() || (p.x() == f.x() && Math.abs(p.y()) < Math.abs(f.y()))) {
                firstOf.put(key, p);
            }
        }
        for (Placed p : firstOf.values()) {
            Node n = p.node();
            String text = n.lane().equals("mebahel") && !n.group().isEmpty() ? n.group() : c.laneNames().getOrDefault(n.lane(), n.lane());
            signs.add(new Sign(text, p.x() - R - 10, p.y(), c.laneColors().getOrDefault(n.lane(), 0x6B5638)));
        }
        // bounds
        minX = minY = Double.MAX_VALUE;
        maxX = maxY = -Double.MAX_VALUE;
        for (Placed p : placed) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        for (Sign s : signs) {
            minX = Math.min(minX, s.x() - font.width(s.text()) - 8);
        }
        if (placed.isEmpty()) {
            minX = maxX = minY = maxY = 0;
        }
        decorate(c);
    }

    /** Little trees, hills and tufts of grass in the empty parts of the page, as a mapmaker would draw them. */
    private void decorate(Chapter c) {
        List<double[]> segs = new ArrayList<>();
        for (String[] e : c.edges()) {
            Placed a = placedById.get(e[0]), b = placedById.get(e[1]);
            if (a != null && b != null) {
                segs.add(new double[]{a.x(), a.y(), b.x(), b.y()});
            }
        }
        int step = 46;
        for (int gx = (int) Math.floor((minX - 200) / step); gx <= (int) Math.ceil((maxX + 420) / step); gx++) {
            for (int gy = (int) Math.floor((minY - 180) / step); gy <= (int) Math.ceil((maxY + 180) / step); gy++) {
                int h = gx * 73856093 ^ gy * 19349663 ^ chapter * 83492791;
                h = (h ^ (h >>> 13)) * 1274126177;
                float r = ((h >>> 8) & 0xFFFF) / 65535f;
                if (r > 0.26f) {
                    continue;
                }
                double x = gx * step + ((h & 0xF) - 8) * 2;
                double y = gy * step + (((h >>> 4) & 0xF) - 8) * 2;
                if (!clearOf(x, y, segs)) {
                    continue;
                }
                decos.add(new Deco(r < 0.15f ? 0 : r < 0.21f ? 1 : 2, x, y));
            }
        }
    }

    private boolean clearOf(double x, double y, List<double[]> segs) {
        for (Placed p : placed) {
            if (Math.abs(p.x() - x) < 60 && y > p.y() - 34 && y < p.y() + 46) {
                return false;
            }
        }
        for (Sign s : signs) {
            if (x > s.x() - font.width(s.text()) - 14 && x < s.x() + 10 && Math.abs(y - s.y()) < 16) {
                return false;
            }
        }
        for (double[] s : segs) {
            if (distToSeg(x, y, s[0], s[1], s[2], s[3]) < 22) {
                return false;
            }
        }
        return true;
    }

    private static double distToSeg(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len = dx * dx + dy * dy;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    // =================================================================== camera
    private int canvasRight() {
        return width - Math.round(panelW() * ease(panelAnim));
    }

    private int panelW() {
        return Math.max(170, Math.min(250, (int) (width * 0.36)));
    }

    private double ccx() {
        return canvasRight() / 2.0;
    }

    private double ccy() {
        return (TOP + height - FOOT) / 2.0;
    }

    private int sx(double x) {
        return (int) Math.round(ccx() + (x - camX) * zoom);
    }

    private int sy(double y) {
        return (int) Math.round(ccy() + (y - camY) * zoom);
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    private void showChapter(int ci, boolean snap) {
        if (ci < 0 || ci >= chapters.size()) {
            return;
        }
        chapter = ci;
        layoutDirty = true;
        relayout();
        Node now = nowNode();
        Placed focus = now == null ? null : placedById.get(now.id());
        if (focus == null) {
            for (Placed p : placed) {
                if (p.node().lane().equals("main") && (focus == null || p.x() > focus.x())) {
                    focus = p;
                }
            }
        }
        double viewW = (canvasRight() - 20) / zoom, viewH = (height - TOP - FOOT - 20) / zoom;
        tgtX = focus == null || maxX - minX < viewW * 0.85 ? (minX + maxX) / 2 : focus.x();
        tgtY = focus == null || maxY - minY < viewH * 0.85 ? (minY + maxY) / 2 : focus.y();
        if (snap) {
            camX = tgtX;
            camY = tgtY;
        }
    }

    private void focusOn(Node n) {
        if (n == null) {
            return;
        }
        if (n.chapter() != chapter) {
            showChapter(n.chapter(), true);
        }
        Placed p = placedById.get(n.id());
        if (p != null) {
            tgtX = p.x();
            tgtY = p.y();
        }
    }

    private void select(Node n) {
        if (n == null) {
            selected = null;
            return;
        }
        selected = n;
        shownNode = n;
        panelScroll = 0;
        focusOn(n);
    }

    private void clampCamera() {
        tgtX = Math.max(minX - 60, Math.min(maxX + 160, tgtX));
        tgtY = Math.max(minY - 80, Math.min(maxY + 80, tgtY));
    }

    // =================================================================== screen
    @Override
    protected void init() {
        open = this;
        MapArt.ensure();
        if (!statesKnown) {
            PacketDistributor.sendToServer(new Payloads.RequestMap(false));
        }
        if (pendingStates != null) {
            JsonObject p = pendingStates;
            pendingStates = null;
            applyStates(p);
        }
        layoutDirty = true;
        lastFrame = Util.getMillis();
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

    @Override
    public boolean isPauseScreen() {
        return false;
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

    private void tickFrame() {
        long nowMs = Util.getMillis();
        float dt = Math.min(0.1f, (nowMs - lastFrame) / 1000f);
        lastFrame = nowMs;
        time += dt;
        if (statesKnown && nowMs - lastRefresh > 3000) {
            lastRefresh = nowMs;
            PacketDistributor.sendToServer(new Payloads.RequestMap(false));
        }
        if (layoutDirty && statesKnown) {
            relayout();
            if (selected != null && !placedById.containsKey(selected.id()) && selected.chapter() == chapter) {
                selected = null;
            }
        }
        clampCamera();
        float k = 1 - (float) Math.exp(-dt * 9);
        if (!dragging) {
            camX += (tgtX - camX) * k;
            camY += (tgtY - camY) * k;
        }
        float want = selected != null ? 1 : 0;
        panelAnim += (want - panelAnim) * (1 - (float) Math.exp(-dt * 14));
        if (Math.abs(want - panelAnim) < 0.002f) {
            panelAnim = want;
        }
        if (panelAnim == 0) {
            shownNode = null;
        }
    }

    // =================================================================== drawing
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        tickFrame();
        hits.clear();
        hoveredSeal = null;
        g.fill(0, 0, width, height, LEATHER);
        if (showCodex) {
            drawCodex(g, mx, my);
        } else {
            drawJourney(g, mx, my);
            drawPanel(g, mx, my);
        }
        drawHeader(g, mx, my);
        drawFooter(g, mx, my);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        // tooltips go over everything, items included
        List<Component> tip = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.tip() != null && mx >= h.x1() && mx < h.x2() && my >= h.y1() && my < h.y2()) {
                tip = h.tip();
                break;
            }
        }
        if (tip != null && !tip.isEmpty()) {
            drawTip(g, tip, mx, my);
        }
    }

    private void hit(int x1, int y1, int x2, int y2, Runnable click, Component... tip) {
        hits.add(new Hit(x1, y1, x2, y2, click, tip.length == 0 ? null : List.of(tip)));
    }

    private static boolean over(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    // ------------------------------------------------------------------ the map
    private void drawJourney(GuiGraphics g, int mx, int my) {
        int cx0 = 0, cy0 = TOP, cx1 = canvasRight(), cy1 = height - FOOT;
        g.enableScissor(cx0, cy0, cx1, cy1);
        drawPaper(g, cx0, cy0, cx1, cy1);
        if (!statesKnown || chapter < 0) {
            g.drawCenteredString(font, "Unrolling the map...", (cx0 + cx1) / 2, (cy0 + cy1) / 2, INK_SOFT);
            g.disableScissor();
            return;
        }
        if (layoutDirty) {
            relayout();
        }
        Chapter c = chapters.get(chapter);
        drawGrid(g, cx0, cy0, cx1, cy1);
        for (Deco d : decos) {
            drawDeco(g, d);
        }
        // roads: the main road first, broad and worn, then every other path on top
        for (int pass = 0; pass < 2; pass++) {
            for (String[] e : c.edges()) {
                Placed a = placedById.get(e[0]), b = placedById.get(e[1]);
                if (a == null || b == null) {
                    continue;
                }
                boolean mainRoad = a.node().lane().equals("main") && b.node().lane().equals("main");
                if (pass == 0) {
                    if (mainRoad) {
                        road(g, a, b, Math.max(3f, 8f * zoom), 0x3A8A6438, 0);
                    }
                    continue;
                }
                boolean walked = done(a.node().id()) && reached(b.node().id());
                boolean ahead = done(a.node().id()) && !reached(b.node().id());
                int col = walked ? (mainRoad ? 0xE05A4128 : 0xC05A4128) : ahead ? 0xB06B5638 : 0x606B5638;
                int dash = walked ? 0 : ahead ? 6 : 3;
                road(g, a, b, Math.max(1f, (mainRoad ? 2f : 1.5f) * zoom), col, dash);
                if (e[2].equals("letter")) {
                    envelope(g, a, b);
                } else if (e[2].equals("spare") || e[2].equals("kill")) {
                    double[] p = bez(a, b, 0.22);
                    disc(g, sx(p[0]), sy(p[1]), Math.max(2, Math.round(2.5f * zoom)), e[2].equals("spare") ? MERCY : BLADE);
                }
            }
        }
        drawFrontier(g, c);
        // seals
        for (Placed p : placed) {
            int x = sx(p.x()), y = sy(p.y());
            if (x < cx0 - 80 || x > cx1 + 80 || y < cy0 - 60 || y > cy1 + 60) {
                continue;
            }
            int r = Math.max(7, Math.round(R * zoom));
            boolean hover = (mx - x) * (mx - x) + (my - y) * (my - y) <= (r + 3) * (r + 3) && my > cy0 && my < cy1 && mx < cx1 && !dragging;
            if (hover) {
                hoveredSeal = p;
            }
            drawSeal(g, p, x, y, r, hover);
        }
        for (Placed p : placed) {
            int x = sx(p.x()), y = sy(p.y());
            if (x < cx0 - 120 || x > cx1 + 120 || y < cy0 - 60 || y > cy1 + 60) {
                continue;
            }
            drawLabel(g, p, x, y, Math.max(7, Math.round(R * zoom)));
        }
        for (Sign s : signs) {
            drawSign(g, s);
        }
        if (hoveredSeal != null) {
            Node n = hoveredSeal.node();
            int x = sx(hoveredSeal.x()), y = sy(hoveredSeal.y());
            int r = Math.max(7, Math.round(R * zoom)) + 3;
            hit(x - r, y - r, x + r, y + r, () -> select(n), Component.literal(n.title()).withStyle(ChatFormatting.BOLD),
                    Component.literal(statusText(n)).withColor(statusColor(n) & 0xFFFFFF));
        }
        drawCompass(g, cx0 + 34, cy1 - 34);
        drawNowBanner(g, mx, my, cx0, cy0, cx1);
        drawOffscreenArrow(g, cx0, cy0, cx1, cy1);
        g.disableScissor();
        g.pose().pushPose();
        g.pose().translate(0, 0, 240);
        drawBurntEdges(g, cx0, cy0, cx1, cy1);
        g.pose().popPose();
    }

    private void drawPaper(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PAPER);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int size = Math.max(32, Math.round(MapArt.PAPER_SIZE * zoom));
        int ox = sx(0), oy = sy(0);
        int startX = ox - (int) Math.ceil((ox - x0) / (double) size) * size;
        int startY = oy - (int) Math.ceil((oy - y0) / (double) size) * size;
        for (int tx = startX; tx < x1; tx += size) {
            for (int ty = startY; ty < y1; ty += size) {
                g.blit(MapArt.PAPER, tx, ty, size, size, 0f, 0f, MapArt.PAPER_SIZE, MapArt.PAPER_SIZE, MapArt.PAPER_SIZE, MapArt.PAPER_SIZE);
            }
        }
        RenderSystem.disableBlend();
    }

    /** faint lines of latitude and longitude */
    private void drawGrid(GuiGraphics g, int x0, int y0, int x1, int y1) {
        double step = 200;
        double wx0 = camX + (x0 - ccx()) / zoom, wx1 = camX + (x1 - ccx()) / zoom;
        double wy0 = camY + (y0 - ccy()) / zoom, wy1 = camY + (y1 - ccy()) / zoom;
        for (double gx = Math.floor(wx0 / step) * step; gx <= wx1; gx += step) {
            int x = sx(gx);
            g.fill(x, y0, x + 1, y1, 0x146B5638);
        }
        for (double gy = Math.floor(wy0 / step) * step; gy <= wy1; gy += step) {
            int y = sy(gy);
            g.fill(x0, y, x1, y + 1, 0x146B5638);
        }
    }

    private void wfill(GuiGraphics g, double x1, double y1, double x2, double y2, int col) {
        int a = sx(x1), b = sy(y1), c = sx(x2), d = sy(y2);
        if (c <= a) {
            c = a + 1;
        }
        if (d <= b) {
            d = b + 1;
        }
        g.fill(a, b, c, d, col);
    }

    private void drawDeco(GuiGraphics g, Deco d) {
        int ink = 0x506B5638;
        double x = d.x(), y = d.y();
        switch (d.kind()) {
            case 0 -> { // a little stand of pines
                for (int t = 0; t < 2 + (int) Math.floorMod((long) x, 2L); t++) {
                    double tx = x + t * 7 - 4, ty = y + (t % 2) * 3;
                    for (int k = 0; k < 6; k++) {
                        wfill(g, tx - k * 0.7, ty + k * 1.3, tx + k * 0.7 + 1, ty + k * 1.3 + 1.3, ink);
                    }
                    wfill(g, tx, ty + 7.8, tx + 1, ty + 10, ink);
                }
            }
            case 1 -> { // hills
                for (int k = 0; k <= 12; k++) {
                    double a = Math.PI * k / 12;
                    wfill(g, x - 6 + Math.cos(a) * -6, y - Math.sin(a) * 6, x - 6 + Math.cos(a) * -6 + 1, y - Math.sin(a) * 6 + 1, ink);
                    wfill(g, x + 5 + Math.cos(a) * -4.5, y + 1 - Math.sin(a) * 4.5, x + 5 + Math.cos(a) * -4.5 + 1, y + 1 - Math.sin(a) * 4.5 + 1, ink);
                }
            }
            default -> { // a tuft of grass
                wfill(g, x, y, x + 1, y + 3, ink);
                wfill(g, x - 2, y + 1, x - 1, y + 3, ink);
                wfill(g, x + 2, y + 1, x + 3, y + 3, ink);
            }
        }
    }

    private double[] bez(Placed a, Placed b, double t) {
        double ax = a.x(), ay = a.y(), bx = b.x(), by = b.y();
        double k = Math.max(30, Math.abs(bx - ax) * 0.5);
        double c1x = ax + k, c1y = ay, c2x = bx - k, c2y = by;
        double u = 1 - t;
        double x = u * u * u * ax + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * bx;
        double y = u * u * u * ay + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * by;
        return new double[]{x, y};
    }

    /** A road between two seals: an S-curve, drawn solid, dashed (dash = run length) or dotted. */
    private void road(GuiGraphics g, Placed a, Placed b, float width, int color, int dash) {
        double len = Math.hypot(b.x() - a.x(), b.y() - a.y()) * zoom + 40;
        int steps = Math.max(8, (int) (len / 1.5));
        int w = Math.max(1, Math.round(width));
        int half = w / 2;
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            if (dash > 0 && (i / dash) % 2 == 1) {
                continue;
            }
            double[] p = bez(a, b, i / (double) steps);
            int x = sx(p[0]), y = sy(p[1]);
            if (x == lastX && y == lastY) {
                continue;
            }
            lastX = x;
            lastY = y;
            g.fill(x - half, y - half, x - half + w, y - half + w, color);
        }
    }

    private void envelope(GuiGraphics g, Placed a, Placed b) {
        double[] p = bez(a, b, 0.5);
        int x = sx(p[0]), y = sy(p[1]);
        int w = Math.max(6, Math.round(8 * zoom)), h = Math.max(4, Math.round(6 * zoom));
        g.fill(x - w / 2 - 1, y - h / 2 - 1, x + w / 2 + 1, y + h / 2 + 1, INK_SOFT);
        g.fill(x - w / 2, y - h / 2, x + w / 2, y + h / 2, PAPER_LIGHT);
        for (int i = 0; i < w / 2; i++) {
            int yy = y - h / 2 + Math.min(h - 1, i * h / w * 2 / 2);
            g.fill(x - w / 2 + i, yy, x - w / 2 + i + 1, yy + 1, INK_SOFT);
            g.fill(x + w / 2 - i - 1, yy, x + w / 2 - i, yy + 1, INK_SOFT);
        }
        g.fill(x - 1, y + h / 2 - 2, x + 1, y + h / 2, RED_INK); // the wax dot
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int t, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int wo = (int) Math.sqrt(r * r - dy * dy);
            int ri = r - t;
            int wi = Math.abs(dy) <= ri ? (int) Math.sqrt(ri * ri - dy * dy) : -1;
            if (wi < 0) {
                g.fill(cx - wo, cy + dy, cx + wo + 1, cy + dy + 1, color);
            } else {
                g.fill(cx - wo, cy + dy, cx - wi, cy + dy + 1, color);
                g.fill(cx + wi + 1, cy + dy, cx + wo + 1, cy + dy + 1, color);
            }
        }
    }

    private void drawSeal(GuiGraphics g, Placed p, int x, int y, int r, boolean hover) {
        Node n = p.node();
        String s = state(n.id());
        boolean active = s.equals("active");
        if (active) {
            double pulse = (Math.sin(time * 3.2) + 1) / 2;
            ring(g, x, y, r + 5 + (int) Math.round(pulse * 3), 2, ((int) (0x50 + pulse * 0x50) << 24) | 0xE0A93A);
        }
        if (n == selected) {
            ring(g, x, y, r + 4, 2, 0xE0FFF4D0);
        }
        int d = hover ? Math.round(r * 2.3f) : r * 2 + 1;
        disc(g, x + 1, y + 2, d / 2, 0x38000000); // shadow
        ResourceLocation tex = s.startsWith("done") ? MapArt.WAX : active ? MapArt.GOLD : s.equals("failed") ? MapArt.ASH : MapArt.TOKEN;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x - d / 2, y - d / 2, d, d, 0f, 0f, MapArt.SEAL, MapArt.SEAL, MapArt.SEAL, MapArt.SEAL);
        RenderSystem.disableBlend();
        // the quest's mark pressed into the seal
        float iconSize = d * 0.52f;
        float sc = iconSize / 16f;
        g.pose().pushPose();
        g.pose().translate(x - iconSize / 2f, y - iconSize / 2f, 0);
        g.pose().scale(sc, sc, 1);
        g.renderFakeItem(n.icon(), 0, 0);
        g.pose().popPose();
        // decisions show what you chose
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (s.equals("done_spare")) {
            disc(g, x + r - 1, y - r + 1, Math.max(2, r / 4), MERCY);
        } else if (s.equals("done_kill")) {
            disc(g, x + r - 1, y - r + 1, Math.max(2, r / 4), BLADE);
        }
        if (active) {
            int fx = x + r - 1, fy = y - r - 11;
            g.fill(fx, fy, fx + 1, fy + 13, INK);
            g.fill(fx + 1, fy, fx + 9, fy + 3, 0xFFB02020);
            g.fill(fx + 1, fy + 3, fx + 7, fy + 5, 0xFF8E1818);
        }
        g.pose().popPose();
    }

    private void drawLabel(GuiGraphics g, Placed p, int x, int y, int r) {
        if (zoom < 0.62f) {
            return;
        }
        Node n = p.node();
        String s = state(n.id());
        int maxW = (int) ((COL_W - 18) * zoom);
        List<FormattedCharSequence> lines = font.split(Component.literal(n.title()), Math.max(40, maxW));
        int count = zoom >= 0.85f ? Math.min(2, lines.size()) : 1;
        int ty = y + r + 4;
        int color = s.equals("active") ? 0xFF7A2E00 : s.startsWith("done") ? INK : INK_SOFT;
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            int w = font.width(line);
            if (i == count - 1 && lines.size() > count) {
                String all = n.title();
                String cut = font.plainSubstrByWidth(all, Math.max(30, maxW - font.width("...")));
                line = Component.literal(lines.size() > 1 && count == 2 ? font.plainSubstrByWidth(remainder(lines, i), Math.max(30, maxW - 12)) + "..." : cut + "...").getVisualOrderText();
                w = font.width(line);
            }
            g.fill(x - w / 2 - 2, ty - 1, x + w / 2 + 2, ty + 9, 0x9CEADBB8);
            g.drawString(font, line, x - w / 2, ty, color, false);
            ty += 10;
        }
    }

    private String remainder(List<FormattedCharSequence> lines, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.size(); i++) {
            lines.get(i).accept((idx, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private void drawSign(GuiGraphics g, Sign s) {
        if (zoom < 0.62f) {
            return;
        }
        int x = sx(s.x()), y = sy(s.y());
        String text = s.text();
        int w = font.width(text);
        int col = 0xFF000000 | darken(s.color(), 0.55);
        g.fill(x - w - 8, y - 6, x + 2, y + 6, 0xB0F1E4C4);
        g.fill(x - w - 8, y + 6, x + 2, y + 7, col);
        g.fill(x + 2, y - 6, x + 3, y + 7, col);
        // a pointing tip
        for (int i = 0; i < 4; i++) {
            g.fill(x + 3, y - 3 + i, x + 3 + 4 - i, y - 2 + i, col);
            g.fill(x + 3, y + 3 - i, x + 3 + 4 - i, y + 4 - i, col);
        }
        g.drawString(font, Component.literal(text).withStyle(ChatFormatting.ITALIC), x - w - 4, y - 4, col, false);
    }

    private static int darken(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), gg = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (gg << 8) | b;
    }

    /** The road you haven't walked yet: it trails off into fog. */
    private void drawFrontier(GuiGraphics g, Chapter c) {
        Node now = nowNode();
        boolean here = now != null && now.chapter() == chapter;
        boolean unfinished = false;
        for (Node n : c.nodes()) {
            if (!visible(n) && n.lane().equals("main")) {
                unfinished = true;
                break;
            }
        }
        if (!here && !unfinished) {
            return;
        }
        Placed last = null;
        for (Placed p : placed) {
            if (p.node().lane().equals("main") && (last == null || p.x() > last.x())) {
                last = p;
            }
        }
        if (last == null) {
            return;
        }
        for (int i = 1; i <= 22; i++) {
            double t = i / 22.0;
            double x = last.x() + t * 150, y = last.y() + Math.sin(t * 3.4) * 14;
            int alpha = (int) (0xB0 * (1 - t));
            int sz = Math.max(1, Math.round(2 * zoom));
            int px = sx(x), py = sy(y);
            if (i % 2 == 0) {
                g.fill(px, py, px + sz, py + sz, (alpha << 24) | 0x5A4128);
            }
        }
        // fog
        int fx = sx(maxX + 70);
        int span = Math.max(60, Math.round(240 * zoom));
        int top = TOP, bottom = height - FOOT;
        for (int i = 0; i < span; i += 3) {
            int a = (int) (0xF0 * Math.min(1, i / (double) span * 1.15));
            g.fill(fx + i, top, fx + i + 3, bottom, (a << 24) | 0xE9D8B4);
        }
        g.fill(fx + span, top, canvasRight(), bottom, 0xF0E9D8B4);
        int lx = sx(last.x() + 150), ly = sy(last.y());
        g.drawString(font, Component.literal("Uncharted").withStyle(ChatFormatting.ITALIC), lx - 20, ly - 4, 0x906B5638, false);
    }

    private void drawCompass(GuiGraphics g, int cx, int cy) {
        int col = 0x606B5638, red = 0x908E1B1B;
        ring(g, cx, cy, 15, 1, col);
        ring(g, cx, cy, 11, 1, 0x356B5638);
        for (int i = -18; i <= 18; i++) {
            int w = Math.max(0, 3 - Math.abs(i) * 3 / 18);
            if (i < 0) {
                g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, red);
            } else {
                g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, col);
            }
            g.fill(cx + i, cy - w, cx + i + 1, cy + w + 1, col);
        }
        g.drawString(font, "N", cx - 2, cy - 29, 0xA08E1B1B, false);
    }

    private void drawBurntEdges(GuiGraphics g, int x0, int y0, int x1, int y1) {
        // a soft darkening toward the edges, then a ragged scorched rim
        int e = 18;
        g.fillGradient(x0, y0, x1, y0 + e, 0x60301E0C, 0x00301E0C);
        g.fillGradient(x0, y1 - e, x1, y1, 0x00301E0C, 0x60301E0C);
        for (int i = 0; i < e; i += 2) {
            int a = (int) (0x60 * (1 - i / (double) e));
            g.fill(x0 + i, y0, x0 + i + 2, y1, (a << 24) | 0x301E0C);
            g.fill(x1 - i - 2, y0, x1 - i, y1, (a << 24) | 0x301E0C);
        }
        for (int x = x0; x < x1; x += 2) {
            int d1 = 2 + (int) (3 * (Math.sin(x * 0.37) * 0.5 + Math.sin(x * 0.11 + 1) * 0.5 + 1));
            int d2 = 2 + (int) (3 * (Math.sin(x * 0.29 + 3) * 0.5 + Math.sin(x * 0.07) * 0.5 + 1));
            g.fill(x, y0, x + 2, y0 + d1, 0xE01A0F06);
            g.fill(x, y1 - d2, x + 2, y1, 0xE01A0F06);
        }
        for (int y = y0; y < y1; y += 2) {
            int d1 = 2 + (int) (3 * (Math.sin(y * 0.31 + 2) * 0.5 + Math.sin(y * 0.13) * 0.5 + 1));
            int d2 = 2 + (int) (3 * (Math.sin(y * 0.23 + 5) * 0.5 + Math.sin(y * 0.09 + 2) * 0.5 + 1));
            g.fill(x0, y, x0 + d1, y + 2, 0xE01A0F06);
            g.fill(x1 - d2, y, x1, y + 2, 0xE01A0F06);
        }
    }

    private void drawNowBanner(GuiGraphics g, int mx, int my, int cx0, int cy0, int cx1) {
        Node now = nowNode();
        if (now == null || selected != null) {
            return;
        }
        String goal = currentGoal(now);
        int maxW = Math.min(280, cx1 - cx0 - 40);
        String title = font.plainSubstrByWidth("Now: " + now.title(), maxW - 22);
        String sub = goal.isEmpty() ? "" : font.plainSubstrByWidth(goal, maxW - 22);
        int w = Math.max(font.width(title), font.width(sub)) + 30;
        int x = cx0 + 12, y = cy0 + 10, h = sub.isEmpty() ? 18 : 28;
        boolean hover = over(mx, my, x, y, x + w, y + h);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x40000000);
        g.fill(x, y, x + w, y + h, hover ? 0xFFFBF1DA : PAPER_LIGHT);
        g.fill(x, y, x + 3, y + h, GILT);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFB89A62);
        disc(g, x + 12, y + 9, 4, 0xFFD9A030);
        g.drawString(font, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 21, y + 5, 0xFF7A2E00, false);
        if (!sub.isEmpty()) {
            g.drawString(font, sub, x + 21, y + 16, INK_SOFT, false);
        }
        g.pose().popPose();
        hit(x, y, x + w, y + h, () -> select(now), Component.literal("Show where you are"));
    }

    private void drawOffscreenArrow(GuiGraphics g, int cx0, int cy0, int cx1, int cy1) {
        Node now = nowNode();
        if (now == null || now.chapter() != chapter) {
            return;
        }
        Placed p = placedById.get(now.id());
        if (p == null) {
            return;
        }
        int x = sx(p.x()), y = sy(p.y());
        if (x > cx0 + 10 && x < cx1 - 10 && y > cy0 + 10 && y < cy1 - 10) {
            return;
        }
        double cx = (cx0 + cx1) / 2.0, cy = (cy0 + cy1) / 2.0;
        double dx = x - cx, dy = y - cy;
        double kx = (cx1 - cx0) / 2.0 - 26, ky = (cy1 - cy0) / 2.0 - 26;
        double s = Math.min(kx / Math.max(1e-6, Math.abs(dx)), ky / Math.max(1e-6, Math.abs(dy)));
        int ax = (int) (cx + dx * s), ay = (int) (cy + dy * s);
        float ang = (float) Math.atan2(dy, dx);
        g.pose().pushPose();
        g.pose().translate(ax, ay, 260);
        disc(g, 0, 0, 11, 0xE0F4E9CF);
        ring(g, 0, 0, 11, 1, GILT);
        g.pose().mulPose(Axis.ZP.rotation(ang));
        for (int i = 0; i < 7; i++) {
            g.fill(-4 + i, -(6 - i) / 2 - 1, -3 + i, (6 - i) / 2 + 1, 0xFFB07A1A);
        }
        g.pose().popPose();
        hit(ax - 11, ay - 11, ax + 11, ay + 11, () -> focusOn(now), Component.literal("Where you are: " + now.title()));
    }

    // ------------------------------------------------------------------ header and footer
    private void leatherBand(GuiGraphics g, int y0, int y1, boolean goldBelow) {
        g.fillGradient(0, y0, width, y1, LEATHER_HI, LEATHER);
        for (int x = 4; x < width - 4; x += 5) {
            g.fill(x, y0 + 3, x + 3, y0 + 4, 0x40D8B070);
            g.fill(x, y1 - 4, x + 3, y1 - 3, 0x40D8B070);
        }
        if (goldBelow) {
            g.fill(0, y1 - 1, width, y1, 0xFF8A6A32);
        } else {
            g.fill(0, y0, width, y0 + 1, 0xFF8A6A32);
        }
    }

    private void drawHeader(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        leatherBand(g, 0, TOP, true);
        if (showCodex) {
            String t = "The Codex";
            g.pose().pushPose();
            g.pose().translate(width / 2f - font.width(t), 10, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, t, 0, 0, GILT_BRIGHT, true);
            g.pose().popPose();
            String sub = "What you've learned about the people, the places and the cult";
            g.drawString(font, sub, width / 2 - font.width(sub) / 2, 31, 0xFFB89A62, false);
            g.pose().popPose();
            return;
        }
        if (chapter >= 0 && chapter < chapters.size()) {
            Chapter c = chapters.get(chapter);
            String small = c.name().toUpperCase();
            g.drawString(font, small, width / 2 - font.width(small) / 2, 5, GILT, false);
            String big = c.subtitle();
            if (font.width(big) * 2 < width - 160) {
                g.pose().pushPose();
                g.pose().translate(width / 2f - font.width(big), 15, 0);
                g.pose().scale(2, 2, 1);
                g.drawString(font, big, 0, 0, GILT_BRIGHT, true);
                g.pose().popPose();
            } else {
                g.drawString(font, Component.literal(big).withStyle(ChatFormatting.BOLD), width / 2 - font.width(big) / 2 - 3, 19, GILT_BRIGHT, true);
            }
            // turn the page
            int prev = neighbour(-1), next = neighbour(1);
            arrow(g, mx, my, 14, 16, -1, prev);
            arrow(g, mx, my, width - 26, 16, 1, next);
            // one pip per act: gold where you've been, a ring for this page, dark for what's still ahead
            int n = chapters.size();
            int px = width / 2 - (n - 1) * 6;
            for (int i = 0; i < n; i++) {
                int x = px + i * 12, y = TOP - 6;
                boolean reachedCh = statesKnown && chapterReached(i);
                if (i == chapter) {
                    diamond(g, x, y, 3, GILT_BRIGHT);
                } else {
                    diamond(g, x, y, 2, reachedCh ? GILT : 0xFF4A3A28);
                }
                final int ci = i;
                hit(x - 5, y - 5, x + 6, y + 5, reachedCh ? () -> {
                    selected = null;
                    showChapter(ci, true);
                } : null, Component.literal(reachedCh ? chapters.get(i).name() + ": " + chapters.get(i).subtitle() : "Not reached yet"));
            }
        }
        // who you are, top right
        String tier = repTier();
        g.drawString(font, tier, width - 8 - font.width(tier), 5, 0xFF000000 | tierColor(), false);
        g.pose().popPose();
    }

    private String repTier() {
        if (rep <= -60) {
            return "Wicked";
        }
        if (rep <= -20) {
            return "Fallen";
        }
        if (rep < 20) {
            return "Wavering";
        }
        if (rep < 60) {
            return "Good";
        }
        return "Saint";
    }

    private int tierColor() {
        return rep <= -60 ? 0xC03030 : rep <= -20 ? 0xD06A3A : rep < 20 ? 0xC8B08A : rep < 60 ? 0x7FC07F : 0xF2D49A;
    }

    private int neighbour(int dir) {
        for (int i = chapter + dir; i >= 0 && i < chapters.size(); i += dir) {
            if (chapterReached(i)) {
                return i;
            }
        }
        return -1;
    }

    private void arrow(GuiGraphics g, int mx, int my, int x, int y, int dir, int target) {
        boolean on = target >= 0;
        boolean hover = on && over(mx, my, x - 6, y - 8, x + 18, y + 16);
        int col = !on ? 0xFF4A3A28 : hover ? GILT_BRIGHT : GILT;
        for (int i = 0; i < 7; i++) {
            int w = i < 4 ? i : 6 - i;
            int xx = dir < 0 ? x + 6 - w * 2 : x + w * 2;
            g.fill(xx, y + i, xx + 3, y + i + 1, col);
        }
        if (on) {
            hit(x - 6, y - 8, x + 18, y + 16, () -> {
                selected = null;
                showChapter(target, true);
            }, Component.literal((dir < 0 ? "Back to " : "On to ") + chapters.get(target).name()));
        }
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int i = -r; i <= r; i++) {
            int w = r - Math.abs(i);
            g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, col);
        }
    }

    private void drawFooter(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        int y0 = height - FOOT;
        leatherBand(g, y0, height, false);
        int x = 8, y = y0 + 8;
        if (!showCodex) {
            x = legend(g, x, y, MapArt.WAX, "Done");
            x = legend(g, x, y, MapArt.GOLD, "You are here");
            x = legend(g, x, y, MapArt.TOKEN, "Ready to start");
        }
        // buttons, right to left
        int bx = width - 8;
        bx = button(g, mx, my, bx, y0 + 4, "Close", this::onClose, false);
        bx = button(g, mx, my, bx, y0 + 4, "Quest list", this::openClassic, false);
        boolean unread = false;
        for (Lore l : unlockedLore()) {
            if (!seenLore.contains(l.title())) {
                unread = true;
                break;
            }
        }
        bx = button(g, mx, my, bx, y0 + 4, showCodex ? "Map" : "Codex", () -> {
            showCodex = !showCodex;
            codexScroll = 0;
        }, unread && !showCodex);
        String help = showCodex ? "" : "Drag to move  ·  Scroll to zoom  ·  ← → turn the page  ·  Space: where you are";
        if (!help.isEmpty() && x + 16 + font.width(help) < bx - 12) {
            g.drawString(font, help, (x + bx) / 2 - font.width(help) / 2, y, 0xFF8A7656, false);
        }
        g.pose().popPose();
    }

    private int legend(GuiGraphics g, int x, int y, ResourceLocation tex, String label) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y - 1, 10, 10, 0f, 0f, MapArt.SEAL, MapArt.SEAL, MapArt.SEAL, MapArt.SEAL);
        RenderSystem.disableBlend();
        g.drawString(font, label, x + 13, y, 0xFFD8C298, false);
        return x + 13 + font.width(label) + 12;
    }

    private int button(GuiGraphics g, int mx, int my, int right, int y, String label, Runnable action, boolean dot) {
        int w = font.width(label) + 14, h = 16;
        int x = right - w;
        boolean hover = over(mx, my, x, y, right, y + h);
        g.fill(x, y, right, y + h, hover ? 0xFF6B4A22 : 0xFF4A3218);
        g.fill(x, y, right, y + 1, hover ? GILT_BRIGHT : 0xFF8A6A32);
        g.fill(x, y + h - 1, right, y + h, 0xFF1A0F06);
        g.drawString(font, label, x + 7, y + 4, hover ? 0xFFFFF0C8 : 0xFFE8D2A0, false);
        if (dot) {
            disc(g, right - 3, y + 2, 3, 0xFFD03A2A);
        }
        hit(x, y, right, y + h, action);
        return x - 6;
    }

    // ------------------------------------------------------------------ the journal page
    private String statusText(Node n) {
        return switch (state(n.id())) {
            case "done" -> "Done";
            case "done_spare" -> "Done: you showed mercy";
            case "done_kill" -> "Done: you chose the blade";
            case "active" -> "You're on this now";
            case "open" -> "Ready to start";
            case "failed" -> "Failed: it will start again";
            default -> "Not reached yet";
        };
    }

    private int statusColor(Node n) {
        String s = state(n.id());
        return s.equals("done_spare") ? 0xFF2E6B2E : s.equals("done_kill") ? 0xFF6A1010 : s.startsWith("done") ? 0xFF2E5A2E
                : s.equals("active") ? 0xFF8A4A00 : s.equals("failed") ? 0xFF5A5A5A : 0xFF6B5638;
    }

    private void drawPanel(GuiGraphics g, int mx, int my) {
        Node n = shownNode;
        if (n == null || panelAnim <= 0.01f) {
            return;
        }
        int pw = panelW();
        int x0 = canvasRight(), y0 = TOP, y1 = height - FOOT;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (int i = 0; i < 8; i++) {
            g.fill(x0 - i - 1, y0, x0 - i, y1, ((8 - i) * 0x0A) << 24);
        }
        g.fill(x0, y0, x0 + pw, y1, PAPER_LIGHT);
        g.fill(x0, y0, x0 + 2, y1, 0xFF8A6A32);
        Chapter c = chapters.get(n.chapter());
        int laneCol = 0xFF000000 | c.laneColors().getOrDefault(n.lane(), 0x6B5638);
        g.fill(x0 + 2, y0, x0 + pw, y0 + 3, laneCol);
        int ix = x0 + 14, iw = pw - 28;
        // close
        int cxb = x0 + pw - 16, cyb = y0 + 8;
        boolean hc = over(mx, my, cxb - 2, cyb - 2, cxb + 10, cyb + 10);
        g.drawString(font, "×", cxb + 1, cyb, hc ? RED_INK : INK_SOFT, false);
        hit(cxb - 2, cyb - 2, cxb + 10, cyb + 10, () -> select(null), Component.literal("Close"));

        g.enableScissor(x0 + 2, y0 + 3, x0 + pw, y1);
        int y = y0 + 12 - panelScroll;
        int top = y;
        String laneName = n.lane().equals("mebahel") && !n.group().isEmpty() ? n.group() : c.laneNames().getOrDefault(n.lane(), "");
        g.drawString(font, (c.name() + "  ·  " + laneName).toUpperCase(), ix, y, 0xFF000000 | darken(laneCol, 0.6), false);
        y += 13;
        for (FormattedCharSequence line : font.split(Component.literal(n.title()).withStyle(ChatFormatting.BOLD), iw - 10)) {
            g.drawString(font, line, ix, y, INK, false);
            y += 10;
        }
        y += 3;
        String st = statusText(n);
        int sw = font.width(st) + 10;
        int sc = statusColor(n);
        g.fill(ix, y, ix + sw, y + 12, (sc & 0xFFFFFF) | 0x26000000);
        g.fill(ix, y, ix + 2, y + 12, sc);
        g.drawString(font, st, ix + 6, y + 2, sc, false);
        y += 18;
        y = divider(g, ix, iw, y);
        for (FormattedCharSequence line : font.split(Component.literal(n.summary()), iw)) {
            g.drawString(font, line, ix, y, 0xFF3B2A1A, false);
            y += 10;
        }
        // objectives
        if (!n.goals().isEmpty()) {
            y += 6;
            g.drawString(font, "OBJECTIVES", ix, y, 0xFF8A6A32, false);
            y += 12;
            String s = state(n.id());
            int stage = stages.getOrDefault(n.id(), 0);
            for (int i = 0; i < n.goals().size(); i++) {
                int gs = i < n.stages().size() ? n.stages().get(i) : 0;
                boolean gDone = s.startsWith("done") || (s.equals("active") && gs < stage);
                boolean gNow = s.equals("active") && gs == stage;
                String mark = gDone ? "✔" : gNow ? "▶" : "•";
                int mc = gDone ? 0xFF2E6B2E : gNow ? 0xFFB06A10 : 0xFF9A8662;
                int tc = gDone ? 0xFF8A7A5A : gNow ? INK : 0xFF6B5638;
                g.drawString(font, mark, ix, y, mc, false);
                Component text = gNow ? Component.literal(n.goals().get(i)).withStyle(ChatFormatting.BOLD) : Component.literal(n.goals().get(i));
                for (FormattedCharSequence line : font.split(text, iw - 12)) {
                    g.drawString(font, line, ix + 11, y, tc, false);
                    y += 10;
                }
                y += 2;
            }
        }
        // where it came from and where it leads (only what you've seen)
        y = links(g, mx, my, ix, iw, y, "CAME FROM", preds.getOrDefault(n.id(), Set.of()), false);
        y = links(g, mx, my, ix, iw, y, "LEADS TO", succs.getOrDefault(n.id(), Set.of()), true);
        panelContentH = y - top + 12;
        g.disableScissor();
        // scrollbar
        int viewH = y1 - y0 - 6;
        if (panelContentH > viewH) {
            int barH = Math.max(16, viewH * viewH / panelContentH);
            int barY = y0 + 3 + (viewH - barH) * panelScroll / Math.max(1, panelContentH - viewH);
            g.fill(x0 + pw - 4, barY, x0 + pw - 2, barY + barH, 0x806B5638);
        }
        g.pose().popPose();
    }

    private int divider(GuiGraphics g, int ix, int iw, int y) {
        int mid = ix + iw / 2;
        g.fill(ix, y + 3, mid - 6, y + 4, 0x606B5638);
        g.fill(mid + 7, y + 3, ix + iw, y + 4, 0x606B5638);
        diamond(g, mid, y + 3, 2, 0xFF8A6A32);
        return y + 12;
    }

    private int links(GuiGraphics g, int mx, int my, int ix, int iw, int y, String heading, Set<String> ids, boolean showHidden) {
        List<Node> known = new ArrayList<>();
        int hidden = 0;
        for (String id : ids) {
            Node m = nodes.get(id);
            if (m == null) {
                continue;
            }
            if (visible(m)) {
                known.add(m);
            } else {
                hidden++;
            }
        }
        if (known.isEmpty() && (hidden == 0 || !showHidden)) {
            return y;
        }
        y += 6;
        g.drawString(font, heading, ix, y, 0xFF8A6A32, false);
        y += 12;
        for (Node m : known) {
            String t = font.plainSubstrByWidth(m.title(), iw - 14);
            boolean hover = over(mx, my, ix, y - 1, ix + iw, y + 10);
            disc(g, ix + 3, y + 3, 2, statusColor(m));
            g.drawString(font, hover ? Component.literal(t).withStyle(ChatFormatting.UNDERLINE) : Component.literal(t), ix + 10, y, hover ? RED_INK : 0xFF5A4128, false);
            hit(ix, y - 1, ix + iw, y + 10, () -> select(m), Component.literal(m.title()).withStyle(ChatFormatting.BOLD),
                    Component.literal(statusText(m)).withColor(statusColor(m) & 0xFFFFFF));
            y += 11;
        }
        if (showHidden && hidden > 0) {
            g.drawString(font, Component.literal(known.isEmpty() ? "Somewhere you haven't found yet" : "...and somewhere you haven't found yet")
                    .withStyle(ChatFormatting.ITALIC), ix + 10, y, 0xFF9A8662, false);
            y += 11;
        }
        return y;
    }

    private void drawTip(GuiGraphics g, List<Component> lines, int mx, int my) {
        int w = 0;
        for (Component c : lines) {
            w = Math.max(w, font.width(c));
        }
        int h = lines.size() * 10 + 6;
        int x = mx + 10, y = my - h - 4;
        if (x + w + 10 > width) {
            x = mx - w - 14;
        }
        if (y < 2) {
            y = my + 14;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x - 1, y - 1, x + w + 9, y + h + 1, 0xFF5A4128);
        g.fill(x, y, x + w + 8, y + h, 0xFFF8EED6);
        int ly = y + 4;
        for (Component c : lines) {
            g.drawString(font, c, x + 4, ly, INK, false);
            ly += 10;
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ the codex
    private List<Lore> unlockedLore() {
        List<Lore> out = new ArrayList<>();
        for (String cat : new String[]{"People", "Places", "The Cult"}) {
            for (Lore l : codex) {
                if (l.category().equals(cat) && reached(l.unlock())) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    private void drawCodex(GuiGraphics g, int mx, int my) {
        int top = TOP + 10, bottom = height - FOOT - 10;
        int bw = Math.min(width - 30, 520), bh = bottom - top;
        int bx = (width - bw) / 2;
        // the cover, then two pages
        g.fill(bx - 6, top - 4, bx + bw + 6, bottom + 4, 0xFF4A2A16);
        g.fill(bx - 5, top - 3, bx + bw + 5, top - 2, 0xFF8A6A32);
        g.fill(bx - 5, bottom + 2, bx + bw + 5, bottom + 3, 0xFF8A6A32);
        int mid = bx + bw / 2;
        g.fill(bx, top, bx + bw, bottom, PAPER_LIGHT);
        for (int i = 0; i < 10; i++) {
            int a = (int) (0x38 * (i / 10.0));
            g.fill(mid - 10 + i, top, mid - 9 + i, bottom, a << 24);
            g.fill(mid + 9 - i, top, mid + 10 - i, bottom, a << 24);
        }
        g.fill(mid, top, mid + 1, bottom, 0x60301E0C);

        List<Lore> known = unlockedLore();
        if (codexPick < 0 || codexPick >= known.size()) {
            codexPick = 0;
            for (int i = 0; i < known.size(); i++) {
                if (!seenLore.contains(known.get(i).title())) {
                    codexPick = i;
                    break;
                }
            }
        }
        // left page: the index
        int lx = bx + 14, lw = bw / 2 - 28;
        g.enableScissor(bx, top + 4, mid - 6, bottom - 4);
        int y = top + 12 - codexScroll;
        String cat = "";
        for (int i = 0; i < known.size(); i++) {
            Lore l = known.get(i);
            if (!l.category().equals(cat)) {
                cat = l.category();
                int have = 0, all = 0;
                for (Lore o : codex) {
                    if (o.category().equals(cat)) {
                        all++;
                        if (reached(o.unlock())) {
                            have++;
                        }
                    }
                }
                if (i > 0) {
                    y += 6;
                }
                g.drawString(font, cat.toUpperCase(), lx, y, RED_INK, false);
                String count = have + " of " + all;
                g.drawString(font, count, lx + lw - font.width(count), y, 0xFF9A8662, false);
                g.fill(lx, y + 10, lx + lw, y + 11, 0x406B5638);
                y += 15;
            }
            boolean sel = i == codexPick;
            boolean hover = over(mx, my, lx - 4, y - 2, lx + lw, y + 10) && my > top && my < bottom;
            if (sel || hover) {
                g.fill(lx - 4, y - 2, lx + lw, y + 10, sel ? 0x40C9A55A : 0x206B5638);
            }
            if (sel) {
                g.fill(lx - 4, y - 2, lx - 2, y + 10, RED_INK);
            }
            g.drawString(font, font.plainSubstrByWidth(l.title(), lw - 16), lx + 2, y, sel ? INK : 0xFF5A4128, false);
            if (!seenLore.contains(l.title())) {
                disc(g, lx + lw - 5, y + 3, 2, 0xFFD03A2A);
            }
            final int ix = i;
            if (my > top && my < bottom) {
                hit(lx - 4, y - 2, lx + lw, y + 10, () -> codexPick = ix);
            }
            y += 13;
        }
        int locked = codex.size() - known.size();
        if (locked > 0) {
            y += 8;
            g.drawString(font, Component.literal(locked + " more to discover").withStyle(ChatFormatting.ITALIC), lx, y, 0xFF9A8662, false);
            y += 10;
        }
        codexContentH = y + codexScroll - top;
        g.disableScissor();
        // right page: the entry
        int rx = mid + 18, rw = bw / 2 - 32;
        if (known.isEmpty()) {
            g.drawString(font, Component.literal("Nothing yet. Go and find out.").withStyle(ChatFormatting.ITALIC), rx, top + 20, INK_SOFT, false);
            return;
        }
        Lore l = known.get(codexPick);
        markSeen(l.title());
        int ry = top + 14;
        g.drawString(font, l.category().toUpperCase(), rx, ry, RED_INK, false);
        ry += 14;
        if (font.width(l.title()) * 2 <= rw) {
            g.pose().pushPose();
            g.pose().translate(rx, ry, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, l.title(), 0, 0, INK, false);
            g.pose().popPose();
            ry += 22;
        } else {
            for (FormattedCharSequence line : font.split(Component.literal(l.title()).withStyle(ChatFormatting.BOLD), rw)) {
                g.drawString(font, line, rx, ry, INK, false);
                ry += 11;
            }
            ry += 3;
        }
        Node from = nodes.get(l.unlock());
        if (from != null) {
            g.drawString(font, Component.literal("from " + from.title()).withStyle(ChatFormatting.ITALIC), rx, ry, 0xFF9A8662, false);
            ry += 12;
        }
        ry = divider(g, rx, rw, ry);
        ry += 2;
        boolean first = true;
        for (FormattedCharSequence line : font.split(Component.literal(l.text()), rw)) {
            g.drawString(font, line, rx, ry, 0xFF3B2A1A, false);
            ry += 11;
            first = false;
        }
        if (!first) {
            ry += 6;
            diamond(g, rx + rw / 2, ry, 2, 0xFF8A6A32);
        }
    }

    private int codexContentH;

    // =================================================================== input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.click() != null && over((int) mx, (int) my, h.x1(), h.y1(), h.x2(), h.y2())) {
                    h.click().run();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.6f));
                    return true;
                }
            }
        }
        if (showCodex) {
            return super.mouseClicked(mx, my, button);
        }
        if (my > TOP && my < height - FOOT && mx < canvasRight() && (button == 0 || button == 1)) {
            dragging = true;
            dragDist = 0;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            camX -= dx / zoom;
            camY -= dy / zoom;
            tgtX = camX;
            tgtY = camY;
            dragDist += Math.abs(dx) + Math.abs(dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            if (dragDist < 4 && button == 0) {
                if (hoveredSeal != null) {
                    select(hoveredSeal.node());
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.6f));
                } else {
                    select(null);
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (showCodex) {
            int view = height - FOOT - TOP - 28;
            codexScroll = (int) Math.max(0, Math.min(Math.max(0, codexContentH - view), codexScroll - scrollY * 20));
            return true;
        }
        if (selected != null && mx >= canvasRight()) {
            int view = height - FOOT - TOP - 6;
            panelScroll = (int) Math.max(0, Math.min(Math.max(0, panelContentH - view), panelScroll - scrollY * 20));
            return true;
        }
        if (hasShiftDown()) {
            tgtX -= (scrollY + scrollX) * 60 / zoom;
            return true;
        }
        double wx = camX + (mx - ccx()) / zoom, wy = camY + (my - ccy()) / zoom;
        float nz = (float) Math.max(0.5, Math.min(2.0, zoom * (scrollY > 0 ? 1.15 : 1 / 1.15)));
        if (nz != zoom) {
            zoom = nz;
            camX = wx - (mx - ccx()) / zoom;
            camY = wy - (my - ccy()) / zoom;
            tgtX = camX;
            tgtY = camY;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            select(null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_C) {
            showCodex = !showCodex;
            return true;
        }
        if (showCodex) {
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
                codexPick = Math.min(Math.max(0, unlockedLore().size() - 1), codexPick + 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
                codexPick = Math.max(0, codexPick - 1);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            int t = neighbour(key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A ? -1 : 1);
            if (t >= 0) {
                selected = null;
                showChapter(t, true);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_HOME) {
            Node now = nowNode();
            if (now != null) {
                focusOn(now);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            zoom = Math.min(2f, zoom * 1.15f);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoom = Math.max(0.5f, zoom / 1.15f);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

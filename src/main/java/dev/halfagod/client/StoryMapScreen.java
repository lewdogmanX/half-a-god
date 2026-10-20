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
    private static final int COL_W = 116;
    private static final int ROW_H = 46;
    private static final int LANE_GAP = 20;
    private static final int R = 11;

    // ---- screen layout
    private static final int TOP = 46;
    private static final int FOOT = 24;

    private static StoryMapScreen open;
    private static JsonObject pendingStates;

    private record Node(String id, String title, String lane, String group, int col, int row, String summary, List<String> goals,
                        List<Integer> stages, ItemStack icon, boolean choice, int chapter, List<String> note) {
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
    private float zoom = 0.7f;
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
    /** lore, letters, path, dreams, secrets, trials, story, fates */
    private String codexTab = "lore";
    private final Map<String, Integer> tabPicks = new HashMap<>();
    private boolean previewStory;
    // what's new since the last update: margin notes, dreams, the hidden questlines, trials, the end card, friends
    private final Map<String, Integer> doneRep = new HashMap<>();
    private JsonArray dreams = new JsonArray();
    private JsonArray secrets = new JsonArray();
    private JsonArray trials = new JsonArray();
    private JsonObject story = new JsonObject();
    private boolean storyOver;
    private JsonArray others = new JsonArray();
    /** "Your Story": a frame or two drawn alone so it can be saved as a picture */
    private int captureFrames;
    private int cardX0, cardY0, cardX1, cardY1;
    /** what the server said about a quest you tried to start from the map, and which quest */
    private String startNote = "";
    private String startNoteFor = "";
    private String saveNote = "";
    private long saveNoteAt;
    private int pageScroll;
    private int pageContentH;
    private JsonArray letters = new JsonArray();
    private JsonArray path = new JsonArray();
    private JsonArray fates;
    private JsonObject moments = new JsonObject();
    private JsonObject ranks = new JsonObject();
    private long day;
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
                            List<String> note = new ArrayList<>();
                            if (n.has("note")) {
                                n.getAsJsonArray("note").forEach(x -> note.add(x.getAsString()));
                            }
                            ResourceLocation iconId = ResourceLocation.tryParse(n.get("icon").getAsString());
                            var item = iconId == null ? Items.PAPER : BuiltInRegistries.ITEM.get(iconId);
                            Node node = new Node(n.get("id").getAsString(), n.get("title").getAsString(), n.get("lane").getAsString(),
                                    n.has("group") ? n.get("group").getAsString() : "",
                                    n.get("col").getAsInt(), n.get("row").getAsInt(), n.get("summary").getAsString(), goals, st,
                                    new ItemStack(item == Items.AIR ? Items.PAPER : item), n.get("choice").getAsBoolean(), ci, note);
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

    /** CI screenshots only: keep the made-up map instead of the server's */
    public static boolean testFreeze;

    public static void receive(String json) {
        if (testFreeze) {
            return;
        }
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
        if (o.has("notice")) {
            startNote = o.get("notice").getAsString();
            startNoteFor = o.has("noticeFor") ? o.get("noticeFor").getAsString() : "";
        }
        states = s;
        stages = st;
        rep = o.has("rep") ? o.get("rep").getAsDouble() : 0;
        letters = o.has("letters") ? o.getAsJsonArray("letters") : new JsonArray();
        path = o.has("path") ? o.getAsJsonArray("path") : new JsonArray();
        fates = o.has("fates") ? o.getAsJsonArray("fates") : null;
        moments = o.has("moments") ? o.getAsJsonObject("moments") : new JsonObject();
        ranks = o.has("ranks") ? o.getAsJsonObject("ranks") : new JsonObject();
        day = o.has("day") ? o.get("day").getAsLong() : 0;
        doneRep.clear();
        if (o.has("doneRep")) {
            o.getAsJsonObject("doneRep").entrySet().forEach(e -> doneRep.put(e.getKey(), e.getValue().getAsInt()));
        }
        dreams = o.has("dreams") ? o.getAsJsonArray("dreams") : new JsonArray();
        secrets = o.has("secrets") ? o.getAsJsonArray("secrets") : new JsonArray();
        trials = o.has("trials") ? o.getAsJsonArray("trials") : new JsonArray();
        story = o.has("story") ? o.getAsJsonObject("story") : new JsonObject();
        storyOver = o.has("storyOver") && o.get("storyOver").getAsBoolean();
        others = o.has("others") ? o.getAsJsonArray("others") : new JsonArray();
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
        // tea and candle-wax stains, scattered over the whole sheet
        for (int gx = (int) Math.floor((minX - 300) / 170); gx <= (int) Math.ceil((maxX + 500) / 170); gx++) {
            for (int gy = (int) Math.floor((minY - 260) / 170); gy <= (int) Math.ceil((maxY + 260) / 170); gy++) {
                int h = gx * 92837111 ^ gy * 689287499 ^ chapter * 283923481;
                h = (h ^ (h >>> 15)) * 0x2C1B3C6D;
                h ^= h >>> 12;
                float r = ((h >>> 8) & 0xFFFF) / 65535f;
                if (r < 0.32f) {
                    decos.add(new Deco(3, gx * 170 + ((h & 0x3F) - 32), gy * 170 + (((h >>> 6) & 0x3F) - 32)));
                }
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
        // where you are sits right of centre, so most of the screen shows the road behind you
        tgtX = focus == null || maxX - minX < viewW * 0.85 ? (minX + maxX) / 2 : Math.max(minX + viewW / 2 - 40, focus.x() - viewW * 0.18);
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
        drawNotes(g, cx0, cy0, cx1, cy1);
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
            g.fill(x, y0, x + 1, y1, 0x0E6B5638);
        }
        for (double gy = Math.floor(wy0 / step) * step; gy <= wy1; gy += step) {
            int y = sy(gy);
            g.fill(x0, y, x1, y + 1, 0x0E6B5638);
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
            case 3 -> { // a stain
                int rr = Math.max(4, Math.round((14 + (int) Math.floorMod((long) (x * 7 + y), 20L)) * zoom));
                disc(g, sx(x), sy(y), rr, 0x0A6B4A20);
                ring(g, sx(x), sy(y), rr, Math.max(1, Math.round(1.5f * zoom)), 0x1C6B4A20);
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
            g.fill(x - w / 2 - 2, ty - 1, x + w / 2 + 2, ty + 9, 0xD0EADBB8);
            g.drawString(font, line, x - w / 2, ty, color, false);
            ty += 10;
        }
    }

    /** 0 good (Good, Saint), 1 wavering, 2 evil (Fallen, Wicked): the mood a quest's margin note is written in */
    private int noteTone(Node n) {
        Integer at = doneRep.get(n.id());
        double r = at != null ? at : rep;
        return r >= 20 ? 0 : r > -20 ? 1 : 2;
    }

    private String noteOf(Node n) {
        if (n.note().size() < 3 || !done(n.id())) {
            return null;
        }
        return n.note().get(noteTone(n));
    }

    /** What you scribbled beside each main-story quest you finished: dark ink, slightly crooked, above the seal. */
    private void drawNotes(GuiGraphics g, int cx0, int cy0, int cx1, int cy1) {
        if (zoom < 0.55f) {
            return;
        }
        float scale = Math.max(0.5f, Math.min(0.85f, 0.62f * zoom / 0.7f));
        for (Placed p : placed) {
            String note = noteOf(p.node());
            if (note == null) {
                continue;
            }
            // only where there's room: nothing sits just above this seal
            boolean crowded = false;
            for (Placed q : placed) {
                if (q != p && Math.abs(q.x() - p.x()) < COL_W * 0.6 && q.y() < p.y() && p.y() - q.y() < ROW_H * 1.2) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) {
                continue;
            }
            int x = sx(p.x()), y = sy(p.y());
            if (x < cx0 - 120 || x > cx1 + 120 || y < cy0 - 40 || y > cy1 + 60) {
                continue;
            }
            // as wide as the space between two seals on screen, in the note's own (smaller) letters
            int maxW = (int) ((COL_W - 20) * zoom / scale);
            List<FormattedCharSequence> lines = font.split(Component.literal(note).withStyle(ChatFormatting.ITALIC), maxW);
            int r = Math.max(7, Math.round(R * zoom));
            int ink = noteTone(p.node()) == 2 ? 0xD06A1414 : noteTone(p.node()) == 0 ? 0xD02A3A1A : 0xD04A3420;
            g.pose().pushPose();
            g.pose().translate(x, y - r - 4 - lines.size() * 9 * scale, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-3f + (Math.floorMod(p.node().id().hashCode(), 5) - 2) * 0.8f));
            g.pose().scale(scale, scale, 1);
            int ly = 0;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, -font.width(line) / 2, ly, ink, false);
                ly += 9;
            }
            g.pose().popPose();
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
        // fog over what you haven't reached, with a cloudy edge (seals of side stories further on still show through)
        int fx = sx(last.x() + 90);
        int span = Math.max(60, Math.round(220 * zoom));
        int top = TOP, bottom = height - FOOT;
        for (int i = 0; i < span; i += 3) {
            int a = (int) (0xE8 * Math.min(1, i / (double) span * 1.15));
            g.fill(fx + i, top, fx + i + 3, bottom, (a << 24) | 0xEDDDBA);
        }
        g.fill(fx + span, top, canvasRight(), bottom, 0xE8EDDDBA);
        double wy0 = camY + (top - ccy()) / zoom, wy1 = camY + (bottom - ccy()) / zoom;
        for (double wy = Math.floor(wy0 / 26) * 26; wy <= wy1 + 26; wy += 26) {
            int hh = (int) wy * 31 + chapter * 7;
            double off = Math.sin(hh * 0.7) * 22 + Math.sin(hh * 0.23) * 14;
            int rr = Math.max(5, Math.round((16 + (float) Math.abs(Math.sin(hh * 0.41)) * 12) * zoom));
            int px = sx(last.x() + 120 + off), py = sy(wy);
            disc(g, px, py, rr, 0x50F6ECD4);
            disc(g, px + rr / 2, py - rr / 3, Math.max(3, rr * 2 / 3), 0x40FBF4E2);
        }
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
        // who you are, top right (and your rank, if you have one)
        String tier = repTier();
        g.drawString(font, tier, width - 8 - font.width(tier), 5, 0xFF000000 | tierColor(), false);
        int wr = ranks.has("watch") ? ranks.get("watch").getAsInt() : 0, hr = ranks.has("hand") ? ranks.get("hand").getAsInt() : 0;
        if (wr > 0 || hr > 0) {
            String title = wr >= hr ? ranks.get("watchTitle").getAsString() : ranks.get("handTitle").getAsString();
            g.drawString(font, title, 8, 5, wr >= hr ? 0xFF8FB8E0 : 0xFFE0B860, false);
        }
        g.pose().popPose();
    }

    private String repTier() {
        if (rep <= -150) {
            return "Wicked";
        }
        if (rep <= -50) {
            return "Fallen";
        }
        if (rep < 50) {
            return "Wavering";
        }
        if (rep < 150) {
            return "Good";
        }
        return "Saint";
    }

    private int tierColor() {
        return rep <= -150 ? 0xC03030 : rep <= -50 ? 0xD06A3A : rep < 50 ? 0xC8B08A : rep < 150 ? 0x7FC07F : 0xF2D49A;
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
        boolean unread = anyUnread();
        bx = button(g, mx, my, bx, y0 + 4, showCodex ? "Map" : "Codex", () -> {
            showCodex = !showCodex;
            codexScroll = 0;
        }, unread && !showCodex);
        String help = showCodex ? "" : "Drag to move  ·  Scroll to zoom  ·  ← → turn the page  ·  Space: where you are";
        if (!others.isEmpty()) {
            // friends on the server: how far along they are and who they've become
            StringBuilder sb = new StringBuilder();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("Others on this server").withStyle(ChatFormatting.BOLD));
            for (JsonElement e : others) {
                JsonObject f = e.getAsJsonObject();
                String act = f.get("act").getAsString();
                String one = f.get("name").getAsString() + " (" + (act.isEmpty() ? "not started" : act) + ", " + f.get("tier").getAsString() + ")";
                sb.append(sb.length() == 0 ? "" : "  ·  ").append(one);
                String rank = !f.get("watch").getAsString().isEmpty() ? f.get("watch").getAsString() : f.get("hand").getAsString();
                tip.add(Component.literal(one + (rank.isEmpty() ? "" : ", " + rank) + (f.get("rival").getAsBoolean() ? "  - your rival" : ""))
                        .withColor(f.get("rival").getAsBoolean() ? 0xE07F6F : 0x3B2A1A));
            }
            String line = sb.toString();
            int room = bx - 12 - (x + 16);
            if (font.width(line) > room) {
                line = font.plainSubstrByWidth(line, Math.max(20, room - font.width("..."))) + "...";
            }
            if (room > 40) {
                int lx = (x + bx) / 2 - font.width(line) / 2;
                g.drawString(font, line, lx, y, 0xFFC9A880, false);
                hit(lx, y - 2, lx + font.width(line), y + 10, null, tip.toArray(new Component[0]));
            }
        } else if (!help.isEmpty() && x + 16 + font.width(help) < bx - 12) {
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
        // ready to start: start it from here, as if whoever gives it out had handed it over
        if (state(n.id()).equals("open")) {
            String label = "Start this quest";
            int bw = font.width(label) + 16;
            boolean hb = over(mx, my, ix, y, ix + bw, y + 16);
            g.fill(ix, y, ix + bw, y + 16, hb ? 0xFF6B4A22 : 0xFF4A3218);
            g.fill(ix, y, ix + bw, y + 1, hb ? GILT_BRIGHT : 0xFF8A6A32);
            g.drawString(font, label, ix + 8, y + 4, hb ? 0xFFFFF0C8 : 0xFFE8D2A0, false);
            String quest = n.id();
            hit(ix, y, ix + bw, y + 16, () -> {
                startNote = "";
                startNoteFor = quest;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.halfagod.net.Payloads.Act("start_quest", quest, ""));
            }, Component.literal("Start it now. It goes in your journal."));
            y += 20;
        }
        if (n.id().equals(startNoteFor) && !startNote.isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.literal(startNote).withStyle(ChatFormatting.ITALIC), iw)) {
                g.drawString(font, line, ix, y, startNote.startsWith("Started") ? 0xFF2E6B2E : RED_INK, false);
                y += 10;
            }
            y += 4;
        }
        y = divider(g, ix, iw, y);
        for (FormattedCharSequence line : font.split(Component.literal(n.summary()), iw)) {
            g.drawString(font, line, ix, y, 0xFF3B2A1A, false);
            y += 10;
        }
        String myNote = noteOf(n);
        if (myNote != null) {
            y += 6;
            g.drawString(font, "IN THE MARGIN", ix, y, 0xFF8A6A32, false);
            y += 12;
            for (FormattedCharSequence line : font.split(Component.literal("\u201C" + myNote + "\u201D").withStyle(ChatFormatting.ITALIC), iw)) {
                g.drawString(font, line, ix, y, noteTone(n) == 2 ? 0xFF6A1414 : 0xFF4A3420, false);
                y += 10;
            }
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
    /** Opens the Codex on a page: lore, letters, path, dreams, secrets, trials, story (Your Story), fates. */
    public void openCodex(String page) {
        showCodex = true;
        codexTab = page;
        if (page.equals("story")) {
            previewStory = true;
        }
    }

    private static final String[][] TAB_NAMES = {{"lore", "Lore"}, {"letters", "Letters"}, {"path", "Your Path"}, {"dreams", "Dreams"},
            {"secrets", "Secrets"}, {"trials", "Trials"}, {"story", "Your Story"}, {"fates", "What Became of Them"}};

    /** the Codex's pages you have (some only appear once there's something on them) */
    private List<String> tabs() {
        List<String> out = new ArrayList<>(List.of("lore", "letters", "path"));
        if (!dreams.isEmpty()) {
            out.add("dreams");
        }
        out.add("secrets");
        if (!trials.isEmpty()) {
            out.add("trials");
        }
        if (storyOver || previewStory) {
            out.add("story");
        }
        if (fates != null) {
            out.add("fates");
        }
        return out;
    }

    private static String tabName(String id, boolean shortNames) {
        if (shortNames && id.equals("fates")) {
            return "Fates";
        }
        if (shortNames && id.equals("path")) {
            return "Path";
        }
        if (shortNames && id.equals("story")) {
            return "Story";
        }
        for (String[] t : TAB_NAMES) {
            if (t[0].equals(id)) {
                return t[1];
            }
        }
        return id;
    }

    private static final Set<String> LORE_KINDS = Set.of("People", "Places", "The Cult", "Wardens");

    private List<Lore> unlockedLore() {
        List<Lore> out = new ArrayList<>();
        for (String cat : new String[]{"People", "Places", "The Cult", "Wardens"}) {
            for (Lore l : codex) {
                if (l.category().equals(cat) && reached(l.unlock())) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    private Lore loreTitled(String category, String title) {
        for (Lore l : codex) {
            if (l.category().equals(category) && l.title().equals(title)) {
                return l;
            }
        }
        return null;
    }

    private static String letterKey(JsonObject l) {
        return "letter:" + l.get("title").getAsString() + "/" + l.get("author").getAsString() + "@" + (l.has("day") ? l.get("day").getAsLong() : 0);
    }

    private static String dreamKey(JsonObject d) {
        return "dream:" + d.get("id").getAsString();
    }

    private boolean anyUnread() {
        for (Lore l : unlockedLore()) {
            if (!seenLore.contains(l.title())) {
                return true;
            }
        }
        for (JsonElement e : letters) {
            if (!seenLore.contains(letterKey(e.getAsJsonObject()))) {
                return true;
            }
        }
        for (JsonElement e : dreams) {
            if (!seenLore.contains(dreamKey(e.getAsJsonObject()))) {
                return true;
            }
        }
        return false;
    }

    /** One line on the left-hand page. */
    private record Row(String group, String groupNote, String title, String note, int dot, String seenKey) {
    }

    private int secretsFound() {
        int n = 0;
        for (JsonElement e : secrets) {
            if (e.getAsJsonObject().get("found").getAsBoolean()) {
                n++;
            }
        }
        return n;
    }

    private List<Row> rows(String tab) {
        List<Row> out = new ArrayList<>();
        switch (tab) {
            case "lore" -> {
                for (Lore l : unlockedLore()) {
                    int have = 0, all = 0;
                    for (Lore o : codex) {
                        if (o.category().equals(l.category())) {
                            all++;
                            if (reached(o.unlock())) {
                                have++;
                            }
                        }
                    }
                    out.add(new Row(l.category(), have + " of " + all, l.title(), "", 0, l.title()));
                }
            }
            case "letters" -> {
                Map<String, Integer> perAuthor = new HashMap<>();
                for (JsonElement e : letters) {
                    perAuthor.merge(e.getAsJsonObject().get("author").getAsString(), 1, Integer::sum);
                }
                for (int i = letters.size() - 1; i >= 0; i--) {
                    JsonObject l = letters.get(i).getAsJsonObject();
                    int n = perAuthor.get(l.get("author").getAsString());
                    out.add(new Row("From " + l.get("author").getAsString().replace("Old ", ""), n + (n == 1 ? " letter" : " letters"),
                            l.get("title").getAsString(), l.has("day") ? "day " + l.get("day").getAsLong() : "", 0, letterKey(l)));
                }
            }
            case "dreams" -> {
                for (int i = dreams.size() - 1; i >= 0; i--) {
                    JsonObject d = dreams.get(i).getAsJsonObject();
                    out.add(new Row("Dreams", dreams.size() + (dreams.size() == 1 ? " dream" : " dreams"), d.get("title").getAsString(),
                            "day " + (d.has("day") ? d.get("day").getAsLong() : 0), 0, dreamKey(d)));
                }
            }
            case "secrets" -> {
                for (JsonElement e : secrets) {
                    JsonObject x = e.getAsJsonObject();
                    boolean found = x.get("found").getAsBoolean(), done = x.get("done").getAsBoolean();
                    out.add(new Row("Secrets found", secretsFound() + " / " + secrets.size(), x.get("name").getAsString(),
                            done ? "done" : found ? "found" : "", found ? (done ? 0xFF7A4AB0 : 0xFFB48CFF) : 0x606B5638, ""));
                }
            }
            case "trials" -> {
                for (int i = trials.size() - 1; i >= 0; i--) {
                    JsonObject t = trials.get(i).getAsJsonObject();
                    boolean guilty = t.getAsJsonObject("verdict").get("guilty").getAsBoolean();
                    out.add(new Row("The Watch's court", trials.size() + (trials.size() == 1 ? " trial" : " trials"),
                            (guilty ? "Guilty" : "Free to go") + (t.has("act") && !t.get("act").getAsString().isEmpty() ? ", " + shortAct(t.get("act").getAsString()) : ""),
                            "day " + t.get("day").getAsLong(), guilty ? RED_INK : MERCY, ""));
                }
            }
            case "fates" -> {
                if (fates != null) {
                    for (JsonElement e : fates) {
                        JsonObject f = e.getAsJsonObject();
                        String kind = f.get("kind").getAsString();
                        out.add(new Row("What became of them", fates.size() + " people", f.get("name").getAsString(), "",
                                kind.equals("mercy") ? MERCY : kind.equals("blade") ? BLADE : 0xFF9A8662, ""));
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    private static String shortAct(String act) {
        int c = act.indexOf(':');
        return c > 0 ? act.substring(0, c) : act;
    }

    private void drawCodex(GuiGraphics g, int mx, int my) {
        int top = TOP + 24, bottom = height - FOOT - 10;
        int bw = Math.min(width - 30, 560);
        int bx = (width - bw) / 2;
        int mid = bx + bw / 2;
        g.fill(bx - 6, top - 4, bx + bw + 6, bottom + 4, 0xFF4A2A16);
        // bookmark ribbons along the top of the book (shorter names if they don't fit)
        List<String> tabs = tabs();
        if (!tabs.contains(codexTab) && statesKnown) {
            codexTab = "lore";
        }
        boolean shortNames = false;
        int pad = 16;
        int total = 0;
        for (String t : tabs) {
            total += font.width(tabName(t, false)) + pad + 3;
        }
        if (total > bw - 16) {
            shortNames = true;
            pad = 10;
        }
        int tx = bx + 8;
        for (String t : tabs) {
            String name = tabName(t, shortNames);
            int w = font.width(name) + pad;
            boolean on = t.equals(codexTab);
            boolean hover = over(mx, my, tx, top - 18, tx + w, top);
            int col = on ? PAPER_LIGHT : hover ? 0xFF9A3A2A : t.equals("secrets") ? 0xFF4A2A6A : 0xFF7A2418;
            g.fill(tx, top - (on ? 18 : 15), tx + w, top + (on ? 2 : 0), col);
            g.fill(tx, top - (on ? 18 : 15), tx + w, top - (on ? 17 : 14), on ? 0xFF8A6A32 : 0xFFB0503A);
            g.drawString(font, name, tx + pad / 2, top - (on ? 13 : 12), on ? RED_INK : 0xFFF2DCC0, false);
            boolean unread = false;
            if (!on && t.equals("letters")) {
                for (JsonElement e : letters) {
                    unread |= !seenLore.contains(letterKey(e.getAsJsonObject()));
                }
            } else if (!on && t.equals("dreams")) {
                for (JsonElement e : dreams) {
                    unread |= !seenLore.contains(dreamKey(e.getAsJsonObject()));
                }
            }
            if (unread) {
                disc(g, tx + w - 3, top - 13, 2, 0xFFFFD040);
            }
            hit(tx, top - 18, tx + w, top, () -> {
                codexTab = t;
                codexScroll = 0;
                pageScroll = 0;
            });
            tx += w + 3;
        }
        // the two pages
        g.fill(bx - 5, bottom + 2, bx + bw + 5, bottom + 3, 0xFF8A6A32);
        g.fill(bx, top, bx + bw, bottom, PAPER_LIGHT);
        for (int i = 0; i < 10; i++) {
            int a = (int) (0x38 * (i / 10.0));
            g.fill(mid - 10 + i, top, mid - 9 + i, bottom, a << 24);
            g.fill(mid + 9 - i, top, mid + 10 - i, bottom, a << 24);
        }
        g.fill(mid, top, mid + 1, bottom, 0x60301E0C);
        int lx = bx + 14, lw = bw / 2 - 28;
        int rx = mid + 18, rw = bw / 2 - 32;
        if (codexTab.equals("path")) {
            drawPath(g, lx, lw, rx, rw, top, bottom);
            return;
        }
        if (codexTab.equals("story")) {
            drawStoryCard(g, mx, my, bx, bw, top, bottom);
            return;
        }
        List<Row> list = rows(codexTab);
        int pick = tabPicks.getOrDefault(codexTab, -1);
        if (pick < 0 || pick >= list.size()) {
            pick = 0;
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).seenKey().isEmpty() && !seenLore.contains(list.get(i).seenKey())) {
                    pick = i;
                    break;
                }
            }
            tabPicks.put(codexTab, pick);
            pageScroll = 0;
        }
        codexPick = pick;
        // left page: the index
        g.enableScissor(bx, top + 4, mid - 6, bottom - 4);
        int y = top + 12 - codexScroll;
        String group = null;
        for (int i = 0; i < list.size(); i++) {
            Row row = list.get(i);
            if (!row.group().equals(group)) {
                group = row.group();
                if (i > 0) {
                    y += 6;
                }
                // (a long name gets cut short with "..." rather than running into the count)
                String head = group.toUpperCase();
                int room = lw - font.width(row.groupNote()) - 8;
                if (font.width(head) > room) {
                    head = font.plainSubstrByWidth(head, Math.max(0, room - font.width("..."))) + "...";
                }
                g.drawString(font, head, lx, y, RED_INK, false);
                g.drawString(font, row.groupNote(), lx + lw - font.width(row.groupNote()), y, 0xFF9A8662, false);
                g.fill(lx, y + 10, lx + lw, y + 11, 0x406B5638);
                y += 15;
            }
            boolean sel = i == pick;
            boolean hover = over(mx, my, lx - 4, y - 2, lx + lw, y + 10) && my > top && my < bottom;
            if (sel || hover) {
                g.fill(lx - 4, y - 2, lx + lw, y + 10, sel ? 0x40C9A55A : 0x206B5638);
            }
            if (sel) {
                g.fill(lx - 4, y - 2, lx - 2, y + 10, RED_INK);
            }
            int textX = lx + 2;
            if (row.dot() != 0) {
                disc(g, lx + 4, y + 3, 3, row.dot());
                textX = lx + 11;
            }
            int noteW = row.note().isEmpty() ? 0 : font.width(row.note()) + 8;
            g.drawString(font, font.plainSubstrByWidth(row.title(), lw - 16 - noteW - (textX - lx)), textX, y, sel ? INK : 0xFF5A4128, false);
            if (!row.note().isEmpty()) {
                g.drawString(font, row.note(), lx + lw - 8 - font.width(row.note()), y, 0xFF9A8662, false);
            }
            if (!row.seenKey().isEmpty() && !seenLore.contains(row.seenKey())) {
                disc(g, lx + lw - 3, y + 3, 2, 0xFFD03A2A);
            }
            final int ix = i;
            if (my > top && my < bottom) {
                hit(lx - 4, y - 2, lx + lw, y + 10, () -> {
                    tabPicks.put(codexTab, ix);
                    pageScroll = 0;
                });
            }
            y += 13;
        }
        if (codexTab.equals("lore")) {
            int all = 0;
            for (Lore l : codex) {
                if (LORE_KINDS.contains(l.category())) {
                    all++;
                }
            }
            int locked = all - list.size();
            if (locked > 0) {
                y += 8;
                g.drawString(font, Component.literal(locked + " more to discover").withStyle(ChatFormatting.ITALIC), lx, y, 0xFF9A8662, false);
                y += 10;
            }
        } else if (codexTab.equals("secrets")) {
            y += 8;
            for (FormattedCharSequence line : font.split(Component.literal("Five hidden paths. Each one opens when you do something nobody asked you to.")
                    .withStyle(ChatFormatting.ITALIC), lw)) {
                g.drawString(font, line, lx, y, 0xFF9A8662, false);
                y += 10;
            }
        }
        codexContentH = y + codexScroll - top;
        g.disableScissor();
        // right page: the entry
        if (list.isEmpty()) {
            String none = codexTab.equals("letters") ? "No letters yet. Someone will write." : codexTab.equals("fates") ? "..." : "Nothing yet. Go and find out.";
            g.drawString(font, Component.literal(none).withStyle(ChatFormatting.ITALIC), rx, top + 20, INK_SOFT, false);
            return;
        }
        Row row = list.get(pick);
        if (!row.seenKey().isEmpty()) {
            markSeen(row.seenKey());
        }
        g.enableScissor(mid + 4, top + 4, bx + bw, bottom - 4);
        int ry = top + 14 - pageScroll;
        int start = ry;
        switch (codexTab) {
            case "lore" -> {
                Lore l = unlockedLore().get(pick);
                ry = pageHeading(g, rx, rw, ry, l.category().toUpperCase(), l.title());
                Node from = nodes.get(l.unlock());
                String trait = l.unlock().replace("mebahelrpgrquest:", "") + "_done";
                if (l.category().equals("Wardens") && moments.has(trait)) {
                    g.drawString(font, Component.literal("Fell on day " + moments.get(trait).getAsLong() + " of your journey").withStyle(ChatFormatting.ITALIC), rx, ry, RED_INK, false);
                    ry += 12;
                } else if (from != null) {
                    g.drawString(font, Component.literal("from " + from.title()).withStyle(ChatFormatting.ITALIC), rx, ry, 0xFF9A8662, false);
                    ry += 12;
                }
                ry = divider(g, rx, rw, ry) + 2;
                ry = paragraph(g, l.text(), rx, rw, ry);
            }
            case "letters" -> {
                JsonObject l = letters.get(letters.size() - 1 - pick).getAsJsonObject();
                String kicker = l.has("found") && l.get("found").getAsBoolean() ? "FOUND ON THE ROAD" : "A LETTER";
                ry = pageHeading(g, rx, rw, ry, kicker + (l.has("day") ? "  ·  DAY " + l.get("day").getAsLong() : ""), l.get("title").getAsString());
                ry = divider(g, rx, rw, ry) + 2;
                for (JsonElement pg : l.getAsJsonArray("pages")) {
                    ry = paragraph(g, pg.getAsString(), rx, rw, ry) + 6;
                }
                String sign = "- " + l.get("author").getAsString();
                g.drawString(font, Component.literal(sign).withStyle(ChatFormatting.ITALIC), rx + rw - font.width(sign) - 4, ry, INK_SOFT, false);
                ry += 12;
            }
            case "dreams" -> {
                JsonObject d = dreams.get(dreams.size() - 1 - pick).getAsJsonObject();
                String kind = d.has("kind") ? d.get("kind").getAsString() : "";
                String kicker = (kind.equals("past") ? "WHAT HE WAS" : kind.equals("omen") ? "AN OMEN" : kind.equals("echo") ? "AN ECHO" : "A DREAM")
                        + "  ·  NIGHT " + (d.has("day") ? d.get("day").getAsLong() : 0);
                ry = pageHeading(g, rx, rw, ry, kicker, d.get("title").getAsString());
                ry = divider(g, rx, rw, ry) + 4;
                for (JsonElement e : d.getAsJsonArray("lines")) {
                    for (FormattedCharSequence line : font.split(Component.literal(e.getAsString()).withStyle(ChatFormatting.ITALIC), rw)) {
                        g.drawString(font, line, rx, ry, 0xFF3B2A1A, false);
                        ry += 11;
                    }
                    ry += 6;
                }
            }
            case "secrets" -> {
                JsonObject x = secrets.get(pick).getAsJsonObject();
                boolean found = x.get("found").getAsBoolean();
                ry = pageHeading(g, rx, rw, ry, "A HIDDEN PATH", found ? x.get("name").getAsString() : "Not found yet");
                if (!found) {
                    ry = divider(g, rx, rw, ry) + 2;
                    ry = paragraph(g, "Something out there is waiting for you to do the thing nobody would think to do. "
                            + "Pray where you shouldn't, feed who you needn't, dream, wander, or keep your hands clean.", rx, rw, ry);
                } else {
                    g.drawString(font, Component.literal(x.get("done").getAsBoolean() ? "You followed it to the end" : "You're on it now")
                            .withStyle(ChatFormatting.ITALIC), rx, ry, 0xFF7A4AB0, false);
                    ry += 12;
                    ry = divider(g, rx, rw, ry) + 2;
                    ry = paragraph(g, x.get("how").getAsString(), rx, rw, ry) + 6;
                    Lore l = loreTitled("Secrets", x.get("name").getAsString());
                    if (l != null && x.get("done").getAsBoolean()) {
                        ry = paragraph(g, l.text(), rx, rw, ry);
                    } else if (l != null) {
                        g.drawString(font, Component.literal("Follow it to the end to learn what it means.").withStyle(ChatFormatting.ITALIC), rx, ry, 0xFF9A8662, false);
                        ry += 12;
                    }
                }
            }
            case "trials" -> {
                JsonObject t = trials.get(trials.size() - 1 - pick).getAsJsonObject();
                JsonObject v = t.getAsJsonObject("verdict");
                boolean guilty = v.get("guilty").getAsBoolean();
                ry = pageHeading(g, rx, rw, ry, "THE COURT OF THE WATCH  ·  DAY " + t.get("day").getAsLong(), guilty ? "Guilty" : "Free to go");
                String plea = v.get("plea").getAsString().replace("_", " ");
                String outcome = t.has("outcome") ? t.get("outcome").getAsString() : "";
                String how = (plea.equals("silent") ? "You said nothing" : plea.equals("bribe") ? "You paid a juror" : "You pleaded " + plea)
                        + (guilty ? ". " + v.get("days").getAsInt() + " days in the cells" + (outcome.isEmpty() || outcome.equals("in the cells") ? "." : ": " + outcome + ".") : ".");
                ry = paragraph(g, how, rx, rw, ry) + 2;
                ry = divider(g, rx, rw, ry) + 2;
                for (JsonElement e : t.getAsJsonArray("lines")) {
                    JsonArray a = e.getAsJsonArray();
                    Component c = Component.empty().append(Component.literal(a.get(0).getAsString() + ": ").withStyle(ChatFormatting.BOLD))
                            .append(Component.literal(a.get(1).getAsString()));
                    for (FormattedCharSequence line : font.split(c, rw)) {
                        g.drawString(font, line, rx, ry, 0xFF3B2A1A, false);
                        ry += 10;
                    }
                    ry += 4;
                }
            }
            case "fates" -> {
                if (fates != null) {
                    JsonObject f = fates.get(pick).getAsJsonObject();
                    ry = pageHeading(g, rx, rw, ry, "WHAT BECAME OF THEM", f.get("name").getAsString());
                    String kind = f.get("kind").getAsString();
                    String how = kind.equals("mercy") ? "You showed mercy" : kind.equals("blade") ? "You chose the blade" : "";
                    if (!how.isEmpty()) {
                        disc(g, rx + 3, ry + 3, 3, kind.equals("mercy") ? MERCY : BLADE);
                        g.drawString(font, Component.literal(how).withStyle(ChatFormatting.ITALIC), rx + 10, ry, kind.equals("mercy") ? 0xFF2E6B2E : 0xFF6A1010, false);
                        ry += 12;
                    }
                    ry = divider(g, rx, rw, ry) + 2;
                    ry = paragraph(g, f.get("text").getAsString(), rx, rw, ry);
                }
            }
            default -> {
            }
        }
        pageContentH = ry - start + 20;
        g.disableScissor();
    }

    // ------------------------------------------------------------------ "Your Story": the end card
    private void drawStoryCard(GuiGraphics g, int mx, int my, int bx, int bw, int top, int bottom) {
        // the card: a sheet of parchment with a wax seal, centred on the open book
        int cw = Math.min(bw - 40, 380), ch = Math.min(bottom - top - 16, 250);
        int x0 = bx + (bw - cw) / 2, y0 = top + (bottom - top - ch) / 2;
        cardX0 = x0 - 6;
        cardY0 = y0 - 6;
        cardX1 = x0 + cw + 6;
        cardY1 = y0 + ch + 6;
        g.fill(x0 - 6, y0 - 6, x0 + cw + 6, y0 + ch + 6, 0xFF2A1C11);
        g.fill(x0 - 5, y0 - 5, x0 + cw + 5, y0 - 4, GILT);
        g.fill(x0 - 5, y0 + ch + 4, x0 + cw + 5, y0 + ch + 5, GILT);
        g.fill(x0, y0, x0 + cw, y0 + ch, PAPER);
        g.enableScissor(x0, y0, x0 + cw, y0 + ch);
        drawPaper(g, x0, y0, x0 + cw, y0 + ch);
        g.disableScissor();
        String name = story.has("name") ? story.get("name").getAsString() : "";
        boolean over = storyOver;
        String kicker = (over ? "THE STORY OF " : "THE STORY SO FAR: ") + name.toUpperCase();
        g.drawString(font, kicker, x0 + cw / 2 - font.width(kicker) / 2, y0 + 10, RED_INK, false);
        String ending = story.has("ending") ? story.get("ending").getAsString() : "";
        int ey = y0 + 24;
        if (font.width(ending) * 2 <= cw - 20) {
            g.pose().pushPose();
            g.pose().translate(x0 + cw / 2f - font.width(ending), ey, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, ending, 0, 0, INK, false);
            g.pose().popPose();
            ey += 22;
        } else {
            g.drawString(font, Component.literal(ending).withStyle(ChatFormatting.BOLD), x0 + cw / 2 - font.width(ending) / 2, ey, INK, false);
            ey += 12;
        }
        String half = story.has("half") ? story.get("half").getAsString() : "";
        for (FormattedCharSequence line : font.split(Component.literal(half).withStyle(ChatFormatting.ITALIC), cw - 30)) {
            g.drawString(font, line, x0 + cw / 2 - font.width(line) / 2, ey, 0xFF6B5638, false);
            ey += 10;
        }
        ey += 4;
        int dx = x0 + cw / 2;
        g.fill(x0 + 20, ey, dx - 6, ey + 1, 0x606B5638);
        g.fill(dx + 7, ey, x0 + cw - 20, ey + 1, 0x606B5638);
        diamond(g, dx, ey, 2, 0xFF8A6A32);
        ey += 8;
        // the numbers, in two columns
        String[][] stats = {
                {"Days", num("days")}, {"Deaths", num("deaths")},
                {"Spared", num("spared")}, {"Killed", num("killed")},
                {"Villagers killed", num("villagers")}, {"Wardens fallen", num("wardens") + " of 7"},
                {"Letters", num("letters")}, {"Dreams", num("dreams")},
                {"Secrets", num("secrets") + " / " + num("secretsTotal")}, {"Trials", num("trials") + (story.has("escapes") && story.get("escapes").getAsInt() > 0 ? " (" + num("escapes") + " escaped)" : "")},
        };
        int colW = (cw - 40) / 2;
        for (int i = 0; i < stats.length; i++) {
            int sx = x0 + 20 + (i % 2) * (colW + 10);
            int sy = ey + (i / 2) * 11;
            g.drawString(font, stats[i][0], sx, sy, 0xFF6B5638, false);
            g.drawString(font, stats[i][1], sx + colW - font.width(stats[i][1]), sy, INK, false);
        }
        ey += (stats.length + 1) / 2 * 11 + 4;
        String watch = story.has("watch") ? story.get("watch").getAsString() : "", hand = story.has("hand") ? story.get("hand").getAsString() : "";
        String rank = !watch.isEmpty() ? watch : !hand.isEmpty() ? hand : "Answered to no one";
        String standing = (story.has("tier") ? story.get("tier").getAsString() : "") + "  ·  " + rank;
        g.drawString(font, standing, x0 + cw / 2 - font.width(standing) / 2, ey, RED_INK, false);
        ey += 14;
        if (story.has("choices") && !story.getAsJsonArray("choices").isEmpty()) {
            g.drawString(font, "BIGGEST CHOICES", x0 + 20, ey, 0xFF8A6A32, false);
            ey += 11;
            for (JsonElement e : story.getAsJsonArray("choices")) {
                String c = font.plainSubstrByWidth("• " + e.getAsString(), cw - 40);
                g.drawString(font, c, x0 + 22, ey, INK, false);
                ey += 10;
            }
        }
        // the half you became, as a seal in the corner
        int sealX = x0 + cw - 26, sealY = y0 + ch - 26;
        String tier = story.has("tier") ? story.get("tier").getAsString() : "";
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tier.equals("Saint") || tier.equals("Good") ? MapArt.GOLD : tier.equals("Wavering") ? MapArt.TOKEN : MapArt.WAX,
                sealX - 14, sealY - 14, 28, 28, 0f, 0f, MapArt.SEAL, MapArt.SEAL, MapArt.SEAL, MapArt.SEAL);
        RenderSystem.disableBlend();
        g.drawString(font, "Half a God", x0 + 10, y0 + ch - 12, 0xFF9A8662, false);
        if (captureFrames > 0) {
            return; // the picture is being taken: no buttons on it
        }
        // save it as a picture
        String label = "Save image";
        int bw2 = font.width(label) + 14;
        int bxx = x0 + cw / 2 - bw2 / 2, byy = y0 + ch + 10;
        if (byy + 16 > bottom + 2) {
            byy = y0 + ch - 20;
            bxx = x0 + 10 + font.width("Half a God") + 10;
        }
        boolean hover = over(mx, my, bxx, byy, bxx + bw2, byy + 16);
        g.fill(bxx, byy, bxx + bw2, byy + 16, hover ? 0xFF6B4A22 : 0xFF4A3218);
        g.fill(bxx, byy, bxx + bw2, byy + 1, hover ? GILT_BRIGHT : 0xFF8A6A32);
        g.drawString(font, label, bxx + 7, byy + 4, hover ? 0xFFFFF0C8 : 0xFFE8D2A0, false);
        hit(bxx, byy, bxx + bw2, byy + 16, () -> captureFrames = 2, Component.literal("Saves this card to your screenshots folder"));
        if (!saveNote.isEmpty() && Util.getMillis() - saveNoteAt < 6000) {
            g.drawString(font, saveNote, x0 + cw / 2 - font.width(saveNote) / 2, Math.min(bottom - 10, byy + 20), 0xFFE8D2A0, false);
        }
    }

    /** CI screenshots: press "Save image" */
    /** CI only: open a quest's page, and press its "Start this quest" button */
    public boolean selectForTest(String id) {
        for (Chapter c : chapters) {
            for (Node n : c.nodes()) {
                if (n.id().equals(id)) {
                    select(n);
                    return true;
                }
            }
        }
        return false;
    }

    public String stateForTest(String id) {
        return state(id);
    }

    public void startForTest() {
        if (selected != null && state(selected.id()).equals("open")) {
            startNote = "";
            startNoteFor = selected.id();
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.halfagod.net.Payloads.Act("start_quest", selected.id(), ""));
        }
    }

    public void saveCardForTest() {
        captureFrames = 2;
    }

    private String num(String key) {
        return story.has(key) ? Long.toString(story.get(key).getAsLong()) : "0";
    }

    /** Called between frames: cut the card out of what was just drawn and save it as a PNG. */
    private void captureCard() {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int px0 = (int) Math.round(cardX0 * scale), py0 = (int) Math.round(cardY0 * scale);
        int pw = (int) Math.round((cardX1 - cardX0) * scale), ph = (int) Math.round((cardY1 - cardY0) * scale);
        try (com.mojang.blaze3d.platform.NativeImage full = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pw = Math.min(pw, full.getWidth() - px0);
            ph = Math.min(ph, full.getHeight() - py0);
            if (pw <= 0 || ph <= 0 || px0 < 0 || py0 < 0) {
                saveNote = "Couldn't save the picture.";
                saveNoteAt = Util.getMillis();
                return;
            }
            try (com.mojang.blaze3d.platform.NativeImage card = new com.mojang.blaze3d.platform.NativeImage(pw, ph, false)) {
                for (int y = 0; y < ph; y++) {
                    for (int x = 0; x < pw; x++) {
                        card.setPixelRGBA(x, y, full.getPixelRGBA(px0 + x, py0 + y));
                    }
                }
                Path dir = mc.gameDirectory.toPath().resolve("screenshots");
                Files.createDirectories(dir);
                String name = "half_a_god_story_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date()) + ".png";
                card.writeToFile(dir.resolve(name));
                saveNote = "Saved to screenshots/" + name;
            }
        } catch (Exception e) {
            HalfAGod.LOG.warn("Could not save the story card", e);
            saveNote = "Couldn't save the picture.";
        }
        saveNoteAt = Util.getMillis();
    }

    private int pageHeading(GuiGraphics g, int rx, int rw, int ry, String kicker, String title) {
        g.drawString(font, kicker, rx, ry, RED_INK, false);
        ry += 14;
        if (font.width(title) * 2 <= rw) {
            g.pose().pushPose();
            g.pose().translate(rx, ry, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, title, 0, 0, INK, false);
            g.pose().popPose();
            return ry + 22;
        }
        for (FormattedCharSequence line : font.split(Component.literal(title).withStyle(ChatFormatting.BOLD), rw)) {
            g.drawString(font, line, rx, ry, INK, false);
            ry += 11;
        }
        return ry + 3;
    }

    private int paragraph(GuiGraphics g, String text, int rx, int rw, int ry) {
        for (String para : text.split("\n")) {
            if (para.isBlank()) {
                ry += 6;
                continue;
            }
            for (FormattedCharSequence line : font.split(Component.literal(para), rw)) {
                g.drawString(font, line, rx, ry, 0xFF3B2A1A, false);
                ry += 11;
            }
        }
        return ry;
    }

    /** Your Path: your reputation across the story, and where you stand with the Watch and the Hand. */
    private void drawPath(GuiGraphics g, int lx, int lw, int rx, int rw, int top, int bottom) {
        g.drawString(font, "YOUR PATH", lx, top + 12, RED_INK, false);
        g.drawString(font, Component.literal("who you were, quest by quest").withStyle(ChatFormatting.ITALIC), lx, top + 24, 0xFF9A8662, false);
        // the five bands, Saint at the top (their names in a column as wide as the widest, so none runs off the page)
        String[] names = {"Saint", "Good", "Wavering", "Fallen", "Wicked"};
        int nameW = 0;
        for (String nm : names) {
            nameW = Math.max(nameW, font.width(nm));
        }
        int cx0 = lx + nameW + 6, cx1 = lx + lw, cy0 = top + 46, cy1 = bottom - 22;
        int[] tint = {0x30E0B040, 0x2060A050, 0x10806040, 0x20C06030, 0x30A02020};
        int[] ink = {0xFF9A6A10, 0xFF2E6B2E, 0xFF6B5638, 0xFF8A4A1A, 0xFF7A1818};
        for (int i = 0; i < 5; i++) {
            int y0 = cy0 + (cy1 - cy0) * i / 5, y1 = cy0 + (cy1 - cy0) * (i + 1) / 5;
            g.fill(cx0, y0, cx1, y1, tint[i]);
            g.drawString(font, names[i], cx0 - 3 - font.width(names[i]), (y0 + y1) / 2 - 4, ink[i], false);
        }
        g.fill(cx0, cy0, cx0 + 1, cy1, 0x806B5638);
        g.fill(cx0, (cy0 + cy1) / 2, cx1, (cy0 + cy1) / 2 + 1, 0x306B5638);
        List<Integer> vals = new ArrayList<>();
        List<int[]> acts = new ArrayList<>();
        List<String> actNames = new ArrayList<>();
        for (JsonElement e : path) {
            String v = e.getAsString();
            if (v.startsWith("#")) {
                acts.add(new int[]{vals.size()});
                actNames.add(v.substring(1));
            } else {
                try {
                    vals.add(Integer.parseInt(v));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        if (vals.size() < 2) {
            g.drawString(font, Component.literal("Your path begins with your first few quests.").withStyle(ChatFormatting.ITALIC), cx0 + 6, (cy0 + cy1) / 2 - 14, INK_SOFT, false);
        } else {
            int n = vals.size();
            java.util.function.IntUnaryOperator px = i -> cx0 + 3 + (int) Math.round((cx1 - cx0 - 8) * i / (double) (n - 1));
            java.util.function.IntUnaryOperator py = v -> cy0 + (int) Math.round((cy1 - cy0) * (250 - Math.max(-250, Math.min(250, v))) / 500.0);
            int lastEnd = -1000;
            for (int k = 0; k < acts.size(); k++) {
                int x = px.applyAsInt(Math.min(n - 1, acts.get(k)[0]));
                for (int y = cy0; y < cy1; y += 3) {
                    g.fill(x, y, x + 1, y + 1, 0x606B5638);
                }
                String label = actNames.get(k);
                label = label.contains(":") ? label.substring(0, label.indexOf(':')) : label;
                label = label.replace("Act ", "");
                int lx2 = Math.min(x + 2, cx1 - font.width(label));
                if (lx2 > lastEnd + 5 && lx2 >= cx0) {
                    g.drawString(font, label, lx2, cy1 + 3, 0xFF8A6A32, false);
                    lastEnd = lx2 + font.width(label);
                }
            }
            for (int i = 1; i < n; i++) {
                int x0 = px.applyAsInt(i - 1), y0 = py.applyAsInt(vals.get(i - 1)), x1 = px.applyAsInt(i), y1 = py.applyAsInt(vals.get(i));
                int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
                for (int k = 0; k <= steps; k++) {
                    int x = x0 + (x1 - x0) * k / Math.max(1, steps), y = y0 + (y1 - y0) * k / Math.max(1, steps);
                    g.fill(x, y, x + 2, y + 2, 0xE05A2A10);
                }
            }
            int ex = px.applyAsInt(n - 1), ey = py.applyAsInt(vals.get(n - 1));
            disc(g, ex, ey, 4, 0xFFD9A030);
            ring(g, ex, ey, 4, 1, 0xFF7A4A10);
        }
        // right page: who you are now (scrolls on small screens)
        g.enableScissor(rx - 8, top + 4, rx + rw + 14, bottom - 4);
        int ry = top + 12 - pageScroll;
        int start = ry;
        g.drawString(font, "WHO YOU ARE", rx, ry, RED_INK, false);
        ry += 14;
        String tier = repTier();
        g.pose().pushPose();
        g.pose().translate(rx, ry, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, tier, 0, 0, 0xFF000000 | darken(tierColor(), 0.7), false);
        g.pose().popPose();
        ry += 22;
        g.drawString(font, "Reputation " + Math.round(rep) + (day > 0 ? "  \u00b7  day " + day : ""), rx, ry, INK_SOFT, false);
        ry += 12;
        ry = divider(g, rx, rw, ry);
        ry = rankBlock(g, rx, rw, ry, "THE WATCH", "watch");
        ry = rankBlock(g, rx, rw, ry + 4, "THE GILDED HAND", "hand");
        ry = divider(g, rx, rw, ry + 2);
        int fallen = 0;
        for (Lore l : codex) {
            if (l.category().equals("Wardens") && moments.has(l.unlock().replace("mebahelrpgrquest:", "") + "_done")) {
                fallen++;
            }
        }
        g.drawString(font, "Wardens fallen: " + fallen + " of 7", rx, ry, INK, false);
        ry += 11;
        g.drawString(font, "Letters received: " + letters.size(), rx, ry, INK, false);
        ry += 11;
        g.drawString(font, "Dreams: " + dreams.size(), rx, ry, INK, false);
        ry += 11;
        g.drawString(font, "Secrets found: " + secretsFound() + " / " + secrets.size(), rx, ry, 0xFF5A2A8A, false);
        ry += 11;
        if (!trials.isEmpty()) {
            int guilty = 0;
            for (JsonElement e : trials) {
                if (e.getAsJsonObject().getAsJsonObject("verdict").get("guilty").getAsBoolean()) {
                    guilty++;
                }
            }
            g.drawString(font, "Trials: " + trials.size() + " (" + guilty + " guilty)", rx, ry, RED_INK, false);
            ry += 11;
        }
        // friends on the same server, and which of them are on the other side
        if (!others.isEmpty()) {
            ry = divider(g, rx, rw, ry + 2);
            g.drawString(font, "ON THIS SERVER", rx, ry, 0xFF8A6A32, false);
            ry += 11;
            for (JsonElement e : others) {
                JsonObject f = e.getAsJsonObject();
                boolean rival = f.get("rival").getAsBoolean();
                String act = f.get("act").getAsString();
                String line = f.get("name").getAsString() + ": " + f.get("tier").getAsString() + (act.isEmpty() ? "" : ", " + act) + (rival ? " (rival)" : "");
                for (FormattedCharSequence l : font.split(Component.literal(line), rw)) {
                    g.drawString(font, l, rx, ry, rival ? RED_INK : INK, false);
                    ry += 10;
                }
                // the Gilded Hand takes contracts from its own
                if (ranks.has("hand") && ranks.get("hand").getAsInt() >= 1 && f.has("uuid")) {
                    String price = "[Put a price on their head]";
                    g.drawString(font, price, rx + 6, ry, 0xFF8E1B1B, false);
                    if (ry > top + 4 && ry + 10 < bottom - 4) {
                        String uuid = f.get("uuid").getAsString();
                        hit(rx + 6, ry - 1, rx + 6 + font.width(price), ry + 9,
                                () -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.halfagod.net.Payloads.Act("bounty", "", uuid)),
                                Component.literal("The Gilded Hand sends hunters after them the next night (20 emeralds)."));
                    }
                    ry += 11;
                }
            }
        }
        pageContentH = ry - start + 16;
        g.disableScissor();
    }

    private int rankBlock(GuiGraphics g, int rx, int rw, int ry, String heading, String key) {
        g.drawString(font, heading, rx, ry, 0xFF8A6A32, false);
        ry += 11;
        int rank = ranks.has(key) ? ranks.get(key).getAsInt() : 0;
        String title = ranks.has(key + "Title") ? ranks.get(key + "Title").getAsString() : "";
        Component shown = Component.literal(rank == 0 ? "Not one of them" : title).withStyle(rank == 0 ? ChatFormatting.ITALIC : ChatFormatting.BOLD);
        if (font.width(shown) > rw - 40) {
            shown = Component.literal(font.substrByWidth(shown, rw - 40 - font.width("...")).getString() + "...").withStyle(rank == 0 ? ChatFormatting.ITALIC : ChatFormatting.BOLD);
        }
        g.drawString(font, shown, rx, ry, rank == 0 ? 0xFF9A8662 : INK, false);
        for (int i = 0; i < 3; i++) {
            diamond(g, rx + rw - 30 + i * 10, ry + 3, 3, i < rank ? (key.equals("watch") ? 0xFF4A7AB0 : 0xFFC9A55A) : 0x406B5638);
        }
        ry += 11;
        String prog = ranks.has(key + "Progress") ? ranks.get(key + "Progress").getAsString() : "";
        for (FormattedCharSequence line : font.split(Component.literal(prog), rw)) {
            g.drawString(font, line, rx, ry, 0xFF6B5638, false);
            ry += 10;
        }
        return ry + 2;
    }

    private int codexContentH;

    @Override
    public void tick() {
        // "Save image": the card was drawn without its button for a frame or two; now cut it out and save it
        if (captureFrames > 0 && --captureFrames == 0) {
            captureCard();
        }
    }

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
            int view = height - FOOT - TOP - 46;
            if (mx > width / 2.0) {
                pageScroll = (int) Math.max(0, Math.min(Math.max(0, pageContentH - view), pageScroll - scrollY * 20));
            } else {
                codexScroll = (int) Math.max(0, Math.min(Math.max(0, codexContentH - view), codexScroll - scrollY * 20));
            }
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
        float nz = (float) Math.max(0.4, Math.min(2.0, zoom * (scrollY > 0 ? 1.15 : 1 / 1.15)));
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
                tabPicks.put(codexTab, Math.min(Math.max(0, rows(codexTab).size() - 1), codexPick + 1));
                pageScroll = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
                tabPicks.put(codexTab, Math.max(0, codexPick - 1));
                pageScroll = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
                List<String> tabs = tabs();
                int i = Math.max(0, tabs.indexOf(codexTab));
                codexTab = tabs.get(Math.floorMod(i + (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A ? -1 : 1), tabs.size()));
                codexScroll = 0;
                pageScroll = 0;
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
            zoom = Math.max(0.4f, zoom / 1.15f);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

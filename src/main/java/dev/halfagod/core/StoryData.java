package dev.halfagod.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.halfagod.HalfAGod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Story tables shipped in the bundled datapack under data/half_a_god/hag_mod/*.json.
 * Keeping them as data means the story can be retuned without touching Java.
 */
public final class StoryData extends SimpleJsonResourceReloadListener {
    public static final StoryData INSTANCE = new StoryData();

    /** exact trait -> reputation change */
    public final Map<String, Double> repTraits = new HashMap<>();
    /** trait prefix -> reputation change */
    public final Map<String, Double> repPrefixes = new LinkedHashMap<>();
    /** reputation thresholds that hand out traits: value, trait, below? */
    public final List<Unlock> unlocks = new ArrayList<>();
    public final List<JsonObject> choices = new ArrayList<>();
    /** trait -> cinematic moment */
    public final Map<String, JsonObject> cinematics = new HashMap<>();
    /** trait -> (companion name -> line) */
    public final Map<String, Map<String, String>> reactions = new HashMap<>();
    public final List<String> companionNames = new ArrayList<>();
    /** companion name -> dialogue profile base (half_a_god:<base>_<tone>) */
    public final Map<String, String> companionProfiles = new HashMap<>();
    /** trait -> chronicle sentence */
    public final Map<String, String> chronicleLines = new HashMap<>();
    /** trait -> act heading (when that trait appears a new act starts in the Chronicle) */
    public final Map<String, String> chronicleActs = new LinkedHashMap<>();
    /** quest id -> successors by branch */
    public final Map<String, Graph> graph = new HashMap<>();
    /** courier: after quest -> book quest */
    public final Map<String, List<Courier>> couriers = new HashMap<>();
    public final List<String> advancementCouriers = new ArrayList<>();
    /** Mebahel's own questlines, woven into the acts */
    public final List<BaseLines.Line> baseLines = new ArrayList<>();
    /** quest id -> the story characters its villagers play */
    public final Map<String, List<Cast.Entry>> cast = new HashMap<>();
    /** quest id -> stage -> what that step asks for (for "not yet" lines) */
    public final Map<String, Map<Integer, List<String>>> steps = new HashMap<>();
    /** quest id -> where its map marker belongs (dimension) and what it's called */
    public final Map<String, String[]> markers = new HashMap<>();
    /** Mara's letters: trigger trait -> letter per tone */
    public final List<JsonObject> letters = new ArrayList<>();

    /** people you saved, who move into a village */
    public final List<Settlement.Settler> settlers = new ArrayList<>();
    /** Ashford's stages: trait -> stage number */
    public final Map<String, Integer> ashfordStages = new LinkedHashMap<>();
    /** repeatable Watch duties (good) and Gilded Hand contracts (evil) */
    public final List<String> watchDuties = new ArrayList<>();
    public final List<String> handContracts = new ArrayList<>();

    /** what became of everyone you judged: per person, the first matching trait's line */
    public final List<JsonObject> fates = new ArrayList<>();
    /** traits whose day is remembered (the Wardens) */
    public final java.util.Set<String> momentTraits = new java.util.HashSet<>();

    /** dreams that come by chance, and the ones the mod shows itself (by name) */
    public final List<JsonObject> dreams = new ArrayList<>();
    public final Map<String, JsonObject> specialDreams = new HashMap<>();
    /** the Trial: charges, what the court says, and the pleas */
    public final List<JsonObject> charges = new ArrayList<>();
    public JsonObject trialScript = new JsonObject();
    public final List<JsonObject> pleas = new ArrayList<>();
    /** the hidden questlines */
    public final List<JsonObject> secrets = new ArrayList<>();
    /** the acts, in order: trait that opens each one -> its name */
    public final Map<String, String> acts = new LinkedHashMap<>();
    /** the story simulator's road: every chapter's main quests in order, with their forks, and side decisions */
    public final List<JsonObject> simChapters = new ArrayList<>();
    /** other tables, whole, by file name (prison, interrogations, words) */
    public final Map<String, JsonObject> tables = new HashMap<>();
    /** the jury's two words, and the answers you can give to each charge */
    public JsonObject votes = new JsonObject();
    public final List<JsonObject> answers = new ArrayList<>();

    public JsonObject table(String name) {
        JsonObject o = tables.get(name);
        return o == null ? new JsonObject() : o;
    }

    public record Unlock(double at, boolean below, String trait, String courier) {
    }

    public record Courier(String book, boolean sparedOnly) {
    }

    public record Graph(List<String> next, List<String> spare, List<String> kill, boolean book) {
    }

    private StoryData() {
        super(new Gson(), "hag_mod");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        repTraits.clear();
        repPrefixes.clear();
        unlocks.clear();
        choices.clear();
        cinematics.clear();
        reactions.clear();
        companionNames.clear();
        companionProfiles.clear();
        chronicleLines.clear();
        chronicleActs.clear();
        graph.clear();
        couriers.clear();
        advancementCouriers.clear();
        baseLines.clear();
        letters.clear();
        cast.clear();
        steps.clear();
        markers.clear();
        settlers.clear();
        fates.clear();
        momentTraits.clear();
        ashfordStages.clear();
        watchDuties.clear();
        handContracts.clear();
        dreams.clear();
        specialDreams.clear();
        charges.clear();
        trialScript = new JsonObject();
        pleas.clear();
        secrets.clear();
        acts.clear();
        simChapters.clear();
        tables.clear();
        votes = new JsonObject();
        answers.clear();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            try {
                switch (e.getKey().getPath()) {
                    case "reputation" -> loadReputation(o);
                    case "choices" -> o.getAsJsonArray("choices").forEach(c -> choices.add(c.getAsJsonObject()));
                    case "cinematics" -> o.getAsJsonArray("moments").forEach(c -> cinematics.put(c.getAsJsonObject().get("trait").getAsString(), c.getAsJsonObject()));
                    case "companions" -> loadCompanions(o);
                    case "chronicle" -> loadChronicle(o);
                    case "story_graph" -> loadGraph(o);
                    case "base_lines" -> loadBaseLines(o);
                    case "cast" -> {
                        for (JsonElement c : o.getAsJsonArray("npcs")) {
                            JsonObject j = c.getAsJsonObject();
                            cast.computeIfAbsent(j.get("quest").getAsString(), k -> new ArrayList<>()).add(new Cast.Entry(
                                    j.get("quest").getAsString(), j.get("stage").getAsInt(), j.get("name").getAsString(),
                                    j.get("place").getAsString(), j.has("structure") ? j.get("structure").getAsString() : "",
                                    j.has("adopt") ? j.get("adopt").getAsString() : ""));
                        }
                        if (o.has("steps")) {
                            for (Map.Entry<String, JsonElement> q : o.getAsJsonObject("steps").entrySet()) {
                                Map<Integer, List<String>> byStage = new java.util.TreeMap<>();
                                for (JsonElement st : q.getValue().getAsJsonArray()) {
                                    JsonArray a = st.getAsJsonArray();
                                    byStage.computeIfAbsent(a.get(0).getAsInt(), k -> new ArrayList<>()).add(a.get(1).getAsString());
                                }
                                steps.put(q.getKey(), byStage);
                            }
                        }
                    }
                    case "markers" -> o.getAsJsonObject("quests").entrySet().forEach(m -> markers.put(m.getKey(),
                            new String[]{m.getValue().getAsJsonObject().get("dim").getAsString(), m.getValue().getAsJsonObject().get("title").getAsString()}));
                    case "settlement" -> {
                        for (JsonElement c : o.getAsJsonArray("settlers")) {
                            JsonObject j = c.getAsJsonObject();
                            settlers.add(new Settlement.Settler(j.get("key").getAsString(), j.get("name").getAsString(),
                                    j.get("place").getAsString(), j.get("trait").getAsString(), j.get("profession").getAsString(),
                                    j.has("when") ? j.get("when").getAsString() : "", list(j, "lines")));
                        }
                        for (JsonElement c : o.getAsJsonArray("ashford")) {
                            ashfordStages.put(c.getAsJsonObject().get("trait").getAsString(), c.getAsJsonObject().get("stage").getAsInt());
                        }
                    }
                    case "fates" -> o.getAsJsonArray("fates").forEach(f -> fates.add(f.getAsJsonObject()));
                    case "moments" -> momentTraits.addAll(list(o, "traits"));
                    case "duties" -> {
                        watchDuties.addAll(list(o, "watch"));
                        handContracts.addAll(list(o, "hand"));
                    }
                    case "letters" -> o.getAsJsonArray("letters").forEach(c -> letters.add(c.getAsJsonObject()));
                    case "dreams" -> {
                        o.getAsJsonArray("dreams").forEach(c -> dreams.add(c.getAsJsonObject()));
                        o.getAsJsonObject("special").entrySet().forEach(c -> specialDreams.put(c.getKey(), c.getValue().getAsJsonObject()));
                    }
                    case "trial" -> {
                        o.getAsJsonArray("charges").forEach(c -> charges.add(c.getAsJsonObject()));
                        trialScript = o.getAsJsonObject("script");
                        o.getAsJsonArray("pleas").forEach(c -> pleas.add(c.getAsJsonObject()));
                        if (o.has("votes")) {
                            votes = o.getAsJsonObject("votes");
                        }
                        if (o.has("answers")) {
                            o.getAsJsonArray("answers").forEach(c -> answers.add(c.getAsJsonObject()));
                        }
                    }
                    case "secrets" -> o.getAsJsonArray("lines").forEach(c -> secrets.add(c.getAsJsonObject()));
                    case "sim" -> o.getAsJsonArray("chapters").forEach(c -> simChapters.add(c.getAsJsonObject()));
                    case "acts" -> o.getAsJsonArray("acts").forEach(c -> acts.put(c.getAsJsonObject().get("trait").getAsString(),
                            c.getAsJsonObject().get("name").getAsString()));
                    case "prison", "interrogations", "words" -> tables.put(e.getKey().getPath(), o);
                    default -> {
                    }
                }
            } catch (Exception ex) {
                HalfAGod.LOG.error("Half a God: could not read story table {}", e.getKey(), ex);
            }
        }
        HalfAGod.LOG.info("Half a God story tables: {} choices, {} cinematic moments, {} story quests, {} of Mebahel's questlines.", choices.size(), cinematics.size(), graph.size(), baseLines.size());
    }

    private void loadReputation(JsonObject o) {
        o.getAsJsonObject("traits").entrySet().forEach(t -> repTraits.put(t.getKey(), t.getValue().getAsDouble()));
        o.getAsJsonObject("prefixes").entrySet().forEach(t -> repPrefixes.put(t.getKey(), t.getValue().getAsDouble()));
        for (JsonElement u : o.getAsJsonArray("unlocks")) {
            JsonObject j = u.getAsJsonObject();
            unlocks.add(new Unlock(j.get("at").getAsDouble(), j.has("below") && j.get("below").getAsBoolean(), j.get("trait").getAsString(),
                    j.has("courier") ? j.get("courier").getAsString() : ""));
        }
    }

    private void loadCompanions(JsonObject o) {
        o.getAsJsonArray("names").forEach(n -> companionNames.add(n.getAsString()));
        if (o.has("profiles")) {
            o.getAsJsonObject("profiles").entrySet().forEach(p -> companionProfiles.put(p.getKey(), p.getValue().getAsString()));
        }
        for (JsonElement r : o.getAsJsonArray("reactions")) {
            JsonObject j = r.getAsJsonObject();
            Map<String, String> lines = new LinkedHashMap<>();
            j.getAsJsonObject("lines").entrySet().forEach(l -> lines.put(l.getKey(), l.getValue().getAsString()));
            reactions.put(j.get("trait").getAsString(), lines);
        }
    }

    private void loadChronicle(JsonObject o) {
        o.getAsJsonObject("traits").entrySet().forEach(t -> chronicleLines.put(t.getKey(), t.getValue().getAsString()));
        o.getAsJsonObject("acts").entrySet().forEach(t -> chronicleActs.put(t.getKey(), t.getValue().getAsString()));
    }

    private void loadGraph(JsonObject o) {
        for (Map.Entry<String, JsonElement> q : o.getAsJsonObject("quests").entrySet()) {
            JsonObject j = q.getValue().getAsJsonObject();
            graph.put(q.getKey(), new Graph(list(j, "next"), list(j, "spare"), list(j, "kill"), j.has("book") && j.get("book").getAsBoolean()));
        }
        for (JsonElement c : o.getAsJsonArray("couriers")) {
            JsonObject j = c.getAsJsonObject();
            if (j.has("after")) {
                couriers.computeIfAbsent(j.get("after").getAsString(), k -> new ArrayList<>())
                        .add(new Courier(j.get("book").getAsString(), j.has("spared") && j.get("spared").getAsBoolean()));
            } else {
                advancementCouriers.add(j.get("book").getAsString());
            }
        }
    }

    private void loadBaseLines(JsonObject o) {
        for (JsonElement e : o.getAsJsonArray("lines")) {
            JsonObject j = e.getAsJsonObject();
            baseLines.add(new BaseLines.Line(j.get("chain").getAsString(), j.get("title").getAsString(), j.get("act").getAsString(),
                    j.get("gate").getAsString(), j.get("join").getAsString(), list(j, "starters"), list(j, "books"),
                    list(j, "members"), list(j, "placeable"), j.get("done_trait").getAsString()));
        }
    }

    private static List<String> list(JsonObject j, String key) {
        List<String> out = new ArrayList<>();
        if (j.has(key)) {
            JsonArray a = j.getAsJsonArray(key);
            a.forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }
}

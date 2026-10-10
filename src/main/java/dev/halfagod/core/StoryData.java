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
    /** trait -> chronicle sentence */
    public final Map<String, String> chronicleLines = new HashMap<>();
    /** trait -> act heading (when that trait appears a new act starts in the Chronicle) */
    public final Map<String, String> chronicleActs = new LinkedHashMap<>();
    /** quest id -> successors by branch */
    public final Map<String, Graph> graph = new HashMap<>();
    /** courier: after quest -> book quest */
    public final Map<String, List<String>> couriers = new HashMap<>();
    public final List<String> advancementCouriers = new ArrayList<>();

    public record Unlock(double at, boolean below, String trait) {
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
        chronicleLines.clear();
        chronicleActs.clear();
        graph.clear();
        couriers.clear();
        advancementCouriers.clear();
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
                    default -> {
                    }
                }
            } catch (Exception ex) {
                HalfAGod.LOG.error("Half a God: could not read story table {}", e.getKey(), ex);
            }
        }
        HalfAGod.LOG.info("Half a God story tables: {} choices, {} cinematic moments, {} story quests.", choices.size(), cinematics.size(), graph.size());
    }

    private void loadReputation(JsonObject o) {
        o.getAsJsonObject("traits").entrySet().forEach(t -> repTraits.put(t.getKey(), t.getValue().getAsDouble()));
        o.getAsJsonObject("prefixes").entrySet().forEach(t -> repPrefixes.put(t.getKey(), t.getValue().getAsDouble()));
        for (JsonElement u : o.getAsJsonArray("unlocks")) {
            JsonObject j = u.getAsJsonObject();
            unlocks.add(new Unlock(j.get("at").getAsDouble(), j.has("below") && j.get("below").getAsBoolean(), j.get("trait").getAsString()));
        }
    }

    private void loadCompanions(JsonObject o) {
        o.getAsJsonArray("names").forEach(n -> companionNames.add(n.getAsString()));
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
                couriers.computeIfAbsent(j.get("after").getAsString(), k -> new ArrayList<>()).add(j.get("book").getAsString());
            } else {
                advancementCouriers.add(j.get("book").getAsString());
            }
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

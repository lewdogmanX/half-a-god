package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Operators only: /halfagod test story <act> [good|evil|waver|random] puts you at a believable point in the story
 * without playing it. The quests before that point are marked done (with the map, margin notes and Your Path drawn as
 * if you'd played them), the decisions along the way are made the way a good, evil or wavering player would make them,
 * your reputation follows, and the Wardens fall on the days they would have.
 *
 * It's laid over your real story: Mebahel's RPG never sees it, so its own quests don't move. "/halfagod test story
 * clear" puts your real record back exactly as it was. Some things it sets off are real, though: Ashford grows and
 * settlers move in to match, so use a test world.
 */
public final class Simulate {
    private static final List<String> ACTS = List.of("prologue", "act1", "act2", "act3", "act4", "act5", "act6", "act7", "end", "after");
    /** the whole story up to the head altar is done, and the last fight (the Choir, then the True Azazel) starts for real */
    private static final String FINAL = "final";
    /** how many of the last chapter's quests come before the Choir of the Whole God (Caution, Up) */
    private static final int BEFORE_THE_ALTAR = 2;
    private static final String[] TIER_WORDS = {"wicked", "fallen", "waver", "good", "saint"};

    private Simulate() {
    }

    public static List<String> acts() {
        List<String> out = new ArrayList<>(ACTS);
        out.add(FINAL);
        out.add("random");
        return out;
    }

    public static String run(ServerPlayer player, String act, String mode) {
        if (act.equalsIgnoreCase(FINAL)) {
            return runTo(player, "end", BEFORE_THE_ALTAR, mode, true);
        }
        return run(player, act, mode, -1, true);
    }

    /**
     * The whole story up to a point inside one chapter (the first {@code upTo} of its quests done), and the quest that
     * comes next started for real. For jumping straight to a fight.
     */
    public static String runTo(ServerPlayer player, String chapter, int upTo, String mode, boolean startNext) {
        return run(player, chapter, mode, upTo, startNext);
    }

    private static String run(ServerPlayer player, String act, String mode, int stopAt, boolean startNext) {
        RepData d = Reputation.data(player);
        if (d.trialState != 0) {
            return "You're on trial or in a cell: /halfagod test trial release first.";
        }
        if (StoryData.INSTANCE.simChapters.isEmpty()) {
            return "The simulator's tables aren't loaded (is the Half a God story pack enabled?).";
        }
        RandomSource rnd = player.getRandom();
        act = act.toLowerCase(Locale.ROOT);
        mode = mode.toLowerCase(Locale.ROOT);
        if (act.equals("random")) {
            act = ACTS.get(rnd.nextInt(8)); // the prologue to Act VII
        }
        boolean finalFight = stopAt >= 0;
        if (!ACTS.contains(act)) {
            return "Unknown point in the story: " + act + ". Try one of " + String.join(", ", acts()) + ".";
        }
        if (mode.equals("random")) {
            mode = new String[]{"good", "evil", "waver"}[rnd.nextInt(3)];
        }
        if (!mode.equals("good") && !mode.equals("evil") && !mode.equals("waver")) {
            return "Play it as good, evil, waver or random.";
        }
        // start from your real record every time (the first simulation keeps a copy of it)
        if (!d.simTraits.isEmpty() || !d.simBackup.isEmpty()) {
            d = restore(player);
        }
        CompoundTagHolder.backup(player, d);
        Set<String> real = Meb.traits(player);
        boolean good = mode.equals("good"), evil = mode.equals("evil");
        double rep = d.value;
        int day = 2;
        int target = ACTS.indexOf(act);
        int quests = 0, decisions = 0;
        Set<String> added = new HashSet<>();
        List<String> path = new ArrayList<>(d.path);
        String next = "";
        for (JsonObject ch : StoryData.INSTANCE.simChapters) {
            int ci = ACTS.indexOf(ch.get("id").getAsString());
            if (ci < 0 || ci > target) {
                continue;
            }
            JsonArray qs = ch.getAsJsonArray("quests");
            // the act you asked for is only partly done; everything before it is finished
            int upTo = ci < target ? qs.size() : finalFight ? stopAt : (act.equals("end") || act.equals("after") ? qs.size() : Math.max(1, (int) Math.round(qs.size() * (0.25 + rnd.nextFloat() * 0.45))));
            int done = 0;
            for (JsonElement e : qs) {
                JsonObject q = e.getAsJsonObject();
                String id = q.get("id").getAsString();
                // the road forks after a decision, and at the very end goes the good way or the bad way
                if (q.has("branch")) {
                    String dec = d.simDecisions.get(q.get("from").getAsString());
                    if (dec == null || !dec.equals(q.get("branch").getAsString())) {
                        continue;
                    }
                }
                if (q.has("road") && (q.get("road").getAsString().equals("bad")) != (evil || (mode.equals("waver") && rep < 0))) {
                    continue;
                }
                if (done >= upTo) {
                    if (next.isEmpty()) {
                        next = id;
                    }
                    continue;
                }
                done++;
                quests++;
                String trait = id.replace("mebahelrpgrquest:", "") + "_done";
                rep += add(d, added, real, trait);
                if (q.has("choice") && q.get("choice").isJsonObject()) {
                    rep += decide(d, added, real, id, q.getAsJsonObject("choice"), mode, rnd);
                    decisions++;
                }
                // everyday life between quests: the good help out, the evil take what they want
                rep += (good ? 2.5 : evil ? -3.5 : (rnd.nextFloat() - 0.5) * 4) * Reputation.OLD_SCALE;
                rep = Math.max(-Reputation.MAX, Math.min(Reputation.MAX, rep));
                // mod choices that this quest opens
                for (JsonObject c : StoryData.INSTANCE.choices) {
                    if (c.get("trigger").getAsString().equals(trait)) {
                        rep += choose(d, added, real, c, mode, rnd);
                        decisions++;
                    }
                }
                if (StoryData.INSTANCE.chronicleActs.containsKey(trait)) {
                    path.add("#" + StoryData.INSTANCE.chronicleActs.get(trait));
                }
                path.add(Long.toString(Math.round(rep)));
                d.doneRep.put(id, (int) Math.round(rep));
                if (StoryData.INSTANCE.momentTraits.contains(trait)) {
                    d.moments.put(trait, (long) day);
                }
                day += 1 + rnd.nextInt(3);
            }
            // side quests with a decision in them, about two in three of them
            String tierWord = TIER_WORDS[Reputation.tierIndex(rep)];
            for (JsonElement e : ch.getAsJsonArray("side")) {
                JsonObject q = e.getAsJsonObject();
                String id = q.get("id").getAsString();
                String key = id.replace("mebahelrpgrquest:hag_", "");
                if (key.startsWith("rc") && !key.endsWith("_" + tierWord)) {
                    continue; // the reputation road only offers your own tier's quest
                }
                if (ci == target && rnd.nextFloat() < 0.5f || rnd.nextFloat() > 0.66f) {
                    continue;
                }
                rep += add(d, added, real, key.isEmpty() ? id : "hag_" + key + "_done");
                rep += decide(d, added, real, id, q.getAsJsonObject("choice"), mode, rnd);
                decisions++;
                rep = Math.max(-Reputation.MAX, Math.min(Reputation.MAX, rep));
            }
        }
        // the end of it all: the ending your reputation earned
        if (target >= ACTS.indexOf("end") && !finalFight) {
            add(d, added, real, "hag_rep_" + TIER_WORDS[Reputation.tierIndex(rep)]);
            d.endDay = day;
        }
        d.simTraits.addAll(added);
        d.knownTraits.addAll(added);   // nothing "happens" for made-up traits: no letters, title cards or choice screens
        d.path.clear();
        d.path.addAll(path);
        d.simNext = next;
        d.trialActs.clear();
        d.initialised = true;
        String label = StoryData.INSTANCE.acts.getOrDefault(Dreams.actTrait(Meb.traits(player)), act);
        d.simLabel = label + ", played " + mode;
        // set straight (not through the usual change, which would hand out real unlock letters)
        d.value = Math.max(-Reputation.MAX, Math.min(Reputation.MAX, rep));
        Reputation.sync(player, 0);
        Reputation.updateTierFlags(player);
        if (finalFight && !next.isEmpty() && !startNext) {
            return "Simulated the story up to " + next.replace("mebahelrpgrquest:hag_", "") + " (not started), played " + mode
                    + ", reputation " + Math.round(d.value) + " (" + Reputation.tier(d.value) + ").";
        }
        if (finalFight && !next.isEmpty()) {
            boolean started = Meb.startQuest(player, net.minecraft.resources.ResourceLocation.parse(next), false);
            return "Simulated the story up to " + next.replace("mebahelrpgrquest:hag_", "") + ", played " + mode + ", reputation "
                    + Math.round(d.value) + " (" + Reputation.tier(d.value) + "). " + (started ? "Started it." : "Couldn't start it.")
                    + " /halfagod test story clear puts your real story back.";
        }
        return "Simulated: " + label + ", played " + mode + ". " + quests + " quests done, " + decisions + " decisions made, reputation "
                + Math.round(Reputation.data(player).value) + " (" + Reputation.tier(Reputation.data(player).value) + ")."
                + (next.isEmpty() ? "" : " Next on the road: " + next.replace("mebahelrpgrquest:hag_", "") + ".")
                + " Your real story is untouched: /halfagod test story clear puts it back.";
    }

    /** adds a made-up trait (if it isn't really yours already) and returns what it does to your reputation */
    private static double add(RepData d, Set<String> added, Set<String> real, String trait) {
        if (real.contains(trait) || !added.add(trait)) {
            return 0;
        }
        return Story.repFor(trait);
    }

    /** a quest's spare-or-kill decision, made the way this kind of player would */
    private static double decide(RepData d, Set<String> added, Set<String> real, String quest, JsonObject choice, String mode, RandomSource rnd) {
        boolean spare = mode.equals("good") ? rnd.nextFloat() < 0.88f : mode.equals("evil") ? rnd.nextFloat() < 0.12f : rnd.nextBoolean();
        JsonArray traits = choice.getAsJsonArray(spare ? "spare" : "kill");
        if (traits == null || traits.isEmpty()) {
            spare = !spare;
            traits = choice.getAsJsonArray(spare ? "spare" : "kill");
        }
        d.simDecisions.put(quest, spare ? "spare" : "kill");
        double rep = 0;
        if (traits != null) {
            for (JsonElement t : traits) {
                rep += add(d, added, real, t.getAsString());
            }
        }
        return rep;
    }

    /** one of the big three-or-four-answer choices */
    private static double choose(RepData d, Set<String> added, Set<String> real, JsonObject c, String mode, RandomSource rnd) {
        JsonArray options = c.getAsJsonArray("options");
        JsonObject pick = null;
        for (JsonElement e : options) {
            JsonObject o = e.getAsJsonObject();
            double r = o.has("rep") ? o.get("rep").getAsDouble() : 0;
            double best = pick == null ? 0 : pick.has("rep") ? pick.get("rep").getAsDouble() : 0;
            if (pick == null || (mode.equals("good") && r > best) || (mode.equals("evil") && r < best)) {
                pick = o;
            }
        }
        if (mode.equals("waver") || pick == null) {
            pick = options.get(rnd.nextInt(options.size())).getAsJsonObject();
        }
        d.bigChoices.add((c.has("title") ? c.get("title").getAsString() : "A choice") + "|" + pick.get("label").getAsString() + "|"
                + (pick.has("rep") ? pick.get("rep").getAsDouble() : 0));
        return add(d, added, real, pick.get("trait").getAsString()) + (pick.has("rep") ? pick.get("rep").getAsDouble() : 0);
    }

    // ------------------------------------------------------------------ going back
    public static String clear(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 0) {
            return "You're on trial or in a cell: /halfagod test trial release first.";
        }
        if (d.simTraits.isEmpty() && d.simBackup.isEmpty()) {
            return "No simulated story to clear.";
        }
        restore(player);
        Reputation.sync(player, 0);
        Reputation.updateTierFlags(player);
        return "Your real story is back.";
    }

    public static String status(ServerPlayer player) {
        RepData d = Reputation.data(player);
        return d.simTraits.isEmpty() ? "No simulated story: you're playing your real one."
                : "Simulated: " + d.simLabel + " (" + d.simTraits.size() + " made-up story traits). /halfagod test story clear puts your real story back.";
    }

    private static RepData restore(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.simBackup.isEmpty()) {
            d.simTraits.clear();
            d.simDecisions.clear();
            d.simNext = "";
            return d;
        }
        RepData back = RepData.load(d.simBackup, player.registryAccess());
        player.setData(RepData.TYPE, back);
        return back;
    }

    /** keeps a copy of the real record the first time (simulations then always start from it) */
    private static final class CompoundTagHolder {
        static void backup(ServerPlayer player, RepData d) {
            if (d.simBackup.isEmpty()) {
                d.simBackup = RepData.save(d, player.registryAccess());
                d.simBackup.remove("simBackup");
            }
        }
    }
}

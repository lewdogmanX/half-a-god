package dev.halfagod.core;

import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tells the story map which quests you've done, which are on, and which are ready to start. */
public final class MapStates {
    private MapStates() {
    }

    public static void send(ServerPlayer player) {
        JsonObject states = new JsonObject();
        for (Meb.Progress p : Meb.quests(player)) {
            String s;
            if (p.claimed()) {
                s = p.spared() ? "done_spare" : p.attacked() ? "done_kill" : "done";
            } else if (p.failed()) {
                s = "failed";
            } else {
                s = "active";
            }
            String prev = states.has(p.questId()) ? states.get(p.questId()).getAsString() : "";
            // a quest done once stays done even if a later attempt failed
            if (!prev.startsWith("done")) {
                states.addProperty(p.questId(), s);
            }
        }
        for (String quest : StoryData.INSTANCE.markers.keySet()) {
            if (!states.has(quest) && Meb.canSee(player, ResourceLocation.parse(quest))) {
                states.addProperty(quest, "open");
            }
        }
        // which objective each quest you're on has reached, so the map can tick off what's done
        JsonObject stages = new JsonObject();
        for (Object p : Meb.rawQuests(player)) {
            if (Meb.isOpen(p)) {
                stages.addProperty(Meb.questIdOf(p), Meb.stageOf(p));
            }
        }
        JsonObject out = new JsonObject();
        out.add("states", states);
        out.add("stages", stages);
        out.addProperty("rep", Reputation.data(player).value);
        out.addProperty("op", player.hasPermissions(2));
        RepData d = Reputation.data(player);
        // the Codex's pages: letters, your path, the Wardens, your ranks, and (once it's over) what became of them
        com.google.gson.JsonArray letters = new com.google.gson.JsonArray();
        for (String l : d.letters) {
            letters.add(com.google.gson.JsonParser.parseString(l));
        }
        out.add("letters", letters);
        com.google.gson.JsonArray path = new com.google.gson.JsonArray();
        d.path.forEach(path::add);
        out.add("path", path);
        JsonObject moments = new JsonObject();
        d.moments.forEach(moments::addProperty);
        out.add("moments", moments);
        out.addProperty("day", player.level().getDayTime() / 24000L + 1);
        JsonObject ranks = new JsonObject();
        ranks.addProperty("watch", d.watchRank);
        ranks.addProperty("hand", d.handRank);
        ranks.addProperty("watchTitle", Ranks.WATCH[d.watchRank]);
        ranks.addProperty("handTitle", Ranks.HAND[d.handRank]);
        ranks.addProperty("watchProgress", Ranks.progress(player, true));
        ranks.addProperty("handProgress", Ranks.progress(player, false));
        out.add("ranks", ranks);
        java.util.Set<String> traits = Meb.traits(player);
        boolean over = Fates.storyOver(traits);
        if (over) {
            out.add("fates", Fates.compute(player));
        }
        // margin notes: your reputation when you finished each story quest
        JsonObject doneRep = new JsonObject();
        d.doneRep.forEach(doneRep::addProperty);
        out.add("doneRep", doneRep);
        // dreams you've had
        com.google.gson.JsonArray dreams = new com.google.gson.JsonArray();
        for (String s : d.dreams) {
            String[] parts = s.split("\\|");
            JsonObject dream = Dreams.byId(parts[0]);
            if (dream != null) {
                JsonObject o = dream.deepCopy();
                o.addProperty("day", parts.length > 1 ? Long.parseLong(parts[1]) : 0);
                dreams.add(o);
            }
        }
        out.add("dreams", dreams);
        // the hidden questlines
        com.google.gson.JsonArray secrets = new com.google.gson.JsonArray();
        for (JsonObject l : StoryData.INSTANCE.secrets) {
            JsonObject o = new JsonObject();
            boolean found = traits.contains(l.get("found").getAsString());
            o.addProperty("name", found ? l.get("name").getAsString() : "???");
            o.addProperty("how", found ? l.get("how").getAsString() : "");
            o.addProperty("found", found);
            o.addProperty("done", traits.contains(l.get("done").getAsString()));
            secrets.add(o);
        }
        out.add("secrets", secrets);
        // trials, with what each witness said
        com.google.gson.JsonArray trials = new com.google.gson.JsonArray();
        for (String t : d.trials) {
            trials.add(com.google.gson.JsonParser.parseString(t));
        }
        out.add("trials", trials);
        // "Your Story": the end card
        out.addProperty("storyOver", over);
        out.add("story", EndCard.compute(player, traits));
        // other players on the server
        out.add("others", Rivalry.info(player));
        PacketDistributor.sendToPlayer(player, new Payloads.MapState(out.toString()));
    }
}

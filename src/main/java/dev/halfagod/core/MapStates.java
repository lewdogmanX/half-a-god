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
        if (Fates.storyOver(Meb.traits(player))) {
            out.add("fates", Fates.compute(player));
        }
        PacketDistributor.sendToPlayer(player, new Payloads.MapState(out.toString()));
    }
}

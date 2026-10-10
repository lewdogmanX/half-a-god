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
        JsonObject out = new JsonObject();
        out.add("states", states);
        out.addProperty("rep", Reputation.data(player).value);
        out.addProperty("op", player.hasPermissions(2));
        PacketDistributor.sendToPlayer(player, new Payloads.MapState(out.toString()));
    }
}

package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Big choices with three or four answers. A choice opens when its trigger trait appears (the quest that leads up
 * to it is finished); each answer adds a trait, moves reputation, and starts that answer's quest.
 */
public final class Choices {
    private static final Map<UUID, Map<String, Long>> LAST_OFFER = new HashMap<>();

    private Choices() {
    }

    private static JsonObject find(String id) {
        for (JsonObject c : StoryData.INSTANCE.choices) {
            if (c.get("id").getAsString().equals(id)) {
                return c;
            }
        }
        return null;
    }

    private static boolean pending(ServerPlayer player, JsonObject c) {
        if (!Meb.hasTrait(player, c.get("trigger").getAsString())) {
            return false;
        }
        if (c.has("requires")) {
            for (JsonElement t : c.getAsJsonArray("requires")) {
                if (!Meb.hasTrait(player, t.getAsString())) {
                    return false;
                }
            }
        }
        if (c.has("forbids")) {
            for (JsonElement t : c.getAsJsonArray("forbids")) {
                if (Meb.hasTrait(player, t.getAsString())) {
                    return false;
                }
            }
        }
        for (JsonElement o : c.getAsJsonArray("options")) {
            if (Meb.hasTrait(player, o.getAsJsonObject().get("trait").getAsString())) {
                return false;
            }
        }
        return true;
    }

    public static void onTrait(ServerPlayer player, String trait) {
        for (JsonObject c : StoryData.INSTANCE.choices) {
            if (c.get("trigger").getAsString().equals(trait) && pending(player, c)) {
                open(player, c);
            }
        }
    }

    /** Re-offers any choice the player closed without answering (every two minutes, or now if forced). */
    public static int offerPending(ServerPlayer player, boolean force) {
        int n = 0;
        long now = player.level().getGameTime();
        Map<String, Long> last = LAST_OFFER.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        for (JsonObject c : StoryData.INSTANCE.choices) {
            if (!pending(player, c)) {
                continue;
            }
            String id = c.get("id").getAsString();
            if (force || now - last.getOrDefault(id, -100000L) > 2400) {
                open(player, c);
                n++;
            }
        }
        return n;
    }

    private static void open(ServerPlayer player, JsonObject c) {
        LAST_OFFER.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(c.get("id").getAsString(), player.level().getGameTime());
        PacketDistributor.sendToPlayer(player, new Payloads.OpenChoice(c.toString()));
    }

    public static void pick(ServerPlayer player, String id, int option) {
        JsonObject c = find(id);
        if (c == null || !pending(player, c)) {
            return;
        }
        JsonArray options = c.getAsJsonArray("options");
        if (option < 0 || option >= options.size()) {
            return;
        }
        JsonObject o = options.get(option).getAsJsonObject();
        Meb.addTrait(player, o.get("trait").getAsString());
        if (o.has("line")) {
            // the answer, said once above the hotbar (not in chat)
            player.displayClientMessage(Component.literal(c.get("speaker").getAsString() + ": ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(o.get("line").getAsString()).withStyle(ChatFormatting.WHITE)), true);
        }
        if (o.has("rep")) {
            Reputation.change(player, o.get("rep").getAsDouble(), o.get("label").getAsString());
        }
        // the end card remembers your biggest decisions
        Reputation.data(player).bigChoices.add((c.has("title") ? c.get("title").getAsString() : "A choice") + "|"
                + o.get("label").getAsString() + "|" + (o.has("rep") ? o.get("rep").getAsDouble() : 0));
        if (o.has("chronicle")) {
            Reputation.data(player).chronicle.add(o.get("chronicle").getAsString());
        }
        if (o.has("courier")) {
            // the consequence arrives later, by courier (the delay lives in the courier file)
            Meb.triggerCourier(player, o.get("courier").getAsString(), id);
        }
        if (o.has("start")) {
            for (JsonElement q : o.getAsJsonArray("start")) {
                String quest = q.getAsString();
                StoryData.Graph g = StoryData.INSTANCE.graph.get(quest);
                if (g != null && g.book()) {
                    Meb.giveBook(player, quest);
                } else {
                    Meb.startQuest(player, ResourceLocation.parse(quest), false);
                }
            }
        }
    }
}

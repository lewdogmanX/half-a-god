package dev.halfagod.core;

import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dreams. When you wake from a full night's sleep (the night really passed, not you getting up), about one night in
 * four you dream: never two nights running, never the same dream twice. The god's past comes in order; omens belong to
 * the act you're in; echoes come back from choices you made.
 *
 * Waking is also when the Watch comes for the guilty (the Trial), and when prisoners count off their sentence.
 */
public final class Dreams {
    private static final float CHANCE = 0.25f;
    public static final ResourceLocation AETHER = ResourceLocation.fromNamespaceAndPath("aether", "the_aether");

    private Dreams() {
    }

    /** Which act the player is in: the index of the last act that has begun, and its trait (or -1 / ""). */
    public static int actIndex(Set<String> traits) {
        int i = 0, best = -1;
        for (String t : StoryData.INSTANCE.acts.keySet()) {
            if (traits.contains(t)) {
                best = i;
            }
            i++;
        }
        return best;
    }

    public static String actTrait(Set<String> traits) {
        String best = "";
        for (String t : StoryData.INSTANCE.acts.keySet()) {
            if (traits.contains(t)) {
                best = t;
            }
        }
        return best;
    }

    public static long night(ServerPlayer player) {
        return player.level().getDayTime() / 24000L;
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        // a whole night slept through: the level woke everyone up (getting out of bed yourself doesn't count)
        if (!(event.getEntity() instanceof ServerPlayer player) || event.wakeImmediately() || event.updateLevel()) {
            return;
        }
        RepData d = Reputation.data(player);
        if (Trial.inCourt(player)) {
            Trial.onNightInCell(player);
            return;
        }
        if (Trial.onWake(player)) {
            return; // the Watch came for you instead
        }
        long night = night(player);
        boolean aether = player.level().dimension().location().equals(AETHER);
        // the hidden questline "What the God Saw": five dreams, then a night in the Aether
        if (aether && d.dreams.size() >= 5 && Secrets.notFound(player, "godsaw")) {
            JsonObject fall = StoryData.INSTANCE.specialDreams.get("godsaw");
            if (fall != null) {
                dream(player, fall);
            }
            Secrets.find(player, "godsaw", 160);
            return;
        }
        if (night - d.lastDreamNight <= 1 || player.getRandom().nextFloat() >= CHANCE) {
            return;
        }
        JsonObject dream = pick(player, d);
        if (dream != null) {
            dream(player, dream);
        }
    }

    /** Shows a dream and remembers it. */
    public static void dream(ServerPlayer player, JsonObject dream) {
        RepData d = Reputation.data(player);
        d.lastDreamNight = night(player);
        d.dreams.add(dream.get("id").getAsString() + "|" + (player.level().getDayTime() / 24000L + 1));
        send(player, dream);
    }

    public static void send(ServerPlayer player, JsonObject dream) {
        if (dream != null) {
            PacketDistributor.sendToPlayer(player, new Payloads.Dream(dream.toString()));
        }
    }

    public static Set<String> seen(RepData d) {
        Set<String> out = new HashSet<>();
        for (String s : d.dreams) {
            out.add(s.split("\\|")[0]);
        }
        return out;
    }

    /** An echo, an omen or the next piece of the god's past, whichever there is (echoes most likely). */
    public static JsonObject pick(ServerPlayer player, RepData d) {
        Set<String> seen = seen(d);
        Set<String> traits = Meb.traits(player);
        String act = actTrait(traits);
        List<JsonObject> echoes = new ArrayList<>(), omens = new ArrayList<>();
        JsonObject past = null;
        for (JsonObject dream : StoryData.INSTANCE.dreams) {
            if (seen.contains(dream.get("id").getAsString())) {
                continue;
            }
            switch (dream.get("kind").getAsString()) {
                case "echo" -> {
                    if (traits.contains(dream.get("trait").getAsString())) {
                        echoes.add(dream);
                    }
                }
                case "omen" -> {
                    if (dream.get("act").getAsString().equals(act)) {
                        omens.add(dream);
                    }
                }
                case "past" -> {
                    if (past == null || dream.get("order").getAsInt() < past.get("order").getAsInt()) {
                        past = dream;
                    }
                }
                default -> {
                }
            }
        }
        // the god's past only starts once the story has (after the first letter)
        if (act.isEmpty()) {
            past = null;
        }
        float r = player.getRandom().nextFloat();
        if (!echoes.isEmpty() && (r < 0.4f || (omens.isEmpty() && past == null))) {
            return echoes.get(player.getRandom().nextInt(echoes.size()));
        }
        if (!omens.isEmpty() && (r < 0.7f || past == null)) {
            return omens.get(player.getRandom().nextInt(omens.size()));
        }
        if (past != null) {
            return past;
        }
        return echoes.isEmpty() ? null : echoes.get(player.getRandom().nextInt(echoes.size()));
    }

    public static JsonObject byId(String id) {
        for (JsonObject dream : StoryData.INSTANCE.dreams) {
            if (dream.get("id").getAsString().equals(id)) {
                return dream;
            }
        }
        return StoryData.INSTANCE.specialDreams.get(id);
    }
}

package dev.halfagod.core;

import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;

/**
 * Watches Mebahel's story traits and reacts: reputation, the Chronicle, cinematic moments,
 * companion comments, big choices and the end-of-story Chronicle book.
 */
public final class Story {
    private Story() {
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int t = player.tickCount;
        Trial.tick(player);
        if (t % 40 == 7) {
            poll(player);
        }
        if (Trial.inCourt(player)) {
            return; // nothing else happens to you in the Watch's court
        }
        if (t % 20 == 3) {
            Cast.tick(player);
        }
        if (t % 20 == 11) {
            Secrets.tick(player);
        }
        if (t % 100 == 41) {
            Ranks.update(player);
            Encounters.tick(player);
            Atlas.tick(player);
            Morality.tick(player);
            Settlement.tick(player);
            Duties.tick(player);
        }
        if (t % 100 == 13) {
            Reputation.tickVillagers(player);
            Meb.repairStages(player);
            StoryGuard.restartAbandoned(player, false);
            Choices.offerPending(player, false);
            BaseLines.tick(player);
            Arcs.tick(player);
        }
    }

    public static double repFor(String trait) {
        StoryData data = StoryData.INSTANCE;
        Double exact = data.repTraits.get(trait);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Double> e : data.repPrefixes.entrySet()) {
            if (trait.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return 0;
    }

    public static void poll(ServerPlayer player) {
        RepData d = Reputation.data(player);
        Set<String> traits = Meb.traits(player);
        if (!d.initialised) {
            // First time this save meets the mod: catch up quietly, no fireworks for old events.
            for (String t : traits) {
                d.value += repFor(t);
                recordChronicle(d, t);
            }
            d.value = Math.max(-100, Math.min(100, d.value));
            d.knownTraits.addAll(traits);
            d.initialised = true;
            Reputation.sync(player, 0);
            Reputation.updateTierFlags(player);
            return;
        }
        for (String t : traits) {
            if (d.knownTraits.add(t)) {
                onNewTrait(player, d, t);
            }
        }
        Reputation.countMebahelChoices(player);
        Secrets.checkClean(player, traits);
        Letters.keepBooks(player);
    }

    private static void recordChronicle(RepData d, String trait) {
        StoryData data = StoryData.INSTANCE;
        String act = data.chronicleActs.get(trait);
        if (act != null) {
            d.path.add("#" + act);
            d.chronicle.add("#" + act);
            d.actRep.put(act, (int) Math.round(d.value));
        }
        String line = data.chronicleLines.get(trait);
        if (line != null) {
            d.chronicle.add(line);
        }
    }

    private static void onNewTrait(ServerPlayer player, RepData d, String trait) {
        double rep = repFor(trait);
        if (rep != 0) {
            Reputation.change(player, rep, rep > 0 ? "an act of mercy" : "an act of cruelty");
        }
        recordChronicle(d, trait);

        JsonObject moment = StoryData.INSTANCE.cinematics.get(trait);
        if (moment != null) {
            Net.send(player, new Payloads.Cinematic(
                    str(moment, "title"), str(moment, "subtitle"),
                    moment.has("color") ? Integer.parseInt(moment.get("color").getAsString().replace("#", ""), 16) : 0xFFD27A,
                    str(moment, "sound"),
                    moment.has("shake") ? moment.get("shake").getAsInt() : 0,
                    moment.has("strength") ? moment.get("strength").getAsFloat() : 1f));
        }

        Map<String, String> lines = StoryData.INSTANCE.reactions.get(trait);
        if (lines != null) {
            companionsReact(player, lines);
        }

        if (trait.startsWith("hag_") && trait.endsWith("_done") && !trait.startsWith("hag_base_")) {
            // the margin note on the map is written in the mood you were in when you finished it
            d.doneRep.put("mebahelrpgrquest:" + trait.substring(0, trait.length() - "_done".length()), (int) Math.round(d.value));
            Morality.onStoryQuestDone(player);
            d.path.add(Long.toString(Math.round(d.value)));
            while (d.path.size() > 800) {
                d.path.remove(0);
            }
        }
        if (StoryData.INSTANCE.momentTraits.contains(trait)) {
            d.moments.put(trait, player.level().getDayTime() / 24000L + 1);
        }
        Choices.onTrait(player, trait);
        Secrets.onTrait(player, trait);
        Words.onTrait(player, trait);
        Letters.onTrait(player, trait);
        Arcs.onTrait(player, trait);
        Trade.onTrait(player, trait);
        Moon.onTrait(player, trait);

        if (trait.startsWith("hag_rep_") && !d.chronicleGiven) {
            Chronicle.give(player, true);
        }
        // the story is over: show what became of everyone
        if (!d.fatesShown && Fates.storyOver(Meb.traits(player))) {
            d.fatesShown = true;
            d.endDay = player.level().getDayTime() / 24000L + 1;
            Net.send(player, new Payloads.OpenMap("story"));
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    /** A companion standing nearby says their line for this moment. */
    private static void companionsReact(ServerPlayer player, Map<String, String> lines) {
        for (Entity e : player.level().getEntities(player, new AABB(player.blockPosition()).inflate(48))) {
            if (!e.hasCustomName() || e.getCustomName() == null) {
                continue;
            }
            String name = e.getCustomName().getString();
            for (String companion : StoryData.INSTANCE.companionNames) {
                if (name.contains(companion) && lines.containsKey(companion)) {
                    player.displayClientMessage(Component.literal(companion + ": ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(lines.get(companion)).withStyle(ChatFormatting.WHITE)), true);
                    return;
                }
            }
        }
    }
}

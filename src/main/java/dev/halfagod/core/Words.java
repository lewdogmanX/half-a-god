package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Three ways what you've done follows you around:
 *  - your reputation travels ahead of you: villagers who've never met you have heard of you (their prices move, through
 *    vanilla's own gossip), and the first time you walk into a village you hear about it;
 *  - confession: sneak and use a cleric to confess something the Watch could try you for; it still comes up at your
 *    trial, but weighs less (once a day);
 *  - interrogations: when you spare one of the story's enemies, you can ask what they know before they go.
 */
public final class Words {
    private static final String HEARD = "hag_heard";
    private static final String[] TIERS = {"wicked", "fallen", "waver", "good", "saint"};
    private static final Map<UUID, String> ASK = new HashMap<>();
    private static final Map<UUID, Long> ASK_AT = new HashMap<>();

    private Words() {
    }

    private static JsonObject words(String section) {
        JsonObject w = StoryData.INSTANCE.table("words");
        return w.has(section) ? w.getAsJsonObject(section) : new JsonObject();
    }

    private static void say(ServerPlayer p, String s, ChatFormatting c) {
        if (s != null && !s.isEmpty()) {
            p.displayClientMessage(Component.literal(s).withStyle(c), true);
        }
    }

    private static long day(ServerPlayer p) {
        return p.serverLevel().getDayTime() / 24000L + 1;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 100 != 37) {
            return;
        }
        if (player.level().dimension() == Level.OVERWORLD) {
            ahead(player);
        }
        Long at = ASK_AT.get(player.getUUID());
        if (at != null && player.getServer().getTickCount() >= at) {
            ASK_AT.remove(player.getUUID());
            String id = ASK.remove(player.getUUID());
            if (id != null) {
                interrogate(player, id);
            }
        }
    }

    // ------------------------------------------------------------------ your reputation, ahead of you
    public static void ahead(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RepData d = Reputation.data(player);
        if (d.trialState != 0) {
            return;
        }
        String tier = TIERS[Reputation.tierIndex(d.value)];
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), player.blockPosition(), 48, PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) {
            return;
        }
        // the first time you walk into a village, you hear what it's heard
        if (d.villagesHeard.add(bell.get().asLong())) {
            while (d.villagesHeard.size() > 256) {
                d.villagesHeard.remove(d.villagesHeard.iterator().next());
            }
            JsonObject a = words("ahead");
            if (a.has(tier)) {
                say(player, a.get(tier).getAsString(), Reputation.tierIndex(d.value) >= 3 ? ChatFormatting.GREEN
                        : Reputation.tierIndex(d.value) == 2 ? ChatFormatting.GRAY : ChatFormatting.RED);
            }
        }
        // and every villager there has heard the same: vanilla gossip, which moves their prices
        int want = gossipFor(tier);
        for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48), v -> !CourtBuilder.isCourtNpc(v))) {
            rumour(v, player.getUUID(), want);
        }
    }

    public static int gossipFor(String tier) {
        JsonObject g = StoryData.INSTANCE.table("words").has("ahead_gossip") ? StoryData.INSTANCE.table("words").getAsJsonObject("ahead_gossip") : new JsonObject();
        return g.has(tier) ? g.get(tier).getAsInt() : 0;
    }

    /** sets how much of your reputation this villager has heard (undoing what it heard before) */
    public static void rumour(Villager v, UUID player, int want) {
        CompoundTag heard = v.getPersistentData().getCompound(HEARD);
        String key = player.toString();
        int had = heard.contains(key) ? heard.getInt(key) : 0;
        if (heard.contains(key) && had == want) {
            return;
        }
        gossip(v, player, had, false);
        gossip(v, player, want, true);
        heard.putInt(key, want);
        v.getPersistentData().put(HEARD, heard);
    }

    /** vanilla caps minor good gossip at 25, so anything above that is told as major good gossip (5 a time) */
    private static void gossip(Villager v, UUID player, int amount, boolean add) {
        if (amount < 0) {
            if (add) {
                v.getGossips().add(player, GossipType.MINOR_NEGATIVE, -amount);
            } else {
                v.getGossips().remove(player, GossipType.MINOR_NEGATIVE, -amount);
            }
        } else if (amount > 0) {
            int minor = Math.min(25, amount);
            int major = (amount - minor) / 5;
            if (add) {
                v.getGossips().add(player, GossipType.MINOR_POSITIVE, minor);
                if (major > 0) {
                    v.getGossips().add(player, GossipType.MAJOR_POSITIVE, major);
                }
            } else {
                v.getGossips().remove(player, GossipType.MINOR_POSITIVE, minor);
                if (major > 0) {
                    v.getGossips().remove(player, GossipType.MAJOR_POSITIVE, major);
                }
            }
        }
    }

    // ------------------------------------------------------------------ confession
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager v) || !player.isShiftKeyDown()
                || CourtBuilder.isCourtNpc(v) || v.getVillagerData().getProfession() != VillagerProfession.CLERIC || v.isBaby()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            Talk.speaking(player, v);
            confession(player);
        }
    }

    /** the things the Watch could try you for that you haven't confessed: id -> what you'd say */
    public static Map<String, String> sins(ServerPlayer player) {
        RepData d = Reputation.data(player);
        JsonObject c = words("confession");
        Set<String> traits = Meb.traits(player);
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (JsonObject ch : StoryData.INSTANCE.charges) {
            String id = ch.get("id").getAsString();
            if (traits.contains(ch.get("trait").getAsString()) && !d.confessed.contains(id)) {
                String t = ch.get("testimony").getAsString();
                int cut = t.indexOf('.');
                out.put(id, "What " + ch.get("witness").getAsString() + " would say: \"" + (cut > 0 ? t.substring(0, cut + 1) : t) + "\"");
            }
        }
        int villagers = player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
        if (villagers > 0 && !d.confessed.contains("villagers") && c.has("villagers")) {
            out.put("villagers", c.get("villagers").getAsString().replace("{n}", Integer.toString(villagers)));
        }
        if (d.looted.size() >= 3 && !d.confessed.contains("stores") && c.has("stores")) {
            out.put("stores", c.get("stores").getAsString());
        }
        if (d.contractsDone >= 3 && !d.confessed.contains("hand") && c.has("hand")) {
            out.put("hand", c.get("hand").getAsString());
        }
        if (d.shrinePrayers > 0 && !d.confessed.contains("shrines") && c.has("shrines")) {
            out.put("shrines", c.get("shrines").getAsString());
        }
        // the cult's work, the street trade, and fighting the Watch
        int[] counts = {d.conversions, d.cultWork, d.dealt, d.resisted};
        String[] keys = {"converted", "cult", "dealing", "resisted"};
        int[] least = {1, 3, 3, 1};
        for (int i = 0; i < keys.length; i++) {
            if (counts[i] >= least[i] && !d.confessed.contains(keys[i]) && c.has(keys[i])) {
                out.put(keys[i], c.get(keys[i]).getAsString().replace("{n}", Integer.toString(counts[i])));
            }
        }
        return out;
    }

    public static void confession(ServerPlayer player) {
        RepData d = Reputation.data(player);
        JsonObject c = words("confession");
        Talk talk = Talk.to(player, "The Cleric").title(c.has("title") ? c.get("title").getAsString() : "Confession").style("church");
        if (d.lastConfession == day(player)) {
            talk.line(c.get("today").getAsString()).send();
            return;
        }
        Map<String, String> sins = sins(player);
        if (sins.isEmpty()) {
            talk.line(c.get("none").getAsString()).send();
            return;
        }
        talk.line(c.get("open").getAsString());
        int n = 0;
        for (Map.Entry<String, String> s : sins.entrySet()) {
            if (n++ >= 5) {
                break;
            }
            String id = s.getKey();
            String label = s.getValue().length() > 60 ? s.getValue().substring(0, 57) + "..." : s.getValue();
            talk.option(id, label, s.getValue(), p -> confess(p, id));
        }
        talk.option("leave", c.get("leave").getAsString(), p -> {
        });
        talk.send();
    }

    public static void confess(ServerPlayer player, String id) {
        RepData d = Reputation.data(player);
        d.confessed.add(id);
        d.lastConfession = day(player);
        JsonObject c = words("confession");
        List<String> after = new ArrayList<>();
        c.getAsJsonArray("after").forEach(e -> after.add(e.getAsString()));
        Talk.to(player, "The Cleric").title(c.get("title").getAsString()).style("church").lines(after).send();
    }

    // ------------------------------------------------------------------ interrogations
    /** a story trait has just been set: if it means you spared someone who knows things, ask them shortly */
    public static void onTrait(ServerPlayer player, String trait) {
        RepData d = Reputation.data(player);
        JsonObject t = StoryData.INSTANCE.table("interrogations");
        if (!t.has("people")) {
            return;
        }
        for (JsonElement e : t.getAsJsonArray("people")) {
            JsonObject p = e.getAsJsonObject();
            if (p.get("trigger").getAsString().equals(trait) && !d.interrogated.contains(p.get("id").getAsString())) {
                ASK.put(player.getUUID(), p.get("id").getAsString());
                // after Mebahel's own dialogue has closed
                ASK_AT.put(player.getUUID(), player.getServer().getTickCount() + 100L);
            }
        }
    }

    private static JsonObject person(String id) {
        JsonObject t = StoryData.INSTANCE.table("interrogations");
        if (t.has("people")) {
            for (JsonElement e : t.getAsJsonArray("people")) {
                if (e.getAsJsonObject().get("id").getAsString().equals(id)) {
                    return e.getAsJsonObject();
                }
            }
        }
        return null;
    }

    public static void interrogate(ServerPlayer player, String id) {
        JsonObject p = person(id);
        RepData d = Reputation.data(player);
        if (p == null || d.interrogated.contains(id)) {
            return;
        }
        // the one you spared is still standing there: the talk screen turns to them
        String who = p.get("name").getAsString();
        for (net.minecraft.world.entity.LivingEntity e : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(32), e -> e != player && e.isAlive() && e.hasCustomName() && e.getCustomName().getString().equals(who))) {
            Talk.speaking(player, e);
            break;
        }
        JsonObject ap = StoryData.INSTANCE.table("interrogations").getAsJsonObject("approach");
        Talk talk = Talk.to(player, p.get("name").getAsString()).title("Before they go").line(p.get("intro").getAsString());
        for (String a : new String[]{"threaten", "bribe", "reason", "leave"}) {
            JsonObject o = ap.getAsJsonObject(a);
            talk.option(a, o.get("label").getAsString(), o.get("hint").getAsString(), pl -> ask(pl, id, a));
        }
        talk.send();
    }

    /** one try at getting it out of them. True if they talked. */
    public static boolean ask(ServerPlayer player, String id, String how) {
        JsonObject p = person(id);
        RepData d = Reputation.data(player);
        if (p == null || d.interrogated.contains(id)) {
            return false;
        }
        if (how.equals("leave")) {
            d.interrogated.add(id);
            return false;
        }
        int tier = Reputation.tierIndex(d.value);
        boolean talked;
        switch (how) {
            case "bribe" -> {
                if (player.getInventory().countItem(Items.EMERALD) < 5) {
                    say(player, "You don't have five emeralds on you.", ChatFormatting.RED);
                    interrogate(player, id);
                    return false;
                }
                take(player, 5);
                talked = true;
            }
            case "threaten" -> {
                Reputation.change(player, -3, "you threatened a prisoner");
                talked = tier <= 2 || player.getRandom().nextFloat() < 0.4f;
            }
            default -> {
                Reputation.change(player, 1, "");
                talked = tier >= 2 || player.getRandom().nextFloat() < 0.4f;
            }
        }
        d.interrogated.add(id);
        Talk talk = Talk.to(player, p.get("name").getAsString()).title("Before they go");
        if (talked) {
            talk.line(p.getAsJsonObject("yes").get(how).getAsString()).line(p.get("intel").getAsString())
                    .line("(Written in your Codex, under Letters: \"" + p.get("title").getAsString() + "\".)");
            JsonObject note = new JsonObject();
            note.addProperty("title", p.get("title").getAsString());
            note.addProperty("author", "Your notes");
            JsonArray pages = new JsonArray();
            pages.add(p.get("intel").getAsString());
            note.add("pages", pages);
            note.addProperty("day", day(player));
            d.letters.add(note.toString());
        } else {
            talk.line(p.getAsJsonObject("no").get(how).getAsString());
        }
        talk.send();
        return talked;
    }

    private static void take(ServerPlayer player, int n) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.EMERALD)) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                n -= k;
            }
        }
        player.inventoryMenu.broadcastChanges();
    }
}

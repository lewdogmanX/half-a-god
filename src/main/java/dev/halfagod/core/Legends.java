package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Legends: your name, in other people's mouths. The world remembers every player's standing (even when they're not
 * online), and villagers use the names that mean something:
 *
 * - when a player with a dark name hurts a villager, the villagers around call on the most famous hero in the world by
 *   name ("Lewis will put you in the dirt") and, if that hero is online elsewhere, they hear their name being called;
 * - when a feared player walks into a village, people whisper their name;
 * - when a famous hero walks in, people say theirs.
 *
 * Everything is said on the action bar to the players close enough to hear it (never in chat).
 */
public final class Legends {
    private static final Map<UUID, Long> SAID = new HashMap<>();
    private static final Map<String, Long> VILLAGE_SAID = new HashMap<>();

    private Legends() {
    }

    // ------------------------------------------------------------------ the world's memory of every name
    public static final class Book extends SavedData {
        final Map<UUID, String> names = new HashMap<>();
        final Map<UUID, Double> values = new HashMap<>();

        static Book load(CompoundTag tag, HolderLookup.Provider p) {
            Book b = new Book();
            CompoundTag all = tag.getCompound("players");
            for (String k : all.getAllKeys()) {
                try {
                    UUID id = UUID.fromString(k);
                    CompoundTag e = all.getCompound(k);
                    b.names.put(id, e.getString("name"));
                    b.values.put(id, e.getDouble("value"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            CompoundTag all = new CompoundTag();
            names.forEach((id, n) -> {
                CompoundTag e = new CompoundTag();
                e.putString("name", n);
                e.putDouble("value", values.getOrDefault(id, 0.0));
                all.put(id.toString(), e);
            });
            tag.put("players", all);
            return tag;
        }
    }

    private static Book book(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Book::new, Book::load, null), "half_a_god_legends");
    }

    private static void remember(ServerPlayer p) {
        Book b = book(p.getServer());
        double v = Reputation.data(p).value;
        String n = p.getName().getString();
        if (!n.equals(b.names.get(p.getUUID())) || !Double.valueOf(v).equals(b.values.get(p.getUUID()))) {
            b.names.put(p.getUUID(), n);
            b.values.put(p.getUUID(), v);
            b.setDirty();
        }
    }

    /** the most famous hero in the world (Good or Saint), other than this player; null if there isn't one */
    private static UUID hero(MinecraftServer server, UUID not) {
        Book b = book(server);
        UUID best = null;
        double bestV = 0;
        for (Map.Entry<UUID, Double> e : b.values.entrySet()) {
            if (!e.getKey().equals(not) && Reputation.tierIndex(e.getValue()) >= 3 && (best == null || e.getValue() > bestV)) {
                best = e.getKey();
                bestV = e.getValue();
            }
        }
        return best;
    }

    private static JsonObject t() {
        return StoryData.INSTANCE.table("legends");
    }

    private static String pick(String key, ServerPlayer p) {
        JsonArray a = t().has(key) ? t().getAsJsonArray(key) : new JsonArray();
        return a.isEmpty() ? "" : a.get(p.getRandom().nextInt(a.size())).getAsString();
    }

    /** a villager says it, to everyone near enough to hear */
    private static void shout(ServerLevel level, BlockPos at, String who, String line, ChatFormatting color) {
        if (line.isEmpty()) {
            return;
        }
        Component c = Component.literal(who + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal("\"" + line + "\"").withStyle(color));
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(at, 40)) {
                p.displayClientMessage(c, true);
            }
        }
    }

    private static String who(Villager v) {
        if (v.hasCustomName()) {
            return v.getCustomName().getString();
        }
        String job = v.getVillagerData().getProfession().name();
        if (job.equals("none") || job.equals("nitwit")) {
            return v.isBaby() ? "A child" : "A villager";
        }
        return "The " + Character.toUpperCase(job.charAt(0)) + job.substring(1);
    }

    // ------------------------------------------------------------------ "they'll put you in the dirt"
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Villager v) || CourtBuilder.isCourtNpc(v) || v.level().isClientSide()
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (Reputation.tierIndex(Reputation.data(attacker).value) > 1) {
            return;
        }
        long now = attacker.getServer().getTickCount();
        Long last = SAID.get(attacker.getUUID());
        if (last != null && now - last < 400) {
            return;
        }
        SAID.put(attacker.getUUID(), now);
        MinecraftServer server = attacker.getServer();
        UUID h = hero(server, attacker.getUUID());
        String name = attacker.getName().getString();
        String line;
        if (h != null) {
            String heroName = book(server).names.get(h);
            line = pick("threat", attacker).replace("{hero}", heroName).replace("{name}", name);
            ServerPlayer heroOnline = server.getPlayerList().getPlayer(h);
            if (heroOnline != null && (heroOnline.level() != attacker.level() || heroOnline.distanceTo(attacker) > 64)) {
                heroOnline.displayClientMessage(Component.literal(pick("called", attacker).replace("{name}", name)).withStyle(ChatFormatting.GOLD), true);
            }
        } else {
            line = pick("threat_nobody", attacker).replace("{name}", name);
        }
        shout((ServerLevel) v.level(), v.blockPosition(), who(v), line, ChatFormatting.RED);
    }

    // ------------------------------------------------------------------ the rough ones, shouting as they come
    /** a named story enemy (a raider, a Blackwater crew) sets its sights on a player: it says so, once */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer p) || !event.getEntity().hasCustomName()
                || event.getEntity().getTags().contains("hag_barked")) {
            return;
        }
        JsonObject b = StoryData.INSTANCE.table("barks");
        JsonObject all = b.has("barks") ? b.getAsJsonObject("barks") : new JsonObject();
        String name = event.getEntity().getCustomName().getString();
        if (all.has(name)) {
            event.getEntity().addTag("hag_barked");
            p.displayClientMessage(Component.literal(name + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\"" + all.get(name).getAsString() + "\"").withStyle(ChatFormatting.RED)), true);
        }
    }

    // ------------------------------------------------------------------ whispers and cheers as you walk in
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 100 != 53) {
            return;
        }
        remember(p);
        RepData d = Reputation.data(p);
        int tier = Reputation.tierIndex(d.value);
        if (d.trialState != 0 || (tier > 0 && tier < 4) || p.isSpectator()) {
            return; // only the Wicked and the Saints are names people say out loud
        }
        ServerLevel level = p.serverLevel();
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), p.blockPosition(), 40, PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) {
            return;
        }
        String key = p.getUUID() + "|" + bell.get().asLong();
        long now = p.getServer().getTickCount();
        Long last = VILLAGE_SAID.get(key);
        if (last != null && now - last < 20 * 60 * 10) {
            return;
        }
        Villager v = level.getNearestEntity(Villager.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat(), p,
                p.getX(), p.getY(), p.getZ(), p.getBoundingBox().inflate(24));
        if (v == null || CourtBuilder.isCourtNpc(v)) {
            return;
        }
        VILLAGE_SAID.put(key, now);
        String line = pick(tier == 0 ? "whisper" : "cheer", p).replace("{name}", p.getName().getString());
        shout(level, v.blockPosition(), who(v), line, tier == 0 ? ChatFormatting.DARK_RED : ChatFormatting.GREEN);
    }
}

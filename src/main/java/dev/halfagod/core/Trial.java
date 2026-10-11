package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.HalfAGod;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Trial. From Act II on, if you're Fallen or Wicked, the Watch can take you in your sleep (once per act, on a random
 * night). You wake in chains in a court of its own, hear witnesses tell what you really did (the ones you killed can't),
 * and plead. Guilty means the obsidian cells: sleep there to pass the days, find the block that isn't quite right, or,
 * if you're high enough in the Gilded Hand, wait for Silas's men. Either way you wake back home, in your own bed, with
 * your things.
 *
 * States (RepData.trialState): 0 free, 1 on trial (in the dock), 2 in prison.
 */
public final class Trial {
    public static final ResourceKey<Level> COURT = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "court"));
    private static final float CHANCE = 1f / 3f;
    private static final int BRIBE = 16;

    /** server tick at which to do something for a player: show the court, take them away after the verdict, show a dream */
    private static final Map<UUID, Long> SHOW_AT = new HashMap<>();
    private static final Map<UUID, Long> MOVE_AT = new HashMap<>();
    private static final Map<UUID, Long> DREAM_AT = new HashMap<>();
    private static final Map<UUID, String> DREAM = new HashMap<>();
    private static final Set<UUID> ESCAPING = new HashSet<>();
    private static final Map<UUID, Long> AWAY_SINCE = new HashMap<>();
    private static boolean warned;

    private Trial() {
    }

    public static ServerLevel court(MinecraftServer server) {
        ServerLevel l = server.getLevel(COURT);
        if (l == null && !warned) {
            warned = true;
            HalfAGod.LOG.warn("Half a God: the court dimension (half_a_god:court) isn't loaded, so there will be no trials.");
        }
        return l;
    }

    public static boolean inCourt(ServerPlayer player) {
        return player.level().dimension() == COURT;
    }

    private static long now(ServerPlayer player) {
        return player.getServer().getTickCount();
    }

    // ------------------------------------------------------------------ when the Watch comes
    private static boolean due(ServerPlayer player, RepData d) {
        if (d.trialState != 0 || d.pendingKidnap || player.isCreative() || player.isSpectator()) {
            return false;
        }
        Set<String> traits = Meb.traits(player);
        if (Dreams.actIndex(traits) < 2 || Reputation.tierIndex(d.value) > 1 || Fates.storyOver(traits)) {
            return false;
        }
        return !d.trialActs.contains(Dreams.actTrait(traits)) && court(player.getServer()) != null;
    }

    /** On waking from a full night: maybe the Watch came. True if they did (no dream tonight). */
    public static boolean onWake(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (!due(player, d) || player.getRandom().nextFloat() >= CHANCE) {
            return false;
        }
        d.pendingKidnap = true;
        return true;
    }

    /** Takes the player to court now. */
    public static boolean kidnap(ServerPlayer player) {
        RepData d = Reputation.data(player);
        d.pendingKidnap = false;
        ServerLevel court = court(player.getServer());
        if (court == null || d.trialState != 0) {
            return false;
        }
        Set<String> traits = Meb.traits(player);
        String act = Dreams.actTrait(traits);
        d.trialActs.add(act.isEmpty() ? "none" : act);
        int slot = CourtData.get(court).slot(player.getUUID());
        CourtBuilder.ensure(court, slot);
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        saveSpawn(player, d);
        confiscate(player, d);
        d.trialState = 1;
        d.trialVerdict = "";
        d.trialScript = script(player, d, traits).toString();
        BlockPos dock = CourtBuilder.origin(slot).offset(CourtBuilder.DOCK);
        player.teleportTo(court, dock.getX() + 0.5, dock.getY(), dock.getZ() + 0.5, 180f, 0f);
        player.setRespawnPosition(COURT, dock, 180f, true, false);
        player.clearFire();
        SHOW_AT.put(player.getUUID(), now(player) + 60);
        return true;
    }

    // ------------------------------------------------------------------ the court's script
    private static String fill(String s, String key, String value) {
        return s.replace("{" + key + "}", value);
    }

    private static JsonArray line(String speaker, String text) {
        JsonArray a = new JsonArray();
        a.add(speaker);
        a.add(text);
        return a;
    }

    private static JsonArray line(JsonElement scriptLine, String key, String value) {
        JsonArray src = scriptLine.getAsJsonArray();
        return line(src.get(0).getAsString(), fill(src.get(1).getAsString(), key, value));
    }

    private record Charge(int weight, JsonArray line, boolean heard) {
    }

    public static JsonObject script(ServerPlayer player, RepData d, Set<String> traits) {
        JsonObject s = StoryData.INSTANCE.trialScript;
        JsonObject out = new JsonObject();
        JsonObject kidnap = StoryData.INSTANCE.specialDreams.get("kidnap");
        out.add("kidnap", kidnap != null ? kidnap.getAsJsonArray("lines") : new JsonArray());
        JsonArray lines = new JsonArray();
        if (s.has("open")) {
            s.getAsJsonArray("open").forEach(l -> lines.add(l.deepCopy()));
        }
        if (d.convictions > 0) {
            lines.add(line("The Watch Captain", "The accused has stood in this dock before, your honour. " + d.convictions
                    + (d.convictions == 1 ? " conviction." : " convictions.")));
        }
        List<Charge> heard = new ArrayList<>();
        List<Charge> silent = new ArrayList<>();
        for (JsonObject c : StoryData.INSTANCE.charges) {
            if (!traits.contains(c.get("trait").getAsString())) {
                continue;
            }
            boolean silenced = false;
            for (JsonElement t : c.getAsJsonArray("silenced")) {
                silenced |= traits.contains(t.getAsString());
            }
            if (silenced) {
                silent.add(new Charge(0, line("The Watch Captain", fill(s.get("silent_witness").getAsString(), "witness",
                        c.get("witness").getAsString())), false));
            } else {
                heard.add(new Charge(c.get("weight").getAsInt(), line(c.get("witness").getAsString(), c.get("testimony").getAsString()), true));
            }
        }
        // what everyday life left behind
        int villagers = player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
        if (villagers > 0 && s.has("villagers")) {
            heard.add(new Charge(Math.min(3, 1 + villagers / 3), line(s.get("villagers"), "n", Integer.toString(villagers)), true));
        }
        if (d.looted.size() >= 3 && s.has("stores")) {
            heard.add(new Charge(1, line(s.get("stores"), "n", Integer.toString(d.looted.size())), true));
        }
        if (d.contractsDone >= 3 && s.has("hand")) {
            heard.add(new Charge(1, line(s.get("hand"), "n", Integer.toString(d.contractsDone)), true));
        }
        if (d.shrinePrayers > 0 && s.has("shrines")) {
            heard.add(new Charge(1, line(s.get("shrines"), "n", Integer.toString(d.shrinePrayers)), true));
        }
        heard.sort(Comparator.comparingInt(Charge::weight).reversed());
        int weight = 0;
        int shown = 0;
        for (Charge c : heard) {
            weight += c.weight();
            if (shown++ < 7) {
                lines.add(c.line());
            }
        }
        if (heard.size() > 7) {
            lines.add(line("The Watch Captain", "There are " + (heard.size() - 7) + " more, your honour. I think the court has heard enough."));
        }
        for (int i = 0; i < Math.min(3, silent.size()); i++) {
            lines.add(silent.get(i).line());
        }
        if (s.has("plea")) {
            lines.add(s.get("plea").deepCopy());
        }
        out.add("lines", lines);
        out.addProperty("weight", weight);
        out.addProperty("heard", heard.size());
        out.addProperty("silent", silent.size());
        JsonArray pleas = new JsonArray();
        boolean canBribe = Ranks.hand(player) >= 1 && emeraldsIn(player, d) >= BRIBE;
        for (JsonObject p : StoryData.INSTANCE.pleas) {
            if (!p.get("id").getAsString().equals("bribe") || canBribe) {
                pleas.add(p.deepCopy());
            }
        }
        out.add("pleas", pleas);
        out.addProperty("day", player.getServer().overworld().getDayTime() / 24000L + 1);
        out.addProperty("act", StoryData.INSTANCE.acts.getOrDefault(Dreams.actTrait(traits), ""));
        return out;
    }

    private static void show(ServerPlayer player, RepData d, boolean resume) {
        if (d.trialScript.isEmpty()) {
            return;
        }
        JsonObject o = JsonParser.parseString(d.trialScript).getAsJsonObject();
        o.addProperty("resume", resume);
        if (!d.trialVerdict.isEmpty()) {
            o.add("verdict", JsonParser.parseString(d.trialVerdict));
        }
        PacketDistributor.sendToPlayer(player, new Payloads.Trial(o.toString()));
    }

    // ------------------------------------------------------------------ the plea and the verdict
    public static void onPlea(ServerPlayer player, String plea) {
        RepData d = Reputation.data(player);
        if (d.trialState != 1 || d.trialScript.isEmpty()) {
            return;
        }
        if (plea.equals("continue")) {
            if (!d.trialVerdict.isEmpty()) {
                MOVE_AT.remove(player.getUUID());
                proceed(player, d);
            }
            return;
        }
        if (!d.trialVerdict.isEmpty()) {
            show(player, d, true);
            return;
        }
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        boolean allowed = false;
        for (JsonElement p : script.getAsJsonArray("pleas")) {
            allowed |= p.getAsJsonObject().get("id").getAsString().equals(plea);
        }
        if (!allowed) {
            return;
        }
        int w = script.get("weight").getAsInt();
        int base = Math.min(7, Math.max(1, Math.min(5, 1 + w / 2)) + d.convictions);
        JsonObject s = StoryData.INSTANCE.trialScript;
        boolean guilty;
        int days = base;
        String says;
        if (w == 0) {
            guilty = false;
            says = "no_case";
        } else {
            switch (plea) {
                case "guilty" -> {
                    guilty = true;
                    days = Math.max(1, base - 1);
                    says = "guilty";
                }
                case "not_guilty" -> {
                    guilty = w >= 3;
                    days = Math.min(7, base + 1);
                    says = guilty ? "guilty" : "acquitted";
                }
                case "bribe" -> {
                    takeEmeralds(player, d, BRIBE);
                    guilty = w >= 6;
                    says = guilty ? "bribe_failed" : "bribe_worked";
                }
                default -> {
                    guilty = w >= 3;
                    says = guilty ? "guilty" : "acquitted";
                }
            }
        }
        JsonObject v = new JsonObject();
        v.addProperty("plea", plea);
        v.addProperty("guilty", guilty);
        v.addProperty("days", guilty ? days : 0);
        JsonArray lines = new JsonArray();
        if (s.has(says)) {
            lines.add(line(s.get(says), "days", days + (days == 1 ? " day" : " days")));
        }
        if (guilty) {
            lines.add(line("The Watch Captain", "Take them down."));
        }
        v.add("lines", lines);
        d.trialVerdict = v.toString();
        show(player, d, true);
        // if they never press "continue", the Watch moves them on anyway
        MOVE_AT.put(player.getUUID(), now(player) + 20 * 120);
    }

    private static void proceed(ServerPlayer player, RepData d) {
        JsonObject v = JsonParser.parseString(d.trialVerdict).getAsJsonObject();
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        // the Codex keeps the transcript
        JsonObject t = new JsonObject();
        t.addProperty("day", script.get("day").getAsLong());
        t.addProperty("act", script.get("act").getAsString());
        t.add("lines", script.getAsJsonArray("lines"));
        t.add("verdict", v);
        t.addProperty("outcome", v.get("guilty").getAsBoolean() ? "in the cells" : "acquitted");
        d.trials.add(t.toString());
        if (v.get("guilty").getAsBoolean()) {
            d.convictions++;
            d.sentence = v.get("days").getAsInt();
            d.served = 0;
            d.trialState = 2;
            toCell(player, d);
        } else {
            release(player, d, "acquitted");
        }
    }

    private static void toCell(ServerPlayer player, RepData d) {
        ServerLevel court = court(player.getServer());
        if (court == null) {
            release(player, d, "acquitted");
            return;
        }
        int slot = CourtData.get(court).slot(player.getUUID());
        CourtBuilder.ensure(court, slot);
        BlockPos loose = CourtBuilder.loosen(court, slot, player.getRandom().nextInt(1000));
        d.escapeBlock = loose.asLong();
        BlockPos spot = CourtBuilder.origin(slot).offset(CourtBuilder.CELL_SPOT);
        player.teleportTo(court, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
        player.setRespawnPosition(COURT, spot, 0f, true, false);
        player.level().playSound(null, spot, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
        player.displayClientMessage(Component.literal(d.sentence + (d.sentence == 1 ? " day" : " days")
                + " in the cells. Sleep to pass them.").withStyle(ChatFormatting.RED), true);
    }

    /** A night slept in the cell counts as a day served. */
    public static void onNightInCell(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return;
        }
        d.served++;
        give(player, new ItemStack(Items.BREAD, 2));
        // the Gilded Hand looks after its own: Silas's men come on the second night
        if (Ranks.hand(player) >= 2 && d.served == 2) {
            release(player, d, "broken out");
            return;
        }
        if (d.served >= d.sentence) {
            release(player, d, "served");
            return;
        }
        player.displayClientMessage(Component.literal("Day " + d.served + " of " + d.sentence + " served. The gaoler pushes bread through the bars.")
                .withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ going home
    /** outcome: served, escaped, broken out, acquitted, released (by an operator) */
    public static void release(ServerPlayer player, RepData d, String outcome) {
        if (d.trialState == 0) {
            return;
        }
        if (!d.trials.isEmpty() && d.trialState == 2) {
            JsonObject t = JsonParser.parseString(d.trials.get(d.trials.size() - 1)).getAsJsonObject();
            t.addProperty("outcome", outcome);
            t.addProperty("served", d.served);
            d.trials.set(d.trials.size() - 1, t.toString());
        }
        switch (outcome) {
            case "escaped" -> {
                d.escapes++;
                // the Hand respects it; the Watch remembers it
                d.handDone += 3;
                d.watchDone = Math.max(0, d.watchDone - 5);
            }
            case "broken out" -> d.brokenOut = true;
            default -> {
            }
        }
        d.trialState = 0;
        d.trialScript = "";
        d.trialVerdict = "";
        d.sentence = 0;
        d.served = 0;
        UUID id = player.getUUID();
        SHOW_AT.remove(id);
        MOVE_AT.remove(id);
        ESCAPING.remove(id);
        goHome(player, d);
        giveBack(player, d);
        restoreSpawn(player, d);
        String dream = switch (outcome) {
            case "escaped" -> "escape";
            case "broken out" -> "breakout";
            case "served", "acquitted" -> "release";
            default -> "";
        };
        if (!dream.isEmpty()) {
            DREAM.put(id, dream);
            DREAM_AT.put(id, now(player) + 50);
        }
    }

    private static void goHome(ServerPlayer player, RepData d) {
        MinecraftServer server = player.getServer();
        ServerLevel level = null;
        Vec3 at = null;
        if (d.hadSpawn && !d.spawnDim.isEmpty()) {
            ResourceLocation dim = ResourceLocation.tryParse(d.spawnDim);
            level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level != null) {
                BlockPos pos = BlockPos.of(d.spawnPos);
                level.getChunk(pos);
                BlockState st = level.getBlockState(pos);
                if (st.getBlock() instanceof BedBlock) {
                    at = BedBlock.findStandUpPosition(EntityType.PLAYER, level, pos, st.getValue(BedBlock.FACING), d.spawnAngle).orElse(null);
                }
                if (at == null && d.spawnForced) {
                    at = Vec3.atBottomCenterOf(pos);
                }
            }
        }
        if (at == null) {
            level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            level.getChunk(spawn);
            at = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        player.teleportTo(level, at.x, at.y, at.z, d.spawnAngle, 0f);
    }

    private static void saveSpawn(ServerPlayer player, RepData d) {
        BlockPos pos = player.getRespawnPosition();
        d.hadSpawn = pos != null;
        d.spawnDim = player.getRespawnDimension().location().toString();
        d.spawnPos = pos == null ? 0 : pos.asLong();
        d.spawnAngle = player.getRespawnAngle();
        d.spawnForced = player.isRespawnForced();
    }

    private static void restoreSpawn(ServerPlayer player, RepData d) {
        if (d.hadSpawn) {
            ResourceLocation dim = ResourceLocation.tryParse(d.spawnDim);
            player.setRespawnPosition(dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim),
                    BlockPos.of(d.spawnPos), d.spawnAngle, d.spawnForced, false);
        } else {
            player.setRespawnPosition(Level.OVERWORLD, null, 0f, false, false);
        }
    }

    // ------------------------------------------------------------------ the evidence (your things)
    private static void confiscate(ServerPlayer player, RepData d) {
        Inventory inv = player.getInventory();
        ListTag list = new ListTag();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            CompoundTag t = new CompoundTag();
            t.putInt("Slot", i);
            t.put("Item", s.save(player.registryAccess()));
            list.add(t);
        }
        // anything already held from an earlier trial stays held (never lose things twice)
        for (Tag old : d.evidence) {
            CompoundTag t = ((CompoundTag) old).copy();
            t.putInt("Slot", -1);
            list.add(t);
        }
        d.evidence = list;
        inv.clearContent();
        player.inventoryMenu.broadcastChanges();
    }

    private static void giveBack(ServerPlayer player, RepData d) {
        Inventory inv = player.getInventory();
        for (Tag tag : d.evidence) {
            CompoundTag t = (CompoundTag) tag;
            Optional<ItemStack> parsed = ItemStack.parse(player.registryAccess(), t.getCompound("Item"));
            if (parsed.isEmpty() || parsed.get().isEmpty()) {
                continue;
            }
            ItemStack s = parsed.get();
            int slot = t.getInt("Slot");
            if (slot >= 0 && slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, s);
            } else {
                give(player, s);
            }
        }
        d.evidence = new ListTag();
        player.inventoryMenu.broadcastChanges();
    }

    private static void give(ServerPlayer player, ItemStack s) {
        if (!player.getInventory().add(s) && !s.isEmpty()) {
            player.drop(s, false);
        }
    }

    private static int emeraldsIn(ServerPlayer player, RepData d) {
        int n = 0;
        for (Tag tag : d.evidence) {
            Optional<ItemStack> s = ItemStack.parse(player.registryAccess(), ((CompoundTag) tag).getCompound("Item"));
            if (s.isPresent() && s.get().is(Items.EMERALD)) {
                n += s.get().getCount();
            }
        }
        return n;
    }

    private static void takeEmeralds(ServerPlayer player, RepData d, int amount) {
        ListTag out = new ListTag();
        for (Tag tag : d.evidence) {
            CompoundTag t = ((CompoundTag) tag).copy();
            Optional<ItemStack> s = ItemStack.parse(player.registryAccess(), t.getCompound("Item"));
            if (amount > 0 && s.isPresent() && s.get().is(Items.EMERALD)) {
                ItemStack e = s.get();
                int take = Math.min(amount, e.getCount());
                amount -= take;
                e.shrink(take);
                if (e.isEmpty()) {
                    continue;
                }
                t.put("Item", e.save(player.registryAccess()));
            }
            out.add(t);
        }
        d.evidence = out;
    }

    // ------------------------------------------------------------------ every tick
    public static void tick(ServerPlayer player) {
        RepData d = Reputation.data(player);
        UUID id = player.getUUID();
        long now = now(player);
        if (d.pendingKidnap && !player.isSleeping()) {
            if (!kidnap(player)) {
                d.pendingKidnap = false;
            }
            return;
        }
        Long show = SHOW_AT.get(id);
        if (show != null && now >= show) {
            SHOW_AT.remove(id);
            if (d.trialState == 1) {
                show(player, d, !d.trialVerdict.isEmpty());
            }
        }
        Long move = MOVE_AT.get(id);
        if (move != null && now >= move) {
            MOVE_AT.remove(id);
            if (d.trialState == 1 && !d.trialVerdict.isEmpty()) {
                proceed(player, d);
            }
        }
        if (ESCAPING.remove(id) && d.trialState == 2) {
            release(player, d, "escaped");
            return;
        }
        Long dream = DREAM_AT.get(id);
        if (dream != null && now >= dream) {
            DREAM_AT.remove(id);
            Dreams.send(player, StoryData.INSTANCE.specialDreams.get(DREAM.remove(id)));
        }
        if (now % 20 != 0) {
            return;
        }
        ServerLevel court = court(player.getServer());
        if (court == null) {
            return;
        }
        if (inCourt(player)) {
            AWAY_SINCE.remove(id);
            CourtBuilder.ensureNpcs(court, CourtData.get(court).slot(id));
            if (d.trialState == 0 && !DREAM_AT.containsKey(id)) {
                // nobody should be here who isn't on trial or in a cell (an operator's teleport, an old save...)
                goHome(player, d);
            }
        } else if (d.trialState != 0) {
            // taken out of the court by something else: give everything back after a little while
            long since = AWAY_SINCE.computeIfAbsent(id, k -> now);
            if (now - since > 200) {
                AWAY_SINCE.remove(id);
                HalfAGod.LOG.info("Half a God: {} left the court without the Watch; ending their trial.", player.getName().getString());
                d.trialState = 0;
                d.trialScript = "";
                d.trialVerdict = "";
                giveBack(player, d);
                restoreSpawn(player, d);
            }
        }
    }

    // ------------------------------------------------------------------ the court's rules
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RepData d = Reputation.data(player);
            if (d.trialState == 1) {
                SHOW_AT.put(player.getUUID(), now(player) + 60);
            } else if (d.trialState == 2) {
                player.displayClientMessage(Component.literal("Day " + d.served + " of " + d.sentence + " served.").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Reputation.data(player).trialState == 1) {
            SHOW_AT.put(player.getUUID(), now(player) + 40);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !inCourt(player)) {
            return;
        }
        RepData d = Reputation.data(player);
        if (d.trialState == 2 && event.getPos().asLong() == d.escapeBlock) {
            ESCAPING.add(player.getUUID());
            return;
        }
        if (!player.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inCourt(player) && !player.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (CourtBuilder.isCourtNpc(event.getTarget())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                String name = event.getTarget().hasCustomName() ? event.getTarget().getCustomName().getString() : "";
                String line = name.equals("The Gaoler") ? "\"Sleep. The days go faster asleep.\"" : "\"Silence in the court.\"";
                player.displayClientMessage(Component.literal(name + ": " + line).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (CourtBuilder.isCourtNpc(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (CourtBuilder.isCourtNpc(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (CourtBuilder.isCourtNpc(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ for operators (/halfagod test trial ...)
    public static String testStart(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 0) {
            return "You're already on trial or in a cell.";
        }
        if (court(player.getServer()) == null) {
            return "The court dimension isn't loaded (is the Half a God story pack enabled?).";
        }
        return kidnap(player) ? "The Watch takes you." : "Couldn't start a trial.";
    }

    public static String testDay(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return "You're not in a cell.";
        }
        onNightInCell(player);
        return "One night served.";
    }

    public static String testRelease(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState == 0) {
            return "You're not on trial or in a cell.";
        }
        release(player, d, "released");
        return "Released.";
    }

    public static String testEscape(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return "You're not in a cell.";
        }
        BlockPos p = BlockPos.of(d.escapeBlock);
        return "The loose block is at " + p.getX() + " " + p.getY() + " " + p.getZ() + " (break it to escape).";
    }

    public static String status(ServerPlayer player) {
        RepData d = Reputation.data(player);
        Set<String> traits = Meb.traits(player);
        String act = Dreams.actTrait(traits);
        return "Trial state: " + (d.trialState == 0 ? "free" : d.trialState == 1 ? "on trial" : "in a cell (" + d.served + "/" + d.sentence + ")")
                + ". Act: " + StoryData.INSTANCE.acts.getOrDefault(act, "none") + (d.trialActs.contains(act) ? " (already tried this act)" : "")
                + ". Tier: " + Reputation.tier(d.value) + ". Convictions: " + d.convictions + ", escapes: " + d.escapes
                + ". Court loaded: " + (court(player.getServer()) != null) + ".";
    }
}

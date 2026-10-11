package dev.halfagod.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What your reputation does to the world around you.
 *
 * Good (and more so Saint): villagers treat you as a hero (gifts and discounts), and guards and golems fight for you
 * near villages; a Saint gets a Watchman at their side when attacked near a village at night.
 * Evil (Fallen, and much more so Wicked): Silas pays a cut of every story job and frightened villagers hand things over.
 * But bounty hunters come at night, illagers near you fight harder, raids follow you into villages, clerics (and at
 * Wicked, everyone) refuse to trade, and golems attack the Wicked.
 * Climbing back up turns all of it off again.
 */
public final class Morality {
    private static final int WICKED = 0, FALLEN = 1, GOOD = 3, SAINT = 4;
    private static final Map<UUID, Long> LAST_BOUNTY = new HashMap<>();
    private static final Map<UUID, Long> LAST_OMEN = new HashMap<>();
    private static final Map<UUID, Long> LAST_WATCHMAN = new HashMap<>();
    private static final String WATCHMAN = "hag_watchman";
    private static final String HUNTER = "hag_bounty_hunter";
    private static final String HARDENED = "hag_hardened";

    private Morality() {
    }

    public static int tier(ServerPlayer p) {
        return Reputation.tierIndex(Reputation.data(p).value);
    }

    public static boolean inVillage(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
    }

    /** every 100 ticks */
    public static void tick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int t = tier(player);
        boolean village = inVillage(level, player.blockPosition());

        // Good: villagers treat you as their hero while you're among them (gifts, cheaper trades)
        if (village && t >= GOOD) {
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 220, t == SAINT ? 1 : 0, true, false, true));
        }

        // Evil: a village you walk into may be raided because of you
        if (village && t <= FALLEN && level.getDifficulty() != Difficulty.PEACEFUL) {
            long day = level.getDayTime() / 24000L;
            if (LAST_OMEN.getOrDefault(player.getUUID(), -1L) != day) {
                LAST_OMEN.put(player.getUUID(), day);
                if (level.random.nextFloat() < (t == WICKED ? 0.35f : 0.15f) && !player.hasEffect(MobEffects.BAD_OMEN)
                        && !player.hasEffect(MobEffects.RAID_OMEN)) {
                    player.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 6000, t == WICKED ? 1 : 0, false, true, true));
                    player.displayClientMessage(Component.literal("The villagers' fear has a smell. Something is coming for this village.")
                            .withStyle(ChatFormatting.DARK_RED), true);
                }
            }
        }

        // Evil: bounty hunters come for you at night
        long time = level.getDayTime() % 24000L;
        if (t <= FALLEN && time > 13500 && time < 22000 && level.getDifficulty() != Difficulty.PEACEFUL
                && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            long night = level.getDayTime() / 24000L;
            if (LAST_BOUNTY.getOrDefault(player.getUUID(), -1L) != night) {
                LAST_BOUNTY.put(player.getUUID(), night);
                float hunt = (t == WICKED ? 0.7f : 0.35f) * new float[]{1f, 1f, 0.75f, 0.5f}[Ranks.hand(player)]; // the Hand protects its own
                if (level.random.nextFloat() < hunt) {
                    sendHunters(player, level, t == WICKED);
                }
            }
        }

        // Watchmen go home at dawn
        if (time < 13000 || time > 23000) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(96), m -> m.getTags().contains(WATCHMAN))) {
                m.discard();
            }
        }
    }

    private static void sendHunters(ServerPlayer player, ServerLevel level, boolean wicked) {
        RandomSource r = level.random;
        double angle = r.nextDouble() * Math.PI * 2;
        int x = player.getBlockX() + (int) (Math.cos(angle) * 20);
        int z = player.getBlockZ() + (int) (Math.sin(angle) * 20);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos base = new BlockPos(x, y, z);
        int vindicators = wicked ? 3 : 2;
        int pillagers = wicked ? 2 : 1;
        int reward = wicked ? 400 : 150;
        for (int i = 0; i < vindicators + pillagers + (wicked ? 1 : 0); i++) {
            EntityType<? extends Mob> type = i < vindicators ? EntityType.VINDICATOR : i < vindicators + pillagers ? EntityType.PILLAGER : EntityType.EVOKER;
            Mob m = type.create(level);
            if (m == null) {
                continue;
            }
            BlockPos p = base.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            p = new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
            m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, r.nextFloat() * 360, 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
            m.setCustomName(Component.literal("Bounty Hunter"));
            m.addTag(HUNTER);
            if (type == EntityType.VINDICATOR) {
                m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(wicked ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
            }
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 0, false, false));
            if (wicked) {
                m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 6000, 0, false, false));
            }
            m.setTarget(player);
            level.addFreshEntity(m);
        }
        player.displayClientMessage(Component.literal("Bounty hunters are on your trail. Your head is worth " + reward + " emeralds.")
                .withStyle(ChatFormatting.RED), true);
        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------ fights
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getEntity() instanceof Mob attacker)
                || !(attacker instanceof Enemy || attacker instanceof Raider)) {
            return;
        }
        int t = tier(player);
        if (t < GOOD) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // the village's guards and golems come to help
        List<Mob> helpers = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(Ranks.watch(player) >= 2 ? 48 : 32),
                m -> m instanceof IronGolem || isGuard(m) || m.getTags().contains(WATCHMAN));
        for (Mob h : helpers) {
            if (h.getTarget() == null || !h.getTarget().isAlive()) {
                h.setTarget(attacker);
            }
        }
        // a Saint gets a Watchman at their side at night near a village
        long time = level.getDayTime() % 24000L;
        boolean night = time > 13000 && time < 23000;
        int watchRank = Ranks.watch(player);
        // a Saint (or a Sergeant of the Watch) gets a Watchman at night; the Captain of the Watch, any time
        boolean watchman = (night && (t == SAINT || watchRank >= 2)) || watchRank >= 3;
        if (watchman && inVillageNear(level, player.blockPosition())) {
            long night = level.getDayTime() / 24000L;
            if (LAST_WATCHMAN.getOrDefault(player.getUUID(), -1L) != night) {
                LAST_WATCHMAN.put(player.getUUID(), night);
                spawnWatchman(player, level, attacker);
            }
        }
    }

    private static boolean inVillageNear(ServerLevel level, BlockPos pos) {
        return inVillage(level, pos) || inVillage(level, pos.offset(32, 0, 0)) || inVillage(level, pos.offset(-32, 0, 0))
                || inVillage(level, pos.offset(0, 0, 32)) || inVillage(level, pos.offset(0, 0, -32));
    }

    private static boolean isGuard(Entity e) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return id != null && id.getNamespace().equals("guardvillagers");
    }

    private static void spawnWatchman(ServerPlayer player, ServerLevel level, LivingEntity attacker) {
        // a Guard Villagers guard if that mod is installed, otherwise an iron golem
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard"));
        if (type == EntityType.PIG || type == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard"))) {
            type = EntityType.IRON_GOLEM;
        }
        Entity e = type.create(level);
        if (!(e instanceof Mob m)) {
            return;
        }
        BlockPos p = player.blockPosition().offset(2, 0, 2);
        p = new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), 0);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
        m.setCustomName(Component.literal("Watchman of Ashford"));
        m.addTag(WATCHMAN);
        m.addTag("mebahelrpgrquest:golem_protected");
        m.setTarget(attacker);
        level.addFreshEntity(m);
        player.displayClientMessage(Component.literal("A Watchman runs out of the dark to stand beside you.").withStyle(ChatFormatting.GOLD), true);
    }

    /** Illagers near the Fallen and the Wicked fight harder. */
    @SubscribeEvent
    public static void onSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Raider raider)
                || raider.getTags().contains(HARDENED)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(raider) < 80 * 80) {
                int t = tier(p);
                if (t <= FALLEN) {
                    raider.addTag(HARDENED);
                    raider.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, -1, 0, false, false));
                    if (t == WICKED) {
                        raider.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 0, false, false));
                    }
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ villagers
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager villager)
                || Meb.isQuestThreat(villager) || Meb.offeredQuest(villager) != null || villager.isBaby()) {
            return;
        }
        int t = tier(player);
        // Evil perk: sneak and lean on a villager and they'll hand something over (once a day each)
        if (player.isShiftKeyDown() && t <= FALLEN) {
            ServerLevel level = player.serverLevel();
            long day = level.getDayTime() / 24000L;
            String tag = "hag_shaken_" + day;
            event.setCanceled(true);
            if (villager.getTags().contains(tag)) {
                player.displayClientMessage(Component.literal("They've got nothing left to give you today.").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            villager.getTags().removeIf(x -> x.startsWith("hag_shaken_"));
            villager.addTag(tag);
            RandomSource r = level.random;
            ItemStack loot = switch (r.nextInt(5)) {
                case 0 -> new ItemStack(Items.EMERALD, 1 + r.nextInt(t == WICKED ? 5 : 3));
                case 1 -> new ItemStack(Items.BREAD, 2 + r.nextInt(4));
                case 2 -> new ItemStack(Items.IRON_INGOT, 1 + r.nextInt(3));
                case 3 -> new ItemStack(Items.GOLD_INGOT, 1 + r.nextInt(2));
                default -> new ItemStack(Items.EMERALD, 2);
            };
            if (Ranks.hand(player) >= 2) {
                loot.setCount(Math.min(loot.getMaxStackSize(), loot.getCount() * 2)); // a Collector knows where they hide things
            }
            ItemEntity drop = new ItemEntity(level, villager.getX(), villager.getY() + 1, villager.getZ(), loot);
            level.addFreshEntity(drop);
            level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1f, 1f);
            Reputation.change(player, -1, "");
            player.displayClientMessage(Component.literal("The villager hands it over without looking at you.").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        // Evil penalty: clerics won't serve the Fallen; nobody trades with the Wicked
        if (t == WICKED || (t == FALLEN && villager.getVillagerData().getProfession() == VillagerProfession.CLERIC)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal(t == WICKED ? "The villager shuts the shutters on you." : "\"Not for you. Find your healing somewhere else.\"")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /** Evil perk: Silas takes care of his people. A cut of every story job. */
    public static void onStoryQuestDone(ServerPlayer player) {
        int t = tier(player);
        if (t > FALLEN) {
            return;
        }
        int amount = (t == WICKED ? 8 : 4) * (Ranks.hand(player) >= 3 ? 2 : 1);
        ItemStack cut = new ItemStack(Items.EMERALD, amount);
        if (!player.getInventory().add(cut)) {
            player.drop(cut, false);
        }
        player.displayClientMessage(Component.literal("Silas's cut: " + amount + " emeralds.").withStyle(ChatFormatting.GOLD), true);
    }
}

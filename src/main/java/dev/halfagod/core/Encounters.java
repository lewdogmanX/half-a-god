package dev.halfagod.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encounters on the road: small moral moments that happen now and then while you travel, always well away from
 * villages. They are meant to be a little rare (at most one every day and a half of game time, and usually longer).
 *
 * - A Wounded Deserter: feed him (mercy) or finish him (cruelty).
 * - A Merchant being robbed by highwaymen: save him and he pays you, or rob him yourself and the highwaymen let you.
 * - A Hunted Man with bounty hunters behind him: protect him, or kill him and collect the bounty.
 * - A Golden Eye Shrine: tear it down and keep the gold, or kneel and pray to it for strength.
 * - A Hungry Pilgrim: share your food and he blesses you, or take what he has.
 */
public final class Encounters {
    private static final long COOLDOWN = 36000L;      // a day and a half of game time, at least
    private static final float CHANCE = 0.003f;       // per check (every 5 seconds) once the cooldown has passed
    private static final long EXPIRE = 6000L;         // an encounter you walk away from ends after 5 minutes
    private static final String TAG = "hag_encounter";
    private static final String KIND = "hag_enc_kind:";
    private static final String OWNER = "hag_enc_owner:";
    private static final String ROLE_FOE = "hag_enc_foe";

    private record Active(String kind, long started, List<UUID> people, List<UUID> foes, BlockPos where) {
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private Encounters() {
    }

    private static void say(ServerPlayer p, String text, ChatFormatting color) {
        p.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    // ------------------------------------------------------------------ starting one
    /** every 100 ticks */
    public static void tick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Active a = ACTIVE.get(player.getUUID());
        if (a != null) {
            follow(player, level, a, now);
            return;
        }
        RepData d = Reputation.data(player);
        if (!d.initialised || player.isSpectator() || player.isCreative() || level.dimension() != Level.OVERWORLD
                || now - d.lastEncounter < COOLDOWN || player.isInWater() || !player.onGround()
                || !level.canSeeSky(player.blockPosition()) || nearVillage(level, player.blockPosition())) {
            return;
        }
        if (level.random.nextFloat() >= CHANCE) {
            return;
        }
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        String[] kinds = peaceful ? new String[]{"deserter", "shrine", "pilgrim"} : new String[]{"deserter", "deserter", "merchant", "merchant", "hunted", "shrine", "pilgrim", "pilgrim"};
        String kind = kinds[level.random.nextInt(kinds.length)];
        BlockPos spot = findSpot(player, level);
        if (spot == null) {
            return;
        }
        d.lastEncounter = now;
        start(player, level, kind, spot, now);
    }

    private static boolean nearVillage(ServerLevel level, BlockPos pos) {
        for (int dx = -96; dx <= 96; dx += 48) {
            for (int dz = -96; dz <= 96; dz += 48) {
                if (Morality.inVillage(level, pos.offset(dx, 0, dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Open ground up the road ahead of you. */
    private static BlockPos findSpot(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        double len = Math.max(0.2, Math.hypot(look.x, look.z));
        for (int i = 0; i < 8; i++) {
            double ang = Math.atan2(look.z / len, look.x / len) + (level.random.nextDouble() - 0.5) * 1.2;
            int dist = 18 + level.random.nextInt(10);
            int x = player.getBlockX() + (int) Math.round(Math.cos(ang) * dist);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
            BlockPos probe = new BlockPos(x, 0, z);
            if (!level.isLoaded(probe)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos.below()).isSolid()
                    || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                    || Math.abs(y - player.getBlockY()) > 8 || nearVillage(level, pos)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, BlockPos at, String name, ServerPlayer owner, String kind, boolean foe) {
        T m = type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        m.setCustomName(Component.literal(name));
        m.setPersistenceRequired();
        m.addTag(TAG);
        m.addTag(KIND + kind);
        m.addTag(OWNER + owner.getUUID());
        if (foe) {
            m.addTag(ROLE_FOE);
        }
        level.addFreshEntity(m);
        return m;
    }

    private static BlockPos near(ServerLevel level, BlockPos at, int spread) {
        RandomSource r = level.random;
        int x = at.getX() + r.nextInt(spread * 2 + 1) - spread, z = at.getZ() + r.nextInt(spread * 2 + 1) - spread;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static void start(ServerPlayer player, ServerLevel level, String kind, BlockPos at, long now) {
        List<UUID> people = new ArrayList<>(), foes = new ArrayList<>();
        switch (kind) {
            case "deserter" -> {
                var m = spawn(level, EntityType.PILLAGER, at, "Wounded Deserter", player, kind, false);
                if (m == null) {
                    return;
                }
                m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                m.setNoAi(true);
                m.setHealth(6);
                m.addTag("mebahelrpgrquest:golem_protected");
                people.add(m.getUUID());
                say(player, "Someone is lying in the road ahead, and not moving much.", ChatFormatting.YELLOW);
            }
            case "pilgrim" -> {
                var m = spawn(level, EntityType.WANDERING_TRADER, at, "Hungry Pilgrim", player, kind, false);
                if (m == null) {
                    return;
                }
                m.setDespawnDelay(0);
                people.add(m.getUUID());
                say(player, "A thin traveller is sitting by the road, watching you come.", ChatFormatting.YELLOW);
            }
            case "merchant" -> {
                var trader = spawn(level, EntityType.WANDERING_TRADER, at, "Robbed Merchant", player, kind, false);
                if (trader == null) {
                    return;
                }
                trader.setDespawnDelay(0);
                people.add(trader.getUUID());
                for (int i = 0; i < 3; i++) {
                    var h = spawn(level, EntityType.VINDICATOR, near(level, at, 4), "Highwayman", player, kind, true);
                    if (h != null) {
                        h.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                        h.setTarget(trader);
                        foes.add(h.getUUID());
                    }
                }
                say(player, "Shouting up ahead: someone's being robbed on the road!", ChatFormatting.RED);
            }
            case "hunted" -> {
                Villager man = spawn(level, EntityType.VILLAGER, at, "Hunted Man", player, kind, false);
                if (man == null) {
                    return;
                }
                man.setVillagerData(man.getVillagerData().setProfession(VillagerProfession.NITWIT));
                Meb.neverOffers(man);
                people.add(man.getUUID());
                for (int i = 0; i < 2; i++) {
                    var h = spawn(level, EntityType.VINDICATOR, near(level, at, 6), "Bounty Hunter", player, kind, true);
                    if (h != null) {
                        h.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                        h.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                        h.setTarget(man);
                        foes.add(h.getUUID());
                    }
                }
                say(player, "A man is running toward you across the fields. Two more are running after him.", ChatFormatting.RED);
            }
            case "shrine" -> {
                if (!buildShrine(player, level, at)) {
                    return;
                }
                say(player, "Something glints gold beside the road ahead.", ChatFormatting.GOLD);
            }
            default -> {
                return;
            }
        }
        ACTIVE.put(player.getUUID(), new Active(kind, now, people, foes, at));
    }

    // ------------------------------------------------------------------ while it's happening
    private static Entity find(ServerLevel level, UUID id) {
        return level.getEntity(id);
    }

    private static void follow(ServerPlayer player, ServerLevel level, Active a, long now) {
        boolean far = player.blockPosition().distSqr(a.where()) > 64 * 64;
        if (a.kind().equals("shrine")) {
            ACTIVE.remove(player.getUUID()); // shrines wait for you, however long
            return;
        }
        if ((now - a.started() > EXPIRE && far) || now - a.started() > EXPIRE * 3 || player.level() != level) {
            end(level, a);
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (a.foes().isEmpty()) {
            return;
        }
        // the highwaymen or the bounty hunters are all down and the one they were after is still alive: they're saved
        boolean foesLeft = false;
        for (UUID f : a.foes()) {
            if (find(level, f) instanceof LivingEntity e && e.isAlive()) {
                foesLeft = true;
            }
        }
        LivingEntity person = find(level, a.people().get(0)) instanceof LivingEntity e && e.isAlive() ? e : null;
        if (!foesLeft && person != null) {
            if (a.kind().equals("merchant")) {
                reward(player, person, new ItemStack(Items.EMERALD, 6 + level.random.nextInt(5)));
                say(player, "Robbed Merchant: \"I thought that was the end of me. Take this. Take it!\"", ChatFormatting.WHITE);
            } else {
                reward(player, person, new ItemStack(Items.EMERALD, 4));
                say(player, "Hunted Man: \"They said I stole from the Hand. I did. It was mine first. Thank you.\"", ChatFormatting.WHITE);
            }
            Reputation.change(player, 4, "you saved someone on the road");
            leave(level, person);
            ACTIVE.remove(player.getUUID());
        } else if (person == null && !foesLeft) {
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void reward(ServerPlayer player, Entity from, ItemStack stack) {
        ItemEntity drop = new ItemEntity(from.level(), from.getX(), from.getY() + 0.5, from.getZ(), stack);
        drop.setDefaultPickUpDelay();
        from.level().addFreshEntity(drop);
        from.level().playSound(null, from.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
    }

    /** They go on their way. */
    private static void leave(ServerLevel level, Entity e) {
        level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        e.discard();
    }

    private static void end(ServerLevel level, Active a) {
        List<UUID> all = new ArrayList<>(a.people());
        all.addAll(a.foes());
        for (UUID id : all) {
            Entity e = find(level, id);
            if (e != null && e.isAlive()) {
                leave(level, e);
            }
        }
    }

    private static String kindOf(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(KIND)) {
                return t.substring(KIND.length());
            }
        }
        return "";
    }

    private static UUID ownerOf(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(OWNER)) {
                try {
                    return UUID.fromString(t.substring(OWNER.length()));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!target.getTags().contains(TAG) || target.getTags().contains(ROLE_FOE)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        String kind = kindOf(target);
        ItemStack held = player.getMainHandItem();
        boolean food = held.has(DataComponents.FOOD);
        ServerLevel level = player.serverLevel();
        switch (kind) {
            case "deserter" -> {
                if (!food) {
                    say(player, "Wounded Deserter: \"Water... bread... anything. I left them. I couldn't do it any more.\"", ChatFormatting.WHITE);
                    return;
                }
                held.shrink(1);
                reward(player, target, new ItemStack(Items.EMERALD, 2 + level.random.nextInt(3)));
                say(player, "Wounded Deserter: \"I'll not forget it. Here, it's all I've got. Don't go north tonight.\"", ChatFormatting.WHITE);
                Reputation.change(player, 3, "you helped a deserter");
                leave(level, target);
                ACTIVE.remove(player.getUUID());
            }
            case "pilgrim" -> {
                if (!food) {
                    say(player, "Hungry Pilgrim: \"Bread, friend? I've walked from the coast, and the coast is a long way.\"", ChatFormatting.WHITE);
                    return;
                }
                held.shrink(1);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0));
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, 0));
                say(player, "Hungry Pilgrim: he eats, then blesses you in a language you don't know. You feel lucky.", ChatFormatting.WHITE);
                Reputation.change(player, 2, "you fed a pilgrim");
                Secrets.onPilgrimFed(player);
                level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
                leave(level, target);
                ACTIVE.remove(player.getUUID());
            }
            case "hunted" -> say(player, "Hunted Man: \"They say I stole from the Hand. Please. Don't let them take me.\"", ChatFormatting.WHITE);
            case "merchant" -> say(player, "Robbed Merchant: \"Help me! They'll take everything!\"", ChatFormatting.WHITE);
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!dead.getTags().contains(TAG) || dead.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) dead.level();
        String kind = kindOf(dead);
        boolean foe = dead.getTags().contains(ROLE_FOE);
        if (foe || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Active a = ACTIVE.remove(player.getUUID());
        switch (kind) {
            case "deserter" -> {
                say(player, "He didn't fight back.", ChatFormatting.DARK_RED);
                Reputation.change(player, -4, "you killed a wounded man");
            }
            case "pilgrim" -> {
                reward(player, dead, new ItemStack(Items.EMERALD, 3));
                say(player, "The pilgrim had three emeralds sewn into his coat. Now you do.", ChatFormatting.DARK_RED);
                Reputation.change(player, -5, "you robbed a pilgrim");
            }
            case "merchant" -> {
                reward(player, dead, new ItemStack(Items.EMERALD, 8 + level.random.nextInt(6)));
                say(player, "The highwaymen look at you, then at the merchant's purse, then nod, and melt away.", ChatFormatting.DARK_RED);
                Reputation.change(player, -6, "you robbed a merchant on the road");
                if (a != null) {
                    end(level, a);
                }
            }
            case "hunted" -> {
                reward(player, dead, new ItemStack(Items.EMERALD, 12));
                say(player, "Bounty Hunter: \"The Hand pays what it owes. Pleasure doing business.\"", ChatFormatting.DARK_RED);
                Reputation.change(player, -6, "you collected a bounty");
                if (a != null) {
                    end(level, a);
                }
            }
            default -> {
            }
        }
    }

    /** Leftovers from before a restart are cleared away. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && !event.getLevel().isClientSide() && event.getEntity().getTags().contains(TAG)) {
            UUID owner = ownerOf(event.getEntity());
            Active a = owner == null ? null : ACTIVE.get(owner);
            if (a == null || (!a.people().contains(event.getEntity().getUUID()) && !a.foes().contains(event.getEntity().getUUID()))) {
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------ the shrine
    private static boolean buildShrine(ServerPlayer player, ServerLevel level, BlockPos at) {
        level.setBlock(at, Blocks.COBBLESTONE.defaultBlockState(), 3);
        level.setBlock(at.above(), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
        BlockPos gold = at.above(2);
        level.setBlock(gold, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(gold.above(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3), 3);
        BlockPos skull = at.east();
        if (level.getBlockState(skull).isAir() && level.getBlockState(skull.below()).isSolid()) {
            level.setBlock(skull, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 4), 3);
        }
        CompoundTag shrines = SettlementData.get(player.getServer()).section(player.getUUID(), "shrines");
        shrines.putBoolean(Long.toString(gold.asLong()), true);
        SettlementData.get(player.getServer()).setDirty();
        return true;
    }

    private static boolean isShrine(ServerPlayer player, BlockPos pos) {
        if (player.getServer() == null) {
            return false;
        }
        return SettlementData.get(player.getServer()).section(player.getUUID(), "shrines").contains(Long.toString(pos.asLong()));
    }

    private static void forgetShrine(ServerPlayer player, BlockPos pos) {
        SettlementData data = SettlementData.get(player.getServer());
        data.section(player.getUUID(), "shrines").remove(Long.toString(pos.asLong()));
        data.setDirty();
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !event.getState().is(Blocks.GOLD_BLOCK) || !isShrine(player, event.getPos())) {
            return;
        }
        forgetShrine(player, event.getPos());
        BlockPos candle = event.getPos().above();
        if (player.level().getBlockState(candle).is(Blocks.CANDLE)) {
            player.level().setBlock(candle, Blocks.AIR.defaultBlockState(), 3);
        }
        say(player, "You tear down the golden eye. The gold is yours, and so is the cult's anger.", ChatFormatting.GOLD);
        Reputation.change(player, 3, "you tore down a cult shrine");
    }

    @SubscribeEvent
    public static void onPray(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!player.level().getBlockState(pos).is(Blocks.GOLD_BLOCK) || !isShrine(player, pos)) {
            return;
        }
        event.setCanceled(true);
        forgetShrine(player, pos);
        player.level().setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
        if (player.level().getBlockState(pos.above()).is(Blocks.CANDLE)) {
            player.level().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 6000, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 0));
        player.level().playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5f, 1.4f);
        say(player, "You kneel. The gold dulls to stone as something takes it, and gives you strength in return.", ChatFormatting.DARK_RED);
        Reputation.change(player, -4, "you prayed at a cult shrine");
        Secrets.onPray(player);
    }
}

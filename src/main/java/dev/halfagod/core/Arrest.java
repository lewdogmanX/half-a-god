package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Watch doesn't take you in your sleep any more: its enforcers come for you. When the Watch has a case against you
 * (the same rules as before: from Act II, Fallen or Wicked, once an act), on a night it chooses a squad of iron
 * enforcers marches on you wherever you are in the overworld. Go to bed and they come at once.
 *
 * - Lose (they'd kill you) and they don't: they beat you down and the Watch takes you to court.
 * - Beat them all and you're free for now: they'll come back the next night or the one after, with more of them, and
 *   "resisting the Watch" goes on your record.
 * - Get far away from them, or leave the overworld, or last until morning, and they lose you. Until the next night.
 *
 * The enforcers are iron golems (the Watch's own guards, as in the court), stronger and faster than any village's, and
 * there are more of them for every conviction and every time you've fought them off.
 */
public final class Arrest {
    private static final float CHANCE = 1f / 3f;
    private static final String TAG = "hag_enforcer";

    private static final class Squad {
        final List<UUID> men = new ArrayList<>();
        long arriveAt;
        boolean arrived;
        long lostSince = -1;
    }

    private static final Map<UUID, Squad> ACTIVE = new HashMap<>();
    /** the night each player was last rolled for (so it's once a night) */
    private static final Map<UUID, Long> ROLLED = new HashMap<>();

    private Arrest() {
    }

    private static JsonObject t() {
        return StoryData.INSTANCE.table("arrest");
    }

    private static String text(String key) {
        return t().has(key) ? t().get(key).getAsString() : "";
    }

    private static void say(ServerPlayer p, String s, ChatFormatting c) {
        if (!s.isEmpty()) {
            p.displayClientMessage(Component.literal(s).withStyle(c), true);
        }
    }

    private static long night(Level level) {
        return level.getDayTime() / 24000L;
    }

    private static boolean isNight(Level level) {
        long tod = level.getDayTime() % 24000L;
        return tod >= 13000 && tod < 23000;
    }

    public static boolean active(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    // ------------------------------------------------------------------ deciding to come
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0) {
            return;
        }
        Squad s = ACTIVE.get(p.getUUID());
        if (s != null) {
            run(p, s);
            return;
        }
        ServerLevel level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD || !isNight(level)) {
            return;
        }
        long n = night(level);
        Long rolled = ROLLED.get(p.getUUID());
        if (rolled != null && rolled == n) {
            return;
        }
        ROLLED.put(p.getUUID(), n);
        RepData d = Reputation.data(p);
        if (!Trial.due(p, d)) {
            return;
        }
        // they come back after you've beaten them off or lost them; otherwise, on a night of the Watch's choosing
        boolean again = d.arrestNight >= 0 && n >= d.arrestNight;
        if (!again && p.getRandom().nextFloat() >= CHANCE) {
            return;
        }
        start(p, 600 + p.getRandom().nextInt(4200));
    }

    /** the Watch is due tonight and you go to bed: they come now (the old kidnapping, awake) */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        Squad s = ACTIVE.get(p.getUUID());
        if (s != null) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            if (!s.arrived) {
                s.arriveAt = p.getServer().getTickCount() + 60;
            }
            say(p, text("no_sleep"), ChatFormatting.RED);
        }
    }

    public static void start(ServerPlayer p, int delayTicks) {
        Squad s = new Squad();
        s.arriveAt = p.getServer().getTickCount() + delayTicks;
        ACTIVE.put(p.getUUID(), s);
        say(p, text("warning"), ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ the squad
    private static void run(ServerPlayer p, Squad s) {
        ServerLevel level = p.serverLevel();
        long now = p.getServer().getTickCount();
        RepData d = Reputation.data(p);
        if (d.trialState != 0) {
            end(p, s, false);
            return;
        }
        if (!s.arrived) {
            if (now < s.arriveAt) {
                return;
            }
            if (level.dimension() != Level.OVERWORLD) {
                // you're not here: they'll try again tomorrow night
                d.arrestNight = night(level) + 1;
                end(p, s, false);
                return;
            }
            arrive(p, s, d);
            return;
        }
        List<IronGolem> men = men(p, s);
        if (men.isEmpty()) {
            // you beat them all
            d.resisted++;
            d.arrestNight = night(level) + 1 + p.getRandom().nextInt(2);
            say(p, text("won"), ChatFormatting.GOLD);
            end(p, s, false);
            return;
        }
        boolean near = false;
        for (IronGolem g : men) {
            if (g.level() == level && g.distanceToSqr(p) < 96 * 96) {
                near = true;
            }
            if (g.level() == level && p.isAlive() && !p.isSpectator()) {
                g.setTarget(p);
                g.setPersistentAngerTarget(p.getUUID());
                g.startPersistentAngerTimer();
            }
        }
        // lost: far away for half a minute, out of the overworld, or morning came
        if (!near) {
            if (s.lostSince < 0) {
                s.lostSince = now;
            }
        } else {
            s.lostSince = -1;
        }
        if ((s.lostSince >= 0 && now - s.lostSince > 600) || level.dimension() != Level.OVERWORLD || !isNight(p.getServer().overworld())) {
            d.arrestNight = night(p.getServer().overworld()) + 1;
            say(p, text("lost"), ChatFormatting.GRAY);
            end(p, s, true);
        }
    }

    private static List<IronGolem> men(ServerPlayer p, Squad s) {
        List<IronGolem> out = new ArrayList<>();
        ServerLevel ow = p.getServer().overworld();
        s.men.removeIf(id -> {
            Entity e = ow.getEntity(id);
            if (e instanceof IronGolem g && g.isAlive()) {
                out.add(g);
                return false;
            }
            return true;
        });
        return out;
    }

    private static void arrive(ServerPlayer p, Squad s, RepData d) {
        ServerLevel level = p.serverLevel();
        int level0 = Math.min(3, d.resisted + d.convictions);
        int count = 3 + level0;
        for (int i = 0; i < count; i++) {
            BlockPos at = spot(p, level);
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g == null) {
                continue;
            }
            g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getRandom().nextFloat() * 360f, 0f);
            g.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            g.setCustomName(Component.literal(text("name").isEmpty() ? "Watch Enforcer" : text("name")));
            g.setPersistenceRequired();
            g.addTag(TAG);
            g.addTag(TAG + "_" + p.getUUID());
            // stronger and faster than any village's: the Watch's own iron
            g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(140 + 20 * level0);
            g.setHealth(g.getMaxHealth());
            g.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20 + 3 * level0);
            g.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.33);
            g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(96);
            g.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            g.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 20, 0, false, false));
            g.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 20, 0, false, false));
            g.setTarget(p);
            level.addFreshEntity(g);
            s.men.add(g.getUUID());
        }
        s.arrived = true;
        level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.6f, 0.8f);
        say(p, text("arrive"), ChatFormatting.RED);
        if (p.isSleeping()) {
            p.stopSleeping();
        }
    }

    /** a dry spot on the surface, eighteen to twenty-six blocks away (closer if the land won't have it) */
    private static BlockPos spot(ServerPlayer p, ServerLevel level) {
        for (int tries = 0; tries < 24; tries++) {
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            int r = tries < 16 ? 18 + p.getRandom().nextInt(9) : 6 + p.getRandom().nextInt(6);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (Math.abs(y - p.getY()) < 12 && level.getFluidState(at.below()).isEmpty() && level.getBlockState(at).isAir()
                    && level.getBlockState(at.above()).isAir() && level.getBlockState(at.above(2)).isAir()) {
                return at;
            }
        }
        return p.blockPosition().offset(3, 0, 3);
    }

    private static void end(ServerPlayer p, Squad s, boolean dismiss) {
        ACTIVE.remove(p.getUUID());
        for (IronGolem g : men(p, s)) {
            // the ones still standing march off (gone)
            g.discard();
        }
    }

    // ------------------------------------------------------------------ beaten down
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) {
            return;
        }
        Squad s = ACTIVE.get(p.getUUID());
        if (s == null || !s.arrived) {
            return;
        }
        boolean near = false;
        for (IronGolem g : men(p, s)) {
            near |= g.level() == p.level() && g.distanceToSqr(p) < 48 * 48;
        }
        if (!near) {
            return;
        }
        event.setCanceled(true);
        p.setHealth(2f);
        p.removeAllEffects();
        p.clearFire();
        p.setRemainingFireTicks(0);
        RepData d = Reputation.data(p);
        d.foughtArrest = true;
        d.arrestNight = -1;
        end(p, s, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1f, 0.6f);
        if (!Trial.kidnap(p)) {
            d.foughtArrest = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Squad s = ACTIVE.get(p.getUUID());
            if (s != null) {
                Reputation.data(p).arrestNight = night(p.getServer().overworld()) + 1;
                end(p, s, true);
            }
        }
    }

    /** enforcers left over from before a restart (nobody is under arrest any more): gone */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity() instanceof IronGolem g && g.getTags().contains(TAG)) {
            boolean wanted = false;
            for (Squad s : ACTIVE.values()) {
                wanted |= s.men.contains(g.getUUID());
            }
            if (!wanted) {
                event.setCanceled(true);
            }
        }
    }

    /** operators' test: bring the enforcers now */
    public static String testNow(ServerPlayer p) {
        if (Reputation.data(p).trialState != 0) {
            return "Not while on trial or in prison.";
        }
        Squad s = new Squad();
        s.arriveAt = p.getServer().getTickCount();
        ACTIVE.put(p.getUUID(), s);
        return "The Watch's enforcers are coming.";
    }
}

package dev.halfagod.core;

import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * The reputation meter: -100 (Wicked) .. +100 (Saint).
 * Moved by everyday play and by the story's choices; it changes villager prices (through vanilla gossip),
 * makes iron golems hostile to the Wicked, and hands out unlock traits at thresholds.
 */
public final class Reputation {
    public static final String[] TIERS = {"Wicked", "Fallen", "Wavering", "Good", "Saint"};

    private Reputation() {
    }

    public static RepData data(ServerPlayer player) {
        return player.getData(RepData.TYPE);
    }

    public static int tierIndex(double value) {
        if (value >= 60) return 4;
        if (value >= 20) return 3;
        if (value > -20) return 2;
        if (value > -60) return 1;
        return 0;
    }

    public static String tier(double value) {
        return TIERS[tierIndex(value)];
    }

    /** Change reputation, tell the HUD, and hand out threshold unlocks. */
    public static void change(ServerPlayer player, double amount, String why) {
        if (amount == 0) {
            return;
        }
        RepData d = data(player);
        int before = tierIndex(d.value);
        d.value = Mth.clamp(d.value + amount, -100, 100);
        int after = tierIndex(d.value);
        sync(player, amount);
        // the bar shows the change and the new tier; nothing goes to chat
        if (before != after) {
            updateTierFlags(player);
        }
        for (StoryData.Unlock u : StoryData.INSTANCE.unlocks) {
            boolean reached = u.below() ? d.value <= u.at() : d.value >= u.at();
            if (reached && !Meb.hasTrait(player, u.trait())) {
                Meb.addTrait(player, u.trait());
                if (!u.courier().isEmpty()) {
                    Meb.triggerCourier(player, u.courier(), u.trait());
                }
            }
        }
    }

    public static ChatFormatting color(int tier) {
        return switch (tier) {
            case 4 -> ChatFormatting.GOLD;
            case 3 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.GRAY;
            case 1 -> ChatFormatting.RED;
            default -> ChatFormatting.DARK_RED;
        };
    }

    public static void sync(ServerPlayer player, double delta) {
        Net.send(player, new Payloads.RepSync((int) Math.round(data(player).value * 10), (int) Math.round(delta * 10)));
    }

    /** Story flags hag_tier_<tier> (only the current one) drive Mebahel's ambient villager lines. */
    public static void updateTierFlags(ServerPlayer player) {
        int t = tierIndex(data(player).value);
        for (int i = 0; i < TIERS.length; i++) {
            Meb.setStoryFlag(player, "hag_tier_" + TIERS[i].toLowerCase(), i == t);
        }
    }

    // ------------------------------------------------------------------ everyday play
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Meb.isQuestThreat(victim)) {
            return; // quest choices are counted by the story, not twice here
        }
        if (victim instanceof Villager) {
            change(player, -15, "you killed a villager");
        } else if (victim instanceof WanderingTrader) {
            change(player, -10, "you killed a trader");
        } else if (victim instanceof IronGolem golem && !golem.isPlayerCreated()) {
            change(player, -10, "you killed a village guardian");
        } else if (victim instanceof Raider raider && raider.getCurrentRaid() != null) {
            change(player, 1, "");
        } else if (victim instanceof Mob mob && victim instanceof Enemy) {
            LivingEntity target = mob.getTarget();
            if (target instanceof AbstractVillager || target instanceof IronGolem) {
                change(player, 0.5, "");
            }
        }
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffectInstance().getEffect().value() == MobEffects.HERO_OF_THE_VILLAGE.value()
                && !event.getEffectInstance().isAmbient() && event.getOldEffectInstance() == null) {
            // (the mod's own "hero among villagers" perk is ambient and doesn't count)
            change(player, 12, "you saved a village from a raid");
        }
    }

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof ZombieVillager) || !(event.getOutcome() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        Player nearest = level.getNearestPlayer(villager, 16);
        if (nearest instanceof ServerPlayer player) {
            change(player, 8, "you cured a villager");
        }
    }

    @SubscribeEvent
    public static void onOpenContainer(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container) || container.getLootTable() == null) {
            return; // only untouched loot chests count, never containers players placed
        }
        RepData d = data(player);
        if (d.looted.contains(pos.asLong())) {
            return;
        }
        if (level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid()) {
            d.addLooted(pos.asLong());
            change(player, -3, "you took a village's stores");
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAbstractVillager() instanceof Villager) {
            change(player, 0.25, "");
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof WanderingTrader
                && tierIndex(data(player).value) == 0 && !Meb.isQuestThreat(event.getTarget())) {
            player.displayClientMessage(Component.literal("The trader backs away from you. \"Not you. Never you.\"").withStyle(ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Villager villager) {
            applyGossip(player, villager);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, 0);
            updateTierFlags(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, 0);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, 0);
        }
    }

    // ------------------------------------------------------------------ villagers and golems react
    /** Every few seconds: nearby villagers' gossip about the player follows the meter (prices, golem anger). */
    public static void tickVillagers(ServerPlayer player) {
        List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(32));
        for (Villager v : villagers) {
            applyGossip(player, v);
        }
    }

    private static void applyGossip(ServerPlayer player, Villager villager) {
        double value = data(player).value;
        GossipContainer gossip = villager.getGossips();
        UUID id = player.getUUID();
        int wantPositive = value >= 20 ? (int) Math.round((value - 10) * 1.2) : 0;   // up to ~108: cheaper trades
        int wantNegative = value <= -20 ? (int) Math.round((-value - 10) * 1.8) : 0; // up to ~162: golems turn on you below -65
        setGossip(gossip, id, GossipType.MINOR_POSITIVE, wantPositive);
        setGossip(gossip, id, GossipType.MINOR_NEGATIVE, wantNegative);
    }

    private static void setGossip(GossipContainer gossip, UUID id, GossipType type, int want) {
        int have = Math.abs(gossip.getReputation(id, t -> t == type)) / Math.max(1, Math.abs(type.weight));
        if (have < want) {
            gossip.add(id, type, want - have);
        } else if (have > want) {
            gossip.remove(id, type, have - want);
        }
    }

    /** For Mebahel's own (non-story) quests: sparing counts as mercy, attacking as cruelty. */
    public static void countMebahelChoices(ServerPlayer player) {
        RepData d = data(player);
        for (Meb.Progress p : Meb.quests(player)) {
            if (p.claimed() && (Duties.isDuty(p.questId()) || Duties.isContract(p.questId()))) {
                if (d.countedChoices.add(p.questId() + "|" + p.acceptedAt())) {
                    boolean watch = Duties.isDuty(p.questId());
                    change(player, watch ? 2 : -3, watch ? "you served the Watch" : "you worked for the Hand");
                    Ranks.onJobDone(player, watch);
                    if (!watch) {
                        d.contractsDone++;
                    }
                    Rivalry.onJob(player, watch);
                }
                continue;
            }
            if (!p.claimed() || p.questId().startsWith("mebahelrpgrquest:hag_") || !(p.spared() || p.attacked())) {
                continue;
            }
            String key = p.questId() + "|" + p.acceptedAt();
            if (d.countedChoices.add(key)) {
                change(player, p.spared() ? 3 : -3, p.spared() ? "you showed mercy" : "you chose the blade");
            }
        }
    }

}

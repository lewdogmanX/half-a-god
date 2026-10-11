package dev.halfagod.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Work that never runs out. While you're Good or a Saint, village residents ask you to do Watch duties (clear the road,
 * stand the night watch, feed the families). While you're Fallen or Wicked, a frightened resident passes you a Gilded
 * Hand contract instead (the Hand pays better). Wavering gets neither: nobody's sure what you are.
 * One at a time, at most every half a day; each is an ordinary Mebahel quest from that villager. A finished duty moves
 * your reputation up, a finished contract moves it down (counted in Reputation.countMebahelChoices).
 */
public final class Duties {
    private static final long GAP = 12000L;

    private Duties() {
    }

    public static boolean isDuty(String questId) {
        return StoryData.INSTANCE.watchDuties.contains(questId);
    }

    public static boolean isContract(String questId) {
        return StoryData.INSTANCE.handContracts.contains(questId);
    }

    /** every 100 ticks */
    public static void tick(ServerPlayer player) {
        int tier = Morality.tier(player);
        List<String> pool = tier >= 3 ? StoryData.INSTANCE.watchDuties : tier <= 1 ? StoryData.INSTANCE.handContracts : List.of();
        if (pool.isEmpty() || player.getServer() == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!Morality.inVillage(level, player.blockPosition())) {
            return;
        }
        SettlementData data = SettlementData.get(player.getServer());
        CompoundTag d = data.section(player.getUUID(), "duties");
        long now = level.getGameTime();
        if (now < d.getLong("next")) {
            return;
        }
        // already doing one, or one is already on offer here
        Set<String> given = new HashSet<>();
        Set<UUID> busyGivers = new HashSet<>();
        for (Object p : Meb.rawQuests(player)) {
            String q = Meb.questIdOf(p);
            UUID giver = Meb.giverOf(p);
            if (giver != null) {
                given.add(giver + "|" + q);
                if (Meb.isOpen(p)) {
                    busyGivers.add(giver);
                }
            }
            if (Meb.isOpen(p) && (isDuty(q) || isContract(q))) {
                return;
            }
        }
        List<Villager> nearby = level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(48),
                v -> v.isAlive() && !v.isBaby());
        List<Villager> residents = new ArrayList<>();
        for (Villager v : nearby) {
            String offered = Meb.offeredQuest(v);
            if (offered != null && !Meb.offerTaken(v) && (isDuty(offered) || isContract(offered))) {
                return;
            }
            if (Meb.isCourier(v) || Meb.isQuestThreat(v) || v.getTags().contains(Settlement.SETTLER) || busyGivers.contains(v.getUUID())
                    || (offered != null && !Meb.offerTaken(v))) {
                continue;
            }
            residents.add(v);
        }
        if (residents.isEmpty()) {
            return;
        }
        residents.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new java.util.Random(now ^ player.getUUID().getLeastSignificantBits()));
        for (String q : shuffled) {
            for (Villager v : residents) {
                if (given.contains(v.getUUID() + "|" + q) || !Meb.offerQuest(v, ResourceLocation.parse(q))) {
                    continue;
                }
                v.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
                player.displayClientMessage(Component.literal(tier >= 3 ? "Someone here needs the Watch's help." : "Someone here is holding a note sealed with a golden hand.")
                        .withStyle(tier >= 3 ? ChatFormatting.AQUA : ChatFormatting.GOLD), true);
                d.putLong("next", now + GAP);
                data.setDirty();
                return;
            }
        }
    }
}

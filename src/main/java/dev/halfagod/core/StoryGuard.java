package dev.halfagod.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps the story from breaking: protected quest villagers, self-restarting quests, and "Lost my way". */
public final class StoryGuard {
    private static final Map<UUID, Map<String, Integer>> RESTARTS = new HashMap<>();
    private static final String STORY = "mebahelrpgrquest:hag_";

    private StoryGuard() {
    }

    /** Monsters and raiders can't hurt (or zombify) the story's villagers. Players still can. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof AbstractVillager villager && !villager.level().isClientSide
                && (event.getSource().getEntity() instanceof Enemy || event.getSource().getEntity() instanceof Raider)
                && Meb.isQuestThreat(villager)) {
            event.setCanceled(true);
        }
    }

    /** A story quest that was abandoned starts again by itself. */
    public static int restartAbandoned(ServerPlayer player, boolean forced) {
        List<Meb.Progress> quests = Meb.quests(player);
        Map<String, Boolean> alive = new HashMap<>();
        for (Meb.Progress p : quests) {
            if (!p.questId().startsWith(STORY)) {
                continue;
            }
            boolean ok = p.claimed() || !p.failed();
            alive.merge(p.questId(), ok, Boolean::logicalOr);
        }
        Map<String, Integer> tries = RESTARTS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        int restarted = 0;
        for (Map.Entry<String, Boolean> e : alive.entrySet()) {
            if (e.getValue()) {
                continue;
            }
            int n = tries.getOrDefault(e.getKey(), 0);
            if (!forced && n >= 3) {
                continue;
            }
            tries.put(e.getKey(), n + 1);
            ResourceLocation id = ResourceLocation.parse(e.getKey());
            if (Meb.startQuest(player, id, false)) {
                restarted++;
                player.sendSystemMessage(Component.literal("[Half a God] Story quests can't be dropped, so this one has started again.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        return restarted;
    }

    /** Finds where the player should be in the story and starts whatever is missing. */
    public static void lostMyWay(ServerPlayer player) {
        int fixed = restartAbandoned(player, true);
        List<Meb.Progress> quests = Meb.quests(player);
        Set<String> seen = new HashSet<>();
        Map<String, Meb.Progress> claimed = new HashMap<>();
        for (Meb.Progress p : quests) {
            seen.add(p.questId());
            if (p.claimed()) {
                claimed.put(p.questId(), p);
            }
        }
        StoryData data = StoryData.INSTANCE;
        Set<String> wanted = new HashSet<>();
        for (Map.Entry<String, Meb.Progress> e : claimed.entrySet()) {
            StoryData.Graph g = data.graph.get(e.getKey());
            if (g != null) {
                if (e.getValue().spared() && !g.spare().isEmpty()) {
                    wanted.addAll(g.spare());
                } else if (e.getValue().attacked() && !g.kill().isEmpty()) {
                    wanted.addAll(g.kill());
                } else {
                    wanted.addAll(g.next());
                }
            }
            wanted.addAll(data.couriers.getOrDefault(e.getKey(), List.of()));
        }
        if (claimed.isEmpty()) {
            wanted.addAll(data.advancementCouriers);
        }
        for (String q : wanted) {
            if (seen.contains(q)) {
                continue;
            }
            StoryData.Graph g = data.graph.get(q);
            ResourceLocation id = ResourceLocation.parse(q);
            if (!Meb.canSee(player, id)) {
                continue; // not unlocked yet (waiting for another part of the story)
            }
            if (g != null && g.book()) {
                Meb.giveBook(player, q);
                fixed++;
            } else if (Meb.startQuest(player, id, false)) {
                fixed++;
            }
        }
        fixed += Choices.offerPending(player, true);
        player.sendSystemMessage(Component.literal(fixed == 0
                ? "[Half a God] Your story is on track. If you're waiting on something, it's probably a courier letter or the next part of the main story."
                : "[Half a God] Back on track: " + fixed + " part(s) of your story were restarted.").withStyle(ChatFormatting.GOLD));
    }
}

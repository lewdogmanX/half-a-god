package dev.halfagod.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mebahel's own questlines, woven into the story. Each one opens with an act; the act's last story quest
 * waits until every line of that act is finished. When a line's next quest needs a villager to offer it,
 * a resident of the village you're standing in offers it (nobody is spawned or teleported).
 */
public final class BaseLines {
    /** how long a line must sit idle before its next step is offered by someone new (its own giver gets first go) */
    private static final int IDLE_TICKS = 6000;
    private static final int PLACE_COOLDOWN = 1200;

    private static final Map<UUID, Map<String, Integer>> LAST_CLAIMED = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> CHANGED_AT = new HashMap<>();
    private static final Map<UUID, Long> LAST_PLACED = new HashMap<>();

    public record Line(String chain, String title, String act, String gate, String join, List<String> starters,
                       List<String> books, List<String> members, List<String> placeable, String doneTrait) {
    }

    private BaseLines() {
    }

    public static boolean isMember(String questId) {
        for (Line l : StoryData.INSTANCE.baseLines) {
            if (l.members().contains(questId)) {
                return true;
            }
        }
        return false;
    }

    private record State(Set<String> claimed, Set<String> active, Set<String> started) {
    }

    private static State state(ServerPlayer player) {
        Set<String> claimed = new HashSet<>(), active = new HashSet<>(), started = new HashSet<>();
        for (Meb.Progress p : Meb.quests(player)) {
            started.add(p.questId());
            if (p.claimed()) {
                claimed.add(p.questId());
            } else if (!p.failed()) {
                active.add(p.questId());
            }
        }
        return new State(claimed, active, started);
    }

    /** Lines whose act has begun and that are not finished yet. */
    private static List<Line> open(ServerPlayer player, State s) {
        List<Line> out = new ArrayList<>();
        Set<String> traits = Meb.traits(player);
        for (Line l : StoryData.INSTANCE.baseLines) {
            if (s.claimed().contains(l.gate()) && !traits.contains(l.doneTrait())) {
                out.add(l);
            }
        }
        return out;
    }

    /** The quest this line needs offered next, or null if it is busy or waiting on something else. */
    private static String nextOffer(ServerPlayer player, Line l, State s, long now) {
        for (String m : l.members()) {
            if (s.active().contains(m)) {
                return null; // already on it
            }
        }
        boolean begun = l.members().stream().anyMatch(s.claimed()::contains);
        if (begun) {
            long changed = CHANGED_AT.getOrDefault(player.getUUID(), Map.of()).getOrDefault(l.chain(), 0L);
            if (now - changed < IDLE_TICKS) {
                return null;
            }
        }
        List<String> pool = begun ? l.placeable() : l.starters();
        for (String q : pool) {
            if (!s.claimed().contains(q) && Meb.canSee(player, ResourceLocation.parse(q))) {
                return q;
            }
        }
        return null;
    }

    public static void tick(ServerPlayer player) {
        if (StoryData.INSTANCE.baseLines.isEmpty()) {
            return;
        }
        State s = state(player);
        long now = player.serverLevel().getGameTime();
        Map<String, Integer> last = LAST_CLAIMED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Map<String, Long> changed = CHANGED_AT.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        List<Line> open = open(player, s);
        for (Line l : open) {
            int n = (int) l.members().stream().filter(s.claimed()::contains).count()
                    + (int) l.members().stream().filter(s.active()::contains).count() * 1000;
            Integer before = last.put(l.chain(), n);
            if (before == null || before != n) {
                changed.put(l.chain(), now);
            }
        }
        if (now - LAST_PLACED.getOrDefault(player.getUUID(), -PLACE_COOLDOWN * 2L) < PLACE_COOLDOWN || !inVillage(player)) {
            return;
        }
        List<Villager> residents = residents(player);
        if (residents.isEmpty()) {
            return;
        }
        Set<String> onOffer = new HashSet<>();
        for (Villager v : residents) {
            String q = Meb.offeredQuest(v);
            if (q != null && !Meb.offerTaken(v)) {
                onOffer.add(q);
            }
        }
        for (Line l : open) {
            String q = nextOffer(player, l, s, now);
            if (q == null || onOffer.contains(q)) {
                continue;
            }
            Villager pick = residents.stream()
                    .filter(v -> Meb.offeredQuest(v) == null || Meb.offerTaken(v) || !Meb.canSee(player, ResourceLocation.parse(Meb.offeredQuest(v))))
                    .min(Comparator.comparingDouble(v -> v.distanceToSqr(player)))
                    .orElse(null);
            if (pick == null || !Meb.offerQuest(pick, ResourceLocation.parse(q))) {
                continue;
            }
            pick.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
            String who = pick.hasCustomName() && pick.getCustomName() != null ? pick.getCustomName().getString() : "A villager";
            player.sendSystemMessage(Component.literal("[Half a God] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(who + " here has work for you (" + l.title() + "). They're glowing for a moment so you can find them.")
                            .withStyle(ChatFormatting.YELLOW)));
            LAST_PLACED.put(player.getUUID(), now);
            return; // one at a time
        }
    }

    private static boolean inVillage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
    }

    private static List<Villager> residents(ServerPlayer player) {
        return player.level().getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(48),
                v -> v.isAlive() && !v.isBaby() && !Meb.isCourier(v) && !Meb.isQuestThreat(v));
    }

    /** For /halfagod lostmyway: what is holding the story up, and give back any lost letters. */
    public static int report(ServerPlayer player) {
        State s = state(player);
        List<Line> open = open(player, s);
        if (open.isEmpty()) {
            return 0;
        }
        int fixed = 0;
        StringBuilder names = new StringBuilder();
        for (Line l : open) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(l.title());
            for (String b : l.books()) {
                if (!s.started().contains(b)) {
                    Meb.giveBook(player, b);
                    fixed++;
                }
            }
        }
        // let the next village visit offer them straight away
        CHANGED_AT.remove(player.getUUID());
        LAST_PLACED.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("[Half a God] The story moves on once these are done: " + names
                + ". Walk into any village and someone there will offer the next one.").withStyle(ChatFormatting.GOLD));
        return fixed;
    }
}

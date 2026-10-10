package dev.halfagod.core;

import dev.halfagod.HalfAGod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge to Mebahel's RPG (a Fabric mod running through Sinytra Connector).
 * Everything goes through reflection on Mebahel's own method names, so this mod
 * never needs Mebahel at compile time and degrades quietly if it is missing.
 */
public final class Meb {
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Meb() {
    }

    public record Progress(String questId, boolean claimed, boolean failed, boolean completed,
                           boolean spared, boolean attacked, long acceptedAt) {
    }

    // ------------------------------------------------------------------ plumbing
    private static Class<?> cls(String name) {
        return CLASSES.computeIfAbsent(name, n -> {
            try {
                return Class.forName(n, true, HalfAGod.class.getClassLoader());
            } catch (Throwable t) {
                try {
                    return Class.forName(n, true, Thread.currentThread().getContextClassLoader());
                } catch (Throwable t2) {
                    warn("class " + n, t2);
                    return Void.class;
                }
            }
        });
    }

    private static Method method(Class<?> owner, String name, int params) {
        if (owner == null || owner == Void.class) {
            return null;
        }
        String key = owner.getName() + "#" + name + "/" + params;
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == params) {
                    m.setAccessible(true);
                    METHODS.put(key, m);
                    return m;
                }
            }
        }
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                METHODS.put(key, m);
                return m;
            }
        }
        return null;
    }

    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Method m = method(target.getClass(), name, args.length);
        if (m == null) {
            warn("method " + name, null);
            return null;
        }
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            warn("call " + name, t);
            return null;
        }
    }

    private static Object callStatic(String className, String name, Object... args) {
        Method m = method(cls(className), name, args.length);
        if (m == null) {
            warn("static " + className + "." + name, null);
            return null;
        }
        try {
            return m.invoke(null, args);
        } catch (Throwable t) {
            warn("static call " + name, t);
            return null;
        }
    }

    private static void warn(String what, Throwable t) {
        if (WARNED.add(what)) {
            HalfAGod.LOG.warn("Half a God could not reach Mebahel's RPG ({}). {}", what, t == null ? "" : t.toString());
        }
    }

    // ------------------------------------------------------------------ player traits and story flags
    @SuppressWarnings("unchecked")
    public static Set<String> traits(ServerPlayer player) {
        Object r = call(player, "mebahelrpgrquest$getTraits");
        return r instanceof Set<?> s ? new HashSet<>((Set<String>) s) : new HashSet<>();
    }

    public static boolean hasTrait(ServerPlayer player, String trait) {
        return Boolean.TRUE.equals(call(player, "mebahelrpgrquest$hasTrait", trait));
    }

    /** Adds a Mebahel trait (used by quest requirements) and the matching story flag (used by ambient dialogue). */
    public static void addTrait(ServerPlayer player, String trait) {
        call(player, "mebahelrpgrquest$addTrait", trait);
        call(player, "mebahelrpgrquest$addStoryFlag", trait);
        callStatic("net.mebahelrpgrquest.network.QuestNetworking", "syncPlayerTraitsToClient", player);
    }

    @SuppressWarnings("unchecked")
    public static void setStoryFlag(ServerPlayer player, String flag, boolean on) {
        if (on) {
            if (!Boolean.TRUE.equals(call(player, "mebahelrpgrquest$hasStoryFlag", flag))) {
                call(player, "mebahelrpgrquest$addStoryFlag", flag);
            }
            return;
        }
        Object r = call(player, "mebahelrpgrquest$getStoryFlags");
        if (r instanceof Set<?> s) {
            try {
                ((Set<String>) s).remove(flag);
            } catch (UnsupportedOperationException ignored) {
                // Mebahel returned a copy; the flag simply stays.
            }
        }
    }

    // ------------------------------------------------------------------ quest progress
    public static List<Progress> quests(ServerPlayer player) {
        List<Progress> out = new ArrayList<>();
        Object r = call(player, "mebahelrpgrquest$getQuests");
        if (!(r instanceof Collection<?> list)) {
            return out;
        }
        for (Object p : list) {
            if (p == null) {
                continue;
            }
            Object id = call(p, "getQuestId");
            if (id == null) {
                continue;
            }
            Object accepted = call(p, "getAcceptedAtMillis");
            out.add(new Progress(id.toString(),
                    Boolean.TRUE.equals(call(p, "isClaimed")),
                    Boolean.TRUE.equals(call(p, "isFailed")),
                    Boolean.TRUE.equals(call(p, "isCompleted")),
                    Boolean.TRUE.equals(call(p, "isThreatTargetSpared")),
                    Boolean.TRUE.equals(call(p, "isThreatTargetAttackTriggered")),
                    accepted instanceof Number n ? n.longValue() : 0L));
        }
        return out;
    }

    public static boolean isQuestThreat(Entity entity) {
        return Boolean.TRUE.equals(call(entity, "mebahelrpgrquest$isQuestThreatTarget"));
    }

    private static Object questManager() {
        try {
            return cls("net.mebahelrpgrquest.quest.QuestManager").getField("INSTANCE").get(null);
        } catch (Throwable t) {
            warn("QuestManager.INSTANCE", t);
            return null;
        }
    }

    public static Object quest(ResourceLocation id) {
        return call(questManager(), "getQuest", id);
    }

    public static boolean canSee(ServerPlayer player, ResourceLocation id) {
        Object q = quest(id);
        return q != null && Boolean.TRUE.equals(callStatic("net.mebahelrpgrquest.quest.QuestAvailability", "canSeeQuest", player, q));
    }

    /** Starts a story quest the same way Mebahel's own delayed flows do (no quest-giver villager is spawned). */
    public static boolean startQuest(ServerPlayer player, ResourceLocation id, boolean silent) {
        Object q = quest(id);
        if (q == null) {
            return false;
        }
        Object giver = callStatic("net.mebahelrpgrquest.quest.DelayedQuestManager", "giverUuid", id);
        if (!(giver instanceof UUID)) {
            return false;
        }
        Object ok = callStatic("net.mebahelrpgrquest.network.QuestDialogueNetworking", "startAutomaticQuest",
                player, q, giver, player.blockPosition(), silent);
        return Boolean.TRUE.equals(ok);
    }

    // ------------------------------------------------------------------ villagers that offer quests
    /** The quest a villager is offering (or null), and whether it has already been taken. */
    public static String offeredQuest(Entity villager) {
        if (!Boolean.TRUE.equals(call(villager, "mebahelrpgrquest$hasQuest"))) {
            return null;
        }
        Object id = call(villager, "mebahelrpgrquest$getQuestId");
        return id == null ? null : id.toString();
    }

    public static boolean offerTaken(Entity villager) {
        return Boolean.TRUE.equals(call(villager, "mebahelrpgrquest$isQuestClaimed"));
    }

    /** Gives a village resident a quest to offer, exactly as Mebahel does when it rolls one for a villager. */
    public static boolean offerQuest(Entity villager, ResourceLocation id) {
        if (quest(id) == null || method(villager.getClass(), "mebahelrpgrquest$setQuestId", 1) == null) {
            return false;
        }
        call(villager, "mebahelrpgrquest$setQuestId", id);
        call(villager, "mebahelrpgrquest$setQuestClaimed", false);
        call(villager, "mebahelrpgrquest$setQuestRollDone", true);
        callStatic("net.mebahelrpgrquest.quest.QuestAssignmentState", "reset", villager);
        callStatic("net.mebahelrpgrquest.quest.QuestEntityNames", "ensureQuestName", villager);
        return id.toString().equals(offeredQuest(villager));
    }

    public static boolean isCourier(Entity villager) {
        return villager.getTags().contains("mebahelrpgrquest:courier");
    }

    /** Sends one of the pack's courier letters whose trigger is "custom" (only this mod fires those). */
    public static void triggerCourier(ServerPlayer player, String deliveryId, String occurrence) {
        callStatic("net.mebahelrpgrquest.quest.CourierManager", "trigger", player, ResourceLocation.parse(deliveryId), occurrence);
    }

    /** Hands the player a quest letter (book quest) through Mebahel's own command. */
    public static void giveBook(ServerPlayer player, String questId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                "mebahelquest givebook " + questId + " " + player.getGameProfile().getName());
    }
}

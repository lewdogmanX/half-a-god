package dev.halfagod.core;

import dev.halfagod.HalfAGod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Quest markers on Antique Atlas. Antique Atlas draws Surveyor "landmarks"; each open story quest with a known
 * destination gets one, owned by the player (so only they see it), and it moves or disappears as the quest does.
 * Everything goes through reflection: without Surveyor installed this does nothing.
 */
public final class Atlas {
    private static final Map<UUID, Map<ResourceLocation, String>> PLACED = new HashMap<>();
    private static final Set<UUID> CLEANED = new HashSet<>();
    private static Boolean available;
    private static Method worldLandmarksOf, put, remove, create, builderAdd, asMap, surveyorUuid;
    private static Object POS, NAME, COLOR;

    private Atlas() {
    }

    private static boolean ready() {
        if (available != null) {
            return available;
        }
        try {
            ClassLoader cl = HalfAGod.class.getClassLoader();
            try {
                Class.forName("folk.sisby.surveyor.landmark.WorldLandmarks", false, cl);
            } catch (ClassNotFoundException e) {
                cl = Thread.currentThread().getContextClassLoader();
            }
            Class<?> wl = Class.forName("folk.sisby.surveyor.landmark.WorldLandmarks", true, cl);
            Class<?> lm = Class.forName("folk.sisby.surveyor.landmark.Landmark", true, cl);
            Class<?> types = Class.forName("folk.sisby.surveyor.landmark.component.LandmarkComponentTypes", true, cl);
            Class<?> builder = Class.forName("folk.sisby.surveyor.landmark.component.LandmarkComponentMap$Builder", true, cl);
            for (Method m : wl.getMethods()) {
                if (m.getName().equals("of") && m.getParameterCount() == 1) worldLandmarksOf = m;
                if (m.getName().equals("put") && m.getParameterCount() == 1) put = m;
                if (m.getName().equals("remove") && m.getParameterCount() == 2) remove = m;
                if (m.getName().equals("asMap") && m.getParameterCount() == 2) asMap = m;
            }
            Class<?> sv = Class.forName("folk.sisby.surveyor.Surveyor", true, cl);
            for (Method m : sv.getMethods()) {
                if (m.getName().equals("getUuid") && m.getParameterCount() == 1) surveyorUuid = m;
            }
            for (Method m : lm.getMethods()) {
                if (m.getName().equals("create") && m.getParameterCount() == 3) create = m;
            }
            for (Method m : builder.getMethods()) {
                if (m.getName().equals("add") && m.getParameterCount() == 2) builderAdd = m;
            }
            Field f;
            f = types.getField("POS");
            POS = f.get(null);
            f = types.getField("NAME");
            NAME = f.get(null);
            f = types.getField("COLOR");
            COLOR = f.get(null);
            available = worldLandmarksOf != null && put != null && remove != null && create != null && builderAdd != null;
        } catch (Throwable t) {
            HalfAGod.LOG.warn("Half a God: Antique Atlas marker update failed", t);
        }
        if (available) {
            HalfAGod.LOG.info("Half a God: quest markers will show on Antique Atlas (Surveyor found).");
        }
        return available;
    }

    /** Surveyor files the singleplayer host's own landmarks under a special "host" id, not the player's UUID. */
    private static UUID owner(ServerPlayer player) {
        try {
            if (surveyorUuid != null) {
                Object o = surveyorUuid.invoke(null, player);
                if (o instanceof UUID u) {
                    return u;
                }
            }
        } catch (Throwable ignored) {
        }
        return player.getUUID();
    }

    public static void tick(ServerPlayer player) {
        if (!ready() || player.getServer() == null) {
            return;
        }
        try {
            UUID owner = owner(player);
            Map<ResourceLocation, String> placed = PLACED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
            Map<ResourceLocation, Object[]> want = new HashMap<>();
            for (Object progress : Meb.rawQuests(player)) {
                if (!Meb.isOpen(progress)) {
                    continue;
                }
                String quest = Meb.questIdOf(progress);
                String[] info = StoryData.INSTANCE.markers.get(quest);
                BlockPos pos = Meb.targetPos(progress);
                if (info == null || pos == null) {
                    continue;
                }
                ServerLevel level = null;
                UUID primary = Meb.primaryThreat(progress);
                if (primary != null) {
                    for (ServerLevel l : player.getServer().getAllLevels()) {
                        Entity e = l.getEntity(primary);
                        if (e != null) {
                            level = l;
                            pos = e.blockPosition();
                            break;
                        }
                    }
                }
                if (level == null) {
                    level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(info[0])));
                }
                if (level == null) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "quest/" + quest.substring(quest.indexOf(':') + 1));
                want.put(id, new Object[]{level, pos, info[1]});
            }
            if (CLEANED.add(player.getUUID())) {
                for (ServerLevel l : player.getServer().getAllLevels()) {
                    cleanStale(l, owner, want.keySet());
                }
                placed.clear();
            }
            // remove markers for quests that are done
            for (ResourceLocation id : new HashSet<>(placed.keySet())) {
                String where = placed.get(id);
                Object[] w = want.get(id);
                if (w == null || !where.equals(key(w))) {
                    Level l = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(where.split("@")[0])));
                    if (l != null) {
                        remove.invoke(worldLandmarksOf.invoke(null, l), owner, id);
                    }
                    placed.remove(id);
                }
            }
            // add or move the rest
            for (Map.Entry<ResourceLocation, Object[]> e : want.entrySet()) {
                String k = key(e.getValue());
                if (k.equals(placed.get(e.getKey()))) {
                    continue;
                }
                ServerLevel level = (ServerLevel) e.getValue()[0];
                BlockPos pos = (BlockPos) e.getValue()[1];
                String title = (String) e.getValue()[2];
                UnaryOperator<Object> components = b -> {
                    try {
                        builderAdd.invoke(b, POS, pos);
                        builderAdd.invoke(b, NAME, Component.literal(title));
                        builderAdd.invoke(b, COLOR, 0xF2C14E);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    return b;
                };
                Object landmark = create.invoke(null, owner, e.getKey(), components);
                put.invoke(worldLandmarksOf.invoke(null, level), landmark);
                placed.put(e.getKey(), k);
            }
        } catch (Throwable t) {
            HalfAGod.LOG.warn("Half a God: Antique Atlas marker update failed", t);
            HalfAGod.LOG.warn("Half a God: Antique Atlas markers turned off ({})", t.toString());
        }
    }

    private static String key(Object[] w) {
        return ((ServerLevel) w[0]).dimension().location() + "@" + ((BlockPos) w[1]).asLong();
    }

    @SuppressWarnings("unchecked")
    private static void cleanStale(ServerLevel level, UUID owner, Set<ResourceLocation> keep) throws Exception {
        if (asMap == null) {
            return;
        }
        Object landmarks = worldLandmarksOf.invoke(null, level);
        if (landmarks == null) {
            return;
        }
        Object map = asMap.invoke(landmarks, owner, null);
        if (map instanceof Map<?, ?> m) {
            for (Object id : new HashSet<>(m.keySet())) {
                if (id instanceof ResourceLocation rl && rl.getNamespace().equals(HalfAGod.MODID) && !keep.contains(rl)) {
                    remove.invoke(landmarks, owner, rl);
                }
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        PLACED.remove(event.getEntity().getUUID());
        CLEANED.remove(event.getEntity().getUUID());
    }
}

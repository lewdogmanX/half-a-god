package dev.halfagod.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Story characters live somewhere and stay there.
 *
 * Mebahel spawns a fresh villager for every quest that needs to talk to someone, wherever its search lands (sometimes
 * next to you in a field). This moves each one to the village their character lives in (Ashford, your home village,
 * the snowy village...), stands them where the last copy of them stood, removes the old copy, and points the quest
 * marker at them. A character's village is fixed the first time you meet them.
 */
public final class Cast {
    public record Entry(String quest, int stage, String name, String place, String structure, String adopt) {
    }

    private static final String DONE = "hag_cast_done";
    private static final String TAG = "hag_cast:";

    private Cast() {
    }

    public static void tick(ServerPlayer player) {
        Map<String, List<Entry>> cast = StoryData.INSTANCE.cast;
        if (cast.isEmpty()) {
            return;
        }
        for (Object progress : Meb.rawQuests(player)) {
            if (!Meb.isOpen(progress)) {
                continue;
            }
            List<Entry> entries = cast.get(Meb.questIdOf(progress));
            if (entries == null) {
                continue;
            }
            for (UUID uuid : Meb.threatUuids(progress)) {
                Entity e = find(player, uuid);
                if (!(e instanceof Mob mob) || !mob.isAlive() || mob.getTags().contains(DONE)) {
                    continue;
                }
                String name = mob.hasCustomName() && mob.getCustomName() != null ? mob.getCustomName().getString() : "";
                for (Entry entry : entries) {
                    if (entry.name().equals(name)) {
                        try {
                            settle(player, progress, mob, entry);
                        } catch (Exception ex) {
                            dev.halfagod.HalfAGod.LOG.warn("Half a God could not place {}: {}", name, ex.toString());
                        }
                        mob.addTag(DONE);
                        break;
                    }
                }
            }
        }
    }

    private static Entity find(ServerPlayer player, UUID uuid) {
        if (player.getServer() == null) {
            return null;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static void settle(ServerPlayer player, Object progress, Mob mob, Entry entry) {
        ServerLevel level = (ServerLevel) mob.level();
        // A Cult of Azazel resident (the Preacher, the Welcomer...): the quest's one takes the place of the one who lives there
        if (entry.adopt() != null && !entry.adopt().isEmpty()) {
            for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(48),
                    o -> o != mob && o.isAlive() && o.getType() == mob.getType() && !Meb.isQuestThreat(o) && !o.hasCustomName())) {
                mob.moveTo(other.getX(), other.getY(), other.getZ(), other.getYRot(), other.getXRot());
                other.discard();
                Meb.retarget(player, progress, mob.getUUID(), mob.blockPosition());
                return;
            }
            return;
        }
        CastData data = CastData.get(level.getServer());
        String castKey = player.getUUID() + "|" + (entry.place().startsWith("char:") ? entry.place() : entry.place() + "|" + entry.name());

        CastData.Place home = data.place(player.getUUID(), entry.place());
        if (home == null) {
            home = new CastData.Place(level.dimension().location(), findVillage(level, mob.blockPosition(), entry.structure()));
            data.setPlace(player.getUUID(), entry.place(), home);
        }
        ServerLevel homeLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, home.dimension()));
        if (homeLevel == null || homeLevel != level) {
            homeLevel = level;
        }

        // Where this character stands: where their previous copy stood, or their own spot in the village.
        BlockPos spot = null;
        float yaw = mob.getYRot();
        UUID previous = data.current(castKey);
        if (previous != null && !previous.equals(mob.getUUID())) {
            Entity old = find(player, previous);
            if (old != null && old.isAlive() && !stillNeeded(player, previous)) {
                if (old.level() == homeLevel && old.blockPosition().closerThan(home.pos(), 64)) {
                    spot = old.blockPosition();
                    yaw = old.getYRot();
                }
                old.discard();
            }
        }
        data.setCurrent(castKey, mob.getUUID());
        mob.addTag(TAG + castKey);
        mob.setPersistenceRequired();

        if (spot == null) {
            if (mob.blockPosition().closerThan(home.pos(), 48)) {
                return; // already in their village
            }
            spot = standingSpot(homeLevel, home.pos(), entry.name());
        }
        homeLevel.getChunk(spot.getX() >> 4, spot.getZ() >> 4);
        mob.getNavigation().stop();
        mob.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        Meb.retarget(player, progress, mob.getUUID(), spot);
    }

    /** True if an open quest still needs this exact entity (then it isn't replaced). */
    private static boolean stillNeeded(ServerPlayer player, UUID uuid) {
        for (Object p : Meb.rawQuests(player)) {
            if (Meb.isOpen(p) && Meb.threatUuids(p).contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    /** The middle of the village this position is in, or of the nearest village (of the right kind) to it. */
    private static BlockPos findVillage(ServerLevel level, BlockPos pos, String structureId) {
        StructureStart here = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        if (here.isValid() && (structureId == null || structureId.isEmpty() || structureId.equals("minecraft:villages") || matches(level, here, structureId))) {
            return here.getBoundingBox().getCenter();
        }
        try {
            com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> found;
            if (structureId != null && !structureId.isEmpty() && !structureId.equals("minecraft:villages")) {
                var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                var holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(structureId)));
                if (holder.isEmpty()) {
                    return pos;
                }
                found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder.get()), pos, 100, false);
            } else {
                var tag = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE);
                if (tag.isEmpty()) {
                    return pos;
                }
                found = level.getChunkSource().getGenerator().findNearestMapStructure(level, tag.get(), pos, 100, false);
            }
            return found == null ? pos : found.getFirst();
        } catch (Exception ex) {
            return pos;
        }
    }

    private static boolean matches(ServerLevel level, StructureStart start, String structureId) {
        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
        return id != null && id.toString().equals(structureId);
    }

    /** A spot on open ground near the village middle; each character gets their own, so they don't all stand in a heap. */
    private static BlockPos standingSpot(ServerLevel level, BlockPos centre, String name) {
        int h = Math.abs(name.hashCode());
        for (int ring = 0; ring < 4; ring++) {
            for (int i = 0; i < 12; i++) {
                double angle = ((h % 12) + i) * Math.PI / 6;
                int r = 5 + ring * 4 + (h % 3);
                int x = centre.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = centre.getZ() + (int) Math.round(Math.sin(angle) * r);
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                BlockState ground = level.getBlockState(pos.below());
                if (isGround(ground) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                    return pos;
                }
            }
        }
        level.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        return new BlockPos(centre.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ()), centre.getZ());
    }

    private static boolean isGround(BlockState s) {
        return s.is(Blocks.DIRT_PATH) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.MOSS_BLOCK);
    }

    /** An older copy of a character that comes back into the world after being replaced is removed. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        for (String tag : mob.getTags()) {
            if (tag.startsWith(TAG)) {
                UUID now = CastData.get(((ServerLevel) event.getLevel()).getServer()).current(tag.substring(TAG.length()));
                if (now != null && !now.equals(mob.getUUID())) {
                    event.setCanceled(true);
                }
                return;
            }
        }
    }
}

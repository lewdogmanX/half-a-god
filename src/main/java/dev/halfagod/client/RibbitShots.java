package dev.halfagod.client;

import dev.halfagod.HalfAGod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * CI only, with the Ribbits mod installed (the Mebahel check): find a real Ribbit village, walk in (the Ribbits' Song
 * starts), find Grandmother Pondwhistle among her village's Ribbits, and talk to her on Mebahel's screen.
 */
final class RibbitShots {
    private static BlockPos village;
    private static volatile boolean ready;
    private static volatile boolean asking;

    private RibbitShots() {
    }

    private static ServerLevel overworld() {
        return Minecraft.getInstance().getSingleplayerServer().overworld();
    }

    static void add() {
        ClientShots.step(20, () -> Minecraft.getInstance().setScreen(null));
        ClientShots.step(1, () -> ClientShots.server(sp -> {
            sp.setGameMode(GameType.CREATIVE);
            ServerLevel level = sp.serverLevel();
            level.setDayTime(6000);
            var h = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse("ribbits:ribbit_village")));
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(h), sp.blockPosition(), 40, false);
            village = found == null ? null : found.getFirst();
            HalfAGod.LOG.info("SHOT ribbit village: {}", village);
            if (village != null) {
                int cx = SectionPos.blockToSectionCoord(village.getX()), cz = SectionPos.blockToSectionCoord(village.getZ());
                for (int x = cx - 3; x <= cx + 3; x++) {
                    for (int z = cz - 3; z <= cz + 3; z++) {
                        level.setChunkForced(x, z, true);
                    }
                }
            }
        }));
        for (int k = 0; k < 3; k++) {
            ClientShots.until(RibbitShots::loaded);
        }
        // into the middle of the village (the questline starts as you walk in)
        ClientShots.step(1, () -> ClientShots.server(sp -> {
            if (village == null) {
                return;
            }
            ServerLevel level = sp.serverLevel();
            Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("ribbits:ribbit_village"));
            StructureStart st = level.getChunk(SectionPos.blockToSectionCoord(village.getX()), SectionPos.blockToSectionCoord(village.getZ())).getStartForStructure(s);
            BlockPos c = st != null && st.isValid() ? st.getBoundingBox().getCenter() : village;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            sp.teleportTo(level, c.getX() + 0.5, y + 1, c.getZ() + 0.5, 0, 0);
            HalfAGod.LOG.info("SHOT in the ribbit village at {}", c);
        }));
        ClientShots.step(300, () -> {
        });
        // Grandmother Pondwhistle: stand in front of her
        ClientShots.step(1, () -> ClientShots.server(sp -> {
            Entity gran = null;
            for (Entity e : sp.serverLevel().getEntities(sp, sp.getBoundingBox().inflate(96))) {
                if (e.hasCustomName() && e.getCustomName().getString().equals("Grandmother Pondwhistle")) {
                    gran = e;
                }
            }
            HalfAGod.LOG.info("SHOT grandmother: {} {}", gran == null ? "not found" : gran.getType(), gran == null ? "" : gran.blockPosition());
            if (gran != null) {
                double a = Math.toRadians(gran.getYRot() + 90);
                double x = gran.getX() + Math.cos(a) * 2.6, z = gran.getZ() + Math.sin(a) * 2.6;
                double dx = gran.getX() - x, dz = gran.getZ() - z;
                sp.teleportTo(sp.serverLevel(), x, gran.getY(), z, (float) Math.toDegrees(Math.atan2(-dx, dz)), 12f);
            }
        }));
        ClientShots.step(60, () -> {
        });
        ClientShots.step(10, () -> ClientShots.shot("23_ribbit_grandmother"));
        ClientShots.step(30, () -> {
            Minecraft mc = Minecraft.getInstance();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.hasCustomName() && e.getCustomName().getString().equals("Grandmother Pondwhistle")) {
                    mc.gameMode.interact(mc.player, e, InteractionHand.MAIN_HAND);
                    break;
                }
            }
        });
        ClientShots.step(10, () -> ClientShots.shot("23b_ribbit_talk"));
        ClientShots.step(20, () -> ClientShots.click(1));
        ClientShots.step(10, () -> ClientShots.shot("23c_ribbit_talk_next"));
        ClientShots.step(20, () -> Minecraft.getInstance().setScreen(null));
    }

    private static boolean loaded() {
        if (village == null || ready) {
            return true;
        }
        if (!asking) {
            asking = true;
            ClientShots.server(sp -> {
                int cx = SectionPos.blockToSectionCoord(village.getX()), cz = SectionPos.blockToSectionCoord(village.getZ());
                boolean all = true;
                for (int x = cx - 3; x <= cx + 3 && all; x++) {
                    for (int z = cz - 3; z <= cz + 3 && all; z++) {
                        all = overworld().getChunkSource().hasChunk(x, z);
                    }
                }
                ready = all;
                asking = false;
            });
        }
        return false;
    }
}

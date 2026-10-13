package dev.halfagod.client;

import dev.halfagod.HalfAGod;
import dev.halfagod.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * CI only (-Dhalfagod.clientshots=true -Dhalfagod.shots=moon, with Create: Cosmonautics installed): finds the moon
 * storyline's three places where they really generate (the observatory on a mountain, the wreck and Iskar on the moon),
 * photographs each from round about (and the observatory from inside), then the Eclipse in hand, raised, and its tooltip.
 */
final class MoonShots {
    private static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("rocketnautics:moon"));
    private static final Map<String, BoundingBox> BOXES = new HashMap<>();
    private static final Map<String, ResourceKey<Level>> WHERE = new HashMap<>();
    private static volatile boolean ready;

    private MoonShots() {
    }

    static void init() {
        ClientShots.step(60, () -> ClientShots.server(sp -> {
            sp.setGameMode(GameType.CREATIVE);
            ClientShots.fly(sp, true);
            MinecraftServer s = sp.getServer();
            s.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, s);
            s.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, s);
            s.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, s);
            s.overworld().setDayTime(5000);
            s.overworld().setWeatherParameters(12000, 0, false, false);
        }));
        ClientShots.step(10, () -> Minecraft.getInstance().options.hideGui = true);
        visit(Level.OVERWORLD, "half_a_god:observatory", "30_observatory", 0.95, 9, 40, 130, 220, 310);
        inside("half_a_god:observatory", "30i_observatory_inside");
        visit(MOON, "half_a_god:faithful_wreck", "31_wreck", 0.85, 9, 30, 120, 210, 300);
        ClientShots.step(1, () -> ClientShots.server(sp -> closeUp(sp, "half_a_god:faithful_wreck", 200, 0.42, 4)));
        ClientShots.step(140, () -> {
        });
        ClientShots.step(10, () -> ClientShots.shot("31x_wreck_close"));
        visit(MOON, "half_a_god:iskars_rest", "32_iskar", 0.75, 26, 20, 110, 200, 290);
        ClientShots.step(1, () -> ClientShots.server(sp -> closeUp(sp, "half_a_god:iskars_rest", 160, 0.4, 10)));
        ClientShots.step(140, () -> {
        });
        ClientShots.step(10, () -> ClientShots.shot("32x_iskar_close"));
        // the Eclipse: in hand on the crater's rim, raised, and its tooltip
        ClientShots.step(10, () -> Minecraft.getInstance().options.hideGui = false);
        ClientShots.step(30, () -> ClientShots.server(sp -> {
            sp.getInventory().selected = 0;
            sp.getInventory().setItem(0, new ItemStack(ModItems.ECLIPSE.get()));
            sp.inventoryMenu.broadcastChanges();
            closeUp(sp, "half_a_god:iskars_rest", 250, 0.55, 6);
        }));
        ClientShots.step(60, () -> {
        });
        ClientShots.step(10, () -> ClientShots.shot("33_eclipse_in_hand"));
        ClientShots.step(12, () -> ClientShots.server(sp -> ModItems.ECLIPSE.get().use(sp.serverLevel(), sp, InteractionHand.MAIN_HAND)));
        ClientShots.step(10, () -> ClientShots.shot("33b_eclipse_raised"));
        ClientShots.step(200, () -> {
        });
        ClientShots.step(20, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new InventoryScreen(mc.player));
        });
        ClientShots.step(10, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof InventoryScreen inv) {
                double scale = mc.getWindow().getGuiScale();
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), (inv.getGuiLeft() + 16) * scale, (inv.getGuiTop() + 150) * scale);
            }
        });
        ClientShots.step(10, () -> ClientShots.shot("33c_eclipse_tooltip"));
        ClientShots.step(20, () -> Minecraft.getInstance().setScreen(null));
        ClientShots.step(20, () -> Minecraft.getInstance().stop());
    }

    /** find where a structure really generates (or, failing that, put one down), fly round it and take pictures */
    private static void visit(ResourceKey<Level> dim, String id, String name, double dist, int up, int... angles) {
        ClientShots.step(1, () -> {
            ready = false;
            ClientShots.server(sp -> {
                try {
                    find(sp, dim, id);
                } catch (Exception e) {
                    HalfAGod.LOG.error("SHOT could not find {}", id, e);
                }
                ready = true;
            });
        });
        for (int k = 0; k < 4; k++) {
            ClientShots.until(() -> ready);   // (each waits up to a minute; generating a crater's worth of moon takes a while)
        }
        for (int i = 0; i < angles.length; i++) {
            int a = angles[i];
            ClientShots.step(1, () -> ClientShots.server(sp -> view(sp, id, a, dist, up)));
            ClientShots.step(i == 0 ? 320 : 100, () -> {
            });
            ClientShots.step(10, () -> ClientShots.shot(name + "_" + a));
        }
    }

    private static void find(ServerPlayer sp, ResourceKey<Level> dim, String id) {
        ServerLevel level = sp.getServer().getLevel(dim);
        if (level == null) {
            HalfAGod.LOG.error("SHOT no dimension {}", dim.location());
            return;
        }
        Holder<Structure> h = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
        BlockPos from = dim == Level.OVERWORLD ? sp.blockPosition() : BlockPos.ZERO;
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(h), from, 160, false);
        StructureStart start = null;
        if (found != null) {
            BlockPos p = found.getFirst();
            start = level.getChunk(SectionPos.blockToSectionCoord(p.getX()), SectionPos.blockToSectionCoord(p.getZ())).getStartForStructure(h.value());
            HalfAGod.LOG.info("SHOT {} generates at {}", id, p);
        }
        if (start == null || !start.isValid()) {
            // not found nearby: put one down the way the /place command does
            BlockPos p = from.offset(600, 0, 600);
            var gen = level.getChunkSource().getGenerator();
            start = h.value().generate(level.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(),
                    level.getStructureManager(), level.getSeed(), new ChunkPos(p), 0, level, b -> true);
            HalfAGod.LOG.info("SHOT {} not found nearby; placing one at {} (valid: {})", id, p, start.isValid());
            if (!start.isValid()) {
                return;
            }
            BoundingBox b = start.getBoundingBox();
            StructureStart st = start;
            ChunkPos.rangeClosed(new ChunkPos(SectionPos.blockToSectionCoord(b.minX()), SectionPos.blockToSectionCoord(b.minZ())),
                    new ChunkPos(SectionPos.blockToSectionCoord(b.maxX()), SectionPos.blockToSectionCoord(b.maxZ()))).forEach(cp -> {
                level.getChunk(cp.x, cp.z);
                st.placeInChunk(level, level.structureManager(), gen, level.getRandom(),
                        new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), level.getMaxBuildHeight(), cp.getMaxBlockZ()), cp);
            });
        }
        BoundingBox b = start.getBoundingBox();
        // make sure every chunk it touches (and a ring round it) is generated, so it's all there to see
        for (int cx = SectionPos.blockToSectionCoord(b.minX()) - 2; cx <= SectionPos.blockToSectionCoord(b.maxX()) + 2; cx++) {
            for (int cz = SectionPos.blockToSectionCoord(b.minZ()) - 2; cz <= SectionPos.blockToSectionCoord(b.maxZ()) + 2; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BOXES.put(id, b);
        WHERE.put(id, dim);
        HalfAGod.LOG.info("SHOT {} box {}", id, b);
    }

    private static void lookFrom(ServerPlayer sp, ServerLevel level, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        sp.teleportTo(level, x, y, z, yaw, pitch);
    }

    /** from round about, above it, looking at its middle */
    private static void view(ServerPlayer sp, String id, int angle, double dist, int up) {
        BoundingBox b = BOXES.get(id);
        ServerLevel level = WHERE.containsKey(id) ? sp.getServer().getLevel(WHERE.get(id)) : null;
        if (b == null || level == null) {
            return;
        }
        double cx = (b.minX() + b.maxX()) / 2.0 + 0.5, cz = (b.minZ() + b.maxZ()) / 2.0 + 0.5;
        double ty = b.minY() + (b.maxY() - b.minY()) * 0.35;
        double r = Math.max(b.getXSpan(), b.getZSpan()) * dist;
        double a = Math.toRadians(angle);
        double x = cx + r * Math.cos(a), z = cz + r * Math.sin(a);
        level.getChunk(SectionPos.blockToSectionCoord((int) x), SectionPos.blockToSectionCoord((int) z));
        double y = Math.max(b.maxY() + up, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z) + 4);
        lookFrom(sp, level, x, y, z, cx, ty, cz);
    }

    /** lower and nearer, from the crater's edge */
    private static void closeUp(ServerPlayer sp, String id, int angle, double dist, int up) {
        BoundingBox b = BOXES.get(id);
        ServerLevel level = WHERE.containsKey(id) ? sp.getServer().getLevel(WHERE.get(id)) : null;
        if (b == null || level == null) {
            return;
        }
        double cx = (b.minX() + b.maxX()) / 2.0 + 0.5, cz = (b.minZ() + b.maxZ()) / 2.0 + 0.5;
        double r = Math.max(b.getXSpan(), b.getZSpan()) * dist;
        double a = Math.toRadians(angle);
        double x = cx + r * Math.cos(a), z = cz + r * Math.sin(a);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
        double gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) cx, (int) cz);
        lookFrom(sp, level, x, ground + up, z, cx, gy, cz);
    }

    /** inside the observatory: by the telescope's pier, across the study, and up into the dome */
    private static void inside(String id, String name) {
        final BlockPos[] pier = new BlockPos[1];
        ClientShots.step(1, () -> ClientShots.server(sp -> {
            BoundingBox b = BOXES.get(id);
            if (b == null) {
                return;
            }
            ServerLevel level = sp.getServer().getLevel(WHERE.get(id));
            for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                if (level.getBlockState(p).is(Blocks.CHISELED_POLISHED_BLACKSTONE)) {
                    pier[0] = p.immutable();
                    break;
                }
            }
            HalfAGod.LOG.info("SHOT observatory pier {}", pier[0]);
            if (pier[0] == null) {
                return;
            }
            // stand off the pier, on the side that's clear, and look across the room
            for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, -2}}) {
                BlockPos at = pier[0].offset(d[0], 0, d[1]);
                if (level.getBlockState(at.above()).isAir() && level.getBlockState(at.above(2)).isAir()) {
                    lookFrom(sp, level, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5,
                            pier[0].getX() - d[0] * 2 + 0.5, pier[0].getY() + 1.5, pier[0].getZ() - d[1] * 2 + 0.5);
                    break;
                }
            }
        }));
        ClientShots.step(120, () -> {
        });
        ClientShots.step(10, () -> ClientShots.shot(name));
        ClientShots.step(5, () -> ClientShots.server(sp -> sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot() + 180f, -62f)));
        ClientShots.step(40, () -> {
        });
        ClientShots.step(10, () -> ClientShots.shot(name + "_dome"));
        ClientShots.step(5, () -> ClientShots.server(sp -> {
            if (pier[0] != null) {
                ServerLevel level = sp.serverLevel();
                lookFrom(sp, level, pier[0].getX() + 0.5, pier[0].getY() + 8.5, pier[0].getZ() + 3.5, pier[0].getX() + 0.5, pier[0].getY() + 4, pier[0].getZ() - 4.5);
            }
        }));
        ClientShots.step(60, () -> {
        });
        ClientShots.step(10, () -> ClientShots.shot(name + "_gallery"));
    }
}

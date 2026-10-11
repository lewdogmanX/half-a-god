package dev.halfagod.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Watch's court and its obsidian cell in the court dimension, one set per player (each player has a slot).
 * The courtroom: a judge's bench, the Watch Captain, four jurors, a gallery, and an iron-barred dock in the middle.
 * The cell: three by three, obsidian, a bed, a barred window over a lava lake, and a gaoler on the other side of the bars.
 */
public final class CourtBuilder {
    public static final int VERSION = 1;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final String NPC = "hag_court";

    /** where the accused stands (relative to the slot's origin), and the middle of the cell */
    public static final BlockPos DOCK = new BlockPos(0, 0, 2);
    public static final BlockPos CELL = new BlockPos(0, 0, 40);
    public static final BlockPos CELL_SPOT = CELL.offset(0, 0, -1);

    private CourtBuilder() {
    }

    public static BlockPos origin(int slot) {
        return new BlockPos(slot * 128, 64, 0);
    }

    private record Npc(String role, String name, VillagerProfession profession, double x, double y, double z, float yaw) {
    }

    private static List<Npc> npcs() {
        List<Npc> out = new ArrayList<>();
        out.add(new Npc("judge", "The Judge", VillagerProfession.CLERIC, 0.5, 2, -8.5, 0f));
        out.add(new Npc("captain", "The Watch Captain", VillagerProfession.WEAPONSMITH, -2.5, 0, -5.5, 180f));
        String[] jurors = {"Juror", "Juror", "Juror", "Juror"};
        VillagerProfession[] jp = {VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON, VillagerProfession.SHEPHERD};
        for (int i = 0; i < 4; i++) {
            out.add(new Npc("juror" + i, jurors[i], jp[i], 5.5, 0, -4.5 + i, 90f));
        }
        out.add(new Npc("gaoler", "The Gaoler", VillagerProfession.TOOLSMITH, CELL.getX() + 4.5, 0, CELL.getZ() + 0.5, 90f));
        return out;
    }

    // ------------------------------------------------------------------ building
    public static void ensure(ServerLevel level, int slot) {
        CourtData data = CourtData.get(level);
        if (data.builtVersion(slot) >= VERSION) {
            return;
        }
        BlockPos o = origin(slot);
        for (int cx = (o.getX() - 16) >> 4; cx <= (o.getX() + 16) >> 4; cx++) {
            for (int cz = (o.getZ() - 16) >> 4; cz <= (o.getZ() + 64) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        List<BlockPos> shapes = new ArrayList<>();
        courtroom(level, o, shapes);
        cell(level, o.offset(CELL), shapes);
        for (BlockPos p : shapes) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), FLAGS);
        }
        data.setBuilt(slot, VERSION);
    }

    private static void set(ServerLevel level, BlockPos o, int x, int y, int z, BlockState s) {
        level.setBlock(o.offset(x, y, z), s, FLAGS);
    }

    private static void fill(ServerLevel level, BlockPos o, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    set(level, o, x, y, z, s);
                }
            }
        }
    }

    private static void courtroom(ServerLevel level, BlockPos o, List<BlockPos> shapes) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // shell: walls, floor and ceiling, then hollow it out
        fill(level, o, -7, -1, -11, 7, 7, 9, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        fill(level, o, -6, 0, -10, 6, 6, 8, air);
        fill(level, o, -7, -1, -11, 7, -1, 9, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        fill(level, o, -7, 7, -11, 7, 7, 9, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        fill(level, o, -1, -1, -7, 1, -1, 8, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        // pillars along the walls
        for (int z = -11; z <= 9; z += 4) {
            fill(level, o, -7, 0, z, -7, 6, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            fill(level, o, 7, 0, z, 7, 6, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        // the bench: a raised platform for the judge, a high front, candles on top
        fill(level, o, -3, 0, -10, 3, 1, -9, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        fill(level, o, -3, 0, -8, 3, 2, -8, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        BlockState candles = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
        set(level, o, -3, 3, -8, candles);
        set(level, o, 3, 3, -8, candles);
        // the Watch's mark behind the bench: an iron eye that never closes
        fill(level, o, -2, 3, -11, 2, 5, -11, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        set(level, o, 0, 4, -11, Blocks.GOLD_BLOCK.defaultBlockState());
        set(level, o, -1, 4, -11, Blocks.IRON_BLOCK.defaultBlockState());
        set(level, o, 1, 4, -11, Blocks.IRON_BLOCK.defaultBlockState());
        // the jury box
        for (int z = -6; z <= -1; z++) {
            set(level, o, 4, 0, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            shapes.add(o.offset(4, 0, z));
        }
        // the gallery
        BlockState bench = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int z : new int[]{5, 7}) {
            for (int x = -6; x <= 6; x++) {
                if (Math.abs(x) >= 2) {
                    set(level, o, x, 0, z, bench);
                    shapes.add(o.offset(x, 0, z));
                }
            }
        }
        // the dock: iron bars all round, two high
        set(level, o, DOCK.getX(), -1, DOCK.getZ(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        for (int x = -1; x <= 1; x++) {
            for (int z = DOCK.getZ() - 1; z <= DOCK.getZ() + 1; z++) {
                if (x == 0 && z == DOCK.getZ()) {
                    continue;
                }
                for (int y = 0; y <= 1; y++) {
                    set(level, o, x, y, z, Blocks.IRON_BARS.defaultBlockState());
                    shapes.add(o.offset(x, y, z));
                }
            }
        }
        // light
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int[] l : new int[][]{{-4, -6}, {4, -6}, {-4, 0}, {4, 0}, {-4, 6}, {4, 6}, {0, -4}, {0, 5}}) {
            set(level, o, l[0], 6, l[1], lantern);
        }
    }

    private static void cell(ServerLevel level, BlockPos c, List<BlockPos> shapes) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        // a basalt pillar out of the lava, with the cell on top
        fill(level, c, -2, -14, -2, 2, -2, 2, Blocks.BASALT.defaultBlockState());
        fill(level, c, -2, -1, -2, 2, 3, 2, obsidian);
        fill(level, c, -1, 0, -1, 1, 2, 1, air);
        // the lava lake below the window
        fill(level, c, -9, -11, 3, 9, -11, 20, Blocks.BASALT.defaultBlockState());
        fill(level, c, -9, -10, 3, 9, -10, 20, Blocks.BASALT.defaultBlockState());
        fill(level, c, -8, -10, 4, 8, -10, 19, Blocks.LAVA.defaultBlockState());
        // the bed (head to the north) and a lantern
        set(level, c, -1, 0, 0, Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        set(level, c, -1, 0, 1, Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        set(level, c, 1, 2, -1, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // the slit window over the lava, and the bars to the gaoler's room
        set(level, c, 0, 1, 2, Blocks.IRON_BARS.defaultBlockState());
        shapes.add(c.offset(0, 1, 2));
        set(level, c, 2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
        shapes.add(c.offset(2, 1, 0));
        // the gaoler's room, with the evidence chest
        fill(level, c, 3, -1, -3, 6, 3, 3, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        fill(level, c, 3, 0, -2, 5, 2, 2, air);
        fill(level, c, 3, -2, -3, 6, -2, 3, Blocks.BASALT.defaultBlockState());
        set(level, c, 5, 0, -2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        set(level, c, 4, 2, 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    // ------------------------------------------------------------------ the loose block
    /** Wall blocks you could stand in front of and dig at (not the gaoler's side, not behind the bed). */
    public static List<BlockPos> escapeCandidates() {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 0; y <= 2; y++) {
            for (int i = -1; i <= 1; i++) {
                if (!(y == 0 && (i == 0 || i == 1))) {
                    out.add(new BlockPos(-2, y, i));          // west wall (not behind the bed)
                }
                out.add(new BlockPos(i, y, -2));              // north wall
                if (!(i == 0 && y == 1)) {
                    out.add(new BlockPos(i, y, 2));           // south wall (not the window)
                }
            }
        }
        return out;
    }

    /** Puts the cell back together and loosens one block in its walls (returns where, absolute). */
    public static BlockPos loosen(ServerLevel level, int slot, int pick) {
        BlockPos c = origin(slot).offset(CELL);
        List<BlockPos> all = escapeCandidates();
        for (BlockPos p : all) {
            level.setBlock(c.offset(p), Blocks.OBSIDIAN.defaultBlockState(), FLAGS);
        }
        // the bed, in case anything happened to it
        level.setBlock(c.offset(-1, 0, 0), Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD), FLAGS);
        level.setBlock(c.offset(-1, 0, 1), Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT), FLAGS);
        BlockPos loose = c.offset(all.get(Math.floorMod(pick, all.size())));
        level.setBlock(loose, Blocks.BLACK_CONCRETE.defaultBlockState(), FLAGS);
        return loose;
    }

    // ------------------------------------------------------------------ the people
    /** Puts the court's people in their places (once their part of the world is loaded), one of each. */
    public static void ensureNpcs(ServerLevel level, int slot) {
        BlockPos o = origin(slot);
        for (Npc n : npcs()) {
            BlockPos at = o.offset((int) Math.floor(n.x()), (int) n.y(), (int) Math.floor(n.z()));
            if (!level.isPositionEntityTicking(at)) {
                continue;
            }
            String tag = NPC + "_" + n.role();
            List<Villager> found = level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(24), v -> v.getTags().contains(tag));
            for (int i = 1; i < found.size(); i++) {
                found.get(i).discard();
            }
            Villager v = found.isEmpty() ? null : found.get(0);
            if (v == null) {
                v = EntityType.VILLAGER.create(level);
                if (v == null) {
                    continue;
                }
                v.setNoAi(true);
                v.setInvulnerable(true);
                v.setPersistenceRequired();
                v.setSilent(true);
                v.setCustomName(Component.literal(n.name()));
                v.setCustomNameVisible(true);
                v.setVillagerData(v.getVillagerData().setProfession(n.profession()).setLevel(5));
                v.addTag(NPC);
                v.addTag(tag);
                v.moveTo(o.getX() + n.x(), o.getY() + n.y(), o.getZ() + n.z(), n.yaw(), 0f);
                level.addFreshEntity(v);
                Meb.clearOffer(v);
            }
            // they stand where they should, facing the right way
            v.moveTo(o.getX() + n.x(), o.getY() + n.y(), o.getZ() + n.z(), n.yaw(), 0f);
            v.setYHeadRot(n.yaw());
            v.setYBodyRot(n.yaw());
        }
    }

    public static boolean isCourtNpc(Entity e) {
        return e.getTags().contains(NPC);
    }
}

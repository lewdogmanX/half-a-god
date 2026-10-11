package dev.halfagod.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the Watch's court and its prison block in the court dimension, one set per player (each player has a slot).
 *
 * The courtroom: a tall hall of deepslate and dark oak, lit by chandeliers and glowing windows. The judge sits on a
 * raised dais under the Watch's eye, with a clerk at a lectern below. A witness stand on the left, six jurors in a
 * raised box on the right, the Watch Captain on the floor, two iron golems guarding the dock, and a gallery of
 * onlookers behind you.
 *
 * The prison block: a corridor of six obsidian cells over a lava lake. Yours has a hay-bale cot, a cauldron, chains on
 * the wall and a barred window over the lava. There are other prisoners across the corridor, and the gaoler watches you.
 * There's no bed and no way out: the days pass in real time.
 */
public final class CourtBuilder {
    public static final int VERSION = 3;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final String NPC = "hag_court";

    /** where the accused stands (relative to the slot's origin), the prison block, and where you stand in your cell */
    public static final BlockPos DOCK = new BlockPos(0, 0, 2);
    public static final BlockPos CELL = new BlockPos(0, 0, 40);
    public static final BlockPos CELL_SPOT = CELL.offset(0, 0, 4);

    private CourtBuilder() {
    }

    public static BlockPos origin(int slot) {
        return new BlockPos(slot * 128, 64, 0);
    }

    // ------------------------------------------------------------------ the people
    private enum Kind { VILLAGER, GOLEM }

    private record Npc(String role, String name, boolean showName, Kind kind, VillagerProfession profession, double x, double y, double z, float yaw) {
    }

    /** the yaw that turns someone at (x, z) to face (tx, tz) */
    private static float face(double x, double z, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    private static Npc villager(String role, String name, boolean show, VillagerProfession p, double x, double y, double z, double tx, double tz) {
        return new Npc(role, name, show, Kind.VILLAGER, p, x, y, z, face(x, z, tx, tz));
    }

    private static List<Npc> npcs() {
        List<Npc> out = new ArrayList<>();
        double dx = DOCK.getX() + 0.5, dz = DOCK.getZ() + 0.5;
        // the court
        out.add(villager("judge", "The Judge", true, VillagerProfession.CLERIC, 0.5, 2, -11.5, dx, dz));
        out.add(villager("clerk", "The Clerk", true, VillagerProfession.LIBRARIAN, -2.5, 0, -9.5, -2.5, 0));
        out.add(villager("captain", "The Watch Captain", true, VillagerProfession.WEAPONSMITH, -2.5, 0, -4.5, 0.5, -11.5));
        out.add(villager("witness", "The Witness", true, VillagerProfession.NONE, -5.5, 1, -6.5, dx, dz));
        VillagerProfession[] jp = {VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON,
                VillagerProfession.SHEPHERD, VillagerProfession.FLETCHER, VillagerProfession.CARTOGRAPHER};
        for (int i = 0; i < 6; i++) {
            boolean back = i >= 3;
            out.add(villager("juror" + i, "Juror", false, jp[i], back ? 7.5 : 6.5, back ? 2 : 1, (back ? -5.5 : -6.5) + (i % 3) * 2, 0.5, -3));
        }
        out.add(new Npc("guard0", "", false, Kind.GOLEM, null, -2.5, 0, -0.5, 180f));
        out.add(new Npc("guard1", "", false, Kind.GOLEM, null, 3.5, 0, -0.5, 180f));
        double[][] gallery = {{-6.5, 7.5}, {-4.5, 9.5}, {-7.5, 11.5}, {4.5, 7.5}, {7.5, 9.5}, {3.5, 11.5}};
        VillagerProfession[] gp = {VillagerProfession.BUTCHER, VillagerProfession.NONE, VillagerProfession.FISHERMAN,
                VillagerProfession.LEATHERWORKER, VillagerProfession.NONE, VillagerProfession.ARMORER};
        for (int i = 0; i < gallery.length; i++) {
            out.add(new Npc("gallery" + i, "", false, Kind.VILLAGER, gp[i], gallery[i][0], 0.5, gallery[i][1], 180f));
        }
        // the prison block
        double sx = CELL_SPOT.getX() + 0.5, sz = CELL_SPOT.getZ() + 0.5;
        int c = CELL.getZ();
        out.add(villager("gaoler", "The Gaoler", true, VillagerProfession.TOOLSMITH, 1.5, 0, c - 0.5, sx, sz));
        out.add(villager("prisoner0", "A Prisoner", false, VillagerProfession.NITWIT, 0.5, 0, c - 5.5, sx, sz));
        out.add(villager("prisoner1", "A Prisoner", false, VillagerProfession.NONE, -6.5, 0, c - 6.5, -6.5, c));
        out.add(villager("prisoner2", "A Prisoner", false, VillagerProfession.NITWIT, -5.5, 0, c + 4.5, -6, c));
        return out;
    }

    // ------------------------------------------------------------------ building
    /** Builds (or rebuilds, after an update) the court and the cells for a slot. True if it built anything. */
    public static boolean ensure(ServerLevel level, int slot) {
        CourtData data = CourtData.get(level);
        if (data.builtVersion(slot) >= VERSION) {
            return false;
        }
        BlockPos o = origin(slot);
        for (int cx = (o.getX() - 20) >> 4; cx <= (o.getX() + 20) >> 4; cx++) {
            for (int cz = (o.getZ() - 24) >> 4; cz <= (o.getZ() + 80) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        // clear whatever an older version built
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -17; x <= 17; x++) {
            for (int y = -16; y <= 14; y++) {
                for (int z = -20; z <= 75; z++) {
                    BlockPos p = o.offset(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, FLAGS);
                    }
                }
            }
        }
        List<BlockPos> shapes = new ArrayList<>();
        courtroom(new B(level, o, shapes));
        prison(new B(level, o.offset(CELL), shapes));
        for (BlockPos p : shapes) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), FLAGS);
        }
        data.setBuilt(slot, VERSION);
        return true;
    }

    /** a little builder: places blocks relative to an origin, and remembers the ones whose shape depends on their neighbours */
    private record B(ServerLevel level, BlockPos o, List<BlockPos> shapes) {
        void set(int x, int y, int z, BlockState s) {
            level.setBlock(o.offset(x, y, z), s, FLAGS);
        }

        void set(int x, int y, int z, Block b) {
            set(x, y, z, b.defaultBlockState());
        }

        /** fences, bars and panes: they join up with what's around them once everything's built */
        void join(int x, int y, int z, Block b) {
            set(x, y, z, b);
            shapes.add(o.offset(x, y, z));
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, s);
                    }
                }
            }
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
            fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
        }
    }

    /** a fixed scatter (the same every build), for cracks and stains */
    private static boolean speck(int x, int y, int z, int oneIn) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return Math.floorMod(h, oneIn) == 0;
    }

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState candles(int n) {
        return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
    }

    private static BlockState hanging(Block lantern) {
        return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    private static BlockState banner(Direction facing) {
        return Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
    }

    private static void door(B b, int x, int z, Block door, Direction facing, DoorHingeSide hinge) {
        BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, false);
        b.set(x, 0, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(x, 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // ------------------------------------------------------------------ the courtroom
    private static void courtroom(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // the hall: 19 wide, 29 long, 10 high, inside walls of deepslate tile
        b.fill(-10, -1, -17, 10, 10, 13, Blocks.DEEPSLATE_TILES);
        b.fill(-9, 0, -16, 9, 9, 12, air);
        // a chequered floor
        for (int x = -9; x <= 9; x++) {
            for (int z = -16; z <= 12; z++) {
                b.set(x, -1, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.CALCITE);
            }
        }
        // a dark wooden ceiling, a stone skirting and a wooden cornice
        b.fill(-10, 10, -17, 10, 10, 13, Blocks.DARK_OAK_PLANKS);
        for (int z = -17; z <= 13; z++) {
            b.set(-10, 0, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(10, 0, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(-10, 9, z, Blocks.DARK_OAK_PLANKS);
            b.set(10, 9, z, Blocks.DARK_OAK_PLANKS);
        }
        for (int x = -10; x <= 10; x++) {
            b.set(x, 0, -17, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(x, 0, 13, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(x, 9, -17, Blocks.DARK_OAK_PLANKS);
            b.set(x, 9, 13, Blocks.DARK_OAK_PLANKS);
        }
        // pillars down both walls, joined by beams across the ceiling
        int[] bays = {-15, -11, -7, -3, 1, 5, 9};
        for (int z : bays) {
            b.fill(-9, 0, z, -9, 8, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
            b.fill(9, 0, z, 9, 8, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
            b.fill(-9, 9, z, 9, 9, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
            // the Watch's blue banners on the pillars
            if (z > -15) {
                b.set(-8, 7, z, banner(Direction.EAST));
                b.set(8, 7, z, banner(Direction.WEST));
            }
        }
        // tall windows of amber glass between the pillars, lit from behind
        for (int i = 0; i < bays.length - 1; i++) {
            int z = bays[i] + 2;
            for (int y = 3; y <= 7; y++) {
                for (int x : new int[]{-10, 10}) {
                    b.join(x, y, z, Blocks.YELLOW_STAINED_GLASS_PANE);
                    b.set(x < 0 ? x - 1 : x + 1, y, z, Blocks.GLOWSTONE);
                }
            }
        }
        // chandeliers: lanterns on chains from the beams
        for (int[] l : new int[][]{{-5, -11}, {5, -11}, {-5, -3}, {5, -3}, {-5, 5}, {5, 5}, {0, -7}, {0, 9}}) {
            b.set(l[0], 8, l[1], Blocks.CHAIN);
            b.set(l[0], 7, l[1], Blocks.CHAIN);
            b.set(l[0], 6, l[1], hanging(Blocks.LANTERN));
        }
        // the red carpet, from the doors to the bench (the dock stands on it)
        for (int z = -10; z <= 12; z++) {
            for (int x = -1; x <= 1; x++) {
                if (Math.abs(z - DOCK.getZ()) > 1) {
                    b.set(x, 0, z, Blocks.RED_CARPET);
                }
            }
        }

        // ---- the bench: a dais for the judge, under the Watch's eye
        b.fill(-6, 0, -16, 6, 0, -12, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(-6, 1, -16, 6, 1, -12, Blocks.DARK_OAK_PLANKS);
        b.fill(-5, 0, -11, 5, 2, -11, Blocks.DARK_OAK_PLANKS);
        b.fill(-6, 0, -11, -6, 3, -11, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(6, 0, -11, 6, 3, -11, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.set(-6, 4, -11, candles(3));
        b.set(6, 4, -11, candles(3));
        b.set(-3, 3, -11, candles(2));
        b.set(3, 3, -11, candles(1));
        // the judge's high-backed chair
        b.fill(-1, 2, -13, 1, 4, -13, Blocks.DARK_OAK_PLANKS);
        b.set(0, 5, -13, Blocks.DARK_OAK_PLANKS);
        b.set(-1, 5, -13, Blocks.DARK_OAK_FENCE);
        b.set(1, 5, -13, Blocks.DARK_OAK_FENCE);
        // soul lanterns on posts either side of the judge
        for (int x : new int[]{-3, 3}) {
            b.join(x, 2, -13, Blocks.DARK_OAK_FENCE);
            b.set(x, 3, -13, Blocks.SOUL_LANTERN);
        }
        // the law, in books, behind the bench
        b.fill(-6, 2, -16, -2, 4, -16, Blocks.BOOKSHELF);
        b.fill(2, 2, -16, 6, 4, -16, Blocks.BOOKSHELF);
        b.fill(-9, 0, -16, -7, 4, -16, Blocks.BOOKSHELF);
        b.fill(7, 0, -16, 9, 4, -16, Blocks.BOOKSHELF);
        // the Watch's mark on the wall: an iron eye that never closes
        b.fill(-4, 5, -17, 4, 9, -17, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(-2, 8, -17, 2, 8, -17, Blocks.IRON_BLOCK);
        b.fill(-2, 6, -17, 2, 6, -17, Blocks.IRON_BLOCK);
        b.set(-3, 7, -17, Blocks.IRON_BLOCK);
        b.set(3, 7, -17, Blocks.IRON_BLOCK);
        b.fill(-2, 7, -17, 2, 7, -17, Blocks.QUARTZ_BLOCK);
        b.set(0, 7, -17, Blocks.GOLD_BLOCK);
        b.set(-5, 7, -16, banner(Direction.SOUTH));
        b.set(5, 7, -16, banner(Direction.SOUTH));
        // the clerk's lectern, below the bench
        b.set(-3, 0, -9, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH).setValue(LecternBlock.HAS_BOOK, true));

        // ---- the witness stand (left): a raised box with a rail on the sides facing the court
        b.fill(-7, 0, -8, -5, 0, -6, Blocks.DARK_OAK_PLANKS);
        for (int z = -8; z <= -6; z++) {
            b.join(-5, 1, z, Blocks.DARK_OAK_FENCE);
        }
        for (int x = -7; x <= -6; x++) {
            b.join(x, 1, -6, Blocks.DARK_OAK_FENCE);
        }
        b.set(-5, 2, -8, candles(2));

        // ---- the jury box (right): two raised rows behind a rail
        b.fill(5, 0, -8, 6, 0, 0, Blocks.DARK_OAK_PLANKS);
        b.fill(7, 0, -8, 8, 1, 0, Blocks.DARK_OAK_PLANKS);
        for (int z = -8; z <= 0; z++) {
            b.join(5, 1, z, Blocks.DARK_OAK_FENCE);
        }
        for (int x = 6; x <= 8; x++) {
            int y = x >= 7 ? 2 : 1;
            b.join(x, y, -8, Blocks.DARK_OAK_FENCE);
            b.join(x, y, 0, Blocks.DARK_OAK_FENCE);
        }
        b.set(5, 2, -8, candles(2));
        b.set(5, 2, 0, candles(2));

        // ---- the dock: a dark rail all round (too high to climb over)
        b.set(DOCK.getX(), -1, DOCK.getZ(), Blocks.POLISHED_BLACKSTONE);
        for (int x = -1; x <= 1; x++) {
            for (int z = DOCK.getZ() - 1; z <= DOCK.getZ() + 1; z++) {
                if (!(x == 0 && z == DOCK.getZ())) {
                    b.set(x, -1, z, Blocks.POLISHED_BLACKSTONE);
                    b.join(x, 0, z, Blocks.DARK_OAK_FENCE);
                }
            }
        }

        // ---- the bar of the court, and the gallery behind it
        for (int x = -9; x <= 9; x++) {
            if (Math.abs(x) >= 2) {
                b.join(x, 0, 5, Blocks.DARK_OAK_FENCE);
            }
        }
        BlockState bench = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int z : new int[]{7, 9, 11}) {
            for (int x = -8; x <= 8; x++) {
                if (Math.abs(x) >= 3) {
                    b.set(x, 0, z, bench);
                }
            }
        }
        // the doors at the back (shut, and nothing behind them)
        b.fill(-2, 0, 13, 2, 4, 13, Blocks.POLISHED_BLACKSTONE_BRICKS);
        door(b, 0, 13, Blocks.DARK_OAK_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
        b.fill(-1, 0, 14, 1, 2, 14, Blocks.DEEPSLATE_TILES);
        for (int x : new int[]{-2, 2}) {
            b.join(x, 0, 12, Blocks.DARK_OAK_FENCE);
            b.set(x, 1, 12, Blocks.LANTERN);
        }
    }

    // ------------------------------------------------------------------ the prison block
    private static void prison(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // the block: obsidian, with a corridor down the middle and three cells either side
        for (int x = -12; x <= 12; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -8; z <= 6; z++) {
                    b.set(x, y, z, speck(x, y, z, 9) ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
                }
            }
        }
        b.fill(-11, 0, -2, 11, 3, 0, air);
        for (int x = -11; x <= 11; x++) {
            for (int z = -2; z <= 0; z++) {
                b.set(x, -1, z, speck(x, 0, z, 4) ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES);
            }
        }
        int[] cellX = {-6, 0, 6};
        for (int cx : cellX) {
            for (int[] rows : new int[][]{{-7, -4, -3}, {2, 5, 1}}) {
                int z0 = rows[0], z1 = rows[1], bars = rows[2];
                b.fill(cx - 2, 0, z0, cx + 2, 3, z1, air);
                for (int x = cx - 2; x <= cx + 2; x++) {
                    for (int z = z0; z <= z1; z++) {
                        b.set(x, -1, z, speck(x, 1, z, 5) ? Blocks.GRAVEL : speck(x, 2, z, 3) ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.COBBLED_DEEPSLATE);
                    }
                    // the barred front, with an iron door in the middle (no handle on the inside)
                    for (int y = 0; y <= 3; y++) {
                        b.join(x, y, bars, Blocks.IRON_BARS);
                    }
                }
                door(b, cx - 2, bars, Blocks.IRON_DOOR, bars < 0 ? Direction.NORTH : Direction.SOUTH, DoorHingeSide.LEFT);
            }
        }
        // the corridor's lamps
        for (int x : new int[]{-6, 0, 6}) {
            b.set(x, 3, -1, hanging(Blocks.LANTERN));
        }
        // the gaoler's end: the evidence chest, a barrel, a lamp
        b.set(11, 0, -2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        b.set(11, 0, 0, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(11, 1, 0, Blocks.LANTERN);
        b.set(11, 0, -1, Blocks.DARK_OAK_SLAB);
        b.set(-11, 3, -2, Blocks.COBWEB);
        b.set(-11, 3, 0, Blocks.COBWEB);
        b.set(-11, 0, -1, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 12));

        // ---- your cell (the middle one on the south side): a hay cot, a cauldron, chains, and a window over the lava
        b.set(-2, 0, 4, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-2, 0, 5, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-2, 1, 4, Blocks.BROWN_CARPET);
        b.set(2, 0, 5, Blocks.CAULDRON);
        b.set(1, 0, 5, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 7));
        b.set(-1, 0, 5, candles(2));
        b.set(2, 3, 3, Blocks.CHAIN);
        b.set(2, 2, 3, Blocks.CHAIN);
        b.set(-2, 3, 2, Blocks.CHAIN);
        b.set(-2, 3, 5, Blocks.COBWEB);
        b.set(2, 3, 5, Blocks.COBWEB);
        b.set(0, 3, 3, hanging(Blocks.LANTERN));
        for (int[] w : new int[][]{{0, 1}, {0, 2}, {-1, 2}, {1, 2}}) {
            b.join(w[0], w[1], 6, Blocks.IRON_BARS);
        }

        // ---- the other cells
        // across the corridor: an old prisoner on a hay cot
        b.set(-2, 0, -7, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        b.set(-1, 0, -7, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        b.set(2, 0, -7, candles(1));
        b.set(2, 3, -7, Blocks.COBWEB);
        b.set(-2, 3, -4, Blocks.CHAIN);
        // north-west: another prisoner
        b.set(-8, 0, -7, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-8, 0, -6, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-4, 0, -7, candles(2));
        b.set(-4, 3, -7, Blocks.COBWEB);
        // north-east: empty, but not for long ago
        b.set(5, 0, -6, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 3));
        b.set(8, 3, -7, Blocks.COBWEB);
        b.set(4, 3, -7, Blocks.COBWEB);
        b.set(8, 3, -5, Blocks.CHAIN);
        b.set(8, 2, -5, Blocks.CHAIN);
        b.set(7, 0, -7, candles(1));
        // south-west: one more prisoner
        b.set(-8, 0, 4, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-8, 0, 5, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-4, 0, 5, candles(2));
        // south-east: empty
        b.set(8, 0, 5, Blocks.CAULDRON);
        b.set(4, 3, 5, Blocks.COBWEB);
        b.set(6, 0, 4, candles(1));

        // ---- underneath: a lava lake, and basalt pillars standing in it
        b.fill(-16, -11, -10, 16, -11, 30, Blocks.BASALT);
        b.fill(-16, -10, -10, 16, -10, 30, Blocks.BASALT);
        b.fill(-15, -10, -9, 15, -10, 29, Blocks.LAVA);
        b.fill(-12, -2, -8, 12, -2, 6, Blocks.BASALT);
        for (int x : new int[]{-12, -6, 0, 6, 12}) {
            for (int z : new int[]{-8, 6}) {
                b.fill(x, -10, z, x, -3, z, log(Blocks.BASALT, Direction.Axis.Y));
            }
        }
        int[][] columns = {{-7, 14, 5}, {9, 19, 8}, {-11, 24, 3}, {3, 27, 6}, {12, 11, 4}};
        for (int[] col : columns) {
            b.fill(col[0], -10, col[1], col[0], -10 + col[2], col[1], log(Blocks.BASALT, Direction.Axis.Y));
        }
    }

    // ------------------------------------------------------------------ the people
    /** Puts the court's people in their places (once their part of the world is loaded), one of each. */
    public static void ensureNpcs(ServerLevel level, int slot) {
        BlockPos o = origin(slot);
        List<Npc> all = npcs();
        Set<String> roles = new HashSet<>();
        for (Npc n : all) {
            roles.add(NPC + "_" + n.role());
        }
        // anyone left over from an older court
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(o).inflate(40, 40, 0).expandTowards(0, 0, 80).expandTowards(0, 0, -30),
                e -> e.getTags().contains(NPC))) {
            boolean known = false;
            for (String t : e.getTags()) {
                known |= roles.contains(t);
            }
            if (!known) {
                e.discard();
            }
        }
        for (Npc n : all) {
            BlockPos at = o.offset((int) Math.floor(n.x()), (int) Math.floor(n.y()), (int) Math.floor(n.z()));
            if (!level.isPositionEntityTicking(at)) {
                continue;
            }
            String tag = NPC + "_" + n.role();
            List<Entity> found = level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(48), e -> e.getTags().contains(tag));
            for (int i = 1; i < found.size(); i++) {
                found.get(i).discard();
            }
            Entity e = found.isEmpty() ? null : found.get(0);
            if (e != null && (n.kind() == Kind.GOLEM) != (e.getType() == EntityType.IRON_GOLEM)) {
                e.discard();
                e = null;
            }
            if (e == null) {
                e = spawn(level, n);
                if (e == null) {
                    continue;
                }
                e.moveTo(o.getX() + n.x(), o.getY() + n.y(), o.getZ() + n.z(), n.yaw(), 0f);
                level.addFreshEntity(e);
                if (e instanceof Villager v) {
                    Meb.clearOffer(v);
                }
            }
            // they stand where they should, facing the right way
            e.moveTo(o.getX() + n.x(), o.getY() + n.y(), o.getZ() + n.z(), n.yaw(), 0f);
            e.setYHeadRot(n.yaw());
            if (e instanceof Mob m) {
                m.setYBodyRot(n.yaw());
            }
        }
    }

    private static Entity spawn(ServerLevel level, Npc n) {
        Mob m = n.kind() == Kind.GOLEM ? EntityType.IRON_GOLEM.create(level) : EntityType.VILLAGER.create(level);
        if (m == null) {
            return null;
        }
        m.setNoAi(true);
        m.setInvulnerable(true);
        m.setPersistenceRequired();
        m.setSilent(true);
        if (!n.name().isEmpty()) {
            m.setCustomName(Component.literal(n.name()));
            m.setCustomNameVisible(n.showName());
        }
        if (m instanceof Villager v) {
            v.setVillagerData(v.getVillagerData().setProfession(n.profession()).setLevel(5));
        }
        m.addTag(NPC);
        m.addTag(NPC + "_" + n.role());
        return m;
    }

    public static boolean isCourtNpc(Entity e) {
        return e.getTags().contains(NPC);
    }

    /** the part a court NPC plays (judge, gaoler, prisoner0, gallery2...), or "" */
    public static String role(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(NPC + "_")) {
                return t.substring(NPC.length() + 1);
            }
        }
        return "";
    }
}

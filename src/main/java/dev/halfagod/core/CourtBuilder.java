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
    public static final int VERSION = 4;
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

    /**
     * What the court and the prison look like right now, for one slot: the prison's part of the day (0 in the cells,
     * 1 labour, 2 the cafeteria), who's visiting, whether the Hand has sent an advocate, who's on the witness stand, who
     * is speaking, whether the quarry guard is watching the cart, and where the gaoler is on his night round (NaN: not).
     */
    public record Scene(int phase, String visitor, String visitorName, boolean advocate, String witnessName, VillagerProfession witnessJob,
                        boolean witnessHidden, String speaker, boolean guardWatching, double gaolerX) {
        public static final Scene QUIET = new Scene(0, "", "", false, "", VillagerProfession.NONE, false, "", true, Double.NaN);
    }

    public static final int PHASE_CELL = 0, PHASE_LABOUR = 1, PHASE_MEAL = 2;

    /** c-relative places in the prison block (relative to CELL) */
    public static final double[] LABOUR_ENTRY = {-15.5, 0, -0.5, 90};
    public static final double[] MEAL_ENTRY = {15.5, 0, -0.5, -90};
    public static final BlockPos CART = new BlockPos(-15, 0, 4);
    public static final BlockPos DIG = new BlockPos(0, 0, 6);
    public static final BlockPos GATE = new BlockPos(20, 0, -8);
    private static final double[] GUARD_QUARRY = {-18.5, 0, 2.5};

    private static List<Npc> npcs(Scene sc) {
        List<Npc> out = new ArrayList<>();
        double dx = DOCK.getX() + 0.5, dz = DOCK.getZ() + 0.5;
        // the court
        out.add(villager("judge", "The Judge", true, VillagerProfession.CLERIC, 0.5, 2, -11.5, dx, dz));
        out.add(villager("clerk", "The Clerk", true, VillagerProfession.LIBRARIAN, -2.5, 0, -9.5, -2.5, 0));
        out.add(villager("captain", "The Watch Captain", true, VillagerProfession.WEAPONSMITH, -2.5, 0, -4.5, 0.5, -11.5));
        out.add(villager("witness", sc.witnessName().isEmpty() ? "The Witness" : sc.witnessName(), true, sc.witnessJob(), -5.5, 1, -6.5, dx, dz));
        VillagerProfession[] jp = {VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON,
                VillagerProfession.SHEPHERD, VillagerProfession.FLETCHER, VillagerProfession.CARTOGRAPHER};
        for (int i = 0; i < 6; i++) {
            boolean back = i >= 3;
            out.add(villager("juror" + i, "Juror", false, jp[i], back ? 7.5 : 6.5, back ? 2 : 1, (back ? -5.5 : -6.5) + (i % 3) * 2, 0.5, -3));
        }
        // the guards stand either side of the dock, at your shoulders
        out.add(new Npc("guard0", "", false, Kind.GOLEM, null, -2.5, 0, DOCK.getZ() + 0.5, 180f));
        out.add(new Npc("guard1", "", false, Kind.GOLEM, null, 3.5, 0, DOCK.getZ() + 0.5, 180f));
        double[][] gallery = {{-6.5, 7.5}, {-4.5, 9.5}, {-7.5, 11.5}, {4.5, 7.5}, {7.5, 9.5}, {3.5, 11.5}};
        VillagerProfession[] gp = {VillagerProfession.BUTCHER, VillagerProfession.NONE, VillagerProfession.FISHERMAN,
                VillagerProfession.LEATHERWORKER, VillagerProfession.NONE, VillagerProfession.ARMORER};
        for (int i = 0; i < gallery.length; i++) {
            out.add(new Npc("gallery" + i, "", false, Kind.VILLAGER, gp[i], gallery[i][0], 0.5, gallery[i][1], 180f));
        }
        if (sc.advocate()) {
            out.add(villager("advocate", "An Advocate of the Hand", true, VillagerProfession.CARTOGRAPHER, 2.5, 0, 0.5, 0.5, -11.5));
        }

        // the prison block (everything relative to CELL)
        int c = CELL.getZ();
        double sx = CELL_SPOT.getX() + 0.5, sz = CELL_SPOT.getZ() + 0.5;
        String[] names = {"Old Maddock", "Wren", "Brother Tam", "Kesh"};
        VillagerProfession[] pj = {VillagerProfession.NITWIT, VillagerProfession.NONE, VillagerProfession.NITWIT, VillagerProfession.NONE};
        double[][] cells = {{0.5, -5.5, 0.5, 4.5}, {-6.5, -6.5, -6.5, 0}, {-5.5, 4.5, -6, 0}, {6.5, 3.5, 6, 0}};
        double[][] quarry = {{-24.5, -5.5}, {-24.5, -3.5}, {-24.5, 1.5}, {-24.5, 3.5}};
        double[][] tables = {{17.5, -3.5, 180}, {20.5, -6.5, 0}, {18.5, 2.5, 0}, {22.5, 4.5, 180}};
        for (int i = 0; i < 4; i++) {
            String role = "prisoner" + i;
            switch (sc.phase()) {
                case PHASE_LABOUR -> out.add(new Npc(role, names[i], true, Kind.VILLAGER, pj[i], quarry[i][0], 0, c + quarry[i][1], 90f));
                case PHASE_MEAL -> out.add(new Npc(role, names[i], true, Kind.VILLAGER, pj[i], tables[i][0], 0.5, c + tables[i][1], (float) tables[i][2]));
                default -> out.add(villager(role, names[i], true, pj[i], cells[i][0], 0, c + cells[i][1], cells[i][2], c + cells[i][3]));
            }
        }
        // two more inmates, who keep to themselves
        double[][] inmatesCell = {{5.5, -6.5, 0}, {7.5, -5.5, 0}};
        double[][] inmatesQuarry = {{-24.5, -1.5, 90}, {-22.5, 0.5, 90}};
        double[][] inmatesMeal = {{23.5, -3.5, 180}, {16.5, 4.5, 180}};
        for (int i = 0; i < 2; i++) {
            double[] at = sc.phase() == PHASE_LABOUR ? inmatesQuarry[i] : sc.phase() == PHASE_MEAL ? inmatesMeal[i] : inmatesCell[i];
            out.add(new Npc("inmate" + i, "", false, Kind.VILLAGER, i == 0 ? VillagerProfession.MASON : VillagerProfession.NONE,
                    at[0], sc.phase() == PHASE_MEAL ? 0.5 : 0, c + at[1], (float) at[2]));
        }
        // the gaoler: watching your cell, at the quarry door, serving supper, or on his night round
        if (!Double.isNaN(sc.gaolerX())) {
            out.add(new Npc("gaoler", "The Gaoler", true, Kind.VILLAGER, VillagerProfession.TOOLSMITH, sc.gaolerX(), 0, c - 0.5, 90f));
        } else if (sc.phase() == PHASE_LABOUR) {
            out.add(villager("gaoler", "The Gaoler", true, VillagerProfession.TOOLSMITH, -20.5, 0, c - 5.5, -25, c));
        } else if (sc.phase() == PHASE_MEAL) {
            out.add(new Npc("gaoler", "The Gaoler", true, Kind.VILLAGER, VillagerProfession.TOOLSMITH, 26.5, 0, c - 0.5, 90f));
        } else {
            out.add(villager("gaoler", "The Gaoler", true, VillagerProfession.TOOLSMITH, 1.5, 0, c - 0.5, sx, sz));
        }
        // the prison's guards: one watching the quarry cart (or not), one at the gate
        float gy = sc.guardWatching() ? face(GUARD_QUARRY[0], GUARD_QUARRY[2], CART.getX() + 0.5, CART.getZ() + 0.5) : 90f;
        out.add(new Npc("guard2", "", false, Kind.GOLEM, null, GUARD_QUARRY[0], 0, c + GUARD_QUARRY[2], gy));
        out.add(new Npc("guard3", "", false, Kind.GOLEM, null, 23.5, 0, c - 6.5, 0f));
        if (!sc.visitor().isEmpty()) {
            VillagerProfession vp = switch (sc.visitor()) {
                case "hand" -> VillagerProfession.CARTOGRAPHER;
                case "watch" -> VillagerProfession.WEAPONSMITH;
                case "widow" -> VillagerProfession.SHEPHERD;
                default -> VillagerProfession.CLERIC;
            };
            out.add(villager("visitor", sc.visitorName(), true, vp, 0.5, 0, c + 0.5, sx, sz));
        }
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
        for (int cx = (o.getX() - 36) >> 4; cx <= (o.getX() + 36) >> 4; cx++) {
            for (int cz = (o.getZ() - 24) >> 4; cz <= (o.getZ() + 80) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        // clear whatever an older version built
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -34; x <= 34; x++) {
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
        b.fill(-5, 0, -11, 5, 1, -11, Blocks.DARK_OAK_PLANKS);
        b.fill(-5, 2, -11, 5, 2, -11, Blocks.DARK_OAK_SLAB);
        b.fill(-6, 0, -11, -6, 3, -11, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(6, 0, -11, 6, 3, -11, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.set(-6, 4, -11, candles(3));
        b.set(6, 4, -11, candles(3));
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
        b.fill(-2, 8, -17, 2, 8, -17, Blocks.QUARTZ_BLOCK);
        b.fill(-2, 6, -17, 2, 6, -17, Blocks.QUARTZ_BLOCK);
        b.fill(-3, 7, -17, 3, 7, -17, Blocks.QUARTZ_BLOCK);
        b.set(-1, 7, -17, Blocks.GOLD_BLOCK);
        b.set(1, 7, -17, Blocks.GOLD_BLOCK);
        b.set(0, 8, -17, Blocks.GOLD_BLOCK);
        b.set(0, 6, -17, Blocks.GOLD_BLOCK);
        b.set(0, 7, -17, Blocks.BLACK_CONCRETE);
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
                    for (int y = 0; y <= 2; y++) {
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
        b.set(-11, 3, -2, Blocks.COBWEB);
        b.set(-11, 3, 0, Blocks.COBWEB);

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
        for (int x = -1; x <= 1; x++) {
            b.join(x, 1, 6, Blocks.IRON_BARS);
            b.join(x, 2, 6, Blocks.IRON_BARS);
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

        quarry(b);
        cafeteria(b);
        // the doors between: the corridor's west end to the quarry, east end to the cafeteria (two walls thick)
        door(b, -12, -1, Blocks.IRON_DOOR, Direction.EAST, DoorHingeSide.LEFT);
        b.fill(-13, 0, -1, -13, 1, -1, air);
        door(b, 12, -1, Blocks.IRON_DOOR, Direction.WEST, DoorHingeSide.LEFT);
        b.fill(13, 0, -1, 13, 1, -1, air);

        // ---- underneath: a lava lake, and basalt pillars standing in it
        b.fill(-32, -7, -12, 32, -7, 30, Blocks.BASALT);
        b.fill(-32, -6, -12, 32, -6, 30, Blocks.BASALT);
        b.fill(-31, -6, -11, 31, -6, 29, Blocks.LAVA);
        b.fill(-28, -2, -8, 28, -2, 6, Blocks.BASALT);
        b.fill(19, -2, -13, 22, -2, -9, Blocks.BASALT);
        for (int x : new int[]{-28, -20, -12, -6, 0, 6, 12, 20, 28}) {
            for (int z : new int[]{-8, 6}) {
                b.fill(x, -6, z, x, -3, z, log(Blocks.BASALT, Direction.Axis.Y));
            }
        }
        int[][] columns = {{-7, 12, 4}, {9, 16, 7}, {-11, 21, 2}, {3, 24, 5}, {12, 10, 3}};
        for (int[] col : columns) {
            b.fill(col[0], -6, col[1], col[0], -6 + col[2], col[1], log(Blocks.BASALT, Direction.Axis.Y));
        }
    }

    /** the quarry, west of the cells: a stone face to break, a cart of tools, and a guard who watches it (mostly) */
    private static void quarry(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -28; x <= -13; x++) {
            for (int y = -1; y <= 6; y++) {
                for (int z = -8; z <= 6; z++) {
                    b.set(x, y, z, speck(x, y, z, 6) ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS);
                }
            }
        }
        b.fill(-27, 0, -7, -14, 5, 5, air);
        for (int x = -27; x <= -14; x++) {
            for (int z = -7; z <= 5; z++) {
                b.set(x, -1, z, speck(x, 0, z, 3) ? Blocks.GRAVEL : speck(x, 1, z, 4) ? Blocks.COBBLESTONE : Blocks.COARSE_DIRT);
            }
        }
        // the face: stone, a little andesite, rubble at its foot
        for (int z = -6; z <= 4; z++) {
            for (int y = 0; y <= 4; y++) {
                b.set(-27, y, z, speck(-27, y, z, 5) ? Blocks.ANDESITE : Blocks.STONE);
            }
            if (speck(-26, 0, z, 3)) {
                b.set(-26, 0, z, Blocks.COBBLESTONE_SLAB);
            }
        }
        // timber props along the walls, lanterns on chains
        for (int z : new int[]{-7, -3, 1, 5}) {
            b.fill(-14, 0, z, -14, 5, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
        }
        for (int x : new int[]{-24, -19}) {
            b.fill(x, 0, -7, x, 5, -7, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
            b.fill(x, 0, 5, x, 5, 5, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
            b.fill(x, 5, -6, x, 5, 4, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
            for (int z : new int[]{-4, 2}) {
                b.set(x, 4, z, Blocks.CHAIN);
                b.set(x, 3, z, hanging(Blocks.LANTERN));
            }
        }
        // the cart: barrels of tools, a grindstone, a pile of rubble
        b.set(CART.getX(), 0, CART.getZ(), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(CART.getX() - 1, 0, CART.getZ(), Blocks.GRINDSTONE);
        b.set(CART.getX(), 0, CART.getZ() + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(CART.getX() + 1, 1, CART.getZ(), Blocks.LANTERN);
        b.set(CART.getX() + 1, 0, CART.getZ(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        b.set(-16, 0, -6, Blocks.COBBLESTONE);
        b.set(-17, 0, -6, Blocks.COBBLESTONE_SLAB);
        b.set(-16, 0, -5, Blocks.COBBLESTONE_SLAB);
    }

    /** the cafeteria, east of the cells: two long tables, a serving counter, and the gate out at the back */
    private static void cafeteria(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 13; x <= 28; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -8; z <= 6; z++) {
                    b.set(x, y, z, speck(x, y, z, 7) ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
            }
        }
        b.fill(14, 0, -7, 27, 4, 5, air);
        for (int x = 14; x <= 27; x++) {
            for (int z = -7; z <= 5; z++) {
                b.set(x, -1, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.ANDESITE);
            }
        }
        // two long tables with benches either side
        BlockState north = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState south = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int table : new int[]{-5, 3}) {
            for (int x = 16; x <= 24; x++) {
                b.set(x, 0, table, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
                b.set(x, 0, table - 1, north);
                b.set(x, 0, table + 1, south);
            }
            for (int x : new int[]{17, 21, 24}) {
                b.set(x, 1, table, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1).setValue(CandleBlock.LIT, true));
            }
            b.set(19, 1, table, Blocks.FLOWER_POT);
        }
        // the counter, and the kitchen behind it
        for (int z = -6; z <= 4; z++) {
            b.set(25, 0, z, Blocks.SPRUCE_PLANKS);
            b.set(25, 1, z, Blocks.SMOOTH_STONE_SLAB);
        }
        b.set(26, 0, -5, Blocks.WATER_CAULDRON);
        b.set(26, 0, -4, Blocks.CAULDRON);
        b.set(26, 0, 3, Blocks.SMOKER);
        b.set(26, 0, 4, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(27, 0, 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        // light
        for (int[] l : new int[][]{{17, -5}, {22, -5}, {17, 3}, {22, 3}, {20, -1}}) {
            b.set(l[0], 4, l[1], Blocks.CHAIN);
            b.set(l[0], 3, l[1], hanging(Blocks.LANTERN));
        }
        // the gate: double iron doors in the north wall, and a dark passage behind (nobody sees past it)
        for (int x = 19; x <= 22; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -13; z <= -9; z++) {
                    b.set(x, y, z, Blocks.OBSIDIAN);
                }
            }
        }
        b.fill(20, 0, -12, 21, 2, -9, air);
        b.set(20, 2, -11, hanging(Blocks.SOUL_LANTERN));
        b.fill(18, 0, -8, 23, 3, -8, Blocks.POLISHED_BLACKSTONE_BRICKS);
        door(b, GATE.getX(), GATE.getZ(), Blocks.IRON_DOOR, Direction.SOUTH, DoorHingeSide.LEFT);
        door(b, GATE.getX() + 1, GATE.getZ(), Blocks.IRON_DOOR, Direction.SOUTH, DoorHingeSide.RIGHT);
        b.set(19, 2, -7, Blocks.LANTERN);
        b.set(22, 2, -7, Blocks.LANTERN);
        b.set(19, 1, -7, Blocks.STONE_BRICK_WALL);
        b.set(22, 1, -7, Blocks.STONE_BRICK_WALL);
        b.set(19, 0, -7, Blocks.STONE_BRICK_WALL);
        b.set(22, 0, -7, Blocks.STONE_BRICK_WALL);
    }

    // ------------------------------------------------------------------ doors, rooms and the hole
    /** the doors the prison opens and shuts (c-relative lower halves) */
    public enum Door { CELL, CELLS, WEST, EAST, GATE }

    private static List<BlockPos> doorsOf(Door d) {
        List<BlockPos> out = new ArrayList<>();
        switch (d) {
            case CELL -> out.add(new BlockPos(-2, 0, 1));
            case CELLS -> {
                for (int cx : new int[]{-6, 0, 6}) {
                    out.add(new BlockPos(cx - 2, 0, 1));
                    out.add(new BlockPos(cx - 2, 0, -3));
                }
            }
            case WEST -> out.add(new BlockPos(-12, 0, -1));
            case EAST -> out.add(new BlockPos(12, 0, -1));
            case GATE -> {
                out.add(GATE);
                out.add(GATE.east());
            }
        }
        return out;
    }

    /** opens or shuts some of the prison's doors (with the sound, once) */
    public static void doors(ServerLevel level, int slot, Door which, boolean open) {
        BlockPos c = origin(slot).offset(CELL);
        boolean changed = false;
        for (BlockPos rel : doorsOf(which)) {
            for (int y = 0; y <= 1; y++) {
                BlockPos p = c.offset(rel).above(y);
                BlockState st = level.getBlockState(p);
                if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.OPEN) != open) {
                    level.setBlock(p, st.setValue(DoorBlock.OPEN, open), FLAGS);
                    changed = true;
                }
            }
        }
        if (changed) {
            BlockPos at = c.offset(doorsOf(which).get(0));
            level.playSound(null, at, open ? net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN : net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.8f);
        }
    }

    public static boolean doorOpen(ServerLevel level, int slot, Door which) {
        BlockState st = level.getBlockState(origin(slot).offset(CELL).offset(doorsOf(which).get(0)));
        return st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.OPEN);
    }

    /** where someone is in the prison block: "cell" (yours), "corridor", "labour", "meal", "ledge", "gate" or "" */
    public static String room(int slot, double x, double y, double z) {
        BlockPos c = origin(slot).offset(CELL);
        double rx = x - c.getX(), rz = z - c.getZ(), ry = y - c.getY();
        if (ry < -3 || ry > 6) {
            return "";
        }
        if (rz >= 6.9 && rz <= 9 && rx >= -1 && rx <= 16) {
            return "ledge";
        }
        if (rx >= 19.5 && rx <= 22.5 && rz <= -8.2 && rz >= -13) {
            return "gate";
        }
        if (rx >= -27 && rx < -13 && rz >= -7 && rz < 6) {
            return "labour";
        }
        if (rx >= 13.5 && rx < 28 && rz >= -7 && rz < 6) {
            return "meal";
        }
        if (rx >= -2 && rx < 3 && rz >= 1.5 && rz < 6) {
            return "cell";
        }
        if (rx >= -12 && rx < 13 && rz >= -2 && rz < 1) {
            return "corridor";
        }
        return "";
    }

    /** is this (absolute) block part of the quarry face? */
    public static boolean isQuarryFace(int slot, BlockPos p) {
        BlockPos r = p.subtract(origin(slot).offset(CELL));
        return r.getX() == -27 && r.getY() >= 0 && r.getY() <= 4 && r.getZ() >= -6 && r.getZ() <= 4;
    }

    public static BlockPos abs(int slot, BlockPos rel) {
        return origin(slot).offset(CELL).offset(rel);
    }

    /** the escape hole under your window: closed (the wall as built), or open with the ledge outside */
    public static void hole(ServerLevel level, int slot, boolean open) {
        BlockPos c = origin(slot).offset(CELL);
        List<BlockPos> shapes = new ArrayList<>();
        B b = new B(level, c, shapes);
        if (open) {
            b.set(0, 0, 6, Blocks.AIR);
            b.set(0, 1, 6, Blocks.AIR);
            // a ledge of basalt along the outside of the wall, east to the corner, and down
            for (int x = 0; x <= 14; x++) {
                b.set(x, -1, 7, Blocks.POLISHED_BASALT);
                b.set(x, -1, 8, Blocks.BASALT);
            }
        } else {
            b.set(0, 0, 6, Blocks.OBSIDIAN);
            b.set(0, 1, 6, Blocks.IRON_BARS);
            shapes.add(c.offset(0, 1, 6));
            for (int x = 0; x <= 14; x++) {
                b.set(x, -1, 7, Blocks.AIR);
                b.set(x, -1, 8, Blocks.AIR);
            }
        }
        for (BlockPos p : shapes) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), FLAGS);
        }
        for (int x = -1; x <= 1; x++) {
            BlockPos p = c.offset(x, 1, 6);
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), FLAGS);
        }
    }

    // ------------------------------------------------------------------ the people
    /** Puts the court's and the prison's people in their places (once their part of the world is loaded), one of each. */
    public static void ensureNpcs(ServerLevel level, int slot) {
        ensureNpcs(level, slot, Scene.QUIET, null);
    }

    public static void ensureNpcs(ServerLevel level, int slot, Scene scene, net.minecraft.world.entity.player.Player viewer) {
        BlockPos o = origin(slot);
        List<Npc> all = npcs(scene);
        Set<String> roles = new HashSet<>();
        for (Npc n : all) {
            roles.add(NPC + "_" + n.role());
        }
        // anyone who isn't part of this scene (a visitor who's gone, the advocate after the trial, an older court's people)
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(o).inflate(48, 40, 0).expandTowards(0, 0, 80).expandTowards(0, 0, -30),
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
            Entity e = find(level, slot, n.role());
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
            float yaw = n.yaw();
            if (viewer != null && n.role().equals(scene.speaker())) {
                yaw = face(o.getX() + n.x(), o.getZ() + n.z(), viewer.getX(), viewer.getZ());
            }
            // the right name, the right job, seen or not
            if (!n.name().isEmpty() && (e.getCustomName() == null || !e.getCustomName().getString().equals(n.name()))) {
                e.setCustomName(Component.literal(n.name()));
            }
            if (e instanceof Villager v && n.profession() != null && v.getVillagerData().getProfession() != n.profession()) {
                v.setVillagerData(v.getVillagerData().setProfession(n.profession()).setLevel(5));
                Meb.clearOffer(v);
            }
            e.setInvisible(n.role().equals("witness") && scene.witnessHidden());
            // they stand where they should, facing the right way
            put(e, o.getX() + n.x(), o.getY() + n.y(), o.getZ() + n.z(), yaw);
        }
    }

    private static void put(Entity e, double x, double y, double z, float yaw) {
        if (e.distanceToSqr(x, y, z) > 0.01 || Math.abs(e.getYRot() - yaw) > 0.5f) {
            e.moveTo(x, y, z, yaw, 0f);
        }
        e.setYHeadRot(yaw);
        if (e instanceof Mob m) {
            m.setYBodyRot(yaw);
        }
    }

    /** one of the slot's people, by part (judge, gaoler, prisoner0...), if they're loaded */
    public static Entity find(ServerLevel level, int slot, String role) {
        String tag = NPC + "_" + role;
        BlockPos o = origin(slot);
        List<Entity> found = level.getEntitiesOfClass(Entity.class, new AABB(o).inflate(48, 40, 0).expandTowards(0, 0, 80).expandTowards(0, 0, -30),
                e -> e.getTags().contains(tag));
        for (int i = 1; i < found.size(); i++) {
            found.get(i).discard();
        }
        return found.isEmpty() ? null : found.get(0);
    }

    /** moves one of the slot's people right now (relative to the slot's origin): the gaoler's round, the quarry guard's head */
    public static void place(ServerLevel level, int slot, String role, double x, double y, double z, float yaw) {
        Entity e = find(level, slot, role);
        if (e != null) {
            BlockPos o = origin(slot);
            put(e, o.getX() + x, o.getY() + y, o.getZ() + z, yaw);
        }
    }

    /** the quarry guard's face: towards the cart (watching) or the stone */
    public static void guardLooks(ServerLevel level, int slot, boolean watching) {
        float yaw = watching ? face(GUARD_QUARRY[0], GUARD_QUARRY[2], CART.getX() + 0.5, CART.getZ() + 0.5) : 90f;
        place(level, slot, "guard2", GUARD_QUARRY[0], 0, CELL.getZ() + GUARD_QUARRY[2], yaw);
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

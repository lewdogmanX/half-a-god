package dev.halfagod.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the Watch's court and its prison in the court dimension, one set per player (each player has a slot).
 *
 * The courtroom: a vaulted hall of deepslate and dark oak. Oak panelling below, tall amber windows above, a ribbed
 * ceiling with chandeliers, side balconies and a rose window over the doors. The judge sits on a raised dais in front
 * of a crimson screen, under the Watch's great eye and its rays of light. Below: the clerk, a witness stand, six
 * jurors in a two-tier box, the Captain's table, the evidence table, the dock with its two golem guards, and a tiered
 * gallery of onlookers.
 *
 * The prison: a fortress on a basalt shelf inside a vast cavern, deep underground. Lava falls down the cavern walls into
 * a lava lake, glow lichen speckles the rock, stalactites hang from the dark. Inside, a corridor of obsidian cells, a
 * quarry, a cafeteria and a gate; beyond the gate, a bridge over the lava and a tunnel into the rock.
 */
public final class CourtBuilder {
    public static final int VERSION = 6;
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

    /** places in the prison (relative to CELL) */
    public static final double[] LABOUR_ENTRY = {-15.5, 0, -1.5, 90};
    public static final double[] MEAL_ENTRY = {15.5, 0, -1.5, -90};
    public static final BlockPos CART = new BlockPos(-15, 0, 4);
    public static final BlockPos DIG = new BlockPos(0, 0, 6);
    public static final BlockPos GATE = new BlockPos(20, 0, -11);
    /** the line the gaoler walks on his night rounds (z, relative to CELL) */
    public static final double ROUND_Z = -0.5;
    private static final double[] GUARD_QUARRY = {-18.5, 0, 2.5};

    private static List<Npc> npcs(Scene sc) {
        List<Npc> out = new ArrayList<>();
        double dx = DOCK.getX() + 0.5, dz = DOCK.getZ() + 0.5;
        // ---- the court
        out.add(villager("judge", "The Judge", true, VillagerProfession.CLERIC, 0.5, 2, -14.5, dx, dz));
        out.add(villager("clerk", "The Clerk", true, VillagerProfession.LIBRARIAN, -3.5, 0, -10.5, -3.5, 0));
        out.add(villager("captain", "The Watch Captain", true, VillagerProfession.WEAPONSMITH, -2.5, 0, -6.5, 0.5, -14.5));
        out.add(villager("witness", sc.witnessName().isEmpty() ? "The Witness" : sc.witnessName(), true, sc.witnessJob(), -8.0, 1, -10.0, dx, dz));
        VillagerProfession[] jp = {VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON,
                VillagerProfession.SHEPHERD, VillagerProfession.FLETCHER, VillagerProfession.CARTOGRAPHER};
        for (int i = 0; i < 6; i++) {
            boolean back = i >= 3;
            out.add(villager("juror" + i, "Juror", false, jp[i], back ? 8.5 : 6.5, back ? 2 : 1, (back ? -9.5 : -10.5) + (i % 3) * 2, 0.5, -6));
        }
        // the guards stand either side of the dock, at your shoulders
        out.add(new Npc("guard0", "", false, Kind.GOLEM, null, -2.5, 0, dz, 180f));
        out.add(new Npc("guard1", "", false, Kind.GOLEM, null, 3.5, 0, dz, 180f));
        // the gallery, on its tiers, and two up on the balconies
        double[][] gallery = {{-7.5, 0, 6.5}, {5.5, 0, 6.5}, {-4.5, 1, 9.5}, {8.5, 1, 9.5}, {-9.5, 2, 12.5}, {3.5, 2, 12.5}, {10.0, 6, 0.5}, {-10.0, 6, 4.5}};
        VillagerProfession[] gp = {VillagerProfession.BUTCHER, VillagerProfession.NONE, VillagerProfession.FISHERMAN,
                VillagerProfession.LEATHERWORKER, VillagerProfession.NONE, VillagerProfession.ARMORER, VillagerProfession.CLERIC, VillagerProfession.FARMER};
        for (int i = 0; i < gallery.length; i++) {
            out.add(villager("gallery" + i, "", false, gp[i], gallery[i][0], gallery[i][1], gallery[i][2], 0.5, -8));
        }
        if (sc.advocate()) {
            out.add(villager("advocate", "An Advocate of the Hand", true, VillagerProfession.CARTOGRAPHER, 5.5, 0, -0.5, 0.5, -14.5));
        }

        // ---- the prison (relative to CELL)
        int c = CELL.getZ();
        double sx = CELL_SPOT.getX() + 0.5, sz = CELL_SPOT.getZ() + 0.5;
        String[] names = {"Old Maddock", "Wren", "Brother Tam", "Kesh"};
        VillagerProfession[] pj = {VillagerProfession.NITWIT, VillagerProfession.NONE, VillagerProfession.NITWIT, VillagerProfession.NONE};
        double[][] cells = {{0.5, -7.5, 0.5, 4.5}, {-6.5, -8.5, -6.5, 0}, {-5.5, 4.5, -6, 0}, {6.5, 3.5, 6, 0}};
        double[][] quarry = {{-24.5, -5.5}, {-24.5, -3.5}, {-24.5, 1.5}, {-24.5, 3.5}};
        double[][] tables = {{17.5, -5.5, 0}, {20.5, -3.5, 180}, {18.5, 1.5, 0}, {22.5, 3.5, 180}};
        for (int i = 0; i < 4; i++) {
            String role = "prisoner" + i;
            switch (sc.phase()) {
                case PHASE_LABOUR -> out.add(new Npc(role, names[i], true, Kind.VILLAGER, pj[i], quarry[i][0], 0, c + quarry[i][1], 90f));
                case PHASE_MEAL -> out.add(new Npc(role, names[i], true, Kind.VILLAGER, pj[i], tables[i][0], 0.5, c + tables[i][1], (float) tables[i][2]));
                default -> out.add(villager(role, names[i], true, pj[i], cells[i][0], 0, c + cells[i][1], cells[i][2], c + cells[i][3]));
            }
        }
        // two more inmates, who keep to themselves
        double[][] inmatesCell = {{5.5, -8.5, 0}, {7.5, -7.5, 0}};
        double[][] inmatesQuarry = {{-24.5, -1.5, 90}, {-22.5, 0.5, 90}};
        double[][] inmatesMeal = {{23.5, -5.5, 0}, {16.5, 3.5, 180}};
        for (int i = 0; i < 2; i++) {
            double[] at = sc.phase() == PHASE_LABOUR ? inmatesQuarry[i] : sc.phase() == PHASE_MEAL ? inmatesMeal[i] : inmatesCell[i];
            out.add(new Npc("inmate" + i, "", false, Kind.VILLAGER, i == 0 ? VillagerProfession.MASON : VillagerProfession.NONE,
                    at[0], sc.phase() == PHASE_MEAL ? 0.5 : 0, c + at[1], (float) at[2]));
        }
        // the gaoler: watching your cell, at the quarry, serving supper, or on his night round
        if (!Double.isNaN(sc.gaolerX())) {
            out.add(new Npc("gaoler", "The Gaoler", true, Kind.VILLAGER, VillagerProfession.TOOLSMITH, sc.gaolerX(), 0, c + ROUND_Z, 90f));
        } else if (sc.phase() == PHASE_LABOUR) {
            out.add(villager("gaoler", "The Gaoler", true, VillagerProfession.TOOLSMITH, -20.5, 0, c - 5.5, -27, c));
        } else if (sc.phase() == PHASE_MEAL) {
            out.add(new Npc("gaoler", "The Gaoler", true, Kind.VILLAGER, VillagerProfession.TOOLSMITH, 27.5, 0, c - 1.5, 90f));
        } else {
            out.add(villager("gaoler", "The Gaoler", true, VillagerProfession.TOOLSMITH, 1.5, 0, c + ROUND_Z, sx, sz));
        }
        // the prison's guards: one watching the quarry cart (or not), one at the gate
        float gy = sc.guardWatching() ? face(GUARD_QUARRY[0], GUARD_QUARRY[2], CART.getX() + 0.5, CART.getZ() + 0.5) : 90f;
        out.add(new Npc("guard2", "", false, Kind.GOLEM, null, GUARD_QUARRY[0], 0, c + GUARD_QUARRY[2], gy));
        out.add(new Npc("guard3", "", false, Kind.GOLEM, null, 23.5, 0, c - 8.5, 0f));
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
    /** Builds (or rebuilds, after an update) the court and the prison for a slot. True if it built anything. */
    public static boolean ensure(ServerLevel level, int slot) {
        CourtData data = CourtData.get(level);
        if (data.builtVersion(slot) >= VERSION) {
            return false;
        }
        BlockPos o = origin(slot);
        for (int cx = (o.getX() - 50) >> 4; cx <= (o.getX() + 50) >> 4; cx++) {
            for (int cz = (o.getZ() - 26) >> 4; cz <= (o.getZ() + 84) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        // clear whatever an older version built
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -48; x <= 48; x++) {
            for (int y = -16; y <= 30; y++) {
                for (int z = -24; z <= 82; z++) {
                    p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, FLAGS);
                    }
                }
            }
        }
        List<BlockPos> shapes = new ArrayList<>();
        // the prison (and its cavern) first: the court's back wall sits just beyond the cavern's
        prison(new B(level, o.offset(CELL), shapes));
        courtroom(new B(level, o, shapes));
        for (BlockPos q : shapes) {
            level.setBlock(q, Block.updateFromNeighbourShapes(level.getBlockState(q), level, q), FLAGS);
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

        /** fences, bars, walls and panes: they join up with what's around them once everything's built */
        void join(int x, int y, int z, Block b) {
            set(x, y, z, b);
            shapes.add(o.offset(x, y, z));
        }

        void join(int x, int y, int z, BlockState s) {
            set(x, y, z, s);
            shapes.add(o.offset(x, y, z));
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        set(x, y, z, s);
                    }
                }
            }
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
            fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
        }

        /** an invisible light, so the place can be seen without a lamp on every block */
        void light(int x, int y, int z, int level) {
            set(x, y, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level));
        }

        /** the Watch's banner: a golden eye with a black pupil, on blue */
        void watchBanner(int x, int y, int z, Direction facing) {
            BlockPos p = o.offset(x, y, z);
            BlockState st = Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
            level.setBlock(p, st, FLAGS);
            if (level.getBlockEntity(p) instanceof BannerBlockEntity be) {
                ListTag list = new ListTag();
                for (String[] l : new String[][]{{"rhombus", "yellow"}, {"circle", "black"}, {"border", "yellow"}}) {
                    CompoundTag t = new CompoundTag();
                    t.putString("pattern", "minecraft:" + l[0]);
                    t.putString("color", l[1]);
                    list.add(t);
                }
                CompoundTag tag = new CompoundTag();
                tag.put("patterns", list);
                try {
                    be.loadWithComponents(tag, level.registryAccess());
                    be.setChanged();
                    level.sendBlockUpdated(p, st, st, Block.UPDATE_CLIENTS);
                } catch (Exception ignored) {
                    // a plain blue banner, then
                }
            }
        }
    }

    // ------------------------------------------------------------------ small helpers
    /** a fixed scatter (the same every build), for cracks and stains */
    private static boolean speck(int x, int y, int z, int oneIn) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return Math.floorMod(h, oneIn) == 0;
    }

    private static int hash(int x, int y, int z, int salt) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + salt * 1013904223;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static double lattice(int x, int y, int z) {
        return (hash(x, y, z, 7) & 0xffff) / 32767.5 - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** smooth value noise in [-1, 1] */
    private static double noise(double x, double y, double z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double u = smooth(x - x0), v = smooth(y - y0), w = smooth(z - z0);
        double a = lerp(u, lattice(x0, y0, z0), lattice(x0 + 1, y0, z0));
        double b = lerp(u, lattice(x0, y0 + 1, z0), lattice(x0 + 1, y0 + 1, z0));
        double c = lerp(u, lattice(x0, y0, z0 + 1), lattice(x0 + 1, y0, z0 + 1));
        double d = lerp(u, lattice(x0, y0 + 1, z0 + 1), lattice(x0 + 1, y0 + 1, z0 + 1));
        return lerp(w, lerp(v, a, b), lerp(v, c, d));
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    private static double fbm(double x, double y, double z) {
        return noise(x / 9, y / 9, z / 9) * 0.6 + noise(x / 4.5, y / 4.5, z / 4.5) * 0.3 + noise(x / 2.2, y / 2.2, z / 2.2) * 0.1;
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

    private static BlockState stairs(Block b, Direction facing, Half half) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
    }

    /** a trapdoor: open ones lie flat against the block on the side opposite the way they face */
    private static BlockState trap(Block b, Direction facing, boolean open, Half half) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, open).setValue(TrapDoorBlock.HALF, half);
    }

    private static BlockState topSlab(Block b) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static void door(B b, int x, int z, Block door, Direction facing, DoorHingeSide hinge) {
        BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, false);
        b.set(x, 0, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(x, 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** a table: upside-down stairs for the ends (the legs), top slabs between */
    private static void table(B b, int x0, int x1, int y, int z, Block stair, Block slab) {
        b.set(x0, y, z, stairs(stair, Direction.WEST, Half.TOP));
        b.set(x1, y, z, stairs(stair, Direction.EAST, Half.TOP));
        for (int x = x0 + 1; x < x1; x++) {
            b.set(x, y, z, topSlab(slab));
        }
    }

    // ================================================================== the courtroom
    private static final int[] PIERS = {-18, -14, -10, -6, -2, 2, 6, 10, 14};

    /** the vaulted ceiling: how high the ceiling block is at a distance |x| from the middle */
    private static int vault(int ax) {
        return switch (ax) {
            case 11 -> 10;
            case 10 -> 11;
            case 9, 8 -> 12;
            case 7, 6 -> 13;
            case 5, 4 -> 14;
            default -> ax > 11 ? 10 : 15;
        };
    }

    private static void courtroom(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // ---- the shell, carved out under a stepped vault
        for (int x = -12; x <= 12; x++) {
            for (int y = -1; y <= 17; y++) {
                for (int z = -22; z <= 16; z++) {
                    b.set(x, y, z, speck(x, y, z, 6) ? Blocks.CRACKED_DEEPSLATE_BRICKS : speck(x, y, z, 5) ? Blocks.DEEPSLATE_TILES : Blocks.DEEPSLATE_BRICKS);
                }
            }
        }
        for (int x = -11; x <= 11; x++) {
            int h = vault(Math.abs(x));
            b.fill(x, 0, -21, x, h - 1, 15, air);
            for (int z = -21; z <= 15; z++) {
                b.set(x, h, z, Blocks.DARK_OAK_PLANKS);
            }
        }
        // the vault's steps, softened with upside-down stairs, and ribs across it over every pier
        for (int x = -11; x <= 11; x++) {
            int ax = Math.abs(x);
            boolean step = ax <= 10 && vault(ax) > vault(ax + 1);
            for (int z = -21; z <= 15; z++) {
                if (step && x != 0) {
                    b.set(x, vault(ax + 1), z, stairs(Blocks.DARK_OAK_STAIRS, x > 0 ? Direction.EAST : Direction.WEST, Half.TOP));
                }
            }
            for (int zr : PIERS) {
                b.set(x, vault(ax), zr, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
                if (ax <= 3) {
                    b.set(x, 14, zr, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
                }
            }
        }
        // ---- the floor: polished stone, a chequered well in front of the bench, a dark line under the bar
        for (int x = -11; x <= 11; x++) {
            for (int z = -21; z <= 15; z++) {
                b.set(x, -1, z, speck(x, -1, z, 7) ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE);
            }
        }
        for (int x = -10; x <= 10; x++) {
            for (int z = -12; z <= 3; z++) {
                b.set(x, -1, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.CALCITE);
            }
            b.set(x, -1, -13, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(x, -1, 4, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }

        // ---- the side walls: oak panelling, a carved rail, stone above with tall lit windows, piers between
        for (int s : new int[]{-1, 1}) {
            int wx = 12 * s, ix = 11 * s;
            Direction flat = s > 0 ? Direction.WEST : Direction.EAST;
            for (int z = -21; z <= 15; z++) {
                b.fill(wx, 0, z, wx, 2, z, Blocks.DARK_OAK_PLANKS);
                b.set(wx, 3, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
                for (int y = 0; y <= 2; y++) {
                    b.set(ix, y, z, trap(Blocks.DARK_OAK_TRAPDOOR, flat, true, Half.BOTTOM));
                }
            }
            for (int zr : PIERS) {
                b.set(ix, 0, zr, Blocks.POLISHED_BLACKSTONE);
                b.fill(ix, 1, zr, ix, 8, zr, Blocks.POLISHED_DEEPSLATE);
                b.set(ix, 9, zr, Blocks.CHISELED_DEEPSLATE);
                b.set(wx, 3, zr, Blocks.POLISHED_DEEPSLATE);
            }
            // the windows: amber in the middle, orange at the sides, a blue head; lit from behind
            for (int zc = -16; zc <= 12; zc += 4) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 6; y <= (dz == 0 ? 9 : 8); y++) {
                        Block glass = dz == 0 ? Blocks.YELLOW_STAINED_GLASS : y == 8 ? Blocks.BLUE_STAINED_GLASS : Blocks.ORANGE_STAINED_GLASS;
                        b.set(wx, y, zc + dz, glass);
                        b.light(wx + s, y, zc + dz, 15);
                        b.set(wx + 2 * s, y, zc + dz, Blocks.WHITE_CONCRETE);
                    }
                }
                b.set(ix, 5, zc, Blocks.POLISHED_DEEPSLATE_SLAB);
            }
        }

        // ---- the north wall: books in the corners, banners, the Watch's eye and its rays
        for (int x : new int[]{-10, -9, -8, -7, 7, 8, 9, 10}) {
            for (int y = 0; y <= 4; y++) {
                if (speck(x, y, -21, 3)) {
                    BlockState shelf = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
                    for (int i = 0; i < 6; i++) {
                        shelf = shelf.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i), !speck(x + i, y, -21, 3));
                    }
                    b.set(x, y, -21, shelf);
                } else {
                    b.set(x, y, -21, Blocks.BOOKSHELF);
                }
            }
            b.set(x, 5, -21, topSlab(Blocks.DARK_OAK_SLAB));
        }
        b.watchBanner(-6, 8, -21, Direction.SOUTH);
        b.watchBanner(6, 8, -21, Direction.SOUTH);
        eye(b, 0, 11, -22);

        // ---- the bench: two tiers, the judge's desk, a crimson screen and a high chair
        b.fill(-8, 0, -21, 8, 0, -14, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(-6, 1, -21, 6, 1, -14, Blocks.DARK_OAK_PLANKS);
        b.fill(-6, 0, -13, 6, 1, -13, Blocks.DARK_OAK_PLANKS);
        b.fill(-6, 2, -13, 6, 2, -13, Blocks.DARK_OAK_SLAB);
        for (int s : new int[]{-1, 1}) {
            b.fill(7 * s, 0, -13, 7 * s, 3, -13, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
            b.set(7 * s, 4, -13, candles(3));
        }
        for (int x = -6; x <= 6; x++) {
            if (x != 0) {
                b.set(x, 0, -12, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.SOUTH, true, Half.BOTTOM));
                b.set(x, 1, -12, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.SOUTH, true, Half.BOTTOM));
            }
        }
        b.watchBanner(0, 1, -12, Direction.SOUTH);
        // the screen behind the judge: a dark oak frame around crimson, with two banners
        b.fill(-4, 2, -18, -4, 6, -18, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(4, 2, -18, 4, 6, -18, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(-4, 7, -18, 4, 7, -18, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
        b.fill(-3, 2, -18, 3, 6, -18, Blocks.RED_WOOL);
        b.set(0, 8, -18, Blocks.CHISELED_POLISHED_BLACKSTONE);
        b.set(-4, 8, -18, candles(3));
        b.set(4, 8, -18, candles(3));
        b.watchBanner(-2, 6, -17, Direction.SOUTH);
        b.watchBanner(2, 6, -17, Direction.SOUTH);
        // the high chair
        b.set(0, 2, -16, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, Half.BOTTOM));
        b.fill(0, 2, -17, 0, 4, -17, Blocks.DARK_OAK_PLANKS);
        b.set(0, 5, -17, Blocks.CHISELED_POLISHED_BLACKSTONE);
        b.set(-1, 2, -16, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        b.set(1, 2, -16, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        // soul lanterns on posts either side of the judge
        for (int x : new int[]{-3, 3}) {
            b.join(x, 2, -15, Blocks.DARK_OAK_FENCE);
            b.set(x, 3, -15, Blocks.SOUL_LANTERN);
        }

        // ---- the clerk, at a lectern below the bench, with a candle on his desk
        b.set(-4, 0, -10, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH).setValue(LecternBlock.HAS_BOOK, true));
        b.set(-5, 0, -11, Blocks.DARK_OAK_PLANKS);
        b.set(-5, 1, -11, candles(2));

        // ---- the witness stand (west): a raised box with corner posts, a rail and a little roof
        b.fill(-10, 0, -12, -7, 0, -9, Blocks.DARK_OAK_PLANKS);
        for (int[] p : new int[][]{{-10, -12}, {-10, -9}, {-7, -12}, {-7, -9}}) {
            b.fill(p[0], 1, p[1], p[0], 3, p[1], log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        }
        for (int z = -11; z <= -10; z++) {
            b.join(-7, 1, z, Blocks.DARK_OAK_FENCE);
            b.fill(-10, 1, z, -10, 2, z, Blocks.DARK_OAK_PLANKS);
        }
        for (int x = -9; x <= -8; x++) {
            b.join(x, 1, -12, Blocks.DARK_OAK_FENCE);
            b.join(x, 1, -9, Blocks.DARK_OAK_FENCE);
        }
        b.fill(-10, 4, -12, -7, 4, -9, topSlab(Blocks.DARK_OAK_SLAB));
        b.set(-7, 5, -9, candles(1));

        // ---- the jury box (east): two raised rows behind a panelled rail, with benches
        b.fill(5, 0, -12, 7, 0, -4, Blocks.DARK_OAK_PLANKS);
        b.fill(8, 0, -12, 10, 1, -4, Blocks.DARK_OAK_PLANKS);
        for (int z = -12; z <= -4; z++) {
            b.join(5, 1, z, Blocks.DARK_OAK_FENCE);
            b.set(4, 0, z, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.WEST, true, Half.BOTTOM));
            if (z > -12 && z < -4) {
                b.set(7, 1, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, Half.BOTTOM));
                b.set(9, 2, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, Half.BOTTOM));
            }
        }
        for (int x = 6; x <= 10; x++) {
            int y = x >= 8 ? 2 : 1;
            b.join(x, y, -12, Blocks.DARK_OAK_FENCE);
            b.join(x, y, -4, Blocks.DARK_OAK_FENCE);
        }
        b.set(5, 2, -12, candles(2));
        b.set(5, 2, -4, candles(2));

        // ---- the tables: the Captain's (west), the defence's (east), the evidence (middle)
        table(b, -6, -4, 0, -5, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB);
        b.set(-5, 1, -5, candles(1));
        table(b, 4, 6, 0, -2, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB);
        b.set(6, 1, -2, candles(1));
        table(b, -1, 1, 0, -8, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB);
        b.set(-1, 1, -8, Blocks.DECORATED_POT);
        b.set(0, 1, -8, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 8));
        b.set(1, 1, -8, candles(3));

        // ---- the dock: a low dark rail all round (too high to climb over), posts and lamps only behind you
        b.fill(-1, -1, DOCK.getZ() - 1, 1, -1, DOCK.getZ() + 1, Blocks.POLISHED_BLACKSTONE);
        for (int x : new int[]{-1, 1}) {
            b.join(x, 0, DOCK.getZ() - 1, Blocks.DARK_OAK_FENCE);
            b.join(x, 0, DOCK.getZ() + 1, Blocks.DARK_OAK_FENCE);
            b.join(x, 1, DOCK.getZ() + 1, Blocks.DARK_OAK_FENCE);
            b.set(x, 2, DOCK.getZ() + 1, Blocks.LANTERN);
        }
        b.join(0, 0, DOCK.getZ() - 1, Blocks.DARK_OAK_FENCE);
        b.join(0, 0, DOCK.getZ() + 1, Blocks.DARK_OAK_FENCE);
        b.join(-1, 0, DOCK.getZ(), Blocks.DARK_OAK_FENCE);
        b.join(1, 0, DOCK.getZ(), Blocks.DARK_OAK_FENCE);

        // ---- the bar of the court: a rail with posts and lanterns, open in the middle
        for (int x = -10; x <= 10; x++) {
            if (Math.abs(x) <= 1) {
                continue;
            }
            if (Math.abs(x) == 2 || Math.abs(x) == 6 || Math.abs(x) == 10) {
                b.fill(x, 0, 4, x, 1, 4, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
                b.set(x, 2, 4, Blocks.LANTERN);
            } else {
                b.join(x, 0, 4, Blocks.DARK_OAK_FENCE);
            }
        }

        // ---- the gallery: three tiers of benches either side of the aisle
        for (int s : new int[]{-1, 1}) {
            int xa = s < 0 ? -10 : 2, xb = s < 0 ? -2 : 10;
            b.fill(xa, 0, 9, xb, 0, 14, Blocks.DARK_OAK_PLANKS);
            b.fill(xa, 1, 12, xb, 1, 14, Blocks.DARK_OAK_PLANKS);
            for (int x = xa; x <= xb; x++) {
                b.set(x, 0, 7, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, Half.BOTTOM));
                b.set(x, 1, 10, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, Half.BOTTOM));
                b.set(x, 2, 13, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, Half.BOTTOM));
                b.set(x, 0, 8, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, Half.BOTTOM));
                b.set(x, 1, 11, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, Half.BOTTOM));
            }
            // the balconies above, on corbels, with a rail
            int ba = s > 0 ? 8 : -11, bb = s > 0 ? 11 : -8;
            b.fill(ba, 5, -8, bb, 5, 12, Blocks.DARK_OAK_PLANKS);
            for (int z = -8; z <= 12; z++) {
                b.join(8 * s, 6, z, Blocks.DARK_OAK_FENCE);
                if ((z & 1) == 0) {
                    b.set(8 * s, 4, z, stairs(Blocks.DARK_OAK_STAIRS, s > 0 ? Direction.EAST : Direction.WEST, Half.TOP));
                }
            }
            b.set(8 * s, 7, -8, candles(2));
            b.set(8 * s, 7, 12, candles(2));
        }

        // ---- the doors (south): a double portal, lanterns either side, a rose window above
        b.fill(-3, 0, 16, 3, 4, 16, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(0, 0, 16, 0, 2, 16, Blocks.POLISHED_BLACKSTONE);
        door(b, -1, 16, Blocks.DARK_OAK_DOOR, Direction.NORTH, DoorHingeSide.LEFT);
        door(b, 1, 16, Blocks.DARK_OAK_DOOR, Direction.NORTH, DoorHingeSide.RIGHT);
        b.fill(-1, 0, 17, 1, 2, 17, Blocks.DEEPSLATE_BRICKS);
        b.set(0, 3, 16, Blocks.CHISELED_POLISHED_BLACKSTONE);
        for (int x : new int[]{-2, 2}) {
            b.join(x, 0, 15, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.join(x, 1, 15, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.set(x, 2, 15, Blocks.LANTERN);
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                int d2 = dx * dx + dy * dy;
                if (d2 > 17) {
                    continue;
                }
                int y = 10 + dy;
                if (d2 > 12) {
                    b.set(dx, y, 16, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    continue;
                }
                Block glass = d2 <= 1 ? Blocks.YELLOW_STAINED_GLASS : (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy)) ? Blocks.BLACK_STAINED_GLASS
                        : d2 <= 5 ? Blocks.ORANGE_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS;
                b.set(dx, y, 16, glass);
                b.light(dx, y, 17, 15);
                b.set(dx, y, 18, Blocks.WHITE_CONCRETE);
            }
        }

        // ---- the aisle carpet, from the doors to the bench
        for (int z = -11; z <= 15; z++) {
            if (Math.abs(z - DOCK.getZ()) <= 1 || z == -8) {
                continue;
            }
            for (int x = -1; x <= 1; x++) {
                b.set(x, 0, z, Blocks.RED_CARPET);
            }
        }

        // ---- chandeliers: candles and lanterns on oak crosses down the middle, single lamps down the sides
        // (none in the middle over the bench: nothing hangs in front of the eye)
        for (int[] c : new int[][]{{0, -2}, {0, 6}, {-7, -10}, {7, -10}}) {
            int xc = c[0], zc = c[1];
            int top = Math.min(13, vault(Math.abs(xc)) - 1);
            b.fill(xc, 10, zc, xc, top, zc, Blocks.CHAIN);
            b.join(xc, 9, zc, Blocks.DARK_OAK_FENCE);
            for (int[] a : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                b.join(xc + a[0], 9, zc + a[1], Blocks.DARK_OAK_FENCE);
                b.set(xc + a[0], 8, zc + a[1], hanging(Blocks.LANTERN));
                b.set(xc + a[0], 10, zc + a[1], candles(3));
            }
        }
        for (int zc : new int[]{-14, -6, 2, 10}) {
            for (int x : new int[]{-6, 6}) {
                b.fill(x, 10, zc, x, 12, zc, Blocks.CHAIN);
                b.set(x, 9, zc, hanging(Blocks.LANTERN));
            }
        }

        // ---- light enough to see by, from nowhere in particular
        for (int z = -18; z <= 12; z += 6) {
            b.light(-4, 9, z, 12);
            b.light(4, 9, z, 12);
        }
        b.light(0, 6, -15, 13);
        b.light(0, 4, -20, 12);
        b.light(-8, 4, -16, 11);
        b.light(8, 4, -16, 11);
        for (int z = -6; z <= 12; z += 6) {
            b.light(-10, 3, z, 10);
            b.light(10, 3, z, 10);
        }
        b.light(-6, 4, 12, 11);
        b.light(6, 4, 12, 11);
        b.light(0, 3, 9, 11);
    }

    /** the Watch's great eye, set in the wall around (cx, cy, z), with rays of light around it (on the inside face) */
    private static void eye(B b, int cx, int cy, int z) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                double lid = 3.4 * (1 - (dx / 7.6) * (dx / 7.6));
                boolean in = Math.abs(dy) <= lid;
                boolean near = !in && Math.abs(dy) <= lid + 1.0;
                if (in) {
                    double r2 = dx * dx + dy * dy;
                    Block blk = r2 <= 1.2 ? Blocks.BLACK_CONCRETE : r2 <= 5.5 ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK;
                    b.set(cx + dx, cy + dy, z, blk);
                } else if (near) {
                    b.set(cx + dx, cy + dy, z, Blocks.POLISHED_BLACKSTONE);
                }
            }
        }
        // rays: end rods standing out of the wall all round the eye's upper half and sides
        for (int deg = -30; deg <= 210; deg += 20) {
            double a = Math.toRadians(deg);
            double c = Math.cos(a), s = Math.sin(a);
            Direction dir = Math.abs(c) * 8.6 > Math.abs(s) * 4.4 ? (c > 0 ? Direction.EAST : Direction.WEST) : (s > 0 ? Direction.UP : Direction.DOWN);
            for (int k = 0; k < 2; k++) {
                int x = cx + (int) Math.round(c * (9.4 + k * 1.3));
                int y = cy + (int) Math.round(s * (4.6 + k * 1.1));
                if (y < 9 || y > vault(Math.abs(x)) - 1 || Math.abs(x) > 10) {
                    continue;
                }
                b.set(x, y, z + 1, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, dir));
            }
        }
    }

    // ================================================================== the prison (everything relative to CELL)
    private static void prison(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        cavern(b);
        // ---- the shelf the prison stands on, and the pillars under it down into the lava
        for (int x = -29; x <= 29; x++) {
            for (int z = -11; z <= 6; z++) {
                b.set(x, -2, z, speck(x, -2, z, 4) ? Blocks.POLISHED_BASALT : Blocks.BASALT);
                b.fill(x, -5, z, x, -3, z, air);
            }
        }
        for (int x = 19; x <= 22; x++) {
            for (int z = -16; z <= -12; z++) {
                b.set(x, -2, z, Blocks.BASALT);
            }
        }
        for (int x : new int[]{-29, -21, -13, -6, 0, 6, 13, 21, 29}) {
            for (int z : new int[]{-11, -3, 6}) {
                b.fill(x, -7, z, x, -3, z, log(Blocks.BASALT, Direction.Axis.Y));
            }
        }
        cellBlock(b);
        quarry(b);
        cafeteria(b);
        gateAndBridge(b);
        // the doors between: the corridor's west end to the quarry, east end to the cafeteria (two walls thick)
        door(b, -12, -2, Blocks.IRON_DOOR, Direction.EAST, DoorHingeSide.LEFT);
        b.fill(-13, 0, -2, -13, 1, -2, air);
        door(b, 12, -2, Blocks.IRON_DOOR, Direction.WEST, DoorHingeSide.LEFT);
        b.fill(13, 0, -2, 13, 1, -2, air);
        outside(b);
    }

    // ------------------------------------------------------------------ the prison from outside
    /** where the keep's piers stand along its long walls (in line with the walls between the cells) */
    private static final int[] KEEP_PIERS = {-12, -9, -3, 3, 9, 12};

    /**
     * How the prison looks from the lava: a black keep with dressed stone, a cornice and battlements, and a bell tower on
     * top; the cafeteria's hall with buttresses, battlements and a smoking chimney; the quarry a crag of raw rock; the shelf
     * they stand on a ragged rim of basalt. Nothing here touches the ledge under your window.
     */
    private static void outside(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // ---- the shelf: a ragged rim beyond the walls (but open water, so to speak, under the ledge)
        for (int x = -33; x <= 33; x++) {
            for (int z = -14; z <= 9; z++) {
                int ox = x < -29 ? -29 - x : x > 29 ? x - 29 : 0;
                int oz = z < -11 ? -11 - z : z > 6 ? z - 6 : 0;
                int out = Math.max(ox, oz);
                if (out == 0 || (z >= 7 && x >= -3 && x <= 17)) {
                    continue;
                }
                int reach = 1 + (int) Math.floor((fbm(x * 1.7, 3, z * 1.7) + 0.5) * 3.2);
                if (out > reach) {
                    continue;
                }
                b.set(x, -2, z, speck(x, -2, z, 3) ? Blocks.POLISHED_BASALT.defaultBlockState() : speck(x, -1, z, 4)
                        ? Blocks.BLACKSTONE.defaultBlockState() : log(Blocks.BASALT, Direction.Axis.Y));
                if (out < reach) {
                    b.set(x, -3, z, Blocks.BLACKSTONE);
                }
            }
        }

        // ---- the keep (the cell block): a dark plinth, piers, a band at the lintels, a cornice, battlements
        for (int face : new int[]{-11, 6}) {
            int out = face < 0 ? -1 : 1;
            Direction away = face < 0 ? Direction.NORTH : Direction.SOUTH;
            for (int x = -12; x <= 12; x++) {
                b.set(x, -1, face, Blocks.POLISHED_BLACKSTONE_BRICKS);
                b.set(x, 3, face, Blocks.POLISHED_BLACKSTONE_BRICKS);
                b.set(x, 5, face, ((x + 12) % 4 == 0) ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS);
                // the cornice: stairs standing out under the battlements
                b.join(x, 5, face + out, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, away.getOpposite(), Half.TOP));
                // battlements: a low wall, a merlon every third block
                if (Math.floorMod(x, 3) == 0) {
                    b.fill(x, 6, face, x, 7, face, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    b.set(x, 8, face, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
                } else {
                    b.join(x, 6, face, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
                }
            }
            for (int x : KEEP_PIERS) {
                for (int y = 0; y <= 4; y++) {
                    b.set(x, y, face, Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            // lamps under the cornice between the piers
            for (int x : new int[]{-6, 6}) {
                b.set(x, 4, face + out, hanging(Blocks.SOUL_LANTERN));
            }
        }
        for (int x = -12; x <= 12; x++) {
            for (int z = -10; z <= 5; z++) {
                if (z >= -4 && z <= 0) {
                    continue; // the corridor's own ceiling
                }
                b.set(x, 5, z, speck(x, 5, z, 6) ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // the other prisoners' windows: small and barred, like yours (the north row looks out through two walls)
        for (int cx : new int[]{-6, 6}) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int y = 1; y <= 2; y++) {
                    b.join(x, y, 6, Blocks.IRON_BARS);
                }
            }
        }
        for (int cx : new int[]{-6, 0, 6}) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int y = 1; y <= 2; y++) {
                    b.set(x, y, -10, air);
                    b.join(x, y, -11, Blocks.IRON_BARS);
                }
            }
        }

        // ---- the bell tower on the keep's roof (the bell you hear every day)
        for (int x = -2; x <= 2; x++) {
            for (int z = -9; z <= -5; z++) {
                boolean rim = Math.abs(x) == 2 || z == -9 || z == -5;
                boolean corner = Math.abs(x) == 2 && (z == -9 || z == -5);
                for (int y = 6; y <= 12; y++) {
                    boolean arch = y >= 9 && y <= 11 && !corner;
                    if (!rim || arch) {
                        b.set(x, y, z, y == 8 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : y >= 9 && y <= 11 ? air
                                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    } else {
                        b.set(x, y, z, corner ? log(Blocks.BASALT, Direction.Axis.Y) : y == 8 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    }
                }
                b.set(x, 12, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int i = -1; i <= 1; i++) {
            b.join(i, 9, -9, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.join(i, 9, -5, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.join(-2, 9, -7 + i, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.join(2, 9, -7 + i, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        b.set(0, 11, -7, Blocks.BELL.defaultBlockState()
                .setValue(BlockStateProperties.BELL_ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        // a pointed roof: two rings of stairs and a lamp on top
        for (int r = 2; r >= 1; r--) {
            int y = 15 - r;
            for (int x = -r; x <= r; x++) {
                for (int z = -7 - r; z <= -7 + r; z++) {
                    if (Math.abs(x) != r && Math.abs(z + 7) != r) {
                        b.set(x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                        continue;
                    }
                    Direction up = Math.abs(z + 7) == r ? (z < -7 ? Direction.SOUTH : Direction.NORTH) : (x < 0 ? Direction.EAST : Direction.WEST);
                    b.join(x, y, z, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, up, Half.BOTTOM));
                }
            }
        }
        b.set(0, 14, -7, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.join(0, 15, -7, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        b.set(0, 16, -7, Blocks.SOUL_LANTERN);
        b.watchBanner(0, 7, -4, Direction.SOUTH);
        b.watchBanner(0, 7, -10, Direction.NORTH);
        b.light(0, 10, -7, 9);

        // ---- the hall (the cafeteria): a dark plinth, buttresses, battlements, a slate roof and the kitchen chimney
        for (int x = 13; x <= 29; x++) {
            b.set(x, -1, -11, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(x, -1, 6, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int z = -11; z <= 6; z++) {
            b.set(29, -1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int x = 14; x <= 28; x++) {
            for (int z = -10; z <= 5; z++) {
                b.set(x, 8, z, Blocks.DEEPSLATE_TILE_SLAB);
            }
        }
        // battlements round the hall's roof (the west side looks down on the keep)
        for (int x = 13; x <= 29; x++) {
            for (int z = -11; z <= 6; z++) {
                if (x != 13 && x != 29 && z != -11 && z != 6) {
                    continue;
                }
                if (Math.floorMod(x + z, 3) == 0) {
                    b.set(x, 8, z, Blocks.STONE_BRICKS);
                    b.set(x, 9, z, Blocks.STONE_BRICK_SLAB);
                } else {
                    b.join(x, 8, z, Blocks.STONE_BRICK_WALL);
                }
            }
        }
        // buttresses: east wall, north wall (either side of the gate), south wall (east of the ledge)
        int[][] butts = {{30, -9}, {30, -4}, {30, 1}, {30, 5}, {15, -12}, {26, -12}, {19, 7}, {23, 7}, {27, 7}};
        for (int[] bt : butts) {
            int x = bt[0], z = bt[1];
            Direction in = x == 30 ? Direction.WEST : z < 0 ? Direction.SOUTH : Direction.NORTH;
            for (int y = -1; y <= 3; y++) {
                b.set(x, y, z, speck(x, y, z, 6) ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
            }
            b.set(x, -1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(x, 4, z, stairs(Blocks.STONE_BRICK_STAIRS, in, Half.BOTTOM));
            b.set(x, -2, z, Blocks.BASALT);
        }
        for (int[] l : new int[][]{{30, -6}, {30, 3}, {17, 7}, {25, 7}}) {
            b.set(l[0], 3, l[1], Blocks.STONE_BRICKS);
            b.set(l[0], 2, l[1], hanging(Blocks.LANTERN));
        }
        // the kitchen chimney, smoking
        b.fill(27, 8, -7, 28, 10, -6, Blocks.STONE_BRICKS);
        b.set(27, 11, -6, Blocks.CAMPFIRE);
        b.set(28, 11, -6, Blocks.STONE_BRICK_SLAB);
        b.set(27, 11, -7, Blocks.STONE_BRICK_SLAB);
        b.set(28, 11, -7, Blocks.STONE_BRICK_SLAB);

        // ---- the quarry: no walls at all from outside, just a crag of raw rock bulging out over the lava
        for (int y = -1; y <= 10; y++) {
            for (int z = -11; z <= 6; z++) {
                int d = bulge(-30, y, z, y);
                for (int k = 1; k <= d; k++) {
                    b.set(-29 - k, y, z, rock(-29 - k, y, z));
                }
            }
            for (int x = -29; x <= -13; x++) {
                int dn = bulge(x, y, -12, y);
                for (int k = 1; k <= dn; k++) {
                    b.set(x, y, -11 - k, rock(x, y, -11 - k));
                }
                int ds = bulge(x, y, 7, y);
                for (int k = 1; k <= ds; k++) {
                    b.set(x, y, 6 + k, rock(x, y, 6 + k));
                }
            }
        }
        for (int x = -29; x <= -13; x++) {
            for (int z = -11; z <= 6; z++) {
                int edge = Math.min(Math.min(x + 29, -13 - x), Math.min(z + 11, 6 - z));
                int h = (int) Math.floor((fbm(x * 1.3, 40, z * 1.3) + 0.4) * 4.5) - Math.max(0, 2 - edge);
                for (int y = 11; y <= 10 + h; y++) {
                    b.set(x, y, z, rock(x, y, z));
                }
                if (speck(x, 11 + Math.max(h, 0), z, 9)) {
                    b.set(x, 11 + Math.max(h, 0), z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
                }
            }
        }
    }

    /** how far the quarry's crag bulges out at a point of its wall (none at the foot, where it meets the shelf) */
    private static int bulge(int x, int y, int z, int height) {
        double n = fbm(x * 1.5 + 11, y * 1.5, z * 1.5);
        int d = (int) Math.floor((n + 0.35) * 4.0);
        if (height <= 1) {
            d = Math.min(d, 1);
        }
        return Math.max(0, Math.min(3, d));
    }

    /** the cell block: a corridor five wide, three cells either side behind bars */
    private static void cellBlock(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -12; x <= 12; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -11; z <= 6; z++) {
                    b.set(x, y, z, speck(x, y, z, 9) ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
                }
            }
        }
        // the corridor: dressed stone, a drain down the middle with iron grates, ribs and corbels overhead
        b.fill(-11, 0, -4, 11, 4, 0, air);
        for (int x = -11; x <= 11; x++) {
            for (int z = -4; z <= 0; z++) {
                b.set(x, -1, z, z == -2 ? Blocks.POLISHED_BLACKSTONE : speck(x, 0, z, 4) ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES);
                b.set(x, 5, z, Blocks.DEEPSLATE_TILES);
            }
            for (int y = 3; y <= 4; y++) {
                b.set(x, y, -5, Blocks.POLISHED_BLACKSTONE_BRICKS);
                b.set(x, y, 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int x : new int[]{-11, -10, -9, -3, 3, 9, 10, 11}) {
            for (int y = 0; y <= 2; y++) {
                b.set(x, y, -5, Blocks.POLISHED_BLACKSTONE_BRICKS);
                b.set(x, y, 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int z = -4; z <= 0; z++) {
            b.fill(-12, 0, z, -12, 4, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.fill(12, 0, z, 12, 4, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int x : new int[]{-9, -3, 3, 9}) {
            for (int z = -4; z <= 0; z++) {
                b.set(x, 4, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
            b.set(x, 3, -4, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.NORTH, Half.TOP));
            b.set(x, 3, 0, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.SOUTH, Half.TOP));
            b.set(x, 3, -2, hanging(Math.abs(x) == 9 ? Blocks.SOUL_LANTERN : Blocks.LANTERN));
        }
        for (int x : new int[]{-6, 0, 6}) {
            b.set(x, 4, -2, hanging(Blocks.LANTERN));
        }
        for (int x = -10; x <= 10; x += 3) {
            b.set(x, 0, -2, Blocks.IRON_TRAPDOOR);
        }
        for (int x : new int[]{-8, -1, 6}) {
            b.light(x, 4, -3, 12);
        }
        // the cells
        for (int cx : new int[]{-6, 0, 6}) {
            for (int[] rows : new int[][]{{-9, -6, -5}, {2, 5, 1}}) {
                int z0 = rows[0], z1 = rows[1], bars = rows[2];
                b.fill(cx - 2, 0, z0, cx + 2, 3, z1, air);
                for (int x = cx - 2; x <= cx + 2; x++) {
                    for (int z = z0; z <= z1; z++) {
                        b.set(x, -1, z, speck(x, 1, z, 5) ? Blocks.MOSSY_COBBLESTONE : speck(x, 2, z, 3) ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.COBBLED_DEEPSLATE);
                    }
                    for (int y = 0; y <= 2; y++) {
                        b.join(x, y, bars, Blocks.IRON_BARS);
                    }
                }
                door(b, cx - 2, bars, Blocks.IRON_DOOR, bars < 0 ? Direction.NORTH : Direction.SOUTH, DoorHingeSide.LEFT);
                b.light(cx, 3, (z0 + z1) / 2, 6);
            }
        }
        // ---- your cell: a hay cot under the window, a bucket, a stool, a shelf with a candle, chains, scratches
        b.set(-2, 0, 5, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        b.set(-1, 0, 5, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        b.set(-2, 1, 5, Blocks.WHITE_CARPET);
        b.set(-1, 1, 5, Blocks.BROWN_CARPET);
        b.set(2, 0, 5, Blocks.CAULDRON);
        b.set(1, 0, 5, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 9));
        b.set(2, 0, 2, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
        b.set(-2, 2, 3, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, false, Half.TOP));
        b.set(-2, 3, 3, candles(2));
        b.set(2, 3, 3, Blocks.CHAIN);
        b.set(2, 2, 3, Blocks.CHAIN);
        b.set(-3, 1, 3, Blocks.CHISELED_DEEPSLATE);
        b.set(-3, 1, 4, Blocks.CHISELED_DEEPSLATE);
        b.set(-2, 3, 2, Blocks.COBWEB);
        b.set(2, 3, 5, Blocks.COBWEB);
        b.set(0, 3, 3, hanging(Blocks.LANTERN));
        for (int x = -1; x <= 1; x++) {
            b.join(x, 1, 6, Blocks.IRON_BARS);
            b.join(x, 2, 6, Blocks.IRON_BARS);
        }
        // ---- the other cells
        b.set(-2, 0, -9, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        b.set(-1, 0, -9, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        b.set(-2, 1, -9, Blocks.BROWN_CARPET);
        b.set(2, 0, -9, candles(1));
        b.set(2, 3, -9, Blocks.COBWEB);
        b.set(-2, 3, -6, Blocks.CHAIN);
        b.set(-8, 0, -9, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-8, 0, -8, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-4, 0, -9, candles(2));
        b.set(-4, 3, -9, Blocks.COBWEB);
        b.set(-8, 2, -6, Blocks.CHAIN);
        b.set(-8, 3, -6, Blocks.CHAIN);
        b.set(8, 0, -9, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(8, 0, -8, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(4, 0, -9, candles(1));
        b.set(8, 3, -6, Blocks.CHAIN);
        b.set(8, 2, -6, Blocks.CHAIN);
        b.set(4, 3, -6, Blocks.COBWEB);
        b.set(-8, 0, 4, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-8, 0, 5, log(Blocks.HAY_BLOCK, Direction.Axis.Z));
        b.set(-4, 0, 5, candles(2));
        b.set(-8, 3, 2, Blocks.COBWEB);
        b.set(8, 0, 5, Blocks.CAULDRON);
        b.set(4, 3, 5, Blocks.COBWEB);
        b.set(7, 0, 5, candles(1));
        b.set(4, 0, 2, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 3));
        // ---- the corridor's ends: the gaoler's corner (east), cobwebs and bones (west)
        b.set(11, 0, -4, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        b.set(11, 0, 0, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(11, 1, 0, Blocks.LANTERN);
        b.set(10, 0, -4, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
        b.set(-11, 4, -4, Blocks.COBWEB);
        b.set(-11, 4, 0, Blocks.COBWEB);
        b.set(-11, 0, -4, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 6));
    }

    /** the quarry, west of the cells: a rough cave of a room, a face to break, timber frames, rails, a cart of tools */
    private static void quarry(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -29; x <= -13; x++) {
            for (int y = -1; y <= 10; y++) {
                for (int z = -11; z <= 6; z++) {
                    b.set(x, y, z, rock(x, y, z));
                }
            }
        }
        b.fill(-28, 0, -10, -14, 8, 5, air);
        // a rough ceiling and upper walls
        for (int x = -28; x <= -14; x++) {
            for (int z = -10; z <= 5; z++) {
                if (speck(x, 9, z, 3)) {
                    b.set(x, 8, z, rock(x, 8, z));
                    if (speck(x, 7, z, 3)) {
                        b.set(x, 7, z, rock(x, 7, z));
                    }
                }
                boolean edge = x == -28 || x == -14 || z == -10 || z == 5;
                for (int y = 5; y <= 7 && edge; y++) {
                    if (speck(x, y, z, 2)) {
                        b.set(x, y, z, rock(x, y, z));
                    }
                }
            }
        }
        // the floor: gravel, cobble, dirt and stone chips
        for (int x = -28; x <= -14; x++) {
            for (int z = -10; z <= 5; z++) {
                b.set(x, -1, z, speck(x, 0, z, 3) ? Blocks.GRAVEL : speck(x, 1, z, 4) ? Blocks.COBBLESTONE : speck(x, 2, z, 5) ? Blocks.ANDESITE : Blocks.COARSE_DIRT);
            }
        }
        // the face (it never runs out), with the rock above it cut back in steps
        for (int z = -10; z <= 5; z++) {
            for (int y = 0; y <= 8; y++) {
                boolean face = z >= -6 && z <= 4 && y <= 4;
                b.set(-27, y, z, face ? (speck(-27, y, z, 5) ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState()) : rock(-27, y, z));
            }
            b.fill(-26, 6, z, -26, 8, z, rock(-26, 7, z));
            b.set(-25, 8, z, rock(-25, 8, z));
            if (z >= -6 && z <= 4 && speck(-26, 0, z, 3)) {
                b.set(-26, 0, z, Blocks.COBBLESTONE_SLAB);
            }
        }
        // timber frames holding the roof up, lanterns hanging from them
        for (int x : new int[]{-23, -18}) {
            b.fill(x, 0, -10, x, 6, -10, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
            b.fill(x, 0, 5, x, 6, 5, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
            b.fill(x, 7, -10, x, 7, 5, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
            b.set(x, 6, -5, hanging(Blocks.LANTERN));
            b.set(x, 6, 1, hanging(Blocks.LANTERN));
        }
        // a rail from the face to the spoil heap
        for (int x = -26; x <= -17; x++) {
            b.set(x, 0, -8, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        b.set(-16, 0, -8, Blocks.COBBLESTONE);
        b.set(-16, 0, -9, Blocks.COBBLESTONE);
        b.set(-15, 0, -9, stairs(Blocks.COBBLESTONE_STAIRS, Direction.WEST, Half.BOTTOM));
        b.set(-16, 1, -9, Blocks.GRAVEL);
        b.set(-15, 0, -8, Blocks.COBBLESTONE_SLAB);
        // a crane over the face, with a bucket hanging from it
        for (int y = 0; y <= 4; y++) {
            b.join(-21, y, -6, Blocks.SPRUCE_FENCE);
        }
        for (int x = -24; x <= -21; x++) {
            b.join(x, 5, -6, Blocks.SPRUCE_FENCE);
        }
        b.set(-24, 4, -6, Blocks.CHAIN);
        b.set(-24, 3, -6, Blocks.CAULDRON);
        // the cart: barrels of tools, a grindstone, a chest, a lamp
        b.set(CART.getX(), 0, CART.getZ(), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(CART.getX(), 0, CART.getZ() + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(CART.getX() - 1, 0, CART.getZ(), Blocks.GRINDSTONE);
        b.set(CART.getX() + 1, 0, CART.getZ(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        b.set(CART.getX(), 1, CART.getZ() + 1, Blocks.LANTERN);
        // water troughs
        b.set(-14, 0, -9, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        b.set(-14, 0, -8, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        for (int[] l : new int[][]{{-26, -8}, {-20, -2}, {-16, 3}, {-25, 2}}) {
            b.light(l[0], 6, l[1], 10);
        }
    }

    /** rough rock: stone, with andesite, cobble, tuff and deepslate through it */
    private static BlockState rock(int x, int y, int z) {
        int h = Math.floorMod(hash(x, y, z, 3), 20);
        Block blk = h < 9 ? Blocks.STONE : h < 13 ? Blocks.ANDESITE : h < 16 ? Blocks.COBBLESTONE : h < 18 ? Blocks.TUFF : Blocks.DEEPSLATE;
        return blk.defaultBlockState();
    }

    /** the cafeteria, east of the cells: beams, two long tables, a kitchen, barred windows over the lava, the gate */
    private static void cafeteria(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 13; x <= 29; x++) {
            for (int y = -1; y <= 7; y++) {
                for (int z = -11; z <= 6; z++) {
                    b.set(x, y, z, speck(x, y, z, 7) ? Blocks.MOSSY_STONE_BRICKS : speck(x, y, z, 9) ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
            }
        }
        b.fill(14, 0, -10, 28, 6, 5, air);
        for (int x = 14; x <= 28; x++) {
            for (int z = -10; z <= 5; z++) {
                b.set(x, -1, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.ANDESITE);
                b.set(x, 7, z, Blocks.SPRUCE_PLANKS);
            }
        }
        // beams on posts (none in front of the gate)
        for (int x : new int[]{16, 20, 24}) {
            b.fill(x, 6, -10, x, 6, 5, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
            b.fill(x, 0, 5, x, 5, 5, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
            if (x != 20) {
                b.fill(x, 0, -10, x, 5, -10, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
            }
            for (int z : new int[]{-6, -1, 3}) {
                b.set(x, 5, z, hanging(Blocks.LANTERN));
            }
        }
        // barred windows looking out over the lava
        for (int x : new int[]{15, 18, 22, 26}) {
            b.join(x, 2, 6, Blocks.IRON_BARS);
            b.join(x, 3, 6, Blocks.IRON_BARS);
        }
        // two long tables, benches either side, candles and cups
        for (int tz : new int[]{-5, 2}) {
            table(b, 16, 24, 0, tz, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB);
            for (int x = 16; x <= 24; x++) {
                b.set(x, 0, tz - 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.BOTTOM));
                b.set(x, 0, tz + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
            }
            b.set(18, 1, tz, candles(2));
            b.set(22, 1, tz, candles(1));
            b.set(20, 1, tz, Blocks.FLOWER_POT);
            b.set(17, 1, tz, Blocks.FLOWER_POT);
        }
        // the serving counter, and the kitchen behind it
        for (int z = -8; z <= 3; z++) {
            b.set(26, 0, z, Blocks.SPRUCE_PLANKS);
            b.set(26, 1, z, Blocks.SMOOTH_STONE_SLAB);
        }
        b.set(27, 0, -6, Blocks.CAMPFIRE);
        b.set(27, 1, -6, Blocks.CAULDRON);
        b.set(28, 0, -3, Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.WEST).setValue(AbstractFurnaceBlock.LIT, true));
        b.set(28, 0, 0, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(28, 0, 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(27, 0, 3, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(28, 1, 0, Blocks.LANTERN);
        b.set(27, 6, -1, Blocks.CHAIN);
        b.set(27, 5, -1, hanging(Blocks.LANTERN));
        // the gate: double iron doors in a blackstone frame, a grille above, lamps and banners either side
        b.fill(18, 0, -11, 23, 4, -11, Blocks.POLISHED_BLACKSTONE_BRICKS);
        door(b, GATE.getX(), GATE.getZ(), Blocks.IRON_DOOR, Direction.SOUTH, DoorHingeSide.LEFT);
        door(b, GATE.getX() + 1, GATE.getZ(), Blocks.IRON_DOOR, Direction.SOUTH, DoorHingeSide.RIGHT);
        b.join(20, 2, -11, Blocks.IRON_BARS);
        b.join(21, 2, -11, Blocks.IRON_BARS);
        b.set(20, 3, -11, Blocks.CHISELED_POLISHED_BLACKSTONE);
        b.set(21, 3, -11, Blocks.CHISELED_POLISHED_BLACKSTONE);
        for (int x : new int[]{19, 22}) {
            b.join(x, 0, -10, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.join(x, 1, -10, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.set(x, 2, -10, Blocks.LANTERN);
        }
        b.watchBanner(18, 5, -10, Direction.SOUTH);
        b.watchBanner(23, 5, -10, Direction.SOUTH);
        for (int x : new int[]{16, 22}) {
            for (int z : new int[]{-7, 2}) {
                b.light(x, 5, z, 11);
            }
        }
    }

    /** beyond the gate: a passage, a bridge over the lava, and a tunnel into the cavern wall */
    private static void gateAndBridge(B b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 19; x <= 22; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -16; z <= -12; z++) {
                    b.set(x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        b.fill(20, 0, -16, 21, 2, -12, air);
        b.set(20, 2, -14, hanging(Blocks.SOUL_LANTERN));
        // the bridge
        b.fill(19, 0, -22, 22, 4, -17, air);
        for (int z = -22; z <= -17; z++) {
            for (int x = 19; x <= 22; x++) {
                b.set(x, -1, z, x == 19 || x == 22 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE);
                b.set(x, -2, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
            b.join(19, 0, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.join(22, 0, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        for (int x : new int[]{19, 22}) {
            b.set(x, 1, -19, Blocks.LANTERN);
            b.fill(x, -7, -19, x, -3, -19, log(Blocks.BASALT, Direction.Axis.Y));
        }
        // the tunnel: a dark arch into the rock, a soul lamp or two, and then nothing
        for (int x = 18; x <= 23; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -30; z <= -23; z++) {
                    b.set(x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        b.fill(20, 0, -29, 21, 2, -23, air);
        b.set(20, 2, -25, hanging(Blocks.SOUL_LANTERN));
        b.set(21, 2, -28, hanging(Blocks.SOUL_LANTERN));
        b.set(19, 3, -23, Blocks.CHISELED_POLISHED_BLACKSTONE);
        b.set(22, 3, -23, Blocks.CHISELED_POLISHED_BLACKSTONE);
    }

    // ================================================================== the cavern
    private static final int CAVE_X0 = -46, CAVE_X1 = 46, CAVE_Y0 = -9, CAVE_Y1 = 26, CAVE_Z0 = -22, CAVE_Z1 = 40;

    /** how far a point is from the cavern's middle: under 1 inside, 1 to 1.5 the rock around it */
    private static double cave(int x, int y, int z) {
        double dx = x / 44.0, dy = (y - 8) / 17.0, dz = (z - 9) / 29.0;
        double v = dx * dx * dx * dx + dy * dy * dy * dy + dz * dz * dz * dz;
        return v + 0.18 * fbm(x, y * 1.4, z);
    }

    private static boolean caveRock(int x, int y, int z) {
        if (x < CAVE_X0 || x > CAVE_X1 || y < CAVE_Y0 || y > CAVE_Y1 || z < CAVE_Z0 || z > CAVE_Z1) {
            return false;
        }
        double v = cave(x, y, z);
        if (v >= 1 || edge(x, y, z)) {
            return v < 1.5;
        }
        return y <= -7 || shore(v, y);
    }

    /** the outermost layer of the cavern's box is always rock (no holes into the void, whatever the noise does) */
    private static boolean edge(int x, int y, int z) {
        return x == CAVE_X0 || x == CAVE_X1 || z == CAVE_Z0 || z == CAVE_Z1 || y == CAVE_Y1;
    }

    /** the rocky shore around the lava, rising towards the walls */
    private static boolean shore(double v, int y) {
        return v > 0.68 && y <= -6 + (int) ((v - 0.68) * 26);
    }

    private static BlockState caveStone(int x, int y, int z) {
        double n = noise(x / 7.0 + 31, y / 7.0, z / 7.0);
        if (y <= -4) {
            if (y >= -6 && speck(x, y, z, 4)) {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            return n > 0 ? log(Blocks.BASALT, Direction.Axis.Y) : Blocks.BLACKSTONE.defaultBlockState();
        }
        if (n > 0.45) {
            return Blocks.TUFF.defaultBlockState();
        }
        if (n < -0.45) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (n < -0.2) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        return Blocks.DEEPSLATE.defaultBlockState();
    }

    private static void cavern(B b) {
        BlockState lava = Blocks.LAVA.defaultBlockState();
        for (int x = CAVE_X0; x <= CAVE_X1; x++) {
            for (int z = CAVE_Z0; z <= CAVE_Z1; z++) {
                for (int y = CAVE_Y0; y <= CAVE_Y1; y++) {
                    double v = cave(x, y, z);
                    if (v >= 1.5) {
                        continue;
                    }
                    if (v >= 1 || y <= -7 || shore(v, y) || edge(x, y, z)) {
                        b.set(x, y, z, caveStone(x, y, z));
                    } else if (y == -6) {
                        b.set(x, y, z, lava);
                    }
                }
            }
        }
        // stalactites from the roof, glow lichen on the walls
        for (int x = CAVE_X0 + 1; x < CAVE_X1; x++) {
            for (int z = CAVE_Z0 + 1; z < CAVE_Z1; z++) {
                for (int y = -5; y < CAVE_Y1; y++) {
                    if (caveRock(x, y, z) || cave(x, y, z) >= 1) {
                        continue;
                    }
                    int h = hash(x, y, z, 11);
                    if (y >= 10 && caveRock(x, y + 1, z) && Math.floorMod(h, 12) == 0) {
                        int len = 1 + Math.floorMod(h >> 4, 4);
                        int room = 0;
                        while (room < len && !caveRock(x, y - room, z) && cave(x, y - room, z) < 1) {
                            room++;
                        }
                        for (int i = 0; i < room; i++) {
                            DripstoneThickness t = i == room - 1 ? DripstoneThickness.TIP : i == room - 2 ? DripstoneThickness.FRUSTUM
                                    : i == 0 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
                            b.set(x, y - i, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, t));
                        }
                        continue;
                    }
                    if (y >= -4 && Math.floorMod(h >> 8, 7) == 0) {
                        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
                            if (caveRock(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) {
                                b.set(x, y, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true));
                                break;
                            }
                        }
                    }
                }
            }
        }
        // a few basalt spires standing in the lava
        int[][] spires = {{-16, 20, 9}, {14, 26, 13}, {-34, 14, 6}, {34, 28, 7}, {3, 33, 11}, {-26, 31, 4}};
        for (int[] s : spires) {
            for (int y = -7; y <= -6 + s[2]; y++) {
                b.set(s[0], y, s[1], log(Blocks.BASALT, Direction.Axis.Y));
                if (y <= -6 + s[2] / 2) {
                    b.set(s[0] + 1, y, s[1], Blocks.BLACKSTONE);
                    b.set(s[0], y, s[1] + 1, Blocks.BLACKSTONE);
                }
            }
        }
        // lava falls down the walls into the lake
        for (int deg : new int[]{15, 55, 90, 125, 165, 210, 330}) {
            double a = Math.toRadians(deg);
            double fx = 0, fz = 9;
            int px = 0, pz = 9;
            for (int i = 0; i < 200; i++) {
                fx += Math.cos(a) * 0.5;
                fz += Math.sin(a) * 0.5;
                int ix = (int) Math.round(fx), iz = (int) Math.round(fz);
                if (cave(ix, 18, iz) >= 1 || caveRock(ix, 18, iz)) {
                    break;
                }
                px = ix;
                pz = iz;
            }
            int top = 18;
            while (top < CAVE_Y1 && cave(px, top + 1, pz) < 1 && !caveRock(px, top + 1, pz)) {
                top++;
            }
            b.set(px, top, pz, lava);
            for (int y = top - 1; y > -6; y--) {
                if (caveRock(px, y, pz) || cave(px, y, pz) >= 1) {
                    break;
                }
                b.set(px, y, pz, lava.setValue(LiquidBlock.LEVEL, 8));
            }
        }
        // the glow off the lake, carried up into the dark (invisible lights, well away from the walls)
        for (int x = -40; x <= 40; x += 8) {
            for (int z = -16; z <= 36; z += 8) {
                for (int y : new int[]{2, 11, 19}) {
                    if (cave(x, y, z) < 0.8 && !caveRock(x, y, z)) {
                        b.light(x, y, z, y < 10 ? 10 : 8);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ doors, rooms and the hole
    /** the doors the prison opens and shuts (lower halves, relative to CELL) */
    public enum Door { CELL, CELLS, WEST, EAST, GATE }

    private static List<BlockPos> doorsOf(Door d) {
        List<BlockPos> out = new ArrayList<>();
        switch (d) {
            case CELL -> out.add(new BlockPos(-2, 0, 1));
            case CELLS -> {
                for (int cx : new int[]{-6, 0, 6}) {
                    out.add(new BlockPos(cx - 2, 0, 1));
                    out.add(new BlockPos(cx - 2, 0, -5));
                }
            }
            case WEST -> out.add(new BlockPos(-12, 0, -2));
            case EAST -> out.add(new BlockPos(12, 0, -2));
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

    /** where someone is in the prison: "cell" (yours), "corridor", "labour", "meal", "ledge", "gate" or "" */
    public static String room(int slot, double x, double y, double z) {
        BlockPos c = origin(slot).offset(CELL);
        double rx = x - c.getX(), rz = z - c.getZ(), ry = y - c.getY();
        if (ry < -3 || ry > 6) {
            return "";
        }
        if (rz >= 6.9 && rz <= 9 && rx >= -1 && rx <= 16) {
            return "ledge";
        }
        if (rx >= 19.5 && rx <= 22.5 && rz <= -11.3 && rz >= -16.5) {
            return "gate";
        }
        if (rx >= -28 && rx < -13 && rz >= -10 && rz < 6) {
            return "labour";
        }
        if (rx >= 13.5 && rx < 29 && rz >= -10 && rz < 6) {
            return "meal";
        }
        if (rx >= -2 && rx < 3 && rz >= 1.5 && rz < 6) {
            return "cell";
        }
        if (rx >= -12 && rx < 13 && rz >= -4 && rz < 1) {
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
            // a ledge of basalt along the outside of the wall, east to the corner
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

    // ------------------------------------------------------------------ putting the people in their places
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
        for (Entity e : level.getEntitiesOfClass(Entity.class, area(o), e -> e.getTags().contains(NPC))) {
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
            put(e, o.getX() + n.x(), o.getY() + n.y(), o.getZ() + n.z(), yaw);
        }
    }

    private static AABB area(BlockPos o) {
        return new AABB(o.getX() - 50, o.getY() - 20, o.getZ() - 32, o.getX() + 50, o.getY() + 32, o.getZ() + 86);
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
        List<Entity> found = level.getEntitiesOfClass(Entity.class, area(origin(slot)), e -> e.getTags().contains(tag));
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

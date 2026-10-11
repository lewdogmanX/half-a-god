package dev.halfagod.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Ashford's empty lot looks like at each point in the story. Coordinates are relative to the lot: x -7..7, z -5..5,
 * y 0 is the first layer above the ground. Two plots: A (x -7..-1) and B (x 1..7), with a walk between them.
 *
 * Good road:  1 burned  -> 2 camp (tents, supplies) -> 3 rebuilding (frame, scaffolding)
 *             -> 4 rebuilt (plot B is a house again, A is being rebuilt) -> 5 thriving (two houses, the Watch's lanterns)
 * Evil road (from stage 3, while you're Fallen or Wicked): plot A stays a burned ruin, and the Gilded Hand puts up a
 *             counting house with a debtors' cage on plot B.
 */
public final class AshfordBuilder {
    public static final int X0 = -7, X1 = 7, Z0 = -5, Z1 = 5, HEIGHT = 7;

    private final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();

    private AshfordBuilder() {
    }

    public static Map<BlockPos, BlockState> plan(int stage, boolean evil) {
        AshfordBuilder b = new AshfordBuilder();
        if (stage <= 0) {
            return b.plan;
        }
        if (evil && stage >= 3) {
            b.ruin(-7, -1, false);
            b.countingHouse(stage >= 5);
        } else {
            switch (stage) {
                case 1 -> {
                    b.ruin(-7, -1, true);
                    b.ruin(1, 7, true);
                }
                case 2 -> {
                    b.ruin(-7, -1, false);
                    b.camp();
                }
                case 3 -> {
                    b.ruin(-7, -1, false);
                    b.frame(1, 7);
                    b.scaffold(0);
                    b.materials();
                }
                case 4 -> {
                    b.frame(-7, -1);
                    b.scaffold(0);
                    b.house(1, 7, false);
                }
                default -> {
                    b.house(-7, -1, true);
                    b.house(1, 7, false);
                    b.lanternRow();
                }
            }
        }
        b.connect();
        return b.plan;
    }

    // ------------------------------------------------------------------ helpers
    private void put(int x, int y, int z, BlockState s) {
        plan.put(new BlockPos(x, y, z), s);
    }

    private void put(int x, int y, int z, Block b) {
        put(x, y, z, b.defaultBlockState());
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    put(x, y, z, s);
                }
            }
        }
    }

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static boolean edge(int x, int z, int x1, int x2, int z1, int z2) {
        return x == x1 || x == x2 || z == z1 || z == z2;
    }

    private static int noise(int x, int z) {
        return Math.floorMod(x * 7 + z * 13 + x * z, 4);
    }

    // ------------------------------------------------------------------ pieces
    /** A burned-out house: charred wall stubs, standing posts, a fallen beam, ash on the floor. */
    private void ruin(int x1, int x2, boolean smoking) {
        int z1 = -3, z2 = 3;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int n = noise(x, z);
                if (edge(x, z, x1, x2, z1, z2)) {
                    int h = n == 3 ? 1 : n;
                    for (int y = 0; y < h; y++) {
                        put(x, y, z, switch (Math.floorMod(x + z + y, 3)) {
                            case 0 -> Blocks.COAL_BLOCK.defaultBlockState();
                            case 1 -> Blocks.BLACKSTONE.defaultBlockState();
                            default -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                        });
                    }
                } else if (n == 0) {
                    put(x, 0, z, Blocks.GRAY_CARPET);
                } else if (n == 2) {
                    put(x, 0, z, Blocks.BLACK_CARPET);
                }
            }
        }
        int[][] corners = {{x1, z1, 3}, {x2, z1, 1}, {x1, z2, 2}, {x2, z2, 0}};
        for (int[] c : corners) {
            for (int y = 0; y <= c[2]; y++) {
                put(c[0], y, c[1], log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
            }
        }
        int mid = (x1 + x2) / 2;
        for (int x = x1 + 1; x <= mid + 1; x++) {
            put(x, 0, 1, log(Blocks.DARK_OAK_LOG, Direction.Axis.X)); // the roof beam, where it fell
        }
        put(mid + 1, 0, -1, Blocks.BLACKSTONE_SLAB);
        put(mid - 1, 0, -2, Blocks.BLACKSTONE_SLAB);
        put(mid, 0, -1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, smoking));
    }

    /** Tents for the families, a cooking fire, the supplies you brought. */
    private void camp() {
        tent(1, -5, -3, Blocks.WHITE_WOOL.defaultBlockState());
        tent(1, 3, 5, Blocks.BROWN_WOOL.defaultBlockState());
        put(3, 0, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        for (int x = 2; x <= 4; x++) {
            put(x, 0, -2, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
            put(x, 0, 2, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
        }
        put(6, 0, -1, Blocks.BARREL);
        put(6, 0, 0, Blocks.HAY_BLOCK);
        put(6, 1, 0, Blocks.HAY_BLOCK);
        put(7, 0, 0, Blocks.HAY_BLOCK);
        put(6, 0, 1, Blocks.BARREL);
        put(7, 0, -1, Blocks.CRAFTING_TABLE);
    }

    /** An A-frame tent, five wide, running along z from z1 to z2. */
    private void tent(int x, int z1, int z2, BlockState wool) {
        for (int z = z1; z <= z2; z++) {
            put(x, 0, z, wool);
            put(x + 4, 0, z, wool);
            put(x + 1, 1, z, wool);
            put(x + 3, 1, z, wool);
            put(x + 2, 2, z, wool);
        }
    }

    /** A house going up: stone footing, planked floor, corner posts, the first course of wall. */
    private void frame(int x1, int x2) {
        int z1 = -3, z2 = 3;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                put(x, 0, z, edge(x, z, x1, x2, z1, z2) ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            box(c[0], 1, c[1], c[0], 3, c[1], log(Blocks.OAK_LOG, Direction.Axis.Y));
        }
        for (int x = x1 + 1; x < x2; x++) {
            put(x, 1, z1, Blocks.OAK_PLANKS);
        }
        for (int z = z1 + 1; z < z2; z++) {
            put(x1, 1, z, Blocks.OAK_PLANKS);
        }
        for (int x = x1; x <= x2; x++) {
            put(x, 4, z1, log(Blocks.OAK_LOG, Direction.Axis.X)); // the first roof beam
        }
    }

    private void scaffold(int x) {
        for (int y = 0; y <= 3; y++) {
            put(x, y, -2, Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false));
            put(x, y, 2, Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false));
        }
    }

    private void materials() {
        for (int x = 2; x <= 4; x++) {
            put(x, 0, -5, log(Blocks.OAK_LOG, Direction.Axis.X));
            put(x, 0, -4, log(Blocks.OAK_LOG, Direction.Axis.X));
        }
        put(3, 1, -5, log(Blocks.OAK_LOG, Direction.Axis.X));
        put(5, 0, -5, Blocks.STONE_BRICKS);
        put(6, 0, -5, Blocks.STONE_BRICKS);
        put(5, 1, -5, Blocks.STONE_BRICKS);
        put(7, 0, -4, Blocks.CRAFTING_TABLE);
    }

    /** A finished house with a door to the south; the Watch hall wears the Watch's banner. */
    private void house(int x1, int x2, boolean watchHall) {
        int z1 = -3, z2 = 3;
        int mid = (x1 + x2) / 2;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean e = edge(x, z, x1, x2, z1, z2);
                put(x, 0, z, e ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    if (!e) {
                        continue;
                    }
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    put(x, y, z, corner ? log(Blocks.OAK_LOG, Direction.Axis.Y) : Blocks.OAK_PLANKS.defaultBlockState());
                }
                put(x, 4, z, e ? log(Blocks.SPRUCE_LOG, x == x1 || x == x2 ? Direction.Axis.Z : Direction.Axis.X) : Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (!e) {
                    put(x, 5, z, Blocks.SPRUCE_SLAB);
                }
            }
        }
        // windows
        put(mid - 1, 2, z1, Blocks.GLASS_PANE);
        put(mid + 1, 2, z1, Blocks.GLASS_PANE);
        put(x1, 2, -1, Blocks.GLASS_PANE);
        put(x1, 2, 1, Blocks.GLASS_PANE);
        put(x2, 2, -1, Blocks.GLASS_PANE);
        put(x2, 2, 1, Blocks.GLASS_PANE);
        // door, step and lamps
        put(mid, 1, z2, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(mid, 2, z2, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        put(mid, 0, z2 + 1, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        put(mid - 2, 0, z2 + 1, Blocks.OAK_FENCE);
        put(mid - 2, 1, z2 + 1, Blocks.LANTERN);
        put(mid + 2, 0, z2 + 1, Blocks.OAK_FENCE);
        put(mid + 2, 1, z2 + 1, Blocks.LANTERN);
        // inside
        put(x1 + 1, 1, z1 + 1, Blocks.CRAFTING_TABLE);
        put(x2 - 1, 1, z1 + 1, Blocks.BARREL);
        put(x2 - 1, 1, z2 - 1, Blocks.LANTERN);
        put(x1 + 1, 1, z2 - 1, Blocks.POTTED_POPPY);
        if (watchHall) {
            put(mid, 3, z2 + 1, Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
        }
    }

    /** The Watch's lanterns down the walk, and flowers by the doors. */
    private void lanternRow() {
        for (int x : new int[]{-6, -2, 2, 6}) {
            if (plan.containsKey(new BlockPos(x, 0, 5))) {
                continue;
            }
            put(x, 0, 5, Blocks.OAK_FENCE);
            put(x, 1, 5, Blocks.LANTERN);
        }
        put(0, 0, -4, Blocks.HAY_BLOCK);
        put(0, 0, 4, Blocks.BARREL);
    }

    /** The Gilded Hand's counting house: a dark stall, a golden counter, a cage for people who can't pay. */
    private void countingHouse(boolean grown) {
        int x1 = 1, x2 = 7, z1 = -3, z2 = 3;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                put(x, 0, z, Blocks.DARK_OAK_PLANKS);
                put(x, 4, z, Blocks.DARK_OAK_SLAB);
            }
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            box(c[0], 1, c[1], c[0], 3, c[1], log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        }
        for (int x = 2; x <= 6; x++) {
            put(x, 1, 1, x == 2 || x == 6 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
        }
        put(4, 1, -1, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH));
        for (int x : new int[]{2, 6}) {
            put(x, 3, -2, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            put(x, 2, -2, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        put(1, 0, 4, Blocks.YELLOW_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 0));
        put(7, 0, 4, Blocks.YELLOW_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 0));
        cage(3, -5);
        if (grown) {
            cage(-6, -5);
            for (int x : new int[]{-6, -3}) {
                put(x, 0, 5, Blocks.BLACKSTONE_WALL);
                put(x, 1, 5, Blocks.SOUL_LANTERN);
            }
            put(4, 0, 4, Blocks.BLACK_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 0));
        }
    }

    /** Iron bars, three by three, with a slab lid. */
    private void cage(int x, int z) {
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                if (dx == 1 && dz == 1) {
                    continue;
                }
                put(x + dx, 0, z + dz, Blocks.IRON_BARS);
                put(x + dx, 1, z + dz, Blocks.IRON_BARS);
            }
        }
        box(x, 2, z, x + 2, 2, z + 2, Blocks.DARK_OAK_SLAB.defaultBlockState());
    }

    /** Panes, bars and fences join up with what's beside them (blocks are placed without neighbour updates). */
    private void connect() {
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockState s = e.getValue();
            if (!(s.getBlock() instanceof CrossCollisionBlock)) {
                continue;
            }
            BlockPos p = e.getKey();
            s = s.setValue(CrossCollisionBlock.NORTH, joins(s, plan.get(p.north())))
                    .setValue(CrossCollisionBlock.SOUTH, joins(s, plan.get(p.south())))
                    .setValue(CrossCollisionBlock.EAST, joins(s, plan.get(p.east())))
                    .setValue(CrossCollisionBlock.WEST, joins(s, plan.get(p.west())));
            e.setValue(s);
        }
    }

    private static boolean joins(BlockState self, BlockState other) {
        if (other == null) {
            return false;
        }
        if (other.getBlock() == self.getBlock()) {
            return true;
        }
        Block b = other.getBlock();
        return b == Blocks.OAK_PLANKS || b == Blocks.COBBLESTONE || b == Blocks.DARK_OAK_PLANKS || b == Blocks.GOLD_BLOCK
                || b instanceof RotatedPillarBlock;
    }
}

package dev.halfagod.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The world answering back.
 * - People you saved move into the story's villages (home, Ashford, the snowy village) and have something to say.
 * - Ashford is rebuilt on an empty lot as the story goes on, or, if you've gone bad, the Gilded Hand moves in.
 * - Each village you know has a notice board by its bell: a wanted poster if you're Fallen or Wicked, a notice for
 *   Watch recruits if you're Wavering, thanks if you're Good or a Saint.
 * Nothing is spawned or built far from the player; everything waits until you're close enough to see it happen.
 * Blocks are only ever placed into air or plants, and only blocks this mod placed are ever removed.
 */
public final class Settlement {
    public static final String SETTLER = "hag_settler";
    private static final String SETTLER_KEY = "hag_settler:";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<UUID, Integer> LINE = new HashMap<>();

    public record Settler(String key, String name, String place, String trait, String profession, String when, List<String> lines) {
    }

    private Settlement() {
    }

    /** every 100 ticks */
    public static void tick(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        Set<String> traits = Meb.traits(player);
        try {
            settlers(player, traits);
        } catch (Exception e) {
            dev.halfagod.HalfAGod.LOG.warn("Half a God: settlers: {}", e.toString());
        }
        try {
            ashford(player, traits);
        } catch (Exception e) {
            dev.halfagod.HalfAGod.LOG.warn("Half a God: Ashford: {}", e.toString());
        }
        try {
            boards(player);
        } catch (Exception e) {
            dev.halfagod.HalfAGod.LOG.warn("Half a God: notice boards: {}", e.toString());
        }
    }

    private static boolean near(ServerPlayer player, CastData.Place place, double dist) {
        return place != null && player.serverLevel().dimension().location().equals(place.dimension())
                && player.blockPosition().distSqr(place.pos()) < dist * dist;
    }

    // ------------------------------------------------------------------ people who move in
    private static boolean sideOk(String when, int tier) {
        return when.isEmpty() || (when.equals("good") && tier >= 3) || (when.equals("evil") && tier <= 1);
    }

    private static void settlers(ServerPlayer player, Set<String> traits) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        SettlementData data = SettlementData.get(player.getServer());
        CompoundTag moved = data.section(id, "settlers");
        Map<String, CastData.Place> places = CastData.get(player.getServer()).placesOf(id);
        int tier = Morality.tier(player);
        for (Settler s : StoryData.INSTANCE.settlers) {
            boolean want = traits.contains(s.trait()) && sideOk(s.when(), tier);
            if (!want) {
                // someone who only stays while you're on their side leaves when you cross over
                if (moved.contains(s.key()) && traits.contains(s.trait())) {
                    UUID who = moved.getCompound(s.key()).getUUID("uuid");
                    if (level.getEntity(who) instanceof Villager v) {
                        v.discard();
                        moved.remove(s.key());
                        data.setDirty();
                    }
                }
                continue;
            }
            if (moved.contains(s.key())) {
                continue; // already living there (or lived there once)
            }
            CastData.Place place = places.get(s.place());
            if (!near(player, place, 112) || !level.isLoaded(place.pos())) {
                continue;
            }
            BlockPos spot = Cast.standingSpot(level, place.pos(), s.name());
            Villager v = EntityType.VILLAGER.create(level);
            if (v == null) {
                continue;
            }
            v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
            VillagerProfession prof = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(s.profession()));
            v.setVillagerData(v.getVillagerData().setType(VillagerType.byBiome(level.getBiome(spot)))
                    .setProfession(prof == null ? VillagerProfession.NITWIT : prof).setLevel(2));
            v.setVillagerXp(10);
            v.setOffers(new MerchantOffers());
            v.setCustomName(Component.literal(s.name()));
            v.setPersistenceRequired();
            v.addTag(SETTLER);
            v.addTag(SETTLER_KEY + id + "|" + s.key());
            v.addTag("mebahelrpgrquest:golem_protected");
            level.addFreshEntity(v);
            Meb.neverOffers(v);
            CompoundTag rec = new CompoundTag();
            rec.putUUID("uuid", v.getUUID());
            moved.put(s.key(), rec);
            data.setDirty();
            player.displayClientMessage(Component.literal(s.name() + " has made a home here.").withStyle(ChatFormatting.GOLD), true);
        }
        // the people who moved in talk instead of trading
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(64), v -> v.getTags().contains(SETTLER))) {
            if (!v.getOffers().isEmpty()) {
                v.setOffers(new MerchantOffers());
            }
        }
    }

    private static Settler settlerOf(Villager v) {
        for (String tag : v.getTags()) {
            if (tag.startsWith(SETTLER_KEY)) {
                String key = tag.substring(tag.lastIndexOf('|') + 1);
                for (Settler s : StoryData.INSTANCE.settlers) {
                    if (s.key().equals(key)) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager v) || !v.getTags().contains(SETTLER)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        Settler s = settlerOf(v);
        if (s == null || s.lines().isEmpty()) {
            return;
        }
        int i = LINE.merge(v.getUUID(), 1, Integer::sum) - 1;
        String line = s.lines().get(Math.floorMod(i, s.lines().size()));
        v.getLookControl().setLookAt(player);
        v.level().playSound(null, v.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
        player.displayClientMessage(Component.literal(s.name() + ": ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)), true);
    }

    // ------------------------------------------------------------------ Ashford
    private static void ashford(ServerPlayer player, Set<String> traits) {
        UUID id = player.getUUID();
        CastData.Place place = CastData.get(player.getServer()).place(id, "ashford");
        if (!near(player, place, 150)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int stage = 1;
        for (Map.Entry<String, Integer> e : StoryData.INSTANCE.ashfordStages.entrySet()) {
            if (traits.contains(e.getKey())) {
                stage = Math.max(stage, e.getValue());
            }
        }
        boolean evil = stage >= 3 && Morality.tier(player) <= 1;
        String want = stage + (evil ? "e" : "g");
        SettlementData data = SettlementData.get(player.getServer());
        CompoundTag a = data.section(id, "ashford");
        a.putInt("stage", stage);
        // multiplayer: one Ashford per village. Whoever is furthest along decides what it looks like; the others share it.
        CompoundTag shared = null;
        for (UUID other : data.playerIds()) {
            if (other.equals(id)) {
                continue;
            }
            CompoundTag o = data.player(other).getCompound("ashford");
            CastData.Place op = CastData.get(player.getServer()).place(other, "ashford");
            if (!o.contains("anchor") || op == null || !op.dimension().equals(place.dimension())
                    || BlockPos.of(o.getLong("anchor")).distSqr(place.pos()) > 200 * 200
                    || (a.contains("anchor") && a.getLong("anchor") != o.getLong("anchor"))) {
                continue; // a different village, or (from before) a lot of your own: leave both alone
            }
            int theirs = o.contains("stage") ? o.getInt("stage") : o.getString("built").isEmpty() ? 0 : o.getString("built").charAt(0) - '0';
            boolean theyBuilt = !o.getString("built").isEmpty();
            if (theirs > stage || (theirs == stage && theyBuilt && a.getString("built").isEmpty())) {
                a.putString("follows", other.toString());
                data.setDirty();
                return; // they're further along: their Ashford stands
            }
            shared = o;
        }
        a.remove("follows");
        if (shared != null && shared.contains("anchor")) {
            // take over the shared lot: build on their anchor, and look after the blocks they put there
            if (!a.contains("anchor") || a.getLong("anchor") != shared.getLong("anchor")) {
                a.putLong("anchor", shared.getLong("anchor"));
                a.putBoolean("levelled", true);
                a.remove("built");
            }
            CompoundTag mine = a.getCompound("placed");
            CompoundTag theirs = shared.getCompound("placed");
            for (String k : theirs.getAllKeys()) {
                if (!mine.contains(k)) {
                    mine.putString(k, theirs.getString(k));
                }
            }
            a.put("placed", mine);
            shared.put("placed", new CompoundTag());
            shared.putString("built", "");
            data.setDirty();
        }
        if (want.equals(a.getString("built"))) {
            return;
        }
        BlockPos anchor;
        if (a.contains("anchor")) {
            anchor = BlockPos.of(a.getLong("anchor"));
        } else {
            if (level.getGameTime() < a.getLong("retry")) {
                return;
            }
            anchor = findLot(level, place.pos());
            if (anchor == null) {
                a.putLong("retry", level.getGameTime() + 2400);
                data.setDirty();
                return;
            }
            a.putLong("anchor", anchor.asLong());
            data.setDirty();
        }
        if (!loaded(level, anchor)) {
            return;
        }
        build(level, anchor, a, AshfordBuilder.plan(stage, evil));
        a.putString("built", want);
        data.setDirty();
    }

    private static boolean loaded(ServerLevel level, BlockPos anchor) {
        return level.isLoaded(anchor.offset(AshfordBuilder.X0 - 1, 0, AshfordBuilder.Z0 - 1))
                && level.isLoaded(anchor.offset(AshfordBuilder.X1 + 1, 0, AshfordBuilder.Z0 - 1))
                && level.isLoaded(anchor.offset(AshfordBuilder.X0 - 1, 0, AshfordBuilder.Z1 + 1))
                && level.isLoaded(anchor.offset(AshfordBuilder.X1 + 1, 0, AshfordBuilder.Z1 + 1));
    }

    private static boolean free(BlockState s) {
        return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK);
    }

    /** An empty, fairly flat patch of natural ground near Ashford's middle, clear of houses, paths and fields. */
    private static BlockPos findLot(ServerLevel level, BlockPos centre) {
        for (int r = 14; r <= 60; r += 4) {
            for (int i = 0; i < 16; i++) {
                double ang = i * Math.PI / 8 + r * 0.37;
                int cx = centre.getX() + (int) Math.round(Math.cos(ang) * r);
                int cz = centre.getZ() + (int) Math.round(Math.sin(ang) * r);
                BlockPos lot = checkLot(level, cx, cz);
                if (lot != null) {
                    return lot;
                }
            }
        }
        return null;
    }

    private static BlockPos checkLot(ServerLevel level, int cx, int cz) {
        BlockPos probe = new BlockPos(cx, 64, cz);
        if (!loaded(level, probe)) {
            return null;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = AshfordBuilder.X0 - 1; x <= AshfordBuilder.X1 + 1; x++) {
            for (int z = AshfordBuilder.Z0 - 1; z <= AshfordBuilder.Z1 + 1; z++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + x, cz + z);
                if (!natural(level.getBlockState(new BlockPos(cx + x, h - 1, cz + z)))) {
                    return null;
                }
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        if (max - min > 2) {
            return null;
        }
        for (int x = AshfordBuilder.X0 - 1; x <= AshfordBuilder.X1 + 1; x++) {
            for (int z = AshfordBuilder.Z0 - 1; z <= AshfordBuilder.Z1 + 1; z++) {
                for (int y = max; y <= max + AshfordBuilder.HEIGHT; y++) {
                    if (!free(level.getBlockState(new BlockPos(cx + x, y, cz + z)))) {
                        return null;
                    }
                }
            }
        }
        return new BlockPos(cx, max, cz);
    }

    private static String idOf(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    private static void build(ServerLevel level, BlockPos anchor, CompoundTag a, Map<BlockPos, BlockState> plan) {
        CompoundTag placed = a.getCompound("placed");
        // the ground under the lot is levelled once, with packed earth
        if (!a.getBoolean("levelled")) {
            for (int x = AshfordBuilder.X0; x <= AshfordBuilder.X1; x++) {
                for (int z = AshfordBuilder.Z0; z <= AshfordBuilder.Z1; z++) {
                    for (int y = -1; y >= -4; y--) {
                        BlockPos p = anchor.offset(x, y, z);
                        if (!free(level.getBlockState(p))) {
                            break;
                        }
                        level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), FLAGS);
                    }
                }
            }
            a.putBoolean("levelled", true);
        }
        // take down what this stage no longer has (only if it's still the block we put there)
        for (String key : new ArrayList<>(placed.getAllKeys())) {
            BlockPos rel = BlockPos.of(Long.parseLong(key));
            BlockState want = plan.get(rel);
            if (want != null && idOf(want).equals(placed.getString(key))) {
                continue;
            }
            BlockPos abs = anchor.offset(rel);
            BlockState cur = level.getBlockState(abs);
            if (idOf(cur).equals(placed.getString(key))) {
                level.setBlock(abs, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            placed.remove(key);
        }
        // put up what it does
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockPos abs = anchor.offset(e.getKey());
            String key = Long.toString(e.getKey().asLong());
            BlockState cur = level.getBlockState(abs);
            if (cur.equals(e.getValue())) {
                placed.putString(key, idOf(cur));
                continue;
            }
            boolean ours = placed.contains(key) && idOf(cur).equals(placed.getString(key));
            if (!ours && !free(cur)) {
                continue; // something the player built: leave it alone
            }
            if (cur.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !ours) {
                BlockPos other = cur.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? abs.above() : abs.below();
                if (level.getBlockState(other).is(cur.getBlock())) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            level.setBlock(abs, e.getValue(), FLAGS);
            placed.putString(key, idOf(e.getValue()));
        }
        a.put("placed", placed);
    }

    // ------------------------------------------------------------------ notice boards
    private static void boards(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        SettlementData data = SettlementData.get(player.getServer());
        CompoundTag boards = data.section(id, "boards");
        int tier = Morality.tier(player);
        List<BlockPos> seen = new ArrayList<>();
        for (Map.Entry<String, CastData.Place> e : CastData.get(player.getServer()).placesOf(id).entrySet()) {
            CastData.Place place = e.getValue();
            if (!near(player, place, 96)) {
                continue;
            }
            boolean dup = false;
            for (BlockPos p : seen) {
                if (p.closerThan(place.pos(), 64)) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                continue;
            }
            seen.add(place.pos());
            String key = Long.toString(place.pos().asLong());
            CompoundTag b = boards.getCompound(key);
            if (!b.contains("pos")) {
                if (!level.isLoaded(place.pos())) {
                    continue;
                }
                BlockPos spot = boardSpot(level, place.pos());
                if (spot == null) {
                    continue;
                }
                b.putLong("pos", spot.asLong());
                b.putInt("tier", -1);
            }
            BlockPos post = BlockPos.of(b.getLong("pos"));
            if (!level.isLoaded(post) || b.getInt("tier") == tier) {
                boards.put(key, b);
                continue;
            }
            // your standing changed: the village puts up a new notice (a torn-down one is replaced too)
            BlockState sign = level.getBlockState(post.above());
            if (!sign.is(Blocks.OAK_SIGN)) {
                if (!free(level.getBlockState(post.above()))) {
                    boards.put(key, b);
                    continue;
                }
                if (free(level.getBlockState(post))) {
                    level.setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), 3);
                }
                if (!level.getBlockState(post).is(Blocks.OAK_FENCE)) {
                    boards.put(key, b);
                    continue;
                }
                level.setBlock(post.above(), Blocks.OAK_SIGN.defaultBlockState()
                        .setValue(BlockStateProperties.ROTATION_16, Math.floorMod(post.hashCode(), 16)), 3);
            }
            if (level.getBlockEntity(post.above()) instanceof SignBlockEntity s) {
                SignText text = notice(tier, player.getGameProfile().getName());
                s.setText(text, true);
                s.setText(text, false);
                s.setWaxed(true);
                s.setChanged();
                BlockState st = level.getBlockState(post.above());
                level.sendBlockUpdated(post.above(), st, st, 3);
            }
            b.putInt("tier", tier);
            boards.put(key, b);
            data.setDirty();
        }
        data.setDirty();
    }

    private static SignText notice(int tier, String name) {
        String[] lines;
        DyeColor color;
        boolean glow;
        switch (tier) {
            case 0 -> {
                lines = new String[]{"WANTED", name, "dead or alive", "400 emeralds"};
                color = DyeColor.RED;
                glow = true;
            }
            case 1 -> {
                lines = new String[]{"WANTED", name, "for cruelty", "150 emeralds"};
                color = DyeColor.RED;
                glow = false;
            }
            case 2 -> {
                lines = new String[]{"NOTICE", "The Watch needs", "brave hands.", "Ask at the bell."};
                color = DyeColor.BLACK;
                glow = false;
            }
            case 3 -> {
                lines = new String[]{"OUR THANKS TO", name, "who stood", "for us."};
                color = DyeColor.BLUE;
                glow = false;
            }
            default -> {
                lines = new String[]{"BLESSED BE", name, "friend of every", "village."};
                color = DyeColor.YELLOW;
                glow = true;
            }
        }
        SignText t = new SignText();
        for (int i = 0; i < 4; i++) {
            Component c = Component.literal(lines[i]);
            if (i == 0) {
                c = Component.literal(lines[i]).withStyle(ChatFormatting.BOLD);
            }
            t = t.setMessage(i, c);
        }
        return t.setColor(color).setHasGlowingText(glow);
    }

    /** Beside the village bell if there is one (otherwise near the village middle): open ground with room for a post and a sign. */
    private static BlockPos boardSpot(ServerLevel level, BlockPos centre) {
        BlockPos bell = null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        search:
        for (int dx = -40; dx <= 40; dx++) {
            for (int dz = -40; dz <= 40; dz++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + dx, centre.getZ() + dz);
                for (int y = top - 7; y <= top; y++) {
                    m.set(centre.getX() + dx, y, centre.getZ() + dz);
                    if (level.getBlockState(m).is(Blocks.BELL)) {
                        bell = m.immutable();
                        break search;
                    }
                }
            }
        }
        BlockPos around = bell != null ? bell : centre;
        for (int r = 2; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = around.getX() + dx, z = around.getZ() + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos p = new BlockPos(x, y, z);
                    if (Math.abs(y - around.getY()) <= 3 && Cast.isGround(level.getBlockState(p.below()))
                            && free(level.getBlockState(p)) && free(level.getBlockState(p.above()))) {
                        return p;
                    }
                }
            }
        }
        return Cast.standingSpot(level, centre, "notice board");
    }
}

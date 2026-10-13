package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A day in the Watch's prison (a whole Minecraft day, twenty real minutes, counted only while you're in it):
 *
 *   0:00  your cell (morning; a visitor halfway through your sentence)
 *   5:00  the bell: doors open, walk to the quarry (a minute, then the guards drag you)
 *   6:00  hard labour: break your quota of stone, or no supper (three days short, a day more)
 *  11:00  the bell: walk to the cafeteria
 *  12:00  supper, and the other prisoners to talk to
 *  16:00  the bell: back to your cell
 *  17:00  lights out: the gaoler walks his rounds, and anyone with a chisel and a death wish digs
 *
 * Sentence served, your cell door and the gate open and you walk out. Old Maddock's way out: his plan, his trust (your
 * bread), a chisel from the quarry cart when the guard isn't looking, then nights of digging under your window between
 * the gaoler's rounds. Caught at any step: two more days and the tool and the hole are gone.
 */
public final class Prison {
    public static final int LABOUR_BELL = 6000, LABOUR_CLOSE = 7200, MEAL_BELL = 13200, MEAL_CLOSE = 14400,
            NIGHT_BELL = 19200, NIGHT_CLOSE = 20400;
    private static final int ROUND_TICKS = 180;

    /** per player, not saved: the visitor today, the gaoler's next round, the last dig, walking out since, last nag */
    private static final Map<UUID, String> VISITOR = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_ROUND = new HashMap<>();
    private static final Map<UUID, Integer> ROUND_AT = new HashMap<>();
    private static final Map<UUID, Long> LAST_DIG = new HashMap<>();
    private static final Map<UUID, Long> WALK_SINCE = new HashMap<>();
    private static final Map<UUID, Long> NAG = new HashMap<>();
    private static final Map<Integer, Boolean> GUARD = new HashMap<>();

    private Prison() {
    }

    private static JsonObject t() {
        return StoryData.INSTANCE.table("prison");
    }

    private static String text(String section, String key) {
        JsonObject o = t().has(section) ? t().getAsJsonObject(section) : new JsonObject();
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static int quota() {
        JsonObject l = t().has("labour") ? t().getAsJsonObject("labour") : new JsonObject();
        return l.has("quota") ? l.get("quota").getAsInt() : 48;
    }

    public static int digsNeeded() {
        return t().has("escape_digs") ? t().get("escape_digs").getAsInt() : 500;
    }

    // ------------------------------------------------------------------ the clock
    /** the part of the day: the cells (morning or night), labour (from its bell), supper (from its bell) */
    public static int phase(RepData d) {
        if (d.trialState != 2 || d.walkingOut) {
            return CourtBuilder.PHASE_CELL;
        }
        int t = d.servedTicks;
        return t >= LABOUR_BELL && t < MEAL_BELL ? CourtBuilder.PHASE_LABOUR : t >= MEAL_BELL && t < NIGHT_BELL ? CourtBuilder.PHASE_MEAL : CourtBuilder.PHASE_CELL;
    }

    public static boolean lightsOut(RepData d) {
        return d.trialState == 2 && !d.walkingOut && d.servedTicks >= NIGHT_CLOSE;
    }

    public static String phaseName(RepData d) {
        if (d.walkingOut) {
            return "walking out";
        }
        int t = d.servedTicks;
        return t < LABOUR_BELL ? "morning" : t < MEAL_BELL ? "the quarry" : t < NIGHT_BELL ? "supper" : "lights out";
    }

    /** Time spent in the prison (only while you're in it, and it can't be slept away). Bells, doors, meals, the day. */
    public static void passTime(ServerPlayer player, int ticks) {
        RepData d = Reputation.data(player);
        while (ticks > 0 && d.trialState == 2 && !d.walkingOut) {
            int step = Math.min(ticks, 20);
            ticks -= step;
            int before = d.servedTicks;
            int after = before + step;
            d.servedTicks = after;
            for (int at : new int[]{LABOUR_BELL, LABOUR_CLOSE, MEAL_BELL, MEAL_CLOSE, NIGHT_BELL, NIGHT_CLOSE}) {
                if (before < at && after >= at) {
                    event(player, d, at);
                }
            }
            if (d.trialState == 2 && !d.walkingOut && d.servedTicks >= Trial.DAY) {
                dawn(player, d);
            } else if (before / 1200 != after / 1200) {
                player.displayClientMessage(Component.literal("Day " + (d.served + 1) + " of " + d.sentence + ", " + phaseName(d) + ". "
                        + Trial.minutesLeft(d) + " min to go.").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    private static ServerLevel level(ServerPlayer player) {
        return Trial.court(player.getServer());
    }

    private static int slot(ServerPlayer player) {
        ServerLevel court = level(player);
        return court == null ? 0 : CourtData.get(court).slot(player.getUUID());
    }

    private static void bell(ServerPlayer player) {
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2f, 0.7f);
    }

    private static void event(ServerPlayer player, RepData d, int at) {
        ServerLevel court = level(player);
        if (court == null) {
            return;
        }
        int slot = slot(player);
        switch (at) {
            case LABOUR_BELL -> {
                bell(player);
                VISITOR.remove(player.getUUID());
                CourtBuilder.doors(court, slot, CourtBuilder.Door.CELLS, true);
                CourtBuilder.doors(court, slot, CourtBuilder.Door.WEST, true);
                say(player, "The bell. Cell doors open: to the quarry.", ChatFormatting.YELLOW);
            }
            case LABOUR_CLOSE -> {
                closeAll(court, slot);
                drag(player, "labour");
                player.getInventory().add(new ItemStack(Items.WOODEN_PICKAXE));
                say(player, text("labour", "start"), ChatFormatting.GRAY);
            }
            case MEAL_BELL -> {
                bell(player);
                takeTools(player);
                if (d.labour >= quota()) {
                    d.fed = true;
                    d.strikes = 0;
                } else {
                    d.fed = false;
                    d.strikes++;
                    if (d.strikes >= 3) {
                        d.strikes = 0;
                        d.sentence++;
                        say(player, text("labour", "strike"), ChatFormatting.RED);
                    }
                }
                d.labour = 0;
                CourtBuilder.doors(court, slot, CourtBuilder.Door.WEST, true);
                CourtBuilder.doors(court, slot, CourtBuilder.Door.EAST, true);
                if (!d.fed) {
                    say(player, text("labour", "short"), ChatFormatting.RED);
                } else {
                    say(player, "The bell. To the cafeteria.", ChatFormatting.YELLOW);
                }
            }
            case MEAL_CLOSE -> {
                closeAll(court, slot);
                drag(player, "meal");
                if (d.fed) {
                    player.getInventory().add(new ItemStack(Items.BREAD, 3));
                    say(player, text("meal", "fed"), ChatFormatting.GRAY);
                } else {
                    say(player, text("meal", "unfed"), ChatFormatting.GRAY);
                }
            }
            case NIGHT_BELL -> {
                bell(player);
                CourtBuilder.doors(court, slot, CourtBuilder.Door.EAST, true);
                CourtBuilder.doors(court, slot, CourtBuilder.Door.CELLS, true);
                say(player, "The bell. Back to your cell.", ChatFormatting.YELLOW);
            }
            case NIGHT_CLOSE -> {
                closeAll(court, slot);
                drag(player, "cell");
                NEXT_ROUND.put(player.getUUID(), NIGHT_CLOSE + 200 + player.getRandom().nextInt(400));
                say(player, text("gaoler", "night"), ChatFormatting.GRAY);
            }
            default -> {
            }
        }
    }

    private static void closeAll(ServerLevel court, int slot) {
        for (CourtBuilder.Door door : CourtBuilder.Door.values()) {
            CourtBuilder.doors(court, slot, door, false);
        }
    }

    private static void say(ServerPlayer player, String s, ChatFormatting colour) {
        if (!s.isEmpty()) {
            player.displayClientMessage(Component.literal(s).withStyle(colour), true);
        }
    }

    /** the guards make sure you're where you should be */
    private static void drag(ServerPlayer player, String room) {
        ServerLevel court = level(player);
        int slot = slot(player);
        if (court == null || room.equals(CourtBuilder.room(slot, player.getX(), player.getY(), player.getZ()))) {
            return;
        }
        putIn(player, room);
        say(player, "A guard takes you by the collar and drags you along.", ChatFormatting.RED);
    }

    /** puts the player in a room of the prison (their cell, the quarry, the cafeteria) */
    public static void putIn(ServerPlayer player, String room) {
        ServerLevel court = level(player);
        if (court == null) {
            return;
        }
        int slot = slot(player);
        BlockPos c = CourtBuilder.origin(slot).offset(CourtBuilder.CELL);
        double[] at = switch (room) {
            case "labour" -> CourtBuilder.LABOUR_ENTRY;
            case "meal" -> CourtBuilder.MEAL_ENTRY;
            default -> new double[]{CourtBuilder.CELL_SPOT.getX() + 0.5, 0, CourtBuilder.CELL_SPOT.getZ() - CourtBuilder.CELL.getZ() + 0.5, 180};
        };
        player.teleportTo(court, c.getX() + at[0], c.getY() + at[1], c.getZ() + at[2], (float) at[3], 0f);
    }

    private static void takeTools(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WOODEN_PICKAXE)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** the end of a day: the hole found open at dawn, the day served, then the morning (searches, a visitor) */
    private static void dawn(ServerPlayer player, RepData d) {
        if (d.digProgress >= digsNeeded()) {
            caught(player, d, "dawn");
        }
        Trial.serveDay(player);
        if (d.trialState != 2 || d.walkingOut) {
            return;
        }
        d.labour = 0;
        // morning count: the guards turn some cells over
        if (d.hasChisel && player.getRandom().nextFloat() < 0.2f) {
            caught(player, d, "found_tool");
        } else if (d.digProgress > 0 && player.getRandom().nextFloat() < 0.1f) {
            caught(player, d, "found_hole");
        }
        // halfway through a sentence, someone comes to see you
        if (!d.visitorDone && d.sentence >= 2 && d.served == d.sentence / 2) {
            d.visitorDone = true;
            String v = visitorFor(player, d);
            VISITOR.put(player.getUUID(), v);
            say(player, "Someone is at your bars: " + visitorName(v) + ". (Use the bars to talk.)", ChatFormatting.YELLOW);
        }
    }

    /** the start of a sentence (called when the Watch takes you down to the cells) */
    public static void start(ServerPlayer player, RepData d) {
        d.labour = 0;
        d.fed = false;
        d.strikes = 0;
        d.visitorDone = false;
        d.hasChisel = false;
        d.digProgress = 0;
        d.walkingOut = false;
        ServerLevel court = level(player);
        if (court != null) {
            closeAll(court, slot(player));
            CourtBuilder.hole(court, slot(player), false);
        }
        clear(player.getUUID());
    }

    public static void clear(UUID id) {
        VISITOR.remove(id);
        NEXT_ROUND.remove(id);
        ROUND_AT.remove(id);
        LAST_DIG.remove(id);
        WALK_SINCE.remove(id);
    }

    /** when you go home, the prison's things stay in the prison */
    public static void leave(ServerPlayer player, RepData d) {
        takeTools(player);
        ServerLevel court = level(player);
        if (court != null) {
            closeAll(court, slot(player));
            CourtBuilder.hole(court, slot(player), false);
        }
        d.walkingOut = false;
        d.hasChisel = false;
        d.digProgress = 0;
        clear(player.getUUID());
    }

    // ------------------------------------------------------------------ walking out
    /** the sentence is served: the doors open, and you walk out through the gate */
    public static void walkOut(ServerPlayer player, RepData d) {
        d.walkingOut = true;
        ServerLevel court = level(player);
        if (court == null) {
            Trial.release(player, d, "served");
            return;
        }
        int slot = slot(player);
        CourtBuilder.doors(court, slot, CourtBuilder.Door.CELL, true);
        CourtBuilder.doors(court, slot, CourtBuilder.Door.EAST, true);
        CourtBuilder.doors(court, slot, CourtBuilder.Door.GATE, true);
        WALK_SINCE.put(player.getUUID(), player.getServer().getTickCount() + 0L);
        player.displayClientMessage(Component.literal("Your sentence is served. Your door is open: walk out through the gate, past the cafeteria.")
                .withStyle(ChatFormatting.GREEN), true);
    }

    // ------------------------------------------------------------------ every tick, in the prison
    public static void tick(ServerPlayer player, RepData d, ServerLevel court, int slot, long now) {
        UUID id = player.getUUID();
        String room = CourtBuilder.room(slot, player.getX(), player.getY(), player.getZ());
        if (d.walkingOut) {
            Long since = WALK_SINCE.computeIfAbsent(id, k -> now);
            if (room.equals("gate") || now - since > 20 * 180) {
                Trial.release(player, d, "served");
            } else if ((now - since) % 200 == 40) {
                say(player, text("gaoler", "out"), ChatFormatting.GRAY);
            }
            return;
        }
        // out of the hole and along the ledge: gone
        if (room.equals("ledge")) {
            BlockPos c = CourtBuilder.origin(slot).offset(CourtBuilder.CELL);
            if (player.getX() - c.getX() >= 10) {
                d.escapes++;
                Trial.release(player, d, "escaped");
                return;
            }
        }
        // the quarry guard's head turns now and then
        if (phase(d) == CourtBuilder.PHASE_LABOUR) {
            boolean w = guardWatching(slot, now);
            if (GUARD.getOrDefault(slot, !w) != w) {
                GUARD.put(slot, w);
                CourtBuilder.guardLooks(court, slot, w);
            }
        }
        // the gaoler's rounds, after lights out
        if (lightsOut(d)) {
            rounds(player, d, court, slot);
        }
        // the prison's noises
        sounds(player, d, court, slot, room);
        if (now % 20 == 0) {
            passTime(player, 20);
        }
    }

    /** the quarry guard watches the cart most of the time; now and then (a few seconds) he looks at the stone instead */
    public static boolean guardWatching(int slot, long now) {
        long k = now / 120;
        int h = (int) (k * 2654435761L ^ (slot * 40503L));
        return Math.floorMod(h, 10) < 7;
    }

    private static void rounds(ServerPlayer player, RepData d, ServerLevel court, int slot) {
        UUID id = player.getUUID();
        int t = d.servedTicks;
        Integer start = ROUND_AT.get(id);
        if (start == null) {
            int next = NEXT_ROUND.computeIfAbsent(id, k -> t + 200 + player.getRandom().nextInt(400));
            if (d.knowsRounds && t == next - 100) {
                player.playNotifySound(SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8f, 1.6f);
                player.playNotifySound(SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8f, 1.6f);
                say(player, text("escape_steps", "tap"), ChatFormatting.GOLD);
            }
            if (t >= next) {
                ROUND_AT.put(id, t);
            }
            return;
        }
        int k = t - start;
        if (k > ROUND_TICKS) {
            ROUND_AT.remove(id);
            NEXT_ROUND.put(id, t + 500 + player.getRandom().nextInt(900));
            return;
        }
        double x = 11.5 - 22.0 * k / ROUND_TICKS;
        CourtBuilder.place(court, slot, "gaoler", x, 0, CourtBuilder.CELL.getZ() + CourtBuilder.ROUND_Z, 90f);
        BlockPos c = CourtBuilder.origin(slot).offset(CourtBuilder.CELL);
        if (Math.abs(x) < 6 && k % 8 == 0) {
            court.playSound(null, c.getX() + x, c.getY(), c.getZ() - 0.5, SoundEvents.STONE_STEP, SoundSource.NEUTRAL, 0.6f, 0.8f);
        }
        // he sees anyone digging as he passes
        Long dug = LAST_DIG.get(id);
        if (Math.abs(x - 0.5) < 4 && dug != null && player.getServer().getTickCount() - dug < 40) {
            caught(player, d, "caught");
        }
    }

    /** where the gaoler is on his round (relative to the slot's origin), or NaN */
    public static double gaolerX(ServerPlayer player, RepData d) {
        Integer start = ROUND_AT.get(player.getUUID());
        if (start == null || !lightsOut(d)) {
            return Double.NaN;
        }
        int k = d.servedTicks - start;
        return k > ROUND_TICKS ? Double.NaN : 11.5 - 22.0 * k / ROUND_TICKS;
    }

    private static void sounds(ServerPlayer player, RepData d, ServerLevel court, int slot, String room) {
        var r = player.getRandom();
        BlockPos c = CourtBuilder.origin(slot).offset(CourtBuilder.CELL);
        switch (room) {
            case "labour" -> {
                if (r.nextInt(14) == 0) {
                    double z = -6 + r.nextInt(11);
                    court.playSound(null, c.getX() - 26, c.getY() + 1, c.getZ() + z, SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.7f, 0.8f + r.nextFloat() * 0.3f);
                }
            }
            case "meal" -> {
                if (r.nextInt(70) == 0) {
                    court.playSound(null, c.getX() + 16 + r.nextInt(9), c.getY() + 1, c.getZ() + (r.nextBoolean() ? -5 : 3),
                            SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6f, 0.8f + r.nextFloat() * 0.4f);
                }
            }
            default -> {
                if (r.nextInt(500) == 0) {
                    SoundEvent[] far = {SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundEvents.CHAIN_STEP, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.CHAIN_HIT};
                    SoundEvent s = far[r.nextInt(far.length)];
                    court.playSound(null, c.getX() + r.nextInt(23) - 11, c.getY() + 2, c.getZ() + r.nextInt(13) - 7, s, SoundSource.AMBIENT,
                            s == SoundEvents.IRON_DOOR_CLOSE ? 0.35f : 0.7f, 0.6f + r.nextFloat() * 0.3f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ what the player does
    /** breaking blocks: only the quarry face, during labour (it never runs out) */
    public static boolean onBreak(ServerPlayer player, BlockPos pos) {
        RepData d = Reputation.data(player);
        ServerLevel court = level(player);
        int slot = slot(player);
        if (court == null || d.trialState != 2 || !CourtBuilder.isQuarryFace(slot, pos) || d.servedTicks < LABOUR_BELL || d.servedTicks >= MEAL_BELL) {
            return false;
        }
        court.levelEvent(2001, pos, Block.getId(court.getBlockState(pos)));
        d.labour++;
        int q = quota();
        if (d.labour == q) {
            say(player, text("labour", "done"), ChatFormatting.GREEN);
        } else if (d.labour < q) {
            say(player, "Stone: " + d.labour + " / " + q, ChatFormatting.GRAY);
        }
        player.causeFoodExhaustion(0.3f);
        return true;
    }

    /** using a block: the quarry cart (the chisel), or the wall under your window (digging) */
    public static void onUseBlock(ServerPlayer player, BlockPos pos) {
        RepData d = Reputation.data(player);
        ServerLevel court = level(player);
        if (court == null || d.trialState != 2 || d.walkingOut) {
            return;
        }
        int slot = slot(player);
        long now = player.getServer().getTickCount();
        if (pos.equals(CourtBuilder.abs(slot, CourtBuilder.CART)) || pos.equals(CourtBuilder.abs(slot, CourtBuilder.CART.south()))) {
            boolean watching = guardWatching(slot, now);
            if (!d.planKnown || d.servedTicks < LABOUR_CLOSE || d.servedTicks >= MEAL_BELL) {
                say(player, text("labour", watching ? "cart" : "cart_free"), ChatFormatting.GRAY);
            } else if (d.hasChisel) {
                say(player, "You've already got a chisel up your sleeve. Don't push your luck.", ChatFormatting.GRAY);
            } else if (watching) {
                say(player, text("labour", "seen"), ChatFormatting.RED);
                caught(player, d, "caught");
            } else {
                d.hasChisel = true;
                say(player, text("labour", "stolen"), ChatFormatting.GOLD);
            }
            return;
        }
        if (pos.equals(CourtBuilder.abs(slot, CourtBuilder.DIG))) {
            dig(player, d, now);
            return;
        }
        // the bars of your cell: talk to whoever's on the other side (a visitor, or the gaoler)
        BlockPos rel = pos.subtract(CourtBuilder.origin(slot).offset(CourtBuilder.CELL));
        if (rel.getZ() == 1 && rel.getX() >= -2 && rel.getX() <= 2 && rel.getY() >= 0 && rel.getY() <= 2) {
            Long last = NAG.get(player.getUUID());
            if (last != null && now - last < 10) {
                return;
            }
            NAG.put(player.getUUID(), now);
            onTalk(player, visitorToday(player, d).isEmpty() ? "gaoler" : "visitor");
        }
    }

    public static void dig(ServerPlayer player, RepData d, long now) {
        UUID id = player.getUUID();
        if (!d.hasChisel) {
            nag(player, now, text("escape_steps", "need_tool"));
            return;
        }
        if (!lightsOut(d)) {
            nag(player, now, text("escape_steps", "need_night"));
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            nag(player, now, "Dig with your hands free (nothing in your hand), and keep at it.");
        }
        if (d.digProgress >= digsNeeded()) {
            return;
        }
        d.digProgress++;
        LAST_DIG.put(id, now);
        if (d.digProgress % 10 == 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.35f, 0.7f);
        }
        if (d.digProgress >= digsNeeded()) {
            ServerLevel court = level(player);
            if (court != null) {
                CourtBuilder.hole(court, slot(player), true);
            }
            say(player, text("escape_steps", "through"), ChatFormatting.GOLD);
        } else if (d.digProgress % 25 == 0) {
            say(player, text("escape_steps", "dig") + " (" + (100 * d.digProgress / digsNeeded()) + "%)", ChatFormatting.GRAY);
        }
    }

    private static void nag(ServerPlayer player, long now, String s) {
        Long last = NAG.get(player.getUUID());
        if (last == null || now - last > 60) {
            NAG.put(player.getUUID(), now);
            say(player, s, ChatFormatting.GRAY);
        }
    }

    /** caught: two more days, the tool taken and the hole filled */
    public static void caught(ServerPlayer player, RepData d, String why) {
        d.sentence += 2;
        d.hasChisel = false;
        d.digProgress = 0;
        LAST_DIG.remove(player.getUUID());
        ServerLevel court = level(player);
        if (court != null) {
            CourtBuilder.hole(court, slot(player), false);
            String room = CourtBuilder.room(slot(player), player.getX(), player.getY(), player.getZ());
            if (room.equals("ledge")) {
                putIn(player, "cell");
            }
        }
        if (!d.trials.isEmpty()) {
            JsonObject t = com.google.gson.JsonParser.parseString(d.trials.get(d.trials.size() - 1)).getAsJsonObject();
            t.addProperty("caught", (t.has("caught") ? t.get("caught").getAsInt() : 0) + 1);
            d.trials.set(d.trials.size() - 1, t.toString());
        }
        player.playNotifySound(SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.5f);
        say(player, text("escape_steps", why), ChatFormatting.RED);
    }

    // ------------------------------------------------------------------ talking
    /** right-clicking one of the prison's people. True if they had something to say. */
    public static boolean onTalk(ServerPlayer player, String role) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return false;
        }
        if (role.equals("gaoler")) {
            String line = d.walkingOut ? text("gaoler", "out") : switch (phase(d)) {
                case CourtBuilder.PHASE_LABOUR -> text("gaoler", "labour");
                case CourtBuilder.PHASE_MEAL -> text("gaoler", "meal");
                default -> lightsOut(d) ? text("gaoler", "night") : text("gaoler", "morning");
            };
            Talk.to(player, "The Gaoler").style("prison").line(line).send();
            return true;
        }
        if (role.equals("visitor")) {
            String v = VISITOR.getOrDefault(player.getUUID(), "stranger");
            Talk.to(player, visitorName(v)).title("A visitor").style("prison").lines(visitorLines(v)).send();
            return true;
        }
        JsonObject who = prisoner(role);
        if (who == null) {
            return false;
        }
        menu(player, who, List.of(known(player, who, d)));
        return true;
    }

    private static JsonObject prisoner(String role) {
        if (!t().has("prisoners")) {
            return null;
        }
        for (JsonElement e : t().getAsJsonArray("prisoners")) {
            if (e.getAsJsonObject().get("role").getAsString().equals(role)) {
                return e.getAsJsonObject();
            }
        }
        return null;
    }

    /** the first time a prisoner knows something about you (your trade, your god), they say that instead of hello */
    private static String known(ServerPlayer player, JsonObject who, RepData d) {
        if (t().has("known")) {
            java.util.Set<String> traits = Meb.traits(player);
            String id = who.get("id").getAsString();
            for (JsonElement e : t().getAsJsonArray("known")) {
                JsonObject k = e.getAsJsonObject();
                String key = "known:" + k.get("trait").getAsString();
                if (k.get("who").getAsString().equals(id) && traits.contains(k.get("trait").getAsString()) && !d.hintsGiven.contains(key)) {
                    d.hintsGiven.add(key);
                    return k.get("text").getAsString();
                }
            }
        }
        return greeting(who, d);
    }

    private static String greeting(JsonObject who, RepData d) {
        return switch (who.get("id").getAsString()) {
            case "maddock" -> d.knowsRounds ? "'Keep your head down and your ears open.'" : "'Sit. Eat. Don't stare.'";
            case "wren" -> "'Got news from outside? No? Want some?'";
            case "tam" -> "'Peace, friend. What little there is down here.'";
            default -> "'Pull up a bench. I've stories, if you've time. You've time.'";
        };
    }

    private static void menu(ServerPlayer player, JsonObject who, List<String> said) {
        RepData d = Reputation.data(player);
        String id = who.get("id").getAsString();
        String name = who.get("name").getAsString();
        Talk talk = Talk.to(player, name).title("The Watch's prison").style("prison").lines(said);
        talk.option("rumour", "Heard anything?", p -> menu(p, who, List.of(rumour(p, d, id))));
        talk.option("where", "Where are we?", p -> menu(p, who, where(p, id)));
        talk.option("about", "Tell me about yourself", p -> menu(p, who, strings(who.getAsJsonArray("about"))));
        if (id.equals("maddock")) {
            JsonObject esc = t().getAsJsonObject("escape");
            talk.option("escape", esc.get("ask").getAsString(), p -> escapeTalk(p, who));
        }
        talk.option("leave", "Leave", p -> {
        });
        talk.send();
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        a.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    private static String rumour(ServerPlayer player, RepData d, String who) {
        // now and then, a hint towards a hidden questline you haven't found (each one once)
        if (t().has("hints") && player.getRandom().nextInt(3) == 0) {
            for (Map.Entry<String, JsonElement> h : t().getAsJsonObject("hints").entrySet()) {
                JsonObject hint = h.getValue().getAsJsonObject();
                if (hint.get("who").getAsString().equals(who) && !d.hintsGiven.contains(h.getKey()) && Secrets.notFound(player, h.getKey())) {
                    d.hintsGiven.add(h.getKey());
                    return hint.get("text").getAsString();
                }
            }
        }
        JsonArray r = t().getAsJsonArray("rumours");
        if (r == null || r.isEmpty()) {
            return "...";
        }
        String s = r.get(Math.floorMod(d.rumour, r.size())).getAsString();
        d.rumour++;
        return s;
    }

    private static List<String> where(ServerPlayer player, String who) {
        List<String> out = new ArrayList<>();
        JsonObject w = t().getAsJsonObject("where");
        if (w != null && w.has(who)) {
            out.add(w.get(who).getAsString());
        }
        JsonArray more = t().getAsJsonArray("where_more");
        if (more != null && !more.isEmpty()) {
            out.add(more.get(player.getRandom().nextInt(more.size())).getAsString());
        }
        return out;
    }

    private static void escapeTalk(ServerPlayer player, JsonObject who) {
        RepData d = Reputation.data(player);
        JsonObject esc = t().getAsJsonObject("escape");
        if (!d.planKnown) {
            d.planKnown = true;
            List<String> lines = new ArrayList<>(strings(esc.getAsJsonArray("plan")));
            lines.add(esc.get("trust").getAsString());
            menu(player, who, lines);
            return;
        }
        if (!d.knowsRounds) {
            Talk talk = Talk.to(player, who.get("name").getAsString()).title("The Watch's prison").style("prison")
                    .line(esc.get("trust").getAsString());
            talk.option("give", esc.get("give").getAsString(), "Your bread, all of it.", p -> {
                if (p.getInventory().countItem(Items.BREAD) >= 2) {
                    remove(p, Items.BREAD, 2);
                    d.knowsRounds = true;
                    menu(p, who, strings(esc.getAsJsonArray("given")));
                } else {
                    menu(p, who, List.of(esc.get("no_bread").getAsString()));
                }
            });
            talk.option("back", "Not now", p -> menu(p, who, List.of(greeting(who, d))));
            talk.send();
            return;
        }
        JsonObject pr = esc.getAsJsonObject("progress");
        String line = !d.hasChisel ? pr.get("need_tool").getAsString() : d.digProgress == 0 ? pr.get("need_dig").getAsString() : pr.get("digging").getAsString();
        menu(player, who, List.of(line));
    }

    private static void remove(ServerPlayer player, net.minecraft.world.item.Item item, int n) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int take = Math.min(n, s.getCount());
                s.shrink(take);
                n -= take;
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ the visitor
    private static String visitorFor(ServerPlayer player, RepData d) {
        if (Ranks.hand(player) >= 1) {
            return "hand";
        }
        if (d.watchRank >= 1) {
            return "watch";
        }
        if (player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER)) >= 3) {
            return "widow";
        }
        return "stranger";
    }

    private static JsonObject visitor(String id) {
        if (t().has("visitors")) {
            for (JsonElement e : t().getAsJsonArray("visitors")) {
                if (e.getAsJsonObject().get("id").getAsString().equals(id)) {
                    return e.getAsJsonObject();
                }
            }
        }
        return null;
    }

    public static String visitorName(String id) {
        JsonObject v = visitor(id);
        return v == null ? "A Visitor" : v.get("name").getAsString();
    }

    private static List<String> visitorLines(String id) {
        JsonObject v = visitor(id);
        return v == null ? List.of("...") : strings(v.getAsJsonArray("lines"));
    }

    /** today's visitor, if one is at your bars ("" if not) */
    public static String visitorToday(ServerPlayer player, RepData d) {
        String v = VISITOR.get(player.getUUID());
        return v != null && d.trialState == 2 && d.servedTicks < LABOUR_BELL ? v : "";
    }

    /** operators: a visitor now */
    public static String testVisitor(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return "You're not in a cell.";
        }
        String v = visitorFor(player, d);
        VISITOR.put(player.getUUID(), v);
        if (d.servedTicks >= LABOUR_BELL) {
            return "Visitors only come in the morning (/halfagod test prison time morning first).";
        }
        return visitorName(v) + " is at your bars.";
    }

    /** operators: jump to a time of the prison day */
    public static String testTime(ServerPlayer player, String when) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2 || d.walkingOut) {
            return "You're not in a cell.";
        }
        int target = switch (when) {
            case "morning" -> 0;
            case "labour" -> LABOUR_BELL - 20;
            case "supper" -> MEAL_BELL - 20;
            case "night" -> NIGHT_BELL - 20;
            case "lightsout" -> NIGHT_CLOSE - 20;
            default -> -1;
        };
        if (target < 0) {
            return "morning, labour, supper, night or lightsout";
        }
        if (target < d.servedTicks) {
            passTime(player, Trial.DAY - d.servedTicks + target);
        } else {
            passTime(player, target - d.servedTicks);
        }
        return "It's " + phaseName(d) + " (day " + (d.served + 1) + " of " + d.sentence + "). The bell goes in a second.";
    }

    /** operators: the escape, a step at a time */
    public static String testEscape(ServerPlayer player, String step) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return "You're not in a cell.";
        }
        switch (step) {
            case "plan" -> d.planKnown = true;
            case "trust" -> {
                d.planKnown = true;
                d.knowsRounds = true;
            }
            case "chisel" -> {
                d.planKnown = true;
                d.hasChisel = true;
            }
            case "dig" -> {
                d.planKnown = true;
                d.hasChisel = true;
                d.digProgress = digsNeeded() - 1;
            }
            default -> {
                return "plan, trust, chisel or dig";
            }
        }
        return "Escape: plan " + d.planKnown + ", rounds " + d.knowsRounds + ", chisel " + d.hasChisel + ", hole " + (100 * d.digProgress / digsNeeded()) + "%.";
    }

    /** the gaoler, with a lantern, coming down the corridor (for the scene) */
    public static CourtBuilder.Scene scene(ServerPlayer player, RepData d, ServerLevel court, int slot, CourtBuilder.Scene court0) {
        String v = visitorToday(player, d);
        return new CourtBuilder.Scene(phase(d), v, v.isEmpty() ? "" : visitorName(v), court0.advocate(), court0.witnessName(), court0.witnessJob(),
                court0.witnessHidden(), court0.speaker(), guardWatching(slot, player.getServer().getTickCount()), gaolerX(player, d));
    }

    /** particles over a juror's head as they vote */
    public static void vote(ServerLevel court, Entity juror, boolean guilty) {
        court.sendParticles(guilty ? ParticleTypes.ANGRY_VILLAGER : ParticleTypes.HAPPY_VILLAGER, juror.getX(), juror.getY() + 2.2, juror.getZ(),
                guilty ? 3 : 6, 0.25, 0.2, 0.25, 0.02);
    }
}

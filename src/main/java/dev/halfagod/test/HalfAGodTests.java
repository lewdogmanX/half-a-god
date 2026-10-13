package dev.halfagod.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.HalfAGod;
import dev.halfagod.core.CourtBuilder;
import dev.halfagod.core.CourtData;
import dev.halfagod.core.Dreams;
import dev.halfagod.core.EndCard;
import dev.halfagod.core.RepData;
import dev.halfagod.core.Reputation;
import dev.halfagod.core.StoryData;
import dev.halfagod.core.Trial;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/**
 * Game tests, run on a real server by CI (gradle runGametest): the story tables load, the court dimension exists and
 * builds, and a whole trial works end to end: taken, tried, sentenced, nights served, home with every item back.
 */
@GameTestHolder(HalfAGod.MODID)
@PrefixGameTestTemplate(false)
public final class HalfAGodTests {
    private HalfAGodTests() {
    }

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) {
            h.fail(what);
            throw new IllegalStateException(what);
        }
    }

    @GameTest(template = "empty")
    public static void storyTables(GameTestHelper h) {
        StoryData d = StoryData.INSTANCE;
        check(h, d.dreams.size() >= 20, "dreams loaded: " + d.dreams.size());
        check(h, d.specialDreams.containsKey("kidnap") && d.specialDreams.containsKey("godsaw"), "special dreams loaded");
        check(h, d.charges.size() >= 20, "trial charges loaded: " + d.charges.size());
        check(h, d.pleas.size() == 4, "pleas loaded: " + d.pleas.size());
        check(h, d.secrets.size() == 5, "secrets loaded: " + d.secrets.size());
        check(h, d.acts.size() >= 9, "acts loaded: " + d.acts.size());
        check(h, Dreams.byId("past_1") != null, "a dream by id");
        check(h, Dreams.actIndex(Set.of("hag_w1_the_skull_market_done")) == 2, "Act II is act 2");
        h.succeed();
    }

    /** The game-test server never loads dimensions from data packs (a real server does; CI checks that separately with a
     *  real server run), so the tests hold court in the test level. */
    private static ServerLevel court(GameTestHelper h) {
        if (h.getLevel().getServer().getLevel(Trial.COURT) == null) {
            Trial.testCourt = h.getLevel();
        }
        return Trial.court(h.getLevel().getServer());
    }

    @GameTest(template = "empty")
    public static void courtBuilds(GameTestHelper h) {
        ServerLevel court = court(h);
        check(h, court != null, "somewhere to hold court");
        int slot = 900;
        CourtBuilder.ensure(court, slot);
        BlockPos o = CourtBuilder.origin(slot);
        BlockState rail = court.getBlockState(o.offset(CourtBuilder.DOCK).offset(-1, 0, 0));
        check(h, rail.is(Blocks.DARK_OAK_FENCE), "the dock has a rail");
        check(h, rail.getValue(net.minecraft.world.level.block.FenceBlock.NORTH) && rail.getValue(net.minecraft.world.level.block.FenceBlock.SOUTH), "the dock's rail joins up");
        check(h, court.getBlockState(o.offset(0, 1, -15)).is(Blocks.DARK_OAK_PLANKS), "the judge sits up on a dais");
        check(h, court.getBlockState(o.offset(-4, 0, -10)).is(Blocks.LECTERN), "the clerk has a lectern");
        check(h, !court.getBlockState(o.offset(0, 0, 4)).is(Blocks.DARK_OAK_FENCE) && court.getBlockState(o.offset(3, 0, 4)).is(Blocks.DARK_OAK_FENCE), "the bar of the court");
        check(h, court.getBlockState(o.offset(0, 11, -22)).is(Blocks.BLACK_CONCRETE) && court.getBlockState(o.offset(0, 10, -21)).isAir(), "the Watch's eye");
        check(h, court.getBlockState(o.offset(0, 15, 0)).is(Blocks.DARK_OAK_PLANKS) && court.getBlockState(o.offset(0, 13, 0)).isAir(), "a vaulted ceiling");
        BlockPos lake = CourtBuilder.abs(slot, new BlockPos(0, -6, 20));
        check(h, court.getBlockState(lake).is(Blocks.LAVA), "a lava lake under the cell window: " + court.getBlockState(lake));
        check(h, court.getBlockState(CourtBuilder.abs(slot, new BlockPos(0, 24, 9))).isAir()
                && !court.getBlockState(CourtBuilder.abs(slot, new BlockPos(0, 26, 9))).isAir(), "a cavern roof overhead");
        BlockPos cell = o.offset(CourtBuilder.CELL);
        BlockPos spot = o.offset(CourtBuilder.CELL_SPOT);
        check(h, court.getBlockState(spot).isAir() && court.getBlockState(spot.above()).isAir(), "room to stand in the cell");
        check(h, court.getBlockState(spot.below()).isSolid(), "a floor to stand on");
        check(h, court.getBlockState(cell.offset(-2, 0, 5)).is(Blocks.HAY_BLOCK), "a hay-bale cot");
        check(h, court.getBlockState(cell.offset(0, 11, -7)).is(Blocks.BELL), "a bell in the tower");
        check(h, court.getBlockState(cell.offset(0, 1, 7)).isAir() && court.getBlockState(cell.offset(5, 0, 7)).isAir(), "nothing in the way of the ledge");
        BlockState wall = court.getBlockState(cell.offset(0, 0, 6));
        check(h, wall.is(Blocks.OBSIDIAN) || wall.is(Blocks.CRYING_OBSIDIAN), "the cell is obsidian");
        BlockState bars = court.getBlockState(cell.offset(1, 2, 1));
        check(h, bars.is(Blocks.IRON_BARS) && bars.getValue(net.minecraft.world.level.block.IronBarsBlock.EAST)
                && bars.getValue(net.minecraft.world.level.block.IronBarsBlock.WEST), "a barred front that joins up");
        check(h, court.getBlockState(cell.offset(0, 1, 6)).is(Blocks.IRON_BARS), "a barred window");
        int beds = 0;
        for (BlockPos p : BlockPos.betweenClosed(cell.offset(-12, -1, -8), cell.offset(12, 4, 6))) {
            if (court.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                beds++;
            }
        }
        check(h, beds == 0, "no beds in the cells: " + beds);
        check(h, CourtData.get(court).builtVersion(slot) == CourtBuilder.VERSION, "the build is remembered");
        check(h, !CourtBuilder.ensure(court, slot), "not built twice");
        // an older court is cleared and rebuilt
        court.setBlock(cell.offset(-1, 0, 0), Blocks.BROWN_BED.defaultBlockState(), 3);
        CourtData.get(court).setBuilt(slot, 2);
        check(h, CourtBuilder.ensure(court, slot), "an old court is rebuilt");
        check(h, !(court.getBlockState(cell.offset(-1, 0, 0)).getBlock() instanceof net.minecraft.world.level.block.BedBlock), "the old bed is gone");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wholeTrial(GameTestHelper h) {
        court(h);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos home = h.absolutePos(new BlockPos(1, 1, 1));
        p.teleportTo(h.getLevel(), home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0f, 0f);
        p.setRespawnPosition(Level.OVERWORLD, home, 0f, true, false);
        p.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 5));
        p.getInventory().setItem(10, new ItemStack(Items.EMERALD, 20));
        p.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 4);
        RepData d = Reputation.data(p);

        check(h, Trial.kidnap(p), "the Watch takes them");
        check(h, Trial.inCourt(p), "they're in court");
        check(h, d.trialState == 1, "on trial");
        check(h, p.getInventory().isEmpty(), "their things were taken");
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        check(h, script.getAsJsonArray("lines").size() >= 4, "the court has things to say");
        check(h, script.get("weight").getAsInt() > 0, "the villagers they killed count against them");

        Trial.onPlea(p, "guilty");
        check(h, !d.trialVerdict.isEmpty(), "a verdict");
        JsonObject v = JsonParser.parseString(d.trialVerdict).getAsJsonObject();
        check(h, v.get("guilty").getAsBoolean(), "guilty");
        Trial.onPlea(p, "continue");
        check(h, d.trialState == 2, "in a cell");
        check(h, Trial.inCourt(p), "still in court");
        check(h, d.sentence >= 1, "a sentence");
        ServerLevel court = Trial.court(p.getServer());
        BlockPos spot = CourtBuilder.origin(CourtData.get(court).slot(p.getUUID())).offset(CourtBuilder.CELL_SPOT);
        check(h, p.blockPosition().equals(spot), "in the cell: " + p.blockPosition() + " not " + spot);
        check(h, p.getInventory().countItem(Items.BREAD) == 2, "bread through the bars");

        // the days pass in real time, in the cell
        int days = d.sentence;
        check(h, Trial.minutesLeft(d) == days * 20, "20 real minutes a day: " + Trial.minutesLeft(d));
        Trial.passTime(p, Trial.DAY - 20);
        check(h, d.served == 0 && d.trialState == 2, "not a day yet");
        Trial.passTime(p, 20);
        check(h, days == 1 ? d.walkingOut : d.served == 1 && d.servedTicks == 0, "a day served after a whole day in the prison");
        for (int i = 1; i < days; i++) {
            Trial.passTime(p, Trial.DAY);
        }
        // sentence served: the doors open and they walk out through the gate
        int slot = CourtData.get(court).slot(p.getUUID());
        check(h, d.trialState == 2 && d.walkingOut, "walking out");
        check(h, CourtBuilder.doorOpen(court, slot, CourtBuilder.Door.GATE) && CourtBuilder.doorOpen(court, slot, CourtBuilder.Door.CELL), "the doors are open");
        BlockPos gate = CourtBuilder.abs(slot, CourtBuilder.GATE);
        p.teleportTo(court, gate.getX() + 0.5, gate.getY(), gate.getZ() - 2.5, 180f, 0f);
        dev.halfagod.core.Prison.tick(p, d, court, slot, p.getServer().getTickCount());
        check(h, d.trialState == 0, "released after serving");
        JsonObject record = JsonParser.parseString(d.trials.get(d.trials.size() - 1)).getAsJsonObject();
        check(h, record.get("outcome").getAsString().equals("served") && record.get("served").getAsInt() == days, "served the whole sentence: " + record);
        check(h, p.level().dimension() == Level.OVERWORLD && p.blockPosition().distSqr(home) < 9, "home again, by the bed");
        check(h, p.getInventory().countItem(Items.DIAMOND) == 5, "diamonds back");
        check(h, p.getInventory().countItem(Items.EMERALD) == 20, "emeralds back");
        check(h, home.equals(p.getRespawnPosition()), "their respawn point is back");
        check(h, d.trials.size() == 1, "the Codex keeps the transcript");
        check(h, d.convictions == 1, "one conviction");

        // a second trial, pleading not guilty with nothing much against them, should end with them free
        p.resetStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
        check(h, Trial.kidnap(p), "taken again");
        Trial.onPlea(p, "not_guilty");
        Trial.onPlea(p, "continue");
        check(h, d.trialState == 0, "the court has no case");
        check(h, p.getInventory().countItem(Items.DIAMOND) == 5, "diamonds back again");

        // what gets saved
        CompoundTag saved = p.serializeAttachments(p.registryAccess());
        check(h, saved != null && saved.getCompound("half_a_god:story").contains("trials"), "trial records are saved");
        check(h, EndCard.compute(p, Set.of()).has("ending"), "the end card works");
        p.discard();
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void waysOut(GameTestHelper h) {
        court(h);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos home = h.absolutePos(new BlockPos(1, 1, 1));
        p.teleportTo(h.getLevel(), home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0f, 0f);
        p.setRespawnPosition(Level.OVERWORLD, home, 0f, true, false);
        p.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 12);
        RepData d = Reputation.data(p);

        // the Hand breaks you out on the second day
        check(h, Trial.kidnap(p), "taken (breakout)");
        d.handRank = 2;
        Trial.onPlea(p, "not_guilty");
        Trial.onPlea(p, "continue");
        check(h, d.trialState == 2 && d.sentence >= 3, "a long sentence for a repeat offender: " + d.sentence);
        Trial.serveDay(p);
        check(h, d.trialState == 2, "still inside after one day");
        Trial.serveDay(p);
        check(h, d.trialState == 0 && d.brokenOut, "Silas's men came on the second day");

        // paying a juror: 16 emeralds come out of your things
        d.handRank = 1;
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
        p.resetStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
        p.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 4);
        check(h, Trial.kidnap(p), "taken (bribe)");
        boolean offered = d.trialScript.contains("\"bribe\"");
        check(h, offered, "a juror can be bought");
        Trial.onPlea(p, "bribe");
        Trial.onPlea(p, "continue");
        check(h, d.trialState == 0, "the bought juror worked");
        check(h, p.getInventory().countItem(Items.EMERALD) == 4, "16 emeralds paid: " + p.getInventory().countItem(Items.EMERALD));
        p.discard();
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sampleWitnesses(GameTestHelper h) {
        court(h);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        RepData d = Reputation.data(p);
        check(h, Trial.kidnap(p, Trial.SAMPLE_RECORD), "taken with a made-up record");
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        check(h, script.get("heard").getAsInt() == 4, "four witnesses speak: " + script.get("heard"));
        check(h, script.get("silent").getAsInt() == 3, "three can't be found: " + script.get("silent"));
        check(h, d.trialScript.contains("Reeve Halden") && d.trialScript.contains("cannot be found"), "the right people");
        Trial.release(p, d, "released");
        check(h, d.trialState == 0, "let out");
        p.discard();
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void storySimulator(GameTestHelper h) {
        court(h);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        double before = Reputation.data(p).value;
        String msg = dev.halfagod.core.Simulate.run(p, "act3", "evil");
        RepData d = Reputation.data(p);
        check(h, msg.startsWith("Simulated"), msg);
        check(h, Dreams.actIndex(dev.halfagod.core.Meb.traits(p)) == 3, "in Act III: " + msg);
        check(h, d.value <= -20, "an evil playthrough is Fallen or worse: " + d.value);
        check(h, !d.doneRep.isEmpty() && d.path.size() > 10, "the map and Your Path are filled in");
        // the trial has a record to read from
        int heard = 0;
        for (int i = 0; i < 4 && heard == 0; i++) {
            dev.halfagod.core.Simulate.run(p, "act3", "evil");
            d = Reputation.data(p);
            check(h, Trial.kidnap(p), "taken");
            heard = JsonParser.parseString(d.trialScript).getAsJsonObject().get("heard").getAsInt();
            Trial.release(p, d, "released");
        }
        check(h, heard > 0, "witnesses from the simulated story");
        // the end of the story
        dev.halfagod.core.Simulate.run(p, "end", "good");
        d = Reputation.data(p);
        check(h, dev.halfagod.core.Fates.storyOver(dev.halfagod.core.Meb.traits(p)), "the story is over");
        check(h, !EndCard.compute(p, dev.halfagod.core.Meb.traits(p)).get("ending").getAsString().equals("Still Walking"), "an ending");
        // and back to the real thing
        dev.halfagod.core.Simulate.clear(p);
        d = Reputation.data(p);
        check(h, d.simTraits.isEmpty() && d.simBackup.isEmpty(), "the made-up record is gone");
        check(h, d.value == before, "reputation as it was: " + d.value);
        check(h, Dreams.actIndex(dev.halfagod.core.Meb.traits(p)) == -1, "back to no story");
        p.discard();
        h.succeed();
    }

    /** the court acts it out: answers to each charge change the case, the jury votes, the Codex keeps it all */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void answersAndJury(GameTestHelper h) {
        court(h);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        RepData d = Reputation.data(p);
        check(h, Trial.kidnap(p, Trial.SAMPLE_RECORD), "taken");
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        int w0 = Trial.weight(script, d);
        var lines = script.getAsJsonArray("lines");
        java.util.List<Integer> charges = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            var l = lines.get(i).getAsJsonArray();
            if (l.size() > 2 && l.get(2).getAsJsonObject().has("charge")) {
                charges.add(i);
            }
            Trial.onLineShown(p, i);
        }
        check(h, charges.size() == 4, "four charges to answer: " + charges.size());
        int w1 = lines.get(charges.get(0)).getAsJsonArray().get(2).getAsJsonObject().get("w").getAsInt();
        int w2 = lines.get(charges.get(1)).getAsJsonArray().get(2).getAsJsonObject().get("w").getAsInt();
        Trial.onAnswer(p, charges.get(0), "deny");
        Trial.onAnswer(p, charges.get(1), "explain");
        Trial.onAnswer(p, charges.get(1), "deny"); // only the first answer counts
        int expect = w0 + (w1 >= 2 ? 1 : -w1) - Math.min(1, w2);
        check(h, Trial.weight(script, d) == Math.max(0, expect), "the answers weigh in: " + Trial.weight(script, d) + " not " + expect);
        Trial.onPlea(p, "silent");
        JsonObject v = JsonParser.parseString(d.trialVerdict).getAsJsonObject();
        var votes = v.getAsJsonArray("votes");
        int guiltyVotes = 0;
        for (var e : votes) {
            guiltyVotes += e.getAsBoolean() ? 1 : 0;
        }
        check(h, votes.size() == 6, "six jurors");
        check(h, v.get("guilty").getAsBoolean() ? guiltyVotes >= 4 : guiltyVotes <= 3, "the votes match the verdict: " + guiltyVotes);
        Trial.onPlea(p, "continue");
        JsonObject t = JsonParser.parseString(d.trials.get(d.trials.size() - 1)).getAsJsonObject();
        check(h, (t.toString().contains("The accused is lying") || t.toString().contains("Struck from the record"))
                && t.toString().contains("It does not forgive them"), "the Codex keeps the court's replies");
        check(h, t.getAsJsonObject("verdict").has("votes"), "and the votes");
        Trial.release(p, d, "released");
        // the Gilded Hand speaks for its own
        d.handRank = 2;
        d.trialActs.clear();
        check(h, Trial.kidnap(p, Trial.SAMPLE_RECORD), "taken again");
        JsonObject s2 = JsonParser.parseString(d.trialScript).getAsJsonObject();
        check(h, s2.get("advocate").getAsBoolean() && s2.get("mods").getAsInt() == -2, "an advocate of the Hand");
        Trial.release(p, d, "released");
        p.discard();
        h.succeed();
    }

    /** a day in the prison: the quarry, supper, Old Maddock, and the way out */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void prisonDay(GameTestHelper h) {
        ServerLevel court = court(h);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos home = h.absolutePos(new BlockPos(1, 1, 1));
        p.teleportTo(h.getLevel(), home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0f, 0f);
        p.setRespawnPosition(Level.OVERWORLD, home, 0f, true, false);
        p.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 3));
        p.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 12);
        RepData d = Reputation.data(p);
        check(h, Trial.kidnap(p), "taken");
        Trial.onPlea(p, "not_guilty");
        Trial.onPlea(p, "continue");
        check(h, d.trialState == 2, "in prison");
        d.sentence = 4;
        int slot = CourtData.get(court).slot(p.getUUID());
        // the quarry
        dev.halfagod.core.Prison.testTime(p, "labour");
        Trial.passTime(p, 1300);
        check(h, CourtBuilder.room(slot, p.getX(), p.getY(), p.getZ()).equals("labour"), "dragged to the quarry");
        check(h, p.getInventory().countItem(Items.WOODEN_PICKAXE) == 1, "a pickaxe for the day");
        BlockPos face = CourtBuilder.abs(slot, new BlockPos(-27, 1, 0));
        check(h, CourtBuilder.isQuarryFace(slot, face), "the quarry face");
        for (int i = 0; i < 48; i++) {
            dev.halfagod.core.Prison.onBreak(p, face);
        }
        check(h, d.labour == 48, "the quota: " + d.labour);
        check(h, !court.getBlockState(face).isAir(), "the stone never runs out");
        // supper
        Trial.passTime(p, dev.halfagod.core.Prison.MEAL_BELL - d.servedTicks);
        check(h, d.fed && p.getInventory().countItem(Items.WOODEN_PICKAXE) == 0, "fed tonight, and the pickaxe handed back");
        int bread = p.getInventory().countItem(Items.BREAD);
        Trial.passTime(p, 1220);
        check(h, CourtBuilder.room(slot, p.getX(), p.getY(), p.getZ()).equals("meal"), "in the cafeteria");
        check(h, p.getInventory().countItem(Items.BREAD) == bread + 3, "supper");
        // Old Maddock
        check(h, dev.halfagod.core.Prison.onTalk(p, "prisoner0"), "Maddock talks");
        check(h, dev.halfagod.core.Talk.openOptions(p).containsAll(java.util.List.of("rumour", "where", "about", "escape")), "what you can ask him");
        dev.halfagod.core.Talk.choose(p, "where");
        dev.halfagod.core.Talk.choose(p, "escape");
        check(h, d.planKnown, "his plan");
        dev.halfagod.core.Talk.choose(p, "escape");
        check(h, dev.halfagod.core.Talk.openOptions(p).contains("give"), "he wants your bread");
        dev.halfagod.core.Talk.choose(p, "give");
        check(h, d.knowsRounds, "he tells you the gaoler's rounds");
        // the chisel: taken when the guard looks away, caught when he doesn't
        dev.halfagod.core.Prison.testTime(p, "labour");
        Trial.passTime(p, 1300);
        int before = d.sentence;
        boolean watching = dev.halfagod.core.Prison.guardWatching(slot, p.getServer().getTickCount());
        dev.halfagod.core.Prison.onUseBlock(p, CourtBuilder.abs(slot, CourtBuilder.CART));
        check(h, watching ? d.sentence == before + 2 && !d.hasChisel : d.hasChisel, "the cart: " + (watching ? "caught" : "stolen"));
        d.hasChisel = true;
        // digging: only after lights out
        dev.halfagod.core.Prison.dig(p, d, p.getServer().getTickCount());
        check(h, d.digProgress == 0, "no digging in the day");
        dev.halfagod.core.Prison.testTime(p, "lightsout");
        Trial.passTime(p, 40);
        check(h, dev.halfagod.core.Prison.lightsOut(d) && CourtBuilder.room(slot, p.getX(), p.getY(), p.getZ()).equals("cell"), "lights out, in the cell");
        for (int i = 0; i < dev.halfagod.core.Prison.digsNeeded(); i++) {
            dev.halfagod.core.Prison.dig(p, d, p.getServer().getTickCount());
        }
        check(h, court.getBlockState(CourtBuilder.abs(slot, CourtBuilder.DIG)).isAir(), "through the wall");
        BlockPos ledge = CourtBuilder.abs(slot, new BlockPos(11, -1, 7));
        check(h, court.getBlockState(ledge).isSolid(), "a ledge outside");
        p.teleportTo(court, ledge.getX() + 0.5, ledge.getY() + 1, ledge.getZ() + 0.5, -90f, 0f);
        dev.halfagod.core.Prison.tick(p, d, court, slot, p.getServer().getTickCount());
        check(h, d.trialState == 0 && d.escapes == 1, "escaped");
        check(h, p.getInventory().countItem(Items.DIAMOND) == 3, "home with your things");
        check(h, !court.getBlockState(CourtBuilder.abs(slot, CourtBuilder.DIG)).isAir(), "the hole is filled behind you");
        check(h, JsonParser.parseString(d.trials.get(d.trials.size() - 1)).getAsJsonObject().get("outcome").getAsString().equals("escaped"), "the Codex says so");
        p.discard();
        h.succeed();
    }

    /** confession, interrogation, your reputation ahead of you */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wordsFollowYou(GameTestHelper h) {
        court(h);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        RepData d = Reputation.data(p);
        p.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 6);
        JsonObject s1 = Trial.script(p, d, dev.halfagod.core.Meb.traits(p));
        check(h, dev.halfagod.core.Words.sins(p).containsKey("villagers"), "something to confess");
        dev.halfagod.core.Words.confess(p, "villagers");
        check(h, !dev.halfagod.core.Words.sins(p).containsKey("villagers"), "confessed");
        JsonObject s2 = Trial.script(p, d, dev.halfagod.core.Meb.traits(p));
        check(h, s2.get("weight").getAsInt() == s1.get("weight").getAsInt() - 1, "a confessed charge weighs less: " + s1.get("weight") + " -> " + s2.get("weight"));
        check(h, s2.toString().contains("confessed this to a priest"), "the clerk says so");
        // interrogation: paid, it always works, once
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
        int letters = d.letters.size();
        check(h, dev.halfagod.core.Words.ask(p, "vael", "bribe"), "Vael talks for money");
        check(h, d.letters.size() == letters + 1 && p.getInventory().countItem(Items.EMERALD) == 2, "a note in the Codex, five emeralds gone");
        check(h, !dev.halfagod.core.Words.ask(p, "vael", "bribe"), "only once");
        // what a village has heard of you, through vanilla gossip
        net.minecraft.world.entity.npc.Villager v = EntityType.VILLAGER.create(h.getLevel());
        check(h, v != null, "a villager");
        dev.halfagod.core.Words.rumour(v, p.getUUID(), -25);
        check(h, v.getGossips().getReputation(p.getUUID(), t -> true) == -25, "they've heard bad things");
        dev.halfagod.core.Words.rumour(v, p.getUUID(), 40);
        check(h, v.getGossips().getReputation(p.getUUID(), t -> true) == 40, "now good things: " + v.getGossips().getReputation(p.getUUID(), t -> true));
        p.discard();
        h.succeed();
    }

    /** two players: a price on a rival's head, and a witness at a rival's trial */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rivals(GameTestHelper h) {
        court(h);
        ServerPlayer a = h.makeMockServerPlayerInLevel();
        ServerPlayer b = h.makeMockServerPlayerInLevel();
        check(h, !a.getUUID().equals(b.getUUID()), "two players");
        a.teleportTo(h.getLevel(), b.getX() + 3, b.getY(), b.getZ(), 0f, 0f);
        RepData da = Reputation.data(a), db = Reputation.data(b);
        // the bounty
        da.handRank = 1;
        a.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
        check(h, dev.halfagod.core.Rivals.placeBounty(a, b.getUUID().toString()), "the Hand takes the job");
        check(h, !db.bountyBy.isEmpty() && a.getInventory().countItem(Items.EMERALD) == 0, "paid, and a price on their head");
        a.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
        check(h, !dev.halfagod.core.Rivals.placeBounty(a, b.getUUID().toString()), "not twice");
        // a witness
        dev.halfagod.core.Rivals.saw(b, "villager");
        // (other tests' players may be close enough to have seen it too)
        check(h, db.sightings.stream().anyMatch(x -> x.startsWith(a.getUUID().toString())), "a saw it: " + db.sightings);
        b.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 3);
        check(h, Trial.kidnap(b), "b is taken");
        check(h, dev.halfagod.core.Talk.openOptions(a).contains("against"), "a is asked to speak");
        JsonObject before = JsonParser.parseString(db.trialScript).getAsJsonObject();
        int w = Trial.weight(before, db);
        dev.halfagod.core.Talk.choose(a, "against");
        JsonObject after = JsonParser.parseString(db.trialScript).getAsJsonObject();
        check(h, Trial.weight(after, db) == w + 2, "a's words weigh against b");
        check(h, after.getAsJsonArray("lines").size() == before.getAsJsonArray("lines").size() + 1, "a line in the transcript");
        var lines = after.getAsJsonArray("lines");
        check(h, lines.get(lines.size() - 1).getAsJsonArray().get(1).getAsString().contains("plead"), "before the plea");
        Trial.release(b, db, "released");
        a.discard();
        b.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dreamsPick(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        RepData d = Reputation.data(p);
        Set<String> traits = Set.of("hag_the_first_letter_done", "hag_a1_ashford_burns_done", "hag_w1_the_skull_market_done", "peddler_slain");
        java.util.Set<String> got = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            JsonObject dream = Dreams.pick(p, d, traits);
            if (dream == null) {
                break;
            }
            String id = dream.get("id").getAsString();
            check(h, got.add(id), "never the same dream twice: " + id);
            String kind = dream.get("kind").getAsString();
            check(h, !kind.equals("omen") || dream.get("act").getAsString().equals("hag_w1_the_skull_market_done"), "omens only for this act: " + id);
            check(h, !kind.equals("echo") || traits.contains(dream.get("trait").getAsString()), "echoes only of what you did: " + id);
            d.dreams.add(id + "|1");
        }
        check(h, got.contains("past_1") && got.contains("past_6"), "the god's past, all of it: " + got);
        check(h, got.contains("echo_peddler_slain"), "the echo of the peddler: " + got);
        check(h, got.contains("omen_w1_the_skull_market"), "the omen for Act II: " + got);
        p.discard();
        h.succeed();
    }

    /** The moon: the three buildings load at the sizes they were drawn, and the Eclipse does what it says. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void moonAndEclipse(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var sizes = StoryData.INSTANCE.table("moon").getAsJsonObject("sizes");
        for (String name : sizes.keySet()) {
            var t = level.getStructureManager().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, name));
            check(h, t.isPresent(), "structure template " + name);
            var size = t.get().getSize();
            var want = sizes.getAsJsonArray(name);
            check(h, size.getX() == want.get(0).getAsInt() && size.getY() == want.get(1).getAsInt() && size.getZ() == want.get(2).getAsInt(),
                    name + " size " + size + " (built " + want + ")");
        }
        check(h, StoryData.INSTANCE.table("moon").has("start"), "moon table loaded");

        ItemStack sword = new ItemStack(dev.halfagod.item.ModItems.ECLIPSE.get());
        check(h, !sword.isDamageableItem(), "the Eclipse never wears out");
        check(h, sword.has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT), "the Eclipse can't burn");
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        sword.getItem().use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(h, zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) && zombie.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),
                "the zombie is slowed and weakened");
        check(h, p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "the wielder is stronger");
        check(h, p.getCooldowns().isOnCooldown(sword.getItem()), "then it rests");
        zombie.discard();
        p.discard();
        h.succeed();
    }
}

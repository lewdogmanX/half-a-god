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
        BlockPos cell = o.offset(CourtBuilder.CELL);
        check(h, court.getBlockState(cell.offset(-1, 0, 0)).is(Blocks.BROWN_BED), "the cell has a bed");
        check(h, court.getBlockState(cell.offset(0, 0, -2)).is(Blocks.OBSIDIAN), "the cell is obsidian");
        check(h, court.getBlockState(cell.offset(0, 0, -1)).isAir(), "the cell is hollow");
        BlockPos loose = CourtBuilder.loosen(court, slot, 7);
        check(h, court.getBlockState(loose).is(Blocks.BLACK_CONCRETE), "one block is loose");
        check(h, CourtData.get(court).builtVersion(slot) == CourtBuilder.VERSION, "the build is remembered");
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
        check(h, court.getBlockState(BlockPos.of(d.escapeBlock)).is(Blocks.BLACK_CONCRETE), "the loose block is there");

        int nights = d.sentence;
        for (int i = 0; i < nights; i++) {
            Trial.onNightInCell(p);
        }
        check(h, d.trialState == 0, "released after serving");
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

        // escape: the loose block
        check(h, Trial.kidnap(p), "taken (escape)");
        Trial.onPlea(p, "silent");
        Trial.onPlea(p, "continue");
        check(h, d.trialState == 2, "in a cell (escape)");
        int hand = d.handDone;
        Trial.release(p, d, "escaped");
        check(h, d.trialState == 0 && d.escapes == 1, "escaped");
        check(h, d.handDone == hand + 3, "the Hand respects it");
        check(h, JsonParser.parseString(d.trials.get(d.trials.size() - 1)).getAsJsonObject().get("outcome").getAsString().equals("escaped"),
                "the Codex says how they got out");

        // the Hand breaks you out on the second night
        d.handRank = 2;
        check(h, Trial.kidnap(p), "taken (breakout)");
        Trial.onPlea(p, "not_guilty");
        Trial.onPlea(p, "continue");
        check(h, d.trialState == 2 && d.sentence >= 3, "a long sentence for a repeat offender: " + d.sentence);
        Trial.onNightInCell(p);
        check(h, d.trialState == 2, "still inside after one night");
        Trial.onNightInCell(p);
        check(h, d.trialState == 0 && d.brokenOut, "Silas's men came on the second night");

        // paying a juror: 16 emeralds come out of your things
        d.handRank = 1;
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
        p.resetStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
        p.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 2);
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
}

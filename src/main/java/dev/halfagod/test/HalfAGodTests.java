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

    @GameTest(template = "half_a_god:empty")
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

    @GameTest(template = "half_a_god:empty")
    public static void courtBuilds(GameTestHelper h) {
        ServerLevel court = Trial.court(h.getLevel().getServer());
        check(h, court != null, "the court dimension is loaded");
        int slot = 900;
        CourtBuilder.ensure(court, slot);
        BlockPos o = CourtBuilder.origin(slot);
        BlockState bars = court.getBlockState(o.offset(CourtBuilder.DOCK).offset(-1, 0, 0));
        check(h, bars.is(Blocks.IRON_BARS), "the dock has bars");
        check(h, bars.getValue(IronBarsBlock.NORTH) || bars.getValue(IronBarsBlock.SOUTH), "the dock's bars join up");
        BlockPos cell = o.offset(CourtBuilder.CELL);
        check(h, court.getBlockState(cell.offset(-1, 0, 0)).is(Blocks.BROWN_BED), "the cell has a bed");
        check(h, court.getBlockState(cell.offset(0, 0, -2)).is(Blocks.OBSIDIAN), "the cell is obsidian");
        check(h, court.getBlockState(cell.offset(0, 0, -1)).isAir(), "the cell is hollow");
        BlockPos loose = CourtBuilder.loosen(court, slot, 7);
        check(h, court.getBlockState(loose).is(Blocks.BLACK_CONCRETE), "one block is loose");
        check(h, CourtData.get(court).builtVersion(slot) == CourtBuilder.VERSION, "the build is remembered");
        h.succeed();
    }

    @GameTest(template = "half_a_god:empty", timeoutTicks = 200)
    public static void wholeTrial(GameTestHelper h) {
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
        check(h, Trial.inCourt(p), "still in the court's dimension");
        check(h, d.sentence >= 1, "a sentence");
        ServerLevel court = Trial.court(p.getServer());
        check(h, court.getBlockState(BlockPos.of(d.escapeBlock)).is(Blocks.BLACK_CONCRETE), "the loose block is there");

        int nights = d.sentence;
        for (int i = 0; i < nights; i++) {
            Trial.onNightInCell(p);
        }
        check(h, d.trialState == 0, "released after serving");
        check(h, p.level().dimension() == Level.OVERWORLD, "home again");
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
}

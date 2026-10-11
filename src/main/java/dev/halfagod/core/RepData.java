package dev.halfagod.core;

import dev.halfagod.HalfAGod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Everything the mod remembers about one player. Kept through death. */
public final class RepData {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HalfAGod.MODID);

    public static final Supplier<AttachmentType<RepData>> TYPE = ATTACHMENTS.register("story",
            () -> AttachmentType.builder(RepData::new).serialize(new Serializer()).copyOnDeath().build());

    /** Reputation, -100 (Wicked) to +100 (Saint). */
    public double value;
    /** Mebahel traits already seen (so each one is reacted to once). */
    public final Set<String> knownTraits = new HashSet<>();
    public boolean initialised;
    /** Mebahel quest choices already counted towards reputation. */
    public final Set<String> countedChoices = new HashSet<>();
    /** Village loot containers already opened (block positions). */
    public final LinkedHashSet<Long> looted = new LinkedHashSet<>();
    /** The Chronicle: lines in the order they happened. */
    public final List<String> chronicle = new ArrayList<>();
    /** Reputation when each act's road chapter finished (act number -> value). */
    public final Map<String, Integer> actRep = new LinkedHashMap<>();
    public boolean chronicleGiven;
    public long nextRepairTick;
    /** Mara's letters as you received them (JSON: title, author, pages, day) */
    public final List<String> letters = new ArrayList<>();
    /** your reputation after every story quest, with "#Act" markers where acts begin */
    public final List<String> path = new ArrayList<>();
    /** trait -> the day it happened (the Wardens) */
    public final Map<String, Long> moments = new LinkedHashMap<>();
    /** Watch duties and Gilded Hand contracts done (each kind of work wears down the other's tally) */
    public int watchDone;
    public int handDone;
    public int watchRank;
    public int handRank;
    public boolean fatesShown;
    public long lastEncounter;

    /** reputation when each story quest was finished (for the margin notes on the map) */
    public final Map<String, Integer> doneRep = new LinkedHashMap<>();
    /** the day the story ended (for the end card) */
    public long endDay;
    /** the biggest choices made, as "title|answer|reputation change" */
    public final List<String> bigChoices = new ArrayList<>();
    /** Gilded Hand contracts done, ever (the Trial's witnesses count them) */
    public int contractsDone;

    /** dreams had, as "id|day" */
    public final List<String> dreams = new ArrayList<>();
    /** the night (day number) of the last dream */
    public long lastDreamNight = -10;

    /** the hidden questlines: how close each one is */
    public int shrinePrayers;
    public int pilgrimsFed;
    public boolean cleanChecked;

    /** the Trial: 0 free, 1 on trial, 2 in prison */
    public int trialState;
    public boolean pendingKidnap;
    /** acts in which the Watch has already come for you */
    public final Set<String> trialActs = new LinkedHashSet<>();
    public int convictions;
    public int sentence;
    public int served;
    public boolean brokenOut;
    /** the current trial's script (JSON), its verdict once given (JSON), and every trial so far (JSON) */
    public String trialScript = "";
    public String trialVerdict = "";
    public final List<String> trials = new ArrayList<>();
    /** what the Watch took (inventory slots) */
    public ListTag evidence = new ListTag();
    /** where you'd respawn before they took you */
    public boolean hadSpawn;
    public String spawnDim = "";
    public long spawnPos;
    public float spawnAngle;
    public boolean spawnForced;
    /** ticks of the current day already spent in the cell */
    public int servedTicks;

    /** Watch duties and Hand contracts done while a rival was on the other side */
    public int rivalMarks;

    /** operators' story simulator: made-up story traits laid over the real ones (Mebahel never sees them), the
     *  decision made at each simulated quest (spare/kill), the quest you'd be on next, and your real record to go back to */
    public final Set<String> simTraits = new LinkedHashSet<>();
    public final Map<String, String> simDecisions = new LinkedHashMap<>();
    public String simNext = "";
    public String simLabel = "";
    public CompoundTag simBackup = new CompoundTag();

    public static CompoundTag save(RepData d, HolderLookup.Provider provider) {
        return new Serializer().write(d, provider);
    }

    public static RepData load(CompoundTag tag, HolderLookup.Provider provider) {
        return new Serializer().read(null, tag, provider);
    }

    public void addLooted(long pos) {
        looted.add(pos);
        while (looted.size() > 512) {
            looted.remove(looted.iterator().next());
        }
    }

    private static final class Serializer implements IAttachmentSerializer<CompoundTag, RepData> {
        @Override
        public RepData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            RepData d = new RepData();
            d.value = tag.getDouble("value");
            d.initialised = tag.getBoolean("init");
            d.chronicleGiven = tag.getBoolean("chronicleGiven");
            for (Tag t : tag.getList("known", Tag.TAG_STRING)) d.knownTraits.add(t.getAsString());
            for (Tag t : tag.getList("counted", Tag.TAG_STRING)) d.countedChoices.add(t.getAsString());
            for (Tag t : tag.getList("chronicle", Tag.TAG_STRING)) d.chronicle.add(t.getAsString());
            for (long l : tag.getLongArray("looted")) d.looted.add(l);
            CompoundTag acts = tag.getCompound("acts");
            for (String k : acts.getAllKeys()) d.actRep.put(k, acts.getInt(k));
            for (Tag t : tag.getList("letters", Tag.TAG_STRING)) d.letters.add(t.getAsString());
            for (Tag t : tag.getList("path", Tag.TAG_STRING)) d.path.add(t.getAsString());
            CompoundTag moments = tag.getCompound("moments");
            for (String k : moments.getAllKeys()) d.moments.put(k, moments.getLong(k));
            d.watchDone = tag.getInt("watchDone");
            d.handDone = tag.getInt("handDone");
            d.watchRank = tag.getInt("watchRank");
            d.handRank = tag.getInt("handRank");
            d.fatesShown = tag.getBoolean("fatesShown");
            d.lastEncounter = tag.getLong("lastEncounter");
            CompoundTag doneRep = tag.getCompound("doneRep");
            for (String k : doneRep.getAllKeys()) d.doneRep.put(k, doneRep.getInt(k));
            d.endDay = tag.getLong("endDay");
            for (Tag t : tag.getList("bigChoices", Tag.TAG_STRING)) d.bigChoices.add(t.getAsString());
            d.contractsDone = tag.getInt("contractsDone");
            for (Tag t : tag.getList("dreams", Tag.TAG_STRING)) d.dreams.add(t.getAsString());
            d.lastDreamNight = tag.contains("lastDreamNight") ? tag.getLong("lastDreamNight") : -10;
            d.shrinePrayers = tag.getInt("shrinePrayers");
            d.pilgrimsFed = tag.getInt("pilgrimsFed");
            d.cleanChecked = tag.getBoolean("cleanChecked");
            d.trialState = tag.getInt("trialState");
            d.pendingKidnap = tag.getBoolean("pendingKidnap");
            for (Tag t : tag.getList("trialActs", Tag.TAG_STRING)) d.trialActs.add(t.getAsString());
            d.convictions = tag.getInt("convictions");
            d.sentence = tag.getInt("sentence");
            d.served = tag.getInt("served");
            d.brokenOut = tag.getBoolean("brokenOut");
            d.trialScript = tag.getString("trialScript");
            d.trialVerdict = tag.getString("trialVerdict");
            for (Tag t : tag.getList("trials", Tag.TAG_STRING)) d.trials.add(t.getAsString());
            d.evidence = tag.getList("evidence", Tag.TAG_COMPOUND);
            d.hadSpawn = tag.getBoolean("hadSpawn");
            d.spawnDim = tag.getString("spawnDim");
            d.spawnPos = tag.getLong("spawnPos");
            d.spawnAngle = tag.getFloat("spawnAngle");
            d.spawnForced = tag.getBoolean("spawnForced");
            d.servedTicks = tag.getInt("servedTicks");
            d.rivalMarks = tag.getInt("rivalMarks");
            for (Tag t : tag.getList("simTraits", Tag.TAG_STRING)) d.simTraits.add(t.getAsString());
            CompoundTag dec = tag.getCompound("simDecisions");
            for (String k : dec.getAllKeys()) d.simDecisions.put(k, dec.getString(k));
            d.simNext = tag.getString("simNext");
            d.simLabel = tag.getString("simLabel");
            d.simBackup = tag.getCompound("simBackup");
            return d;
        }

        @Override
        public CompoundTag write(RepData d, HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("value", d.value);
            tag.putBoolean("init", d.initialised);
            tag.putBoolean("chronicleGiven", d.chronicleGiven);
            tag.put("known", strings(d.knownTraits));
            tag.put("counted", strings(d.countedChoices));
            tag.put("chronicle", strings(d.chronicle));
            tag.putLongArray("looted", d.looted.stream().mapToLong(Long::longValue).toArray());
            CompoundTag acts = new CompoundTag();
            d.actRep.forEach(acts::putInt);
            tag.put("acts", acts);
            tag.put("letters", strings(d.letters));
            tag.put("path", strings(d.path));
            CompoundTag moments = new CompoundTag();
            d.moments.forEach(moments::putLong);
            tag.put("moments", moments);
            tag.putInt("watchDone", d.watchDone);
            tag.putInt("handDone", d.handDone);
            tag.putInt("watchRank", d.watchRank);
            tag.putInt("handRank", d.handRank);
            tag.putBoolean("fatesShown", d.fatesShown);
            tag.putLong("lastEncounter", d.lastEncounter);
            CompoundTag doneRep = new CompoundTag();
            d.doneRep.forEach(doneRep::putInt);
            tag.put("doneRep", doneRep);
            tag.putLong("endDay", d.endDay);
            tag.put("bigChoices", strings(d.bigChoices));
            tag.putInt("contractsDone", d.contractsDone);
            tag.put("dreams", strings(d.dreams));
            tag.putLong("lastDreamNight", d.lastDreamNight);
            tag.putInt("shrinePrayers", d.shrinePrayers);
            tag.putInt("pilgrimsFed", d.pilgrimsFed);
            tag.putBoolean("cleanChecked", d.cleanChecked);
            tag.putInt("trialState", d.trialState);
            tag.putBoolean("pendingKidnap", d.pendingKidnap);
            tag.put("trialActs", strings(d.trialActs));
            tag.putInt("convictions", d.convictions);
            tag.putInt("sentence", d.sentence);
            tag.putInt("served", d.served);
            tag.putBoolean("brokenOut", d.brokenOut);
            tag.putString("trialScript", d.trialScript);
            tag.putString("trialVerdict", d.trialVerdict);
            tag.put("trials", strings(d.trials));
            tag.put("evidence", d.evidence.copy());
            tag.putBoolean("hadSpawn", d.hadSpawn);
            tag.putString("spawnDim", d.spawnDim);
            tag.putLong("spawnPos", d.spawnPos);
            tag.putFloat("spawnAngle", d.spawnAngle);
            tag.putBoolean("spawnForced", d.spawnForced);
            tag.putInt("servedTicks", d.servedTicks);
            tag.putInt("rivalMarks", d.rivalMarks);
            tag.put("simTraits", strings(d.simTraits));
            CompoundTag dec = new CompoundTag();
            d.simDecisions.forEach(dec::putString);
            tag.put("simDecisions", dec);
            tag.putString("simNext", d.simNext);
            tag.putString("simLabel", d.simLabel);
            tag.put("simBackup", d.simBackup.copy());
            return tag;
        }

        private static ListTag strings(Iterable<String> values) {
            ListTag list = new ListTag();
            for (String s : values) list.add(StringTag.valueOf(s));
            return list;
        }
    }
}

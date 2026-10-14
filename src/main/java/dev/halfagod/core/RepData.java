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

    /** the trial's answers to each charge (JSON: line index -> deny/explain/silent), and what you've confessed to a priest */
    public String trialAnswers = "";
    public final Set<String> confessed = new LinkedHashSet<>();
    public long lastConfession = -1;
    /** other players who saw what you did: "uuid|name|what|day" (what: villager, stores) */
    public final List<String> sightings = new ArrayList<>();

    /** the prison day: stone broken today, days without the quota, fed today, today's visitor seen */
    public int labour;
    public int strikes;
    public boolean fed;
    public boolean visitorDone;
    public int rumour;
    public final Set<String> hintsGiven = new LinkedHashSet<>();
    /** the escape: Maddock's plan heard, his trust earned (the rounds), the chisel, the hole, and escapes ever */
    public boolean planKnown;
    public boolean knowsRounds;
    public boolean hasChisel;
    public int digProgress;
    public int escapes;
    /** sentence served: the gate is open and you're walking out */
    public boolean walkingOut;

    /** interrogations done (by person), villages that have already heard of you (meeting points), a price on your head */
    public final Set<String> interrogated = new LinkedHashSet<>();
    public final Set<Long> villagesHeard = new LinkedHashSet<>();
    public String bountyBy = "";
    public final Map<String, Long> bountiesPlaced = new LinkedHashMap<>();

    /** Watch duties and Hand contracts done while a rival was on the other side */
    public int rivalMarks;

    /** the cult's work: villagers brought to "the faith", and cult quotas worked (the court hears both) */
    public int conversions;
    public int cultWork;
    /** the Watch's enforcers: times you beat them off, the night they next come, and whether this trial began with a fight */
    public int resisted;
    public long arrestNight = -1;
    public boolean foughtArrest;
    /** street dealing (the Green Trade): sales made, and how much the Watch has noticed */
    public int dealt;
    public int heat;
    /** the moon: the Eclipse brought home, or Iskar laid to rest */
    public boolean eclipseHome;
    /** died carrying the Eclipse before bringing it home (Iskar's hand has it again) */
    public boolean eclipseLost;
    /** the story's letters and found books already kept in the Codex (quest id, or quest id # stage for a found book) */
    public final Set<String> booksKept = new LinkedHashSet<>();
    public boolean booksBackfilled;

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
            d.conversions = tag.contains("conversions") ? tag.getInt("conversions") : d.conversions;
            d.cultWork = tag.contains("cultWork") ? tag.getInt("cultWork") : d.cultWork;
            d.resisted = tag.contains("resisted") ? tag.getInt("resisted") : d.resisted;
            d.arrestNight = tag.contains("arrestNight") ? tag.getLong("arrestNight") : d.arrestNight;
            d.foughtArrest = tag.contains("foughtArrest") ? tag.getBoolean("foughtArrest") : d.foughtArrest;
            d.dealt = tag.contains("dealt") ? tag.getInt("dealt") : d.dealt;
            d.heat = tag.contains("heat") ? tag.getInt("heat") : d.heat;
            d.eclipseHome = tag.contains("eclipseHome") ? tag.getBoolean("eclipseHome") : d.eclipseHome;
            d.eclipseLost = tag.contains("eclipseLost") ? tag.getBoolean("eclipseLost") : d.eclipseLost;
            d.booksBackfilled = tag.getBoolean("booksBackfilled");
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
            d.trialAnswers = tag.getString("trialAnswers");
            for (Tag t : tag.getList("confessed", Tag.TAG_STRING)) d.confessed.add(t.getAsString());
            d.lastConfession = tag.contains("lastConfession") ? tag.getLong("lastConfession") : -1;
            for (Tag t : tag.getList("sightings", Tag.TAG_STRING)) d.sightings.add(t.getAsString());
            d.labour = tag.getInt("labour");
            d.strikes = tag.getInt("strikes");
            d.fed = tag.getBoolean("fed");
            d.visitorDone = tag.getBoolean("visitorDone");
            d.rumour = tag.getInt("rumour");
            for (Tag t : tag.getList("hintsGiven", Tag.TAG_STRING)) d.hintsGiven.add(t.getAsString());
            d.planKnown = tag.getBoolean("planKnown");
            d.knowsRounds = tag.getBoolean("knowsRounds");
            d.hasChisel = tag.getBoolean("hasChisel");
            d.digProgress = tag.getInt("digProgress");
            d.escapes = tag.getInt("escapes");
            d.walkingOut = tag.getBoolean("walkingOut");
            for (Tag t : tag.getList("interrogated", Tag.TAG_STRING)) d.interrogated.add(t.getAsString());
            for (Tag t : tag.getList("booksKept", Tag.TAG_STRING)) d.booksKept.add(t.getAsString());
            for (long l : tag.getLongArray("villagesHeard")) d.villagesHeard.add(l);
            d.bountyBy = tag.getString("bountyBy");
            CompoundTag bp = tag.getCompound("bountiesPlaced");
            for (String k : bp.getAllKeys()) d.bountiesPlaced.put(k, bp.getLong(k));
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
            tag.putInt("conversions", d.conversions);
            tag.putInt("cultWork", d.cultWork);
            tag.putInt("resisted", d.resisted);
            tag.putLong("arrestNight", d.arrestNight);
            tag.putBoolean("foughtArrest", d.foughtArrest);
            tag.putInt("dealt", d.dealt);
            tag.putInt("heat", d.heat);
            tag.putBoolean("eclipseHome", d.eclipseHome);
            tag.putBoolean("eclipseLost", d.eclipseLost);
            tag.putBoolean("booksBackfilled", d.booksBackfilled);
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
            tag.putString("trialAnswers", d.trialAnswers);
            tag.put("confessed", strings(d.confessed));
            tag.putLong("lastConfession", d.lastConfession);
            tag.put("sightings", strings(d.sightings));
            tag.putInt("labour", d.labour);
            tag.putInt("strikes", d.strikes);
            tag.putBoolean("fed", d.fed);
            tag.putBoolean("visitorDone", d.visitorDone);
            tag.putInt("rumour", d.rumour);
            tag.put("hintsGiven", strings(d.hintsGiven));
            tag.putBoolean("planKnown", d.planKnown);
            tag.putBoolean("knowsRounds", d.knowsRounds);
            tag.putBoolean("hasChisel", d.hasChisel);
            tag.putInt("digProgress", d.digProgress);
            tag.putInt("escapes", d.escapes);
            tag.putBoolean("walkingOut", d.walkingOut);
            tag.put("interrogated", strings(d.interrogated));
            tag.put("booksKept", strings(d.booksKept));
            tag.putLongArray("villagesHeard", d.villagesHeard.stream().mapToLong(Long::longValue).toArray());
            tag.putString("bountyBy", d.bountyBy);
            CompoundTag bp = new CompoundTag();
            d.bountiesPlaced.forEach(bp::putLong);
            tag.put("bountiesPlaced", bp);
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

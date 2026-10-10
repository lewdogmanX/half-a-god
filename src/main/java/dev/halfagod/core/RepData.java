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
            return tag;
        }

        private static ListTag strings(Iterable<String> values) {
            ListTag list = new ListTag();
            for (String s : values) list.add(StringTag.valueOf(s));
            return list;
        }
    }
}

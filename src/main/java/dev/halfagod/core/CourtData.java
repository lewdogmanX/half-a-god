package dev.halfagod.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * The court's own records (kept in the court dimension): which courtroom and cell each player gets, and which of
 * them have been built. Every player has their own, so two trials can't collide.
 */
public final class CourtData extends SavedData {
    private CompoundTag slots = new CompoundTag();
    private CompoundTag built = new CompoundTag();
    private int next;

    public static CourtData get(ServerLevel court) {
        return court.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CourtData::new, CourtData::load, null), "half_a_god_court");
    }

    private static CourtData load(CompoundTag tag, HolderLookup.Provider lookup) {
        CourtData d = new CourtData();
        d.slots = tag.getCompound("slots");
        d.built = tag.getCompound("built");
        d.next = tag.getInt("next");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.put("slots", slots);
        tag.put("built", built);
        tag.putInt("next", next);
        return tag;
    }

    public int slot(UUID player) {
        String k = player.toString();
        if (!slots.contains(k)) {
            slots.putInt(k, next++);
            setDirty();
        }
        return slots.getInt(k);
    }

    public int builtVersion(int slot) {
        return built.getInt(Integer.toString(slot));
    }

    public void setBuilt(int slot, int version) {
        built.putInt(Integer.toString(slot), version);
        setDirty();
    }
}

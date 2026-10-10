package dev.halfagod.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where each player's story characters live, and which entity currently plays each one.
 * Saved with the world (data/half_a_god_cast.dat) so old copies can be cleaned up when their chunk loads.
 */
public final class CastData extends SavedData {
    public record Place(ResourceLocation dimension, BlockPos pos) {
    }

    private final Map<String, Place> places = new HashMap<>();
    private final Map<String, UUID> current = new HashMap<>();

    public static CastData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CastData::new, CastData::load, null), "half_a_god_cast");
    }

    private static CastData load(CompoundTag tag, HolderLookup.Provider lookup) {
        CastData d = new CastData();
        CompoundTag p = tag.getCompound("places");
        for (String k : p.getAllKeys()) {
            CompoundTag e = p.getCompound(k);
            d.places.put(k, new Place(ResourceLocation.parse(e.getString("dim")), BlockPos.of(e.getLong("pos"))));
        }
        CompoundTag c = tag.getCompound("current");
        for (String k : c.getAllKeys()) {
            d.current.put(k, c.getUUID(k));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        CompoundTag p = new CompoundTag();
        places.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putString("dim", v.dimension().toString());
            e.putLong("pos", v.pos().asLong());
            p.put(k, e);
        });
        tag.put("places", p);
        CompoundTag c = new CompoundTag();
        current.forEach(c::putUUID);
        tag.put("current", c);
        return tag;
    }

    public Place place(UUID player, String place) {
        return places.get(player + "|" + place);
    }

    public void setPlace(UUID player, String place, Place value) {
        places.put(player + "|" + place, value);
        setDirty();
    }

    /** every place this player's story has fixed, by name (home, ashford, char:The Librarian...) */
    public Map<String, Place> placesOf(UUID player) {
        Map<String, Place> out = new HashMap<>();
        String prefix = player + "|";
        places.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                out.put(k.substring(prefix.length()), v);
            }
        });
        return out;
    }

    public UUID current(String castKey) {
        return current.get(castKey);
    }

    public void setCurrent(String castKey, UUID uuid) {
        current.put(castKey, uuid);
        setDirty();
    }
}

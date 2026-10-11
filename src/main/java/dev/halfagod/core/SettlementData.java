package dev.halfagod.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * What the world has become for each player: who moved in, what Ashford looks like and which blocks this mod put
 * there, the notice boards in each village, and when duties were last offered. Saved as data/half_a_god_settlement.dat.
 */
public final class SettlementData extends SavedData {
    private CompoundTag root = new CompoundTag();

    public static SettlementData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementData::new, SettlementData::load, null), "half_a_god_settlement");
    }

    private static SettlementData load(CompoundTag tag, HolderLookup.Provider lookup) {
        SettlementData d = new SettlementData();
        d.root = tag.getCompound("players");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.put("players", root);
        return tag;
    }

    /** This player's record; changes to it must be followed by setDirty(). */
    public CompoundTag player(UUID player) {
        String k = player.toString();
        if (!root.contains(k)) {
            root.put(k, new CompoundTag());
        }
        return root.getCompound(k);
    }

    /** Every player with a record. */
    public java.util.List<UUID> playerIds() {
        java.util.List<UUID> out = new java.util.ArrayList<>();
        for (String k : root.getAllKeys()) {
            try {
                out.add(UUID.fromString(k));
            } catch (IllegalArgumentException ignored) {
                // not a player
            }
        }
        return out;
    }

    /** A named section of this player's record (created if missing). */
    public CompoundTag section(UUID player, String name) {
        CompoundTag p = player(player);
        if (!p.contains(name)) {
            p.put(name, new CompoundTag());
        }
        return p.getCompound(name);
    }
}

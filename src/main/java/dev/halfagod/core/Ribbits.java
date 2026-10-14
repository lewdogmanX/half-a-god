package dev.halfagod.core;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The Ribbits' Song (the Ribbits mod): walking into a Ribbit village starts it. */
public final class Ribbits {
    private Ribbits() {
    }

    private static JsonObject t() {
        return StoryData.INSTANCE.table("ribbits");
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 31 || p.isSpectator()
                || !ModList.get().isLoaded("ribbits") || !ModList.get().isLoaded("mebahelrpgrquest")
                || !(p.level() instanceof ServerLevel level) || Trial.inCourt(p) || !t().has("start")) {
            return;
        }
        String start = t().get("start").getAsString();
        if (Meb.hasTrait(p, start.substring(start.indexOf(':') + 1) + "_done")
                || Meb.quests(p).stream().anyMatch(q -> q.questId().equals(start) && !q.failed())) {
            return;
        }
        Structure village = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(t().get("structure").getAsString()));
        if (village != null && level.structureManager().getStructureWithPieceAt(p.blockPosition(), village).isValid()) {
            Meb.startQuest(p, ResourceLocation.parse(start), false);
        }
    }
}

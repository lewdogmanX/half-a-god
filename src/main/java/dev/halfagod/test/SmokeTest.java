package dev.halfagod.test;

import dev.halfagod.HalfAGod;
import dev.halfagod.core.CourtBuilder;
import dev.halfagod.core.StoryData;
import dev.halfagod.core.Trial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * CI only (-Dhalfagod.smoketest=true): starts a real dedicated server, checks the court dimension loaded from the story
 * pack and can be built in, prints the result and stops. Run twice, it checks a brand-new world and an existing one.
 */
public final class SmokeTest {
    private SmokeTest() {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        StringBuilder out = new StringBuilder("HAG-SMOKE");
        ServerLevel court = server.getLevel(Trial.COURT);
        out.append(" court=").append(court != null ? "loaded" : "MISSING");
        if (court != null) {
            CourtBuilder.ensure(court, 3);
            BlockPos cell = CourtBuilder.origin(3).offset(CourtBuilder.CELL);
            out.append(" cell=").append(court.getBlockState(cell.offset(-2, 0, 4)).is(Blocks.HAY_BLOCK) ? "built" : "BROKEN");
            out.append(" night=").append(court.dimensionType().hasFixedTime() && !court.isDay() ? "yes" : "NO");
        }
        out.append(" dreams=").append(StoryData.INSTANCE.dreams.size());
        out.append(" charges=").append(StoryData.INSTANCE.charges.size());
        out.append(" secrets=").append(StoryData.INSTANCE.secrets.size());
        HalfAGod.LOG.info(out.toString());
        server.halt(false);
    }
}

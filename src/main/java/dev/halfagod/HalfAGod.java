package dev.halfagod;

import com.mojang.logging.LogUtils;
import dev.halfagod.core.Commands;
import dev.halfagod.core.RepData;
import dev.halfagod.core.Reputation;
import dev.halfagod.core.Story;
import dev.halfagod.core.StoryData;
import dev.halfagod.core.StoryGuard;
import dev.halfagod.net.Payloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Half a God: the story datapack (bundled in this jar), plus the reputation meter, story fixes,
 * big choices, cinematic moments and the Chronicle.
 */
@Mod(HalfAGod.MODID)
public final class HalfAGod {
    public static final String MODID = "half_a_god";
    public static final Logger LOG = LogUtils.getLogger();

    public HalfAGod(IEventBus modBus, ModContainer container) {
        RepData.ATTACHMENTS.register(modBus);
        modBus.addListener(Payloads::register);

        NeoForge.EVENT_BUS.register(Reputation.class);
        NeoForge.EVENT_BUS.register(Story.class);
        NeoForge.EVENT_BUS.register(StoryGuard.class);
        NeoForge.EVENT_BUS.register(Commands.class);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(StoryData.INSTANCE));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, dev.halfagod.client.ClientConfig.SPEC);
            dev.halfagod.client.ClientSetup.init(modBus);
        }
    }
}

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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
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
        dev.halfagod.item.ModItems.register(modBus);
        modBus.addListener(Payloads::register);
        // The story datapack lives in its own always-on pack at the top of the list, so its versions of
        // Mebahel's quests win over the originals no matter what order the mods load in.
        modBus.addListener((AddPackFindersEvent e) -> {
            if (e.getPackType() == PackType.SERVER_DATA) {
                e.addPackFinders(ResourceLocation.fromNamespaceAndPath(MODID, "story"), PackType.SERVER_DATA,
                        Component.literal("Half a God story"), PackSource.BUILT_IN, true, Pack.Position.TOP);
            }
            // the paper-and-ink skin for Mebahel's quest board and contract notes (always on, on top)
            if (e.getPackType() == PackType.CLIENT_RESOURCES) {
                e.addPackFinders(ResourceLocation.fromNamespaceAndPath(MODID, "skin"), PackType.CLIENT_RESOURCES,
                        Component.literal("Half a God: paper and ink"), PackSource.BUILT_IN, true, Pack.Position.TOP);
            }
        });

        NeoForge.EVENT_BUS.register(Reputation.class);
        NeoForge.EVENT_BUS.register(Story.class);
        NeoForge.EVENT_BUS.register(StoryGuard.class);
        NeoForge.EVENT_BUS.register(Commands.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Cast.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Atlas.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Morality.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Settlement.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Encounters.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Dreams.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Trial.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Secrets.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Rivals.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Words.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Azazel.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Arrest.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Legends.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Moon.class);
        NeoForge.EVENT_BUS.register(dev.halfagod.core.Ribbits.class);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(StoryData.INSTANCE));
        if (Boolean.getBoolean("halfagod.smoketest")) {
            NeoForge.EVENT_BUS.register(dev.halfagod.test.SmokeTest.class);
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, dev.halfagod.client.ClientConfig.SPEC);
            dev.halfagod.client.ClientSetup.init(modBus);
        }
    }
}

package dev.halfagod.client;

import dev.halfagod.HalfAGod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "reputation"), RepHud::render));
        NeoForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.BossEventProgress e) -> RepHud.onBossBar(e));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            RepHud.tick();
            CameraShake.tick();
        });
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeCameraAngles e) -> CameraShake.apply(e));
        if (Boolean.getBoolean("halfagod.clientshots")) {
            ClientShots.init();
            NeoForge.EVENT_BUS.addListener(ClientShots::tick);
        }
        // Mebahel's quest map key opens the story map; the old map is gone (Mebahel's Quest Journal is untouched)
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e) -> {
            if (e.getNewScreen() != null
                    && e.getNewScreen().getClass().getName().equals("net.mebahelrpgrquest.client.gui.QuestMapScreen")) {
                e.setNewScreen(new StoryMapScreen(e.getCurrentScreen()));
            }
        });
    }
}

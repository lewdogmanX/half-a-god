package dev.halfagod.client;

import dev.halfagod.HalfAGod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
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
    }
}

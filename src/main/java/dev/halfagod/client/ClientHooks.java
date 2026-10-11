package dev.halfagod.client;

import dev.halfagod.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Packet handlers. Only ever loaded on the client. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void onRep(Payloads.RepSync p) {
        RepHud.set(p.value10() / 10.0, p.delta10() / 10.0);
    }

    public static void onOpenStory(Payloads.OpenStory p) {
        Minecraft.getInstance().setScreen(new StoryScreen(p.op()));
    }

    public static void onOpenChoice(Payloads.OpenChoice p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ChoiceScreen) {
            return;
        }
        mc.setScreen(new ChoiceScreen(p.json()));
    }

    public static void onMapState(Payloads.MapState p) {
        StoryMapScreen.receive(p.json());
    }

    public static void onOpenMap(Payloads.OpenMap p) {
        StoryMapScreen screen = new StoryMapScreen(null);
        if (p.fates()) {
            screen.openFates();
        }
        Minecraft.getInstance().setScreen(screen);
    }

    public static void onCinematic(Payloads.Cinematic p) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(12, 80, 24);
        if (!p.subtitle().isEmpty()) {
            mc.gui.setSubtitle(Component.literal(p.subtitle()).withColor(0xE8E0D0));
        }
        mc.gui.setTitle(Component.literal(p.title()).withColor(p.color()));
        ResourceLocation id = ResourceLocation.tryParse(p.sound());
        if (id != null) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(id);
            if (sound != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, 0.9f));
            }
        }
        if (p.shakeTicks() > 0 && ClientConfig.SCREEN_SHAKE.get()) {
            CameraShake.start(p.shakeTicks(), p.shake());
        }
    }
}

package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ring a village bell to travel. The Good walk the lantern road for free; the Fallen and Wicked pay the Gilded Hand's
 * smugglers. The Wavering have neither. Destinations are the villages your story has taken you to.
 */
public final class Travel {
    public static final int SMUGGLER_FEE = 8;
    private static final Map<UUID, List<CastData.Place>> OFFERED = new HashMap<>();

    private Travel() {
    }

    private static String nameOf(String key) {
        return switch (key) {
            case "home" -> "Your home village";
            case "ashford" -> "Ashford";
            case "snowy_village" -> "The snowy village";
            case "desert_village" -> "The desert village";
            case "buyers_village" -> "The valley village";
            default -> key.startsWith("char:") ? key.substring(5) + "'s village" : key;
        };
    }

    @SubscribeEvent
    public static void onBell(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL)) {
            return;
        }
        int t = Morality.tier(player);
        if (t == 2 || !Morality.inVillage(player.serverLevel(), event.getPos())) {
            return;
        }
        open(player);
    }

    public static void open(ServerPlayer player) {
        int t = Morality.tier(player);
        boolean evil = t <= 1;
        if (t == 2) {
            player.displayClientMessage(Component.literal("Nobody will guide you yet. Earn a name, good or bad.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Map<String, CastData.Place> places = CastData.get(player.getServer()).placesOf(player.getUUID());
        List<CastData.Place> list = new ArrayList<>();
        JsonArray dests = new JsonArray();
        places.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            CastData.Place p = e.getValue();
            if (!p.dimension().equals(player.level().dimension().location()) || p.pos().closerThan(player.blockPosition(), 96)) {
                return;
            }
            JsonObject o = new JsonObject();
            o.addProperty("name", nameOf(e.getKey()));
            o.addProperty("distance", (int) Math.sqrt(p.pos().distSqr(player.blockPosition())));
            dests.add(o);
            list.add(p);
        });
        OFFERED.put(player.getUUID(), list);
        JsonObject root = new JsonObject();
        root.addProperty("title", evil ? "The Smugglers' Tunnels" : "The Lantern Road");
        root.addProperty("text", evil ? "A man in a grey hood names his price: " + SMUGGLER_FEE + " emeralds, wherever you're going. No questions."
                : "The Watch keeps the road lit between the villages you've helped. Where to?");
        root.addProperty("cost", evil ? SMUGGLER_FEE : 0);
        root.add("destinations", dests);
        PacketDistributor.sendToPlayer(player, new Payloads.OpenTravel(root.toString()));
    }

    public static void pick(ServerPlayer player, int index) {
        List<CastData.Place> list = OFFERED.remove(player.getUUID());
        if (list == null || index < 0 || index >= list.size()) {
            return;
        }
        int t = Morality.tier(player);
        if (t == 2) {
            return;
        }
        if (t <= 1) {
            if (player.getInventory().countItem(Items.EMERALD) < SMUGGLER_FEE) {
                player.displayClientMessage(Component.literal("The smuggler laughs. \"Come back with " + SMUGGLER_FEE + " emeralds.\"").withStyle(ChatFormatting.RED), true);
                return;
            }
            int left = SMUGGLER_FEE;
            for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
                var stack = player.getInventory().getItem(i);
                if (stack.is(Items.EMERALD)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        CastData.Place dest = list.get(index);
        ServerLevel level = player.serverLevel();
        BlockPos c = dest.pos();
        level.getChunk(c.getX() >> 4, c.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + 3, c.getZ() + 3);
        player.teleportTo(level, c.getX() + 3.5, y, c.getZ() + 3.5, player.getYRot(), player.getXRot());
        level.playSound(null, player.blockPosition(), t <= 1 ? SoundEvents.CHEST_CLOSE : SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1f);
    }
}

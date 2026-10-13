package dev.halfagod.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Green Trade, as the rest of the world sees it: every hand-to-hand sale in the story counts (the court hears how
 * many), and when the Fixer starts talking to the Watch, its enforcers come for you that very night.
 */
public final class Trade {
    private Trade() {
    }

    private static boolean in(JsonObject t, String list, String trait) {
        if (!t.has(list)) {
            return false;
        }
        for (JsonElement e : t.getAsJsonArray(list)) {
            if (e.getAsString().equals(trait)) {
                return true;
            }
        }
        return false;
    }

    public static void onTrait(ServerPlayer player, String trait) {
        JsonObject t = StoryData.INSTANCE.table("trade");
        RepData d = Reputation.data(player);
        if (in(t, "sales", trait)) {
            d.dealt++;
        }
        if (in(t, "watch_hears", trait) && d.trialState == 0) {
            d.arrestNight = player.getServer().overworld().getDayTime() / 24000L;
            if (t.has("watch_line")) {
                player.displayClientMessage(Component.literal(t.get("watch_line").getAsString()).withStyle(ChatFormatting.GOLD), true);
            }
        }
    }
}

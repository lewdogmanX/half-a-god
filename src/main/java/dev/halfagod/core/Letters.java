package dev.halfagod.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Old Mara's letters at the end of each act. She writes differently depending on who you've become:
 * warm to the Good, careful with the Wavering, frightened of the Fallen. After the truth comes out she signs
 * as Maraal, and if you rejected her the letters simply stop.
 */
public final class Letters {
    private Letters() {
    }

    /** bright (Good, Saint), grey (Wavering) or dark (Fallen, Wicked) */
    public static String tone(double rep) {
        if (rep >= 20) {
            return "bright";
        }
        return rep > -20 ? "grey" : "dark";
    }

    public static void onTrait(ServerPlayer player, String trait) {
        for (JsonObject l : StoryData.INSTANCE.letters) {
            if (!l.get("trigger").getAsString().equals(trait)) {
                continue;
            }
            if (l.has("forbids")) {
                boolean blocked = false;
                for (JsonElement t : l.getAsJsonArray("forbids")) {
                    blocked |= Meb.hasTrait(player, t.getAsString());
                }
                if (blocked) {
                    continue;
                }
            }
            JsonObject letter = l.getAsJsonObject(tone(Reputation.data(player).value));
            if (letter == null) {
                continue;
            }
            String author = Meb.hasTrait(player, "maraal_forgiven") ? "Maraal" : l.has("author") ? l.get("author").getAsString() : "Old Mara";
            give(player, letter.get("title").getAsString(), author, letter.getAsJsonArray("pages"));
            // kept for the Codex, to read again
            JsonObject kept = new JsonObject();
            kept.addProperty("title", letter.get("title").getAsString());
            kept.addProperty("author", author);
            kept.add("pages", letter.getAsJsonArray("pages"));
            kept.addProperty("day", player.level().getDayTime() / 24000L + 1);
            Reputation.data(player).letters.add(kept.toString());
        }
    }

    private static void give(ServerPlayer player, String title, String author, Iterable<JsonElement> pages) {
        List<Filterable<Component>> out = new ArrayList<>();
        for (JsonElement p : pages) {
            out.add(Filterable.passThrough(Component.literal(p.getAsString())));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, out, true));
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        player.displayClientMessage(Component.literal("A letter from " + author).withStyle(ChatFormatting.GOLD), true);
    }
}

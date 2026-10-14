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
            com.google.gson.JsonArray pages = letter.getAsJsonArray("pages").deepCopy();
            // on a server with friends, Mara gossips about them
            String ps = Rivalry.postscript(player);
            if (ps != null) {
                pages.add(ps);
            }
            give(player, letter.get("title").getAsString(), author, pages);
            // kept for the Codex, to read again
            JsonObject kept = new JsonObject();
            kept.addProperty("title", letter.get("title").getAsString());
            kept.addProperty("author", author);
            kept.add("pages", pages);
            kept.addProperty("day", player.level().getDayTime() / 24000L + 1);
            Reputation.data(player).letters.add(kept.toString());
        }
    }

    /**
     * Every story letter a courier brings you, and every book you find on a quest, kept in the Codex to read again.
     * Letters are kept when their quest starts (that's when the letter arrives); a found book once you're past the step
     * where you find it. The first time this runs on an old save, it keeps everything you'd already had, oldest first.
     */
    public static void keepBooks(ServerPlayer player) {
        JsonObject t = StoryData.INSTANCE.table("letter_books");
        if (!t.has("books")) {
            return;
        }
        JsonObject books = t.getAsJsonObject("books");
        RepData d = Reputation.data(player);
        java.util.Map<String, Integer> stageOf = new java.util.HashMap<>();
        for (Object raw : Meb.rawQuests(player)) {
            stageOf.put(Meb.questIdOf(raw), Meb.stageOf(raw));
        }
        List<Meb.Progress> got = new ArrayList<>(Meb.quests(player));
        got.sort(java.util.Comparator.comparingLong(Meb.Progress::acceptedAt));
        List<String> fresh = new ArrayList<>();
        for (Meb.Progress p : got) {
            String q = p.questId();
            if (books.has(q) && !d.booksKept.contains(q) && !fresh.contains(q)) {
                fresh.add(q);
            }
            for (String key : books.keySet()) {
                if (!key.startsWith(q + "#") || d.booksKept.contains(key) || fresh.contains(key)) {
                    continue;
                }
                int stage = Integer.parseInt(key.substring(key.indexOf('#') + 1));
                boolean past = p.claimed() || stageOf.getOrDefault(q, 0) > stage;
                if (past) {
                    fresh.add(key);
                }
            }
        }
        if (fresh.isEmpty()) {
            d.booksBackfilled = true;
            return;
        }
        List<String> entries = new ArrayList<>();
        for (String key : fresh) {
            JsonObject b = books.getAsJsonObject(key);
            JsonObject kept = new JsonObject();
            kept.addProperty("title", b.get("title").getAsString());
            kept.addProperty("author", b.get("author").getAsString());
            kept.add("pages", b.getAsJsonArray("pages").deepCopy());
            if (b.has("found")) {
                kept.addProperty("found", true);
            }
            if (d.booksBackfilled) {
                kept.addProperty("day", player.level().getDayTime() / 24000L + 1);   // (before this, nobody wrote the day down)
            }
            entries.add(kept.toString());
            d.booksKept.add(key);
        }
        if (d.booksBackfilled) {
            d.letters.addAll(entries);
        } else {
            d.letters.addAll(0, entries);   // the ones you had before the Codex kept them: oldest, so first
            d.booksBackfilled = true;
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

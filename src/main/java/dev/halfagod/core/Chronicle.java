package dev.halfagod.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/** The Chronicle: a book written from the player's own playthrough. */
public final class Chronicle {
    private static final int LINES_PER_PAGE = 11;

    private Chronicle() {
    }

    public static void give(ServerPlayer player, boolean ending) {
        RepData d = Reputation.data(player);
        List<Filterable<Component>> pages = new ArrayList<>();
        String name = player.getGameProfile().getName();

        MutableComponent cover = Component.literal("The Chronicle\nof " + name + "\n\n").withStyle(ChatFormatting.BOLD);
        cover.append(Component.literal(ending ? "Written when the story ended.\n\n" : "The story so far.\n\n").withStyle(ChatFormatting.RESET, ChatFormatting.ITALIC));
        int tier = Reputation.tierIndex(d.value);
        cover.append(Component.literal("Remembered as: ").withStyle(ChatFormatting.RESET))
                .append(Component.literal(Reputation.TIERS[tier]).withStyle(Reputation.color(tier), ChatFormatting.BOLD));
        cover.append(Component.literal("\nReputation: " + Math.round(d.value)).withStyle(ChatFormatting.RESET, ChatFormatting.DARK_GRAY));
        pages.add(Filterable.passThrough(cover));

        MutableComponent page = Component.empty();
        int lines = 0;
        for (String entry : d.chronicle) {
            boolean heading = entry.startsWith("#");
            String text = heading ? entry.substring(1) : entry;
            int cost = heading ? 3 : 1 + text.length() / 19;
            if (lines + cost > LINES_PER_PAGE && lines > 0) {
                pages.add(Filterable.passThrough(page));
                page = Component.empty();
                lines = 0;
            }
            if (heading) {
                Integer rep = d.actRep.get(text);
                page.append(Component.literal(text + "\n").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED));
                if (rep != null) {
                    int t = Reputation.tierIndex(rep);
                    page.append(Component.literal("You were " + Reputation.TIERS[t] + "\n\n").withStyle(ChatFormatting.ITALIC, Reputation.color(t)));
                } else {
                    page.append(Component.literal("\n"));
                }
            } else {
                page.append(Component.literal("- " + text + "\n").withStyle(ChatFormatting.RESET, ChatFormatting.BLACK));
            }
            lines += cost;
        }
        if (lines > 0) {
            pages.add(Filterable.passThrough(page));
        }
        if (pages.size() > 100) {
            pages = pages.subList(0, 100);
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("The Chronicle of " + (name.length() > 14 ? name.substring(0, 14) : name)), "Old Mara", 0, pages, true));
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        if (ending) {
            d.chronicleGiven = true;
        }
        player.displayClientMessage(Component.literal("The Chronicle of your story is in your pack.").withStyle(ChatFormatting.GOLD), true);
    }
}

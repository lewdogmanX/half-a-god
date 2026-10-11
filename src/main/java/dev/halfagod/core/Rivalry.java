package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * A light rivalry between players on one server. Everyone plays their own story; you can see how far along your
 * friends are and who they've become, Mara's letters gossip about them, and when one of you works for the Watch while
 * the other is on the Hand's side (or the other way round), it's noted, with a little extra pay. Nothing forces a fight.
 */
public final class Rivalry {
    private Rivalry() {
    }

    /** Good (Saint, Good) = 1, Wavering = 0, evil (Fallen, Wicked) = -1 */
    private static int side(ServerPlayer p) {
        int t = Reputation.tierIndex(Reputation.data(p).value);
        return t >= 3 ? 1 : t <= 1 ? -1 : 0;
    }

    private static List<ServerPlayer> others(ServerPlayer player) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            if (p != player && Reputation.data(p).initialised) {
                out.add(p);
            }
        }
        return out;
    }

    public static String actName(ServerPlayer p) {
        return StoryData.INSTANCE.acts.getOrDefault(Dreams.actTrait(Meb.traits(p)), "");
    }

    /** For the map: the other players, how far along they are, who they've become. */
    public static JsonArray info(ServerPlayer player) {
        JsonArray out = new JsonArray();
        int mine = side(player);
        for (ServerPlayer p : others(player)) {
            RepData d = Reputation.data(p);
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName().getString());
            String act = actName(p);
            o.addProperty("act", act.contains(":") ? act.substring(0, act.indexOf(':')) : act);
            o.addProperty("tier", Reputation.tier(d.value));
            o.addProperty("watch", Ranks.WATCH[d.watchRank]);
            o.addProperty("hand", Ranks.HAND[d.handRank]);
            o.addProperty("rival", mine != 0 && side(p) == -mine);
            out.add(o);
        }
        return out;
    }

    /** A postscript for one of Mara's letters, about another player (or null if you're alone). */
    public static String postscript(ServerPlayer player) {
        List<ServerPlayer> others = others(player);
        if (others.isEmpty()) {
            return null;
        }
        ServerPlayer p = others.get(player.getRandom().nextInt(others.size()));
        String act = actName(p);
        String where = act.isEmpty() ? "has only just started out" : "has reached " + (act.contains(":") ? act.substring(act.indexOf(':') + 2) : act);
        String[] word = {"something to be afraid of", "dangerous", "hard to read", "a good sort", "a saint, nearly"};
        String text = "P.S. I hear " + p.getName().getString() + " " + where + ". People say they've become "
                + word[Reputation.tierIndex(Reputation.data(p).value)] + ".";
        int a = side(player), b = side(p);
        if (a != 0 && a == -b) {
            text += a > 0 ? "\n\nBe careful of them. They speak of you the same way." : "\n\nThey'll come for you one day, if the Watch lets them.";
        } else if (a != 0 && a == b) {
            text += "\n\nYou could do worse than walk beside them.";
        }
        return text;
    }

    /** A Watch duty or Hand contract done while someone on the other side is online. */
    public static void onJob(ServerPlayer player, boolean watch) {
        int mine = side(player);
        if (mine == 0 || (watch && mine < 0) || (!watch && mine > 0)) {
            return;
        }
        for (ServerPlayer p : others(player)) {
            if (side(p) == -mine) {
                String name = p.getName().getString();
                Component line = watch
                        ? Component.literal("The Watch adds a line to its file on " + name + ". (+2 emeralds)").withStyle(ChatFormatting.AQUA)
                        : Component.literal("Silas grins: \"" + name + " won't like that.\" (+2 emeralds)").withStyle(ChatFormatting.GOLD);
                player.displayClientMessage(line, true);
                ItemStack pay = new ItemStack(Items.EMERALD, 2);
                if (!player.getInventory().add(pay)) {
                    player.drop(pay, false);
                }
                Reputation.data(player).rivalMarks++;
                return;
            }
        }
    }
}

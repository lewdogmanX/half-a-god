package dev.halfagod.core;

import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ranks in the Watch and in the Gilded Hand, earned with finished duties and contracts. They are meant to be hard:
 * a lot of work (and duties come at most every half a day), the right reputation for the higher ranks, and every job
 * done for the other side wears five off your tally. Fall below a rank's tally and you lose it.
 *
 * Watch:  Watchman (6 duties)  ·  Sergeant (18, Good or better)  ·  Captain (40, Saint)
 * Hand:   Runner (6 contracts)  ·  Collector (18, Fallen or worse)  ·  Hand's Favourite (40, Wicked)
 */
public final class Ranks {
    public static final String[] WATCH = {"", "Watchman", "Sergeant of the Watch", "Captain of the Watch"};
    public static final String[] HAND = {"", "Runner for the Hand", "Collector of the Hand", "The Hand's Favourite"};
    public static final int[] NEED = {0, 6, 18, 40};
    private static final int PENALTY = 5;

    private Ranks() {
    }

    public static int watch(ServerPlayer p) {
        return Reputation.data(p).watchRank;
    }

    public static int hand(ServerPlayer p) {
        return Reputation.data(p).handRank;
    }

    /** the title villagers and the map use, or "" */
    public static String title(ServerPlayer p) {
        RepData d = Reputation.data(p);
        return d.watchRank >= d.handRank ? WATCH[d.watchRank] : HAND[d.handRank];
    }

    private static boolean tierAllows(boolean watch, int rank, int tier) {
        if (rank <= 1) {
            return true;
        }
        if (watch) {
            return rank == 2 ? tier >= 3 : tier >= 4;
        }
        return rank == 2 ? tier <= 1 : tier <= 0;
    }

    /** A duty (watch=true) or contract was handed in. */
    public static void onJobDone(ServerPlayer player, boolean watch) {
        RepData d = Reputation.data(player);
        if (watch) {
            d.watchDone++;
            d.handDone = Math.max(0, d.handDone - PENALTY);
        } else {
            d.handDone++;
            d.watchDone = Math.max(0, d.watchDone - PENALTY);
        }
        // the rank's bonus pay
        int rank = watch ? d.watchRank : d.handRank;
        int bonus = watch ? new int[]{0, 2, 4, 6}[rank] : new int[]{0, 3, 6, 10}[rank];
        if (bonus > 0) {
            ItemStack pay = new ItemStack(Items.EMERALD, bonus);
            if (!player.getInventory().add(pay)) {
                player.drop(pay, false);
            }
        }
        update(player);
    }

    /** Promotions and demotions (also checked as your reputation moves). */
    public static void update(ServerPlayer player) {
        RepData d = Reputation.data(player);
        int tier = Morality.tier(player);
        d.watchRank = settle(player, true, d.watchRank, d.watchDone, tier);
        d.handRank = settle(player, false, d.handRank, d.handDone, tier);
    }

    private static int settle(ServerPlayer player, boolean watch, int rank, int done, int tier) {
        int r = rank;
        while (r > 0 && done < NEED[r]) {
            r--;
        }
        if (r < rank) {
            player.displayClientMessage(Component.literal(watch ? "The Watch has struck your name from its rolls. You are " + (r == 0 ? "no longer one of them." : WATCH[r] + " again.")
                    : "The Gilded Hand has stopped returning your notes. You are " + (r == 0 ? "nobody to them now." : HAND[r] + " again."))
                    .withStyle(ChatFormatting.GRAY), true);
            return r;
        }
        while (r < 3 && done >= NEED[r + 1] && tierAllows(watch, r + 1, tier)) {
            r++;
        }
        if (r > rank) {
            String name = watch ? WATCH[r] : HAND[r];
            String sub = watch ? new String[]{"", "The Watch knows your name", "They salute you at the gate", "Every Watchman in the land answers to you"}[r]
                    : new String[]{"", "Silas has your name in his ledger", "You collect for the Hand now", "Silas calls you his friend. Be afraid."}[r];
            PacketDistributor.sendToPlayer(player, new Payloads.Cinematic(name, sub, watch ? 0x7FB8F0 : 0xE0A93A,
                    watch ? "minecraft:block.bell.use" : "minecraft:entity.villager.celebrate", 0, 0f));
        }
        return r;
    }

    /** "Duties done: 12. Sergeant at 18 (Good or better)." for the Codex */
    public static String progress(ServerPlayer player, boolean watch) {
        RepData d = Reputation.data(player);
        int rank = watch ? d.watchRank : d.handRank;
        int done = watch ? d.watchDone : d.handDone;
        String[] names = watch ? WATCH : HAND;
        if (rank >= 3) {
            return (watch ? "Duties" : "Contracts") + " done: " + done + ". The highest rank there is.";
        }
        String need = rank + 1 >= 2 ? (watch ? (rank + 1 == 2 ? " (and Good or better)" : " (and a Saint)") : (rank + 1 == 2 ? " (and Fallen or worse)" : " (and Wicked)")) : "";
        return (watch ? "Duties" : "Contracts") + " done: " + done + ". " + names[rank + 1] + " at " + NEED[rank + 1] + need + ".";
    }
}

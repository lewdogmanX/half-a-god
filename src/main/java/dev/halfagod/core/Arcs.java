package dev.halfagod.core;

import net.minecraft.server.level.ServerPlayer;

/** Story beats that depend on your reputation rather than on a single quest. */
public final class Arcs {
    private static final String GT = "mebahelrpgrquest:hag_gt";

    private Arcs() {
    }

    public static void tick(ServerPlayer player) {
        wayOut(player);
    }

    /** Deep in the Green Trade but climbing back to Good: Reeve Halden writes with a way out. */
    private static void wayOut(ServerPlayer player) {
        if (Reputation.data(player).value < 20 || Meb.hasTrait(player, "hag_act5_arc_done")
                || Meb.hasTrait(player, "hag_way_out_sent") || Meb.hasTrait(player, "gt_getting_out")) {
            return;
        }
        boolean inTrade = false;
        for (Meb.Progress p : Meb.quests(player)) {
            if (p.questId().startsWith(GT) && !p.failed()) {
                inTrade = true;
                break;
            }
        }
        if (inTrade) {
            Meb.addTrait(player, "hag_way_out_sent");
            Meb.triggerCourier(player, "half_a_god:gt_way_out", player.getUUID().toString());
        }
    }
}

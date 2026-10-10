package dev.halfagod.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/** Story beats that depend on your reputation rather than on a single quest. */
public final class Arcs {
    private static final String GT = "mebahelrpgrquest:hag_gt";

    private Arcs() {
    }

    public static void tick(ServerPlayer player) {
        wayOut(player);
        companionMoods(player);
    }

    /** Dunn, Corwin and Ansel talk to you differently depending on who you've become. */
    private static void companionMoods(ServerPlayer player) {
        Map<String, String> profiles = StoryData.INSTANCE.companionProfiles;
        if (profiles.isEmpty()) {
            return;
        }
        String tone = Letters.tone(Reputation.data(player).value);
        for (Entity e : player.level().getEntities(player, new AABB(player.blockPosition()).inflate(32))) {
            if (!e.hasCustomName() || e.getCustomName() == null) {
                continue;
            }
            String base = profiles.get(e.getCustomName().getString());
            if (base == null || !player.getUUID().equals(Meb.companionOwner(e))) {
                continue;
            }
            String want = "half_a_god:" + base + "_" + tone;
            if (!want.equals(Meb.dialogueProfile(e))) {
                Meb.setDialogueProfile(player, e, want);
            }
        }
    }

    public static void onTrait(ServerPlayer player, String trait) {
        if (trait.startsWith("hag_lost_page_")) {
            for (int i = 1; i <= 8; i++) {
                if (!Meb.hasTrait(player, "hag_lost_page_" + i)) {
                    return;
                }
            }
            if (!Meb.hasTrait(player, "hag_first_champion_sent")) {
                Meb.addTrait(player, "hag_first_champion_sent");
                Meb.triggerCourier(player, "half_a_god:first_champion", player.getUUID().toString());
            }
        }
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

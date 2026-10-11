package dev.halfagod.core;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The rivalry, still a light one: put a price on another player's head with the Gilded Hand (their hunters come the
 * next night they're out under the sky), and speak at another player's trial if you saw what they did.
 */
public final class Rivals {
    private Rivals() {
    }

    private static JsonObject words(String section) {
        JsonObject w = StoryData.INSTANCE.table("words");
        return w.has(section) ? w.getAsJsonObject(section) : new JsonObject();
    }

    public static String text(String key) {
        JsonObject b = words("bounty");
        return b.has(key) ? b.get(key).getAsString() : "";
    }

    private static String fill(String s, String name) {
        return s.replace("{name}", name);
    }

    private static long day(ServerPlayer p) {
        return p.serverLevel().getDayTime() / 24000L + 1;
    }

    private static void say(ServerPlayer p, String s, ChatFormatting c) {
        if (!s.isEmpty()) {
            p.displayClientMessage(Component.literal(s).withStyle(c), true);
        }
    }

    // ------------------------------------------------------------------ a price on a rival's head
    /** from the map's list of players: offer the Hand's contract on one of them */
    public static void askBounty(ServerPlayer player, String uuid) {
        ServerPlayer target = online(player, uuid);
        if (target == null || target == player) {
            return;
        }
        String name = target.getName().getString();
        JsonObject b = words("bounty");
        Talk.to(player, "An Agent of the Gilded Hand").title(fill(b.get("title").getAsString(), name))
                .line(fill(b.get("text").getAsString(), name))
                .option("yes", b.get("yes").getAsString(), p -> placeBounty(p, target.getUUID().toString()))
                .option("no", b.get("no").getAsString(), p -> {
                })
                .send();
    }

    private static ServerPlayer online(ServerPlayer player, String uuid) {
        try {
            return player.getServer().getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** pays the Hand: true if the hunters are on their way */
    public static boolean placeBounty(ServerPlayer player, String uuid) {
        ServerPlayer target = online(player, uuid);
        if (target == null || target == player) {
            return false;
        }
        String name = target.getName().getString();
        RepData d = Reputation.data(player);
        RepData t = Reputation.data(target);
        int cost = words("bounty").has("cost") ? words("bounty").get("cost").getAsInt() : 20;
        if (Ranks.hand(player) < 1) {
            say(player, text("need_rank"), ChatFormatting.RED);
            return false;
        }
        if (Ranks.hand(target) > Ranks.hand(player)) {
            say(player, fill(text("protected"), name), ChatFormatting.RED);
            return false;
        }
        Long last = d.bountiesPlaced.get(uuid);
        if (!t.bountyBy.isEmpty() || (last != null && day(player) - last < 3)) {
            say(player, fill(text("cooldown"), name), ChatFormatting.GRAY);
            return false;
        }
        if (player.getInventory().countItem(Items.EMERALD) < cost) {
            say(player, text("need_money"), ChatFormatting.RED);
            return false;
        }
        int left = cost;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.EMERALD)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        player.inventoryMenu.broadcastChanges();
        d.bountiesPlaced.put(uuid, day(player));
        t.bountyBy = player.getName().getString();
        say(player, text("placed"), ChatFormatting.GOLD);
        return true;
    }

    // ------------------------------------------------------------------ witnesses
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager v && !CourtBuilder.isCourtNpc(v) && event.getSource().getEntity() instanceof ServerPlayer killer) {
            saw(killer, "villager");
        }
    }

    /** someone did something; any other player close enough saw it */
    public static void saw(ServerPlayer doer, String what) {
        RepData d = Reputation.data(doer);
        long day = day(doer);
        for (ServerPlayer other : doer.serverLevel().players()) {
            if (other == doer || other.distanceTo(doer) > 48 || other.isSpectator()) {
                continue;
            }
            String entry = other.getUUID() + "|" + other.getName().getString() + "|" + what + "|" + day;
            if (!d.sightings.contains(entry)) {
                d.sightings.add(entry);
            }
        }
        while (d.sightings.size() > 24) {
            d.sightings.remove(0);
        }
    }

    /** the Watch has taken someone: anyone who saw what they did is asked to speak (once each, the latest thing they saw) */
    public static void onKidnap(ServerPlayer accused) {
        RepData d = Reputation.data(accused);
        Set<String> asked = new LinkedHashSet<>();
        JsonObject t = words("testify");
        for (int i = d.sightings.size() - 1; i >= 0; i--) {
            String[] p = d.sightings.get(i).split("\\|");
            if (p.length < 4 || !asked.add(p[0])) {
                continue;
            }
            ServerPlayer witness = online(accused, p[0]);
            if (witness == null || witness == accused || Reputation.data(witness).trialState != 0) {
                continue;
            }
            ask(witness, accused, p[2], Long.parseLong(p[3]), t);
        }
    }

    private static void ask(ServerPlayer witness, ServerPlayer accused, String what, long day, JsonObject t) {
        String name = accused.getName().getString();
        String did = t.getAsJsonObject("what").has(what) ? t.getAsJsonObject("what").get(what).getAsString() : what;
        String me = witness.getName().getString();
        UUID id = accused.getUUID();
        Talk.to(witness, "A Watch Runner").title(fill(t.get("title").getAsString(), name))
                .line(fill(t.get("text").getAsString(), name).replace("{day}", Long.toString(day)).replace("{what}", did))
                .option("against", t.get("against").getAsString(), p -> testify(p, id, me, true, day, t))
                .option("for", t.get("for").getAsString(), p -> testify(p, id, me, false, day, t))
                .option("out", t.get("out").getAsString(), p -> {
                })
                .send();
    }

    private static void testify(ServerPlayer witness, UUID accusedId, String me, boolean against, long day, JsonObject t) {
        ServerPlayer accused = witness.getServer().getPlayerList().getPlayer(accusedId);
        if (accused == null || !Trial.addRivalLine(accused, me, against, day)) {
            say(witness, t.get("late").getAsString(), ChatFormatting.GRAY);
            return;
        }
        say(witness, t.get(against ? "thanks_against" : "thanks_for").getAsString(), ChatFormatting.GRAY);
    }
}

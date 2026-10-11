package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** "Your Story": the card at the end of it all. Who you became, how long it took, what it cost, and your biggest choices. */
public final class EndCard {
    private static final String[][] ENDINGS = {
            {"hag_rep_saint", "The Lantern-Bearer", "You became the gold half: mercy, all the way down."},
            {"hag_rep_good", "Morning", "You leaned toward the gold half, and it held."},
            {"hag_rep_waver", "Half and Half", "You never chose a half. Maybe that's why you're still whole."},
            {"hag_rep_fallen", "The Iron Road", "You leaned toward the iron half, and it leaned back."},
            {"hag_rep_wicked", "The Hand of Azazel", "You became the iron half: cruelty, all the way down."},
    };

    private EndCard() {
    }

    public static JsonObject compute(ServerPlayer player, Set<String> traits) {
        RepData d = Reputation.data(player);
        JsonObject o = new JsonObject();
        o.addProperty("name", player.getName().getString());
        String title = "Still Walking", half = "Your story isn't over yet.";
        for (String[] e : ENDINGS) {
            if (traits.contains(e[0])) {
                title = e[1];
                half = e[2];
            }
        }
        o.addProperty("ending", title);
        o.addProperty("half", half);
        o.addProperty("tier", Reputation.tier(d.value));
        o.addProperty("rep", Math.round(d.value));
        o.addProperty("days", d.endDay > 0 ? d.endDay : player.level().getDayTime() / 24000L + 1);
        o.addProperty("deaths", player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)));
        int spared = 0, killed = 0;
        for (Meb.Progress p : Meb.quests(player)) {
            if (p.claimed() && p.questId().startsWith("mebahelrpgrquest:hag_")) {
                if (p.spared()) {
                    spared++;
                } else if (p.attacked()) {
                    killed++;
                }
            }
        }
        for (String dec : d.simDecisions.values()) {
            if (dec.equals("spare")) {
                spared++;
            } else if (dec.equals("kill")) {
                killed++;
            }
        }
        o.addProperty("spared", spared);
        o.addProperty("killed", killed);
        o.addProperty("villagers", player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER)));
        o.addProperty("wardens", d.moments.size());
        o.addProperty("letters", d.letters.size());
        o.addProperty("watch", Ranks.WATCH[d.watchRank]);
        o.addProperty("hand", Ranks.HAND[d.handRank]);
        o.addProperty("secrets", Secrets.found(player));
        o.addProperty("secretsTotal", StoryData.INSTANCE.secrets.size());
        o.addProperty("dreams", d.dreams.size());
        o.addProperty("trials", d.trials.size());
        o.addProperty("convictions", d.convictions);
        o.add("choices", choices(player, d, traits));
        return o;
    }

    /** Your three biggest choices: the ones that moved your reputation most (or, from older saves, who you spared and who you didn't). */
    private static JsonArray choices(ServerPlayer player, RepData d, Set<String> traits) {
        record C(String text, double weight) {
        }
        List<C> list = new ArrayList<>();
        for (String s : d.bigChoices) {
            String[] p = s.split("\\|");
            if (p.length < 3) {
                continue;
            }
            double rep;
            try {
                rep = Double.parseDouble(p[2]);
            } catch (NumberFormatException e) {
                rep = 0;
            }
            list.add(new C(p[0] + ": " + p[1], Math.abs(rep)));
        }
        list.sort(Comparator.comparingDouble(C::weight).reversed());
        JsonArray out = new JsonArray();
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            out.add(list.get(i).text());
        }
        if (out.size() < 3 && Fates.storyOver(traits)) {
            for (JsonElement e : Fates.compute(player)) {
                JsonObject f = e.getAsJsonObject();
                String kind = f.get("kind").getAsString();
                if (kind.equals("other")) {
                    continue;
                }
                out.add((kind.equals("mercy") ? "You showed mercy to " : "You chose the blade for ") + f.get("name").getAsString());
                if (out.size() >= 3) {
                    break;
                }
            }
        }
        return out;
    }
}

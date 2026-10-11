package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/** What became of them: one line for every person you judged, picked from what you actually did. */
public final class Fates {
    private static final String[] ENDINGS = {"hag_rep_saint", "hag_rep_good", "hag_rep_waver", "hag_rep_fallen", "hag_rep_wicked"};

    private Fates() {
    }

    public static boolean storyOver(Set<String> traits) {
        for (String e : ENDINGS) {
            if (traits.contains(e)) {
                return true;
            }
        }
        return false;
    }

    public static JsonArray compute(ServerPlayer player) {
        Set<String> traits = Meb.traits(player);
        JsonArray out = new JsonArray();
        for (JsonObject person : StoryData.INSTANCE.fates) {
            for (JsonElement oe : person.getAsJsonArray("options")) {
                JsonObject o = oe.getAsJsonObject();
                if (traits.contains(o.get("trait").getAsString())) {
                    JsonObject f = new JsonObject();
                    f.addProperty("name", person.get("name").getAsString());
                    f.addProperty("kind", o.get("kind").getAsString());
                    f.addProperty("text", o.get("text").getAsString());
                    out.add(f);
                    break;
                }
            }
        }
        return out;
    }
}

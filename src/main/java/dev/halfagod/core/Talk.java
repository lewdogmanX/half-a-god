package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Conversations on the mod's own screen: a name, a few lines, and some answers to pick from. Used by the prisoners,
 * visitors, the gaoler, confessions, interrogations, the Gilded Hand's price on a rival, and testifying at a rival's trial.
 * The server remembers what each answer does; the client only shows it and says which one was picked.
 */
public final class Talk {
    private record Option(String id, String label, String hint, Consumer<ServerPlayer> action) {
    }

    private record Open(String id, Map<String, Option> options) {
    }

    private static final Map<UUID, Open> OPEN = new HashMap<>();
    private static int counter;

    private final ServerPlayer player;
    private final String name;
    private String title = "";
    private String style = "paper";
    private final List<String> lines = new ArrayList<>();
    private final Map<String, Option> options = new LinkedHashMap<>();

    private Talk(ServerPlayer player, String name) {
        this.player = player;
        this.name = name;
    }

    public static Talk to(ServerPlayer player, String name) {
        return new Talk(player, name);
    }

    public Talk title(String t) {
        title = t;
        return this;
    }

    /** "paper" (the default), "prison" or "church" */
    public Talk style(String s) {
        style = s;
        return this;
    }

    public Talk line(String l) {
        if (l != null && !l.isEmpty()) {
            lines.add(l);
        }
        return this;
    }

    public Talk lines(List<String> ls) {
        ls.forEach(this::line);
        return this;
    }

    public Talk option(String id, String label, Consumer<ServerPlayer> action) {
        return option(id, label, "", action);
    }

    public Talk option(String id, String label, String hint, Consumer<ServerPlayer> action) {
        options.put(id, new Option(id, label, hint, action));
        return this;
    }

    public void send() {
        if (options.isEmpty()) {
            option("leave", "Leave", p -> {
            });
        }
        String id = "t" + (++counter);
        OPEN.put(player.getUUID(), new Open(id, options));
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("title", title);
        o.addProperty("style", style);
        JsonArray ls = new JsonArray();
        lines.forEach(ls::add);
        o.add("lines", ls);
        JsonArray opts = new JsonArray();
        for (Option op : options.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", op.id());
            j.addProperty("label", op.label());
            j.addProperty("hint", op.hint());
            opts.add(j);
        }
        o.add("options", opts);
        Net.send(player, new Payloads.Talk(o.toString()));
    }

    public static void close(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        JsonObject o = new JsonObject();
        o.addProperty("close", true);
        Net.send(player, new Payloads.Talk(o.toString()));
    }

    /** the conversation the player has open, if any (game tests answer through this) */
    public static List<String> openOptions(ServerPlayer player) {
        Open o = OPEN.get(player.getUUID());
        return o == null ? List.of() : new ArrayList<>(o.options().keySet());
    }

    /** answers the open conversation as if the player had clicked (game tests, and the client's "talk" act) */
    public static boolean choose(ServerPlayer player, String option) {
        Open o = OPEN.remove(player.getUUID());
        if (o == null) {
            return false;
        }
        Option op = o.options().get(option);
        if (op == null) {
            return false;
        }
        op.action().accept(player);
        return true;
    }

    /** everything the client says about the mod's screens arrives here */
    public static void onAct(ServerPlayer player, String kind, String a, String b) {
        switch (kind) {
            case "talk" -> {
                Open o = OPEN.get(player.getUUID());
                if (o != null && o.id().equals(a)) {
                    choose(player, b);
                }
            }
            case "talk_closed" -> {
                Open o = OPEN.get(player.getUUID());
                if (o != null && o.id().equals(a)) {
                    OPEN.remove(player.getUUID());
                }
            }
            case "line" -> Trial.onLineShown(player, parse(a));
            case "answer" -> Trial.onAnswer(player, parse(a), b);
            case "bounty" -> Rivals.askBounty(player, b);
            default -> {
            }
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

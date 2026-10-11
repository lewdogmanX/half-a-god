package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.HalfAGod;
import dev.halfagod.core.Dreams;
import dev.halfagod.core.EndCard;
import dev.halfagod.core.RepData;
import dev.halfagod.core.Reputation;
import dev.halfagod.core.StoryData;
import dev.halfagod.core.Trial;
import dev.halfagod.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CI only (-Dhalfagod.clientshots=true): a real game client under a virtual screen joins a world, goes through a dream,
 * a whole trial (court, verdict, cell) and the story map's new pages, taking a screenshot of each, then quits.
 */
public final class ClientShots {
    private record Step(int pause, Runnable run) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait = 200;
    private static Screen trial;

    private ClientShots() {
    }

    private static void step(int waitAfter, Runnable r) {
        STEPS.add(new Step(waitAfter, r));
    }

    private static void server(Consumer<ServerPlayer> c) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                c.accept(sp);
            }
        });
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> HalfAGod.LOG.info("SHOT {}: {}", name, m.getString()));
    }

    private static void click(int times) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < times && mc.screen != null; i++) {
            mc.screen.mouseClicked(1, 1, 0);
        }
    }

    public static void init() {
        step(40, () -> server(sp -> {
            sp.awardStat(Stats.ENTITY_KILLED.get(EntityType.VILLAGER), 9);
            Reputation.change(sp, -70, "test");
            Reputation.data(sp).handRank = 1;
            sp.getInventory().add(new ItemStack(Items.EMERALD, 20));
            sp.getInventory().add(new ItemStack(Items.IRON_SWORD));
        }));
        step(10, () -> shot("01_world"));
        step(170, () -> server(sp -> Dreams.send(sp, Dreams.byId("past_2"))));
        step(10, () -> shot("02_dream"));
        step(30, () -> Minecraft.getInstance().setScreen(null));
        step(100, () -> server(Trial::kidnap));
        step(10, () -> shot("03_trial_taken"));
        step(20, () -> {
            trial = Minecraft.getInstance().screen;
            click(4);
        });
        step(10, () -> shot("04_trial_court"));
        step(20, () -> click(30));
        step(10, () -> shot("05_trial_plea"));
        step(30, () -> Minecraft.getInstance().setScreen(null));
        step(10, () -> shot("06_courtroom"));
        step(20, () -> Minecraft.getInstance().setScreen(trial));
        step(40, () -> PacketDistributor.sendToServer(new Payloads.Plea("silent")));
        step(10, () -> shot("07_verdict"));
        step(80, () -> {
            PacketDistributor.sendToServer(new Payloads.Plea("continue"));
            Minecraft.getInstance().setScreen(null);
        });
        step(10, () -> shot("08_cell_window"));
        step(20, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.player.setYRot(-90f);
            mc.player.setXRot(10f);
        });
        step(10, () -> shot("09_cell_gaoler"));
        step(20, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.player.setYRot(180f);
            mc.player.setXRot(0f);
        });
        step(10, () -> shot("10_cell_wall"));
        step(5, () -> server(sp -> {
            RepData d = Reputation.data(sp);
            for (int i = 0; i < 8 && d.trialState == 2; i++) {
                Trial.onNightInCell(sp);
            }
        }));
        step(150, () -> {
        });
        step(10, () -> shot("11_home_dream"));
        step(30, () -> Minecraft.getInstance().setScreen(null));
        step(40, () -> server(sp -> {
            JsonObject fake = fakeMap(sp);
            Minecraft.getInstance().execute(() -> {
                StoryMapScreen.testFreeze = false;
                StoryMapScreen screen = new StoryMapScreen(null);
                Minecraft.getInstance().setScreen(screen);
                StoryMapScreen.receive(fake.toString());
                StoryMapScreen.testFreeze = true;
            });
        }));
        step(10, () -> shot("12_map_notes"));
        for (String page : new String[]{"secrets", "trials", "dreams", "path", "story"}) {
            step(30, () -> {
                if (Minecraft.getInstance().screen instanceof StoryMapScreen s) {
                    s.openCodex(page);
                }
            });
            step(10, () -> shot("13_codex_" + page));
        }
        step(20, () -> Minecraft.getInstance().stop());
    }

    /** a map with some of the prologue done, at different reputations, and a few of everything else */
    private static JsonObject fakeMap(ServerPlayer sp) {
        JsonObject o = new JsonObject();
        JsonObject states = new JsonObject();
        String[] done = {"the_first_letter", "p1_the_bell", "p2_winter_stores", "p3_the_shepherd", "p4_eye_on_chapel", "p5_raise_the_watch"};
        int[] reps = {30, 25, 5, -10, -40, -70};
        JsonObject doneRep = new JsonObject();
        for (int i = 0; i < done.length; i++) {
            states.addProperty("mebahelrpgrquest:hag_" + done[i], "done");
            doneRep.addProperty("mebahelrpgrquest:hag_" + done[i], reps[i]);
        }
        states.addProperty("mebahelrpgrquest:hag_p6_market_road", "active");
        o.add("states", states);
        o.add("stages", new JsonObject());
        o.addProperty("rep", -70);
        o.addProperty("op", true);
        o.add("letters", new JsonArray());
        JsonArray path = new JsonArray();
        path.add("#Prologue: The Bell That Won't Ring");
        for (int r : reps) {
            path.add(Integer.toString(r));
        }
        o.add("path", path);
        o.add("moments", new JsonObject());
        o.addProperty("day", 31);
        JsonObject ranks = new JsonObject();
        ranks.addProperty("watch", 0);
        ranks.addProperty("hand", 1);
        ranks.addProperty("watchTitle", "");
        ranks.addProperty("handTitle", "Runner for the Hand");
        ranks.addProperty("watchProgress", "Duties done: 0.");
        ranks.addProperty("handProgress", "Contracts done: 7.");
        o.add("ranks", ranks);
        o.add("doneRep", doneRep);
        JsonArray dreams = new JsonArray();
        for (String id : new String[]{"past_1", "omen_w1_the_skull_market", "echo_peddler_slain"}) {
            JsonObject d = Dreams.byId(id).deepCopy();
            d.addProperty("day", 7);
            dreams.add(d);
        }
        o.add("dreams", dreams);
        JsonArray secrets = new JsonArray();
        int i = 0;
        for (JsonObject l : StoryData.INSTANCE.secrets) {
            JsonObject x = new JsonObject();
            boolean found = i < 2;
            x.addProperty("name", found ? l.get("name").getAsString() : "???");
            x.addProperty("how", found ? l.get("how").getAsString() : "");
            x.addProperty("found", found);
            x.addProperty("done", i == 0);
            secrets.add(x);
            i++;
        }
        o.add("secrets", secrets);
        JsonArray trials = new JsonArray();
        for (String t : Reputation.data(sp).trials) {
            trials.add(JsonParser.parseString(t));
        }
        o.add("trials", trials);
        o.addProperty("storyOver", false);
        o.add("story", EndCard.compute(sp, Set.of()));
        JsonArray others = new JsonArray();
        JsonObject f = new JsonObject();
        f.addProperty("name", "Alex");
        f.addProperty("act", "Act II");
        f.addProperty("tier", "Good");
        f.addProperty("watch", "Watchman");
        f.addProperty("hand", "");
        f.addProperty("rival", true);
        others.add(f);
        o.add("others", others);
        return o;
    }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null || index >= STEPS.size()) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = STEPS.get(index++);
        try {
            s.run().run();
        } catch (Exception ex) {
            HalfAGod.LOG.error("SHOT step {} failed", index, ex);
        }
        wait = s.pause();
    }
}

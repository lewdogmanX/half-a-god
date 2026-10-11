package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.HalfAGod;
import dev.halfagod.core.CourtBuilder;
import dev.halfagod.core.CourtData;
import dev.halfagod.core.Dreams;
import dev.halfagod.core.Prison;
import dev.halfagod.core.Talk;
import dev.halfagod.core.Words;
import dev.halfagod.core.EndCard;
import dev.halfagod.core.RepData;
import dev.halfagod.core.Reputation;
import dev.halfagod.core.StoryData;
import dev.halfagod.core.Trial;
import dev.halfagod.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
    private record Step(int pause, Runnable run, java.util.function.BooleanSupplier until) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait = 200;

    private ClientShots() {
    }

    private static void step(int waitAfter, Runnable r) {
        STEPS.add(new Step(waitAfter, r, null));
    }

    /** waits (up to a minute) until something is so, then goes on */
    private static void until(java.util.function.BooleanSupplier c) {
        STEPS.add(new Step(0, () -> {
        }, c));
    }

    private static int waited;

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

    private static void look(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    /** moves the player within their court slot (server side) */
    private static void at(ServerPlayer sp, double x, double y, double z, float yaw, float pitch) {
        net.minecraft.server.level.ServerLevel court = Trial.court(sp.getServer());
        net.minecraft.core.BlockPos o = CourtBuilder.origin(CourtData.get(court).slot(sp.getUUID()));
        sp.teleportTo(court, o.getX() + x, o.getY() + y, o.getZ() + z, yaw, pitch);
    }

    private static final String[] ANSWERS = {"deny", "explain", "silent"};
    private static int answered;

    /** clicks through the court until it asks for an answer to a charge */
    private static void untilAsking() {
        for (int i = 0; i < 15 && TrialScreen.active() != null && !TrialScreen.active().isAsking(); i++) {
            TrialScreen.active().click();
        }
    }

    /** clicks through the court, answering each charge in turn, until it asks for a plea */
    private static void advance(int times) {
        for (int i = 0; i < times && TrialScreen.active() != null; i++) {
            TrialScreen t = TrialScreen.active();
            if (t.isAsking()) {
                t.answerForTest(ANSWERS[answered++ % ANSWERS.length]);
            } else {
                t.click();
            }
        }
    }

    private static void trialClick(int times) {
        for (int i = 0; i < times && TrialScreen.active() != null; i++) {
            TrialScreen.active().click();
        }
    }

    /** right-clicks one of the prison's people (server side): who you talk to is who the talk screen turns to */
    private static void talkTo(ServerPlayer sp, String role) {
        net.minecraft.server.level.ServerLevel court = Trial.court(sp.getServer());
        net.minecraft.world.entity.Entity who = CourtBuilder.find(court, slot(sp), role);
        if (who != null) {
            Talk.speaking(sp, who);
        }
        Prison.onTalk(sp, role);
    }

    private static void fly(ServerPlayer sp, boolean on) {
        sp.getAbilities().mayfly = on;
        sp.getAbilities().flying = on;
        sp.onUpdateAbilities();
    }

    private static int slot(ServerPlayer sp) {
        return CourtData.get(Trial.court(sp.getServer())).slot(sp.getUUID());
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
        step(20, () -> server(sp -> Trial.kidnap(sp, Trial.SAMPLE_RECORD)));
        until(() -> TrialScreen.active() != null);
        step(200, () -> {
        });
        step(10, () -> shot("03_trial_taken"));
        step(20, () -> trialClick(3));
        step(15, () -> shot("04_trial_court"));
        // looking around while the court speaks: the jury, then the witness stand, then back to the bench
        step(10, () -> look(-128f, 5f));
        step(10, () -> shot("04c_trial_look_jury"));
        step(10, () -> look(140f, 0f));
        step(10, () -> shot("04d_trial_look_witness"));
        step(10, () -> look(180f, 0f));
        step(5, ClientShots::untilAsking);
        step(25, () -> shot("04b_trial_answer"));
        step(20, () -> advance(60));
        step(10, () -> shot("05_trial_plea"));
        step(30, () -> TrialScreen.testHide = true);
        step(10, () -> shot("06_courtroom"));
        // the court from other angles: the jury on the right, the witness stand on the left, and from the gallery
        step(10, () -> look(-128f, 5f));
        step(10, () -> shot("06b_court_jury"));
        step(10, () -> look(140f, 0f));
        step(10, () -> shot("06c_court_witness"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.DOCK.getX() + 0.5, 0, 12.5, 180f, -8f)));
        step(30, () -> shot("06d_court_gallery"));
        // the whole hall from up by the rose window, and the bench from the floor
        step(10, () -> server(sp -> {
            fly(sp, true);
            at(sp, 0.5, 8, 14.0, 180f, 16f);
        }));
        step(40, () -> shot("06e_court_overview"));
        step(10, () -> server(sp -> at(sp, 0.5, 0, -6.5, 180f, -14f)));
        step(30, () -> shot("06f_court_bench"));
        step(10, () -> server(sp -> at(sp, -9.5, 7, 13.5, 205f, 14f)));
        step(30, () -> shot("06g_court_balcony"));
        step(10, () -> server(sp -> {
            at(sp, CourtBuilder.DOCK.getX() + 0.5, 0, CourtBuilder.DOCK.getZ() + 0.5, 180f, 0f);
            fly(sp, false);
        }));
        step(20, () -> TrialScreen.testHide = false);
        step(90, () -> {
            if (TrialScreen.active() != null) {
                TrialScreen.active().pleadForTest("silent");
            }
        });
        step(140, () -> shot("07_verdict_votes"));
        step(10, () -> shot("07b_verdict"));
        step(80, () -> trialClick(2));
        step(10, () -> shot("08_cell_bars"));
        step(10, () -> server(sp -> {
            Reputation.data(sp).sentence = 5;
            Trial.passTime(sp, 1200);
        }));
        step(10, () -> shot("08b_cell_time_left"));
        step(20, () -> look(0f, 25f));
        step(10, () -> shot("09_cell_window"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 0.5, 0, CourtBuilder.CELL.getZ() + 5.5, 0f, 40f)));
        step(30, () -> shot("09b_cell_lava"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL_SPOT.getX() + 0.5, 0, CourtBuilder.CELL_SPOT.getZ() + 0.5, 180f, 0f)));
        step(20, () -> look(60f, 15f));
        step(10, () -> shot("10_cell_cot"));
        // the corridor, and the prison in its cavern from over the lava
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() - 10.5, 0, CourtBuilder.CELL.getZ() - 2.5, -90f, 2f)));
        step(30, () -> shot("09c_corridor"));
        step(10, () -> server(sp -> {
            fly(sp, true);
            at(sp, CourtBuilder.CELL.getX() + 0.5, 6, CourtBuilder.CELL.getZ() + 26.0, 180f, 8f);
        }));
        step(60, () -> shot("09d_cavern_prison"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 33.0, 15, CourtBuilder.CELL.getZ() + 31.0, 133f, 24f)));
        step(60, () -> shot("09e_cavern_wide"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() - 30.0, 12, CourtBuilder.CELL.getZ() + 20.0, -120f, 10f)));
        step(60, () -> shot("09f_cavern_falls"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 7.0, 11, CourtBuilder.CELL.getZ() + 15.0, 160f, 6f)));
        step(60, () -> shot("09g_keep_roof"));
        step(10, () -> server(sp -> {
            at(sp, CourtBuilder.CELL_SPOT.getX() + 0.5, 0, CourtBuilder.CELL_SPOT.getZ() + 0.5, 180f, 0f);
            fly(sp, false);
        }));
        // the prison day: the quarry, the cafeteria, the other prisoners
        step(10, () -> server(sp -> Prison.testTime(sp, "labour")));
        step(40, () -> server(sp -> Trial.passTime(sp, 1220)));
        step(40, () -> look(90f, 8f));
        step(10, () -> shot("14_quarry"));
        step(10, () -> server(sp -> {
            BlockPos face = CourtBuilder.abs(slot(sp), new BlockPos(-27, 1, -1));
            for (int i = 0; i < 5; i++) {
                Prison.onBreak(sp, face);
            }
        }));
        step(10, () -> look(140f, 10f));
        step(10, () -> shot("14b_quarry_cart"));
        step(10, () -> server(sp -> Prison.testTime(sp, "supper")));
        step(40, () -> server(sp -> Trial.passTime(sp, 1220)));
        step(40, () -> look(-90f, 6f));
        step(10, () -> shot("15_cafeteria"));
        step(10, () -> server(sp -> talkTo(sp, "prisoner0")));
        step(25, () -> shot("16_talk_maddock"));
        step(10, () -> server(sp -> Talk.choose(sp, "where")));
        step(25, () -> shot("16b_talk_where"));
        step(10, () -> server(sp -> Talk.choose(sp, "escape")));
        step(25, () -> shot("16c_talk_escape"));
        step(10, () -> {
            server(sp -> Talk.choose(sp, "leave"));
            Minecraft.getInstance().setScreen(null);
        });
        // morning: a visitor at your bars
        step(10, () -> server(sp -> {
            Prison.testTime(sp, "morning");
            Prison.testVisitor(sp);
        }));
        step(60, () -> look(180f, 10f));
        step(10, () -> shot("17_visitor"));
        step(10, () -> server(sp -> talkTo(sp, "visitor")));
        step(25, () -> shot("17b_visitor_talk"));
        // on Mebahel's talk screen (when his mod is installed): click on to the visitor's next line
        step(10, () -> {
            if (MebTalk.open()) {
                MebTalk.pickForTest(0);
            }
        });
        step(40, () -> shot("17c_visitor_next"));
        step(10, () -> {
            server(sp -> Talk.choose(sp, "leave"));
            Minecraft.getInstance().setScreen(null);
        });
        // lights out, and a hole under the window
        step(10, () -> server(sp -> Prison.testTime(sp, "lightsout")));
        step(40, () -> server(sp -> {
            Trial.passTime(sp, 40);
            Prison.testEscape(sp, "dig");
            Prison.dig(sp, Reputation.data(sp), sp.getServer().getTickCount());
        }));
        step(30, () -> look(0f, 20f));
        step(10, () -> shot("18_escape_hole"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 2.5, -1 + 1, CourtBuilder.CELL.getZ() + 7.5, -90f, 15f)));
        step(30, () -> shot("18b_ledge"));
        // ...but this time, serve it out and walk through the gate
        step(10, () -> server(sp -> {
            RepData d = Reputation.data(sp);
            Prison.caught(sp, d, "dawn");
            Prison.putIn(sp, "cell");
            d.served = d.sentence - 1;
            Trial.serveDay(sp);
        }));
        step(40, () -> look(180f, 5f));
        step(10, () -> shot("19_walk_out"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 20.5, 0, CourtBuilder.CELL.getZ() - 7.5, 180f, 0f)));
        step(30, () -> look(180f, 0f));
        step(10, () -> shot("19b_the_gate"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 21.0, 0, CourtBuilder.CELL.getZ() - 21.5, 0f, 6f)));
        step(40, () -> shot("19c_bridge_back"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 21.0, 0, CourtBuilder.CELL.getZ() - 18.5, 180f, 0f)));
        step(30, () -> shot("19d_the_tunnel"));
        step(10, () -> server(sp -> at(sp, CourtBuilder.CELL.getX() + 20.5, 0, CourtBuilder.CELL.getZ() - 12.5, 180f, 0f)));
        step(150, () -> {
        });
        step(10, () -> shot("11_home_dream"));
        step(30, () -> Minecraft.getInstance().setScreen(null));
        // confession, and an interrogation
        step(20, () -> server(Words::confession));
        step(25, () -> shot("20_confession"));
        step(10, () -> server(sp -> Talk.choose(sp, "villagers")));
        step(25, () -> shot("20b_confessed"));
        step(10, () -> server(sp -> Words.interrogate(sp, "vael")));
        step(25, () -> shot("21_interrogation"));
        step(10, () -> server(sp -> Words.ask(sp, "vael", "bribe")));
        step(25, () -> shot("21b_interrogation_answer"));
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
        for (String page : new String[]{"secrets", "trials", "dreams", "path", "letters", "story"}) {
            step(30, () -> {
                if (Minecraft.getInstance().screen instanceof StoryMapScreen s) {
                    s.openCodex(page);
                }
            });
            step(10, () -> shot("13_codex_" + page));
        }
        step(60, () -> {
            if (Minecraft.getInstance().screen instanceof StoryMapScreen s) {
                s.saveCardForTest();
            }
        });
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

    private static int idle;
    private static boolean opened;

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            // not in the world yet: say what's on screen, and open the test world from the title screen
            idle++;
            if (idle % 100 == 0) {
                HalfAGod.LOG.info("SHOT waiting, screen: {}", mc.screen == null ? "none" : mc.screen.getClass().getName());
                if (idle <= 300) {
                    shot("00_waiting_" + idle);
                }
            }
            if (!opened && idle > 60 && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true;
                HalfAGod.LOG.info("SHOT opening the test world");
                mc.createWorldOpenFlows().openWorld("shots", () -> HalfAGod.LOG.info("SHOT world opening cancelled"));
            }
            // "this world uses experimental settings": go on anyway
            if (mc.screen != null && idle % 20 == 10) {
                for (var child : mc.screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().contains("know what")) {
                        HalfAGod.LOG.info("SHOT pressing: {}", b.getMessage().getString());
                        b.onPress();
                        break;
                    }
                }
            }
            if (idle > 20 * 60 * 8) {
                HalfAGod.LOG.info("SHOT giving up waiting for the world");
                mc.stop();
            }
            return;
        }
        if (index >= STEPS.size()) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = STEPS.get(index);
        if (s.until() != null && !s.until().getAsBoolean() && waited++ < 1200) {
            return;
        }
        waited = 0;
        index++;
        try {
            s.run().run();
        } catch (Exception ex) {
            HalfAGod.LOG.error("SHOT step {} failed", index, ex);
        }
        wait = s.pause();
    }
}

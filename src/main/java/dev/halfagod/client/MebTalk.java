package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.halfagod.HalfAGod;
import dev.halfagod.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * All of the mod's conversations on Mebahel's own talk screen, when his mod is installed (through Sinytra Connector):
 * his typewriter text, his villager voices, his camera turning to whoever you're talking to (when someone is there). Each line is a page you click on from; the last page has the answers. The answers come back here,
 * not to his quests (a small mixin hands his screen's option list and clicks to this class).
 *
 * Everything is looked up by name, carefully: if his screen isn't there, or something in it has changed, this says so
 * once in the log and the mod's own talk screen is used instead. Nobody ever ends up in a conversation that can't be
 * answered.
 */
public final class MebTalk {
    static final String SCREEN = "net.mebahelrpgrquest.client.gui.QuestDialogueScreen";
    private static final String OPTION = SCREEN + "$DialogueOption";
    /** our own option actions (his are all small numbers or negative) */
    static final int NEXT = 9_001, FIRST_ANSWER = 9_100;
    private static final String NOBODY = "00000000-0000-0000-0000-000000000000";

    private static final class State {
        String id = "";
        String entity = "";
        final List<String> pages = new ArrayList<>();
        final List<String[]> answers = new ArrayList<>();
        int page;
        boolean waiting;
        long waitingSince;
        boolean done;
    }

    private static final Map<Object, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static Class<?> screenClass;
    private static Constructor<?> screenCtor, optionCtor;
    private static boolean broken, looked;

    private MebTalk() {
    }

    // ------------------------------------------------------------------ looking things up
    private static boolean ready() {
        if (looked) {
            return !broken;
        }
        looked = true;
        try {
            screenClass = Class.forName(SCREEN, false, MebTalk.class.getClassLoader());
        } catch (Throwable t) {
            try {
                screenClass = Class.forName(SCREEN, false, Thread.currentThread().getContextClassLoader());
            } catch (Throwable t2) {
                broken = true; // his mod isn't installed: nothing to say
                return false;
            }
        }
        try {
            for (Constructor<?> c : screenClass.getDeclaredConstructors()) {
                if (c.getParameterCount() == 12) {
                    screenCtor = c;
                }
            }
            Class<?> opt = Class.forName(OPTION, false, screenClass.getClassLoader());
            optionCtor = opt.getDeclaredConstructor(String.class, int.class, boolean.class);
            optionCtor.setAccessible(true);
            if (screenCtor == null) {
                throw new NoSuchMethodException("QuestDialogueScreen(12 arguments)");
            }
            screenCtor.setAccessible(true);
            for (String m : new String[]{"setDialogueText", "beginContentTransition", "rebuildDialogueOptions", "recalculateOptionsLayout",
                    "resetOptionScroll", "clampSelectedOptionIndex", "clampOptionScroll", "isContentTransitioning", "openingResponsesPending",
                    "playClickSound"}) {
                method(m);
            }
            for (String f : new String[]{"dialogueOptions", "selectedOptionIndex", "actionSent"}) {
                field(f);
            }
            HalfAGod.LOG.info("Half a God: conversations will use Mebahel's talk screen.");
            return true;
        } catch (Throwable t) {
            broken = true;
            HalfAGod.LOG.warn("Half a God: Mebahel's talk screen has changed ({}); using the mod's own talk screen instead.", t.toString());
            return false;
        }
    }

    private static Method method(String name) throws NoSuchMethodException {
        Method m = METHODS.get(name);
        if (m != null) {
            return m;
        }
        for (Method c : screenClass.getDeclaredMethods()) {
            if (c.getName().equals(name)) {
                c.setAccessible(true);
                METHODS.put(name, c);
                return c;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = FIELDS.get(name);
        if (f == null) {
            f = screenClass.getDeclaredField(name);
            f.setAccessible(true);
            FIELDS.put(name, f);
        }
        return f;
    }

    private static Object call(Object screen, String name, Object... args) throws Exception {
        return method(name).invoke(screen, args);
    }

    private static void fail(Object screen, Throwable t) {
        broken = true;
        HalfAGod.LOG.warn("Half a God: Mebahel's talk screen didn't behave ({}); using the mod's own talk screen from now on.", t.toString());
        State s = STATES.remove(screen);
        Minecraft mc = Minecraft.getInstance();
        if (s != null && mc.screen == screen) {
            try {
                field("actionSent").setBoolean(screen, true);
            } catch (Throwable ignored) {
            }
            mc.setScreen(null);
            if (!s.done) {
                PacketDistributor.sendToServer(new Payloads.Act("talk_closed", s.id, ""));
            }
        }
    }

    // ------------------------------------------------------------------ from the server
    /** a conversation arrives: opens it on his screen (or carries on the one open). False: show it on the mod's own. */
    public static boolean show(JsonObject o) {
        if (!ready()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        // nobody standing there (a runner's message, the Hand's agent): his screen still works, it just shows the name
        String entity = o.has("entity") ? o.get("entity").getAsString() : NOBODY;
        Object current = mc.screen;
        State s = current == null ? null : STATES.get(current);
        if (s != null && s.entity.equals(entity)) {
            return carryOn(current, s, o);
        }
        try {
            UUID uuid = UUID.fromString(entity);
            List<String> pages = pages(o);
            Object screen = screenCtor.newInstance(null, uuid, ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "talk"), 5, false,
                    o.has("name") ? o.get("name").getAsString() : "", pages.get(0), "", "", "", "", List.of());
            State n = new State();
            fill(n, o, pages);
            STATES.put(screen, n);
            mc.setScreen((Screen) screen);
            return true;
        } catch (Throwable t) {
            broken = true;
            HalfAGod.LOG.warn("Half a God: couldn't open Mebahel's talk screen ({}); using the mod's own talk screen instead.", t.toString());
            return false;
        }
    }

    /** the conversation is over (the server had nothing more to say) */
    public static boolean close() {
        Minecraft mc = Minecraft.getInstance();
        Object screen = mc.screen;
        State s = screen == null ? null : STATES.get(screen);
        if (s == null) {
            return false;
        }
        s.done = true;
        end(screen);
        return true;
    }

    private static List<String> pages(JsonObject o) {
        List<String> out = new ArrayList<>();
        if (o.has("lines")) {
            for (JsonElement e : o.getAsJsonArray("lines")) {
                out.add(e.getAsString());
            }
        }
        if (out.isEmpty()) {
            out.add("...");
        }
        return out;
    }

    private static void fill(State s, JsonObject o, List<String> pages) {
        s.id = o.get("id").getAsString();
        s.entity = o.has("entity") ? o.get("entity").getAsString() : NOBODY;
        s.pages.clear();
        s.pages.addAll(pages);
        s.answers.clear();
        JsonArray opts = o.has("options") ? o.getAsJsonArray("options") : new JsonArray();
        for (JsonElement e : opts) {
            JsonObject j = e.getAsJsonObject();
            // his option rows have no room for a note, so a short one goes after the answer itself
            String label = j.get("label").getAsString();
            String hint = j.has("hint") ? j.get("hint").getAsString() : "";
            if (!hint.isEmpty() && hint.length() <= 60) {
                label = label + "  (" + hint + ")";
            }
            s.answers.add(new String[]{j.get("id").getAsString(), label});
        }
        s.page = 0;
        s.waiting = false;
        s.done = false;
    }

    /** the next part of the same conversation: fade the new words in, in the same screen */
    private static boolean carryOn(Object screen, State s, JsonObject o) {
        List<String> pages = pages(o);
        fill(s, o, pages);
        try {
            call(screen, "beginContentTransition", (Runnable) () -> refresh(screen, s), pages.get(0));
            return true;
        } catch (Throwable t) {
            fail(screen, t);
            return false;
        }
    }

    private static void refresh(Object screen, State s) {
        try {
            call(screen, "setDialogueText", s.pages.get(Math.min(s.page, s.pages.size() - 1)));
            call(screen, "rebuildDialogueOptions");
            call(screen, "recalculateOptionsLayout");
            field("selectedOptionIndex").setInt(screen, 0);
            call(screen, "resetOptionScroll");
            call(screen, "clampSelectedOptionIndex");
            call(screen, "clampOptionScroll");
        } catch (Throwable t) {
            fail(screen, t);
        }
    }

    private static void end(Object screen) {
        State s = STATES.remove(screen);
        try {
            field("actionSent").setBoolean(screen, true); // so his screen doesn't tell his quests anything on the way out
        } catch (Throwable ignored) {
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == screen) {
            mc.setScreen(null);
        }
        if (s != null && !s.done) {
            PacketDistributor.sendToServer(new Payloads.Act("talk_closed", s.id, ""));
        }
    }

    // ------------------------------------------------------------------ from his screen (through the mixin)
    /** his screen is about to build its list of options: ours instead. True if it was ours. */
    public static boolean onBuildOptions(Object screen) {
        State s = STATES.get(screen);
        if (s == null) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) field("dialogueOptions").get(screen);
            list.clear();
            if (s.page < s.pages.size() - 1) {
                list.add(optionCtor.newInstance("Continue", NEXT, true));
            } else {
                for (int i = 0; i < s.answers.size(); i++) {
                    list.add(optionCtor.newInstance(s.answers.get(i)[1], FIRST_ANSWER + i, true));
                }
            }
        } catch (Throwable t) {
            fail(screen, t);
        }
        return true;
    }

    /** an option was picked on his screen. True if it was ours (his own code mustn't run then). */
    public static boolean onActivate(Object screen) {
        State s = STATES.get(screen);
        if (s == null) {
            return false;
        }
        try {
            List<?> list = (List<?>) field("dialogueOptions").get(screen);
            if (s.waiting || list.isEmpty() || Boolean.TRUE.equals(call(screen, "isContentTransitioning"))
                    || Boolean.TRUE.equals(call(screen, "openingResponsesPending"))) {
                return true;
            }
            call(screen, "clampSelectedOptionIndex");
            int i = field("selectedOptionIndex").getInt(screen);
            call(screen, "playClickSound");
            if (s.page < s.pages.size() - 1) {
                s.page++;
                call(screen, "beginContentTransition", (Runnable) () -> refresh(screen, s), s.pages.get(s.page));
                return true;
            }
            if (i < 0 || i >= s.answers.size()) {
                return true;
            }
            // tell the server; it answers with the next part of the conversation (which carries on here), or that it's over
            s.waiting = true;
            s.waitingSince = System.currentTimeMillis();
            s.done = true;
            PacketDistributor.sendToServer(new Payloads.Act("talk", s.id, s.answers.get(i)[0]));
        } catch (Throwable t) {
            fail(screen, t);
        }
        return true;
    }

    /** his screen wants to tell his quests something (closing, mostly). True if it was ours: then it says nothing. */
    public static boolean onSendAction(Object screen) {
        State s = STATES.get(screen);
        if (s == null) {
            return false;
        }
        // Esc, or something else took the screen: the conversation is over (the state stays with the screen, so a second
        // try on the way out is also kept from his quests)
        if (!s.done) {
            s.done = true;
            PacketDistributor.sendToServer(new Payloads.Act("talk_closed", s.id, ""));
        }
        s.waiting = false;
        try {
            field("actionSent").setBoolean(screen, true);
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** every client tick: a conversation waiting on a server that has nothing more to say closes after a moment */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Object screen = mc.screen;
        State s = screen == null ? null : STATES.get(screen);
        if (s != null && s.waiting && System.currentTimeMillis() - s.waitingSince > 2500) {
            end(screen);
        }
    }

    /** for the CI screenshots: is a conversation open on his screen? */
    public static boolean open() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen != null && STATES.containsKey(mc.screen);
    }

    /** for the CI screenshots: pick an answer (or carry on to the next page) as if clicked */
    public static void pickForTest(int index) {
        Minecraft mc = Minecraft.getInstance();
        Object screen = mc.screen;
        if (screen == null || !STATES.containsKey(screen)) {
            return;
        }
        try {
            field("selectedOptionIndex").setInt(screen, index);
            onActivate(screen);
        } catch (Throwable t) {
            fail(screen, t);
        }
    }
}

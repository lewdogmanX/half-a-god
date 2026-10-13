package dev.halfagod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Trial, as you see it from the dock: you're taken in the night (a few dark lines), then the court speaks one line
 * at a time, waiting for you (click, Space or Enter) and the person speaking turns to you in the courtroom. After each
 * witness you answer the charge: deny it, explain it, or say nothing. Then you plead, the six jurors vote one by one,
 * and the verdict. The words go in a panel along the bottom, with the court's name on a small banner at the top.
 *
 * Most of the time the panel is drawn over the game, not as a menu: you can look around the courtroom (you can't walk
 * out of the dock). Only when you have to answer a charge or plead does it become a screen with a cursor and buttons
 * (or keys 1 to 4), and then it goes back. Esc doesn't get you out of court.
 */
public final class TrialScreen extends Screen {
    private static final int PAPER = 0xE8EDE0C2;
    private static final int INK = 0xFF3B2A1A;
    private static final int RED_INK = 0xFF8E1B1B;
    private static final int GOLD = 0xFFC9A55A;
    private static final float VOTE_START = 1.5f, VOTE_EACH = 1.2f;

    /** a line as shown: who, what, the server's line number (-1 for the court's reply to an answer), and its notes */
    private record Entry(String speaker, String text, int index, JsonObject meta) {
    }

    private JsonObject data;
    private final List<String> kidnap = new ArrayList<>();
    private final List<Entry> lines = new ArrayList<>();
    private JsonObject verdict;
    /** 0 taken in the night, 1 the court speaks, 2 plead, 3 the verdict */
    private int phase;
    private int shown;
    private long phaseStart = Util.getMillis();
    private long lastLine = Util.getMillis();
    private boolean pleaded;
    /** the line (entry) waiting for an answer, or -1 */
    private int asking = -1;
    private final Set<Integer> answered = new HashSet<>();
    private final Set<Integer> told = new HashSet<>();
    private int panelX, panelY, panelW, panelH;

    /** the trial in progress (drawn over the game, or the open screen when you must choose), or null */
    private static TrialScreen active;
    /** CI screenshots: hide the trial for a moment to look at the courtroom on its own */
    public static boolean testHide;
    private static long lastClick;
    private static int awayTicks;
    private static final ResourceLocation COURT = ResourceLocation.fromNamespaceAndPath("half_a_god", "court");
    private static final java.util.Set<ResourceLocation> HIDDEN = java.util.Set.of(VanillaGuiLayers.HOTBAR, VanillaGuiLayers.JUMP_METER,
            VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.EXPERIENCE_LEVEL,
            ResourceLocation.fromNamespaceAndPath("half_a_god", "reputation"));

    public TrialScreen(String json) {
        super(Component.literal("The Court of the Watch"));
        read(json, true);
    }

    /** a new copy of the court's script (a resend, a rival's words, or the verdict) */
    public void update(String json) {
        read(json, false);
        rebuild();
    }

    private void read(String json, boolean first) {
        data = JsonParser.parseString(json).getAsJsonObject();
        kidnap.clear();
        lines.clear();
        if (data.has("kidnap")) {
            data.getAsJsonArray("kidnap").forEach(e -> kidnap.add(e.getAsString()));
        }
        JsonObject ans = data.has("answered") ? data.getAsJsonObject("answered") : new JsonObject();
        JsonArray src = data.getAsJsonArray("lines");
        for (int i = 0; i < src.size(); i++) {
            JsonArray a = src.get(i).getAsJsonArray();
            JsonObject meta = a.size() > 2 && a.get(2).isJsonObject() ? a.get(2).getAsJsonObject() : null;
            lines.add(new Entry(a.get(0).getAsString(), a.get(1).getAsString(), i, meta));
            if (ans.has(Integer.toString(i))) {
                answered.add(i);
                Entry r = reaction(meta, ans.get(Integer.toString(i)).getAsString());
                if (r != null) {
                    lines.add(r);
                }
            }
        }
        verdict = data.has("verdict") ? data.getAsJsonObject("verdict") : null;
        boolean resume = data.has("resume") && data.get("resume").getAsBoolean();
        if (verdict != null) {
            if (phase != 3) {
                setPhase(3);
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.6f, 0.5f));
                }
            }
            shown = lines.size();
        } else if (resume) {
            setPhase(2);
            shown = lines.size();
        } else if (first) {
            setPhase(kidnap.isEmpty() ? 1 : 0);
            shown = 0;
        }
        shown = Math.min(shown, lines.size());
    }

    private Entry reaction(JsonObject meta, String answer) {
        if (meta == null || !data.has("react")) {
            return null;
        }
        JsonObject r = data.getAsJsonObject("react");
        String key = switch (answer) {
            case "deny" -> meta.get("w").getAsInt() >= 2 ? "deny_proven" : "deny_weak";
            case "explain" -> "explain";
            default -> "silent_answer";
        };
        if (!r.has(key)) {
            return null;
        }
        JsonArray a = r.getAsJsonArray(key);
        return new Entry(a.get(0).getAsString(), a.get(1).getAsString(), -1, null);
    }

    private void setPhase(int p) {
        phase = p;
        phaseStart = Util.getMillis();
        lastLine = phaseStart;
    }

    private float since() {
        return (Util.getMillis() - phaseStart) / 1000f;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private int pleaCols() {
        JsonArray pleas = data.getAsJsonArray("pleas");
        return pleas.size() > 2 && panelW >= 440 ? Math.min(4, pleas.size()) : 2;
    }

    /** a panel along the bottom: a low one while the court just talks (more of the court in view), taller for choices */
    private void layout() {
        panelW = Math.min(520, width - 20);
        boolean compact = (phase == 1 && asking < 0) || (phase == 2 && pleaded);
        panelH = compact ? Math.max(80, Math.min(110, height / 4)) : Math.max(100, Math.min(160, height * 2 / 5));
        panelX = (width - panelW) / 2;
        panelY = height - panelH - 6;
    }

    private void rebuild() {
        clearWidgets();
        layout();
        if (phase == 1 && asking >= 0) {
            JsonArray answers = data.has("answers") ? data.getAsJsonArray("answers") : new JsonArray();
            int n = Math.max(1, answers.size());
            int bw = (panelW - 24 - (n - 1) * 8) / n;
            for (int i = 0; i < answers.size(); i++) {
                JsonObject a = answers.get(i).getAsJsonObject();
                String id = a.get("id").getAsString();
                Button b = Button.builder(Component.literal((i + 1) + ". " + a.get("label").getAsString()), btn -> answer(id))
                        .bounds(panelX + 12 + i * (bw + 8), panelY + panelH - 28, bw, 20).build();
                if (a.has("hint")) {
                    b.setTooltip(Tooltip.create(Component.literal(a.get("hint").getAsString())));
                }
                addRenderableWidget(b);
            }
        } else if (phase == 2 && !pleaded) {
            JsonArray pleas = data.getAsJsonArray("pleas");
            int cols = pleaCols();
            int bw = (panelW - 24 - (cols - 1) * 8) / cols;
            int rows = (pleas.size() + cols - 1) / cols;
            for (int i = 0; i < pleas.size(); i++) {
                JsonObject p = pleas.get(i).getAsJsonObject();
                String id = p.get("id").getAsString();
                int bx = panelX + 12 + (i % cols) * (bw + 8);
                int by = panelY + panelH - 8 - rows * 22 + (i / cols) * 22;
                Button b = Button.builder(Component.literal((i + 1) + ". " + p.get("label").getAsString()), btn -> plead(id)).bounds(bx, by, bw, 20).build();
                if (p.has("hint")) {
                    b.setTooltip(Tooltip.create(Component.literal(p.get("hint").getAsString())));
                }
                addRenderableWidget(b);
            }
        }
    }

    private float votesDone() {
        int n = verdict != null && verdict.has("votes") ? verdict.getAsJsonArray("votes").size() : 0;
        return n == 0 ? 0 : VOTE_START + n * VOTE_EACH + 0.6f;
    }

    private void plead(String id) {
        pleaded = true;
        PacketDistributor.sendToServer(new Payloads.Plea(id));
        rebuild();
    }

    private void answer(String id) {
        if (asking < 0) {
            return;
        }
        Entry e = lines.get(asking);
        answered.add(e.index());
        PacketDistributor.sendToServer(new Payloads.Act("answer", Integer.toString(e.index()), id));
        Entry r = reaction(e.meta(), id);
        if (r != null) {
            lines.add(asking + 1, r);
            shown = asking + 2;
        }
        asking = -1;
        lastLine = Util.getMillis();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.7f));
        }
        rebuild();
    }

    private boolean needsAnswer(Entry e) {
        JsonObject m = e.meta();
        return e.index() >= 0 && m != null && m.has("charge") && !m.has("unread") && !answered.contains(e.index());
    }

    @Override
    public void tick() {
        // the court waits for you: only its first line comes by itself
        if (phase == 1 && asking < 0 && shown == 0 && !lines.isEmpty()) {
            next();
        }
    }

    private float kidnapDone() {
        return kidnap.isEmpty() ? 0 : 1.0f + (kidnap.size() - 1) * 2.6f + 1.2f;
    }

    /** a click, Space or Enter: the next line (or all of what's fading in, or out of the court at the end) */
    public void click() {
        if (phase == 0) {
            if (since() < kidnapDone()) {
                phaseStart = Util.getMillis() - (long) (kidnapDone() * 1000);
            } else {
                setPhase(1);
            }
            return;
        }
        if (phase == 1 && asking < 0) {
            next();
        } else if (phase == 3 && verdict != null) {
            if (since() < votesDone()) {
                phaseStart = Util.getMillis() - (long) (votesDone() * 1000);
            } else {
                finish();
            }
        }
    }

    /** out of court: the server takes you to your cell, or home */
    private void finish() {
        PacketDistributor.sendToServer(new Payloads.Plea("continue"));
        if (active == this) {
            active = null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == this) {
            mc.setScreen(null);
        }
    }

    /** answering a charge, or pleading: the only times you need a cursor */
    private boolean needsInput() {
        return (phase == 1 && asking >= 0) || (phase == 2 && !pleaded);
    }

    /** opens the screen when there's a choice to make, and closes it again after (so you can look around) */
    private void sync() {
        Minecraft mc = Minecraft.getInstance();
        if (testHide) {
            if (mc.screen == this) {
                mc.setScreen(null);
            }
            return;
        }
        if (needsInput()) {
            if (mc.screen == null) {
                mc.setScreen(this);
            }
        } else if (mc.screen == this) {
            mc.setScreen(null);
        }
    }

    // ------------------------------------------------------------------ the trial, from outside the screen
    /** the court's script arrives (the first time, a resend, a rival's words, or the verdict) */
    public static void receive(String json) {
        if (active == null || (active.phase == 3 && !json.contains("\"verdict\""))) {
            // (a new trial, never an old verdict left on screen)
            active = new TrialScreen(json);
        } else {
            active.update(json);
        }
        active.sync();
    }

    public static TrialScreen active() {
        return active;
    }

    /** the panel, drawn over the game whenever the trial isn't the open screen */
    public static void hud(GuiGraphics g, DeltaTracker dt) {
        TrialScreen t = active;
        Minecraft mc = Minecraft.getInstance();
        if (t == null || testHide || mc.screen == t) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        if (t.minecraft == null || t.width != w || t.height != h) {
            t.init(mc, w, h);
        }
        t.render(g, -1, -1, dt.getGameTimeDeltaPartialTick(true));
    }

    public static void clientTick() {
        TrialScreen t = active;
        Minecraft mc = Minecraft.getInstance();
        if (t == null) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            active = null;
            return;
        }
        if (mc.screen != t) {
            t.tick();
        }
        t.sync();
        // taken out of court some other way (the server ended the trial): the panel goes too
        if (t.phase >= 1 && !mc.level.dimension().location().equals(COURT)) {
            if (++awayTicks > 100) {
                active = null;
                if (mc.screen == t) {
                    mc.setScreen(null);
                }
            }
        } else {
            awayTicks = 0;
        }
    }

    private static boolean listening() {
        Minecraft mc = Minecraft.getInstance();
        return active != null && !testHide && mc.screen == null;
    }

    /** a click in the court moves the court on (and never hits anything) */
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered e) {
        if (!listening()) {
            return;
        }
        e.setCanceled(true);
        e.setSwingHand(false);
        if ((e.isAttack() || e.isUseItem()) && Util.getMillis() - lastClick > 250) {
            lastClick = Util.getMillis();
            active.click();
        }
    }

    public static void onKey(InputEvent.Key e) {
        if (listening() && e.getAction() == GLFW.GLFW_PRESS
                && (e.getKey() == GLFW.GLFW_KEY_SPACE || e.getKey() == GLFW.GLFW_KEY_ENTER || e.getKey() == GLFW.GLFW_KEY_KP_ENTER)) {
            active.click();
        }
    }

    /** you stay in the dock: you can look around, not walk (or jump) */
    public static void onMovement(MovementInputUpdateEvent e) {
        if (active != null && active.phase >= 1) {
            var in = e.getInput();
            in.leftImpulse = 0;
            in.forwardImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
    }

    /** the hotbar and the bars are put away while the court sits (the panel is where they'd be) */
    public static void onLayer(RenderGuiLayerEvent.Pre e) {
        if (active != null && !testHide && HIDDEN.contains(e.getName())) {
            e.setCanceled(true);
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        active = null;
    }

    private void next() {
        if (phase == 0) {
            setPhase(1);
            return;
        }
        if (phase != 1 || asking >= 0) {
            return;
        }
        if (shown < lines.size()) {
            Entry e = lines.get(shown);
            shown++;
            lastLine = Util.getMillis();
            if (e.index() >= 0 && told.add(e.index())) {
                PacketDistributor.sendToServer(new Payloads.Act("line", Integer.toString(e.index()), ""));
            }
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.5f));
            }
            if (needsAnswer(e)) {
                asking = shown - 1;
                rebuild();
                return;
            }
        }
        if (shown >= lines.size()) {
            setPhase(2);
            rebuild();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        if (phase == 0) {
            g.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        layout();
        // no darkening and no blur: the court is the background. A soft shade behind the panel, then dark wood and paper
        g.fillGradient(0, panelY - 30, width, height, 0x00000000, 0x70000000);
        g.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelY + panelH + 4, 0xD02A1C11);
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY - 2, GOLD);
        g.fill(panelX - 3, panelY + panelH + 2, panelX + panelW + 3, panelY + panelH + 3, GOLD);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PAPER);
        // the banner at the top
        String head = "THE COURT OF THE WATCH";
        String sub = sub();
        int bw = Math.max(font.width(head), font.width(sub)) + 28;
        int bx = width / 2 - bw / 2;
        g.fill(bx, 4, bx + bw, 30, 0xB0140D08);
        g.fill(bx, 30, bx + bw, 31, GOLD);
    }

    private String sub() {
        return (data.has("act") && !data.get("act").getAsString().isEmpty() ? data.get("act").getAsString() + "  ·  " : "")
                + "day " + (data.has("day") ? data.get("day").getAsLong() : 0);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (phase == 0) {
            renderKidnap(g);
            if (since() >= kidnapDone()) {
                String hint = "Click, Space or Enter to continue";
                g.drawString(font, hint, width / 2 - font.width(hint) / 2, height - 24, 0x80E8DCC4, false);
            }
            return;
        }
        String head = "THE COURT OF THE WATCH";
        g.drawString(font, head, width / 2 - font.width(head) / 2, 8, GOLD, false);
        String sub = sub();
        g.drawString(font, Component.literal(sub).withStyle(ChatFormatting.ITALIC), width / 2 - font.width(sub) / 2, 19, 0xFFB8A27A, false);
        int ix = panelX + 12, iw = panelW - 24;
        int rows = 0;
        if (phase == 2 && !pleaded) {
            JsonArray pleas = data.getAsJsonArray("pleas");
            rows = (pleas.size() + pleaCols() - 1) / pleaCols();
        }
        int top = panelY + 8;
        int bottom = phase == 1 && asking >= 0 ? panelY + panelH - 44
                : phase == 2 ? panelY + panelH - 8 - rows * 22 - (pleaded ? 6 : 16) : panelY + panelH - 16;
        if (phase != 3) {
            transcript(g, ix, iw, top, bottom);
        }
        if (phase == 1 && asking >= 0) {
            g.drawString(font, "HOW DO YOU ANSWER THE CHARGE?", ix, bottom + 4, RED_INK, false);
        } else if (phase == 1) {
            String hint = "Click, Space or Enter to continue";
            boolean blink = (Util.getMillis() / 600) % 2 == 0;
            g.drawString(font, (blink ? "\u25B6 " : "  ") + hint, panelX + panelW - 8 - font.width("\u25B6 " + hint), panelY + panelH - 12, 0xFF9A8662, false);
        } else if (phase == 2 && !pleaded) {
            g.drawString(font, "HOW DO YOU PLEAD?", ix, bottom + 3, RED_INK, false);
        } else if (phase == 2) {
            g.drawString(font, Component.literal("The jury is out...").withStyle(ChatFormatting.ITALIC), ix, panelY + panelH - 16, 0xFF6B5638, false);
        } else if (phase == 3 && verdict != null) {
            renderVerdict(g, ix, iw);
            if (since() >= votesDone()) {
                String hint = "Click, Space or Enter to leave the court";
                g.drawString(font, hint, panelX + panelW - 8 - font.width(hint), panelY + panelH - 12, 0xFF9A8662, false);
            }
        }
    }

    private void transcript(GuiGraphics g, int ix, int iw, int top, int bottom) {
        // the newest line at the bottom, the ones before it fading upwards
        List<FormattedCharSequence> out = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int n = phase == 1 ? shown : lines.size();
        for (int i = 0; i < n; i++) {
            Entry l = lines.get(i);
            boolean quiet = l.text().startsWith("(") || l.text().contains("cannot be found");
            boolean latest = i == n - 1;
            Component c = Component.empty().append(Component.literal(l.speaker() + ": ").withStyle(latest ? ChatFormatting.DARK_RED : ChatFormatting.RESET)
                            .withStyle(ChatFormatting.BOLD))
                    .append(quiet ? Component.literal(l.text()).withStyle(ChatFormatting.ITALIC) : Component.literal(l.text()));
            for (FormattedCharSequence seq : font.split(c, iw)) {
                out.add(seq);
                colors.add(quiet ? 0xFF6B5638 : latest ? INK : 0xFF7A6448);
            }
            if (!latest) {
                out.add(FormattedCharSequence.EMPTY);
                colors.add(INK);
            }
        }
        int maxLines = Math.max(1, (bottom - top) / 10);
        int from = Math.max(0, out.size() - maxLines);
        int y = top + Math.max(0, maxLines - (out.size() - from)) * 10;
        g.enableScissor(panelX, top, panelX + panelW, bottom);
        for (int i = from; i < out.size(); i++) {
            g.drawString(font, out.get(i), ix, y, colors.get(i), false);
            y += 10;
        }
        g.disableScissor();
    }

    private void renderVerdict(GuiGraphics g, int ix, int iw) {
        float t = since();
        JsonArray votes = verdict.has("votes") ? verdict.getAsJsonArray("votes") : new JsonArray();
        // the jury, one by one
        if (votes.size() > 0) {
            g.drawString(font, Component.literal("THE JURY").withStyle(ChatFormatting.BOLD), ix, panelY + 10, INK, false);
            int guilty = 0, not = 0;
            int cw = Math.min(70, (iw - 70) / votes.size());
            JsonObject words = data.has("votesText") ? data.getAsJsonObject("votesText") : new JsonObject();
            for (int i = 0; i < votes.size(); i++) {
                int x = ix + 70 + i * cw;
                boolean v = votes.get(i).getAsBoolean();
                if (t >= VOTE_START + i * VOTE_EACH) {
                    if (v) {
                        guilty++;
                    } else {
                        not++;
                    }
                    String word = v ? (words.has("guilty") ? words.get("guilty").getAsString() : "Guilty.") : (words.has("not") ? words.get("not").getAsString() : "Not guilty.");
                    g.drawString(font, "Juror " + (i + 1), x, panelY + 8, 0xFF8A7656, false);
                    g.drawString(font, word, x, panelY + 18, v ? RED_INK : 0xFF2E6B2E, false);
                } else {
                    g.drawString(font, "Juror " + (i + 1), x, panelY + 8, 0x608A7656, false);
                    g.drawString(font, "...", x, panelY + 18, 0x608A7656, false);
                }
            }
            if (guilty + not > 0) {
                g.drawString(font, guilty + " to " + not, ix, panelY + 22, 0xFF6B5638, false);
            }
        }
        if (t < votesDone()) {
            return;
        }
        boolean guilty = verdict.get("guilty").getAsBoolean();
        String big = guilty ? "GUILTY" : "FREE TO GO";
        int top = votes.size() > 0 ? panelY + 36 : panelY + 10;
        g.pose().pushPose();
        g.pose().translate(ix, top, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, big, 0, 0, guilty ? RED_INK : 0xFF2E6B2E, false);
        g.pose().popPose();
        int vy = top + 22;
        for (JsonElement e : verdict.getAsJsonArray("lines")) {
            JsonArray a = e.getAsJsonArray();
            Component c = Component.empty().append(Component.literal(a.get(0).getAsString() + ": ").withStyle(ChatFormatting.BOLD))
                    .append(Component.literal(a.get(1).getAsString()));
            for (FormattedCharSequence seq : font.split(c, iw - 110)) {
                if (vy < panelY + panelH - 8) {
                    g.drawString(font, seq, ix, vy, INK, false);
                }
                vy += 10;
            }
        }
    }

    private void renderKidnap(GuiGraphics g) {
        float t = since();
        int w = Math.min(320, width - 40);
        int y = height / 2 - kidnap.size() * 14;
        for (int i = 0; i < kidnap.size(); i++) {
            float k = Math.max(0f, Math.min(1f, (t - 1.0f - i * 2.6f) / 1.2f));
            for (FormattedCharSequence line : font.split(Component.literal(kidnap.get(i)), w)) {
                if (k > 0.02f) {
                    g.drawString(font, line, width / 2 - font.width(line) / 2, y, ((int) (k * 255) << 24) | 0xE8DCC4, false);
                }
                y += 11;
            }
            y += 10;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (!needsInput()) {
            click();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) && !needsInput()) {
            click();
            return true;
        }
        // 1 to 4: the answers to a charge, or the pleas, in order
        int n = key - GLFW.GLFW_KEY_1;
        if (n >= 0 && n < 4) {
            if (phase == 1 && asking >= 0 && data.has("answers") && n < data.getAsJsonArray("answers").size()) {
                answer(data.getAsJsonArray("answers").get(n).getAsJsonObject().get("id").getAsString());
                return true;
            }
            if (phase == 2 && !pleaded && n < data.getAsJsonArray("pleas").size()) {
                plead(data.getAsJsonArray("pleas").get(n).getAsJsonObject().get("id").getAsString());
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** CI screenshots: is the court waiting on an answer to a charge? */
    public boolean isAsking() {
        return asking >= 0;
    }

    /** CI screenshots: a plea, as if its button were clicked */
    public void pleadForTest(String id) {
        plead(id);
    }

    /** CI screenshots: answer the charge on screen */
    public void answerForTest(String id) {
        answer(id);
    }
}

package dev.halfagod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.halfagod.HalfAGod;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Trial. From Act II on, if you're Fallen or Wicked, the Watch can take you in your sleep (once per act, on a random
 * night). You wake in chains in a court of its own, hear witnesses tell what you really did (the ones you killed can't),
 * and plead. Guilty means the obsidian cells, and there's no sleeping through it and no way out: a day in the cells is a
 * real Minecraft day (twenty minutes) spent in the cell, counted only while you're there. If you're high enough in the
 * Gilded Hand, Silas's men come for you on the second day. Either way you wake back home, in your own bed, with your things.
 *
 * States (RepData.trialState): 0 free, 1 on trial (in the dock), 2 in prison.
 */
public final class Trial {
    public static final ResourceKey<Level> COURT = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "court"));
    private static final float CHANCE = 1f / 3f;
    private static final int BRIBE = 16;
    /** a day in the cells: a full Minecraft day, in real time */
    public static final int DAY = 24000;

    /** server tick at which to do something for a player: show the court, take them away after the verdict, show a dream */
    private static final Map<UUID, Long> SHOW_AT = new HashMap<>();
    private static final Map<UUID, Long> MOVE_AT = new HashMap<>();
    private static final Map<UUID, Long> DREAM_AT = new HashMap<>();
    private static final Map<UUID, String> DREAM = new HashMap<>();
    private static final Map<UUID, Long> AWAY_SINCE = new HashMap<>();
    /** who's speaking in court and who's on the witness stand (for the court's people), and when the jury began to vote */
    private record Act(String speaker, String witness, String job, boolean hidden) {
    }

    private static final Map<UUID, Act> ACT = new HashMap<>();
    private static final Map<UUID, Long> VOTE_AT = new HashMap<>();
    private static boolean warned;

    private Trial() {
    }

    /** game tests only: the test server never loads dimensions from data packs, so the tests hold court in another level */
    public static ServerLevel testCourt;

    public static ServerLevel court(MinecraftServer server) {
        if (testCourt != null) {
            return testCourt;
        }
        ServerLevel l = server.getLevel(COURT);
        if (l == null && !warned) {
            warned = true;
            HalfAGod.LOG.warn("Half a God: the court dimension (half_a_god:court) isn't loaded, so there will be no trials.");
        }
        return l;
    }

    public static boolean inCourt(ServerPlayer player) {
        return player.level().dimension() == COURT || (testCourt != null && player.level() == testCourt
                && Math.abs(player.getX() - CourtBuilder.origin(CourtData.get(testCourt).slot(player.getUUID())).getX()) < 64);
    }

    private static long now(ServerPlayer player) {
        return player.getServer().getTickCount();
    }

    // ------------------------------------------------------------------ when the Watch comes
    private static boolean due(ServerPlayer player, RepData d) {
        if (d.trialState != 0 || d.pendingKidnap || player.isCreative() || player.isSpectator()) {
            return false;
        }
        Set<String> traits = Meb.traits(player);
        if (Dreams.actIndex(traits) < 2 || Reputation.tierIndex(d.value) > 1 || Fates.storyOver(traits)) {
            return false;
        }
        return !d.trialActs.contains(Dreams.actTrait(traits)) && court(player.getServer()) != null;
    }

    /** On waking from a full night: maybe the Watch came. True if they did (no dream tonight). */
    public static boolean onWake(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (!due(player, d) || player.getRandom().nextFloat() >= CHANCE) {
            return false;
        }
        d.pendingKidnap = true;
        return true;
    }

    /** Takes the player to court now. */
    public static boolean kidnap(ServerPlayer player) {
        return kidnap(player, Set.of());
    }

    /** extra: story choices the court treats as yours for this trial only (operators' "witnesses" test) */
    public static boolean kidnap(ServerPlayer player, Set<String> extra) {
        RepData d = Reputation.data(player);
        d.pendingKidnap = false;
        ServerLevel court = court(player.getServer());
        if (court == null || d.trialState != 0) {
            return false;
        }
        Set<String> traits = Meb.traits(player);
        String act = Dreams.actTrait(traits);
        d.trialActs.add(act.isEmpty() ? "none" : act);
        int slot = CourtData.get(court).slot(player.getUUID());
        CourtBuilder.ensure(court, slot);
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        saveSpawn(player, d);
        confiscate(player, d);
        d.trialState = 1;
        d.trialVerdict = "";
        d.trialAnswers = "";
        ACT.remove(player.getUUID());
        Set<String> record = new java.util.HashSet<>(traits);
        record.addAll(extra);
        d.trialScript = script(player, d, record).toString();
        BlockPos dock = CourtBuilder.origin(slot).offset(CourtBuilder.DOCK);
        player.teleportTo(court, dock.getX() + 0.5, dock.getY(), dock.getZ() + 0.5, 180f, 0f);
        player.setRespawnPosition(COURT, dock, 180f, true, false);
        player.clearFire();
        SHOW_AT.put(player.getUUID(), now(player) + 60);
        Rivals.onKidnap(player);
        return true;
    }

    // ------------------------------------------------------------------ the court's script
    private static String fill(String s, String key, String value) {
        return s.replace("{" + key + "}", value);
    }

    private static JsonArray line(String speaker, String text) {
        JsonArray a = new JsonArray();
        a.add(speaker);
        a.add(text);
        return a;
    }

    private static JsonArray line(JsonElement scriptLine, String key, String value) {
        JsonArray src = scriptLine.getAsJsonArray();
        return line(src.get(0).getAsString(), fill(src.get(1).getAsString(), key, value));
    }

    private record Charge(int weight, JsonArray line, boolean heard, String id) {
    }

    /** who's who on the witness stand, by what they call themselves */
    private static String job(String witness) {
        String w = witness.toLowerCase(java.util.Locale.ROOT);
        if (w.contains("brother") || w.contains("priest") || w.contains("pilgrim")) return "cleric";
        if (w.contains("librarian") || w.contains("scholar")) return "librarian";
        if (w.contains("shepherd")) return "shepherd";
        if (w.contains("watch")) return "weaponsmith";
        if (w.contains("shopkeeper") || w.contains("assayer") || w.contains("merchant")) return "cartographer";
        if (w.contains("famil") || w.contains("farmer") || w.contains("widow")) return "farmer";
        if (w.contains("smith")) return "toolsmith";
        return "none";
    }

    private static JsonArray meta(JsonArray line, String id, int weight, String witness, boolean confessed) {
        JsonObject m = new JsonObject();
        m.addProperty("charge", id);
        m.addProperty("w", weight);
        m.addProperty("job", job(witness));
        m.addProperty("confessed", confessed);
        line.add(m);
        return line;
    }

    private static Charge charge(RepData d, String id, int weight, String witness, String testimony) {
        // a confessed charge is still read out, but weighs less
        boolean confessed = d.confessed.contains(id);
        int w = confessed ? Math.max(0, weight - 1) : weight;
        return new Charge(w, meta(line(witness, testimony), id, w, witness, confessed), true, id);
    }

    public static JsonObject script(ServerPlayer player, RepData d, Set<String> traits) {
        JsonObject s = StoryData.INSTANCE.trialScript;
        JsonObject out = new JsonObject();
        JsonObject kidnap = StoryData.INSTANCE.specialDreams.get("kidnap");
        out.add("kidnap", kidnap != null ? kidnap.getAsJsonArray("lines") : new JsonArray());
        JsonArray lines = new JsonArray();
        if (s.has("open")) {
            s.getAsJsonArray("open").forEach(l -> lines.add(l.deepCopy()));
        }
        if (d.convictions > 0) {
            lines.add(line("The Watch Captain", "The accused has stood in this dock before, your honour. " + d.convictions
                    + (d.convictions == 1 ? " conviction." : " convictions.")));
        }
        List<Charge> heard = new ArrayList<>();
        List<Charge> silent = new ArrayList<>();
        for (JsonObject c : StoryData.INSTANCE.charges) {
            if (!traits.contains(c.get("trait").getAsString())) {
                continue;
            }
            boolean silenced = false;
            for (JsonElement t : c.getAsJsonArray("silenced")) {
                silenced |= traits.contains(t.getAsString());
            }
            String id = c.get("id").getAsString();
            if (silenced) {
                silent.add(new Charge(0, line("The Watch Captain", fill(s.get("silent_witness").getAsString(), "witness",
                        c.get("witness").getAsString())), false, id));
            } else {
                heard.add(charge(d, id, c.get("weight").getAsInt(), c.get("witness").getAsString(), c.get("testimony").getAsString()));
            }
        }
        // what everyday life left behind
        int villagers = player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
        if (villagers > 0 && s.has("villagers")) {
            JsonArray l = line(s.get("villagers"), "n", Integer.toString(villagers));
            heard.add(charge(d, "villagers", Math.min(3, 1 + villagers / 3), l.get(0).getAsString(), l.get(1).getAsString()));
        }
        if (d.looted.size() >= 3 && s.has("stores")) {
            JsonArray l = line(s.get("stores"), "n", Integer.toString(d.looted.size()));
            heard.add(charge(d, "stores", 1, l.get(0).getAsString(), l.get(1).getAsString()));
        }
        if (d.contractsDone >= 3 && s.has("hand")) {
            JsonArray l = line(s.get("hand"), "n", Integer.toString(d.contractsDone));
            heard.add(charge(d, "hand", 1, l.get(0).getAsString(), l.get(1).getAsString()));
        }
        if (d.shrinePrayers > 0 && s.has("shrines")) {
            JsonArray l = line(s.get("shrines"), "n", Integer.toString(d.shrinePrayers));
            heard.add(charge(d, "shrines", 1, l.get(0).getAsString(), l.get(1).getAsString()));
        }
        heard.sort(Comparator.comparingInt(Charge::weight).reversed());
        int weight = 0;
        int shown = 0;
        for (Charge c : heard) {
            weight += c.weight();
            if (shown++ < 7) {
                lines.add(c.line());
                // a priest heard it first
                if (d.confessed.contains(c.id()) && s.has("confessed")) {
                    lines.add(s.get("confessed").deepCopy());
                }
            } else {
                // charges the court doesn't read out still count, but can't be answered
                c.line().get(2).getAsJsonObject().addProperty("unread", true);
            }
        }
        if (heard.size() > 7) {
            lines.add(line("The Watch Captain", "There are " + (heard.size() - 7) + " more, your honour. I think the court has heard enough."));
        }
        for (int i = 0; i < Math.min(3, silent.size()); i++) {
            lines.add(silent.get(i).line());
        }
        // your standing with the Hand and the Watch
        int mods = 0;
        boolean advocate = Ranks.hand(player) >= 1 && s.has("advocate");
        if (advocate) {
            lines.add(s.get("advocate").deepCopy());
            lines.add(s.get("advocate_judge").deepCopy());
            mods -= Math.min(2, Ranks.hand(player));
        }
        if (d.watchRank >= 1 && s.has("watch_lenient")) {
            boolean wicked = Reputation.tierIndex(d.value) == 0;
            lines.add(s.get(wicked ? "watch_harsh" : "watch_lenient").deepCopy());
            mods += wicked ? 1 : -1;
        }
        if (s.has("plea")) {
            lines.add(s.get("plea").deepCopy());
        }
        out.add("lines", lines);
        out.addProperty("weight", weight);
        out.addProperty("mods", mods);
        out.addProperty("advocate", advocate);
        JsonObject react = new JsonObject();
        for (String k : new String[]{"deny_proven", "deny_weak", "explain", "silent_answer"}) {
            if (s.has(k)) {
                react.add(k, s.get(k).deepCopy());
            }
        }
        out.add("react", react);
        JsonArray answers = new JsonArray();
        StoryData.INSTANCE.answers.forEach(a -> answers.add(a.deepCopy()));
        out.add("answers", answers);
        out.add("votesText", StoryData.INSTANCE.votes.deepCopy());
        out.addProperty("heard", heard.size());
        out.addProperty("silent", silent.size());
        JsonArray pleas = new JsonArray();
        boolean canBribe = Ranks.hand(player) >= 1 && emeraldsIn(player, d) >= BRIBE;
        for (JsonObject p : StoryData.INSTANCE.pleas) {
            if (!p.get("id").getAsString().equals("bribe") || canBribe) {
                pleas.add(p.deepCopy());
            }
        }
        out.add("pleas", pleas);
        out.addProperty("day", player.getServer().overworld().getDayTime() / 24000L + 1);
        out.addProperty("act", StoryData.INSTANCE.acts.getOrDefault(Dreams.actTrait(traits), ""));
        return out;
    }

    private static void show(ServerPlayer player, RepData d, boolean resume) {
        if (d.trialScript.isEmpty()) {
            return;
        }
        JsonObject o = JsonParser.parseString(d.trialScript).getAsJsonObject();
        o.addProperty("resume", resume);
        o.add("answered", answers(d));
        if (!d.trialVerdict.isEmpty()) {
            o.add("verdict", JsonParser.parseString(d.trialVerdict));
        }
        send(player, new Payloads.Trial(o.toString()));
    }

    public static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Net.send(player, payload);
    }

    // ------------------------------------------------------------------ answering each charge
    public static JsonObject answers(RepData d) {
        if (d.trialAnswers.isEmpty()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(d.trialAnswers).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static JsonObject metaOf(JsonArray line) {
        return line.size() > 2 && line.get(2).isJsonObject() ? line.get(2).getAsJsonObject() : null;
    }

    /** how heavy the case is: every charge, as answered, plus your standing with the Hand and the Watch, and any rivals' words */
    public static int weight(JsonObject script, RepData d) {
        int w = script.get("weight").getAsInt();
        JsonObject ans = answers(d);
        JsonArray lines = script.getAsJsonArray("lines");
        for (int i = 0; i < lines.size(); i++) {
            JsonObject m = metaOf(lines.get(i).getAsJsonArray());
            if (m == null || !m.has("charge") || m.has("unread") || !ans.has(Integer.toString(i))) {
                continue;
            }
            int cw = m.get("w").getAsInt();
            w += switch (ans.get(Integer.toString(i)).getAsString()) {
                case "deny" -> cw >= 2 ? 1 : -cw;
                case "explain" -> -Math.min(1, cw);
                default -> 0;
            };
        }
        w += script.has("mods") ? script.get("mods").getAsInt() : 0;
        w += script.has("rival") ? script.get("rival").getAsInt() : 0;
        return Math.max(0, w);
    }

    /** what the court says back to an answer */
    private static JsonArray reaction(JsonObject script, JsonObject meta, String answer) {
        JsonObject r = script.has("react") ? script.getAsJsonObject("react") : new JsonObject();
        String key = switch (answer) {
            case "deny" -> meta.get("w").getAsInt() >= 2 ? "deny_proven" : "deny_weak";
            case "explain" -> "explain";
            default -> "silent_answer";
        };
        return r.has(key) ? r.getAsJsonArray(key) : null;
    }

    /** the transcript as it was spoken: every line, with the court's reply after each answer */
    private static JsonArray transcript(JsonObject script, RepData d) {
        JsonArray out = new JsonArray();
        JsonObject ans = answers(d);
        JsonArray lines = script.getAsJsonArray("lines");
        for (int i = 0; i < lines.size(); i++) {
            JsonArray l = lines.get(i).getAsJsonArray();
            out.add(line(l.get(0).getAsString(), l.get(1).getAsString()));
            JsonObject m = metaOf(l);
            if (m != null && ans.has(Integer.toString(i))) {
                JsonArray r = reaction(script, m, ans.get(Integer.toString(i)).getAsString());
                if (r != null) {
                    out.add(line(r.get(0).getAsString(), r.get(1).getAsString()));
                }
            }
        }
        return out;
    }

    /** The accused's answer to one charge (deny, explain, silent). */
    public static void onAnswer(ServerPlayer player, int index, String answer) {
        RepData d = Reputation.data(player);
        if (d.trialState != 1 || !d.trialVerdict.isEmpty() || d.trialScript.isEmpty()
                || !(answer.equals("deny") || answer.equals("explain") || answer.equals("silent"))) {
            return;
        }
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        JsonArray lines = script.getAsJsonArray("lines");
        if (index < 0 || index >= lines.size()) {
            return;
        }
        JsonObject m = metaOf(lines.get(index).getAsJsonArray());
        JsonObject ans = answers(d);
        if (m == null || !m.has("charge") || m.has("unread") || ans.has(Integer.toString(index))) {
            return;
        }
        ans.addProperty(Integer.toString(index), answer);
        d.trialAnswers = ans.toString();
        JsonArray r = reaction(script, m, answer);
        if (r != null) {
            speak(player, r.get(0).getAsString(), "", false);
        }
    }

    /** The accused's screen has shown line `index`: the court acts it out (the speaker turns and glows, the witness stands). */
    public static void onLineShown(ServerPlayer player, int index) {
        RepData d = Reputation.data(player);
        if (d.trialState != 1 || d.trialScript.isEmpty()) {
            return;
        }
        JsonArray lines = JsonParser.parseString(d.trialScript).getAsJsonObject().getAsJsonArray("lines");
        if (index < 0 || index >= lines.size()) {
            return;
        }
        JsonArray l = lines.get(index).getAsJsonArray();
        String speaker = l.get(0).getAsString();
        JsonObject m = metaOf(l);
        boolean missing = l.get(1).getAsString().contains("cannot be found");
        speak(player, speaker, m != null && m.has("job") ? m.get("job").getAsString() : "", missing);
        ServerLevel court = court(player.getServer());
        // a heavy charge sets the gallery muttering
        if (court != null && m != null && m.has("w") && m.get("w").getAsInt() >= 3) {
            int slot = CourtData.get(court).slot(player.getUUID());
            for (int i = 0; i < 6; i += 2) {
                net.minecraft.world.entity.Entity g = CourtBuilder.find(court, slot, "gallery" + i);
                if (g != null) {
                    court.playSound(null, g.getX(), g.getY(), g.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 0.7f + i * 0.08f);
                }
            }
        }
    }

    private static String roleOf(String speaker) {
        return switch (speaker) {
            case "The Judge" -> "judge";
            case "The Watch Captain" -> "captain";
            case "The Clerk" -> "clerk";
            case "An Advocate of the Hand" -> "advocate";
            default -> "witness";
        };
    }

    private static void speak(ServerPlayer player, String speaker, String job, boolean witnessMissing) {
        String role = roleOf(speaker);
        Act old = ACT.getOrDefault(player.getUUID(), new Act("", "", "none", false));
        Act act = role.equals("witness") ? new Act(role, speaker, job.isEmpty() ? job(speaker) : job, false)
                : new Act(role, old.witness(), old.job(), witnessMissing || old.hidden());
        ACT.put(player.getUUID(), act);
        ServerLevel court = court(player.getServer());
        if (court == null) {
            return;
        }
        int slot = CourtData.get(court).slot(player.getUUID());
        CourtBuilder.ensureNpcs(court, slot, scene(player, Reputation.data(player), court, slot), player);
        net.minecraft.world.entity.Entity e = CourtBuilder.find(court, slot, role);
        if (e instanceof net.minecraft.world.entity.LivingEntity le) {
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 70, 0, false, false));
            court.playSound(null, le.getX(), le.getY(), le.getZ(), role.equals("judge") ? SoundEvents.VILLAGER_WORK_CLERIC : SoundEvents.VILLAGER_AMBIENT,
                    SoundSource.NEUTRAL, 0.6f, 0.9f);
        }
    }

    /** what the court and the prison look like for this player right now */
    public static CourtBuilder.Scene scene(ServerPlayer player, RepData d, ServerLevel court, int slot) {
        Act a = ACT.getOrDefault(player.getUUID(), new Act("", "", "none", false));
        boolean advocate = false;
        if (d.trialState == 1 && !d.trialScript.isEmpty()) {
            JsonObject sc = JsonParser.parseString(d.trialScript).getAsJsonObject();
            advocate = sc.has("advocate") && sc.get("advocate").getAsBoolean();
        }
        net.minecraft.world.entity.npc.VillagerProfession job = jobOf(a.job());
        CourtBuilder.Scene c0 = new CourtBuilder.Scene(0, "", "", advocate, d.trialState == 1 ? a.witness() : "", job,
                d.trialState == 1 && a.hidden(), d.trialState == 1 ? a.speaker() : "", true, Double.NaN);
        return Prison.scene(player, d, court, slot, c0);
    }

    private static net.minecraft.world.entity.npc.VillagerProfession jobOf(String job) {
        return switch (job) {
            case "cleric" -> net.minecraft.world.entity.npc.VillagerProfession.CLERIC;
            case "librarian" -> net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN;
            case "shepherd" -> net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD;
            case "weaponsmith" -> net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH;
            case "cartographer" -> net.minecraft.world.entity.npc.VillagerProfession.CARTOGRAPHER;
            case "farmer" -> net.minecraft.world.entity.npc.VillagerProfession.FARMER;
            case "toolsmith" -> net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH;
            default -> net.minecraft.world.entity.npc.VillagerProfession.NONE;
        };
    }

    /** a rival's words, added to an accused player's trial before they plead (true if in time) */
    public static boolean addRivalLine(ServerPlayer accused, String name, boolean against, long day) {
        RepData d = Reputation.data(accused);
        if (d.trialState != 1 || !d.trialVerdict.isEmpty() || d.trialScript.isEmpty()) {
            return false;
        }
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        JsonObject s = StoryData.INSTANCE.trialScript;
        String key = against ? "rival_against" : "rival_for";
        if (!s.has(key)) {
            return false;
        }
        JsonArray src = s.getAsJsonArray(key);
        JsonArray l = line(fill(src.get(0).getAsString(), "name", name), fill(fill(src.get(1).getAsString(), "name", name), "day", Long.toString(day)));
        JsonObject m = new JsonObject();
        m.addProperty("rival", true);
        m.addProperty("job", "none");
        l.add(m);
        JsonArray lines = script.getAsJsonArray("lines");
        JsonArray out = new JsonArray();
        for (int i = 0; i < lines.size(); i++) {
            if (i == lines.size() - 1) {
                out.add(l);
            }
            out.add(lines.get(i));
        }
        // answers are kept by line number: everything from the plea on moves down one
        JsonObject ans = answers(d);
        JsonObject moved = new JsonObject();
        for (Map.Entry<String, JsonElement> e : ans.entrySet()) {
            int i = Integer.parseInt(e.getKey());
            moved.add(Integer.toString(i >= lines.size() - 1 ? i + 1 : i), e.getValue());
        }
        d.trialAnswers = moved.toString();
        script.add("lines", out);
        script.addProperty("rival", (script.has("rival") ? script.get("rival").getAsInt() : 0) + (against ? 2 : -1));
        d.trialScript = script.toString();
        if (!SHOW_AT.containsKey(accused.getUUID())) {
            show(accused, d, false);
        }
        return true;
    }

    // ------------------------------------------------------------------ the plea and the verdict
    public static void onPlea(ServerPlayer player, String plea) {
        RepData d = Reputation.data(player);
        if (d.trialState != 1 || d.trialScript.isEmpty()) {
            return;
        }
        if (plea.equals("continue")) {
            if (!d.trialVerdict.isEmpty()) {
                MOVE_AT.remove(player.getUUID());
                proceed(player, d);
            }
            return;
        }
        if (!d.trialVerdict.isEmpty()) {
            show(player, d, true);
            return;
        }
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        boolean allowed = false;
        for (JsonElement p : script.getAsJsonArray("pleas")) {
            allowed |= p.getAsJsonObject().get("id").getAsString().equals(plea);
        }
        if (!allowed) {
            return;
        }
        int w = weight(script, d);
        int base = Math.min(7, Math.max(1, Math.min(5, 1 + w / 2)) + d.convictions);
        JsonObject s = StoryData.INSTANCE.trialScript;
        boolean guilty;
        int days = base;
        String says;
        if (w == 0) {
            guilty = false;
            says = "no_case";
        } else {
            switch (plea) {
                case "guilty" -> {
                    guilty = true;
                    days = Math.max(1, base - 1);
                    says = "guilty";
                }
                case "not_guilty" -> {
                    guilty = w >= 3;
                    days = Math.min(7, base + 1);
                    says = guilty ? "guilty" : "acquitted";
                }
                case "bribe" -> {
                    takeEmeralds(player, d, BRIBE);
                    guilty = w >= 6;
                    says = guilty ? "bribe_failed" : "bribe_worked";
                }
                default -> {
                    guilty = w >= 3;
                    says = guilty ? "guilty" : "acquitted";
                }
            }
        }
        JsonObject v = new JsonObject();
        v.addProperty("plea", plea);
        v.addProperty("guilty", guilty);
        v.addProperty("weight", w);
        // the jury votes, one at a time (a bought juror makes it three and three)
        int g = w == 0 ? 0 : guilty ? Math.min(6, 4 + player.getRandom().nextInt(3) + (w >= 8 ? 1 : 0))
                : says.equals("bribe_worked") ? 3 : player.getRandom().nextInt(4);
        List<Boolean> votes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            votes.add(i < g);
        }
        java.util.Collections.shuffle(votes, new java.util.Random(player.getRandom().nextLong()));
        JsonArray va = new JsonArray();
        votes.forEach(va::add);
        v.add("votes", va);
        VOTE_AT.put(player.getUUID(), now(player));
        v.addProperty("days", guilty ? days : 0);
        JsonArray lines = new JsonArray();
        if (s.has("jury")) {
            lines.add(s.get("jury").deepCopy());
        }
        if (s.has(says)) {
            lines.add(line(s.get(says), "days", days + (days == 1 ? " day" : " days")));
        }
        if (guilty) {
            lines.add(line("The Watch Captain", "Take them down."));
        }
        v.add("lines", lines);
        d.trialVerdict = v.toString();
        show(player, d, true);
        // if they never press "continue", the Watch moves them on anyway
        MOVE_AT.put(player.getUUID(), now(player) + 20 * 120);
    }

    private static void proceed(ServerPlayer player, RepData d) {
        JsonObject v = JsonParser.parseString(d.trialVerdict).getAsJsonObject();
        JsonObject script = JsonParser.parseString(d.trialScript).getAsJsonObject();
        // the Codex keeps the transcript
        JsonObject t = new JsonObject();
        t.addProperty("day", script.get("day").getAsLong());
        t.addProperty("act", script.get("act").getAsString());
        t.add("lines", transcript(script, d));
        t.add("verdict", v);
        t.addProperty("outcome", v.get("guilty").getAsBoolean() ? "in the cells" : "acquitted");
        d.trials.add(t.toString());
        if (v.get("guilty").getAsBoolean()) {
            d.convictions++;
            d.sentence = v.get("days").getAsInt();
            d.served = 0;
            d.trialState = 2;
            toCell(player, d);
        } else {
            release(player, d, "acquitted");
        }
    }

    private static void toCell(ServerPlayer player, RepData d) {
        ServerLevel court = court(player.getServer());
        if (court == null) {
            release(player, d, "acquitted");
            return;
        }
        int slot = CourtData.get(court).slot(player.getUUID());
        CourtBuilder.ensure(court, slot);
        d.servedTicks = 0;
        Prison.start(player, d);
        putInCell(player, court, slot);
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
        give(player, new ItemStack(Items.BREAD, 2));
        player.displayClientMessage(Component.literal(d.sentence + (d.sentence == 1 ? " day" : " days")
                + " in the cells. There's nothing to do but wait (" + minutesLeft(d) + " min).").withStyle(ChatFormatting.RED), true);
    }

    private static void putInCell(ServerPlayer player, ServerLevel court, int slot) {
        BlockPos spot = CourtBuilder.origin(slot).offset(CourtBuilder.CELL_SPOT);
        player.teleportTo(court, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 180f, 0f);
        player.setRespawnPosition(COURT, spot, 180f, true, false);
    }

    /** real minutes (in the cell) until you're out */
    public static int minutesLeft(RepData d) {
        long ticks = (long) Math.max(0, d.sentence - d.served) * DAY - d.servedTicks;
        return (int) Math.max(1, (ticks + 1199) / 1200);
    }

    /** Time spent in the prison (only while you're in it, and it can't be slept away): every DAY ticks is a day served. */
    public static void passTime(ServerPlayer player, int ticks) {
        Prison.passTime(player, ticks);
    }

    /** A whole day gone by in the cell. */
    public static void serveDay(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return;
        }
        d.served++;
        d.servedTicks = 0;
        give(player, new ItemStack(Items.BREAD, 2));
        // the Gilded Hand looks after its own: Silas's men come on the second day
        if (Ranks.hand(player) >= 2 && d.served == 2 && d.sentence > 2) {
            release(player, d, "broken out");
            return;
        }
        if (d.served >= d.sentence) {
            // sentence served: the doors open and you walk out
            Prison.walkOut(player, d);
            return;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.7f);
        player.displayClientMessage(Component.literal("Day " + d.served + " of " + d.sentence + " served. The gaoler pushes bread through the bars. "
                + minutesLeft(d) + " min to go.").withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ going home
    /** outcome: served, escaped, broken out, acquitted, released (by an operator) */
    public static void release(ServerPlayer player, RepData d, String outcome) {
        if (d.trialState == 0) {
            return;
        }
        if (!d.trials.isEmpty() && d.trialState == 2) {
            JsonObject t = JsonParser.parseString(d.trials.get(d.trials.size() - 1)).getAsJsonObject();
            t.addProperty("outcome", outcome);
            t.addProperty("served", d.served);
            d.trials.set(d.trials.size() - 1, t.toString());
        }
        if (outcome.equals("broken out")) {
            d.brokenOut = true;
        }
        if (outcome.equals("escaped")) {
            // the Hand respects it; the Watch remembers it
            d.handDone += 3;
            d.watchDone = Math.max(0, d.watchDone - 5);
        }
        Prison.leave(player, d);
        d.trialState = 0;
        d.trialScript = "";
        d.trialVerdict = "";
        d.sentence = 0;
        d.served = 0;
        d.servedTicks = 0;
        UUID id = player.getUUID();
        SHOW_AT.remove(id);
        MOVE_AT.remove(id);
        goHome(player, d);
        giveBack(player, d);
        restoreSpawn(player, d);
        String dream = switch (outcome) {
            case "escaped" -> "escape";
            case "broken out" -> "breakout";
            case "served", "acquitted" -> "release";
            default -> "";
        };
        if (!dream.isEmpty()) {
            DREAM.put(id, dream);
            DREAM_AT.put(id, now(player) + 50);
        }
    }

    private static void goHome(ServerPlayer player, RepData d) {
        MinecraftServer server = player.getServer();
        ServerLevel level = null;
        Vec3 at = null;
        if (d.hadSpawn && !d.spawnDim.isEmpty()) {
            ResourceLocation dim = ResourceLocation.tryParse(d.spawnDim);
            level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level != null) {
                BlockPos pos = BlockPos.of(d.spawnPos);
                level.getChunk(pos);
                BlockState st = level.getBlockState(pos);
                if (st.getBlock() instanceof BedBlock) {
                    at = BedBlock.findStandUpPosition(EntityType.PLAYER, level, pos, st.getValue(BedBlock.FACING), d.spawnAngle).orElse(null);
                }
                if (at == null && d.spawnForced) {
                    at = Vec3.atBottomCenterOf(pos);
                }
            }
        }
        if (at == null) {
            level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            level.getChunk(spawn);
            at = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        player.teleportTo(level, at.x, at.y, at.z, d.spawnAngle, 0f);
    }

    private static void saveSpawn(ServerPlayer player, RepData d) {
        BlockPos pos = player.getRespawnPosition();
        d.hadSpawn = pos != null;
        d.spawnDim = player.getRespawnDimension().location().toString();
        d.spawnPos = pos == null ? 0 : pos.asLong();
        d.spawnAngle = player.getRespawnAngle();
        d.spawnForced = player.isRespawnForced();
    }

    private static void restoreSpawn(ServerPlayer player, RepData d) {
        if (d.hadSpawn) {
            ResourceLocation dim = ResourceLocation.tryParse(d.spawnDim);
            player.setRespawnPosition(dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim),
                    BlockPos.of(d.spawnPos), d.spawnAngle, d.spawnForced, false);
        } else {
            player.setRespawnPosition(Level.OVERWORLD, null, 0f, false, false);
        }
    }

    // ------------------------------------------------------------------ the evidence (your things)
    private static void confiscate(ServerPlayer player, RepData d) {
        Inventory inv = player.getInventory();
        ListTag list = new ListTag();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            CompoundTag t = new CompoundTag();
            t.putInt("Slot", i);
            t.put("Item", s.save(player.registryAccess()));
            list.add(t);
        }
        // anything already held from an earlier trial stays held (never lose things twice)
        for (Tag old : d.evidence) {
            CompoundTag t = ((CompoundTag) old).copy();
            t.putInt("Slot", -1);
            list.add(t);
        }
        d.evidence = list;
        inv.clearContent();
        player.inventoryMenu.broadcastChanges();
    }

    private static void giveBack(ServerPlayer player, RepData d) {
        Inventory inv = player.getInventory();
        for (Tag tag : d.evidence) {
            CompoundTag t = (CompoundTag) tag;
            Optional<ItemStack> parsed = ItemStack.parse(player.registryAccess(), t.getCompound("Item"));
            if (parsed.isEmpty() || parsed.get().isEmpty()) {
                continue;
            }
            ItemStack s = parsed.get();
            int slot = t.getInt("Slot");
            if (slot >= 0 && slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, s);
            } else {
                give(player, s);
            }
        }
        d.evidence = new ListTag();
        player.inventoryMenu.broadcastChanges();
    }

    private static void give(ServerPlayer player, ItemStack s) {
        if (!player.getInventory().add(s) && !s.isEmpty()) {
            player.drop(s, false);
        }
    }

    private static int emeraldsIn(ServerPlayer player, RepData d) {
        int n = 0;
        for (Tag tag : d.evidence) {
            Optional<ItemStack> s = ItemStack.parse(player.registryAccess(), ((CompoundTag) tag).getCompound("Item"));
            if (s.isPresent() && s.get().is(Items.EMERALD)) {
                n += s.get().getCount();
            }
        }
        return n;
    }

    private static void takeEmeralds(ServerPlayer player, RepData d, int amount) {
        ListTag out = new ListTag();
        for (Tag tag : d.evidence) {
            CompoundTag t = ((CompoundTag) tag).copy();
            Optional<ItemStack> s = ItemStack.parse(player.registryAccess(), t.getCompound("Item"));
            if (amount > 0 && s.isPresent() && s.get().is(Items.EMERALD)) {
                ItemStack e = s.get();
                int take = Math.min(amount, e.getCount());
                amount -= take;
                e.shrink(take);
                if (e.isEmpty()) {
                    continue;
                }
                t.put("Item", e.save(player.registryAccess()));
            }
            out.add(t);
        }
        d.evidence = out;
    }

    // ------------------------------------------------------------------ every tick
    public static void tick(ServerPlayer player) {
        RepData d = Reputation.data(player);
        UUID id = player.getUUID();
        long now = now(player);
        if (d.pendingKidnap && !player.isSleeping()) {
            if (!kidnap(player)) {
                d.pendingKidnap = false;
            }
            return;
        }
        Long show = SHOW_AT.get(id);
        if (show != null && now >= show) {
            SHOW_AT.remove(id);
            if (d.trialState == 1) {
                show(player, d, !d.trialVerdict.isEmpty());
            }
        }
        Long move = MOVE_AT.get(id);
        if (move != null && now >= move) {
            MOVE_AT.remove(id);
            if (d.trialState == 1 && !d.trialVerdict.isEmpty()) {
                proceed(player, d);
            }
        }
        Long dream = DREAM_AT.get(id);
        if (dream != null && now >= dream) {
            DREAM_AT.remove(id);
            Dreams.send(player, StoryData.INSTANCE.specialDreams.get(DREAM.remove(id)));
        }
        ServerLevel court = court(player.getServer());
        if (court == null) {
            return;
        }
        // the jury votes, one juror at a time, then the gavel
        Long vote = VOTE_AT.get(id);
        if (vote != null) {
            jury(player, d, court, now - vote);
        }
        boolean here = inCourt(player);
        if (here && d.trialState == 2 && player.isAlive()) {
            Prison.tick(player, d, court, CourtData.get(court).slot(id), now);
        }
        if (now % 20 != 0) {
            return;
        }
        if (here) {
            AWAY_SINCE.remove(id);
            int slot = CourtData.get(court).slot(id);
            if (CourtBuilder.ensure(court, slot)) {
                // the court was rebuilt by an update while you were in it: back to your place
                if (d.trialState == 1) {
                    BlockPos dock = CourtBuilder.origin(slot).offset(CourtBuilder.DOCK);
                    player.teleportTo(court, dock.getX() + 0.5, dock.getY(), dock.getZ() + 0.5, 180f, 0f);
                    player.setRespawnPosition(COURT, dock, 180f, true, false);
                } else if (d.trialState == 2) {
                    putInCell(player, court, slot);
                }
            }
            CourtBuilder.ensureNpcs(court, slot, scene(player, d, court, slot), player);
            if (d.trialState == 0 && !DREAM_AT.containsKey(id)) {
                // nobody should be here who isn't on trial or in a cell (an operator's teleport, an old save...)
                goHome(player, d);
            }
        } else if (d.trialState != 0) {
            // taken out of the court by something else: give everything back after a little while
            long since = AWAY_SINCE.computeIfAbsent(id, k -> now);
            if (now - since > 200) {
                AWAY_SINCE.remove(id);
                HalfAGod.LOG.info("Half a God: {} left the court without the Watch; ending their trial.", player.getName().getString());
                d.trialState = 0;
                d.trialScript = "";
                d.trialVerdict = "";
                giveBack(player, d);
                restoreSpawn(player, d);
            }
        }
    }

    private static void jury(ServerPlayer player, RepData d, ServerLevel court, long e) {
        if (e > 220 || d.trialVerdict.isEmpty()) {
            VOTE_AT.remove(player.getUUID());
            return;
        }
        int slot = CourtData.get(court).slot(player.getUUID());
        JsonObject v = JsonParser.parseString(d.trialVerdict).getAsJsonObject();
        JsonArray votes = v.has("votes") ? v.getAsJsonArray("votes") : new JsonArray();
        for (int i = 0; i < votes.size(); i++) {
            if (e == 30 + i * 24L) {
                net.minecraft.world.entity.Entity j = CourtBuilder.find(court, slot, "juror" + i);
                if (j != null) {
                    boolean g = votes.get(i).getAsBoolean();
                    Prison.vote(court, j, g);
                    court.playSound(null, j.getX(), j.getY(), j.getZ(), g ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.9f, 1f);
                }
            }
        }
        if (e == 184 || e == 190 || e == 196) {
            net.minecraft.world.entity.Entity judge = CourtBuilder.find(court, slot, "judge");
            if (judge != null) {
                court.playSound(null, judge.getX(), judge.getY(), judge.getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.NEUTRAL, 1.2f, 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ the court's rules
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RepData d = Reputation.data(player);
            if (d.trialState == 1) {
                SHOW_AT.put(player.getUUID(), now(player) + 60);
            } else if (d.trialState == 2) {
                player.displayClientMessage(Component.literal("Day " + (d.served + 1) + " of " + d.sentence + ". " + minutesLeft(d)
                        + " min to go.").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Reputation.data(player).trialState == 1) {
            SHOW_AT.put(player.getUUID(), now(player) + 40);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !inCourt(player)) {
            return;
        }
        if (Prison.onBreak(player, event.getPos())) {
            // the quarry face: it counts, and it's never used up
            event.setCanceled(true);
            return;
        }
        if (!player.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inCourt(player) && !player.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        // nothing in the court or the cells is yours to use (doors, the lectern, the chest...)
        if (event.getEntity() instanceof ServerPlayer player && inCourt(player)) {
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                Prison.onUseBlock(player, event.getPos());
            }
            if (!player.isCreative()) {
                // no doors, chests or lecterns; eating your bread is still allowed
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (CourtBuilder.isCourtNpc(event.getTarget())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                String role = CourtBuilder.role(event.getTarget());
                Talk.speaking(player, event.getTarget());
                if (Prison.onTalk(player, role)) {
                    return;
                }
                String name = event.getTarget().hasCustomName() ? event.getTarget().getCustomName().getString() : "";
                String line = role.equals("gaoler") ? "\"No beds in here. You'll feel every hour of it.\""
                        : role.startsWith("prisoner") ? "\"Don't bother with the walls. I've tried.\""
                        : role.startsWith("gallery") ? "\"Shh!\""
                        : role.startsWith("guard") ? "" : "\"Silence in the court.\"";
                if (!line.isEmpty()) {
                    player.displayClientMessage(Component.literal((name.isEmpty() ? "" : name + ": ") + line).withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (CourtBuilder.isCourtNpc(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (CourtBuilder.isCourtNpc(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (CourtBuilder.isCourtNpc(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ for operators (/halfagod test trial ...)
    /** a made-up record for testing: four witnesses who speak, three who can't (nothing is added to your real story) */
    public static final Set<String> SAMPLE_RECORD = Set.of("corwin_slain", "hag_cruelty_2", "hag_cruelty_x1_the_informant", "hag_lib_sold",
            "vael_slain", "hag_cruelty_x3_the_third_skull", "gt_silenced_the_witness");

    public static String testWitnesses(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 0) {
            return "You're already on trial or in a cell (/halfagod test trial release first).";
        }
        if (court(player.getServer()) == null) {
            return "The court dimension isn't loaded (is the Half a God story pack enabled?).";
        }
        return kidnap(player, SAMPLE_RECORD) ? "The Watch takes you, with a made-up record full of witnesses (your real story is unchanged)." : "Couldn't start a trial.";
    }

    public static String testStart(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 0) {
            return "You're already on trial or in a cell.";
        }
        if (court(player.getServer()) == null) {
            return "The court dimension isn't loaded (is the Half a God story pack enabled?).";
        }
        return kidnap(player) ? "The Watch takes you." : "Couldn't start a trial.";
    }

    public static String testDay(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState != 2) {
            return "You're not in a cell.";
        }
        serveDay(player);
        return "One day served.";
    }

    public static String testRelease(ServerPlayer player) {
        RepData d = Reputation.data(player);
        if (d.trialState == 0) {
            return "You're not on trial or in a cell.";
        }
        release(player, d, "released");
        return "Released.";
    }

    public static String status(ServerPlayer player) {
        RepData d = Reputation.data(player);
        Set<String> traits = Meb.traits(player);
        String act = Dreams.actTrait(traits);
        return "Trial state: " + (d.trialState == 0 ? "free" : d.trialState == 1 ? "on trial" : "in prison (day " + (d.served + 1) + " of " + d.sentence + ", " + Prison.phaseName(d) + ", " + minutesLeft(d)
                + " min to go; stone today " + d.labour + ", escape: plan " + d.planKnown + ", rounds " + d.knowsRounds + ", chisel " + d.hasChisel
                + ", hole " + (100 * d.digProgress / Prison.digsNeeded()) + "%)")
                + ". Act: " + StoryData.INSTANCE.acts.getOrDefault(act, "none") + (d.trialActs.contains(act) ? " (already tried this act)" : "")
                + ". Tier: " + Reputation.tier(d.value) + ". Convictions: " + d.convictions + ", escapes: " + d.escapes
                + ". Court loaded: " + (court(player.getServer()) != null) + ".";
    }
}

package dev.halfagod.core;

import com.google.gson.JsonObject;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The five hidden questlines. Each opens when you do something odd (see datapack-src/hidden_lines.py); finding one shows
 * a title card ("A hidden path opens") and starts its first quest. The Codex counts them: "Secrets found: 2 / 5".
 */
public final class Secrets {
    private record Pending(String key, long at) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** story choices that end in someone's death */
    private static final Set<String> KILLS = Set.of("peddler_slain", "orsk_executed", "corwin_slain", "morrow_slain", "hag_cruelty_1",
            "hag_cruelty_4", "hag_cruelty_5", "hag_cruelty_6", "hag_cruelty_x1_the_informant", "hag_cruelty_x2_the_bastille_warden",
            "hag_cruelty_x3_the_third_skull", "hag_cruelty_x5_the_last_garrison_man", "vael_slain", "midwife_slain",
            "gt_silenced_the_witness", "gt_vasco_killed", "hag_faithful_ended");

    private Secrets() {
    }

    public static JsonObject line(String key) {
        for (JsonObject l : StoryData.INSTANCE.secrets) {
            if (l.get("key").getAsString().equals(key)) {
                return l;
            }
        }
        return null;
    }

    public static boolean notFound(ServerPlayer player, String key) {
        JsonObject l = line(key);
        return l != null && !Meb.hasTrait(player, l.get("found").getAsString());
    }

    /** Marks a hidden questline as found; the title card and its first quest follow after a short pause. */
    public static boolean find(ServerPlayer player, String key, int delayTicks) {
        JsonObject l = line(key);
        if (l == null || Meb.hasTrait(player, l.get("found").getAsString())) {
            return false;
        }
        Meb.addTrait(player, l.get("found").getAsString());
        PENDING.put(player.getUUID(), new Pending(key, player.getServer().getTickCount() + Math.max(1, delayTicks)));
        return true;
    }

    /** every 20 ticks */
    public static void tick(ServerPlayer player) {
        if (Trial.inCourt(player)) {
            return;
        }
        Pending p = PENDING.get(player.getUUID());
        if (p != null) {
            if (player.getServer().getTickCount() < p.at()) {
                return;
            }
            PENDING.remove(player.getUUID());
            JsonObject l = line(p.key());
            if (l != null) {
                PacketDistributor.sendToPlayer(player, new Payloads.Cinematic("A hidden path opens", l.get("name").getAsString(),
                        0xC9A0FF, "minecraft:block.amethyst_block.resonate", 0, 0f));
            }
        }
        // start the first quest of every line that's been found (and keep trying until Mebahel takes it)
        RepData d = Reputation.data(player);
        Set<String> traits = Meb.traits(player);
        for (JsonObject l : StoryData.INSTANCE.secrets) {
            String key = l.get("key").getAsString();
            if (!traits.contains(l.get("found").getAsString()) || traits.contains(l.get("done").getAsString())
                    || d.countedChoices.contains("secret|" + key)) {
                continue;
            }
            String first = l.get("first").getAsString();
            boolean started = false;
            for (Meb.Progress q : Meb.quests(player)) {
                started |= q.questId().equals(first);
            }
            StoryData.Graph g = StoryData.INSTANCE.graph.get(first);
            if (!started && g != null && g.book()) {
                Meb.giveBook(player, first);
                started = true;
            } else if (!started) {
                started = Meb.startQuest(player, ResourceLocation.parse(first), false);
            }
            if (started) {
                d.countedChoices.add("secret|" + key);
            }
        }
    }

    // ------------------------------------------------------------------ what opens them
    /** The Old Tongue: pray at three of the cult's shrines. */
    public static void onPray(ServerPlayer player) {
        RepData d = Reputation.data(player);
        d.shrinePrayers++;
        if (!notFound(player, "tongue")) {
            return;
        }
        if (d.shrinePrayers == 1) {
            say(player, "Under the gold, something whispers a word you don't know.");
        } else if (d.shrinePrayers == 2) {
            say(player, "The whisper again, closer. It wants you to listen.");
        } else {
            say(player, "This time the shrine answers. One word, over and over, in the Old Tongue.");
            find(player, "tongue", 60);
        }
    }

    /** The Last Pilgrim: feed three pilgrims on the road, once you've met Brother Vael. */
    public static void onPilgrimFed(ServerPlayer player) {
        RepData d = Reputation.data(player);
        d.pilgrimsFed++;
        if (!notFound(player, "pilgrim")) {
            return;
        }
        if (d.pilgrimsFed == 1) {
            say(player, "The pilgrim looks at you a moment too long, as if he knows your face.");
        } else if (d.pilgrimsFed == 2) {
            say(player, "This one asks your name, then says it back slowly, like he's remembering it.");
        } else if (!Meb.hasTrait(player, "hag_a12_brother_vael_done")) {
            say(player, "'Not yet,' the pilgrim says. 'You haven't met him yet. I'll find you again.'");
        } else {
            say(player, "The pilgrim takes your hand. 'You met Vael,' he says. 'I can tell. Sit with me a while.'");
            find(player, "pilgrim", 80);
        }
    }

    /** Sky and Fire: carry something from the Aether into the Nether. */
    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getTo() != Level.NETHER || !notFound(player, "skyfire")) {
            return;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("aether")) {
                say(player, "The Nether goes quiet around you. Something down here recognises what you're carrying.");
                find(player, "skyfire", 100);
                return;
            }
        }
    }

    /** Clean Hands: reach Act IV without killing a villager or any story character. Checked once, when Act IV begins. */
    public static void checkClean(ServerPlayer player, Set<String> traits) {
        RepData d = Reputation.data(player);
        if (d.cleanChecked || !traits.contains("hag_e1_the_librarians_request_done")) {
            return;
        }
        d.cleanChecked = true;
        if (!cleanHands(player, traits)) {
            return;
        }
        find(player, "clean", 200);
    }

    public static boolean cleanHands(ServerPlayer player, Set<String> traits) {
        if (player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER)) > 0) {
            return false;
        }
        for (Meb.Progress p : Meb.quests(player)) {
            if (p.questId().startsWith("mebahelrpgrquest:hag_") && p.attacked()) {
                return false;
            }
        }
        for (String k : KILLS) {
            if (traits.contains(k)) {
                return false;
            }
        }
        return true;
    }

    /** What became of Oren depends on what became of Vael. */
    public static void onTrait(ServerPlayer player, String trait) {
        if (trait.equals("hag_secret_pilgrim_done")) {
            Set<String> t = Meb.traits(player);
            boolean dead = t.contains("vael_slain") || t.contains("hag_vael_branded") || t.contains("hag_payoff_vael_spy");
            Meb.addTrait(player, dead ? "hag_secret_pilgrim_vael_dead" : "hag_secret_pilgrim_vael_alive");
        }
    }

    public static int found(ServerPlayer player) {
        Set<String> traits = Meb.traits(player);
        int n = 0;
        for (JsonObject l : StoryData.INSTANCE.secrets) {
            if (traits.contains(l.get("found").getAsString())) {
                n++;
            }
        }
        return n;
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }
}

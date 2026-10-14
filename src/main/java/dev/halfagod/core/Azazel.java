package dev.halfagod.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Cult of Azazel mod, made part of the story (see datapack-src/azazel.py for the words):
 *
 * - Azazel can't be called before the story sends you to him (The Instruction): placing the Nether Spawner, finishing
 *   its ring of gold and debris, the altar, the Altar Compass-Key and the Cult Scroll all do nothing until then, and he
 *   can't come into the world any other way (unless a player in creative is there). His structures generate as usual.
 * - The Cult Scroll is only for the dark side (knelt to the True Azazel, or a Wicked name).
 * - The cult's work (his quotas) and every villager brought to "the faith" cost reputation, and the Watch hears of it.
 * - The Totem of Rebirth saves you where you stand instead of carrying you off to your bed.
 */
public final class Azazel {
    private static final String NM = "netherman";
    private static final Map<UUID, Long> LAST_SAID = new HashMap<>();

    private Azazel() {
    }

    private static boolean present() {
        return ModList.get().isLoaded(NM) && ModList.get().isLoaded("mebahelrpgrquest");
    }

    private static JsonObject t() {
        return StoryData.INSTANCE.table("azazel");
    }

    private static String text(String section, String key) {
        JsonObject o = t();
        if (section != null) {
            o = o.has(section) ? o.getAsJsonObject(section) : new JsonObject();
        }
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static boolean anyTrait(ServerPlayer p, String list) {
        if (!t().has(list)) {
            return false;
        }
        Set<String> traits = Meb.traits(p);
        for (JsonElement e : t().getAsJsonArray(list)) {
            if (traits.contains(e.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** has the story sent this player to Azazel yet? */
    public static boolean unlocked(Player p) {
        if (!(p instanceof ServerPlayer sp) || sp.isCreative() || !present() || !t().has("unlock")) {
            return true;
        }
        return anyTrait(sp, "unlock");
    }

    /** the dark side: knelt to the True Azazel, or Wicked */
    private static boolean dark(ServerPlayer p) {
        return p.isCreative() || anyTrait(p, "knelt") || Reputation.tierIndex(Reputation.data(p).value) == 0;
    }

    private static void say(Player p, String s) {
        if (s.isEmpty() || !(p instanceof ServerPlayer sp)) {
            return;
        }
        long now = sp.getServer().getTickCount();
        Long last = LAST_SAID.get(sp.getUUID());
        if (last != null && now - last < 40) {
            return;
        }
        LAST_SAID.put(sp.getUUID(), now);
        sp.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
    }

    private static boolean is(BlockState s, String id) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).equals(ResourceLocation.fromNamespaceAndPath(NM, id));
    }

    private static boolean is(ItemStack s, String id) {
        return !s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(ResourceLocation.fromNamespaceAndPath(NM, id));
    }

    private static boolean is(Entity e, String id) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(ResourceLocation.fromNamespaceAndPath(NM, id));
    }

    // ------------------------------------------------------------------ calling him early
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || unlocked(p)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        boolean summon = is(placed, "nether_spawner");
        if (!summon && (placed.is(Blocks.GOLD_BLOCK) || placed.is(Blocks.ANCIENT_DEBRIS))) {
            // finishing the ring around a spawner that's already there (one built into a structure, say)
            BlockPos at = event.getPos();
            for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 2, 2))) {
                if (is(event.getLevel().getBlockState(q), "nether_spawner")) {
                    summon = true;
                    break;
                }
            }
        }
        if (summon) {
            event.setCanceled(true);
            say(p, text("locked", "summon"));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        ItemStack held = event.getItemStack();
        String why = null;
        if (!unlocked(p)) {
            if (is(event.getLevel().getBlockState(event.getPos()), "altar")) {
                why = "altar";
            } else if (is(held, "altar_compass_key")) {
                why = "compass";
            } else if (is(held, "quota")) {
                why = "scroll";
            }
        } else if (is(held, "quota") && !dark(p)) {
            why = "scroll_good";
        }
        if (why != null) {
            event.setCanceled(true);
            say(p, why.equals("scroll_good") ? text(null, "scroll_good") : text("locked", why));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!unlocked(p) && (is(held, "altar_compass_key") || is(held, "quota"))) {
            event.setCanceled(true);
            say(p, text("locked", is(held, "quota") ? "scroll" : "compass"));
        } else if (is(held, "quota") && !dark(p)) {
            event.setCanceled(true);
            say(p, text(null, "scroll_good"));
        }
    }

    /** nobody brings him into the world early, by any road (a player in creative can) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.loadedFromDisk() || event.getLevel().isClientSide() || !present() || !(is(e, "azazel") || is(e, "azazel_human"))) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        boolean allowed = false;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(e) < 96 * 96 && unlocked(p)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            event.setCanceled(true);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(e) < 64 * 64) {
                    say(p, text("locked", "azazel"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the Totem of Rebirth: where you stand
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !present()) {
            return;
        }
        ItemStack totem = ItemStack.EMPTY;
        for (InteractionHand h : InteractionHand.values()) {
            if (is(p.getItemInHand(h), "chance_totem")) {
                totem = p.getItemInHand(h);
                break;
            }
        }
        if (totem.isEmpty()) {
            totem = curiosTotem(p);
        }
        if (totem.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        totem.shrink(1);
        p.setHealth(4f);
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 3));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        p.level().broadcastEntityEvent(p, (byte) 35);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 0.7f);
        say(p, text(null, "totem"));
    }

    /** his totem worn as a charm (Curios), through his own helper */
    private static ItemStack curiosTotem(ServerPlayer p) {
        if (!ModList.get().isLoaded("curios")) {
            return ItemStack.EMPTY;
        }
        try {
            Object r = Class.forName("com.benji.netherman.common.compat.CuriosCompat").getMethod("getTotemFromCurios", Player.class).invoke(null, p);
            return r instanceof ItemStack s && is(s, "chance_totem") ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ the cult's work, noticed
    /** a villager brought to "the faith" with his Essence of Faith */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onConvert(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getTarget() instanceof Villager v) || CourtBuilder.isCourtNpc(v) || !is(event.getItemStack(), "faith_essence")) {
            return;
        }
        RepData d = Reputation.data(p);
        d.conversions++;
        Reputation.change(p, t().has("rep_conversion") ? t().get("rep_conversion").getAsDouble() : -5, "a villager brought to the faith");
        Rivals.saw(p, "conversion");
        say(p, text(null, "conversion"));
    }

    /** his cult quotas (from a small mixin on his QuotaManager): every bit of the cult's work counts against you */
    public static void onQuota(Player player, int task, int amount) {
        if (!(player instanceof ServerPlayer p) || amount <= 0) {
            return;
        }
        RepData d = Reputation.data(p);
        d.cultWork += amount;
        double per = t().has("rep_work") ? t().get("rep_work").getAsDouble() : -2;
        Reputation.change(p, per * Math.min(amount, 5), "the cult's work");
        say(p, text(null, "work"));
    }

    // ------------------------------------------------------------------ his guidebook (the mod's tutorial): gone
    private static boolean guide(ItemStack s) {
        return is(s, "azazel_guide_book");
    }

    /** the Believers don't sell it */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTradeWith(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() && event.getTarget() instanceof net.minecraft.world.item.trading.Merchant m
                && BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()).getNamespace().equals(NM)) {
            m.getOffers().removeIf(o -> guide(o.getResult()));
        }
    }

    /** any copy (and the talking book it opens into) crumbles to nothing */
    @SubscribeEvent
    public static void onGuideJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide() || !ModList.get().isLoaded(NM)) {
            return;
        }
        if ((e instanceof net.minecraft.world.entity.item.ItemEntity item && guide(item.getItem())) || is(e, "azazel_guide_book")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGuideTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 23 || !ModList.get().isLoaded(NM)) {
            return;
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (guide(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (guide(p.containerMenu.getCarried())) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }
}

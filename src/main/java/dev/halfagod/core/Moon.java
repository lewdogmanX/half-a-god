package dev.halfagod.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.halfagod.item.EclipseItem;
import dev.halfagod.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Silver King (the moon storyline, with Create: Cosmonautics).
 *
 * - Walking into Hester Quill's observatory starts the first quest.
 * - The Faithful, their Priest and Iskar's Last Light breathe on the moon (Cosmonautics lets nothing breathe there).
 * - Taking the Eclipse from Iskar's hand puts it in yours. If you die before you're home on the overworld, it goes back
 *   to his hand, and you'll have to go up and take it again. Once you're home with it, it's yours for good.
 * - Laying him to rest instead is worth a good name.
 * - The Eclipse is twice as deadly to Azazel (both forms) and to anything fire can't hurt, and a quarter stronger at
 *   night, in the Nether and on the moon. Azazel knows the blade when he sees it.
 */
public final class Moon {
    private Moon() {
    }

    private static boolean present() {
        return ModList.get().isLoaded("rocketnautics") && ModList.get().isLoaded("mebahelrpgrquest");
    }

    private static JsonObject t() {
        return StoryData.INSTANCE.table("moon");
    }

    private static String s(String key) {
        return t().has(key) ? t().get(key).getAsString() : "";
    }

    private static void say(ServerPlayer p, String line, ChatFormatting color) {
        if (!line.isEmpty()) {
            p.displayClientMessage(Component.literal(line).withStyle(color, ChatFormatting.ITALIC), true);
        }
    }

    private static boolean isEclipse(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof EclipseItem;
    }

    private static boolean carries(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isEclipse(inv.getItem(i))) {
                return true;
            }
        }
        return isEclipse(p.containerMenu.getCarried());
    }

    private static boolean in(ServerLevel level, ServerPlayer p, String key) {
        String id = s(key);
        if (id.isEmpty()) {
            return false;
        }
        Structure st = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(id));
        return st != null && level.structureManager().getStructureWithPieceAt(p.blockPosition(), st).isValid();
    }

    private static boolean onMoon(Level level) {
        return level.dimension().location().toString().equals(s("moon").isEmpty() ? "rocketnautics:moon" : s("moon"));
    }

    private static void give(ServerPlayer p, String line) {
        ItemStack sword = new ItemStack(ModItems.ECLIPSE.get());
        if (!p.getInventory().add(sword)) {
            p.drop(sword, false);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.6f);
        say(p, line, ChatFormatting.AQUA);
    }

    // ------------------------------------------------------------------ the story
    public static void onTrait(ServerPlayer p, String trait) {
        if (!present()) {
            return;
        }
        if (trait.equals(s("take_trait")) && !carries(p)) {
            give(p, s("given"));
        } else if (trait.equals(s("rest_trait"))) {
            Reputation.change(p, t().has("rest_rep") ? t().get("rest_rep").getAsDouble() : 15, "laid a god to rest");
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 29 || !present() || p.isSpectator()
                || !(p.level() instanceof ServerLevel level) || Trial.inCourt(p)) {
            return;
        }
        RepData d = Reputation.data(p);
        if (level.dimension() == Level.OVERWORLD) {
            // the observatory: Hester Quill
            String start = s("start");
            if (!start.isEmpty() && in(level, p, "structure") && !Meb.hasTrait(p, start.substring(start.indexOf(':') + 1) + "_done")
                    && Meb.quests(p).stream().noneMatch(q -> q.questId().equals(start) && !q.failed())) {
                Meb.startQuest(p, ResourceLocation.parse(start), false);
            }
            // home, alive, with the Eclipse
            if (!d.eclipseHome && Meb.hasTrait(p, s("take_trait")) && carries(p)) {
                d.eclipseHome = true;
                d.eclipseLost = false;
                say(p, s("home"), ChatFormatting.GOLD);
            }
        } else if (onMoon(level) && d.eclipseLost && !d.eclipseHome && Meb.hasTrait(p, s("take_trait")) && !carries(p)
                && in(level, p, "iskar")) {
            d.eclipseLost = false;
            give(p, s("again"));
        }
    }

    /** dying with it before it's home sends it back to Iskar (the Totem, or the Watch dragging you to court, aren't death) */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !present()) {
            return;
        }
        RepData d = Reputation.data(p);
        if (d.eclipseHome || !carries(p)) {
            return;
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isEclipse(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (isEclipse(p.containerMenu.getCarried())) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        d.eclipseLost = true;
        say(p, s("lost"), ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------ the moon's people breathe
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreathe(LivingBreatheEvent event) {
        LivingEntity e = event.getEntity();
        if (event.canBreathe() || !e.hasCustomName() || e.getCustomName() == null || !t().has("breathing")) {
            return;
        }
        String name = e.getCustomName().getString();
        for (JsonElement n : t().getAsJsonArray("breathing")) {
            if (n.getAsString().equals(name)) {
                event.setCanBreathe(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ the blade that cuts the sun
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p
                || !isEclipse(p.getMainHandItem())) {
            return;
        }
        LivingEntity target = event.getEntity();
        float mult = 1f;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        if (id.equals("netherman:azazel") || id.equals("netherman:azazel_human") || target.fireImmune()) {
            mult *= 2f;
        }
        Level level = p.level();
        if (level.dimension() == Level.NETHER || onMoon(level) || (level.dimensionType().hasSkyLight() && level.isNight())) {
            mult *= 1.25f;
        }
        if (mult != 1f) {
            event.setAmount(event.getAmount() * mult);
        }
    }

    /** Azazel sees the Eclipse */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.isCanceled() || event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().startsWith("netherman:azazel")) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(e) < 64 * 64 && carries(p)) {
                p.displayClientMessage(Component.literal("Azazel: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("\"" + s("azazel_sees") + "\"").withStyle(ChatFormatting.RED)), true);
            }
        }
    }
}

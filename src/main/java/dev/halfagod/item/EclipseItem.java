package dev.halfagod.item;

import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Eclipse: Iskar's sword, made to cut the sun. 13 attack damage at a sword's speed, never wears out, can't burn.
 * Twice as deadly to both forms of Azazel and to creatures of fire, a quarter stronger at night, in the Nether and on the
 * moon (see Moon.java). Raised (use), the sky goes dark around you: everything hostile within sixteen blocks is slowed,
 * weakened and shown up, and you're stronger and harder to hurt, for eight seconds; then it rests for three minutes.
 */
public final class EclipseItem extends SwordItem {
    public static final Tier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4062, 9f, 9f, 18, () -> Ingredient.EMPTY);
    public static final int COOLDOWN = 20 * 180;
    public static final int LASTS = 20 * 8;

    public EclipseItem() {
        super(TIER, new Item.Properties().attributes(SwordItem.createAttributes(TIER, 3, -2.4f)).fireResistant().rarity(Rarity.EPIC)
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16),
                e -> e != player && e.isAlive() && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == player)))) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LASTS, 2));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LASTS, 1));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, LASTS, 0));
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, LASTS, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LASTS, 0));
        sl.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4f, 0.5f);
        sl.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6f, 0.6f);
        for (ServerPlayer near : sl.players()) {
            if (near.distanceTo(player) < 48) {
                try {
                    PacketDistributor.sendToPlayer(near, new Payloads.EclipseFx(LASTS));
                } catch (RuntimeException ignored) {
                    // a client without the mod (or a test player) just doesn't see the sky go dark
                }
            }
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.half_a_god.eclipse.lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.half_a_god.eclipse.lore2").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.half_a_god.eclipse.lore3").withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("item.half_a_god.eclipse.lore4").withStyle(ChatFormatting.DARK_PURPLE));
    }
}

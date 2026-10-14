package dev.halfagod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Golden Maraca (the Ribbits' Song): the oldest beat in the swamp. Hold use to shake it: chk-chk, golden notes, and
 * you and everyone near you move to the rhythm (a little speed, a little spring) while it plays.
 */
public final class GoldenMaracaItem extends Item {
    public GoldenMaracaItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;   // (the shake)
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity who, ItemStack stack, int left) {
        int t = 72000 - left;
        if (level.isClientSide() || !(level instanceof ServerLevel sl)) {
            return;
        }
        if (t % 5 == 0) {
            float pitch = (t / 5) % 2 == 0 ? 1.0f : 1.18f;
            sl.playSound(null, who.getX(), who.getY(), who.getZ(), ModItems.MARACA_SHAKE.get(), SoundSource.PLAYERS, 0.7f,
                    pitch + sl.random.nextFloat() * 0.06f);
        }
        if (t % 10 == 0) {
            // golden notes (a note particle's colour comes from its x speed, 0..1: these are the yellows and golds)
            sl.sendParticles(ParticleTypes.NOTE, who.getX() + (sl.random.nextDouble() - 0.5), who.getEyeY() + 0.4,
                    who.getZ() + (sl.random.nextDouble() - 0.5), 0, 0.05 + sl.random.nextDouble() * 0.04, 0, 0, 1);
            sl.sendParticles(ParticleTypes.WAX_ON, who.getX(), who.getY() + 1.2, who.getZ(), 3, 0.4, 0.3, 0.4, 0.02);
        }
        if (t % 20 == 0) {
            for (Player p : sl.getEntitiesOfClass(Player.class, who.getBoundingBox().inflate(8))) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, true));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 0, true, true));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.half_a_god.golden_maraca.lore1").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.half_a_god.golden_maraca.lore2").withStyle(ChatFormatting.GRAY));
    }
}

package dev.halfagod.mixin;

import dev.halfagod.core.Azazel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cult of Azazel's cult quotas: each bit of progress is the cult's work, and the story notices (see {@link Azazel}). */
@Pseudo
@Mixin(targets = "com.benji.netherman.QuotaManager", remap = false)
public abstract class QuotaManagerMixin {
    @Inject(method = "addProgress", at = @At("HEAD"), require = 0)
    private static void hag$work(Player player, int task, int amount, CallbackInfo ci) {
        Azazel.onQuota(player, task, amount);
    }
}

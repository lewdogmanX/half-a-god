package dev.halfagod.mixin;

import dev.halfagod.core.Azazel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cult of Azazel's cult quotas: progress on a quota is the cult's work, and the story notices (see {@link Azazel}).
 * His mod calls addProgress for every kill and every gold/bell placed, cultist or not; only progress his quota actually
 * took (a cultist with that task on their scroll) counts, so we compare the scroll's progress before and after.
 */
@Pseudo
@Mixin(targets = "com.benji.netherman.QuotaManager", remap = false)
public abstract class QuotaManagerMixin {
    @Unique
    private static final ThreadLocal<Integer> HAG$BEFORE = new ThreadLocal<>();

    @Unique
    private static int hag$progress(Player player) {
        CompoundTag tag = player.getPersistentData();
        int sum = 0;
        for (int i = 1; i <= 3; i++) {
            sum += tag.getInt("QuotaProg" + i);
        }
        return sum;
    }

    @Inject(method = "addProgress", at = @At("HEAD"), require = 0)
    private static void hag$before(Player player, int task, int amount, CallbackInfo ci) {
        HAG$BEFORE.set(hag$progress(player));
    }

    @Inject(method = "addProgress", at = @At("RETURN"), require = 0)
    private static void hag$work(Player player, int task, int amount, CallbackInfo ci) {
        Integer before = HAG$BEFORE.get();
        HAG$BEFORE.remove();
        if (before == null) {
            return;
        }
        int counted = hag$progress(player) - before;
        if (counted > 0) {
            Azazel.onQuota(player, task, counted);
        }
    }
}

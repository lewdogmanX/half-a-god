package dev.halfagod.mixin;

import dev.halfagod.client.MebTalk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets Mebahel's talk screen carry the mod's own conversations: when the screen is showing one of ours, its options are
 * ours and a click on one comes to us (see {@link MebTalk}); otherwise nothing changes. Only his own method names are
 * used, and nothing here is required: if he renames something, that part just doesn't apply and the mod uses its own
 * talk screen.
 */
@Pseudo
@Mixin(targets = "net.mebahelrpgrquest.client.gui.QuestDialogueScreen", remap = false)
public abstract class QuestDialogueTalkMixin {
    @Inject(method = "rebuildDialogueOptions", at = @At("HEAD"), cancellable = true, require = 0)
    private void hag$options(CallbackInfo ci) {
        if (MebTalk.onBuildOptions(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "activateSelectedOption", at = @At("HEAD"), cancellable = true, require = 0)
    private void hag$activate(CallbackInfo ci) {
        if (MebTalk.onActivate(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendAction", at = @At("HEAD"), cancellable = true, require = 0)
    private void hag$send(int action, CallbackInfo ci) {
        if (MebTalk.onSendAction(this)) {
            ci.cancel();
        }
    }
}

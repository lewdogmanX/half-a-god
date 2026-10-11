package dev.halfagod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Re-colours the talk screen of Mebahel's RPG (the plaques the villagers' words and your answers sit on) to Half a
 * God's paper and ink: dark wood behind the words, gold lines, warm paper-coloured text. Only colours change; his
 * layout, text and buttons are his. If his screen changes and a colour isn't found, that colour just stays his.
 */
@Pseudo
@Mixin(targets = "net.mebahelrpgrquest.client.gui.QuestDialogueScreen", remap = false)
public abstract class QuestDialogueScreenMixin {
    // the plaque behind the words
    @ModifyConstant(method = "drawDialogueContent", constant = @Constant(intValue = 0x595d5852), require = 0)
    private int hag$plaque(int c) {
        return 0xC82A1C11;
    }

    @ModifyConstant(method = "drawDialogueContent", constant = @Constant(intValue = 0x66232120), require = 0)
    private int hag$plaqueDark(int c) {
        return 0x9A140D08;
    }

    @ModifyConstant(method = "drawDialogueContent", constant = @Constant(intValue = 0xfff3ebdd), require = 0)
    private int hag$words(int c) {
        return 0xFFEDE0C2;
    }

    // the plaque's top and bottom edges
    @ModifyConstant(method = "drawPlaqueHorizontalBorder", constant = @Constant(intValue = 0x595d5852), require = 0)
    private int hag$borderBack(int c) {
        return 0xC82A1C11;
    }

    @ModifyConstant(method = "drawPlaqueHorizontalBorder", constant = @Constant(intValue = 0x8ad4c9ba), require = 0)
    private int hag$borderLine(int c) {
        return 0xE6C9A55A;
    }

    // your answers
    @ModifyConstant(method = "drawDialogueOptionRow", constant = @Constant(intValue = 0x556e665b), require = 0)
    private int hag$rowBack(int c) {
        return 0x9A3B2A1A;
    }

    @ModifyConstant(method = "drawDialogueOptionRow", constant = @Constant(intValue = 0xffd5cdbb), require = 0)
    private int hag$rowText(int c) {
        return 0xFFE8DCC4;
    }

    @ModifyConstant(method = "drawDialogueOptionRow", constant = @Constant(intValue = 0x00fff6e7), require = 0)
    private int hag$rowHover(int c) {
        return 0x00F2D7A0;
    }

    @ModifyConstant(method = "drawDialogueOptionRow", constant = @Constant(intValue = 0xfff0e8d9), require = 0)
    private int hag$rowTextHover(int c) {
        return 0xFFF7E6BE;
    }

    @ModifyConstant(method = "drawDialogueOptionRow", constant = @Constant(intValue = 0xff8f8a80), require = 0)
    private int hag$rowTextOff(int c) {
        return 0xFF9A8662;
    }

    // the little ornament by the chosen answer, and the scrollbar
    @ModifyConstant(method = "drawOptionOrnament", constant = @Constant(intValue = 0xcc413b34), require = 0)
    private int hag$ornamentBack(int c) {
        return 0xCC2A1C11;
    }

    @ModifyConstant(method = "drawOptionOrnament", constant = @Constant(intValue = 0xffd5cdbb), require = 0)
    private int hag$ornament(int c) {
        return 0xFFC9A55A;
    }

    @ModifyConstant(method = "drawOptionScrollbarIfNeeded", constant = @Constant(intValue = 0x445b5141), require = 0)
    private int hag$scrollTrack(int c) {
        return 0x553B2A1A;
    }

    @ModifyConstant(method = "drawOptionScrollbarIfNeeded", constant = @Constant(intValue = 0xccb49666), require = 0)
    private int hag$scrollThumb(int c) {
        return 0xCCC9A55A;
    }
}

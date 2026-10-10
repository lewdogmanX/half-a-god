package dev.halfagod.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/half_a_god-client.toml */
public final class ClientConfig {
    public enum Anchor { AUTO, BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_BAR;
    public static final ModConfigSpec.EnumValue<Anchor> ANCHOR;
    public static final ModConfigSpec.IntValue OFFSET_X;
    public static final ModConfigSpec.IntValue OFFSET_Y;
    public static final ModConfigSpec.BooleanValue SHOW_NUMBER;
    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("The reputation bar").push("reputation_bar");
        SHOW_BAR = b.comment("Show the reputation bar on screen").define("show", true);
        ANCHOR = b.comment("Where the bar sits. AUTO puts it beside the hotbar (on the side away from your offhand) and moves it if anything is in the way.")
                .defineEnum("anchor", Anchor.AUTO);
        OFFSET_X = b.comment("Nudge the bar left (-) or right (+), in pixels").defineInRange("offset_x", 0, -1000, 1000);
        OFFSET_Y = b.comment("Nudge the bar up (-) or down (+), in pixels").defineInRange("offset_y", 0, -1000, 1000);
        SHOW_NUMBER = b.comment("Show the number next to the tier name").define("show_number", true);
        b.pop();
        b.push("cinematics");
        SCREEN_SHAKE = b.comment("Shake the screen at big story moments").define("screen_shake", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}

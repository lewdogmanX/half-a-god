package dev.halfagod.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/half_a_god-client.toml */
public final class ClientConfig {
    public enum Position { TOP_CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_BAR;
    public static final ModConfigSpec.EnumValue<Position> POSITION;
    public static final ModConfigSpec.IntValue OFFSET_X;
    public static final ModConfigSpec.IntValue OFFSET_Y;
    public static final ModConfigSpec.IntValue WIDTH;
    public static final ModConfigSpec.BooleanValue SHOW_NUMBER;
    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("The reputation bar").push("reputation_bar");
        SHOW_BAR = b.comment("Show the reputation bar on screen").define("show", true);
        POSITION = b.comment("Where the bar sits. TOP_CENTER keeps it away from the hotbar, so it never covers mods that draw there",
                        "(stamina, rolling, thirst). It moves down below any boss bars.")
                .defineEnum("position", Position.TOP_CENTER);
        OFFSET_X = b.comment("Nudge the bar left (-) or right (+), in pixels").defineInRange("offset_x", 0, -2000, 2000);
        OFFSET_Y = b.comment("Nudge the bar up (-) or down (+), in pixels").defineInRange("offset_y", 0, -2000, 2000);
        WIDTH = b.comment("Bar width in pixels").defineInRange("width", 64, 32, 160);
        SHOW_NUMBER = b.comment("Show the number next to the bar").define("show_number", false);
        b.pop();
        b.push("story");
        SCREEN_SHAKE = b.comment("Shake the screen at big story moments").define("screen_shake", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}

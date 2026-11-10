package com.dshdev.xaerochunkmark;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config. Everything here is purely cosmetic: the mark colour and how strongly it is blended over the map.
 */
public final class ChunkMarkConfig {
    private static final int DEFAULT_RGB = 0xFF9D2E;
    private static final int DEFAULT_FILL_ALPHA = 60;
    private static final int DEFAULT_BORDER_ALPHA = 220;

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<String> COLOR;
    private static final ForgeConfigSpec.IntValue FILL_ALPHA;
    private static final ForgeConfigSpec.IntValue BORDER_ALPHA;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Appearance of the chunk marks drawn on Xaero's World Map.").push("appearance");
        COLOR = builder
                .comment("Mark colour as RRGGBB hex (a leading # or 0x is allowed).")
                .define("color", "FF9D2E");
        FILL_ALPHA = builder
                .comment("Opacity of the inside of a marked chunk, 0-255.")
                .defineInRange("fillOpacity", DEFAULT_FILL_ALPHA, 0, 255);
        BORDER_ALPHA = builder
                .comment("Opacity of the outline drawn where a marked chunk touches an unmarked one, 0-255.")
                .defineInRange("borderOpacity", DEFAULT_BORDER_ALPHA, 0, 255);
        builder.pop();
        SPEC = builder.build();
    }

    private ChunkMarkConfig() {
    }

    public static int colorRgb() {
        try {
            String value = COLOR.get().trim();
            if (value.startsWith("#")) {
                value = value.substring(1);
            } else if (value.startsWith("0x") || value.startsWith("0X")) {
                value = value.substring(2);
            }
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (Throwable ignored) {
            // Config not loaded yet (or a malformed value): fall back to the default colour.
            return DEFAULT_RGB;
        }
    }

    public static int fillAlpha() {
        try {
            return FILL_ALPHA.get();
        } catch (Throwable ignored) {
            return DEFAULT_FILL_ALPHA;
        }
    }

    public static int borderAlpha() {
        try {
            return BORDER_ALPHA.get();
        } catch (Throwable ignored) {
            return DEFAULT_BORDER_ALPHA;
        }
    }

    /** Folded into the region hash so that editing the config makes the map repaint. */
    public static int styleHash() {
        return (colorRgb() * 31 + fillAlpha()) * 31 + borderAlpha();
    }
}

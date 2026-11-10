package com.dshdev.xaerochunkmark;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

/**
 * Draws the client-side chunk marks on Xaero's World Map.
 *
 * <p>Xaero's map blends the colours returned here over the map pixels. The colour integers use the same layout as
 * the map's own pixels: alpha in the low byte, then red, green and blue.</p>
 */
public final class ChunkMarkHighlighter extends ChunkHighlighter {
    private final int[] colors = new int[5];

    private ResourceKey<Level> cachedDimension;
    private int cachedChunkX;
    private int cachedChunkZ;
    private MutableComponent cachedTooltip;

    public ChunkMarkHighlighter() {
        // true: marks are visible over chunks whose terrain has never been discovered, like claims are.
        super(true);
    }

    private static String dimensionKey(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    private static ChunkMarkStorage storage() {
        return ChunkMarkManager.get().storage();
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return storage().regionHasMarks(dimensionKey(dimension), regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return storage().isMarked(dimensionKey(dimension), chunkX, chunkZ);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        String key = dimensionKey(dimension);
        ChunkMarkStorage store = storage();
        if (!store.isMarked(key, chunkX, chunkZ)) {
            return null;
        }
        int rgb = ChunkMarkConfig.colorRgb();
        // Convert 0xRRGGBB into the map's BBGGRRAA layout.
        int formatted = (rgb & 0xFF) << 24 | (rgb >> 8 & 0xFF) << 16 | (rgb >> 16 & 0xFF) << 8;
        int center = formatted | ChunkMarkConfig.fillAlpha();
        int side = formatted | ChunkMarkConfig.borderAlpha();

        this.colors[0] = center;
        this.colors[1] = store.isMarked(key, chunkX, chunkZ - 1) ? center : side;
        this.colors[2] = store.isMarked(key, chunkX + 1, chunkZ) ? center : side;
        this.colors[3] = store.isMarked(key, chunkX, chunkZ + 1) ? center : side;
        this.colors[4] = store.isMarked(key, chunkX - 1, chunkZ) ? center : side;
        return this.colors;
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        int data = storage().regionHash(dimensionKey(dimension), regionX, regionZ);
        // The style is part of the hash so that editing the config repaints existing marks.
        return data * 31 + ChunkMarkConfig.styleHash();
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!chunkIsHighlit(dimension, chunkX, chunkZ)) {
            return null;
        }
        if (dimension.equals(this.cachedDimension) && chunkX == this.cachedChunkX && chunkZ == this.cachedChunkZ
                && this.cachedTooltip != null) {
            return this.cachedTooltip;
        }
        MutableComponent icon = Component.literal("\u25a0 ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(ChunkMarkConfig.colorRgb())));
        MutableComponent tooltip = Component.empty()
                .append(icon)
                .append(Component.translatable("gui.xaero_chunkmark.tooltip_coords", chunkX, chunkZ));
        this.cachedDimension = dimension;
        this.cachedChunkX = chunkX;
        this.cachedChunkZ = chunkZ;
        this.cachedTooltip = tooltip;
        return tooltip;
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return getChunkHighlightSubtleTooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> tooltips, ResourceKey<Level> dimension,
                                                 int blockX, int blockZ, int width) {
        // The marks are a world map feature; the minimap has its own highlighter registry which we do not touch.
    }
}

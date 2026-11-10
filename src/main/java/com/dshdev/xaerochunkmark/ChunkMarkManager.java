package com.dshdev.xaerochunkmark;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.DimensionHighlighterHandler;
import xaero.map.highlight.HighlighterRegistry;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

/**
 * Client-side coordinator: keeps the storage in sync with the world that Xaero's map is currently showing, makes sure
 * the highlighter is registered with that map session and asks the map to repaint the affected regions.
 */
public final class ChunkMarkManager {
    private static final ChunkMarkManager INSTANCE = new ChunkMarkManager();

    private final ChunkMarkHighlighter highlighter = new ChunkMarkHighlighter();
    private ChunkMarkStorage storage;
    private String worldKey;
    private boolean registrationWarned;

    private ChunkMarkManager() {
    }

    public static ChunkMarkManager get() {
        return INSTANCE;
    }

    public ChunkMarkHighlighter highlighter() {
        return this.highlighter;
    }

    public ChunkMarkStorage storage() {
        if (this.storage == null) {
            this.storage = new ChunkMarkStorage(FMLPaths.CONFIGDIR.get().resolve(XaeroChunkMark.MODID));
        }
        return this.storage;
    }

    public void onClientTick() {
        MapProcessor processor = currentProcessor();
        if (processor == null) {
            return;
        }
        String worldId = processor.getCurrentWorldId();
        if (worldId == null || worldId.isEmpty()) {
            return;
        }
        String multiworld = processor.getCurrentMWId();
        String key = multiworld == null || multiworld.isEmpty() ? worldId : worldId + "|" + multiworld;
        if (!key.equals(this.worldKey)) {
            this.worldKey = key;
            // Loads the marks of the world we just joined and saves the previous one.
            storage().open(key);
            ChunkMarkManager.clearAllHashes(processor);
        }
        registerHighlighter(processor);
        storage().flushIfDirty();
        String error = storage().pollError();
        if (error != null) {
            XaeroChunkMark.LOGGER.error("Chunk mark storage error: {}", error);
        }
    }

    public void toggle(ResourceKey<Level> dimension, int left, int top, int right, int bottom, boolean mark) {
        String key = dimension.location().toString();
        if (storage().applyRect(key, left, top, right, bottom, mark) == 0) {
            return;
        }
        MapProcessor processor = currentProcessor();
        if (processor == null) {
            return;
        }
        repaint(processor, dimension, left, top, right, bottom);
        storage().flushIfDirty();
    }

    public void onConfigChanged() {
        MapProcessor processor = currentProcessor();
        if (processor != null) {
            clearAllHashes(processor);
        }
    }

    public void flush() {
        if (this.storage != null) {
            this.storage.flushBlocking();
        }
    }

    private static MapProcessor currentProcessor() {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            return session == null ? null : session.getMapProcessor();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void registerHighlighter(MapProcessor processor) {
        HighlighterRegistry registry = processor.getHighlighterRegistry();
        if (registry == null) {
            return;
        }
        List<AbstractHighlighter> registered = registry.getHighlighters();
        if (registered.contains(this.highlighter)) {
            return;
        }
        try {
            // The registry's list is swapped for a fresh one instead of being mutated: Xaero seals the original list
            // with Collections.unmodifiableList() once the session has been built.
            List<AbstractHighlighter> updated = new ArrayList<>(registered.size() + 1);
            updated.addAll(registered);
            updated.add(this.highlighter);
            Field field = HighlighterRegistry.class.getDeclaredField("highlighters");
            field.setAccessible(true);
            field.set(registry, updated);
            XaeroChunkMark.LOGGER.info("Registered chunk mark highlighter with Xaero's World Map");
            clearAllHashes(processor);
        } catch (Throwable t) {
            if (!this.registrationWarned) {
                this.registrationWarned = true;
                XaeroChunkMark.LOGGER.error("Could not register the chunk mark highlighter with Xaero's World Map", t);
            }
        }
    }

    private static void clearAllHashes(MapProcessor processor) {
        try {
            MapWorld world = processor.getMapWorld();
            if (world == null) {
                return;
            }
            for (MapDimension dimension : world.getDimensionsList()) {
                DimensionHighlighterHandler handler = dimension.getHighlightHandler();
                if (handler != null) {
                    handler.clearCachedHashes();
                }
            }
        } catch (Throwable t) {
            XaeroChunkMark.LOGGER.warn("Failed to invalidate Xaero's World Map highlights", t);
        }
    }

    private static void repaint(MapProcessor processor, ResourceKey<Level> dimension,
                                int left, int top, int right, int bottom) {
        try {
            MapWorld world = processor.getMapWorld();
            if (world == null) {
                return;
            }
            MapDimension mapDimension = world.getDimension(dimension);
            if (mapDimension == null) {
                return;
            }
            DimensionHighlighterHandler handler = mapDimension.getHighlightHandler();
            if (handler == null) {
                return;
            }
            // Clear the touched regions plus their neighbours, because the outline colour of a region's edge chunks
            // depends on the chunks immediately across the region border.
            for (int regionX = ChunkMarkStorage.regionOf(left) - 1; regionX <= ChunkMarkStorage.regionOf(right) + 1; regionX++) {
                for (int regionZ = ChunkMarkStorage.regionOf(top) - 1; regionZ <= ChunkMarkStorage.regionOf(bottom) + 1; regionZ++) {
                    handler.clearCachedHash(regionX, regionZ);
                }
            }
        } catch (Throwable t) {
            XaeroChunkMark.LOGGER.warn("Failed to repaint Xaero's World Map highlights", t);
        }
    }
}

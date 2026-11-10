package com.dshdev.xaerochunkmark;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/**
 * Adds the "mark chunk(s)" entry to the world map's right-click menu.
 *
 * <p>The map screen exposes no API for menu entries, so this is called from a mixin injected at the end of
 * {@code GuiMap#getRightClickOptions()}. Everything the entry needs (the hovered chunk and the dragged selection) is
 * private state of the map screen and is read reflectively so that a renamed field in a future Xaero version only
 * disables the entry instead of crashing the game.</p>
 */
public final class ChunkMarkClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Field rightClickX;
    private static Field rightClickZ;
    private static Field rightClickDimension;
    private static Field selection;
    private static boolean fieldsResolved;
    private static boolean fieldsUnavailable;
    private static boolean warned;

    private ChunkMarkClient() {
    }

    /** Mixin entry point: called after the map screen has built its own menu entries. */
    public static void addRightClickOptions(GuiMap screen, List<RightClickOption> options) {
        if (!resolveFields()) {
            return;
        }
        try {
            MapTileSelection mapSelection = (MapTileSelection) selection.get(screen);
            if (mapSelection == null) {
                // The menu was opened on a map element (waypoint, player, ...) rather than on the map itself.
                return;
            }
            ResourceKey<Level> dimension = (ResourceKey<Level>) rightClickDimension.get(screen);
            if (dimension == null) {
                return;
            }
            int blockX = rightClickX.getInt(screen);
            int blockZ = rightClickZ.getInt(screen);

            int startX = mapSelection.getStartX();
            int startZ = mapSelection.getStartZ();
            int endX = mapSelection.getEndX();
            int endZ = mapSelection.getEndZ();

            final int left;
            final int top;
            final int right;
            final int bottom;
            if (endX == 0 && endZ == 0 && (startX != 0 || startZ != 0)) {
                // A very fast click can open the menu before the screen has had a frame to expand the selection.
                left = right = blockX >> 4;
                top = bottom = blockZ >> 4;
            } else {
                left = Math.min(startX, endX);
                right = Math.max(startX, endX);
                top = Math.min(startZ, endZ);
                bottom = Math.max(startZ, endZ);
            }

            long chunkCount = (long) (right - left + 1) * (long) (bottom - top + 1);
            if (chunkCount <= 0 || chunkCount > 65536) {
                return;
            }

            final ResourceKey<Level> markDimension = dimension;
            String dimensionKey = dimension.location().toString();
            boolean allMarked = ChunkMarkManager.get().storage()
                    .allMarked(dimensionKey, left, top, right, bottom);
            final boolean mark = !allMarked;

            String translationKey;
            Object[] arguments;
            if (chunkCount == 1) {
                translationKey = mark ? "gui.xaero_chunkmark.mark_chunk" : "gui.xaero_chunkmark.unmark_chunk";
                arguments = new Object[]{left, top};
            } else {
                translationKey = mark ? "gui.xaero_chunkmark.mark_area" : "gui.xaero_chunkmark.unmark_area";
                arguments = new Object[]{(int) chunkCount};
            }

            options.add(new RightClickOption(translationKey, options.size(), screen) {
                @Override
                public void onAction(Screen openedScreen) {
                    ChunkMarkManager.get().toggle(markDimension, left, top, right, bottom, mark);
                }
            }.setNameFormatArgs(arguments));
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.error("Could not add the chunk mark entry to Xaero's World Map context menu", t);
            }
        }
    }

    private static boolean resolveFields() {
        if (fieldsResolved) {
            return !fieldsUnavailable;
        }
        fieldsResolved = true;
        try {
            rightClickX = accessible("rightClickX");
            rightClickZ = accessible("rightClickZ");
            rightClickDimension = accessible("rightClickDim");
            selection = accessible("mapTileSelection");
            return true;
        } catch (Throwable t) {
            fieldsUnavailable = true;
            LOGGER.error("Xaero's World Map screen layout is not recognised; chunk marking is disabled", t);
            return false;
        }
    }

    private static Field accessible(String name) throws NoSuchFieldException {
        Field field = GuiMap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

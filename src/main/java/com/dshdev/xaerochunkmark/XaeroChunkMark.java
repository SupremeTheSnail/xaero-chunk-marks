package com.dshdev.xaerochunkmark;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Xaero's Chunk Marks - a client-only add-on for Xaero's World Map.
 *
 * <p>It adds a "mark chunk" entry to the map's right-click menu and paints the marked chunks on the map. Marks are
 * stored locally in {@code config/xaerochunkmark}; nothing is ever sent to the server, so no chunk is claimed and no
 * chunk is force-loaded.</p>
 */
@Mod(XaeroChunkMark.MODID)
public final class XaeroChunkMark {
    public static final String MODID = "xaerochunkmark";
    public static final Logger LOGGER = LogUtils.getLogger();

    public XaeroChunkMark() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            // The whole mod is client-side; on a dedicated server there is nothing to do.
            return;
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ChunkMarkConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("Xaero's Chunk Marks loaded");
    }
}

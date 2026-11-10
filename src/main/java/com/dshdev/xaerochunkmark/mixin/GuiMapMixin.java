package com.dshdev.xaerochunkmark.mixin;

import com.dshdev.xaerochunkmark.ChunkMarkClient;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/**
 * Appends the chunk mark entry to Xaero's World Map right-click menu.
 *
 * <p>{@code remap = false} everywhere: both the target class and the target method belong to Xaero's mod, so their
 * names are already the runtime names and must not be run through the Minecraft mapping.</p>
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
    @Inject(method = "getRightClickOptions()Ljava/util/ArrayList;", at = @At("RETURN"), remap = false)
    private void xaerochunkmark$appendChunkMarkEntry(CallbackInfoReturnable<ArrayList<RightClickOption>> info) {
        ChunkMarkClient.addRightClickOptions((GuiMap) (Object) this, info.getReturnValue());
    }
}

package com.skilles.chronoclones.client;

import com.skilles.chronoclones.client.preview.GoggleCache;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.item.RecordingTooltips;
import com.skilles.chronoclones.menu.client.RoutineEditorScreen;
import com.skilles.chronoclones.network.ChronoclonesNetwork;
import com.skilles.chronoclones.network.RoutinePayloads;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

/** Loader-neutral client wiring; each loader's client entrypoint calls {@link #init()}. */
public final class ChronoclonesClientInit {

    private ChronoclonesClientInit() {}

    public static void init() {
        ChronoclonesNetwork.clientReplyHandler = PreviewCache::accept;
        ChronoclonesNetwork.clientHighlightHandler = RecordingHighlights::accept;
        ChronoclonesNetwork.clientGoggleHandler = GoggleCache::accept;
        ChronoclonesNetwork.clientRoutineHandler = ChronoclonesClientInit::openRoutineEditor;
        ChronoclonesNetwork.clientSkinHandler = AuthorSkins::accept;
        RecordingTooltips.detailRequested = () -> {
            //? if >=26 {
            var window = Minecraft.getInstance().getWindow();
            //?} else {
            /*long window = Minecraft.getInstance().getWindow().getWindow();
            *///?}
            return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        };
    }

    /** Client caches emptied when the player leaves a world. */
    public static void disconnected() {
        PreviewCache.forget();
        GoggleCache.forget();
        RecordingHighlights.forget();
        AuthorSkins.forget();
    }

    private static void openRoutineEditor(RoutinePayloads.Open open) {
        Minecraft.getInstance().setScreenAndShow(
                new RoutineEditorScreen(open.source(), open.recording(), open.revision()));
    }
}

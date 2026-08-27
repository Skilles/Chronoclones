package com.skilles.chronoclones.client;

import java.util.EnumMap;
import java.util.Map;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.network.AnchorNudgePayload;
import com.skilles.chronoclones.recording.LocalSpace;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.skilles.chronoclones.platform.PlatformClientNetwork;

public final class NudgeKeys {

    private NudgeKeys() {}

    //? if >=26 {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Chronoclones.id("nudge"));
    //?} else {
    /*private static final String CATEGORY = "key.categories.chronoclones.nudge";
    *///?}

    private static final Map<NudgeDirection.Key, KeyMapping> KEYS =
            new EnumMap<>(NudgeDirection.Key.class);
    private static final KeyMapping reset;

    static {
        bind(NudgeDirection.Key.FORWARD, "forward", InputConstants.KEY_UP);
        bind(NudgeDirection.Key.BACK, "back", InputConstants.KEY_DOWN);
        bind(NudgeDirection.Key.LEFT, "left", InputConstants.KEY_LEFT);
        bind(NudgeDirection.Key.RIGHT, "right", InputConstants.KEY_RIGHT);
        bind(NudgeDirection.Key.UP, "up", InputConstants.KEY_PAGEUP);
        bind(NudgeDirection.Key.DOWN, "down", InputConstants.KEY_PAGEDOWN);

        reset = new KeyMapping("key.chronoclones.nudge.reset", InputConstants.Type.KEYSYM,
                InputConstants.KEY_END, CATEGORY);
    }

    private static void bind(NudgeDirection.Key key, String name, int code) {
        KEYS.put(key, new KeyMapping("key.chronoclones.nudge." + name,
                InputConstants.Type.KEYSYM, code, CATEGORY));
    }

    /** Every mapping the mod owns, for whichever registry the loader uses. */
    public static void forEachMapping(java.util.function.Consumer<KeyMapping> registrar) {
        KEYS.values().forEach(registrar);
        registrar.accept(reset);
    }

    /** Called at the end of every client tick. */
    public static void tick() {
        PreviewCache.Target target = PreviewCache.current();
        Minecraft client = Minecraft.getInstance();
        if (target == null || client.player == null) {
            // Presses queue up until something consumes them, and would then all arrive at once on
            // the next anchor looked at.
            drain();
            return;
        }

        Direction playerFacing = LocalSpace.snapToCardinal(client.player.getYRot());

        for (Map.Entry<NudgeDirection.Key, KeyMapping> entry : KEYS.entrySet()) {
            while (entry.getValue().consumeClick()) {
                send(target.anchorPos(),
                        NudgeDirection.step(entry.getKey(), playerFacing, target.facing()));
            }
        }
        while (reset.consumeClick()) {
            send(target.anchorPos(), BlockPos.ZERO);
        }
    }

    private static void drain() {
        for (KeyMapping mapping : KEYS.values()) {
            while (mapping.consumeClick()) {
                // discarded
            }
        }
        while (reset.consumeClick()) {
            // discarded
        }
    }

    private static void send(BlockPos anchorPos, BlockPos delta) {
        PlatformClientNetwork.sendToServer(new AnchorNudgePayload(anchorPos, delta));
        PreviewCache.nudged(delta);
    }
}

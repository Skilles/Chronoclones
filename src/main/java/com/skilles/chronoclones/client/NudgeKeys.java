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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Nudging an anchor's routine with the keyboard, while its preview is visible.
 */
@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class NudgeKeys {

    private NudgeKeys() {}

    /** 26.x made key categories a registry keyed by Identifier. */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Chronoclones.id("nudge"));

    private static final Map<NudgeDirection.Key, KeyMapping> KEYS =
            new EnumMap<>(NudgeDirection.Key.class);
    private static KeyMapping reset;

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        // Arrows for the horizontal plane, Page Up/Down for height.
        bind(event, NudgeDirection.Key.FORWARD, "forward", InputConstants.KEY_UP);
        bind(event, NudgeDirection.Key.BACK, "back", InputConstants.KEY_DOWN);
        bind(event, NudgeDirection.Key.LEFT, "left", InputConstants.KEY_LEFT);
        bind(event, NudgeDirection.Key.RIGHT, "right", InputConstants.KEY_RIGHT);
        bind(event, NudgeDirection.Key.UP, "up", InputConstants.KEY_PAGEUP);
        bind(event, NudgeDirection.Key.DOWN, "down", InputConstants.KEY_PAGEDOWN);

        reset = new KeyMapping("key.chronoclones.nudge.reset", InputConstants.Type.KEYSYM,
                InputConstants.KEY_END, CATEGORY);
        event.register(reset);
    }

    private static void bind(RegisterKeyMappingsEvent event, NudgeDirection.Key key,
                             String name, int code) {
        KeyMapping mapping = new KeyMapping("key.chronoclones.nudge." + name,
                InputConstants.Type.KEYSYM, code, CATEGORY);
        KEYS.put(key, mapping);
        event.register(mapping);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        PreviewCache.Target target = PreviewCache.current();
        if (target == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        // Snapped, because local space is built on quarter-turns and nothing else maps the block
        // grid onto itself.
        Direction playerFacing = LocalSpace.snapToCardinal(client.player.getYRot());

        for (Map.Entry<NudgeDirection.Key, KeyMapping> entry : KEYS.entrySet()) {
            // consumeClick, not isDown: one press is one block.
            while (entry.getValue().consumeClick()) {
                send(target.anchorPos(),
                        NudgeDirection.step(entry.getKey(), playerFacing, target.facing()));
            }
        }
        while (reset != null && reset.consumeClick()) {
            send(target.anchorPos(), BlockPos.ZERO);
        }
    }

    private static void send(BlockPos anchorPos, BlockPos delta) {
        ClientPacketDistributor.sendToServer(new AnchorNudgePayload(anchorPos, delta));
        // Move the preview with it; see PreviewCache.nudged.
        PreviewCache.nudged(delta);
    }
}

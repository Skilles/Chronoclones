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
 * Nudging an anchor's routine with the keyboard, while you can see it.
 *
 * <p>Bound only while the preview is up and showing the anchor's <em>own</em> routine. That is the
 * whole interaction: you look at an anchor, you see where its work lands, you move it. Making these
 * keys live all the time would be worse than useless — they are arrow keys, and something has to
 * decide which of several anchors you meant.
 *
 * <p>Works with a shard in hand too. The offset belongs to the anchor rather than to the routine, so
 * aiming one before committing to it is the same operation as adjusting one already running — and it
 * is the more useful of the two, since it is the point at which you can still see you got it wrong.
 */
@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class NudgeKeys {

    private NudgeKeys() {}

    /**
     * Registered rather than named by string: 26.x made key categories a registry keyed by
     * Identifier, so the display name comes from a lang key derived from the id.
     */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Chronoclones.id("nudge"));

    private static final Map<NudgeDirection.Key, KeyMapping> KEYS =
            new EnumMap<>(NudgeDirection.Key.class);
    private static KeyMapping reset;

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        // Arrows for the horizontal plane and Page Up/Down for height. All rebindable: arrows are
        // unbound in vanilla but plenty of people have their own ideas about them.
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
            // consumeClick rather than isDown: one press is one block, and holding a key should not
            // walk the routine across the base in half a second.
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
        // Move the preview with it rather than dropping it — see PreviewCache.nudged for why
        // waiting for the server to answer made every press flash back to the original origin.
        PreviewCache.nudged(delta);
    }
}

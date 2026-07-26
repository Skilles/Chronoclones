package com.skilles.chronoclones.client.preview;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.network.GogglePayloads;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Every anchor the goggles can currently see.
 *
 * <p>Separate from {@link PreviewCache}, which answers a different question. That one is about the
 * anchor under the crosshair and is driven by where you are looking; this is about the room you are
 * standing in and is driven by a timer. Conflating them was what made a second source impossible to
 * add — one cached routine, keyed by one position.
 *
 * <p>Polled rather than pushed. A push would mean the server tracking which players are wearing
 * goggles and re-sending whenever any anchor in range changed, which is a subscription system for a
 * feature whose whole job is "roughly what is around me".
 */
public final class GoggleCache {

    private GoggleCache() {}

    /** Two seconds. Long enough that walking around does not flood, short enough to feel live. */
    private static final long REFRESH_INTERVAL_TICKS = 40;

    private static List<GogglePayloads.Entry> anchors = List.of();
    private static boolean truncated;
    private static long lastRequestTick = Long.MIN_VALUE;

    /** The anchors to draw, refreshing in the background if the goggles are on. */
    public static List<PreviewCache.Target> current() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }
        if (!client.player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CHRONO_GOGGLES.get())) {
            // Taking them off clears immediately rather than fading out over the refresh interval.
            forget();
            return List.of();
        }

        long now = client.level.getGameTime();
        if (now - lastRequestTick >= REFRESH_INTERVAL_TICKS) {
            lastRequestTick = now;
            net.neoforged.neoforge.client.network.ClientPacketDistributor
                    .sendToServer(new GogglePayloads.Request());
        }

        List<PreviewCache.Target> targets = new ArrayList<>(anchors.size());
        for (GogglePayloads.Entry entry : anchors) {
            targets.add(new PreviewCache.Target(entry.pos(), entry.facing(), entry.recording(),
                    false, entry.failure(), entry.originOffset()));
        }
        return targets;
    }

    /** Whether the server had more anchors than it was willing to send. */
    public static boolean isTruncated() {
        return truncated;
    }

    /** Called on the main thread by the payload handler. */
    public static void accept(GogglePayloads.Reply reply) {
        anchors = reply.anchors();
        truncated = reply.truncated();
    }

    public static void forget() {
        anchors = List.of();
        truncated = false;
        lastRequestTick = Long.MIN_VALUE;
    }
}

package com.skilles.chronoclones.client.preview;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.network.GogglePayloads;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;

public final class GoggleCache {

    private GoggleCache() {}

    private static final long REFRESH_INTERVAL_TICKS = 40;

    private static List<GogglePayloads.Entry> anchors = List.of();
    private static boolean truncated;
    private static final RequestClock CLOCK = new RequestClock();

    public static List<PreviewCache.Target> current() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }
        if (!client.player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CHRONO_GOGGLES.get())) {
            forget();
            return List.of();
        }

        long now = client.level.getGameTime();
        if (CLOCK.claim(now, REFRESH_INTERVAL_TICKS)) {
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

    public static boolean isTruncated() {
        return truncated;
    }

    public static void accept(GogglePayloads.Reply reply) {
        anchors = reply.anchors();
        truncated = reply.truncated();
    }

    public static void forget() {
        anchors = List.of();
        truncated = false;
        CLOCK.reset();
    }
}

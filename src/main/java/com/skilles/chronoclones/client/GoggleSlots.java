package com.skilles.chronoclones.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.skilles.chronoclones.client.preview.GoggleCache;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public final class GoggleSlots {

    private GoggleSlots() {}

    public record Session(Set<Integer> touched, Map<Integer, ItemStack> carried) {}

    public static @Nullable Session sessionFor(AbstractContainerScreen<?> screen) {
        BlockPos open = openContainerPos();
        if (open == null) {
            return null;
        }
        return collect(GoggleCache.current(), open, screen.getMenu().slots.size());
    }

    static @Nullable Session collect(List<PreviewCache.Target> anchors, BlockPos open, int menuSize) {
        Set<Integer> touched = new HashSet<>();
        Map<Integer, ItemStack> carried = new HashMap<>();

        for (PreviewCache.Target target : anchors) {
            for (TimedAction timed : target.recording().actions()) {
                if (!(timed.action() instanceof ChronoAction.UseContainer session)) {
                    continue;
                }
                if (!(session.target() instanceof MenuTarget.Block block)
                        || !target.placement().toWorld(block.localPos()).equals(open)) {
                    continue;
                }
                if (session.menuSize() != menuSize) {
                    continue;
                }
                for (SessionStep step : session.steps()) {
                    step.squares().forEach(touched::add);
                }
                for (ChronoAction.UseContainer.CarrierSlot slot : session.carrier()) {
                    carried.putIfAbsent(slot.menuSlot(), slot.stack());
                }
            }
        }

        return touched.isEmpty() && carried.isEmpty() ? null : new Session(touched, carried);
    }

    private static @Nullable BlockPos openContainerPos() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }
}

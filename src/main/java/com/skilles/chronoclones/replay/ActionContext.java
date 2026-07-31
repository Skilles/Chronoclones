package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ActionSettings.ToolRule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** Everything one action needs to know about the anchor running it. */
public record ActionContext(ServerLevel level, Placement placement, Operator operator,
                            ItemStacksResourceHandler inventory, ActionSettings settings,
                            AnchorFakePlayer actor, int cloneIndex) {
    public FakePlayer acquire(Vec3 position, float yaw, float pitch, ItemStack held) {
        return acquire(position, yaw, pitch, InteractionHand.MAIN_HAND, held);
    }

    public FakePlayer acquire(Vec3 position, float yaw, float pitch, InteractionHand hand,
                              ItemStack held) {
        return actor.acquire(level, operator, cloneIndex, position, yaw, pitch, hand, held);
    }

    public void release(FakePlayer player) {
        actor.release(operator, player);
    }

    public ResourceHandler<ItemResource> items() {
        return inventory;
    }

    public BlockPos anchorPos() {
        return placement.anchorPos();
    }

    public SlotRule slot() {
        return settings.slot();
    }

    public ToolRule tool() {
        return settings.tool();
    }

    public TargetRule target() {
        return settings.target();
    }

    public boolean recordedSubject() {
        return settings.recordedSubject();
    }
}

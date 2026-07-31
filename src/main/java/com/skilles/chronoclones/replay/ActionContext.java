package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ActionSettings.ToolRule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Everything one action needs to know about the anchor running it.
 *
 * <p>The executors used to take these five as five parameters, in an order each of them chose, and
 * adding a sixth meant touching every signature. They are one thing -- "this clone, on this anchor,
 * under these settings" -- and travel together.
 *
 * @param inventory the squares of the one clone performing this action, not the anchor's whole
 *                  storage: a container session lends from it, so it must be the concrete handler
 * @param actor     the anchor's pool of fake players, which every world mutation goes through
 * @param cloneIndex which clone of this anchor is acting, so it acts as its own player
 */
public record ActionContext(ServerLevel level, Placement placement, Operator operator,
                            ItemStacksResourceHandler inventory, ActionSettings settings,
                            AnchorFakePlayer actor, int cloneIndex) {

    /** This clone's player, positioned and equipped for one action. */
    public FakePlayer acquire(Vec3 position, float yaw, float pitch, ItemStack held) {
        return actor.acquire(level, operator, cloneIndex, position, yaw, pitch, held);
    }

    /** Hands it back, banking whatever experience the action left it holding. */
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

    /** Whether this acts only on the thing it recorded, or on whatever is there instead. */
    public boolean recordedSubject() {
        return settings.recordedSubject();
    }
}

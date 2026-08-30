package com.skilles.chronoclones.replay;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The items a right-click pops on the ground, gathered into the anchor afterwards.
 *
 * <p>Picking a berry bush or shearing a sheep hands the player nothing: the harvest is spawned
 * at the block or creature, and vanilla players walk over it. A clone stands still, so what it
 * popped is telling the difference between what the interaction spawned and what was already
 * lying there.
 */
final class PoppedDrops {

    /** Wider than popResource's spread, narrower than a neighbouring farm plot. */
    private static final double REACH = 2.0;

    private final AABB around;
    private final Set<Integer> before;

    private PoppedDrops(AABB around, Set<Integer> before) {
        this.around = around;
        this.before = before;
    }

    static PoppedDrops watch(ServerLevel level, Vec3 center) {
        AABB around = AABB.ofSize(center, REACH * 2, REACH * 2, REACH * 2);
        Set<Integer> before = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, around)) {
            before.add(entity.getId());
        }
        return new PoppedDrops(around, before);
    }

    /** Everything the interaction spawned goes to the anchor; what does not fit stays put. */
    void gather(ActionContext ctx) {
        for (ItemEntity entity : ctx.level().getEntitiesOfClass(ItemEntity.class, around)) {
            if (before.contains(entity.getId())) {
                continue;
            }
            ItemStack stack = entity.getItem();
            int stored = ctx.items().insert(stack, stack.getCount());
            if (stored >= stack.getCount()) {
                entity.discard();
            } else if (stored > 0) {
                entity.setItem(stack.copyWithCount(stack.getCount() - stored));
            }
        }
    }
}

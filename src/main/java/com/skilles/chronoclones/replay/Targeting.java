package com.skilles.chronoclones.replay;

import java.util.Comparator;
import java.util.List;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Choosing which creature an action acts on.
 *
 * <p>Shared because three actions ask the same question -- swinging at something, right-clicking
 * something, and opening something's menu -- and answering it three ways is how they drift apart.
 */
final class Targeting {

    private Targeting() {}

    /** The reach this rule asks for, never more than the anchor is allowed. */
    static AABB boxAround(Vec3 worldPos, TargetRule rule) {
        return new AABB(worldPos, worldPos)
                .inflate(rule.radiusWithin(ChronoclonesConfig.MAX_RADIUS.getAsInt()));
    }

    /**
     * What this action acts on, out of everything the rule already admitted.
     *
     * <p>The recorded kind, unless the owner has widened the action to anything -- in which case the
     * recorded kind is still preferred, and anything else will do.
     *
     * <p>Recorded-only is the default, and deliberately: a routine taught on a cow that will settle
     * for whatever wandered past is a routine that shears the neighbour's sheep and kills the
     * villager. Widening it is a thing somebody has to ask for, the same as widening a break from
     * cobblestone to any block.
     *
     * @param recordedOnly whether {@code expected} is the answer or merely the preference
     * @return null when nothing here qualifies
     */
    static <T extends Entity> @Nullable T choose(List<T> candidates, Vec3 worldPos,
                                                 EntityType<?> expected, boolean recordedOnly) {
        if (candidates.isEmpty()) {
            return null;
        }
        @Nullable T recorded = candidates.stream()
                .filter(e -> e.getType() == expected)
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                .orElse(null);

        if (recorded != null || recordedOnly) {
            return recorded;
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                .orElseThrow();
    }
}

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

/** Choosing which creature an action acts on. */
final class Targeting {

    private Targeting() {}

    static AABB boxAround(Vec3 worldPos, TargetRule rule) {
        return new AABB(worldPos, worldPos)
                .inflate(rule.radiusWithin(ChronoclonesConfig.MAX_RADIUS.getAsInt()));
    }

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

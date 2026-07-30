package com.skilles.chronoclones.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The experience one clone is carrying, in points.
 *
 * <p>Points rather than levels, because that is what the world deals in: an ore drops points, a
 * furnace hands over points, and enchanting spends levels that are only a reading of points. The
 * level and the bar underneath it are derived here with vanilla's own thresholds so the anchor's bar
 * and a player's own agree.
 */
public record ExperienceStore(int points) {

    public static final ExperienceStore EMPTY = new ExperienceStore(0);

    public static final Codec<ExperienceStore> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("points", 0).forGetter(ExperienceStore::points)
    ).apply(i, ExperienceStore::new));

    public ExperienceStore {
        points = Math.max(0, points);
    }

    public boolean isEmpty() {
        return points == 0;
    }

    public ExperienceStore add(int amount) {
        return amount <= 0 ? this : new ExperienceStore(points + amount);
    }

    /** Whether this holds at least {@code levels} whole levels, which is what enchanting asks. */
    public boolean canAfford(int levels) {
        return level() >= levels;
    }

    /** Spends points outright, for a cost already worked out in points. */
    public ExperienceStore spend(int amount) {
        return new ExperienceStore(Math.max(0, points - amount));
    }

    public int level() {
        return levelOf(points);
    }

    /** How far into the current level, 0 to 1, which is what the bar draws. */
    public float progress() {
        return progressOf(points);
    }

    /**
     * The whole level this many points reaches, by walking vanilla's widening bands.
     */
    public static int levelOf(int points) {
        int level = 0;
        int remaining = points;
        while (remaining >= neededForNextLevel(level)) {
            remaining -= neededForNextLevel(level);
            level++;
        }
        return level;
    }

    public static float progressOf(int points) {
        int level = 0;
        int remaining = points;
        while (remaining >= neededForNextLevel(level)) {
            remaining -= neededForNextLevel(level);
            level++;
        }
        return (float) remaining / neededForNextLevel(level);
    }

    /** Vanilla's {@code Player.getXpNeededForNextLevel}, which is not reachable without a player. */
    public static int neededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    /** Total points to reach {@code levels} from nothing, for turning a level cost into points. */
    public static int pointsForLevels(int levels) {
        int total = 0;
        for (int level = 0; level < levels; level++) {
            total += neededForNextLevel(level);
        }
        return total;
    }
}

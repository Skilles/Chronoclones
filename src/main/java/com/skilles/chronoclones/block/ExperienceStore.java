package com.skilles.chronoclones.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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

    public boolean canAfford(int levels) {
        return level() >= levels;
    }

    public ExperienceStore spend(int amount) {
        return new ExperienceStore(Math.max(0, points - amount));
    }

    public int level() {
        return levelOf(points);
    }

    public float progress() {
        return progressOf(points);
    }

    public static int pointsFor(int level, float progress) {
        return pointsForLevels(level) + Math.round(progress * neededForNextLevel(level));
    }

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

    public static int neededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    public static int pointsForLevels(int levels) {
        int total = 0;
        for (int level = 0; level < levels; level++) {
            total += neededForNextLevel(level);
        }
        return total;
    }
}

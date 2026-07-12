package com.mooswqz.moostensuraaddon.util;

import net.minecraft.world.entity.player.Player;

public class XpUtils {
    public static int getLevelEquivalentXpCost(int levelEquivalent) {
        return getTotalXpForLevel(levelEquivalent);
    }

    public static boolean hasLevelEquivalentXp(Player player, int levelEquivalent) {
        return getTotalXp(player) >= getLevelEquivalentXpCost(levelEquivalent);
    }

    public static void deductLevelEquivalentXp(Player player, int levelEquivalent) {
        deductExactXp(player, getLevelEquivalentXpCost(levelEquivalent));
    }

    public static void deductRelativeLevels(Player player, int levelsToDeduct) {
        if (player == null || levelsToDeduct <= 0) {
            return;
        }

        int currentLevel = Math.max(0, player.experienceLevel);
        int targetLevel = Math.max(0, currentLevel - levelsToDeduct);

        float currentProgress = Math.max(0.0F, Math.min(1.0F, player.experienceProgress));
        int targetProgressXp = Math.round(currentProgress * getXpNeededForNextLevel(targetLevel));

        int newTotalXp = getTotalXpForLevel(targetLevel) + targetProgressXp;

        setTotalXp(player, newTotalXp);
    }

    public static void deductExactXp(Player player, int amount) {
        int currentXp = getTotalXp(player);
        int newXp = Math.max(currentXp - amount, 0);

        setTotalXp(player, newXp);
    }

    public static void setTotalXp(Player player, int totalXp) {
        player.experienceLevel = 0;
        player.experienceProgress = 0.0F;
        player.totalExperience = 0;

        player.giveExperiencePoints(Math.max(0, totalXp));
    }

    public static int getTotalXp(Player player) {
        int level = player.experienceLevel;
        int totalXp = getTotalXpForLevel(level);

        totalXp += Math.round(player.experienceProgress * player.getXpNeededForNextLevel());

        return totalXp;
    }

    public static int getTotalXpForLevel(int level) {
        if (level <= 15) {
            return level * level + 6 * level;
        }

        if (level <= 30) {
            return (int) (2.5D * level * level - 40.5D * level + 360.0D);
        }

        return (int) (4.5D * level * level - 162.5D * level + 2220.0D);
    }

    public static int getXpNeededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }

        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }

        return 7 + level * 2;
    }
}
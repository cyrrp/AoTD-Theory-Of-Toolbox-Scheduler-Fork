// file: data/kaysaar/aotd/tot/scripts/trade/contracts/AoTDTradeContractLevelData.java
package data.kaysaar.aotd.tot.scripts.trade.contracts;

import java.util.LinkedHashMap;

public class AoTDTradeContractLevelData {

    public int currXp;

    public static final int LEVELS_MAX = 15;

    /**
     * Total XP required to be at each level (1..15). Level 1 starts at 0.
     *
     * <p>NOTE: Kept as-is (max = 31500) per your earlier UI/max-exp requirement.
     */
    public static final int[] XP_TO_REACH_LEVEL =
            new int[] {
                0, // 1
                200, // 2
                520, // 3
                950, // 4
                1500, // 5
                2200, // 6
                3100, // 7
                4300, // 8
                5900, // 9
                8000, // 10
                10700, // 11
                14200, // 12
                18600, // 13
                24200, // 14
                31500 // 15
            };

    public static int CONTRACTS_AT_LEVEL_1 = 4;
    public static int CONTRACTS_AT_MAX_LEVEL = 20;
    public static int MAX_LEVEL = LEVELS_MAX; // =15

    // ==========================================================
    // Titles
    // ==========================================================
    public static final LinkedHashMap<Integer, String> levelTitles = new LinkedHashMap<>();

    static {
        for (int i = 1; i <= 4; i++) levelTitles.put(i, "Enterprise Trade Affiliate");
        for (int i = 5; i <= 9; i++) levelTitles.put(i, "Recognized Distributor");
        for (int i = 10; i <= 14; i++) levelTitles.put(i, "Corporate Commodity Trader");
        levelTitles.put(15, "Inter-faction Trade Pillar");
    }

    public AoTDTradeContractLevelData() {}

    public AoTDTradeContractLevelData(int currXp) {
        this.currXp = currXp;
    }

    // ==========================================================
    // Level queries
    // ==========================================================
    public int getMaxGeneratedContractsForLevel() {
        return getMaxGeneratedContractsForLevel(currXp);
    }

    public boolean canExoticContractsAppear() {
        return getCurrentLevel() >= getLevelForExoticPrivateContractsToAppear();
    }

    public static int getMaxGeneratedContractsForLevel(int currXp) {
        int lvl = getCurrentLevel(currXp);

        if (lvl <= 1) return CONTRACTS_AT_LEVEL_1;
        if (lvl >= MAX_LEVEL) return CONTRACTS_AT_MAX_LEVEL;

        float t = (float) (lvl - 1) / (MAX_LEVEL - 1);
        float value = CONTRACTS_AT_LEVEL_1 + t * (CONTRACTS_AT_MAX_LEVEL - CONTRACTS_AT_LEVEL_1);

        return Math.round(value);
    }

    public static int getLevelForFactionContractsToAppear() {
        return 6;
    }

    public static int getLevelForExoticPrivateContractsToAppear() {
        return 3;
    }

    /** Level is based on effective XP (negative XP does not reduce level below 1). */
    public static int getCurrentLevel(int currXp) {
        int effective = Math.max(0, currXp);

        int lvl = 1;
        for (int i = 0; i < XP_TO_REACH_LEVEL.length; i++) {
            int levelNum = i + 1;
            if (effective >= XP_TO_REACH_LEVEL[i]) lvl = levelNum;
            else break;
        }
        return Math.min(Math.max(lvl, 1), LEVELS_MAX);
    }

    public int getCurrentLevel() {
        return getCurrentLevel(currXp);
    }

    public String getCurrentTitle() {
        return levelTitles.getOrDefault(getCurrentLevel(), "Trader");
    }

    // ==========================================================
    // XP add/remove
    // ==========================================================
    public void addExp(int amount) {
        currXp += amount; // may go negative on purpose
    }

    public void removeXp(int amount) {
        if (amount <= 0) return;
        currXp -= amount; // may go negative on purpose
    }

    // ==========================================================
    // UI helpers
    // ==========================================================
    public float getProgressLevelFloat() {
        int level = getCurrentLevel();
        if (level >= LEVELS_MAX) return 1f;

        int effectiveXp = Math.max(0, currXp);

        int floor = XP_TO_REACH_LEVEL[level - 1];
        int next = XP_TO_REACH_LEVEL[level];

        int bandSize = next - floor;
        if (bandSize <= 0) return 1f;

        float progress = (float) (effectiveXp - floor) / bandSize;
        return Math.max(0f, Math.min(1f, progress));
    }

    /** Total XP required to reach the *next* level (for "curr / threshold" display). */
    public int getCurrentThresholdTotal() {
        int lvl = getCurrentLevel();
        if (lvl >= LEVELS_MAX) return XP_TO_REACH_LEVEL[LEVELS_MAX - 1]; // 31500 at max
        return XP_TO_REACH_LEVEL[lvl];
    }

    /** "Progress XP" value for UI. Keeps negative if you want to show bad rep. */
    public int getUiCurrentXp() {
        return currXp;
    }

    /** "curr / threshold" pair for UI. Example at level 1: currXp / 200. */
    public int getUiThresholdXp() {
        return getCurrentThresholdTotal();
    }

    /** Band floor XP for current level (effective, never negative). */
    public int getLevelFloorXp() {
        int lvl = getCurrentLevel();
        return XP_TO_REACH_LEVEL[lvl - 1];
    }

    /** Band size (next - floor). 0 if max level. */
    public int getLevelBandSize() {
        int lvl = getCurrentLevel();
        if (lvl >= LEVELS_MAX) return 0;
        return XP_TO_REACH_LEVEL[lvl] - XP_TO_REACH_LEVEL[lvl - 1];
    }

    /**
     * Band progress (0..bandSize). At max level, return the max XP for display convenience (31500),
     * since you wanted the UI to show max exp like "31500".
     */
    public int getLevelBandProgress() {
        int lvl = getCurrentLevel();

        if (lvl >= LEVELS_MAX) {
            return XP_TO_REACH_LEVEL[LEVELS_MAX - 1]; // 31500
        }

        int effective = Math.max(0, currXp);
        int floor = getLevelFloorXp();
        int next = XP_TO_REACH_LEVEL[lvl];

        int bandSize = Math.max(0, next - floor);
        int progress = Math.max(0, effective - floor);

        return Math.min(progress, bandSize);
    }

    // ==========================================================
    // XP from contract value
    // ==========================================================
    /**
     * XP gain from completing a month, based on monthly credits worth.
     *
     * <p>Analysis vs your thresholds: - Total XP to max = 31500. - Typical monthly worth in your
     * generator tends to land in ranges where sqrt(v) is ~150..350. - Using /1.3 with a 200 cap
     * yields monthly XP commonly ~110..200 for “good” contracts. - With common durations (3-7
     * private, 6-24 faction) this tends to land “max level” around ~20-45 completed contracts in
     * practice without changing the 31500 cap.
     */
    public static int getXpForMonthlyContractValue(int monthlyCreditsWorth) {
        int v = Math.max(0, monthlyCreditsWorth);

        int xp = (int) Math.floor(Math.sqrt(v) / 1.3);

        if (v > 0) xp = Math.max(xp, 8);
        return Math.min(xp, 200);
    }

    public void addExpFromMonthlyValue(int monthlyCreditsWorth) {
        addExp(getXpForMonthlyContractValue(monthlyCreditsWorth));
    }

    // ==========================================================
    // Penalties for missing a month
    // ==========================================================
    /**
     * Penalty XP for a missed month. Scales with contract value. If atRiskOfTermination is true,
     * hits harder.
     */
    public static int getMissPenaltyXp(int monthlyCreditsWorth, boolean atRiskOfTermination) {
        int base = getXpForMonthlyContractValue(monthlyCreditsWorth);

        int penalty = Math.max(12, (int) Math.ceil(base * 1.25));
        if (atRiskOfTermination) penalty = (int) Math.ceil(penalty * 1.5);

        return Math.min(penalty, 320);
    }

    public void applyMissPenalty(int monthlyCreditsWorth, boolean atRiskOfTermination) {
        removeXp(getMissPenaltyXp(monthlyCreditsWorth, atRiskOfTermination));
    }
}

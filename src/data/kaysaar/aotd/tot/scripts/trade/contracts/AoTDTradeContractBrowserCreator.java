package data.kaysaar.aotd.tot.scripts.trade.contracts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.FactionReputationReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.MerchantReputationReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDContractRewardCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import org.lazywizard.lazylib.MathUtils;

public class AoTDTradeContractBrowserCreator {

    // ===========================
    // Public registries
    // ===========================
    public static final LinkedHashSet<String> blackMarketFactions = new LinkedHashSet<>();
    public static final LinkedHashSet<String> BLACK_MARKET_COMMODITIES = new LinkedHashSet<>();

    // blacklist for PRIVATE contracts only
    public static final LinkedHashSet<String> PRIVATE_COMMODITY_BLACKLIST = new LinkedHashSet<>();

    static {
        blackMarketFactions.add(Factions.PIRATES);
        blackMarketFactions.add(Factions.LUDDIC_PATH);

        BLACK_MARKET_COMMODITIES.add(Commodities.ORGANS);
        BLACK_MARKET_COMMODITIES.add(Commodities.DRUGS);
        BLACK_MARKET_COMMODITIES.add(Commodities.HAND_WEAPONS);

        // ---- PRIVATE BLACKLIST ----
        PRIVATE_COMMODITY_BLACKLIST.add(Commodities.SUPPLIES);
        PRIVATE_COMMODITY_BLACKLIST.add(Commodities.SHIPS);
        PRIVATE_COMMODITY_BLACKLIST.add(Commodities.CREW);
        PRIVATE_COMMODITY_BLACKLIST.add(Commodities.MARINES);
    }

    public static void registerBlackMarketCommodity(String commodityId) {
        if (commodityId == null || commodityId.isEmpty()) return;
        BLACK_MARKET_COMMODITIES.add(commodityId);
    }

    public static void registerBlackMarketFaction(String factionId) {
        if (factionId == null || factionId.isEmpty()) return;
        blackMarketFactions.add(factionId);
    }

    public static void registerPrivateBlacklistCommodity(String commodityId) {
        if (commodityId == null || commodityId.isEmpty()) return;
        PRIVATE_COMMODITY_BLACKLIST.add(commodityId);
    }

    // ==========================================================
    // Helpers
    // ==========================================================

    private static boolean isPrivateCommodityAllowed(String commodityId) {
        return commodityId != null && !PRIVATE_COMMODITY_BLACKLIST.contains(commodityId);
    }

    private static boolean commodityIsExotic(String cid) {
        return cid != null && Global.getSettings().getCommoditySpec(cid).isExotic();
    }

    private static boolean isIllegalCommodity(String cid) {
        return cid != null && BLACK_MARKET_COMMODITIES.contains(cid);
    }

    private static int getDurationOfPrivateContract() {
        return MathUtils.getRandomNumberInRange(3, 7);
    }

    private static int rollRange(Random r, int min, int max) {
        if (max < min) max = min;
        return min + r.nextInt(Math.max(1, max - min + 1));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int lerpInt(int a, int b, float t) {
        return Math.round(a + (b - a) * clamp01(t));
    }

    private static FactionAPI pickFromIds(Random r, LinkedHashSet<String> ids) {
        if (ids == null || ids.isEmpty()) return null;
        int idx = r.nextInt(ids.size());
        int i = 0;
        for (String id : ids) {
            if (i == idx) return Global.getSector().getFaction(id);
            i++;
        }
        return null;
    }

    /** Normal private issuer: mostly independents, sometimes others (excluding player). */
    private static FactionAPI pickNormalIssuerPreferIndep(Random r) {
        FactionAPI indep = Global.getSector().getFaction(Factions.INDEPENDENT);
        if (indep != null && indep.isShowInIntelTab() && !indep.isPlayerFaction()) return indep;

        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>(r);
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f == null) continue;
            if (f.isPlayerFaction()) continue;
            if (!f.isShowInIntelTab()) continue;

            float w = 1f;
            if (Factions.INDEPENDENT.equals(f.getId())) w = 4f;

            // downweight black market factions as "normal issuers"
            if (blackMarketFactions.contains(f.getId())) w = 0.6f;

            picker.add(f, w);
        }
        FactionAPI pick = picker.pick();
        return pick != null ? pick : indep;
    }

    /**
     * Commodity picker: leans slightly toward commodities the player produces (production > 0), but
     * does not force them.
     *
     * <p>Two-state weighting only: - player produces => weight boosted - player doesn't => normal
     * weight
     */
    private static String pickPrivateCommodityWeighted(
            Random r, LinkedHashSet<String> all, boolean allowIllegal, boolean allowExotic) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(r);

        for (String cid : all) {
            if (cid == null) continue;

            if (!isPrivateCommodityAllowed(cid)) continue;

            int prod =
                    AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                            cid, Factions.PLAYER);
            boolean exotic = commodityIsExotic(cid);

            // Exotics only allowed if produced
            if (!allowExotic && exotic && prod <= 0) continue;

            if (!allowIllegal && isIllegalCommodity(cid)) continue;

            float w = 1f;

            // Player production bias
            if (prod > 0) {
                w *= 1.6f;
            }

            // Exotic-specific behavior
            if (exotic) {
                if (prod > 0) {
                    w *= 5.0f; // strong boost for produced exotics
                }
                // IMPORTANT: no demand scaling for exotics
            } else {
                // Normal commodities still scale with demand
                int demand = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(cid);
                if (demand <= 0) demand = 1;
                w *= Math.max(1f, (float) Math.sqrt(demand) / 20f);
            }

            picker.add(cid, w);
        }

        return picker.pick();
    }

    /**
     * Private cut rolls: - non-exotic single-line contracts (normal)
     *
     * <p>You asked earlier for faction cuts min=0.25 max=0.6, but this class is private-only
     * generator. So we keep private in a sane "buyer pays player" range, with mild level lift.
     */
    private static float rollCutPrivate(Random r, int level, String commodityId) {
        float cutOriginal = 0.15f;

        if (commodityId != null && AoTDCommodityEconSpecManager.getEconSpec(commodityId) != null) {
            cutOriginal = AoTDCommodityEconSpecManager.getEconSpec(commodityId).getExternalCut();
        }

        if (cutOriginal <= 0f) {
            cutOriginal = 0.15f;
        }

        float u = r.nextFloat();
        float shift = Math.min(0.20f, Math.max(0f, (level - 1) * 0.012f));

        float lowChance = 0.78f - shift;
        float midChance = 0.18f;

        float mult;

        if (u < lowChance) {
            // ~0.30–0.40 (for base 0.15)
            mult = 2.0f + r.nextFloat() * 0.7f; // 2.0–2.7
        } else if (u < lowChance + midChance) {
            // ~0.40–0.50
            mult = 2.7f + r.nextFloat() * 0.7f; // 2.7–3.4
        } else {
            // ~0.50–0.75
            mult = 3.4f + r.nextFloat() * 1.6f; // 3.4–5.0
        }

        return cutOriginal * mult;
    }

    private static int computeMonthlyAmountPrivate(
            Random r,
            String commodityId,
            float cut,
            int targetIncome,
            int level,
            float maxSupplyShare,
            float fallbackSupplyHard,
            float minDemandShareCap,
            float maxDemandShareCap) {
        if (commodityId == null) return 1;

        CommoditySpecAPI specAPI = Global.getSettings().getCommoditySpec(commodityId);

        float base = specAPI.getBasePrice();
        if (base <= 0f) base = 1f;

        int byIncome = (int) Math.floor(targetIncome / Math.max(1f, base * cut));
        if (byIncome < 1) byIncome = 1;

        int sectorSupply =
                AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int sectorDemand =
                AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);

        boolean exotic = commodityIsExotic(commodityId);

        // --- supply cap ---
        int capBySupply;
        if (sectorSupply > 0) {
            capBySupply = Math.round(sectorSupply * maxSupplyShare);
            if (capBySupply < 1) capBySupply = 1;
        } else {
            capBySupply = Math.round(fallbackSupplyHard + level * 20f);
            if (capBySupply < 50) capBySupply = 50;
        }

        // =====================================================
        // Exotics: ignore demand entirely.
        // Amount is controlled by income target + production cap.
        // =====================================================
        if (exotic) {
            int cap = capBySupply;

            int amt = Math.min(byIncome, cap);

            // Smaller minimum for exotics, because they may be high-value / low-volume.
            int minAmt = 5 + level * 3;

            if (amt < minAmt) amt = minAmt;

            // Still obey production cap.
            if (amt > cap) amt = cap;

            return Math.max(1, amt);
        }

        // --- demand cap for normal commodities only ---
        float demandShare;
        if (sectorDemand <= 0) {
            demandShare = 0.03f;
        } else if (sectorDemand < 10_000) {
            demandShare = 0.06f;
        } else if (sectorDemand < 100_000) {
            demandShare = 0.04f;
        } else if (sectorDemand < 1_000_000) {
            demandShare = 0.03f;
        } else {
            demandShare = 0.02f;
        }

        demandShare = Math.max(minDemandShareCap, Math.min(maxDemandShareCap, demandShare));

        int capByDemand;
        if (sectorDemand > 0) {
            capByDemand = Math.round(sectorDemand * demandShare);
            if (capByDemand < 1) capByDemand = 1;
        } else {
            capByDemand = 500 + level * 40;
        }

        int cap = Math.min(capBySupply, capByDemand);
        int amt = Math.min(byIncome, cap);

        int minAmt = 20 + level * 8;
        if (amt < minAmt) amt = minAmt;

        if (amt > cap) amt = cap;

        return Math.max(1, amt);
    }

    /**
     * If a private contract ends up with only illegal commodities, it should actually be black
     * market: - swap person to black market faction
     *
     * <p>This is your "safe call" to avoid "independent broker asking only drugs".
     */
    private static void enforceIllegalImpliesBlackMarket(Random r, AoTDTradeContract c) {
        if (c == null) return;
        if (!c.isPrivate()) return;

        // check if ALL commodities in the contract are illegal (in our black market list)
        boolean hasAny = false;
        boolean allIllegal = true;
        for (AoTDTradeContract.TradeContractData d : c.contractData.values()) {
            if (d == null) continue;
            String cid = d.getCommodityId();
            if (cid == null) continue;
            hasAny = true;
            if (!isIllegalCommodity(cid)) {
                allIllegal = false;
                break;
            }
        }
        if (!hasAny || !allIllegal) return;

        FactionAPI bm = pickFromIds(r, blackMarketFactions);
        if (bm == null) return;

        // replace person with a black market faction person
        PersonAPI p = bm.createRandomPerson();
        // AoTDTradeContract has no setter for person in your snippet; if you have one, use it.
        // If you don't, you can do this transformation earlier, at creation time, instead of
        // "fixing".
        //
        // For now, we keep the "safe call" as a no-op unless you add a setter:
        // c.setPerson(p);

        // If you cannot add a setter, the better approach is: create the person AFTER commodity
        // pick,
        // and choose black market faction if the commodity is illegal. (We do that below.)
    }

    // ==========================================================
    // Main generator
    // ==========================================================

    public static List<AoTDTradeContract> generateContractsForBrowser(int maxContracts, int level) {
        ArrayList<AoTDTradeContract> out = new ArrayList<>();
        if (maxContracts <= 0) return out;

        LinkedHashSet<String> all =
                AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied();
        if (all == null || all.isEmpty()) return out;

        Random r = new Random(Misc.random.nextLong());

        // ===========================
        // TUNABLES (easy to tweak)
        // ===========================
        final int ASSUMED_MAX_LEVEL = AoTDTradeContractLevelData.LEVELS_MAX;

        // monthly income targets (private)
        final int PRIVATE_MIN_LVL1 = 20_000;
        final int PRIVATE_MAX_LVL1 = 40_000;
        final int PRIVATE_MIN_MAX = 100_000;
        final int PRIVATE_MAX_MAX = 600_000;

        // supply share cap (avoid modded "supply 1000 demand 14000" asking 10k)
        final float MAX_SECTOR_SUPPLY_SHARE_PRIVATE = 0.30f; // <=20% of sector supply

        // fallback supply cap when sector supply is unknown/0
        final float FALLBACK_SUPPLY_HARD = 250f;

        // demand share cap bounds (tiered by demand digits in computeMonthlyAmountPrivate)
        final float DEMAND_SHARE_CAP_MIN = 0.015f; // never below 1.5%
        final float DEMAND_SHARE_CAP_MAX = 0.08f; // never above 8%

        // black market presence:
        // lvl1: cap 1..2 total black market contracts, later slightly more
        // also: black market is not forced to dominate normal contracts
        final float BM_CHANCE_PER_CONTRACT_LVL1 = 0.10f;
        final float BM_CHANCE_PER_LEVEL = 0.006f;
        final float BM_CHANCE_MAX = 0.22f;

        // number of tries to produce black market contracts (soft)
        final int BM_SOFT_TRIES = 6;

        // player-produced lean (two state)
        // implemented in pickPrivateCommodityWeighted()

        // additional rewards gate is already level>=5 in rollAdditionalRewardsCount
        // ===========================

        float t = clamp01((level - 1f) / Math.max(1f, (ASSUMED_MAX_LEVEL - 1f)));
        int privateIncomeMin = lerpInt(PRIVATE_MIN_LVL1, PRIVATE_MIN_MAX, t);
        int privateIncomeMax = lerpInt(PRIVATE_MAX_LVL1, PRIVATE_MAX_MAX, t);
        if (privateIncomeMax < privateIncomeMin) privateIncomeMax = privateIncomeMin;

        // build available black pool for this run (only those present in "all")
        ArrayList<String> blackPool = new ArrayList<>();
        for (String cid : BLACK_MARKET_COMMODITIES) {
            if (cid == null) continue;
            if (all.contains(cid)) blackPool.add(cid);
        }

        // ---------------------------------------------
        // Decide a soft cap on how many black market contracts we want
        // ---------------------------------------------
        int bmCap = getBlackMarketCapForLevel(level, r);
        int bmMade = 0;

        // ---------------------------------------------
        // First pass: attempt to add up to bmCap black market contracts,
        // but keep them uncommon at low levels.
        // ---------------------------------------------
        if (!blackPool.isEmpty() && out.size() < maxContracts) {
            float bmChance =
                    BM_CHANCE_PER_CONTRACT_LVL1 + BM_CHANCE_PER_LEVEL * Math.max(0, level - 1);
            if (bmChance > BM_CHANCE_MAX) bmChance = BM_CHANCE_MAX;

            // ensure at least 1 sometimes, but not always at lvl1
            boolean forceOne = (level >= 2) || (r.nextFloat() < 0.55f);

            int tries = Math.min(BM_SOFT_TRIES, maxContracts);
            for (int i = 0; i < tries && out.size() < maxContracts && bmMade < bmCap; i++) {
                if (!forceOne) {
                    if (r.nextFloat() >= bmChance) continue;
                } else {
                    // first successful chance uses the "forced" slot
                    // but we still respect bmCap
                }

                // pick illegal commodity (black market)
                String cid = blackPool.get(r.nextInt(blackPool.size()));
                if (cid == null) continue;

                // issuer MUST be black market faction
                FactionAPI issuerFaction = pickFromIds(r, blackMarketFactions);
                if (issuerFaction == null) continue;

                PersonAPI p = issuerFaction.createRandomPerson();
                AoTDTradeContract c =
                        new AoTDTradeContract(
                                "aotd_contract_" + Misc.genUID(),
                                p,
                                null,
                                getDurationOfPrivateContract());

                float cut = rollCutPrivate(r, level, cid);
                int target = rollRange(r, privateIncomeMin, privateIncomeMax);

                // amount is still capped by supply+demand tiers
                int amount =
                        computeMonthlyAmountPrivate(
                                r,
                                cid,
                                cut,
                                target,
                                level,
                                MAX_SECTOR_SUPPLY_SHARE_PRIVATE,
                                FALLBACK_SUPPLY_HARD,
                                DEMAND_SHARE_CAP_MIN,
                                DEMAND_SHARE_CAP_MAX);

                c.addContractData(cid, amount, cut);
                out.add(c);
                bmMade++;

                // only force at most one "forced" addition
                forceOne = false;
            }
        }

        // ---------------------------------------------
        // Fill remaining mostly with normal private contracts
        // ---------------------------------------------
        while (out.size() < maxContracts) {

            // normal private issuer
            FactionAPI issuerFaction = pickNormalIssuerPreferIndep(r);
            if (issuerFaction == null) break;

            // pick a commodity (non-illegal, non-exotic)
            String cid = pickPrivateCommodityWeighted(r, all, false, true);
            if (cid == null) break;

            // build contract
            PersonAPI p = issuerFaction.createRandomPerson();
            AoTDTradeContract c =
                    new AoTDTradeContract(
                            "aotd_contract_" + Misc.genUID(),
                            p,
                            null,
                            getDurationOfPrivateContract());

            float cut = rollCutPrivate(r, level, cid);
            int target = rollRange(r, privateIncomeMin, privateIncomeMax);

            int amount =
                    computeMonthlyAmountPrivate(
                            r,
                            cid,
                            cut,
                            target,
                            level,
                            MAX_SECTOR_SUPPLY_SHARE_PRIVATE,
                            FALLBACK_SUPPLY_HARD,
                            DEMAND_SHARE_CAP_MIN,
                            DEMAND_SHARE_CAP_MAX);

            c.addContractData(cid, amount, cut);

            // safety: if somehow we ended up with only illegal (shouldn't), swap to black market
            // issuer
            // (If you add AoTDTradeContract#setPerson later, enforceIllegalImpliesBlackMarket can
            // do it post-hoc.)
            if (isIllegalCommodity(cid)) {
                FactionAPI bmFac = pickFromIds(r, blackMarketFactions);
                if (bmFac != null) {
                    PersonAPI bp = bmFac.createRandomPerson();
                    c =
                            new AoTDTradeContract(
                                    "aotd_contract_" + Misc.genUID(),
                                    bp,
                                    null,
                                    getDurationOfPrivateContract());
                    c.addContractData(cid, amount, cut);
                    bmMade++;
                }
            }

            out.add(c);
        }

        // ---------------------------------------------
        // Scramble order (you asked earlier for this)
        // ---------------------------------------------
        Collections.shuffle(out, r);
        for (AoTDTradeContract contract : out) {
            if (contract.isPrivate()) {
                if (blackMarketFactions.contains(contract.getFaction().getId())) {
                    contract.setContractTypeId("aotd_contract_black_market");
                } else {
                    contract.setContractTypeId("aotd_contract_independent");
                }
            } else {
                contract.setContractTypeId("aotd_contract_foreign_trade");
            }
        }

        // ---------------------------------------------
        // Rewards
        // ---------------------------------------------
        applyGeneratedContractRewards(
                out, AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel());

        return out;
    }

    // ==========================================================
    // Black market cap logic
    // ==========================================================
    private static int getBlackMarketCapForLevel(int level, Random r) {
        // lvl1: 1..2 max
        if (level <= 1) return 1 + (r.nextFloat() < 0.20f ? 1 : 0);

        // lvl2-3: usually 1-2, sometimes 3
        if (level <= 3) {
            int cap = 1 + (r.nextFloat() < 0.45f ? 1 : 0);
            if (r.nextFloat() < 0.10f) cap++;
            return cap;
        }

        // lvl4-7: 2-3
        if (level <= 7) return 2 + (r.nextFloat() < 0.35f ? 1 : 0);

        // lvl8-11: 3-4
        if (level <= 11) return 3 + (r.nextFloat() < 0.40f ? 1 : 0);

        // lvl12-15: 4-5, rare 6
        int cap = 4 + (r.nextFloat() < 0.45f ? 1 : 0);
        if (r.nextFloat() < 0.12f) cap++;
        return cap;
    }

    private static void applyGeneratedContractRewards(List<AoTDTradeContract> out, int level) {
        if (out == null || out.isEmpty()) return;

        for (AoTDTradeContract contract : out) {
            if (contract == null) continue;

            int monthlyMoney = contract.getPredictedMoneyWorthForMonth();
            int months = contract.getMonthsRemaining();

            int merchantXp = getMerchantXpForContract(monthlyMoney, months);
            contract.addReward("rep_merchant", new MerchantReputationReward(merchantXp));

            // faction-issued (NOT private, NOT player-issued) => add faction rep reward
            if (!contract.isPrivate() && !contract.isIssuedByPlayer()) {
                int currLvl =
                        AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
                int plus = getFactionRepPlusForLevel(currLvl);
                int minus = getFactionRepMinusForLevel(currLvl);
                contract.addReward(
                        "rep_faction",
                        new FactionReputationReward(contract.getFactionId(), plus, minus));
            }

            int extra = rollAdditionalRewardsCount(contract, level, Misc.random);
            if (extra > 0) {
                AoTDContractRewardCreatorManager.pickAdditionalRewardsForContract(contract, extra);
            }
        }
    }

    /**
     * Merchant XP should care *a lot* about duration. monthlyCreditsWorth only sets the baseline;
     * monthsRemaining multiplies it hard.
     */
    private static int getMerchantXpForContract(int monthlyCreditsWorth, int monthsRemaining) {
        int moneyXp = AoTDTradeContractLevelData.getXpForMonthlyContractValue(monthlyCreditsWorth);

        int m = Math.max(1, monthsRemaining);

        // big driver: duration. 3m ~1.1x, 6m ~1.9x, 12m ~3.7x, 24m ~7.3x (then capped)
        float durMult = 0.60f + 0.28f * m;
        if (durMult > 8.0f) durMult = 8.0f;

        int xp = Math.round(moneyXp * durMult);

        // sane bounds (tweak as needed)
        if (xp < 5) xp = 5;
        if (xp > 2000) xp = 2000;

        return xp;
    }

    /** Faction rep reward scaling: starts meaningful, grows with level. */
    private static int getFactionRepPlusForLevel(int level) {
        int lvl = Math.max(1, level);
        // lvl1=6, lvl5=14, lvl10=24, lvl15=34
        return 6 + Math.max(0, lvl - 1) * 2;
    }

    /** Faction penalty harsher than bonus. */
    private static int getFactionRepMinusForLevel(int level) {
        int lvl = Math.max(1, level);
        // lvl1=8, lvl5=20, lvl10=35, lvl15=50
        return 8 + Math.max(0, lvl - 1) * 3;
    }

    // ==========================================================
    // Additional rewards logic
    // ==========================================================
    private static int rollAdditionalRewardsCount(AoTDTradeContract contract, int level, Random r) {
        if (contract == null) return 0;

        // gate: no additional rewards before level 5
        if (level < 5) return 0;

        // t = 0 at lvl5, 1 at lvl15
        float t = clamp01((level - 5f) / 10f);

        // private: starts modest, becomes very likely at 15
        float chance = 0.30f + 0.55f * t; // 30% -> 85%
        if (r.nextFloat() > chance) return 0;

        // private: 1-2, with lvl pushing towards 2
        return (r.nextFloat() < (0.25f + 0.60f * t)) ? 2 : 1; // 25%->85% for 2
    }
}

package data.kaysaar.aotd.tot.scripts.economy;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.econ.impl.Spaceport;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.strings.AoTDIndTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Authoritative AoTD state for industries on one market.
 *
 * <p>{@link #statesOnMarket} is the desired economic state. The separate
 * reconciled map records which state was actually materialized through
 * apply/unapply. This lets the economy update reconcile only transitions
 * instead of replaying every industry on every pass.</p>
 */
public class AoTDIndustryData {
    public enum AoTDIndustryState {
        PENDING,
        ALREADY_WORKING
    }

    public LinkedHashMap<String, String> industriesToIgnoreDueToUpgrade = new LinkedHashMap<>();
    public LinkedHashMap<String, AoTDIndustryState> statesOnMarket = new LinkedHashMap<>();
    public LinkedHashMap<String, AoTDIndustryState> reconciledStatesOnMarket = new LinkedHashMap<>();

    private transient List<Industry> stableIndustryOrder = List.of();

    public static String source = "aotd_economy_correction";
    public static String memKey = "$aotd_industry_data";

    public LinkedHashMap<String, String> getIndustriesToIgnoreDueToUpgrade() {
        if (industriesToIgnoreDueToUpgrade == null) industriesToIgnoreDueToUpgrade = new LinkedHashMap<>();
        return industriesToIgnoreDueToUpgrade;
    }

    private Object readResolve() {
        if (industriesToIgnoreDueToUpgrade == null) industriesToIgnoreDueToUpgrade = new LinkedHashMap<>();
        if (statesOnMarket == null) statesOnMarket = new LinkedHashMap<>();
        if (reconciledStatesOnMarket == null) reconciledStatesOnMarket = new LinkedHashMap<>();
        stableIndustryOrder = List.of();
        return this;
    }

    public static AoTDIndustryData getInstance(MarketAPI market) {
        if (!market.getMemoryWithoutUpdate().contains(memKey)) {
            AoTDIndustryData data = new AoTDIndustryData();
            for (Industry industry : market.getIndustries()) {
                data.statesOnMarket.put(industry.getId(), AoTDIndustryState.ALREADY_WORKING);
            }
            market.getMemoryWithoutUpdate().set(memKey, data);
        }
        AoTDIndustryData data = (AoTDIndustryData) market.getMemoryWithoutUpdate().get(memKey);
        if (data.reconciledStatesOnMarket == null) data.reconciledStatesOnMarket = new LinkedHashMap<>();
        if (data.stableIndustryOrder == null) data.stableIndustryOrder = List.of();
        return data;
    }

    /** Backward-compatible entry point. */
    public void checkForNewIndustries(MarketAPI market) {
        checkForNewIndustriesAndReport(market);
    }

    /**
     * Refreshes desired states and returns true only when market membership or
     * an AoTD pending/active state changed.
     */
    public boolean checkForNewIndustriesAndReport(MarketAPI market) {
        boolean changed = false;
        LinkedHashSet<String> currentIds = new LinkedHashSet<>();

        for (Industry industry : market.getIndustries()) {
            String industryId = industry.getId();
            currentIds.add(industryId);

            AoTDIndustryState previous = statesOnMarket.get(industryId);
            AoTDIndustryState desired = previous;
            if (desired == null) {
                desired = initialStateFor(industry);
            }
            if (previous != desired) {
                statesOnMarket.put(industryId, desired);
                changed = true;
            }

            if (industry.isUpgrading()) {
                String id = (String) ReflectionUtilis.getPrivateVariable("upgradeId", industry);
                if (AshMisc.isStringValid(id) && !getIndustriesToIgnoreDueToUpgrade().containsKey(id)) {
                    getIndustriesToIgnoreDueToUpgrade().put(industryId, id);
                }
            }
        }

        LinkedHashSet<String> removedStates = new LinkedHashSet<>();
        for (String id : statesOnMarket.keySet()) {
            if (!currentIds.contains(id)) removedStates.add(id);
        }
        if (!removedStates.isEmpty()) {
            changed = true;
            for (String id : removedStates) {
                statesOnMarket.remove(id);
                reconciledStatesOnMarket.remove(id);
            }
        }

        LinkedHashSet<String> upgradesToRemove = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : getIndustriesToIgnoreDueToUpgrade().entrySet()) {
            if (!market.hasIndustry(entry.getKey())) {
                if (market.getIndustry(entry.getValue()) != null) {
                    AoTDIndustryState previous = statesOnMarket.put(
                            entry.getValue(), AoTDIndustryState.ALREADY_WORKING);
                    if (previous != AoTDIndustryState.ALREADY_WORKING) changed = true;
                    statesOnMarket.remove(entry.getKey());
                    reconciledStatesOnMarket.remove(entry.getKey());
                }
                upgradesToRemove.add(entry.getKey());
            }
        }
        for (String id : upgradesToRemove) getIndustriesToIgnoreDueToUpgrade().remove(id);

        if (changed) invalidateStableIndustryOrder();
        return changed;
    }

    private static AoTDIndustryState initialStateFor(Industry industry) {
        if (industry.getAllDemand().isEmpty()
                || industry.getSpec().hasTag(AoTDIndTags.ALWAYS_ACTIVE_NON_PENDING)
                || AoTDEconomy.runningPrePlayerEconomy
                || industry instanceof PopulationAndInfrastructure
                || industry instanceof Spaceport) {
            return AoTDIndustryState.ALREADY_WORKING;
        }
        return AoTDIndustryState.PENDING;
    }

    public boolean isPending(String id) {
        return statesOnMarket.get(id) == AoTDIndustryState.PENDING;
    }

    public boolean needsReconciliation(String id) {
        AoTDIndustryState desired = statesOnMarket.get(id);
        return desired != null && reconciledStatesOnMarket.get(id) != desired;
    }

    public void markReconciled(String id) {
        AoTDIndustryState desired = statesOnMarket.get(id);
        if (desired == null) reconciledStatesOnMarket.remove(id);
        else reconciledStatesOnMarket.put(id, desired);
    }

    public int getPendingCount() {
        int result = 0;
        for (AoTDIndustryState state : statesOnMarket.values()) {
            if (state == AoTDIndustryState.PENDING) result++;
        }
        return result;
    }

    /**
     * Stable order used by deficit/availability calculations. The sorted list
     * is returned directly without per-query allocation. A cheap identity
     * validation detects membership/replacement changes even before the next
     * economy reconciliation pass, without acquiring the registry monitor.
     */
    public List<Industry> getStableIndustryOrder(MarketAPI market) {
        List<Industry> industries = market.getIndustries();
        boolean rebuild = stableIndustryOrder == null
                || stableIndustryOrder.size() != industries.size();
        if (!rebuild) {
            for (Industry cached : stableIndustryOrder) {
                if (market.getIndustry(cached.getId()) != cached) {
                    rebuild = true;
                    break;
                }
            }
        }
        if (rebuild) {
            ArrayList<Industry> sorted = new ArrayList<>(industries);
            // Java List.sort is stable: equal-order industries preserve the
            // market list order required by AoTD deficit semantics.
            sorted.sort(Comparator.comparingInt(
                    (Industry industry) -> industry.getSpec().getOrder()));
            stableIndustryOrder = List.copyOf(sorted);
        }
        return stableIndustryOrder;
    }

    public void invalidateStableIndustryOrder() {
        stableIndustryOrder = List.of();
    }

    public void applyEndOfMonthChange(MarketAPI market) {
        statesOnMarket.clear();
        for (Industry industry : market.getIndustries()) {
            if (!(industry.isBuilding() && !industry.isUpgrading())
                    || industry.getSpec().hasTag(AoTDIndTags.ALWAYS_ACTIVE_NON_PENDING)
                    || industry instanceof PopulationAndInfrastructure
                    || industry instanceof Spaceport) {
                statesOnMarket.put(industry.getId(), AoTDIndustryState.ALREADY_WORKING);
            }
        }
        // Keep the materialized map intact so the next pass reconciles only
        // entries whose desired state really changed.
        invalidateStableIndustryOrder();
    }
}

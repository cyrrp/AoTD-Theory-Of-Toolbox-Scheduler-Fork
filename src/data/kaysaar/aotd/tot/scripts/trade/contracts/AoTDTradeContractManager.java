package data.kaysaar.aotd.tot.scripts.trade.contracts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import data.kaysaar.aotd.tot.intel.AoTDContractFinished;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.BlueprintReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.FactionReputationReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.MerchantReputationReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDPlayerContractCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly.MonthlyCommodityToStorageReward;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;

public class AoTDTradeContractManager {

    public static final String MEM_KEY = "$aotd_trade_contract_manager";
    AoTDTradeContractLevelData currLevel = new AoTDTradeContractLevelData();
    LinkedHashMap<String, AoTDTradeContract> currentlyGeneratedInBrowser = new LinkedHashMap<>();

    public static MonthlyReport.FDNode getNodeForContracts() {
        return SharedData.getData().getCurrentReport().getNode("aotd_trade_contracts");
    }

    public static LinkedHashSet<String> viableExoticCommodities = new LinkedHashSet<>();

    public LinkedHashMap<String, AoTDTradeContract> getCurrentlyGeneratedInBrowser() {
        if (currentlyGeneratedInBrowser == null)
            currentlyGeneratedInBrowser = new LinkedHashMap<>();
        return currentlyGeneratedInBrowser;
    }

    public void generateNewContractsForBrowser() {

        List<AoTDTradeContract> gen =
                AoTDTradeContractBrowserCreator.generateContractsForBrowser(
                        getCurrLevelData().getMaxGeneratedContractsForLevel(),
                        getCurrLevelData().getCurrentLevel());

        getCurrentlyGeneratedInBrowser().clear();
        for (AoTDTradeContract c : gen) getCurrentlyGeneratedInBrowser().put(c.getId(), c);
    }

    public static int getDurationOfPrivateContract() {
        return MathUtils.getRandomNumberInRange(2, 8);
    }

    public AoTDTradeContractLevelData getCurrLevelData() {
        if (currLevel == null) currLevel = new AoTDTradeContractLevelData();
        return currLevel;
    }

    public static AoTDTradeContractManager getInstance() {
        Map<String, Object> pd = Global.getSector().getPersistentData();
        if (!pd.containsKey(MEM_KEY)) {
            pd.put(MEM_KEY, new AoTDTradeContractManager());
        }
        return (AoTDTradeContractManager) pd.get(MEM_KEY);
    }

    public void terminateContract(String contractId) {
        if (contractId == null) return;
        if (activeContracts.get(contractId) != null) {
            activeContracts.get(contractId).terminateContract();
        }
        if (activeContracts.remove(contractId) != null) {
            invalidatePredictions();
        }
    }

    public void setContractFrozen(String contractId, boolean frozen) {
        if (contractId == null) return;

        AoTDTradeContract c = activeContracts.get(contractId);
        if (c == null) return;

        c.setFrozen(frozen);
        invalidatePredictions();
    }

    private final LinkedHashMap<String, AoTDTradeContract> activeContracts = new LinkedHashMap<>();

    private int lastPredictedCycle = Integer.MIN_VALUE;
    private int lastPredictedMonth = Integer.MIN_VALUE;

    public Map<String, AoTDTradeContract> getActiveContracts() {
        return activeContracts;
    }

    public void addContract(AoTDTradeContract contract) {
        if (contract == null) return;
        activeContracts.put(contract.getId(), contract);
        invalidatePredictions();
    }

    public void removeContract(String contractId) {
        if (contractId == null) return;
        activeContracts.remove(contractId);
        invalidatePredictions();
    }

    public void clearAll() {
        activeContracts.clear();
        invalidatePredictions();
    }

    public void invalidatePredictions() {
        lastPredictedCycle = Integer.MIN_VALUE;
        lastPredictedMonth = Integer.MIN_VALUE;
    }

    public void ensurePredictionsUpToDate() {
        int cycle = Global.getSector().getClock().getCycle();
        int month = Global.getSector().getClock().getMonth();
        if (cycle == lastPredictedCycle && month == lastPredictedMonth) return;

        rebuildPredictions();
        lastPredictedCycle = cycle;
        lastPredictedMonth = month;
    }

    private void rebuildPredictions() {
        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        AoTDFactionTradeData playerTrade =
                AoTDTradeManager.getInstance().getFactionTradeData(playerFactionId);
        if (playerTrade == null) return;

        for (AoTDMarketData md : playerTrade.getTradeData().values()) {
            if (md != null) md.resetContractPredictions();
        }

        if (activeContracts.isEmpty()) return;

        // collect commodities involved in ALL contract lines
        Set<String> contractCommodities = new HashSet<>();
        for (AoTDTradeContract c : activeContracts.values()) {
            if (c == null) continue;
            if (c.isExpired() || c.isTerminated()) continue;
            if (c.getContractData().isEmpty()) continue;

            for (AoTDTradeContract.TradeContractData line : c.getContractData().values()) {
                if (line == null) continue;
                if (line.getReqMonthly() <= 0) continue;
                if (line.getCommodityId() == null) continue;
                contractCommodities.add(line.getCommodityId());
            }
        }
        if (contractCommodities.isEmpty()) return;

        // availability snapshot (per market, per commodity) from remainingNet
        Map<String, Map<String, Integer>> available = new HashMap<>();
        for (AoTDMarketData md : playerTrade.getTradeData().values()) {
            if (md == null) continue;
            for (String commodityId : contractCommodities) {
                int avail = md.getRemainingNet(commodityId);
                if (avail > 0) {
                    available
                            .computeIfAbsent(md.marketId, k -> new HashMap<>())
                            .put(commodityId, avail);
                }
            }
        }

        // exporters by commodity, sorted once
        Map<String, ArrayList<AoTDMarketData>> exportersByCommodity = new HashMap<>();
        for (String commodityId : contractCommodities) {
            ArrayList<AoTDMarketData> exporters = new ArrayList<>();
            for (AoTDMarketData md : playerTrade.getTradeData().values()) {
                if (md == null) continue;
                Map<String, Integer> av = available.get(md.marketId);
                if (av == null) continue;
                if (av.getOrDefault(commodityId, 0) > 0) exporters.add(md);
            }
            exporters.sort((a, b) -> Float.compare(b.outsideWeight, a.outsideWeight));
            exportersByCommodity.put(commodityId, exporters);
        }

        // deterministic allocation: contracts in insertion order, and lines in insertion order
        // (LinkedHashMap)
        for (AoTDTradeContract c : activeContracts.values()) {
            if (c == null) continue;
            if (c.isExpired() || c.isTerminated()) continue;

            for (AoTDTradeContract.TradeContractData line : c.getContractData().values()) {
                if (line == null) continue;

                String commodityId = line.getCommodityId();
                int need = Math.max(0, line.getReqMonthly());
                if (commodityId == null || need <= 0) continue;

                ArrayList<AoTDMarketData> exporters = exportersByCommodity.get(commodityId);
                if (exporters == null || exporters.isEmpty()) continue;

                int remaining = need;
                for (AoTDMarketData md : exporters) {
                    if (remaining <= 0) break;

                    Map<String, Integer> av = available.get(md.marketId);
                    if (av == null) continue;

                    int avail = av.getOrDefault(commodityId, 0);
                    if (avail <= 0) continue;

                    int moved = Math.min(avail, remaining);
                    av.put(commodityId, avail - moved);

                    md.recordPredictedContractExport(c.getId(), commodityId, moved);
                    remaining -= moved;
                }
            }
        }
    }

    public void pruneEmptyContracts() {
        if (activeContracts.isEmpty()) return;

        Iterator<Map.Entry<String, AoTDTradeContract>> it = activeContracts.entrySet().iterator();
        boolean removed = false;

        while (it.hasNext()) {
            Map.Entry<String, AoTDTradeContract> entry = it.next();
            AoTDTradeContract c = entry.getValue();

            if (c == null
                    || c.getContractData() == null
                    || c.getContractData().isEmpty()
                    || c.isTerminated()
                    || c.isExpired()) {
                it.remove();
                removed = true;
            }
        }

        if (removed) {
            invalidatePredictions();
        }
    }

    /**
     * Runs monthly after INTERNAL trade, before EXTERNAL trade. Consumes player's remainingNet
     * exports for each contract line.
     */
    public void runMonthlyContracts() {
        if (activeContracts.isEmpty()) return;

        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        AoTDFactionTradeData playerTrade =
                AoTDTradeManager.getInstance().getFactionTradeData(playerFactionId);
        if (playerTrade == null) return;

        for (AoTDMarketData md : playerTrade.getTradeData().values()) {
            if (md != null) md.resetContractResults();
        }

        ArrayList<String> toRemove = new ArrayList<>();
        MonthlyReport report = SharedData.getData().getCurrentReport();
        MonthlyReport.FDNode mainNodeContracts = report.getNode("aotd_trade_contracts");
        mainNodeContracts.name = "Trade Contracts";
        mainNodeContracts.icon = "graphics/stations/station_side03.png";
        mainNodeContracts.custom = "node_id_contracts_";
        for (AoTDTradeContract c : activeContracts.values()) {
            if (c == null) continue;
            if (c.isContractFrozen()) continue;
            if (c.isExpired() || c.isTerminated()) {
                toRemove.add(c.getId());
                continue;
            }
            if (c.isIssuedByPlayer()
                    && AoTDPlayerContractCreatorManager.getCreator(c.getContractTypeId()) != null) {
                PlayerContractCreatorAPI creatorAPI =
                        AoTDPlayerContractCreatorManager.getCreator(c.getContractTypeId());
                creatorAPI.applyChangesToContractIfNecessary(c);
            }
            if (c.getContractData().isEmpty()) {
                toRemove.add(c.getId());
                continue;
            }
            boolean missedThisMonth = false;
            float contractProgressMonth = 0f;
            int pointsOfProgress = 0;
            // process each commodity line
            for (AoTDTradeContract.TradeContractData line : c.getContractData().values()) {
                if (line == null) continue;

                String commodityId = line.getCommodityId();
                int required = Math.max(0, line.getReqMonthly());
                if (commodityId == null || required <= 0) continue;

                int delivered =
                        pullFromPlayerExports(playerTrade, commodityId, required, c.getId());

                if (delivered < required) {
                    missedThisMonth = true;
                }
                float progress = (float) delivered / required;
                contractProgressMonth += progress;
                c.executeMonthEndForCommodity(delivered, commodityId);
                // credits delta (profit or cost)
                int creditsDelta = c.getMoneyFromMonth(commodityId, delivered);

                if (creditsDelta != 0) {
                    MonthlyReport.FDNode curr =
                            report.getNode(mainNodeContracts, "node_id_contracts_" + c.getId());
                    curr.icon = c.getIconName();
                    curr.name = c.getNameOfContract();
                    if (c.getSubTypeOfContractString() != null) {
                        curr.name = c.getNameOfContract() + " - " + c.getSubTypeOfContractString();
                    }
                    if (c.isIssuedByPlayer()) {
                        curr.name = c.getSubTypeOfContractString();
                    }
                    MonthlyReport.FDNode nodeCommodityEach = report.getNode(curr, commodityId);
                    nodeCommodityEach.icon =
                            Global.getSettings().getCommoditySpec(commodityId).getIconName();
                    nodeCommodityEach.name =
                            Global.getSettings().getCommoditySpec(commodityId).getName();

                    if (creditsDelta > 0) {
                        nodeCommodityEach.income += creditsDelta;
                    } else {
                        nodeCommodityEach.upkeep += (-creditsDelta);
                    }
                }
                pointsOfProgress++;

                // if it is a faction-target contract AND not private AND not player-issued,
                // distribute to that faction's markets equally (economy contribution)
                if (!c.isPrivate() && !c.isIssuedByPlayer() && delivered > 0) {
                    distributeToFactionEqually(c.getFactionId(), commodityId, delivered);
                }
            }
            c.executeMonthEnd(contractProgressMonth / pointsOfProgress);
            if (missedThisMonth && !c.isIssuedByPlayer()) {
                c.addMiss();
                if (c.isTerminated()) {
                    for (TradeContractRewardDataAPI s : c.getRewards().values()) {
                        s.executePenaltyAthTheTerminationOfContract(false);
                    }
                    toRemove.add(c.getId());
                    continue;
                }
            }

            c.decrementMonth();
            if (c.isExpired()) {
                if (!c.isTerminated() && !c.isIssuedByPlayer()) {
                    for (TradeContractRewardDataAPI value : c.getRewards().values()) {
                        value.executeRewardAtTheEndOfContract();
                    }
                }
                toRemove.add(c.getId());
            }
        }
        ArrayList<AoTDTradeContract> contracts = new ArrayList<>();
        for (String id : toRemove) {

            AoTDTradeContract contract = activeContracts.remove(id);
            if (contract != null) {
                contracts.add(contract);
            }
        }
        if (!contracts.isEmpty()) {
            AoTDContractFinished finished = new AoTDContractFinished(contracts);
            Global.getSector().getIntelManager().addIntel(finished);
        }

        invalidatePredictions();
    }

    private int pullFromPlayerExports(
            AoTDFactionTradeData playerTrade, String commodityId, int need, String contractId) {
        if (need <= 0) return 0;

        ArrayList<AoTDMarketData> exporters = new ArrayList<>();
        for (AoTDMarketData md : playerTrade.getTradeData().values()) {
            if (md == null) continue;
            MarketAPI market = Global.getSector().getEconomy().getMarket(md.marketId);
            if (market == null) continue;
            if (!market.hasSpaceport() || market.getAccessibilityMod().computeEffective(0f) <= 0f)
                continue;
            int avail = md.getRemainingNet(commodityId);
            if (avail > 0) exporters.add(md);
        }
        if (exporters.isEmpty()) return 0;

        exporters.sort((a, b) -> Float.compare(b.outsideWeight, a.outsideWeight));

        int remaining = need;
        int delivered = 0;

        for (AoTDMarketData md : exporters) {
            if (remaining <= 0) break;

            int avail = md.getRemainingNet(commodityId);
            if (avail <= 0) continue;

            int moved = Math.min(avail, remaining);

            md.remainingNet.merge(commodityId, -moved, Integer::sum);
            if (md.remainingNet.getOrDefault(commodityId, 0) == 0) {
                md.remainingNet.remove(commodityId);
            }

            md.recordContractExport(contractId, commodityId, moved);

            delivered += moved;
            remaining -= moved;
        }

        return delivered;
    }

    private void distributeToFactionEqually(String factionId, String commodityId, int delivered) {
        if (delivered <= 0) return;
        if (factionId == null || factionId.isEmpty()) return;

        AoTDFactionTradeData facTrade =
                AoTDTradeManager.getInstance().getFactionTradeData(factionId);
        if (facTrade == null) return;

        ArrayList<AoTDMarketData> markets = new ArrayList<>(facTrade.getTradeData().values());
        if (markets.isEmpty()) return;

        int n = markets.size();
        int each = delivered / n;
        int rem = delivered % n;

        for (int i = 0; i < n; i++) {
            AoTDMarketData md = markets.get(i);
            if (md == null) continue;

            int add = each + (i < rem ? 1 : 0);
            if (add <= 0) continue;

            md.remainingNet.merge(commodityId, add, Integer::sum);
            if (md.remainingNet.getOrDefault(commodityId, 0) == 0) {
                md.remainingNet.remove(commodityId);
            }
        }
    }

    public static AoTDTradeContract createTestContract() {
        AoTDTradeContract contract = new AoTDTradeContract("test", null, "", 8);

        contract.addContractData("hand_weapons", 1234, 0.60f);
        //        contract.addContractData("fuel", 1234, 0.60f);
        //        contract.addContractData("supplies", 1234, 0.60f);
        //        contract.addContractData("food", 1234, 0.60f);
        return contract;
    }

    public static AoTDTradeContract createTestContract(
            FactionAPI faction, boolean withPerson, float cut) {
        AoTDTradeContract contract = null;
        if (withPerson) {
            contract =
                    new AoTDTradeContract(
                            "test" + faction.getId(), faction.createRandomPerson(), null, 8);
            contract.addContractData("drugs", 1000, 0.60f);
            int money = contract.getPredictedMoneyWorthForMonth();
            int exp = AoTDTradeContractLevelData.getXpForMonthlyContractValue(money);
            contract.addReward("rep_merchant", new MerchantReputationReward(exp));
            contract.addReward(
                    "rep_" + Factions.PIRATES,
                    new FactionReputationReward(Factions.PIRATES, 10, 5));
            contract.addReward(
                    "blueprint_" + Global.getSettings().getFighterWingSpec("gladius_wing"),
                    new BlueprintReward("gladius_wing", BlueprintReward.BlueprintData.FIGHTER));

        } else {
            contract = new AoTDTradeContract("test" + faction.getId(), null, faction.getId(), 8);
            contract.addContractData("fuel", 4000, 0.60f);
            contract.addContractData("supplies", 10000, 0.60f);
            contract.addReward("monthly_storage", new MonthlyCommodityToStorageReward());
        }

        getInstance().addContract(contract);
        return contract;
    }

    public void moveToTop(String contractId) {
        if (contractId == null) return;

        AoTDTradeContract c = activeContracts.remove(contractId);
        if (c == null) return;

        // rebuild map with this first
        LinkedHashMap<String, AoTDTradeContract> reordered = new LinkedHashMap<>();
        reordered.put(contractId, c);

        for (Map.Entry<String, AoTDTradeContract> e : activeContracts.entrySet()) {
            reordered.put(e.getKey(), e.getValue());
        }

        activeContracts.clear();
        activeContracts.putAll(reordered);

        invalidatePredictions();
    }
}

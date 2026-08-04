// file: data/kaysaar/aotd/tot/scripts/trade/contracts/AoTDTradeContract.java
package data.kaysaar.aotd.tot.scripts.trade.contracts;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDPlayerContractCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons.GraphPeriodChosenButton;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDTradeContract implements Cloneable {

    @Override
    public AoTDTradeContract clone() {
        AoTDTradeContract copy = null;
        try {
            copy = (AoTDTradeContract) super.clone();
            copy.rewards.putAll(this.rewards);
            copy.contractData.putAll(this.contractData);

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return copy;
    }

    boolean wasTaken = false;

    public void setWasTaken(boolean wasTaken) {
        this.wasTaken = wasTaken;
    }

    public boolean itWasTaken() {
        return wasTaken;
    }

    public void runCleanUp() {
        ArrayList<String> toCleanUp = new ArrayList<>();
        for (TradeContractData s : getContractData().values()) {
            if (s.getReqMonthly() <= 0) {
                toCleanUp.add(s.getCommodityId());
            }
        }
        toCleanUp.forEach(x -> getContractData().remove(x));
    }

    public static class TradeContractData {
        String commodityId;
        int reqMonthly;
        float cutFromBasePrice; // 0..1 (you use as "cut" / margin / subsidy)
        boolean isSuspended = false;
        String factionId;
        String contractId;
        float attemptedAmountToTake;
        boolean isProductionPercentageMode = false;

        public void setAttemptedAmountToTake(float attemptedAmountToTake) {
            this.attemptedAmountToTake = attemptedAmountToTake;
        }

        public TradeContractData(String commodityId, int reqMonthly, float cutFromBasePrice) {
            this.commodityId = commodityId;
            this.reqMonthly = reqMonthly;
            this.cutFromBasePrice = cutFromBasePrice;
        }

        public TradeContractData(
                String commodityId,
                float attemptedAmountToTake,
                float cutFromBasePrice,
                String factionId,
                String contractId) {
            this.commodityId = commodityId;
            this.attemptedAmountToTake = attemptedAmountToTake;
            this.cutFromBasePrice = cutFromBasePrice;
            this.factionId = factionId;
            this.contractId = contractId;
            this.isProductionPercentageMode = true;
        }

        public String getCommodityId() {
            return commodityId;
        }

        public int getReqMonthly() {
            if (isSuspended) {
                return 0;
            }
            if (isProductionPercentageMode) {
                int total =
                        AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                                        commodityId, factionId)
                                - AoTDSectorProductionDemandDataUtils
                                        .getTotalDemandFromFactionBeforeContract(
                                                commodityId, factionId, contractId);
                return (int) Math.floor(total * attemptedAmountToTake);
            }
            return reqMonthly;
        }

        public float getCutFromBasePrice() {
            return cutFromBasePrice;
        }

        public void setCommodityId(String commodityId) {
            this.commodityId = commodityId;
        }

        public void setReqMonthly(int reqMonthly) {
            this.reqMonthly = reqMonthly;
        }

        public void setCutFromBasePrice(float cutFromBasePrice) {
            this.cutFromBasePrice = cutFromBasePrice;
        }

        public void setSuspended(boolean suspended) {
            isSuspended = suspended;
        }
    }

    public LinkedHashMap<String, TradeContractRewardDataAPI> getRewards() {
        return rewards;
    }

    LinkedHashMap<String, TradeContractRewardDataAPI> rewards = new LinkedHashMap<>();

    public void addReward(String id, TradeContractRewardDataAPI reward) {
        rewards.put(id, reward);
    }

    public boolean isFrozen = false;

    public boolean isContractFrozen() {
        return isFrozen;
    }

    public String contractTypeId;

    public String getContractTypeId() {
        return contractTypeId;
    }

    public Color getContractTypeColor() {
        return getColorOfContractName().brighter();
    }

    public void setContractTypeId(String contractTypeId) {
        this.contractTypeId = contractTypeId;
    }

    public FactionAPI getFaction() {
        if (isPrivate()) {
            return person.getFaction();
        }
        return Global.getSector().getFaction(factionId);
    }

    public void setFrozen(boolean frozen) {
        isFrozen = frozen;
    }

    // WARNING USE IT ONLY BEFORE PLACING IT INTO LIST
    public void setNewId(String id) {
        this.id = id;
        for (TradeContractData value : contractData.values()) {
            if (value.isProductionPercentageMode) {
                value.factionId = getFactionId();
                value.contractId = id;
            }
        }
    }

    public void generateFlavorTextOfMerchantInOffer(TooltipMakerAPI tooltip) {
        if (tooltip == null) return;

        // No flavor for player-issued contracts
        if (isIssuedByPlayer()) return;

        FactionAPI fac = getFaction();
        boolean isBlackMarket =
                isPrivate()
                        && fac != null
                        && AoTDTradeContractBrowserCreator.blackMarketFactions.contains(
                                fac.getId());
        boolean isFactionIssued = !isPrivate();

        ArrayList<String> lines = new ArrayList<>();

        if (isBlackMarket) {
            lines.add(
                    "You bring me what I ask for, on schedule, and we both walk away satisfied. I don’t need details — just consistency.");
            lines.add(
                    "I’m not interested in paperwork or explanations, only results delivered month after month without interruption.");
            lines.add(
                    "Keep the shipments steady and quiet, and this arrangement will remain profitable for both of us.");
            lines.add(
                    "No delays, no stories — just the agreed volume on time and we won’t have any problems.");
        } else if (isFactionIssued) {
            String name = fac != null ? fac.getDisplayName() : "The issuing authority";
            name = AoTDToolboxMisc.capitalizeFirst(name);
            lines.add(
                    name
                            + " has formally issued this procurement contract to ensure stable and predictable supply across its territories.");
            lines.add(
                    "This public trade directive from "
                            + name
                            + " seeks certified suppliers capable of maintaining consistent monthly throughput.");
            lines.add(
                    name
                            + " posts this standing contract to address ongoing logistical demand and reinforce regional stability.");
            lines.add(
                    "Under regulated trade terms, "
                            + name
                            + " invites reliable partners to fulfill recurring supply obligations.");
        } else {
            lines.add(
                    "I’m looking for a dependable supplier who can maintain steady deliveries without excuses or last-minute surprises.");
            lines.add(
                    "Meet my quota reliably each month and you’ll have a stable, ongoing business arrangement with me.");
            lines.add(
                    "What I value most is consistency — keep the goods flowing on schedule and we’ll both benefit.");
            lines.add(
                    "If you can handle regular throughput without disruption, this will be a straightforward and profitable deal.");
        }

        if (lines.isEmpty()) return;

        String pick = lines.get(Misc.random.nextInt(lines.size()));
        tooltip.addPara(pick, 3f);
    }

    public boolean canFreezeContract() {
        return isIssuedByPlayer();
    }

    public boolean canEditContract() {
        return isIssuedByPlayer()
                && AoTDPlayerContractCreatorManager.getCreator(getContractTypeId())
                        .canEditContract();
    }

    public boolean canTerminateContract() {
        return true;
    }

    public void terminateContract() {
        getContractData().clear();
        applyContractPenaltyForCanceling();
    }

    public void applyContractPenaltyForCanceling() {
        for (TradeContractRewardDataAPI value : rewards.values()) {
            value.executePenaltyAthTheTerminationOfContract(true);
        }
    }

    public String getContractType() {
        if (AshMisc.isStringValid(factionId)) {
            if (factionId.equals(Factions.PLAYER)) {
                return "State-Mandated";
            } else {
                return "Foreign Trade";
            }
        }
        return "Private";
    }

    public final LinkedHashMap<String, TradeContractData> contractData = new LinkedHashMap<>();

    public void addContractData(String commodityId, int reqMonthly, float cutFromBasePrice) {
        if (commodityId == null) return;
        if (reqMonthly <= 0) {
            contractData.remove(commodityId);

        } else {
            contractData.put(
                    commodityId, new TradeContractData(commodityId, reqMonthly, cutFromBasePrice));
        }
    }

    public void addContractData(
            String commodityId, float percentageToTake, float cutFromBasePrice) {
        if (commodityId == null) return;
        if (percentageToTake <= 0) {
            contractData.remove(commodityId);

        } else {
            contractData.put(
                    commodityId,
                    new TradeContractData(
                            commodityId,
                            percentageToTake,
                            cutFromBasePrice,
                            getFactionId(),
                            getId()));
        }
    }

    public Map<String, TradeContractData> getContractData() {
        return contractData;
    }

    public String getIconName() {
        if (AshMisc.isStringValid(factionId)) {
            return Global.getSector().getFaction(factionId).getCrest();
        }
        return person.getPortraitSprite();
    }

    public int getMonthlyAmountNeeded(String commodityId) {
        TradeContractData d = contractData.get(commodityId);
        return d == null ? 0 : Math.max(0, d.getReqMonthly());
    }

    public float getCut(String commodityId) {
        TradeContractData d = contractData.get(commodityId);
        return d == null ? 0f : d.getCutFromBasePrice();
    }

    public String getNameOfContract() {
        if (AshMisc.isStringValid(factionId)) {
            return AoTDToolboxMisc.capitalizeFirst(
                    Global.getSector().getFaction(factionId).getDisplayName());
        }
        return person.getNameString();
    }

    public Color getColorOfContractName() {
        if (AshMisc.isStringValid(factionId)) {
            return Global.getSector().getFaction(factionId).getBaseUIColor();
        }
        return person.getFaction().getBaseUIColor();
    }

    public String getDurationOfContractString() {
        if (AshMisc.isStringValid(factionId)) {
            if (factionId.equals(Factions.PLAYER)) {
                return "Ongoing";
            }
        }
        return GraphPeriodChosenButton.getCombinedLabelStringForPeriod(getMonthsRemaining());
    }

    public Pair<String, Color> getCurrentContractStatus() {
        if (isFrozen) return new Pair<>("Frozen", Misc.getGrayColor());

        if (!AoTDToolboxMisc.isContractMetFully(this)) {
            if (isIssuedByPlayer()) return new Pair<>("Not fulfilled", Misc.getHighlightColor());

            if (doesContractHavePenalty && missedTimes >= allowedMissedTimes - 1)
                return new Pair<>("Termination risk", Misc.getNegativeHighlightColor());

            return new Pair<>(
                    "At risk (" + missedTimes + "/" + allowedMissedTimes + ")",
                    Misc.getNegativeHighlightColor());
        }

        return new Pair<>("Fulfilled", Misc.getPositiveHighlightColor());
    }

    public String getSubTypeOfContractString() {
        if (isIssuedByPlayer()) {
            return AoTDPlayerContractCreatorManager.getCreator(getContractTypeId())
                    .getNameOfContract();
        }
        if (isPrivate()
                && AoTDTradeContractBrowserCreator.blackMarketFactions.contains(
                        getFaction().getId())) {
            return "Black Market Contract";
        }
        return null;
    }

    private String id;
    private PersonAPI person;
    private String factionId;
    private int monthsRemaining;
    private int missedTimes;
    public boolean hasCustomEffectSection = false;

    public int allowedMissedTimes = 3;
    public boolean doesContractHavePenalty = true;

    public int getAllowedMissedTimes() {
        return allowedMissedTimes;
    }

    public AoTDTradeContract(String id, PersonAPI person, String factionId, int monthsRemaining) {
        this.id = id;
        this.person = person;
        this.factionId = (factionId != null && !factionId.isEmpty()) ? factionId : null;
        this.monthsRemaining = Math.max(0, monthsRemaining);
        this.missedTimes = 0;
    }

    public AoTDTradeContract(
            String id,
            PersonAPI person,
            String factionId,
            int monthsRemaining,
            boolean customEffectSection) {
        this.id = id;
        this.person = person;
        this.hasCustomEffectSection = customEffectSection;
        this.factionId = (factionId != null && !factionId.isEmpty()) ? factionId : null;
        this.monthsRemaining = Math.max(0, monthsRemaining);
        this.missedTimes = 0;
    }

    public boolean hasCustomEffectSection() {
        return hasCustomEffectSection;
    }

    public void printCustomSection(TooltipMakerAPI tooltip, float width) {}

    public String getId() {
        return id;
    }

    public PersonAPI getPerson() {
        return person;
    }

    public String getFactionId() {
        return factionId;
    }

    public boolean isPrivate() {
        return factionId == null;
    }

    public boolean isIssuedByPlayer() {
        return Factions.PLAYER.equals(factionId);
    }

    public int getMonthsRemaining() {
        return monthsRemaining;
    }

    public int getMissedTimes() {
        return missedTimes;
    }

    public void decrementMonth() {
        if (monthsRemaining > 0) monthsRemaining--;
    }

    public void addMiss() {
        if (doesContractHavePenalty) missedTimes++;
    }

    public boolean atRiskOfTermination() {
        return doesContractHavePenalty && (missedTimes >= allowedMissedTimes - 1);
    }

    public boolean isExpired() {
        return monthsRemaining <= 0;
    }

    public boolean isTerminated() {
        return doesContractHavePenalty && missedTimes >= allowedMissedTimes;
    }

    public void executeEndOfContract(boolean wasTerminatedByPlayerManually) {}

    public void executeMonthEnd(float percentageOfEntireContractMet) {}

    public void executeMonthEndForCommodity(int delivered, String commodityId) {
        for (TradeContractRewardDataAPI value : rewards.values()) {
            value.executeRewardMonthly(delivered, getMonthlyAmountNeeded(commodityId), commodityId);
        }
    }

    public int getPredictedMoneyWorthForMonth() {
        int am = 0;
        for (TradeContractData value : contractData.values()) {
            am += getMoneyFromMonth(value.getCommodityId(), value.getReqMonthly());
        }
        return am;
    }

    public boolean isContractEarningIncome() {
        if (!isIssuedByPlayer()) {
            return true;
        } else {
            PlayerContractCreatorAPI creatorAPI =
                    AoTDPlayerContractCreatorManager.getCreator(getContractTypeId());

            if (creatorAPI != null && !creatorAPI.isContractPaidByPlayer()) {
                return true;
            }
            return false;
        }
    }

    public int getMoneyFromMonth(String commodityId, int amountMet) {
        if (amountMet <= 0) return 0;
        float base = Global.getSettings().getCommoditySpec(commodityId).getBasePrice();
        float cut = Math.max(0f, getCut(commodityId));
        PlayerContractCreatorAPI creatorAPI =
                AoTDPlayerContractCreatorManager.getCreator(getContractTypeId());
        if (isIssuedByPlayer()) {
            if (creatorAPI != null && !creatorAPI.isContractPaidByPlayer()) {
                return (int) Math.floor(base * amountMet * cut);
            }
            return (int) Math.floor(-base * amountMet * cut);
        } else {
            return (int) Math.floor(base * amountMet * cut);
        }
    }
}

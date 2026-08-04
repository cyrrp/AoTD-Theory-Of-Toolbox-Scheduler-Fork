package data.kaysaar.aotd.tot.ui.grandwonders;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import java.util.Map;

public class GrandWonderContract extends AoTDTradeContract {
    AoTDConstructionSite site;
    public static float MARGIN_OF_COMMODITY_PRICE = 0.10f;

    public GrandWonderContract(AoTDConstructionSite site) {
        super(site.getUniqueIdForContract(), null, Factions.PLAYER, 9999);
        this.site = site;
        for (Map.Entry<String, Integer> entry : site.getMonthlyResNeeded().entrySet()) {
            addContractData(entry.getKey(), entry.getValue(), MARGIN_OF_COMMODITY_PRICE);
        }
    }

    @Override
    public void executeMonthEndForCommodity(int delivered, String commodityId) {
        site.addResourcesToBeSpentOnRestoration(commodityId, delivered);
    }

    @Override
    public String getSubTypeOfContractString() {
        return site.getWonderAPI().getCurrentName();
    }

    @Override
    public String getContractTypeId() {
        return "grand_wonder_construction";
    }

    @Override
    public void printCustomSection(TooltipMakerAPI tooltip, float width) {
        tooltip.addPara(
                "Every month this contract attempts to consume the required resources to complete construction of this massive wonder.",
                3f);
    }

    @Override
    public boolean canEditContract() {
        return false;
    }

    @Override
    public boolean canTerminateContract() {
        return false;
    }

    @Override
    public boolean isExpired() {
        boolean isThisSitePresent = false;
        for (Industry industry : site.getMarket().getIndustries()) {
            if (industry instanceof AoTDConstructionSite site) {
                if (site.equals(this.site)) {
                    isThisSitePresent = true;
                }
            }
        }
        return !site.isUpgrading()
                || !isThisSitePresent
                || !site.getMarket().getFaction().isPlayerFaction();
    }

    @Override
    public boolean canFreezeContract() {
        return false;
    }

    @Override
    public void executeMonthEnd(float percentageOfEntireContractMet) {
        contractData.clear();
        for (Map.Entry<String, Integer> entry : site.getMonthlyResNeeded().entrySet()) {
            addContractData(entry.getKey(), entry.getValue(), MARGIN_OF_COMMODITY_PRICE);
            if (entry.getValue() > 0) {
                addContractData(entry.getKey(), entry.getValue(), MARGIN_OF_COMMODITY_PRICE);
            }
        }
        this.runCleanUp();
    }

    @Override
    public String getContractType() {
        return "Grand Wonder Construction";
    }
}

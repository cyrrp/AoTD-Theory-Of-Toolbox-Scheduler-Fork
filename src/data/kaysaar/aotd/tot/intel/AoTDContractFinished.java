package data.kaysaar.aotd.tot.intel;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.DetailedTradeContractUI;
import java.util.ArrayList;
import java.util.Set;

public class AoTDContractFinished extends BaseIntelPlugin {
    ArrayList<AoTDTradeContract> contractsFinishedThisMonth = new ArrayList<>();

    public AoTDContractFinished(ArrayList<AoTDTradeContract> contractsFinishedThisMonth) {
        this.contractsFinishedThisMonth = contractsFinishedThisMonth;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara("Those contracts were finished this month:", 5f);
        info.addSpacer(10f);
        for (AoTDTradeContract contract : contractsFinishedThisMonth) {
            info.addCustom(
                    DetailedTradeContractUI.createContractorSection(width, 40, contract, false),
                    3f);
        }
        endAfterDelay(3f);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara("Report of this month's finished contract is ready!", Misc.getGrayColor(), 3f);
    }

    @Override
    protected String getName() {
        return "Trade Contracts Report";
    }

    @Override
    protected void notifyEnded() {
        contractsFinishedThisMonth.clear();
    }

    @Override
    public boolean hasLargeDescription() {
        return false;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_TRADE);
        return tags;
    }

    @Override
    public String getIcon() {
        return "graphics/stations/station_side03.png";
    }
}

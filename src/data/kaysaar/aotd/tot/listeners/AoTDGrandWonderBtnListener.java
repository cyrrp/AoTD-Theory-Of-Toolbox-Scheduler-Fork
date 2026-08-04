package data.kaysaar.aotd.tot.listeners;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderManager;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import data.kaysaar.aotd.tot.ui.grandwonders.GrandWonderChooseConstructionDialog;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class AoTDGrandWonderBtnListener implements IndustryOptionProvider {
    public static Object WONDER = new Object();
    public static Object REMOVE_WONDER = new Object();

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (ind.getId().contains("aotd_gw_construction_site")) {
            AoTDConstructionSite site = (AoTDConstructionSite) ind;
            if (!ind.isUpgrading()) {
                IndustryOptionData data =
                        new IndustryOptionData("Choose Wonder to Build", WONDER, ind, this);
                data.color = new Color(181, 148, 16);
                return Collections.singletonList(data);
            }
        }
        if (ind instanceof GrandWonderAPI) {
            if (!ind.canShutDown() && !ind.showShutDown()) {
                IndustryOptionData data =
                        new IndustryOptionData("Dismantle Wonder", REMOVE_WONDER, ind, this);
                data.color = Misc.getNegativeHighlightColor();
                return Collections.singletonList(data);
            }
        }
        return null;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id.equals(WONDER)) {
            tooltip.addPara("Choose which grand wonder to construct", 3f);
        } else {
            tooltip.addPara("Order to dismantle grand wonder.", 3f);
            tooltip.addPara(
                    "Warning! This action might have greater consequences across entire faction!",
                    Misc.getNegativeHighlightColor(),
                    5f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id.equals(WONDER)) {
            AshMisc.initPopUpDialog(
                    new GrandWonderChooseConstructionDialog(ui, (AoTDConstructionSite) opt.ind),
                    1000,
                    650);
        } else {
            final BaseIndustry industry = (BaseIndustry) opt.ind;
            CustomDialogDelegate delegate =
                    new BaseCustomDialogDelegate() {
                        @Override
                        public void createCustomDialog(
                                CustomPanelAPI panel, CustomDialogCallback callback) {
                            float opad = 10f;
                            Color highlight = Misc.getHighlightColor();
                            TooltipMakerAPI info = panel.createUIElement(600, 100, false);
                            info.setParaInsigniaLarge();
                            info.addSpacer(2f);

                            info.addPara(
                                    "Dismantling this great wonder will provoke widespread anger among your population, seeing it as a waste of valuable resources.",
                                    0f);

                            info.addPara(
                                    "This outrage will reduce stability by %s for 365 days.",
                                    4f, Misc.getNegativeHighlightColor(), "3");
                            panel.addUIElement(info).inTL(0, 0);
                        }

                        @Override
                        public boolean hasCancelButton() {
                            return true;
                        }

                        @Override
                        public void customDialogConfirm() {
                            MarketAPI market = industry.getMarket();
                            long token =
                                    SchedulerBridge.beforeMarketMutation(
                                            market, SchedulerBridge.MUTATION_INDUSTRY_STRUCTURE);
                            try {
                                market.removeIndustry(
                                        industry.getId(),
                                        MarketAPI.MarketInteractionMode.REMOTE,
                                        false);
                                GrandWonderManager.getInstance().removeBuiltSoFar(industry.getId());
                                for (MarketAPI factionMarket :
                                        Misc.getFactionMarkets(market.getFaction())) {
                                    factionMarket
                                            .getStability()
                                            .addTemporaryModFlat(
                                                    365,
                                                    "aotd_wonder_destroyed_" + industry.getId(),
                                                    "Destruction of wonder",
                                                    -3);
                                }
                            } finally {
                                SchedulerBridge.afterMarketMutation(
                                        token,
                                        market,
                                        SchedulerBridge.DIRTY_STRUCTURE
                                                | SchedulerBridge.DIRTY_INDUSTRIES
                                                | SchedulerBridge.DIRTY_DERIVED_ECONOMY,
                                        0L);
                            }
                        }

                        @Override
                        public void customDialogCancel() {}
                    };
            ui.showDialog(600, 125, delegate);
        }
    }

    @Override
    public void addToIndustryTooltip(
            Industry ind,
            Industry.IndustryTooltipMode mode,
            TooltipMakerAPI tooltip,
            float width,
            boolean expanded) {}
}

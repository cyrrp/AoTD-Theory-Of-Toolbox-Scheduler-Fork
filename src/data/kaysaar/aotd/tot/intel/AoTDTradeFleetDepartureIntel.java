package data.kaysaar.aotd.tot.intel;

import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.misc.TradeFleetDepartureIntel;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;
import java.awt.*;
import java.util.ArrayList;

public class AoTDTradeFleetDepartureIntel extends TradeFleetDepartureIntel {
    public AoTDTradeFleetDepartureIntel(RouteManager.RouteData route) {
        super(route);
    }

    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        initTransientData();

        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = Misc.getTextColor();
        float pad = 3f;
        float opad = 10f;

        info.addImage(faction.getLogo(), width, 128, opad);

        float tier = data.size;
        String fleetType = getFleetTypeName().toLowerCase();

        //		LabelAPI label = info.addPara(Misc.ucFirst(faction.getPersonNamePrefixAOrAn()) + " " +
        //					 faction.getPersonNamePrefix() + " " + fleetType + " is departing from " +
        //					 data.from.getName() + " and heading to " + data.to.getName() + ".",
        //					 opad, tc,
        //					 faction.getBaseUIColor(),
        //					 faction.getPersonNamePrefix());
        //		label.setHighlight(faction.getPersonNamePrefix(), data.from.getName(),
        // data.to.getName());
        //		label.setHighlightColors(data.from.getFaction().getBaseUIColor(),
        // data.from.getFaction().getBaseUIColor(), data.to.getFaction().getBaseUIColor());

        LabelAPI label =
                info.addPara(
                        "Your contacts "
                                + data.from.getOnOrAt()
                                + " "
                                + data.from.getName()
                                + " let you know that "
                                + faction.getPersonNamePrefixAOrAn()
                                + " "
                                + faction.getPersonNamePrefix()
                                + " "
                                + fleetType
                                + " is preparing for a voyage and will soon depart for "
                                + data.to.getName()
                                + ".",
                        opad,
                        tc,
                        faction.getBaseUIColor(),
                        faction.getPersonNamePrefix());

        label.setHighlight(data.from.getName(), faction.getPersonNamePrefix(), data.to.getName());
        label.setHighlightColors(
                data.from.getFaction().getBaseUIColor(),
                faction.getBaseUIColor(),
                data.to.getFaction().getBaseUIColor());

        addBulletPoints(info, ListInfoMode.IN_DESC);

        String what = getWhat();

        if (!deliverList.isEmpty()) {
            info.addPara(
                    "On the outward trip to "
                            + data.to.getName()
                            + " the fleet will carry "
                            + EconomyFleetAssignmentAI.EconomyRouteData.getCargoList(deliverList)
                            + ".",
                    opad);
            ArrayList<Pair<String, Integer>> commodities = new ArrayList<>();
            for (EconomyFleetAssignmentAI.CargoQuantityData curr : deliverList) {
                commodities.add(new Pair<>(curr.getCommodity().getId(), curr.units));
            }
            AoTDCommodityShortPanelCombined panel =
                    new AoTDCommodityShortPanelCombined(width, 2, commodities);
            info.addCustom(panel.getMainPanel(), opad);

        } else {
            info.addPara(
                    "The fleet will carry nothing of note on the trip to "
                            + data.to.getName()
                            + ".",
                    opad);
        }
        if (!returnList.isEmpty()) {
            info.addPara(
                    "On the trip back to "
                            + data.from.getName()
                            + " the fleet will carry "
                            + EconomyFleetAssignmentAI.EconomyRouteData.getCargoList(returnList)
                            + ".",
                    opad);

            ArrayList<Pair<String, Integer>> commodities = new ArrayList<>();
            for (EconomyFleetAssignmentAI.CargoQuantityData curr : returnList) {
                commodities.add(new Pair<>(curr.getCommodity().getId(), curr.units));
            }
            AoTDCommodityShortPanelCombined panel =
                    new AoTDCommodityShortPanelCombined(width, 2, commodities);
            info.addCustom(panel.getMainPanel(), opad);
        } else {
            info.addPara(
                    "The fleet will carry nothing of note on the trip back to "
                            + data.from.getName()
                            + ".",
                    opad);
        }

        if (valuable && large) {
            info.addPara(
                    "It's noteworthy because it's carrying a large quantity of valuable "
                            + what
                            + ".",
                    opad);
        } else if (valuable) {
            info.addPara("It's noteworthy because it's carrying valuable " + what + ".", opad);
        } else if (large) {
            info.addPara(
                    "It's noteworthy because it's carrying a large quantity of " + what + ".",
                    opad);
        }

        if (data.smuggling) {
            info.addPara(
                    "Smugglers often operate in a gray legal and moral area. "
                            + "Thus, if one comes to an unfortunate end - as so often happens in their line of work - "
                            + "it's unlikely to cause a unified response from whatever "
                            + "faction or organization they're nominally affiliated with.",
                    g,
                    opad);
        }
    }
}

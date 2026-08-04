package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetRouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;

public class AoTDFleetCheck extends BaseCommandPlugin {
    @Override
    public boolean execute(
            String ruleId,
            InteractionDialogAPI dialog,
            List<Misc.Token> params,
            Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return false;

        CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        boolean smuggler = mem.getBoolean(MemFlags.MEMORY_KEY_SMUGGLER);
        boolean trader = mem.getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET);

        RouteManager.RouteData route =
                RouteManager.getInstance().getRoute(EconomyFleetRouteManager.SOURCE_ID, fleet);

        return (trader || smuggler) && route != null;
    }
}

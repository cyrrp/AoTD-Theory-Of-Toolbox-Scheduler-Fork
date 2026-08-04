package data.kaysaar.aotd.tot.intel;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryMissionIntel;

public class AoTDDeliveryMissionIntel extends DeliveryMissionIntel {
    public AoTDDeliveryMissionIntel(DeliveryBarEvent event, InteractionDialogAPI dialog) {
        super(event, dialog);
    }

    @Override
    protected void applyTradeValueImpact(float totalReward) {}
}

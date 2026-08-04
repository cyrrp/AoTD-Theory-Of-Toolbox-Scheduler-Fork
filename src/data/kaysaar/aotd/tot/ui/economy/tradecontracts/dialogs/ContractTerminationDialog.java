package data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.DetailedTradeContractUI;

public class ContractTerminationDialog extends BasePopUpDialog {
    AoTDTradeContract contract;
    DetailedTradeContractUI contractUI;

    public ContractTerminationDialog(
            String headerTitle, DetailedTradeContractUI contractUI, AoTDTradeContract contract) {
        super(headerTitle);
        this.contractUI = contractUI;
        this.contract = contract;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        super.createContentForDialog(tooltip, width);
        tooltip.setParaFont(Fonts.ORBITRON_20AABOLD);
        if (!contract.isIssuedByPlayer()) {
            tooltip.addPara("Termination of this contract will bring consequences!", 3f)
                    .setAlignment(Alignment.MID);

        } else {
            tooltip.addPara(
                            "Termination will end this agreement with no additional consequences.",
                            3f)
                    .setAlignment(Alignment.MID);
        }
        tooltip.addPara("Do you want to proceed?", 5f).setAlignment(Alignment.MID);
    }

    @Override
    public void applyConfirmScript() {
        super.applyConfirmScript();
        AoTDTradeContractManager.getInstance().terminateContract(contract.getId());
        if (!contract.isIssuedByPlayer()) {
            Global.getSoundPlayer().playUISound(Sounds.REP_LOSS, 1, 1);
        }
        contractUI.setContract(null);
        contractUI.setUpdateUI(true);
    }
}

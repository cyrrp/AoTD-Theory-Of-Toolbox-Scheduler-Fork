package data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.DetailedTradeContractUI;

public class ContractFreezeDialog extends BasePopUpDialog {
    AoTDTradeContract contract;
    DetailedTradeContractUI contractUI;

    public ContractFreezeDialog(
            String headerTitle, DetailedTradeContractUI contractUI, AoTDTradeContract contract) {
        super(headerTitle);
        this.contractUI = contractUI;
        this.contract = contract;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        super.createContentForDialog(tooltip, width);

        tooltip.setParaFont(Fonts.ORBITRON_20AABOLD);
        if (contract.isContractFrozen()) {
            tooltip.addPara(
                    "Unfreezing this contract will resume normal operations. "
                            + "Monthly deliveries will again be expected and payments will occur "
                            + "based on the resources delivered.",
                    3f);
        } else {
            tooltip.addPara(
                    "Freezing this contract will temporarily suspend it. While frozen, "
                            + "no resources will be taken from your colonies and no payments will be made. "
                            + "The contract will remain inactive until you choose to unfreeze it.",
                    3f);
        }

        tooltip.addPara("Do you want to proceed?", 5f).setAlignment(Alignment.MID);
    }

    @Override
    public void applyConfirmScript() {
        super.applyConfirmScript();
        AoTDTradeContractManager.getInstance()
                .setContractFrozen(contract.getId(), !contract.isContractFrozen());
        contractUI.createUI();
        contractUI.setUpdateUI(true);
    }
}

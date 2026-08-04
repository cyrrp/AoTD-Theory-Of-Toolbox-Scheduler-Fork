package data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.EconomyTradeDealsData;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.DetailedTradeContractUI;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.ContractCreatorDetailsPlugin;

public class ContractEditDialog extends BasePopUpDialog {
    AoTDTradeContract existingContract;
    AoTDTradeContract newContract;
    ContractCreatorDetailsPlugin plugin;
    DetailedTradeContractUI tradeContractUI;

    public ContractEditDialog(AoTDTradeContract contract, DetailedTradeContractUI tradeContractUI) {
        super("Edit Contract");
        this.newContract = contract.clone();
        this.existingContract = contract;
        this.tradeContractUI = tradeContractUI;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        float height = getPanelToInfluence().getPosition().getHeight() - 50;
        plugin = new ContractCreatorDetailsPlugin(width, height, existingContract);
        tooltip.addCustom(plugin.getMainPanel(), 5f);
    }

    @Override
    public void applyConfirmScript() {
        super.applyConfirmScript();
        AoTDTradeContractManager.getInstance().removeContract(existingContract.getId());
        newContract.runCleanUp();
        if (!newContract.getContractData().isEmpty()) {
            AoTDTradeContractManager.getInstance().addContract(newContract);
            tradeContractUI.setContract(newContract);
        } else {
            tradeContractUI.setContract(null);
        }
        EconomyTradeDealsData.forceTableUpdate = true;
    }
}

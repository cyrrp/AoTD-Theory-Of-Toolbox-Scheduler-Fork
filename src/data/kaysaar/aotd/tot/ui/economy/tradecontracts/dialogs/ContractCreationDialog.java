package data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDPlayerContractCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import data.kaysaar.aotd.tot.ui.economy.EconomyTradeDealsData;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.ContractCreatorPlugin;

public class ContractCreationDialog extends BasePopUpDialog {
    ContractCreatorPlugin content;

    public ContractCreationDialog() {
        super("Create Contract");
        this.confirmButtonText = "Create";
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        float height = getPanelToInfluence().getPosition().getHeight() - 50;
        content = new ContractCreatorPlugin(width, height);
        tooltip.addCustom(content.getMainPanel(), 0f);
        tooltip.setHeightSoFar(height);
        super.createContentForDialog(tooltip, width);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (content != null) {
            if (content.getExplainSection() != null) {
                if (content.getExplainSection().getCurrentContract().getContractData().isEmpty()) {
                    boolean currState = getConfirmButton().isEnabled();
                    if (currState) {
                        getConfirmButton().setEnabled(false);
                    }
                } else {
                    boolean currState = getConfirmButton().isEnabled();
                    if (!currState) {
                        getConfirmButton().setEnabled(true);
                    }
                }
            }
        }
    }

    @Override
    public void applyConfirmScript() {
        super.applyConfirmScript();
        if (content.getExplainSection().getCurrentContract() != null
                && content.getExplainSection().getId() != null) {
            if (!content.getExplainSection().getCurrentContract().getContractData().isEmpty()) {
                content.getExplainSection().getCurrentContract().setNewId(Misc.genUID());
                PlayerContractCreatorAPI creatorAPI =
                        AoTDPlayerContractCreatorManager.getCreator(
                                content.getExplainSection().getId());
                creatorAPI.onContractCreated(content.getExplainSection().getCurrentContract());
                AoTDTradeContractManager.getInstance()
                        .addContract(content.getExplainSection().getCurrentContract());
                EconomyTradeDealsData.forceTableUpdate = true;
            }
        }
    }

    @Override
    public void onExit() {
        content.clearUI();
    }
}

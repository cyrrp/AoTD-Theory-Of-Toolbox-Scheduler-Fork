package data.kaysaar.aotd.tot.ui.economy.tradecontracts;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.ProgressBarComponentV2;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractLevelData;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs.ContractBrowsingDialog;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs.ContractCreationDialog;
import java.awt.*;
import java.util.List;

public class TradeContractFactionData implements ExtendedUIPanelPlugin {

    CustomPanelAPI mainPanel, contentPanel;
    CustomPanelAPI reputationPanel;
    DetailedTradeContractUI tradeContractUI;
    UILinesRenderer renderer;
    ButtonAPI browseContracts;
    ButtonAPI issueContract;
    String currentlyChosenContract = "";

    public DetailedTradeContractUI getTradeContractUI() {
        return tradeContractUI;
    }

    public void setCurrentlyChosenContract(String currentlyChosenContract) {
        String prev = this.currentlyChosenContract;
        this.currentlyChosenContract = currentlyChosenContract;
        if (!prev.equals(currentlyChosenContract)) {
            createDetailedContractUI();
        }
    }

    public TradeContractFactionData(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        renderer = new UILinesRenderer(0f);
        renderer.setPanel(mainPanel);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        createReputationPanel();
        float remHeight =
                contentPanel.getPosition().getHeight()
                        - reputationPanel.getPosition().getHeight()
                        - 10;
        float startingY = reputationPanel.getPosition().getHeight() + 5;
        if (tradeContractUI == null) {
            tradeContractUI =
                    new DetailedTradeContractUI(
                            contentPanel.getPosition().getWidth(), remHeight, null);
            contentPanel.addComponent(tradeContractUI.getMainPanel()).inTL(0, startingY);
        }
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public void createDetailedContractUI() {
        AoTDTradeContract contract =
                AoTDTradeContractManager.getInstance()
                        .getActiveContracts()
                        .get(currentlyChosenContract);
        tradeContractUI.setContract(contract);
    }

    public void createReputationPanel() {
        if (reputationPanel != null) {
            contentPanel.removeComponent(reputationPanel);
        }
        reputationPanel =
                Global.getSettings().createCustom(contentPanel.getPosition().getWidth(), 300, null);
        TooltipMakerAPI tl =
                reputationPanel.createUIElement(
                        reputationPanel.getPosition().getWidth(),
                        reputationPanel.getPosition().getHeight(),
                        false);
        tl.setParaFont(Fonts.ORBITRON_20AA);
        tl.addPara("Merchant Reputation", Misc.getTooltipTitleAndLightHighlightColor(), 0f)
                .setAlignment(Alignment.MID);
        AoTDTradeContractLevelData data = AoTDTradeContractManager.getInstance().getCurrLevelData();
        ProgressBarComponentV2 barComponentV2 =
                new ProgressBarComponentV2(
                        reputationPanel.getPosition().getWidth() - 5,
                        25,
                        data.getLevelBandProgress() + " / " + data.getUiThresholdXp(),
                        Fonts.DEFAULT_SMALL,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        data.getProgressLevelFloat());
        tl.addCustom(barComponentV2.getMainPanel(), 10f);
        tl.addTooltipToPrevious(
                new TooltipMakerAPI.TooltipCreator() {
                    @Override
                    public boolean isTooltipExpandable(Object tooltipParam) {
                        return false;
                    }

                    @Override
                    public float getTooltipWidth(Object tooltipParam) {
                        return 400f;
                    }

                    @Override
                    public void createTooltip(
                            TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                        tooltip.addTitle("Merchant Reputation");

                        tooltip.addPara(
                                "Merchant Reputation represents how trusted you are among traders and production contractors.",
                                5f);

                        tooltip.addPara(
                                "As your reputation grows, the number of available contracts increases and the quality of those contracts improves. "
                                        + "Highly reputable merchants gain access to larger and more valuable production opportunities.",
                                3f);

                        tooltip.addPara(
                                "The most effective way to increase Merchant Reputation is by successfully completing contracts. "
                                        + "Delivering requested goods and fulfilling agreements reliably will steadily improve your standing "
                                        + "and attract more lucrative offers over time.",
                                Misc.getHighlightColor(),
                                5f);
                    }
                },
                TooltipMakerAPI.TooltipLocation.RIGHT,
                false);
        tl.setParaFont(Fonts.ORBITRON_12);
        tl.addPara(
                        "Level %s - %s",
                        3f, Color.ORANGE, "" + data.getCurrentLevel(), data.getCurrentTitle())
                .setAlignment(Alignment.MID);
        browseContracts =
                tl.addButton(
                        "Browse posted contracts",
                        null,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TL_BR,
                        contentPanel.getPosition().getWidth() - 10,
                        30,
                        10);
        browseContracts.setEnabled(!AshMisc.getMarketsUnderPlayer().isEmpty());
        if (!browseContracts.isEnabled()) {
            tl.addTooltipToPrevious(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return false;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 400;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            tooltip.addPara(
                                    "Establish your faction by founding a colony before you can accept contracts.",
                                    3f);
                        }
                    },
                    TooltipMakerAPI.TooltipLocation.RIGHT,
                    false);
        }
        issueContract =
                tl.addButton(
                        "Issue contract",
                        null,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.BL_TR,
                        contentPanel.getPosition().getWidth() - 10,
                        30,
                        10f);
        issueContract.setEnabled(!AshMisc.getMarketsUnderPlayer().isEmpty());
        if (!issueContract.isEnabled()) {
            tl.addTooltipToPrevious(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return false;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 400;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            tooltip.addPara(
                                    "Establish your faction by founding a colony before you can issue contracts.",
                                    3f);
                        }
                    },
                    TooltipMakerAPI.TooltipLocation.RIGHT,
                    false);
        }
        tl.addSectionHeading("Contract Data", Alignment.MID, 5f);
        reputationPanel
                .getPosition()
                .setSize(reputationPanel.getPosition().getWidth(), tl.getHeightSoFar());
        reputationPanel.addUIElement(tl).inTL(0, 0);
        contentPanel.addComponent(reputationPanel).inTL(0, 0);
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {
        renderer.render(alphaMult);
    }

    @Override
    public void advance(float amount) {
        if (browseContracts != null && browseContracts.isChecked()) {
            browseContracts.setChecked(false);
            if (Global.getSettings().isDevMode()) {
                AoTDTradeContractManager.getInstance().generateNewContractsForBrowser();
            }
            AshMisc.initPopUpDialog(new ContractBrowsingDialog(), 1150, 550);
        }
        if (issueContract != null && issueContract.isChecked()) {
            issueContract.setChecked(false);
            AshMisc.initPopUpDialog(new ContractCreationDialog(), 1230, 630);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}

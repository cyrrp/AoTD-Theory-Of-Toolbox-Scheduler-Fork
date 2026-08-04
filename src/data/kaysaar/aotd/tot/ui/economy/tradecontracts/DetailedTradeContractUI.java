package data.kaysaar.aotd.tot.ui.economy.tradecontracts;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons.GraphPeriodChosenButton;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.browser.IntrestedInCommodities;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs.ContractEditDialog;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs.ContractFreezeDialog;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs.ContractTerminationDialog;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DetailedTradeContractUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    AoTDTradeContract contract;
    public ButtonAPI freeze, top, delete, accept, edit;
    boolean showcaseMode = false;
    boolean updateUI;

    public boolean isUpdateUI() {
        return updateUI;
    }

    public void setUpdateUI(boolean updateUI) {
        this.updateUI = updateUI;
    }

    public DetailedTradeContractUI(float width, float height, AoTDTradeContract contract) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.contract = contract;
        createUI();
    }

    public DetailedTradeContractUI(
            float width, float height, AoTDTradeContract contract, boolean showcaseMode) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.showcaseMode = showcaseMode;
        this.contract = contract;
        createUI();
    }

    public void setContract(AoTDTradeContract contract) {
        this.contract = contract;
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
        if (contract != null) {
            float heightOfButtons = 85;
            if (showcaseMode) {
                heightOfButtons = 30;
            }
            TooltipMakerAPI tl =
                    contentPanel.createUIElement(
                            contentPanel.getPosition().getWidth(),
                            contentPanel.getPosition().getHeight() - heightOfButtons - 5,
                            true);
            TooltipMakerAPI tlButtons =
                    contentPanel.createUIElement(
                            contentPanel.getPosition().getWidth(), heightOfButtons, false);
            tl.addCustom(
                    createContractorSection(
                            contentPanel.getPosition().getWidth(), 40, contract, false),
                    0f);
            if (showcaseMode) {
                contract.generateFlavorTextOfMerchantInOffer(tl);
            }
            tl.addSectionHeading(
                    "Required monthly resources to fulfill contract", Alignment.MID, 5f);
            AoTDCommodityShortPanelCombined combined =
                    new AoTDCommodityShortPanelCombined(
                            contentPanel.getPosition().getWidth() - 10, 3, contract, false, false);
            tl.addCustom(combined.getMainPanel(), 5f);
            if (showcaseMode) {
                for (AoTDTradeContract.TradeContractData value :
                        contract.getContractData().values()) {
                    int def =
                            AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                                            value.getCommodityId(), Factions.PLAYER)
                                    - AoTDSectorProductionDemandDataUtils
                                            .getTotalDemandFromFactionTillContract(
                                                    value.getCommodityId(),
                                                    Factions.PLAYER,
                                                    contract.getId());
                    def *= -1;
                    if (def > 0) {
                        tl.addPara(
                                "Warning! Our faction does not produce enough resources to fulfill this contract!",
                                Misc.getNegativeHighlightColor(),
                                5f);
                        break;
                    }
                }
            }
            int am = contract.getPredictedMoneyWorthForMonth();
            Color c = Color.ORANGE;
            if (am < 0) {
                c = Misc.getNegativeHighlightColor();
            }
            if (contract.getPredictedMoneyWorthForMonth() > 0) {
                tl.addPara(
                        "Assuming the full shipment is delivered, our faction earns an estimated %s per month from this contract.",
                        10f, c, Misc.getDGSCredits(am));
            } else {
                tl.addPara(
                        "Assuming the full shipment is delivered, our faction pays an estimated %s per month for this contract.",
                        10f, c, Misc.getDGSCredits(-am));
            }

            if (!contract.isIssuedByPlayer()) {
                if (!showcaseMode) {
                    tl.addPara(
                            "Remaining duration of contract : %s",
                            5f,
                            Color.ORANGE,
                            GraphPeriodChosenButton.getCombinedLabelStringForPeriod(
                                    contract.getMonthsRemaining()));
                    tl.addPara(
                            "Missed shipments : %s / %s",
                            3f,
                            Color.ORANGE,
                            contract.getMissedTimes() + "",
                            contract.getAllowedMissedTimes() + "");
                    if (contract.atRiskOfTermination()) {
                        tl.addPara(
                                "Missing any portion of a required shipment will immediately terminate the contract, resulting in decline of merchant renown!",
                                Misc.getNegativeHighlightColor(),
                                3f);
                    }
                } else {
                    tl.addPara(
                            "Allowed maximum amount of missed shipments: %s",
                            5f, Color.ORANGE, contract.getAllowedMissedTimes() + "");
                }
                tl.addPara(
                        "*For the contract to count as fulfilled this month, all required cargo must be delivered in full. Any shortfall will count as a missed shipment.",
                        Misc.getGrayColor().brighter(),
                        3f);

                tl.addSectionHeading(
                        "Effects upon successful contract completion", Alignment.MID, 5f);
            } else {
                tl.addSectionHeading("Ongoing contract effects", Alignment.MID, 5f);
            }
            tl.addSpacer(2f);
            float initOpadText = 3f;
            if (contract.hasCustomEffectSection()) {
                contract.printCustomSection(tl, contentPanel.getPosition().getWidth());
            } else {
                tl.setBulletedListMode(BaseIntelPlugin.BULLET);

                for (TradeContractRewardDataAPI value : contract.getRewards().values()) {
                    value.createRewardSection(
                            tl,
                            contentPanel.getPosition().getWidth(),
                            TradeContractRewardDataAPI.TradeContractRewardTooltipMode.CONTRACT_DATA,
                            initOpadText);
                }
                tl.setBulletedListMode(null);
            }

            if (!contract.isIssuedByPlayer()) {
                tl.addSectionHeading("Penalties should contract be terminated", Alignment.MID, 5f);
                tl.setBulletedListMode(BaseIntelPlugin.BULLET);

                for (TradeContractRewardDataAPI value : contract.getRewards().values()) {
                    value.createPenaltySectionForNotMeetingContract(
                            tl,
                            contentPanel.getPosition().getWidth(),
                            TradeContractRewardDataAPI.TradeContractRewardTooltipMode.CONTRACT_DATA,
                            initOpadText);
                }
                tl.setBulletedListMode(null);
            }
            if (!showcaseMode) {
                top =
                        tlButtons.addButton(
                                "Move to Top Priority",
                                null,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor().darker().darker(),
                                Alignment.MID,
                                CutStyle.TL_BR,
                                contentPanel.getPosition().getWidth() - 15,
                                25f,
                                0f);
                CustomPanelAPI row =
                        Global.getSettings()
                                .createCustom(contentPanel.getPosition().getWidth(), 25, null);
                TooltipMakerAPI rTl =
                        row.createUIElement(
                                row.getPosition().getWidth(), row.getPosition().getHeight(), false);
                freeze =
                        rTl.addButton(
                                "Freeze Contract",
                                null,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Alignment.MID,
                                CutStyle.TL_BR,
                                (contentPanel.getPosition().getWidth() - 30) / 2,
                                25f,
                                0f);
                if (contract.isContractFrozen()) {
                    freeze.setText("Resume Contract");
                }
                edit =
                        rTl.addButton(
                                "Edit Contract",
                                null,
                                Misc.getBasePlayerColor(),
                                Misc.getPositiveHighlightColor().darker().darker(),
                                Alignment.MID,
                                CutStyle.TL_BR,
                                (contentPanel.getPosition().getWidth() - 30) / 2,
                                25f,
                                5f);
                freeze.setEnabled(contract.canFreezeContract());
                edit.setEnabled(contract.canEditContract());
                edit.getPosition()
                        .inTL(
                                contentPanel.getPosition().getWidth()
                                        - 10
                                        - edit.getPosition().getWidth(),
                                0);
                row.addUIElement(rTl).inTL(-5, 0);
                tlButtons.addCustom(row, 5f);

                delete =
                        tlButtons.addButton(
                                "Terminate Contract",
                                null,
                                Misc.getBasePlayerColor(),
                                Misc.getNegativeHighlightColor().darker().darker(),
                                Alignment.MID,
                                CutStyle.TL_BR,
                                contentPanel.getPosition().getWidth() - 15,
                                25f,
                                5f);
                freeze.setEnabled(contract.canFreezeContract());
                delete.setEnabled(contract.canTerminateContract());

                contentPanel
                        .addUIElement(tlButtons)
                        .inTL(2, contentPanel.getPosition().getHeight() - 85);
            } else {
                accept =
                        tlButtons.addButton(
                                "Accept Contract",
                                null,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor().darker().darker(),
                                Alignment.MID,
                                CutStyle.TL_BR,
                                contentPanel.getPosition().getWidth() - 15,
                                25f,
                                0f);
                contentPanel
                        .addUIElement(tlButtons)
                        .inTL(2, contentPanel.getPosition().getHeight() - 30);
            }
            contentPanel.addUIElement(tl).inTL(3, 0);
        }
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public static CustomPanelAPI createContractorSection(
            float width, float height, AoTDTradeContract contract, boolean forBrowser) {
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI tl = panel.createUIElement(width - height - 10, height, false);
        ImageViewer viewer = new ImageViewer(height, height, contract.getIconName());
        panel.addComponent(viewer.getComponentPanel()).inTL(0, 0);

        tl.setParaFont(Fonts.ORBITRON_20AA);
        tl.addPara(contract.getNameOfContract(), contract.getColorOfContractName(), 0f);
        tl.setParaFont(Fonts.DEFAULT_SMALL);

        if (forBrowser) {
            LabelAPI label = tl.addPara("Interested in buying:", 6f);
            float length = label.computeTextWidth(label.getText());
            ArrayList<String> strs = new ArrayList<>();
            contract.getContractData().values().forEach(x -> strs.add(x.getCommodityId()));
            CustomPanelAPI mainp =
                    new IntrestedInCommodities(
                                    width - length - height - 10,
                                    30,
                                    Alignment.TL,
                                    strs.toArray(new String[0]))
                            .getMainPanel();

            tl.addCustom(mainp, 5f).getPosition().inTL(length + 10, 18);
        } else {
            if (contract.getSubTypeOfContractString() == null) {
                tl.addPara(contract.getContractType(), contract.getContractTypeColor(), 3f);

            } else {
                tl.addPara(
                        contract.getContractType() + " - " + contract.getSubTypeOfContractString(),
                        contract.getContractTypeColor(),
                        3f);
            }
        }

        panel.addUIElement(tl).inTL(height, 0);
        return panel;
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (top != null && top.isChecked()) {
            top.setChecked(false);
            AoTDTradeContractManager.getInstance().moveToTop(contract.getId());
            setUpdateUI(true);
        }
        if (delete != null && delete.isChecked()) {
            delete.setChecked(false);
            ContractTerminationDialog dialog =
                    new ContractTerminationDialog("Terminate Contract", this, contract);
            AshMisc.initPopUpDialog(dialog, 750, 150);
        }
        if (edit != null && edit.isChecked()) {
            edit.setChecked(false);
            AshMisc.initPopUpDialog(new ContractEditDialog(contract, this), 950, 630);
        }
        if (freeze != null && freeze.isChecked()) {
            freeze.setChecked(false);
            String contractFreezeText = "Freeze Contract";
            if (contract.isContractFrozen()) {
                contractFreezeText = "Resume Contract";
            }
            ContractFreezeDialog dialog =
                    new ContractFreezeDialog(contractFreezeText, this, contract);
            AshMisc.initPopUpDialog(dialog, 1050, 180);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}

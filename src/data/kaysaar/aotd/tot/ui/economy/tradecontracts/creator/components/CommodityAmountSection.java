package data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.ProgressBarComponentV2;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import java.awt.*;
import java.util.List;

public class CommodityAmountSection implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    CustomPanelAPI contentPanel;
    CustomPanelAPI infoPanel;
    ProgressBarComponentV2 toggleForAmount;
    ButtonAPI confirm;
    int currNumber = 0;
    int maxNumber;
    String commodityId;
    public int perSegment = 100;
    PlayerContractCreatorAPI creatorAPI;
    int currSegment = 0;
    public boolean signalToUpdateAmount = false;
    public boolean percentageMode = false;

    public CommodityAmountSection(
            float width,
            float height,
            String commodityid,
            int currNumber,
            int maxNumber,
            PlayerContractCreatorAPI creatorAPI) {
        this.currNumber = currNumber;
        this.maxNumber = maxNumber;
        this.creatorAPI = creatorAPI;
        this.commodityId = commodityid;
        if (creatorAPI.useUnits()) {
            if (maxNumber > 0 && maxNumber < 100) {
                this.maxNumber = 100;
            }
        } else {
            this.maxNumber = 10;
            perSegment = Math.floorDiv(maxNumber, 10);
            if (perSegment <= 0) {
                perSegment = 1;
            }
            this.currSegment = currNumber / perSegment;
            if (this.currSegment >= this.maxNumber) {
                this.currSegment = this.maxNumber;
            }
        }

        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    public void setCreatorAPI(PlayerContractCreatorAPI creatorAPI) {
        this.creatorAPI = creatorAPI;
    }

    float heightOfMain = 145;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }
        if (creatorAPI.useUnits()) {
            contentPanel =
                    Global.getSettings()
                            .createCustom(
                                    mainPanel.getPosition().getWidth(),
                                    mainPanel.getPosition().getHeight(),
                                    null);
            TooltipMakerAPI contentMain =
                    contentPanel.createUIElement(
                            mainPanel.getPosition().getWidth(), heightOfMain, false);
            contentMain.setParaFont(Fonts.ORBITRON_20AABOLD);
            CustomPanelAPI row =
                    Global.getSettings()
                            .createCustom(contentPanel.getPosition().getWidth(), 50, null);
            CommoditySpecAPI specAPI = Global.getSettings().getCommoditySpec(commodityId);
            ImageViewer viewer =
                    new ImageViewer(
                            row.getPosition().getHeight(),
                            row.getPosition().getHeight(),
                            specAPI.getIconName());
            row.addComponent(viewer.getComponentPanel()).inMid();
            contentMain.addCustom(row, 0f);
            contentMain.addPara(specAPI.getName(), 3f).setAlignment(Alignment.MID);
            contentMain
                    .addPara(
                            "Current Amount in Contract",
                            Misc.getTooltipTitleAndLightHighlightColor(),
                            0f)
                    .setAlignment(Alignment.MID);
            int max = maxNumber / perSegment;
            int curr = currNumber / perSegment;
            currSegment = curr;
            toggleForAmount =
                    new ProgressBarComponentV2(
                            contentPanel.getPosition().getWidth(),
                            25,
                            "testing huge text",
                            Fonts.DEFAULT_SMALL,
                            Misc.getBasePlayerColor(),
                            Misc.getDarkPlayerColor(),
                            currSegment,
                            max,
                            0) {
                        @Override
                        public void influenceLabel() {
                            LabelAPI labelAPI = getProgressLabel();
                            int per = perSegment;
                            int curr = this.currentSection;
                            int max = this.sections;
                            int currAm = curr * per;
                            int maxAm = max * per;
                            String am = String.valueOf(currAm);
                            String mAm = String.valueOf(maxAm);
                            labelAPI.setText(am + " / " + mAm);
                            labelAPI.setHighlight(am, mAm);
                            labelAPI.setHighlightColor(Color.ORANGE);
                            labelAPI.getPosition()
                                    .setSize(
                                            labelAPI.computeTextWidth(labelAPI.getText()),
                                            labelAPI.computeTextHeight(labelAPI.getText()));
                        }
                    };

            contentMain.addCustom(toggleForAmount.getMainPanel(), 10f);
            contentPanel.addUIElement(contentMain).inTL(0, 0);
        } else {
            contentPanel =
                    Global.getSettings()
                            .createCustom(
                                    mainPanel.getPosition().getWidth(),
                                    mainPanel.getPosition().getHeight(),
                                    null);
            TooltipMakerAPI contentMain =
                    contentPanel.createUIElement(
                            mainPanel.getPosition().getWidth(), heightOfMain, false);
            contentMain.setParaFont(Fonts.ORBITRON_20AABOLD);
            CustomPanelAPI row =
                    Global.getSettings()
                            .createCustom(contentPanel.getPosition().getWidth(), 50, null);
            CommoditySpecAPI specAPI = Global.getSettings().getCommoditySpec(commodityId);
            ImageViewer viewer =
                    new ImageViewer(
                            row.getPosition().getHeight(),
                            row.getPosition().getHeight(),
                            specAPI.getIconName());
            row.addComponent(viewer.getComponentPanel()).inMid();
            contentMain.addCustom(row, 0f);
            contentMain.addPara(specAPI.getName(), 3f).setAlignment(Alignment.MID);
            contentMain
                    .addPara(
                            "Current Amount in Contract",
                            Misc.getTooltipTitleAndLightHighlightColor(),
                            0f)
                    .setAlignment(Alignment.MID);
            toggleForAmount =
                    new ProgressBarComponentV2(
                            contentPanel.getPosition().getWidth(),
                            25,
                            "testing huge text",
                            Fonts.DEFAULT_SMALL,
                            Misc.getBasePlayerColor(),
                            Misc.getDarkPlayerColor(),
                            currSegment,
                            maxNumber,
                            0) {
                        @Override
                        public void influenceLabel() {
                            LabelAPI labelAPI = getProgressLabel();
                            String curr = "" + (this.currentSection * 10);
                            labelAPI.setText(curr + "%");
                            labelAPI.setHighlight(curr);
                            labelAPI.setHighlightColor(Color.ORANGE);
                            labelAPI.getPosition()
                                    .setSize(
                                            labelAPI.computeTextWidth(labelAPI.getText()),
                                            labelAPI.computeTextHeight(labelAPI.getText()));
                        }
                    };

            contentMain.addCustom(toggleForAmount.getMainPanel(), 10f);
            contentPanel.addUIElement(contentMain).inTL(0, 0);
        }

        recreateInfoPanel();
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public void setCommodityId(String commodityId, int currNumber, int maxNumber) {
        this.commodityId = commodityId;
        this.currNumber = currNumber;
        this.maxNumber = maxNumber;
        if (creatorAPI.useUnits()) {
            if (maxNumber > 0 && maxNumber < 100) {
                this.maxNumber = 100;
            }
        } else {
            this.maxNumber = 10;
            perSegment = Math.floorDiv(maxNumber, 10);
            this.currSegment = currNumber / perSegment;
            if (this.currSegment >= this.maxNumber) {
                this.currSegment = this.maxNumber;
            }
        }
        createUI();
    }

    public void resetProgressBar() {
        createUI();
    }

    public void recreateInfoPanel() {
        if (infoPanel != null) {
            contentPanel.removeComponent(infoPanel);
        }
        infoPanel =
                Global.getSettings()
                        .createCustom(
                                contentPanel.getPosition().getWidth(),
                                contentPanel.getPosition().getHeight() - heightOfMain,
                                null);
        TooltipMakerAPI tooltip =
                infoPanel.createUIElement(
                        infoPanel.getPosition().getWidth(),
                        infoPanel.getPosition().getHeight(),
                        false);
        int basePrice = (int) Global.getSettings().getCommoditySpec(commodityId).getBasePrice();
        basePrice *= (int) (creatorAPI.getCutToPayForCommodity(commodityId) * getCurrAmount());
        tooltip.setParaFont(Fonts.ORBITRON_12);

        creatorAPI.createProcTooltipSection(
                tooltip, tooltip.getWidthSoFar(), basePrice, getCurrAmount(), commodityId);
        confirm =
                tooltip.addButton(
                        "Confirm",
                        null,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.NONE,
                        infoPanel.getPosition().getWidth() - 10,
                        25,
                        5f);

        infoPanel.addUIElement(tooltip).inTL(0, 0);
        contentPanel
                .addComponent(infoPanel)
                .inTL(
                        5,
                        contentPanel.getPosition().getHeight()
                                - infoPanel.getPosition().getHeight()
                                - 5);
    }

    public int getCurrAmount() {
        return currSegment * perSegment;
    }

    public int getCurrSegment() {
        return currSegment;
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
        if (toggleForAmount != null) {
            if (toggleForAmount.haveMovedToAnotherSegment()) {
                toggleForAmount.setHaveMovedToAnotherSegment(false);
                toggleForAmount.influenceLabel();
                currSegment = toggleForAmount.currentSection;
                recreateInfoPanel();
            }
        }
        if (confirm != null && confirm.isChecked()) {
            confirm.setChecked(false);
            currNumber = getCurrAmount();
            signalToUpdateAmount = true;
        }
    }

    public boolean isSignalToUpdateAmount() {
        return signalToUpdateAmount;
    }

    public void setSignalToUpdateAmount(boolean signalToUpdateAmount) {
        this.signalToUpdateAmount = signalToUpdateAmount;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}

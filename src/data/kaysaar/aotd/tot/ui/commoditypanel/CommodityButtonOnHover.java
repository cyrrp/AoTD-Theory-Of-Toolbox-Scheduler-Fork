// file: data/kaysaar/aotd/tot/ui/commoditypanel/CommodityButtonOnHover.java
package data.kaysaar.aotd.tot.ui.commoditypanel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommodityButtonOnHover implements TooltipMakerAPI.TooltipCreator {

    private final CommoditySpecAPI spec;
    private final MarketAPI market;
    private final boolean isInDialog;
    private boolean incomeMode = false;

    public CommodityButtonOnHover(CommoditySpecAPI spec, MarketAPI market) {
        this(spec, market, false);
    }

    public CommodityButtonOnHover(CommoditySpecAPI spec, MarketAPI market, boolean isInDialog) {
        this.spec = spec;
        this.market = market;
        this.isInDialog = isInDialog;
    }

    public CommodityButtonOnHover(
            CommoditySpecAPI spec, MarketAPI market, boolean isInDialog, boolean incomeMode) {
        this.spec = spec;
        this.market = market;
        this.isInDialog = isInDialog;
        this.incomeMode = incomeMode;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 550f;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        tooltip.setCodexEntryId(CodexDataV2.getCommodityEntryId(spec.getId()));
        tooltip.addTitle(spec.getName());
        tooltip.addPara(
                Global.getSettings()
                        .getDescription(spec.getId(), Description.Type.RESOURCE)
                        .getText1(),
                3f);

        AoTDCommodityOnMarket com =
                AoTDCommodityOnMarket.getComMarketInstanceSave(market, spec.getId());

        if (!isInDialog) {
            tooltip.addPara(
                    "Click to view global market info", Misc.getPositiveHighlightColor(), 5f);
        }

        // ---------------- LOCAL ----------------
        tooltip.addSectionHeading(
                "Local Production and Demand",
                market.getFaction().getBaseUIColor(),
                market.getFaction().getDarkUIColor(),
                Alignment.MID,
                5f);

        int supply = com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        int demand = com.getSupplyDemandData().getTotalRawUnitsFromDemand();

        if (supply > 0) {
            tooltip.addPara(
                    "Current production of %s: %s.",
                    3f, Color.ORANGE, spec.getName(), Misc.getWithDGS(supply));
            tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
            int unknown = 0;
            for (Industry industry : market.getIndustries()) {
                int raw = com.getSupplyDemandData().getRawSupplyFromIndustry(industry);
                if (industry.isHidden()) {
                    unknown += raw;
                    continue;
                }
                if (raw > 0) {
                    tooltip.addPara(
                            industry.getCurrentName() + ": %s",
                            3f,
                            Misc.getPositiveHighlightColor(),
                            "+" + Misc.getWithDGS(raw));
                }
            }
            if (unknown > 0) {
                tooltip.addPara(
                        "No Data: %s",
                        3f, Misc.getPositiveHighlightColor(), "+" + Misc.getWithDGS(unknown));
            }
            tooltip.setBulletedListMode(null);

            if (com.getExcessQuantity() > 0) {
                tooltip.addPara(
                        "Market surplus: currently %s units can be purchased at reduced prices.",
                        5f,
                        Misc.getPositiveHighlightColor(),
                        Color.ORANGE,
                        Misc.getWithDGS(com.getExcessQuantity()));
            }

            tooltip.addSpacer(10f);
        } else {
            tooltip.addPara(
                    "There is no local production present on this market.",
                    Misc.getGrayColor(),
                    3f);
            tooltip.addSpacer(10f);
        }

        if (demand > 0) {
            int unknown = 0;
            tooltip.addPara(
                    "Current demand of %s: %s.",
                    3f, Color.ORANGE, spec.getName(), Misc.getWithDGS(demand));
            tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
            for (Industry industry : market.getIndustries()) {
                int raw = com.getSupplyDemandData().getRawDemandFromIndustry(industry);
                if (industry.isHidden()) {
                    unknown += raw;
                    continue;
                }
                if (raw > 0) {
                    tooltip.addPara(
                            industry.getCurrentName() + " : %s",
                            3f,
                            Misc.getNegativeHighlightColor(),
                            "-" + Misc.getWithDGS(raw));
                }
            }
            if (unknown > 0) {
                tooltip.addPara(
                        "No Data: %s",
                        3f, Misc.getNegativeHighlightColor(), "-" + Misc.getWithDGS(unknown));
            }
            tooltip.setBulletedListMode(null);

            if (com.getDeficitQuantity() > 0) {
                tooltip.addPara(
                        "Market shortage: up to %s units can be sold here for significantly higher prices.",
                        5f,
                        Misc.getNegativeHighlightColor(),
                        Color.ORANGE,
                        Misc.getWithDGS(com.getDeficitQuantity()));
                ;
                if (Global.getSettings().isDevMode()) {
                    tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
                    for (MutableStat.StatMod value :
                            com.getExcDefData().deficit.getFlatMods().values()) {
                        tooltip.addPara(
                                "%s: %s",
                                3f,
                                new Color[] {Misc.getNegativeHighlightColor(), Misc.getTextColor()},
                                Misc.getWithDGS(value.value),
                                value.getDesc());
                    }
                    tooltip.setBulletedListMode(null);
                }
            }

            tooltip.addSpacer(5f);
        } else {
            tooltip.addPara(
                    "There is no local demand present on this market.", Misc.getGrayColor(), 3f);
            tooltip.addSpacer(5f);
        }

        // ---------------- IMPORTS ----------------
        if (com.doesImport()) {
            tooltip.addSectionHeading(
                    "Imports",
                    market.getFaction().getBaseUIColor(),
                    market.getFaction().getDarkUIColor(),
                    Alignment.MID,
                    5f);
            tooltip.addSpacer(5f);

            int imports = -com.getSupplyDemandData().getExport(com);

            tooltip.addPara(
                    "%s requires around %s units of %s.",
                    0f, Color.ORANGE, market.getName(), Misc.getWithDGS(imports), spec.getName());

            tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);

            AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);
            int importsFromFaction = md != null ? md.getInternalImported(com.getId()) : 0;

            int independentImports = imports - importsFromFaction;
            if (independentImports < 0) independentImports = 0;

            if (importsFromFaction > 0) {
                float saved =
                        importsFromFaction
                                * spec.getBasePrice()
                                * AoTDCommodityEconSpecManager.getCutForCommodity(
                                        com.getSpec().getId(), false);
                tooltip.addPara(
                        "%s supplied internally from faction-trades",
                        3f,
                        Color.ORANGE,
                        Misc.getWithDGS(importsFromFaction),
                        Misc.getDGSCredits(saved));
            }

            if (independentImports > 0) {
                tooltip.addPara(
                        "%s purchased from external markets.",
                        3f, Color.ORANGE, Misc.getWithDGS(independentImports));
            }

            tooltip.setBulletedListMode(null);
        }

        // ---------------- EXPORTS ----------------
        if (com.getSupplyDemandData().getExport(com) > 0
                || com.getSupplyDemandData().getTotalRawUnitsFromSupply() > 0) {

            tooltip.addSectionHeading(
                    "Export and Local Supply",
                    market.getFaction().getBaseUIColor(),
                    market.getFaction().getDarkUIColor(),
                    Alignment.MID,
                    5f);
            tooltip.addSpacer(5f);

            int exports = com.getSupplyDemandData().getExport(com);
            int localized =
                    Math.min(
                            com.getSupplyDemandData().getTotalRawUnitsFromDemand(),
                            com.getSupplyDemandData().getTotalRawUnitsFromSupply());

            int totalProduced = localized + Math.max(exports, 0);
            if (totalProduced > 0) {
                if (localized > 0 && exports <= 0) {
                    tooltip.addPara(
                            "%s sells %s units of %s.",
                            0f,
                            Color.ORANGE,
                            market.getName(),
                            Misc.getWithDGS(localized),
                            spec.getName());
                } else if (localized > 0 && exports > 0) {
                    tooltip.addPara(
                            "%s sells %s units of %s.",
                            0f,
                            Color.ORANGE,
                            market.getName(),
                            Misc.getWithDGS(totalProduced),
                            spec.getName());
                } else if (exports > 0) {
                    tooltip.addPara(
                            "%s sells %s units of %s",
                            0f,
                            Color.ORANGE,
                            market.getName(),
                            Misc.getWithDGS(exports),
                            spec.getName());
                }
            }
            if (!com.isDemandLegal() || !com.isSupplyLegal()) {
                tooltip.addPara(
                        "We earn no income from local trade, as well as from exports due to commodity being illegal!",
                        Misc.getNegativeHighlightColor(),
                        3f);
            } else {
                tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);

                AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);

                int exportsToFaction = md != null ? md.getInternalExported(com.getId()) : 0;

                // ---- Contracts (actual OR predicted) ----
                int contractExports = 0;
                LinkedHashMap<String, Integer> contractShipments = new LinkedHashMap<>();
                boolean usingPredicted = false;

                if (md != null) {
                    AoTDTradeContractManager mgr = AoTDTradeContractManager.getInstance();
                    // This must prepare predicted allocations for the current month if contracts
                    // weren't executed yet.
                    // Implement in manager: compute predicted allocations without mutating
                    // remainingNet.
                    mgr.ensurePredictionsUpToDate();

                    boolean hasActual =
                            md.exportedByContract != null && !md.exportedByContract.isEmpty();
                    Map<String, LinkedHashMap<String, Integer>> source =
                            hasActual ? md.exportedByContract : md.predictedExportedByContract;

                    usingPredicted = !hasActual;

                    if (source != null && !source.isEmpty()) {
                        for (Map.Entry<String, LinkedHashMap<String, Integer>> e :
                                source.entrySet()) {
                            String contractId = e.getKey();
                            LinkedHashMap<String, Integer> perCommodity = e.getValue();
                            if (perCommodity == null) continue;

                            int shipped = perCommodity.getOrDefault(spec.getId(), 0);
                            if (shipped > 0) {
                                contractShipments.put(contractId, shipped);
                                contractExports += shipped;
                            }
                        }
                    }
                }

                // External export remainder (best-effort, based on current export stat)
                // If your getExport() isn't aligned with your solver snapshot, consider replacing
                // this with an explicit
                // "externalExported" field on AoTDMarketData computed during the month-end
                // pipeline.
                int independentExports = exports - exportsToFaction - contractExports;
                if (independentExports < 0) independentExports = 0;

                // LOCAL USE
                if (localized > 0) {
                    float income =
                            localized
                                    * spec.getBasePrice()
                                    * AoTDCommodityEconSpecManager.getCutForCommodity(
                                            com.getSpec().getId(), true);
                    tooltip.addPara(
                            "%s sold locally, generating roughly %s in internal trade.",
                            3f,
                            Color.ORANGE,
                            Misc.getWithDGS(localized),
                            Misc.getDGSCredits(income));
                }

                // FACTION EXPORT (internal)
                if (exportsToFaction > 0) {
                    float income =
                            exportsToFaction
                                    * spec.getBasePrice()
                                    * AoTDCommodityEconSpecManager.getCutForCommodity(
                                            com.getSpec().getId(), true);
                    tooltip.addPara(
                            "%s shipped to faction markets, generating roughly %s.",
                            3f,
                            Color.ORANGE,
                            Misc.getWithDGS(exportsToFaction),
                            Misc.getDGSCredits(income));
                }
                // EXTERNAL EXPORT (remaining after contracts)
                float income = AoTDToolboxMisc.getSpeculatedExportIncome(com);

                if (independentExports > 0 && income > 0) {
                    tooltip.addPara(
                            "%s exported to external markets, generating roughly %s.",
                            3f,
                            Color.ORANGE,
                            Misc.getWithDGS(independentExports),
                            Misc.getDGSCredits(income));
                }
                if (!incomeMode) {
                    if (AoTDToolboxMisc.getSpeculatedExportIncomeFromContractsForUI(com) != 0) {
                        String title =
                                usingPredicted ? "Trade contracts (estimated)" : "Trade contracts";
                        tooltip.addSectionHeading(
                                title,
                                market.getFaction().getBaseUIColor(),
                                market.getFaction().getDarkUIColor(),
                                Alignment.MID,
                                5f);
                        tooltip.addSpacer(5f);

                        AoTDTradeContractManager mgr = AoTDTradeContractManager.getInstance();
                        Map<String, AoTDTradeContract> contracts = mgr.getActiveContracts();

                        tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);

                        for (Map.Entry<String, Integer> e : contractShipments.entrySet()) {
                            String contractId = e.getKey();
                            int shipped = e.getValue();
                            if (shipped <= 0) continue;

                            AoTDTradeContract c =
                                    contracts != null ? contracts.get(contractId) : null;

                            String target = "contract";
                            String who = "buyer";
                            float cut = 0f;
                            boolean playerIssued = false;

                            if (c != null) {
                                playerIssued = c.isIssuedByPlayer();

                                // per-commodity contract line
                                AoTDTradeContract.TradeContractData line =
                                        c.getContractData().get(spec.getId());
                                if (line != null) cut = Math.max(0f, line.getCutFromBasePrice());

                                if (c.getFactionId() != null) {
                                    target =
                                            Global.getSector()
                                                    .getFaction(c.getFactionId())
                                                    .getDisplayName();
                                } else {
                                    target = "private buyer";
                                }
                                if (c.getPerson() != null) who = c.getPerson().getNameString();
                            }

                            double creditsDelta;
                            if (c != null) {
                                // uses your unified rule (profit or cost)
                                creditsDelta = c.getMoneyFromMonth(spec.getId(), shipped);
                            } else {
                                // fallback: assume normal buyer pays base
                                creditsDelta = shipped * spec.getBasePrice();
                            }

                            String verb =
                                    creditsDelta >= 0 ? "generating roughly" : "costing roughly";
                            String creditsText = Misc.getDGSCredits(Math.abs((float) creditsDelta));

                            // Optional: show (player-issued) marker
                            String extra =
                                    (playerIssued
                                            ? " (Issued by "
                                                    + Global.getSector()
                                                            .getPlayerPerson()
                                                            .getNameString()
                                                    + ")"
                                            : "");

                            tooltip.addPara(
                                    "%s shipped to %s (%s)%s, " + verb + " %s.",
                                    3f,
                                    Color.ORANGE,
                                    Misc.getWithDGS(shipped),
                                    target,
                                    who,
                                    extra,
                                    creditsText);
                        }
                    }
                }
                tooltip.setBulletedListMode(null);
            }

            // CONTRACT EXPORT (predicted or actual)

        }
    }
}

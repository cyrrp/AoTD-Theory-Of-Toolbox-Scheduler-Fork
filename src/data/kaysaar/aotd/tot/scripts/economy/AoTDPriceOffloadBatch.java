package data.kaysaar.aotd.tot.scripts.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-data price/stockpile work batch.
 *
 * <p>The campaign thread owns capture and commit. Worker threads only call
 * {@link #computeMarket(int)} and therefore never receive or dereference a
 * Starsector API object.</p>
 */
public final class AoTDPriceOffloadBatch {
    public final AoTDRuntimeEpoch.Stamp epochStamp;
    public static final class ModelConfig {
        public final float referenceTradeQuantity;
        public final float normalBuyMin;
        public final float normalBuyMax;
        public final float normalSellMin;
        public final float normalSellMax;
        public final float excessPriceFloor;
        public final float excessSellSpread;
        public final float deficitCenterMin;
        public final float deficitCenterMax;
        public final float illegalDeficitCenterMin;
        public final float illegalDeficitCenterMax;
        public final float maxResellReturnMult;
        public final float greedFraction;
        public final float curveStateStrength;
        public final float customPriceResponse;
        public final float customPriceStockpileDenomMult;
        public final float customPriceDenomMaxReferenceMult;
        public final float pricingStockpileReserveFraction;
        public final float pricingStockpileReserveMinMult;
        public final float minStockpileForPricing;
        public final float minDemandForPricing;
        public final float minStateAmount;

        public ModelConfig(
                float referenceTradeQuantity,
                float normalBuyMin,
                float normalBuyMax,
                float normalSellMin,
                float normalSellMax,
                float excessPriceFloor,
                float excessSellSpread,
                float deficitCenterMin,
                float deficitCenterMax,
                float illegalDeficitCenterMin,
                float illegalDeficitCenterMax,
                float maxResellReturnMult,
                float greedFraction,
                float curveStateStrength,
                float customPriceResponse,
                float customPriceStockpileDenomMult,
                float customPriceDenomMaxReferenceMult,
                float pricingStockpileReserveFraction,
                float pricingStockpileReserveMinMult,
                float minStockpileForPricing,
                float minDemandForPricing,
                float minStateAmount) {
            this.referenceTradeQuantity = referenceTradeQuantity;
            this.normalBuyMin = normalBuyMin;
            this.normalBuyMax = normalBuyMax;
            this.normalSellMin = normalSellMin;
            this.normalSellMax = normalSellMax;
            this.excessPriceFloor = excessPriceFloor;
            this.excessSellSpread = excessSellSpread;
            this.deficitCenterMin = deficitCenterMin;
            this.deficitCenterMax = deficitCenterMax;
            this.illegalDeficitCenterMin = illegalDeficitCenterMin;
            this.illegalDeficitCenterMax = illegalDeficitCenterMax;
            this.maxResellReturnMult = maxResellReturnMult;
            this.greedFraction = greedFraction;
            this.curveStateStrength = curveStateStrength;
            this.customPriceResponse = customPriceResponse;
            this.customPriceStockpileDenomMult = customPriceStockpileDenomMult;
            this.customPriceDenomMaxReferenceMult = customPriceDenomMaxReferenceMult;
            this.pricingStockpileReserveFraction = pricingStockpileReserveFraction;
            this.pricingStockpileReserveMinMult = pricingStockpileReserveMinMult;
            this.minStockpileForPricing = minStockpileForPricing;
            this.minDemandForPricing = minDemandForPricing;
            this.minStateAmount = minStateAmount;
        }
    }

    public static final class CommodityInput {
        public final String commodityId;
        public final float utility;
        public final float rawSupply;
        public final float rawDemand;
        public final float sharedSubmarketLimit;
        public final float officialDeficit;
        public final float officialExcess;
        public final float currentDeficit;
        public final float currentExcess;
        public final float localTradeQuantity;
        public final float demandWrapper;
        public final float supplyWrapper;
        public final boolean illegal;
        public final boolean noDemandOrSupply;
        public final boolean variabilityV0;

        public CommodityInput(
                String commodityId,
                float utility,
                float rawSupply,
                float rawDemand,
                float sharedSubmarketLimit,
                float officialDeficit,
                float officialExcess,
                float currentDeficit,
                float currentExcess,
                float localTradeQuantity,
                float demandWrapper,
                float supplyWrapper,
                boolean illegal,
                boolean noDemandOrSupply,
                boolean variabilityV0) {
            this.commodityId = commodityId;
            this.utility = utility;
            this.rawSupply = rawSupply;
            this.rawDemand = rawDemand;
            this.sharedSubmarketLimit = sharedSubmarketLimit;
            this.officialDeficit = officialDeficit;
            this.officialExcess = officialExcess;
            this.currentDeficit = currentDeficit;
            this.currentExcess = currentExcess;
            this.localTradeQuantity = localTradeQuantity;
            this.demandWrapper = demandWrapper;
            this.supplyWrapper = supplyWrapper;
            this.illegal = illegal;
            this.noDemandOrSupply = noDemandOrSupply;
            this.variabilityV0 = variabilityV0;
        }
    }

    public static final class DemandClassInput {
        public final String demandClass;
        public final float storedDeficitAnchor;
        public final float storedExcessAnchor;
        public final CommodityInput[] commodities;

        public DemandClassInput(
                String demandClass,
                float storedDeficitAnchor,
                float storedExcessAnchor,
                CommodityInput[] commodities) {
            this.demandClass = demandClass;
            this.storedDeficitAnchor = storedDeficitAnchor;
            this.storedExcessAnchor = storedExcessAnchor;
            this.commodities = commodities == null ? new CommodityInput[0] : commodities;
        }
    }

    public static final class MarketInput {
        public final String marketId;
        public final String marketName;
        public final String factionId;
        public final DemandClassInput[] demandClasses;

        public MarketInput(
                String marketId,
                String marketName,
                String factionId,
                DemandClassInput[] demandClasses) {
            this.marketId = marketId;
            this.marketName = marketName;
            this.factionId = factionId;
            this.demandClasses = demandClasses == null ? new DemandClassInput[0] : demandClasses;
        }
    }

    public static final class CalculatorModel {
        public float targetSellMult;
        public float targetBuyMult;
        public float blankSellMult;
        public float blankBuyMult;
        public float minSellMult;
        public float maxSellMult;
        public float minBuyMult;
        public float maxBuyMult;
        public float neutralStockpileUtility;
        public int officialStateMode;
        public float officialStateUtility;
        public float officialStatePressureDenom;
        public float stateStartSellMult;
        public float stateStartBuyMult;
        public float stateExtremeSellMult;
        public float stateExtremeBuyMult;
    }

    public static final class CommodityResult {
        public int stocks;
        public float pricingStockpile;
        public float demandCurve;
        public float greed;
        public boolean noDemandOrSupply;
        public boolean variabilityV0;
        public float v0SellMult;
        public float v0BuyMult;
        public final CalculatorModel model = new CalculatorModel();
    }

    public static final class DemandClassResult {
        public int anchorMode;
        public float anchorValue;
        public CommodityResult[] commodities;
    }

    public static final class MarketResult {
        public DemandClassResult[] demandClasses;
        public Throwable failure;
        public long computeNanos;
    }

    private final ModelConfig config;
    private final List<MarketInput> marketInputs = new ArrayList<>();
    private volatile MarketResult[] results = new MarketResult[0];

    public AoTDPriceOffloadBatch(ModelConfig config) {
        this(config, AoTDRuntimeEpoch.captureBatch("price-offload"));
    }

    public AoTDPriceOffloadBatch(
            ModelConfig config, AoTDRuntimeEpoch.Stamp epochStamp) {
        if (config == null) throw new IllegalArgumentException("config");
        if (epochStamp == null) throw new IllegalArgumentException("epochStamp");
        this.config = config;
        this.epochStamp = epochStamp;
    }

    public int addMarket(MarketInput input) {
        if (input == null) throw new IllegalArgumentException("input");
        marketInputs.add(input);
        return marketInputs.size() - 1;
    }

    public void freeze() {
        if (results.length != marketInputs.size()) {
            results = new MarketResult[marketInputs.size()];
        }
    }

    public int size() {
        return marketInputs.size();
    }

    public MarketInput inputAt(int index) {
        return marketInputs.get(index);
    }

    public MarketResult resultAt(int index) {
        MarketResult[] local = results;
        return index < 0 || index >= local.length ? null : local[index];
    }

    public void computeMarket(int index) {
        if (!AoTDRuntimeEpoch.isCurrent(epochStamp)) return;
        long started = System.nanoTime();
        MarketResult result;
        try {
            result = computeMarket(config, marketInputs.get(index));
        } catch (Exception failure) {
            result = new MarketResult();
            result.failure = failure;
        }
        result.computeNanos = Math.max(0L, System.nanoTime() - started);
        results[index] = result;
    }

    private static MarketResult computeMarket(ModelConfig c, MarketInput market) {
        MarketResult result = new MarketResult();
        result.demandClasses = new DemandClassResult[market.demandClasses.length];
        for (int classIndex = 0; classIndex < market.demandClasses.length; classIndex++) {
            result.demandClasses[classIndex] = computeDemandClass(c, market, market.demandClasses[classIndex]);
        }
        return result;
    }

    private static DemandClassResult computeDemandClass(
            ModelConfig c, MarketInput market, DemandClassInput input) {
        DemandClassResult output = new DemandClassResult();
        output.commodities = new CommodityResult[input.commodities.length];

        float classStockpileUtility = 0f;
        float rawDeficitUtility = 0f;
        float rawExcessUtility = 0f;
        float deficitUtility = 0f;
        float excessUtility = 0f;
        float localTradeUtility = 0f;

        float[] realStocks = new float[input.commodities.length];
        float[] pricingStockpiles = new float[input.commodities.length];

        for (int i = 0; i < input.commodities.length; i++) {
            CommodityInput commodity = input.commodities[i];
            float floor = Math.max(1f, c.minStockpileForPricing);
            float realStock = Math.max(floor, Math.max(0f, commodity.rawSupply));
            float pricingBasis = Math.max(realStock, Math.max(0f, commodity.sharedSubmarketLimit));
            pricingBasis = Math.max(pricingBasis, Math.max(0f, commodity.rawDemand));
            pricingBasis = Math.max(pricingBasis, c.referenceTradeQuantity);

            float pricingStockpile = pricingBasis;
            if (commodity.currentExcess > commodity.currentDeficit
                    && commodity.currentExcess >= c.minStateAmount) {
                pricingStockpile = pricingBasis + commodity.currentExcess;
            } else if (commodity.currentDeficit > commodity.currentExcess
                    && commodity.currentDeficit >= c.minStateAmount) {
                pricingStockpile = Math.max(floor, pricingBasis - commodity.currentDeficit);
            }

            realStocks[i] = realStock;
            pricingStockpiles[i] = pricingStockpile;

            float utility = Math.max(0.0001f, commodity.utility);
            classStockpileUtility += Math.max(0f, pricingStockpile) * utility;
            rawDeficitUtility += Math.max(commodity.officialDeficit, commodity.currentDeficit) * utility;
            rawExcessUtility += Math.max(commodity.officialExcess, commodity.currentExcess) * utility;
            localTradeUtility += commodity.localTradeQuantity * utility;

            if (commodity.currentDeficit > commodity.currentExcess
                    && commodity.currentDeficit >= c.minStateAmount) {
                deficitUtility += commodity.currentDeficit * utility;
            } else if (commodity.currentExcess >= commodity.currentDeficit
                    && commodity.currentExcess >= c.minStateAmount) {
                excessUtility += commodity.currentExcess * utility;
            }
        }

        boolean localPlayerBoughtFromMarket = localTradeUtility < -0.0001f;
        boolean localPlayerSoldToMarket = localTradeUtility > 0.0001f;
        if (rawExcessUtility <= 0.0001f
                && input.storedExcessAnchor > 0.0001f
                && localPlayerBoughtFromMarket) {
            rawExcessUtility = input.storedExcessAnchor;
        }
        if (rawDeficitUtility <= 0.0001f
                && input.storedDeficitAnchor > 0.0001f
                && localPlayerSoldToMarket) {
            rawDeficitUtility = input.storedDeficitAnchor;
        }

        boolean activeDeficit = deficitUtility > excessUtility
                && deficitUtility >= c.minStateAmount;
        boolean activeExcess = excessUtility >= deficitUtility
                && excessUtility >= c.minStateAmount;
        boolean anchoredDeficit = !activeDeficit && !activeExcess
                && rawDeficitUtility > rawExcessUtility
                && rawDeficitUtility >= c.minStateAmount;
        boolean anchoredExcess = !activeDeficit && !activeExcess
                && rawExcessUtility >= rawDeficitUtility
                && rawExcessUtility >= c.minStateAmount;

        int mode = 0;
        boolean hasDeficit = false;
        boolean hasExcess = false;
        float pressureDenom = c.referenceTradeQuantity;
        float classDemandUtility;
        float pressure;

        if (activeDeficit || anchoredDeficit) {
            mode = 1;
            hasDeficit = true;
            float current = Math.max(rawDeficitUtility, deficitUtility);
            float stored = Math.max(input.storedDeficitAnchor, current);
            output.anchorMode = 1;
            output.anchorValue = stored;
            pressureDenom = Math.max(c.referenceTradeQuantity, stored);
            classDemandUtility = classStockpileUtility + deficitUtility;
            pressure = clamp(deficitUtility / pressureDenom, 0f, 1f);
        } else if (activeExcess || anchoredExcess) {
            mode = -1;
            hasExcess = true;
            float current = Math.max(rawExcessUtility, excessUtility);
            float stored = Math.max(input.storedExcessAnchor, current);
            output.anchorMode = -1;
            output.anchorValue = stored;
            pressureDenom = Math.max(c.referenceTradeQuantity, stored);
            classDemandUtility = Math.max(1f, classStockpileUtility - excessUtility);
            pressure = clamp(excessUtility / pressureDenom, 0f, 1f);
        } else {
            classDemandUtility = Math.max(1f, classStockpileUtility);
            pressure = 0f;
        }

        float curveTargetUtility = classStockpileUtility
                + (classDemandUtility - classStockpileUtility) * c.curveStateStrength;
        float demandCurve = curveTargetUtility
                + c.minStockpileForPricing
                - c.minDemandForPricing;
        demandCurve = Math.max(1f, demandCurve);

        for (int i = 0; i < input.commodities.length; i++) {
            CommodityInput commodity = input.commodities[i];
            CommodityResult commodityResult = new CommodityResult();
            output.commodities[i] = commodityResult;

            commodityResult.stocks = Math.round(realStocks[i]);
            commodityResult.pricingStockpile = pricingStockpiles[i];
            commodityResult.demandCurve = demandCurve;
            commodityResult.greed = Math.max(1f, demandCurve * c.greedFraction);
            commodityResult.noDemandOrSupply = commodity.noDemandOrSupply;
            commodityResult.variabilityV0 = commodity.variabilityV0;

            float buyRoll = stableRoll(market, commodity.commodityId + "_buy");
            float sellRoll = stableRoll(market, commodity.commodityId + "_sell");
            float blankBuy = lerp(c.normalBuyMin, c.normalBuyMax, buyRoll);
            float blankSell = lerp(c.normalSellMin, c.normalSellMax, sellRoll);

            float finalSell;
            float finalBuy;
            if (hasDeficit) {
                float deficitMax = deficitCenterMax(c, market, commodity);
                float deficitStart = commodity.illegal
                        ? c.illegalDeficitCenterMin : c.deficitCenterMin;
                float center = lerp(deficitStart, deficitMax, pressure);
                finalSell = center;
                finalBuy = center;
            } else if (hasExcess) {
                finalBuy = lerp(blankBuy, c.excessPriceFloor, pressure);
                finalSell = lerp(blankSell, c.excessPriceFloor + c.excessSellSpread, pressure);
            } else {
                finalSell = blankSell;
                finalBuy = blankBuy;
            }

            float minSell;
            float maxSell;
            float minBuy;
            float maxBuy;
            if (hasDeficit) {
                float deficitMax = deficitCenterMax(c, market, commodity);
                minSell = c.excessPriceFloor;
                maxSell = deficitMax;
                minBuy = c.normalBuyMin;
                maxBuy = Math.max(deficitMax / c.maxResellReturnMult,
                        commodity.illegal ? c.illegalDeficitCenterMax : 2f);
            } else if (hasExcess) {
                minSell = 0.25f;
                maxSell = c.normalSellMax;
                minBuy = c.excessPriceFloor;
                maxBuy = Math.max(c.normalBuyMax, 1.25f);
            } else {
                minSell = 0.25f;
                maxSell = 1.60f;
                minBuy = c.normalBuyMin;
                maxBuy = 2.50f;
            }

            float demandWrapper = safeWrapper(commodity.demandWrapper);
            float supplyWrapper = safeWrapper(commodity.supplyWrapper);
            CalculatorModel model = commodityResult.model;
            model.targetSellMult = finalSell / demandWrapper;
            model.targetBuyMult = finalBuy / supplyWrapper;
            model.blankSellMult = blankSell / demandWrapper;
            model.blankBuyMult = blankBuy / supplyWrapper;
            model.minSellMult = minSell / demandWrapper;
            model.maxSellMult = maxSell / demandWrapper;
            model.minBuyMult = minBuy / supplyWrapper;
            model.maxBuyMult = maxBuy / supplyWrapper;
            model.neutralStockpileUtility = classStockpileUtility;
            model.officialStateMode = mode;
            model.officialStateUtility = mode < 0 ? excessUtility : (mode > 0 ? deficitUtility : 0f);
            model.officialStatePressureDenom = pressureDenom;

            if (hasDeficit) {
                float start = commodity.illegal
                        ? c.illegalDeficitCenterMin : c.deficitCenterMin;
                model.stateStartSellMult = start / demandWrapper;
                model.stateStartBuyMult = start / supplyWrapper;
                float max = deficitCenterMax(c, market, commodity);
                model.stateExtremeSellMult = max / demandWrapper;
                model.stateExtremeBuyMult = (max / c.maxResellReturnMult) / supplyWrapper;
            } else if (hasExcess) {
                model.stateStartSellMult = blankSell / demandWrapper;
                model.stateStartBuyMult = blankBuy / supplyWrapper;
                model.stateExtremeSellMult = (c.excessPriceFloor + c.excessSellSpread) / demandWrapper;
                model.stateExtremeBuyMult = c.excessPriceFloor / supplyWrapper;
            } else {
                model.stateStartSellMult = blankSell / demandWrapper;
                model.stateStartBuyMult = blankBuy / supplyWrapper;
                model.stateExtremeSellMult = blankSell / demandWrapper;
                model.stateExtremeBuyMult = blankBuy / supplyWrapper;
            }

            commodityResult.v0SellMult = finalSell;
            commodityResult.v0BuyMult = finalBuy;
        }

        return output;
    }

    private static float deficitCenterMax(
            ModelConfig c, MarketInput market, CommodityInput commodity) {
        if (!commodity.illegal) return c.deficitCenterMax;
        float roll = stableRoll(market, commodity.commodityId + "_illegal_deficit_max");
        return lerp(c.illegalDeficitCenterMin, c.illegalDeficitCenterMax, roll);
    }

    private static float stableRoll(MarketInput market, String commodityId) {
        String seedString = String.valueOf(market.marketId) + "|"
                + String.valueOf(market.marketName) + "|"
                + String.valueOf(market.factionId) + "|" + commodityId;
        int seed = seedString.hashCode();
        seed ^= (seed << 13);
        seed ^= (seed >>> 17);
        seed ^= (seed << 5);

        // Exact first nextFloat() of new java.util.Random(seed), without allocation.
        long mask = (1L << 48) - 1L;
        long state = (((long) seed) ^ 0x5DEECE66DL) & mask;
        state = (state * 0x5DEECE66DL + 0xBL) & mask;
        int next24 = (int) (state >>> 24);
        return next24 / ((float) (1 << 24));
    }

    private static float safeWrapper(float wrapper) {
        return Float.isFinite(wrapper) && wrapper > 0f ? wrapper : 1f;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp(t, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

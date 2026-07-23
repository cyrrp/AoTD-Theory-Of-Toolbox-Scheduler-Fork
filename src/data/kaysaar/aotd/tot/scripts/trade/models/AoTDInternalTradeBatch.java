package data.kaysaar.aotd.tot.scripts.trade.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure-data internal-trade batch. Campaign thread owns capture and commit. */
public final class AoTDInternalTradeBatch {
    public static final class MarketInput {
        public final String marketId;
        public final float weight;
        public final boolean eligible;
        public final LinkedHashMap<String, Integer> netProductionValues;

        public MarketInput(String marketId, float weight, boolean eligible,
                           Map<String, Integer> netProductionValues) {
            this.marketId = marketId;
            this.weight = weight;
            this.eligible = eligible;
            this.netProductionValues = new LinkedHashMap<>();
            if (netProductionValues != null) this.netProductionValues.putAll(netProductionValues);
        }
    }

    public static final class FactionInput {
        public final String factionId;
        public final MarketInput[] markets;

        public FactionInput(String factionId, MarketInput[] markets) {
            this.factionId = factionId;
            this.markets = markets == null ? new MarketInput[0] : markets;
        }
    }

    public static final class MarketResult {
        public final String marketId;
        public final LinkedHashMap<String, Integer> internalSent = new LinkedHashMap<>();
        public final LinkedHashMap<String, Integer> internalReceived = new LinkedHashMap<>();
        public final LinkedHashMap<String, Integer> remainingNet = new LinkedHashMap<>();

        MarketResult(MarketInput input) {
            marketId = input.marketId;
            remainingNet.putAll(input.netProductionValues);
        }
    }

    public static final class FactionResult {
        public String factionId;
        public MarketResult[] markets;
        public Throwable failure;
        public long computeNanos;
    }

    private static final class MarketAmount {
        final int marketIndex;
        int amount;
        final float weight;
        MarketAmount(int marketIndex, int amount, float weight) {
            this.marketIndex = marketIndex;
            this.amount = amount;
            this.weight = weight;
        }
    }

    private static final class Bucket {
        final ArrayList<MarketAmount> exporters = new ArrayList<>();
        final ArrayList<MarketAmount> importers = new ArrayList<>();
        int supply;
        int need;
    }

    private final List<FactionInput> inputs = new ArrayList<>();
    private volatile FactionResult[] results = new FactionResult[0];

    public int addFaction(FactionInput input) {
        if (input == null) throw new IllegalArgumentException("input");
        inputs.add(input);
        return inputs.size() - 1;
    }

    public void freeze() {
        if (results.length != inputs.size()) results = new FactionResult[inputs.size()];
    }

    public int size() { return inputs.size(); }
    public FactionInput inputAt(int index) { return inputs.get(index); }
    public FactionResult resultAt(int index) {
        FactionResult[] local = results;
        return index < 0 || index >= local.length ? null : local[index];
    }

    public void computeFaction(int index) {
        long started = System.nanoTime();
        FactionResult result;
        try {
            result = compute(inputs.get(index));
        } catch (Exception failure) {
            result = new FactionResult();
            result.factionId = inputs.get(index).factionId;
            result.failure = failure;
        }
        result.computeNanos = Math.max(0L, System.nanoTime() - started);
        results[index] = result;
    }

    private static FactionResult compute(FactionInput input) {
        FactionResult output = new FactionResult();
        output.factionId = input.factionId;
        output.markets = new MarketResult[input.markets.length];
        for (int i = 0; i < input.markets.length; i++) output.markets[i] = new MarketResult(input.markets[i]);

        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        for (int marketIndex = 0; marketIndex < input.markets.length; marketIndex++) {
            MarketInput market = input.markets[marketIndex];
            if (!market.eligible || market.netProductionValues.isEmpty()) continue;
            for (Map.Entry<String, Integer> entry : market.netProductionValues.entrySet()) {
                int net = entry.getValue() == null ? 0 : entry.getValue();
                if (net == 0) continue;
                Bucket bucket = buckets.computeIfAbsent(entry.getKey(), k -> new Bucket());
                if (net > 0) {
                    bucket.exporters.add(new MarketAmount(marketIndex, net, market.weight));
                    bucket.supply += net;
                } else {
                    int need = -net;
                    bucket.importers.add(new MarketAmount(marketIndex, need, market.weight));
                    bucket.need += need;
                }
            }
        }

        for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
            Bucket bucket = entry.getValue();
            if (bucket.supply <= 0 || bucket.need <= 0) continue;
            bucket.exporters.sort((a, b) -> Float.compare(b.weight, a.weight));
            bucket.importers.sort((a, b) -> Float.compare(b.weight, a.weight));
            int exporterIndex = 0;
            int importerIndex = 0;
            while (exporterIndex < bucket.exporters.size() && importerIndex < bucket.importers.size()) {
                MarketAmount exporter = bucket.exporters.get(exporterIndex);
                MarketAmount importer = bucket.importers.get(importerIndex);
                int moved = Math.min(exporter.amount, importer.amount);
                if (moved <= 0) {
                    if (exporter.amount <= 0) exporterIndex++;
                    if (importer.amount <= 0) importerIndex++;
                    continue;
                }
                applySent(output.markets[exporter.marketIndex], entry.getKey(), moved);
                applyReceived(output.markets[importer.marketIndex], entry.getKey(), moved);
                exporter.amount -= moved;
                importer.amount -= moved;
                if (exporter.amount <= 0) exporterIndex++;
                if (importer.amount <= 0) importerIndex++;
            }
        }
        return output;
    }

    private static void applySent(MarketResult market, String commodityId, int amount) {
        int available = market.remainingNet.getOrDefault(commodityId, 0);
        int moved = Math.min(amount, Math.max(0, available));
        if (moved <= 0) return;
        market.internalSent.merge(commodityId, moved, Integer::sum);
        int left = available - moved;
        if (left == 0) market.remainingNet.remove(commodityId); else market.remainingNet.put(commodityId, left);
    }

    private static void applyReceived(MarketResult market, String commodityId, int amount) {
        int signedNeed = market.remainingNet.getOrDefault(commodityId, 0);
        int moved = Math.min(amount, Math.max(0, -signedNeed));
        if (moved <= 0) return;
        market.internalReceived.merge(commodityId, moved, Integer::sum);
        int left = -signedNeed - moved;
        if (left == 0) market.remainingNet.remove(commodityId); else market.remainingNet.put(commodityId, -left);
    }
}

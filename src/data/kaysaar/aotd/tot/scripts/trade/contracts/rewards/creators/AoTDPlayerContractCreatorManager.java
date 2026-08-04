package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators;

import java.util.LinkedHashMap;

public class AoTDPlayerContractCreatorManager {
    public static LinkedHashMap<String, PlayerContractCreatorAPI> contractCreators =
            new LinkedHashMap<>();

    public static void addCreator(String creatorId, PlayerContractCreatorAPI creator) {
        contractCreators.put(creatorId, creator);
    }

    public static PlayerContractCreatorAPI getCreator(String creatorId) {
        return contractCreators.get(creatorId);
    }

    public static LinkedHashMap<String, PlayerContractCreatorAPI> getRewardsCopy() {
        return new LinkedHashMap<>(contractCreators);
    }

    public static LinkedHashMap<String, PlayerContractCreatorAPI> getContractCreators() {
        return contractCreators;
    }
}

package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import java.util.LinkedHashMap;

public class AoTDContractRewardCreatorManager {
    public static LinkedHashMap<String, BaseContractRewardCreator> rewards = new LinkedHashMap<>();

    public static void addCreator(String creatorId, BaseContractRewardCreator creator) {
        rewards.put(creatorId, creator);
    }

    public static BaseContractRewardCreator getCreator(String creatorId) {
        return rewards.get(creatorId);
    }

    public static LinkedHashMap<String, BaseContractRewardCreator> getRewardsCopy() {
        return new LinkedHashMap<>(rewards);
    }

    public static void pickAdditionalRewardsForContract(
            AoTDTradeContract contract, int amountOfRewards) {
        if (contract == null || amountOfRewards <= 0) return;

        WeightedRandomPicker<BaseContractRewardCreator> picker = new WeightedRandomPicker<>();

        for (BaseContractRewardCreator c : rewards.values()) {
            if (c == null) continue;
            c.getAlreadyTakenIds().clear();
            c.resetPicks();
        }

        for (BaseContractRewardCreator c : rewards.values()) {
            if (c == null) continue;
            if (!c.doesContractMeetCriteria(contract)) continue;
            if (AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel()
                    < c.getReqMinLevelForRewardToGenerate()) continue;
            if (c.getProbability() <= 0f) continue;
            if (!c.canPickMore()) continue;

            float gate = c.getPickGateChance(contract);
            if (gate <= 0f) continue;
            if (gate < 1f && Misc.random.nextFloat() > gate) continue; // << extra rarity gate

            picker.add(c, c.getProbability());
        }

        int picked = 0;
        while (picked < amountOfRewards && !picker.isEmpty()) {
            BaseContractRewardCreator chosen = picker.pickAndRemove();
            if (chosen == null) break;

            if (!chosen.canPickMore()) continue;

            chosen.addNewRewardToContract(contract);
            chosen.markPicked();
            picked++;

            // re-add only if it can repeat AND still under cap
            if (chosen.canRewardTypeRepeat()
                    && chosen.getProbability() > 0f
                    && chosen.canPickMore()) {
                picker.add(chosen, chosen.getProbability());
            }
        }
    }
}

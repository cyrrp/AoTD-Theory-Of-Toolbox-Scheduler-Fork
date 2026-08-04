package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.rewards;

import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.AICoreReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.BaseContractRewardCreator;

public class GenericAICoreCreator extends BaseContractRewardCreator {

    @Override
    public int getReqMinLevelForRewardToGenerate() {
        return 7;
    }

    @Override
    public int getMaxPicks() {
        return 1;
    }

    @Override
    public float getProbability() {
        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        float base = 10f;
        base += Math.max(0, lvl - getReqMinLevelForRewardToGenerate()) * 2.5f;
        return base;
    }

    @Override
    public float getPickGateChance(AoTDTradeContract contract) {
        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        float chance = 0.06f + 0.01f * Math.max(0, lvl - 5); // 6% -> 16%
        return Math.min(chance, 0.16f);
    }

    @Override
    public boolean canRewardTypeRepeat() {
        return false;
    }

    @Override
    public boolean doesContractMeetCriteria(AoTDTradeContract contract) {
        if (contract == null) return false;
        // AI cores only for private (your note)
        return contract.isPrivate() && !contract.isIssuedByPlayer();
    }

    @Override
    public void addNewRewardToContract(AoTDTradeContract contract) {
        if (contract == null) return;
        if (!canPickMore()) return;

        String key = "ai_cores";
        if (getAlreadyTakenIds().contains(key)) return;

        // AICoreReward constructor should decide amounts by level internally
        contract.addReward(key, new AICoreReward());

        getAlreadyTakenIds().add(key);
        markPicked();
    }
}

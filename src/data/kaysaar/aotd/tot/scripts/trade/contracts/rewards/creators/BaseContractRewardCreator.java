package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators;

import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import java.util.LinkedHashSet;

public abstract class BaseContractRewardCreator {
    // Creators are ONLY used for generic contracts that can appear on contract browser!
    public LinkedHashSet<String> alreadyTakenIds = new LinkedHashSet<>();

    public LinkedHashSet<String> getAlreadyTakenIds() {
        return alreadyTakenIds;
    }

    public float getPickGateChance(AoTDTradeContract contract) {
        return 1f; // default: always allowed into picker
    }

    public int pickedSoFar = 0;

    public int getMaxPicks() {
        return 2;
    }

    public void resetPicks() {
        pickedSoFar = 0;
    }

    public boolean canPickMore() {
        return pickedSoFar < getMaxPicks();
    }

    protected void markPicked() {
        pickedSoFar++;
    }

    public int getPickedSoFar() {
        return pickedSoFar;
    }

    public float getProbability() {
        return 1f;
    }

    public int getReqMinLevelForRewardToGenerate() {
        return 1;
    }

    public boolean doesContractMeetCriteria(AoTDTradeContract contract) {
        return true;
    }

    public void addNewRewardToContract(AoTDTradeContract contract) {

        // Remember to put id of taken stuff if you dont wanna generate duplicates , list will
        // always be cleared before static method is called :
        // AoTDContractRewardCreatorManager.pickRewardsForContract()
    }

    public boolean canRewardTypeRepeat() {
        return true;
    }
}

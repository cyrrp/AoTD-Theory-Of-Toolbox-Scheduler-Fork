package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.rewards;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.FactionReputationReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.BaseContractRewardCreator;

public class GenericFactionReputationCreator extends BaseContractRewardCreator {

    @Override
    public int getReqMinLevelForRewardToGenerate() {
        return 5;
    }

    @Override
    public int getMaxPicks() {
        // don't spam rep rewards
        return 1;
    }

    @Override
    public float getProbability() {
        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        return 12f + Math.max(0, lvl - getReqMinLevelForRewardToGenerate()) * 2f;
    }

    @Override
    public boolean canRewardTypeRepeat() {
        return false; // per contract browser roll: at most once
    }

    @Override
    public boolean doesContractMeetCriteria(AoTDTradeContract contract) {
        if (contract == null) return false;
        // faction rep reward only makes sense for faction-issued (not private, not player-issued)
        return !contract.isPrivate() && !contract.isIssuedByPlayer();
    }

    @Override
    public void addNewRewardToContract(AoTDTradeContract contract) {
        if (contract == null) return;
        if (!canPickMore()) return;

        FactionAPI fac = contract.getFaction();
        if (fac == null) return;
        if (Factions.PLAYER.equals(fac.getId())) return;

        String key = "fac_rep:" + fac.getId();
        if (getAlreadyTakenIds().contains(key)) return;

        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        int plus = 5 + Math.max(0, lvl - 5) * 2; // lvl5=5, lvl15=25
        int minus = 8 + Math.max(0, lvl - 5) * 3; // harsher downside

        contract.addReward(key, new FactionReputationReward(fac.getId(), plus, minus));

        getAlreadyTakenIds().add(key);
        markPicked();
    }
}

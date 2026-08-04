package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.rewards;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.SpecialItemReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.BaseContractRewardCreator;
import java.util.LinkedHashSet;

public class GenericSpecialItemCreator extends BaseContractRewardCreator {

    // editable pools
    public static final LinkedHashSet<String> RARE_ITEMS = new LinkedHashSet<>();
    public static final LinkedHashSet<String> VERY_RARE_ITEMS = new LinkedHashSet<>();

    @Override
    public float getPickGateChance(AoTDTradeContract contract) {
        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        // still rare at 15, just slightly less ridiculous
        // lvl5: 0.5%, lvl15: 1.5%
        float chance = 0.005f + 0.001f * Math.max(0, lvl - 5);
        return Math.min(chance, 0.015f);
    }

    static {
        // Rare
        RARE_ITEMS.add("corrupted_nanoforge");
        RARE_ITEMS.add("synchrotron");
        RARE_ITEMS.add("orbital_fusion_lamp");
        RARE_ITEMS.add("mantle_bore");
        RARE_ITEMS.add("catalytic_core");
        RARE_ITEMS.add("soil_nanites");
        RARE_ITEMS.add("biofactory_embryo");
        RARE_ITEMS.add("fullerene_spool");
        RARE_ITEMS.add("plasma_dynamo");
        RARE_ITEMS.add("cryoarithmetic_engine");
        RARE_ITEMS.add("drone_replicator");
        RARE_ITEMS.add("dealmaker_holosuite");

        // Very rare / top end
        VERY_RARE_ITEMS.add("pristine_nanoforge");
        VERY_RARE_ITEMS.add("coronal_portal");
    }

    @Override
    public int getReqMinLevelForRewardToGenerate() {
        return 9;
    }

    @Override
    public int getMaxPicks() {
        return 1;
    }

    @Override
    public float getProbability() {
        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        // highest rarity -> low weight, ramps slowly
        return 2f + Math.max(0, lvl - getReqMinLevelForRewardToGenerate()) * 0.8f;
    }

    @Override
    public boolean canRewardTypeRepeat() {
        return false;
    }

    @Override
    public boolean doesContractMeetCriteria(AoTDTradeContract contract) {
        if (contract == null) return false;
        // allow both private + faction, but you can restrict if you want
        return true;
    }

    @Override
    public void addNewRewardToContract(AoTDTradeContract contract) {
        if (contract == null) return;
        if (!canPickMore()) return;

        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();

        // chance to roll very rare increases with level (still rare)
        float t = (lvl - 9f) / 6f; // 9..15
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        float veryRareChance = 0.05f + 0.20f * t; // 5% -> 25%

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        boolean useVeryRare = Misc.random.nextFloat() < veryRareChance;

        if (useVeryRare && !VERY_RARE_ITEMS.isEmpty()) {
            for (String id : VERY_RARE_ITEMS) {
                if (id == null) continue;
                if (Global.getSettings().getSpecialItemSpec(id) == null) continue;
                String key = "spitem:" + id;
                if (getAlreadyTakenIds().contains(key)) continue;
                picker.add(id, 1f);
            }
        }

        if (picker.isEmpty() && !RARE_ITEMS.isEmpty()) {
            for (String id : RARE_ITEMS) {
                if (id == null) continue;
                if (Global.getSettings().getSpecialItemSpec(id) == null) continue;
                String key = "spitem:" + id;
                if (getAlreadyTakenIds().contains(key)) continue;
                picker.add(id, 1f);
            }
        }

        String pick = picker.pick();
        if (pick == null) return;

        String rewardKey = "spitem:" + pick;
        // data is null unless specified (your note) -> pass null
        contract.addReward(rewardKey, new SpecialItemReward(pick, null));

        getAlreadyTakenIds().add(rewardKey);
        markPicked();
    }
}

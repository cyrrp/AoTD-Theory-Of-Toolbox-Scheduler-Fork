package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.rewards;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract.BlueprintReward;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.BaseContractRewardCreator;

public class GenericBlueprintCreator extends BaseContractRewardCreator {

    @Override
    public int getReqMinLevelForRewardToGenerate() {
        return 5;
    }

    @Override
    public float getPickGateChance(AoTDTradeContract contract) {
        int lvl = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        float chance = 0.03f + 0.01f * Math.max(0, lvl - 5); // 3% -> 13%
        return Math.min(chance, 0.13f);
    }

    @Override
    public int getMaxPicks() {
        if (AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel() < 8) {
            return 1;
        } else if (AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel()
                < 10) {
            return 2;
        }
        return 3;
    }

    @Override
    public float getProbability() {
        float baseProb = 20;
        int currLevel = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        int aditional = currLevel - getReqMinLevelForRewardToGenerate();
        baseProb += (5 * aditional);
        return baseProb;
    }

    @Override
    public boolean canRewardTypeRepeat() {
        return true;
    }

    @Override
    public void addNewRewardToContract(AoTDTradeContract contract) {
        if (contract == null) return;

        int level = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        FactionAPI fac = contract.getFaction();
        if (fac == null) return;

        WeightedRandomPicker<Pair<BlueprintReward.BlueprintData, String>> picker =
                new WeightedRandomPicker<>();

        // --- Fighters (always eligible from lvl 5; keep some baseline even if <5) ---
        float fighterMult = (level >= 5) ? 2.5f : 1f;
        for (String wingId : fac.getKnownFighters()) {
            if (wingId == null) continue;
            String takenKey = "FIGHTER:" + wingId;
            if (getAlreadyTakenIds().contains(takenKey)) continue;
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
            if (spec.hasTag(Tags.RESTRICTED)) continue;

            picker.add(new Pair<>(BlueprintReward.BlueprintData.FIGHTER, wingId), 1f * fighterMult);
        }

        // --- Weapons ---
        for (String weaponId : fac.getKnownWeapons()) {
            if (weaponId == null) continue;
            WeaponSpecAPI w = Global.getSettings().getWeaponSpec(weaponId);
            if (w == null) continue;
            if (w.hasTag(Tags.RESTRICTED)) continue;
            if (w.getType().equals(WeaponAPI.WeaponType.BUILT_IN)) continue;
            if (w.getType().equals(WeaponAPI.WeaponType.LAUNCH_BAY)) continue;
            if (w.getType().equals(WeaponAPI.WeaponType.DECORATIVE)) continue;
            if (w.getType().equals(WeaponAPI.WeaponType.SYSTEM)) continue;
            if (w.getType().equals(WeaponAPI.WeaponType.STATION_MODULE)) continue;
            float mult = 1f; // baseline: can always roll
            switch (w.getSize()) {
                case SMALL:
                    if (level >= 5) mult += 3f;
                    break;
                case MEDIUM:
                    if (level >= 7) mult += 3f;
                    break;
                case LARGE:
                    if (level >= 9) mult += 3f;
                    break;
            }

            String takenKey = "WEAPON:" + weaponId;
            if (getAlreadyTakenIds().contains(takenKey)) continue;
            picker.add(new Pair<>(BlueprintReward.BlueprintData.WEAPON, weaponId), mult);
        }

        // --- Ships ---
        for (String hullId : fac.getKnownShips()) {
            if (hullId == null) continue;
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);

            if (spec == null) continue;
            if (spec.hasTag(Tags.RESTRICTED)) continue;

            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.MODULE)) continue;
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) continue;
            if (spec.getHullSize().equals(ShipAPI.HullSize.FIGHTER)) continue;
            float mult = 1f; // baseline: can always roll
            switch (spec.getHullSize()) {
                case FRIGATE:
                    if (level >= 5) mult += 3f;
                    break;
                case DESTROYER:
                    if (level >= 7) mult += 3f;
                    break;
                case CRUISER:
                    if (level >= 7) mult += 0.75f; // small chance at 7
                    if (level >= 9) mult += 3f;
                    break;
                case CAPITAL_SHIP:
                    if (level >= 9) mult += 3f;
                    break;
                default:
                    break;
            }

            String takenKey = "SHIP:" + spec.getBaseHullId();
            if (getAlreadyTakenIds().contains(takenKey)) continue;
            picker.add(new Pair<>(BlueprintReward.BlueprintData.SHIP, spec.getBaseHullId()), mult);
        }

        Pair<BlueprintReward.BlueprintData, String> pick = picker.pick();
        if (pick == null) return;

        BlueprintReward.BlueprintData type = pick.one;
        String id = pick.two;

        String takenKey = type.name() + ":" + id;
        getAlreadyTakenIds().add(takenKey);

        String rewardKey = "bp_" + takenKey;
        contract.addReward(rewardKey, new BlueprintReward(id, type));
    }
}

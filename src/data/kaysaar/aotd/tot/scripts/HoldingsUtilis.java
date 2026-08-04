package data.kaysaar.aotd.tot.scripts;

import ashlib.data.plugins.ui.models.DropDownButton;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.ui.starsystems.components.StarSystemHoldingDropDown;
import data.kaysaar.aotd.tot.ui.warehouses.components.WarehouseDropDown;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class HoldingsUtilis {
    public static ArrayList<StarSystemAPI> getSystemsWithPlayerFactionColonies() {
        ArrayList<StarSystemAPI> systems = new ArrayList<>();
        for (StarSystemAPI starSystem : Global.getSector().getStarSystems()) {
            if (starSystem.getCenter() == null) continue;
            if (Global.getSector()
                    .getEconomy()
                    .getMarkets(starSystem.getCenter().getContainingLocation())
                    .stream()
                    .anyMatch(
                            x ->
                                    (x.getFaction() != null && x.getFaction().isPlayerFaction())
                                            || x.isPlayerOwned())) {
                systems.add(starSystem);
            }
        }
        return systems;
    }

    public static ArrayList<MarketAPI> getFactionMarketsInSystem(
            FactionAPI faction, StarSystemAPI system) {
        ArrayList<MarketAPI> systems = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system)) {
            if (market.getFaction() != null
                    && market.getFaction().getId().equals(faction.getId())) {
                systems.add(market);
            } else if (faction.isPlayerFaction() && market.isPlayerOwned()) {
                systems.add(market);
            }
        }
        return systems;
    }

    public static ArrayList<MarketAPI> getStorageMarkets() {
        ArrayList<MarketAPI> systems = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.isPlayerOwned()
                    && Misc.playerHasStorageAccess(market)
                    && Misc.getStorageTotalValue(market) > 0) {
                systems.add(market);
            }
            if ((market.isPlayerOwned() || market.getFaction().isPlayerFaction())
                    && Misc.getStorageTotalValue(market) > 0) {
                systems.add(market);
            }
        }
        return systems;
    }

    public static void sortDropDownButtonsByName(
            ArrayList<DropDownButton> buttons, final boolean ascending) {
        Collections.sort(
                buttons,
                new Comparator<DropDownButton>() {
                    @Override
                    public int compare(DropDownButton button1, DropDownButton button2) {
                        String name1 = getButtonName(button1);
                        String name2 = getButtonName(button2);
                        return ascending
                                ? name1.compareToIgnoreCase(name2)
                                : name2.compareToIgnoreCase(name1);
                    }
                });
    }

    public static void sortDropDownButtonsIncome(
            ArrayList<DropDownButton> buttons, final boolean ascending) {
        Collections.sort(
                buttons,
                new Comparator<DropDownButton>() {
                    @Override
                    public int compare(DropDownButton button1, DropDownButton button2) {
                        float days1 = calculateIncome(button1);
                        float days2 = calculateIncome(button2);
                        return ascending
                                ? Float.compare(days1, days2)
                                : Float.compare(days2, days1);
                    }
                });
    }

    public static float getUpkeepForStorage(MarketAPI market) {
        if (market.isPlayerOwned() || market.getFaction().isPlayerFaction()) {
            return 0;
        }
        float storageFraction = Global.getSettings().getFloat("storageFreeFraction");
        return Misc.getStorageTotalValue(market) * storageFraction;
    }

    private static String getButtonName(DropDownButton button) {

        if (button instanceof StarSystemHoldingDropDown hl) {
            return hl.getStarSystem().getName();
        }
        if (button instanceof WarehouseDropDown hl) {
            return hl.market.getName();
        }
        return "";
    }

    private static float calculateIncome(DropDownButton button) {
        float income = 0f;
        if (button instanceof StarSystemHoldingDropDown hl) {
            for (MarketAPI market : hl.getMarkets()) {
                income += market.getNetIncome();
            }
        }
        if (button instanceof WarehouseDropDown drop) {
            float storageFraction = Global.getSettings().getFloat("storageFreeFraction");

            income += Misc.getStorageTotalValue(drop.market) * storageFraction;
            if (drop.market.isPlayerOwned() || drop.market.getFaction().isPlayerFaction()) {
                income = 0;
            }
        }
        return income;
    }
}

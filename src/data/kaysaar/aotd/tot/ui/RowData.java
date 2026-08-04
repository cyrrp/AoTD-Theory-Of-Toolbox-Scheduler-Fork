package data.kaysaar.aotd.tot.ui;

import java.util.LinkedHashMap;

public class RowData {
    public float row;
    public LinkedHashMap<String, Integer> stringsInRow;

    public RowData(float rowNumber, LinkedHashMap<String, Integer> stringsInRow) {
        this.row = rowNumber;
        this.stringsInRow = stringsInRow;
    }
}

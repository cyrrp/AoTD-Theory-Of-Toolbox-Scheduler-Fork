package data.kaysaar.aotd.tot.plugins;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.Global;
import org.json.JSONException;
import org.json.JSONObject;

public class AoTDCommodityEconSpec {
    public String commodityId;
    public float supplyMult, demandMult;
    public String calculationScript;
    public float internalCut, externalCut;
    public float econUnitMult = 1f;

    public String getCommodityId() {
        return commodityId;
    }

    public AoTDBaseDemSupCalc getCalculationScript() {
        return (AoTDBaseDemSupCalc) Global.getSettings().getInstanceOfScript(calculationScript);
    }

    public static AoTDCommodityEconSpec generateFromJson(JSONObject obj) throws JSONException {

        String id = obj.getString("commodityId");
        if (!AshMisc.isStringValid(id)) return null;
        String script = obj.getString("calculationScript");
        float supplyEconMult = (float) obj.getDouble("supplyEconUnitMult");
        float demandEconMult = (float) obj.getDouble("demandEconUnitMult");
        float internalCut = 0.05f;
        float externalCut = 0.15f;
        float econUnitMult = 1f;
        if (!AshMisc.isStringValid(script)) {
            script = AoTDBaseDemSupCalc.class.getName();
        }
        if (AshMisc.isStringValid("internalCut")) {
            internalCut = (float) obj.getDouble("internalCut");
        }

        if (AshMisc.isStringValid("externalCut")) {
            externalCut = (float) obj.getDouble("externalCut");
        }
        if (AshMisc.isStringValid("econUnitMult")) {
            econUnitMult = (float) obj.getDouble("econUnitMult");
        }
        AoTDCommodityEconSpec spec =
                new AoTDCommodityEconSpec(
                        id, supplyEconMult, demandEconMult, script, internalCut, externalCut);
        if (econUnitMult != 1) {
            spec.setEconUnitMult(econUnitMult);
        }
        return spec;
    }

    public AoTDCommodityEconSpec(
            String commodityId,
            float supplyMult,
            float demandMult,
            String classForCalc,
            float internalCut,
            float externalCut) {
        this.commodityId = commodityId;
        this.supplyMult = supplyMult;
        this.demandMult = demandMult;
        this.internalCut = internalCut;
        this.externalCut = externalCut;
        this.calculationScript = classForCalc;
    }

    public void setEconUnitMult(float econUnitMult) {
        this.econUnitMult = econUnitMult;
    }

    public float getExternalCut() {
        return externalCut;
    }

    public float getInternalCut() {
        return internalCut;
    }

    public void setExternalCut(float externalCut) {
        this.externalCut = externalCut;
    }

    public void setInternalCut(float internalCut) {
        this.internalCut = internalCut;
    }

    public void setCalculationScript(String calculationScript) {
        this.calculationScript = calculationScript;
    }

    public float getSupplyMult() {
        return supplyMult;
    }

    public float getDemandMult() {
        return demandMult;
    }
}

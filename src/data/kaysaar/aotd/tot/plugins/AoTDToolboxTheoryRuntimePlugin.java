package data.kaysaar.aotd.tot.plugins;

import data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpecManager;

/**
 * Runtime entry point for the scheduler fork.
 *
 * <p>The upstream runtime JAR used as the base for the scheduler revisions predates
 * the production-spec bootstrap that is present in the maintained source tree.
 * Keep the scheduler plugin behavior intact while restoring that bootstrap for
 * Vaults of Knowledge and other consumers of the production API.</p>
 */
public class AoTDToolboxTheoryRuntimePlugin extends AoTDToolboxTheoryPlugin {
    @Override
    public void onAboutToStartGeneratingCodex() {
        AoTDProductionSpecManager.generateSpecsForAllStuff();
        super.onAboutToStartGeneratingCodex();
    }
}

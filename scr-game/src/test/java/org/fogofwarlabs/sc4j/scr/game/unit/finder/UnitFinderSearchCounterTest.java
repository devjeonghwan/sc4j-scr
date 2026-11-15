package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.engine.spatial.SearchCounter;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the process-wide unit finder search counter belongs to the loaded game data.
 */
@RequiresInstallation
final class UnitFinderSearchCounterTest {
    @Test
    void search_counter_belongs_to_the_loaded_game_data() throws Exception {
        try (GameData process = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game first = UnitFinderScenario.start(process);
            SearchCounter counter = first.getUnitSystem().getUnitFinderSearchCounter();
            int afterFirst = counter.getValue();

            try (GameData freshProcess = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
                Game fresh = UnitFinderScenario.start(freshProcess);

                Assertions.assertEquals(afterFirst, fresh.getUnitSystem().getUnitFinderSearchCounter().getValue(),
                                        "Data loaded afresh is a fresh process.");
            }

            Game second = UnitFinderScenario.start(process);

            Assertions.assertSame(counter, second.getUnitSystem().getUnitFinderSearchCounter());
            Assertions.assertEquals(2 * afterFirst, counter.getValue(), "The same replay advances the process's counter by the same count again.");
        }
    }
}

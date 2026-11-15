package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the spell energy lookup of orders.
 */
@RequiresInstallation
final class ComputerSpellsTest {
    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void get_order_energy_cost_should_return_order_technology_energy_shifted_by_eight() {
        Game game = Game.create(gameData, new GameMap(32, 32, 0, new int[1024]), new LcgRandom(0));

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(100 << 8, ComputerSpells.getOrderEnergyCost(game, ScrOrderIds.CAST_LOCKDOWN), "Lockdown");
        Assertions.assertEquals(75 << 8, ComputerSpells.getOrderEnergyCost(game, ScrOrderIds.CAST_PSIONIC_STORM), "Psionic Storm");
        Assertions.assertEquals(100 << 8, ComputerSpells.getOrderEnergyCost(game, ScrOrderIds.CAST_MAELSTROM), "Maelstrom");
        Assertions.assertEquals(150 << 8, ComputerSpells.getOrderEnergyCost(game, ScrOrderIds.CAST_MIND_CONTROL), "Mind Control");
        Assertions.assertEquals(0, ComputerSpells.getOrderEnergyCost(game, ScrOrderIds.ATTACK_UNIT), "an attack order has no technology");
    }
}

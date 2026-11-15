package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks upgrade, damage, supply and energy cheat effects.
 */
@RequiresInstallation
final class GameCheatsEffectsTest {
    private static final int UNIT_CAPACITY = 16;
    private static final int MAP_WIDTH = 8;
    private static final int MAP_HEIGHT = 8;
    private static final int TILE_COUNT = MAP_WIDTH * MAP_HEIGHT;

    private static final int HUMAN_PLAYER = 0;
    private static final int COMPUTER_PLAYER = 1;
    private static final int ZERG_SUPPLY_COUNTERS = 0;
    private static final int HALF_SUPPLY_REQUEST = 2;
    private static final int HALF_SUPPLY_CAP = 400;
    private static final int ENERGY_SHIFT = 8;
    /**
     * The energy points of the unit, the points it spends, the points it keeps after spending and the points of a request it cannot afford.
     */
    private static final int INITIAL_ENERGY = 10;
    private static final int SPENT_ENERGY = 5;
    private static final int REMAINING_ENERGY = 5;
    private static final int UNAFFORDABLE_ENERGY = 50;

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

    /**
     * Creates a {@link Game} instance with the frame controls required by these cheat checks.
     *
     * @return the created {@link Game} instance
     * @throws IOException if the fixture terrain records cannot be parsed
     */
    private static @NotNull Game createGame() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_WIDTH, MAP_HEIGHT, 0, new int[TILE_COUNT], new int[TILE_COUNT]), new LcgRandom(1));

        game.setCheatsEnabled(1);
        game.getPlayerTable().setPlayer(HUMAN_PLAYER, new Player(HUMAN_PLAYER, HUMAN_PLAYER, PlayerTypes.HUMAN, 0, 0));
        game.getPlayerTable().setPlayer(COMPUTER_PLAYER, new Player(COMPUTER_PLAYER, COMPUTER_PLAYER, PlayerTypes.COMPUTER, 0, 0));

        PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(true);

        GameData activeGameData = gameData;

        Assertions.assertNotNull(activeGameData);

        upgrades.initializeDefaults(activeGameData.getUpgradeTypes());
        game.getPlayerTable().setPlayerUpgrades(upgrades);

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);
        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.setFrame(GameCheatsScenario.createFrame(0));

        return game;
    }


    @Test
    void something_for_nothing_raises_each_upgrade_once_for_occupied_players() throws IOException {
        Game game = createGame();
        GameData activeGameData = gameData;
        PlayerUpgrades upgrades = game.getPlayerTable().getPlayerUpgrades();

        Assertions.assertNotNull(activeGameData);
        Assertions.assertNotNull(upgrades);

        game.getGameCheats().apply(GameCheats.SOMETHING_FOR_NOTHING);

        GameFrame activeFrame = game.getFrame();

        Assertions.assertNotNull(activeFrame);

        Assertions.assertEquals(0, activeFrame.getVisibility().getCheatFlags());

        for (int upgrade = 0; upgrade < ScrUpgradeIds.UNUSED_60; upgrade++) {
            int expected = Math.min(1, activeGameData.getUpgrades().get("Max Repeats", upgrade));

            Assertions.assertEquals(expected, upgrades.getLevel(HUMAN_PLAYER, upgrade), "upgrade " + upgrade);
            Assertions.assertEquals(0, upgrades.getLevel(COMPUTER_PLAYER, upgrade), "computer upgrade " + upgrade);
        }

        Assertions.assertEquals(0, upgrades.getLevel(HUMAN_PLAYER, ScrUpgradeIds.UNUSED_60));

        game.getGameCheats().apply(GameCheats.SOMETHING_FOR_NOTHING);

        Assertions.assertEquals(Math.min(2, activeGameData.getUpgrades().get("Max Repeats", 0)), upgrades.getLevel(HUMAN_PLAYER, 0));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void power_overwhelming_zeroes_damage_from_non_human_players_only() throws IOException {
        Game game = createGame();

        Assertions.assertFalse(game.getGameCheats().suppressesDamage(COMPUTER_PLAYER, true));

        game.getGameCheats().apply(GameCheats.POWER_OVERWHELMING);

        Assertions.assertTrue(game.getGameCheats().suppressesDamage(COMPUTER_PLAYER, false));
        Assertions.assertFalse(game.getGameCheats().suppressesDamage(HUMAN_PLAYER, false));
        Assertions.assertTrue(game.getGameCheats().suppressesDamage(-1, true));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getGameCheats().suppressesDamage(-1, false));

        game.getGameCheats().apply(0);

        Assertions.assertFalse(game.getGameCheats().suppressesDamage(COMPUTER_PLAYER, true));
    }

    @Test
    void food_for_thought_ignores_provided_supply_but_keeps_supply_cap() throws IOException {
        Game game = createGame();
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        Assertions.assertFalse(accounting.hasAvailableSupply(HUMAN_PLAYER, ZERG_SUPPLY_COUNTERS, HALF_SUPPLY_REQUEST));

        game.getGameCheats().apply(GameCheats.FOOD_FOR_THOUGHT);

        Assertions.assertTrue(accounting.hasAvailableSupply(HUMAN_PLAYER, ZERG_SUPPLY_COUNTERS, HALF_SUPPLY_REQUEST));
        Assertions.assertFalse(accounting.hasAvailableSupply(HUMAN_PLAYER, ZERG_SUPPLY_COUNTERS, HALF_SUPPLY_CAP + HALF_SUPPLY_REQUEST));

        game.getGameCheats().apply(0);

        Assertions.assertFalse(accounting.hasAvailableSupply(HUMAN_PLAYER, ZERG_SUPPLY_COUNTERS, HALF_SUPPLY_REQUEST));
    }

    @Test
    void the_gathering_skips_energy_tests_and_spending() throws IOException {
        Game game = createGame();
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setEnergy(INITIAL_ENERGY << ENERGY_SHIFT);

        Assertions.assertFalse(game.getUnitEnergy().hasEnergy(unit, UNAFFORDABLE_ENERGY << ENERGY_SHIFT));

        game.getUnitEnergy().spend(unit, SPENT_ENERGY << ENERGY_SHIFT);

        Assertions.assertEquals(REMAINING_ENERGY << ENERGY_SHIFT, unit.getEnergy());

        game.getGameCheats().apply(GameCheats.THE_GATHERING);

        Assertions.assertTrue(game.getUnitEnergy().hasEnergy(unit, UNAFFORDABLE_ENERGY << ENERGY_SHIFT));

        game.getUnitEnergy().spend(unit, UNAFFORDABLE_ENERGY << ENERGY_SHIFT);

        Assertions.assertEquals(REMAINING_ENERGY << ENERGY_SHIFT, unit.getEnergy());

        game.getGameCheats().apply(0);

        Assertions.assertFalse(game.getUnitEnergy().hasEnergy(unit, UNAFFORDABLE_ENERGY << ENERGY_SHIFT));
    }
}

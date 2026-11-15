package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that connecting an add-on changes the owner of the stored Nuclear Silo missile only when the connecting building is itself a Nuclear Silo,
 * and that a neutral add-on connects to its parent and reverts when abandoned.
 */
@RequiresInstallation
final class UnitAddonsTest {
    /**
     * The position of the connecting building in the running game.
     */
    private static final int BUILDING_X = 3560;
    private static final int BUILDING_Y = 2160;
    /**
     * The position of the add-on and of the stored missile in the running game.
     */
    private static final int ADDON_X = 3720;
    private static final int ADDON_Y = 2160;

    /**
     * The instruction limit of the construction game calls.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int COMMAND_CENTER_TILE = 8;
    /**
     * The divisor from a placement box size to the offset of its center.
     */
    private static final int CENTER_DIVISOR = 2;
    /**
     * The add-on offset entry of the Comsat Station: the entries start at the Command Center.
     */
    private static final int COMSAT_ADDON_ENTRY = 1;
    /**
     * The neutral player that an abandoned add-on reverts to.
     */
    private static final int NEUTRAL_PLAYER = 11;

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
    void connect_should_keep_the_missile_owner_of_a_nuclear_silo_connected_to_a_command_center() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit commandCenter = RunningGameScenario.createUnit(game,
                                                                    ScrUnitIds.TERRAN_COMMAND_CENTER,
                                                                    BUILDING_X, BUILDING_Y,
                                                                    RunningGameScenario.HUMAN);
                Unit silo = RunningGameScenario.createUnit(game,
                                                           ScrUnitIds.TERRAN_NUCLEAR_SILO,
                                                           ADDON_X, ADDON_Y,
                                                           RunningGameScenario.COMPUTER);
                Unit missile = RunningGameScenario.createUnit(game,
                                                              ScrUnitIds.TERRAN_NUCLEAR_MISSILE,
                                                              ADDON_X, ADDON_Y,
                                                              RunningGameScenario.COMPUTER);

                silo.setSiloNuke(missile);

                game.getUnitAddons().connect(commandCenter, silo, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertSame(silo, commandCenter.getAddon());
                Assertions.assertEquals(RunningGameScenario.HUMAN, silo.getOwnerIndex());
                Assertions.assertEquals(RunningGameScenario.COMPUTER, missile.getOwnerIndex());
            });
        }
    }

    @Test
    void connect_should_give_the_linked_missile_of_a_connecting_nuclear_silo_the_owner_of_the_silo() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit silo = RunningGameScenario.createUnit(game,
                                                           ScrUnitIds.TERRAN_NUCLEAR_SILO,
                                                           BUILDING_X, BUILDING_Y,
                                                           RunningGameScenario.HUMAN);
                Unit missile = RunningGameScenario.createUnit(game,
                                                              ScrUnitIds.TERRAN_NUCLEAR_MISSILE,
                                                              BUILDING_X, BUILDING_Y,
                                                              RunningGameScenario.COMPUTER);
                Unit comsat = RunningGameScenario.createUnit(game,
                                                             ScrUnitIds.TERRAN_COMSAT_STATION,
                                                             ADDON_X, ADDON_Y,
                                                             RunningGameScenario.COMPUTER);

                silo.setSiloNuke(missile);

                game.getUnitAddons().connect(silo, comsat, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(RunningGameScenario.HUMAN, comsat.getOwnerIndex());
                Assertions.assertEquals(RunningGameScenario.HUMAN, missile.getOwnerIndex());
            });
        }
    }

    @Test
    void neutral_addon_at_parent_addon_position_connects_and_reverts_when_abandoned() {
        Game game = ProductionScenario.createConstructionGame(gameData);

        Assertions.assertNotNull(gameData);

        DatTable units = gameData.getUnits();
        int centerX = COMMAND_CENTER_TILE * TerrainTileset.TILE_SIZE +
                      units.get("Placement Box Width", ScrUnitIds.TERRAN_COMMAND_CENTER) / CENTER_DIVISOR;
        int centerY = COMMAND_CENTER_TILE * TerrainTileset.TILE_SIZE +
                      units.get("Placement Box Height", ScrUnitIds.TERRAN_COMMAND_CENTER) / CENTER_DIVISOR;
        int addonX = COMMAND_CENTER_TILE * TerrainTileset.TILE_SIZE + units.get("Addon Horizontal", COMSAT_ADDON_ENTRY) +
                     units.get("Placement Box Width", ScrUnitIds.TERRAN_COMSAT_STATION) / CENTER_DIVISOR;
        int addonY = COMMAND_CENTER_TILE * TerrainTileset.TILE_SIZE + units.get("Addon Vertical", COMSAT_ADDON_ENTRY) +
                     units.get("Placement Box Height", ScrUnitIds.TERRAN_COMSAT_STATION) / CENTER_DIVISOR;
        Unit commandCenter = ProductionScenario.createCompletedUnit(game,
                                                                    ScrUnitIds.TERRAN_COMMAND_CENTER,
                                                                    centerX, centerY,
                                                                    ProductionScenario.PLAYER);

        commandCenter.setStatus(commandCenter.getStatus() | UnitStatusFlags.GROUNDED_BUILDING);

        Unit comsat = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_COMSAT_STATION, addonX, addonY, ProductionScenario.ENEMY_PLAYER);

        comsat.setStatus(comsat.getStatus() | UnitStatusFlags.GROUNDED_BUILDING);

        game.getUnitAddons().findAndConnect(commandCenter, INSTRUCTION_LIMIT);

        Assertions.assertNull(commandCenter.getAddon());

        game.getUnitOwnership().changeOwner(comsat, PlayerTable.NEUTRAL_PLAYER_ID, false, INSTRUCTION_LIMIT);
        game.getUnitAddons().findAndConnect(commandCenter, INSTRUCTION_LIMIT);

        Assertions.assertSame(comsat, commandCenter.getAddon());
        Assertions.assertEquals(ProductionScenario.PLAYER, comsat.getOwnerIndex());

        Sprite ownerSprite = comsat.getSpriteOrNull();

        Assertions.assertNotNull(ownerSprite);

        Assertions.assertEquals(ProductionScenario.PLAYER, ownerSprite.getOwner());

        game.getUnitLifecycle().removeTargetReferences(commandCenter, comsat);

        Assertions.assertNull(commandCenter.getAddon());

        commandCenter.setAddon(comsat);
        game.getUnitAddons().abandon(commandCenter, INSTRUCTION_LIMIT);

        Assertions.assertNull(commandCenter.getAddon());
        Assertions.assertEquals(NEUTRAL_PLAYER, comsat.getOwnerIndex());
    }
}

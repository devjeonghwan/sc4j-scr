package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that a Probe ordered to place a Protoss building walks to its site without charging resources, also with an emptied queue slot.
 */
@RequiresInstallation
final class UnitConstructionTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;

    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;

    /**
     * The pool index of the builder.
     */
    private static final int BUILDER_POOL_INDEX = 1;

    /**
     * The human player whose record the approach uses.
     */
    private static final int PLAYER = 0;
    /**
     * The player identifier of the test player.
     */
    private static final int PLAYER_ID = 0;
    /**
     * The team of the test player.
     */
    private static final int TEAM = 0;
    /**
     * The minerals of the player, which the Probe's approach leaves unchanged.
     */
    private static final int MINERALS = 150;

    /**
     * The site position the Probe walks to.
     */
    private static final int TARGET_X = 320;
    private static final int TARGET_Y = 352;
    /**
     * The state of a Probe that walks to the building site.
     */
    private static final int APPROACHING_STATE = 1;

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
    void update_protoss_should_start_probe_approach_without_charging_resources() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));
        Unit builder = UnitTestUtils.createUnit(BUILDER_POOL_INDEX);

        builder.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        builder.setOrderTargetX(TARGET_X);
        builder.setOrderTargetY(TARGET_Y);
        builder.setPrimaryOrder(ScrOrderIds.PLACE_PROTOSS_BUILDING);

        Player player = new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.PROTOSS, TEAM);

        player.setMinerals(MINERALS);
        game.getPlayerTable().setPlayer(PLAYER, player);

        game.getUnitConstruction().updateProtoss(builder, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertEquals(APPROACHING_STATE, builder.getPrimaryOrderState());
        Assertions.assertEquals(TARGET_X, builder.getMovementDestinationX());
        Assertions.assertEquals(TARGET_Y, builder.getMovementDestinationY());
        Assertions.assertEquals(TARGET_X, builder.getDirectionTargetX());
        Assertions.assertEquals(TARGET_Y, builder.getDirectionTargetY());
        Assertions.assertEquals(MINERALS, player.getMinerals());
    }

    @Test
    void update_protoss_should_keep_walking_to_the_site_with_an_emptied_queue_slot() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));
        Unit builder = UnitTestUtils.createUnit(BUILDER_POOL_INDEX);

        builder.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        builder.setOrderTargetX(TARGET_X);
        builder.setOrderTargetY(TARGET_Y);
        builder.setPrimaryOrder(ScrOrderIds.PLACE_PROTOSS_BUILDING);
        builder.setBuildQueueIndex(0);
        builder.setFirstBuildQueueType(ScrUnitIds.NONE);

        game.getUnitConstruction().updateProtoss(builder, RunningGameScenario.INSTRUCTION_LIMIT);
        game.getUnitConstruction().updateProtoss(builder, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertEquals(APPROACHING_STATE, builder.getPrimaryOrderState());
        Assertions.assertEquals(TARGET_X, builder.getMovementDestinationX());
        Assertions.assertEquals(TARGET_Y, builder.getMovementDestinationY());
    }
}

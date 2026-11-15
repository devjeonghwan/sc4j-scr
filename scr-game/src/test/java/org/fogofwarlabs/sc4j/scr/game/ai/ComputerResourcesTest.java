package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.resource.MineralSearchScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the mineral field searches that computer towns and worker orders share: radius, gate, ring and claim rules.
 */
@RequiresInstallation
final class ComputerResourcesTest {
    private static final int WORKER_OWNER = 0;
    private static final int WORKER_INDEX = 1;
    private static final int MINERAL_INDEX = 2;
    private static final int SECOND_MINERAL_INDEX = 3;

    /**
     * The vertical position of the workers, the mineral fields and the search centers.
     */
    private static final int POSITION_Y = 128;
    private static final int CENTER_POSITION_X = 128;
    private static final int LEFT_POSITION_X = 64;
    private static final int RIGHT_POSITION_X = 192;
    /**
     * A horizontal position more than {@code 384} pixels right of {@link #LEFT_POSITION_X}.
     */
    private static final int FAR_POSITION_X = 464;
    /**
     * A horizontal position less than {@code 384} pixels right of {@link #LEFT_POSITION_X}.
     */
    private static final int NEAR_POSITION_X = 400;
    private static final int EDGE_WORKER_POSITION_X = 160;
    private static final int EDGE_MINERAL_POSITION_X = 32;

    /**
     * The tile column of the mineral field at {@link #RIGHT_POSITION_X}.
     */
    private static final int RIGHT_TILE_X = 6;
    /**
     * The tile row of {@link #POSITION_Y}.
     */
    private static final int TILE_Y = 4;
    /**
     * The occupied tiles around the mineral field at the left map edge, which leave one ring tile free.
     */
    private static final int @NotNull [] @NotNull [] RING_TILES = {{1, 2}, {2, 2}, {0, 3}, {2, 3}, {0, 5}, {1, 5}, {2, 5}};
    /**
     * The row of the free ring tile in the bound column.
     */
    private static final int BOUND_ROW_TILE_Y = 2;

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

        gameData = null;
    }

    private static int getTileIndex(int tileX, int tileY) {
        return tileY * MineralSearchScenario.MAP_SIZE + tileX;
    }

    private static @NotNull Game createGame(int @NotNull [] flags) throws IOException {
        Assertions.assertNotNull(gameData);

        return MineralSearchScenario.createGame(gameData, flags);
    }

    private static @NotNull Unit placeWorker(@NotNull Game game, int positionX) {
        return MineralSearchScenario.placeUnit(game, WORKER_INDEX, ScrUnitIds.PROTOSS_PROBE, WORKER_OWNER, positionX, POSITION_Y);
    }

    private static @NotNull Unit placeMineral(@NotNull Game game, int poolIndex, int positionX, int positionY) {
        Unit mineral = MineralSearchScenario.placeUnit(game, poolIndex, ScrUnitIds.RESOURCE_MINERAL_FIELD, PlayerTable.NEUTRAL_PLAYER_ID,
                                                       positionX, positionY);

        completeMineral(game, mineral);

        return mineral;
    }

    private static void completeMineral(@NotNull Game game, @NotNull Unit mineral) {
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(gameData);
        Assertions.assertNotNull(accounting);

        accounting.updateCompleted(mineral, gameData.getUnitTypes(), 1, false, null);
    }


    @Test
    void find_mineral_should_skip_gathered_fields_and_fields_on_unexplored_tiles() throws IOException {
        int[] flags = MineralSearchScenario.createWalkableFlags();

        flags[getTileIndex(RIGHT_TILE_X, TILE_Y)] |= 1 << (WORKER_OWNER + MapTileFlags.NOT_EXPLORED_SHIFT);

        Game game = createGame(flags);
        Unit worker = placeWorker(game, CENTER_POSITION_X);
        Unit gathered = placeMineral(game, MINERAL_INDEX, LEFT_POSITION_X, POSITION_Y);
        Unit unexplored = placeMineral(game, SECOND_MINERAL_INDEX, RIGHT_POSITION_X, POSITION_Y);

        gathered.setResourceBeingGathered(true);

        Assertions.assertNull(game.getComputerResources().findMineral(worker, CENTER_POSITION_X, POSITION_Y, false));

        game.getMap().revealTile(getTileIndex(RIGHT_TILE_X, TILE_Y), 1 << WORKER_OWNER);

        Assertions.assertSame(unexplored, game.getComputerResources().findMineral(worker, CENTER_POSITION_X, POSITION_Y, false));
    }

    @Test
    void find_nearby_mineral_should_accept_a_gathered_field() throws IOException {
        Game game = createGame(MineralSearchScenario.createWalkableFlags());
        Unit worker = placeWorker(game, CENTER_POSITION_X);
        Unit gathered = placeMineral(game, MINERAL_INDEX, LEFT_POSITION_X, POSITION_Y);

        gathered.setResourceBeingGathered(true);

        Assertions.assertSame(gathered, game.getComputerResources().findNearbyMineral(worker, CENTER_POSITION_X, POSITION_Y));
    }

    @Test
    void find_mineral_should_search_twice_as_far_for_a_town_with_few_workers() throws IOException {
        Game game = createGame(MineralSearchScenario.createWalkableFlags());
        Unit worker = placeWorker(game, LEFT_POSITION_X);
        Unit mineral = placeMineral(game, MINERAL_INDEX, FAR_POSITION_X, POSITION_Y);

        Assertions.assertNull(game.getComputerResources().findMineral(worker, LEFT_POSITION_X, POSITION_Y, false));
        Assertions.assertSame(mineral, game.getComputerResources().findMineral(worker, LEFT_POSITION_X, POSITION_Y, true));
    }

    @Test
    void find_nearby_mineral_should_search_384_pixels_around_the_search_center() throws IOException {
        Game farGame = createGame(MineralSearchScenario.createWalkableFlags());
        Unit farWorker = placeWorker(farGame, LEFT_POSITION_X);

        placeMineral(farGame, MINERAL_INDEX, FAR_POSITION_X, POSITION_Y);

        Assertions.assertNull(farGame.getComputerResources().findNearbyMineral(farWorker, LEFT_POSITION_X, POSITION_Y));

        Game nearGame = createGame(MineralSearchScenario.createWalkableFlags());
        Unit nearWorker = placeWorker(nearGame, LEFT_POSITION_X);
        Unit nearMineral = placeMineral(nearGame, MINERAL_INDEX, NEAR_POSITION_X, POSITION_Y);

        Assertions.assertSame(nearMineral, nearGame.getComputerResources().findNearbyMineral(nearWorker, LEFT_POSITION_X, POSITION_Y));
        Assertions.assertNull(nearGame.getComputerResources().findMineral(nearWorker, LEFT_POSITION_X, POSITION_Y, false));
    }

    @Test
    void find_mineral_should_skip_claimed_fields_only_for_a_computer_worker_of_a_town_with_few_workers() throws IOException {
        Game game = createGame(MineralSearchScenario.createWalkableFlags());
        Unit worker = placeWorker(game, CENTER_POSITION_X);
        Unit mineral = placeMineral(game, MINERAL_INDEX, RIGHT_POSITION_X, POSITION_Y);

        mineral.setResourceClaimedByComputer(true);
        game.getPlayerTable().setPlayer(WORKER_OWNER, new Player(WORKER_OWNER, WORKER_OWNER, PlayerTypes.COMPUTER, 0, 0));

        Assertions.assertNull(game.getComputerResources().findMineral(worker, CENTER_POSITION_X, POSITION_Y, true));
        Assertions.assertSame(mineral, game.getComputerResources().findMineral(worker, CENTER_POSITION_X, POSITION_Y, false));

        game.getPlayerTable().setPlayer(WORKER_OWNER, new Player(WORKER_OWNER, WORKER_OWNER, PlayerTypes.HUMAN, 0, 0));

        Assertions.assertSame(mineral, game.getComputerResources().findMineral(worker, CENTER_POSITION_X, POSITION_Y, true));
    }

    @Test
    void find_mineral_should_find_nothing_without_a_completed_neutral_mineral_field() throws IOException {
        Game game = createGame(MineralSearchScenario.createWalkableFlags());
        Unit worker = placeWorker(game, CENTER_POSITION_X);
        Unit mineral = MineralSearchScenario.placeUnit(game, MINERAL_INDEX, ScrUnitIds.RESOURCE_MINERAL_FIELD, PlayerTable.NEUTRAL_PLAYER_ID,
                                                       RIGHT_POSITION_X, POSITION_Y);

        Assertions.assertNull(game.getComputerResources().findMineral(worker, CENTER_POSITION_X, POSITION_Y, false));

        completeMineral(game, mineral);

        Assertions.assertSame(mineral, game.getComputerResources().findMineral(worker, CENTER_POSITION_X, POSITION_Y, false));
    }

    @Test
    void find_mineral_should_test_the_ring_from_the_bound_column_at_the_left_map_edge() throws IOException {
        int[] flags = MineralSearchScenario.createWalkableFlags();

        for (int[] tile : RING_TILES) {
            flags[getTileIndex(tile[0], tile[1])] |= MapTileFlags.OCCUPIED;
        }

        Game game = createGame(flags);
        Unit worker = placeWorker(game, EDGE_WORKER_POSITION_X);
        Unit mineral = placeMineral(game, MINERAL_INDEX, EDGE_MINERAL_POSITION_X, POSITION_Y);

        Assertions.assertSame(mineral, game.getComputerResources().findMineral(worker, EDGE_WORKER_POSITION_X, POSITION_Y, false));

        game.getMap().occupyTile(getTileIndex(0, BOUND_ROW_TILE_Y));

        Assertions.assertNull(game.getComputerResources().findMineral(worker, EDGE_WORKER_POSITION_X, POSITION_Y, false));
    }
}

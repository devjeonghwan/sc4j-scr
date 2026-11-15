package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.engine.client.command.SelectionFilter;
import org.fogofwarlabs.sc4j.scr.client.ClientReplayScenario;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.SpriteDrawOrder;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks the box, type and Shift selection filters with the SC:R selection rules against replay state, and that the replacement key is the
 * {@link SpriteDrawOrder#computeKey} draw-order key of the unit's sprite.
 * <p>
 * The test of type {@code 228} depends on the units.dat memory layout, which is the contract: the game reads the flags of the empty type {@code 228}
 * from the bytes that follow the flags array, the Target Acquisition Range bytes of the first types.
 */
@RequiresInstallation
final class ScrSelectionRulesTest {
    private static final int ALL_PLAYERS = 0xFF;
    private static final int NO_PLAYERS = 0;
    private static final @NotNull String REPLAY = GameTestResources.REPLAY_TVT_BAEKDUDAEGAN;
    private static final long REPLAY_FRAME = 6000;

    private static final int LOCAL_PLAYER = 0;
    private static final int ENEMY_PLAYER = 1;

    /**
     * The width and height in tiles of the map of the type {@code 228} test.
     */
    private static final int MAP_SIZE = 8;
    private static final int MAP_TILESET = 0;
    private static final int RANDOM_SEED = 1;
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The unit type whose Target Acquisition Range byte is the second flags byte of the empty type {@code 228}.
     */
    private static final int ALIASED_FLAGS_TYPE = 1;
    private static final int SELECTION_CAPACITY = 12;
    private static final int SCV_TYPE = 7;

    private static @NotNull SelectionFilter<Unit> createFilter(@NotNull ScrGameView view, int visionMask) {
        return new SelectionFilter<>(ScrSelectionRules.CAPACITY, new ScrSelectionRules(view, LOCAL_PLAYER, false, visionMask));
    }

    @Test
    void box_selection_prefers_own_units_and_falls_back_to_last_building() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(REPLAY, REPLAY_FRAME)) {
            Game game = session.getGame();
            ScrGameView view = new ScrGameView(game);
            ScrSelectionRules rules = new ScrSelectionRules(view, LOCAL_PLAYER, false, ALL_PLAYERS);
            SelectionFilter<Unit> filter = createFilter(view, ALL_PLAYERS);
            Unit commandCenter = ClientReplayScenario.findCompletedUnits(game, LOCAL_PLAYER, ScrUnitIds.TERRAN_COMMAND_CENTER).getFirst();
            List<Unit> workers = ClientReplayScenario.findCompletedUnits(game, LOCAL_PLAYER, ScrUnitIds.TERRAN_SCV);
            List<Unit> depots = ClientReplayScenario.findCompletedUnits(game, LOCAL_PLAYER, ScrUnitIds.TERRAN_SUPPLY_DEPOT);
            Unit enemy = ClientReplayScenario.findCompletedUnits(game, ENEMY_PLAYER, ScrUnitIds.TERRAN_COMMAND_CENTER).getFirst();

            Assertions.assertTrue(depots.size() >= 2 && !workers.isEmpty(), "The replay state has two depots and a worker.");

            Unit enemyWorker = ClientReplayScenario.findCompletedUnits(game, ENEMY_PLAYER, ScrUnitIds.TERRAN_SCV).getFirst();

            Assertions.assertFalse(rules.canJoinMultipleSelection(commandCenter), "buildings never join a multiple selection");
            Assertions.assertTrue(rules.canJoinMultipleSelection(workers.getFirst()));
            Assertions.assertFalse(rules.canJoinMultipleSelection(enemyWorker), "live play takes only own units");
            Assertions.assertTrue(new ScrSelectionRules(view, LOCAL_PLAYER, true, ALL_PLAYERS).canJoinMultipleSelection(enemyWorker),
                                  "a replay takes any owner");

            List<Unit> mixed = List.of(depots.get(0), commandCenter, workers.getFirst(), depots.get(1));

            Assertions.assertEquals(List.of(workers.getFirst()), filter.filterBox(mixed), "an own unit drops every building");

            List<Unit> buildings = List.of(depots.get(0), commandCenter, enemy, depots.get(1));

            Assertions.assertEquals(List.of(depots.get(1)), filter.filterBox(buildings), "the last non-qualifying unit is the fallback");
            Assertions.assertEquals(List.of(), createFilter(view, NO_PLAYERS).filterBox(buildings), "invisible units are never listed");
        }
    }

    @Test
    void type_and_shift_selection_keep_clicked_building_and_cap_units_at_twelve() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(REPLAY, REPLAY_FRAME)) {
            Game game = session.getGame();
            SelectionFilter<Unit> filter = createFilter(new ScrGameView(game), ALL_PLAYERS);
            List<Unit> depots = ClientReplayScenario.findCompletedUnits(game, LOCAL_PLAYER, ScrUnitIds.TERRAN_SUPPLY_DEPOT);
            List<Unit> workers = ClientReplayScenario.findCompletedUnits(game, LOCAL_PLAYER, ScrUnitIds.TERRAN_SCV);

            Assertions.assertTrue(depots.size() >= 2 && workers.size() > ScrSelectionRules.CAPACITY, "The replay state has depots and many workers.");

            Assertions.assertEquals(List.of(depots.get(1)), filter.filterType(depots, depots.get(1)));

            List<Unit> typed = filter.filterType(workers, workers.getLast());

            Assertions.assertEquals(SELECTION_CAPACITY, typed.size());
            Assertions.assertSame(workers.getLast(), typed.getFirst(), "the clicked unit stays in slot 0");

            for (Unit worker : typed) {
                Assertions.assertEquals(SCV_TYPE, worker.getUnitId());
            }

            List<Unit> merged = filter.merge(List.of(depots.getFirst()), List.of(workers.getFirst()));

            Assertions.assertEquals(List.of(depots.getFirst()), merged, "a building selection takes no Shift additions");

            List<Unit> added = filter.merge(List.of(workers.get(0)), List.of(workers.get(1), workers.get(0)));

            Assertions.assertEquals(List.of(workers.get(0), workers.get(1)), added);
        }
    }

    @Test
    void can_join_multiple_selection_should_test_the_powerup_flag_of_the_empty_type_228() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(gameData, new GameMap(MAP_SIZE, MAP_SIZE, MAP_TILESET, new int[MAP_SIZE * MAP_SIZE]), new LcgRandom(RANDOM_SEED));
            DatTable units = game.requireGameData().getUnits();
            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
            int aliasedRange = units.get(UnitsDat.TARGET_ACQUISITION_RANGE, ALIASED_FLAGS_TYPE);

            // The powerup bit is in the second flags byte of type 228: the Target Acquisition Range byte of type 1.
            units.set(UnitsDat.TARGET_ACQUISITION_RANGE, ALIASED_FLAGS_TYPE, aliasedRange | (UnitTypeFlags.POWERUP >>> Byte.SIZE));
            unit.setUnitId(ScrUnitIds.NONE);
            unit.setOwnerIndex(LOCAL_PLAYER);

            Assertions.assertNotEquals(0, game.requireGameData().getUnitTypes().getFlags(ScrUnitIds.NONE) & UnitTypeFlags.POWERUP);
            Assertions.assertFalse(new ScrSelectionRules(new ScrGameView(game), LOCAL_PLAYER, false, ALL_PLAYERS).canJoinMultipleSelection(unit),
                                   "a powerup never joins a multiple selection");
        }
    }

    @Test
    void get_replacement_key_should_use_the_draw_order_key() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(REPLAY, REPLAY_FRAME)) {
            Game game = session.getGame();
            Unit worker = ClientReplayScenario.findCompletedUnits(game, LOCAL_PLAYER, ScrUnitIds.TERRAN_SCV).getFirst();
            Sprite sprite = worker.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            Assertions.assertEquals(SpriteDrawOrder.computeKey(sprite),
                                    new ScrSelectionRules(new ScrGameView(game), LOCAL_PLAYER, false, ALL_PLAYERS).getReplacementKey(worker));
        }
    }
}

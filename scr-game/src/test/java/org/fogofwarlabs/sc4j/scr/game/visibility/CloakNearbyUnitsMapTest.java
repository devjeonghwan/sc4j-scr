package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.CorpusTest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.LiveMapGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Plays an EUD map headless whose units without an air weapon run the cloak-nearby order, and checks that the run does not fail.
 * <p>
 * The map is read from the client's maps folder ({@link ExternalInput#MAPS}).
 */
@CorpusTest({ExternalInput.INSTALLATION, ExternalInput.MAPS})
final class CloakNearbyUnitsMapTest {
    private static final @NotNull String MAP_NAME = "[EUD]랜덤영웅디펜스_1.09.6(hotfix1).scx";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 0x1234;
    private static final int HUMAN_LOBBY_SLOT = 0;
    /**
     * The frame the run reaches.
     */
    private static final int LAST_FRAME = 3000;

    /**
     * Returns whether a creation list holds a unit that runs the cloak-nearby order without an air weapon.
     *
     * @param pool     the unit pool
     * @param units    the game's unit type table
     * @param grounded whether to walk the grounded-building list rather than the hidden list
     * @return {@code true} if such a unit is in the list, {@code false} otherwise
     */
    private static boolean hasCloakerWithoutAirWeapon(@NotNull UnitPool pool, @NotNull ScrUnitTypeView units, boolean grounded) {
        for (int index = pool.getFirstCreatedIndex(grounded); index != 0; index = pool.getNextCreatedIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (unit.getSecondaryOrder() == ScrOrderIds.CLOAK_NEARBY_UNITS && units.getAirWeapon(unit.getUnitId()) == ScrWeaponIds.NONE) {
                return true;
            }
        }

        return false;
    }


    @Test
    void play_should_run_the_cloak_nearby_order_of_units_without_an_air_weapon() throws Exception {
        Path mapPath = ExternalInput.MAPS.resolveFile(MAP_NAME);
        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
        MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.PROTOSS, HUMAN_LOBBY_SLOT, GameSpeed.FASTEST);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            LiveGame live = new LiveGame(setup, GAME_SEED);
            ReplayRuntime runtime = LiveMapGameScenario.startLive(gameData, live, mapPath);
            Game game = runtime.getGame();
            UnitPool pool = game.getUnitSystem().requireUnitPool();
            ScrUnitTypeView units = game.requireGameData().getUnitTypes();
            boolean cloakerSeen = false;

            while (game.getCurrentFrame() < LAST_FRAME) {
                LiveMapGameScenario.stepFrame(runtime, live);

                Assertions.assertNull(runtime.getFailure());

                cloakerSeen |= hasCloakerWithoutAirWeapon(pool, units, true) || hasCloakerWithoutAirWeapon(pool, units, false);
            }

            Assertions.assertTrue(cloakerSeen);
        }
    }
}

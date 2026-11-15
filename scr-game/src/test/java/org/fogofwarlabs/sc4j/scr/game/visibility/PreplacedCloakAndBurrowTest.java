package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Checks preplaced cloak and burrow state and cloaked-list membership.
 */
@RequiresInstallation
final class PreplacedCloakAndBurrowTest {
    /**
     * The measured host tick difference at the replay start.
     */
    private static final int START_ELAPSED_HOST_TICKS = 0;
    /**
     * The host clock at the replay start.
     */
    private static final int START_HOST_TICK = 0;

    private static @NotNull ReplayRuntime startReplay(@NotNull GameData gameData, @NotNull String name) throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(name)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(START_ELAPSED_HOST_TICKS, START_HOST_TICK);

        Assertions.assertNull(runtime.getFailure());

        return runtime;
    }

    /**
     * @param unit the unit
     * @return {@code true} if the unit both requires a detector and is cloaked, {@code false} otherwise
     */
    private static boolean hasCloakStatus(@NotNull Unit unit) {
        int cloakStatus = UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED;

        return (unit.getStatus() & cloakStatus) == cloakStatus;
    }

    /**
     * @param game the game whose cloaked-unit list is counted
     * @return the number of units in the cloaked-unit list
     */
    private static int countListedCloakedUnits(@NotNull Game game) {
        int listedCloakedUnits = 0;

        for (Unit unit = game.getUnitCloaking().getFirstCloakedUnit(); unit != null; unit = unit.getNextCloakedUnit()) {
            listedCloakedUnits++;
        }

        return listedCloakedUnits;
    }


    @Test
    void preplaced_cloaked_heroes_start_cloaked_and_in_the_cloaked_list() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = startReplay(gameData, GameTestResources.REPLAY_UMS_PYTHON_SIX_PLAYERS);
            Game game = runtime.getGame();
            int cloaked = 0;
            StringBuilder heroes = new StringBuilder();
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);
                int unitType = unit.getUnitId();
                boolean cloakedHero = unitType == ScrUnitIds.HERO_SARAH_KERRIGAN ||
                                      unitType == ScrUnitIds.HERO_TOM_KAZANSKY ||
                                      unitType == ScrUnitIds.HERO_SAMIR_DURAN ||
                                      unitType == ScrUnitIds.HERO_ALEXEI_STUKOV ||
                                      unitType == ScrUnitIds.HERO_INFESTED_DURAN;

                if (cloakedHero) {
                    heroes.append(unitType).append('/').append(unit.getOwnerIndex()).append('/')
                          .append(Integer.toHexString(unit.getStatus())).append('/');

                    PlayerTechnology heroTechnology = game.getPlayerTable().getPlayerTechnology();

                    Assertions.assertNotNull(heroTechnology);

                    heroes.append(heroTechnology.isAvailable(unit.getOwnerIndex(),
                                                             unitType == ScrUnitIds.HERO_TOM_KAZANSKY
                                                             ? ScrTechIds.CLOAKING_FIELD
                                                             : ScrTechIds.PERSONNEL_CLOAKING)).append(' ');
                }

                if (cloakedHero && hasCloakStatus(unit) && unit.isCloakedLinked()) {
                    cloaked++;
                }
            }

            Assertions.assertTrue(cloaked > 0,
                                  "Hero units with the CHK cloak state are cloaked (status 0x300) and linked in the cloaked list: " + heroes);
            Assertions.assertTrue(countListedCloakedUnits(game) >= cloaked, "Cloaked units are linked in the game-global cloaked list.");
        }
    }

    @Test
    void preplaced_burrowed_units_start_burrowed_and_cloaked() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = startReplay(gameData, GameTestResources.REPLAY_UMS_MICRO_TOURNAMENT);
            Game game = runtime.getGame();
            int burrowed = 0;
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if ((unit.getStatus() & UnitStatusFlags.BURROWED) != 0 && hasCloakStatus(unit)) {
                    burrowed++;
                }
            }

            Assertions.assertTrue(burrowed > 0, "Units with the CHK burrow state are burrowed and cloaked.");
            Assertions.assertTrue(countListedCloakedUnits(game) >= burrowed);
        }
    }
}

package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.presentation.CommandErrorEvents;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks lobby-selected unit capacity and creation failure at a full table.
 */
@RequiresInstallation
final class UnitLimitTest {
    private static final int EXTENDED_UNIT_CAPACITY = 3400;
    private static final int CLASSIC_UNIT_CAPACITY = 1700;
    private static final int UNIT_POSITION = 64;

    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 1;

    /**
     * Creates a Terran Marine for the player through the unit storage constructor.
     *
     * @param state      the simulation world
     * @param accounting the unit accounting of the world
     * @param player     the owning player
     * @return the created unit, or {@code null} if the unit table is full
     */
    private static @Nullable Unit createMarine(@NotNull Game state, @NotNull UnitAccounting accounting, int player) {
        UnitStorageConstructor constructor = state.getUnitStorageConstructor();
        int unitTypeIdentifier = ScrUnitIds.TERRAN_MARINE;
        UnitCreationContext creationContext = state.getUnitSystem().getUnitCreationContext();

        Assertions.assertNotNull(creationContext);

        return constructor.create(creationContext, accounting, unitTypeIdentifier, UNIT_POSITION, UNIT_POSITION, player, 0,
                                  RunningGameScenario.INSTRUCTION_LIMIT);
    }


    @Test
    void lobby_option_selects_classic_or_extended_unit_capacity_and_records_it() throws IOException {
        byte[] mapData = LiveSessionScenario.readBottleneckScenario();

        MeleeGameSetup extended = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);

        Assertions.assertEquals(EXTENDED_UNIT_CAPACITY, new LiveGame(extended, GAME_SEED).getReplay().getLimits().getUnitCapacity());

        MeleeGameSetup classic = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);

        classic.setClassicLimits(true);

        LiveGame game = new LiveGame(classic, GAME_SEED);

        Assertions.assertEquals(CLASSIC_UNIT_CAPACITY, game.getReplay().getLimits().getUnitCapacity());

        ReplayFile saved = ReplayFile.parse(game.writeReplay(0, 0L));

        Assertions.assertEquals(CLASSIC_UNIT_CAPACITY, saved.getLimits().getUnitCapacity(), "LMTS records the classic limits.");
    }

    @Test
    void full_unit_table_fails_creation_with_the_unit_limit_message() throws IOException {
        byte[] mapData = LiveSessionScenario.readBottleneckScenario();

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            MeleeGameSetup setup = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);

            setup.setClassicLimits(true);

            LiveGame game = new LiveGame(setup, GAME_SEED);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Game state = live.getGame();
            UnitPool pool = state.getUnitSystem().getUnitPool();
            UnitAccounting accounting = state.getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(pool);
            Assertions.assertNotNull(accounting);
            Assertions.assertEquals(CLASSIC_UNIT_CAPACITY, pool.getCapacity());

            int player = game.getLocalPlayer();
            int created = 0;

            while (pool.getAllocatedCount() < pool.getCapacity()) {
                Assertions.assertNotNull(createMarine(state, accounting, player));

                created++;
            }

            Assertions.assertTrue(created > 0);

            state.getInterfaceLink().getCommandErrorEvents().clear();

            Assertions.assertNull(createMarine(state, accounting, player), "The 1701st unit cannot be created.");

            state.getUnitStorageConstructor().notifyCreationFailure(player);

            CommandErrorEvents events = state.getInterfaceLink().getCommandErrorEvents();

            Assertions.assertEquals(1, events.getCount());
            Assertions.assertEquals("strMAXUNITS", events.get(0).getTextKey());
        }
    }
}

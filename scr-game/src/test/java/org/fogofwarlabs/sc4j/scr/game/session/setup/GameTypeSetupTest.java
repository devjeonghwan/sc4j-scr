package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.VictoryCondition;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerRecordCodec;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Checks single-player template headers, trigger resources, flags and departing-unit behavior.
 */
final class GameTypeSetupTest {
    private static final int FRAMES = 30;
    private static final int SEED = 0x1234;
    private static final int DEPARTURE_REASON = 1;
    /**
     * The flag beacons of a game type that creates flags, one per race.
     */
    private static final int FLAG_BEACON_COUNT = 3;
    /**
     * The player type of a computer removed by its departure.
     */
    private static final int REMOVED_COMPUTER_TYPE = 11;
    /**
     * The order of a dying unit.
     */
    private static final int DIE_ORDER = 0;

    private static final @NotNull String MAP = "(4)Lost Civilization.scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final @NotNull String TEMPLATE_DIRECTORY = "locales/enUS/Assets/Templates/";
    private static final int TEMPLATE_OPTIONS = 0x41;
    private static final int HEADER_OPTIONS = 0x81;
    private static final @NotNull String @NotNull [] TEMPLATES = {
            "Melee(1).got", "Free For All(1).got", "One on One(1).got",
            "Capture The Flag(1).got",
            "Greed(1).got", "Greed(2).got", "Greed(3).got", "Greed(4).got",
            "Slaughter(1).got", "Slaughter(2).got", "Slaughter(3).got", "Slaughter(4).got",
            "Sudden Death(1).got"
    };

    /**
     * Creates a {@link LiveGame} instance.
     *
     * @param mapData  the map bytes
     * @param template the single-player template
     * @return the created {@link LiveGame} instance
     * @throws IOException if the setup cannot read the map
     */
    private static @NotNull LiveGame createGame(byte @NotNull [] mapData, @NotNull GameTemplate template) throws IOException {
        MeleeGameSetup setup = new MeleeGameSetup(mapData,
                                                  PLAYER_NAME, ScrRaceIds.TERRAN, 0,
                                                  new int[]{ScrRaceIds.ZERG, ScrRaceIds.PROTOSS},
                                                  GameSpeed.FASTEST);

        setup.setTemplate(template);

        return new LiveGame(setup, SEED);
    }

    /**
     * Reads the game options of an installed single-player template.
     *
     * @param storage the installed game storage
     * @param name    the template file name
     * @return the parsed template
     * @throws IOException if the template cannot be read
     */
    private static @NotNull GameTemplate readTemplate(@NotNull CascStorage storage, @NotNull String name) throws IOException {
        byte[] file = storage.readFile(TEMPLATE_DIRECTORY + name);

        return GameTemplate.parse(Arrays.copyOfRange(file, TEMPLATE_OPTIONS, TEMPLATE_OPTIONS + GameTemplate.SIZE));
    }

    /**
     * Steps the runtime for the configured frame count and checks each failure result.
     *
     * @param runtime the runtime to step
     */
    private static void runConfiguredFrames(@NotNull ReplayRuntime runtime) {
        Game state = runtime.getGame();
        int until = state.getCurrentFrame() + FRAMES;
        int hostTicks = 0;

        while (state.getCurrentFrame() < until) {
            hostTicks += GameSpeed.FASTEST_DURATION;

            Assertions.assertTrue(runtime.step(GameSpeed.FASTEST_DURATION, hostTicks));
            Assertions.assertNull(runtime.getFailure());
        }
    }

    /**
     * Counts all three races' flag beacons in the created-unit list.
     *
     * @param state the unit state to scan
     * @return the flag beacon count
     */
    private static int countFlagBeacons(@NotNull Game state) {
        UnitPool pool = state.getUnitSystem().getUnitPool();
        int beacons = 0;

        Assertions.assertNotNull(pool);

        for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
            int unitType = pool.getUnit(index).getUnitId();

            if (unitType >= ScrUnitIds.SPECIAL_ZERG_FLAG_BEACON && unitType <= ScrUnitIds.SPECIAL_PROTOSS_FLAG_BEACON) {
                beacons++;
            }
        }

        return beacons;
    }

    /**
     * Counts the units in an owner list.
     *
     * @param pool  the pool containing the owner lists
     * @param owner the owner-list index
     * @return the unit count
     */
    private static int countUnits(@NotNull UnitPool pool, int owner) {
        int count = 0;

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            count++;
        }

        return count;
    }


    @RequiresInstallation
    @Test
    void every_single_player_game_type_starts_runs_and_replays() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
        ChkFile map = ChkFile.parseTolerant(mapData);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
             CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            for (String name : TEMPLATES) {
                GameTemplate template = readTemplate(storage, name);
                LiveGame game = createGame(mapData, template);
                byte[] written = game.writeReplay(0, 0);
                byte[] header = ReplayFile.readHeaderSection(written);

                Assertions.assertArrayEquals(template.encode(), Arrays.copyOfRange(header, HEADER_OPTIONS, HEADER_OPTIONS + GameTemplate.SIZE), name);

                ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), map, gameData,
                                                                  game.getLocalPlayer(),
                                                                  RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                  RunningGameScenario.INSTRUCTION_LIMIT);

                live.start(0, 0);

                Assertions.assertNull(live.getFailure(), name);

                Game state = live.getGame();
                VictoryCondition victory = template.getVictoryCondition();
                String triggers = victory.getTriggerFile(template.getSubTypeValue());

                Assertions.assertNotNull(triggers, name);

                int records = gameData.readInstalledFile(triggers).length / TriggerRecordCodec.RECORD_SIZE;

                TriggerContext liveTriggers = state.getTriggerContext();

                Assertions.assertNotNull(liveTriggers);

                Assertions.assertEquals(records, liveTriggers.getPlayerRecordCount(game.getLocalPlayer()), name + " registers " + triggers);
                Assertions.assertEquals(victory.createsFlags() ? FLAG_BEACON_COUNT : 0, countFlagBeacons(state), name + " flag beacons");

                runConfiguredFrames(live);

                ReplayRuntime replay = new ReplayRuntime(ReplayFile.parse(written), map, gameData,
                                                         RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                         RunningGameScenario.INSTRUCTION_LIMIT);

                replay.start(0, 0);

                Assertions.assertNull(replay.getFailure(), name + " replay");

                TriggerContext replayTriggers = replay.getGame().getTriggerContext();

                Assertions.assertNotNull(replayTriggers);

                Assertions.assertEquals(records, replayTriggers.getPlayerRecordCount(game.getLocalPlayer()), name + " replay triggers");
            }
        }
    }

    @RequiresInstallation
    @Test
    void departure_kills_the_units_only_in_capture_the_flag_and_sudden_death() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
        ChkFile map = ChkFile.parseTolerant(mapData);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
             CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            for (String name : new String[]{"Melee(1).got", "Capture The Flag(1).got", "Sudden Death(1).got"}) {
                GameTemplate template = readTemplate(storage, name);
                LiveGame game = createGame(mapData, template);
                ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), map, gameData,
                                                                  game.getLocalPlayer(),
                                                                  RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                  RunningGameScenario.INSTRUCTION_LIMIT);

                live.start(0, 0);
                runConfiguredFrames(live);

                Game state = live.getGame();
                UnitPool pool = state.getUnitSystem().getUnitPool();
                int computer = -1;

                Assertions.assertNotNull(pool);

                for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS && computer < 0; player++) {
                    Player candidatePlayer = state.getPlayerTable().getPlayer(player);

                    Assertions.assertNotNull(candidatePlayer);

                    if (candidatePlayer.getType() == PlayerTypes.COMPUTER) {
                        computer = player;
                    }
                }

                Assertions.assertTrue(computer >= 0);

                int owned = countUnits(pool, computer);
                int neutral = countUnits(pool, PlayerTable.NEUTRAL_PLAYER_ID);

                Assertions.assertTrue(owned > 0);

                state.getPlayerDeparture().leave(computer, DEPARTURE_REASON, RunningGameScenario.INSTRUCTION_LIMIT);

                Player departedPlayer = state.getPlayerTable().getPlayer(computer);

                Assertions.assertNotNull(departedPlayer);

                Assertions.assertEquals(REMOVED_COMPUTER_TYPE, departedPlayer.getType(), name + ": a removed computer becomes type 11");

                if (template.getVictoryCondition().killsDepartingUnits()) {
                    Assertions.assertEquals(neutral, countUnits(pool, PlayerTable.NEUTRAL_PLAYER_ID), name + " gives no unit to the neutral player");

                    for (int index = pool.getFirstOwnerIndex(computer); index != 0; index = pool.getNextOwnerIndex(index)) {
                        Unit unit = pool.getUnit(index);

                        Assertions.assertTrue(unit.getSpriteOrNull() == null || unit.getPrimaryOrder() == DIE_ORDER,
                                              name + " kills unit " + unit.getUnitId());
                    }
                } else {
                    Assertions.assertEquals(0, countUnits(pool, computer), name + " leaves no unit with the computer");
                    Assertions.assertEquals(neutral + owned, countUnits(pool, PlayerTable.NEUTRAL_PLAYER_ID),
                                            name + " gives every unit to the neutral player");
                }

                runConfiguredFrames(live);
            }
        }
    }

    @Test
    void victory_conditions_select_their_trigger_resources() {
        Assertions.assertEquals("triggers/Melee.trg", VictoryCondition.MELEE.getTriggerFile(0));
        Assertions.assertEquals("triggers/Melee.trg", VictoryCondition.ONE_ON_ONE.getTriggerFile(0), "One on One loads Melee.trg (victory 7)");
        Assertions.assertEquals("triggers/CTF.trg", VictoryCondition.CAPTURE_THE_FLAG.getTriggerFile(0));
        Assertions.assertEquals("triggers/SuddenDeath.trg", VictoryCondition.SUDDEN_DEATH.getTriggerFile(0));
        Assertions.assertEquals("triggers/Greed7500.trg", VictoryCondition.GREED.getTriggerFile(7500));
        Assertions.assertEquals("triggers/Slaughter45.trg", VictoryCondition.SLAUGHTER.getTriggerFile(45));
        Assertions.assertNull(VictoryCondition.NONE.getTriggerFile(0));
        Assertions.assertSame(VictoryCondition.NONE, VictoryCondition.of(2), "Victory 2 has no resource");
        Assertions.assertSame(VictoryCondition.NONE, VictoryCondition.of(8));
        Assertions.assertFalse(VictoryCondition.GREED.acceptsSubtypeValue(3000), "Greed fails the game start for another resource amount");
        Assertions.assertThrows(IllegalArgumentException.class, () -> VictoryCondition.SLAUGHTER.getTriggerFile(20));
        Assertions.assertTrue(VictoryCondition.CAPTURE_THE_FLAG.killsDepartingUnits());
        Assertions.assertTrue(VictoryCondition.SUDDEN_DEATH.killsDepartingUnits());
        Assertions.assertFalse(VictoryCondition.MELEE.killsDepartingUnits());
    }
}

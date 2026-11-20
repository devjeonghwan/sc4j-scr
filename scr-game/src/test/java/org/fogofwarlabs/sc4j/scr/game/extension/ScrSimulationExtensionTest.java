package org.fogofwarlabs.sc4j.scr.game.extension;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.NetworkReplayScenario;
import org.fogofwarlabs.sc4j.scr.game.extension.simulation.*;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayLoaderUtils;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameFile;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameSession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.*;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks the simulation plugins of a running game: plugin creation sees the scenario of live, replayed and saved games, the frame start runs
 * before the triggers, a death by weapon damage names its killer and
 * weapon, a weapon damage decision and an order decision change what the routines do, and a mod command reaches the plugins and is kept in the
 * written replay.
 */
@RequiresInstallation
@ExtendWith(GameResourcesExtension.class)
final class ScrSimulationExtensionTest {
    private static final @NotNull String HUMAN_NAME = "tester";
    private static final int SEED = 0x6AB9B92E;
    private static final int STEPPED_FRAMES = 48;
    private static final int MOD_COMMAND_FRAMES = 96;
    private static final int POSITION_X = 1000;
    private static final int POSITION_Y = 1000;
    private static final int TARGET_OFFSET = 24;
    /**
     * The hit points the weakened target keeps, with eight fractional bits.
     */
    private static final int WEAK_HIT_POINTS = 1 << 8;
    /**
     * The base damage a decision gives a hit, with eight fractional bits: more than any Zergling has hit points.
     */
    private static final int LETHAL_DAMAGE = 1000 << 8;
    private static final byte @NotNull [] MOD_COMMAND_DATA = {0x53, 0x43, 0x34, 0x4A};

    private static @NotNull Game createLiveGame(@NotNull GameData gameData, @NotNull LiveGame live, @NotNull ChkFile scenario) throws IOException {
        ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), scenario, gameData,
                                                             live.getLocalPlayer(),
                                                             RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                             RunningGameScenario.INSTRUCTION_LIMIT);

        return runtime.getGame();
    }

    private static @NotNull Unit createUnit(@NotNull ReplayRuntime runtime, int unitType, int offset, int owner) {
        Unit[] created = new Unit[1];

        RunningGameScenario.runInCommandDispatch(runtime, () -> created[0] = RunningGameScenario.createUnit(runtime.getGame(),
                                                                                                          unitType,
                                                                                                          POSITION_X + offset, POSITION_Y,
                                                                                                          owner));

        return created[0];
    }

    private static void hit(@NotNull ReplayRuntime runtime, @NotNull Unit source, @NotNull Unit target) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> runtime.getGame().getUnitDamage().dealWeaponHit(ScrWeaponIds.GAUSS_RIFLE,
                                                                                                               source, target,
                                                                                                               RunningGameScenario.INSTRUCTION_LIMIT));
    }

    private static @NotNull UnitDiedEvent findDeath(RecordingSimulationPluginSource.@NotNull Recording recording, @NotNull Unit unit) {
        List<UnitDiedEvent> deaths = recording.findEvents(UnitDiedEvent.class).stream().filter(event -> event.getUnit().getUnit() == unit).toList();

        Assertions.assertEquals(1, deaths.size(), "one death of the unit");

        return deaths.getFirst();
    }


    @Test
    void plugin_creation_sees_the_scenario_of_live_replayed_and_saved_games(@NotNull InstallationTestResources resources) throws Throwable {
        GameData gameData = resources.getGameData();
        byte[] mapData = MpqArchive.open(GameTestResources.getPath(GameTestResources.MAP_EUD_COMPUTER)).readFile(ChkFile.ARCHIVE_ENTRY);
        LiveGame live = new LiveGame(MeleeGameSetup.createUseMapSettings(mapData, HUMAN_NAME, ScrRaceIds.TERRAN, 0, GameSpeed.FASTEST), SEED);
        ReplayFile replay = ReplayFile.parse(GameTestResources.readAllBytes(GameTestResources.REPLAY_ZVZ_COMPUTER_SHORT));
        byte[] saveData = GameTestResources.readAllBytes(GameTestResources.SAVE_SINGLE_PLAYER_WITH_COMPUTER);
        LiveGame saved = SaveGameSession.read(SaveGameFile.parse(saveData)).createLiveGame();
        byte[] replayMapData = replay.getMapData();

        Assertions.assertNotNull(replayMapData);

        ChkFile replayScenario = ChkFile.parseTolerant(replayMapData);
        ChkFile liveScenario = ChkFile.parseTolerant(mapData);
        ChkFile inspectionScenario = ChkFile.parseTolerant(replayMapData);
        ChkFile savedScenario = ChkFile.parseTolerant(saved.getSetup().getMapData());
        ChkFile[] expected = {replayScenario, liveScenario, inspectionScenario, savedScenario};
        RecordingSimulationPluginSource.Recording recording = new RecordingSimulationPluginSource.Recording();
        Game[] games = new Game[expected.length];

        RecordingSimulationPluginSource.record(recording, () -> {
            ReplayRuntime replayed = new ReplayRuntime(replay, replayScenario, gameData,
                                                       RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                       RunningGameScenario.INSTRUCTION_LIMIT);

            games[0] = replayed.getGame();
            games[1] = createLiveGame(gameData, live, liveScenario);
            games[2] = ReplayLoaderUtils.createReplayInspectionState(replay, inspectionScenario, gameData);
            games[3] = createLiveGame(gameData, saved, savedScenario);
        });

        Assertions.assertEquals(expected.length, recording.getScenarios().size());

        for (int index = 0; index < expected.length; index++) {
            Assertions.assertSame(expected[index], recording.getScenarios().get(index), "the scenario at plugin creation");
            Assertions.assertSame(expected[index], games[index].getSetup().getScenario(), "the scenario of the setup");
        }
    }

    @Test
    void frame_start_runs_before_the_triggers_of_each_frame(@NotNull InstallationTestResources resources) throws Throwable {
        RecordingSimulationPluginSource.Recording recording = new RecordingSimulationPluginSource.Recording();

        recording.setFrameProbe(game -> {
            TriggerContext triggers = game.getTriggerContext();

            return triggers == null ? -1 : triggers.getClockCountdown();
        });

        RecordingSimulationPluginSource.record(recording, () -> {
            ReplayRuntime runtime = RunningGameScenario.start(resources.getGameData());
            Game game = runtime.getGame();
            TriggerContext triggers = game.getTriggerContext();

            Assertions.assertNotNull(triggers);

            for (int step = 0; step < STEPPED_FRAMES; step++) {
                int frame = game.getCurrentFrame();
                int countdown = triggers.getClockCountdown();
                int starts = recording.getFrameValues().size();

                Assertions.assertTrue(runtime.step(0, frame * runtime.getFrameDuration()));
                Assertions.assertEquals(frame + 1, game.getCurrentFrame());
                Assertions.assertEquals(starts + 1, recording.getFrameValues().size());
                Assertions.assertEquals(countdown, recording.getFrameValues().getLast(), "the frame start precedes the trigger step");
                Assertions.assertNotEquals(countdown, triggers.getClockCountdown(), "the trigger step of the frame ran");
            }
        });
    }

    @Test
    void death_by_weapon_damage_names_the_killer_and_the_weapon(@NotNull InstallationTestResources resources) throws Throwable {
        RecordingSimulationPluginSource.Recording recording = new RecordingSimulationPluginSource.Recording();

        RecordingSimulationPluginSource.record(recording, () -> {
            ReplayRuntime runtime = RunningGameScenario.start(resources.getGameData());
            Unit marine = createUnit(runtime, ScrUnitIds.TERRAN_MARINE, 0, RunningGameScenario.HUMAN);
            Unit zergling = createUnit(runtime, ScrUnitIds.ZERG_ZERGLING, TARGET_OFFSET, RunningGameScenario.COMPUTER);

            recording.getEvents().clear();
            zergling.setHitPoints(WEAK_HIT_POINTS);
            hit(runtime, marine, zergling);

            UnitDamagedEvent damage = recording.findEvents(UnitDamagedEvent.class).getLast();
            UnitDiedEvent death = findDeath(recording, zergling);
            SimulationUnit killer = death.getKiller();

            Assertions.assertSame(zergling, damage.getTarget().getUnit());
            Assertions.assertNotNull(damage.getSource());
            Assertions.assertSame(marine, damage.getSource().getUnit());
            Assertions.assertEquals(ScrWeaponIds.GAUSS_RIFLE, damage.getWeapon());
            Assertions.assertTrue(damage.getDamage() >= WEAK_HIT_POINTS);
            Assertions.assertNotNull(killer);
            Assertions.assertSame(marine, killer.getUnit());
            Assertions.assertEquals(RunningGameScenario.HUMAN, death.getKillerPlayer());
            Assertions.assertEquals(ScrWeaponIds.GAUSS_RIFLE, death.getWeapon());
            Assertions.assertEquals(ScrUnitIds.ZERG_ZERGLING, death.getUnit().getUnitType());
            Assertions.assertEquals(RunningGameScenario.COMPUTER, death.getUnit().getOwnerIndex());
        });
    }

    @Test
    void weapon_damage_decision_cancels_or_raises_the_damage(@NotNull InstallationTestResources resources) throws Throwable {
        RecordingSimulationPluginSource.Recording recording = new RecordingSimulationPluginSource.Recording();

        RecordingSimulationPluginSource.record(recording, () -> {
            ReplayRuntime runtime = RunningGameScenario.start(resources.getGameData());
            Unit marine = createUnit(runtime, ScrUnitIds.TERRAN_MARINE, 0, RunningGameScenario.HUMAN);
            Unit zergling = createUnit(runtime, ScrUnitIds.ZERG_ZERGLING, TARGET_OFFSET, RunningGameScenario.COMPUTER);
            int hitPoints = zergling.getHitPoints();

            recording.getEvents().clear();
            recording.setDecider(decision -> {
                if (decision instanceof WeaponDamageDecision damage) {
                    damage.setCancelled(true);
                }
            });
            hit(runtime, marine, zergling);

            Assertions.assertEquals(hitPoints, zergling.getHitPoints(), "a cancelled hit deals no damage");
            Assertions.assertTrue(recording.findEvents(UnitDamagedEvent.class).isEmpty());

            recording.setDecider(decision -> {
                if (decision instanceof WeaponDamageDecision damage) {
                    damage.setDamage(LETHAL_DAMAGE);
                }
            });
            hit(runtime, marine, zergling);

            Assertions.assertEquals(0, zergling.getHitPoints(), "a raised hit kills the target");
            Assertions.assertEquals(ScrWeaponIds.GAUSS_RIFLE, findDeath(recording, zergling).getWeapon());
        });
    }

    @Test
    void order_decision_rejects_or_changes_an_issued_order(@NotNull InstallationTestResources resources) throws Throwable {
        RecordingSimulationPluginSource.Recording recording = new RecordingSimulationPluginSource.Recording();

        RecordingSimulationPluginSource.record(recording, () -> {
            ReplayRuntime runtime = RunningGameScenario.start(resources.getGameData());
            Game game = runtime.getGame();
            Unit marine = createUnit(runtime, ScrUnitIds.TERRAN_MARINE, 0, RunningGameScenario.HUMAN);
            int idleOrder = marine.getPrimaryOrder();
            Runnable move = () -> game.getOrders().issue(marine,
                                                         ScrOrderIds.MOVE,
                                                         null,
                                                         POSITION_X, POSITION_Y + TARGET_OFFSET,
                                                         ScrUnitIds.NONE,
                                                         false,
                                                         RunningGameScenario.INSTRUCTION_LIMIT);

            recording.getEvents().clear();
            recording.setDecider(decision -> {
                if (decision instanceof OrderDecision order && order.getOrderId() == ScrOrderIds.MOVE) {
                    order.setRejected(true);
                }
            });
            RunningGameScenario.runInCommandDispatch(runtime, move);

            Assertions.assertEquals(idleOrder, marine.getPrimaryOrder(), "a rejected order leaves the orders unchanged");
            Assertions.assertTrue(recording.findEvents(OrderIssuedEvent.class).stream().noneMatch(event -> event.getOrderId() == ScrOrderIds.MOVE));

            recording.setDecider(decision -> {
                if (decision instanceof OrderDecision order && order.getOrderId() == ScrOrderIds.MOVE) {
                    order.setOrderId(ScrOrderIds.HOLD_POSITION);
                }
            });
            RunningGameScenario.runInCommandDispatch(runtime, move);

            OrderIssuedEvent issued = recording.findEvents(OrderIssuedEvent.class).getLast();

            Assertions.assertEquals(ScrOrderIds.HOLD_POSITION, marine.getPrimaryOrder(), "a changed order is issued instead");
            Assertions.assertSame(marine, issued.getUnit().getUnit());
            Assertions.assertEquals(ScrOrderIds.HOLD_POSITION, issued.getOrderId());
        });
    }

    @Test
    void mod_command_reaches_the_plugins_and_is_kept_in_the_written_replay(@NotNull InstallationTestResources resources) throws Throwable {
        Path map = GameTestResources.getPath(GameTestResources.MAP_EUD_COMPUTER);
        byte[] scenario = MpqArchive.open(map).readFile(ChkFile.ARCHIVE_ENTRY);
        MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(scenario, HUMAN_NAME, ScrRaceIds.TERRAN, 0, GameSpeed.FASTEST);
        LiveGame live = new LiveGame(setup, SEED);
        RecordingSimulationPluginSource.Recording recording = new RecordingSimulationPluginSource.Recording();
        Game[] games = new Game[1];

        RecordingSimulationPluginSource.record(recording, () -> {
            ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), ChkFile.parseTolerant(scenario), resources.getGameData(),
                                                                 live.getLocalPlayer(),
                                                                 RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                 RunningGameScenario.INSTRUCTION_LIMIT);

            games[0] = runtime.getGame();
            NetworkReplayScenario.copyCodeConstants(runtime.getGame(), resources.getGamePath());
            NetworkReplayScenario.prepare(runtime.getGame());
            runtime.setSourceFile(GameSourceFile.read(map));
            runtime.start(0, 0);

            Assertions.assertTrue(live.submitModCommand(MOD_COMMAND_DATA));

            new ReplayRunScenario(runtime, GameSpeed.FASTEST_DURATION, live::exchangeTurn).runTo(MOD_COMMAND_FRAMES);
        });

        Assertions.assertEquals(1, recording.getModCommandData().size());
        Assertions.assertArrayEquals(MOD_COMMAND_DATA, recording.getModCommandData().getFirst());
        Assertions.assertSame(games[0].getPlayerTable().getPlayer(live.getLocalPlayer()), recording.getModCommandPlayers().getFirst());

        List<ReplayCommand> recorded = ReplayFile.parse(live.writeReplay(0, 0))
                                                 .getCommands()
                                                 .stream()
                                                 .filter(command -> command.getTypeId() == ReplayCommandIds.MOD_COMMAND)
                                                 .toList();

        Assertions.assertEquals(1, recorded.size());
        Assertions.assertArrayEquals(new byte[]{(byte) MOD_COMMAND_DATA.length, 0x53, 0x43, 0x34, 0x4A}, recorded.getFirst().getPayload());
    }
}

package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerEvents;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that built-in campaign missions play through the live command path.
 */
@RequiresInstallation
final class CampaignMissionsTest {
    private static final int MAXIMUM_MISSION_FRAMES = 6000;
    private static final int CHEAT_COMMAND_FRAME = 8;
    /**
     * The Unix time of a typed line.
     */
    private static final int TYPED_TIME = 0x6543210F;
    private static final int FIRST_ATTACK_FRAME = 16;
    private static final int ATTACK_FRAME_INTERVAL = 48;
    private static final int MAXIMUM_CHEAT_FRAMES = 400;
    private static final int ORIGINAL_MISSION_FRAMES = 120;

    private static final int OUTCOME_VICTORY = 3;
    private static final int OPPONENT_PLAYER = 0;
    /**
     * The victory state of a player who won.
     */
    private static final int WON_VICTORY_STATE = 3;
    /**
     * The text result of a cheat.
     */
    private static final int TEXT_CHEAT = 1;
    /**
     * The mission indexes of Brood War Terran 1 and of the original Terran 1.
     */
    private static final int BROOD_WAR_TERRAN_ONE_MISSION = 40;
    private static final int ORIGINAL_TERRAN_ONE_MISSION = 2;
    /**
     * The header engine of a StarCraft game without the expansion.
     */
    private static final int ORIGINAL_ENGINE = 0;

    private static final @NotNull String BROOD_WAR_TERRAN_ONE = "bw-terran-1";
    private static final @NotNull String ORIGINAL_TERRAN_ONE = "terran-1";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 1790573170;

    /**
     * Selects the available nonworker army and submits a right-click on the opposing command center.
     *
     * @param game  the live command target
     * @param state the unit state to scan
     * @param human the army owner
     */
    private static void issueAttackCommand(@NotNull LiveGame game, @NotNull Game state, int human) {
        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        List<Integer> army = new ArrayList<>();
        int target = 0;

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index) || pool.getUnit(index).getSpriteOrNull() == null) {
                continue;
            }

            Unit unit = pool.getUnit(index);

            if (unit.getOwnerIndex() == OPPONENT_PLAYER && unit.getUnitId() == ScrUnitIds.TERRAN_COMMAND_CENTER && target == 0) {
                target = index;
            }

            GameData unitGameData = state.getGameData();

            Assertions.assertNotNull(unitGameData);

            boolean building = (unitGameData.getUnits().get("Special Ability Flags", unit.getUnitId()) & UnitTypeFlags.BUILDING) != 0;

            if (unit.getOwnerIndex() == human && !building && army.size() < UnitSelection.MAXIMUM_SELECTED &&
                unit.getUnitId() != ScrUnitIds.TERRAN_SCV &&
                unit.getUnitId() != ScrUnitIds.SPECIAL_MAP_REVEALER) {
                army.add(index);
            }
        }

        if (target == 0 || army.isEmpty()) {
            return;
        }

        byte[] selection = new byte[1 + army.size() * Integer.BYTES];

        selection[0] = (byte) army.size();

        for (int index = 0; index < army.size(); index++) {
            int handle = pool.getHandle(army.get(index));

            for (int shift = 0; shift < Integer.BYTES; shift++) {
                selection[1 + index * Integer.BYTES + shift] = (byte) (handle >>> (shift * Byte.SIZE));
            }
        }

        game.submit(ReplayCommandIds.SELECT_121, selection);

        Unit unit = pool.getUnit(target);
        int handle = pool.getHandle(target);

        game.submit(ReplayCommandIds.RIGHT_CLICK_121, new byte[]{
                (byte) unit.getPositionX(), (byte) (unit.getPositionX() >>> Byte.SIZE),
                (byte) unit.getPositionY(), (byte) (unit.getPositionY() >>> Byte.SIZE),
                (byte) handle, (byte) (handle >>> Byte.SIZE), (byte) (handle >>> 2 * Byte.SIZE), (byte) (handle >>> 3 * Byte.SIZE),
                (byte) unit.getUnitId(), (byte) (unit.getUnitId() >>> Byte.SIZE), 0
        });
    }


    /**
     * Plays Brood War Terran 1 to victory: with the invincibility cheat (a recorded Cheat command) the human's units are ordered onto player 1's
     * Command Center until the victory trigger (player 1 commands at most 0 Command Centers) ends the game. The interface inputs a player gives
     * between frames are supplied as the client does: Center View completes at once and each transmission completes when its duration has passed at
     * Fastest.
     */
    @Test
    void brood_war_terran_one_is_winnable_with_player_commands() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            CampaignMissions missions = CampaignMissions.load(storage);
            int mission = missions.resolve(BROOD_WAR_TERRAN_ONE);

            Assertions.assertEquals(BROOD_WAR_TERRAN_ONE_MISSION, mission);
            Assertions.assertTrue(missions.isExpansion(mission));

            byte[] mapData = missions.readScenario(storage, mission, null);
            LiveGame game = new LiveGame(MeleeGameSetup.createCampaign(mapData, PLAYER_NAME, mission, GameSpeed.FASTEST, true), GAME_SEED);
            int human = game.getLocalPlayer();
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              human,
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            Game state = live.getGame();
            int transmissionEnd = -1;
            int frame = 0;

            for (; frame < MAXIMUM_MISSION_FRAMES && game.updateOutcome(state) == 0; frame++) {
                if (frame == CHEAT_COMMAND_FRAME) {
                    GameFrame invincibilityFrame = state.getFrame();

                    Assertions.assertNotNull(invincibilityFrame);

                    Assertions.assertEquals(TEXT_CHEAT,
                                            game.submitText("power overwhelming", invincibilityFrame.getVisibility().getCheatFlags(),
                                                            state.areCheatsEnabled(), state.getRuntimeRandom(), TYPED_TIME));
                }

                if (frame > FIRST_ATTACK_FRAME && frame % ATTACK_FRAME_INTERVAL == 0) {
                    issueAttackCommand(game, state, human);
                }

                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));

                TriggerContext triggers = state.getTriggerContext();

                Assertions.assertNotNull(triggers);

                TriggerEvents events = triggers.getEvents();

                for (int event = 0; event < events.getCount(); event++) {
                    if (events.getKind(event) == TriggerEvents.CENTER_VIEW) {
                        triggers.completeCenterView();
                    }

                    if (events.getKind(event) == TriggerEvents.TRANSMISSION) {
                        transmissionEnd = frame + events.getFourth(event) / GameSpeed.FASTEST_DURATION + 1;
                    }
                }

                if (frame == transmissionEnd) {
                    triggers.completeTransmission();
                }
            }

            Assertions.assertEquals(OUTCOME_VICTORY, game.getLocalOutcome(), "no victory by loop " + frame);

            int commandCenterType = ScrUnitIds.TERRAN_COMMAND_CENTER;
            UnitPool remainingUnits = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(remainingUnits);

            int remainingCommandCenters = 0;

            for (int index = 1; index <= remainingUnits.getCapacity(); index++) {
                if (remainingUnits.isAllocated(index) && remainingUnits.getUnit(index).getOwnerIndex() == OPPONENT_PLAYER &&
                    remainingUnits.getUnit(index).getUnitId() == commandCenterType) {
                    remainingCommandCenters++;
                }
            }

            Assertions.assertTrue(remainingCommandCenters == 0);
        }
    }

    /**
     * Checks a recorded victory cheat and the different live and replay-observer result paths.
     */
    @Test
    void cow_level_cheat_wins_the_mission_but_not_for_the_replay_observer() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            CampaignMissions missions = CampaignMissions.load(storage);
            int mission = missions.resolve(BROOD_WAR_TERRAN_ONE);
            byte[] mapData = missions.readScenario(storage, mission, null);
            LiveGame game = new LiveGame(MeleeGameSetup.createCampaign(mapData, PLAYER_NAME, mission, GameSpeed.FASTEST, true), GAME_SEED);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            Game state = live.getGame();
            int frame = 0;

            for (; frame < MAXIMUM_CHEAT_FRAMES && game.updateOutcome(state) == 0; frame++) {
                if (frame == CHEAT_COMMAND_FRAME) {
                    GameFrame victoryCheatFrame = state.getFrame();

                    Assertions.assertNotNull(victoryCheatFrame);

                    Assertions.assertEquals(TEXT_CHEAT,
                                            game.submitText("there is no cow level", victoryCheatFrame.getVisibility().getCheatFlags(),
                                                            state.areCheatsEnabled(), state.getRuntimeRandom(), TYPED_TIME));
                }

                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            }

            Assertions.assertEquals(OUTCOME_VICTORY, game.getLocalOutcome(), "no victory by loop " + frame);

            Player victoriousPlayer = state.getPlayerTable().getPlayer(game.getLocalPlayer());

            Assertions.assertNotNull(victoriousPlayer);

            Assertions.assertEquals(WON_VICTORY_STATE, victoriousPlayer.getVictoryState());

            ReplayFile saved = ReplayFile.parse(game.writeReplay(state.getCurrentFrame(), 0L));
            byte[] savedMap = saved.getMapData();

            Assertions.assertNotNull(savedMap);

            ReplayRuntime playback = new ReplayRuntime(saved, ChkFile.parseTolerant(savedMap), gameData,
                                                       RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                       RunningGameScenario.INSTRUCTION_LIMIT);

            playback.start(0, 0);

            Assertions.assertNull(playback.getFailure());

            int step = 0;

            while (playback.step(GameSpeed.FASTEST_DURATION, step++ * GameSpeed.FASTEST_DURATION)) {
            }

            Assertions.assertNull(playback.getFailure());
            Assertions.assertEquals(state.getCurrentFrame(), playback.getGame().getCurrentFrame());

            Player observerPlayer = playback.getGame().getPlayerTable().getPlayer(game.getLocalPlayer());

            Assertions.assertNotNull(observerPlayer);

            Assertions.assertEquals(0, observerPlayer.getVictoryState());
        }
    }

    /**
     * Plays the original Terran 1 as StarCraft: the header engine is 0, the expansion technologies and upgrades stay unavailable for writing, and the
     * saved replay reproduces the live game while no transmission is involved.
     */
    @Test
    void original_terran_one_starts_without_expansion_and_replays_identically() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            CampaignMissions missions = CampaignMissions.load(storage);
            int mission = missions.resolve(ORIGINAL_TERRAN_ONE);

            Assertions.assertEquals(ORIGINAL_TERRAN_ONE_MISSION, mission);
            Assertions.assertFalse(missions.isExpansion(mission));

            byte[] mapData = missions.readScenario(storage, mission, null);
            LiveGame game = new LiveGame(MeleeGameSetup.createCampaign(mapData, PLAYER_NAME, mission, GameSpeed.FASTEST, false), GAME_SEED);

            Assertions.assertEquals(ORIGINAL_ENGINE, game.getReplay().getHeader().getEngine());

            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            Game state = live.getGame();

            PlayerUpgrades originalUpgrades = state.getPlayerTable().getPlayerUpgrades();

            Assertions.assertNotNull(originalUpgrades);

            Assertions.assertFalse(originalUpgrades.isExpansionEnabled());

            for (int frame = 0; frame < ORIGINAL_MISSION_FRAMES; frame++) {
                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            }

            ReplayFile saved = ReplayFile.parse(game.writeReplay(state.getCurrentFrame(), 0L));

            Assertions.assertEquals(ORIGINAL_ENGINE, saved.getHeader().getEngine());

            byte[] savedMap = saved.getMapData();

            Assertions.assertNotNull(savedMap);

            ReplayRuntime playback = new ReplayRuntime(saved, ChkFile.parseTolerant(savedMap), gameData,
                                                       RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                       RunningGameScenario.INSTRUCTION_LIMIT);

            playback.start(0, 0);

            Assertions.assertNull(playback.getFailure());

            int step = 0;

            while (playback.step(GameSpeed.FASTEST_DURATION, step++ * GameSpeed.FASTEST_DURATION)) {
            }

            Assertions.assertEquals(state.getCurrentFrame(), playback.getGame().getCurrentFrame());
            Assertions.assertEquals(state.getRandom().getState(), playback.getGame().getRandom().getState());

            UnitPool livePool = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(livePool);

            int liveAllocatedCount = livePool.getAllocatedCount();
            UnitPool replayPool = playback.getGame().getUnitSystem().getUnitPool();

            Assertions.assertNotNull(replayPool);

            Assertions.assertEquals(liveAllocatedCount, replayPool.getAllocatedCount());
        }
    }
}

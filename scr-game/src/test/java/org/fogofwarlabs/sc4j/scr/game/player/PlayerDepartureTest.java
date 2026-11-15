package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplaySettings;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.VictoryCondition;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Checks the player types that removed computer and human players get under every victory condition, the vision a removed player leaves, the shared
 * vision a vision command sets, the alliance rows an alliance command sets, and in recorded replays the relation commands, the neutralized units of
 * a leaving player and the victory of the remaining player.
 */
final class PlayerDepartureTest {
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_SIZE = 64;
    /**
     * The tileset of the test map.
     */
    private static final int TILESET = 0;
    /**
     * The race of the test players.
     */
    private static final int PLAYER_RACE = 0;
    /**
     * The team of the test players.
     */
    private static final int PLAYER_TEAM = 0;
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 1;
    /**
     * The removed player of the vision tests.
     */
    private static final int REMOVED_PLAYER = 1;
    /**
     * The vision of every player.
     */
    private static final int ALL_PLAYERS_VISION = 0xFF;
    /**
     * The removed player's own vision, the comparison mask of a game it plays.
     */
    private static final int REMOVED_PLAYER_VISION = 1 << REMOVED_PLAYER;
    /**
     * Every player's vision but the removed player's.
     */
    private static final int REMAINING_VISION = ALL_PLAYERS_VISION & ~REMOVED_PLAYER_VISION;
    /**
     * The instruction limit of a player removal.
     */
    private static final int INSTRUCTION_LIMIT = 1000;
    /**
     * The removed computer player of the player type test.
     */
    private static final int COMPUTER_PLAYER = 0;
    /**
     * The removed human player of the player type test.
     */
    private static final int HUMAN_PLAYER = 1;
    /**
     * The player type of a removed computer player.
     */
    private static final int COMPUTER_LEFT_TYPE = 11;
    /**
     * The player type of a removed human player.
     */
    private static final int PLAYER_LEFT_TYPE = 10;
    /**
     * The player type of a human player.
     */
    private static final int HUMAN_TYPE = 2;
    /**
     * The victory state of a defeated player.
     */
    private static final int DEFEATED_STATE = 2;
    /**
     * The victory state of a victorious player.
     */
    private static final int VICTORY_STATE = 3;
    /**
     * The neutral player.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The starting minerals of the game settings.
     */
    private static final int STARTING_MINERALS = 50;
    /**
     * The player that issues the vision and alliance commands.
     */
    private static final int ISSUER = 1;
    /**
     * The player that the issuer shares vision or allies with.
     */
    private static final int PARTNER = 2;
    /**
     * The issuer's own vision bit.
     */
    private static final int ISSUER_VISION = 1 << ISSUER;
    /**
     * The partner's vision bit.
     */
    private static final int PARTNER_VISION = 1 << PARTNER;
    /**
     * The shared-vision request of a command that shares with nobody.
     */
    private static final int NO_SHARED_VISION = 0;
    /**
     * A value in the issuer's vision bits {@code 8..15}, which a vision command keeps.
     */
    private static final int KEPT_VISION = 0x0300;
    /**
     * The low vision byte of the issuer before a command: its own bit and the partner's.
     */
    private static final int SHARED_LOW_VISION = ISSUER_VISION | PARTNER_VISION;
    /**
     * The allies option value that allows alliances.
     */
    private static final int ALLIES_ON = 1;
    /**
     * The allies option value that forbids alliances.
     */
    private static final int ALLIES_OFF = 0;
    /**
     * The tournament option value of a tournament game.
     */
    private static final int TOURNAMENT_ON = 1;
    /**
     * The tournament option value of a game outside tournament mode.
     */
    private static final int TOURNAMENT_OFF = 0;
    /**
     * The capacity of the unit pool.
     */
    private static final int UNIT_CAPACITY = 4;
    /**
     * The alliance value of an enemy.
     */
    private static final int ENEMY = 0;
    /**
     * The alliance value of an ally.
     */
    private static final int ALLIED = 1;
    /**
     * The bits of one player's alliance value in the recorded alliance DWORD.
     */
    private static final int ALLIANCE_BITS = 2;
    /**
     * A recorded alliance DWORD that allies the issuer with its partner.
     */
    private static final int PARTNER_ALLIANCE = ALLIED << PARTNER * ALLIANCE_BITS;
    /**
     * The slot of the player who stays in the game and wins.
     */
    private static final int VICTORY_REMAINING_SLOT = 0;
    /**
     * The slot of the recorded issuer 1, who leaves the game at frame 15970.
     */
    private static final int DEPARTING_SLOT = 3;
    /**
     * A slot without an initially active player.
     */
    private static final int INACTIVE_SLOT = 1;
    private static final int VICTORY_AFTER_DEPARTURE_FRAME = 15972;
    private static final int AFTER_VICTORY_FRAME = 16040;
    private static final int REPLAY_END_FRAME = 16054;
    /**
     * The slot of the player who leaves the game.
     */
    private static final int LEAVING_SLOT = 0;
    /**
     * The slot of the player whose relation commands the replay records.
     */
    private static final int REMAINING_SLOT = 1;
    /**
     * The shared vision mask of the remaining player's own vision.
     */
    private static final int REMAINING_PLAYER_VISION = 1 << REMAINING_SLOT;
    private static final int AFTER_RELATION_COMMANDS_FRAME = 15390;
    private static final int BEFORE_DEPARTURE_FRAME = 36517;
    private static final int AFTER_DEPARTURE_FRAME = 36520;

    /**
     * Creates a {@link Game} instance with a unit pool, player relations and sight coverage, the issuer sharing vision with its partner.
     *
     * @param replayPlayback   whether the game is a replay playback
     * @param alliesOption     the allies option of the game settings
     * @param tournamentOption the tournament option of the game settings
     * @param partnerType      the player type of the partner
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createRelationsGame(boolean replayPlayback, int alliesOption, int tournamentOption, int partnerType) {
        Game game = Game.create(null, new GameMap(MAP_SIZE, MAP_SIZE, TILESET, new int[MAP_SIZE * MAP_SIZE]), new LcgRandom(RANDOM_SEED));
        int[] playerTypes = new int[PlayerTable.PLAYER_SLOTS];

        playerTypes[ISSUER]  = PlayerTypes.HUMAN;
        playerTypes[PARTNER] = partnerType;

        PlayerRelations relations = new PlayerRelations(playerTypes, false, false, false);
        SightCoverage sight = new SightCoverage(MAP_SIZE);

        relations.setSharedVision(ISSUER, KEPT_VISION | SHARED_LOW_VISION);
        sight.setSharedVisionMasks(relations.getSharedVisionMasks());

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.getPlayerTable().setPlayerRelations(relations);
        game.getPlayerTable().setPlayer(ISSUER, new Player(ISSUER, ISSUER, PlayerTypes.HUMAN, PLAYER_RACE, PLAYER_TEAM));
        game.getPlayerTable().setPlayer(PARTNER, new Player(PARTNER, PARTNER, partnerType, PLAYER_RACE, PLAYER_TEAM));
        game.setSightCoverage(sight);
        game.setGameSettings(new ReplaySettings(0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                alliesOption, 0,
                                                0,
                                                tournamentOption,
                                                0,
                                                0, 0,
                                                new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS]));
        game.setReplayPlayback(replayPlayback);

        return game;
    }

    private static void assertSharedVision(@NotNull Game game, int expected, @NotNull String message) {
        PlayerRelations relations = game.getPlayerTable().getPlayerRelations();
        SightCoverage sight = game.getSightCoverage();

        Assertions.assertNotNull(relations);
        Assertions.assertNotNull(sight);
        Assertions.assertEquals(expected, relations.getSharedVision(ISSUER), message);
        Assertions.assertEquals(expected, sight.getSharedVisionMask(ISSUER), message);
    }

    private static void assertAlliance(@NotNull Game game, int target, int expected, @NotNull String message) {
        PlayerRelations relations = game.getPlayerTable().getPlayerRelations();

        Assertions.assertNotNull(relations);
        Assertions.assertEquals(expected, relations.getAlliance(ISSUER, target), message);
    }

    /**
     * Steps a started replay runtime at the fastest game speed until its game reaches a frame or the replay ends, checking that no step fails.
     *
     * @param runtime   the started replay runtime
     * @param frame     the frame to reach
     * @param hostTicks the host tick count before the first step
     * @return the host tick count after the last step
     */
    private static int stepToFrame(@NotNull ReplayRuntime runtime, int frame, int hostTicks) {
        Game state = runtime.getGame();
        int ticks = hostTicks;

        while (state.getCurrentFrame() < frame && runtime.step(GameSpeed.FASTEST_DURATION, ticks += GameSpeed.FASTEST_DURATION)) {
            Assertions.assertNull(runtime.getFailure());
        }

        return ticks;
    }

    /**
     * Steps a started replay runtime at the fastest game speed until the replay ends, checking that no step fails.
     *
     * @param runtime   the started replay runtime
     * @param hostTicks the host tick count before the first step
     */
    private static void stepToEnd(@NotNull ReplayRuntime runtime, int hostTicks) {
        int ticks = hostTicks;

        while (runtime.step(GameSpeed.FASTEST_DURATION, ticks += GameSpeed.FASTEST_DURATION)) {
            Assertions.assertNull(runtime.getFailure());
        }
    }


    @RequiresInstallation
    @Test
    void remove_player_should_set_departed_types_under_every_victory_condition() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (int victoryCondition : new int[]{
                    VictoryCondition.MELEE.getValue(),
                    VictoryCondition.ONE_ON_ONE.getValue(),
                    VictoryCondition.CAPTURE_THE_FLAG.getValue(),
                    VictoryCondition.SUDDEN_DEATH.getValue()
            }) {
                Game game = Game.create(data, new GameMap(MAP_SIZE, MAP_SIZE, TILESET, new int[MAP_SIZE * MAP_SIZE]), new LcgRandom(RANDOM_SEED));

                game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
                game.setGameSettings(new GameSettings(victoryCondition, 0, STARTING_MINERALS, 0, new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS], 0));
                game.getPlayerTable().setPlayer(COMPUTER_PLAYER,
                                                new Player(COMPUTER_PLAYER, COMPUTER_PLAYER, PlayerTypes.COMPUTER, PLAYER_RACE, PLAYER_TEAM));
                game.getPlayerTable().setPlayer(HUMAN_PLAYER, new Player(HUMAN_PLAYER, HUMAN_PLAYER, PlayerTypes.HUMAN, PLAYER_RACE, PLAYER_TEAM));

                game.getPlayerDeparture().removePlayer(COMPUTER_PLAYER, INSTRUCTION_LIMIT);
                game.getPlayerDeparture().removePlayer(HUMAN_PLAYER, INSTRUCTION_LIMIT);

                Player computerAfterDeparture = game.getPlayerTable().getPlayer(COMPUTER_PLAYER);

                Assertions.assertNotNull(computerAfterDeparture);

                Assertions.assertEquals(COMPUTER_LEFT_TYPE, computerAfterDeparture.getType(), "A removed computer player becomes type 11.");

                Player humanAfterDeparture = game.getPlayerTable().getPlayer(HUMAN_PLAYER);

                Assertions.assertNotNull(humanAfterDeparture);

                Assertions.assertEquals(PLAYER_LEFT_TYPE, humanAfterDeparture.getType(), "A removed human player becomes type 10.");
            }
        }
    }

    @Test
    void remove_player_should_take_the_player_out_of_the_replay_vision() {
        Game game = Game.create(null, new GameMap(MAP_SIZE, MAP_SIZE, TILESET, new int[MAP_SIZE * MAP_SIZE]), new LcgRandom(RANDOM_SEED));
        LocalVision vision = game.getLocalVision();

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.setGameSettings(new GameSettings(VictoryCondition.MELEE.getValue(),
                                              0, STARTING_MINERALS,
                                              0, new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS],
                                              0));
        game.getPlayerTable().setPlayer(REMOVED_PLAYER, new Player(REMOVED_PLAYER, REMOVED_PLAYER, PlayerTypes.HUMAN, PLAYER_RACE, PLAYER_TEAM));
        game.setReplayPlayback(true);
        vision.startReplayOrObserver();

        game.getPlayerDeparture().removePlayer(REMOVED_PLAYER, INSTRUCTION_LIMIT);

        Assertions.assertEquals(REMAINING_VISION, vision.getReplayMask());
        Assertions.assertEquals(REMAINING_VISION, vision.getComparisonMask());
    }

    @Test
    void remove_player_should_keep_the_vision_of_a_game() {
        Game game = Game.create(null, new GameMap(MAP_SIZE, MAP_SIZE, TILESET, new int[MAP_SIZE * MAP_SIZE]), new LcgRandom(RANDOM_SEED));
        LocalVision vision = game.getLocalVision();

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.setGameSettings(new GameSettings(VictoryCondition.MELEE.getValue(),
                                              0, STARTING_MINERALS,
                                              0, new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS],
                                              0));
        game.getPlayerTable().setPlayer(REMOVED_PLAYER, new Player(REMOVED_PLAYER, REMOVED_PLAYER, PlayerTypes.HUMAN, PLAYER_RACE, PLAYER_TEAM));
        vision.setComparisonMask(REMOVED_PLAYER_VISION);

        game.getPlayerDeparture().removePlayer(REMOVED_PLAYER, INSTRUCTION_LIMIT);

        Assertions.assertEquals(REMOVED_PLAYER_VISION, vision.getComparisonMask());
    }

    @Test
    void set_shared_vision_should_keep_the_issuer_own_vision() {
        Game game = createRelationsGame(true, ALLIES_OFF, TOURNAMENT_ON, PlayerTypes.HUMAN);

        game.getPlayerDeparture().setSharedVision(ISSUER, NO_SHARED_VISION);

        assertSharedVision(game, KEPT_VISION | ISSUER_VISION, "Sharing with nobody keeps the issuer's own bit and bits 8..15.");

        game.getPlayerDeparture().setSharedVision(ISSUER, PARTNER_VISION);

        assertSharedVision(game, KEPT_VISION | SHARED_LOW_VISION, "A replay shares whatever the game options are.");
    }

    @Test
    void set_shared_vision_should_ignore_a_live_command_when_alliances_are_not_allowed() {
        Game alliesOff = createRelationsGame(false, ALLIES_OFF, TOURNAMENT_OFF, PlayerTypes.HUMAN);
        Game tournament = createRelationsGame(false, ALLIES_ON, TOURNAMENT_ON, PlayerTypes.HUMAN);

        alliesOff.getPlayerDeparture().setSharedVision(ISSUER, NO_SHARED_VISION);
        tournament.getPlayerDeparture().setSharedVision(ISSUER, NO_SHARED_VISION);

        assertSharedVision(alliesOff, KEPT_VISION | ISSUER_VISION, "Sharing with nobody needs no admission.");
        assertSharedVision(tournament, KEPT_VISION | ISSUER_VISION, "Sharing with nobody needs no admission.");

        alliesOff.getPlayerDeparture().setSharedVision(ISSUER, PARTNER_VISION);
        tournament.getPlayerDeparture().setSharedVision(ISSUER, PARTNER_VISION);

        assertSharedVision(alliesOff, KEPT_VISION | ISSUER_VISION, "Without the allies option the partner is refused.");
        assertSharedVision(tournament, KEPT_VISION | ISSUER_VISION, "In tournament mode the partner is refused.");
    }

    @Test
    void set_shared_vision_should_admit_a_live_command_with_alliances_or_toward_an_observer() {
        Game allies = createRelationsGame(false, ALLIES_ON, TOURNAMENT_OFF, PlayerTypes.HUMAN);
        Game observed = createRelationsGame(false, ALLIES_OFF, TOURNAMENT_OFF, PlayerTypes.OBSERVER);

        allies.getPlayerDeparture().setSharedVision(ISSUER, NO_SHARED_VISION);
        allies.getPlayerDeparture().setSharedVision(ISSUER, PARTNER_VISION);
        observed.getPlayerDeparture().setSharedVision(ISSUER, NO_SHARED_VISION);
        observed.getPlayerDeparture().setSharedVision(ISSUER, PARTNER_VISION);

        assertSharedVision(allies, KEPT_VISION | SHARED_LOW_VISION, "The allies option admits the partner.");
        assertSharedVision(observed, KEPT_VISION | SHARED_LOW_VISION, "An observer partner is always admitted.");
    }

    @Test
    void set_alliances_should_ignore_the_command_unless_alliances_are_allowed_even_in_a_replay() {
        Game alliesOff = createRelationsGame(true, ALLIES_OFF, TOURNAMENT_OFF, PlayerTypes.HUMAN);
        Game tournament = createRelationsGame(true, ALLIES_ON, TOURNAMENT_ON, PlayerTypes.HUMAN);
        Game allies = createRelationsGame(true, ALLIES_ON, TOURNAMENT_OFF, PlayerTypes.HUMAN);

        alliesOff.getPlayerDeparture().setAlliances(ISSUER, PARTNER_ALLIANCE);
        tournament.getPlayerDeparture().setAlliances(ISSUER, PARTNER_ALLIANCE);
        allies.getPlayerDeparture().setAlliances(ISSUER, PARTNER_ALLIANCE);

        assertAlliance(alliesOff, PARTNER, ENEMY, "Without the allies option the command is ignored.");
        assertAlliance(alliesOff, ISSUER, ALLIED, "Without the allies option the row stays unchanged.");
        assertAlliance(tournament, PARTNER, ENEMY, "In tournament mode the command is ignored.");
        assertAlliance(allies, PARTNER, ALLIED, "With the allies option the recorded row applies.");
        assertAlliance(allies, ISSUER, ENEMY, "The recorded row replaces the whole row.");
    }

    @RequiresInstallation
    @Test
    void replay_awards_the_remaining_player_victory_and_continues_to_its_end() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Game state = runtime.getGame();
            int hostTicks = 0;

            hostTicks = stepToFrame(runtime, VICTORY_AFTER_DEPARTURE_FRAME, hostTicks);

            Player departedPlayer = state.getPlayerTable().getPlayer(DEPARTING_SLOT);

            Assertions.assertNotNull(departedPlayer);
            Assertions.assertEquals(DEFEATED_STATE, departedPlayer.getVictoryState(), "Recorded issuer 1 (slot 3) left at frame 15970.");
            Assertions.assertEquals(PLAYER_LEFT_TYPE, departedPlayer.getType());

            Player remainingPlayer = state.getPlayerTable().getPlayer(VICTORY_REMAINING_SLOT);

            Assertions.assertNotNull(remainingPlayer);
            Assertions.assertEquals(0, remainingPlayer.getVictoryState());

            TriggerContext triggers = state.getTriggerContext();

            Assertions.assertNotNull(triggers);
            Assertions.assertTrue(triggers.isInitiallyActive(VICTORY_REMAINING_SLOT));
            Assertions.assertTrue(triggers.isInitiallyActive(DEPARTING_SLOT));
            Assertions.assertFalse(triggers.isInitiallyActive(INACTIVE_SLOT));

            hostTicks = stepToFrame(runtime, AFTER_VICTORY_FRAME, hostTicks);

            Player victoriousPlayer = state.getPlayerTable().getPlayer(VICTORY_REMAINING_SLOT);

            Assertions.assertNotNull(victoriousPlayer);
            Assertions.assertEquals(VICTORY_STATE, victoriousPlayer.getVictoryState(), "The melee Opponents trigger awards Victory.");

            Player departedPlayerAfterVictory = state.getPlayerTable().getPlayer(DEPARTING_SLOT);

            Assertions.assertNotNull(departedPlayerAfterVictory);
            Assertions.assertEquals(DEFEATED_STATE, departedPlayerAfterVictory.getVictoryState(), "An existing victory state is not overwritten.");
            Assertions.assertTrue(triggers.getPlayerRecordCount(VICTORY_REMAINING_SLOT) >= 1, "Without the visibility override the records remain.");

            stepToEnd(runtime, hostTicks);

            ReplaySession session = runtime.getSession();

            Assertions.assertNotNull(session);
            Assertions.assertTrue(session.isFinished());
            Assertions.assertEquals(REPLAY_END_FRAME, state.getCurrentFrame(), "A player's victory does not stop replay simulation.");
        }
    }

    @RequiresInstallation
    @Test
    void replay_applies_relation_commands_and_neutralizes_the_leaving_player() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVZ_HEARTBREAK_RIDGE)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Game state = runtime.getGame();
            int hostTicks = 0;

            hostTicks = stepToFrame(runtime, AFTER_RELATION_COMMANDS_FRAME, hostTicks);

            PlayerRelations relations = state.getPlayerTable().getPlayerRelations();

            Assertions.assertNotNull(relations);
            Assertions.assertEquals(ALLIED, relations.getAlliance(REMAINING_SLOT, REMAINING_SLOT),
                                    "The allies option is off, so the alliance command 0x00400004 is ignored and the row keeps self and neutral.");
            Assertions.assertEquals(ALLIED, relations.getAlliance(REMAINING_SLOT, PlayerTable.NEUTRAL_PLAYER_ID));
            Assertions.assertEquals(ENEMY, relations.getAlliance(REMAINING_SLOT, LEAVING_SLOT));
            Assertions.assertEquals(REMAINING_PLAYER_VISION, relations.getSharedVision(REMAINING_SLOT),
                                    "The recorded vision WORD 0x0002 is player 1's own vision.");

            SightCoverage sightCoverage = state.getSightCoverage();

            Assertions.assertNotNull(sightCoverage);
            Assertions.assertEquals(REMAINING_PLAYER_VISION, sightCoverage.getSharedVisionMask(REMAINING_SLOT));

            Player leaving = state.getPlayerTable().getPlayer(LEAVING_SLOT);

            Assertions.assertNotNull(leaving);

            UnitPool pool = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            int neutralBefore = 0;

            for (int index = pool.getFirstOwnerIndex(PlayerTable.NEUTRAL_PLAYER_ID); index != 0; index = pool.getNextOwnerIndex(index)) {
                neutralBefore++;
            }

            hostTicks = stepToFrame(runtime, BEFORE_DEPARTURE_FRAME, hostTicks);

            int ownedBefore = 0;

            for (int index = pool.getFirstOwnerIndex(LEAVING_SLOT); index != 0; index = pool.getNextOwnerIndex(index)) {
                ownedBefore++;
            }

            Assertions.assertTrue(ownedBefore > 0);
            Assertions.assertEquals(0, leaving.getVictoryState());
            Assertions.assertEquals(HUMAN_TYPE, leaving.getType(), "The leaving player is a human.");

            hostTicks = stepToFrame(runtime, AFTER_DEPARTURE_FRAME, hostTicks);

            Assertions.assertEquals(DEFEATED_STATE, leaving.getVictoryState(), "Reason 1 is a departure, not a drop.");
            Assertions.assertEquals(PLAYER_LEFT_TYPE, leaving.getType(), "The leaving human becomes a departed player.");
            Assertions.assertEquals(0, pool.getFirstOwnerIndex(LEAVING_SLOT), "Every unit of the leaving player is neutral.");

            int neutralAfter = 0;
            int keptSpriteOwner = 0;

            for (int index = pool.getFirstOwnerIndex(PlayerTable.NEUTRAL_PLAYER_ID); index != 0; index = pool.getNextOwnerIndex(index)) {
                neutralAfter++;

                Unit unit = pool.getUnit(index);

                Assertions.assertEquals(NEUTRAL_PLAYER, unit.getOwnerIndex());

                Sprite sprite = unit.getSpriteOrNull();

                if (sprite != null && sprite.getOwner() == LEAVING_SLOT) {
                    keptSpriteOwner++;
                }
            }

            Assertions.assertTrue(neutralAfter >= neutralBefore + 1);
            Assertions.assertTrue(keptSpriteOwner > 0, "Giving units to player 11 keeps their sprite owner.");

            stepToEnd(runtime, hostTicks);

            ReplaySession session = runtime.getSession();

            Assertions.assertNotNull(session);
            Assertions.assertTrue(session.isFinished(), "The replay continues to its last frame after the departure.");
        }
    }
}

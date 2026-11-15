package org.fogofwarlabs.sc4j.scr.client.glue.score;

import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.presentation.InterfaceTimers;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks score fill, sort, replay buttons and actions-per-minute data, and the score rows of departed players,
 * multiplayer computer names and force labels collected from game state.
 */
final class ScoreScreenTest {
    private static final long SELECT_TIME = 1000;
    /**
     * A pump interval just past the {@code 20} ms fill timer period, so that every pump fires the timer once.
     */
    private static final int PUMP_INTERVAL = 21;
    private static final int MAXIMUM_PUMPS = 250;
    private static final int TAB_CHANGE_DELAY = 100;

    /**
     * The local row's first two Overview values: a large value counted in whole units and a small one in sixteenths.
     */
    private static final int LARGE_VALUE = 1000;
    private static final int SMALL_VALUE = 10;
    private static final int FILL_DIVISOR = 8;
    private static final int FINEST_MULTIPLIER = 16;
    private static final int LARGE_FIRST_STEP = 125;
    /**
     * The shown row of the winning local player and the Overview columns of the large and the small value.
     */
    private static final int LOCAL_SHOWN_ROW = 0;
    private static final int LARGE_VALUE_COLUMN = 0;
    private static final int SMALL_VALUE_COLUMN = 1;

    private static final int LOCAL_PLAYER = 0;
    private static final int LOCAL_RACE = 1;
    private static final long ELAPSED_SECONDS = 90;

    private static final int PLAYER_ID_COUNT = 256;
    private static final int COMMAND_PLAYER_ID = 3;
    private static final int COMMAND_SLOT = 1;
    private static final int COMMAND_COUNT = 150;
    private static final int COMMAND_TYPE = 0x14;
    private static final long ELAPSED_MILLISECONDS = 120_000;
    private static final int EXPECTED_ACTIONS_PER_MINUTE = 75;
    /**
     * A slot without recorded commands.
     */
    private static final int IDLE_SLOT = 0;
    /**
     * The replay a multiplayer game writes, which enables Watch Replay.
     */
    private static final @NotNull String WATCHED_REPLAY_FILE = "LastReplay.rep";

    private static final int FASTEST_SPEED = 6;
    private static final int SETTLE_FRAMES = 60;
    private static final int LEFT_HUMAN = PlayerTypes.PLAYER_LEFT;
    private static final int REMOVED_COMPUTER = PlayerTypes.COMPUTER_LEFT;
    private static final @NotNull String HUMAN_NAME = "Human";
    private static final @NotNull String COMPUTER_NAME = "Computer";
    /**
     * The slot name of a single-player computer, which takes no row, and the force name of a row outside UMS games.
     */
    private static final @NotNull String NO_SLOT_NAME = "";
    private static final @NotNull String NO_FORCE_NAME = "";
    /**
     * The campaign mission the score rows test plays, the name it starts with and the locale.
     */
    private static final @NotNull String CAMPAIGN_MISSION = "zerg-2";
    private static final @NotNull String PLAYER_NAME = "Tester";
    private static final @NotNull String LOCALE = "enUS";
    /**
     * The number of forces whose names a game can show.
     */
    private static final int FORCE_COUNT = 4;
    private static final @NotNull String @NotNull [] FORCE_NAMES = {"North", "South", "East", "West"};

    /**
     * Creates a {@link ScoreScreen} instance with fixed rows and an event-recording fill listener.
     *
     * @param victory whether the local player won
     * @param events  the fill events to append
     * @return the created {@link ScoreScreen} instance
     */
    private static @NotNull ScoreScreen createScreen(boolean victory, @NotNull List<String> events) {
        List<ScoreScreen.Row> rows = List.of(
                new ScoreScreen.Row(LOCAL_PLAYER, "Local", LOCAL_RACE, 0, false,
                                    new long[][]{{LARGE_VALUE, SMALL_VALUE, 0, LARGE_VALUE + SMALL_VALUE}, {5, 1, 0, 5}, {0, 0, 0, 0}, {0, 0, 0, 0}}),
                new ScoreScreen.Row(1, "First", 0, 1, true, new long[][]{{500, 20, 0, 520}, {3, 1, 0, 3}, {0, 0, 0, 0}, {0, 0, 0, 0}}),
                new ScoreScreen.Row(2, "Second", 2, 2, true, new long[][]{{100, 30, 0, 130}, {7, 1, 0, 7}, {0, 0, 0, 0}, {0, 0, 0, 0}})
        );
        ScoreScreen score = new ScoreScreen(rows, LOCAL_PLAYER, victory, LOCAL_RACE, ELAPSED_SECONDS);

        score.setFillListener(new ScoreScreen.FillListener() {
            /**
             * Called when counting starts for the selected tab.
             * <p>
             * Records the start event with the counting flag.
             *
             * @param counting whether counting audio should play
             */
            @Override
            public void onFillStarted(boolean counting) {
                events.add("start " + counting);
            }

            /**
             * Called when every displayed counter has reached its target.
             * <p>
             * Records the end event.
             */
            @Override
            public void onFillEnded() {
                events.add("end");
            }
        });

        return score;
    }

    /**
     * Finds the first score row for the requested player.
     *
     * @param rows   the collected score rows
     * @param player the requested player
     * @return the matching row, or {@code null} if the player has no row
     */
    private static ScoreScreen.@Nullable Row findPlayerRow(@NotNull List<ScoreScreen.Row> rows, int player) {
        for (ScoreScreen.Row row : rows) {
            if (row.getPlayer() == player) {
                return row;
            }
        }

        return null;
    }

    @Test
    void score_fill_counts_in_scaled_steps_and_ends_with_fill_end_event() {
        List<String> events = new ArrayList<>();
        ScoreScreen score = createScreen(true, events);
        InterfaceTimers timers = new InterfaceTimers();

        score.setTimers(timers);
        score.selectTab(ScoreScreen.Tab.OVERVIEW, SELECT_TIME);

        Assertions.assertEquals("start true", events.getFirst());

        ScoreScreen.Counter large = score.getCounter(LOCAL_SHOWN_ROW, LARGE_VALUE_COLUMN);

        Assertions.assertFalse(large.isShown(), "the text is written by the first tick");
        // The timer fires when the pump's clock is past its interval, one step per pump.
        timers.pump((int) SELECT_TIME + PUMP_INTERVAL);

        Assertions.assertTrue(large.isShown());
        Assertions.assertEquals(LARGE_FIRST_STEP, large.getValue(), "1000 / 8");
        timers.pump((int) SELECT_TIME + 2 * PUMP_INTERVAL);

        Assertions.assertEquals(LARGE_FIRST_STEP + (LARGE_VALUE - LARGE_FIRST_STEP) / FILL_DIVISOR, large.getValue());

        ScoreScreen.Counter small = score.getCounter(LOCAL_SHOWN_ROW, SMALL_VALUE_COLUMN);
        int smallScaled = SMALL_VALUE * FINEST_MULTIPLIER;

        Assertions.assertEquals((smallScaled / FILL_DIVISOR + (smallScaled - smallScaled / FILL_DIVISOR) / FILL_DIVISOR) / FINEST_MULTIPLIER,
                                small.getValue(),
                                "10 counts in sixteenths");

        for (int pump = 1; pump <= MAXIMUM_PUMPS; pump++) {
            timers.pump((int) SELECT_TIME + 2 * PUMP_INTERVAL + PUMP_INTERVAL * pump);
        }

        Assertions.assertFalse(score.isFilling());
        Assertions.assertEquals(LARGE_VALUE, large.getValue());
        Assertions.assertEquals(SMALL_VALUE, small.getValue());
        Assertions.assertEquals("end", events.getLast());
    }

    @Test
    void select_tab_should_sort_losing_local_player_last_and_keep_column_maximum() {
        ScoreScreen score = createScreen(false, new ArrayList<>());

        score.selectTab(ScoreScreen.Tab.OVERVIEW, SELECT_TIME);

        List<ScoreScreen.Row> shown = score.getShownRows();

        Assertions.assertEquals(List.of(1, 2, 0), shown.stream().map(ScoreScreen.Row::getPlayer).toList());
        Assertions.assertEquals(LARGE_VALUE, score.getColumnMaximum(LARGE_VALUE_COLUMN));
        score.selectTab(ScoreScreen.Tab.UNITS, SELECT_TIME + TAB_CHANGE_DELAY);

        Assertions.assertEquals(ScoreScreen.Tab.UNITS, score.getTab());
        Assertions.assertEquals(List.of(2, 1, 0), score.getShownRows().stream().map(ScoreScreen.Row::getPlayer).toList());
    }

    @Test
    void replay_buttons_disable_watch_in_single_player_and_save_after_saving() {
        ScoreScreen score = createScreen(true, new ArrayList<>());

        Assertions.assertFalse(score.isEnabled(ScoreScreen.WATCH_REPLAY_CONTROL), "Watch Replay is disabled in single player");
        Assertions.assertTrue(score.isEnabled(ScoreScreen.SAVE_REPLAY_CONTROL));
        score.setReplaySaved();

        Assertions.assertFalse(score.isEnabled(ScoreScreen.SAVE_REPLAY_CONTROL));
        // A multiplayer game's written replay enables Watch Replay.
        score.setWatchedReplay(Path.of(WATCHED_REPLAY_FILE));

        Assertions.assertTrue(score.isEnabled(ScoreScreen.WATCH_REPLAY_CONTROL));
    }

    @Test
    void compute_actions_per_minute_should_average_over_game_time() {
        int[] slots = new int[PLAYER_ID_COUNT];

        Arrays.fill(slots, -1);
        slots[COMMAND_PLAYER_ID] = COMMAND_SLOT;

        List<ReplayCommand> commands = new ArrayList<>();

        for (int index = 0; index < COMMAND_COUNT; index++) {
            commands.add(new ReplayCommand(index, COMMAND_PLAYER_ID, COMMAND_TYPE, new byte[0]));
        }

        int[] actionsPerMinute = ScoreScreen.computeActionsPerMinute(commands, slots, ELAPSED_MILLISECONDS);

        Assertions.assertEquals(EXPECTED_ACTIONS_PER_MINUTE, actionsPerMinute[COMMAND_SLOT], "150 actions in 2 minutes");
        Assertions.assertEquals(0, actionsPerMinute[IDLE_SLOT]);
    }

    @RequiresInstallation
    @Test
    void collect_rows_should_keep_left_and_removed_players_with_force_names() throws IOException {
        try (ClientSession session = ClientSession.startCampaign(ExternalInput.INSTALLATION.getPath(),
                                                                 CAMPAIGN_MISSION, PLAYER_NAME, FASTEST_SPEED, ClientSessionScenario.GAME_SEED,
                                                                 LOCALE)) {
            session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
            session.startReplay();

            for (int frame = 0; frame < SETTLE_FRAMES; frame++) {
                session.stepFrame(false);
            }

            Game game = session.getGame();
            int human = session.getLocalPlayer();
            int computer = -1;

            for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
                Player record = game.getPlayerTable().getPlayer(slot);

                if (record != null && record.getType() == PlayerTypes.COMPUTER && computer < 0) {
                    computer = slot;
                }
            }

            Assertions.assertTrue(computer >= 0, "the mission has a computer player");

            String[] names = new String[PlayerTable.PLAYABLE_PLAYER_SLOTS];

            names[human]    = HUMAN_NAME;
            names[computer] = NO_SLOT_NAME;

            // Single player: a computer without a slot name takes no row; no force names outside UMS.
            List<ScoreScreen.Row> rows = ScoreScreen.collectRows(new ScrGameView(game), names, COMPUTER_NAME, false, null);

            Assertions.assertNotNull(findPlayerRow(rows, human));
            Assertions.assertNull(findPlayerRow(rows, computer));

            ScoreScreen.Row forceRow = findPlayerRow(rows, human);

            Assertions.assertNotNull(forceRow);
            Assertions.assertEquals(NO_FORCE_NAME, forceRow.getForceName());

            // A computer removed from play (type 11) and a human who left (type 10) keep their rows.
            Player departedComputer = game.getPlayerTable().getPlayer(computer);

            Assertions.assertNotNull(departedComputer);

            departedComputer.setType(REMOVED_COMPUTER);

            Player departedHuman = game.getPlayerTable().getPlayer(human);

            Assertions.assertNotNull(departedHuman);

            departedHuman.setType(LEFT_HUMAN);
            rows = ScoreScreen.collectRows(new ScrGameView(game), names, COMPUTER_NAME, true, FORCE_NAMES);

            ScoreScreen.Row computerRow = findPlayerRow(rows, computer);
            ScoreScreen.Row humanRow = findPlayerRow(rows, human);

            Assertions.assertNotNull(computerRow);
            Assertions.assertNotNull(humanRow);
            Assertions.assertEquals(COMPUTER_NAME, computerRow.getName(), "multiplayer computers show strCOMPUTER");
            Assertions.assertTrue(computerRow.isComputer(), "type 11 takes no team bonus");
            Assertions.assertEquals(HUMAN_NAME, humanRow.getName());

            Player teamPlayer = game.getPlayerTable().getPlayer(human);

            Assertions.assertNotNull(teamPlayer);

            int team = teamPlayer.getTeam();

            Assertions.assertEquals(team >= 1 && team <= FORCE_COUNT ? FORCE_NAMES[team - 1] : NO_FORCE_NAME, humanRow.getForceName());
        }
    }
}

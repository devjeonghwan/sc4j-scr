package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplaySettings;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.presentation.MusicRotation;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks replay mode initialization, retained random-state inputs, the start of an observer and the local player of replay
 * playback.
 */
final class ReplayInitializationTest {
    private static final int NO_STORM_ID = -1;

    /**
     * The local player value that names no player: the playable slot count.
     */
    private static final int NO_LOCAL_PLAYER = 8;
    /**
     * The slot of the only human in the campaign header fixture.
     */
    private static final int CAMPAIGN_HUMAN_SLOT = 5;

    /**
     * The engine, map size and subtype of the header fixture.
     */
    private static final int BROOD_WAR_ENGINE = 1;
    private static final int FIXTURE_MAP_SIZE = 64;
    private static final int FIXTURE_SUBTYPE = 1;

    /**
     * The victory condition and starting-unit options the one-on-one replay records.
     */
    private static final int ONE_ON_ONE_VICTORY_CONDITION = 7;
    private static final int ONE_ON_ONE_STARTING_UNITS = 2;

    /**
     * The seed of the established random source and the source it draws from.
     */
    private static final int ESTABLISHED_SEED = 0x24681357;
    private static final int ESTABLISHED_SOURCE = 7;

    /**
     * The runtime random draws of the replay initialization and the random state they leave.
     */
    private static final int RUNTIME_RANDOM_COUNT = 3;
    private static final int RUNTIME_RANDOM_STATE = 0x40E4233C;

    /**
     * The campaign mission of a non-campaign game and of a campaign game.
     */
    private static final int NO_CAMPAIGN_MISSION = 0;
    private static final int CAMPAIGN_MISSION = 1;
    /**
     * The start-up draws of the two-human fixture header (seed {@code 0}: values 38, 7719, 21238): the first human picked for
     * the music, and the rotation the third value starts.
     */
    private static final int MUSIC_PLAYER = 0;
    private static final int NO_MUSIC_PLAYER = -1;
    private static final int REPLAY_START_DRAWS = 3;
    private static final int HUMAN_DRAWS = 2;
    private static final int LIVE_START_DRAWS = 1;
    private static final int FIRST_ROTATION = 3;
    private static final int SECOND_ROTATION = 0;
    private static final int NO_ROTATION = -1;
    /**
     * The local comparison mask of the first observer slot until its vision starts: the shift keeps the slot's low five bits.
     */
    private static final int OBSERVER_COMPARISON_MASK = 1;
    /**
     * The slot of the live player in the music race fixture.
     */
    private static final int LIVE_PLAYER_SLOT = 2;

    /**
     * The game type of a one-on-one game.
     */
    private static final int ONE_ON_ONE_GAME_TYPE = 4;
    /**
     * The game type of a Use Map Settings game.
     */
    private static final int USE_MAP_SETTINGS_GAME_TYPE = 10;
    /**
     * The frame duration of the fast game speed, in milliseconds.
     */
    private static final int FAST_FRAME_DURATION = 56;

    /**
     * Creates a {@link ReplayHeader} instance with the given player types and issuer identities.
     *
     * @param types           the player type inputs
     * @param stormIds        the recorded issuer identities
     * @param campaignMission the campaign mission, {@code 0} outside campaigns
     * @return the created {@link ReplayHeader} instance
     */
    private static @NotNull ReplayHeader createHeader(int @NotNull [] types, int @NotNull [] stormIds, int campaignMission) {
        List<ReplayHeader.ReplayPlayer> players = new ArrayList<>();

        for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
            int playerType = slot < types.length ? types[slot] : PlayerTypes.NONE;
            int stormId = slot < stormIds.length ? stormIds[slot] : NO_STORM_ID;

            players.add(new ReplayHeader.ReplayPlayer(slot, stormId, playerType, 0, 0, ""));
        }

        ReplaySettings settings = new ReplaySettings(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        return new ReplayHeader(BROOD_WAR_ENGINE, 0, 0, "",
                                FIXTURE_MAP_SIZE, FIXTURE_MAP_SIZE, PlayerTable.PLAYABLE_PLAYER_SLOTS, GameSpeed.FASTEST,
                                GameTemplate.TYPE_USE_MAP_SETTINGS, FIXTURE_SUBTYPE,
                                "", "",
                                players, new int[PlayerTable.PLAYABLE_PLAYER_SLOTS], settings,
                                campaignMission);
    }

    @Test
    void fresh_replay_accepts_one_on_one_settings_and_does_not_treat_use_map_settings_as_melee() throws IOException {
        ReplayFile oneOnOne = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVZ_HEARTBREAK_RIDGE)));

        Assertions.assertEquals(ONE_ON_ONE_GAME_TYPE, oneOnOne.getHeader().getType());
        Assertions.assertEquals(ONE_ON_ONE_VICTORY_CONDITION, oneOnOne.getHeader().getSettings().getVictoryCondition());
        Assertions.assertEquals(ONE_ON_ONE_STARTING_UNITS, oneOnOne.getHeader().getSettings().getCreateInitialUnits());
        Assertions.assertFalse(oneOnOne.getHeader().getSettings().usesMapSettings());

        ReplayInitialization initialization = ReplayInitialization.createFreshReplay(oneOnOne.getHeader());

        Assertions.assertEquals((int) oneOnOne.getHeader().getRandomSeed(), initialization.getRandom().getState());
        Assertions.assertEquals(0, initialization.getRandom().getTotalRandomCounts());

        ReplayFile useMapSettings = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_UMS_DESTINATION)));

        Assertions.assertTrue(useMapSettings.getHeader().getSettings().usesMapSettings());
        // Use Map Settings is initialized through its own map-settings path, not rejected.
        Assertions.assertEquals(useMapSettings.getHeader().getType() == USE_MAP_SETTINGS_GAME_TYPE,
                                ReplayInitialization.isSupportedGameMode(useMapSettings.getHeader()));
    }

    @Test
    void replay_initialization_applies_fresh_reset_and_runtime_draws_and_rejects_disabled_random() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));
        LcgRandom random = new LcgRandom(ESTABLISHED_SEED);

        random.next(ESTABLISHED_SOURCE);

        int establishedState = random.getState();
        ReplayInitialization initialization = new ReplayInitialization(replay.getHeader(), random);

        Assertions.assertEquals(NO_LOCAL_PLAYER, initialization.getLocalPlayer());
        Assertions.assertEquals(0, initialization.getComparisonMask(), "A replay starts its vision after the world is created.");
        Assertions.assertEquals(RUNTIME_RANDOM_COUNT, initialization.getRuntimeRandom().getDrawCount());
        Assertions.assertEquals(RUNTIME_RANDOM_STATE, initialization.getRuntimeRandom().getState());
        Assertions.assertEquals(FAST_FRAME_DURATION, initialization.getFrameTiming().getFrameDuration());
        Assertions.assertEquals(NO_LOCAL_PLAYER, initialization.getCreationContext().getLocalPlayer());
        Assertions.assertTrue(initialization.getCreationContext().isCompletionRefreshSuppressed());
        Assertions.assertFalse(initialization.getCreationContext().isBuildingRefreshEnabled());
        Assertions.assertSame(random, initialization.getRandom());
        Assertions.assertEquals(establishedState, random.getState());
        Assertions.assertEquals(1, random.getCallCount(ESTABLISHED_SOURCE));

        ReplayInitialization fresh = ReplayInitialization.createFreshReplay(replay.getHeader());

        Assertions.assertEquals((int) replay.getHeader().getRandomSeed(), fresh.getRandom().getState());
        Assertions.assertEquals(1, fresh.getRandom().getEnabled());

        for (int source = 0; source < LcgRandom.SOURCE_COUNT; source++) {
            Assertions.assertEquals(0, fresh.getRandom().getCallCount(source));
        }

        random.setEnabled(0);

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ReplayInitialization(replay.getHeader(), random));
    }

    @Test
    void replay_start_draws_per_human_and_the_first_music_rotation_of_the_process() {
        ReplayHeader header = createHeader(new int[]{PlayerTypes.HUMAN, PlayerTypes.HUMAN}, new int[]{0, 1}, NO_CAMPAIGN_MISSION);
        MusicRotation rotation = new MusicRotation();
        ReplayInitialization first = ReplayInitialization.createFreshReplay(header, 0, rotation);

        Assertions.assertEquals(REPLAY_START_DRAWS, first.getRuntimeRandom().getDrawCount());
        Assertions.assertEquals(MUSIC_PLAYER, first.getMusicPlayer());
        Assertions.assertEquals(FIRST_ROTATION, rotation.getRotation());

        ReplayInitialization second = ReplayInitialization.createFreshReplay(header, 0, rotation);

        Assertions.assertEquals(HUMAN_DRAWS, second.getRuntimeRandom().getDrawCount(), "a later game of the process draws no rotation");
        Assertions.assertEquals(SECOND_ROTATION, rotation.getRotation());
    }

    @Test
    void live_start_draws_only_the_music_rotation() {
        ReplayHeader header = createHeader(new int[]{PlayerTypes.HUMAN, PlayerTypes.HUMAN}, new int[]{0, 1}, NO_CAMPAIGN_MISSION);
        MusicRotation rotation = new MusicRotation();
        ReplayInitialization live = ReplayInitialization.createLiveGame(header, 0, false, 0, rotation);

        Assertions.assertEquals(LIVE_START_DRAWS, live.getRuntimeRandom().getDrawCount(), "a live player picks no human");
        Assertions.assertEquals(NO_MUSIC_PLAYER, live.getMusicPlayer());
        Assertions.assertEquals(FIRST_ROTATION, rotation.getRotation(), "the first draw 38 starts the rotation at 3");
    }

    @Test
    void observer_start_draws_per_human_and_starts_on_the_first_human_as_a_replay_start() {
        ReplayHeader header = createHeader(new int[]{PlayerTypes.HUMAN, PlayerTypes.HUMAN}, new int[]{0, 1}, NO_CAMPAIGN_MISSION);
        MusicRotation rotation = new MusicRotation();
        ReplayInitialization observer = ReplayInitialization.createLiveGame(header, PlayerTable.FIRST_OBSERVER_SLOT, true, 0, rotation);

        Assertions.assertTrue(observer.isLive());
        Assertions.assertTrue(observer.isReplayOrObserver());
        Assertions.assertEquals(PlayerTable.FIRST_OBSERVER_SLOT, observer.getLocalPlayer());
        Assertions.assertEquals(OBSERVER_COMPARISON_MASK, observer.getComparisonMask());
        Assertions.assertEquals(REPLAY_START_DRAWS, observer.getRuntimeRandom().getDrawCount(), "an observer draws once per human");
        Assertions.assertEquals(MUSIC_PLAYER, observer.getMusicPlayer());
        Assertions.assertEquals(FIRST_ROTATION, rotation.getRotation());
        Assertions.assertEquals(MUSIC_PLAYER, observer.findStartScreenPlayer());
        Assertions.assertFalse(ReplayInitialization.createLiveGame(header, 1, true, 0, new MusicRotation()).isReplayOrObserver());
    }

    @Test
    void find_music_race_should_take_the_terran_console_race_of_a_viewer_start_without_humans() {
        ReplayHeader header = createHeader(new int[]{PlayerTypes.COMPUTER, PlayerTypes.COMPUTER, PlayerTypes.HUMAN},
                                           new int[]{NO_STORM_ID, NO_STORM_ID, 0},
                                           NO_CAMPAIGN_MISSION);
        ReplayHeader computers = createHeader(new int[]{PlayerTypes.COMPUTER, PlayerTypes.COMPUTER}, new int[]{0, 1}, NO_CAMPAIGN_MISSION);
        PlayerTable players = new PlayerTable(null);

        players.setPlayer(LIVE_PLAYER_SLOT, new Player(LIVE_PLAYER_SLOT, 0, PlayerTypes.HUMAN, ScrRaceIds.PROTOSS, 0));

        Assertions.assertEquals(ScrRaceIds.TERRAN, ReplayInitialization.createFreshReplay(computers).findMusicRace(players));
        Assertions.assertEquals(ScrRaceIds.TERRAN,
                                ReplayInitialization.createLiveGame(computers, PlayerTable.FIRST_OBSERVER_SLOT, true).findMusicRace(players));
        Assertions.assertEquals(ScrRaceIds.PROTOSS, ReplayInitialization.createLiveGame(header, LIVE_PLAYER_SLOT).findMusicRace(players),
                                "a live player's console race is its own");
        Assertions.assertEquals(ScrRaceIds.PROTOSS, ReplayInitialization.createFreshReplay(header).findMusicRace(players),
                                "the start-up draws pick the only human");
    }

    @Test
    void campaign_start_draws_no_music_rotation() {
        ReplayHeader header = createHeader(new int[]{PlayerTypes.HUMAN, PlayerTypes.HUMAN}, new int[]{0, 1}, CAMPAIGN_MISSION);
        MusicRotation rotation = new MusicRotation();
        ReplayInitialization campaign = ReplayInitialization.createFreshReplay(header, 0, rotation);

        Assertions.assertEquals(HUMAN_DRAWS, campaign.getRuntimeRandom().getDrawCount());
        Assertions.assertEquals(NO_ROTATION, rotation.getRotation(), "a campaign start leaves the rotation");
    }

    @Test
    void find_native_replay_local_player_should_pick_first_human_despite_host_storm_id() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.COMPUTER, PlayerTypes.COMPUTER};
        int[] stormIds = {1, 0, NO_STORM_ID, NO_STORM_ID};

        Assertions.assertEquals(0, ReplayInitialization.findNativeReplayLocalPlayer(createHeader(types, stormIds, NO_CAMPAIGN_MISSION)));
    }

    @Test
    void find_native_replay_local_player_should_pick_campaign_human_in_later_slot() {
        int[] types = {PlayerTypes.COMPUTER, PlayerTypes.NEUTRAL, PlayerTypes.RESCUE_PASSIVE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.HUMAN};
        int[] stormIds = {NO_STORM_ID, NO_STORM_ID, NO_STORM_ID, NO_STORM_ID, NO_STORM_ID, 0};

        Assertions.assertEquals(CAMPAIGN_HUMAN_SLOT,
                                ReplayInitialization.findNativeReplayLocalPlayer(createHeader(types, stormIds, NO_CAMPAIGN_MISSION)));
    }

    @Test
    void find_native_replay_local_player_should_return_no_player_without_humans() {
        int[] types = {PlayerTypes.COMPUTER, PlayerTypes.COMPUTER};

        Assertions.assertEquals(NO_LOCAL_PLAYER,
                                ReplayInitialization.findNativeReplayLocalPlayer(createHeader(types, new int[]{0, 3}, NO_CAMPAIGN_MISSION)));
    }
}

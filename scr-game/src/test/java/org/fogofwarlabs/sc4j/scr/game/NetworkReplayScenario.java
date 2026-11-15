package org.fogofwarlabs.sc4j.scr.game;

import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ButtonSetIds;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines.StatusTexts;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayCommandObserver;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.OutgoingTurnBuffer;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.NativeCodeConstants;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalInterfaceState;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays LAN fixtures and collects resource synchronization words.
 */
public final class NetworkReplayScenario {
    /**
     * The divisor of the viewport width and height that places the mouse at the center of the viewport.
     */
    private static final int VIEWPORT_CENTER_DIVISOR = 2;

    private static final int MULTIPLAYER_SESSION = 1;
    private static final int SESSION_TURN_LIMIT = 2;
    private static final int NETWORK_TURNS_PER_SECOND = 24;

    private static final int RECORD_UPDATE_OFFSET = 3;
    private static final int KIND_TWO_PERIOD = 2;
    private static final int KIND_TWO_PHASE = 1;
    private static final int LAST_FRAME_WINDOW = 8;

    /**
     * The portrait refresh BYTE of the local interface before the first frame.
     */
    private static final int INITIAL_PORTRAIT_REFRESH = 1;
    /**
     * The identifier of the first numbered text; the {@code stat_txt} identifiers are 1-based.
     */
    private static final int FIRST_TEXT_IDENTIFIER = 1;

    private static final @NotNull String SCENARIO_ENTRY = "staredit\\scenario.chk";
    private static final @NotNull String EXECUTABLE_DIRECTORY = "x86_64";
    private static final @NotNull String EXECUTABLE = "StarCraft.exe";
    private static final @NotNull String KEYED_TEXT_FILE = "locales/koKR/Assets/rez/network.json";
    private static final @NotNull String NUMBERED_TEXT_FILE = "locales/koKR/Assets/rez/stat_txt.json";

    /**
     * Starts a recorded LAN game as the given human player's client ran it: a live network game with the native code constants of the installed
     * executable, the map archive and the local inputs the client supplies.
     *
     * @param data        the installed game resources
     * @param gamePath    the installed game
     * @param map         the map the game was played on
     * @param replay      the recording
     * @param localPlayer the recording client's player
     * @return the started runtime
     * @throws IOException if the map, a required installed resource or the installed localized text cannot be read
     */
    public static @NotNull ReplayRuntime startNetworkReplay(@NotNull GameData data,
                                                            @NotNull Path gamePath, @NotNull Path map,
                                                            @NotNull ReplayFile replay,
                                                            int localPlayer) throws IOException {
        return startNetworkReplay(data, gamePath, map, replay, localPlayer, null);
    }

    /**
     * Starts a recorded LAN game as {@link #startNetworkReplay(GameData, Path, Path, ReplayFile, int)} does, with a diagnostic command observer
     * installed before startup.
     *
     * @param data        the installed game resources
     * @param gamePath    the installed game
     * @param map         the map the game was played on
     * @param replay      the recording
     * @param localPlayer the recording client's player
     * @param observer    the command observer, or {@code null}
     * @return the started runtime
     * @throws IOException if the map, a required installed resource or the installed localized text cannot be read
     */
    public static @NotNull ReplayRuntime startNetworkReplay(@NotNull GameData data,
                                                            @NotNull Path gamePath, @NotNull Path map,
                                                            @NotNull ReplayFile replay,
                                                            int localPlayer,
                                                            @Nullable ReplayCommandObserver observer) throws IOException {
        ChkFile scenario = ChkFile.parseTolerant(MpqArchive.open(map).readFile(SCENARIO_ENTRY));
        ReplayRuntime runtime = ReplayRuntime.createLiveGame(replay, scenario, data,
                                                             localPlayer, true,
                                                             RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                             RunningGameScenario.INSTRUCTION_LIMIT);
        Game game = runtime.getGame();

        copyCodeConstants(game, gamePath);
        prepare(game);
        game.getInterfaceLink().setOutgoingTurn(new OutgoingTurnBuffer(LiveGame.MAXIMUM_TURN_BYTES));
        game.getInterfaceLink().setSessionTurnLimits(SESSION_TURN_LIMIT, LiveGame.MAXIMUM_TURN_BYTES);
        runtime.getFrameTiming().startSession(MULTIPLAYER_SESSION, NETWORK_TURNS_PER_SECOND, NETWORK_TURNS_PER_SECOND);
        runtime.setSourceFile(GameSourceFile.read(map));
        runtime.setCommandObserver(observer);
        runtime.start(0, 0);

        return runtime;
    }

    /**
     * Copies the native code constants of the installed executable into a game.
     * <p>
     * A missing or unreadable executable is ignored; the game then receives the constants and the reason of the failed load.
     *
     * @param game     the game
     * @param gamePath the installed game
     */
    public static void copyCodeConstants(@NotNull Game game, @NotNull Path gamePath) {
        NativeCodeConstants constants = new NativeCodeConstants();

        constants.loadExecutable(gamePath.resolve(EXECUTABLE_DIRECTORY).resolve(EXECUTABLE));
        game.getGameContext().getCodeConstants().copyFrom(constants);
    }

    /**
     * Prepares the local interface inputs before the first frame.
     *
     * @param game the live game
     * @throws IOException if installed localized text cannot be read
     */
    public static void prepare(@NotNull Game game) throws IOException {
        game.getInterfaceLink().getLocalKeyStates().setAllReleased();

        LocalView view = game.getInterfaceLink().getLocalView();

        view.setView(0, 0,
                     RunningGameScenario.VIEWPORT_WIDTH / VIEWPORT_CENTER_DIVISOR, RunningGameScenario.VIEWPORT_HEIGHT / VIEWPORT_CENTER_DIVISOR);
        view.setAlertScreen(0, 0);
        view.setScreenGeometry(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                               game.getMap().getWidth() * TerrainTileset.TILE_SIZE - RunningGameScenario.VIEWPORT_WIDTH,
                               game.getMap().getHeight() * TerrainTileset.TILE_SIZE - RunningGameScenario.VIEWPORT_HEIGHT);

        LocalInterfaceState local = game.getInterfaceLink().getLocalInterface();

        local.startInterfaceTick(0);
        local.setInterfaceSelection(new int[UnitSelection.MAXIMUM_SELECTED]);
        local.setPortraitRefresh(INITIAL_PORTRAIT_REFRESH);
        local.setStallStamp(0);
        local.setChatLabel(new byte[LocalInterfaceState.CHAT_LABEL_SIZE]);
        local.setStatusPanelCache(new int[LocalInterfaceState.STATUS_PANEL_CACHE_DWORDS]);
        local.setMouseButtons(0);
        local.setChatMode(0);
        local.setGluePalette(0);
        local.setDisplayedButtonSet(ButtonSetIds.EMPTY);

        MessageLines lines = game.getInterfaceLink().getMessageLines();

        lines.setLayout(text -> new int[]{text.length});
        lines.setHostClock(() -> game.getCurrentFrame() * GameSpeed.FASTEST_DURATION);
        lines.setSubtitles(Boolean.TRUE);
        lines.setDepartureFormats("%s has left the game.", "%s was dropped from the game.");

        Map<String, byte[]> keyed = new HashMap<>();
        List<byte[]> numbered = new ArrayList<>();
        GameData textData = game.getGameData();

        Assertions.assertNotNull(textData);

        if (JsonReader.parse(textData.readInstalledFile(KEYED_TEXT_FILE)) instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> text && text.get("Key") instanceof String key && text.get("Value") instanceof String value) {
                    keyed.put(key, value.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        if (JsonReader.parse(textData.readInstalledFile(NUMBERED_TEXT_FILE)) instanceof List<?> entries) {
            for (Object entry : entries) {
                numbered.add(entry instanceof Map<?, ?> text && text.get("Value") instanceof String value
                             ? value.getBytes(StandardCharsets.UTF_8)
                             : null);
            }
        }

        lines.setStatusTexts(new StatusTexts() {
            /**
             * Resolves a localized keyed text.
             *
             * @param key the string key
             * @return the localized text bytes, or {@code null} when the key is unknown
             */
            @Override
            public byte @Nullable [] resolveKeyedText(@NotNull String key) {
                return keyed.get(key);
            }

            /**
             * Resolves a localized numbered text.
             *
             * @param textIdentifier the 1-based {@code stat_txt} id
             * @return the localized text bytes, or {@code null} when the id is unknown
             */
            @Override
            public byte @Nullable [] resolveNumberedText(int textIdentifier) {
                return textIdentifier >= FIRST_TEXT_IDENTIFIER && textIdentifier <= numbered.size()
                       ? numbered.get(textIdentifier - FIRST_TEXT_IDENTIFIER)
                       : null;
            }
        });
    }

    /**
     * Collects the resource synchronization words near the requested last frame.
     *
     * @param gamePath    the installed game
     * @param map         the map the game was played on
     * @param resource    the recording, one of the {@link GameTestResources} replays
     * @param localPlayer the recording client's player
     * @param lastFrame   the last frame to run
     * @return the collected kind-2 synchronization words keyed by frame
     * @throws IOException if the recording, the installed resources, the map or the installed localized text cannot be read
     */
    public static @NotNull Map<Long, Integer> collectResourceWords(@NotNull Path gamePath, @NotNull Path map,
                                                                   @NotNull String resource,
                                                                   int localPlayer,
                                                                   long lastFrame) throws IOException {
        ReplayFile replay = ReplayFile.parse(GameTestResources.readAllBytes(resource));
        Map<Long, Integer> words = new HashMap<>();

        try (GameData data = GameData.loadFrom(gamePath)) {
            ReplayRuntime runtime = startNetworkReplay(data, gamePath, map, replay, localPlayer);
            Game game = runtime.getGame();
            ReplaySession session = runtime.getSession();

            Assertions.assertNotNull(session);

            session.setCommandsExecutedListener(() -> {
                long frame = game.getCurrentFrame();
                int update = (int) frame + RECORD_UPDATE_OFFSET;

                if (update % KIND_TWO_PERIOD == KIND_TWO_PHASE && frame >= lastFrame - LAST_FRAME_WINDOW) {
                    words.put(frame,
                              SyncChecksumUtils.calculateRecordWord(SyncChecksumUtils.calculateResourceHash(game),
                                                                    SyncChecksumUtils.calculateTileRowHash(game.getMap(), update - 1),
                                                                    SyncChecksumUtils.SLOT_MASKS));
                }
            });

            int now = 0;

            while (game.getCurrentFrame() <= lastFrame) {
                now += GameSpeed.FASTEST_DURATION;

                Assertions.assertTrue(runtime.step(GameSpeed.FASTEST_DURATION, now), "The replay ends before the last frame to run.");
            }
        }

        return words;
    }


    /**
     * Creates a {@link NetworkReplayScenario} instance.
     */
    private NetworkReplayScenario() {
    }
}

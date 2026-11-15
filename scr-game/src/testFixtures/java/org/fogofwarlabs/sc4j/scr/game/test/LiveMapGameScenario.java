package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ButtonSetIds;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalInterfaceState;
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
 * Starts live games on map files with the local interface state a client supplies before session startup, and advances them frame by
 * frame in the client's turn-exchange, simulation and outcome order.
 */
public final class LiveMapGameScenario {
    /**
     * The installed network texts that the message lines resolve keyed texts from.
     */
    private static final @NotNull String NETWORK_TEXT_PATH = "locales/enUS/Assets/rez/network.json";
    /**
     * The installed status texts that the message lines resolve numbered texts from.
     */
    private static final @NotNull String STATUS_TEXT_PATH = "locales/enUS/Assets/rez/stat_txt.json";
    /**
     * The key field of an installed text entry.
     */
    private static final @NotNull String TEXT_KEY = "Key";
    /**
     * The value field of an installed text entry.
     */
    private static final @NotNull String TEXT_VALUE = "Value";
    /**
     * The message format of a player who left the game.
     */
    private static final @NotNull String LEFT_GAME_FORMAT = "%s has left the game.";
    /**
     * The message format of a player who was dropped from the game.
     */
    private static final @NotNull String DROPPED_GAME_FORMAT = "%s was dropped from the game.";

    /**
     * Creates a {@link ReplayRuntime} instance and starts the supplied live session, attached to the runtime's session as the client
     * attaches it.
     *
     * @param gameData the installed game data
     * @param live     the live session
     * @param mapPath  the map file the game is loaded from
     * @return the created {@link ReplayRuntime} instance after startup
     * @throws IOException if the map, its source file or the installed text files cannot be read
     */
    public static @NotNull ReplayRuntime startLive(@NotNull GameData gameData, @NotNull LiveGame live, @NotNull Path mapPath) throws IOException {
        ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(),
                                                             ChkFile.parseTolerant(live.getSetup().getMapData()),
                                                             gameData,
                                                             live.getLocalPlayer(),
                                                             false,
                                                             live.getTeamSlotRaces(),
                                                             RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                             RunningGameScenario.INSTRUCTION_LIMIT);

        prepareInterface(runtime.getGame());

        runtime.setSourceFile(GameSourceFile.read(mapPath));
        live.startSession(runtime.getGame(), runtime.getFrameTiming());
        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        ReplaySession session = runtime.getSession();

        Assertions.assertNotNull(session);

        live.attachReplaySession(session);

        return runtime;
    }

    /**
     * Supplies the input, view and message state before session startup.
     *
     * @param game the game
     * @throws IOException if the installed text files cannot be read
     */
    private static void prepareInterface(@NotNull Game game) throws IOException {
        game.getInterfaceLink().getLocalKeyStates().setAllReleased();
        game.getInterfaceLink().getLocalView().setView(0, 0, RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);

        LocalInterfaceState local = game.getInterfaceLink().getLocalInterface();

        local.startInterfaceTick(0);
        local.setInterfaceSelection(new int[UnitSelection.MAXIMUM_SELECTED]);
        local.setPortraitRefresh(1);
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
        lines.setDepartureFormats(LEFT_GAME_FORMAT, DROPPED_GAME_FORMAT);

        Map<String, byte[]> keyed = new HashMap<>();
        List<byte[]> numbered = new ArrayList<>();
        GameData gameData = game.getGameData();

        Assertions.assertNotNull(gameData);

        if (JsonReader.parse(gameData.readInstalledFile(NETWORK_TEXT_PATH)) instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map && map.get(TEXT_KEY) instanceof String key && map.get(TEXT_VALUE) instanceof String value) {
                    keyed.put(key, value.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        if (JsonReader.parse(gameData.readInstalledFile(STATUS_TEXT_PATH)) instanceof List<?> entries) {
            for (Object entry : entries) {
                numbered.add(entry instanceof Map<?, ?> map && map.get(TEXT_VALUE) instanceof String value
                             ? value.getBytes(StandardCharsets.UTF_8)
                             : null);
            }
        }

        lines.setStatusTexts(new MessageLines.StatusTexts() {
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
                return textIdentifier >= 1 && textIdentifier <= numbered.size() ? numbered.get(textIdentifier - 1) : null;
            }
        });
    }

    /**
     * Advances the runtime and its optional live session in turn-exchange, simulation and outcome order.
     *
     * @param runtime the game runtime
     * @param live    the live session, or {@code null}
     */
    public static void stepFrame(@NotNull ReplayRuntime runtime, @Nullable LiveGame live) {
        int frame = runtime.getGame().getCurrentFrame();

        if (live != null) {
            live.exchangeTurn(frame);
        }

        runtime.getGame().getInterfaceLink().getMessageLines().expire(frame * GameSpeed.FASTEST_DURATION);
        runtime.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION);

        if (live != null) {
            live.updateOutcome(runtime.getGame());
        }
    }


    /**
     * Creates a {@link LiveMapGameScenario} instance.
     */
    private LiveMapGameScenario() {
    }
}

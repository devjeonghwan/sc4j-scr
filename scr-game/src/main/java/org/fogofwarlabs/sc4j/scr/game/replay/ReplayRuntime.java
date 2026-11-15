package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.MusicRotation;
import org.fogofwarlabs.sc4j.scr.game.presentation.PaletteAnimation;
import org.fogofwarlabs.sc4j.scr.game.presentation.StartScreenUtils;
import org.fogofwarlabs.sc4j.scr.game.presentation.StatusButtonPanel;
import org.fogofwarlabs.sc4j.scr.game.session.PauseNotice;
import org.fogofwarlabs.sc4j.scr.game.session.setup.PlayerSlots;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.TransmissionPortraitTimer;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Owns one fresh offline replay startup attempt and its retained partial world. A failed phase is terminal; callers must
 * construct a new runtime to reset it.
 */
public final class ReplayRuntime {
    private static final int VIEWPORT_WIDTH_INDEX = 2;
    private static final int CONSOLE_WIDTH = 640;
    private static final int PALETTE_ENTRY_BYTES = 4;
    private static final int STARTUP_PHASE_COUNT = 2;

    /**
     * Creates a {@link ReplayRuntime} instance for a live game whose command stream grows while it runs.
     * <p>
     * The game runs with the replay flag clear and the human or an observer as local player ({@link ReplayInitialization#createLiveGame}); startup,
     * command execution and frames are the common path.
     *
     * @param replay           the in-memory recording of the live game
     * @param mapFile          the scenario retaining section order
     * @param gameData         the installed game resources
     * @param localPlayer      the human's player index
     * @param viewportWidth    the viewport width in pixels
     * @param viewportHeight   the viewport height in pixels
     * @param instructionLimit the per-invocation script and movement budget
     * @return the created {@link ReplayRuntime} instance
     * @throws IOException if a required installed resource cannot be loaded
     */
    public static @NotNull ReplayRuntime createLiveGame(@NotNull ReplayFile replay,
                                                        @NotNull ChkFile mapFile,
                                                        @NotNull GameData gameData,
                                                        int localPlayer,
                                                        int viewportWidth, int viewportHeight,
                                                        int instructionLimit) throws IOException {
        return createLiveGame(replay, mapFile, gameData, localPlayer, false, viewportWidth, viewportHeight, instructionLimit);
    }

    /**
     * Creates a {@link ReplayRuntime} instance for a live game, single player or network
     * ({@link ReplayInitialization#createLiveGame(ReplayHeader, int, boolean)}).
     *
     * @param replay           the in-memory recording of the live game
     * @param mapFile          the scenario retaining section order
     * @param gameData         the installed game resources
     * @param localPlayer      the human's player index
     * @param networkGame      whether the game is a network game
     * @param viewportWidth    the viewport width in pixels
     * @param viewportHeight   the viewport height in pixels
     * @param instructionLimit the per-invocation script and movement budget
     * @return the created {@link ReplayRuntime} instance
     * @throws IOException              if a required installed resource cannot be loaded
     * @throws IllegalArgumentException if the local player is neither one of the eight game players nor an observer slot
     */
    public static @NotNull ReplayRuntime createLiveGame(@NotNull ReplayFile replay,
                                                        @NotNull ChkFile mapFile,
                                                        @NotNull GameData gameData,
                                                        int localPlayer,
                                                        boolean networkGame,
                                                        int viewportWidth, int viewportHeight,
                                                        int instructionLimit) throws IOException {
        return createLiveGame(replay, mapFile, gameData, localPlayer, networkGame, null, viewportWidth, viewportHeight, instructionLimit);
    }

    /**
     * Creates a {@link ReplayRuntime} instance for a live team game: as
     * {@link #createLiveGame(ReplayFile, ChkFile, GameData, int, boolean, int, int, int)} with the slot races the team
     * bases use before team placement.
     *
     * @param replay           the in-memory recording of the live game
     * @param mapFile          the scenario retaining section order
     * @param gameData         the installed game resources
     * @param localPlayer      the human's player index
     * @param networkGame      whether the game is a network game
     * @param teamSlotRaces    the eight slot races in lobby order, or {@code null} outside team games
     * @param viewportWidth    the viewport width in pixels
     * @param viewportHeight   the viewport height in pixels
     * @param instructionLimit the per-invocation script and movement budget
     * @return the created {@link ReplayRuntime} instance
     * @throws IOException              if a required installed resource cannot be loaded
     * @throws IllegalArgumentException if the local player is neither one of the eight game players nor an observer slot
     */
    public static @NotNull ReplayRuntime createLiveGame(@NotNull ReplayFile replay,
                                                        @NotNull ChkFile mapFile,
                                                        @NotNull GameData gameData,
                                                        int localPlayer,
                                                        boolean networkGame,
                                                        int @Nullable [] teamSlotRaces,
                                                        int viewportWidth, int viewportHeight,
                                                        int instructionLimit) throws IOException {
        return createLiveGame(replay,
                              mapFile,
                              gameData,
                              localPlayer,
                              localPlayer,
                              networkGame,
                              teamSlotRaces,
                              viewportWidth, viewportHeight,
                              instructionLimit);
    }

    /**
     * Creates a {@link ReplayRuntime} instance for a live team game whose local human is not its force's leader: as
     * {@link #createLiveGame(ReplayFile, ChkFile, GameData, int, boolean, int[], int, int, int)} with the human's own slot
     * as the local selection slot ({@link ReplayInitialization#setLocalSelectionSlot}).
     *
     * @param replay             the in-memory recording of the live game
     * @param mapFile            the scenario retaining section order
     * @param gameData           the installed game resources
     * @param localPlayer        the player the human commands, its force's leader in a team game
     * @param localSelectionSlot the human's own slot
     * @param networkGame        whether the game is a network game
     * @param teamSlotRaces      the eight slot races in lobby order, or {@code null} outside team games
     * @param viewportWidth      the viewport width in pixels
     * @param viewportHeight     the viewport height in pixels
     * @param instructionLimit   the per-invocation script and movement budget
     * @return the created {@link ReplayRuntime} instance
     * @throws IOException              if a required installed resource cannot be loaded
     * @throws IllegalArgumentException if the local player is neither one of the eight game players nor an observer slot
     */
    public static @NotNull ReplayRuntime createLiveGame(@NotNull ReplayFile replay,
                                                        @NotNull ChkFile mapFile,
                                                        @NotNull GameData gameData,
                                                        int localPlayer,
                                                        int localSelectionSlot,
                                                        boolean networkGame,
                                                        int @Nullable [] teamSlotRaces,
                                                        int viewportWidth, int viewportHeight,
                                                        int instructionLimit) throws IOException {
        return createLiveGame(replay,
                              mapFile,
                              gameData,
                              localPlayer,
                              localSelectionSlot,
                              networkGame,
                              teamSlotRaces,
                              new MusicRotation(),
                              viewportWidth, viewportHeight,
                              instructionLimit);
    }

    /**
     * Creates a {@link ReplayRuntime} instance for a live team game whose local human is not its force's leader, as
     * {@link #createLiveGame(ReplayFile, ChkFile, GameData, int, int, boolean, int[], int, int, int)} does, with the client
     * process's music rotation, which a non-campaign start advances.
     *
     * @param replay             the in-memory recording of the live game
     * @param mapFile            the scenario retaining section order
     * @param gameData           the installed game resources
     * @param localPlayer        the player the human commands, its force's leader in a team game
     * @param localSelectionSlot the human's own slot
     * @param networkGame        whether the game is a network game
     * @param teamSlotRaces      the eight slot races in lobby order, or {@code null} outside team games
     * @param musicRotation      the music rotation of the client process
     * @param viewportWidth      the viewport width in pixels
     * @param viewportHeight     the viewport height in pixels
     * @param instructionLimit   the per-invocation script and movement budget
     * @return the created {@link ReplayRuntime} instance
     * @throws IOException              if a required installed resource cannot be loaded
     * @throws IllegalArgumentException if the local player is neither one of the eight game players nor an observer slot
     */
    public static @NotNull ReplayRuntime createLiveGame(@NotNull ReplayFile replay,
                                                        @NotNull ChkFile mapFile,
                                                        @NotNull GameData gameData,
                                                        int localPlayer,
                                                        int localSelectionSlot,
                                                        boolean networkGame,
                                                        int @Nullable [] teamSlotRaces,
                                                        @NotNull MusicRotation musicRotation,
                                                        int viewportWidth, int viewportHeight,
                                                        int instructionLimit) throws IOException {
        if (!PlayerSlots.isPlayerIdentifier(localPlayer)) {
            throw Exceptions.newIllegalArgumentException("A live local player is one of the eight game players or an observer, not {0}.",
                                                         localPlayer);
        }

        return new ReplayRuntime(replay,
                                 mapFile,
                                 gameData,
                                 localPlayer,
                                 localSelectionSlot,
                                 networkGame,
                                 teamSlotRaces,
                                 musicRotation,
                                 viewportWidth, viewportHeight,
                                 instructionLimit);
    }


    private final @NotNull ReplayFile replay;
    private final @NotNull GameData gameData;
    private final @NotNull ReplayInitialization initialization;
    private final @NotNull ReplayStartup startup;

    private final int @NotNull [] viewport;
    private final int instructionLimit;

    private int screenWidth;
    private int screenHeight;
    /**
     * Whether the host loop advances palette cycles.
     */
    private boolean colorCycling;
    private @Nullable GameSourceFile sourceFile;
    private @Nullable ReplayCommandObserver commandObserver;
    private ReplayCommandExecutor.@Nullable PlaybackControl playbackControl;

    private @NotNull String phase;
    private boolean started;
    private @Nullable Exception failure;

    private @Nullable ReplaySession session;
    private @Nullable ReplayFrameRunner frameRunner;
    private @Nullable ReplayDisplay display;
    private @Nullable ReplayCommandExecutor commandExecutor;

    /**
     * Creates a {@link ReplayRuntime} instance with the actual ordered map constructors. Construction alone does not enable
     * command execution.
     *
     * @param replay           the accepted expansion Melee replay
     * @param mapFile          the embedded map retaining section order
     * @param gameData         the installed game resources
     * @param viewportWidth    the viewport width in pixels
     * @param viewportHeight   the viewport height in pixels
     * @param instructionLimit the per-invocation script and movement budget
     * @throws IOException                   if a required installed resource cannot be loaded
     * @throws IllegalArgumentException      if a viewport dimension or the instruction budget is not positive
     * @throws UnsupportedOperationException if the replay stream skipped unknown command blocks
     */
    public ReplayRuntime(@NotNull ReplayFile replay,
                         @NotNull ChkFile mapFile,
                         @NotNull GameData gameData,
                         int viewportWidth, int viewportHeight,
                         int instructionLimit) throws IOException {
        this(replay, mapFile, gameData, new MusicRotation(), viewportWidth, viewportHeight, instructionLimit);
    }

    /**
     * Creates a {@link ReplayRuntime} instance with the actual ordered map constructors and the client process's music
     * rotation, which a non-campaign start advances. Construction alone does not enable command execution.
     *
     * @param replay           the accepted expansion Melee replay
     * @param mapFile          the embedded map retaining section order
     * @param gameData         the installed game resources
     * @param musicRotation    the music rotation of the client process
     * @param viewportWidth    the viewport width in pixels
     * @param viewportHeight   the viewport height in pixels
     * @param instructionLimit the per-invocation script and movement budget
     * @throws IOException                   if a required installed resource cannot be loaded
     * @throws IllegalArgumentException      if a viewport dimension or the instruction budget is not positive
     * @throws UnsupportedOperationException if the replay stream skipped unknown command blocks
     */
    public ReplayRuntime(@NotNull ReplayFile replay,
                         @NotNull ChkFile mapFile,
                         @NotNull GameData gameData,
                         @NotNull MusicRotation musicRotation,
                         int viewportWidth, int viewportHeight,
                         int instructionLimit) throws IOException {
        this(replay, mapFile, gameData, -1, -1, false, null, musicRotation, viewportWidth, viewportHeight, instructionLimit);
    }

    private ReplayRuntime(@NotNull ReplayFile replay,
                          @NotNull ChkFile mapFile,
                          @NotNull GameData gameData,
                          int liveLocalPlayer,
                          int localSelectionSlot,
                          boolean networkGame,
                          int @Nullable [] teamSlotRaces,
                          @NotNull MusicRotation musicRotation,
                          int viewportWidth, int viewportHeight,
                          int instructionLimit) throws IOException {
        if (viewportWidth <= 0 || viewportHeight <= 0 || instructionLimit <= 0) {
            throw Exceptions.newIllegalArgumentException("Replay execution requires positive viewport dimensions and instruction budget.");
        }

        if (replay.getUnknownTypeCount() != 0) {
            throw Exceptions.newUnsupportedOperationException("Replay execution cannot consume a stream with skipped unknown command blocks.");
        }

        this.replay         = replay;
        this.gameData       = gameData;
        this.initialization = liveLocalPlayer < 0
                              ? ReplayInitialization.createFreshReplay(replay.getHeader(), replay.getSpeedOverrideIndex(), musicRotation)
                              : ReplayInitialization.createLiveGame(replay.getHeader(), liveLocalPlayer, networkGame, replay.getSpeedOverrideIndex(),
                                                                    musicRotation);
        this.initialization.setTeamSlotRaces(teamSlotRaces);

        if (liveLocalPlayer >= 0) {
            this.initialization.setLocalSelectionSlot(localSelectionSlot);
        }

        this.startup = new ReplayStartup(replay, mapFile, gameData, this.initialization, viewportWidth, viewportHeight, instructionLimit);

        this.viewport         = new int[]{0, 0, viewportWidth, viewportHeight};
        this.instructionLimit = instructionLimit;

        this.screenWidth     = StartScreenUtils.DEFAULT_SCREEN_WIDTH;
        this.screenHeight    = StartScreenUtils.DEFAULT_SCREEN_HEIGHT;
        this.colorCycling    = true;
        this.sourceFile      = null;
        this.commandObserver = null;
        this.playbackControl = null;

        this.phase   = "constructed";
        this.started = false;
        this.failure = null;

        this.session         = null;
        this.frameRunner     = null;
        this.display         = null;
        this.commandExecutor = null;
    }

    /**
     * @return the authoritative world, including changes preceding a failure
     */
    public @NotNull Game getGame() {
        return this.startup.getGame();
    }

    /**
     * @return the start inputs the game was created from, including the start-up draws' music player
     */
    public @NotNull ReplayInitialization getInitialization() {
        return this.initialization;
    }

    /**
     * @return the per-invocation script and movement budget
     */
    public int getInstructionLimit() {
        return this.instructionLimit;
    }

    /**
     * @return the live speed configuration and duration table, including changes from EUD writes and replay speed commands
     */
    public @NotNull GameSpeed getFrameTiming() {
        return this.initialization.getFrameTiming();
    }

    /**
     * @return the established fresh-profile simulation frame duration in milliseconds
     */
    public int getFrameDuration() {
        return this.initialization.getFrameTiming().getFrameDuration();
    }

    /**
     * @return the current or first failed startup phase
     */
    public @NotNull String getPhase() {
        return this.phase;
    }

    /**
     * @return the retained failure, or {@code null} before a failure
     */
    public @Nullable Exception getFailure() {
        return this.failure;
    }

    /**
     * @return the executable session, or {@code null} until all startup effects complete
     */
    public @Nullable ReplaySession getSession() {
        return this.session;
    }

    /**
     * @return the cycled display palette, or {@code null} before startup
     */
    public @Nullable PaletteAnimation getDisplayPalette() {
        return this.display == null ? null : this.display.getPalette();
    }

    /**
     * Sets the game screen size the start places the start screen of a replay or observer view with ({@link #start}); without it the
     * start uses the default screen size ({@link StartScreenUtils#DEFAULT_SCREEN_WIDTH}, {@link StartScreenUtils#DEFAULT_SCREEN_HEIGHT}).
     *
     * @param screenWidth  the game screen width in map pixels
     * @param screenHeight the game screen height in map pixels
     * @throws IllegalStateException    if startup has already been attempted
     * @throws IllegalArgumentException if a screen dimension is not positive
     */
    public void setScreenSize(int screenWidth, int screenHeight) {
        if (this.started) {
            throw Exceptions.newIllegalStateException("The screen size is set before startup.");
        }

        if (screenWidth <= 0 || screenHeight <= 0) {
            throw Exceptions.newIllegalArgumentException("The screen size {0} x {1} is not positive.", screenWidth, screenHeight);
        }

        this.screenWidth  = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Sets whether the host loop advances palette cycles.
     *
     * @param colorCycling whether the palette cycles
     */
    public void setColorCycling(boolean colorCycling) {
        this.colorCycling = colorCycling;
    }

    /**
     * Sets the file the game is loaded from: the replay file of replay playback, the map file of a live game. The start
     * keeps its path in the game context ({@link GameContext#setMapArchive}); without a source file the path stays empty,
     * so the EUD emulation builds no Storm archive list.
     *
     * @param sourceFile the source file
     * @throws IllegalStateException if startup has already been attempted
     */
    public void setSourceFile(@NotNull GameSourceFile sourceFile) {
        if (this.started) {
            throw Exceptions.newIllegalStateException("The source file is set before startup.");
        }

        this.sourceFile = sourceFile;
    }

    /**
     * Sets the diagnostic observer notified around every recorded command. It must be set before {@link #start} and
     * must not change game state; {@code null} (the default) disables observation.
     *
     * @param commandObserver the observer, or {@code null}
     */
    public void setCommandObserver(@Nullable ReplayCommandObserver commandObserver) {
        this.commandObserver = commandObserver;
    }

    /**
     * Sets the replay viewer's playback that Pause and Resume commands pause and continue and Replay Seek commands seek
     * ({@link ReplayCommandExecutor#setPlaybackControl}) and the received turns' Replay Speed commands pause and continue
     * ({@link ReplaySession#setPlaybackControl}); it may be set before or after {@link #start}.
     *
     * @param playbackControl the viewer's playback, or {@code null} for none
     */
    public void setPlaybackControl(ReplayCommandExecutor.@Nullable PlaybackControl playbackControl) {
        this.playbackControl = playbackControl;

        if (this.commandExecutor != null) {
            this.commandExecutor.setPlaybackControl(playbackControl);
        }

        if (this.session != null) {
            this.session.setPlaybackControl(playbackControl);
        }
    }

    /**
     * Sets the game speed option that trigger Waits use during replay playback ({@link ReplayFrameRunner#setGameSpeedOption(int)}) without
     * changing the speed configuration ({@link #configureSpeed} changes both); headless runs keep the profile speed.
     *
     * @param speed the option {@code 0..6}, or {@code -1} for the profile speed
     * @throws IllegalStateException if the replay has not started
     */
    public void setGameSpeedOption(int speed) {
        if (this.frameRunner == null) {
            throw Exceptions.newIllegalStateException("The game speed option is set after startup.");
        }

        this.frameRunner.setGameSpeedOption(speed);
    }


    /**
     * Configures the replay's speed and the game speed option of its trigger Waits ({@link ReplaySession#configureSpeed}): the interactive viewer
     * starts a replay at its options' speed setting with factor {@code 1} and mode {@code 0}, as the replay start does, and a restarted replay at
     * the setting and factor it had.
     *
     * @param speed  the speed setting {@code 0..6}
     * @param factor the DWORD factor
     * @param mode   the mode byte, not {@code 0} while the playback is paused
     * @throws IllegalStateException    if the replay has not started
     * @throws IllegalArgumentException if the speed setting is outside {@code 0..6}
     */
    public void configureSpeed(int speed, int factor, int mode) {
        if (this.session == null) {
            throw Exceptions.newIllegalStateException("The replay speed is configured after startup.");
        }

        this.session.configureSpeed(speed, factor, mode);
    }

    /**
     * Takes the pauses and resumes the recorded commands made ({@link ReplayCommandExecutor#takePauseNotices}).
     *
     * @return the notices in execution order, empty before the start
     */
    public @NotNull List<PauseNotice> takePauseNotices() {
        return this.commandExecutor == null ? List.of() : this.commandExecutor.takePauseNotices();
    }

    /**
     * Makes replay playback present the first human player's triggers ({@link ReplayInitialization#selectNativeReplayLocalPlayer()}). The
     * viewer then ends that player's transmission waits on its wall clock ({@link TransmissionPortraitTimer}); headless runs leave it off and
     * stay independent of host time.
     *
     * @return the selected trigger local player, {@code 8} when the replay has none
     * @throws IllegalStateException if the replay already started or this is a live game
     */
    public int useNativeReplayLocalPlayer() {
        if (this.started) {
            throw Exceptions.newIllegalStateException("The replay local player is chosen before startup.");
        }

        this.initialization.selectNativeReplayLocalPlayer();

        return this.initialization.getTriggerLocalPlayer();
    }

    /**
     * Executes the ordered frame-zero startup phases with actual installed UI resources. Failure retains the affected phase
     * and closes the random scope without retrying it.
     * <p>
     * A replay or observer start whose local view was not supplied places the game start's screen of the first human
     * ({@link ReplayInitialization#findStartScreenPlayer()}) with the set screen size ({@link #setScreenSize}, {@link StartScreenUtils}),
     * with the mouse at the screen's top-left and no mouse button down. A live player's display places its own start screen.
     *
     * @param elapsedHostTicks the measured unsigned host tick difference at startup
     * @param currentHostTick  the absolute host DWORD clock sampled for startup display
     * @throws IllegalStateException         if startup has already been attempted
     * @throws UnsupportedOperationException if the execution scope, the unit registry, the foliage or the creep field is not initialized
     */
    public void start(int elapsedHostTicks, int currentHostTick) {
        if (this.started) {
            throw Exceptions.newIllegalStateException("Replay startup cannot be retried after partial execution.");
        }

        this.started = true;

        Game game = this.getGame();

        if (this.sourceFile != null) {
            game.getGameContext().setMapArchive(this.sourceFile.getPath(), this.sourceFile.getContents());
        }

        try {
            this.phase = "display resources";

            UnitPhase.Context units = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                            UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);

            units.initializeLurkerHitHistory();

            StatusButtonPanel panel = StatusButtonPanel.createReplayAfterStartup(this.gameData.loadReplayStatusButtonDefinition(),
                                                                                 this.viewport[VIEWPORT_WIDTH_INDEX] - CONSOLE_WIDTH,
                                                                                 new int[StatusButtonPanel.REFERENCE_COUNT],
                                                                                 new long[StatusButtonPanel.TOOLTIP_VALUE_COUNT],
                                                                                 this.gameData.loadReplayLabelInitialText(),
                                                                                 this.gameData.loadReplayLabelsLocalization(),
                                                                                 this.gameData.loadReplayProgressResources());

            units.getDisplayState().initializeFreshReplayPanels(panel,
                                                                this.gameData.loadStatisticsPanelDefinition(),
                                                                new int[]{0, 0, 0, this.initialization.getLocalPlayer(), 0,
                                                                          this.initialization.getFrameTiming().getSpeedIndex(),
                                                                          1, 0, 0},
                                                                0, 0);

            byte[] paletteBytes = this.gameData.loadTerrainPalette(game.getMap().getTileset());
            int[] palette = new int[PaletteAnimation.COLOR_COUNT];

            for (int colorIndex = 0; colorIndex < palette.length; colorIndex++) {
                palette[colorIndex] = LittleEndianUtils.readInt32(paletteBytes, colorIndex * PALETTE_ENTRY_BYTES);
            }

            this.display = new ReplayDisplay(game,
                                             units.getDisplayState(),
                                             PaletteAnimation.createForTileset(game.getMap().getTileset(), palette, 0),
                                             this.initialization.createFrameIndicators(),
                                             (int) this.replay.getHeader().getFrames(),
                                             this.initialization.getFrameTiming().getDuration(this.replay.getHeader().getSpeed()),
                                             0);

            GameFrame frame = this.startup.createFrame(units);

            game.requireExecutionScope().finishInitialization();
            game.getUnitSystem().requireUnitRegistry().finishInitialization();
            game.requireFoliage().finishInitialization();
            game.requireCreepField().finishInitialization();

            frame.prepare(elapsedHostTicks, false, false, this.viewport, this.instructionLimit);

            for (int phaseIndex = 0; phaseIndex < STARTUP_PHASE_COUNT; phaseIndex++) {
                this.phase = "startup game phase " + phaseIndex;
                game.getEngineGame().getFrameRunner().runSetupFrame();
                this.phase = "startup display phase " + phaseIndex;
                this.display.update(currentHostTick, false, this.colorCycling, 0, 0);
            }

            this.phase = "startup completion";

            if (this.initialization.isReplayOrObserver() && !game.getInterfaceLink().getLocalView().isKnown()) {
                StartScreenUtils.placeStartScreen(game, this.initialization.findStartScreenPlayer(), this.screenWidth, this.screenHeight);
                game.getInterfaceLink().getLocalInterface().setMouseButtons(0);
            }

            TriggerContext triggers = this.startup.createTriggers(new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS], 0);
            ReplayFrameRunner frameRunner = new ReplayFrameRunner(game,
                                                                  frame,
                                                                  units,
                                                                  triggers,
                                                                  this.initialization.getFrameTiming(), this.replay.getHeader().getSpeed(),
                                                                  false,
                                                                  false,
                                                                  true,
                                                                  this.viewport,
                                                                  this.instructionLimit,
                                                                  0, 0,
                                                                  0);

            frameRunner.setReplayFlag(!this.initialization.isLive());
            this.frameRunner = frameRunner;

            ReplayCommandExecutor commandExecutor = new ReplayCommandExecutor(this.instructionLimit, (int) this.replay.getHeader().getFrames());

            commandExecutor.setPlaybackControl(this.playbackControl);
            this.commandExecutor = commandExecutor;

            ReplayCommandObserver observer = this.commandObserver;
            ReplayCommandHandler handler = commandExecutor;

            if (observer != null) {
                handler = (commandState, command) -> {
                    observer.onBeforeCommand(commandState, command);
                    commandExecutor.execute(commandState, command);
                    observer.onAfterCommand(commandState, command);
                };
            }

            ReplaySession replaySession = new ReplaySession(game, this.replay, handler, frameRunner);

            replaySession.setPlaybackControl(this.playbackControl);
            game.markWorldInitialized();
            this.session = replaySession;
            this.phase   = "running";
        } catch (IOException | RuntimeException exception) {
            this.failure = exception;
        } finally {
            game.getRandom().setEnabled(0);
        }
    }

    /**
     * Runs the host loop's color cycling step between game frames ({@link ReplayDisplay#updatePalette}).
     *
     * @param currentHostTick the absolute host DWORD clock
     * @param minimized       whether the window is minimized
     * @param paused          whether the game is paused
     * @return {@code true} if the palette advanced, {@code false} otherwise
     */
    public boolean updateColorCycle(int currentHostTick, boolean minimized, boolean paused) {
        ReplayDisplay current = this.display;

        return current != null && this.failure == null && current.updatePalette(currentHostTick, minimized, this.colorCycling, paused ? 1 : 0);
    }

    /**
     * Advances the established replay caller, then executes its separate host display update. A failed call retains its
     * partial state and prevents subsequent attempts.
     *
     * @param elapsedHostTicks the measured unsigned host tick difference for the game phase
     * @param currentHostTick  the absolute host DWORD clock for display timers
     * @return {@code true} if a replay frame completed, {@code false} otherwise (the replay had finished)
     * @throws IllegalStateException if startup is incomplete or an earlier call failed
     */
    public boolean step(int elapsedHostTicks, int currentHostTick) {
        if (this.session == null || this.display == null || this.failure != null) {
            throw Exceptions.newIllegalStateException("Replay execution requires successfully completed startup.");
        }

        try {
            this.phase = "replay game frame";

            if (!this.session.step(elapsedHostTicks)) {
                return false;
            }

            this.phase = "replay display frame";
            this.display.update(currentHostTick, false, this.colorCycling, 0, 0);
            this.phase = "running";

            return true;
        } catch (RuntimeException exception) {
            this.failure = exception;

            throw exception;
        }
    }

    /**
     * Runs a game loop of replay playback while the viewer's playback is paused, without a frame ({@link ReplaySession#stepPaused()}). A
     * failed call retains its partial state and prevents subsequent attempts.
     *
     * @return {@code true} if the loop ran, {@code false} otherwise (the game ended)
     * @throws IllegalStateException if startup is incomplete, an earlier call failed or the runtime runs a live game
     */
    public boolean stepPaused() {
        if (this.session == null || this.failure != null) {
            throw Exceptions.newIllegalStateException("Replay execution requires successfully completed startup.");
        }

        try {
            this.phase = "replay paused game loop";

            boolean ran = this.session.stepPaused();

            this.phase = "running";

            return ran;
        } catch (RuntimeException exception) {
            this.failure = exception;

            throw exception;
        }
    }
}

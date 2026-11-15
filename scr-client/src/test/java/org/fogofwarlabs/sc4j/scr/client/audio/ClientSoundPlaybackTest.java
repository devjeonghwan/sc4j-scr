package org.fogofwarlabs.sc4j.scr.client.audio;

import org.fogofwarlabs.sc4j.engine.client.audio.*;
import org.fogofwarlabs.sc4j.engine.resource.audio.SoundClip;
import org.fogofwarlabs.sc4j.scr.client.world.ClientCamera;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.core.test.TestResultUtils;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.type.ScrSoundTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.presentation.SoundEvents;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Verifies that the sounds of a Stim Pack command, a single-unit Unload command and an Unload All order in a headless
 * live single-player game survive the frame increment of their game loop and reach the client's playback layer
 * ({@link ClientSoundPlayback#drain}), and that the playback's decisions for replays on a silent device on a simulated
 * clock (which sound plays on which channel at which gain and position, which channel stops) match their recorded traces.
 */
@RequiresInstallation
final class ClientSoundPlaybackTest {
    private static final int INSTRUCTION_LIMIT = 100000;

    /**
     * The installed melee map and its scenario file.
     */
    private static final @NotNull String MELEE_MAP_EXTENSION = ".scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";

    /**
     * The lobby of the live game: the human's name, the Fastest speed and the game seed.
     */
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int FASTEST_SPEED = 6;
    private static final int GAME_SEED = 777;

    private static final int TILE_SIZE = 32;

    /**
     * The milliseconds of a Fastest frame, the elapsed time and host tick step of every loop.
     */
    private static final int FRAME_DURATION = 42;
    /**
     * The game loops run before the test units are created.
     */
    private static final int STARTUP_FRAMES = 8;

    /**
     * The distance of the test units from the Command Center, and the offsets of the passengers and the Dropship from the first Marine.
     */
    private static final int UNIT_DISTANCE = 96;
    private static final int PASSENGER_OFFSET = 24;
    private static final int SECOND_PASSENGER_OFFSET = 48;
    private static final int DROPSHIP_HORIZONTAL_OFFSET = 64;
    private static final int DROPSHIP_VERTICAL_OFFSET = 48;

    /**
     * The drained identifiers of the Stim Pack sounds, and the volume an on-screen sound exceeds.
     */
    private static final int FIRST_STIM_SOUND = 278;
    private static final int LAST_STIM_SOUND = 279;
    private static final int AUDIBLE_VOLUME = 10;
    /**
     * The drained identifier of the Terran transport unload sound ({@code Misc\TDrTra01.wav}): the transport unload sound {@code 43} plus the
     * Terran race offset {@code 1}.
     */
    private static final int TERRAN_TRANSPORT_UNLOAD_SOUND = 44;

    /**
     * The game loop budgets of a command sound, a boarding, the pause after the unload and the Unload All order.
     */
    private static final int SOUND_FRAMES = 30;
    private static final int BOARDING_FRAMES = 400;
    private static final int SETTLE_FRAMES = 20;
    private static final int UNLOAD_ALL_FRAMES = 60;

    /**
     * The unit status bit of a unit loaded in a transport.
     */
    private static final int LOADED_STATUS = 0x40;

    /**
     * The fields of a drained event record: the sound identifier, the volume and whether its file resolves.
     */
    private static final int SOUND_IDENTIFIER_FIELD = 0;
    private static final int VOLUME_FIELD = 1;
    private static final int AVAILABLE_FIELD = 2;
    private static final int AVAILABLE = 1;

    /**
     * The replays of the decision traces and the SHA-256 digests of their traces; each trace is also written to the test results
     * ({@code audio-trace-<replay>.txt}).
     */
    private static final @NotNull List<String> TRACE_REPLAYS = List.of(GameTestResources.REPLAY_TERRAN_VS_COMPUTER,
                                                                       GameTestResources.REPLAY_PVZ_JUDGEMENT_DAY,
                                                                       GameTestResources.REPLAY_ZVZ_HEARTBREAK_RIDGE);
    private static final @NotNull List<String> TRACE_DIGESTS = List.of("b93163768a5592f2cf9a8b545c3a20413dcadd96e603a0a2f25edffef9566ebc",
                                                                       "94180003fb197f5884ad663512972c660462844b2df5cd744cd4ad4a2998f17e",
                                                                       "612cff7c0540b384b3f67c7f0e207315682addf1d6c6290917e461bcc189c578");
    /**
     * The game loops played before a trace, whose sounds are discarded, and the game loops of a trace; each loop is {@link #FRAME_DURATION}
     * milliseconds of the simulated clock.
     */
    private static final int TRACE_SKIPPED_LOOPS = 6000;
    private static final int TRACE_LOOPS = 4000;
    /**
     * The game loops between camera moves: the camera moves to the first positioned sound of a loop at most this often.
     */
    private static final int TRACE_CAMERA_LOOPS = 100;
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final int TRACE_START_HOST_TICK = 0;
    private static final @NotNull String TRACE_FILE_PREFIX = "audio-trace-";
    private static final @NotNull String TRACE_FILE_SUFFIX = ".txt";
    private static final @NotNull String TRACE_DIGEST_ALGORITHM = "SHA-256";

    /**
     * A silent device that writes every buffer upload, voice start and stop of a playing voice into a trace, with the game loop.
     */
    private static final class TraceDevice implements AudioDevice {
        private final @NotNull SilentAudioDevice device;
        private final @NotNull StringBuilder trace;
        private final @NotNull List<AudioBuffer> buffers;

        private int loop;
        private int voiceCount;

        /**
         * Creates a {@link TraceDevice} instance.
         *
         * @param device the silent device that times the voices
         * @param trace  receives the trace lines
         */
        private TraceDevice(@NotNull SilentAudioDevice device, @NotNull StringBuilder trace) {
            this.device  = device;
            this.trace   = trace;
            this.buffers = new ArrayList<>();

            this.loop       = 0;
            this.voiceCount = 0;
        }

        /**
         * Sets the game loop of the following trace lines.
         *
         * @param loop the game loop
         */
        private void setLoop(int loop) {
            this.loop = loop;
        }

        @Override
        public long getTime() {
            return this.device.getTime();
        }

        @Override
        public @NotNull AudioBuffer createBuffer(@NotNull SoundClip clip) {
            AudioBuffer buffer = this.device.createBuffer(clip);

            this.trace.append(this.loop).append(" buffer ").append(this.buffers.size()).append(' ').append(clip.getChannelCount()).append(' ')
                      .append(clip.getSampleRate()).append(' ').append(clip.getFrameCount()).append('\n');
            this.buffers.add(buffer);

            return buffer;
        }

        @Override
        public @NotNull AudioVoice createVoice() {
            return new TraceVoice(this, this.device.createVoice(), this.voiceCount++);
        }

        @Override
        public void close() {
            this.device.close();
        }
    }

    /**
     * A voice of a {@link TraceDevice}.
     */
    private static final class TraceVoice implements AudioVoice {
        private final @NotNull TraceDevice owner;
        private final @NotNull AudioVoice voice;
        private final int number;

        private @Nullable AudioBuffer buffer;
        private boolean looping;
        private float gain;
        private float pan;

        /**
         * Creates a {@link TraceVoice} instance.
         *
         * @param owner  the device
         * @param voice  the silent voice
         * @param number the voice's creation number
         */
        private TraceVoice(@NotNull TraceDevice owner, @NotNull AudioVoice voice, int number) {
            this.owner  = owner;
            this.voice  = voice;
            this.number = number;

            this.buffer  = null;
            this.looping = false;
            this.gain    = 1.0f;
            this.pan     = 0.0f;
        }

        @Override
        public boolean isPlaying() {
            return this.voice.isPlaying();
        }

        @Override
        public void setBuffer(@Nullable AudioBuffer buffer) {
            this.voice.setBuffer(buffer);
            this.buffer = buffer;
        }

        @Override
        public void setLooping(boolean looping) {
            this.voice.setLooping(looping);
            this.looping = looping;
        }

        @Override
        public void setGain(float gain) {
            this.voice.setGain(gain);
            this.gain = gain;
        }

        @Override
        public void setPan(float pan) {
            this.voice.setPan(pan);
            this.pan = pan;
        }

        @Override
        public void start() {
            this.voice.start();
            this.owner.trace.append(this.owner.loop).append(" start ").append(this.number).append(' ')
                            .append(this.buffer == null ? -1 : this.owner.buffers.indexOf(this.buffer)).append(' ').append(this.gain).append(' ')
                            .append(this.pan).append(' ').append(this.looping).append('\n');
        }

        @Override
        public void stop() {
            if (this.voice.isPlaying()) {
                this.owner.trace.append(this.owner.loop).append(" stop ").append(this.number).append('\n');
            }

            this.voice.stop();
        }

        @Override
        public void queueBuffer(@NotNull AudioBuffer buffer) {
            this.voice.queueBuffer(buffer);
        }

        @Override
        public int getQueuedBufferCount() {
            return this.voice.getQueuedBufferCount();
        }

        @Override
        public int getProcessedBufferCount() {
            return this.voice.getProcessedBufferCount();
        }

        @Override
        public @NotNull AudioBuffer unqueueBuffer() {
            return this.voice.unqueueBuffer();
        }

        @Override
        public void release() {
            this.voice.release();
        }
    }

    /**
     * Steps the callback until its condition succeeds or the frame budget is exhausted.
     *
     * @param step   the frame callback
     * @param done   the completion predicate
     * @param frames the maximum number of steps
     */
    private static void stepUntil(@NotNull Runnable step, @NotNull BooleanSupplier done, int frames) {
        for (int index = 0; index < frames && !done.getAsBoolean(); index++) {
            step.run();
        }
    }

    /**
     * Finds the first allocated, visible command center of an owner.
     *
     * @param state the initialized world
     * @param owner the required owner
     * @return the matching unit, or {@code null} if none is present
     */
    private static @Nullable Unit findCommandCenter(@NotNull Game state, int owner) {
        UnitPool pool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int index = 1; index <= pool.getCapacity(); index++) {
            Unit unit = pool.getUnit(index);

            if (pool.isAllocated(index) && unit.getSpriteOrNull() != null && unit.getOwnerIndex() == owner &&
                unit.getUnitId() == ScrUnitIds.TERRAN_COMMAND_CENTER) {
                return unit;
            }
        }

        return null;
    }

    /**
     * Encodes the allocated unit handle into two little-endian bytes.
     *
     * @param state the initialized world
     * @param unit  the selected unit
     * @return the encoded handle bytes
     */
    private static byte @NotNull [] encodeUnitHandle(@NotNull Game state, @NotNull Unit unit) {
        UnitPool unitPool = state.getUnitSystem().getUnitPool();
        int poolIndex = unit.getIndex();

        Assertions.assertNotNull(unitPool);

        int value = unitPool.getHandle(poolIndex);

        return new byte[]{(byte) value, (byte) (value >>> Byte.SIZE)};
    }

    /**
     * Finds the first drained sound identifier inside the inclusive range.
     *
     * @param heard the drained event records
     * @param first the first identifier in the range
     * @param last  the last identifier in the range
     * @return the event record, or {@code null} if no sound matches
     */
    private static int @Nullable [] findSoundEvent(@NotNull List<int[]> heard, int first, int last) {
        for (int[] sound : heard) {
            if (sound[SOUND_IDENTIFIER_FIELD] >= first && sound[SOUND_IDENTIFIER_FIELD] <= last) {
                return sound;
            }
        }

        return null;
    }

    /**
     * Formats drained sound identifiers for a failed fixture assertion.
     *
     * @param heard the drained event records
     * @return the identifier list
     */
    private static @NotNull String formatSoundEvents(@NotNull List<int[]> heard) {
        StringBuilder text = new StringBuilder();

        for (int[] sound : heard) {
            text.append(sound[SOUND_IDENTIFIER_FIELD]).append(' ');
        }

        return text.toString();
    }

    /**
     * Plays a replay for {@link #TRACE_SKIPPED_LOOPS} game loops without sound, then for {@link #TRACE_LOOPS} game loops with a camera that
     * follows the sounds, and records every drained sound event (sound, source unit type, position, volume, priority, property bits,
     * availability) and every device decision of the playback.
     *
     * @param gameData the game data
     * @param replay   the replay, a {@link GameTestResources} name
     * @return the trace
     * @throws IOException if the replay cannot be read
     */
    private static @NotNull String recordTrace(@NotNull GameData gameData, @NotNull String replay) throws IOException {
        ReplayFile replayFile = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(replay)));
        byte[] mapContents = replayFile.getMapData();

        Assertions.assertNotNull(mapContents);

        ChkFile map = ChkFile.parseTolerant(mapContents);
        ReplayRuntime runtime = new ReplayRuntime(replayFile,
                                                  map,
                                                  gameData,
                                                  RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                  RunningGameScenario.INSTRUCTION_LIMIT);

        runtime.start(0, TRACE_START_HOST_TICK);

        Assertions.assertNull(runtime.getFailure());

        Game game = runtime.getGame();
        ClientCamera camera = new ClientCamera(map.getWidth() * TILE_SIZE, map.getHeight() * TILE_SIZE);
        StringBuilder trace = new StringBuilder();
        long[] time = {0L};
        int[] eventIndex = {0};
        ScrSoundTypeView sounds = gameData.getSoundTypes();
        SoundEvents events = game.getInterfaceLink().getSoundEvents();

        int cameraLoop = TRACE_SKIPPED_LOOPS - TRACE_CAMERA_LOOPS;

        camera.centerOn(map.getWidth() * TILE_SIZE / 2.0f, map.getHeight() * TILE_SIZE / 2.0f);

        TraceDevice device = new TraceDevice(new SilentAudioDevice(() -> time[0]), trace);
        SoundSettings sound = new SoundSettings();

        // The traces record the gains of the playback itself, so the sound effects bus stays at full gain.
        sound.setSoundEffectsVolume(SoundSettings.MAXIMUM_VOLUME);

        try (ClientAudio audio = new ClientAudio(new AudioMixer(device), sound);
             ClientSoundPlayback playback = new ClientSoundPlayback(audio)) {
            playback.setObserver((soundId, volume, available) -> {
                int index = eventIndex[0]++;
                boolean known = soundId >= 0 && soundId < sounds.getCount();

                trace.append(game.getCurrentFrame()).append(" event ").append(soundId).append(' ').append(events.getSourceUnitType(index))
                     .append(' ').append(events.getPositionX(index)).append(' ').append(events.getPositionY(index)).append(' ').append(volume)
                     .append(' ').append(known ? sounds.getPriority(soundId) : -1).append(' ').append(known ? sounds.getFlags(soundId) : -1)
                     .append(' ').append(available).append('\n');
            });

            for (int loop = 0; loop < TRACE_SKIPPED_LOOPS && runtime.getFailure() == null; loop++) {
                runtime.step(FRAME_DURATION, loop * FRAME_DURATION);
                events.clear();
            }

            for (int loop = TRACE_SKIPPED_LOOPS; loop < TRACE_SKIPPED_LOOPS + TRACE_LOOPS; loop++) {
                time[0] = loop * FRAME_DURATION * NANOSECONDS_PER_MILLISECOND;
                device.setLoop(loop);
                eventIndex[0] = 0;

                runtime.step(FRAME_DURATION, loop * FRAME_DURATION);

                boolean positioned = events.getCount() > 0 && (events.getPositionX(0) != 0 || events.getPositionY(0) != 0);

                if (positioned && loop - cameraLoop >= TRACE_CAMERA_LOOPS) {
                    cameraLoop = loop;
                    camera.centerOn(events.getPositionX(0), events.getPositionY(0));
                    trace.append(loop).append(" camera ").append(events.getPositionX(0)).append(' ').append(events.getPositionY(0)).append('\n');
                }

                playback.drain(new ScrGameView(game), new ScrPresentation(game), camera);

                if (runtime.getFailure() != null || runtime.getSession() == null || runtime.getSession().isEnded()) {
                    break;
                }
            }
        }

        return trace.toString();
    }

    @Test
    void replay_sound_decisions_match_recorded_traces() throws IOException, NoSuchAlgorithmException {
        List<String> digests = new ArrayList<>();

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (int index = 0; index < TRACE_REPLAYS.size(); index++) {
                String replay = TRACE_REPLAYS.get(index);
                String trace = recordTrace(gameData, replay);
                byte[] contents = trace.getBytes(StandardCharsets.UTF_8);
                String name = Path.of(replay).getFileName().toString();
                Path result = TestResultUtils.getResultPath(TRACE_FILE_PREFIX + name + TRACE_FILE_SUFFIX);

                Files.createDirectories(result.toAbsolutePath().getParent());
                Files.write(result, contents);

                digests.add(HexFormat.of().formatHex(MessageDigest.getInstance(TRACE_DIGEST_ALGORITHM).digest(contents)));
            }
        }

        Assertions.assertEquals(TRACE_DIGESTS, digests, "the decision traces changed, see " + TRACE_FILE_PREFIX + "*" + TRACE_FILE_SUFFIX);
    }

    @Test
    void command_and_order_sounds_reach_playback_layer() throws IOException {
        Path mapPath;

        try (Stream<Path> maps = Files.list(ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER))) {
            mapPath = maps.filter(path -> path.getFileName().toString().endsWith(MELEE_MAP_EXTENSION)).sorted().findFirst().orElse(null);
        }

        Assertions.assertNotNull(mapPath, "An installed melee map is required.");

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            MeleeGameSetup setup = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, new int[]{ScrRaceIds.ZERG}, FASTEST_SPEED);
            LiveGame game = new LiveGame(setup, GAME_SEED);
            ChkFile map = ChkFile.parseTolerant(mapData);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(),
                                                              map,
                                                              gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            Game state = live.getGame();
            int local = game.getLocalPlayer();
            List<int[]> heard = new ArrayList<>();
            ClientCamera camera = new ClientCamera(map.getWidth() * TILE_SIZE, map.getHeight() * TILE_SIZE);

            try (ClientSoundPlayback playback = new ClientSoundPlayback(null)) {
                // No audio in tests: the observer still sees every drained event.
                playback.setEnabled(false);
                playback.setObserver((soundId, volume, available) -> heard.add(new int[]{soundId, volume, available ? AVAILABLE : 0}));

                int[] frame = {0};
                Runnable step = () -> {
                    game.exchangeTurn(state.getCurrentFrame());

                    Assertions.assertTrue(live.step(FRAME_DURATION, frame[0]++ * FRAME_DURATION));

                    playback.drain(new ScrGameView(state), new ScrPresentation(state), camera);
                };

                for (int index = 0; index < STARTUP_FRAMES; index++) {
                    step.run();
                }

                Unit center = findCommandCenter(state, local);

                Assertions.assertNotNull(center, "the local Command Center");

                Sprite horizontalSprite = center.getSpriteOrNull();

                Assertions.assertNotNull(horizontalSprite);

                int x = horizontalSprite.getX() + UNIT_DISTANCE;
                Sprite verticalSprite = center.getSpriteOrNull();

                Assertions.assertNotNull(verticalSprite);

                int y = verticalSprite.getY() + UNIT_DISTANCE;

                camera.centerOn(x, y);

                // Units are created as a trigger action would: inside a frame phase of the execution scope.
                GameExecutionScope creationScope = state.getExecutionScope();
                int creationFrame = state.getCurrentFrame();

                Assertions.assertNotNull(creationScope);

                creationScope.beginFramePhase(creationFrame);

                Unit marine;
                Unit passenger;
                Unit second;
                Unit dropship;

                try {
                    marine    = state.getTriggers().createUnit(ScrUnitIds.TERRAN_MARINE, x, y, local, INSTRUCTION_LIMIT);
                    passenger = state.getTriggers().createUnit(ScrUnitIds.TERRAN_MARINE, x + PASSENGER_OFFSET, y, local, INSTRUCTION_LIMIT);
                    second    = state.getTriggers().createUnit(ScrUnitIds.TERRAN_MARINE, x + SECOND_PASSENGER_OFFSET, y, local,
                                                               INSTRUCTION_LIMIT);
                    dropship  = state.getTriggers().createUnit(ScrUnitIds.TERRAN_DROPSHIP,
                                                               x + DROPSHIP_HORIZONTAL_OFFSET, y + DROPSHIP_VERTICAL_OFFSET, local,
                                                               INSTRUCTION_LIMIT);
                } finally {
                    GameExecutionScope endingScope = state.getExecutionScope();

                    Assertions.assertNotNull(endingScope);

                    endingScope.endFramePhase();
                }

                Assertions.assertNotNull(marine);
                Assertions.assertNotNull(passenger);
                Assertions.assertNotNull(second);
                Assertions.assertNotNull(dropship);

                PlayerTechnology technology = state.getPlayerTable().getPlayerTechnology();
                int technologyIdentifier = ScrTechIds.STIM_PACKS;

                Assertions.assertNotNull(technology);

                technology.setResearched(local, technologyIdentifier, true);

                // Stim Pack: the sound is raised while the command executes, before the frame increment.
                heard.clear();

                byte[] marineHandle = encodeUnitHandle(state, marine);

                game.submit(ReplayCommandIds.SELECT, new byte[]{1, marineHandle[0], marineHandle[1]});
                game.submit(ReplayCommandIds.STIM, new byte[0]);
                stepUntil(step, () -> findSoundEvent(heard, FIRST_STIM_SOUND, LAST_STIM_SOUND) != null, SOUND_FRAMES);

                int[] stim = findSoundEvent(heard, FIRST_STIM_SOUND, LAST_STIM_SOUND);

                Assertions.assertNotNull(stim, "Stim Pack sound drained: " + formatSoundEvents(heard));
                Assertions.assertEquals(AVAILABLE, stim[AVAILABLE_FIELD], "Stim Pack sound file resolves");
                Assertions.assertTrue(stim[VOLUME_FIELD] > AUDIBLE_VOLUME, "Stim Pack sound is audible on screen");

                // Load two marines, then unload one by command and the other by the Unload All order.
                for (Unit boarding : new Unit[]{passenger, second}) {
                    byte[] boardingHandle = encodeUnitHandle(state, boarding);

                    game.submit(ReplayCommandIds.SELECT, new byte[]{1, boardingHandle[0], boardingHandle[1]});

                    Sprite targetHorizontalSprite = dropship.getSpriteOrNull();

                    Assertions.assertNotNull(targetHorizontalSprite);

                    int targetX = targetHorizontalSprite.getX();
                    Sprite targetVerticalSprite = dropship.getSpriteOrNull();

                    Assertions.assertNotNull(targetVerticalSprite);

                    int targetY = targetVerticalSprite.getY();
                    byte[] targetHandle = encodeUnitHandle(state, dropship);
                    int targetType = dropship.getUnitId();
                    byte[] targetPayload = {(byte) targetX, (byte) (targetX >>> Byte.SIZE),
                                            (byte) targetY, (byte) (targetY >>> Byte.SIZE),
                                            targetHandle[0], targetHandle[1],
                                            (byte) targetType, (byte) (targetType >>> Byte.SIZE), 0};

                    game.submit(ReplayCommandIds.RIGHT_CLICK, targetPayload);
                    stepUntil(step, () -> (boarding.getStatus() & LOADED_STATUS) != 0, BOARDING_FRAMES);

                    Assertions.assertNotEquals(0, boarding.getStatus() & LOADED_STATUS, "marine boarded the dropship");
                }

                heard.clear();

                byte[] dropshipHandle = encodeUnitHandle(state, dropship);

                game.submit(ReplayCommandIds.SELECT, new byte[]{1, dropshipHandle[0], dropshipHandle[1]});
                game.submit(ReplayCommandIds.UNLOAD, encodeUnitHandle(state, passenger));
                stepUntil(step, () -> findSoundEvent(heard, TERRAN_TRANSPORT_UNLOAD_SOUND, TERRAN_TRANSPORT_UNLOAD_SOUND) != null, SOUND_FRAMES);

                Assertions.assertNotNull(findSoundEvent(heard, TERRAN_TRANSPORT_UNLOAD_SOUND, TERRAN_TRANSPORT_UNLOAD_SOUND),
                                         "Unload command sound drained: " + formatSoundEvents(heard));

                heard.clear();
                stepUntil(step, () -> false, SETTLE_FRAMES);
                game.submit(ReplayCommandIds.UNLOAD_ALL, new byte[]{0});
                stepUntil(step, () -> findSoundEvent(heard, TERRAN_TRANSPORT_UNLOAD_SOUND, TERRAN_TRANSPORT_UNLOAD_SOUND) != null, UNLOAD_ALL_FRAMES);

                int[] unloadAll = findSoundEvent(heard, TERRAN_TRANSPORT_UNLOAD_SOUND, TERRAN_TRANSPORT_UNLOAD_SOUND);

                Assertions.assertNotNull(unloadAll, "Unload All sound drained: " + formatSoundEvents(heard));
                Assertions.assertEquals(AVAILABLE, unloadAll[AVAILABLE_FIELD], "unload sound file resolves");
            }
        }
    }
}

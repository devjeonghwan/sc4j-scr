package org.fogofwarlabs.sc4j.scr.client.audio;

import org.fogofwarlabs.sc4j.engine.client.audio.*;
import org.fogofwarlabs.sc4j.engine.resource.audio.SoundClip;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks game track chains, initial tracks and glue track names of {@link Music}, and that the first buffer of a track plays at the saved
 * music volume.
 */
final class MusicTest {
    private static final int NO_TRACK = 0;
    private static final float DELTA = 1e-6f;

    /**
     * The track of the title screen, the first music of a client start.
     */
    private static final @NotNull String TITLE_TRACK = "title";
    private static final int SAVED_MUSIC_VOLUME = 40;

    /**
     * A silent device whose voices record their gain at each start.
     */
    private static final class StartGainDevice implements AudioDevice {
        private final @NotNull SilentAudioDevice device;
        private final @NotNull List<Float> startGains;

        private StartGainDevice() {
            this.device     = new SilentAudioDevice(() -> 0L);
            this.startGains = new ArrayList<>();
        }

        @Override
        public long getTime() {
            return this.device.getTime();
        }

        @Override
        public @NotNull AudioBuffer createBuffer(@NotNull SoundClip clip) {
            return this.device.createBuffer(clip);
        }

        @Override
        public @NotNull AudioVoice createVoice() {
            return new StartGainVoice(this, this.device.createVoice());
        }

        @Override
        public void close() {
            this.device.close();
        }
    }

    /**
     * A voice of a {@link StartGainDevice}.
     */
    private static final class StartGainVoice implements AudioVoice {
        private final @NotNull StartGainDevice owner;
        private final @NotNull AudioVoice voice;

        private float gain;

        private StartGainVoice(@NotNull StartGainDevice owner, @NotNull AudioVoice voice) {
            this.owner = owner;
            this.voice = voice;

            this.gain = 1.0f;
        }

        @Override
        public boolean isPlaying() {
            return this.voice.isPlaying();
        }

        @Override
        public void setBuffer(@Nullable AudioBuffer buffer) {
            this.voice.setBuffer(buffer);
        }

        @Override
        public void setLooping(boolean looping) {
            this.voice.setLooping(looping);
        }

        @Override
        public void setGain(float gain) {
            this.voice.setGain(gain);
            this.gain = gain;
        }

        @Override
        public void setPan(float pan) {
            this.voice.setPan(pan);
        }

        @Override
        public void start() {
            this.voice.start();
            this.owner.startGains.add(this.gain);
        }

        @Override
        public void stop() {
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
     * Opens the client audio with saved sound settings on a silent device, creates the music on it and starts the title track.
     *
     * @param sound the saved sound settings
     * @return the gain of the title track's voice when it started
     * @throws IOException if the installation cannot be read
     */
    private static float playTitle(@NotNull SoundSettings sound) throws IOException {
        StartGainDevice device = new StartGainDevice();

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath());
             ClientAudio audio = new ClientAudio(new AudioMixer(device), sound);
             Music music = new Music(storage, audio)) {
            music.play(Music.findTrack(TITLE_TRACK));

            Assertions.assertFalse(device.startGains.isEmpty(), "the title track starts");

            return device.startGains.getFirst();
        }
    }

    @Test
    void find_track_should_find_glue_tracks_by_name() {
        Assertions.assertEquals(25, Music.findTrack("title"));
        Assertions.assertEquals(13, Music.findTrack("zrdyroom"));
        Assertions.assertEquals(16, Music.findTrack("zdefeat"));
        Assertions.assertEquals(21, Music.findTrack("pvict"), "the first of the duplicated victory records");
        Assertions.assertEquals(NO_TRACK, Music.findTrack("missing"));
    }

    @Test
    void get_next_track_should_chain_game_tracks_of_race() {
        Assertions.assertEquals(2, Music.getNextTrack(1, 0));
        Assertions.assertEquals(4, Music.getNextTrack(3, 0));
        Assertions.assertEquals(1, Music.getNextTrack(4, 0), "the fourth Zerg track returns to the first");
        Assertions.assertEquals(9, Music.getNextTrack(12, 0));
        Assertions.assertEquals(1, Music.getNextTrack(27, 0), "Radio Free Zerg continues with the Zerg tracks");
        Assertions.assertEquals(NO_TRACK, Music.getNextTrack(25, 0), "a repeating glue track has no next track");
    }

    @Test
    void get_next_track_should_skip_fourth_track_in_original_campaign() {
        Assertions.assertEquals(1, Music.getNextTrack(3, 12), "Zerg mission 12 skips zerg4");
        Assertions.assertEquals(5, Music.getNextTrack(7, 1));
        Assertions.assertEquals(9, Music.getNextTrack(11, 31));

        Assertions.assertEquals(4, Music.getNextTrack(3, 32), "Brood War missions keep the fourth track");
    }

    @Test
    void get_game_track_should_choose_first_game_track() {
        // Outside campaigns: the race's first track plus the rotation.
        Assertions.assertEquals(1, Music.getGameTrack(0, 0, 0));
        Assertions.assertEquals(8, Music.getGameTrack(1, 0, 3));
        Assertions.assertEquals(10, Music.getGameTrack(2, 0, 1));

        // Original campaign: (mission - first mission of the race) % 3.
        Assertions.assertEquals(5, Music.getGameTrack(1, 1, 0));
        Assertions.assertEquals(7, Music.getGameTrack(1, 3, 0));
        Assertions.assertEquals(1, Music.getGameTrack(0, 15, 0));

        // Brood War: a cycle position of 0 plays the fourth track.
        Assertions.assertEquals(12, Music.getGameTrack(2, 32, 0));
        Assertions.assertEquals(10, Music.getGameTrack(2, 33, 0));
        Assertions.assertEquals(8, Music.getGameTrack(1, 40, 0));
    }

    @Test
    @RequiresInstallation
    void play_should_start_title_silent_when_saved_music_is_off() throws IOException {
        SoundSettings disabled = new SoundSettings();
        SoundSettings muted = new SoundSettings();

        disabled.setMusicEnabled(false);
        muted.setMusicVolume(0);

        Assertions.assertEquals(0.0f, playTitle(disabled), DELTA, "music disabled");
        Assertions.assertEquals(0.0f, playTitle(muted), DELTA, "music volume 0");
    }

    @Test
    @RequiresInstallation
    void play_should_start_title_at_saved_music_volume() throws IOException {
        SoundSettings sound = new SoundSettings();

        sound.setMusicVolume(SAVED_MUSIC_VOLUME);

        Assertions.assertEquals(ClientAudio.getGain(SAVED_MUSIC_VOLUME), playTitle(sound), DELTA);
    }
}

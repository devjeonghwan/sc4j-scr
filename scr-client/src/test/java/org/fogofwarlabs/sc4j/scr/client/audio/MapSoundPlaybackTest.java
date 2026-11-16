package org.fogofwarlabs.sc4j.scr.client.audio;

import org.fogofwarlabs.sc4j.engine.client.audio.AudioMixer;
import org.fogofwarlabs.sc4j.engine.client.audio.PlayingSound;
import org.fogofwarlabs.sc4j.engine.client.audio.SilentAudioDevice;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that map sound files play on the native number of map sound channels and that a map sound started while every channel plays does not
 * replace a playing one, so a long map music keeps playing under short trigger sounds.
 */
final class MapSoundPlaybackTest {
    private static final int SAMPLE_RATE = 22050;
    private static final int SAMPLE_BYTES = 2;
    private static final int MONO = 1;
    private static final int PCM_FORMAT = 1;
    private static final int FORMAT_CHUNK_SIZE = 16;
    /**
     * The bytes of the RIFF header, the format chunk and the data chunk header before the samples.
     */
    private static final int HEADER_BYTES = 44;
    /**
     * The RIFF size field counts the file from the WAVE tag on.
     */
    private static final int RIFF_SIZE_EXCLUDED = 8;
    private static final int VOLUME = 100;

    /**
     * Creates a silent mono 16-bit RIFF WAVE file of one second.
     *
     * @return the file
     */
    private static byte @NotNull [] createWave() {
        int dataBytes = SAMPLE_RATE * SAMPLE_BYTES;
        ByteBuffer file = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN);

        file.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(HEADER_BYTES + dataBytes - RIFF_SIZE_EXCLUDED);
        file.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        file.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(FORMAT_CHUNK_SIZE);
        file.putShort((short) PCM_FORMAT).putShort((short) MONO).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * SAMPLE_BYTES);
        file.putShort((short) SAMPLE_BYTES).putShort((short) (SAMPLE_BYTES * Byte.SIZE));
        file.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);

        return file.array();
    }

    @Test
    void play_data_should_not_replace_a_playing_map_sound_when_every_channel_plays() {
        byte[] wave = createWave();
        List<PlayingSound> sounds = new ArrayList<>();

        // The clock stands still, so every started sound keeps playing.
        try (ClientAudio audio = new ClientAudio(new AudioMixer(new SilentAudioDevice(() -> 0L)), new SoundSettings());
             ClientSoundPlayback playback = new ClientSoundPlayback(audio)) {
            for (int channel = 0; channel < ClientSoundPlayback.DATA_CHANNEL_COUNT; channel++) {
                PlayingSound sound = playback.playData(wave, VOLUME);

                Assertions.assertNotNull(sound, "Every map sound channel takes a sound.");
                sounds.add(sound);
            }

            Assertions.assertNull(playback.playData(wave, VOLUME), "A map sound without a free channel does not start.");

            for (PlayingSound sound : sounds) {
                Assertions.assertTrue(sound.isPlaying(), "No playing map sound gives way.");
            }
        }
    }
}

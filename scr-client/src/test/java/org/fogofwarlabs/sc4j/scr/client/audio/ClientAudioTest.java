package org.fogofwarlabs.sc4j.scr.client.audio;

import org.fogofwarlabs.sc4j.engine.client.audio.AudioMixer;
import org.fogofwarlabs.sc4j.engine.client.audio.SilentAudioDevice;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that {@link ClientAudio} opens its sound effects bus at the saved sound effect gain and follows changed settings.
 */
final class ClientAudioTest {
    private static final float DELTA = 1e-6f;
    private static final int SAVED_SOUND_EFFECTS_VOLUME = 30;
    private static final int CHANGED_SOUND_EFFECTS_VOLUME = 70;

    /**
     * Opens client audio with saved sound settings on a silent device.
     *
     * @param sound the saved sound settings
     * @return the client audio, closed by the caller
     */
    private static @NotNull ClientAudio openSilentAudio(@NotNull SoundSettings sound) {
        return new ClientAudio(new AudioMixer(new SilentAudioDevice(() -> 0L)), sound);
    }

    @Test
    void client_audio_opens_effects_bus_silent_when_saved_sound_effects_are_off() {
        SoundSettings sound = new SoundSettings();

        sound.setSoundEffectsEnabled(false);

        try (ClientAudio audio = openSilentAudio(sound)) {
            Assertions.assertEquals(0.0f, audio.getEffectsBus().getGain(), DELTA);
        }
    }

    @Test
    void apply_settings_should_follow_changed_sound_effects_volume() {
        SoundSettings sound = new SoundSettings();

        sound.setSoundEffectsVolume(SAVED_SOUND_EFFECTS_VOLUME);

        try (ClientAudio audio = openSilentAudio(sound)) {
            Assertions.assertEquals(ClientAudio.getGain(SAVED_SOUND_EFFECTS_VOLUME), audio.getEffectsBus().getGain(), DELTA);

            sound.setSoundEffectsVolume(CHANGED_SOUND_EFFECTS_VOLUME);
            audio.applySettings();

            Assertions.assertEquals(ClientAudio.getGain(CHANGED_SOUND_EFFECTS_VOLUME), audio.getEffectsBus().getGain(), DELTA);
        }
    }
}

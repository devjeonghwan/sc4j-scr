package org.fogofwarlabs.sc4j.scr.client.audio;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the defaults, unsigned volume clamps and mixer gains of {@link SoundSettings}.
 */
final class SoundSettingsTest {
    private static final int DEFAULT_MUSIC_VOLUME = 25;
    private static final int DEFAULT_SOUND_EFFECTS_VOLUME = 50;
    private static final int MAXIMUM_VOLUME = 100;

    @Test
    void new_sound_settings_start_with_defaults() {
        SoundSettings settings = new SoundSettings();

        Assertions.assertEquals(DEFAULT_MUSIC_VOLUME, settings.getMusicVolume());
        Assertions.assertEquals(DEFAULT_SOUND_EFFECTS_VOLUME, settings.getSoundEffectsVolume());
        Assertions.assertTrue(settings.isMusicEnabled());
        Assertions.assertTrue(settings.isSoundEffectsEnabled());
        Assertions.assertTrue(settings.isUnitSpeech());
        Assertions.assertTrue(settings.isUnitAcknowledgments());
        Assertions.assertTrue(settings.isBuildingSounds());
        Assertions.assertTrue(settings.isSoundInBackground());
        Assertions.assertTrue(settings.isGameSubtitles());
    }

    @Test
    void volume_setters_clamp_as_unsigned_values() {
        SoundSettings settings = new SoundSettings();

        settings.setMusicVolume(150);

        Assertions.assertEquals(MAXIMUM_VOLUME, settings.getMusicVolume());

        // Negative volumes clamp to the maximum because the setters compare unsigned.
        settings.setSoundEffectsVolume(-5);

        Assertions.assertEquals(MAXIMUM_VOLUME, settings.getSoundEffectsVolume());

        settings.setSoundEffectsVolume(0);

        Assertions.assertEquals(0, settings.getSoundEffectsVolume());
    }

    @Test
    void volumes_map_to_linear_group_gains() {
        SoundSettings settings = new SoundSettings();

        Assertions.assertEquals(0.25f, settings.getMusicGain());
        Assertions.assertEquals(0.5f, settings.getSoundEffectsGain());

        settings.setMusicEnabled(false);
        settings.setSoundEffectsVolume(0);

        Assertions.assertEquals(0.0f, settings.getMusicGain());
        Assertions.assertEquals(0.0f, settings.getSoundEffectsGain());
        Assertions.assertFalse(settings.isMusicAudible());
        Assertions.assertFalse(settings.isSoundEffectsAudible(), "a volume of 0 stops sound effects from starting");

        Assertions.assertEquals(0.5f, ClientAudio.getGain(50));
        Assertions.assertEquals(1.0f, ClientAudio.getGain(150));
    }
}

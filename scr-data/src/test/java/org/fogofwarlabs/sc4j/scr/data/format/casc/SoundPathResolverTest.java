package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Tests the localized, audio-set and raw-byte sound path resolution of {@link SoundPathResolver}.
 */
@RequiresInstallation
final class SoundPathResolverTest {
    @Test
    void resolve_should_pick_localized_voices_classic_only_files_and_null_for_missing_sounds() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            SoundPathResolver korean = new SoundPathResolver(storage, "koKR", SoundPathResolver.AudioSet.REMASTERED);

            // The Dark Archon voice (sfxdata 1065) has a koKR recording.
            Assertions.assertEquals("locales/koKR/assets/sound/Protoss/DARCHON/PDaRdy00.WAV", korean.resolve("Protoss\\DARCHON\\PDaRdy00.WAV"));
            // Sfxdata 568 exists only in the classic set.
            Assertions.assertEquals("SD/sound/Protoss/ARCHON/PArMin00.WAV", korean.resolve("Protoss\\ARCHON\\PArMin00.WAV"));
            // Sfxdata 1066 exists nowhere in the storage.
            Assertions.assertNull(korean.resolve("Protoss\\DARCHON\\PDrDth00.WAV"));
            Assertions.assertThrows(IOException.class, () -> korean.read("Protoss\\DARCHON\\PDrDth00.WAV"));

            SoundPathResolver classic = new SoundPathResolver(storage, "enUS", SoundPathResolver.AudioSet.CLASSIC);

            Assertions.assertEquals("SD/sound/Zerg/Drone/ZDrErr00.WAV", classic.resolve("Zerg\\Drone\\ZDrErr00.WAV"));

            SoundPathResolver english = new SoundPathResolver(storage, "enUS", SoundPathResolver.AudioSet.REMASTERED);

            Assertions.assertEquals("sound/Zerg/Drone/ZDrErr00.WAV", english.resolve("Zerg\\Drone\\ZDrErr00.WAV"));
        }
    }

    @Test
    void raw_asset_path_bytes_resolve_in_storage_and_read_nothing_without_map_archive() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            SoundPathResolver resolver = new SoundPathResolver(storage, "enUS", SoundPathResolver.AudioSet.CLASSIC);
            byte[] asciiPath = "sound\\Zerg\\Drone\\ZDrErr00.WAV".getBytes(StandardCharsets.US_ASCII);

            Assertions.assertEquals(resolver.resolveAsset("sound\\Zerg\\Drone\\ZDrErr00.WAV"), resolver.resolveAsset(asciiPath));
            Assertions.assertNotNull(resolver.readAsset(asciiPath, null));

            // CP949 bytes are not UTF-8: no storage name matches, and without a map archive nothing is read.
            byte[] koreanPath = "staredit\\wav\\한.wav".getBytes(Charset.forName("x-windows-949"));

            Assertions.assertNull(resolver.resolveAsset(koreanPath));
            Assertions.assertNull(resolver.readAsset(koreanPath, null));
        }
    }
}

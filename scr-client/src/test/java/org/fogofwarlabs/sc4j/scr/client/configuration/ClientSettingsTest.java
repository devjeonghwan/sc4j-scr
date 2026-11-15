package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.fogofwarlabs.sc4j.engine.client.settings.SettingsStore;
import org.fogofwarlabs.sc4j.scr.client.audio.SoundSettings;
import org.fogofwarlabs.sc4j.scr.client.ui.input.HotkeyBindingsUtils;
import org.fogofwarlabs.sc4j.scr.client.ui.input.HotkeyProfile;
import org.fogofwarlabs.sc4j.scr.data.format.casc.SoundPathResolver;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Verifies the client settings file ({@code sc4j-settings.json}) with a synthetic file: values are read by group, among them the graphics, sound and
 * hotkey values, unknown keys are reported, invalid values are rejected, values are written back by the one writer and launch options override the
 * file.
 */
final class ClientSettingsTest {
    /**
     * The speed index of {@code x2}.
     */
    private static final int DOUBLE_SPEED_INDEX = 3;
    /**
     * The neutral gamma, the default of the Video options.
     */
    private static final int NEUTRAL_GAMMA = 100;

    /**
     * The hotkey actions the hotkey tests bind: Move, Stop and Train SCV.
     */
    private static final int MOVE = 664;
    private static final int STOP = 665;
    private static final int TRAIN_SCV = 592;

    private static final @NotNull String DEVICE_PROFILE = "$device";
    private static final @NotNull String NO_HOTKEYS_PROFILE = "$NoHotkeys";

    private static final @NotNull String SETTINGS = """
                                                    {
                                                      "graphics": {"renderer": "hd2", "vsync": "adaptive", "fpsLimitEnabled": true, "fpsLimit": 144,
                                                                   "hdSkin": "carbot", "hdLighting": false, "bloom": true},
                                                      "audio": {"set": "classic"},
                                                      "input": {"mouseConfine": false},
                                                      "game": {"speed": "x2", "locale": "koKR"},
                                                      "maps": {"compatibility": true}
                                                    }
                                                    """;

    /**
     * A settings file as the client writes it: the stable layout, one line per group, the groups in the order of their first write.
     */
    private static final @NotNull String WRITTEN_SETTINGS = """
                                                            {
                                                              "createGame": {"type": 3, "speed": 6, "subtype": 1},
                                                              "graphics": {"hdLighting": true, "renderer": "hd", "vsync": "off", "fpsLimit": 300, "windowMode": 1},
                                                              "game": {"gameSpeed": 6, "gameTimer": true, "tipNumber": 101, "consoleSplit": true, "locale": "enUS"},
                                                              "input": {"mouseConfine": false, "hardwareCursor": true, "mouseScaling": false},
                                                              "audio": {"set": "remastered", "musicVolume": 75, "sfxVolume": 35, "musicEnabled": false}
                                                            }
                                                            """;
    private static final int WRITTEN_FRAME_RATE_LIMIT = 300;
    private static final int WRITTEN_TIP_NUMBER = 101;
    private static final int WRITTEN_SOUND_EFFECTS_VOLUME = 35;
    private static final int WRITTEN_GAME_SPEED = 6;
    private static final int WRITTEN_GAME_TYPE = 3;

    /**
     * Writes a temporary settings file.
     *
     * @param contents the file contents
     * @return the written file
     * @throws IOException if the file cannot be created or written
     */
    private static @NotNull Path write(@NotNull String contents) throws IOException {
        Path file = Files.createTempFile("sc4j-settings", ".json");

        Files.writeString(file, contents, StandardCharsets.UTF_8);

        return file;
    }

    @Test
    void load_should_read_every_group_and_report_ignored_keys() throws IOException {
        Path file = write(SETTINGS);

        try {
            ClientSettings settings = ClientSettings.load(file);
            GraphicsSettings graphics = new GraphicsSettings();

            settings.applyTo(graphics);

            Assertions.assertEquals(GraphicsSettings.Vsync.ADAPTIVE, graphics.getVsync());
            Assertions.assertTrue(graphics.isFrameRateLimitEnabled());
            Assertions.assertEquals(144, graphics.getFrameRateLimit());
            Assertions.assertTrue(settings.hasRenderer());
            Assertions.assertEquals(HdRenderAssets.Quality.HD2, settings.getRendererQuality());
            Assertions.assertEquals("Carbot", settings.getHdSkin());
            Assertions.assertEquals(Boolean.FALSE, settings.getHdLighting());
            Assertions.assertEquals(SoundPathResolver.AudioSet.CLASSIC, settings.getAudioSet());
            Assertions.assertEquals(Boolean.FALSE, settings.getMouseConfine());
            Assertions.assertEquals(DOUBLE_SPEED_INDEX, settings.getSpeedIndex());
            Assertions.assertEquals("koKR", settings.getLocale());
            Assertions.assertEquals(Boolean.TRUE, settings.getMapCompatibility());
            Assertions.assertEquals(List.of("graphics.bloom"), settings.getIgnored());
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void load_should_read_the_file_the_client_writes() throws IOException {
        Path file = write(WRITTEN_SETTINGS);

        try {
            ClientSettings settings = ClientSettings.load(file);
            GraphicsSettings graphics = new GraphicsSettings();
            InterfaceSettings interfaceSettings = new InterfaceSettings();
            SoundSettings sound = new SoundSettings();

            settings.applyTo(graphics);
            settings.applyTo(interfaceSettings);
            settings.applyTo(sound);

            Assertions.assertEquals(HdRenderAssets.Quality.HD, settings.getRendererQuality());
            Assertions.assertEquals(Boolean.TRUE, settings.getHdLighting());
            Assertions.assertEquals(GraphicsSettings.Vsync.OFF, graphics.getVsync());
            Assertions.assertEquals(WRITTEN_FRAME_RATE_LIMIT, graphics.getFrameRateLimit());
            Assertions.assertEquals(GraphicsSettings.WINDOWED_FULLSCREEN, graphics.getWindowMode());
            Assertions.assertEquals(WRITTEN_GAME_SPEED, settings.getGameSpeed());
            Assertions.assertEquals(WRITTEN_TIP_NUMBER, interfaceSettings.getTipNumber());
            Assertions.assertTrue(interfaceSettings.isConsoleSplit());
            Assertions.assertEquals("enUS", settings.getLocale());
            Assertions.assertEquals(Boolean.FALSE, settings.getMouseConfine());
            Assertions.assertEquals(SoundPathResolver.AudioSet.REMASTERED, settings.getAudioSet());
            Assertions.assertEquals(WRITTEN_SOUND_EFFECTS_VOLUME, sound.getSoundEffectsVolume());
            Assertions.assertFalse(sound.isMusicEnabled());
            Assertions.assertEquals(WRITTEN_GAME_TYPE, settings.getCreateGame().get("type"));
            Assertions.assertTrue(settings.getIgnored().isEmpty());

            SettingsStore.write(file, "createGame", Map.of("type", WRITTEN_GAME_TYPE));

            Assertions.assertEquals(WRITTEN_SETTINGS, Files.readString(file, StandardCharsets.UTF_8), "an unchanged value leaves the same bytes");
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void load_should_reject_invalid_values() throws IOException {
        for (String contents : List.of("{\"graphics\": {\"fpsLimit\": 5000}}", "{\"audio\": {\"set\": \"hd\"}}",
                                       "{\"graphics\": {\"vsync\": 1}}", "[]")) {
            Path file = write(contents);

            try {
                Assertions.assertThrows(Exception.class, () -> ClientSettings.load(file), contents);
            } finally {
                Files.delete(file);
            }
        }
    }

    @Test
    void launch_options_override_settings_file() throws IOException {
        Path file = write(SETTINGS);

        try {
            ClientOptions fromFile = ClientOptions.parse(new String[]{"--settings", file.toString()});

            Assertions.assertEquals(GraphicsSettings.Vsync.ADAPTIVE, fromFile.getGraphicsSettings().getVsync());
            Assertions.assertEquals(144, fromFile.getGraphicsSettings().getFrameRateLimit());
            Assertions.assertEquals(HdRenderAssets.Quality.HD2, fromFile.getSceneQuality());
            Assertions.assertEquals("Carbot", fromFile.getHdSkin());
            Assertions.assertEquals(SoundPathResolver.AudioSet.CLASSIC, fromFile.getAudioSet());
            Assertions.assertEquals("koKR", fromFile.getLocale());

            ClientOptions overridden = ClientOptions.parse(new String[]{"--settings", file.toString(), "--vsync", "off", "--max-fps", "60",
                                                                        "--hd-skin", "default", "--renderer", "classic",
                                                                        "--audio", "remastered",
                                                                        "--locale", "enUS"});

            Assertions.assertEquals(GraphicsSettings.Vsync.OFF, overridden.getGraphicsSettings().getVsync());
            Assertions.assertEquals(60, overridden.getGraphicsSettings().getFrameRateLimit());
            Assertions.assertNull(overridden.getHdSkin());
            Assertions.assertNull(overridden.getSceneQuality());
            Assertions.assertEquals(SoundPathResolver.AudioSet.REMASTERED, overridden.getAudioSet());
            Assertions.assertEquals("enUS", overridden.getLocale());

            ClientOptions none = ClientOptions.parse(new String[]{"--settings", "none"});

            Assertions.assertNull(none.getHdSkin());
            Assertions.assertEquals(SoundPathResolver.AudioSet.REMASTERED, none.getAudioSet());
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void data_directory_setting_is_read_validated_and_overridden_by_data_option() throws IOException {
        Path file = write("{\"paths\": {\"data\": \"games/SC\"}}");

        try {
            Assertions.assertEquals("games/SC", ClientSettings.load(file).getDataDirectory());

            ClientOptions fromFile = ClientOptions.parse(new String[]{"--settings", file.toString()});

            Assertions.assertEquals(fromFile.getInstallLayout().getRoot().resolve("games").resolve("SC"), fromFile.getGameDirectory());
            Assertions.assertEquals("paths.data in sc4j-settings.json", fromFile.getDataDirectory().getSource());

            ClientOptions overridden = ClientOptions.parse(new String[]{"--settings", file.toString(), "--data", "other"});

            Assertions.assertEquals(overridden.getInstallLayout().getRoot().resolve("other"), overridden.getGameDirectory());
            Assertions.assertEquals("--data", overridden.getDataDirectory().getSource());

            // Without --data or paths.data: -Dsc4j.data (set by the build for tests) or <install root>/data.
            ClientOptions none = ClientOptions.parse(new String[]{"--settings", "none"});
            String property = System.getProperty(InstallLayout.DATA_PROPERTY, "");

            Path expectedDirectory = property.isBlank() ? none.getInstallLayout().getRoot().resolve("data") : Path.of(property);

            if (!expectedDirectory.isAbsolute()) {
                expectedDirectory = none.getInstallLayout().getRoot().resolve(expectedDirectory).normalize();
            }

            Assertions.assertEquals(expectedDirectory, none.getGameDirectory());
        } finally {
            Files.delete(file);
        }

        for (String contents : List.of("{\"paths\": {\"data\": 3}}", "{\"paths\": {\"data\": \" \"}}")) {
            Path invalid = write(contents);

            try {
                Assertions.assertThrows(IllegalArgumentException.class, () -> ClientSettings.load(invalid), contents);
            } finally {
                Files.delete(invalid);
            }
        }
    }

    @Test
    void write_should_keep_every_other_key_with_stable_layout() throws IOException {
        Path file = write("""
                          {
                            "graphics": {"renderer": "hd2", "fpsLimit": 144},
                            "createGame": {"name": "한글 게임", "type": 2},
                            "future": {"anything": [1, 2, {"deep": true}]}
                          }
                          """);

        try {
            SettingsStore.write(file, "createGame", Map.of("type", 10));

            ClientSettings settings = ClientSettings.load(file);

            Assertions.assertEquals(10, settings.getCreateGame().get("type"));
            Assertions.assertEquals("한글 게임", settings.getCreateGame().get("name"));
            Assertions.assertEquals(HdRenderAssets.Quality.HD2, settings.getRendererQuality());
            Assertions.assertEquals(List.of("future.anything"), settings.getIgnored());

            String text = Files.readString(file, StandardCharsets.UTF_8);

            Assertions.assertTrue(text.contains("한글"), text);
            Assertions.assertTrue(text.contains("\"deep\": true"), text);

            // A second write with the same content leaves the same bytes: the layout is stable.
            SettingsStore.write(file, "createGame", Map.of("type", 10));

            Assertions.assertEquals(text, Files.readString(file, StandardCharsets.UTF_8));
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void write_should_reject_file_system_root() {
        Path root = Path.of("").toAbsolutePath().getRoot();

        Assertions.assertNotNull(root);

        IOException exception = Assertions.assertThrows(IOException.class, () -> SettingsStore.write(root, "createGame", Map.of("type", 3)));

        Assertions.assertEquals("The path does not name a file: " + root, exception.getMessage());
    }

    @Test
    void write_should_create_missing_file_and_refuse_non_object_document(@TempDir @NotNull Path directory) throws IOException {
        Path file = directory.resolve("sc4j-settings.json");

        SettingsStore.write(file, "createGame", Map.of("type", 3));

        Assertions.assertEquals(3, ClientSettings.load(file).getCreateGame().get("type"));

        Files.writeString(file, "[1, 2]", StandardCharsets.UTF_8);

        Assertions.assertThrows(IOException.class, () -> SettingsStore.write(file, "createGame", Map.of("type", 2)));
        Assertions.assertEquals("[1, 2]", Files.readString(file, StandardCharsets.UTF_8));

        try (Stream<Path> left = Files.list(directory)) {
            Assertions.assertEquals(1, left.count(), "no temporary file is left behind");
        }
    }

    @Test
    void writable_settings_file_follows_settings_option() throws IOException {
        Path file = write("{}");

        try {
            Assertions.assertEquals(file, ClientOptions.parse(new String[]{"--settings", file.toString()}).getSettingsFile());
            Assertions.assertNull(ClientOptions.parse(new String[]{"--settings", "none"}).getSettingsFile());
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void smooth_turning_defaults_on_and_is_read_and_validated_from_settings_file() throws IOException {
        Assertions.assertTrue(new GraphicsSettings().isSmoothTurning(), "SC:R's UseHDRotation defaults to on");

        Path file = Files.createTempFile("sc4j-settings", ".json");

        try {
            Files.writeString(file, "{\"graphics\": {\"smoothTurning\": false}}", StandardCharsets.UTF_8);

            GraphicsSettings graphics = new GraphicsSettings();

            ClientSettings.load(file).applyTo(graphics);

            Assertions.assertFalse(graphics.isSmoothTurning());

            Files.writeString(file, "{\"graphics\": {\"smoothTurning\": \"no\"}}", StandardCharsets.UTF_8);

            Assertions.assertThrows(IllegalArgumentException.class, () -> ClientSettings.load(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void settings_file_reads_and_writes_sound_values() throws IOException {
        Path file = Files.createTempFile("sc4j-settings", ".json");

        try {
            Files.writeString(file, "{\"audio\": {\"set\": \"classic\", \"musicVolume\": 40, \"unitSpeech\": false}}", StandardCharsets.UTF_8);
            SettingsStore.write(file, "audio", Map.of("sfxEnabled", false, "sfxVolume", 75, "gameSubtitles", false));

            SoundSettings sound = new SoundSettings();
            ClientSettings settings = ClientSettings.load(file);

            settings.applyTo(sound);

            Assertions.assertEquals(40, sound.getMusicVolume(), "values of the file stay when others are written");
            Assertions.assertTrue(sound.isMusicEnabled(), "absent values keep the native default");
            Assertions.assertEquals(75, sound.getSoundEffectsVolume());
            Assertions.assertFalse(sound.isSoundEffectsEnabled());
            Assertions.assertFalse(sound.isUnitSpeech());
            Assertions.assertTrue(sound.isUnitAcknowledgments());
            Assertions.assertFalse(sound.isGameSubtitles());
            Assertions.assertTrue(settings.getIgnored().isEmpty());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void load_should_reject_invalid_volume_and_sound_flag() throws IOException {
        Path file = Files.createTempFile("sc4j-settings", ".json");

        try {
            Files.writeString(file, "{\"audio\": {\"musicVolume\": 101}}", StandardCharsets.UTF_8);

            Assertions.assertThrows(IllegalArgumentException.class, () -> ClientSettings.load(file));

            Files.writeString(file, "{\"audio\": {\"sfxEnabled\": 1}}", StandardCharsets.UTF_8);

            Assertions.assertThrows(IllegalArgumentException.class, () -> ClientSettings.load(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void shadow_stacking_and_gamma_are_read_and_validated_from_settings_file() throws IOException {
        GraphicsSettings defaults = new GraphicsSettings();

        Assertions.assertTrue(defaults.isShadowStacking(), "SC:R's ShadowStacking defaults to on");
        Assertions.assertEquals(NEUTRAL_GAMMA, defaults.getGamma());

        Path file = Files.createTempFile("sc4j-settings", ".json");

        try {
            Files.writeString(file, "{\"graphics\": {\"shadowStacking\": false, \"gamma\": 125}}", StandardCharsets.UTF_8);

            GraphicsSettings graphics = new GraphicsSettings();

            ClientSettings.load(file).applyTo(graphics);

            Assertions.assertFalse(graphics.isShadowStacking());
            Assertions.assertEquals(125, graphics.getGamma());

            Files.writeString(file, "{\"graphics\": {\"gamma\": 150}}", StandardCharsets.UTF_8);

            Assertions.assertThrows(IllegalArgumentException.class, () -> ClientSettings.load(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void saved_hotkey_bindings_round_trip_through_settings_file() throws IOException {
        Path file = Files.createTempFile("sc4j-hotkeys", ".json");

        try {
            Files.writeString(file, "{\"input\": {\"mouseConfine\": false}, \"custom\": {\"kept\": 1}}");

            Map<Integer, Integer> keys = new LinkedHashMap<>();

            keys.put(MOVE, (int) ';');
            keys.put(STOP, (int) 's');
            keys.put(TRAIN_SCV, (int) 'ㅅ');

            Map<String, Object> values = new LinkedHashMap<>();

            values.put("hotkeyProfile", HotkeyProfile.DEVICE);
            values.put("hotkeys", HotkeyBindingsUtils.format(keys));

            SettingsStore.write(file, "input", values);

            String contents = Files.readString(file, StandardCharsets.UTF_8);

            Assertions.assertTrue(contents.contains("STR_MAKE_T_SCV=ㅅ\\nSTR_MOVE=;\\nSTR_STOP=s\\n"), contents);

            ClientSettings settings = ClientSettings.load(file);

            Assertions.assertEquals(keys, settings.getHotkeys());
            Assertions.assertEquals(DEVICE_PROFILE, settings.getHotkeyProfile());
            Assertions.assertEquals(Boolean.FALSE, settings.getMouseConfine());
            Assertions.assertEquals(List.of("custom.kept"), settings.getIgnored());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void load_should_reject_hotkeys_that_are_no_hotkey_string() throws IOException {
        Path file = Files.createTempFile("sc4j-hotkeys", ".json");

        try {
            Files.writeString(file, "{\"input\": {\"hotkeyProfile\": \"$nohotkeys\"}}");

            Assertions.assertEquals(NO_HOTKEYS_PROFILE, ClientSettings.load(file).getHotkeyProfile());

            Files.writeString(file, "{\"input\": {\"hotkeys\": {\"664\": \"q\"}}}");

            Assertions.assertThrows(IllegalArgumentException.class, () -> ClientSettings.load(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}

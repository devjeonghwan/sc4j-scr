package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.fogofwarlabs.sc4j.engine.client.community.RecordingCommunitySession;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LatencyEstimate;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkAccount;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.community.PlayerStatus;
import org.fogofwarlabs.sc4j.scr.client.configuration.InstallLayout;
import org.fogofwarlabs.sc4j.scr.client.glue.CharacterFile;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueNavigator;
import org.fogofwarlabs.sc4j.scr.client.glue.lobby.BeaconLinkGlueScenario;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that the main menu names the run's player at the top right in place of the login button, as the account's {@code Name#tag} or as
 * the guest name of a run without an account, SD at 640x480 in English and HD at 1920x1080 in Korean, and that Single Player opens the
 * campaigns at once, playing with the character file of the account.
 * <p>
 * The reference frames are stored as SHA-256 digests of the read-back pixels (the frames hold installation artwork, which the repository does
 * not keep); every drawn frame is also written to {@code build/glue-screenshots}. The digests are those of the reference machine's OpenGL
 * driver.
 */
@RequiresInstallation
final class MainMenuScreenTest {
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";
    private static final int SELF_KEY = 1;

    private static final int SMALL_WIDTH = 640;
    private static final int SMALL_HEIGHT = 480;
    private static final int LARGE_WIDTH = 1920;
    private static final int LARGE_HEIGHT = 1080;
    private static final @NotNull String ACCOUNT_FRAME = "main-menu-account-";
    private static final @NotNull String GUEST_FRAME = "main-menu-guest-";
    /**
     * The campaign screen after Single Player, written to {@code build/glue-screenshots} and not compared.
     */
    private static final @NotNull String CAMPAIGN_FRAME = "single-player-campaign-640x480-enUS";
    /**
     * The hotkeys of Single Player and of the Expansion choice in English.
     */
    private static final @NotNull String SINGLE_PLAYER_HOTKEY = "s";
    private static final @NotNull String EXPANSION_HOTKEY = "e";
    /**
     * The digests of the reference frames.
     */
    private static final @NotNull Map<String, String> REFERENCE_DIGESTS = Map.of(
            ACCOUNT_FRAME + "640x480-enUS", "e9367f0741aad0c1883cfa85ea04583b61143c24bf50b5e6037fb441c0c88b1a",
            ACCOUNT_FRAME + "1920x1080-koKR", "da927f1a626e224e4ca88808cf90f730de97b974d4d32b65d9ae4791b795583b",
            GUEST_FRAME + "640x480-enUS", "9f64e023371ec84fdf21bf05681d7d49a1248c66ae2aa5b22d91aa4f12c862b8",
            GUEST_FRAME + "1920x1080-koKR", "a2c471bf84e3f6b9671fac0dedd16d1ad98fb3598c09ae8510fabc8e3df73fb8"
    );

    private static @NotNull RecordingCommunitySession createCommunity() {
        return new RecordingCommunitySession(RecordingCommunitySession.createPresence(SELF_KEY, BeaconLinkGlueScenario.ACCOUNT_NAME,
                                                                                      BeaconLinkGlueScenario.MOD_ID,
                                                                                      BeaconLinkGlueScenario.MOD_VERSION, true,
                                                                                      PlayerStatus.AVAILABLE, LatencyEstimate.UNKNOWN));
    }

    private static void assertReferenceDigests(@NotNull Map<String, String> digests) {
        List<String> mismatches = new ArrayList<>();

        for (Map.Entry<String, String> drawn : digests.entrySet()) {
            if (!drawn.getValue().equals(REFERENCE_DIGESTS.get(drawn.getKey()))) {
                mismatches.add(drawn.getKey() + " " + drawn.getValue());
            }
        }

        Assertions.assertEquals(List.of(), mismatches, "Frames that differ from the reference (see build/glue-screenshots).");
    }

    @TempDir
    @Nullable Path directory;

    private @NotNull String drawMainMenu(@NotNull String name, @NotNull String locale, int width, int height, @NotNull String qualityPrefix,
                                         @Nullable String accountName) throws IOException {
        Path root = this.directory;

        Assertions.assertNotNull(root);

        try (BeaconLinkGlueScenario scenario = BeaconLinkGlueScenario.open(root.resolve(name), locale, width, height, qualityPrefix, createCommunity(),
                                                                           List.of(), accountName)) {
            scenario.show(GlueNavigator.Destination.MAIN_MENU);

            return scenario.capture(name);
        }
    }

    @Test
    void top_right_names_the_account_with_its_tag_or_the_guest() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        Map<String, String> digests = new LinkedHashMap<>();
        String small = SMALL_WIDTH + "x" + SMALL_HEIGHT + "-enUS";
        String large = LARGE_WIDTH + "x" + LARGE_HEIGHT + "-koKR";

        digests.put(ACCOUNT_FRAME + small, this.drawMainMenu(ACCOUNT_FRAME + small, "enUS", SMALL_WIDTH, SMALL_HEIGHT, GlueCanvas.SD_PREFIX,
                                                             BeaconLinkGlueScenario.ACCOUNT_NAME));
        digests.put(ACCOUNT_FRAME + large, this.drawMainMenu(ACCOUNT_FRAME + large, "koKR", LARGE_WIDTH, LARGE_HEIGHT, GlueCanvas.HD_PREFIX,
                                                             BeaconLinkGlueScenario.ACCOUNT_NAME));
        digests.put(GUEST_FRAME + small, this.drawMainMenu(GUEST_FRAME + small, "enUS", SMALL_WIDTH, SMALL_HEIGHT, GlueCanvas.SD_PREFIX, null));
        digests.put(GUEST_FRAME + large, this.drawMainMenu(GUEST_FRAME + large, "koKR", LARGE_WIDTH, LARGE_HEIGHT, GlueCanvas.HD_PREFIX, null));

        assertReferenceDigests(digests);
    }

    @Test
    void single_player_opens_the_campaigns_with_the_account_character_without_asking_for_an_id() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        Path root = this.directory;

        Assertions.assertNotNull(root);

        Path install = root.resolve(CAMPAIGN_FRAME);

        try (BeaconLinkGlueScenario scenario = BeaconLinkGlueScenario.open(install, "enUS", SMALL_WIDTH, SMALL_HEIGHT, GlueCanvas.SD_PREFIX,
                                                                           createCommunity())) {
            LinkAccount account = scenario.getBeaconLink().getAccount();

            Assertions.assertNotNull(account);

            scenario.show(GlueNavigator.Destination.MAIN_MENU);
            scenario.type(SINGLE_PLAYER_HOTKEY);

            Assertions.assertTrue(scenario.isPopupOpen(), "Single Player asks for Original or Expansion");

            scenario.type(EXPANSION_HOTKEY);

            Assertions.assertFalse(scenario.isPopupOpen(), "no ID is asked for");

            Path character = InstallLayout.of(install).getCharacterDirectory().resolve(account.getId() + CharacterFile.EXTENSION);
            CharacterFile read = CharacterFile.read(character);

            Assertions.assertTrue(Files.isRegularFile(character), "the campaigns play with the account's character file");
            Assertions.assertNotNull(read);
            Assertions.assertEquals(BeaconLinkGlueScenario.ACCOUNT_NAME, read.getName());

            // The campaign screen's portraits are videos, so its frame is written for inspection and not compared.
            scenario.capture(CAMPAIGN_FRAME);
        }
    }
}

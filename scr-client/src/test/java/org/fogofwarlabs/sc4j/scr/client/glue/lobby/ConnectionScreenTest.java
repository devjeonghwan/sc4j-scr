package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.engine.client.community.RecordingCommunitySession;
import org.fogofwarlabs.sc4j.engine.client.input.Key;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LatencyEstimate;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.community.PlayerStatus;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueNavigator;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that the connection choice enters Beacon.Link with the run's account: Beacon.Link and Ok open the channel chat at once with a
 * session named after the account, while the top right names the account; a run without an account keeps Ok disabled on Beacon.Link, says
 * that it needs an account and opens no session. SD at 640x480 in English and HD at 1920x1080 in Korean.
 * <p>
 * The reference frames are stored as SHA-256 digests of the read-back pixels (the frames hold installation artwork, which the repository does
 * not keep); every drawn frame is also written to {@code build/glue-screenshots}. The digests are those of the reference machine's OpenGL
 * driver.
 */
@RequiresInstallation
final class ConnectionScreenTest {
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";
    private static final int SELF_KEY = 1;

    private static final int SMALL_WIDTH = 640;
    private static final int SMALL_HEIGHT = 480;
    private static final int LARGE_WIDTH = 1920;
    private static final int LARGE_HEIGHT = 1080;
    private static final @NotNull String ACCOUNT_FRAME = "beaconlink-entry-";
    private static final @NotNull String GUEST_FRAME = "beaconlink-guest-";
    /**
     * The provider list's first row, Beacon.Link, in dialog pixels.
     */
    private static final float BEACON_LINK_ROW_X = 150;
    private static final float BEACON_LINK_ROW_Y = 100;
    /**
     * The digests of the reference frames.
     */
    private static final @NotNull Map<String, String> REFERENCE_DIGESTS = Map.of(
            ACCOUNT_FRAME + "640x480-enUS", "cc42ce89cc32466b15cd8462bc2f7ee11af8ea5558efbe236dfc31a2374d11eb",
            ACCOUNT_FRAME + "1920x1080-koKR", "5083dcbb9f91d698843cbbdc4b0fabfd7b42fac04cead247be36f317608b1ba5",
            GUEST_FRAME + "640x480-enUS", "18d2db48ea3002f55c750b4737ea109b56687bc0ebd6edd96d8917fed769bf16",
            GUEST_FRAME + "1920x1080-koKR", "49fec2708144fe2c0353913c17430677b22416bf94ed74862b998bcb72fad1c2"
    );

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

    /**
     * Opens the connection choice, selects Beacon.Link, captures the screen and presses Ok.
     *
     * @return the frame's digest
     */
    private @NotNull String enterBeaconLink(@NotNull String name, @NotNull String locale, int width, int height, @NotNull String qualityPrefix,
                                            @Nullable String accountName) throws IOException {
        Path root = this.directory;

        Assertions.assertNotNull(root);

        RecordingCommunitySession community = new RecordingCommunitySession(
                RecordingCommunitySession.createPresence(SELF_KEY, BeaconLinkGlueScenario.ACCOUNT_NAME, BeaconLinkGlueScenario.MOD_ID,
                                                         BeaconLinkGlueScenario.MOD_VERSION, true, PlayerStatus.AVAILABLE, LatencyEstimate.UNKNOWN)
        );

        try (BeaconLinkGlueScenario scenario = BeaconLinkGlueScenario.open(root.resolve(name), locale, width, height, qualityPrefix, community, List.of(),
                                                                           accountName)) {
            scenario.show(GlueNavigator.Destination.CONNECTION);
            scenario.click(BEACON_LINK_ROW_X, BEACON_LINK_ROW_Y);

            String digest = scenario.capture(name);

            scenario.press(Key.ENTER);

            List<String> expected = accountName == null ? List.of() : List.of(accountName);

            Assertions.assertEquals(expected, scenario.getBeaconLink().getDisplayNames(), name);
            Assertions.assertFalse(scenario.isPopupOpen(), name);

            return digest;
        }
    }

    @Test
    void ok_enters_beacon_link_with_the_account_name_and_a_guest_gets_no_session() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        Map<String, String> digests = new LinkedHashMap<>();
        String small = SMALL_WIDTH + "x" + SMALL_HEIGHT + "-enUS";
        String large = LARGE_WIDTH + "x" + LARGE_HEIGHT + "-koKR";

        digests.put(ACCOUNT_FRAME + small, this.enterBeaconLink(ACCOUNT_FRAME + small, "enUS", SMALL_WIDTH, SMALL_HEIGHT, GlueCanvas.SD_PREFIX,
                                                                BeaconLinkGlueScenario.ACCOUNT_NAME));
        digests.put(ACCOUNT_FRAME + large, this.enterBeaconLink(ACCOUNT_FRAME + large, "koKR", LARGE_WIDTH, LARGE_HEIGHT, GlueCanvas.HD_PREFIX,
                                                                BeaconLinkGlueScenario.ACCOUNT_NAME));
        digests.put(GUEST_FRAME + small, this.enterBeaconLink(GUEST_FRAME + small, "enUS", SMALL_WIDTH, SMALL_HEIGHT, GlueCanvas.SD_PREFIX, null));
        digests.put(GUEST_FRAME + large, this.enterBeaconLink(GUEST_FRAME + large, "koKR", LARGE_WIDTH, LARGE_HEIGHT, GlueCanvas.HD_PREFIX, null));

        assertReferenceDigests(digests);
    }
}

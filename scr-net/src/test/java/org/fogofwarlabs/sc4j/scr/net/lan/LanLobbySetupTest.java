package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Compares installed-map lobby setup with recorded settings and exercises game-type slot and force tables.
 */
@RequiresInstallation
final class LanLobbySetupTest {
    private static final @NotNull String HOST_NAME = "B";
    private static final int HOST_SLOT = 0;
    private static final int OPEN_SLOT = 1;
    private static final int UNUSED_SLOT = 2;
    private static final int MELEE_FORCE = 0;
    private static final int FIRST_FORCE = 1;

    private static final int HUMAN_TYPE = 2;
    private static final int OPEN_TYPE = 6;
    private static final int NONE_TYPE = 0;
    private static final int SELECTABLE_RACE = 7;

    private static @NotNull ChkFile loadBottleneckScenario() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, LanTestResources.CAPTURED_MAP_FILE_NAME);

        return ChkFile.parseTolerant(MpqArchive.open(mapPath).readFile("staredit\\scenario.chk"));
    }

    private static @NotNull LanLobby.Setup createLobbySetup(@NotNull LanGameOptions options) throws IOException {
        return LanLobby.Setup.create(loadBottleneckScenario(), options,
                                     HOST_NAME,
                                     LanTestResources.CAPTURED_MAP_FILE_NAME,
                                     LanTestResources.CAPTURED_MAP_FILE_SIZE, LanTestResources.CAPTURED_MAP_CHECKSUM);
    }


    @Test
    void top_vs_bottom_on_bottleneck_matches_the_captured_lobby() throws IOException {
        LanLobby.Setup setup = createLobbySetup(LanGameOptions.TOP_VS_BOTTOM_1);
        LanLobby.Setup captured = LanTestResources.createCapturedLobbySetup(HOST_NAME);

        Assertions.assertEquals(HexFormat.of().formatHex(LanLobby.createGameSettings(captured)),
                                HexFormat.of().formatHex(LanLobby.createGameSettings(setup)));

        for (int index = 0; index < LanLobby.RECORDS; index++) {
            MeleeGameSetup.PlayerRecord expected = captured.getRecord(index);
            MeleeGameSetup.PlayerRecord actual = setup.getRecord(index);
            String where = "record " + index;

            Assertions.assertEquals(expected.getPlayerId(), actual.getPlayerId(), where);
            Assertions.assertEquals(expected.getType(), actual.getType(), where);
            Assertions.assertEquals(expected.getRace(), actual.getRace(), where);
            Assertions.assertEquals(expected.getTeam(), actual.getTeam(), where);
            Assertions.assertEquals(expected.getName(), actual.getName(), where);
        }
    }

    @Test
    void melee_opens_every_start_location_without_forces() throws IOException {
        LanLobby.Setup setup = createLobbySetup(LanGameOptions.MELEE);

        Assertions.assertEquals(0, setup.getRecord(HOST_SLOT).getPlayerId());
        Assertions.assertEquals(HUMAN_TYPE, setup.getRecord(HOST_SLOT).getType());
        Assertions.assertEquals(MELEE_FORCE, setup.getRecord(HOST_SLOT).getTeam());
        Assertions.assertEquals(OPEN_TYPE, setup.getRecord(OPEN_SLOT).getType());
        Assertions.assertEquals(SELECTABLE_RACE, setup.getRecord(OPEN_SLOT).getRace());
        Assertions.assertEquals(MELEE_FORCE, setup.getRecord(OPEN_SLOT).getTeam());
        Assertions.assertEquals(NONE_TYPE, setup.getRecord(UNUSED_SLOT).getType());
    }

    @Test
    void use_map_settings_keeps_the_map_forces_and_sends_the_force_names() throws IOException {
        LanLobby.Setup setup = createLobbySetup(LanGameOptions.USE_MAP_SETTINGS);

        Assertions.assertEquals(FIRST_FORCE, setup.getRecord(HOST_SLOT).getTeam());
        Assertions.assertEquals(FIRST_FORCE, setup.getRecord(OPEN_SLOT).getTeam());
        Assertions.assertEquals(OPEN_TYPE, setup.getRecord(OPEN_SLOT).getType());
        Assertions.assertNotNull(setup.getForceNames());
    }
}

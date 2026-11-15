package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayLoaderUtils;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Verifies a two-team Capture the Flag LAN game ({@code Templates\Team Capture The Flag(1).got}) on
 * {@code (8)The Hunters.scm}: the team branch of the game start gives each team with a player one flag beacon, owned by
 * the team's leader, and {@code CTF.trg} is registered for every player.
 */
@RequiresInstallation
final class LanTeamCaptureTheFlagTest {
    /**
     * {@code Templates\Team Capture The Flag(1).got}: type {@code 13}, subtype {@code 1}, two teams.
     */
    private static final @NotNull LanGameOptions TEAM_CAPTURE_THE_FLAG_2 = LanGameOptions.parse(
            HexFormat.of().parseHex("0d00010002000000040101020200000002010000000000320000000000000000")
    );
    private static final int SEED = 0x2468ACE0;

    private static final @NotNull String MAP_NAME = "(8)The Hunters.scm";
    private static final int MAP_SIZE = 71818;
    private static final int MAP_CHECKSUM = 0xC2ED13D0;

    private static final int HOST_SLOT = 0;
    /**
     * The joiner's slot: the first slot of the second team.
     */
    private static final int JOINER_SLOT = 4;
    private static final int HOST_PLAYER = 0;
    private static final int JOINER_PLAYER = 1;
    private static final int FIRST_TEAM = 1;
    private static final int SECOND_TEAM = 2;

    private static final int FRAMES = 20;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100000;

    private static final int TEAMS = 2;
    /**
     * The trigger records {@code CTF.trg} registers for a player.
     */
    private static final int CAPTURE_THE_FLAG_TRIGGER_RECORDS = 4;
    private static final int TEAM_CAPTURE_THE_FLAG_TYPE = 13;

    @Test
    void each_team_leader_gets_one_flag_beacon() throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        ChkFile map = ChkFile.parseTolerant(mapData);
        LanLobby.Setup setup = LanLobby.Setup.create(map, TEAM_CAPTURE_THE_FLAG_2, "host", MAP_NAME, MAP_SIZE, MAP_CHECKSUM);
        MeleeGameSetup.PlayerRecord[] lobby = new MeleeGameSetup.PlayerRecord[LanLobby.RECORDS];
        byte[] lobbyData = new byte[LanLobby.LOBBY_DATA_SIZE];

        for (int index = 0; index < LanLobby.RECORDS; index++) {
            lobby[index] = setup.getRecord(index);
        }

        lobby[HOST_SLOT]   = new MeleeGameSetup.PlayerRecord(HOST_PLAYER, PlayerTypes.HUMAN, ScrRaceIds.TERRAN, FIRST_TEAM, "host");
        lobby[JOINER_SLOT] = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.HUMAN, ScrRaceIds.PROTOSS, SECOND_TEAM, "joiner");

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            lobbyData[LanLobby.LOBBY_DATA_COLORS + slot] = (byte) LobbyResolution.COLOR_NONE;
        }

        LobbyResolution.Table table = LanLobby.createResolutionTable(LanLobby.createGameSettings(setup), lobby, lobbyData);
        MeleeGameSetup gameSetup = MeleeGameSetup.createNetwork(mapData, "host",
                                                                table, LanLobby.createResolutionOptions(TEAM_CAPTURE_THE_FLAG_2),
                                                                TEAM_CAPTURE_THE_FLAG_2.createTemplate(),
                                                                GameSpeed.FASTEST, true);
        LiveGame live = new LiveGame(gameSetup, SEED, HOST_PLAYER);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), map,
                                                                 gameData,
                                                                 live.getLocalPlayer(), live.getLocalSlot(), true,
                                                                 live.getTeamSlotRaces(),
                                                                 VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                                 INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            int[] leaders = ReplayLoaderUtils.getTeamLeaders(live.getReplay().getHeader());

            Assertions.assertNotNull(leaders, "Team Capture the Flag is a team game.");

            UnitPool pool = game.getUnitSystem().getUnitPool();
            int beacons = 0;

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() < ScrUnitIds.SPECIAL_ZERG_FLAG_BEACON || unit.getUnitId() > ScrUnitIds.SPECIAL_PROTOSS_FLAG_BEACON) {
                    continue;
                }

                beacons++;

                Assertions.assertTrue(unit.getOwnerIndex() == leaders[0] || unit.getOwnerIndex() == leaders[1], "A beacon belongs to a team leader.");
            }

            Assertions.assertEquals(TEAMS, beacons, "One beacon per team with a player.");

            TriggerContext triggers = game.getTriggerContext();

            Assertions.assertNotNull(triggers);
            Assertions.assertEquals(CAPTURE_THE_FLAG_TRIGGER_RECORDS, triggers.getPlayerRecordCount(live.getLocalPlayer()));

            int tick = 0;

            while (game.getCurrentFrame() < FRAMES) {
                Assertions.assertTrue(runtime.step(FRAME_MILLISECONDS, tick += FRAME_MILLISECONDS));
                Assertions.assertNull(runtime.getFailure());
            }
        }

        ReplayFile written = ReplayFile.parse(live.writeReplay(0, 0));

        Assertions.assertEquals(TEAM_CAPTURE_THE_FLAG_TYPE, written.getHeader().getType(), "The header records Team Capture the Flag.");
    }
}

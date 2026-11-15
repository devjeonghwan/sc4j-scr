package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Names the test resources of the captured LAN session: the native SC:R replays of its games, its map with a stand-in file of
 * the map's size and checksum, and its lobby before the join.
 */
final class LanTestResources {
    /**
     * The replay of the first captured Top vs Bottom game on Bottleneck ({@code 2518} frames).
     */
    static final @NotNull String REPLAY_TOP_VS_BOTTOM_FIRST = "replays/lan/INTERNAL_00000001.rep";
    /**
     * The replay of the second captured Top vs Bottom game on Bottleneck, with a random race ({@code 118} frames).
     */
    static final @NotNull String REPLAY_TOP_VS_BOTTOM_RANDOM_RACE = "replays/lan/INTERNAL_00000002.rep";
    /**
     * The recorded game's map: {@code (2)Bottleneck.scm} of the installed game, its file size and its checksum.
     */
    static final @NotNull String CAPTURED_MAP_FILE_NAME = "(2)Bottleneck.scm";
    static final int CAPTURED_MAP_FILE_SIZE = 68376;
    static final int CAPTURED_MAP_CHECKSUM = 1484706167;

    private static final int CHECKSUM_BITS = Integer.SIZE;
    private static final int SOLUTION_BITS = Integer.SIZE;

    /**
     * The recorded map's tileset and size in tiles.
     */
    private static final int CAPTURED_TILESET = 1;
    private static final int CAPTURED_MAP_DIMENSION = 128;
    /**
     * The recorded lobby's slots of the host and the joiner and their Top vs Bottom forces.
     */
    private static final int HOST_SLOT = 0;
    private static final int JOINER_SLOT = 1;
    private static final int TOP_FORCE = 1;
    private static final int BOTTOM_FORCE = 2;
    private static final int NO_PLAYER = -1;
    /**
     * The bytes of the host's block that are {@code 1}.
     */
    private static final int @NotNull [] HOST_BLOCK_SET_BYTES = {94, 212, 260, 262, 264, 266, 308};

    /**
     * Writes a file with the size and checksum of the recorded map into a folder.
     *
     * @param folder the folder
     * @return the file
     * @throws IOException if the file cannot be written
     */
    static @NotNull Path writeCapturedMap(@NotNull Path folder) throws IOException {
        Path file = folder.resolve(CAPTURED_MAP_FILE_NAME);

        Files.createDirectories(folder);
        Files.write(file, createCapturedMapContent());

        return file;
    }

    /**
     * Creates the captured map content with {@link #CAPTURED_MAP_FILE_SIZE} bytes and {@link #CAPTURED_MAP_CHECKSUM}: zeros
     * whose final four bytes are solved for over {@code GF(2)}.
     *
     * @return zeros whose last four bytes give the checksum
     * @throws IllegalStateException if the solved bytes do not give the checksum
     */
    private static byte @NotNull [] createCapturedMapContent() {
        byte[] data = new byte[CAPTURED_MAP_FILE_SIZE];
        int tail = CAPTURED_MAP_FILE_SIZE - Integer.BYTES;
        int base = LanMapTransfer.calculateChecksum(data, CAPTURED_MAP_FILE_SIZE);
        int[] columns = new int[SOLUTION_BITS];

        for (int bit = 0; bit < SOLUTION_BITS; bit++) {
            data[tail + bit / Byte.SIZE] = (byte) (1 << bit % Byte.SIZE);
            columns[bit]                 = LanMapTransfer.calculateChecksum(data, CAPTURED_MAP_FILE_SIZE) ^ base;
            data[tail + bit / Byte.SIZE] = 0;
        }

        // Solve columns * x = checksum ^ base over GF(2): rows are the result bits, augmented with the target bit.
        long[] rows = new long[CHECKSUM_BITS];
        int target = CAPTURED_MAP_CHECKSUM ^ base;

        for (int row = 0; row < CHECKSUM_BITS; row++) {
            long value = 0;

            for (int bit = 0; bit < SOLUTION_BITS; bit++) {
                value |= (long) (columns[bit] >>> row & 1) << bit;
            }

            rows[row] = value | (long) (target >>> row & 1) << SOLUTION_BITS;
        }

        int rank = 0;
        int[] pivots = new int[SOLUTION_BITS];

        for (int bit = 0; bit < SOLUTION_BITS && rank < CHECKSUM_BITS; bit++) {
            int pivot = -1;

            for (int row = rank; row < CHECKSUM_BITS; row++) {
                if ((rows[row] >>> bit & 1) != 0) {
                    pivot = row;
                }
            }

            if (pivot < 0) {
                continue;
            }

            long swap = rows[pivot];

            rows[pivot] = rows[rank];
            rows[rank]  = swap;

            for (int row = 0; row < CHECKSUM_BITS; row++) {
                if (row != rank && (rows[row] >>> bit & 1) != 0) {
                    rows[row] ^= rows[rank];
                }
            }

            pivots[rank++] = bit;
        }

        int solution = 0;

        for (int row = 0; row < rank; row++) {
            solution |= (int) (rows[row] >>> SOLUTION_BITS & 1) << pivots[row];
        }

        for (int index = 0; index < Integer.BYTES; index++) {
            data[tail + index] = (byte) (solution >>> Byte.SIZE * index);
        }

        if (LanMapTransfer.calculateChecksum(data, CAPTURED_MAP_FILE_SIZE) != CAPTURED_MAP_CHECKSUM) {
            throw Exceptions.newIllegalStateException("no stand-in of the checksum");
        }

        return data;
    }

    /**
     * Creates a {@link LanLobby.Setup} instance for the recorded Top vs Bottom lobby before the join.
     *
     * @param hostName the host's name
     * @return the created {@link LanLobby.Setup} instance
     */
    static @NotNull LanLobby.Setup createCapturedLobbySetup(@NotNull String hostName) {
        byte[] lobbyData = new byte[LanLobby.LOBBY_DATA_SIZE];

        for (int index = LanLobby.LOBBY_DATA_COLORS; index < LanLobby.LOBBY_DATA_COLORS + PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            lobbyData[index] = (byte) LanLobby.COLOR_DEFAULT;
        }

        return createCapturedLobbySetup(hostName, lobbyData);
    }

    /**
     * Creates a {@link LanLobby.Setup} instance with the supplied lobby data ({@code 0x69}).
     *
     * @param hostName  the host's name
     * @param lobbyData the lobby data
     * @return the created {@link LanLobby.Setup} instance
     */
    static @NotNull LanLobby.Setup createCapturedLobbySetup(@NotNull String hostName, byte @NotNull [] lobbyData) {
        int[] races = {7, 7, 1, 1, 0, 2, 1, 0};
        MeleeGameSetup.PlayerRecord[] records = new MeleeGameSetup.PlayerRecord[LanLobby.RECORDS];

        records[HOST_SLOT]   = new MeleeGameSetup.PlayerRecord(0, PlayerTypes.HUMAN, LobbyResolution.SELECTABLE_RACE, TOP_FORCE, hostName);
        records[JOINER_SLOT] = new MeleeGameSetup.PlayerRecord(NO_PLAYER, LanLobby.TYPE_OPEN, LobbyResolution.SELECTABLE_RACE, BOTTOM_FORCE, "");

        for (int slot = JOINER_SLOT + 1; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            records[slot] = new MeleeGameSetup.PlayerRecord(NO_PLAYER, PlayerTypes.NONE, races[slot], 0, "");
        }

        for (int slot = PlayerTable.PLAYABLE_PLAYER_SLOTS; slot < LanLobby.RECORDS; slot++) {
            records[slot] = new MeleeGameSetup.PlayerRecord(NO_PLAYER, PlayerTypes.CLOSED, 0, 0, "");
        }

        return new LanLobby.Setup(CAPTURED_TILESET,
                                  CAPTURED_MAP_DIMENSION, CAPTURED_MAP_DIMENSION,
                                  HexFormat.of().parseHex("060600000000000000000000"),
                                  HexFormat.of().parseHex("070701010002010000000000"),
                                  HexFormat.of().parseHex("060600000000000000000000"),
                                  HexFormat.of().parseHex("010100000000000001000000"), HexFormat.of().parseHex("0101000000000000"),
                                  lobbyData,
                                  records,
                                  CAPTURED_MAP_FILE_NAME, CAPTURED_MAP_FILE_SIZE, CAPTURED_MAP_CHECKSUM);
    }

    /**
     * Creates the recorded host block with the selected bytes set to {@code 1}.
     *
     * @return the created block
     */
    static byte @NotNull [] createCapturedHostBlock() {
        byte[] block = new byte[LanLobby.PLAYER_BLOCK_SIZE];

        for (int index : HOST_BLOCK_SET_BYTES) {
            block[index] = 1;
        }

        return block;
    }


    /**
     * Creates a {@link LanTestResources} instance.
     */
    private LanTestResources() {
    }
}

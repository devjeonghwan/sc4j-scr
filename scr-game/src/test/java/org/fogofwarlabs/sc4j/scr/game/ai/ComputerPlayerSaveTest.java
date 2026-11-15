package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.save.SaveReferences;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Checks the saved spending-request fields on caller-provided player buffers.
 */
@RequiresInstallation
final class ComputerPlayerSaveTest {
    private static final int MINERAL_BUDGET_OFFSET = 0x0C;
    private static final int SUPPLY_BUDGET_OFFSET = 0x14;
    private static final int REQUESTS_OFFSET = 0x18;
    private static final int REQUEST_SIZE = 8;
    private static final int REQUEST_KIND_OFFSET = 1;
    private static final int REQUEST_COUNT_OFFSET = 0x210;
    private static final int NUKE_RATE_OFFSET = 0x212;
    private static final int REQUEST_CAPACITY = 63;
    private static final int TOWN_REQUEST_KIND = 3;

    /**
     * The values of player {@code 0}; every other player adds its index.
     */
    private static final int MINERAL_BUDGET = 100;
    private static final int GAS_BUDGET = 200;
    private static final int SUPPLY_BUDGET = 300;
    private static final int NUKE_RATE = 40;
    private static final int REQUEST_PRIORITY = 80;
    private static final int REQUEST_UNIT_TYPE = 41;

    private static final int SCHEDULER_COUNTER_COUNT = 8;
    private static final int STATE_HASH_SEED = 123;
    /**
     * The size of each guard range around the saved players: one before and one after them.
     */
    private static final int GUARD_SIZE = 16;
    private static final int GUARD_RANGE_COUNT = 2;
    private static final byte GUARD_FILL = (byte) 0xA5;
    private static final int BUFFER_POSITION = 7;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @ParameterizedTest
    @ValueSource(ints = {0, 1, 63})
    void write_save_players_should_zero_request_targets_and_leave_state_unchanged(int requestCount) throws IOException {
        Assertions.assertNotNull(gameData);

        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.getSpriteSystem().initializeSpritePool(1);
        game.getSpriteSystem().initializeSpriteEffectPool(1);
        game.initializeOrderPool(1);
        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

        ComputerPlayerPhase phase = new ComputerPlayerPhase(game, 1, new int[SCHEDULER_COUNTER_COUNT], new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        phase.initializeScripts(false);

        for (int owner = 0; owner < PlayerTable.PLAYABLE_PLAYER_SLOTS; owner++) {
            ComputerPlayer player = phase.getPlayer(owner);

            player.beginUpdate(MINERAL_BUDGET + owner, GAS_BUDGET + owner);
            player.setSupplyBudget(SUPPLY_BUDGET + owner);
            player.setNukeRate(NUKE_RATE + owner);

            for (int requestIndex = 0; requestIndex < requestCount; requestIndex++) {
                player.pushRequest(REQUEST_PRIORITY + owner, ComputerPlayer.REQUEST_KIND_TOWN, REQUEST_UNIT_TYPE + owner, null, -1);
            }
        }

        byte[] storage = new byte[ComputerPlayerPhase.SAVED_PLAYERS_SIZE + GUARD_SIZE * GUARD_RANGE_COUNT];

        Arrays.fill(storage, GUARD_FILL);

        ByteBuffer players = ByteBuffer.wrap(storage, GUARD_SIZE, ComputerPlayerPhase.SAVED_PLAYERS_SIZE).slice().order(ByteOrder.LITTLE_ENDIAN);

        players.position(BUFFER_POSITION);

        int originalPosition = players.position();
        int originalLimit = players.limit();
        int originalStateHash = phase.computeStateHash(STATE_HASH_SEED);
        long originalRandomCount = game.getRandom().getTotalRandomCounts();

        phase.writeSavePlayers(players, new SaveReferences(game));

        for (int owner = 0; owner < PlayerTable.PLAYABLE_PLAYER_SLOTS; owner++) {
            int record = owner * ComputerPlayer.SAVED_SIZE;

            Assertions.assertEquals(MINERAL_BUDGET + owner, players.getInt(record + MINERAL_BUDGET_OFFSET), "Mineral budget of player " + owner);
            Assertions.assertEquals(SUPPLY_BUDGET + owner, players.getInt(record + SUPPLY_BUDGET_OFFSET), "Supply budget of player " + owner);
            Assertions.assertEquals(requestCount, Byte.toUnsignedInt(players.get(record + REQUEST_COUNT_OFFSET)));
            Assertions.assertEquals(NUKE_RATE + owner, Byte.toUnsignedInt(players.get(record + NUKE_RATE_OFFSET)));
            Assertions.assertEquals(requestCount, phase.getPlayer(owner).getRequestCount());

            for (int requestIndex = 0; requestIndex < REQUEST_CAPACITY; requestIndex++) {
                int request = record + REQUESTS_OFFSET + requestIndex * REQUEST_SIZE;
                boolean active = requestIndex < requestCount;

                Assertions.assertEquals(active ? REQUEST_PRIORITY + owner : 0, Byte.toUnsignedInt(players.get(request)), "Priority");
                Assertions.assertEquals(active ? TOWN_REQUEST_KIND : 0, Byte.toUnsignedInt(players.get(request + REQUEST_KIND_OFFSET)), "Kind");
                Assertions.assertEquals(active ? REQUEST_UNIT_TYPE + owner : 0, Short.toUnsignedInt(players.getShort(request + Short.BYTES)), "Type");
                Assertions.assertEquals(0, players.getInt(request + Integer.BYTES), "Target of player " + owner + ", request " + requestIndex);
            }
        }

        for (int guardIndex = 0; guardIndex < GUARD_SIZE; guardIndex++) {
            Assertions.assertEquals(GUARD_FILL, storage[guardIndex], "Leading guard");
            Assertions.assertEquals(GUARD_FILL, storage[storage.length - GUARD_SIZE + guardIndex], "Trailing guard");
        }

        Assertions.assertEquals(originalPosition, players.position());
        Assertions.assertEquals(originalLimit, players.limit());
        Assertions.assertEquals(originalStateHash, phase.computeStateHash(STATE_HASH_SEED));
        Assertions.assertEquals(originalRandomCount, game.getRandom().getTotalRandomCounts());
    }
}

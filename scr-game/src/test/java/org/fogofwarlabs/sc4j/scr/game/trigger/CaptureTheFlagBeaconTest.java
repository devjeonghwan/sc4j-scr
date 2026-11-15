package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

/**
 * Checks flag creation and beacon relocation after the required elapsed time.
 */
@RequiresInstallation
final class CaptureTheFlagBeaconTest {
    private static final int HOST_TICK_INTERVAL = 42;
    private static final int INITIAL_FRAME_COUNT = 100;

    /**
     * The elapsed game seconds after which a flag beacon creates its Flag.
     */
    private static final int FLAG_CREATION_SECONDS = 600;
    /**
     * The game-context offset of the elapsed game-clock DWORD in the EUD game-data block.
     */
    private static final int ELAPSED_CLOCK_OFFSET = 0xE608;
    private static final int FLAG_CREATION_FRAMES = 20;
    private static final int PLACEMENT_FRAMES = 2;

    /**
     * The distance step of the beacon site search.
     */
    private static final int SITE_SEARCH_STEP = 32;
    /**
     * The farthest distance of the beacon site search.
     */
    private static final int SITE_SEARCH_DISTANCE = 320;
    /**
     * The empty button set of a placed beacon.
     */
    private static final int EMPTY_BUTTON_SET = 228;
    /**
     * The CTFCOPInit order of a placed beacon.
     */
    private static final int BEACON_INIT_ORDER = 153;
    /**
     * The CTFCOP2 order of a beacon that waits for its owner.
     */
    private static final int BEACON_WAIT_ORDER = 155;

    @Test
    void beacons_create_completed_flags_and_switch_orders_after_six_hundred_seconds() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_UMS_CAPTURE_THE_FLAG)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayRuntime runtime = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            int tick = 0;

            while (game.getCurrentFrame() < INITIAL_FRAME_COUNT) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
            }

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            int beacons = 0;
            int preplacedFlags = 0;

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getPrimaryOrder() == ScrOrderIds.CTFCOP_INIT) {
                    beacons++;
                }

                if (unit.getUnitId() == ScrUnitIds.POWERUP_FLAG) {
                    preplacedFlags++;
                }
            }

            Assertions.assertTrue(beacons > 0, "The map has flag beacons executing CTFCOPInit.");

            TriggerContext elapsedContext = game.getTriggerContext();

            Assertions.assertNotNull(elapsedContext);

            // An EUD write of the elapsed game clock, byte by byte, reaches the clock the beacons wait for.
            for (int index = 0; index < Integer.BYTES; index++) {
                elapsedContext.writeGameDataByte(ELAPSED_CLOCK_OFFSET + index, FLAG_CREATION_SECONDS >>> index * Byte.SIZE);
            }

            int until = game.getCurrentFrame() + FLAG_CREATION_FRAMES;

            while (game.getCurrentFrame() < until) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
                Assertions.assertNull(runtime.getFailure());
            }

            int flags = 0;
            int switched = 0;

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == ScrUnitIds.POWERUP_FLAG) {
                    flags++;

                    Assertions.assertEquals(1, unit.getStatus() & UnitStatusFlags.COMPLETED, "Creating the Flag completes it.");
                }

                if (unit.getPrimaryOrder() == ScrOrderIds.CTFCOP_STARTED) {
                    switched++;

                    Assertions.assertEquals(EMPTY_BUTTON_SET, unit.getCurrentButtonSet(), "The switched beacon gets button set 228.");
                }
            }

            Assertions.assertEquals(beacons, switched, "Every beacon switched to CTFCOP2.");
            Assertions.assertEquals(preplacedFlags + beacons, flags, "Every beacon created one Flag.");
        }
    }

    @Test
    void place_beacon_relocates_to_placeable_site_and_returns_to_ctfcop_init() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_UMS_CAPTURE_THE_FLAG)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayRuntime runtime = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Game game = runtime.getGame();
            int tick = 0;

            while (game.getCurrentFrame() < INITIAL_FRAME_COUNT) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
            }

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit beacon = null;

            for (int index = pool.getFirstCreatedIndex(true); index != 0 && beacon == null; index = pool.getNextCreatedIndex(index)) {
                if (pool.getUnit(index).getPrimaryOrder() == ScrOrderIds.CTFCOP_INIT) {
                    beacon = pool.getUnit(index);
                }
            }

            Assertions.assertNotNull(beacon, "The map has a flag beacon.");

            Unit placed = null;
            int targetX = 0;
            int targetY = 0;

            for (int offset = SITE_SEARCH_STEP; offset <= SITE_SEARCH_DISTANCE && placed == null; offset += SITE_SEARCH_STEP) {
                for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = beacon.getPositionX() + direction[0] * offset;
                    int y = beacon.getPositionY() + direction[1] * offset;

                    if (game.getUnitPlacement().canPlaceBuilding(beacon, beacon.getOwnerIndex(), beacon.getUnitId(), x, y, true, false)) {
                        placed  = beacon;
                        targetX = x;
                        targetY = y;

                        break;
                    }
                }
            }

            Assertions.assertNotNull(placed, "A placeable site exists near the beacon.");

            game.getOrders().issue(beacon, ScrOrderIds.CTFCOP2, null, targetX, targetY, TriggerUnitGroups.NONE, false,
                                   RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(BEACON_WAIT_ORDER, beacon.getPrimaryOrder());

            int until = game.getCurrentFrame() + PLACEMENT_FRAMES;

            while (game.getCurrentFrame() < until) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
            }

            Assertions.assertNull(runtime.getFailure());
            Assertions.assertEquals(targetX, beacon.getPositionX(), "Placing the beacon moves it to the order target.");
            Assertions.assertEquals(targetY, beacon.getPositionY());
            Assertions.assertEquals(EMPTY_BUTTON_SET, beacon.getCurrentButtonSet(), "The placed beacon gets button set 228.");
            Assertions.assertEquals(BEACON_INIT_ORDER, beacon.getPrimaryOrder(), "CTFCOPInit 153 follows.");

            int blockedX = beacon.getPositionX();
            int blockedY = beacon.getPositionY();

            game.getOrders().issue(beacon, ScrOrderIds.CTFCOP2, null, 0, 0, TriggerUnitGroups.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
            until = game.getCurrentFrame() + PLACEMENT_FRAMES;

            while (game.getCurrentFrame() < until) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
            }

            Assertions.assertNull(runtime.getFailure());
            Assertions.assertEquals(blockedX, beacon.getPositionX(), "An unplaceable site leaves the beacon in place.");
            Assertions.assertEquals(blockedY, beacon.getPositionY());
            Assertions.assertEquals(BEACON_INIT_ORDER, beacon.getPrimaryOrder());
        }
    }
}

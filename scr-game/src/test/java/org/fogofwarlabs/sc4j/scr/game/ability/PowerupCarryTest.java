package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

/**
 * Covers enemy-worker Flag pickup on the {@link ScrOrderIds#POWERUP_IDLE} tick and drop when the carrier dies.
 */
@RequiresInstallation
final class PowerupCarryTest {
    private static final int STEP_HOST_TICKS = 42;
    private static final int START_FRAME = 100;
    private static final int TRIGGER_CLOCK_TICKS = 600;
    /**
     * The game-context offset of the elapsed game-clock DWORD in the EUD game-data block.
     */
    private static final int ELAPSED_CLOCK_OFFSET = 0xE608;
    private static final int FLAG_CREATION_FRAMES = 20;
    private static final int PICKUP_FRAMES = 10;
    private static final int RETURN_FRAMES = 3;

    private static final int POWERUP_CARRYING_FLAG = 0x4;
    private static final int NOTHING_ORDER = 23;
    private static final int POWERUP_IDLE_ORDER = 97;
    private static final int RETURNED_FLAG_ORDER = 17;
    private static final int CARRIED_FLAG_IMAGE = 392;


    @Test
    void enemy_worker_picks_up_flag_and_drops_it_when_killed() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_UMS_CAPTURE_THE_FLAG)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            int tick = 0;

            while (game.getCurrentFrame() < START_FRAME) {
                Assertions.assertTrue(runtime.step(STEP_HOST_TICKS, tick += STEP_HOST_TICKS));
            }

            TriggerContext elapsedTriggers = game.getTriggerContext();

            Assertions.assertNotNull(elapsedTriggers);

            // An EUD write of the elapsed game clock, byte by byte, reaches the clock the beacons wait for.
            for (int index = 0; index < Integer.BYTES; index++) {
                elapsedTriggers.writeGameDataByte(ELAPSED_CLOCK_OFFSET + index, TRIGGER_CLOCK_TICKS >>> index * Byte.SIZE);
            }

            int until = game.getCurrentFrame() + FLAG_CREATION_FRAMES;

            while (game.getCurrentFrame() < until) {
                Assertions.assertTrue(runtime.step(STEP_HOST_TICKS, tick += STEP_HOST_TICKS));
            }

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit flag = null;
            Unit worker = null;

            for (int index = pool.getFirstCreatedIndex(true); index != 0 && worker == null; index = pool.getNextCreatedIndex(index)) {
                Unit candidate = pool.getUnit(index);
                Player owner = game.getPlayerTable().getPlayer(candidate.getOwnerIndex());

                if (candidate.getUnitId() != ScrUnitIds.POWERUP_FLAG ||
                    candidate.getPrimaryOrder() != ScrOrderIds.POWERUP_IDLE || owner == null) {
                    continue;
                }

                for (int otherIndex = pool.getFirstCreatedIndex(true); otherIndex != 0 && worker == null;
                     otherIndex = pool.getNextCreatedIndex(otherIndex)) {
                    Unit unit = pool.getUnit(otherIndex);
                    Player player = game.getPlayerTable().getPlayer(unit.getOwnerIndex());

                    if (unit.getOwnerIndex() != candidate.getOwnerIndex() && player != null && player.getType() == PlayerTypes.HUMAN &&
                        unit.getOwnerIndex() < PlayerTable.PLAYABLE_PLAYER_SLOTS &&
                        (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0 &&
                        (gameData.getUnits().get("Special Ability Flags", unit.getUnitId()) & UnitTypeFlags.WORKER) != 0) {
                        flag   = candidate;
                        worker = unit;
                    }
                }
            }

            Assertions.assertNotNull(flag, "A beacon created a Flag.");
            Assertions.assertNotNull(worker, "An enemy worker exists.");

            // The map's own triggers remove the Flags; they are not part of this test.
            TriggerContext playerTriggers = game.getTriggerContext();

            Assertions.assertNotNull(playerTriggers);

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                playerTriggers.clearPlayerRecords(player);
            }

            int originX = flag.getPositionX();
            int originY = flag.getPositionY();

            Assertions.assertEquals(originX, flag.getResources(), "The powerup stores its origin.");
            Assertions.assertTrue(game.getUnitRelocation().relocate(worker, originX, originY));

            until = game.getCurrentFrame() + PICKUP_FRAMES;

            StringBuilder trace = new StringBuilder();

            while (game.getCurrentFrame() < until && worker.getCarriedPowerup() == null) {
                trace.append(flag.getPrimaryOrder()).append(' ');

                Assertions.assertTrue(runtime.step(STEP_HOST_TICKS, tick += STEP_HOST_TICKS));
                Assertions.assertNull(runtime.getFailure());
            }

            Assertions.assertSame(flag, worker.getCarriedPowerup(), "The Flag's PowerupIdle tick picks it up: " + trace);
            Assertions.assertSame(worker, flag.getPowerupCarrier());
            Assertions.assertEquals(POWERUP_CARRYING_FLAG, worker.getCarryingFlags());

            Sprite hiddenFlagSprite = flag.getSpriteOrNull();

            Assertions.assertNotNull(hiddenFlagSprite);

            Assertions.assertNotEquals(0, hiddenFlagSprite.getFlags() & Sprite.FLAG_HIDDEN, "Picking up the Flag hides it.");
            Assertions.assertEquals(NOTHING_ORDER, flag.getPrimaryOrder());

            boolean hasFlagImage = false;
            Sprite carrierSprite = worker.getSpriteOrNull();

            Assertions.assertNotNull(carrierSprite);

            for (SpriteImage carried : carrierSprite.getImages()) {
                hasFlagImage |= carried.getImageTypeId() == CARRIED_FLAG_IMAGE;
            }

            Assertions.assertTrue(hasFlagImage, "The worker shows the carried Flag image.");

            game.getUnitLifecycle().kill(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(worker.getCarriedPowerup());
            Assertions.assertEquals(0, worker.getCarryingFlags());
            Assertions.assertNull(flag.getPowerupCarrier());

            int order = flag.getPrimaryOrder();

            Assertions.assertTrue(order == POWERUP_IDLE_ORDER || order == RETURNED_FLAG_ORDER,
                                  "The Flag returns to PowerupIdle, or order 17 at its origin.");

            until = game.getCurrentFrame() + RETURN_FRAMES;

            while (game.getCurrentFrame() < until) {
                Assertions.assertTrue(runtime.step(STEP_HOST_TICKS, tick += STEP_HOST_TICKS));
                Assertions.assertNull(runtime.getFailure());
            }

            Sprite flagSprite = flag.getSpriteOrNull();

            Assertions.assertNotNull(flagSprite);

            Assertions.assertEquals(0, flagSprite.getFlags() & Sprite.FLAG_HIDDEN, "The hidden PowerupIdle shows the Flag again.");
        }
    }
}

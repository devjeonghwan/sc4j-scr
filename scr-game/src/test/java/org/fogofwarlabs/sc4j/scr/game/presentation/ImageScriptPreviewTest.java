package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that the image script preview runs a placement's image script in an isolated world and leaves the source game unchanged.
 */
@RequiresInstallation
final class ImageScriptPreviewTest {
    private static final int MAP_WIDTH = 1;
    private static final int MAP_HEIGHT = 1;
    private static final int TILESET = 0;
    private static final int RANDOM_SEED = 12345;

    private static final int UNIT_SERIAL = 1;
    private static final int UNIT_POSITION = 16;
    private static final int FULL_HIT_POINTS_PERCENT = 100;
    private static final int MARINE = 0;
    private static final int BROODLING = 40;

    private static final int DEATH_ANIMATION = 1;
    private static final int GROUND_ATTACK_INIT_ANIMATION = 2;
    private static final int GROUND_ATTACK_REPEAT_ANIMATION = 5;

    private static final int INVOCATIONS_AFTER_REQUEST = 2;
    private static final int INVOCATIONS_AFTER_STEP = 3;

    private static final int ATTACK_FRAME_INDEX = 17;
    private static final int MARINE_INITIAL_FRAME_INDEX = 68;
    private static final int IMAGES_WITH_SHADOW = 2;
    private static final int SHADOW_IMAGE_INDEX = 1;
    private static final int SHADOW_MODIFIER = 10;

    /**
     * Creates a {@link Game} instance for the source game of a preview on a one-tile map.
     *
     * @param gameData the loaded game data
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame(@NotNull GameData gameData) {
        return Game.create(gameData, new GameMap(MAP_WIDTH, MAP_HEIGHT, TILESET, new int[MAP_WIDTH * MAP_HEIGHT]), new LcgRandom(RANDOM_SEED));
    }

    /**
     * Creates a {@link ChkUnit} instance for a full-health placement of player {@code 0} at the preview position.
     *
     * @param unitType the unit type
     * @return the created {@link ChkUnit} instance
     */
    private static @NotNull ChkUnit createUnit(int unitType) {
        return new ChkUnit(UNIT_SERIAL, UNIT_POSITION, UNIT_POSITION, unitType, 0, 0, 0, 0, FULL_HIT_POINTS_PERCENT, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void preview_steps_attack_animation_without_changing_world_state() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = createGame(gameData);
            ChkUnit unit = createUnit(MARINE);

            game.getRandom().setEnabled(0);

            long initialHash = game.computeStateHash();
            ImageScriptPreview preview = new ImageScriptPreview(game, unit);

            Assertions.assertEquals(1, preview.getInvocationCount());
            Assertions.assertEquals(0, preview.getSprite().getPoolIndex());
            Assertions.assertEquals(unit.getX(), preview.getSprite().getX());
            Assertions.assertEquals(unit.getY(), preview.getSprite().getY());
            Assertions.assertEquals(0, preview.getImage().getIndex());
            Assertions.assertSame(preview.getSprite(), preview.getImage().getSprite());

            preview.requestAnimation(GROUND_ATTACK_REPEAT_ANIMATION);

            Assertions.assertNull(preview.getStopReason());
            Assertions.assertEquals(GROUND_ATTACK_INIT_ANIMATION, preview.getImage().getAnimationType());
            Assertions.assertEquals(0, preview.getImage().getDrawnFrameIndex());
            Assertions.assertEquals(0, preview.getImage().getWait());

            preview.step();

            Assertions.assertEquals(ATTACK_FRAME_INDEX, preview.getImage().getDrawnFrameIndex());
            Assertions.assertEquals(0, preview.getImage().getWait());
            Assertions.assertEquals(INVOCATIONS_AFTER_STEP, preview.getInvocationCount());
            Assertions.assertEquals(initialHash, game.computeStateHash());
            Assertions.assertEquals(0, game.getCurrentFrame());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void preview_passes_sound_and_stops_at_missing_effect_storage_without_mutating_world() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = createGame(gameData);
            ChkUnit unit = createUnit(BROODLING);
            long initialHash = game.computeStateHash();
            ImageScriptPreview preview = new ImageScriptPreview(game, unit);

            Assertions.assertEquals(1, preview.getInvocationCount());

            preview.requestAnimation(DEATH_ANIMATION);

            Assertions.assertNotNull(preview.getStopReason());
            Assertions.assertTrue(preview.getStopReason().contains("initialized primary thingy pool"));

            int entryOffset = gameData.getIscript().getAnimationOffset(preview.getScriptIdentifier(), DEATH_ANIMATION);

            Assertions.assertTrue(gameData.getIscript().decodeInstruction(entryOffset).getName().startsWith("playsnd"));
            Assertions.assertNotEquals(entryOffset, preview.getImage().getProgramCounter());
            Assertions.assertEquals(INVOCATIONS_AFTER_REQUEST, preview.getInvocationCount());

            int stoppedOffset = preview.getImage().getProgramCounter();

            preview.step();

            Assertions.assertEquals(INVOCATIONS_AFTER_REQUEST, preview.getInvocationCount());
            Assertions.assertEquals(stoppedOffset, preview.getImage().getProgramCounter());
            Assertions.assertEquals(0, game.getInterfaceLink().getSoundEvents().getCount());
            Assertions.assertEquals(initialHash, game.computeStateHash());
        }
    }

    @Test
    void preview_keeps_initialized_program_after_ignored_animation_request() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = createGame(gameData);
            ImageScriptPreview preview = new ImageScriptPreview(game, createUnit(MARINE));
            int initializedOffset = preview.getImage().getProgramCounter();
            int initializedWait = preview.getImage().getWait();

            preview.getImage().setFlags(0);
            preview.requestAnimation(GROUND_ATTACK_REPEAT_ANIMATION);

            Assertions.assertNull(preview.getStopReason());
            Assertions.assertEquals(initializedOffset, preview.getImage().getProgramCounter());
            Assertions.assertEquals(initializedWait, preview.getImage().getWait());

            preview.step();

            Assertions.assertNull(preview.getStopReason());
            Assertions.assertEquals(initializedWait - 1, preview.getImage().getWait());
            Assertions.assertEquals(INVOCATIONS_AFTER_STEP, preview.getInvocationCount());
            Assertions.assertEquals(IMAGES_WITH_SHADOW, preview.getSprite().getImages().size());
        }
    }

    @Test
    void preview_initializes_and_steps_marine_and_shadow_in_isolated_sprite() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = createGame(gameData);
            ChkUnit unit = createUnit(MARINE);
            long initialHash = game.computeStateHash();
            ImageScriptPreview preview = new ImageScriptPreview(game, unit);

            Assertions.assertNull(preview.getStopReason());
            Assertions.assertEquals(IMAGES_WITH_SHADOW, preview.getSprite().getImages().size());
            Assertions.assertEquals(MARINE_INITIAL_FRAME_INDEX, preview.getImage().getDrawnFrameIndex());
            Assertions.assertEquals(0, preview.getSprite().getImages().get(SHADOW_IMAGE_INDEX).getDrawnFrameIndex());
            Assertions.assertEquals(SHADOW_MODIFIER, preview.getSprite().getImages().get(SHADOW_IMAGE_INDEX).getModifier());

            int previousWait = preview.getImage().getWait();

            preview.step();

            Assertions.assertNull(preview.getStopReason());
            Assertions.assertEquals(previousWait - 1, preview.getImage().getWait());
            Assertions.assertEquals(MARINE_INITIAL_FRAME_INDEX, preview.getSprite().getImages().get(SHADOW_IMAGE_INDEX).getDrawnFrameIndex());
            Assertions.assertEquals(initialHash, game.computeStateHash());
        }
    }
}

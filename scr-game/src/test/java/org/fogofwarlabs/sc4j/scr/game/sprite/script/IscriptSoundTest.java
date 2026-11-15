package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.presentation.SoundEvents;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Checks script sound events and retained simulation state.
 */
@RequiresInstallation
final class IscriptSoundTest {
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_DIMENSION = 8;
    /**
     * The tile count of the test map.
     */
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    /**
     * The tileset of the test map.
     */
    private static final int TILESET = 0;
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 1;
    /**
     * The capacity of the image pool.
     */
    private static final int IMAGE_POOL_CAPACITY = 4;
    /**
     * The horizontal position of the sounding sprite.
     */
    private static final int SPRITE_POSITION_X = 123;
    /**
     * The vertical position of the sounding sprite.
     */
    private static final int SPRITE_POSITION_Y = 177;
    /**
     * The image type of the sounding image.
     */
    private static final int IMAGE_TYPE = 17;
    /**
     * The script offset of a {@code playsnd} instruction.
     */
    private static final int PLAY_SOUND_OFFSET = 15664;
    /**
     * The opcode of {@code playsnd}.
     */
    private static final int PLAY_SOUND_OPCODE = 0x18;
    /**
     * The instruction limit of the interrupted sprite execution.
     */
    private static final int INSTRUCTION_LIMIT = 1;
    /**
     * The source unit type of a sound without a unit.
     */
    private static final int NO_UNIT_TYPE = 228;
    /**
     * The byte size of a RIFF header: the {@code RIFF} tag, the chunk size and the {@code WAVE} tag.
     */
    private static final int RIFF_HEADER_SIZE = 12;
    /**
     * The byte offset of the {@code RIFF} tag in a wave file.
     */
    private static final int RIFF_TAG_OFFSET = 0;
    /**
     * The byte offset of the {@code WAVE} tag in a wave file.
     */
    private static final int WAVE_TAG_OFFSET = 8;
    /**
     * The byte length of a RIFF tag.
     */
    private static final int TAG_LENGTH = 4;
    /**
     * The frame after the first frame.
     */
    private static final int NEXT_FRAME = 1;
    /**
     * The sound event count after the {@code playsnd} execution: its sound.
     */
    private static final int EMITTED_EVENT_COUNT = 1;
    /**
     * The index of the first sound event.
     */
    private static final int FIRST_EVENT = 0;
    /**
     * The sprite type of the sounding sprite, which the sound event does not depend on.
     */
    private static final int SPRITE_TYPE = 0;

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


    @Test
    void execute_sprite_should_emit_script_sound_with_position_and_load_its_original_wave() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

        game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);

        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, SPRITE_POSITION_X, SPRITE_POSITION_Y, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        sprite.insertHeadImage(image);
        sprite.setMainImage(image);
        image.setProgramCounter(PLAY_SOUND_OFFSET);

        Assertions.assertNotNull(gameData);

        IscriptInstruction instruction = gameData.getIscript().decodeInstruction(PLAY_SOUND_OFFSET);

        Assertions.assertEquals(PLAY_SOUND_OPCODE, instruction.getOpcode());

        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                                                                () -> game.getIscriptExecutor().executeSprite(sprite, null, INSTRUCTION_LIMIT));

        Assertions.assertTrue(failure.getMessage().contains("instruction limit"));

        SoundEvents events = game.getInterfaceLink().getSoundEvents();

        Assertions.assertEquals(EMITTED_EVENT_COUNT, events.getCount());

        int soundId = instruction.getOperands()[0];

        Assertions.assertEquals(soundId, events.getSoundId(FIRST_EVENT));
        Assertions.assertEquals(SPRITE_POSITION_X, events.getPositionX(FIRST_EVENT));
        Assertions.assertEquals(SPRITE_POSITION_Y, events.getPositionY(FIRST_EVENT));
        Assertions.assertEquals(NO_UNIT_TYPE, events.getSourceUnitType(FIRST_EVENT));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

        byte[] wave = gameData.readSound(soundId);

        Assertions.assertTrue(wave.length > RIFF_HEADER_SIZE);
        Assertions.assertEquals("RIFF", new String(wave, RIFF_TAG_OFFSET, TAG_LENGTH, StandardCharsets.US_ASCII));
        Assertions.assertEquals("WAVE", new String(wave, WAVE_TAG_OFFSET, TAG_LENGTH, StandardCharsets.US_ASCII));

        // The frame increment keeps the events: commands raise sounds before it within the same loop iteration.
        game.setCurrentFrame(NEXT_FRAME);

        Assertions.assertEquals(EMITTED_EVENT_COUNT, events.getCount());
    }
}

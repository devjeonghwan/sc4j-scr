package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
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

/**
 * Checks animation request gates, attack remapping and bounded execution.
 */
@RequiresInstallation
final class IscriptTransitionTest {
    /**
     * The program counter set before a request; an ignored request keeps it.
     */
    private static final int RETAINED_PROGRAM_COUNTER = 4321;
    /**
     * The return address set before a request; an ignored request keeps it.
     */
    private static final int RETAINED_RETURN_ADDRESS = 9876;
    /**
     * The wait set before a request; an ignored request keeps it.
     */
    private static final int RETAINED_WAIT = 17;
    /**
     * The largest animation byte.
     */
    private static final int LAST_ANIMATION_TYPE = 255;
    /**
     * The range of the animation byte, by which a request wraps.
     */
    private static final int ANIMATION_BYTE_RANGE = 256;
    /**
     * A script identifier that no iscript uses.
     */
    private static final int MISSING_SCRIPT = 99999;
    /**
     * The instruction limit of an animation request.
     */
    private static final int REQUEST_LIMIT = 32;
    /**
     * The instruction limit of a single instruction.
     */
    private static final int SINGLE_INSTRUCTION_LIMIT = 1;
    /**
     * The GndAttkInit animation.
     */
    private static final int GROUND_ATTACK_INIT = 2;
    /**
     * The AirAttkInit animation.
     */
    private static final int AIR_ATTACK_INIT = 3;
    /**
     * The GndAttkRpt animation.
     */
    private static final int GROUND_ATTACK_REPEAT = 5;
    /**
     * The AirAttkRpt animation.
     */
    private static final int AIR_ATTACK_REPEAT = 6;
    /**
     * The Death animation.
     */
    private static final int DEATH_ANIMATION = 1;
    /**
     * The Init animation.
     */
    private static final int INIT_ANIMATION = 0;
    /**
     * The Walking animation.
     */
    private static final int WALKING_ANIMATION = 11;
    /**
     * The IsWorking animation.
     */
    private static final int IS_WORKING_ANIMATION = 19;
    /**
     * The iscript whose attack entries start with a {@code playfram} and a {@code wait}.
     */
    private static final int ATTACK_SCRIPT = 153;
    /**
     * The opcode of {@code playfram}.
     */
    private static final int PLAY_FRAME_OPCODE = 0x00;
    /**
     * The opcode of {@code wait}.
     */
    private static final int WAIT_OPCODE = 0x05;
    /**
     * The opcode of {@code playsnd}.
     */
    private static final int PLAY_SOUND_OPCODE = 0x18;
    /**
     * The opcode of {@code playsndrand}.
     */
    private static final int PLAY_RANDOM_SOUND_OPCODE = 0x19;
    /**
     * The opcode of {@code playsndbtwn}.
     */
    private static final int PLAY_SOUND_BETWEEN_OPCODE = 0x1A;
    /**
     * The byte size of a {@code playfram} instruction.
     */
    private static final int PLAY_FRAME_SIZE = 3;
    /**
     * The program counter advance over the {@code playfram} and the {@code wait} at an animation entry.
     */
    private static final int ENTRY_ADVANCE = 5;
    /**
     * The iscript whose Init entry is a {@code playfram} and a {@code wait 1}, and whose Death entry reaches {@code end}.
     */
    private static final int ENTRY_SCRIPT = 33;
    /**
     * The wait of the {@code wait} instruction at the Init entry of {@link #ENTRY_SCRIPT}.
     */
    private static final int ENTRY_WAIT = 1;
    /**
     * The byte distance from the Death entry of {@link #ENTRY_SCRIPT} to its {@code end} instruction.
     */
    private static final int END_DISTANCE = 2;
    /**
     * An Init request above the animation byte range.
     */
    private static final int WRAPPED_INIT_REQUEST = 256;
    /**
     * A Death request above the animation byte range.
     */
    private static final int WRAPPED_DEATH_REQUEST = 257;
    /**
     * The frame base before the Init restart.
     */
    private static final int FRAME_BASE = 7;
    /**
     * The iscript whose Death entry is a sound instruction.
     */
    private static final int SOUND_SCRIPT = 4;
    /**
     * The random call site of {@code playsndrand}.
     */
    private static final int SOUND_RANDOM_SOURCE = 4;
    /**
     * The random call site of {@code playsndbtwn}.
     */
    private static final int SOUND_RANGE_RANDOM_SOURCE = 5;
    /**
     * The operand of {@code playsnd} that holds the sound.
     */
    private static final int PLAY_SOUND_OPERAND = 0;
    /**
     * The operand of {@code playsndrand} that holds the sound count.
     */
    private static final int RANDOM_SOUND_COUNT_OPERAND = 0;
    /**
     * The first sound operand of {@code playsndrand}.
     */
    private static final int RANDOM_SOUND_FIRST_OPERAND = 1;
    /**
     * The operand of {@code playsndbtwn} that holds the first sound of the range.
     */
    private static final int SOUND_RANGE_FIRST_OPERAND = 0;
    /**
     * The operand of {@code playsndbtwn} that holds the last sound of the range.
     */
    private static final int SOUND_RANGE_LAST_OPERAND = 1;
    /**
     * The sound event count after the Death entry's sound instruction: its sound.
     */
    private static final int SOUND_EVENT_COUNT = 1;
    /**
     * The index of the first sound event.
     */
    private static final int FIRST_EVENT = 0;
    /**
     * The image count of the sounding sprite: its only image.
     */
    private static final int SINGLE_IMAGE_COUNT = 1;
    /**
     * The frame index of the image after the Init restart.
     */
    private static final int RESTARTED_FRAME = 0;
    /**
     * An instruction limit that is not positive.
     */
    private static final int INVALID_INSTRUCTION_LIMIT = 0;
    /**
     * The image flag of an image that runs every animation of its script.
     */
    private static final int USES_FULL_ISCRIPT_FLAG = 0x10;
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_DIMENSION = 1;
    /**
     * The tile count of the test map.
     */
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    /**
     * The tileset of the test map.
     */
    private static final int TILESET = 0;
    /**
     * The seed of the game random generator and of the expected random generator.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The sprite type of the owning sprites, which the animation requests do not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The image type of the requesting images, which the animation requests do not depend on.
     */
    private static final int IMAGE_TYPE = 0;

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
    void run_image_preview_animation_should_ignore_non_init_non_death_without_full_iscript() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setProgramCounter(RETAINED_PROGRAM_COUNTER);
        image.setReturnAddress(RETAINED_RETURN_ADDRESS);
        image.setWait(RETAINED_WAIT);

        for (int animationType = GROUND_ATTACK_INIT; animationType <= LAST_ANIMATION_TYPE; animationType++) {
            Assertions.assertTrue(game.getIscriptExecutor().runImagePreviewAnimation(image, sprite, MISSING_SCRIPT, animationType, REQUEST_LIMIT));

            Assertions.assertEquals(INIT_ANIMATION, image.getAnimationType());
            Assertions.assertEquals(RETAINED_PROGRAM_COUNTER, image.getProgramCounter());
            Assertions.assertEquals(RETAINED_RETURN_ADDRESS, image.getReturnAddress());
            Assertions.assertEquals(RETAINED_WAIT, image.getWait());
        }

        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void run_image_preview_animation_should_ignore_repeated_death_walking_and_working() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setFlags(USES_FULL_ISCRIPT_FLAG);

        for (int animationType : new int[]{DEATH_ANIMATION, WALKING_ANIMATION, IS_WORKING_ANIMATION}) {
            image.setAnimationType(animationType);
            image.setProgramCounter(RETAINED_PROGRAM_COUNTER);
            image.setReturnAddress(RETAINED_RETURN_ADDRESS);
            image.setWait(RETAINED_WAIT);

            Assertions.assertTrue(game.getIscriptExecutor().runImagePreviewAnimation(image,
                                                                                     sprite,
                                                                                     MISSING_SCRIPT,
                                                                                     animationType + ANIMATION_BYTE_RANGE,
                                                                                     REQUEST_LIMIT));

            Assertions.assertEquals(animationType, image.getAnimationType());
            Assertions.assertEquals(RETAINED_PROGRAM_COUNTER, image.getProgramCounter());
            Assertions.assertEquals(RETAINED_RETURN_ADDRESS, image.getReturnAddress());
            Assertions.assertEquals(RETAINED_WAIT, image.getWait());
        }

        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void run_image_preview_animation_should_remap_attack_repeat_when_attack_category_differs() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setFlags(USES_FULL_ISCRIPT_FLAG);

        for (int request : new int[]{GROUND_ATTACK_REPEAT, AIR_ATTACK_REPEAT}) {
            for (int currentAnimation : new int[]{
                    INIT_ANIMATION,
                    DEATH_ANIMATION,
                    GROUND_ATTACK_INIT,
                    AIR_ATTACK_INIT,
                    GROUND_ATTACK_REPEAT,
                    AIR_ATTACK_REPEAT,
                    WALKING_ANIMATION,
                    IS_WORKING_ANIMATION,
                    LAST_ANIMATION_TYPE
            }) {
                image.setAnimationType(currentAnimation);
                image.setWait(RETAINED_WAIT);
                image.setReturnAddress(RETAINED_RETURN_ADDRESS);

                int expectedAnimation = switch (request) {
                    case GROUND_ATTACK_REPEAT -> currentAnimation == GROUND_ATTACK_INIT || currentAnimation == GROUND_ATTACK_REPEAT
                                                 ? GROUND_ATTACK_REPEAT
                                                 : GROUND_ATTACK_INIT;
                    case AIR_ATTACK_REPEAT -> currentAnimation == AIR_ATTACK_INIT || currentAnimation == AIR_ATTACK_REPEAT
                                              ? AIR_ATTACK_REPEAT
                                              : AIR_ATTACK_INIT;
                    default -> throw Exceptions.newAssertionError();
                };

                Assertions.assertNotNull(gameData);

                int entryOffset = gameData.getIscript().getAnimationOffset(ATTACK_SCRIPT, expectedAnimation);

                Assertions.assertEquals(PLAY_FRAME_OPCODE, gameData.getIscript().decodeInstruction(entryOffset).getOpcode());
                Assertions.assertEquals(WAIT_OPCODE, gameData.getIscript().decodeInstruction(entryOffset + PLAY_FRAME_SIZE).getOpcode());

                Assertions.assertTrue(game.getIscriptExecutor().runImagePreviewAnimation(image, sprite, ATTACK_SCRIPT, request, REQUEST_LIMIT));

                Assertions.assertEquals(expectedAnimation, image.getAnimationType());
                Assertions.assertEquals(entryOffset + ENTRY_ADVANCE, image.getProgramCounter());
                Assertions.assertEquals(0, image.getReturnAddress());
            }
        }

        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void run_image_preview_animation_should_restart_init_and_permit_death_without_full_iscript() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setWait(RETAINED_WAIT);
        image.setReturnAddress(RETAINED_RETURN_ADDRESS);
        image.setFrameIndex(FRAME_BASE);

        Assertions.assertTrue(game.getIscriptExecutor().runImagePreviewAnimation(image, sprite, ENTRY_SCRIPT, WRAPPED_INIT_REQUEST, REQUEST_LIMIT));

        Assertions.assertEquals(INIT_ANIMATION, image.getAnimationType());
        Assertions.assertEquals(RESTARTED_FRAME, image.getDrawnFrameIndex());
        Assertions.assertEquals(ENTRY_WAIT, image.getWait());
        Assertions.assertEquals(0, image.getReturnAddress());

        Assertions.assertTrue(game.getIscriptExecutor().runImagePreviewAnimation(image, sprite, ENTRY_SCRIPT, WRAPPED_DEATH_REQUEST, REQUEST_LIMIT));

        Assertions.assertEquals(DEATH_ANIMATION, image.getAnimationType());

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(gameData.getIscript().getAnimationOffset(ENTRY_SCRIPT, DEATH_ANIMATION) + END_DISTANCE, image.getProgramCounter());
    }

    @Test
    void run_image_preview_animation_should_emit_death_sound_then_continue_at_next_instruction() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        sprite.insertTailImage(image);
        sprite.setMainImage(image);
        image.setAnimationType(LAST_ANIMATION_TYPE);

        Assertions.assertNotNull(gameData);

        int entryOffset = gameData.getIscript().getAnimationOffset(SOUND_SCRIPT, DEATH_ANIMATION);
        IscriptInstruction instruction = gameData.getIscript().decodeInstruction(entryOffset);
        int[] operands = instruction.getOperands();
        LcgRandom expectedRandom = new LcgRandom(RANDOM_SEED);
        int expectedSound = switch (instruction.getOpcode()) {
            case PLAY_SOUND_OPCODE -> operands[PLAY_SOUND_OPERAND];
            case PLAY_RANDOM_SOUND_OPCODE -> operands[RANDOM_SOUND_FIRST_OPERAND +
                                                      expectedRandom.next(SOUND_RANDOM_SOURCE) % operands[RANDOM_SOUND_COUNT_OPERAND]];
            case PLAY_SOUND_BETWEEN_OPCODE -> operands[SOUND_RANGE_FIRST_OPERAND] + expectedRandom.next(SOUND_RANGE_RANDOM_SOURCE) %
                                                                                    (operands[SOUND_RANGE_LAST_OPERAND] - operands[SOUND_RANGE_FIRST_OPERAND] + 1);
            default -> throw Exceptions.newAssertionError("The captured Death entry must remain a sound instruction.");
        };

        IllegalStateException exception = Assertions.assertThrows(
                IllegalStateException.class,
                () -> game.getIscriptExecutor().runImagePreviewAnimation(image, sprite, SOUND_SCRIPT, DEATH_ANIMATION, SINGLE_INSTRUCTION_LIMIT)
        );

        Assertions.assertTrue(exception.getMessage().contains("instruction limit"));
        Assertions.assertEquals(entryOffset + gameData.getIscript().getInstructionSize(entryOffset), image.getProgramCounter());
        Assertions.assertEquals(SOUND_EVENT_COUNT, game.getInterfaceLink().getSoundEvents().getCount());
        Assertions.assertEquals(expectedSound, game.getInterfaceLink().getSoundEvents().getSoundId(FIRST_EVENT));
        Assertions.assertEquals(SINGLE_IMAGE_COUNT, sprite.getImages().size());
        Assertions.assertEquals(expectedRandom.getTotalRandomCounts(), game.getRandom().getTotalRandomCounts());
    }

    @Test
    void limited_invocation_resumes_at_its_first_unexecuted_instruction() throws IOException {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        Assertions.assertNotNull(gameData);

        int entryOffset = gameData.getIscript().getAnimationOffset(ENTRY_SCRIPT, INIT_ANIMATION);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> game.getIscriptExecutor().runImagePreviewAnimation(image, sprite, ENTRY_SCRIPT, INIT_ANIMATION, SINGLE_INSTRUCTION_LIMIT)
        );

        Assertions.assertEquals(entryOffset + PLAY_FRAME_SIZE, image.getProgramCounter());
        Assertions.assertEquals(0, image.getWait());

        Assertions.assertTrue(game.getIscriptExecutor().executeImagePreview(image, sprite, SINGLE_INSTRUCTION_LIMIT));

        Assertions.assertEquals(entryOffset + ENTRY_ADVANCE, image.getProgramCounter());
        Assertions.assertEquals(ENTRY_WAIT, image.getWait());

        Assertions.assertTrue(game.getIscriptExecutor().executeImagePreview(image, sprite, SINGLE_INSTRUCTION_LIMIT));

        Assertions.assertEquals(entryOffset + ENTRY_ADVANCE, image.getProgramCounter());
        Assertions.assertEquals(0, image.getWait());

        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> game.getIscriptExecutor().executeImagePreview(image, sprite, INVALID_INSTRUCTION_LIMIT));
    }
}

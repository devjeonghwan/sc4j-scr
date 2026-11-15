package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.AddedGraphicsScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks an image added after the native {@code images.dat} entries with its own synthetic frames and script: the unit of an added type shows the
 * added image and its shadow, picks the frame of its direction in each frame set, walks, attacks and dies through its own animations, and a
 * written replay that trains it plays back twice with equal state hashes.
 */
@RequiresInstallation
final class AddedImageTest {
    private static final int FIGHT_OFFSET_Y = 96;
    private static final int FIGHT_DISTANCE = 32;
    private static final int WALK_DISTANCE = 160;
    private static final int STEP_FRAMES = 400;
    private static final int LAST_FRAME = 1600;

    private static @Nullable GameData gameData;
    private static @Nullable AddedGraphicsScenario scenario;

    @BeforeAll
    static void loadGameData() throws IOException {
        GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());

        gameData = installedData;
        scenario = AddedGraphicsScenario.register(installedData);
    }

    @AfterAll
    static void closeGameData() {
        GameData installedData = gameData;

        gameData = null;
        scenario = null;

        if (installedData != null) {
            IgnoreException.execute(
                    installedData::close
            );
        }
    }

    private static @NotNull GameData requireGameData() {
        Assertions.assertNotNull(gameData);

        return gameData;
    }

    private static @NotNull AddedGraphicsScenario requireScenario() {
        Assertions.assertNotNull(scenario);

        return scenario;
    }

    /**
     * Steps a game and collects the frame set and the mirroring of every frame the added main image of a sprite shows, until the frame limit or the
     * end of the sprite's images; every drawn frame lies in the image's frames, at a direction offset of the frame set.
     */
    private static void collectFrames(@NotNull ReplayRuntime runtime, @NotNull Sprite sprite, int frames, @NotNull Set<Integer> sets,
                                      @NotNull Set<Boolean> mirrored) {
        Game game = runtime.getGame();

        for (int frame = 0; frame < frames && sprite.getMainImage() != null; frame++) {
            SpriteImage image = sprite.getMainImage();

            if (image.getImageTypeId() == requireScenario().getImageId()) {
                sets.add(image.getFrameIndex() / AddedGraphicsScenario.DIRECTIONS);
                mirrored.add((image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0);
                Assertions.assertEquals(0, image.getFrameIndex() % AddedGraphicsScenario.DIRECTIONS);
                Assertions.assertTrue(image.getFrameIndexOffset() >= 0 && image.getFrameIndexOffset() < AddedGraphicsScenario.DIRECTIONS);
                Assertions.assertTrue(image.getDrawnFrameIndex() < AddedGraphicsScenario.FRAME_COUNT);
            }

            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }
    }

    private static @NotNull Sprite requireSprite(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        return sprite;
    }

    @Test
    void added_unit_shows_the_added_image_and_its_shadow_at_the_frame_of_its_direction() throws IOException {
        AddedGraphicsScenario added = requireScenario();
        ReplayRuntime runtime = RunningGameScenario.start(requireGameData());
        Game game = runtime.getGame();
        Unit[] units = new Unit[1];

        RunningGameScenario.runInCommandDispatch(runtime, () -> units[0] = added.createUnit(game, 0, 0, RunningGameScenario.HUMAN));

        Sprite sprite = units[0].getSpriteOrNull();

        Assertions.assertNotNull(sprite);
        Assertions.assertEquals(added.getSpriteId(), sprite.getSpriteTypeId());

        SpriteImage main = sprite.getMainImage();
        List<Integer> imageTypes = new ArrayList<>();

        for (SpriteImage image : sprite.getImages()) {
            imageTypes.add(image.getImageTypeId());
        }

        Assertions.assertNotNull(main);
        Assertions.assertEquals(added.getImageId(), main.getImageTypeId());
        Assertions.assertTrue(imageTypes.contains(added.getShadowImageId()), "The added script creates the added shadow image: " + imageTypes);
        Assertions.assertEquals(AddedGraphicsScenario.FRAME_COUNT, game.requireGameData().getGrpFile(added.getImageId()).getFrameCount());
        Assertions.assertEquals(AddedGraphicsScenario.IDLE_SET * AddedGraphicsScenario.DIRECTIONS, main.getFrameIndex());
        Assertions.assertTrue(main.getFrameIndexOffset() >= 0 && main.getFrameIndexOffset() < AddedGraphicsScenario.DIRECTIONS);
    }

    @Test
    void added_unit_walks_attacks_and_dies_through_its_own_animations() throws IOException {
        AddedGraphicsScenario added = requireScenario();
        ReplayRuntime runtime = RunningGameScenario.start(requireGameData());
        Game game = runtime.getGame();
        Unit[] units = new Unit[2];

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            units[0] = added.createUnit(game, 0, FIGHT_OFFSET_Y, RunningGameScenario.HUMAN);
            game.getOrders().issue(units[0], ScrOrderIds.MOVE, null, units[0].getPositionX() + WALK_DISTANCE, units[0].getPositionY(),
                                   ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
        });

        Set<Integer> walking = new TreeSet<>();
        Set<Boolean> eastMirrored = new TreeSet<>();
        Set<Boolean> westMirrored = new TreeSet<>();

        collectFrames(runtime, requireSprite(units[0]), STEP_FRAMES, walking, eastMirrored);
        RunningGameScenario.runInCommandDispatch(runtime, () -> game.getOrders().issue(units[0], ScrOrderIds.MOVE, null,
                                                                                       units[0].getPositionX() - WALK_DISTANCE, units[0].getPositionY(),
                                                                                       ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT));
        collectFrames(runtime, requireSprite(units[0]), STEP_FRAMES, walking, westMirrored);

        Assertions.assertTrue(walking.contains(AddedGraphicsScenario.WALK_SET) && walking.contains(AddedGraphicsScenario.SECOND_WALK_SET),
                              "The walking animation shows both walk frame sets: " + walking);
        Assertions.assertTrue(eastMirrored.contains(false), "Facing east shows unmirrored frames.");
        Assertions.assertTrue(westMirrored.contains(true), "Facing west shows the mirrored frames.");

        RunningGameScenario.runInCommandDispatch(runtime, () -> units[1] = added.createNativeUnit(game, ScrUnitIds.TERRAN_GOLIATH, units[0],
                                                                                                  FIGHT_DISTANCE, RunningGameScenario.COMPUTER));

        Set<Integer> fighting = new TreeSet<>();

        collectFrames(runtime, requireSprite(units[0]), LAST_FRAME, fighting, new TreeSet<>());

        Assertions.assertNull(runtime.getFailure());
        Assertions.assertTrue(fighting.contains(AddedGraphicsScenario.ATTACK_SET), "The attack animation shows the attack frame set: " + fighting);
        Assertions.assertTrue(fighting.contains(AddedGraphicsScenario.DEATH_SET), "The death animation shows the death frame set: " + fighting);
        Assertions.assertNull(AddedGraphicsScenario.findUnit(game, RunningGameScenario.HUMAN, added.getUnitTypeId()), "The added unit dies.");
    }

    @Test
    void added_unit_is_trained_from_a_written_replay_that_plays_back_with_equal_hashes() throws IOException {
        AddedGraphicsScenario added = requireScenario();
        List<ReplayRuntime> runtimes = new ArrayList<>();
        List<Long> first = added.playTrainingReplay(LAST_FRAME, runtimes);
        List<Long> second = added.playTrainingReplay(LAST_FRAME, runtimes);

        Assertions.assertEquals(first, second, "Two playbacks of the written replay have equal state hashes.");

        for (ReplayRuntime runtime : runtimes) {
            Unit unit = AddedGraphicsScenario.findUnit(runtime.getGame(), RunningGameScenario.HUMAN, added.getUnitTypeId());

            Assertions.assertNotNull(unit, "The replay trains the added unit.");

            Assertions.assertEquals(added.getImageId(), unit.requireSprite().requireMainImage().getImageTypeId());
        }
    }
}

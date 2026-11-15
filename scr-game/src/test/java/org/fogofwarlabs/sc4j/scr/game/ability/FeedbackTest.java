package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;

import java.io.IOException;

/**
 * Verifies Feedback target effects, energy spending and rejected-target behavior in a running game.
 */
@RequiresInstallation
final class FeedbackTest {
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    private static final int FEEDBACK_SMALL_IMAGE = 979;
    private static final int FEEDBACK_IMAGE_COUNT = 3;
    private static final int FEEDBACK_HIT_SMALL_SPRITE = 500;
    private static final int DIE_ORDER = 0;
    private static final int CASTER_POSITION_X = 3560;
    private static final int CASTER_POSITION_Y = 2160;
    private static final int TARGET_POSITION_X = 3656;
    private static final int TARGET_POSITION_Y = 2160;
    private static final int CASTER_ENERGY = 200 << FIXED_POINT_FRACTION_BITS;
    private static final int SURVIVING_TARGET_ENERGY = 10 << FIXED_POINT_FRACTION_BITS;
    private static final int LETHAL_TARGET_ENERGY = 50 << FIXED_POINT_FRACTION_BITS;
    private static final int LETHAL_TARGET_HIT_POINTS = 1 << FIXED_POINT_FRACTION_BITS;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
            gameData = null;
        }
    }

    private static boolean hasFeedbackImage(@NotNull Sprite sprite) {
        for (SpriteImage image : sprite.getImages()) {
            if (image.getImageTypeId() >= FEEDBACK_SMALL_IMAGE && image.getImageTypeId() < FEEDBACK_SMALL_IMAGE + FEEDBACK_IMAGE_COUNT) {
                return true;
            }
        }

        return false;
    }


    private @Nullable ReplayRuntime runtime;

    @BeforeEach
    void startScenario() throws IOException {
        Assertions.assertNotNull(gameData);

        this.runtime = RunningGameScenario.start(gameData);
    }

    @AfterEach
    void releaseScenario() {
        this.runtime = null;
    }


    @Test
    void feedback_places_a_surviving_ghost_overlay_on_the_target_with_a_different_size() {
        this.assertSurvivingTarget(ScrUnitIds.TERRAN_GHOST, true);
    }

    @Test
    void feedback_places_a_surviving_science_vessel_overlay_on_the_target() {
        this.assertSurvivingTarget(ScrUnitIds.TERRAN_SCIENCE_VESSEL, false);
    }

    @Test
    void feedback_places_a_lethal_hit_effect_at_the_target_position_and_elevation() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_GHOST,
                                                         TARGET_POSITION_X, TARGET_POSITION_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit caster = this.prepareCaster(game, target);
            Sprite targetSprite = target.requireSprite();
            Sprite casterSprite = caster.requireSprite();
            int targetX = (short) targetSprite.getX();
            int targetY = (short) targetSprite.getY();
            int targetElevation = targetSprite.getElevationLevel();
            int spriteType = FEEDBACK_HIT_SMALL_SPRITE + game.getUnitSpells().getSpriteSize(target);
            int casterEnergy = caster.getEnergy();
            int cost = game.requireGameData().getTechTypes().getEnergyCost(ScrTechIds.FEEDBACK) << FIXED_POINT_FRACTION_BITS;

            target.setHitPoints(LETHAL_TARGET_HIT_POINTS);
            target.setEnergy(LETHAL_TARGET_ENERGY);

            game.getUnitSpells().updateFeedback(caster, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(DIE_ORDER, target.getPrimaryOrder());
            Assertions.assertSame(targetSprite, target.getSpriteOrNull());
            Assertions.assertEquals(0, target.getEnergy());
            Assertions.assertEquals(casterEnergy - cost, caster.getEnergy());
            Assertions.assertFalse(hasFeedbackImage(casterSprite));

            SpriteEffectPool effects = game.getSpriteSystem().getSpriteEffectPool();

            Assertions.assertNotNull(effects);

            Sprite feedbackSprite = null;

            for (int slotIndex = 0; slotIndex < effects.getCapacity(); slotIndex++) {
                Sprite sprite = effects.getSpriteEffect(slotIndex).getSpriteOrNull();

                if (sprite != null && sprite.getSpriteTypeId() == spriteType) {
                    Assertions.assertNull(feedbackSprite, "one Feedback hit effect");

                    feedbackSprite = sprite;
                }
            }

            Assertions.assertNotNull(feedbackSprite);
            Assertions.assertEquals(targetX, (short) feedbackSprite.getX());
            Assertions.assertEquals(targetY, (short) feedbackSprite.getY());
            Assertions.assertEquals(targetElevation + 1, feedbackSprite.getElevationLevel());
            Assertions.assertNotEquals(casterSprite.getX(), feedbackSprite.getX());
        });
    }

    @Test
    void feedback_does_not_spend_energy_or_create_an_overlay_for_an_empty_target() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_GHOST,
                                                         TARGET_POSITION_X, TARGET_POSITION_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit caster = this.prepareCaster(game, target);
            int hitPoints = target.getHitPoints();

            target.setEnergy(0);

            game.getUnitSpells().updateFeedback(caster, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(hitPoints, target.getHitPoints());
            Assertions.assertEquals(0, target.getEnergy());
            Assertions.assertEquals(CASTER_ENERGY, caster.getEnergy());
            Assertions.assertFalse(hasFeedbackImage(target.requireSprite()));
            Assertions.assertFalse(hasFeedbackImage(caster.requireSprite()));
        });
    }

    @Test
    void feedback_rejects_a_non_spellcaster_without_damage_or_energy_spending() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_MARINE,
                                                         TARGET_POSITION_X, TARGET_POSITION_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit caster = this.prepareCaster(game, target);
            int hitPoints = target.getHitPoints();

            target.setEnergy(SURVIVING_TARGET_ENERGY);

            game.getUnitSpells().updateFeedback(caster, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(hitPoints, target.getHitPoints());
            Assertions.assertEquals(SURVIVING_TARGET_ENERGY, target.getEnergy());
            Assertions.assertEquals(CASTER_ENERGY, caster.getEnergy());
            Assertions.assertFalse(hasFeedbackImage(target.requireSprite()));
        });
    }

    @Test
    void feedback_rejects_a_spellcaster_with_hallucination_status() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_GHOST,
                                                         TARGET_POSITION_X, TARGET_POSITION_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit caster = this.prepareCaster(game, target);
            int hitPoints = target.getHitPoints();

            target.setStatus(target.getStatus() | UnitStatusFlags.HALLUCINATION);
            target.setEnergy(SURVIVING_TARGET_ENERGY);

            game.getUnitSpells().updateFeedback(caster, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(hitPoints, target.getHitPoints());
            Assertions.assertEquals(SURVIVING_TARGET_ENERGY, target.getEnergy());
            Assertions.assertEquals(CASTER_ENERGY, caster.getEnergy());
            Assertions.assertFalse(hasFeedbackImage(target.requireSprite()));
        });
    }

    @Test
    void feedback_finishes_without_spending_energy_when_its_target_is_missing() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_GHOST,
                                                         TARGET_POSITION_X, TARGET_POSITION_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit caster = this.prepareCaster(game, target);

            caster.setOrderTargetUnit(null);

            game.getUnitSpells().updateFeedback(caster, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(CASTER_ENERGY, caster.getEnergy());
            Assertions.assertFalse(hasFeedbackImage(caster.requireSprite()));
        });
    }

    private void assertSurvivingTarget(int targetType, boolean differentSize) {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit target = RunningGameScenario.createUnit(game, targetType, TARGET_POSITION_X, TARGET_POSITION_Y, RunningGameScenario.COMPUTER);
            Unit caster = this.prepareCaster(game, target);
            Sprite targetSprite = target.requireSprite();
            Sprite casterSprite = caster.requireSprite();
            int targetSize = game.getUnitSpells().getSpriteSize(target);
            int imageType = FEEDBACK_SMALL_IMAGE + targetSize;
            int hitPoints = target.getHitPoints();
            int cost = game.requireGameData().getTechTypes().getEnergyCost(ScrTechIds.FEEDBACK) << FIXED_POINT_FRACTION_BITS;

            if (differentSize) {
                Assertions.assertNotEquals(game.getUnitSpells().getSpriteSize(caster), targetSize, "distinct overlay sizes");
            }

            target.setEnergy(SURVIVING_TARGET_ENERGY);

            game.getUnitSpells().updateFeedback(caster, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(DIE_ORDER, target.getPrimaryOrder());
            Assertions.assertTrue(target.getHitPoints() < hitPoints);
            Assertions.assertEquals(0, target.getEnergy());
            Assertions.assertEquals(CASTER_ENERGY - cost, caster.getEnergy());
            Assertions.assertTrue(targetSprite.getImages().stream().anyMatch(image -> image.getImageTypeId() == imageType));
            Assertions.assertFalse(hasFeedbackImage(casterSprite));
        });
    }

    private @NotNull Unit prepareCaster(@NotNull Game game, @NotNull Unit target) {
        Unit caster = RunningGameScenario.createUnit(game,
                                                     ScrUnitIds.PROTOSS_DARK_ARCHON,
                                                     CASTER_POSITION_X, CASTER_POSITION_Y,
                                                     RunningGameScenario.HUMAN);

        target.requireSprite().setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);
        caster.setEnergy(CASTER_ENERGY);
        caster.setHeading(FlingyMotionUtils.calculateDirection(target.getPositionX() - caster.getPositionX(),
                                                               target.getPositionY() - caster.getPositionY()));
        caster.setPrimaryOrder(ScrOrderIds.CAST_FEEDBACK);
        caster.setOrderTargetUnit(target);

        return caster;
    }
}

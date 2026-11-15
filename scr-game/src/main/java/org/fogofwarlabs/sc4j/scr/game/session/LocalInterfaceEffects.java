package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffect;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.fogofwarlabs.sc4j.scr.game.visibility.SpriteVisibility;
import org.jetbrains.annotations.NotNull;

/**
 * Applies local interface effects directly, outside the command stream.
 * <p>
 * These effects are not recorded commands, so replaying the command stream does not reproduce them.
 */
public final class LocalInterfaceEffects {
    /**
     * The sprite created where the critter died ({@code 0x10b}).
     */
    private static final int CRITTER_EFFECT_SPRITE = 0x10B;
    private static final int CRITTER_EFFECT_OWNER = 0;
    private static final int CRITTER_EFFECT_ELEVATION = 4;
    private static final int ATTACK_ALERT_MARK = 100;
    private static final int ATTACK_ALERT_MARK_RADIUS = 352;

    private final @NotNull Game game;

    /**
     * Creates a {@link LocalInterfaceEffects} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public LocalInterfaceEffects(@NotNull Game game) {
        this.game = game;
    }


    /**
     * Decrements nonzero attack-alert marks in the active creation list.
     */
    public void decrementAttackAlertMarks() {
        UnitPool units = this.game.getUnitSystem().getUnitPool();

        if (units == null) {
            return;
        }

        for (int index = units.getFirstCreatedIndex(true); index != 0; index = units.getNextCreatedIndex(index)) {
            Unit unit = units.getUnit(index);

            if (unit.getAlertMark() != 0) {
                unit.setAlertMark(unit.getAlertMark() - 1);
            }
        }
    }

    /**
     * Marks the units found by the unexpanded rectangle query around an attack-alert position.
     * The query uses signed WORD boundaries and advances the shared finder counter and markers.
     *
     * @param positionX the horizontal alert position
     * @param positionY the vertical alert position
     * @throws IllegalStateException if unit storage exists without its initialized finder
     */
    public void markAttackAlertArea(int positionX, int positionY) {
        UnitPool units = this.game.getUnitSystem().getUnitPool();

        if (units == null) {
            return;
        }

        UnitSpatialIndex spatialIndex = this.game.getUnitSystem().getUnitSpatialIndex();

        if (spatialIndex == null) {
            throw Exceptions.newIllegalStateException("Attack-alert area marking requires the initialized unit finder.");
        }

        int count = spatialIndex.collectPlacementCandidates(positionX - ATTACK_ALERT_MARK_RADIUS, positionY - ATTACK_ALERT_MARK_RADIUS,
                                                            positionX + ATTACK_ALERT_MARK_RADIUS, positionY + ATTACK_ALERT_MARK_RADIUS);

        for (int index = 0; index < count; index++) {
            spatialIndex.getPlacementCandidate(index).setAlertMark(ATTACK_ALERT_MARK);
        }
    }

    /**
     * Applies the single-player critter easter egg after repeated selection clicks: kills the critter and creates a
     * sprite effect with tile visibility. The interface runs between frames with the game random generator disabled.
     *
     * @param unit             the clicked critter
     * @param instructionLimit the order and animation budget
     */
    public void killCritter(@NotNull Unit unit, int instructionLimit) {
        int positionX = unit.getPositionX();
        int positionY = unit.getPositionY();

        this.game.getUnitLifecycle().kill(unit, instructionLimit);

        SpriteEffect effect = this.game.getIscriptExecutor().createSpriteEffect(CRITTER_EFFECT_SPRITE, positionX, positionY, CRITTER_EFFECT_OWNER,
                                                                                instructionLimit);

        if (effect == null) {
            return;
        }

        Sprite sprite = effect.getSprite();

        sprite.setElevationLevel(CRITTER_EFFECT_ELEVATION);

        SpriteVisibility.updatePositionTile(sprite, this.game.getMap(), this.game.getLocalVision().getComparisonMask());
    }
}

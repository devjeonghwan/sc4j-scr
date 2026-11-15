package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Re-creates the status-effect overlay images of a unit whose sprite images were rebuilt.
 * <p>
 * Spell handlers rebuild effects with temporary timer resets; invalid defensive matrix or irradiation is removed. Acid spores recreate the overlay on
 * the unit or its subunit.
 */
public final class UnitStatusEffects {
    private final @NotNull Game game;

    /**
     * Creates a {@link UnitStatusEffects} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitStatusEffects(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Rebuilds effect overlays after a morph, an unburrow or a construction image replacement.
     *
     * @param unit             the unit whose images were rebuilt
     * @param instructionLimit the image, order, script and speed budget
     * @throws IllegalStateException if the unit (its subunit when it has one) has no sprite
     */
    public void apply(@NotNull Unit unit, int instructionLimit) {
        if (unit.getDefensiveMatrixTimer() != 0) {
            if ((unit.getStatus() & UnitStatusFlags.INVINCIBLE) != 0 ||
                !this.game.getUnitSpells().isTechnologyTargetValid(unit, ScrTechIds.DEFENSIVE_MATRIX, unit)) {
                unit.setDefensiveMatrixHealth(0);
                unit.setDefensiveMatrixTimer(0);
            } else {
                this.game.getUnitSpells().createDefensiveMatrixImage(unit, instructionLimit);
            }
        }

        if (unit.getLockdownTimer() != 0) {
            int timer = unit.getLockdownTimer();

            unit.setLockdownTimer(0);
            this.game.getUnitSpells().applyLockdown(unit, instructionLimit);
            unit.setLockdownTimer(timer);
        }

        if (unit.getMaelstromTimer() != 0) {
            int timer = unit.getMaelstromTimer();

            unit.setMaelstromTimer(0);
            this.game.getUnitSpells().applyMaelstrom(unit, instructionLimit);
            unit.setMaelstromTimer(timer);
        }

        if (unit.getIrradiateTimer() != 0) {
            if (!this.game.getUnitTargeting().canWeaponTarget(ScrWeaponIds.IRRADIATE, unit)) {
                unit.setIrradiateTimer(0);
                unit.setIrradiatedBy(null);
            } else {
                int timer = unit.getIrradiateTimer();

                unit.setIrradiateTimer(0);
                this.game.getUnitSpells().applyIrradiation(unit.getIrradiatedBy(), unit.getIrradiateOwner(), unit, instructionLimit);
                unit.setIrradiateTimer(timer);
            }
        }

        if (unit.getSpeedDecreaseByte() != 0) {
            int timer = unit.getSpeedDecreaseByte();

            unit.setSpeedDecreaseByte(0);
            this.game.getUnitSpells().applyEnsnare(unit, instructionLimit);
            unit.setSpeedDecreaseByte(timer);
        }

        if (unit.getStasisTimer() != 0) {
            int timer = unit.getStasisTimer();

            unit.setStasisTimer(0);
            this.game.getUnitSpells().applyStasis(unit, instructionLimit);
            unit.setStasisTimer(timer);
        }

        if (unit.getPlagueTimer() != 0) {
            int timer = unit.getPlagueTimer();

            unit.setPlagueTimer(0);
            this.game.getUnitSpells().applyPlague(unit, instructionLimit);
            unit.setPlagueTimer(timer);
        }

        if (unit.getAcidSporeCount() != 0) {
            int count = Math.min(unit.getAcidSporeCount() / UnitSpells.ACID_SPORES_PER_STAGE, UnitSpells.ACID_SPORE_STAGES - 1);
            Unit carrier = unit.getSubunit() != null ? unit.getSubunit() : unit;
            Sprite sprite = carrier.requireSprite();

            this.game.getIscriptExecutor().createImage(sprite,
                                                       sprite.getImages().isEmpty() ? null : sprite.getImages().getFirst(),
                                                       ImageIds.ACID_SPORES_1_OVERLAY_SMALL +
                                                       UnitSpells.ACID_SPORE_STAGES * this.game.getUnitSpells().getSpriteSize(unit) + count,
                                                       0, 0,
                                                       0,
                                                       carrier,
                                                       instructionLimit);
        }
    }
}

package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.combat;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sound.ScrPlayRandomSound;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code attackmelee}: deals the melee damage of the unit, then plays one of the sounds at random.
 */
public final class ScrMeleeAttack extends ScrInstruction {
    private final int @NotNull [] sounds;

    /**
     * Creates a {@link ScrMeleeAttack} instance.
     *
     * @param decoded the decoded {@code attackmelee} instruction
     */
    public ScrMeleeAttack(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.sounds = ScrPlayRandomSound.copySounds(decoded);
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * The sound draw is the one of {@code playsndrand} ({@link ScrPlayRandomSound#playRandomSound}), also without a unit context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        Unit unit = target.getUnit();

        // Deal melee damage before the random sound.
        if (unit != null) {
            target.getGame().getUnitDamage().dealMeleeDamage(unit, getRemainingInstructions(context));
        }

        ScrPlayRandomSound.playRandomSound(target, this.sounds);

        return AnimationStep.CONTINUE;
    }
}

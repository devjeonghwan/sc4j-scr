package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sprite;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code lowsprul}: creates a sprite at the low fixed elevation.
 */
public final class ScrCreateLowSprite extends ScrInstruction {
    /**
     * The fixed elevation of a {@code lowsprul} sprite.
     */
    private static final int LOW_SPRITE_ELEVATION = 1;

    private final int spriteType;
    private final int offsetX;
    private final int offsetY;

    /**
     * Creates a {@link ScrCreateLowSprite} instance.
     *
     * @param decoded the decoded {@code lowsprul} instruction
     */
    public ScrCreateLowSprite(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.spriteType = decoded.getOperands()[0];
        this.offsetX    = decoded.getOperands()[1];
        this.offsetY    = decoded.getOperands()[2];
    }


    /**
     * Runs this instruction for the animated object of the context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        target.getExecutor().createSpriteEffectAtImage(target.getImage(),
                                                       target.getSprite(),
                                                       this.spriteType,
                                                       this.offsetX, this.offsetY,
                                                       LOW_SPRITE_ELEVATION,
                                                       target.getUnit(),
                                                       false,
                                                       getRemainingInstructions(context));

        return AnimationStep.CONTINUE;
    }
}

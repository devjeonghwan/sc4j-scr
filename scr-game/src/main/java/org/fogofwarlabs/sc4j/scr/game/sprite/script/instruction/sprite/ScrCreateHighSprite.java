package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sprite;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code highsprol}: creates a sprite one elevation below the sprite of the image.
 */
public final class ScrCreateHighSprite extends ScrInstruction {
    private final int spriteType;
    private final int offsetX;
    private final int offsetY;

    /**
     * Creates a {@link ScrCreateHighSprite} instance.
     *
     * @param decoded the decoded {@code highsprol} instruction
     */
    public ScrCreateHighSprite(@NotNull IscriptInstruction decoded) {
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

        Sprite sprite = target.getSprite();

        target.getExecutor().createSpriteEffectAtImage(target.getImage(),
                                                       sprite,
                                                       this.spriteType,
                                                       this.offsetX, this.offsetY,
                                                       sprite.getElevationLevel() - 1,
                                                       target.getUnit(),
                                                       false,
                                                       getRemainingInstructions(context));

        return AnimationStep.CONTINUE;
    }
}

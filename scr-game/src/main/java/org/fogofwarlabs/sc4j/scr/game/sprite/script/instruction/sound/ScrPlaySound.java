package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sound;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code playsnd}: plays a sound at the sprite of the image.
 */
public final class ScrPlaySound extends ScrInstruction {
    private final int sound;

    /**
     * Creates a {@link ScrPlaySound} instance.
     *
     * @param decoded the decoded {@code playsnd} instruction
     */
    public ScrPlaySound(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.sound = decoded.getOperands()[0];
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

        target.getExecutor().playSound(this.sound, sprite.getX(), sprite.getY());

        return AnimationStep.CONTINUE;
    }
}

package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sound;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code playsndbtwn}: plays a random sound of the range of the two operands at the sprite of the image.
 */
public final class ScrPlaySoundBetween extends ScrInstruction {
    /**
     * The random source of {@code playsndbtwn}.
     */
    private static final int RANDOM_SOURCE = 5;

    private final int firstSound;
    private final int lastSound;

    /**
     * Creates a {@link ScrPlaySoundBetween} instance.
     *
     * @param decoded the decoded {@code playsndbtwn} instruction
     */
    public ScrPlaySoundBetween(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.firstSound = decoded.getOperands()[0];
        this.lastSound  = decoded.getOperands()[1];
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
        int sound = this.firstSound + target.getGame().getRandom().next(RANDOM_SOURCE) % (this.lastSound - this.firstSound + 1);

        target.getExecutor().playSound(sound, sprite.getX(), sprite.getY());

        return AnimationStep.CONTINUE;
    }
}

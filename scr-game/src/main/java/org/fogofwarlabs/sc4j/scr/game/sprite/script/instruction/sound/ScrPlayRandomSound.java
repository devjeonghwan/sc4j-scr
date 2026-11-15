package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sound;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Runs {@code playsndrand}: plays one of the sounds of the operands at random at the sprite of the image.
 */
public final class ScrPlayRandomSound extends ScrInstruction {
    /**
     * The random source of {@code playsndrand} and of the {@code attackmelee} sound.
     */
    private static final int RANDOM_SOURCE = 4;
    /**
     * The operand that holds the sound count; the sounds follow it.
     */
    private static final int COUNT_OPERAND = 0;

    /**
     * Copies the sounds of a {@code playsndrand} or {@code attackmelee} instruction: the operands after the count.
     *
     * @param decoded the decoded instruction
     * @return the sounds in operand order
     */
    public static int @NotNull [] copySounds(@NotNull IscriptInstruction decoded) {
        int[] operands = decoded.getOperands();

        return Arrays.copyOfRange(operands, COUNT_OPERAND + 1, COUNT_OPERAND + 1 + operands[COUNT_OPERAND]);
    }

    /**
     * Plays one of the sounds at random at the sprite of the image of the target: draws one value from the random source of
     * {@code playsndrand} and takes the sound at the value modulo the sound count.
     *
     * @param target the image, sprite, unit and game of the update
     * @param sounds the sounds
     * @throws ArithmeticException if there is no sound
     */
    public static void playRandomSound(@NotNull ScrImageTarget target, int @NotNull [] sounds) {
        Sprite sprite = target.getSprite();
        int sound = sounds[target.getGame().getRandom().next(RANDOM_SOURCE) % sounds.length];

        target.getExecutor().playSound(sound, sprite.getX(), sprite.getY());
    }


    private final int @NotNull [] sounds;

    /**
     * Creates a {@link ScrPlayRandomSound} instance.
     *
     * @param decoded the decoded {@code playsndrand} instruction
     */
    public ScrPlayRandomSound(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.sounds = copySounds(decoded);
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Plays the sound {@link #playRandomSound} selects.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        playRandomSound(getTarget(context), this.sounds);

        return AnimationStep.CONTINUE;
    }
}

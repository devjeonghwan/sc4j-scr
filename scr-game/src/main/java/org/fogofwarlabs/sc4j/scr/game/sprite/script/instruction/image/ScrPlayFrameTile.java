package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code playframtile}: shows the frame plus the tileset index of the map, if the GRP of the image has that frame.
 */
public final class ScrPlayFrameTile extends ScrInstruction {
    /**
     * The mask retaining an unsigned WORD.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The mask of the frame count of a GRP (its high bit is a flag).
     */
    private static final int FRAME_COUNT_MASK = 0x7FFF;

    private final int frameIndex;

    /**
     * Creates a {@link ScrPlayFrameTile} instance.
     *
     * @param decoded the decoded {@code playframtile} instruction
     */
    public ScrPlayFrameTile(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.frameIndex = decoded.getOperands()[0];
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

        SpriteImage image = target.getImage();
        GrpFile graphics = target.getExecutor().getGrpFile(image);
        int tilesetFrameIndex = (this.frameIndex + target.getGame().getGameContext().getContextTileset()) & WORD_MASK;

        if (tilesetFrameIndex < (graphics.getFrameCount() & FRAME_COUNT_MASK)) {
            image.showFrame(tilesetFrameIndex);
        }

        return AnimationStep.CONTINUE;
    }
}

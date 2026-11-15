package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.overlay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecution;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Runs {@code creategasoverlays}: creates the smoke image of a gas overlay of a resource container above the image at the
 * special overlay offset of its frame.
 */
public final class ScrCreateGasOverlays extends ScrInstruction {
    private final int overlayIndex;

    /**
     * Creates a {@link ScrCreateGasOverlays} instance.
     *
     * @param decoded the decoded {@code creategasoverlays} instruction
     */
    public ScrCreateGasOverlays(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.overlayIndex = decoded.getOperands()[0];
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * An unbounded update of a resource container fails with an {@link UnsupportedOperationException}, and unreadable overlay
     * offsets with an {@link IllegalStateException}.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        Unit unit = target.getUnit();
        Game game = target.getGame();

        if (unit == null || (game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_CONTAINER) == 0) {
            return AnimationStep.CONTINUE;
        }

        IscriptExecution execution = target.getExecution();

        if (execution == null) {
            throw Exceptions.newUnsupportedOperationException("Gas overlay creation requires bounded pooled execution.");
        }

        SpriteImage image = target.getImage();
        int offset;

        try {
            offset = game.requireGameData().getImageSpecialOverlayOffset(image.getImageTypeId(), image.getDrawnFrameIndex(), this.overlayIndex);
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "Failed to load gas overlay offsets for image {0}", image.getImageTypeId());
        }

        int horizontalOffset = (short) offset;

        if ((image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0) {
            horizontalOffset = -horizontalOffset;
        }

        // Select the smoke image before the pooled Init.
        int imageType = (unit.getResources() != 0 ? ImageIds.VESPENE_GEYSER_SMOKE1 : ImageIds.VESPENE_GEYSER_SMOKE1_OVERLAY) + this.overlayIndex;

        target.getExecutor().createImagePreview(execution,
                                                image,
                                                target.getSprite(),
                                                imageType,
                                                image.getOffsetX() + horizontalOffset, image.getOffsetY() + (short) (offset >>> Short.SIZE),
                                                IscriptExecutor.ORDER_ABOVE,
                                                unit,
                                                true);

        return AnimationStep.CONTINUE;
    }
}

package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sprite;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code grdsprol}: creates a sprite one elevation above the sprite of the image where a Marine could stand.
 */
public final class ScrCreateGroundSprite extends ScrInstruction {
    private final int spriteType;
    private final int offsetX;
    private final int offsetY;

    /**
     * Creates a {@link ScrCreateGroundSprite} instance.
     *
     * @param decoded the decoded {@code grdsprol} instruction
     */
    public ScrCreateGroundSprite(@NotNull IscriptInstruction decoded) {
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

        Game game = target.getGame();
        SpriteImage image = target.getImage();
        Sprite sprite = target.getSprite();

        // The overlay appears only where a Marine could stand.
        if (game.getUnitPositioning().isTerrainPlacementWalkable(ScrUnitIds.TERRAN_MARINE,
                                                                 sprite.getX() + image.getOffsetX() + this.offsetX,
                                                                 sprite.getY() + image.getOffsetY() + this.offsetY)) {
            target.getExecutor().createSpriteEffectAtImage(image,
                                                           sprite,
                                                           this.spriteType,
                                                           this.offsetX, this.offsetY,
                                                           sprite.getElevationLevel() + 1,
                                                           target.getUnit(),
                                                           false,
                                                           getRemainingInstructions(context));
        }

        return AnimationStep.CONTINUE;
    }
}

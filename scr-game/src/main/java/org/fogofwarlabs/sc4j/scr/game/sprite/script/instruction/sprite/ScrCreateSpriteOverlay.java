package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sprite;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code sprol}: creates a sprite one elevation above the sprite of the image; the Halo Rockets trail replaces it
 * for a Goliath bullet with Charon Boosters.
 */
public final class ScrCreateSpriteOverlay extends ScrInstruction {
    private final int spriteType;
    private final int offsetX;
    private final int offsetY;

    /**
     * Creates a {@link ScrCreateSpriteOverlay} instance.
     *
     * @param decoded the decoded {@code sprol} instruction
     */
    public ScrCreateSpriteOverlay(@NotNull IscriptInstruction decoded) {
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
        Sprite sprite = target.getSprite();
        int effectSpriteType = this.spriteType;
        Unit bulletOwner = game.getProjectilePhase().getCurrentOwner();

        if (bulletOwner != null &&
            (bulletOwner.getUnitId() == ScrUnitIds.TERRAN_GOLIATH || bulletOwner.getUnitId() == ScrUnitIds.HERO_ALAN_SCHEZAR)) {
            PlayerUpgrades upgrades = game.getPlayerTable().getPlayerUpgrades();

            if (upgrades == null) {
                throw Exceptions.newUnsupportedOperationException("Projectile trail selection requires player upgrades.");
            }

            if (upgrades.getLevel(bulletOwner.getOwnerIndex(), ScrUpgradeIds.CHARON_BOOSTERS) != 0) {
                effectSpriteType = SpriteIds.HALO_ROCKETS_TRAIL;
            }
        }

        target.getExecutor().createSpriteEffectAtImage(target.getImage(),
                                                       sprite,
                                                       effectSpriteType,
                                                       this.offsetX, this.offsetY,
                                                       sprite.getElevationLevel() + 1,
                                                       target.getUnit(),
                                                       false,
                                                       getRemainingInstructions(context));

        return AnimationStep.CONTINUE;
    }
}

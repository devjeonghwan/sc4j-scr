package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitSpeed;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Predicts the future positions of a moving unit and retains the mode-one iscript scratch state the prediction leaves.
 */
public final class IscriptMotionPrediction {
    /**
     * The mask retaining an unsigned heading or turn-rate byte.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The mask retaining the low WORD of a sampled position.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The fraction bits of a fixed-point unit position.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    /**
     * The multiplier of the state hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    private final @NotNull IscriptModeOneInterpreter interpreter;

    private int scriptIdentifier;
    private int animation;

    /**
     * Creates a {@link IscriptMotionPrediction} instance with a zeroed scratch state.
     */
    public IscriptMotionPrediction() {
        this.interpreter = new IscriptModeOneInterpreter();

        this.scriptIdentifier = 0;
        this.animation        = 0;
    }

    /**
     * Predicts the positions of a moving unit: switches a copy of its main image script to the Walking animation, then turns the unit toward its
     * movement waypoint and moves it frame by frame with the speed the mode-one script yields, sampling the position every {@code frameCount} frames.
     *
     * @param game             the game, whose random generator the script draws
     * @param unit             the moving unit
     * @param frameCount       the number of frames between two samples
     * @param positions        receives the sampled pixel positions, X in the low word and Y in the high word
     * @param instructionLimit the instruction budget of the mode-one script
     * @throws IllegalArgumentException if the budget is not positive
     * @throws IllegalStateException    if the main image or its script header is absent or the iscript cannot be read, or the unit has no sprite
     * @throws NullPointerException     if the world was built without game data
     */
    public void predict(@NotNull Game game, @NotNull Unit unit, int frameCount, int @NotNull [] positions, int instructionLimit) {
        if (instructionLimit <= 0) {
            throw Exceptions.newIllegalArgumentException("Native iscript prediction requires a positive instruction budget.");
        }

        SpriteImage image = unit.requireSprite().getMainImage();

        if (image == null) {
            throw Exceptions.newIllegalStateException("Native prediction dereferences an absent main image.");
        }

        IscriptModeOneInterpreter interpreter = this.interpreter;

        this.scriptIdentifier = image.getScriptIdentifier();
        this.animation        = image.getAnimationType();
        interpreter.start(image.getProgramCounter(), image.getReturnAddress(), image.getWait(), instructionLimit);

        IscriptFile script = game.requireGameData().getIscript();
        int speed = unit.getNextSpeed();
        int direction = FlingyMotionUtils.calculateDirection((short) unit.getMovementWaypointX() - (short) unit.requireSprite().getX(),
                                                             (short) unit.getMovementWaypointY() - (short) unit.requireSprite().getY());
        int heading = unit.getTargetHeading();
        int flingyType = game.requireGameData().getUnitTypes().getFlingyType(unit.getUnitId());
        int turnRate = game.requireGameData().getFlingyTypes().getTurnRate(flingyType);
        int speedModifier = unit.getSpeedModifier();

        turnRate = UnitSpeed.applySpeedModifier(turnRate, speedModifier) & BYTE_MASK;

        try {
            if (this.animation != IscriptExecutor.ANIMATION_WALKING) {
                this.animation = IscriptExecutor.ANIMATION_WALKING;

                IscriptHeader header = script.findHeader(this.scriptIdentifier);

                if (header == null) {
                    throw Exceptions.newIllegalStateException("Native prediction could not resolve the retained script header.");
                }

                if (header.getType() >= IscriptExecutor.ANIMATION_WALKING) {
                    interpreter.setProgramCounter(script.getAnimationOffset(this.scriptIdentifier, IscriptExecutor.ANIMATION_WALKING));

                    if (interpreter.getProgramCounter() != 0) {
                        interpreter.clearReturnAddress();
                        interpreter.clearWait();
                    }
                }

                speed = interpreter.step(game, unit, speed);
            }

            int fixedPositionX = unit.getFixedPositionX();
            int fixedPositionY = unit.getFixedPositionY();
            int velocityX = 0;
            int velocityY = 0;
            // The heading stays at the direction once it reaches it, so the velocity is computed at the first moving frame only.
            boolean refreshVelocity = true;

            for (int sampleIndex = 0; sampleIndex < positions.length; sampleIndex++) {
                for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    if (heading != direction) {
                        int difference = (byte) (direction - heading);

                        heading = (heading + Math.max(-turnRate, Math.min(turnRate, difference))) & BYTE_MASK;
                    }

                    if (heading == direction && speed != 0) {
                        if (refreshVelocity) {
                            long velocity = FlingyMotionUtils.calculateDirectionVector(heading, speed);

                            velocityX       = (int) velocity;
                            velocityY       = (int) (velocity >>> Integer.SIZE);
                            refreshVelocity = false;
                        }

                        fixedPositionX += velocityX;
                        fixedPositionY += velocityY;
                    }

                    speed = interpreter.step(game, unit, 0);
                }

                positions[sampleIndex] = ((fixedPositionX >> FIXED_POINT_FRACTION_BITS) & WORD_MASK) |
                                         ((fixedPositionY >> FIXED_POINT_FRACTION_BITS) << Short.SIZE);
            }
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "Native prediction could not read iscript memory.");
        }
    }

    /**
     * Mixes the retained scratch state into a state hash.
     *
     * @param hash the hash so far
     * @return the updated hash
     */
    public int computeStateHash(int hash) {
        hash = hash * HASH_MULTIPLIER + this.scriptIdentifier;
        hash = hash * HASH_MULTIPLIER + this.interpreter.getProgramCounter();
        hash = hash * HASH_MULTIPLIER + this.interpreter.getReturnAddress();
        hash = hash * HASH_MULTIPLIER + this.animation;

        return hash * HASH_MULTIPLIER + this.interpreter.getWait();
    }
}

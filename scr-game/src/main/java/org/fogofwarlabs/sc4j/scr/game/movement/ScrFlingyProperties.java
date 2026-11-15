package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.content.table.MotionProfileTable;
import org.fogofwarlabs.sc4j.engine.movement.MotionProfileProperties;
import org.fogofwarlabs.sc4j.scr.content.ScrFlingyTraits;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the flingy type values of SC:R units for the engine motion algorithm by the native flingy type WORD of the unit
 * ({@link Unit#getFlingyType()}): the speed values from the game's {@code flingy.dat} view, so an id outside the table reads the DAT memory
 * around it as the view does, and the accelerating turn from the engine {@code acceleratingTurn} column of the game's content (an id outside the
 * table has no accelerating turn). Data without a game content reads the importer's rule ({@link ScrFlingyTraits#isAcceleratingTurn}).
 */
public final class ScrFlingyProperties implements MotionProfileProperties<Unit> {
    private final @NotNull ScrFlingyTypeView flingyTypes;
    private final @Nullable MotionProfileTable motionProfiles;

    /**
     * Creates a {@link ScrFlingyProperties} instance over the flingy data of a game.
     *
     * @param flingyTypes    the {@code flingy.dat} view of the game
     * @param motionProfiles the motion profile table of the game's content, or {@code null} for data without a game content
     */
    public ScrFlingyProperties(@NotNull ScrFlingyTypeView flingyTypes, @Nullable MotionProfileTable motionProfiles) {
        this.flingyTypes    = flingyTypes;
        this.motionProfiles = motionProfiles;
    }

    /**
     * @param object the moving object
     * @return the top speed of the object's flingy type, fixed-point with eight fraction bits
     */
    @Override
    public int getTopSpeed(@NotNull Unit object) {
        return this.flingyTypes.getTopSpeed(object.getFlingyType());
    }

    /**
     * @param object the moving object
     * @return the acceleration of the object's flingy type, fixed-point with eight fraction bits
     */
    @Override
    public int getAcceleration(@NotNull Unit object) {
        return this.flingyTypes.getAcceleration(object.getFlingyType());
    }

    /**
     * @param object the moving object
     * @return the distance the object's flingy type needs to stop from its top speed, fixed-point with eight fraction bits
     */
    @Override
    public int getStoppingDistance(@NotNull Unit object) {
        return this.flingyTypes.getHaltDistance(object.getFlingyType());
    }

    /**
     * @param object the moving object
     * @return {@code true} if the turn rate of the object grows by one each frame it turns, {@code false} otherwise
     */
    @Override
    public boolean isAcceleratingTurn(@NotNull Unit object) {
        return this.isAcceleratingTurn(object.getFlingyType());
    }

    /**
     * Checks whether the turn rate of a flingy type grows by one each frame it turns; bullets read it by their flingy type.
     *
     * @param flingyType the native flingy type WORD
     * @return {@code true} if the turn rate grows, {@code false} otherwise
     */
    public boolean isAcceleratingTurn(int flingyType) {
        MotionProfileTable table = this.motionProfiles;

        if (table == null) {
            return ScrFlingyTraits.isAcceleratingTurn(flingyType);
        }

        return flingyType >= 0 && flingyType < table.getCount() && table.isAcceleratingTurn(flingyType);
    }
}

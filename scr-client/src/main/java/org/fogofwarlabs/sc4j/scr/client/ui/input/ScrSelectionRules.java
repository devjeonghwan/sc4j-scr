package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.engine.client.command.SelectionRules;
import org.fogofwarlabs.sc4j.scr.client.ui.command.InterfaceSelection;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.SpriteDrawOrder;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Selects local interface units by the SC:R rules: the {@link SelectionRules} of the engine selection filter for box, same-type and Shift-merge
 * selections. A subunit stands for its base unit; a unit is listed when the vision mask sees its sprite, it is detected when it needs detection
 * and its type is not excluded; it joins a multiple selection when its type allows it and it is the local player's (any owner in a replay); the
 * replacement key is the packed sprite draw-order key. The rules read game state without changing units.
 */
public final class ScrSelectionRules implements SelectionRules<Unit> {
    /**
     * The most units a selection holds.
     */
    public static final int CAPACITY = InterfaceSelection.CAPACITY;

    /**
     * Status bits whose presence requires the unit to be detected.
     */
    private static final int DETECTION_STATUS = UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED;

    private final @NotNull ScrGameView view;

    private final int localPlayer;
    private final boolean replay;
    private final int visionMask;

    /**
     * Creates a {@link ScrSelectionRules} instance.
     *
     * @param view        the game view
     * @param localPlayer the local player slot
     * @param replay      whether replay or observer selection permits any owner
     * @param visionMask  the local vision mask (byte)
     */
    public ScrSelectionRules(@NotNull ScrGameView view, int localPlayer, boolean replay, int visionMask) {
        this.view = view;

        this.localPlayer = localPlayer;
        this.replay      = replay;
        this.visionMask  = visionMask;
    }

    /**
     * Finds the unit that a unit stands for in a selection: a subunit (units.dat flag {@link UnitTypeFlags#SUBUNIT}) stands for its base unit.
     *
     * @param unit the unit
     * @return the unit selected for it, the unit itself if it stands for itself
     * @throws NullPointerException if the game was built without game data
     */
    @Override
    public @NotNull Unit getMainUnit(@NotNull Unit unit) {
        Unit parent = unit.getSubunit();

        if (parent == null) {
            return unit;
        }

        int type = unit.getUnitId();
        ScrUnitTypeView unitTypes = this.view.requireGameData().getUnitTypes();
        int flags = type >= 0 && unitTypes.hasEntry(type) ? unitTypes.getFlags(type) : 0;

        return (flags & UnitTypeFlags.SUBUNIT) != 0 ? parent : unit;
    }

    /**
     * Tells whether the local player may select a unit at all: the vision mask sees its sprite, it is detected when it needs detection, and
     * its type is not excluded ({@link UnitPicker#canBeSelected(Unit)}).
     *
     * @param unit the unit
     * @return {@code true} if the unit may be selected, {@code false} otherwise
     */
    @Override
    public boolean isListable(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null || (sprite.getVisibilityFlags() & this.visionMask) == 0) {
            return false;
        }

        if ((unit.getStatus() & DETECTION_STATUS) != 0 && (unit.getDetectionFlags() & this.visionMask) == 0) {
            return false;
        }

        return UnitPicker.canBeSelected(unit);
    }

    /**
     * Tells whether a unit may be one of several selected units: its type may join a multiple selection
     * ({@link UnitSelection#canSelectMultiple}) and it is the local player's, or of any owner in a replay.
     *
     * @param unit the unit
     * @return {@code true} if the unit joins a multiple selection, {@code false} otherwise
     * @throws NullPointerException if the game was built without game data
     */
    @Override
    public boolean canJoinMultipleSelection(@NotNull Unit unit) {
        return UnitSelection.canSelectMultiple(this.view.requireGameData().getUnitTypes(), unit) &&
               (this.replay || unit.getOwnerIndex() == this.localPlayer);
    }

    /**
     * Reads the replacement key of a unit: its sprite's {@link SpriteDrawOrder#computeKey} draw-order key.
     *
     * @param unit the unit
     * @return the unsigned key, or {@code 0}, the lowest key, for a unit without a sprite
     */
    @Override
    public int getReplacementKey(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        return sprite != null ? SpriteDrawOrder.computeKey(sprite) : 0;
    }

    /**
     * Tells whether a unit is of the same kind as a clicked unit: the same owner and type, the same burrow state, the same detection state
     * unless the unit is passively cloaked and not burrowed, and the same hallucination state.
     *
     * @param unit    the unit
     * @param clicked the clicked unit
     * @return {@code true} if the unit is of the clicked unit's kind, {@code false} otherwise
     */
    @Override
    public boolean isSameKind(@NotNull Unit unit, @NotNull Unit clicked) {
        if (unit.getOwnerIndex() != clicked.getOwnerIndex() || unit.getUnitId() != clicked.getUnitId()) {
            return false;
        }

        int status = unit.getStatus();
        int clickedStatus = clicked.getStatus();

        if (((status ^ clickedStatus) & UnitStatusFlags.BURROWED) != 0) {
            return false;
        }

        boolean passive = (status & UnitStatusFlags.PASSIVELY_CLOAKED) != 0 && (status & UnitStatusFlags.BURROWED) == 0;

        if (!passive && ((status ^ clickedStatus) & UnitStatusFlags.REQUIRES_DETECTOR) != 0) {
            return false;
        }

        return ((status ^ clickedStatus) & UnitStatusFlags.HALLUCINATION) == 0;
    }
}

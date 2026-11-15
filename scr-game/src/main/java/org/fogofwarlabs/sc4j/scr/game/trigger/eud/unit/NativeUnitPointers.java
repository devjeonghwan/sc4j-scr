package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps pointer fields of the 488-byte unit layout to unit-pool indices for {@link ClassicUnitTableRegion}.
 * Replacements store raw links without relinking the surrounding lists. Type-dependent pointer fields select the
 * psi-provider or rally target; rally coordinates occupy the corresponding position DWORD.
 */
public final class NativeUnitPointers {
    /**
     * The mask of the low WORD.
     */
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The native offset of the previous unit of the unit's list (creation, hidden, dying, revealer), or the previous free slot.
     */
    public static final int PREVIOUS_LINK = 0x00;
    /**
     * The native offset of the next unit of the unit's list, or the next free slot of a free slot.
     */
    public static final int NEXT_LINK = 0x08;
    /**
     * The native offset of the movement target.
     */
    public static final int MOVEMENT_TARGET = 0x28;
    /**
     * The native offset of the order target.
     */
    public static final int ORDER_TARGET = 0x80;
    /**
     * The native offset of the previous unit of the owner's list.
     */
    public static final int PREVIOUS_OWNER_LINK = 0x90;
    /**
     * The native offset of the next unit of the owner's list.
     */
    public static final int NEXT_OWNER_LINK = 0x98;
    /**
     * The native offset of the subunit.
     */
    public static final int SUBUNIT = 0xA0;
    /**
     * The native offset of the automatic target.
     */
    public static final int AUTOMATIC_TARGET = 0xB8;
    /**
     * The native offset of the connected (parent) unit.
     */
    public static final int CONNECTED_UNIT = 0xC0;
    /**
     * The native offset of the unit under construction.
     */
    public static final int CURRENT_BUILD_UNIT = 0x158;
    /**
     * The native offset of the unit that irradiated this unit.
     */
    public static final int IRRADIATED_BY = 0x1A8;
    /**
     * The native offset of a Pylon's previous psi provider.
     */
    public static final int PREVIOUS_PSI_PROVIDER = 0x170;
    /**
     * The native offset of the next psi provider of a Pylon, the rally unit of a building that rallies; the member follows
     * the unit's type.
     */
    public static final int PSI_NEXT_OR_RALLY_UNIT = 0x178;

    /**
     * The shift of the Y WORD in the rally position DWORD.
     */
    private static final int RALLY_POSITION_Y_SHIFT = 16;


    /**
     * Reads the rally position DWORD of a building that rallies, at the offset of {@link #PREVIOUS_PSI_PROVIDER}.
     *
     * @param unit a unit
     * @return the rally position DWORD: X in the low WORD, Y above
     */
    public static int readRallyPosition(@NotNull Unit unit) {
        return unit.getRallyPositionX() & WORD_MASK | (unit.getRallyPositionY() & WORD_MASK) << RALLY_POSITION_Y_SHIFT;
    }

    /**
     * Stores the rally position DWORD of a building that rallies, at the offset of {@link #PREVIOUS_PSI_PROVIDER}.
     *
     * @param unit  a unit
     * @param value X in the low WORD, Y above
     */
    public static void writeRallyPosition(@NotNull Unit unit, int value) {
        unit.setRallyPositionX(value);
        unit.setRallyPositionY(value >>> RALLY_POSITION_Y_SHIFT);
    }

    /**
     * Reads the unit pointer at a native offset.
     *
     * @param pool         the unit pool
     * @param unit         the unit
     * @param nativeOffset one of the pointer offsets of this class
     * @return the one-based pool index the pointer designates, or {@code 0} for none
     * @throws IllegalArgumentException if the offset is no pointer offset of this class
     */
    public static int read(@NotNull UnitPool pool, @NotNull Unit unit, int nativeOffset) {
        int poolIndex = unit.getIndex();

        return switch (nativeOffset) {
            case PREVIOUS_LINK -> pool.isAllocated(poolIndex) ? pool.getPreviousCreatedIndex(poolIndex) : pool.getPreviousFreeIndex(poolIndex);
            case NEXT_LINK -> pool.isAllocated(poolIndex) ? pool.getNextCreatedIndex(poolIndex) : pool.getNextFreeIndex(poolIndex);
            case PREVIOUS_OWNER_LINK -> pool.getPreviousOwnerIndex(poolIndex);
            case NEXT_OWNER_LINK -> pool.getNextOwnerIndex(poolIndex);
            default -> {
                Unit target = getTarget(unit, nativeOffset);

                yield target == null ? 0 : target.getIndex();
            }
        };
    }

    /**
     * Stores a raw pointer.
     *
     * @param pool         the unit pool
     * @param unit         the unit
     * @param nativeOffset one of the pointer offsets of this class
     * @param poolIndex    the one-based pool index of the new target, or {@code 0} for none
     * @throws IllegalArgumentException if the offset is no pointer offset of this class
     */
    public static void write(@NotNull UnitPool pool, @NotNull Unit unit, int nativeOffset, int poolIndex) {
        int unitIndex = unit.getIndex();
        Unit target = poolIndex == 0 ? null : pool.getUnit(poolIndex);

        switch (nativeOffset) {
            case PREVIOUS_LINK -> {
                if (pool.isAllocated(unitIndex)) {
                    pool.setPreviousCreatedIndexRaw(unitIndex, poolIndex);
                } else {
                    pool.setPreviousFreeIndexRaw(unitIndex, poolIndex);
                }
            }
            case NEXT_LINK -> {
                if (pool.isAllocated(unitIndex)) {
                    pool.setNextCreatedIndexRaw(unitIndex, poolIndex);
                } else {
                    pool.setNextFreeIndexRaw(unitIndex, poolIndex);
                }
            }
            case PREVIOUS_OWNER_LINK -> pool.setPreviousOwnerIndexRaw(unitIndex, poolIndex);
            case NEXT_OWNER_LINK -> pool.setNextOwnerIndexRaw(unitIndex, poolIndex);
            case MOVEMENT_TARGET -> unit.setMovementTargetUnit(target);
            case ORDER_TARGET -> unit.setOrderTargetUnit(target);
            case SUBUNIT -> unit.setSubunit(target);
            case AUTOMATIC_TARGET -> unit.setAutomaticTargetUnit(target);
            case CONNECTED_UNIT -> unit.setParentUnit(target);
            case CURRENT_BUILD_UNIT -> unit.setCurrentBuildUnit(target);
            case IRRADIATED_BY -> unit.setIrradiatedBy(target);
            case PREVIOUS_PSI_PROVIDER -> unit.setPreviousPsiProvider(target);
            case PSI_NEXT_OR_RALLY_UNIT -> {
                if (unit.getUnitId() == ScrUnitIds.PROTOSS_PYLON) {
                    unit.setNextPsiProvider(target);
                } else {
                    unit.setRallyTargetUnit(target);
                }
            }
            default -> throw Exceptions.newIllegalArgumentException("Native unit offset 0x{0} is no pointer.", Integer.toHexString(nativeOffset));
        }
    }

    private static @Nullable Unit getTarget(@NotNull Unit unit, int nativeOffset) {
        return switch (nativeOffset) {
            case MOVEMENT_TARGET -> unit.getMovementTargetUnit();
            case ORDER_TARGET -> unit.getOrderTargetUnit();
            case SUBUNIT -> unit.getSubunit();
            case AUTOMATIC_TARGET -> unit.getAutomaticTargetUnit();
            case CONNECTED_UNIT -> unit.getParentUnit();
            case CURRENT_BUILD_UNIT -> unit.getCurrentBuildUnit();
            case IRRADIATED_BY -> unit.getIrradiatedBy();
            case PREVIOUS_PSI_PROVIDER -> unit.getPreviousPsiProvider();
            case PSI_NEXT_OR_RALLY_UNIT -> unit.getUnitId() == ScrUnitIds.PROTOSS_PYLON ? unit.getNextPsiProvider() : unit.getRallyTargetUnit();
            default -> throw Exceptions.newIllegalArgumentException("Native unit offset 0x{0} is no pointer.", Integer.toHexString(nativeOffset));
        };
    }


    private NativeUnitPointers() {
    }
}

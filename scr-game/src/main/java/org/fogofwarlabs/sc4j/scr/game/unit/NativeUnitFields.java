package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Maps the scalar fields of the {@code 488}-byte unit layout to {@link Unit} for the EUD unit table and for the unbounded build queue
 * read of {@link UnitQueries#getQueuedType} and write of {@link UnitLifecycle#clearCurrentQueueSlot}. The generation BYTE is the slot's
 * generation, which its {@link UnitPool} manages.
 * <p>
 * Field tables identify the start and width of each represented byte range; padding, pointers and unsupported fields
 * have no start. Reads and writes translate offsets only at this boundary.
 */
public final class NativeUnitFields {
    /**
     * The size of the native unit structure.
     */
    private static final int SIZE = 488;
    /**
     * The mask of one byte of a field.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The native offset of the four bytes of the classic event bytes ({@link Unit#getClassicEventBytes()}).
     */
    private static final int CLASSIC_EVENT_BYTES = 0xD0;
    /**
     * The native offsets of the single-byte fields outside the field tables.
     */
    private static final int @NotNull [] SINGLE_BYTE_FIELDS = {0xCA, 0xCB, 0xD6, 0xE9, 0x1B0};

    /**
     * The start of the field holding each native byte, or {@code -1}.
     */
    private static final int @NotNull [] FIELD_START = new int[SIZE];
    /**
     * The width of each field, indexed by its start.
     */
    private static final int @NotNull [] FIELD_WIDTH = new int[SIZE];

    static {
        int[] starts = {
                0x10, 0x20, 0x22, 0x30, 0x32, 0x34, 0x36, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3F, 0x40,
                0x42, 0x44, 0x48, 0x4C, 0x50, 0x54, 0x58, 0x5C, 0x60, 0x62, 0x63, 0x68, 0x69, 0x6A,
                0x6B, 0x6C, 0x70, 0x78, 0x7A, 0x88, 0x8C, 0x8E, 0xC8, 0xC9, 0xD4, 0xD5, 0xD7, 0xD8, 0xDA,
                0xDB, 0xDC, 0xDE, 0xE0, 0xE2, 0xE4, 0xE6, 0xE8, 0xEA, 0xEB, 0xEC, 0xEE, 0xF0, 0xF2,
                0xF4, 0xF6, 0xF8, 0xFA, 0xFC, 0xFE, 0x100, 0x102, 0x114, 0x115, 0x116, 0x118, 0x128, 0x12A,
                0x12C, 0x12E, 0x138, 0x140, 0x144, 0x145, 0x146, 0x147, 0x148, 0x150, 0x190, 0x191, 0x193, 0x194, 0x196,
                0x198, 0x19A, 0x19C, 0x19E, 0x1A0, 0x1A1, 0x1A2, 0x1A3, 0x1A4, 0x1A5, 0x1A6, 0x1A7, 0x1B1, 0x1B2,
                0x1B3, 0x1B4, 0x1B6, 0x1B7, 0x1B8, 0x1B9, 0x1BA, 0x1BB, 0x1BC, 0x1BD, 0x1BE, 0x1BF, 0x1C2, 0x1D0,
                0x1D2, 0x1D4, 0x1D8, 0x1DC, 0x1E0, 0x1E4, 0x1E5, 0x1E6, 0x1E7
        };
        int[] widths = {
                4, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 2, 1, 2,
                2, 4, 4, 4, 4, 4, 4, 4, 2, 1, 1, 1, 1, 1,
                1, 2, 4, 2, 2, 4, 2, 2, 1, 1, 1, 1, 1, 2, 1,
                1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2,
                2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2,
                2, 2, 1, 4, 1, 1, 1, 1, 4, 4, 1, 1, 1, 2, 2,
                2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2,
                2, 4, 4, 4, 4, 1, 1, 1, 1
        };

        Arrays.fill(FIELD_START, -1);

        for (int fieldIndex = 0; fieldIndex < starts.length; fieldIndex++) {
            FIELD_WIDTH[starts[fieldIndex]] = widths[fieldIndex];

            for (int byteIndex = starts[fieldIndex]; byteIndex < starts[fieldIndex] + widths[fieldIndex]; byteIndex++) {
                FIELD_START[byteIndex] = starts[fieldIndex];
            }
        }

        // The classic event bytes are one DWORD here; one of their bytes is the rank increase.
        FIELD_WIDTH[CLASSIC_EVENT_BYTES] = Integer.BYTES;

        for (int byteIndex = CLASSIC_EVENT_BYTES; byteIndex < CLASSIC_EVENT_BYTES + Integer.BYTES; byteIndex++) {
            FIELD_START[byteIndex] = CLASSIC_EVENT_BYTES;
        }

        // Single bytes: unread storage, the under-attack area mark, the computer spell target bits, the generation and the irradiating player.
        for (int single : SINGLE_BYTE_FIELDS) {
            FIELD_WIDTH[single] = Byte.BYTES;
            FIELD_START[single] = single;
        }
    }


    /**
     * Finds the start of the represented field that holds a native byte.
     *
     * @param offset the native byte offset
     * @return the start of the field holding the byte, or {@code -1} if none is represented
     */
    public static int getFieldStart(int offset) {
        return offset >= 0 && offset < SIZE ? FIELD_START[offset] : -1;
    }

    /**
     * Reads one byte of a represented field.
     *
     * @param unit   the unit
     * @param offset the native byte offset of a represented field byte
     * @return the unsigned byte
     */
    public static int readByte(@NotNull Unit unit, int offset) {
        int start = FIELD_START[offset];

        return readField(unit, start) >>> ((offset - start) * Byte.SIZE) & BYTE_MASK;
    }

    /**
     * Stores one byte of a represented field, keeping its other bytes.
     *
     * @param unit   the unit
     * @param offset the native byte offset of a represented field byte
     * @param value  the byte in the low eight bits
     */
    public static void writeByte(@NotNull Unit unit, int offset, int value) {
        int start = FIELD_START[offset];
        int width = FIELD_WIDTH[start];
        int shift = (offset - start) * Byte.SIZE;
        int merged = LittleEndianUtils.replaceByte(readField(unit, start), shift, value);

        writeField(unit, start, width == Integer.BYTES ? merged : merged & ((1 << (width * Byte.SIZE)) - 1));
    }

    private static int readField(@NotNull Unit unit, int offset) {
        return switch (offset) {
            case 0x10 -> unit.getHitPoints();
            case 0x20 -> unit.getMovementDestinationX();
            case 0x22 -> unit.getMovementDestinationY();
            case 0x30 -> unit.getMovementWaypointX();
            case 0x32 -> unit.getMovementWaypointY();
            case 0x34 -> unit.getDirectionTargetX();
            case 0x36 -> unit.getDirectionTargetY();
            case 0x38 -> unit.getMovementFlags();
            case 0x39 -> unit.getHeading();
            case 0x3A -> unit.getTurnRate();
            case 0x3B -> unit.getTargetHeading();
            case 0x3C -> unit.getFlingyType();
            case 0x3F -> unit.getMovementControl();
            case 0x40 -> unit.getPositionX();
            case 0x42 -> unit.getPositionY();
            case 0x44 -> unit.getFixedPositionX();
            case 0x48 -> unit.getFixedPositionY();
            case 0x4C -> unit.getTopSpeed();
            case 0x50 -> unit.getCurrentSpeed();
            case 0x54 -> unit.getNextSpeed();
            case 0x58 -> unit.getVelocityX();
            case 0x5C -> unit.getVelocityY();
            case 0x60 -> unit.getAcceleration();
            case 0x62 -> unit.getMovementDirection();
            case 0x63 -> unit.getDesiredMovementDirection();
            case 0x68 -> unit.getOwnerIndex();
            case 0x69 -> unit.getPrimaryOrder();
            case 0x6A -> unit.getPrimaryOrderState();
            case 0x6B -> unit.getOrderSignal();
            case 0x6C -> unit.getOrderTargetUnitType();
            case 0x70 -> unit.getCooldownTimers();
            case 0x78 -> unit.getOrderTargetX();
            case 0x7A -> unit.getOrderTargetY();
            case 0x88 -> unit.getShieldPoints();
            case 0x8C -> unit.getUnitId();
            case 0x8E -> unit.getSpareWord();
            case 0xC9 -> unit.getPrimaryOrderCountdown();
            case 0xD5 -> unit.getVisibilityUpdateCountdown();
            case 0xDB -> unit.getMovementState();
            case 0xDC -> unit.getFirstBuildQueueType();
            case 0xDE -> unit.getSecondBuildQueueType();
            case 0xE0 -> unit.getThirdBuildQueueType();
            case 0xE2 -> unit.getFourthBuildQueueType();
            case 0xE4 -> unit.getFifthBuildQueueType();
            case 0xE6 -> unit.getEnergy();
            case 0xE8 -> unit.getBuildQueueIndex();
            case 0xE9 -> unit.getGeneration();
            case 0xEA -> unit.getSecondaryOrder();
            case 0xEB -> unit.getDamageOverlayCount();
            case 0xEC -> unit.getConstructionHitPointIncrement();
            case 0xEE -> unit.getConstructionShieldIncrement();
            case 0xF0 -> unit.getRemainingBuildTime();
            case 0x114 -> unit.getResearchType();
            case 0x115 -> unit.getUpgradeType();
            case 0x116 -> unit.getLarvaSpawnCountdown();
            case 0x128 -> unit.getResources();
            case 0x12A -> unit.getSecondLarvaSideCount();
            case 0x12C -> unit.getThirdLarvaSideCount();
            case 0x12E -> unit.getFourthLarvaSideCount();
            case 0x138 -> unit.getResourceAreaIndex();
            case 0x140 -> unit.getStatus();
            case 0x146 -> unit.getSecondaryOrderState();
            case 0x147 -> unit.getMovementDestinationCountdown();
            case 0x190 -> unit.getPathRecalculationCountdown();
            case 0x191 -> unit.getPathCollisionFlags();
            case 0x194 -> unit.getTerrainCollisionLeft();
            case 0x196 -> unit.getTerrainCollisionTop();
            case 0x198 -> unit.getTerrainCollisionRight();
            case 0x19A -> unit.getTerrainCollisionBottom();
            case 0xF4 -> unit.getLoadedUnitHandle(0);
            case 0xF6 -> unit.getLoadedUnitHandle(1);
            case 0xF8 -> unit.getLoadedUnitHandle(2);
            case 0xFA -> unit.getLoadedUnitHandle(3);
            case 0xFC -> unit.getLoadedUnitHandle(4);
            case 0xFE -> unit.getLoadedUnitHandle(5);
            case 0x100 -> unit.getLoadedUnitHandle(6);
            case 0x102 -> unit.getLoadedUnitHandle(7);
            case 0x19E -> unit.getDefensiveMatrixHealth();
            case 0x1C2 -> unit.getLoadedUnitHandleExtensionBits();
            case 0x19C -> unit.getRemainingLifetime();
            case 0x1A1 -> unit.getSpeedIncreaseByte();
            case 0x1A2 -> unit.getSpeedDecreaseByte();
            case 0xC8 -> unit.getHighlightedOrderCount();
            case 0xD7 -> unit.getUserActionFlags();
            case 0xD8 -> unit.getCurrentButtonSet();
            case 0x193 -> unit.getHealingState();
            case 0x1A0 -> unit.getDefensiveMatrixTimer();
            case 0x1A3 -> unit.getLockdownTimer();
            case 0x1A4 -> unit.getIrradiateTimer();
            case 0x1A5 -> unit.getStasisTimer();
            case 0x1A6 -> unit.getPlagueTimer();
            case 0x1A7 -> unit.getStormTimer();
            case 0x1B4 -> unit.getMaelstromTimer();
            case 0x1B6 -> unit.getAcidSporeCount();
            case 0x1B7 -> unit.getAcidSporeTimer(0);
            case 0x1B8 -> unit.getAcidSporeTimer(1);
            case 0x1B9 -> unit.getAcidSporeTimer(2);
            case 0x1BA -> unit.getAcidSporeTimer(3);
            case 0x1BB -> unit.getAcidSporeTimer(4);
            case 0x1BC -> unit.getAcidSporeTimer(5);
            case 0x1BD -> unit.getAcidSporeTimer(6);
            case 0x1BE -> unit.getAcidSporeTimer(7);
            case 0x1BF -> unit.getAcidSporeTimer(8);
            case 0x118 -> unit.getCreepTimer();
            case 0x144 -> unit.getCarryingFlags();
            case 0xD4 -> unit.getLastAttackingPlayer();
            case CLASSIC_EVENT_BYTES -> unit.getClassicEventBytes();
            case 0xCA -> unit.getClassicUnusedByte();
            case 0xCB -> unit.getAlertMark();
            case 0xD6 -> unit.getSpellTargetFlags();
            case 0x1B0 -> unit.getIrradiateOwner();
            case 0x145 -> unit.getWireframeRandomizer();
            case 0xDA -> unit.getCloakCounter();
            case 0x1B1 -> unit.getParasiteFlags();
            case 0x1B3 -> unit.getBlindnessState();
            case 0xF2 -> unit.getPreviousVisibleHitPoints();
            case 0x148 -> unit.getDetectionFlags();
            case 0x150 -> unit.getSecondaryOrderPosition();
            case 0x1E4 -> unit.getRepulsionFlags();
            case 0x1E5 -> unit.getRepulsionDirection();
            case 0x1E6 -> unit.getRepulsionCellX();
            case 0x1E7 -> unit.getRepulsionCellY();
            case 0x1B2 -> unit.getStatusCycleElapsed();
            case 0x1D0 -> unit.getAirStrength();
            case 0x1D2 -> unit.getGroundStrength();
            case 0x1D4 -> unit.getMinimumHorizontalEdgeIndex();
            case 0x1D8 -> unit.getMaximumHorizontalEdgeIndex();
            case 0x1DC -> unit.getMinimumVerticalEdgeIndex();
            case 0x1E0 -> unit.getMaximumVerticalEdgeIndex();
            default -> throw Exceptions.newIllegalArgumentException("Unknown native unit field: {0}", offset);
        };
    }

    private static void writeField(@NotNull Unit unit, int offset, int value) {
        switch (offset) {
            case 0x10 -> unit.setHitPoints(value);
            case 0x20 -> unit.setMovementDestinationX(value);
            case 0x22 -> unit.setMovementDestinationY(value);
            case 0x30 -> unit.setMovementWaypointX(value);
            case 0x32 -> unit.setMovementWaypointY(value);
            case 0x34 -> unit.setDirectionTargetX(value);
            case 0x36 -> unit.setDirectionTargetY(value);
            case 0x38 -> unit.setMovementFlags(value);
            case 0x39 -> unit.setHeading(value);
            case 0x3A -> unit.setTurnRate(value);
            case 0x3B -> unit.setTargetHeading(value);
            case 0x3C -> unit.setFlingyType(value);
            case 0x3F -> unit.setMovementControl(value);
            case 0x40 -> unit.setPositionX(value);
            case 0x42 -> unit.setPositionY(value);
            case 0x44 -> unit.setFixedPositionX(value);
            case 0x48 -> unit.setFixedPositionY(value);
            case 0x4C -> unit.setTopSpeed(value);
            case 0x50 -> unit.setCurrentSpeed(value);
            case 0x54 -> unit.setNextSpeed(value);
            case 0x58 -> unit.setVelocityX(value);
            case 0x5C -> unit.setVelocityY(value);
            case 0x60 -> unit.setAcceleration(value);
            case 0x62 -> unit.setMovementDirection(value);
            case 0x63 -> unit.setDesiredMovementDirection(value);
            case 0x68 -> unit.setOwnerIndex(value);
            case 0x69 -> unit.setPrimaryOrder(value);
            case 0x6A -> unit.setPrimaryOrderState(value);
            case 0x6B -> unit.setOrderSignal(value);
            case 0x6C -> unit.setOrderTargetUnitType(value);
            case 0x70 -> unit.setCooldownTimers(value);
            case 0x78 -> unit.setOrderTargetX(value);
            case 0x7A -> unit.setOrderTargetY(value);
            case 0x88 -> unit.setShieldPoints(value);
            case 0x8C -> unit.setUnitId(value);
            case 0x8E -> unit.setSpareWord(value);
            case 0xC9 -> unit.setPrimaryOrderCountdown(value);
            case 0xD5 -> unit.setVisibilityUpdateCountdown(value);
            case 0xDB -> unit.setMovementState(value);
            case 0xDC -> unit.setFirstBuildQueueType(value);
            case 0xDE -> unit.setSecondBuildQueueType(value);
            case 0xE0 -> unit.setThirdBuildQueueType(value);
            case 0xE2 -> unit.setFourthBuildQueueType(value);
            case 0xE4 -> unit.setFifthBuildQueueType(value);
            case 0xE6 -> unit.setEnergy(value);
            case 0xE8 -> unit.setBuildQueueIndex(value);
            case 0xE9 -> unit.setGeneration(value);
            case 0xEA -> unit.setSecondaryOrderByte(value);
            case 0xEB -> unit.setDamageOverlayCount(value);
            case 0xEC -> unit.setConstructionHitPointIncrement(value);
            case 0xEE -> unit.setConstructionShieldIncrement(value);
            case 0xF0 -> unit.setRemainingBuildTime(value);
            case 0x114 -> unit.setResearchType(value);
            case 0x115 -> unit.setUpgradeType(value);
            case 0x116 -> unit.setLarvaSpawnCountdown(value);
            case 0x128 -> unit.setResources(value);
            case 0x12A -> unit.setSecondLarvaSideCount(value);
            case 0x12C -> unit.setThirdLarvaSideCount(value);
            case 0x12E -> unit.setFourthLarvaSideCount(value);
            case 0x138 -> unit.setResourceAreaIndex(value);
            case 0x140 -> unit.setStatus(value);
            case 0x146 -> unit.setSecondaryOrderState(value);
            case 0x147 -> unit.setMovementDestinationCountdown(value);
            case 0x190 -> unit.setPathRecalculationCountdown(value);
            case 0x191 -> unit.setPathCollisionFlags(value);
            case 0x194 -> unit.setTerrainCollisionLeft(value);
            case 0x196 -> unit.setTerrainCollisionTop(value);
            case 0x198 -> unit.setTerrainCollisionRight(value);
            case 0x19A -> unit.setTerrainCollisionBottom(value);
            case 0xF4 -> unit.setLoadedUnitHandle(0, value);
            case 0xF6 -> unit.setLoadedUnitHandle(1, value);
            case 0xF8 -> unit.setLoadedUnitHandle(2, value);
            case 0xFA -> unit.setLoadedUnitHandle(3, value);
            case 0xFC -> unit.setLoadedUnitHandle(4, value);
            case 0xFE -> unit.setLoadedUnitHandle(5, value);
            case 0x100 -> unit.setLoadedUnitHandle(6, value);
            case 0x102 -> unit.setLoadedUnitHandle(7, value);
            case 0x19E -> unit.setDefensiveMatrixHealth(value);
            case 0x1C2 -> unit.setLoadedUnitHandleExtensionBits(value);
            case 0x19C -> unit.setRemainingLifetime(value);
            case 0x1A1 -> unit.setSpeedIncreaseByte(value);
            case 0x1A2 -> unit.setSpeedDecreaseByte(value);
            case 0xC8 -> unit.setHighlightedOrderCount(value);
            case 0xD7 -> unit.setUserActionFlags(value);
            case 0xD8 -> unit.setCurrentButtonSet(value);
            case 0x193 -> unit.setHealingState(value);
            case 0x1A0 -> unit.setDefensiveMatrixTimer(value);
            case 0x1A3 -> unit.setLockdownTimer(value);
            case 0x1A4 -> unit.setIrradiateTimer(value);
            case 0x1A5 -> unit.setStasisTimer(value);
            case 0x1A6 -> unit.setPlagueTimer(value);
            case 0x1A7 -> unit.setStormTimer(value);
            case 0x1B4 -> unit.setMaelstromTimer(value);
            case 0x1B6 -> unit.setAcidSporeCount(value);
            case 0x1B7 -> unit.setAcidSporeTimer(0, value);
            case 0x1B8 -> unit.setAcidSporeTimer(1, value);
            case 0x1B9 -> unit.setAcidSporeTimer(2, value);
            case 0x1BA -> unit.setAcidSporeTimer(3, value);
            case 0x1BB -> unit.setAcidSporeTimer(4, value);
            case 0x1BC -> unit.setAcidSporeTimer(5, value);
            case 0x1BD -> unit.setAcidSporeTimer(6, value);
            case 0x1BE -> unit.setAcidSporeTimer(7, value);
            case 0x1BF -> unit.setAcidSporeTimer(8, value);
            case 0x118 -> unit.setCreepTimer(value);
            case 0x144 -> unit.setCarryingFlags(value);
            case 0xD4 -> unit.setLastAttackingPlayer(value);
            case CLASSIC_EVENT_BYTES -> unit.setClassicEventBytes(value);
            case 0xCA -> unit.setClassicUnusedByte(value);
            case 0xCB -> unit.setAlertMark(value);
            case 0xD6 -> unit.setSpellTargetFlags(value);
            case 0x1B0 -> unit.setIrradiateOwner(value & BYTE_MASK);
            case 0x145 -> unit.setWireframeRandomizer(value);
            case 0xDA -> unit.setCloakCounter(value);
            case 0x1B1 -> unit.setParasiteFlags(value);
            case 0x1B3 -> unit.setBlindnessState(value);
            case 0xF2 -> unit.setPreviousVisibleHitPoints(value);
            case 0x148 -> unit.setDetectionFlags(value);
            case 0x150 -> unit.setSecondaryOrderPosition(value);
            case 0x1E4 -> unit.setRepulsionFlags(value);
            case 0x1E5 -> unit.setRepulsionDirection(value);
            case 0x1E6 -> unit.setRepulsionCellX(value);
            case 0x1E7 -> unit.setRepulsionCellY(value);
            case 0x1B2 -> unit.setStatusCycleElapsed(value);
            case 0x1D0 -> unit.setAirStrength(value);
            case 0x1D2 -> unit.setGroundStrength(value);
            case 0x1D4 -> unit.setMinimumHorizontalEdgeIndex(value);
            case 0x1D8 -> unit.setMaximumHorizontalEdgeIndex(value);
            case 0x1DC -> unit.setMinimumVerticalEdgeIndex(value);
            case 0x1E0 -> unit.setMaximumVerticalEdgeIndex(value);
            default -> throw Exceptions.newIllegalArgumentException("Unknown native unit field: {0}", offset);
        }
    }


    private NativeUnitFields() {
    }
}

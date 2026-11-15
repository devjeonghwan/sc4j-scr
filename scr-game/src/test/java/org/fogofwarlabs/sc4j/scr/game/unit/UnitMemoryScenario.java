package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Interprets retained test images through their mapped unit fields and opaque bytes.
 */
public final class UnitMemoryScenario {
    /**
     * The image offsets of the owner BYTE, the primary order BYTE, the unit type WORD, the first build queue WORD and the status flags DWORD in
     * the native classic unit memory, which the tests write to build their input units.
     */
    public static final int OWNER_OFFSET = 0x68;
    public static final int PRIMARY_ORDER_OFFSET = 0x69;
    public static final int UNIT_TYPE_OFFSET = 0x8C;
    public static final int BUILD_QUEUE_OFFSET = 0xDC;
    public static final int STATUS_OFFSET = 0x140;

    private static final int SPRITE_REFERENCE_OFFSET = 0x18;
    private static final int MOVEMENT_TARGET_REFERENCE_OFFSET = 0x28;
    private static final int ORDER_TARGET_REFERENCE_OFFSET = 0x80;
    private static final int SUBUNIT_REFERENCE_OFFSET = 0xA0;
    private static final int FIRST_ORDER_REFERENCE_OFFSET = 0xA8;
    private static final int LAST_ORDER_REFERENCE_OFFSET = 0xB0;
    private static final int AUTOMATIC_TARGET_REFERENCE_OFFSET = 0xB8;
    private static final int PARENT_REFERENCE_OFFSET = 0xC0;
    private static final int CARRIED_POWERUP_REFERENCE_OFFSET = 0x108;
    private static final int CURRENT_BUILD_REFERENCE_OFFSET = 0x158;
    private static final int PATH_REFERENCE_OFFSET = 0x188;
    private static final int TRAILING_CAPTURE_REFERENCE_OFFSET = 0x1C8;
    private static final int BYTE_MASK = 0xFF;
    private static final long REFERENCE_BYTE_MASK = 0xFFL;

    private static final @NotNull Map<Unit, UnitMemoryScenario> CAPTURES = new WeakHashMap<>();

    /**
     * Returns the independently owned scenario associated with a test unit.
     *
     * @param unit the test unit
     * @return the associated scenario
     */
    public static synchronized @NotNull UnitMemoryScenario of(@NotNull Unit unit) {
        return CAPTURES.computeIfAbsent(unit, UnitMemoryScenario::new);
    }

    private final byte @NotNull [] opaque;
    private final @NotNull Map<Integer, Long> references;

    private final @NotNull WeakReference<Unit> unit;

    private UnitMemoryScenario(@NotNull Unit unit) {
        this.opaque     = new byte[UnitMemoryCheck.UNMAPPED_SIZE];
        this.references = new HashMap<>();

        this.unit = new WeakReference<>(unit);
    }

    /**
     * Reads the unsigned byte at the supplied image offset.
     *
     * @param offset the image byte offset
     * @return the field value
     * @throws IllegalStateException     if the scenario's weak target was collected
     * @throws IndexOutOfBoundsException if the offset cannot hold the operation's width inside the image
     */
    public int readMemoryUnsignedByte(int offset) {
        return UnitMemoryCheck.read(this.requireUnit(), this.opaque, offset, Byte.BYTES);
    }

    /**
     * Reads the unsigned word at the supplied image offset.
     *
     * @param offset the image byte offset
     * @return the field value
     * @throws IllegalStateException     if the scenario's weak target was collected
     * @throws IndexOutOfBoundsException if the offset cannot hold the operation's width inside the image
     */
    public int readMemoryUnsignedWord(int offset) {
        return UnitMemoryCheck.read(this.requireUnit(), this.opaque, offset, Short.BYTES);
    }

    /**
     * Reads the integer at the supplied image offset.
     *
     * @param offset the image byte offset
     * @return the field value
     * @throws IllegalStateException     if the scenario's weak target was collected
     * @throws IndexOutOfBoundsException if the offset cannot hold the operation's width inside the image
     */
    public int readMemoryInteger(int offset) {
        return UnitMemoryCheck.read(this.requireUnit(), this.opaque, offset, Integer.BYTES);
    }

    /**
     * Writes the byte at the supplied image offset.
     *
     * @param offset the image byte offset
     * @param value  the value to write
     * @throws IllegalStateException     if the scenario's weak target was collected
     * @throws IndexOutOfBoundsException if the offset cannot hold the operation's width inside the image
     */
    public void writeMemoryByte(int offset, int value) {
        UnitMemoryCheck.write(this.requireUnit(), this.opaque, offset, Byte.BYTES, value);
    }

    /**
     * Writes the word at the supplied image offset.
     *
     * @param offset the image byte offset
     * @param value  the value to write
     * @throws IllegalStateException     if the scenario's weak target was collected
     * @throws IndexOutOfBoundsException if the offset cannot hold the operation's width inside the image
     */
    public void writeMemoryWord(int offset, int value) {
        UnitMemoryCheck.write(this.requireUnit(), this.opaque, offset, Short.BYTES, value);
    }

    /**
     * Writes the integer at the supplied image offset.
     *
     * @param offset the image byte offset
     * @param value  the value to write
     * @throws IllegalStateException     if the scenario's weak target was collected
     * @throws IndexOutOfBoundsException if the offset cannot hold the operation's width inside the image
     */
    public void writeMemoryInteger(int offset, int value) {
        UnitMemoryCheck.write(this.requireUnit(), this.opaque, offset, Integer.BYTES, value);
    }

    /**
     * Clears the supplied image byte range.
     *
     * @param offset the first image byte offset
     * @param length the number of bytes to clear
     * @throws IllegalStateException     if the scenario's weak target was collected
     * @throws IndexOutOfBoundsException if the computed range is outside the image
     * @throws IllegalArgumentException  if the computed range end precedes its start
     */
    public void clearMemoryBytes(int offset, int length) {
        UnitMemoryCheck.clear(this.requireUnit(), this.opaque, offset, length);
    }

    /**
     * Exports modeled scalar values only; opaque input bytes and process addresses are never reinserted.
     *
     * @return the independent image of modeled scalar values
     * @throws IllegalStateException if the scenario's weak target was collected
     */
    public byte @NotNull [] exportMemoryBytes() {
        byte[] result = new byte[UnitMemoryCheck.SIZE];

        for (int offset = 0; offset < result.length; offset++) {
            if (UnitMemoryCheck.isScalarByte(offset)) {
                result[offset] = (byte) this.readMemoryUnsignedByte(offset);
            }
        }

        Unit target = this.requireUnit();

        if (UnitMemoryCheck.isVulture(target.getUnitId())) {
            result[CARRIED_POWERUP_REFERENCE_OFFSET] = (byte) target.getSpiderMineCount();
        }

        return result;
    }

    /**
     * Exports the scenario's full retained image for codec-only roundtrip tests.
     *
     * @return the independent full image
     * @throws IllegalStateException if the scenario's weak target was collected
     */
    public byte @NotNull [] exportCaptureBytes() {
        return UnitMemoryCheck.createSnapshot(this.requireUnit(), this.opaque);
    }

    int getCapturedMineCount() {
        return (int) (this.references.getOrDefault(CARRIED_POWERUP_REFERENCE_OFFSET, 0L) & BYTE_MASK);
    }

    long readReference(int start) {
        long value = this.references.getOrDefault(start, 0L);
        Unit target = this.requireUnit();

        return start == CARRIED_POWERUP_REFERENCE_OFFSET && UnitMemoryCheck.isVulture(target.getUnitId())
               ? value & ~REFERENCE_BYTE_MASK | target.getSpiderMineCount()
               : value;
    }

    void writeReference(int start, long value) {
        Unit target = this.requireUnit();

        this.references.put(start, value);

        switch (start) {
            case SPRITE_REFERENCE_OFFSET -> target.setSprite(null);
            case MOVEMENT_TARGET_REFERENCE_OFFSET -> target.setMovementTargetUnit(null);
            case ORDER_TARGET_REFERENCE_OFFSET -> target.setOrderTargetUnit(null);
            case SUBUNIT_REFERENCE_OFFSET -> target.setSubunit(null);
            case FIRST_ORDER_REFERENCE_OFFSET -> target.setFirstOrder(null);
            case LAST_ORDER_REFERENCE_OFFSET -> target.setLastOrder(null);
            case AUTOMATIC_TARGET_REFERENCE_OFFSET -> target.setAutomaticTargetUnit(null);
            case PARENT_REFERENCE_OFFSET -> target.setParentUnit(null);
            case CARRIED_POWERUP_REFERENCE_OFFSET -> {
                target.setCarriedPowerup(null);

                if (UnitMemoryCheck.isVulture(target.getUnitId())) {
                    target.setSpiderMineCount((int) value);
                }
            }
            case CURRENT_BUILD_REFERENCE_OFFSET -> target.setCurrentBuildUnit(null);
            case PATH_REFERENCE_OFFSET -> target.setPathIndex(-1);
            case TRAILING_CAPTURE_REFERENCE_OFFSET -> {
            }
            default -> throw Exceptions.newIllegalArgumentException("Unknown capture reference: {0}", start);
        }
    }

    /**
     * Requires the target at a reached image operation.
     *
     * @return the retained target unit
     * @throws IllegalStateException if the scenario's weak target was collected
     */
    private @NotNull Unit requireUnit() {
        Unit target = this.unit.get();

        if (target == null) {
            throw Exceptions.newIllegalStateException("The captured unit is no longer available.");
        }

        return target;
    }
}

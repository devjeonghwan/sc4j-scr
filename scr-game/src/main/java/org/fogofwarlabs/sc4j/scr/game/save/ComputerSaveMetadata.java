package org.fogofwarlabs.sc4j.scr.game.save;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * Retains the uninterpreted BYTE and WORD payloads of computer saved records for their fixed typed pool identities.
 * <p>
 * These values are saved-format metadata. They are preserved through represented load, save and pool reuse without
 * interpreting them as gameplay state or retaining a second memory image.
 */
public final class ComputerSaveMetadata {
    private static final int SAVED_TOWN_SIZE = 0x1CC;
    private static final int SAVED_TOWN_BYTE = 0x1F;
    private static final int SAVED_MILITARY_SIZE = 0x14;
    private static final int SAVED_MILITARY_BYTE = 0x09;
    private static final int SAVED_MILITARY_WORD = 0x0A;
    private static final int SAVED_GUARD_SIZE = 0x20;
    private static final int SAVED_GUARD_WORD = 0x0A;
    private static final int SAVED_BUILDING_SIZE = 0x2C;
    private static final int SAVED_BUILDING_WORD = 0x0E;

    private final byte @NotNull [] townBytes;
    private final byte @NotNull [] militaryBytes;
    private final short @NotNull [] militaryWords;
    private final short @NotNull [] guardWords;
    private final short @NotNull [] buildingWords;

    /**
     * Creates a {@link ComputerSaveMetadata} instance for the existing typed pool capacities.
     *
     * @param townCount     the town capacity
     * @param militaryCount the military record capacity
     * @param guardCount    the guard record capacity
     * @param buildingCount the building record capacity
     */
    public ComputerSaveMetadata(int townCount, int militaryCount, int guardCount, int buildingCount) {
        this.townBytes     = new byte[townCount];
        this.militaryBytes = new byte[militaryCount];
        this.militaryWords = new short[militaryCount];
        this.guardWords    = new short[guardCount];
        this.buildingWords = new short[buildingCount];
    }

    /**
     * Reads the payload of a successfully restored town record.
     *
     * @param data   the saved section, little-endian
     * @param offset the saved record offset
     * @param index  the typed pool index
     */
    public void readTown(@NotNull ByteBuffer data, int offset, int index) {
        this.townBytes[index] = data.get(offset + SAVED_TOWN_BYTE);
    }

    /**
     * Writes the retained town payloads after the section's ordinary fields have been written.
     *
     * @param data the saved section, little-endian
     */
    public void writeTowns(@NotNull ByteBuffer data) {
        for (int index = 0; index < this.townBytes.length; index++) {
            data.put(index * SAVED_TOWN_SIZE + SAVED_TOWN_BYTE, this.townBytes[index]);
        }
    }

    /**
     * Clears the payload when a free town's ordinary fields are cleared for saving.
     *
     * @param index the typed pool index
     */
    public void clearFreeTown(int index) {
        this.townBytes[index] = 0;
    }

    /**
     * Reads the payloads of a successfully restored military record.
     *
     * @param data   the saved section, little-endian
     * @param offset the saved record offset
     * @param index  the typed pool index
     */
    public void readMilitary(@NotNull ByteBuffer data, int offset, int index) {
        this.militaryBytes[index] = data.get(offset + SAVED_MILITARY_BYTE);
        this.militaryWords[index] = data.getShort(offset + SAVED_MILITARY_WORD);
    }

    /**
     * Writes the retained military payloads after the section's ordinary fields have been written.
     *
     * @param data the saved section, little-endian
     */
    public void writeMilitary(@NotNull ByteBuffer data) {
        for (int index = 0; index < this.militaryBytes.length; index++) {
            int offset = index * SAVED_MILITARY_SIZE;

            data.put(offset + SAVED_MILITARY_BYTE, this.militaryBytes[index]);
            data.putShort(offset + SAVED_MILITARY_WORD, this.militaryWords[index]);
        }
    }

    /**
     * Clears the payloads when a free military record's ordinary fields are cleared for saving.
     *
     * @param index the typed pool index
     */
    public void clearFreeMilitary(int index) {
        this.militaryBytes[index] = 0;
        this.militaryWords[index] = 0;
    }

    /**
     * Reads the payload of a successfully restored guard record.
     *
     * @param data   the saved section, little-endian
     * @param offset the saved record offset
     * @param index  the typed pool index
     */
    public void readGuard(@NotNull ByteBuffer data, int offset, int index) {
        this.guardWords[index] = data.getShort(offset + SAVED_GUARD_WORD);
    }

    /**
     * Writes the retained guard payloads, including free records, after the section's ordinary fields have been written.
     *
     * @param data the saved section, little-endian
     */
    public void writeGuards(@NotNull ByteBuffer data) {
        for (int index = 0; index < this.guardWords.length; index++) {
            data.putShort(index * SAVED_GUARD_SIZE + SAVED_GUARD_WORD, this.guardWords[index]);
        }
    }

    /**
     * Reads the payload of a successfully restored building record.
     *
     * @param data   the saved section, little-endian
     * @param offset the saved record offset
     * @param index  the typed pool index
     */
    public void readBuilding(@NotNull ByteBuffer data, int offset, int index) {
        this.buildingWords[index] = data.getShort(offset + SAVED_BUILDING_WORD);
    }

    /**
     * Writes the retained building payloads, including free records, after the section's ordinary fields have been written.
     *
     * @param data the saved section, little-endian
     */
    public void writeBuildings(@NotNull ByteBuffer data) {
        for (int index = 0; index < this.buildingWords.length; index++) {
            data.putShort(index * SAVED_BUILDING_SIZE + SAVED_BUILDING_WORD, this.buildingWords[index]);
        }
    }
}

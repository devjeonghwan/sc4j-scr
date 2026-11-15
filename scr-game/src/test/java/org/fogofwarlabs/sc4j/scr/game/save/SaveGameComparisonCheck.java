package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Compares serialized saved-game parts byte by byte, by run and by record field, and formats the differences for a report.
 */
public final class SaveGameComparisonCheck {
    private static final int REPORTED_DIFFERENCES = 40;
    private static final int REPORTED_RUNS = REPORTED_DIFFERENCES * 2;
    private static final int HEXADECIMAL_WINDOW_SIZE = 16;
    private static final int FIELD_LABEL_PAIR_SIZE = 2;

    /**
     * Creates an ordered offset-to-label map from alternating pairs.
     *
     * @param pairs the alternating offset and label pairs
     * @return the ordered offset-to-label map
     */
    public static @NotNull Map<Integer, String> createFieldLabels(@NotNull Object @NotNull ... pairs) {
        Map<Integer, String> fields = new TreeMap<>();

        for (int index = 0; index < pairs.length; index += FIELD_LABEL_PAIR_SIZE) {
            fields.put((Integer) pairs[index], (String) pairs[index + 1]);
        }

        return fields;
    }

    /**
     * Creates a {@link SaveFileReader} instance over the bytes of one written section.
     *
     * @param section the section to serialize
     * @param context the serialization context
     * @return the created {@link SaveFileReader} instance
     */
    public static @NotNull SaveFileReader createSectionReader(@NotNull SaveSection section, @NotNull SaveGameContext context) {
        SaveFileWriter output = new SaveFileWriter();

        section.write(context, output);

        return new SaveFileReader(output.toByteArray(), 0);
    }

    /**
     * Reads the WORD count and its fixed-size decoded records.
     *
     * @param input     the serialized input
     * @param entrySize the recorded entry width
     * @return the decoded fixed-size records, or an empty array for zero count
     */
    static byte @NotNull [] readCountedRecords(@NotNull SaveFileReader input, int entrySize) {
        int count = input.readWord();

        return count == 0 ? new byte[0] : input.readSection(count * entrySize);
    }

    /**
     * Prefixes fixed-size records with their WORD indexes for comparison.
     *
     * @param records    the records
     * @param recordSize the recorded record width
     * @return the records prefixed with their WORD indexes
     */
    public static byte @NotNull [] addRecordIndexes(byte @NotNull [] records, int recordSize) {
        int count = records.length / recordSize;
        ByteBuffer entries = ByteBuffer.allocate(count * (recordSize + Short.BYTES)).order(ByteOrder.LITTLE_ENDIAN);

        for (int index = 0; index < count; index++) {
            entries.putShort((short) index);
            entries.put(records, index * recordSize, recordSize);
        }

        return entries.array();
    }

    /**
     * Reports the runs of differing bytes of a flat part, by offset.
     *
     * @param name   the part name
     * @param ours   the simulated serialized bytes
     * @param theirs the reference serialized bytes
     * @param report the diagnostic output
     */
    static void compareRuns(@NotNull String name, byte @NotNull [] ours, byte @NotNull [] theirs, @NotNull StringBuilder report) {
        List<String> runs = new ArrayList<>();
        int differing = 0;
        int length = Math.min(ours.length, theirs.length);

        for (int offset = 0; offset < length; ) {
            if (ours[offset] == theirs[offset]) {
                offset++;

                continue;
            }

            int start = offset;

            while (offset < length && (ours[offset] != theirs[offset] || offset + 1 < length && ours[offset + 1] != theirs[offset + 1])) {
                offset++;
            }

            differing += offset - start;
            runs.add("+" + Integer.toHexString(start) + ".." + Integer.toHexString(offset) +
                     " native " + formatHexadecimalWindow(theirs, start) + " ours " + formatHexadecimalWindow(ours, start));
        }

        report.append(System.lineSeparator()).append("  ").append(name).append(": ").append(differing).append(" differing bytes in ")
              .append(runs.size()).append(" runs");

        for (int index = 0; index < runs.size() && index < REPORTED_RUNS; index++) {
            report.append(System.lineSeparator()).append("    ").append(runs.get(index));
        }
    }

    /**
     * Reports the byte counts and first differing byte window.
     *
     * @param name   the part name
     * @param ours   the simulated serialized bytes
     * @param theirs the reference serialized bytes
     * @param report the diagnostic output
     */
    public static void compareBytes(@NotNull String name, byte @NotNull [] ours, byte @NotNull [] theirs, @NotNull StringBuilder report) {
        int differing = 0;
        int first = -1;

        for (int offset = 0; offset < Math.min(ours.length, theirs.length); offset++) {
            if (ours[offset] != theirs[offset]) {
                differing++;

                if (first < 0) {
                    first = offset;
                }
            }
        }

        report.append(System.lineSeparator()).append("  ").append(name).append(": ")
              .append(ours.length == theirs.length && differing == 0
                      ? "identical"
                      : "native " + theirs.length + " bytes, ours " + ours.length + ", " +
                        differing + " differing from +" + Integer.toHexString(Math.max(first, 0)) +
                        (first < 0
                         ? ""
                         : " native " + formatHexadecimalWindow(theirs, first) + " ours " + formatHexadecimalWindow(ours, first)));
    }

    /**
     * Formats a bounded byte window as hexadecimal text.
     *
     * @param data   the byte contents
     * @param offset the first byte offset
     * @return the hexadecimal window text
     */
    static @NotNull String formatHexadecimalWindow(byte @NotNull [] data, int offset) {
        byte[] window = Arrays.copyOfRange(data, offset, Math.min(data.length, offset + HEXADECIMAL_WINDOW_SIZE));
        StringBuilder text = new StringBuilder();

        for (byte value : window) {
            text.append(String.format("%02x", Byte.toUnsignedInt(value)));
        }

        return text.toString();
    }

    /**
     * Compares the entries of a pool part by slot (a WORD or DWORD at the entry start) and reports the differing bytes by field.
     *
     * @param name      the part name
     * @param ours      the simulated serialized entries
     * @param theirs    the reference serialized entries
     * @param entrySize the recorded entry width, including the slot
     * @param slotSize  the serialized slot-prefix width
     * @param fields    the field labels by entry offset
     * @param report    the diagnostic output
     */
    public static void compareRecords(@NotNull String name,
                                      byte @NotNull [] ours, byte @NotNull [] theirs,
                                      int entrySize, int slotSize,
                                      @NotNull Map<Integer, String> fields,
                                      @NotNull StringBuilder report) {
        TreeMap<Integer, String> fieldStarts = new TreeMap<>(fields);
        Map<Integer, byte[]> ourEntries = readRecordsBySlot(ours, entrySize, slotSize);
        Map<Integer, byte[]> theirEntries = readRecordsBySlot(theirs, entrySize, slotSize);
        List<String> missing = new ArrayList<>();
        List<String> differences = new ArrayList<>();
        Map<String, Integer> fieldCounts = new TreeMap<>();

        for (Map.Entry<Integer, byte[]> entry : ourEntries.entrySet()) {
            if (!theirEntries.containsKey(entry.getKey())) {
                missing.add("slot " + entry.getKey() + " extra in ours: " + formatHexadecimalWindow(entry.getValue(), 0));
                fieldCounts.merge("<extra>", 1, Integer::sum);
            }
        }

        for (Map.Entry<Integer, byte[]> entry : theirEntries.entrySet()) {
            byte[] ourEntry = ourEntries.get(entry.getKey());

            if (ourEntry == null) {
                missing.add("slot " + entry.getKey() + " missing in ours: " + formatHexadecimalWindow(entry.getValue(), 0));
                fieldCounts.merge("<missing>", 1, Integer::sum);

                continue;
            }

            for (int offset = 0; offset < entrySize; offset++) {
                if (ourEntry[offset] == entry.getValue()[offset]) {
                    continue;
                }

                Integer start = fieldStarts.floorKey(offset);
                String field = start == null ? "+" + Integer.toHexString(offset) : fieldStarts.get(start);

                fieldCounts.merge(field, 1, Integer::sum);
                differences.add("slot " + entry.getKey() + " +" + Integer.toHexString(offset - slotSize) + " (" + field + "): native " +
                                Byte.toUnsignedInt(entry.getValue()[offset]) + " ours " + Byte.toUnsignedInt(ourEntry[offset]));
            }
        }

        report.append(System.lineSeparator()).append("  ").append(name).append(": native ").append(theirEntries.size())
              .append(" ours ").append(ourEntries.size()).append(", differing bytes by field ").append(fieldCounts);
        missing.addAll(differences);

        for (int index = 0; index < missing.size() && index < REPORTED_DIFFERENCES; index++) {
            report.append(System.lineSeparator()).append("    ").append(missing.get(index));
        }
    }

    /**
     * Decodes slot-prefixed records into insertion-ordered entries.
     *
     * @param data      the byte contents
     * @param entrySize the recorded entry width
     * @param slotSize  the serialized slot-prefix width
     * @return the records keyed by their decoded slot
     */
    private static @NotNull Map<Integer, byte[]> readRecordsBySlot(byte @NotNull [] data, int entrySize, int slotSize) {
        Map<Integer, byte[]> entries = new LinkedHashMap<>();

        for (int offset = 0; offset + entrySize <= data.length; offset += entrySize) {
            int slot = slotSize == Integer.BYTES ? LittleEndianUtils.readInt32(data, offset) : LittleEndianUtils.readUnsigned16(data, offset);

            entries.put(slot, Arrays.copyOfRange(data, offset, offset + entrySize));
        }

        return entries;
    }


    /**
     * Creates a {@link SaveGameComparisonCheck} instance.
     */
    private SaveGameComparisonCheck() {
    }
}

package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads classic dialog-sheet frame offsets and dimensions for shared interface painters. Missing headers and out-of-range frames have no record.
 */
public final class SheetFrameRecords {
    /**
     * The horizontal offset slot of a decoded classic sheet frame record.
     */
    public static final int X_INDEX = 0;
    /**
     * The vertical offset slot of a decoded classic sheet frame record.
     */
    public static final int Y_INDEX = 1;
    /**
     * The width slot of a decoded classic sheet frame record.
     */
    public static final int WIDTH_INDEX = 2;
    /**
     * The height slot of a decoded classic sheet frame record.
     */
    public static final int HEIGHT_INDEX = 3;

    private static final int HEADER_SIZE = 6;
    private static final int RECORD_SIZE = 8;
    private static final int FIELDS = 4;
    private static final @NotNull String DDS_SUFFIX = ".dds.grp";
    private static final @NotNull String CLASSIC_SUFFIX = ".grp";

    private final @NotNull CascStorage storage;
    private final @NotNull Map<String, int[]> records;

    /**
     * Creates a {@link SheetFrameRecords} instance.
     *
     * @param storage the installed game storage
     */
    public SheetFrameRecords(@NotNull CascStorage storage) {
        this.storage = storage;
        this.records = new HashMap<>();
    }

    /**
     * Reads the classic record of a sheet frame.
     *
     * @param ddsPath the sheet path, for example {@code glue/palnl/dlg.dds.grp}
     * @param frame   the frame index
     * @return the frame record {x offset, y offset, width, height}, or {@code null} when the sheet has no classic header or no such frame
     */
    public int @Nullable [] getFrameRecord(@NotNull String ddsPath, int frame) {
        int[] all = this.records.computeIfAbsent(ddsPath.replace('\\', '/'), this::read);

        if (frame < 0 || FIELDS * frame + FIELDS > all.length) {
            return null;
        }

        return new int[]{all[FIELDS * frame + X_INDEX], all[FIELDS * frame + Y_INDEX],
                         all[FIELDS * frame + WIDTH_INDEX], all[FIELDS * frame + HEIGHT_INDEX]};
    }


    private int @NotNull [] read(@NotNull String ddsPath) {
        if (!ddsPath.toLowerCase(Locale.ROOT).endsWith(DDS_SUFFIX)) {
            return new int[0];
        }

        String classicPath = ddsPath.substring(0, ddsPath.length() - DDS_SUFFIX.length()) + CLASSIC_SUFFIX;

        try {
            byte[] data = this.storage.readFile(classicPath);
            int count = Byte.toUnsignedInt(data[0]) | Byte.toUnsignedInt(data[1]) << Byte.SIZE;
            int[] all = new int[FIELDS * count];

            for (int frame = 0; frame < count && HEADER_SIZE + RECORD_SIZE * frame + FIELDS <= data.length; frame++) {
                for (int field = 0; field < FIELDS; field++) {
                    all[FIELDS * frame + field] = Byte.toUnsignedInt(data[HEADER_SIZE + RECORD_SIZE * frame + field]);
                }
            }

            return all;
        } catch (IOException | RuntimeException exception) {
            return new int[0];
        }
    }
}

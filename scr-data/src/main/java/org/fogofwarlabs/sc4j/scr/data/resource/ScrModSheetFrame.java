package org.fogofwarlabs.sc4j.scr.data.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsGrpFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Names one frame of an interface sheet that a mod ships in its jar ({@link ScrModFiles}) for an added unit type, such as its command icon or
 * its wireframe strip: per quality a {@code .dds.grp} file in the format of the installed sheet ({@link DdsGrpFile}) whose first frame is the
 * picture, so the console reads and draws it as an installed frame. The SD file is required; a quality the mod does not give takes the SD file,
 * whose scale keeps the picture at its interface size.
 */
public final class ScrModSheetFrame {
    /**
     * Builds {@link ScrModSheetFrame} instances.
     */
    public static final class Builder {
        private final @NotNull ScrModFiles files;
        private final @NotNull String sdPath;

        private @Nullable String hd2Path;
        private @Nullable String hdPath;

        /**
         * Creates a {@link Builder} instance with the SD file and no HD files.
         *
         * @param files  the files of the mod
         * @param sdPath the path of the SD {@code .dds.grp} file
         */
        public Builder(@NotNull ScrModFiles files, @NotNull String sdPath) {
            this.files  = files;
            this.sdPath = sdPath;

            this.hd2Path = null;
            this.hdPath  = null;
        }

        /**
         * Sets the HD2 {@code .dds.grp} file.
         *
         * @param path the path of the file, or {@code null} for the SD file
         * @return this instance for chaining
         */
        public @NotNull Builder setHd2(@Nullable String path) {
            this.hd2Path = path;

            return this;
        }

        /**
         * Sets the HD {@code .dds.grp} file.
         *
         * @param path the path of the file, or {@code null} for the SD file
         * @return this instance for chaining
         */
        public @NotNull Builder setHd(@Nullable String path) {
            this.hdPath = path;

            return this;
        }

        /**
         * Builds a {@link ScrModSheetFrame} instance with the current paths.
         *
         * @return the built {@link ScrModSheetFrame} instance
         */
        public @NotNull ScrModSheetFrame build() {
            return new ScrModSheetFrame(this);
        }
    }

    private final @NotNull ScrModFiles files;
    private final @NotNull String sdPath;

    private final @Nullable String hd2Path;
    private final @Nullable String hdPath;

    private ScrModSheetFrame(@NotNull Builder builder) {
        this.files  = builder.files;
        this.sdPath = builder.sdPath;

        this.hd2Path = builder.hd2Path;
        this.hdPath  = builder.hdPath;
    }

    /**
     * Returns the path of the file of a quality.
     *
     * @param quality the asset quality
     * @return the path of the quality's file, or of the SD file if the mod gives none of the quality
     */
    public @NotNull String getPath(AssetResolver.@NotNull AssetSet quality) {
        String path = switch (quality) {
            case SD -> this.sdPath;
            case HD2 -> this.hd2Path;
            case HD -> this.hdPath;
        };

        return path == null ? this.sdPath : path;
    }


    /**
     * Reads the file of a quality.
     *
     * @param quality the asset quality
     * @return the parsed file, whose frame {@code 0} is the picture
     * @throws IOException if the file cannot be read, is malformed or holds no frame
     */
    public @NotNull DdsGrpFile readSheet(AssetResolver.@NotNull AssetSet quality) throws IOException {
        String path = this.getPath(quality);
        DdsGrpFile sheet = DdsGrpFile.parse(this.files.readFile(path));

        if (sheet.getFrameCount() == 0) {
            throw Exceptions.newIOException("The mod sheet {0} holds no frame.", path);
        }

        return sheet;
    }
}

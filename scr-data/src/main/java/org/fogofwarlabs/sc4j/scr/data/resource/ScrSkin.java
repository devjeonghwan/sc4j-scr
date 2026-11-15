package org.fogofwarlabs.sc4j.scr.data.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.json.JsonReader;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.image.ImageRelations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Selects the HD anims of one asset quality and skin, independent of the map tileset: {@code images.rel} resolves an image
 * to the anim that draws it, {@code anim/main_NNN.anim} (HD) or {@code HD2/anim/main_NNN.anim} (HD2), replaced by
 * {@code anim/<skin>/main_NNN.anim} for the images a skin lists in its {@code skin.json}. The
 * {@link ScrResourceProvider} keeps one skin per quality and skin name ({@link ScrResourceProvider#loadSkin}).
 */
public final class ScrSkin {
    /**
     * Creates the skin of a quality and skin name ({@link ScrResourceProvider#loadSkin}).
     *
     * @param storage the open storage
     * @param quality the asset quality, {@link AssetResolver.AssetSet#HD} or {@link AssetResolver.AssetSet#HD2}
     * @param name    the skin folder name, or {@code null} for the default anims
     * @return the created {@link ScrSkin} instance
     * @throws IOException if {@code images.rel} or the skin list is missing or malformed
     */
    static @NotNull ScrSkin create(@NotNull CascStorage storage, AssetResolver.@NotNull AssetSet quality, @Nullable String name) throws IOException {
        return new ScrSkin(quality, name, ImageRelations.parse(storage.readFile("images.rel")), readSkinImages(storage, quality, name));
    }

    private static boolean @NotNull [] readSkinImages(@NotNull CascStorage storage, AssetResolver.@NotNull AssetSet quality, @Nullable String name) throws IOException {
        if (name == null) {
            return new boolean[ImagesDat.IMAGE_COUNT];
        }

        String path = quality.getPrefix() + "anim/" + name + "/skin.json";

        if (!storage.hasFile(path)) {
            path = "anim/" + name + "/skin.json";
        }

        if (!storage.hasFile(path)) {
            throw Exceptions.newIOException("Unknown HD skin: {0}", name);
        }

        return parseSkinImages(storage.readFile(path), path);
    }

    /**
     * Parses the image list of a skin's {@code skin.json}: every integer entry of its {@code imageList} that is an image identifier marks that
     * image, and other integers are ignored.
     *
     * @param contents the file contents
     * @param path     the file path, for the error messages
     * @return for each image, whether the skin replaces its anim
     * @throws IOException if the contents are not JSON, have no {@code imageList} or the list has an entry that is not an integer
     */
    private static boolean @NotNull [] parseSkinImages(byte @NotNull [] contents, @NotNull String path) throws IOException {
        if (!(JsonReader.parse(contents) instanceof Map<?, ?> root) || !(root.get("imageList") instanceof List<?> list)) {
            throw Exceptions.newIOException("{0} has no imageList.", path);
        }

        boolean[] images = new boolean[ImagesDat.IMAGE_COUNT];

        for (Object value : list) {
            if (!(value instanceof Long image)) {
                throw Exceptions.newIOException("{0} has a non-integer imageList entry: {1}", path, value);
            }

            if (image >= 0 && image < images.length) {
                images[image.intValue()] = true;
            }
        }

        return images;
    }


    private final AssetResolver.@NotNull AssetSet quality;
    private final @Nullable String name;

    private final @NotNull ImageRelations relations;
    private final boolean @NotNull [] skinImages;

    /**
     * For each image, the lowest image drawn by the same anim ({@code images.rel} shares one anim among several images).
     */
    private final int @NotNull [] sharedImages;

    private ScrSkin(AssetResolver.@NotNull AssetSet quality, @Nullable String name,
                    @NotNull ImageRelations relations, boolean @NotNull [] skinImages) {
        this.quality = quality;
        this.name    = name;

        this.relations  = relations;
        this.skinImages = skinImages;

        this.sharedImages = new int[ImagesDat.IMAGE_COUNT];

        int[] firstImages = new int[ImagesDat.IMAGE_COUNT];

        Arrays.fill(firstImages, -1);

        for (int image = 0; image < this.sharedImages.length; image++) {
            int animationImage = relations.getAnimationImage(image);

            if (animationImage < 0 || animationImage >= firstImages.length) {
                this.sharedImages[image] = image;
            } else {
                if (firstImages[animationImage] < 0) {
                    firstImages[animationImage] = image;
                }

                this.sharedImages[image] = firstImages[animationImage];
            }
        }
    }

    /**
     * @return the asset quality
     */
    public AssetResolver.@NotNull AssetSet getQuality() {
        return this.quality;
    }

    /**
     * @return the skin folder name, or {@code null} for the default anims
     */
    public @Nullable String getName() {
        return this.name;
    }

    /**
     * @return the image relations of {@code images.rel}
     */
    public @NotNull ImageRelations getRelations() {
        return this.relations;
    }

    /**
     * Returns the image whose anim an image shares: both load the same texture sheet, so their textures and frame placement are the same.
     *
     * @param image the image identifier
     * @return the lowest image drawn by the same anim, {@code image} itself when no lower image shares it
     */
    public int getSharedImage(int image) {
        return image >= 0 && image < this.sharedImages.length ? this.sharedImages[image] : image;
    }

    /**
     * @param animationImage an anim identifier (an image after {@code images.rel})
     * @return {@code true} if the skin replaces that anim, {@code false} otherwise
     */
    public boolean isSkinImage(int animationImage) {
        return this.name != null && animationImage >= 0 && animationImage < this.skinImages.length && this.skinImages[animationImage];
    }

    /**
     * Finds the HD anim of an image: the skin's anim when the skin replaces it and has the file, else the quality's.
     *
     * @param storage the open storage
     * @param image   the image identifier
     * @return the CASC path of the anim, or {@code null} if the installation has none
     */
    @Nullable String findAnimationPath(@NotNull CascStorage storage, int image) {
        int animationImage = this.relations.getAnimationImage(image);
        String path = String.format("%sanim/main_%03d.anim", this.quality.getPrefix(), animationImage);

        if (this.isSkinImage(animationImage)) {
            String skinPath = String.format("%sanim/%s/main_%03d.anim", this.quality.getPrefix(), this.name, animationImage);

            if (storage.hasFile(skinPath)) {
                path = skinPath;
            }
        }

        return storage.hasFile(path) ? path : null;
    }
}

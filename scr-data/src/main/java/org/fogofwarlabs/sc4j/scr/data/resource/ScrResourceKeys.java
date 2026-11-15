package org.fogofwarlabs.sc4j.scr.data.resource;

import org.fogofwarlabs.sc4j.engine.resource.ResourceKey;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates the resource keys of the {@link ScrResourceProvider}. A key is a prefix naming how the resource is imported,
 * followed by a CASC path or a name:
 * <ul>
 *     <li>{@value #IMAGE_PREFIX}{@code <imageId>}: the SD sprite sheet of an {@code images.dat} entry (its GRP), or the
 *     texture sheet of the image in the provider's HD skin;</li>
 *     <li>{@value #GRP_PREFIX}{@code <path>}: the sprite sheet of a GRP file;</li>
 *     <li>{@value #PCX_PREFIX}{@code <path>}: the one-frame sprite sheet of an indexed PCX picture;</li>
 *     <li>{@value #PALETTE_PREFIX}{@code <path>}: the palette of a {@code .wpe} file;</li>
 *     <li>{@value #TILESET_PREFIX}{@code <name>}: the megatile pictures of {@code tileset/<name>};</li>
 *     <li>{@value #TABLE_PREFIX}{@code <path>}: the strings of a {@code .tbl} file;</li>
 *     <li>{@value #FONT_PREFIX}{@code <name>}: the game font {@code font/<name>.fnt};</li>
 *     <li>{@value #DIALOG_PREFIX}{@code <name>}: the interface file {@code SD/rez/<name>.ui.json};</li>
 *     <li>{@value #TEXTURE_PREFIX}{@code <quality>/<skin>/<imageId>}: the HD texture sheet of an image in a quality
 *     ({@code HD} or {@code HD2}) and skin ({@value #STANDARD_SKIN} for the default anims).</li>
 * </ul>
 */
public final class ScrResourceKeys {
    /**
     * The prefix of an image key.
     */
    public static final @NotNull String IMAGE_PREFIX = "image/";
    /**
     * The prefix of a GRP sprite sheet key.
     */
    public static final @NotNull String GRP_PREFIX = "grp/";
    /**
     * The prefix of a PCX sprite sheet key.
     */
    public static final @NotNull String PCX_PREFIX = "pcx/";
    /**
     * The prefix of a palette key.
     */
    public static final @NotNull String PALETTE_PREFIX = "palette/";
    /**
     * The prefix of a tileset key.
     */
    public static final @NotNull String TILESET_PREFIX = "tileset/";
    /**
     * The prefix of a string table key.
     */
    public static final @NotNull String TABLE_PREFIX = "tbl/";
    /**
     * The prefix of a font key.
     */
    public static final @NotNull String FONT_PREFIX = "font/";
    /**
     * The prefix of a dialog key.
     */
    public static final @NotNull String DIALOG_PREFIX = "dialog/";
    /**
     * The prefix of an HD texture sheet key.
     */
    public static final @NotNull String TEXTURE_PREFIX = "texture/";
    /**
     * The skin segment of a texture sheet key without a skin.
     */
    public static final @NotNull String STANDARD_SKIN = "standard";

    /**
     * Creates the key of an image.
     *
     * @param imageId the {@code images.dat} entry
     * @return the key
     */
    public static @NotNull ResourceKey ofImage(int imageId) {
        return ResourceKey.of(IMAGE_PREFIX + imageId);
    }

    /**
     * Creates the key of a GRP file.
     *
     * @param path the CASC path, such as {@code unit/cmdicons/cmdicons.grp}
     * @return the key
     */
    public static @NotNull ResourceKey ofGrp(@NotNull String path) {
        return ResourceKey.of(GRP_PREFIX + path);
    }

    /**
     * Creates the key of an indexed PCX picture.
     *
     * @param path the CASC path, such as {@code game/tunit.pcx}
     * @return the key
     */
    public static @NotNull ResourceKey ofPcx(@NotNull String path) {
        return ResourceKey.of(PCX_PREFIX + path);
    }

    /**
     * Creates the key of a {@code .wpe} palette.
     *
     * @param path the CASC path, such as {@code tileset/jungle.wpe}
     * @return the key
     */
    public static @NotNull ResourceKey ofPalette(@NotNull String path) {
        return ResourceKey.of(PALETTE_PREFIX + path);
    }

    /**
     * Creates the key of a tileset's megatile pictures.
     *
     * @param name the tileset file name, such as {@code jungle}
     * @return the key
     */
    public static @NotNull ResourceKey ofTileset(@NotNull String name) {
        return ResourceKey.of(TILESET_PREFIX + name);
    }

    /**
     * Creates the key of a string table.
     *
     * @param path the CASC path, such as {@code arr/images.tbl}
     * @return the key
     */
    public static @NotNull ResourceKey ofTable(@NotNull String path) {
        return ResourceKey.of(TABLE_PREFIX + path);
    }

    /**
     * Creates the key of a game font.
     *
     * @param name the font name, such as {@code font10}
     * @return the key
     */
    public static @NotNull ResourceKey ofFont(@NotNull String name) {
        return ResourceKey.of(FONT_PREFIX + name);
    }

    /**
     * Creates the key of an interface file.
     *
     * @param name the file name without {@code .ui.json}, such as {@code gluMain}
     * @return the key
     */
    public static @NotNull ResourceKey ofDialog(@NotNull String name) {
        return ResourceKey.of(DIALOG_PREFIX + name);
    }

    /**
     * Creates the key of the HD texture sheet of an image.
     *
     * @param quality the asset quality, {@link AssetResolver.AssetSet#HD} or {@link AssetResolver.AssetSet#HD2}
     * @param skin    the skin folder name, or {@code null} for the default anims
     * @param imageId the {@code images.dat} entry
     * @return the key
     */
    public static @NotNull ResourceKey ofTexture(AssetResolver.@NotNull AssetSet quality, @Nullable String skin, int imageId) {
        return ResourceKey.of(TEXTURE_PREFIX + quality.name() + ResourceKey.SEGMENT_SEPARATOR + (skin == null ? STANDARD_SKIN : skin) +
                              ResourceKey.SEGMENT_SEPARATOR + imageId);
    }

    private ScrResourceKeys() {
    }
}

package org.fogofwarlabs.sc4j.scr.data.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationLibrary;
import org.fogofwarlabs.sc4j.engine.resource.ResourceKey;
import org.fogofwarlabs.sc4j.engine.resource.ResourceNotFoundException;
import org.fogofwarlabs.sc4j.engine.resource.ResourceProvider;
import org.fogofwarlabs.sc4j.engine.resource.ResourceType;
import org.fogofwarlabs.sc4j.engine.resource.audio.SoundClip;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.engine.resource.graphics.Palette;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteSheet;
import org.fogofwarlabs.sc4j.engine.resource.graphics.TextureSheet;
import org.fogofwarlabs.sc4j.engine.resource.graphics.TileGraphics;
import org.fogofwarlabs.sc4j.engine.resource.text.FontFace;
import org.fogofwarlabs.sc4j.engine.resource.text.StringTable;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedImage;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.text.TblFile;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogImportUtils;
import org.fogofwarlabs.sc4j.scr.data.resource.importer.*;
import org.fogofwarlabs.sc4j.scr.data.type.ScrImageTypeView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the resources of a StarCraft: Remastered installation as engine model objects: it reads the CASC storage and
 * imports GRP, PCX, {@code .wpe}, tileset, {@code .tbl}, font and interface files and the HD anims ({@link ScrResourceKeys}
 * names the keys). The SD, HD and HD2 qualities and the skins (such as Carbot) are resolved here: a texture sheet key names
 * the quality and skin, whose {@link ScrSkin} the provider keeps ({@link #loadSkin}). The animation library is the iscript of
 * the game data, imported by the {@link AnimationSource} the mod gives.
 * <p>
 * A provider either owns its storage, opened from the installation folder on the first load and closed by
 * {@link #close()}, or borrows an open storage that its owner closes. The provider may be shared by loader threads: the
 * loads are synchronized, except the texture sheet loads, which run in parallel on the thread-safe storage. Imported objects
 * are kept by key and returned again, except texture sheets (their blocks are uploaded once and dropped); sounds are not
 * offered.
 */
public final class ScrResourceProvider implements ResourceProvider, AutoCloseable {
    /**
     * Imports the animation library of the game data.
     */
    public interface AnimationSource {
        /**
         * Imports the animation library.
         *
         * @param storage the open storage
         * @return the animation library
         * @throws IOException if the scripts cannot be read
         */
        @NotNull AnimationLibrary importAnimations(@NotNull CascStorage storage) throws IOException;
    }

    private static final @NotNull String UNIT_FOLDER = "unit/";
    private static final @NotNull String TILESET_FOLDER = "tileset/";
    private static final @NotNull String FONT_FOLDER = "font/";
    private static final @NotNull String IMAGES_DAT = "arr/images.dat";
    private static final @NotNull String IMAGES_TBL = "arr/images.tbl";
    /**
     * The segments of a texture sheet key after its prefix: quality, skin and image.
     */
    private static final int TEXTURE_KEY_SEGMENTS = 3;

    /**
     * Creates a {@link ScrResourceProvider} instance that opens and owns the storage of an installation on the first load.
     *
     * @param installDirectory the StarCraft: Remastered installation folder
     * @param animations       the importer of the animation library
     * @return the created {@link ScrResourceProvider} instance
     */
    public static @NotNull ScrResourceProvider open(@NotNull Path installDirectory, @NotNull AnimationSource animations) {
        return new ScrResourceProvider(installDirectory, null, animations);
    }

    /**
     * Creates a {@link ScrResourceProvider} instance on a borrowed open storage, without an animation library.
     *
     * @param storage the open storage; its owner closes it after the provider
     * @return the created {@link ScrResourceProvider} instance
     */
    public static @NotNull ScrResourceProvider borrow(@NotNull CascStorage storage) {
        return new ScrResourceProvider(null, storage, unused -> {
            throw ResourceNotFoundException.create(ResourceType.ANIMATION_LIBRARY, null);
        });
    }

    /**
     * Creates a {@link ScrResourceProvider} instance over an open storage that it does not own, which also reads the frames of the added images
     * of a game ({@link #setAddedGraphics}).
     *
     * @param storage       the open storage, closed by its owner
     * @param addedGraphics the added graphics of the game whose images the provider loads
     * @return the created {@link ScrResourceProvider} instance
     */
    public static @NotNull ScrResourceProvider borrow(@NotNull CascStorage storage, @NotNull ScrAddedGraphics addedGraphics) {
        ScrResourceProvider provider = borrow(storage);

        provider.setAddedGraphics(addedGraphics);

        return provider;
    }

    private static @NotNull String getPath(@NotNull ResourceKey key, @NotNull String prefix, @NotNull ResourceType type) throws ResourceNotFoundException {
        String name = key.getName();

        if (!name.startsWith(prefix) || name.length() == prefix.length()) {
            throw ResourceNotFoundException.create(type, key);
        }

        return name.substring(prefix.length());
    }


    private final @Nullable Path installDirectory;
    private @Nullable CascStorage storage;
    private final @NotNull AnimationSource animations;

    private @Nullable ScrImageTypeView imageTypes;
    private @Nullable TblFile imageNames;
    private @NotNull ScrAddedGraphics addedGraphics;

    private final @NotNull Map<ResourceKey, SpriteSheet> spriteSheets;
    private final @NotNull Map<ResourceKey, Palette> palettes;
    private final @NotNull Map<ResourceKey, TileGraphics> tileGraphics;
    private final @NotNull Map<ResourceKey, StringTable> stringTables;
    private final @NotNull Map<ResourceKey, FontFace> fonts;
    private final @NotNull Map<ResourceKey, DialogDefinition> dialogs;
    private final @NotNull Map<String, ScrSkin> skins;
    private @Nullable AnimationLibrary animationLibrary;

    private ScrResourceProvider(@Nullable Path installDirectory, @Nullable CascStorage storage, @NotNull AnimationSource animations) {
        this.installDirectory = installDirectory;
        this.storage          = storage;
        this.animations       = animations;

        this.imageTypes    = null;
        this.imageNames    = null;
        this.addedGraphics = new ScrAddedGraphics(0);

        this.spriteSheets     = new HashMap<>();
        this.palettes         = new HashMap<>();
        this.tileGraphics     = new HashMap<>();
        this.stringTables     = new HashMap<>();
        this.fonts            = new HashMap<>();
        this.dialogs          = new HashMap<>();
        this.skins            = new HashMap<>();
        this.animationLibrary = null;
    }

    /**
     * Returns the CASC storage, opening it first when the provider owns it and has not opened it yet. Content the engine
     * model does not cover yet (sounds, videos, DAT tables) is read from it directly.
     *
     * @return the open storage
     * @throws IOException if the storage cannot be opened
     */
    public synchronized @NotNull CascStorage getStorage() throws IOException {
        CascStorage opened = this.storage;

        if (opened == null) {
            Path directory = this.installDirectory;

            if (directory == null) {
                throw Exceptions.newIllegalStateException("The provider has no storage.");
            }

            opened       = CascStorage.open(directory);
            this.storage = opened;
        }

        return opened;
    }

    /**
     * @return the added graphics whose images the provider loads; without a game, a registration without entries
     */
    public synchronized @NotNull ScrAddedGraphics getAddedGraphics() {
        return this.addedGraphics;
    }

    /**
     * Sets the added graphics of the game whose images the provider loads: an image key ({@link ScrResourceKeys#ofImage}) or texture key
     * ({@link ScrResourceKeys#ofTexture}) of an added image reads the SD GRP or the ANIM file of the quality from the mod files
     * ({@link org.fogofwarlabs.sc4j.scr.data.resource.ScrModGraphics}), or the base image's installed frames when the mod gives none.
     *
     * @param addedGraphics the added graphics, shared and read when an image is loaded
     */
    public synchronized void setAddedGraphics(@NotNull ScrAddedGraphics addedGraphics) {
        this.addedGraphics = addedGraphics;
    }

    /**
     * Loads a sprite sheet: an image ({@link ScrResourceKeys#ofImage}, its SD GRP), a GRP file or an indexed PCX picture.
     *
     * @param key the key of the sprite sheet
     * @return the sprite sheet
     * @throws ResourceNotFoundException if the key has another prefix, names no image or no file of the storage
     * @throws IOException               if the sprite sheet cannot be read
     */
    @Override
    public synchronized @NotNull SpriteSheet loadSpriteSheet(@NotNull ResourceKey key) throws IOException {
        SpriteSheet sheet = this.spriteSheets.get(key);

        if (sheet != null) {
            return sheet;
        }

        String name = key.getName();

        if (name.startsWith(ScrResourceKeys.IMAGE_PREFIX) && this.addedGraphics.containsImage(parseImageId(key))) {
            ScrAddedImage added = this.addedGraphics.getImage(parseImageId(key));

            sheet = added.getGraphics() == null
                    ? this.loadSpriteSheet(ScrResourceKeys.ofImage(added.getBaseImage()))
                    : added.getGraphics().loadSheet();
        } else if (name.startsWith(ScrResourceKeys.IMAGE_PREFIX)) {
            String path = this.findImagePath(key);

            try {
                sheet = this.loadSpriteSheet(ScrResourceKeys.ofGrp(path));
            } catch (IllegalArgumentException exception) {
                throw Exceptions.newIOException(exception, "Image {0} names an invalid GRP path: {1}", key, path);
            }
        } else if (name.startsWith(ScrResourceKeys.GRP_PREFIX)) {
            sheet = GrpSheetUtils.createSheet(GrpFile.parse(this.readFile(key, getPath(key, ScrResourceKeys.GRP_PREFIX, ResourceType.SPRITE_SHEET),
                                                                          ResourceType.SPRITE_SHEET)));
        } else {
            sheet = PcxSheetUtils.createSheet(this.readFile(key, getPath(key, ScrResourceKeys.PCX_PREFIX, ResourceType.SPRITE_SHEET),
                                                            ResourceType.SPRITE_SHEET));
        }

        this.spriteSheets.put(key, sheet);

        return sheet;
    }

    /**
     * Loads a texture sheet: the HD anim of an image in a quality and skin ({@link ScrResourceKeys#ofTexture}), its layers as
     * block-compressed textures.
     *
     * @param key the key of the texture sheet
     * @return the texture sheet, a new one per call
     * @throws ResourceNotFoundException if the key is not a texture sheet key or the installation has no anim for the image
     * @throws IOException               if the skin or the anim cannot be read, or the anim is not a single-entry HD anim
     */
    @Override
    public @NotNull TextureSheet loadTextureSheet(@NotNull ResourceKey key) throws IOException {
        String[] segments = getPath(key, ScrResourceKeys.TEXTURE_PREFIX, ResourceType.TEXTURE_SHEET)
                .split(String.valueOf(ResourceKey.SEGMENT_SEPARATOR));
        AssetResolver.AssetSet quality;
        int imageId;

        try {
            quality = AssetResolver.AssetSet.valueOf(segments[0]);
            imageId = Integer.parseInt(segments[segments.length - 1]);
        } catch (IllegalArgumentException exception) {
            throw ResourceNotFoundException.create(ResourceType.TEXTURE_SHEET, key);
        }

        if (segments.length != TEXTURE_KEY_SEGMENTS || !quality.isHd()) {
            throw ResourceNotFoundException.create(ResourceType.TEXTURE_SHEET, key);
        }

        ScrAddedGraphics added = this.getAddedGraphics();

        if (added.containsImage(imageId)) {
            ScrAddedImage image = added.getImage(imageId);
            AnimFile animation = image.getGraphics() == null ? null : image.getGraphics().readAnimation(quality);

            return animation == null
                   ? this.loadTextureSheet(ScrResourceKeys.ofTexture(quality, ScrResourceKeys.STANDARD_SKIN.equals(segments[1]) ? null : segments[1],
                                                                     image.getBaseImage()))
                   : AnimTextureUtils.createSheet(animation);
        }

        CascStorage opened = this.getStorage();
        ScrSkin skin = this.loadSkin(quality, ScrResourceKeys.STANDARD_SKIN.equals(segments[1]) ? null : segments[1]);
        String path = skin.findAnimationPath(opened, imageId);

        if (path == null) {
            throw ResourceNotFoundException.create(ResourceType.TEXTURE_SHEET, key);
        }

        try {
            return AnimTextureUtils.createSheet(AnimFile.parse(opened.readFile(path)));
        } catch (IOException exception) {
            throw Exceptions.newIOException(exception, "{0}: {1}", path, exception.getMessage());
        }
    }

    /**
     * Returns the HD skin of a quality and skin name, read once and kept.
     *
     * @param quality the asset quality, {@link AssetResolver.AssetSet#HD} or {@link AssetResolver.AssetSet#HD2}
     * @param name    the skin folder name, or {@code null} for the default anims
     * @return the skin
     * @throws IOException if {@code images.rel} or the skin list is missing or malformed
     */
    public synchronized @NotNull ScrSkin loadSkin(AssetResolver.@NotNull AssetSet quality, @Nullable String name) throws IOException {
        String cacheKey = quality.name() + ResourceKey.SEGMENT_SEPARATOR + (name == null ? ScrResourceKeys.STANDARD_SKIN : name);
        ScrSkin skin = this.skins.get(cacheKey);

        if (skin == null) {
            skin = ScrSkin.create(this.getStorage(), quality, name);
            this.skins.put(cacheKey, skin);
        }

        return skin;
    }

    /**
     * Loads a palette: the {@code 256} opaque colours of a {@code .wpe} file.
     *
     * @param key the key of the palette ({@link ScrResourceKeys#ofPalette})
     * @return the palette
     * @throws ResourceNotFoundException if the key has another prefix or names no file of the storage
     * @throws IOException               if the palette cannot be read
     */
    @Override
    public synchronized @NotNull Palette loadPalette(@NotNull ResourceKey key) throws IOException {
        Palette palette = this.palettes.get(key);

        if (palette == null) {
            palette = TilesetGraphicsUtils.createPalette(this.readFile(key, getPath(key, ScrResourceKeys.PALETTE_PREFIX, ResourceType.PALETTE),
                                                                       ResourceType.PALETTE));
            this.palettes.put(key, palette);
        }

        return palette;
    }

    /**
     * Loads the graphics of a tileset: its megatiles composed from its minitiles.
     *
     * @param key the key of the tileset ({@link ScrResourceKeys#ofTileset})
     * @return the tile graphics
     * @throws ResourceNotFoundException if the key has another prefix or names no tileset of the storage
     * @throws IOException               if the tile graphics cannot be read
     */
    @Override
    public synchronized @NotNull TileGraphics loadTileGraphics(@NotNull ResourceKey key) throws IOException {
        TileGraphics graphics = this.tileGraphics.get(key);

        if (graphics == null) {
            String prefix = TILESET_FOLDER + getPath(key, ScrResourceKeys.TILESET_PREFIX, ResourceType.TILE_GRAPHICS);

            graphics = TilesetGraphicsUtils.createTileGraphics(this.readFile(key, prefix + ".vx4ex", ResourceType.TILE_GRAPHICS),
                                                               this.readFile(key, prefix + ".vr4", ResourceType.TILE_GRAPHICS));
            this.tileGraphics.put(key, graphics);
        }

        return graphics;
    }

    /**
     * Loads the animation library: the iscript of the game data, imported once by the mod's {@link AnimationSource}.
     *
     * @return the animation library
     * @throws ResourceNotFoundException if the provider borrows its storage and has no animation source
     * @throws IOException               if the animations cannot be read
     */
    @Override
    public synchronized @NotNull AnimationLibrary loadAnimations() throws IOException {
        AnimationLibrary library = this.animationLibrary;

        if (library == null) {
            library               = this.animations.importAnimations(this.getStorage());
            this.animationLibrary = library;
        }

        return library;
    }

    /**
     * Loads a sound clip.
     *
     * @param key the key of the sound
     * @return the sound clip
     * @throws ResourceNotFoundException always: the SC:R client plays its sounds from the storage
     */
    @Override
    public @NotNull SoundClip loadSound(@NotNull ResourceKey key) throws IOException {
        throw ResourceNotFoundException.create(ResourceType.SOUND, key);
    }

    /**
     * Loads a string table: the strings of a {@code .tbl} file, each byte one ISO-8859-1 character.
     *
     * @param key the key of the string table ({@link ScrResourceKeys#ofTable})
     * @return the string table
     * @throws ResourceNotFoundException if the key has another prefix or names no file of the storage
     * @throws IOException               if the string table cannot be read
     */
    @Override
    public synchronized @NotNull StringTable loadStringTable(@NotNull ResourceKey key) throws IOException {
        StringTable table = this.stringTables.get(key);

        if (table == null) {
            TblFile file = TblFile.parse(this.readFile(key, getPath(key, ScrResourceKeys.TABLE_PREFIX, ResourceType.STRING_TABLE),
                                                       ResourceType.STRING_TABLE));
            List<String> strings = new ArrayList<>(file.getStringCount());

            for (int index = 0; index < file.getStringCount(); index++) {
                strings.add(file.getString(index));
            }

            table = new StringTable(strings);
            this.stringTables.put(key, table);
        }

        return table;
    }

    /**
     * Loads a font face: a game font and its distance-field atlas.
     *
     * @param key the key of the font ({@link ScrResourceKeys#ofFont})
     * @return the font face
     * @throws ResourceNotFoundException if the key has another prefix or names no font of the storage
     * @throws IOException               if the font cannot be read
     */
    @Override
    public synchronized @NotNull FontFace loadFont(@NotNull ResourceKey key) throws IOException {
        FontFace font = this.fonts.get(key);

        if (font == null) {
            String name = getPath(key, ScrResourceKeys.FONT_PREFIX, ResourceType.FONT);

            font = FontFaceUtils.createFont(name, this.readFile(key, FONT_FOLDER + name + ".fnt", ResourceType.FONT),
                                            DdsFile.parse(this.readFile(key, FONT_FOLDER + name + ".DDS", ResourceType.FONT)));
            this.fonts.put(key, font);
        }

        return font;
    }

    /**
     * Loads a dialog definition: an interface file of {@code SD/rez}.
     *
     * @param key the key of the dialog ({@link ScrResourceKeys#ofDialog})
     * @return the dialog definition
     * @throws ResourceNotFoundException if the key has another prefix or names no interface file of the storage
     * @throws IOException               if the dialog cannot be read
     */
    @Override
    public synchronized @NotNull DialogDefinition loadDialog(@NotNull ResourceKey key) throws IOException {
        DialogDefinition dialog = this.dialogs.get(key);

        if (dialog == null) {
            String name = getPath(key, ScrResourceKeys.DIALOG_PREFIX, ResourceType.DIALOG);

            dialog = ScrDialogImportUtils.parse(this.readFile(key, ScrDialogImportUtils.getPath(name), ResourceType.DIALOG), name);
            this.dialogs.put(key, dialog);
        }

        return dialog;
    }

    /**
     * Reads the image id of an image key: the number after its prefix.
     *
     * @param key the image key
     * @return the {@code images.dat} entry the key names
     * @throws ResourceNotFoundException if the key has another prefix or its name is not a number
     */
    private static int parseImageId(@NotNull ResourceKey key) throws ResourceNotFoundException {
        try {
            return Integer.parseInt(getPath(key, ScrResourceKeys.IMAGE_PREFIX, ResourceType.SPRITE_SHEET));
        } catch (NumberFormatException exception) {
            throw ResourceNotFoundException.create(ResourceType.SPRITE_SHEET, key);
        }
    }

    /**
     * Finds the GRP path of an image key: the {@code images.tbl} string of the image's {@code images.dat} GRP reference
     * below {@code unit/}.
     *
     * @param key the image key
     * @return the CASC path of the image's GRP
     * @throws ResourceNotFoundException if the key names no image or the image no string
     * @throws IOException               if {@code images.dat} or {@code images.tbl} cannot be read
     */
    private @NotNull String findImagePath(@NotNull ResourceKey key) throws IOException {
        int imageId = parseImageId(key);

        ScrImageTypeView types = this.imageTypes;
        TblFile names = this.imageNames;

        if (types == null || names == null) {
            CascStorage opened = this.getStorage();

            types           = ImagesDat.createTypeTable(opened.readParsed(IMAGES_DAT, DatTable.class, ImagesDat.createSchema()::parse));
            names           = opened.readParsed(IMAGES_TBL, TblFile.class, TblFile::parse);
            this.imageTypes = types;
            this.imageNames = names;
        }

        if (imageId < 0 || imageId >= types.getCount()) {
            throw ResourceNotFoundException.create(ResourceType.SPRITE_SHEET, key);
        }

        int stringIndex = types.getGraphicsFile(imageId) - 1;

        if (stringIndex < 0 || stringIndex >= names.getStringCount()) {
            throw ResourceNotFoundException.create(ResourceType.SPRITE_SHEET, key);
        }

        return UNIT_FOLDER + names.getString(stringIndex).replace('\\', '/');
    }

    private byte @NotNull [] readFile(@NotNull ResourceKey key, @NotNull String path, @NotNull ResourceType type) throws IOException {
        CascStorage opened = this.getStorage();

        if (!opened.hasFile(path)) {
            throw ResourceNotFoundException.create(type, key);
        }

        return opened.readFile(path);
    }


    /**
     * Closes the storage when the provider owns and opened it; a borrowed storage stays open.
     */
    @Override
    public synchronized void close() {
        CascStorage opened = this.storage;

        if (opened != null && this.installDirectory != null) {
            this.storage = null;
            opened.close();
        }
    }
}

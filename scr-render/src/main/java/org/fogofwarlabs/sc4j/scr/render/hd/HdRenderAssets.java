package org.fogofwarlabs.sc4j.scr.render.hd;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteFrame;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteSheet;
import org.fogofwarlabs.sc4j.scr.data.format.casc.AssetResolver;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimLights;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsGrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainMaskTable;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.format.text.TblFile;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceKeys;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.data.type.ScrImageTypeView;
import org.fogofwarlabs.sc4j.scr.render.ImageOverlayOffsets;
import org.fogofwarlabs.sc4j.scr.render.OverlayAssets;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.fogofwarlabs.sc4j.scr.render.RenderAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Loads HD or HD2 terrain, animation, overlay and color assets for one tileset.
 */
public final class HdRenderAssets {
    /**
     * The components of a team color: red, green and blue.
     */
    static final int COLOR_COMPONENTS = 3;

    /**
     * The Bunker main image, whose attack overlay offsets place the Bunker fire overlay.
     */
    private static final int BUNKER_IMAGE = 304;
    /**
     * The Bunker fire overlay image (sprite {@code 377}), whose HD frames are authored around the Bunker.
     */
    private static final int BUNKER_FIRE_IMAGE = 306;
    /**
     * The frames of one direction set (directions {@code 0..16}; the others are flipped).
     */
    private static final int DIRECTION_FRAMES = 17;
    /**
     * The direction a flipped frame mirrors around: frame {@code d} is drawn flipped for direction {@code 32 - d}.
     */
    private static final int FLIPPED_DIRECTION_TOTAL = 32;
    /**
     * The directions per overlay position of the Bunker's attack overlay table.
     */
    private static final int DIRECTIONS_PER_POSITION = 4;
    /**
     * The creep-edge offset values per frame: horizontal, then vertical.
     */
    private static final int CREEP_OFFSETS_PER_FRAME = 2;

    private static final @NotNull String @NotNull [] TILESET_NAMES = {
            "badlands", "platform", "install", "ashworld", "jungle", "desert", "ice", "twilight"
    };
    private static final int EFFECT_WATER = 0;
    private static final int EFFECT_HEAT = 1;
    private static final int EFFECT_NONE = 2;
    /**
     * The per-tileset terrain effect.
     */
    private static final int @NotNull [] TILESET_EFFECTS = {
            EFFECT_WATER, EFFECT_NONE, EFFECT_NONE, EFFECT_HEAT, EFFECT_WATER, EFFECT_NONE, EFFECT_WATER, EFFECT_WATER
    };

    /**
     * The player colors of {@code game/tminimap.pcx}, one palette index each.
     */
    private static final int TEAM_COLOR_COUNT = 16;

    /**
     * The largest tile identifier: a tile identifier is a WORD.
     */
    private static final int MAXIMUM_TILE_IDENTIFIER = 0xFFFF;

    /**
     * The image-overlay ({@code .lo*}) layout: header with the position count and the first frame's offset.
     */
    private static final int OVERLAY_HEADER_SIZE = 12;
    private static final int OVERLAY_POSITION_COUNT_OFFSET = 4;
    private static final int OVERLAY_FRAME_OFFSET_OFFSET = 8;
    private static final int MAXIMUM_OVERLAY_POSITIONS = 64;
    /**
     * The bytes of one overlay position: a signed horizontal byte, then a signed vertical byte.
     */
    private static final int OVERLAY_POSITION_SIZE = 2;
    /**
     * The packed offset layout: the horizontal offset in the low WORD, the vertical offset above it.
     */
    private static final int OFFSET_WORD_MASK = 0xFFFF;
    private static final int VERTICAL_OFFSET_SHIFT = 16;

    /**
     * The mask of an unsigned byte.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Selects the installed remastered asset quality: one of the remastered sets of the shared resolver ({@link AssetResolver.AssetSet}).
     */
    public enum Quality {
        /**
         * {@code anim/} and {@code TileSet/}: four texture pixels per game pixel.
         */
        HD(AssetResolver.AssetSet.HD),
        /**
         * {@code HD2/anim/} and {@code HD2/TileSet/}: two texture pixels per game pixel.
         */
        HD2(AssetResolver.AssetSet.HD2);

        private final AssetResolver.@NotNull AssetSet assetSet;

        Quality(AssetResolver.@NotNull AssetSet assetSet) {
            this.assetSet = assetSet;
        }

        /**
         * @return the asset set of this quality
         */
        public AssetResolver.@NotNull AssetSet getAssetSet() {
            return this.assetSet;
        }

        /**
         * @return the texture pixels per game pixel
         */
        public int getScale() {
            return this.assetSet.getScale();
        }

        /**
         * @return the path prefix of the quality's files: {@code ""} (HD) or {@code "HD2/"}
         */
        public @NotNull String getPrefix() {
            return this.assetSet.getPrefix();
        }
    }

    /**
     * Creates an {@link HdRenderAssets} instance by loading the assets of a tileset.
     *
     * @param resources the resource provider of the installation, kept open while the assets are used
     * @param tileset   the map tileset index from {@code 0} through {@code 7}
     * @param quality   the asset quality
     * @return the created {@link HdRenderAssets} instance
     * @throws IOException if an asset is missing or malformed
     */
    public static @NotNull HdRenderAssets load(@NotNull ScrResourceProvider resources, int tileset, @NotNull Quality quality) throws IOException {
        return load(resources, tileset, quality, null);
    }

    /**
     * Creates an {@link HdRenderAssets} instance by loading the tileset assets with animation and light overrides from an optional skin descriptor.
     *
     * @param resources the resource provider of the installation, kept open while the assets are used
     * @param tileset   the map tileset index from {@code 0} through {@code 7}
     * @param quality   the asset quality
     * @param skin      the skin folder name, or {@code null} for the default anims
     * @return the created {@link HdRenderAssets} instance
     * @throws IOException if an asset is missing or malformed
     */
    public static @NotNull HdRenderAssets load(@NotNull ScrResourceProvider resources, int tileset, @NotNull Quality quality,
                                               @Nullable String skin) throws IOException {
        return load(tileset, HdAnimations.load(resources, quality, skin));
    }

    /**
     * Creates an {@link HdRenderAssets} instance by loading the assets of a tileset around already loaded anims (shared with an {@link HdImageSet}).
     *
     * @param tileset    the map tileset index from {@code 0} through {@code 7}
     * @param animations the anims of the quality and skin
     * @return the created {@link HdRenderAssets} instance
     * @throws IOException if an asset is missing or malformed
     */
    public static @NotNull HdRenderAssets load(int tileset, @NotNull HdAnimations animations) throws IOException {
        ScrResourceProvider resources = animations.getResources();
        CascStorage storage = resources.getStorage();
        Quality quality = animations.getQuality();
        String skin = animations.getSkin();

        if (tileset < 0 || tileset >= TILESET_NAMES.length) {
            throw Exceptions.newIOException("Unsupported tileset index: {0}", tileset);
        }

        String name = TILESET_NAMES[tileset];
        byte[] groups = storage.readFile("tileset/" + name + ".cv5");

        if (groups.length % TerrainTileset.CV5_RECORD_SIZE != 0) {
            throw Exceptions.newIOException("Invalid CV5 record size.");
        }

        DdsGrpFile megatiles = DdsGrpFile.parse(storage.readFile(quality.getPrefix() + "TileSet/" + name + ".dds.vr4"));
        DdsGrpFile creepEdges = DdsGrpFile.parse(storage.readFile(quality.getPrefix() + "TileSet/" + name + ".dds.grp"));
        SpriteSheet classicCreepEdges = resources.loadSpriteSheet(ScrResourceKeys.ofGrp("tileset/" + name + ".grp"));

        if (classicCreepEdges.getFrameCount() != creepEdges.getFrameCount()) {
            throw Exceptions.newIOException("HD creep-edge frame count {0} differs from the classic {1}",
                                            creepEdges.getFrameCount(), classicCreepEdges.getFrameCount());
        }

        int[] creepOffsets = new int[creepEdges.getFrameCount() * CREEP_OFFSETS_PER_FRAME];

        for (int frame = 0; frame < creepEdges.getFrameCount(); frame++) {
            SpriteFrame classicFrame = classicCreepEdges.getFrame(frame);
            // The HD frames cover the classic rectangle padded to texture blocks; keep them inside the tile.
            int tileSize = TerrainTileset.TILE_SIZE * quality.getScale();

            creepOffsets[frame * CREEP_OFFSETS_PER_FRAME]     = Math.max(0,
                                                                         Math.min(classicFrame.getX() * quality.getScale(),
                                                                                  tileSize - creepEdges.getFrameWidth(frame)));
            creepOffsets[frame * CREEP_OFFSETS_PER_FRAME + 1] = Math.max(0,
                                                                         Math.min(classicFrame.getY() * quality.getScale(),
                                                                                  tileSize - creepEdges.getFrameHeight(frame)));
        }

        byte[] palette = storage.readFile("tileset/" + name + ".wpe");
        byte[] minimapColors = RenderAssets.copyIndices(resources.loadSpriteSheet(ScrResourceKeys.ofPcx("game/tminimap.pcx")), TEAM_COLOR_COUNT, 1);
        float[] teamColors = new float[TEAM_COLOR_COUNT * COLOR_COMPONENTS];

        for (int color = 0; color < TEAM_COLOR_COUNT; color++) {
            int paletteIndex = minimapColors[color] & BYTE_MASK;

            for (int channel = 0; channel < COLOR_COMPONENTS; channel++) {
                teamColors[color * COLOR_COMPONENTS + channel] = (palette[paletteIndex * RenderAssets.PALETTE_ENTRY_SIZE + channel] & BYTE_MASK) /
                                                                 OverlayPass.CHANNEL_MAXIMUM;
            }
        }

        ScrImageTypeView images = ImagesDat.createTypeTable(storage.readParsed("arr/images.dat", DatTable.class, ImagesDat.createSchema()::parse));
        String maskPath = quality.getPrefix() + "TileSet/" + name + ".tmsk";
        int effect = TILESET_EFFECTS[tileset];
        TerrainMaskTable waterMasks = null;
        DdsGrpFile waterMaskFrames = null;
        DdsGrpFile waterNormals = null;
        DdsGrpFile waterDetails = null;
        DdsFile noise = null;

        if (effect != EFFECT_NONE && storage.hasFile(maskPath)) {
            waterMasks      = TerrainMaskTable.parse(storage.readFile(maskPath), megatiles.getFrameCount());
            waterMaskFrames = DdsGrpFile.parse(storage.readFile(quality.getPrefix() + "TileSet/" + name + "_mask.dds.grp"));

            if (effect == EFFECT_WATER) {
                waterNormals = DdsGrpFile.parse(storage.readFile(quality.getPrefix() + "effect/water_normal_2.dds.grp"));
                waterDetails = DdsGrpFile.parse(storage.readFile(quality.getPrefix() + "effect/water_normal_1.dds.grp"));
            } else {
                noise = DdsFile.parse(storage.readFile(quality.getPrefix() + "effect/noise.dds"));
            }
        }

        TblFile imageNames = storage.readParsed("arr/images.tbl", TblFile.class, TblFile::parse);
        int[] bunkerAttackOffsets = readBunkerAttackOffsets(storage, images, imageNames);

        return new HdRenderAssets(storage, quality,
                                  groups, megatiles,
                                  creepEdges, creepOffsets,
                                  teamColors, palette,
                                  animations, OverlayAssets.load(resources, tileset), AnimLights.parse(storage.readFile("anim/main.lit")),
                                  waterMasks, waterMaskFrames,
                                  waterNormals, waterDetails, noise,
                                  skin != null && storage.hasFile("anim/" + skin + "/main.lit")
                                  ? AnimLights.parse(storage.readFile("anim/" + skin + "/main.lit"))
                                  : null,
                                  bunkerAttackOffsets, name);
    }

    /**
     * Reads the Bunker attack overlay's first-frame signed offsets, one pair per position.
     *
     * @param storage    the borrowed CASC storage
     * @param images     the image type table
     * @param imageNames the {@code images.tbl} strings
     * @return the packed offsets of frame {@code 0} (horizontal in the low {@code 16} bits, vertical in the high {@code 16} bits)
     * @throws IOException if the resource is missing or malformed
     */
    private static int @NotNull [] readBunkerAttackOffsets(@NotNull CascStorage storage,
                                                           @NotNull ScrImageTypeView images, @NotNull TblFile imageNames) throws IOException {
        String path = "unit/" + imageNames.getString(images.getOverlay(BUNKER_IMAGE, ScrImageTypeView.OVERLAY_ATTACK) - 1).replace('\\', '/');
        byte[] contents = storage.readFile(path);
        ByteBuffer source = ByteBuffer.wrap(contents).order(ByteOrder.LITTLE_ENDIAN);

        if (contents.length < OVERLAY_HEADER_SIZE) {
            throw Exceptions.newIOException("Image-overlay data lacks its header: {0}", path);
        }

        int positionCount = source.getInt(OVERLAY_POSITION_COUNT_OFFSET);
        int frameOffset = source.getInt(OVERLAY_FRAME_OFFSET_OFFSET);

        if (positionCount <= 0 || positionCount > MAXIMUM_OVERLAY_POSITIONS || frameOffset < 0 ||
            frameOffset + positionCount * OVERLAY_POSITION_SIZE > contents.length) {
            throw Exceptions.newIOException("Invalid image-overlay data: {0}", path);
        }

        int[] offsets = new int[positionCount];

        for (int position = 0; position < positionCount; position++) {
            offsets[position] = (contents[frameOffset + position * OVERLAY_POSITION_SIZE] & OFFSET_WORD_MASK) |
                                contents[frameOffset + position * OVERLAY_POSITION_SIZE + 1] << VERTICAL_OFFSET_SHIFT;
        }

        return offsets;
    }

    /**
     * Resolves a tile identifier to its VX4 megatile through the tileset CV5 group.
     *
     * @param groups         the tileset CV5 records
     * @param tileIdentifier the map tile value
     * @return the megatile index, or {@code -1} when the CV5 group is outside the tileset
     */
    private static int findCv5Megatile(byte @NotNull [] groups, int tileIdentifier) {
        int groupOffset = TerrainTileset.getGroupIndex(tileIdentifier) * TerrainTileset.CV5_RECORD_SIZE;

        if (tileIdentifier < 0 || tileIdentifier > MAXIMUM_TILE_IDENTIFIER || groupOffset + TerrainTileset.CV5_RECORD_SIZE > groups.length) {
            return -1;
        }

        return LittleEndianUtils.readUnsigned16(groups, TerrainTileset.getMegatileOffset(tileIdentifier));
    }

    private final @NotNull CascStorage storage;
    private final @NotNull Quality quality;

    private final byte @NotNull [] groups;
    private final @NotNull DdsGrpFile megatiles;
    private final @NotNull DdsGrpFile creepEdges;
    private final int @NotNull [] creepOffsets;

    private final float @NotNull [] teamColors;
    /**
     * The tileset {@code .wpe} palette, four bytes per entry.
     */
    private final byte @NotNull [] palette;

    private final @NotNull HdAnimations animations;
    private final @NotNull OverlayAssets overlayAssets;
    private final @NotNull AnimLights lights;

    private final @Nullable TerrainMaskTable waterMasks;
    private final @Nullable DdsGrpFile waterMaskFrames;
    private final @Nullable DdsGrpFile waterNormals;
    private final @Nullable DdsGrpFile waterDetails;
    private final @Nullable DdsFile noise;

    private final @Nullable AnimLights skinLights;

    private final int @NotNull [] bunkerAttackOffsets;
    private final @NotNull String tilesetName;

    private HdRenderAssets(@NotNull CascStorage storage, @NotNull Quality quality,
                           byte @NotNull [] groups, @NotNull DdsGrpFile megatiles,
                           @NotNull DdsGrpFile creepEdges, int @NotNull [] creepOffsets,
                           float @NotNull [] teamColors, byte @NotNull [] palette,
                           @NotNull HdAnimations animations, @NotNull OverlayAssets overlayAssets, @NotNull AnimLights lights,
                           @Nullable TerrainMaskTable waterMasks, @Nullable DdsGrpFile waterMaskFrames,
                           @Nullable DdsGrpFile waterNormals, @Nullable DdsGrpFile waterDetails, @Nullable DdsFile noise,
                           @Nullable AnimLights skinLights,
                           int @NotNull [] bunkerAttackOffsets, @NotNull String tilesetName) {
        this.storage = storage;
        this.quality = quality;

        this.groups       = groups;
        this.megatiles    = megatiles;
        this.creepEdges   = creepEdges;
        this.creepOffsets = creepOffsets;

        this.teamColors = teamColors;
        this.palette    = palette;

        this.animations    = animations;
        this.overlayAssets = overlayAssets;
        this.lights        = lights;

        this.waterMasks      = waterMasks;
        this.waterMaskFrames = waterMaskFrames;
        this.waterNormals    = waterNormals;
        this.waterDetails    = waterDetails;
        this.noise           = noise;

        this.skinLights = skinLights;

        this.bunkerAttackOffsets = bunkerAttackOffsets;
        this.tilesetName         = tilesetName;
    }

    /**
     * @return the asset quality
     */
    public @NotNull Quality getQuality() {
        return this.quality;
    }

    /**
     * @return the anims of the quality and skin
     */
    public @NotNull HdAnimations getAnimations() {
        return this.animations;
    }

    /**
     * @return the megatile texture container, one frame per VX4 megatile
     */
    public @NotNull DdsGrpFile getMegatiles() {
        return this.megatiles;
    }

    /**
     * @return the creep-edge texture container in classic creep-edge frame order
     */
    public @NotNull DdsGrpFile getCreepEdges() {
        return this.creepEdges;
    }

    /**
     * @param frame the creep-edge frame
     * @return the frame's horizontal offset inside the tile in texture pixels
     */
    public int getCreepEdgeXOffset(int frame) {
        return this.creepOffsets[frame * CREEP_OFFSETS_PER_FRAME];
    }

    /**
     * @param frame the creep-edge frame
     * @return the frame's vertical offset inside the tile in texture pixels
     */
    public int getCreepEdgeYOffset(int frame) {
        return this.creepOffsets[frame * CREEP_OFFSETS_PER_FRAME + 1];
    }

    /**
     * @return {@code true} if the tileset has a terrain effect (water or heat distortion), {@code false} otherwise
     */
    boolean hasTerrainEffect() {
        return this.waterMasks != null;
    }

    /**
     * @param megatile the VX4 megatile index
     * @return the water-mask frame of the megatile, or {@code -1} without water
     */
    int getWaterMaskFrame(int megatile) {
        return this.waterMasks != null ? this.waterMasks.getMaskFrame(megatile) : -1;
    }

    /**
     * @param image the image identifier
     * @return the lights of the anim that draws the image: the skin's {@code main.lit} for skinned images
     */
    @NotNull AnimLights getLights(int image) {
        return this.skinLights != null && this.animations.isSkinImage(this.animations.getRelations().getAnimationImage(image))
               ? this.skinLights
               : this.lights;
    }

    /**
     * @return the classic selection-circle frames and circle/bar color tables
     */
    @NotNull OverlayAssets getOverlayAssets() {
        return this.overlayAssets;
    }

    /**
     * Returns the installed Bunker fire overlay offset to subtract from an image position. The frame direction selects an attack-overlay position,
     * mirrored for flipped frames.
     *
     * @param image   the image identifier
     * @param frame   the image frame
     * @param flipped whether the image is horizontally flipped
     * @return the packed offset to subtract (horizontal in the low {@code 16} bits, vertical in the high {@code 16} bits), or {@code 0}
     */
    int getAnchoredOverlayOffset(int image, int frame, boolean flipped) {
        try {
            return this.getAnchoredOverlayOffset(image, frame, flipped, null);
        } catch (IOException exception) {
            return 0;
        }
    }

    /**
     * Returns the offset of {@link #getAnchoredOverlayOffset(int, int, boolean)} from the game's live overlay tables when given, or from the
     * installed resource otherwise.
     *
     * @param image    the image identifier
     * @param frame    the image frame
     * @param flipped  whether the image is horizontally flipped
     * @param overlays the game's overlay offsets, or {@code null} for the installed resource
     * @return the packed offset to subtract (horizontal in the low {@code 16} bits, vertical in the high {@code 16} bits), or {@code 0}
     * @throws IOException if the game's overlay resource or position is unavailable
     */
    int getAnchoredOverlayOffset(int image, int frame, boolean flipped, @Nullable ImageOverlayOffsets overlays) throws IOException {
        if (image != BUNKER_FIRE_IMAGE) {
            return 0;
        }

        int turn = frame % DIRECTION_FRAMES;
        int position = flipped ? (FLIPPED_DIRECTION_TOTAL - turn) / DIRECTIONS_PER_POSITION - 1 : turn / DIRECTIONS_PER_POSITION;

        if (overlays != null) {
            return position < 0 ? 0 : overlays.getOffset(BUNKER_IMAGE, ScrImageTypeView.OVERLAY_ATTACK, 0, position);
        }

        if (position < 0 || position >= this.bunkerAttackOffsets.length) {
            return 0;
        }

        return this.bunkerAttackOffsets[position];
    }

    /**
     * Resolves a tile identifier to its VX4 megatile through the tileset CV5 group without the HD megatile range check, as the foliage placement
     * does.
     *
     * @param tileIdentifier the map tile value
     * @return the megatile index, or {@code -1} when the CV5 group is outside the tileset
     */
    int getCv5Megatile(int tileIdentifier) {
        return findCv5Megatile(this.groups, tileIdentifier);
    }

    /**
     * Resolves a tile identifier to its VX4 megatile through the tileset CV5 group.
     *
     * @param tileIdentifier the map tile value
     * @return the megatile index
     * @throws IOException if the group or megatile is outside the installed tileset
     */
    public int getMegatile(int tileIdentifier) throws IOException {
        int megatile = findCv5Megatile(this.groups, tileIdentifier);

        if (megatile < 0) {
            throw Exceptions.newIOException("Tile group is out of bounds: {0}", tileIdentifier);
        }

        if (megatile >= this.megatiles.getFrameCount()) {
            throw Exceptions.newIOException("Megatile {0} is outside the HD tileset.", megatile);
        }

        return megatile;
    }


    /**
     * Writes a player color's team color as linear {@code 0..1} RGB.
     *
     * @param color       the player color index, clamped to {@code 0..15}
     * @param destination the array receiving three components
     * @param offset      the first destination index
     */
    public void copyTeamColor(int color, float @NotNull [] destination, int offset) {
        System.arraycopy(this.teamColors, Math.clamp(color, 0, TEAM_COLOR_COUNT - 1) * COLOR_COMPONENTS, destination, offset, COLOR_COMPONENTS);
    }

    /**
     * Writes a palette entry as linear {@code 0..1} RGB.
     *
     * @param paletteIndex the palette index {@code 0..255}
     * @param destination  the array receiving three components
     * @param offset       the first destination index
     */
    public void copyPaletteColor(int paletteIndex, float @NotNull [] destination, int offset) {
        for (int channel = 0; channel < COLOR_COMPONENTS; channel++) {
            destination[offset + channel] = (this.palette[(paletteIndex & BYTE_MASK) * RenderAssets.PALETTE_ENTRY_SIZE + channel] & BYTE_MASK) /
                                            OverlayPass.CHANNEL_MAXIMUM;
        }
    }

    /**
     * Creates an {@link HdTerrainEffect} instance for the terrain effect pass of this tileset: water or heat distortion.
     *
     * @param compressed whether S3TC textures can be uploaded compressed
     * @return the created {@link HdTerrainEffect} instance
     * @throws IOException           if an effect frame is invalid
     * @throws IllegalStateException if the tileset has no terrain effect
     */
    @NotNull HdTerrainEffect createTerrainEffect(boolean compressed) throws IOException {
        if (this.waterMaskFrames == null) {
            throw Exceptions.newIllegalStateException("The tileset has no terrain effect.");
        }

        if (this.noise != null) {
            return HdTerrainEffect.createHeatDistortion(this.waterMaskFrames, this.noise, compressed);
        }

        if (this.waterNormals == null || this.waterDetails == null) {
            throw Exceptions.newIllegalStateException("The tileset has no water normals.");
        }

        return HdTerrainEffect.createWater(this.waterMaskFrames, this.waterNormals, this.waterDetails, compressed);
    }

    /**
     * Creates an {@link HdFoliage} instance with the tileset's HD foliage for one scene.
     *
     * @return the created {@link HdFoliage} instance, or {@code null} when the installed data has no foliage for the tileset
     * @throws IOException if the foliage data is malformed
     */
    @Nullable HdFoliage loadFoliage() throws IOException {
        return HdFoliage.load(this.storage, this.quality.getPrefix(), this.animations.getSkin(), this.tilesetName);
    }
}

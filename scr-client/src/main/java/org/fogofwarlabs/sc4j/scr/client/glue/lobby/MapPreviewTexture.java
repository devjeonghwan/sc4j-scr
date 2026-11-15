package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.ui.minimap.MinimapImage;
import org.fogofwarlabs.sc4j.scr.client.ui.minimap.MinimapPreviewUnit;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a per-screen dynamic texture built from map tiles and preview units; unsupported previews leave no image.
 */
final class MapPreviewTexture implements MapInformationPanel.Preview {
    private static final @NotNull Logger LOGGER = Logger.of(MapPreviewTexture.class);

    /**
     * The prefix of the texture key; the identity hash code of the preview follows it.
     */
    private static final @NotNull String KEY_PREFIX = "preview@";
    private static final @NotNull String UNITS_FILE = "arr/units.dat";

    /**
     * The key of the texture in the canvas's dynamic textures.
     */
    private final @NotNull String key;

    private @Nullable ScrUnitTypeView unitTypes;

    private int texture;
    private int width;
    private int height;

    /**
     * Creates a {@link MapPreviewTexture} instance without an image.
     */
    MapPreviewTexture() {
        this.key = KEY_PREFIX + System.identityHashCode(this);

        this.unitTypes = null;

        this.texture = 0;
        this.width   = 0;
        this.height  = 0;
    }

    /**
     * @return the preview width in pixels
     */
    @Override
    public int getWidth() {
        return this.width;
    }

    /**
     * @return the preview height in pixels
     */
    @Override
    public int getHeight() {
        return this.height;
    }

    /**
     * @return the preview texture
     */
    @Override
    public int getTexture() {
        return this.texture;
    }

    /**
     * @return {@code true} if the preview has positive dimensions, {@code false} otherwise
     */
    boolean hasImage() {
        return this.width > 0 && this.height > 0;
    }


    /**
     * Replaces the image with the preview of a map, or releases it.
     *
     * @param glueContext the glue services
     * @param map         the map, or {@code null} for none
     */
    void replace(@NotNull GlueContext glueContext, @Nullable MapInformation map) {
        GlueCanvas canvas = glueContext.getCanvas();

        this.release(canvas);

        if (map == null) {
            return;
        }

        try {
            if (this.unitTypes == null) {
                this.unitTypes = UnitsDat.createTypeTable(UnitsDat.createSchema().parse(canvas.getStorage().readFile(UNITS_FILE)));
            }

            InterfaceAssets assets = glueContext.getTilesetAssets(map.getTileset());

            // The tile words of the combined MTXM copies, as the map load keeps them.
            int[] tileIds = map.getMap().getTileIds();
            short[] words = new short[Math.min(tileIds.length, map.getWidth() * map.getHeight())];

            for (int index = 0; index < words.length; index++) {
                words[index] = (short) tileIds[index];
            }

            List<MinimapPreviewUnit> units = new ArrayList<>();

            for (ChkUnit unit : map.getPreviewUnits()) {
                units.add(new MinimapPreviewUnit(unit.getX(), unit.getY(), unit.getUnitId(), unit.getOwner()));
            }

            MinimapImage image = MinimapImage.buildPreview(map.getWidth(), map.getHeight(), words, units, this.unitTypes, assets);

            if (image == null) {
                return;
            }

            this.texture = canvas.updateTexture(this.key, image.getTextureWidth(), image.getTextureHeight(), image.getRgba());
            this.width   = image.getWidth();
            this.height  = image.getHeight();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warning("MAP PREVIEW UNAVAILABLE: " + exception.getMessage());
        }
    }

    /**
     * Releases the image.
     *
     * @param canvas the glue canvas
     */
    void release(@NotNull GlueCanvas canvas) {
        if (this.hasImage()) {
            canvas.releaseTexture(this.key);
        }

        this.texture = 0;
        this.width   = 0;
        this.height  = 0;
    }
}

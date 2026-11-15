package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.production.PlacementResult;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffect;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the remembered resources of the placement preview from the remembered building list (the active list of
 * {@link GameFrame#getSecondarySpriteEffects}), in list order.
 * <p>
 * A Vespene Geyser image, and the image of a gas building standing on a geyser, is remembered as a Vespene Geyser, and a mineral field image as
 * its mineral field type; each covers the units.dat placement box of that type centered on the image. The geyser test reads only Vespene Geyser
 * images whose remembered type can be selected ({@link UnitPicker#canBeSelected(int)}).
 */
final class RememberedResources implements PlacementResult.Memory {
    /**
     * The unit type of an image that is not a remembered resource.
     */
    private static final int NO_TYPE = -1;
    /**
     * The divisor of a placement box size that gives the distance from the image position to the box's first edge.
     */
    private static final int FOOTPRINT_CENTER_DIVISOR = 2;
    /**
     * The sides of a placement box along one axis, each widened by the resource distance.
     */
    private static final int SIDES_PER_AXIS = 2;

    /**
     * Returns the unit type a remembered image stands for in the resource distance test.
     *
     * @param spriteTypeId the image's sprites.dat entry
     * @return the units.dat type, or {@link #NO_TYPE} for an image that is not a resource
     */
    private static int getResourceType(int spriteTypeId) {
        return switch (spriteTypeId) {
            case SpriteIds.EXTRACTOR, SpriteIds.ASSIMILATOR, SpriteIds.REFINERY, SpriteIds.VESPENE_GEYSER -> ScrUnitIds.RESOURCE_VESPENE_GEYSER;
            case SpriteIds.MINERAL_FIELD_TYPE1 -> ScrUnitIds.RESOURCE_MINERAL_FIELD;
            case SpriteIds.MINERAL_FIELD_TYPE2 -> ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2;
            case SpriteIds.MINERAL_FIELD_TYPE3 -> ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3;
            default -> NO_TYPE;
        };
    }


    private final @NotNull ScrGameView view;

    /**
     * Creates a {@link RememberedResources} instance reading the remembered building list of a game view.
     *
     * @param view the game view
     */
    RememberedResources(@NotNull ScrGameView view) {
        this.view = view;
    }

    /**
     * Tests whether the placement box of a remembered resource, centered on its image, lies within the distance of a tile. The box's first tile is
     * the image position less half the box (signed WORDs halved toward zero), divided by the tile size toward zero; the tile differences are compared
     * unsigned with the box size in tiles widened by the distance on both sides.
     *
     * @param tileX    the tile column
     * @param tileY    the tile row
     * @param distance the distance in tiles around the placement box
     * @return {@code true} if a remembered resource's placement box lies within {@code distance} tiles of the tile, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public boolean hasResourceNear(int tileX, int tileY, int distance) {
        GameFrame frame = this.view.getGameFrame();

        if (frame == null) {
            return false;
        }

        ScrUnitTypeView units = this.view.requireGameData().getUnitTypes();
        SpriteEffectPool remembered = frame.getSecondarySpriteEffects();

        for (int index = remembered.getFirstActiveIndex(); index >= 0; index = remembered.getNextIndex(index)) {
            Sprite sprite = remembered.getSpriteEffect(index).getSprite();
            int type = getResourceType(sprite.getSpriteTypeId());

            if (type == NO_TYPE) {
                continue;
            }

            int width = (short) units.getPlacementWidth(type);
            int height = (short) units.getPlacementHeight(type);
            int left = ((short) sprite.getX() - width / FOOTPRINT_CENTER_DIVISOR) / TerrainTileset.TILE_SIZE;
            int top = ((short) sprite.getY() - height / FOOTPRINT_CENTER_DIVISOR) / TerrainTileset.TILE_SIZE;

            if (Integer.compareUnsigned(tileX - left + distance, width / TerrainTileset.TILE_SIZE + SIDES_PER_AXIS * distance) < 0 &&
                Integer.compareUnsigned(tileY - top + distance, height / TerrainTileset.TILE_SIZE + SIDES_PER_AXIS * distance) < 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tests whether the first remembered Vespene Geyser image whose placement box covers a position is centered at that position. The box is the
     * Vespene Geyser's placement box centered on the image (signed WORDs halved toward zero); both differences are compared unsigned.
     *
     * @param positionX the geyser center X
     * @param positionY the geyser center Y
     * @return {@code true} if that remembered Vespene Geyser image is centered at the position, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public boolean hasGeyserAt(int positionX, int positionY) {
        GameFrame frame = this.view.getGameFrame();

        if (frame == null) {
            return false;
        }

        ScrUnitTypeView units = this.view.requireGameData().getUnitTypes();
        int width = (short) units.getPlacementWidth(ScrUnitIds.RESOURCE_VESPENE_GEYSER);
        int height = (short) units.getPlacementHeight(ScrUnitIds.RESOURCE_VESPENE_GEYSER);
        SpriteEffectPool remembered = frame.getSecondarySpriteEffects();

        for (int index = remembered.getFirstActiveIndex(); index >= 0; index = remembered.getNextIndex(index)) {
            SpriteEffect entry = remembered.getSpriteEffect(index);
            Sprite sprite = entry.getSprite();

            if (sprite.getSpriteTypeId() != SpriteIds.VESPENE_GEYSER || !UnitPicker.canBeSelected(UnitPicker.getRememberedBuildingType(entry))) {
                continue;
            }

            int spriteX = (short) sprite.getX();
            int spriteY = (short) sprite.getY();

            if (Integer.compareUnsigned(spriteX + width / FOOTPRINT_CENTER_DIVISOR - positionX, width) < 0 &&
                Integer.compareUnsigned(spriteY + height / FOOTPRINT_CENTER_DIVISOR - positionY, height) < 0) {
                return spriteX == positionX && spriteY == positionY;
            }
        }

        return false;
    }
}

package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.client.command.CommandTarget;
import org.fogofwarlabs.sc4j.engine.client.command.TargetPicker;
import org.fogofwarlabs.sc4j.engine.client.world.UnitPickOrder;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.SpriteDrawOrder;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffect;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds units under the pointer and in drag rectangles from the current game state.
 * Pointer picking visits allocated pool entries and resolves candidates by the draw-order key ({@link SpriteDrawOrder#computeKey}, a higher
 * key compared unsigned wins), image coverage and placement area.
 * Rectangle collection retains spatial-finder order. Decoded image bounds are cached by image type and frame.
 * <p>
 * It is the SC:R pick order of the engine ({@link UnitPickOrder}) and the engine {@link TargetPicker} of the SC:R map input: the map input hands
 * the engine {@link org.fogofwarlabs.sc4j.engine.client.command.CommandTranslator} its presses in map pixels, and the target is the pressed point
 * with the unit picked there.
 */
public final class UnitPicker implements UnitPickOrder, TargetPicker {
    private static final int WIDTH_INDEX = 2;
    private static final int HEIGHT_INDEX = 3;
    private static final int CANVAS_WIDTH_INDEX = 4;
    private static final int CANVAS_HEIGHT_INDEX = 5;

    /**
     * The remembered building's type is the low WORD of the thingy life DWORD.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The shift of the image type in a frame bounds key ({@code imageType << 16 | frame}).
     */
    private static final int IMAGE_TYPE_SHIFT = 16;

    /**
     * Returns the type of a remembered building: the low WORD of its fog sprite effect's life value.
     *
     * @param remembered the remembered building's fog sprite effect
     * @return the units.dat type
     */
    static int getRememberedBuildingType(@NotNull SpriteEffect remembered) {
        return remembered.getLife() & WORD_MASK;
    }

    /**
     * Tests whether a unit type can be selected.
     *
     * @param unit the unit
     * @return {@code true} if its type can be selected, {@code false} otherwise
     */
    public static boolean canBeSelected(@NotNull Unit unit) {
        return canBeSelected(unit.getUnitId());
    }

    /**
     * Tests whether the type is outside the missile, scarab, spell and door exclusions.
     *
     * @param type the units.dat type
     * @return {@code true} if the type can be selected, {@code false} otherwise
     */
    public static boolean canBeSelected(int type) {
        return switch (ScrUnitIds.requireValidOrAdded(type)) {
            case ScrUnitIds.TERRAN_NUCLEAR_MISSILE, ScrUnitIds.PROTOSS_SCARAB, ScrUnitIds.SPELL_DISRUPTION_WEB, ScrUnitIds.SPELL_DARK_SWARM,
                 ScrUnitIds.SPECIAL_UPPER_LEVEL_DOOR, ScrUnitIds.SPECIAL_RIGHT_UPPER_LEVEL_DOOR, ScrUnitIds.SPECIAL_PIT_DOOR,
                 ScrUnitIds.SPECIAL_RIGHT_PIT_DOOR -> false;
            default -> true;
        };
    }

    private final @NotNull ScrGameView view;
    private final @NotNull Map<Long, int[]> frameBounds;

    /**
     * Creates a {@link UnitPicker} instance reading the given game state.
     *
     * @param view the game view
     */
    public UnitPicker(@NotNull ScrGameView view) {
        this.view        = view;
        this.frameBounds = new HashMap<>();
    }


    private boolean isInClickableBounds(@NotNull Sprite sprite, int x, int y) throws IOException {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (SpriteImage image : sprite.getImages()) {
            if ((image.getFlags() & SpriteImage.FLAG_CLICKABLE) == 0) {
                continue;
            }

            int[] bounds = this.getImageBounds(sprite, image);

            if (bounds == null) {
                continue;
            }

            left   = Math.min(left, bounds[0]);
            top    = Math.min(top, bounds[1]);
            right  = Math.max(right, bounds[0] + bounds[WIDTH_INDEX]);
            bottom = Math.max(bottom, bounds[1] + bounds[HEIGHT_INDEX]);
        }

        return x >= left && x < right && y >= top && y < bottom;
    }

    /**
     * Tests every clickable image for flag {@code 0x100}, and the main image for other units.
     *
     * @throws NullPointerException if the world was built without game data
     */
    private boolean hasClickableData(@NotNull Unit unit, int x, int y) throws IOException {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null || !this.isInClickableBounds(sprite, x, y)) {
            return false;
        }

        if ((this.view.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.FLAG_100) != 0) {
            for (SpriteImage image : sprite.getImages()) {
                if ((image.getFlags() & SpriteImage.FLAG_CLICKABLE) != 0 && this.hasImageData(sprite, image, x, y)) {
                    return true;
                }
            }

            return false;
        }

        SpriteImage main = sprite.getMainImage();

        return main != null && this.hasImageData(sprite, main, x, y);
    }

    /**
     * Tests the pointer against decoded image-frame coverage.
     *
     * @throws NullPointerException if the world was built without game data
     */
    private boolean hasImageData(@NotNull Sprite sprite, @NotNull SpriteImage image, int x, int y) throws IOException {
        int[] bounds = this.getImageBounds(sprite, image);

        if (bounds == null) {
            return false;
        }

        GrpFile grp = this.view.requireGameData().getGrpFile(image.getImageTypeId());
        int frameX = x - bounds[0];

        if ((image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0) {
            frameX = bounds[WIDTH_INDEX] - 1 - frameX;
        }

        return grp.isFramePointCovered(image.getDrawnFrameIndex(), frameX, y - bounds[1]);
    }

    /**
     * Reads cached decoded frame bounds and applies the sprite and image offsets.
     *
     * @return the left, top, width and height, or {@code null} without a valid graphics frame
     * @throws NullPointerException if the world was built without game data
     */
    private int @Nullable [] getImageBounds(@NotNull Sprite sprite, @NotNull SpriteImage image) throws IOException {
        if (!image.hasGraphics()) {
            return null;
        }

        GrpFile grp = this.view.requireGameData().getGrpFile(image.getImageTypeId());
        int frameIndex = image.getDrawnFrameIndex();

        if (frameIndex < 0 || frameIndex >= grp.getFrameCount()) {
            return null;
        }

        long key = (long) image.getImageTypeId() << IMAGE_TYPE_SHIFT | frameIndex;
        int[] frame = this.frameBounds.get(key);

        if (frame == null) {
            GrpFile.Frame decoded = grp.decodeFrame(frameIndex);

            frame = new int[]{decoded.getXOffset(), decoded.getYOffset(), decoded.getFrameWidth(), decoded.getFrameHeight(), grp.getWidth(),
                              grp.getHeight()};

            this.frameBounds.put(key, frame);
        }

        int x = sprite.getX() + image.getOffsetX();
        int y = sprite.getY() + image.getOffsetY();

        if ((image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0) {
            x += frame[CANVAS_WIDTH_INDEX] / 2 - (frame[0] + frame[WIDTH_INDEX]);
        } else {
            x += frame[0] - frame[CANVAS_WIDTH_INDEX] / 2;
        }

        if ((image.getFlags() & SpriteImage.FLAG_Y_FROZEN) != 0) {
            y = image.getFrozenMapY();
        } else {
            y += frame[1] - frame[CANVAS_HEIGHT_INDEX] / 2;
        }

        return new int[]{x, y, frame[WIDTH_INDEX], frame[HEIGHT_INDEX]};
    }

    /**
     * Finds the unit under a map position.
     *
     * @param x the map x coordinate
     * @param y the map y coordinate
     * @return the picked unit, or {@code null}
     * @throws IOException          if a unit image cannot be read
     * @throws NullPointerException if the world was built without game data
     */
    public @Nullable Unit pickUnit(int x, int y) throws IOException {
        UnitPool pool = this.view.getUnitSystem().getUnitPool();

        if (pool == null) {
            return null;
        }

        ScrUnitTypeView units = this.view.requireGameData().getUnitTypes();
        Unit best = null;
        int bestKey = 0;
        int bestSize = 0;

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit candidate = pool.getUnit(index);
            Sprite sprite = candidate.getSpriteOrNull();

            if (sprite == null || (sprite.getFlags() & Sprite.FLAG_HIDDEN) != 0 || !this.isInClickableBounds(sprite, x, y)) {
                continue;
            }

            Unit parent = candidate.getSubunit();
            Unit unit = (units.getFlags(candidate.getUnitId()) & UnitTypeFlags.SUBUNIT) != 0 && parent != null ? parent : candidate;
            Sprite unitSprite = unit.getSpriteOrNull();

            if (!canBeSelected(unit) || unitSprite == null) {
                continue;
            }

            int unitKey = SpriteDrawOrder.computeKey(unitSprite);

            if (best == null) {
                best     = unit;
                bestKey  = unitKey;
                bestSize = units.getPlacementWidth(unit.getUnitId()) * units.getPlacementHeight(unit.getUnitId());

                continue;
            }

            if (Integer.compareUnsigned(bestKey, unitKey) < 0) {
                if (this.hasClickableData(unit, x, y) || unit.getSubunit() != null && this.hasClickableData(unit.getSubunit(), x, y)) {
                    best     = unit;
                    bestKey  = unitKey;
                    bestSize = unitSprite.getWidth() * unitSprite.getHeight();

                    continue;
                }
            } else {
                if (this.hasClickableData(best, x, y)) {
                    continue;
                }

                if (best.getSubunit() != null && this.hasClickableData(best.getSubunit(), x, y)) {
                    continue;
                }
            }

            if (units.getPlacementWidth(unit.getUnitId()) * units.getPlacementHeight(unit.getUnitId()) < bestSize) {
                best     = unit;
                bestKey  = unitKey;
                bestSize = units.getPlacementWidth(unit.getUnitId()) * units.getPlacementHeight(unit.getUnitId());
            }
        }

        return best;
    }

    /**
     * Finds the unit picked at a world position ({@link #pickUnit}).
     *
     * @param x the world x
     * @param y the world y
     * @return the unit, or {@code null} if no unit is picked there
     * @throws UncheckedIOException if a unit image cannot be read
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public @Nullable Unit findUnitAt(int x, int y) {
        try {
            return this.pickUnit(x, y);
        } catch (IOException exception) {
            throw Exceptions.newUncheckedIOException(exception);
        }
    }

    /**
     * Picks the target at a map position: the pressed point and the unit picked there ({@link #findUnitAt}).
     *
     * @param x the map x coordinate
     * @param y the map y coordinate
     * @return the pressed point with the unit picked there, if any
     * @throws UncheckedIOException if a unit image cannot be read
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public @NotNull CommandTarget pick(float x, float y) {
        int mapX = (int) Math.floor(x);
        int mapY = (int) Math.floor(y);

        return CommandTarget.at(mapX, mapY, this.findUnitAt(mapX, mapY));
    }

    /**
     * Finds the remembered building under a map position: the first entry of the remembered building list (the active list of {@link
     * GameFrame#getSecondarySpriteEffects}) whose type (thingy WORD) can be selected ({@link #canBeSelected(int)}) and whose fog sprite covers the
     * point. The covered box is measured from the sprite position plus half the units.dat placement box (signed WORDs halved toward zero) and spans
     * the sprite's width and height bytes toward the top-left, both differences compared unsigned.
     * <p>
     * Its callers are the click marker (targeted orders, the rally point shown on selection) and the right click; no left-click selection reads the
     * list, so a remembered building is never selected.
     *
     * @param x the map x coordinate
     * @param y the map y coordinate
     * @return the remembered building, or {@code null}
     * @throws NullPointerException if the world was built without game data
     */
    public @Nullable SpriteEffect pickRememberedBuilding(int x, int y) {
        GameFrame frame = this.view.getGameFrame();

        if (frame == null) {
            return null;
        }

        ScrUnitTypeView units = this.view.requireGameData().getUnitTypes();
        SpriteEffectPool remembered = frame.getSecondarySpriteEffects();

        for (int index = remembered.getFirstActiveIndex(); index >= 0; index = remembered.getNextIndex(index)) {
            SpriteEffect entry = remembered.getSpriteEffect(index);
            int type = getRememberedBuildingType(entry);

            if (!canBeSelected(type)) {
                continue;
            }

            Sprite sprite = entry.getSprite();
            int column = (short) units.getPlacementWidth(type) / 2 - x + (short) sprite.getX();

            if (Integer.compareUnsigned(column, sprite.getWidth()) >= 0) {
                continue;
            }

            int row = (short) units.getPlacementHeight(type) / 2 - y + (short) sprite.getY();

            if (Integer.compareUnsigned(row, sprite.getHeight()) < 0) {
                return entry;
            }
        }

        return null;
    }

    /**
     * Collects the expanded rectangle query in {@link UnitSpatialIndex#findAllInRectangle} order. Hidden sprites
     * are not in the finder; turrets and unselectable types are listed and left to the selection filter ({@link ScrSelectionRules}).
     *
     * @param left   the left map coordinate
     * @param top    the top map coordinate
     * @param right  the right map coordinate
     * @param bottom the bottom map coordinate
     * @param result the list receiving the units; it is cleared first
     * @throws UnsupportedOperationException if the unit pool is not initialized
     * @throws NullPointerException          if the world was built without game data
     */
    public void collect(int left, int top, int right, int bottom, @NotNull List<Unit> result) {
        result.clear();

        UnitSpatialIndex index = this.view.getUnitSystem().getUnitSpatialIndex();

        if (index == null) {
            return;
        }

        for (int poolIndex : index.findAllInRectangle(this.view.requireGameData().getUnitTypes(), left, top, right, bottom)) {
            result.add(this.view.getUnitSystem().requireUnitPool().getUnit(poolIndex));
        }
    }
}

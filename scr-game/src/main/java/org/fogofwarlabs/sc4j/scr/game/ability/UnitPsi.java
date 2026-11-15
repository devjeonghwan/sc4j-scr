package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.visibility.SpriteVisibility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Retains pylon membership and the power mask sampled from the original psi-field graphics.
 * <p>
 * The mask holds a cell per tile of the psi field: the graphics frame gives a quadrant, mirrored into the other quadrants, and each cell is sampled
 * at its tile center.
 */
public final class UnitPsi {
    /**
     * {@code 1/2} tile: the rounding of the quadrant size and the sample point inside a tile.
     */
    private static final int HALF_TILE_SIZE = TerrainTileset.TILE_SIZE / 2;
    /**
     * The mirrored quadrants of the coverage mask ({@code 2} times the sampled columns and {@code 2} times the sampled rows).
     */
    private static final int MASK_QUADRANTS = 4;
    /**
     * The halves of a coverage mask row: a mask row holds the sampled columns and their mirror, so its width is {@code 2} times the sampled columns.
     */
    private static final int MASK_ROW_HALVES = 2;
    /**
     * The mask of the player type byte.
     */
    private static final int BYTE_MASK = 0xFF;

    private final @NotNull Game game;

    private @Nullable Unit firstProvider;

    private boolean @Nullable [] coverage;
    private int horizontalRadius;
    private int verticalRadius;

    /**
     * Creates a {@link UnitPsi} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitPsi(@NotNull Game game) {
        this.game = game;

        this.firstProvider = null;

        this.coverage         = null;
        this.horizontalRadius = 0;
        this.verticalRadius   = 0;
    }

    /**
     * @return the one-based pool index of the first psi provider, or {@code 0} if there is none
     */
    public int getFirstProviderIndex() {
        return this.firstProvider == null ? 0 : this.firstProvider.getIndex();
    }

    /**
     * Tests whether a retained pylon of the owner powers a building center.
     *
     * @param owner     the player requiring power
     * @param positionX the building center horizontal coordinate
     * @param positionY the building center vertical coordinate
     * @return {@code true} if a retained same-owner pylon covers the point, {@code false} otherwise
     * @throws IllegalStateException if the provider has no sprite
     */
    public boolean isPowered(int owner, int positionX, int positionY) {
        for (Unit provider = this.firstProvider; provider != null; provider = provider.getNextPsiProvider()) {
            if (provider.getOwnerIndex() != owner) {
                continue;
            }

            Sprite sprite = provider.requireSprite();
            int differenceX = positionX - sprite.getX();
            int differenceY = positionY - sprite.getY();

            if (Math.abs(differenceX) >= this.horizontalRadius || Math.abs(differenceY) >= this.verticalRadius) {
                continue;
            }

            int column = (this.horizontalRadius + differenceX) / TerrainTileset.TILE_SIZE;
            int row = (this.verticalRadius + differenceY) / TerrainTileset.TILE_SIZE;

            if (this.coverage[row * (this.horizontalRadius / HALF_TILE_SIZE) + column]) {
                return true;
            }
        }

        return false;
    }


    /**
     * Initializes the provider once, then completes its initialization order.
     *
     * @param unit             the completed pylon
     * @param instructionLimit the image and order instruction budget
     * @throws IllegalStateException if the unit is not a Pylon
     * @throws NullPointerException  if the world was built without game data
     */
    public void initializeProvider(@NotNull Unit unit, int instructionLimit) {
        if (unit.getUnitId() != ScrUnitIds.PROTOSS_PYLON) {
            throw Exceptions.newIllegalStateException("Psi provider initialization requires a Pylon.");
        }

        this.addProvider(unit, instructionLimit);
        this.game.getOrders().issueUntargeted(
                unit,
                this.game.requireGameData().getUnitTypes().getReturnToIdleOrder(ScrUnitIds.PROTOSS_PYLON),
                instructionLimit
        );
        this.game.getOrders().completeOrder(unit, instructionLimit);
    }

    /**
     * Links a Pylon outside the provider list in front of it and creates its hidden psi field sprite.
     *
     * @param unit             the Pylon
     * @param instructionLimit the image instruction budget
     * @throws IllegalStateException if the unit has no sprite
     */
    private void addProvider(@NotNull Unit unit, int instructionLimit) {
        if (unit == this.firstProvider || unit.getPreviousPsiProvider() != null || unit.getNextPsiProvider() != null) {
            return;
        }

        if (this.coverage == null) {
            this.initializeCoverage();
        }

        unit.setNextPsiProvider(this.firstProvider);
        unit.setPreviousPsiProvider(null);

        if (this.firstProvider != null) {
            this.firstProvider.setPreviousPsiProvider(unit);
        }

        this.firstProvider = unit;

        Sprite source = unit.requireSprite();
        Sprite field = this.game.getIscriptExecutor().createSprite(SpriteIds.PSI_FIELD_RIGHT_UPPER,
                                                                   source.getX(), source.getY(),
                                                                   0,
                                                                   null,
                                                                   instructionLimit);

        unit.setPsiFieldSprite(field);

        if (field != null) {
            field.setFlags(field.getFlags() | Sprite.FLAG_HIDDEN);
            SpriteVisibility.apply(field, 0, 0);
        }

        this.game.setBuildingPowerRefresh(1);
    }

    /**
     * Samples the coverage mask from the first frame of the psi field graphics.
     *
     * @throws IllegalStateException if the psi field graphics cannot be read
     * @throws NullPointerException  if the world was built without game data
     */
    private void initializeCoverage() {
        try {
            GrpFile graphics = this.game.requireGameData().getGrpFile(ImageIds.PSI_FIELD1_RIGHT_UPPER);
            int columns = (graphics.getFrameWidth(0) + (HALF_TILE_SIZE - 1)) / TerrainTileset.TILE_SIZE;
            int rows = (graphics.getFrameHeight(0) + (HALF_TILE_SIZE - 1)) / TerrainTileset.TILE_SIZE;
            boolean[] mask = new boolean[columns * rows * MASK_QUADRANTS];

            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    boolean covered = graphics.isFramePointCovered(0,
                                                                   column * TerrainTileset.TILE_SIZE + HALF_TILE_SIZE,
                                                                   row * TerrainTileset.TILE_SIZE + HALF_TILE_SIZE);
                    int upperMiddle = (row * MASK_ROW_HALVES + 1) * columns;
                    int lowerMiddle = (rows * MASK_QUADRANTS - row * MASK_ROW_HALVES - 1) * columns;

                    mask[upperMiddle + column]     = covered;
                    mask[upperMiddle - column - 1] = covered;
                    mask[lowerMiddle + column]     = covered;
                    mask[lowerMiddle - column - 1] = covered;
                }
            }

            this.horizontalRadius = columns * TerrainTileset.TILE_SIZE;
            this.verticalRadius   = rows * TerrainTileset.TILE_SIZE;
            this.coverage         = mask;
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "Failed to initialize the psionic coverage mask.");
        }
    }

    /**
     * Takes every completed Pylon of the active unit list out of the provider list and deletes its psi field sprite, as the saved game writer does
     * before it writes the images: a saved Pylon holds neither provider links nor a field sprite. {@link #restoreProviders} adds them again.
     *
     * @throws UnsupportedOperationException if the unit pool is not initialized
     */
    public void releaseProviders() {
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();

        for (int poolIndex = pool.getFirstCreatedIndex(true); poolIndex != 0; poolIndex = pool.getNextCreatedIndex(poolIndex)) {
            Unit unit = pool.getUnit(poolIndex);

            if (unit.getUnitId() != ScrUnitIds.PROTOSS_PYLON || (unit.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
                continue;
            }

            Unit previous = unit.getPreviousPsiProvider();
            Unit next = unit.getNextPsiProvider();

            this.deleteFieldSprite(unit);

            if (previous != null) {
                previous.setNextPsiProvider(next);
            }

            if (next != null) {
                next.setPreviousPsiProvider(previous);
            }

            if (this.firstProvider == unit) {
                this.firstProvider = next;
            }

            unit.setNextPsiProvider(null);
            unit.setPreviousPsiProvider(null);
        }
    }

    /**
     * Adds every completed Pylon of the active unit list to the provider list again, after the saved game writer wrote the orders and after the saved
     * game reader read every part: a Pylon outside the list is linked in front with a new psi field sprite, and every completed Pylon returns to its
     * idle order; one of a rescuable owner (type {@link PlayerTypes#RESCUE_PASSIVE}) then receives {@link ScrOrderIds#RESCUE_PASSIVE}.
     *
     * @param instructionLimit the image and order instruction budget
     * @throws UnsupportedOperationException if the unit pool is not initialized
     */
    public void restoreProviders(int instructionLimit) {
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();

        for (int poolIndex = pool.getFirstCreatedIndex(true); poolIndex != 0; poolIndex = pool.getNextCreatedIndex(poolIndex)) {
            Unit unit = pool.getUnit(poolIndex);

            if (unit.getUnitId() != ScrUnitIds.PROTOSS_PYLON || (unit.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
                continue;
            }

            Player owner = this.game.getPlayerTable().getPlayer(unit.getOwnerIndex());

            this.initializeProvider(unit, instructionLimit);

            if (owner != null && (owner.getType() & BYTE_MASK) == PlayerTypes.RESCUE_PASSIVE) {
                this.game.getOrders().issueUntargeted(unit, ScrOrderIds.RESCUE_PASSIVE, instructionLimit);
            }
        }
    }

    /**
     * Removes power membership and its separate pooled field sprite during pylon destruction.
     *
     * @param unit the destroyed pylon
     */
    public void removeProvider(@NotNull Unit unit) {
        Unit previous = unit.getPreviousPsiProvider();
        Unit next = unit.getNextPsiProvider();

        if (unit == this.firstProvider || previous != null) {
            if (previous == null) {
                this.firstProvider = next;
            } else {
                previous.setNextPsiProvider(next);
            }

            if (next != null) {
                next.setPreviousPsiProvider(previous);
            }

            unit.setPreviousPsiProvider(null);
            unit.setNextPsiProvider(null);
        }

        this.game.setBuildingPowerRefresh(1);
        this.deleteFieldSprite(unit);
    }

    /**
     * Deletes the Pylon's psi field sprite with its images, if it has one.
     *
     * @param unit the Pylon
     * @throws IllegalStateException         if the psi field sprite cannot leave its row
     * @throws UnsupportedOperationException if the image pool or the sprite pool is not initialized
     */
    private void deleteFieldSprite(@NotNull Unit unit) {
        Sprite sprite = unit.getPsiFieldSprite();

        if (sprite == null) {
            return;
        }

        for (SpriteImage image : sprite.getImages()) {
            this.game.getSpriteSystem().requireImagePool().recycle(image);
        }

        sprite.clearImages();

        if (!this.game.getSpriteSystem().requireSpritePool().recycleEmptySprite(sprite, this.game.getMap().getHeight())) {
            throw Exceptions.newIllegalStateException("Psi field removal could not unlink its sprite row.");
        }

        unit.setPsiFieldSprite(null);
    }
}

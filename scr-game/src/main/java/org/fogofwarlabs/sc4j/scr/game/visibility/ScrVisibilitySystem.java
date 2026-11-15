package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.fogofwarlabs.sc4j.engine.visibility.VisibilitySystem;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serves the SC:R visibility as the engine {@link VisibilitySystem}: a view over the native stores. A point is visible or explored for a player
 * when the player's bit of the low or second byte of the tile flags ({@link MapTileFlags#NOT_VISIBLE}, {@link MapTileFlags#NOT_EXPLORED_SHIFT})
 * is clear; a unit is in a player's sight when the player's bit of its sprite's visibility flags is set, and a unit that requires a detector is
 * detected by the players of its detection flags. The sight of a unit is {@link UnitVisibility#updateFog} and its range
 * {@link UnitVisibility#calculateSightRange} in pixels; cloaking is {@link UnitCloaking#cloakUnit} and {@link UnitCloaking#decloakUnit}.
 * <p>
 * The view holds no state and is no state hash contributor: the SC:R game mixes the stores.
 */
public final class ScrVisibilitySystem implements VisibilitySystem {
    private @Nullable Game game;

    /**
     * Creates a {@link ScrVisibilitySystem} instance; it serves the SC:R game it finds when it is attached.
     */
    public ScrVisibilitySystem() {
        this.game = null;
    }

    /**
     * Resolves what this system uses from the game. Called once, after every system exists.
     * <p>
     * Binds the SC:R game system of the game.
     *
     * @param game the game
     */
    @Override
    public void attach(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game) {
        this.game = game.getModSystem(Game.SYSTEM_KEY);
    }

    /**
     * Checks whether a player currently sees a point.
     *
     * @param player the player
     * @param x      the x coordinate
     * @param y      the y coordinate
     * @return {@code true} if the point is in the player's sight, {@code false} otherwise
     * @throws IllegalStateException if the system is not attached
     */
    @Override
    public boolean isVisible(@NotNull Player player, int x, int y) {
        return (this.requireGame().getMap().getTileFlagsAtPosition(x, y) >>> player.getIndex() & 1) == 0;
    }

    /**
     * Checks whether a player has ever seen a point.
     *
     * @param player the player
     * @param x      the x coordinate
     * @param y      the y coordinate
     * @return {@code true} if the point is explored by the player, {@code false} otherwise
     * @throws IllegalStateException if the system is not attached
     */
    @Override
    public boolean isExplored(@NotNull Player player, int x, int y) {
        return (this.requireGame().getMap().getTileFlagsAtPosition(x, y) >>> (MapTileFlags.NOT_EXPLORED_SHIFT + player.getIndex()) & 1) == 0;
    }

    /**
     * Checks whether a player sees a unit: the unit is in the player's sight and, if it is cloaked, detected by the player.
     *
     * @param player the player
     * @param unit   the unit
     * @return {@code true} if the player sees the unit, {@code false} otherwise
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     */
    @Override
    public boolean isUnitVisible(@NotNull Player player, org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        Sprite sprite = Unit.of(unit).getSpriteOrNull();

        return sprite != null && (sprite.getVisibilityFlags() >>> player.getIndex() & 1) != 0 && this.isDetected(player, unit);
    }

    /**
     * Checks whether a player detects a cloaked or burrowed unit.
     *
     * @param player the player
     * @param unit   the unit
     * @return {@code true} if the unit is not hidden from the player, {@code false} otherwise
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     */
    @Override
    public boolean isDetected(@NotNull Player player, org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        Unit scrUnit = Unit.of(unit);

        return (scrUnit.getStatus() & UnitStatusFlags.REQUIRES_DETECTOR) == 0 || (scrUnit.getDetectionFlags() >>> player.getIndex() & 1) != 0;
    }

    /**
     * Returns the current sight range of a unit, after its stat modifiers.
     * <p>
     * The range is {@link UnitVisibility#calculateSightRange} (in tiles, blindness included) times the tile size.
     *
     * @param unit the unit
     * @return the sight range in pixels
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the system is not attached
     * @throws NullPointerException     if the world was built without game data
     */
    @Override
    public int getSightRange(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        return this.requireGame().getUnitVisibility().calculateSightRange(Unit.of(unit), false) * TerrainTileset.TILE_SIZE;
    }

    /**
     * Updates the sight a unit gives its owner after it moved, changed type or changed its sight range.
     *
     * @param unit the unit
     * @throws IllegalArgumentException      if the unit is not an SC:R unit
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the fog descriptors or the sprite are missing
     */
    @Override
    public void updateSight(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        this.requireGame().getUnitVisibility().updateFog(Unit.of(unit));
    }

    /**
     * Sets whether a unit is cloaked: hidden from the players that do not detect it.
     *
     * @param unit    the unit
     * @param cloaked whether the unit is cloaked
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws IllegalStateException    if the system is not attached or a burrowed unit has no sprite
     */
    @Override
    public void setCloaked(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, boolean cloaked) {
        UnitCloaking cloaking = this.requireGame().getUnitCloaking();

        if (cloaked) {
            cloaking.cloakUnit(Unit.of(unit));
        } else {
            cloaking.decloakUnit(Unit.of(unit));
        }
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R visibility system is not attached.");
        }

        return attached;
    }
}

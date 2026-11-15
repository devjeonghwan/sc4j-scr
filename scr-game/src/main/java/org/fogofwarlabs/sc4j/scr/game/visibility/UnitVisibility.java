package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the established unit sight and initial hidden-unit reveal sequence.
 */
public final class UnitVisibility {
    /**
     * The sight range of an incomplete building that is not a Zerg building morph.
     */
    private static final int INCOMPLETE_BUILDING_SIGHT_RANGE = 4;
    /**
     * The sight range of a blinded unit.
     */
    private static final int BLIND_SIGHT_RANGE = 2;
    /**
     * The mask of the sprite flags BYTE.
     */
    private static final int BYTE_MASK = 0xFF;

    private final @NotNull Game game;

    /**
     * Creates a {@link UnitVisibility} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitVisibility(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Calculates the players that see what a unit sees: every player for a unit carrying a Flag, a transport with a loaded Flag carrier or a dropped
     * Flag, otherwise the unit's parasite players, the shared vision of a playable owner and the shared vision of every parasite player.
     *
     * @param unit the unit supplying its owner and parasite flags
     * @return the untruncated visibility DWORD passed to sight coverage
     * @throws UnsupportedOperationException if the player masks are missing
     * @throws NullPointerException          if the world was built without game data
     */
    public int calculateMask(@NotNull Unit unit) {
        // Fails before any work when the world was built without game data.
        this.game.requireGameData();

        int unitType = unit.getUnitId();

        // Every player sees what a worker carrying a Flag, or a transport carrying one, sees.
        if (this.game.getUnitQueries().isCarryingFlag(unit)) {
            return Sprite.VISIBLE_TO_ALL_PLAYERS;
        }

        if (this.game.getUnitQueries().isTransport(unit)) {
            for (int slotIndex = 0; slotIndex < Unit.LOADED_UNIT_SLOTS; slotIndex++) {
                Unit loadedUnit = this.game.getUnitTransport().getLoadedUnit(unit, slotIndex);

                if (loadedUnit != null && this.game.getUnitQueries().isCarryingFlag(loadedUnit)) {
                    return Sprite.VISIBLE_TO_ALL_PLAYERS;
                }
            }
        }

        if (unitType == ScrUnitIds.POWERUP_FLAG && unit.getPrimaryOrder() == ScrOrderIds.UNUSED_POWERUP) {
            return Sprite.VISIBLE_TO_ALL_PLAYERS;
        }

        SightCoverage coverage = this.game.getSightCoverage();

        if (coverage == null) {
            throw Exceptions.newUnsupportedOperationException("Shared vision requires initialized player masks.");
        }

        int parasiteFlags = unit.getParasiteFlags();
        int mask = parasiteFlags;

        if (unit.getOwnerIndex() < PlayerTable.PLAYABLE_PLAYER_SLOTS) {
            mask |= coverage.getSharedVisionMask(unit.getOwnerIndex());
        }

        for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
            if ((parasiteFlags & (1 << playerIndex)) != 0) {
                mask |= coverage.getSharedVisionMask(playerIndex);
            }
        }

        return mask;
    }

    /**
     * Calculates the sight descriptor index of a unit: an incomplete building that is not a Zerg building morph sees {@code 4}, a blinded unit
     * {@code 2}, a unit with its sight upgrade {@code 11}, every other unit its unit type's sight range.
     *
     * @param unit            the unit
     * @param ignoreBlindness whether a blinded unit keeps its sight range
     * @return the sight range in tiles, which is the sight descriptor index of the unit's reveal
     * @throws UnsupportedOperationException if the player upgrade storage is missing
     * @throws NullPointerException          if the world was built without game data
     */
    public int calculateSightRange(@NotNull Unit unit, boolean ignoreBlindness) {
        int status = unit.getStatus();

        if (UnitQueries.isGroundedBuilding(unit) && (status & UnitStatusFlags.COMPLETED) == 0 &&
            !UnitQueries.isBuildingMorph(this.game.requireGameData().getUnitTypes(), unit)) {
            return INCOMPLETE_BUILDING_SIGHT_RANGE;
        }

        if (!ignoreBlindness && unit.getBlindnessState() != 0) {
            return BLIND_SIGHT_RANGE;
        }

        int unitType = unit.getUnitId();
        int sightRange = this.game.requireGameData().getUnitTypes().getSightRange(unitType);

        return this.game.applyModifiers(Stat.SIGHT_RANGE, unit.getOwnerIndex(), unitType, ContentType.NO_INDEX, sightRange);
    }

    /**
     * Refreshes a unit after a relocation: a hidden unit and its turret are unhidden and the unit moves from the hidden to the active list; then
     * sight, sprite visibility, unit finder and the elevation bookkeeping of the unit and its turret are refreshed.
     *
     * @param unit           the relocated unit
     * @param foliageEnabled whether the foliage context of the unit finder registration is enabled
     * @throws UnsupportedOperationException if a sprite or the unit lists are missing
     * @throws NullPointerException          if the world was built without game data
     */
    public void refreshPosition(@NotNull Unit unit, boolean foliageEnabled) {
        Unit subunit = unit.getSubunit();
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        boolean hasSubunit = subunit != null && subunit.getUnitId() < ScrUnitIds.NONE &&
                             (units.getFlags(subunit.getUnitId()) & UnitTypeFlags.SUBUNIT) != 0;

        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("The relocation refresh requires the sprite of unit {0}.", unit.getIndex());
        }

        if ((sprite.getFlags() & Sprite.FLAG_HIDDEN) != 0) {
            sprite.setFlags(sprite.getFlags() & (~Sprite.FLAG_HIDDEN & BYTE_MASK));

            if (hasSubunit) {
                Sprite subunitSprite = subunit.getSpriteOrNull();

                if (subunitSprite == null) {
                    throw Exceptions.newUnsupportedOperationException("The relocation refresh requires the turret sprite of unit {0}.",
                                                                      unit.getIndex());
                }

                subunitSprite.setFlags(subunitSprite.getFlags() & (~Sprite.FLAG_HIDDEN & BYTE_MASK));
            }

            UnitPool pool = this.game.getUnitSystem().getUnitPool();

            if (pool == null) {
                throw Exceptions.newUnsupportedOperationException("The relocation refresh requires initialized unit lists.");
            }

            pool.transferCreatedUnitToActive(unit);
        }

        this.updateFog(unit);
        this.game.getSpriteVisibility().update(unit, this.game.getMap(), this.game.getLocalVision().getComparisonMask());
        this.game.getUnitStorageConstructor().registerSpatially(unit, foliageEnabled);
        this.updateElevation(unit);

        if (hasSubunit) {
            this.updateElevation(subunit);
        }
    }

    /**
     * Shows a hidden unit: unhides it and its turret, updates its sight, sprite visibility and unit finder registration and the elevation bookkeeping
     * of the unit and its turret, and moves it from the hidden to the active list. A unit that is not hidden is left unchanged.
     *
     * @param unit           the hidden unit
     * @param foliageEnabled whether the foliage context of the unit finder registration is enabled
     * @throws UnsupportedOperationException if a sprite or the unit lists are missing
     * @throws NullPointerException          if the world was built without game data
     */
    public void show(@NotNull Unit unit, boolean foliageEnabled) {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("Unit reveal requires the unit's sprite.");
        }

        if ((sprite.getFlags() & Sprite.FLAG_HIDDEN) == 0) {
            return;
        }

        sprite.setFlags(sprite.getFlags() & (~Sprite.FLAG_HIDDEN & BYTE_MASK));

        Unit subunit = unit.getSubunit();
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        boolean hasSubunit = subunit != null && subunit.getUnitId() < ScrUnitIds.NONE &&
                             (units.getFlags(subunit.getUnitId()) & UnitTypeFlags.SUBUNIT) != 0;

        if (hasSubunit) {
            Sprite subunitSprite = subunit.getSpriteOrNull();

            if (subunitSprite == null) {
                throw Exceptions.newUnsupportedOperationException("Subunit reveal requires the subunit's sprite.");
            }

            subunitSprite.setFlags(subunitSprite.getFlags() & (~Sprite.FLAG_HIDDEN & BYTE_MASK));
        }

        this.updateFog(unit);
        this.game.getSpriteVisibility().update(unit, this.game.getMap(), this.game.getLocalVision().getComparisonMask());
        this.game.getUnitStorageConstructor().registerSpatially(unit, foliageEnabled);
        this.updateElevation(unit);

        if (hasSubunit) {
            this.updateElevation(subunit);
        }

        UnitPool pool = this.game.getUnitSystem().getUnitPool();

        if (pool == null) {
            throw Exceptions.newUnsupportedOperationException("Unit reveal requires initialized unit lists.");
        }

        pool.transferCreatedUnitToActive(unit);
    }

    /**
     * Updates the sight of a unit with the shared player masks and the terrain overlay storage: a mask of playable players and a sight range below
     * {@code 12} reveal tiles ({@link SightCoverage#reveal(Game, int, int, boolean, int, int)}), and a ground unit sees from the height of its tile.
     * A unit of a non-playable player without parasite players, and a Nuclear Missile, has no sight.
     *
     * @param unit the unit whose sight is updated
     * @throws UnsupportedOperationException if the fog descriptors or the sprite are missing
     */
    public void updateFog(@NotNull Unit unit) {
        if (unit.getOwnerIndex() >= PlayerTable.PLAYABLE_PLAYER_SLOTS && unit.getParasiteFlags() == 0 ||
            this.game.requireGameData().getUnitTypes().isSightless(unit.getUnitId())) {
            return;
        }

        SightCoverage coverage = this.game.getSightCoverage();

        if (coverage == null) {
            throw Exceptions.newUnsupportedOperationException("Unit sight requires initialized fog descriptors.");
        }

        int visibilityMask = this.calculateMask(unit);
        int sightRange = this.calculateSightRange(unit, false);
        boolean air = (unit.getStatus() & UnitStatusFlags.FLYING) != 0;
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("Unit sight requires the unit's sprite coordinates.");
        }

        coverage.reveal(this.game, (short) sprite.getX(), (short) sprite.getY(), air, visibilityMask, sightRange);
    }

    /**
     * Releases the unit's path, clears its movement state and updates its ground elevation path flag.
     *
     * @param unit the unit
     * @throws UnsupportedOperationException if the unit has a path but the path pool is missing, or the unit has no sprite
     */
    private void updateElevation(@NotNull Unit unit) {
        if (unit.getPathIndex() >= 0) {
            PathPool pathPool = this.game.getPathSystem().getPathPool();

            if (pathPool == null) {
                throw Exceptions.newUnsupportedOperationException("Reveal requires the initialized persistent path pool.");
            }

            pathPool.release(unit.getPathIndex());
            unit.setPathIndex(-1);
        }

        unit.setMovementState(0);

        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("Elevation bookkeeping requires the unit's sprite.");
        }

        UnitInitializer.updateGroundElevationPathFlag(unit, sprite.getElevationLevel());
    }
}

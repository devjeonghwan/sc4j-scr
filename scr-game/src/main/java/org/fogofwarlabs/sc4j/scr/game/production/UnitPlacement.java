package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Evaluates building terrain, resource, power and unit-collision placement rules.
 * <p>
 * An evaluation provides overall and per-tile codes ({@link #evaluateBuilding}, {@link #evaluateAddon}, {@link #canPlaceBuilding}). On unbuildable
 * terrain with tile flag {@link MapTileFlags#FLAG_80000000}, only beacons pass.
 */
public final class UnitPlacement implements PlacementEvaluator {
    /**
     * The tile flags that block a building placed by a trigger; non-Zerg buildings are also blocked by creep.
     */
    private static final int BLOCKING_TILE_FLAGS = MapTileFlags.PARTIALLY_WALKABLE | MapTileFlags.OCCUPIED | MapTileFlags.UNBUILDABLE;
    /**
     * The terrain flags that block a Zerg building, a building judged from remembered state, and any other building.
     */
    private static final int ZERG_TERRAIN_FLAGS = MapTileFlags.PARTIALLY_WALKABLE | MapTileFlags.UNBUILDABLE;
    private static final int REMEMBERED_TERRAIN_FLAGS = MapTileFlags.PARTIALLY_WALKABLE | MapTileFlags.TEMPORARY_CREEP | MapTileFlags.UNBUILDABLE;
    private static final int TERRAIN_FLAGS = MapTileFlags.PARTIALLY_WALKABLE | MapTileFlags.HAS_CREEP | MapTileFlags.UNBUILDABLE;
    /**
     * The creep bit of the remembered-state check.
     */
    private static final int REMEMBERED_CREEP = MapTileFlags.TEMPORARY_CREEP;
    /**
     * The tiles around a site within which a resource container keeps a resource depot away.
     */
    private static final int RESOURCE_TILE_DISTANCE = 3;

    /**
     * The divisor from a placement box size to the offset of its center.
     */
    private static final int FOOTPRINT_CENTER_DIVISOR = 2;

    /**
     * Applies the unit pass of the placement check: each tile still at {@link PlacementResult#VALID} that is visible, or fogged with
     * {@code checkFogged}, is blocked by occupancy (unless the builder is the grounded building standing on it) and then by the first unit of the
     * footprint query overlapping it: {@link PlacementResult#UNDETECTED} for a unit undetected by the builder (only with {@code checkUndetected};
     * otherwise such units are skipped), {@link PlacementResult#BLOCKED} otherwise.
     *
     * @throws IllegalStateException if the builder has no sprite
     */
    private static int evaluateUnits(@NotNull ScrUnitTypeView units,
                                     @NotNull GameMap map,
                                     @NotNull UnitSpatialIndex spatial,
                                     @Nullable Unit builder,
                                     int unitType, int abilities,
                                     int tileX, int tileY,
                                     int tileWidth, int tileHeight,
                                     int positionX, int positionY,
                                     int foggedMask,
                                     boolean checkUndetected,
                                     boolean checkFogged,
                                     byte @Nullable [] codes) {
        int count = spatial.collectPlacementCandidates(positionX - units.getDimensionLeft(unitType), positionY - units.getDimensionUp(unitType),
                                                       positionX + units.getDimensionRight(unitType), positionY + units.getDimensionDown(unitType));
        Unit[] candidates = new Unit[count];

        for (int index = 0; index < count; index++) {
            candidates[index] = spatial.getPlacementCandidate(index);
        }

        boolean builderMayOccupy = builder != null && UnitQueries.isGroundedBuilding(builder) &&
                                   builder.getUnitId() != ScrUnitIds.ZERG_NYDUS_CANAL &&
                                   (builder.requireSprite().getFlags() & Sprite.FLAG_HIDDEN) == 0;
        int result = 0;

        for (int row = 0; row < tileHeight; row++) {
            for (int column = 0; column < tileWidth; column++) {
                int horizontal = tileX + column;
                int vertical = tileY + row;

                if (codes != null && codes[row * PlacementResult.ROW_STRIDE + column] != 0) {
                    continue;
                }

                int flags = map.getTileFlags(vertical * map.getWidth() + horizontal);

                if (!checkFogged && (flags & foggedMask) != 0) {
                    continue;
                }

                int code = 0;

                if ((flags & MapTileFlags.OCCUPIED) != 0 && (!builderMayOccupy || !overlapsTile(spatial, builder, horizontal, vertical))) {
                    code = PlacementResult.BLOCKED;
                } else {
                    for (Unit candidate : candidates) {
                        if (candidate == builder || (candidate.getStatus() & (UnitStatusFlags.GROUNDED_BUILDING | UnitStatusFlags.FLYING)) != 0 ||
                            (abilities & UnitTypeFlags.ADDON) != 0 && (candidate.getStatus() & UnitStatusFlags.CAN_MOVE) != 0 ||
                            units.getBaseType(candidate.getUnitId()) == ScrUnitIds.SPELL_DARK_SWARM ||
                            units.getBaseType(candidate.getUnitId()) == ScrUnitIds.SPELL_DISRUPTION_WEB) {
                            continue;
                        }

                        boolean undetected = builder != null &&
                                             (candidate.getStatus() & (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED)) != 0 &&
                                             (candidate.getDetectionFlags() & 1 << builder.getOwnerIndex()) == 0;

                        if (undetected && !checkUndetected) {
                            continue;
                        }

                        Sprite sprite = candidate.getSpriteOrNull();

                        if (sprite != null && (sprite.getFlags() & Sprite.FLAG_HIDDEN) != 0) {
                            continue;
                        }

                        if (overlapsTile(spatial, candidate, horizontal, vertical)) {
                            code = undetected ? PlacementResult.UNDETECTED : PlacementResult.BLOCKED;

                            break;
                        }
                    }
                }

                if (codes == null) {
                    if (code != 0) {
                        return code;
                    }
                } else {
                    codes[row * PlacementResult.ROW_STRIDE + column] = (byte) code;
                    result                                           = Math.max(result, code);
                }
            }
        }

        return result;
    }

    /**
     * Applies the resource query of the terrain pass: a resource container within {@link #RESOURCE_TILE_DISTANCE} tiles.
     */
    private static boolean isResourceNear(@NotNull ScrUnitTypeView units, @NotNull UnitSpatialIndex spatial, int tileX, int tileY) {
        int count = spatial.collectPlacementCandidates((tileX - RESOURCE_TILE_DISTANCE) * TerrainTileset.TILE_SIZE,
                                                       (tileY - RESOURCE_TILE_DISTANCE) * TerrainTileset.TILE_SIZE,
                                                       (tileX + (RESOURCE_TILE_DISTANCE + 1)) * TerrainTileset.TILE_SIZE,
                                                       (tileY + (RESOURCE_TILE_DISTANCE + 1)) * TerrainTileset.TILE_SIZE);

        for (int index = 0; index < count; index++) {
            if ((units.getFlags(spatial.getPlacementCandidate(index).getUnitId()) & UnitTypeFlags.RESOURCE_CONTAINER) != 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Fills the tile-code array with the code when one is supplied, and returns the code.
     */
    private static int fill(byte @Nullable [] codes, int code) {
        if (codes != null) {
            Arrays.fill(codes, (byte) code);
        }

        return code;
    }

    private static boolean overlapsTile(@NotNull UnitSpatialIndex spatial, @NotNull Unit unit, int tileX, int tileY) {
        return spatial.getCoordinate(true, unit.getMinimumHorizontalEdgeIndex()) < tileX * TerrainTileset.TILE_SIZE + TerrainTileset.TILE_SIZE &&
               spatial.getCoordinate(true, unit.getMaximumHorizontalEdgeIndex()) > tileX * TerrainTileset.TILE_SIZE &&
               spatial.getCoordinate(false, unit.getMinimumVerticalEdgeIndex()) < tileY * TerrainTileset.TILE_SIZE + TerrainTileset.TILE_SIZE &&
               spatial.getCoordinate(false, unit.getMaximumVerticalEdgeIndex()) > tileY * TerrainTileset.TILE_SIZE;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitPlacement} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitPlacement(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Checks explored terrain, creep, occupancy and unit collisions for a building footprint.
     *
     * @param builder         the constructing unit, or {@code null} for a placement preview
     * @param owner           the building owner
     * @param unitType        the proposed building type
     * @param positionX       the footprint center X
     * @param positionY       the footprint center Y
     * @param checkUndetected whether undetected units obstruct placement
     * @param checkInvisible  whether invisible tiles participate in occupancy checks
     * @return {@code true} if placement is permitted, {@code false} otherwise
     */
    public boolean canPlaceBuilding(@Nullable Unit builder,
                                    int owner,
                                    int unitType,
                                    int positionX, int positionY,
                                    boolean checkUndetected,
                                    boolean checkInvisible) {
        return this.canPlaceBuilding(builder, builder, owner, unitType, positionX, positionY, checkUndetected, checkInvisible);
    }

    /**
     * Checks a building footprint with a separate unit-collision builder. Terrain uses {@code builder}; occupancy and unit collisions
     * {@code collisionBuilder}. It is the evaluation of {@link #evaluateBuilding}, stopped at the first failing code.
     *
     * @param builder          the constructing unit for terrain checks, or {@code null}
     * @param collisionBuilder the unit excluded from and evaluating unit collisions, or {@code null}
     * @param owner            the building owner
     * @param unitType         the proposed building type
     * @param positionX        the footprint center X
     * @param positionY        the footprint center Y
     * @param checkUndetected  whether undetected units obstruct placement
     * @param checkInvisible   whether invisible tiles participate in occupancy checks
     * @return {@code true} if placement is permitted, {@code false} otherwise
     */
    public boolean canPlaceBuilding(@Nullable Unit builder,
                                    @Nullable Unit collisionBuilder,
                                    int owner,
                                    int unitType,
                                    int positionX, int positionY,
                                    boolean checkUndetected,
                                    boolean checkInvisible) {
        return this.getPlacementCode(builder, collisionBuilder, owner, unitType, positionX, positionY, checkUndetected, checkInvisible) ==
               PlacementResult.VALID;
    }

    /**
     * Returns the first failing code of a building footprint, the evaluation of {@link #canPlaceBuilding}; the build command check shows the code's
     * text when it is not {@link PlacementResult#VALID}.
     *
     * @param builder          the constructing unit for terrain checks, or {@code null}
     * @param collisionBuilder the unit excluded from and evaluating unit collisions, or {@code null}
     * @param owner            the building owner
     * @param unitType         the proposed building type
     * @param positionX        the footprint center X
     * @param positionY        the footprint center Y
     * @param checkUndetected  whether undetected units obstruct placement
     * @param checkInvisible   whether invisible tiles participate in occupancy checks
     * @return the placement code
     * @throws NullPointerException if the world was built without game data
     */
    public int getPlacementCode(@Nullable Unit builder,
                                @Nullable Unit collisionBuilder,
                                int owner,
                                int unitType,
                                int positionX, int positionY,
                                boolean checkUndetected,
                                boolean checkInvisible) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int tileX = positionX - units.getPlacementWidth(unitType) / FOOTPRINT_CENTER_DIVISOR >> TerrainTileset.TILE_SHIFT;
        int tileY = positionY - units.getPlacementHeight(unitType) / FOOTPRINT_CENTER_DIVISOR >> TerrainTileset.TILE_SHIFT;

        return this.evaluate(builder,
                             collisionBuilder,
                             owner,
                             unitType,
                             tileX, tileY,
                             positionX, positionY,
                             false,
                             checkUndetected,
                             checkInvisible,
                             null,
                             null,
                             true);
    }

    /**
     * Checks a trigger-created building's site: as {@link #canPlaceBuilding} without a builder, except that a building needing a psionic matrix does
     * not need one there. The normal footprint check would stop at {@link PlacementResult#NO_POWER}.
     *
     * @param owner     the building owner
     * @param unitType  the building type
     * @param positionX the footprint center X
     * @param positionY the footprint center Y
     * @return {@code true} if the trigger may create the building there, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public boolean canPlaceTriggerBuilding(int owner, int unitType, int positionX, int positionY) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int tileX = positionX - units.getPlacementWidth(unitType) / FOOTPRINT_CENTER_DIVISOR >> TerrainTileset.TILE_SHIFT;
        int tileY = positionY - units.getPlacementHeight(unitType) / FOOTPRINT_CENTER_DIVISOR >> TerrainTileset.TILE_SHIFT;

        return this.evaluate(null, null, owner, unitType, tileX, tileY, positionX, positionY, false, true, false, null, null, false) ==
               PlacementResult.VALID;
    }


    /**
     * Evaluates a building footprint with its per-tile codes; the simulation's own placement tests are this evaluation with {@code rememberedState}
     * off (see {@link #canPlaceBuilding}). Unit positions and orders are unchanged; the finder retains its reached query scratch state.
     *
     * @param builder         the placing unit, or {@code null}
     * @param owner           the player whose exploration, visibility and psi fields apply
     * @param unitType        the building type
     * @param tileX           the left footprint tile
     * @param tileY           the top footprint tile
     * @param rememberedState whether fogged tiles are judged from remembered state (the placement preview)
     * @param checkUndetected whether undetected units obstruct with {@link PlacementResult#UNDETECTED}
     * @param checkFogged     whether fogged tiles take part in the creep, occupancy and unit checks
     * @param memory          the remembered images for {@code rememberedState}, or {@code null} for none
     * @return the result with the overall and per-tile codes
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public @NotNull PlacementResult evaluateBuilding(@Nullable Unit builder,
                                                     int owner,
                                                     int unitType,
                                                     int tileX, int tileY,
                                                     boolean rememberedState,
                                                     boolean checkUndetected,
                                                     boolean checkFogged,
                                                     PlacementResult.@Nullable Memory memory) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int width = units.getPlacementWidth(unitType);
        int height = units.getPlacementHeight(unitType);
        byte[] codes = new byte[PlacementResult.TILE_CODE_COUNT];
        int code = this.evaluate(builder,
                                 builder,
                                 owner,
                                 unitType,
                                 tileX, tileY,
                                 tileX * TerrainTileset.TILE_SIZE + width / FOOTPRINT_CENTER_DIVISOR,
                                 tileY * TerrainTileset.TILE_SIZE + height / FOOTPRINT_CENTER_DIVISOR,
                                 rememberedState,
                                 checkUndetected,
                                 checkFogged,
                                 memory,
                                 codes,
                                 true);

        return new PlacementResult(code, tileX, tileY, width / TerrainTileset.TILE_SIZE, height / TerrainTileset.TILE_SIZE, codes, null);
    }

    /**
     * Evaluates an add-on footprint and then the footprint of its parent building placed at the add-on's offset: always with {@code rememberedState}
     * (the placement preview), otherwise only when the add-on footprint is valid. The overall code is the add-on's, or the parent's when the add-on
     * is valid.
     *
     * @param builder         the parent building placing the add-on
     * @param owner           the player whose exploration, visibility and psi fields apply
     * @param addonType       the add-on type
     * @param tileX           the add-on's left footprint tile
     * @param tileY           the add-on's top footprint tile
     * @param rememberedState whether both checks judge fogged tiles from remembered state
     * @param checkUndetected whether undetected units obstruct in both checks
     * @param checkFogged     whether fogged tiles take part in both checks
     * @param memory          the remembered images for {@code rememberedState}, or {@code null} for none
     * @return the add-on result, carrying the parent's result when it was evaluated
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public @NotNull PlacementResult evaluateAddon(@NotNull Unit builder,
                                                  int owner,
                                                  int addonType,
                                                  int tileX, int tileY,
                                                  boolean rememberedState,
                                                  boolean checkUndetected,
                                                  boolean checkFogged,
                                                  PlacementResult.@Nullable Memory memory) {
        PlacementResult addon = this.evaluateBuilding(builder, owner, addonType, tileX, tileY, rememberedState, checkUndetected, checkFogged, memory);

        if (!rememberedState && !addon.isValid()) {
            return addon;
        }

        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        // The parent's tile is the add-on tile less the add-on offset in tiles.
        int parentX = tileX - units.getAddonHorizontalOffset(addonType) / TerrainTileset.TILE_SIZE;
        int parentY = tileY - units.getAddonVerticalOffset(addonType) / TerrainTileset.TILE_SIZE;
        PlacementResult parent = this.evaluateBuilding(builder,
                                                       owner,
                                                       builder.getUnitId(),
                                                       parentX, parentY,
                                                       rememberedState,
                                                       checkUndetected,
                                                       checkFogged,
                                                       memory);

        return new PlacementResult(addon.getFootprintCode(), tileX, tileY, addon.getTileWidth(), addon.getTileHeight(), addon.getTileCodes(), parent);
    }

    /**
     * Evaluates a building footprint for a trigger move. Off the map is {@link PlacementResult#OFF_MAP}; the terrain pass gives
     * {@link PlacementResult#BLOCKED} for a tile with the blocking flags (creep also blocks a non-Zerg building), except the beacons on tile flag
     * {@link MapTileFlags#FLAG_80000000}, else {@link PlacementResult#NEAR_RESOURCES} for a resource depot near resources; the unit pass follows
     * without a builder, skipping the owner's fogged tiles for a human owner.
     *
     * @param owner    the building owner
     * @param unitType the building type
     * @param tileX    the left footprint tile
     * @param tileY    the top footprint tile
     * @return the highest code, {@link PlacementResult#VALID} when the site is valid
     * @throws IllegalStateException if terrain or spatial storage is missing
     * @throws NullPointerException  if the world was built without game data
     */
    public int evaluateTriggerMove(int owner, int unitType, int tileX, int tileY) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        GameMap map = this.game.getMap();
        UnitSpatialIndex spatial = this.game.getUnitSystem().getUnitSpatialIndex();

        if (spatial == null || !map.hasTileFlags()) {
            throw Exceptions.newIllegalStateException("Building placement requires terrain and spatial storage.");
        }

        int width = units.getPlacementWidth(unitType);
        int height = units.getPlacementHeight(unitType);
        int tileWidth = (short) width / TerrainTileset.TILE_SIZE;
        int tileHeight = (short) height / TerrainTileset.TILE_SIZE;

        tileX = (short) tileX;
        tileY = (short) tileY;

        if (tileX < 0 || tileY < 0 || tileX + tileWidth > map.getWidth() || tileY + tileHeight > map.getHeight()) {
            return PlacementResult.OFF_MAP;
        }

        boolean zerg = (units.getGroupFlags(unitType) & UnitGroupFlags.ZERG) != 0;
        int terrainMask = zerg ? BLOCKING_TILE_FLAGS : BLOCKING_TILE_FLAGS | MapTileFlags.HAS_CREEP;
        boolean beacon = units.isBeacon(unitType);
        int abilities = units.getFlags(unitType);
        byte[] codes = new byte[PlacementResult.TILE_CODE_COUNT];
        int result = 0;

        for (int row = 0; row < tileHeight; row++) {
            for (int column = 0; column < tileWidth; column++) {
                int horizontal = tileX + column;
                int vertical = tileY + row;
                int flags = map.getTileFlags(vertical * map.getWidth() + horizontal);
                int code = 0;

                if ((flags & terrainMask) != 0) {
                    if (!(beacon && (flags & MapTileFlags.FLAG_80000000) != 0)) {
                        code = PlacementResult.BLOCKED;
                    }
                } else if ((abilities & UnitTypeFlags.RESOURCE_DEPOT) != 0 && isResourceNear(units, spatial, horizontal, vertical)) {
                    code = PlacementResult.NEAR_RESOURCES;
                }

                codes[row * PlacementResult.ROW_STRIDE + column] = (byte) code;
                result                                           = Math.max(result, code);
            }
        }

        // The owner's fogged tiles are skipped for a human owner only.
        Player ownerRecord = this.game.getPlayerTable().getPlayer(owner);
        int foggedMask = ownerRecord != null && ownerRecord.getType() == PlayerTypes.HUMAN ? 1 << owner : 0;

        return Math.max(result,
                        evaluateUnits(units,
                                      map,
                                      spatial,
                                      null,
                                      unitType, abilities,
                                      tileX, tileY,
                                      tileWidth, tileHeight,
                                      tileX * TerrainTileset.TILE_SIZE + (short) width / FOOTPRINT_CENTER_DIVISOR,
                                      tileY * TerrainTileset.TILE_SIZE + (short) height / FOOTPRINT_CENTER_DIVISOR,
                                      foggedMask,
                                      true,
                                      false,
                                      codes));
    }

    /**
     * Evaluates a footprint. With {@code codes} it computes every tile's code and returns the maximum; without it, it returns the first failing code
     * (any nonzero code decides the boolean tests).
     *
     * @param builder          the constructing unit for terrain checks, or {@code null}
     * @param collisionBuilder the unit excluded from and evaluating unit collisions, or {@code null}
     * @param owner            the player whose exploration, visibility and psi fields apply
     * @param unitType         the building type
     * @param tileX            the left footprint tile
     * @param tileY            the top footprint tile
     * @param positionX        the footprint center X
     * @param positionY        the footprint center Y
     * @param remembered       whether fogged tiles are judged from remembered state
     * @param checkUndetected  whether undetected units obstruct placement
     * @param checkFogged      whether fogged tiles take part in the creep, occupancy and unit checks
     * @param memory           the remembered images for {@code remembered}, or {@code null} for none
     * @param codes            the per-tile codes to fill, or {@code null}
     * @param checkPower       whether a building that requires a psionic matrix must be powered
     * @return the placement code of the footprint
     * @throws IllegalStateException if terrain or spatial storage is missing, or the candidate has no sprite
     * @throws NullPointerException  if the world was built without game data
     */
    private int evaluate(@Nullable Unit builder,
                         @Nullable Unit collisionBuilder,
                         int owner,
                         int unitType,
                         int tileX, int tileY,
                         int positionX, int positionY,
                         boolean remembered,
                         boolean checkUndetected,
                         boolean checkFogged,
                         PlacementResult.@Nullable Memory memory,
                         byte @Nullable [] codes,
                         boolean checkPower) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        GameMap map = this.game.getMap();
        UnitSpatialIndex spatial = this.game.getUnitSystem().getUnitSpatialIndex();

        if (spatial == null || !map.hasTileFlags()) {
            throw Exceptions.newIllegalStateException("Building placement requires terrain and spatial storage.");
        }

        int tileWidth = units.getPlacementWidth(unitType) / TerrainTileset.TILE_SIZE;
        int tileHeight = units.getPlacementHeight(unitType) / TerrainTileset.TILE_SIZE;

        // A footprint off the map is OFF_MAP on every tile.
        if (tileX < 0 || tileY < 0 || tileX + tileWidth > map.getWidth() || tileY + tileHeight > map.getHeight()) {
            return fill(codes, PlacementResult.OFF_MAP);
        }

        // A ground builder that cannot reach the site is UNREACHABLE.
        if (builder != null && (builder.getStatus() & (UnitStatusFlags.GROUNDED_BUILDING | UnitStatusFlags.FLYING)) == 0 &&
            !this.game.getEngineGame().getPathfinding().isReachable(builder, tileX * TerrainTileset.TILE_SIZE, tileY * TerrainTileset.TILE_SIZE)) {
            return fill(codes, PlacementResult.UNREACHABLE);
        }

        // The owner's explored and visible masks apply only to human player records; every tile counts as explored and visible for other owners, such
        // as computer players.
        Player ownerRecord = this.game.getPlayerTable().getPlayer(owner);
        int foggedMask = ownerRecord != null && ownerRecord.getType() == PlayerTypes.HUMAN ? 1 << owner : 0;
        int unexploredMask = foggedMask << MapTileFlags.NOT_EXPLORED_SHIFT;

        if (units.isGasStructure(unitType)) {
            boolean anyVisible = false;

            for (int vertical = tileY; vertical < tileY + tileHeight; vertical++) {
                for (int horizontal = tileX; horizontal < tileX + tileWidth; horizontal++) {
                    int flags = map.getTileFlags(vertical * map.getWidth() + horizontal);

                    if ((flags & unexploredMask) != 0) {
                        return fill(codes, PlacementResult.UNEXPLORED);
                    }

                    anyVisible |= (flags & foggedMask) == 0;
                }
            }

            if (!anyVisible) {
                return fill(codes, !remembered || memory != null && memory.hasGeyserAt(positionX, positionY) ? PlacementResult.VALID
                                                                                                             : PlacementResult.NO_GEYSER);
            }

            // The point query at the building center, then a geyser whose sprite shares its tile.
            for (int poolIndex : spatial.findAllAtPoint(positionX, positionY)) {
                Unit candidate = spatial.getPoolUnit(poolIndex);

                if (candidate.getUnitId() != ScrUnitIds.RESOURCE_VESPENE_GEYSER) {
                    continue;
                }

                Sprite candidateSprite = candidate.requireSprite();

                if (candidateSprite.getX() / TerrainTileset.TILE_SIZE == positionX / TerrainTileset.TILE_SIZE &&
                    candidateSprite.getY() / TerrainTileset.TILE_SIZE == positionY / TerrainTileset.TILE_SIZE) {
                    return fill(codes, PlacementResult.VALID);
                }
            }

            return fill(codes, PlacementResult.NO_GEYSER);
        }

        int abilities = units.getFlags(unitType);

        // A psi building outside the owner's psi fields is NO_POWER.
        if (checkPower && (abilities & UnitTypeFlags.REQUIRES_PSIONIC_MATRIX) != 0 && !this.game.getPsi().isPowered(owner, positionX, positionY)) {
            return fill(codes, PlacementResult.NO_POWER);
        }

        boolean nydusExit = builder != null && builder.getUnitId() == ScrUnitIds.ZERG_NYDUS_CANAL;
        int creepBit = remembered ? REMEMBERED_CREEP : MapTileFlags.HAS_CREEP;
        boolean zerg = (units.getGroupFlags(unitType) & UnitGroupFlags.ZERG) != 0;
        int terrainMask = zerg ? ZERG_TERRAIN_FLAGS : remembered ? REMEMBERED_TERRAIN_FLAGS : TERRAIN_FLAGS;

        // Only beacons, including flag beacons, pass unbuildable terrain with tile flag FLAG_80000000.
        boolean beacon = units.isBeacon(unitType);
        int result = 0;

        for (int row = 0; row < tileHeight; row++) {
            for (int column = 0; column < tileWidth; column++) {
                int horizontal = tileX + column;
                int vertical = tileY + row;
                int flags = map.getTileFlags(vertical * map.getWidth() + horizontal);
                boolean fogged = (flags & foggedMask) != 0;
                boolean unexplored = (flags & unexploredMask) != 0;
                int code;
                int reached;

                if (nydusExit) {
                    // A Nydus exit requires creep on every tile, fogged or not.
                    code    = unexplored ? PlacementResult.UNEXPLORED : remembered && fogged ? PlacementResult.NOT_VISIBLE
                                                                                             : (flags & creepBit) == 0 ? PlacementResult.NO_CREEP : 0;
                    reached = code;
                } else if ((abilities & UnitTypeFlags.REQUIRES_CREEP) != 0) {
                    // The no-creep code replaces an unexplored tile's code but not the maximum.
                    code    = unexplored ? PlacementResult.UNEXPLORED : 0;
                    reached = code;

                    if ((checkFogged || remembered || !fogged) && (flags & creepBit) == 0) {
                        code    = PlacementResult.NO_CREEP;
                        reached = Math.max(reached, code);
                    }
                } else {
                    // Unexplored, then unbuildable terrain, then resources near a resource depot.
                    if (unexplored) {
                        code = PlacementResult.UNEXPLORED;
                    } else if ((remembered || !fogged) && (flags & terrainMask) != 0 && !(beacon && (flags & MapTileFlags.FLAG_80000000) != 0)) {
                        code = PlacementResult.BLOCKED;
                    } else if ((abilities & UnitTypeFlags.RESOURCE_DEPOT) != 0 &&
                               (remembered && memory != null && memory.hasResourceNear(horizontal, vertical, RESOURCE_TILE_DISTANCE) ||
                                isResourceNear(units, spatial, horizontal, vertical))) {
                        code = PlacementResult.NEAR_RESOURCES;
                    } else {
                        code = 0;
                    }

                    reached = code;
                }

                if (codes == null) {
                    if (code != 0) {
                        return code;
                    }
                } else {
                    codes[row * PlacementResult.ROW_STRIDE + column] = (byte) code;
                    result                                           = Math.max(result, reached);
                }
            }
        }

        return Math.max(result,
                        evaluateUnits(units,
                                      map,
                                      spatial,
                                      collisionBuilder,
                                      unitType, abilities,
                                      tileX, tileY,
                                      tileWidth, tileHeight,
                                      positionX, positionY,
                                      foggedMask,
                                      checkUndetected,
                                      checkFogged,
                                      codes));
    }
}

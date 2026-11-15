package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnits;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.RegionPathSearch;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegionLookupUtils;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegions;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Owns resource-cluster positions, counts, totals and occupancy flags for bounded area identities.
 */
public final class ResourceAreas {
    /**
     * The area identity {@code 0}: no area.
     */
    public static final int NO_AREA = 0;

    /**
     * The number of area records, including the unused area {@link #NO_AREA}.
     */
    private static final int AREA_CAPACITY = 250;
    /**
     * The last area identity clustering may assign.
     */
    public static final int LAST_AREA = AREA_CAPACITY - 1;
    /**
     * The pixel distance of a mineral field from an area position.
     */
    private static final int MINERAL_AREA_DISTANCE = 0x280;
    /**
     * The pixel distance of another unit from an area position.
     */
    private static final int OTHER_AREA_DISTANCE = 0x380;
    /**
     * The route tiles of a mineral field from an area position.
     */
    private static final int MINERAL_AREA_ROUTE_TILES = 0x14;
    /**
     * The route tiles of another unit from an area position.
     */
    private static final int OTHER_AREA_ROUTE_TILES = 0x1C;

    /**
     * The frames between two periodic updates.
     */
    private static final int UPDATE_INTERVAL = 450;
    /**
     * The pixel distance of a building that occupies an area.
     */
    private static final int OCCUPYING_DISTANCE = 384;
    /**
     * The route tiles of a building that occupies an area.
     */
    private static final int OCCUPYING_ROUTE_TILES = 12;
    /**
     * Occupancy flag: a building is near the area.
     */
    public static final int OCCUPIED_FLAG = 0x01;
    /**
     * Occupancy flag: a computer player expands to the area.
     */
    public static final int EXPANSION_FLAG = 0x02;

    /**
     * Clustering class of a mineral field.
     */
    private static final int MINERAL_CLASS = 1;
    /**
     * Clustering class of another resource (a geyser).
     */
    private static final int GAS_CLASS = 2;
    /**
     * The class offset of a resource already assigned to an area.
     */
    private static final int ASSIGNED_CLASS_OFFSET = 2;
    /**
     * The half size in pixels of the box the clustering scores and collects resources in.
     */
    private static final int CLUSTER_RADIUS = 960;
    /**
     * The resources one score point of a clustered resource stands for.
     */
    private static final int RESOURCES_PER_SCORE = 500;
    /**
     * The largest score one clustered resource adds.
     */
    private static final int MAXIMUM_RESOURCE_SCORE = 4;
    /**
     * The start of the closest route search of a cluster center.
     */
    private static final int NO_CLOSEST_DISTANCE = 99999999;

    /**
     * The route distance of two positions without a route.
     */
    private static final int UNREACHABLE_ROUTE = 9999;
    /**
     * The coordinate WORDs of one start location.
     */
    private static final int START_POSITION_WORDS = 2;

    /**
     * The size of one saved area record.
     */
    private static final int SAVED_RECORD_SIZE = 0x30;
    /**
     * The offsets of the saved area count and update countdown DWORDs after the area records.
     */
    private static final int SAVED_AREA_COUNT = AREA_CAPACITY * SAVED_RECORD_SIZE;
    private static final int SAVED_UPDATE_COUNTDOWN = SAVED_AREA_COUNT + Integer.BYTES;
    /**
     * The size of the saved area table: the area records, the area count and the update countdown.
     */
    public static final int SAVED_SIZE = SAVED_UPDATE_COUNTDOWN + Integer.BYTES;
    /**
     * The field offsets of a saved area record.
     */
    private static final int SAVED_HORIZONTAL_POSITION = 0x0;
    private static final int SAVED_VERTICAL_POSITION = 0x2;
    private static final int SAVED_MINERAL_COUNT = 0x4;
    private static final int SAVED_GAS_COUNT = 0x5;
    private static final int SAVED_START_LOCATION_MARKER = 0x6;
    private static final int SAVED_OCCUPANCY_FLAGS = 0x7;
    private static final int SAVED_MINERAL_TOTAL = 0x8;
    private static final int SAVED_GAS_TOTAL = 0xC;
    /**
     * The masks that read the stored BYTE, WORD and DWORD fields unsigned.
     */
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;
    private static final long DWORD_MASK = 0xFFFFFFFFL;
    private static final long LONG_WORD_MASK = 0xFFFFL;
    private static final long LONG_BYTE_MASK = 0xFFL;
    /**
     * The divisor that centers an area between its outermost resources.
     */
    private static final int AREA_CENTER_DIVISOR = 2;

    private static long mixHash(long hash, long value) {
        hash ^= value;
        hash *= Game.STATE_HASH_PRIME;
        hash ^= hash >>> Game.STATE_HASH_FOLD_SHIFT;

        return hash;
    }


    private final @NotNull Game game;
    private final @NotNull TerrainTileset terrain;
    private final @NotNull TerrainRegions regions;
    private final @NotNull RegionPathSearch search;

    private final short @NotNull [] horizontalPositions;
    private final short @NotNull [] verticalPositions;

    private final byte @NotNull [] mineralCounts;
    private final byte @NotNull [] gasCounts;

    private final byte @NotNull [] startLocationMarkers;

    private final byte @NotNull [] occupancyFlags;

    private final int @NotNull [] mineralTotals;
    private final int @NotNull [] gasTotals;

    private int areaCount;

    private int updateCountdown;

    /**
     * Creates a {@link ResourceAreas} instance for an established terrain and unit context.
     *
     * @param game    the game holding the unit lists, map and data
     * @param terrain the installed terrain height data
     * @throws UnsupportedOperationException if the terrain contours are not initialized
     */
    public ResourceAreas(@NotNull Game game, @NotNull TerrainTileset terrain) {
        this.game    = game;
        this.terrain = terrain;
        this.regions = game.getPathSystem().requireTerrainContours().getRegions();
        this.search  = new RegionPathSearch(this.regions);

        this.horizontalPositions = new short[AREA_CAPACITY];
        this.verticalPositions   = new short[AREA_CAPACITY];

        this.mineralCounts = new byte[AREA_CAPACITY];
        this.gasCounts     = new byte[AREA_CAPACITY];

        this.startLocationMarkers = new byte[AREA_CAPACITY];

        this.occupancyFlags = new byte[AREA_CAPACITY];

        this.mineralTotals = new int[AREA_CAPACITY];
        this.gasTotals     = new int[AREA_CAPACITY];

        this.areaCount = 0;

        this.updateCountdown = 0;
    }

    /**
     * @return the area-count DWORD
     */
    public int getAreaCount() {
        return this.areaCount;
    }

    /**
     * @param area the area identity
     * @return the signed horizontal position WORD of the area
     */
    public int getHorizontalPosition(int area) {
        return this.horizontalPositions[area];
    }

    /**
     * @param area the area identity
     * @return the signed vertical position WORD of the area
     */
    public int getVerticalPosition(int area) {
        return this.verticalPositions[area];
    }

    /**
     * @param area the area identity
     * @return the mineral-field count BYTE of the area record
     */
    public int getMineralCount(int area) {
        return this.mineralCounts[area] & BYTE_MASK;
    }

    /**
     * @param area the area identity
     * @return the geyser count BYTE of the area record
     */
    public int getGasCount(int area) {
        return this.gasCounts[area] & BYTE_MASK;
    }

    /**
     * @param area the area identity
     * @return the mineral total DWORD of the area record
     */
    public int getMineralTotal(int area) {
        return this.mineralTotals[area];
    }

    /**
     * @param area the area identity
     * @return the gas total DWORD of the area record
     */
    public int getGasTotal(int area) {
        return this.gasTotals[area];
    }

    /**
     * @param area the area identity
     * @return the start-location BYTE of the area record, nonzero for an area registered at a start location
     */
    public int getStartLocationMarker(int area) {
        return this.startLocationMarkers[area] & BYTE_MASK;
    }

    /**
     * @param area the area identity
     * @return the occupancy BYTE: {@code 1} a nearby building or occupied start location, {@code 2} a computer expansion
     */
    public int getOccupancyFlags(int area) {
        return this.occupancyFlags[area] & BYTE_MASK;
    }


    /**
     * Applies start-location registration, resource assignment and clustering using area identities {@code 1} through {@code 249}.
     *
     * @param startPositions the start-location coordinate WORDs, two per playable slot
     * @param playerTypes    the explicitly decoded player-record type BYTEs of the playable slots
     * @throws IllegalArgumentException      if an array does not cover the playable slots
     * @throws UnsupportedOperationException if the unit pool or the unit spatial index is not initialized, or a resource has no sprite
     * @throws NullPointerException          if the world was built without game data
     * @throws IllegalStateException         if a clustered resource has no sprite
     */
    public void initialize(int @NotNull [] startPositions, int @NotNull [] playerTypes) {
        if (startPositions.length != PlayerTable.PLAYABLE_PLAYER_SLOTS * START_POSITION_WORDS ||
            playerTypes.length != PlayerTable.PLAYABLE_PLAYER_SLOTS) {
            throw Exceptions.newIllegalArgumentException("Native resource areas require eight start locations and player records.");
        }

        Arrays.fill(this.horizontalPositions, (short) 0);
        Arrays.fill(this.verticalPositions, (short) 0);
        Arrays.fill(this.mineralCounts, (byte) 0);
        Arrays.fill(this.gasCounts, (byte) 0);
        Arrays.fill(this.startLocationMarkers, (byte) 0);
        Arrays.fill(this.occupancyFlags, (byte) 0);
        Arrays.fill(this.mineralTotals, 0);
        Arrays.fill(this.gasTotals, 0);

        this.areaCount       = 0;
        this.updateCountdown = 0;

        for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
            int horizontal = startPositions[playerIndex * START_POSITION_WORDS] & WORD_MASK;
            int vertical = startPositions[playerIndex * START_POSITION_WORDS + 1] & WORD_MASK;

            if (horizontal == 0 && vertical == 0) {
                continue;
            }

            int area = this.getAreaCount() + 1;

            this.areaCount                  = area;
            this.horizontalPositions[area]  = (short) horizontal;
            this.verticalPositions[area]    = (short) vertical;
            this.startLocationMarkers[area] = (byte) 1;

            if ((playerTypes[playerIndex] & BYTE_MASK) == PlayerTypes.COMPUTER || (playerTypes[playerIndex] & BYTE_MASK) == PlayerTypes.HUMAN) {
                this.occupancyFlags[area] = (byte) (this.getOccupancyFlags(area) | OCCUPIED_FLAG);
            }
        }

        UnitPool pool = this.game.getUnitSystem().requireUnitPool();
        int[] units = new int[pool.getAllocatedCount()];
        int unitCount = 0;

        for (int poolIndex = pool.getFirstCreatedIndex(true); poolIndex != 0; poolIndex = pool.getNextCreatedIndex(poolIndex)) {
            units[unitCount++] = poolIndex;
        }

        byte[] classifications = new byte[unitCount];

        for (int unitIndex = 0; unitIndex < unitCount; unitIndex++) {
            Unit unit = pool.getUnit(units[unitIndex]);

            if (ComputerUnits.isDeadOrHidden(unit) || !this.isResource(unit)) {
                continue;
            }

            boolean mineral = this.game.requireGameData().getUnitTypes().isMineralField(unit.getUnitId());

            classifications[unitIndex] = (byte) (mineral ? MINERAL_CLASS : GAS_CLASS);
            unit.setResourceAreaIndex(0);

            int area = this.findArea(unit);

            if (area != NO_AREA) {
                this.addResource(area, unit, mineral);
                classifications[unitIndex] += ASSIGNED_CLASS_OFFSET;
            }
        }

        this.clusterResources(units, unitCount, classifications, GAS_CLASS);
        this.clusterResources(units, unitCount, classifications, MINERAL_CLASS);
    }

    /**
     * Updates periodic resource totals and occupied areas.
     * <p>
     * Each building ({@link UnitQueries#isBuilding}) marks at most one area per update: the lowest area identity that is not yet occupied, shares the
     * terrain component of the building and lies within the occupying pixel distance and route tiles. A building that marks an area ends its area
     * loop, so later buildings take the next unoccupied areas.
     * <p>
     * Imported area identities outside the area capacity fail at their first array access; they cannot alias the diagnostic count or timer footer.
     *
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the unit pool is not initialized
     * @throws IllegalStateException         if a building has no sprite
     */
    public void update() {
        int areaCount = this.getAreaCount();

        if (areaCount == 0) {
            return;
        }

        int countdown = this.updateCountdown;

        if (countdown != 0) {
            this.updateCountdown = countdown - 1;

            return;
        }

        this.updateCountdown = UPDATE_INTERVAL;

        for (int area = 1; area <= areaCount; area++) {
            this.occupancyFlags[area] = (byte) (this.getOccupancyFlags(area) & ~OCCUPIED_FLAG);
            this.mineralCounts[area]  = 0;
            this.gasCounts[area]      = 0;
            this.mineralTotals[area]  = 0;
            this.gasTotals[area]      = 0;
        }

        UnitPool pool = this.game.getUnitSystem().requireUnitPool();

        for (int poolIndex = pool.getFirstCreatedIndex(true); poolIndex != 0; poolIndex = pool.getNextCreatedIndex(poolIndex)) {
            Unit unit = pool.getUnit(poolIndex);

            if (ComputerUnits.isDeadOrHidden(unit)) {
                continue;
            }

            if (this.isResource(unit)) {
                int area = unit.getResourceAreaIndex();

                if (area != 0) {
                    this.addResource(area, unit, this.game.requireGameData().getUnitTypes().isMineralField(unit.getUnitId()));
                }
            } else if (UnitQueries.isBuilding(this.game.requireGameData().getUnitTypes(), unit.getUnitId())) {
                int horizontal = (short) unit.requireSprite().getX();
                int vertical = (short) unit.requireSprite().getY();

                for (int area = 1; area <= areaCount; area++) {
                    if ((this.getOccupancyFlags(area) & OCCUPIED_FLAG) != 0) {
                        continue;
                    }

                    int areaHorizontal = this.getHorizontalPosition(area);
                    int areaVertical = this.getVerticalPosition(area);

                    if (unit.sharesTerrainComponent(this.regions, areaHorizontal, areaVertical) &&
                        Integer.compareUnsigned(RegionPathSearch.calculateDistance(horizontal, areaHorizontal, vertical, areaVertical),
                                                OCCUPYING_DISTANCE) <= 0 &&
                        Integer.compareUnsigned(this.calculateRouteDistance(horizontal, vertical, areaHorizontal, areaVertical),
                                                OCCUPYING_ROUTE_TILES) <= 0) {
                        this.occupancyFlags[area] = (byte) (this.getOccupancyFlags(area) | OCCUPIED_FLAG);

                        break;
                    }
                }
            }
        }
    }

    /**
     * Writes the area table as the last section of the computer-player part: one record per area identity with the position WORDs, the mineral-field
     * and geyser counts, the start-location marker, the occupancy flags, the mineral and gas totals, then the area count and the update countdown
     * DWORDs. Record padding is left untouched.
     *
     * @param data the section, little-endian
     */
    public void writeSaveRecords(@NotNull ByteBuffer data) {
        for (int area = 0; area < AREA_CAPACITY; area++) {
            int offset = area * SAVED_RECORD_SIZE;

            data.putShort(offset + SAVED_HORIZONTAL_POSITION, this.horizontalPositions[area]);
            data.putShort(offset + SAVED_VERTICAL_POSITION, this.verticalPositions[area]);
            data.put(offset + SAVED_MINERAL_COUNT, this.mineralCounts[area]);
            data.put(offset + SAVED_GAS_COUNT, this.gasCounts[area]);
            data.put(offset + SAVED_START_LOCATION_MARKER, this.startLocationMarkers[area]);
            data.put(offset + SAVED_OCCUPANCY_FLAGS, this.occupancyFlags[area]);
            data.putInt(offset + SAVED_MINERAL_TOTAL, this.mineralTotals[area]);
            data.putInt(offset + SAVED_GAS_TOTAL, this.gasTotals[area]);
        }

        data.putInt(SAVED_AREA_COUNT, this.areaCount);
        data.putInt(SAVED_UPDATE_COUNTDOWN, this.updateCountdown);
    }

    /**
     * Reads the area table that {@link #writeSaveRecords} wrote; the route search storage keeps its contents.
     *
     * @param data the section, little-endian
     */
    public void readSaveRecords(@NotNull ByteBuffer data) {
        for (int area = 0; area < AREA_CAPACITY; area++) {
            int offset = area * SAVED_RECORD_SIZE;

            this.horizontalPositions[area]  = data.getShort(offset + SAVED_HORIZONTAL_POSITION);
            this.verticalPositions[area]    = data.getShort(offset + SAVED_VERTICAL_POSITION);
            this.mineralCounts[area]        = data.get(offset + SAVED_MINERAL_COUNT);
            this.gasCounts[area]            = data.get(offset + SAVED_GAS_COUNT);
            this.startLocationMarkers[area] = data.get(offset + SAVED_START_LOCATION_MARKER);
            this.occupancyFlags[area]       = data.get(offset + SAVED_OCCUPANCY_FLAGS);
            this.mineralTotals[area]        = data.getInt(offset + SAVED_MINERAL_TOTAL);
            this.gasTotals[area]            = data.getInt(offset + SAVED_GAS_TOTAL);
        }

        this.areaCount = data.getInt(SAVED_AREA_COUNT);

        this.updateCountdown = data.getInt(SAVED_UPDATE_COUNTDOWN);
    }

    /**
     * Mixes the resource-area fields and the update countdown into the world hash.
     *
     * @param hash the preceding world hash
     * @return the hash including resource-area fields and the update countdown
     */
    public long computeStateHash(long hash) {
        hash = mixHash(hash, this.areaCount & DWORD_MASK);
        hash = mixHash(hash, this.updateCountdown & DWORD_MASK);

        for (int area = 0; area < AREA_CAPACITY; area++) {
            hash = mixHash(hash, this.horizontalPositions[area] & LONG_WORD_MASK);
            hash = mixHash(hash, this.verticalPositions[area] & LONG_WORD_MASK);
            hash = mixHash(hash, this.mineralCounts[area] & LONG_BYTE_MASK);
            hash = mixHash(hash, this.gasCounts[area] & LONG_BYTE_MASK);
            hash = mixHash(hash, this.startLocationMarkers[area] & LONG_BYTE_MASK);
            hash = mixHash(hash, this.occupancyFlags[area] & LONG_BYTE_MASK);
            hash = mixHash(hash, this.mineralTotals[area] & DWORD_MASK);
            hash = mixHash(hash, this.gasTotals[area] & DWORD_MASK);
        }

        return hash;
    }

    /**
     * Finds the area of a unit: the first area within {@code 640} pixels ({@code 896} for a unit that is not a mineral field) on the unit's ground
     * component and ground height whose route is at most {@code 20} tiles ({@code 28}) long.
     *
     * @param unit the unit, usually a resource or a resource depot
     * @return the area identity, or {@link #NO_AREA}
     * @throws UnsupportedOperationException if the unit has no sprite
     */
    public int findArea(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("The resource area of a unit requires its sprite.");
        }

        int horizontal = (short) sprite.getX();
        int vertical = (short) sprite.getY();
        boolean mineral = this.game.requireGameData().getUnitTypes().isMineralField(unit.getUnitId());

        for (int area = 1; area <= this.getAreaCount(); area++) {
            int areaHorizontal = this.getHorizontalPosition(area);
            int areaVertical = this.getVerticalPosition(area);

            if (Integer.compareUnsigned(RegionPathSearch.calculateDistance(areaHorizontal, horizontal, areaVertical, vertical),
                                        mineral ? MINERAL_AREA_DISTANCE : OTHER_AREA_DISTANCE) > 0 ||
                !unit.sharesTerrainComponent(this.regions, areaHorizontal, areaVertical) ||
                (this.game.getMap().getTerrainHeight(this.terrain, areaHorizontal, areaVertical) !=
                 this.game.getMap().getTerrainHeight(this.terrain, horizontal, vertical)) ||
                Integer.compareUnsigned(this.calculateRouteDistance(areaHorizontal, areaVertical, horizontal, vertical),
                                        mineral ? MINERAL_AREA_ROUTE_TILES : OTHER_AREA_ROUTE_TILES) > 0) {
                continue;
            }

            return area;
        }

        return NO_AREA;
    }

    /**
     * Marks the area as a computer expansion: sets {@link #EXPANSION_FLAG} of the occupancy BYTE, as the computer expansion selection does for the
     * area it selects. {@link #update()} clears only the occupied flag.
     *
     * @param area the area identity
     */
    public void markExpansion(int area) {
        this.occupancyFlags[area] = (byte) (this.getOccupancyFlags(area) | EXPANSION_FLAG);
    }

    /**
     * Clears the expansion flag of the occupancy BYTE, as the release of a computer town does.
     *
     * @param area the area identity
     */
    public void clearExpansion(int area) {
        this.occupancyFlags[area] = (byte) (this.getOccupancyFlags(area) & ~EXPANSION_FLAG);
    }

    /**
     * Scores and assigns one resource class to bounded area identities.
     *
     * @param units           the collected unit pool indices
     * @param unitCount       the collected unit count
     * @param classifications the current classification of each collected unit
     * @param classification  the class to cluster
     * @throws UnsupportedOperationException if the unit pool or the unit spatial index is not initialized
     * @throws IllegalStateException         if the center or the unit has no sprite
     */
    private void clusterResources(int @NotNull [] units, int unitCount, byte @NotNull [] classifications, int classification) {
        int[] candidates = new int[unitCount];
        int[] scores = new int[unitCount];
        int candidateCount = 0;

        for (int unitIndex = 0; unitIndex < unitCount; unitIndex++) {
            if (classifications[unitIndex] != classification) {
                continue;
            }

            candidates[candidateCount] = units[unitIndex];

            Unit center = this.game.getUnitSystem().requireUnitPool().getUnit(units[unitIndex]);
            int horizontal = (short) center.requireSprite().getX();
            int vertical = (short) center.requireSprite().getY();
            // The unexpanded finder query around the candidate.
            UnitSpatialIndex finder = this.game.getUnitSystem().requireUnitSpatialIndex();
            int score = 1;
            int found = finder.collectPlacementCandidates(horizontal - CLUSTER_RADIUS, vertical - CLUSTER_RADIUS,
                                                          horizontal + CLUSTER_RADIUS, vertical + CLUSTER_RADIUS);

            for (int foundIndex = 0; foundIndex < found; foundIndex++) {
                Unit other = finder.getPlacementCandidate(foundIndex);

                if (this.isClusterCandidate(other, horizontal, vertical)) {
                    score += Math.min(other.getResources() / RESOURCES_PER_SCORE + 1, MAXIMUM_RESOURCE_SCORE);
                }
            }

            scores[candidateCount++] = score;
        }

        for (int iteration = 0; iteration < candidateCount; iteration++) {
            int selected = 0;

            for (int candidateIndex = 1; candidateIndex < candidateCount; candidateIndex++) {
                if (Integer.compareUnsigned(scores[candidateIndex], scores[selected]) > 0) {
                    selected = candidateIndex;
                }
            }

            scores[selected] = 0;

            Unit center = this.game.getUnitSystem().requireUnitPool().getUnit(candidates[selected]);
            int horizontal = (short) center.requireSprite().getX();
            int vertical = (short) center.requireSprite().getY();
            int horizontalSum = 0;
            int verticalSum = 0;
            int matched = 0;

            for (int unitIndex = 0; unitIndex < unitCount; unitIndex++) {
                Unit unit = this.game.getUnitSystem().requireUnitPool().getUnit(units[unitIndex]);

                if (this.isClusterCandidate(unit, horizontal, vertical)) {
                    horizontalSum += (short) unit.requireSprite().getX();
                    verticalSum += (short) unit.requireSprite().getY();
                    matched++;
                }
            }

            if (matched == 0) {
                continue;
            }

            if (Integer.compareUnsigned(this.getAreaCount(), LAST_AREA) >= 0) {
                return;
            }

            int area = this.getAreaCount() + 1;

            this.areaCount = area;

            int meanHorizontal = Integer.divideUnsigned(horizontalSum, matched);
            int meanVertical = Integer.divideUnsigned(verticalSum, matched);
            int closestDistance = NO_CLOSEST_DISTANCE;

            for (int unitIndex = 0; unitIndex < unitCount; unitIndex++) {
                if (classifications[unitIndex] != MINERAL_CLASS) {
                    continue;
                }

                Unit unit = this.game.getUnitSystem().requireUnitPool().getUnit(units[unitIndex]);
                int distance = this.calculateRouteDistance(meanHorizontal, meanVertical,
                                                           (short) unit.requireSprite().getX(), (short) unit.requireSprite().getY());

                if (Integer.compareUnsigned(distance, closestDistance) <= 0 && Integer.compareUnsigned(distance, MINERAL_AREA_ROUTE_TILES) <= 0) {
                    closestDistance = distance;
                    center          = unit;
                }
            }

            candidates[selected] = center.getIndex();
            horizontal           = (short) center.requireSprite().getX();
            vertical             = (short) center.requireSprite().getY();

            int minimumHorizontal = horizontal;
            int maximumHorizontal = horizontal;
            int minimumVertical = vertical;
            int maximumVertical = vertical;

            for (int unitIndex = 0; unitIndex < unitCount; unitIndex++) {
                Unit unit = this.game.getUnitSystem().requireUnitPool().getUnit(units[unitIndex]);

                if (!this.isClusterCandidate(unit, horizontal, vertical) ||
                    classifications[unitIndex] < MINERAL_CLASS ||
                    classifications[unitIndex] > GAS_CLASS) {
                    continue;
                }

                this.addResource(area, unit, classifications[unitIndex] == MINERAL_CLASS);
                classifications[unitIndex] += ASSIGNED_CLASS_OFFSET;
                                              minimumHorizontal = Math.min(minimumHorizontal, (short) unit.requireSprite().getX());
                                              maximumHorizontal = Math.max(maximumHorizontal, (short) unit.requireSprite().getX());
                                              minimumVertical   = Math.min(minimumVertical, (short) unit.requireSprite().getY());
                                              maximumVertical   = Math.max(maximumVertical, (short) unit.requireSprite().getY());
            }

            this.horizontalPositions[area] = (short) ((minimumHorizontal + maximumHorizontal) / AREA_CENTER_DIVISOR);
            this.verticalPositions[area]   = (short) ((minimumVertical + maximumVertical) / AREA_CENTER_DIVISOR);
        }
    }

    /**
     * Checks unassigned resources against the center, terrain, and route bounds.
     *
     * @param unit       the resource candidate
     * @param horizontal the center horizontal coordinate
     * @param vertical   the center vertical coordinate
     * @return {@code true} if the candidate qualifies, {@code false} otherwise
     * @throws UnsupportedOperationException if an unassigned resource candidate has no sprite
     */
    private boolean isClusterCandidate(@NotNull Unit unit, int horizontal, int vertical) {
        if (!this.isResource(unit) || unit.getResourceAreaIndex() != 0) {
            return false;
        }

        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("Resource-area candidate lacks the original sprite coordinate context.");
        }

        int unitHorizontal = (short) sprite.getX();
        int unitVertical = (short) sprite.getY();
        boolean mineral = this.game.requireGameData().getUnitTypes().isMineralField(unit.getUnitId());

        return unitHorizontal >= (short) (horizontal - CLUSTER_RADIUS) && unitHorizontal <= (short) (horizontal + CLUSTER_RADIUS) &&
               unitVertical >= (short) (vertical - CLUSTER_RADIUS) && unitVertical <= (short) (vertical + CLUSTER_RADIUS) &&
               unit.sharesTerrainComponent(this.regions, horizontal, vertical) &&
               (this.game.getMap().getTerrainHeight(this.terrain, unitHorizontal, unitVertical) ==
                this.game.getMap().getTerrainHeight(this.terrain, horizontal, vertical)) &&
               Integer.compareUnsigned(RegionPathSearch.calculateDistance(horizontal, unitHorizontal, vertical, unitVertical),
                                       mineral ? MINERAL_AREA_DISTANCE : OTHER_AREA_DISTANCE) <= 0 &&
               Integer.compareUnsigned(this.calculateRouteDistance(horizontal, vertical, unitHorizontal, unitVertical),
                                       mineral ? MINERAL_AREA_ROUTE_TILES : OTHER_AREA_ROUTE_TILES) <= 0;
    }

    /**
     * Checks the unit type resource-container flag.
     *
     * @param unit the candidate unit
     * @return {@code true} if the unit type is a resource container, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    private boolean isResource(@NotNull Unit unit) {
        return (this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_CONTAINER) != 0;
    }

    /**
     * Calculates a component route distance in tiles using retained search storage.
     *
     * @param firstHorizontal  the source horizontal coordinate
     * @param firstVertical    the source vertical coordinate
     * @param secondHorizontal the destination horizontal coordinate
     * @param secondVertical   the destination vertical coordinate
     * @return the route distance, or the unreachable marker
     */
    private int calculateRouteDistance(int firstHorizontal, int firstVertical, int secondHorizontal, int secondVertical) {
        int firstRegion = TerrainRegionLookupUtils.lookup(this.regions, firstHorizontal, firstVertical);
        int secondRegion = TerrainRegionLookupUtils.lookup(this.regions, secondHorizontal, secondVertical);

        if (firstRegion == secondRegion) {
            return RegionPathSearch.calculateDistance(firstHorizontal, secondHorizontal, firstVertical, secondVertical) / TerrainTileset.TILE_SIZE;
        }

        if (this.regions.getComponent(firstRegion) != this.regions.getComponent(secondRegion)) {
            return UNREACHABLE_ROUTE;
        }

        if (!this.search.search(firstHorizontal, firstVertical, secondHorizontal, secondVertical)) {
            return UNREACHABLE_ROUTE;
        }

        int routeCount = this.search.getRouteCount();

        if (routeCount <= RegionPathSearch.DIRECT_ROUTE_COUNT) {
            return RegionPathSearch.calculateDistance(firstHorizontal, secondHorizontal, firstVertical, secondVertical) / TerrainTileset.TILE_SIZE;
        }

        int previousHorizontal = firstHorizontal;
        int previousVertical = firstVertical;
        int total = 0;

        for (int routeIndex = 1; routeIndex < routeCount - 1; routeIndex++) {
            int regionIndex = this.search.getRouteRegion(routeIndex);
            int horizontal = this.regions.getCenterX(regionIndex);
            int vertical = this.regions.getCenterY(regionIndex);

            total += RegionPathSearch.calculateDistance(previousHorizontal, horizontal, previousVertical, vertical);
            previousHorizontal = horizontal;
            previousVertical   = vertical;
        }

        total += RegionPathSearch.calculateDistance(previousHorizontal, secondHorizontal, previousVertical, secondVertical);

        return total >>> TerrainTileset.TILE_SHIFT;
    }

    /**
     * Accumulates one resource and stores its assigned area identity.
     *
     * @param area    the area identity
     * @param unit    the resource unit
     * @param mineral whether the resource is a mineral field
     */
    private void addResource(int area, @NotNull Unit unit, boolean mineral) {
        if (mineral) {
            this.mineralCounts[area]++;
            this.mineralTotals[area] += unit.getResources();
        } else {
            this.gasCounts[area]++;
            this.gasTotals[area] += unit.getResources();
        }

        unit.setResourceAreaIndex(area);
    }
}

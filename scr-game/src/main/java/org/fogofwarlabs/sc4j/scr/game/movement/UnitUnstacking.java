package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.path.PathObstacleCollector;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.RegionPathSearch;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegionLookupUtils;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegions;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Retains bounded side segments for illegal-position checks and moves units to legal destinations.
 */
public final class UnitUnstacking {
    /**
     * The mask of an unsigned WORD pixel position.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The mask of one WORD of a packed rectangle or segment.
     */
    private static final long LONG_WORD_MASK = 0xFFFFL;
    /**
     * The mask of the low DWORD of a packed destination.
     */
    private static final long DWORD_MASK = 0xFFFFFFFFL;
    /**
     * The fraction bits of the fixed-point escape lengths and direction vectors.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    /**
     * The shift of the second WORD of a packed rectangle, segment or position.
     */
    private static final int SECOND_WORD_SHIFT = Short.SIZE;
    /**
     * The shift of the third WORD of a packed rectangle or segment.
     */
    private static final int THIRD_WORD_SHIFT = Integer.SIZE;
    /**
     * The shift of the fourth WORD of a packed rectangle or segment.
     */
    private static final int FOURTH_WORD_SHIFT = Integer.SIZE + Short.SIZE;
    /**
     * The shift of the vertical coordinate in a packed destination.
     */
    private static final int PACKED_COORDINATE_SHIFT = Integer.SIZE;

    /**
     * The random source of every unstacking roll.
     */
    private static final int UNSTACK_RANDOM_SOURCE = 50;
    /**
     * The largest value of the waiting roll.
     */
    private static final int WAIT_ROLL_MAXIMUM = 31;
    /**
     * A waiting roll below this keeps the unit waiting for a moving blocker.
     */
    private static final int WAIT_ROLL_LIMIT = 24;

    /**
     * The direction step of a random escape turn: a sixteenth of a turn.
     */
    private static final int ESCAPE_TURN_STEP = 16;
    /**
     * The largest number of escape turn steps either way around the heading.
     */
    private static final int ESCAPE_TURN_STEPS = 3;
    /**
     * The smallest number of escape length steps.
     */
    private static final int MINIMUM_ESCAPE_STEPS = 2;
    /**
     * The largest number of escape length steps.
     */
    private static final int MAXIMUM_ESCAPE_STEPS = 4;
    /**
     * The pixels of one escape length step.
     */
    private static final int ESCAPE_STEP_LENGTH = 4;
    /**
     * An escape from a building blocker is this many times longer.
     */
    private static final int BUILDING_ESCAPE_FACTOR = 3;
    /**
     * A unit at most this far in pixels from its move target jitters in place instead of escaping toward it.
     */
    private static final int NEAR_DESTINATION_DISTANCE = 32;
    /**
     * The largest number of jitter steps either way on each axis.
     */
    private static final int JITTER_STEPS = 2;
    /**
     * The pixels of one jitter step.
     */
    private static final int JITTER_STEP_LENGTH = 16;
    /**
     * The largest number of turn steps either way around the direction toward the move target.
     */
    private static final int DESTINATION_TURN_STEPS = 1;
    /**
     * The base length in pixels of an escape toward the move target.
     */
    private static final int DESTINATION_ESCAPE_LENGTH = 8;
    /**
     * The largest number of escape length steps added to an escape toward the move target.
     */
    private static final int DESTINATION_ESCAPE_STEPS = 2;

    /**
     * The largest number of turn steps either way around the direction toward a region center.
     */
    private static final int TERRAIN_TURN_STEPS = 2;
    /**
     * The pixels of one terrain escape length step.
     */
    private static final int TERRAIN_ESCAPE_STEP_LENGTH = 8;
    /**
     * A nearest walkable neighbor region more than this many pixels away counts as far.
     */
    private static final int NEIGHBOR_MARGIN = 32;
    /**
     * The length in pixels of an escape toward a far, the current or an unwalkable neighbor region, at most.
     */
    private static final int NEIGHBOR_ESCAPE_LENGTH = 64;

    /**
     * The number of side segments around a blocker.
     */
    private static final int SIDE_COUNT = 4;
    /**
     * The index of the top side segment.
     */
    private static final int TOP_SEGMENT = 0;
    /**
     * The index of the right side segment.
     */
    private static final int RIGHT_SEGMENT = 1;
    /**
     * The index of the bottom side segment.
     */
    private static final int BOTTOM_SEGMENT = 2;
    /**
     * The index of the left side segment.
     */
    private static final int LEFT_SEGMENT = 3;
    /**
     * The distance of no side point.
     */
    private static final int NO_SIDE_DISTANCE = 9999;
    /**
     * The include-moving argument of the side blocker test: every moving unit blocks a side.
     */
    private static final int INCLUDE_MOVING_BLOCKERS = 1;
    /**
     * The selected-unit argument of the side blocker test: no moving unit is singled out.
     */
    private static final int NO_SELECTED_UNIT = 0;

    /**
     * The clipping outcode of a point above the bounds.
     */
    private static final int OUTCODE_ABOVE = 8;
    /**
     * The clipping outcode of a point below the bounds.
     */
    private static final int OUTCODE_BELOW = 4;
    /**
     * The clipping outcode of a point right of the bounds.
     */
    private static final int OUTCODE_RIGHT = 2;
    /**
     * The clipping outcode of a point left of the bounds.
     */
    private static final int OUTCODE_LEFT = 1;
    /**
     * The clipping outcodes of a point above or below the bounds.
     */
    private static final int OUTCODE_VERTICAL = OUTCODE_ABOVE | OUTCODE_BELOW;

    /**
     * Clips a segment to the given bounds (Cohen-Sutherland clipping with signed integer intersections).
     *
     * @param segment the segment as four packed signed WORDs (from X, from Y, to X, to Y)
     * @param bounds  the bounds as four packed signed WORDs (left, top, right, bottom)
     * @return the clipped segment in the same packing, or {@link Long#MIN_VALUE} if no part lies inside the bounds
     */
    public static long clipSegment(long segment, long bounds) {
        int fromX = (short) segment;
        int fromY = (short) (segment >>> SECOND_WORD_SHIFT);
        int toX = (short) (segment >>> THIRD_WORD_SHIFT);
        int toY = (short) (segment >>> FOURTH_WORD_SHIFT);
        int left = (short) bounds;
        int top = (short) (bounds >>> SECOND_WORD_SHIFT);
        int right = (short) (bounds >>> THIRD_WORD_SHIFT);
        int bottom = (short) (bounds >>> FOURTH_WORD_SHIFT);

        while (true) {
            int fromFlags = (fromY < top ? OUTCODE_ABOVE : 0) | (fromY > bottom ? OUTCODE_BELOW : 0) |
                            (fromX > right ? OUTCODE_RIGHT : 0) | (fromX < left ? OUTCODE_LEFT : 0);
            int toFlags = (toY < top ? OUTCODE_ABOVE : 0) | (toY > bottom ? OUTCODE_BELOW : 0) |
                          (toX > right ? OUTCODE_RIGHT : 0) | (toX < left ? OUTCODE_LEFT : 0);

            if ((fromFlags | toFlags) == 0) {
                return packRectangle(fromX, fromY, toX, toY);
            }

            if ((fromFlags & toFlags) != 0) {
                return Long.MIN_VALUE;
            }

            int flags = fromFlags != 0 ? fromFlags : toFlags;
            int horizontal;
            int vertical;

            if ((flags & OUTCODE_VERTICAL) != 0) {
                vertical   = (flags & OUTCODE_ABOVE) != 0 ? top : bottom;
                horizontal = fromX + (vertical - fromY) * (toX - fromX) / (toY - fromY);
            } else {
                horizontal = (flags & OUTCODE_RIGHT) != 0 ? right : left;
                vertical   = fromY + (horizontal - fromX) * (toY - fromY) / (toX - fromX);
            }

            if (fromFlags != 0) {
                fromX = horizontal;
                fromY = vertical;
            } else {
                toX = horizontal;
                toY = vertical;
            }
        }
    }

    /**
     * Packs four coordinates into four signed WORDs, the first in the lowest WORD.
     *
     * @param left   the left or from-X coordinate
     * @param top    the top or from-Y coordinate
     * @param right  the right or to-X coordinate
     * @param bottom the bottom or to-Y coordinate
     * @return the packed rectangle or segment
     */
    public static long packRectangle(int left, int top, int right, int bottom) {
        return left & LONG_WORD_MASK | (top & LONG_WORD_MASK) << SECOND_WORD_SHIFT |
               (right & LONG_WORD_MASK) << THIRD_WORD_SHIFT | (bottom & LONG_WORD_MASK) << FOURTH_WORD_SHIFT;
    }

    private static long calculateExpandedBounds(@NotNull ScrUnitTypeView unitTypes, @NotNull Unit obstacle, @NotNull Unit incoming) {
        int obstacleType = obstacle.getUnitId();
        int incomingType = incoming.getUnitId();
        int horizontal = (short) obstacle.requireSprite().getX();
        int vertical = (short) obstacle.requireSprite().getY();

        return packRectangle(horizontal - unitTypes.getDimensionLeft(obstacleType) - unitTypes.getDimensionRight(incomingType) - 1,
                             vertical - unitTypes.getDimensionUp(obstacleType) - unitTypes.getDimensionDown(incomingType) - 1,
                             horizontal + unitTypes.getDimensionRight(obstacleType) + unitTypes.getDimensionLeft(incomingType) + 1,
                             vertical + unitTypes.getDimensionDown(obstacleType) + unitTypes.getDimensionUp(incomingType) + 1);
    }

    private static boolean isBlocked(@NotNull Game game, @NotNull Unit unit, int horizontal, int vertical) {
        return !isInsideBounds(game, unit, horizontal, vertical) ||
               game.getUnitSystem().requireUnitSpatialIndex().findMovementBlocker(game.getHooks().getCollisionRules(),
                                                                                  unit, game.requireGameData().getUnitTypes(),
                                                                                  horizontal, vertical) != null ||
               !game.getUnitPositioning().isTerrainPlacementWalkable(unit.getUnitId(), horizontal, vertical);
    }

    private static boolean isInsideBounds(@NotNull Game game, @NotNull Unit unit, int horizontal, int vertical) {
        ScrUnitTypeView unitTypes = game.requireGameData().getUnitTypes();
        int unitType = unit.getUnitId();

        return horizontal >= unitTypes.getDimensionLeft(unitType) && vertical >= unitTypes.getDimensionUp(unitType) &&
               horizontal + unitTypes.getDimensionRight(unitType) < game.getMap().getWidth() * TerrainTileset.TILE_SIZE &&
               vertical + unitTypes.getDimensionDown(unitType) < game.getMap().getHeight() * TerrainTileset.TILE_SIZE;
    }


    private final long @NotNull [] segments;
    private final int @NotNull [] adjustment;
    private int @NotNull [] candidates;

    /**
     * Creates a {@link UnitUnstacking} instance with empty segment and candidate storage.
     */
    UnitUnstacking() {
        this.segments   = new long[SIDE_COUNT];
        this.adjustment = new int[UnitPositioning.POSITION_COORDINATES];
        this.candidates = new int[0];
    }

    /**
     * Selects an escape destination for a unit on a position it cannot hold: off unwalkable terrain, to the nearest free side of a standing blocker,
     * or a random step away from a moving one, and gives the unit a one-waypoint path to it.
     *
     * @param game    the game
     * @param unit    the unit on the illegal position
     * @param blocker the unit that blocks the position, or {@code null} for a terrain-only check
     * @return {@code true} if the unit got an escape path, {@code false} otherwise
     * @throws IllegalStateException         if the unit or the blocker has no sprite
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the terrain contours, the unit spatial index or the path pool is not initialized
     */
    boolean findEscape(@NotNull Game game, @NotNull Unit unit, @Nullable Unit blocker) {
        Sprite sprite = unit.requireSprite();
        int horizontal = (short) sprite.getX();
        int vertical = (short) sprite.getY();
        int destinationX = horizontal;
        int destinationY = vertical;
        ScrUnitTypeView unitTypes = game.requireGameData().getUnitTypes();

        if (!game.getUnitPositioning().isTerrainPlacementWalkable(unit.getUnitId(), horizontal, vertical)) {
            long escape = this.findTerrainEscape(game, unit, horizontal, vertical);

            destinationX = (int) escape;
            destinationY = (int) (escape >>> PACKED_COORDINATE_SHIFT);
        } else if (blocker != null) {
            // Refresh the terrain collision rectangle that the side search clips to; the blocked result is not needed.
            game.getMovementCollision().isBlockedByTerrain(unit, horizontal, vertical);

            Sprite blockerSprite = blocker.requireSprite();
            boolean movingBlocker = (blocker.getStatus() & UnitStatusFlags.CAN_MOVE) != 0 &&
                                    (!UnitMovement.isSpriteAtMovementDestination(blocker, blockerSprite) ||
                                     blocker.getMovementState() == MovementStates.CHECK_ILLEGAL ||
                                     blocker.getMovementState() == MovementStates.MOVE_TO_LEGAL);
            boolean waitForBlocker = movingBlocker && game.getRandom().next(UNSTACK_RANDOM_SOURCE, 0, WAIT_ROLL_MAXIMUM) < WAIT_ROLL_LIMIT;

            if (!waitForBlocker) {
                long destination = movingBlocker ? Long.MIN_VALUE : this.findNearestSide(game, unit, blocker);

                if (destination == Long.MIN_VALUE) {
                    int direction = (unit.getTargetHeading() +
                                     ESCAPE_TURN_STEP * game.getRandom().next(UNSTACK_RANDOM_SOURCE, -ESCAPE_TURN_STEPS, ESCAPE_TURN_STEPS)) &
                                    FlingyMotionUtils.DIRECTION_MASK;
                    int length = game.getRandom().next(UNSTACK_RANDOM_SOURCE, MINIMUM_ESCAPE_STEPS, MAXIMUM_ESCAPE_STEPS) * ESCAPE_STEP_LENGTH;

                    if (!movingBlocker && UnitQueries.isGroundedBuilding(blocker)) {
                        length *= BUILDING_ESCAPE_FACTOR;
                    }

                    long vector = FlingyMotionUtils.calculateDirectionVector(direction, length << FIXED_POINT_FRACTION_BITS);

                    destinationX = horizontal + ((int) vector >> FIXED_POINT_FRACTION_BITS);
                    destinationY = vertical + ((int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT) >> FIXED_POINT_FRACTION_BITS);

                    if (isBlocked(game, unit, destinationX, destinationY) ||
                        !unit.sharesTerrainComponent(game.getPathSystem().requireTerrainContours().getRegions(), destinationX, destinationY)) {
                        int distance = RegionPathSearch.calculateDistance(horizontal, (short) unit.getMovementDestinationX(),
                                                                          vertical, (short) unit.getMovementDestinationY());

                        if (distance <= NEAR_DESTINATION_DISTANCE ||
                            game.getRandom().next(UNSTACK_RANDOM_SOURCE, 0, WAIT_ROLL_MAXIMUM) >= WAIT_ROLL_LIMIT) {
                            destinationX = horizontal +
                                           JITTER_STEP_LENGTH * game.getRandom().next(UNSTACK_RANDOM_SOURCE, -JITTER_STEPS, JITTER_STEPS);
                            destinationY = vertical + JITTER_STEP_LENGTH * game.getRandom().next(UNSTACK_RANDOM_SOURCE, -JITTER_STEPS, JITTER_STEPS);
                        } else {
                            direction = (FlingyMotionUtils.calculateDirection((short) unit.getMovementDestinationX() - horizontal,
                                                                              (short) unit.getMovementDestinationY() - vertical) +
                                         ESCAPE_TURN_STEP *
                                         game.getRandom().next(UNSTACK_RANDOM_SOURCE, -DESTINATION_TURN_STEPS, DESTINATION_TURN_STEPS)) &
                                        FlingyMotionUtils.DIRECTION_MASK;
                            length    = DESTINATION_ESCAPE_LENGTH +
                                        game.getRandom().next(UNSTACK_RANDOM_SOURCE, 0, DESTINATION_ESCAPE_STEPS) * ESCAPE_STEP_LENGTH;

                            if (!movingBlocker && UnitQueries.isGroundedBuilding(blocker)) {
                                length *= BUILDING_ESCAPE_FACTOR;
                            }

                            vector       = FlingyMotionUtils.calculateDirectionVector(direction, length << FIXED_POINT_FRACTION_BITS);
                            destinationX = horizontal + ((int) vector >> FIXED_POINT_FRACTION_BITS);
                            destinationY = vertical +
                                           ((int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT) >> FIXED_POINT_FRACTION_BITS);
                        }

                        // Test the whole map without a lower margin; the bottom margin applies to destination clamping below.
                        boolean reject = !isInsideBounds(game, unit, destinationX, destinationY) ||
                                         !unit.sharesTerrainComponent(game.getPathSystem().requireTerrainContours().getRegions(),
                                                                      destinationX, destinationY);

                        if (!reject && isBlocked(game, unit, destinationX, destinationY)) {
                            Unit candidate = game.getUnitSystem().requireUnitSpatialIndex().findMovementBlocker(game.getHooks().getCollisionRules(),
                                                                                                                unit, unitTypes,
                                                                                                                destinationX, destinationY);

                            reject = candidate == null || (movingBlocker && (candidate.getStatus() & UnitStatusFlags.CAN_MOVE) == 0);
                        }

                        if (reject) {
                            destinationX = horizontal;
                            destinationY = vertical;
                        }
                    }
                } else {
                    destinationX = (int) destination;
                    destinationY = (int) (destination >>> PACKED_COORDINATE_SHIFT);
                }
            }
        }

        int unitType = unit.getUnitId();

        destinationX = Math.max(unitTypes.getDimensionLeft(unitType),
                                Math.min(game.getMap().getWidth() * TerrainTileset.TILE_SIZE - 1 - unitTypes.getDimensionRight(unitType),
                                         destinationX));
        destinationY = Math.max(unitTypes.getDimensionUp(unitType),
                                Math.min(game.getMap().getHeight() * TerrainTileset.TILE_SIZE - (UnitPositioning.MAP_BOTTOM_MARGIN + 1) -
                                         unitTypes.getDimensionDown(unitType),
                                         destinationY));

        if (destinationX == horizontal && destinationY == vertical) {
            return false;
        }

        PathPool paths = game.getPathSystem().requirePathPool();

        if (unit.getPathIndex() >= 0) {
            paths.release(unit.getPathIndex());
            unit.setPathIndex(-1);
        }

        if (((short) unit.getMovementDestinationX() == horizontal && (short) unit.getMovementDestinationY() == vertical) ||
            (unit.getMovementFlags() & MovementFlags.BRAKING) != 0) {
            game.getUnitPositioning().setMovementDestination(unit, destinationX, destinationY);
        } else {
            unit.setDirectionTargetX(destinationX);
            unit.setDirectionTargetY(destinationY);

            if (unit.getMovementWaypointX() != (destinationX & WORD_MASK) || unit.getMovementWaypointY() != (destinationY & WORD_MASK)) {
                unit.setMovementWaypointX(destinationX);
                unit.setMovementWaypointY(destinationY);
                unit.setMovementFlags(unit.getMovementFlags() | MovementFlags.ORDERED);
            }
        }

        int pathIndex = paths.allocate();

        unit.setPathIndex(pathIndex);

        if (pathIndex >= 0) {
            int position = destinationX & WORD_MASK | destinationY << SECOND_WORD_SHIFT;

            paths.setRegionCount(pathIndex, 1);
            paths.setRegionIndex(pathIndex, 0);
            paths.setWaypointCount(pathIndex, 1);
            paths.setWaypointIndex(pathIndex, 0);
            paths.setFullRegionCount(pathIndex, 1);
            paths.setSourceX(pathIndex, horizontal);
            paths.setSourceY(pathIndex, vertical);
            paths.setWaypointPosition(pathIndex, position);
            paths.setDestination(pathIndex, position);
            paths.setFlags(pathIndex, 0);
            paths.setTimer(pathIndex, 0);
            paths.setWaypoint(pathIndex, 0, position);
        }

        unit.setTerrainCollisionLeft(0);
        unit.setTerrainCollisionTop(0);
        unit.setTerrainCollisionRight(0);
        unit.setTerrainCollisionBottom(0);

        return true;
    }

    /**
     * Selects a destination for a unit whose position cannot hold its type: inside a walkable region the unit moves a random {@code 16..32} pixels
     * (random source {@link #UNSTACK_RANDOM_SOURCE}) toward the region center, turned by up to two {@code 16}-unit steps and kept short of the
     * center; inside an unwalkable region it moves to the nearest walkable neighbor ({@code 64} pixels toward the center of the last walkable
     * neighbor when the nearest one's area is more than {@code 32} pixels away, otherwise to that region's adjusted destination, or {@code 64} pixels
     * up when that is the current position), and without walkable neighbors up to {@code 64} pixels toward a random unwalkable neighbor.
     *
     * @param game       the game
     * @param unit       the unit on the illegal position
     * @param horizontal the horizontal pixel position of the unit
     * @param vertical   the vertical pixel position of the unit
     * @return the destination, X in the low DWORD and Y in the high DWORD
     * @throws UnsupportedOperationException if the terrain contours are not initialized
     */
    private long findTerrainEscape(@NotNull Game game, @NotNull Unit unit, int horizontal, int vertical) {
        TerrainRegions regions = game.getPathSystem().requireTerrainContours().getRegions();
        int region = TerrainRegionLookupUtils.lookup(regions, horizontal, vertical);
        int destinationX = horizontal;
        int destinationY = vertical;

        if (regions.getTerrainType(region) != TerrainRegions.BLOCKED_TERRAIN) {
            int centerX = regions.getCenterX(region);
            int centerY = regions.getCenterY(region);
            int direction = (FlingyMotionUtils.calculateDirection(centerX - horizontal, centerY - vertical) +
                             ESCAPE_TURN_STEP * game.getRandom().next(UNSTACK_RANDOM_SOURCE, -TERRAIN_TURN_STEPS, TERRAIN_TURN_STEPS)) &
                            FlingyMotionUtils.DIRECTION_MASK;
            // Keep both lengths with eight fraction bits, cap one raw unit (1/256 pixel) short of the center, then truncate the vector to pixel
            // coordinates.
            int length = game.getRandom().next(UNSTACK_RANDOM_SOURCE, MINIMUM_ESCAPE_STEPS, MAXIMUM_ESCAPE_STEPS) * TERRAIN_ESCAPE_STEP_LENGTH <<
                         FIXED_POINT_FRACTION_BITS;
            int centerLength = RegionPathSearch.calculateDistance(horizontal, centerX, vertical, centerY) << FIXED_POINT_FRACTION_BITS;

            if (length > centerLength - 1) {
                length = centerLength - 1;
            }

            long vector = FlingyMotionUtils.calculateDirectionVector(direction, length);

            destinationX = horizontal + ((int) vector >> FIXED_POINT_FRACTION_BITS);
            destinationY = vertical + ((int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT) >> FIXED_POINT_FRACTION_BITS);

            if (!unit.sharesTerrainComponent(regions, destinationX, destinationY)) {
                destinationX = horizontal;
                destinationY = vertical;
            }

            return (destinationX & DWORD_MASK) | (long) destinationY << PACKED_COORDINATE_SHIFT;
        }

        int walkableCount = regions.getWalkableNeighborCount(region);
        int bestDistance = Integer.MAX_VALUE;
        int bestRegion = -1;
        int lastCenterX = 0;
        int lastCenterY = 0;

        for (int neighbor = 0; neighbor < walkableCount; neighbor++) {
            int candidate = regions.getNeighborRegion(region, neighbor);

            lastCenterX = regions.getCenterX(candidate);
            lastCenterY = regions.getCenterY(candidate);

            int distance = RegionPathSearch.calculateDistance(horizontal, lastCenterX, vertical, lastCenterY);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestRegion   = candidate;
            }
        }

        if (bestRegion >= 0) {
            boolean outside = horizontal + NEIGHBOR_MARGIN < regions.getLeftBound(bestRegion) ||
                              horizontal - NEIGHBOR_MARGIN >= regions.getRightBound(bestRegion) ||
                              vertical + NEIGHBOR_MARGIN < regions.getUpperBound(bestRegion) ||
                              vertical - NEIGHBOR_MARGIN >= regions.getLowerBound(bestRegion);

            if (outside) {
                int direction = FlingyMotionUtils.calculateDirection(lastCenterX - horizontal, lastCenterY - vertical);
                long vector = FlingyMotionUtils.calculateDirectionVector(direction, NEIGHBOR_ESCAPE_LENGTH);

                destinationX = horizontal + (int) vector;
                destinationY = vertical + (int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT);
            } else {
                int[] destination = this.adjustment;

                destination[UnitPositioning.X_INDEX] = horizontal;
                destination[UnitPositioning.Y_INDEX] = vertical;

                TerrainRegionLookupUtils.adjustDestinationToRegion(regions,
                                                                   game.getMap().getWidth(), game.getMap().getHeight(),
                                                                   bestRegion,
                                                                   destination);

                destinationX = destination[UnitPositioning.X_INDEX];
                destinationY = destination[UnitPositioning.Y_INDEX];

                if (destinationX == horizontal && destinationY == vertical) {
                    destinationY = vertical - NEIGHBOR_ESCAPE_LENGTH;
                }
            }
        } else if (regions.getNeighborCount(region) > walkableCount) {
            int candidate = regions.getNeighborRegion(
                    region,
                    walkableCount + game.getRandom().next(UNSTACK_RANDOM_SOURCE, 0, regions.getNeighborCount(region) - walkableCount - 1)
            );
            int centerX = regions.getCenterX(candidate);
            int centerY = regions.getCenterY(candidate);
            int length = Math.min(RegionPathSearch.calculateDistance(horizontal, centerX, vertical, centerY), NEIGHBOR_ESCAPE_LENGTH);
            int direction = FlingyMotionUtils.calculateDirection(centerX - horizontal, centerY - vertical);
            long vector = FlingyMotionUtils.calculateDirectionVector(direction, length);

            destinationX = horizontal + (int) vector;
            destinationY = vertical + (int) (vector >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT);
        }

        return (destinationX & DWORD_MASK) | (long) destinationY << PACKED_COORDINATE_SHIFT;
    }

    /**
     * Updates the MoveToLegal state: follows the one-waypoint escape path and enters the illegal position check when the waypoint is reached or
     * cannot be approached; a unit stopped on the way gives up and rests colliding.
     *
     * @param context          the movement context
     * @param game             the game
     * @param unit             the unit moving to a legal position
     * @param instructionLimit the instruction budget of the movement animations
     * @return {@code true} if the movement dispatcher continues with the next state in this frame, {@code false} otherwise
     * @throws UnsupportedOperationException if the path pool, the unit path workspace or the unit spatial index is not initialized
     * @throws NullPointerException          if the world was built without game data
     * @throws IllegalStateException         if the unit has no sprite
     */
    boolean moveToLegal(@NotNull UnitMovement.Context context, @NotNull Game game, @NotNull Unit unit, int instructionLimit) {
        int pathIndex = unit.getPathIndex();

        if (pathIndex < 0) {
            unit.setMovementState(MovementStates.CHECK_ILLEGAL);

            return true;
        }

        PathPool paths = game.getPathSystem().requirePathPool();

        if ((unit.getPathCollisionFlags() & UnitInitializer.STOPPED_MOVING_TO_LEGAL_PATH_FLAG) != 0) {
            unit.setPathCollisionFlags(unit.getPathCollisionFlags() & ~UnitInitializer.STOPPED_MOVING_TO_LEGAL_PATH_FLAG);
            unit.setStatus(unit.getStatus() | UnitStatusFlags.COLLISION);
            paths.release(pathIndex);
            unit.setPathIndex(-1);
            unit.setMovementState(MovementStates.AT_REST);

            return true;
        }

        int waypoint = paths.getWaypointPosition(pathIndex);

        if ((paths.getFlags(pathIndex) & PathPool.FLAG_NEW_MOVE_TARGET) != 0 ||
            waypoint != (unit.getMovementWaypointX() | unit.getMovementWaypointY() << SECOND_WORD_SHIFT)) {
            unit.setMovementWaypointX(waypoint);
            unit.setMovementWaypointY(waypoint >>> SECOND_WORD_SHIFT);
        }

        boolean finished = !game.getPathSystem().requireUnitPath().isKeepingCurrentWaypoint(game, unit, paths);

        if (!finished) {
            FlingyMotionUtils.update(context, unit, game.requireGameData().getFlingyProperties());

            if (isInsideBounds(game, unit, context.getPositionX(), context.getPositionY())) {
                game.getUnitMovement().commitMotion(context, unit, instructionLimit);
            } else {
                int direction = FlingyMotionUtils.calculateDirection((short) unit.getMovementWaypointX() - (short) unit.requireSprite().getX(),
                                                                     (short) unit.getMovementWaypointY() - (short) unit.requireSprite().getY());

                if (unit.getTargetHeading() == direction) {
                    finished = true;
                } else {
                    FlingyMotionUtils.update(context, unit, game.requireGameData().getFlingyProperties());
                    unit.setTargetHeading(unit.getMovementDirection());
                    FlingyMotionUtils.updateFacing(unit, game.requireGameData().getFlingyProperties());
                    IscriptExecutor.applySpriteDirection(game, unit.requireSprite(), unit.getHeading());
                }
            }
        }

        if (finished) {
            paths.release(pathIndex);
            unit.setPathIndex(-1);
            game.getUnitSystem().requireUnitSpatialIndex().notifyCollision(unit, game.requireGameData().getUnitTypes());
            unit.setStatus(unit.getStatus() | UnitStatusFlags.COLLISION);
            unit.setMovementState(MovementStates.CHECK_ILLEGAL);
        }

        return false;
    }

    private long findNearestSide(@NotNull Game game, @NotNull Unit unit, @NotNull Unit blocker) {
        ScrUnitTypeView unitTypes = game.requireGameData().getUnitTypes();
        long bounds = calculateExpandedBounds(unitTypes, blocker, unit);
        int left = (short) bounds;
        int top = (short) (bounds >>> SECOND_WORD_SHIFT);
        int right = (short) (bounds >>> THIRD_WORD_SHIFT);
        int bottom = (short) (bounds >>> FOURTH_WORD_SHIFT);

        this.segments[TOP_SEGMENT]    = packRectangle(left, top, right, top);
        this.segments[RIGHT_SEGMENT]  = packRectangle(right, top, right, bottom);
        this.segments[BOTTOM_SEGMENT] = packRectangle(left, bottom, right, bottom);
        this.segments[LEFT_SEGMENT]   = packRectangle(left, top, left, bottom);

        int count = this.clipSegments(SIDE_COUNT,
                                      packRectangle(unit.getTerrainCollisionLeft(), unit.getTerrainCollisionTop(),
                                                    unit.getTerrainCollisionRight(), unit.getTerrainCollisionBottom()));
        int capacity = game.getUnitSystem().requireUnitSpatialIndex().getQueryCapacity();

        if (this.candidates.length < capacity) {
            this.candidates = new int[capacity];
        }

        int unitType = unit.getUnitId();
        int candidateCount = game.getUnitSystem().requireUnitSpatialIndex().collectUnstackingCandidates(left - unitTypes.getDimensionLeft(unitType),
                                                                                                        top - unitTypes.getDimensionUp(unitType),
                                                                                                        right + unitTypes.getDimensionRight(unitType),
                                                                                                        bottom + unitTypes.getDimensionDown(unitType),
                                                                                                        this.candidates);

        for (int index = 0; index < candidateCount; index++) {
            Unit candidate = game.getUnitSystem().requireUnitPool().getUnit(this.candidates[index]);

            if (PathObstacleCollector.isBlockingPath(game.getHooks().getCollisionRules(), unitTypes,
                                                     candidate, unit,
                                                     INCLUDE_MOVING_BLOCKERS, NO_SELECTED_UNIT)) {
                count = this.clipSegments(count, calculateExpandedBounds(unitTypes, candidate, unit));
            }
        }

        int horizontal = (short) unit.requireSprite().getX();
        int vertical = (short) unit.requireSprite().getY();
        int bestDistance = NO_SIDE_DISTANCE;
        int bestHorizontal = horizontal;
        int bestVertical = vertical;

        for (int index = 0; index < count; index++) {
            long segment = this.segments[index];
            int candidateX = Math.max((short) segment, Math.min((short) (segment >>> THIRD_WORD_SHIFT), horizontal));
            int candidateY = Math.max((short) (segment >>> SECOND_WORD_SHIFT), Math.min((short) (segment >>> FOURTH_WORD_SHIFT), vertical));
            int distance = RegionPathSearch.calculateDistance(horizontal, candidateX, vertical, candidateY);

            if (distance < bestDistance) {
                bestDistance   = distance;
                bestHorizontal = candidateX;
                bestVertical   = candidateY;
            }
        }

        if (bestDistance != NO_SIDE_DISTANCE && game.getUnitPositioning().isTerrainPlacementWalkable(unitType, bestHorizontal, bestVertical) &&
            !isBlocked(game, unit, bestHorizontal, bestVertical) &&
            unit.sharesTerrainComponent(game.getPathSystem().requireTerrainContours().getRegions(), bestHorizontal, bestVertical)) {
            return (bestHorizontal & DWORD_MASK) | (long) bestVertical << PACKED_COORDINATE_SHIFT;
        }

        return Long.MIN_VALUE;
    }

    private int clipSegments(int count, long bounds) {
        int retained = 0;

        for (int index = 0; index < count; index++) {
            long clipped = clipSegment(this.segments[index], bounds);

            if (clipped != Long.MIN_VALUE) {
                this.segments[retained++] = clipped;
            }
        }

        return retained;
    }
}

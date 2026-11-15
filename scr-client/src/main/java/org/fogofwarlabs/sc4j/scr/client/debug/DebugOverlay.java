package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegions;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Draws debug overlays over the game view from domain state only, in screen space after the world pass, so both the classic and the HD renderer show
 * them. Tile overlays read packed visibility, exploration and terrain flags from {@link GameMap#getTileFlags(int)}.
 */
public final class DebugOverlay {
    private static final int UNIT_CULL_MARGIN = 64;
    private static final int COLLISION_COLOR = 0x40FF40C0;
    private static final int ORDER_TARGET_COLOR = 0xFF4040E0;
    private static final int MOVEMENT_TARGET_COLOR = 0x40A0FFE0;
    private static final int ORDER_TEXT_HEIGHT = 10;
    private static final int ORDER_TEXT_OFFSET = 6;
    private static final int ORDER_TEXT_COLOR = 0xFFFFFFE0;
    private static final int PATH_COLOR = 0xFFFF40C0;
    private static final int WAYPOINT_COLOR = 0xFFFF40FF;
    private static final int WAYPOINT_RADIUS = 2;
    private static final int WAYPOINT_DIAMETER = 4;
    private static final int CURRENT_WAYPOINT_COLOR = 0x40FF40E0;
    private static final int DESTINATION_RADIUS = 3;
    private static final int DESTINATION_DIAMETER = 6;
    private static final int DESTINATION_COLOR = 0x40FF40FF;
    private static final int ORDER_TARGET_RADIUS = 3;
    private static final int BLOCKED_TILE_COLOR = 0xFF000050;
    private static final int OCCUPIED_TILE_COLOR = 0xFFA00080;
    private static final int CREEP_TILE_COLOR = 0xA040FF50;
    private static final int UNEXPLORED_TILE_COLOR = 0x000000C0;
    private static final int UNSEEN_TILE_COLOR = 0x00000070;
    private static final int REGION_COLOR = 0xFFFF4060;
    private static final int REGION_CENTER_DIAMETER = 3;
    private static final int REGION_CENTER_COLOR = 0xFFFF40C0;
    private static final int REGION_TEXT_HORIZONTAL_OFFSET = 3;
    private static final int REGION_TEXT_VERTICAL_OFFSET = 5;
    private static final int REGION_TEXT_HEIGHT = 9;
    private static final int REGION_TEXT_COLOR = 0xFFFF80C0;
    private static final float LINE_WIDTH = 1.0f;
    private static final float CURRENT_WAYPOINT_LINE_WIDTH = 1.5f;
    private static final float TARGET_LINE_WIDTH = 1.5f;
    private static final float REGION_CENTER_RADIUS = 1.5f;
    private static final int BYTE_MASK = 0xFF;

    /**
     * An overlay layer.
     */
    public enum Layer {
        /**
         * Path waypoints, current waypoint, movement destination, order target position and target unit lines.
         */
        PATH("waypoints, current waypoint, destination and order target lines"),
        /**
         * Unit collision boxes from {@code units.dat} {@code Unit Size}.
         */
        COLLISION("unit collision boxes (units.dat unit size)"),
        /**
         * Primary and secondary order, order state and movement flags next to each unit.
         */
        ORDERS("orders, order state and movement flags next to each unit"),
        /**
         * Terrain region bounds and centers.
         */
        REGIONS("terrain region bounds and centres"),
        /**
         * Unwalkable and occupied tiles.
         */
        PATHING("unwalkable and occupied tiles"),
        /**
         * Creep tiles.
         */
        CREEP("creep tiles"),
        /**
         * Tiles the vision player does not see or has not explored.
         */
        VISION("tiles the vision player does not see or has not explored");

        private final @NotNull String description;

        /**
         * Creates a {@link Layer} instance with its command help description.
         *
         * @param description what the layer draws
         */
        Layer(@NotNull String description) {
            this.description = description;
        }

        /**
         * @return the layer's name in commands, for example {@code path}
         */
        public @NotNull String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        /**
         * @return what the layer draws, for the command help
         */
        public @NotNull String getDescription() {
            return this.description;
        }
    }

    /**
     * Converts map positions to screen positions.
     */
    public interface View {
        /**
         * @return the screen pixels per map pixel
         */
        float getZoom();

        /**
         * Converts a horizontal map position to a screen column.
         *
         * @param mapX the horizontal map position
         * @return the screen column
         */
        float toScreenX(float mapX);

        /**
         * Converts a vertical map position to a screen row.
         *
         * @param mapY the vertical map position
         * @return the screen row
         */
        float toScreenY(float mapY);
    }

    /**
     * Draws the unit's movement: its current waypoint, the remaining waypoints of its path record (packed as {@code x | y << 16}, from the current
     * waypoint index to the waypoint count), the movement destination and the order target position of a moving unit.
     *
     * @param gameView the game view
     * @param renderer the interface renderer between {@code begin} and {@code end}
     * @param view     the camera transform
     * @param unit     the unit whose movement is drawn
     */
    private static void drawPath(@NotNull ScrGameView gameView, @NotNull InterfaceRenderer renderer, @NotNull View view, @NotNull Unit unit) {
        boolean moving = unit.getMovementDestinationX() != unit.getPositionX() || unit.getMovementDestinationY() != unit.getPositionY();
        float lastX = view.toScreenX(unit.getPositionX());
        float lastY = view.toScreenY(unit.getPositionY());
        PathPool pathPool = gameView.getPathPool();
        int recordIndex = unit.getPathIndex();

        if (pathPool != null && recordIndex >= 0) {
            int waypointCount = pathPool.getWaypointCount(recordIndex) & BYTE_MASK;

            for (int index = pathPool.getWaypointIndex(recordIndex) & BYTE_MASK; index < waypointCount; index++) {
                int waypoint = pathPool.getWaypoint(recordIndex, index);
                float x = view.toScreenX((short) waypoint);
                float y = view.toScreenY((short) (waypoint >>> Short.SIZE));

                renderer.drawLine(lastX, lastY, x, y, LINE_WIDTH, PATH_COLOR);
                renderer.fillRectangle(x - WAYPOINT_RADIUS, y - WAYPOINT_RADIUS, WAYPOINT_DIAMETER, WAYPOINT_DIAMETER, WAYPOINT_COLOR);

                lastX = x;
                lastY = y;
            }
        }

        if (moving) {
            float waypointX = view.toScreenX(unit.getMovementWaypointX());
            float waypointY = view.toScreenY(unit.getMovementWaypointY());

            renderer.drawLine(view.toScreenX(unit.getPositionX()),
                              view.toScreenY(unit.getPositionY()),
                              waypointX,
                              waypointY,
                              CURRENT_WAYPOINT_LINE_WIDTH,
                              CURRENT_WAYPOINT_COLOR);

            float destinationX = view.toScreenX(unit.getMovementDestinationX());
            float destinationY = view.toScreenY(unit.getMovementDestinationY());

            renderer.drawRectangle(destinationX - DESTINATION_RADIUS,
                                   destinationY - DESTINATION_RADIUS,
                                   DESTINATION_DIAMETER,
                                   DESTINATION_DIAMETER,
                                   LINE_WIDTH,
                                   DESTINATION_COLOR);
        }

        if (unit.getOrderTargetUnit() == null && (unit.getOrderTargetX() != 0 || unit.getOrderTargetY() != 0)) {
            float targetX = view.toScreenX(unit.getOrderTargetX());
            float targetY = view.toScreenY(unit.getOrderTargetY());

            renderer.drawLine(targetX - ORDER_TARGET_RADIUS,
                              targetY - ORDER_TARGET_RADIUS,
                              targetX + ORDER_TARGET_RADIUS,
                              targetY + ORDER_TARGET_RADIUS,
                              LINE_WIDTH,
                              ORDER_TARGET_COLOR);
            renderer.drawLine(targetX - ORDER_TARGET_RADIUS,
                              targetY + ORDER_TARGET_RADIUS,
                              targetX + ORDER_TARGET_RADIUS,
                              targetY - ORDER_TARGET_RADIUS,
                              LINE_WIDTH,
                              ORDER_TARGET_COLOR);
        }
    }

    private static void drawTargetLine(@NotNull InterfaceRenderer renderer, @NotNull View view, @NotNull Unit unit, @Nullable Unit target, int color) {
        if (target == null || target == unit || target.getSpriteOrNull() == null) {
            return;
        }

        renderer.drawLine(view.toScreenX(unit.getPositionX()),
                          view.toScreenY(unit.getPositionY()),
                          view.toScreenX(target.getPositionX()),
                          view.toScreenY(target.getPositionY()),
                          TARGET_LINE_WIDTH,
                          color);
    }

    private final @NotNull Set<Layer> layers;

    /**
     * Creates a {@link DebugOverlay} instance with the path, collision and order layers enabled.
     */
    public DebugOverlay() {
        this.layers = EnumSet.of(Layer.PATH, Layer.COLLISION, Layer.ORDERS);
    }

    /**
     * @param layer the layer
     * @return {@code true} if the layer is drawn, {@code false} otherwise
     */
    public boolean isEnabled(@NotNull Layer layer) {
        return this.layers.contains(layer);
    }

    /**
     * Sets whether the given debug layer is drawn.
     *
     * @param layer   the layer
     * @param enabled whether the layer is drawn
     */
    public void setEnabled(@NotNull Layer layer, boolean enabled) {
        if (enabled) {
            this.layers.add(layer);
        } else {
            this.layers.remove(layer);
        }
    }

    /**
     * @return the enabled layers
     */
    public @NotNull Set<Layer> getEnabledLayers() {
        return EnumSet.copyOf(this.layers.isEmpty() ? EnumSet.noneOf(Layer.class) : this.layers);
    }

    /**
     * Draws the enabled layers.
     *
     * @param gameView     the game view
     * @param renderer     the interface renderer between {@code begin} and {@code end}
     * @param view         the camera transform
     * @param viewWidth    the view width in screen pixels
     * @param viewHeight   the view height in screen pixels
     * @param visionPlayer the player whose vision the {@link Layer#VISION} layer shows
     * @throws NullPointerException if the world was built without game data
     */
    public void draw(@NotNull ScrGameView gameView, @NotNull InterfaceRenderer renderer, @NotNull View view,
                     float viewWidth, float viewHeight, int visionPlayer) {
        GameMap map = gameView.getMap();

        if (map.hasTileFlags()) {
            this.drawTiles(map, renderer, view, viewWidth, viewHeight, visionPlayer);
        }

        if (this.layers.contains(Layer.REGIONS) && gameView.getTerrainContours() != null) {
            this.drawRegions(gameView.getTerrainContours().getRegions(), renderer, view, viewWidth, viewHeight);
        }

        UnitPool unitPool = gameView.getUnitSystem().getUnitPool();

        if (unitPool == null) {
            return;
        }

        ScrUnitTypeView unitTypes = gameView.requireGameData().getUnitTypes();

        for (int index = 1; index <= unitPool.getCapacity(); index++) {
            if (!unitPool.isAllocated(index)) {
                continue;
            }

            Unit unit = unitPool.getUnit(index);

            if (unit.getSpriteOrNull() == null) {
                continue;
            }

            float x = view.toScreenX(unit.getPositionX());
            float y = view.toScreenY(unit.getPositionY());

            if (x < -UNIT_CULL_MARGIN || y < -UNIT_CULL_MARGIN || x > viewWidth + UNIT_CULL_MARGIN || y > viewHeight + UNIT_CULL_MARGIN) {
                continue;
            }

            if (this.layers.contains(Layer.COLLISION) && unit.getUnitId() < unitTypes.getCount()) {
                float left = view.toScreenX(unit.getPositionX() - unitTypes.getDimensionLeft(unit.getUnitId()));
                float top = view.toScreenY(unit.getPositionY() - unitTypes.getDimensionUp(unit.getUnitId()));
                float right = view.toScreenX(unit.getPositionX() + unitTypes.getDimensionRight(unit.getUnitId()) + 1);
                float bottom = view.toScreenY(unit.getPositionY() + unitTypes.getDimensionDown(unit.getUnitId()) + 1);

                renderer.drawRectangle(left, top, right - left, bottom - top, LINE_WIDTH, COLLISION_COLOR);
            }

            if (this.layers.contains(Layer.PATH)) {
                drawPath(gameView, renderer, view, unit);
                drawTargetLine(renderer, view, unit, unit.getOrderTargetUnit(), ORDER_TARGET_COLOR);
                drawTargetLine(renderer, view, unit, unit.getMovementTargetUnit(), MOVEMENT_TARGET_COLOR);
            }

            if (this.layers.contains(Layer.ORDERS)) {
                String text = String.format(Locale.ROOT, "o%d/%d m%02X", unit.getPrimaryOrder(), unit.getSecondaryOrder(), unit.getMovementFlags());

                renderer.drawText(text,
                                  x - renderer.measureText(text, ORDER_TEXT_HEIGHT) / 2,
                                  y + ORDER_TEXT_OFFSET,
                                  ORDER_TEXT_HEIGHT,
                                  ORDER_TEXT_COLOR);
            }
        }
    }

    private void drawTiles(@NotNull GameMap map,
                           @NotNull InterfaceRenderer renderer, @NotNull View view,
                           float viewWidth, float viewHeight,
                           int visionPlayer) {
        boolean pathing = this.layers.contains(Layer.PATHING);
        boolean creep = this.layers.contains(Layer.CREEP);
        boolean vision = this.layers.contains(Layer.VISION);

        if (!pathing && !creep && !vision) {
            return;
        }

        float size = TerrainTileset.TILE_SIZE * view.getZoom();

        for (int tileY = 0; tileY < map.getHeight(); tileY++) {
            float top = view.toScreenY(tileY * TerrainTileset.TILE_SIZE);

            if (top > viewHeight || top + size < 0) {
                continue;
            }

            for (int tileX = 0; tileX < map.getWidth(); tileX++) {
                float left = view.toScreenX(tileX * TerrainTileset.TILE_SIZE);

                if (left > viewWidth || left + size < 0) {
                    continue;
                }

                int flags = map.getTileFlags(tileX, tileY);
                int terrain = flags >>> Short.SIZE;

                if (pathing && (terrain & (MapTileFlags.WALKABLE >>> Short.SIZE)) == 0) {
                    renderer.fillRectangle(left, top, size, size, BLOCKED_TILE_COLOR);
                }

                if (pathing && (terrain & (MapTileFlags.OCCUPIED >>> Short.SIZE)) != 0) {
                    renderer.drawRectangle(left, top, size, size, LINE_WIDTH, OCCUPIED_TILE_COLOR);
                }

                if (creep && (terrain & (MapTileFlags.HAS_CREEP >>> Short.SIZE)) != 0) {
                    renderer.fillRectangle(left, top, size, size, CREEP_TILE_COLOR);
                }

                if (vision && visionPlayer >= 0 && visionPlayer < PlayerTable.PLAYABLE_PLAYER_SLOTS) {
                    boolean seen = (flags & 1 << visionPlayer) == 0;
                    boolean explored = (flags & 1 << visionPlayer + MapTileFlags.NOT_EXPLORED_SHIFT) == 0;

                    if (!explored) {
                        renderer.fillRectangle(left, top, size, size, UNEXPLORED_TILE_COLOR);
                    } else if (!seen) {
                        renderer.fillRectangle(left, top, size, size, UNSEEN_TILE_COLOR);
                    }
                }
            }
        }
    }

    private void drawRegions(@NotNull TerrainRegions regions,
                             @NotNull InterfaceRenderer renderer, @NotNull View view,
                             float viewWidth, float viewHeight) {
        for (int region = 0; region < regions.getRegionCount(); region++) {
            float left = view.toScreenX(regions.getLeftBound(region));
            float top = view.toScreenY(regions.getUpperBound(region));
            float right = view.toScreenX(regions.getRightBound(region));
            float bottom = view.toScreenY(regions.getLowerBound(region));

            if (right < 0 || bottom < 0 || left > viewWidth || top > viewHeight) {
                continue;
            }

            renderer.drawRectangle(left, top, right - left, bottom - top, LINE_WIDTH, REGION_COLOR);

            float centerX = view.toScreenX(regions.getCenterX(region));
            float centerY = view.toScreenY(regions.getCenterY(region));

            renderer.fillRectangle(centerX - REGION_CENTER_RADIUS,
                                   centerY - REGION_CENTER_RADIUS,
                                   REGION_CENTER_DIAMETER,
                                   REGION_CENTER_DIAMETER,
                                   REGION_CENTER_COLOR);
            renderer.drawText(Integer.toString(region),
                              centerX + REGION_TEXT_HORIZONTAL_OFFSET,
                              centerY - REGION_TEXT_VERTICAL_OFFSET,
                              REGION_TEXT_HEIGHT,
                              REGION_TEXT_COLOR);
        }
    }
}

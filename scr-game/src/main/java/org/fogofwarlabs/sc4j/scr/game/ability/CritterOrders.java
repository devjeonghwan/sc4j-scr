package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.jetbrains.annotations.NotNull;

/**
 * Executes the wandering order of map critters.
 * <p>
 * Choosing a destination uses the game RNG and movable bounds. After arrival, other critters pause and the Kakaru wanders again immediately; paused
 * critters restart when the order timer expires.
 */
public final class CritterOrders {
    /**
     * The order states: choosing a destination, moving to it, pausing.
     */
    private static final int STATE_WANDER = 0;
    private static final int STATE_MOVING = 1;
    private static final int STATE_PAUSING = 2;

    /**
     * The random source of the wandering direction and the Kakaru distance.
     */
    private static final int WANDER_RANDOM_SOURCE = 48;
    /**
     * The random source of the pause length.
     */
    private static final int PAUSE_RANDOM_SOURCE = 47;
    /**
     * The highest random direction.
     */
    private static final int LAST_DIRECTION = 255;
    /**
     * The wandering distance of every critter but the Kakaru, in pixels.
     */
    private static final int WANDER_DISTANCE = 32;
    /**
     * The random wandering distance range of the Kakaru, in pixels.
     */
    private static final int KAKARU_MINIMUM_DISTANCE = 160;
    private static final int KAKARU_MAXIMUM_DISTANCE = 640;
    /**
     * The longest random pause, in frames.
     */
    private static final int MAXIMUM_PAUSE = 75;
    /**
     * A random pause of at most this many frames becomes no pause.
     */
    private static final int SHORT_PAUSE_LIMIT = 15;
    /**
     * The pause of an immovable critter, in frames.
     */
    private static final int IMMOVABLE_PAUSE = 75;

    private final @NotNull Game game;

    /**
     * Creates a {@link CritterOrders} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public CritterOrders(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Advances a critter's wandering order.
     *
     * @param unit the critter executing {@link ScrOrderIds#CRITTER}
     * @throws UnsupportedOperationException if the critter's sprite is missing
     * @throws NullPointerException          if the world was built without game data
     */
    public void update(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            throw Exceptions.newUnsupportedOperationException("Critter wandering requires the unit sprite.");
        }

        switch (unit.getPrimaryOrderState()) {
            case STATE_WANDER -> {
                int direction = this.game.getRandom().next(WANDER_RANDOM_SOURCE, 0, LAST_DIRECTION);
                int distance = this.game.getBaseType(unit) == ScrUnitIds.CRITTER_KAKARU
                               ? this.game.getRandom().next(WANDER_RANDOM_SOURCE, KAKARU_MINIMUM_DISTANCE, KAKARU_MAXIMUM_DISTANCE)
                               : WANDER_DISTANCE;
                long offset = FlingyMotionUtils.calculateDirectionVector(direction, distance);
                int targetX = (short) sprite.getX() + (int) offset;
                int targetY = (short) sprite.getY() + (int) (offset >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT);
                ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
                int unitType = unit.getUnitId();

                targetX = UnitPositioning.clampCoordinate(targetX,
                                                          units.getDimensionLeft(unitType), units.getDimensionRight(unitType),
                                                          this.game.getMap().getWidth() * TerrainTileset.TILE_SIZE,
                                                          0);
                targetY = UnitPositioning.clampCoordinate(targetY,
                                                          units.getDimensionUp(unitType), units.getDimensionDown(unitType),
                                                          this.game.getMap().getHeight() * TerrainTileset.TILE_SIZE,
                                                          UnitPositioning.MAP_BOTTOM_MARGIN);

                unit.setOrderTargetX(targetX);
                unit.setOrderTargetY(targetY);
                this.game.getEngineGame().getMovement().setDestination(unit, targetX, targetY);
                unit.setPrimaryOrderState(STATE_MOVING);
            }
            case STATE_MOVING -> {
                if ((short) sprite.getX() != (short) unit.getMovementDestinationX() ||
                    (short) sprite.getY() != (short) unit.getMovementDestinationY()) {
                    return;
                }

                if (this.game.getBaseType(unit) == ScrUnitIds.CRITTER_KAKARU) {
                    unit.setPrimaryOrderState(STATE_WANDER);
                } else if ((unit.getStatus() & UnitStatusFlags.IMMOVABLE) != 0) {
                    unit.setOrderTimer(IMMOVABLE_PAUSE);
                    unit.setPrimaryOrderState(STATE_PAUSING);
                } else {
                    int timer = this.game.getRandom().next(PAUSE_RANDOM_SOURCE, 0, MAXIMUM_PAUSE);

                    unit.setOrderTimer(timer <= SHORT_PAUSE_LIMIT ? 0 : timer);
                    unit.setPrimaryOrderState(STATE_PAUSING);
                }
            }
            case STATE_PAUSING -> {
                if (unit.getOrderTimer() == 0) {
                    unit.setPrimaryOrderState(STATE_WANDER);
                }
            }
            default -> {
            }
        }
    }
}

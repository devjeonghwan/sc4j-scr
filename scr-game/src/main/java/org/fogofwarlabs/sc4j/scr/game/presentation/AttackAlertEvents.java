package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Retains under-attack alert calls made during the current frame for local presentation without changing game state or drawing random numbers. The
 * consumer applies replay, owner and alert cooldown checks.
 * <p>
 * Game code fills the queue during a frame; the game clears it when the frame advances, and the client reads it after each played frame without
 * clearing it.
 */
public final class AttackAlertEvents {
    /**
     * The initial number of reusable event slots.
     */
    private static final int INITIAL_CAPACITY = 8;

    /**
     * One alert call.
     */
    private static final class Event {
        private int poolIndex;

        private int owner;
        private int unitType;

        private int positionX;
        private int positionY;

        private Event() {
            this.poolIndex = 0;

            this.owner    = 0;
            this.unitType = 0;

            this.positionX = 0;
            this.positionY = 0;
        }
    }

    private final @NotNull FrameEventBuffer<Event> events;

    /**
     * Creates an {@link AttackAlertEvents} instance with reusable event storage.
     */
    public AttackAlertEvents() {
        this.events = FrameEventBuffer.createReusing(Event::new, INITIAL_CAPACITY);
    }

    /**
     * @return the retained event count
     */
    public int getCount() {
        return this.events.getCount();
    }

    /**
     * @param index the event position
     * @return the unit pool index of the damaged unit
     */
    public int getPoolIndex(int index) {
        return this.events.get(index).poolIndex;
    }

    /**
     * @param index the event position
     * @return the owner of the damaged unit, which the consumer compares with the local player
     */
    public int getOwner(int index) {
        return this.events.get(index).owner;
    }

    /**
     * @param index the event position
     * @return the unit type of the damaged unit, which selects the building or unit alert sound
     */
    public int getUnitType(int index) {
        return this.events.get(index).unitType;
    }

    /**
     * @param index the event position
     * @return the horizontal sprite position of the damaged unit (its unit position without a sprite), the center of the alert area
     */
    public int getPositionX(int index) {
        return this.events.get(index).positionX;
    }

    /**
     * @param index the event position
     * @return the vertical sprite position of the damaged unit (its unit position without a sprite)
     */
    public int getPositionY(int index) {
        return this.events.get(index).positionY;
    }


    /**
     * Records one call of the alert routine.
     *
     * @param unit the damaged unit
     */
    public void add(@NotNull Unit unit) {
        Event event = this.events.next();
        Sprite sprite = unit.getSpriteOrNull();

        event.poolIndex = unit.getIndex();
        event.owner     = unit.getOwnerIndex();
        event.unitType  = unit.getUnitId();
        event.positionX = sprite == null ? unit.getPositionX() : sprite.getX();
        event.positionY = sprite == null ? unit.getPositionY() : sprite.getY();
    }

    /**
     * Clears events while retaining allocated storage for subsequent frames.
     */
    public void clear() {
        this.events.clear();
    }
}

package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Retains the unit notifications that game code raised during the current game-loop iteration, for presentation.
 * <p>
 * The consumer applies replay, owner and speech mute checks before playing unit or race sounds and showing status text. Recording a call changes no
 * game state and draws no random number.
 * <p>
 * Game code fills the queue during a loop iteration; the replay session clears it before the next iteration's commands, and the client reads it after
 * each iteration without clearing it.
 */
public final class NotificationEvents {
    /**
     * The initial event capacity.
     */
    private static final int INITIAL_CAPACITY = 10;

    /**
     * The routine a notification calls.
     */
    public enum Kind {
        /**
         * A trained, hatched or merged unit is ready (its {@code units.dat} ready sound).
         */
        UNIT_READY,
        /**
         * A builder or building finished its construction work (race work-done sound).
         */
        WORK_DONE,
        /**
         * Research completed (race key {@code researchComplete}).
         */
        RESEARCH_COMPLETE,
        /**
         * An upgrade completed (race key {@code upgradeComplete}).
         */
        UPGRADE_COMPLETE,
        /**
         * An add-on was detached from its building.
         */
        ADDON_DETACHED,
        /**
         * A building or Drone landing was interrupted.
         */
        LANDING_INTERRUPTED
    }

    /**
     * One notification.
     */
    public static final class Event {
        private final @NotNull Kind kind;
        private final int poolIndex;
        private final int owner;

        /**
         * Creates an {@link Event} instance for a notification call.
         *
         * @param kind      the routine called
         * @param poolIndex the unit pool index
         * @param owner     the unit owner at the call
         */
        Event(@NotNull Kind kind, int poolIndex, int owner) {
            this.kind      = kind;
            this.poolIndex = poolIndex;
            this.owner     = owner;
        }

        /**
         * @return the routine called
         */
        public @NotNull Kind getKind() {
            return this.kind;
        }

        /**
         * @return the pool index of the unit the routine receives
         */
        public int getPoolIndex() {
            return this.poolIndex;
        }

        /**
         * @return the unit's owner at the call, which the routine compares with the local player
         */
        public int getOwner() {
            return this.owner;
        }
    }

    private final @NotNull FrameEventBuffer<Event> events;

    /**
     * Creates a {@link NotificationEvents} instance with event storage.
     */
    public NotificationEvents() {
        this.events = FrameEventBuffer.createAppending(INITIAL_CAPACITY);
    }

    /**
     * @return the number of retained events
     */
    public int getCount() {
        return this.events.getCount();
    }

    /**
     * @param index the event position
     * @return the event
     */
    public @NotNull Event get(int index) {
        return this.events.get(index);
    }


    /**
     * Records one call.
     *
     * @param kind the routine
     * @param unit the unit passed to it
     */
    public void raise(@NotNull Kind kind, @NotNull Unit unit) {
        this.events.add(new Event(kind, unit.getIndex(), unit.getOwnerIndex()));
    }

    /**
     * Discards the events of the previous game-loop iteration.
     */
    public void clear() {
        this.events.clear();
    }
}

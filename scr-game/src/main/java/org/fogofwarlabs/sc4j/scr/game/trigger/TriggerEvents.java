package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.presentation.FrameEventBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * Retains the presentation output of trigger actions for the local player until acknowledged by the client or reset by the producer.
 * <p>
 * Events describe drawing, audio and camera output for the local player. The Center View arrival callback is reported through
 * {@link TriggerContext#completeCenterView()}.
 * <p>
 * Trigger actions fill the queue; {@link ScrTriggerSystem#update} clears it when the next trigger update begins. The client
 * presents the queue with {@link #present}, which clears it after the complete presentation pass returns normally,
 * including events handled as normal no-ops, so a batch is presented once even when no trigger update follows. A
 * presentation failure retains the batch and any earlier effects. Readers that do not acknowledge the queue retain the
 * producer-owned reset lifecycle.
 */
public final class TriggerEvents {
    /**
     * Display Text Message or the text of a Transmission: {@code first} is the map string index, {@code second} is
     * {@code 1} when action flag {@code 0x4} forces display regardless of the subtitle option, {@code third} is a minimum
     * display time in milliseconds ({@code 0} for Display Text).
     */
    public static final int TEXT = 1;
    /**
     * Play WAV: {@code first} is the map string index of the sound file.
     */
    public static final int WAV = 2;
    /**
     * Center View: {@code first} and {@code second} are the location center pixel; the camera places the screen center
     * there within the map. {@code third} is {@code 1} when the owner's trigger waits for the scroll to arrive (a scroll
     * with an arrival callback), {@code 0} for an immediate move.
     */
    public static final int CENTER_VIEW = 3;
    /**
     * A resumed Center View finishes the running scroll at once.
     */
    public static final int CENTER_VIEW_FINISH = 4;
    /**
     * Minimap Ping: {@code first} and {@code second} are the location center pixel.
     */
    public static final int MINIMAP_PING = 5;
    /**
     * A trigger defeat eliminated a player (the elimination message): {@code first} is the player.
     */
    public static final int PLAYER_ELIMINATED = 7;
    /**
     * Pause Game or Unpause Game changed the trigger pause: {@code first} is {@code 1} when the game became paused (Pause
     * Game) and {@code 0} when it resumed (Unpause Game); menus and camera follow it.
     */
    public static final int GAME_PAUSE = 8;
    /**
     * Transmission or Talking Portrait talking portrait: {@code first} is the unit type, {@code second} and {@code third}
     * the speaking unit's sprite position ({@link #NO_POSITION} when no unit of the type is in the location, and for
     * Talking Portrait), {@code fourth} the duration in milliseconds.
     */
    public static final int TRANSMISSION = 9;
    /**
     * The local transmission skip: stop the transmission sound and hide the talking portrait; no operands.
     */
    public static final int TRANSMISSION_SKIP = 10;
    /**
     * Mute Unit Speech: for the local player ({@code first}, the current trigger player) and a record without the skip
     * flag {@code 0x10}, silences unit-ready and work-done notifications.
     */
    public static final int UNIT_SPEECH_MUTE = 11;
    /**
     * Unmute Unit Speech: for the local player ({@code first}), clears the speech-mute DWORD.
     */
    public static final int UNIT_SPEECH_UNMUTE = 12;
    /**
     * Reports an EUD access failure: {@code first} selects the general notice ({@code 0}) or the failed-region notice
     * ({@code 1}), and {@code second} is the bitwise complement of the failing trigger address.
     */
    public static final int EUD_FAILURE_NOTICE = 13;

    /**
     * The speaker position of a {@link #TRANSMISSION} without a speaking unit (Talking Portrait, no unit found).
     */
    public static final int NO_POSITION = -1;

    /**
     * The initial event slot count of the buffer.
     */
    private static final int INITIAL_CAPACITY = 16;

    /**
     * Receives the events of a presentation pass.
     */
    @FunctionalInterface
    public interface Presenter {
        /**
         * Presents one event.
         *
         * @param kind   the event kind constant
         * @param first  the first value
         * @param second the second value
         * @param third  the third value
         * @param fourth the fourth value
         */
        void present(int kind, int first, int second, int third, int fourth);
    }

    /**
     * One trigger presentation event.
     */
    private static final class Event {
        private int kind;
        private int first;
        private int second;
        private int third;
        private int fourth;

        private Event() {
            this.kind   = 0;
            this.first  = 0;
            this.second = 0;
            this.third  = 0;
            this.fourth = 0;
        }
    }

    private final @NotNull FrameEventBuffer<Event> events;

    /**
     * Creates a {@link TriggerEvents} instance with an empty event buffer.
     */
    public TriggerEvents() {
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
     * @return the event kind constant
     * @throws IndexOutOfBoundsException if the index is negative or not below {@link #getCount()}
     */
    public int getKind(int index) {
        return this.events.get(index).kind;
    }

    /**
     * @param index the event position
     * @return the first value of the event
     * @throws IndexOutOfBoundsException if the index is negative or not below {@link #getCount()}
     */
    public int getFirst(int index) {
        return this.events.get(index).first;
    }

    /**
     * @param index the event position
     * @return the second value of the event
     * @throws IndexOutOfBoundsException if the index is negative or not below {@link #getCount()}
     */
    public int getSecond(int index) {
        return this.events.get(index).second;
    }

    /**
     * @param index the event position
     * @return the third value of the event
     * @throws IndexOutOfBoundsException if the index is negative or not below {@link #getCount()}
     */
    public int getThird(int index) {
        return this.events.get(index).third;
    }

    /**
     * @param index the event position
     * @return the fourth value of the event
     * @throws IndexOutOfBoundsException if the index is negative or not below {@link #getCount()}
     */
    public int getFourth(int index) {
        return this.events.get(index).fourth;
    }


    /**
     * Appends an event with a zero fourth value.
     *
     * @param kind   the event kind constant
     * @param first  the first value
     * @param second the second value
     * @param third  the third value
     */
    void add(int kind, int first, int second, int third) {
        this.add(kind, first, second, third, 0);
    }

    /**
     * Appends an event.
     *
     * @param kind   the event kind constant
     * @param first  the first value
     * @param second the second value
     * @param third  the third value
     * @param fourth the fourth value
     */
    void add(int kind, int first, int second, int third, int fourth) {
        Event event = this.events.next();

        event.kind   = kind;
        event.first  = first;
        event.second = second;
        event.third  = third;
        event.fourth = fourth;
    }

    /**
     * Presents every retained event in order, then acknowledges the batch by clearing the queue.
     * <p>
     * The queue is cleared only after the complete pass returns normally; if the presenter fails, the batch is retained.
     *
     * @param presenter the receiver of each event
     */
    public void present(@NotNull Presenter presenter) {
        for (int index = 0; index < this.events.getCount(); index++) {
            Event event = this.events.get(index);

            presenter.present(event.kind, event.first, event.second, event.third, event.fourth);
        }

        this.clear();
    }

    /**
     * Clears events while retaining allocated storage for subsequent frames.
     */
    public void clear() {
        this.events.clear();
    }
}

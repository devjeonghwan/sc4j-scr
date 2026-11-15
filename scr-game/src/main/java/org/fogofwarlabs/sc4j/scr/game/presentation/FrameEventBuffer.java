package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Retains one presentation event queue: the events that game code raised since the queue was last cleared.
 * <p>
 * A {@linkplain #createReusing reusing} buffer preallocates mutable event slots and hands them out again after {@link #clear()}, so raising an event
 * allocates nothing once the buffer has grown to the busiest frame. An {@linkplain #createAppending appending} buffer stores immutable events. Each
 * queue documents who clears it and when; the buffer itself has no notion of frames.
 *
 * @param <E> the event type
 */
public final class FrameEventBuffer<E> {
    /**
     * The smallest initial capacity.
     */
    private static final int MINIMUM_CAPACITY = 1;
    /**
     * The factor by which a full buffer grows its storage.
     */
    private static final int GROWTH_FACTOR = 2;

    /**
     * Creates a {@link FrameEventBuffer} instance with reusable mutable slots.
     *
     * @param factory  the factory that creates an empty slot
     * @param capacity the initial slot count
     * @param <E>      the event type
     * @return the created {@link FrameEventBuffer} instance; fill events with {@link #next()}
     */
    public static <E> @NotNull FrameEventBuffer<E> createReusing(@NotNull Supplier<? extends E> factory, int capacity) {
        return new FrameEventBuffer<>(factory, capacity);
    }

    /**
     * Creates a {@link FrameEventBuffer} instance for immutable events.
     *
     * @param capacity the initial capacity
     * @param <E>      the event type
     * @return the created {@link FrameEventBuffer} instance; store events with {@link #add(Object)}
     */
    public static <E> @NotNull FrameEventBuffer<E> createAppending(int capacity) {
        return new FrameEventBuffer<>(null, capacity);
    }

    private final @Nullable Supplier<? extends E> factory;

    private @Nullable Object @NotNull [] events;
    private int count;

    private FrameEventBuffer(@Nullable Supplier<? extends E> factory, int capacity) {
        this.factory = factory;

        this.events = new Object[Math.max(MINIMUM_CAPACITY, capacity)];
        this.count  = 0;

        this.fill(0);
    }

    /**
     * @param index the event position
     * @return the event
     * @throws IndexOutOfBoundsException if {@code index} is not below {@link #getCount()}
     * @throws NullPointerException      if the slot at {@code index} holds no event
     */
    @SuppressWarnings("unchecked")
    public @NotNull E get(int index) {
        Object event = this.events[Objects.checkIndex(index, this.count)];

        if (event == null) {
            throw Exceptions.newNullPointerException();
        }

        return (E) event;
    }

    /**
     * @return the number of events
     */
    public int getCount() {
        return this.count;
    }


    /**
     * Takes the next slot of a reusing buffer; the caller overwrites every field.
     *
     * @return the slot, now counted as an event
     * @throws IllegalStateException if this is an appending buffer
     * @throws NullPointerException  if the next slot holds no event
     */
    @SuppressWarnings("unchecked")
    public @NotNull E next() {
        if (this.factory == null) {
            throw Exceptions.newIllegalStateException("An appending event buffer stores events with add().");
        }

        this.growIfFull();

        Object event = this.events[this.count++];

        if (event == null) {
            throw Exceptions.newNullPointerException();
        }

        return (E) event;
    }

    /**
     * Stores an event in an appending buffer.
     *
     * @param event the immutable event
     * @throws IllegalStateException if this is a reusing buffer
     */
    public void add(@NotNull E event) {
        if (this.factory != null) {
            throw Exceptions.newIllegalStateException("A reusing event buffer hands out slots with next().");
        }

        this.growIfFull();

        this.events[this.count++] = event;
    }

    /**
     * Discards the events from a position on; a reusing buffer keeps their slots for later events.
     *
     * @param size the number of events kept
     * @throws IndexOutOfBoundsException if {@code size} is negative or above {@link #getCount()}
     */
    public void truncate(int size) {
        int kept = Objects.checkIndex(size, this.count + 1);

        if (this.factory == null) {
            Arrays.fill(this.events, kept, this.count, null);
        }

        this.count = kept;
    }

    /**
     * Discards the events; a reusing buffer keeps its slots for later events.
     */
    public void clear() {
        if (this.factory == null) {
            Arrays.fill(this.events, 0, this.count, null);
        }

        this.count = 0;
    }

    private void growIfFull() {
        if (this.count == this.events.length) {
            int previousCapacity = this.events.length;

            this.events = Arrays.copyOf(this.events, previousCapacity * GROWTH_FACTOR);

            this.fill(previousCapacity);
        }
    }

    private void fill(int startIndex) {
        if (this.factory == null) {
            return;
        }

        for (int index = startIndex; index < this.events.length; index++) {
            this.events[index] = this.factory.get();
        }
    }
}

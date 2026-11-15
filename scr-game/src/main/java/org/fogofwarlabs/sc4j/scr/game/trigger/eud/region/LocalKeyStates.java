package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Local keyboard input exposed through classic regions: unsigned key-state bytes indexed by virtual-key code, and
 * separately supplied pressed flags for asynchronous reads. The byte state is unknown until supplied; reads then fail.
 * These values belong to the local interface and are independent of simulation state.
 */
public final class LocalKeyStates {
    /**
     * The mask of the low BYTE.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * The number of key-state bytes of the region.
     */
    public static final int SIZE = 0xFF;

    /**
     * The number of virtual-key codes.
     */
    private static final int KEY_CODE_COUNT = 256;


    private byte @Nullable [] states;

    private final boolean @NotNull [] asyncPressed;


    /**
     * Creates a {@link LocalKeyStates} instance whose key states are unknown and no key is down for asynchronous key-state
     * reads.
     */
    public LocalKeyStates() {
        this.states = null;

        this.asyncPressed = new boolean[KEY_CODE_COUNT];
    }


    /**
     * Sets the known state with every key released: all key-state bytes zero and no key down for asynchronous key-state
     * reads.
     */
    public void setAllReleased() {
        this.states = new byte[SIZE];

        Arrays.fill(this.asyncPressed, false);
    }

    /**
     * Sets the asynchronous pressed flag read for the keys {@code 0x30..0x3B}.
     *
     * @param key     the virtual-key code in {@code 0..0xFF}
     * @param pressed whether the key is down
     */
    public void setAsyncPressed(int key, boolean pressed) {
        this.asyncPressed[key] = pressed;
    }

    /**
     * @param key the virtual-key code in {@code 0..0xFF}
     * @return {@code 1} if the supplied asynchronous state reports the key down, {@code 0} otherwise
     * @throws UnsupportedOperationException if the key states are unknown
     */
    public int getAsyncPressed(int key) {
        this.requireStates();

        return this.asyncPressed[key] ? 1 : 0;
    }

    /**
     * Sets the key-state bytes, or {@code null} when unknown.
     *
     * @param states the {@link #SIZE} key-state bytes indexed by virtual-key code, copied, or {@code null}
     * @throws IllegalArgumentException if the length is not {@link #SIZE}
     */
    public void setStates(byte @Nullable [] states) {
        if (states != null && states.length != SIZE) {
            throw Exceptions.newIllegalArgumentException("Key states need {0} bytes.", SIZE);
        }

        this.states = states == null ? null : states.clone();
    }

    /**
     * Sets one key-state byte.
     *
     * @param key   the virtual-key code in {@code 0..0xFE}
     * @param value the state byte
     * @throws UnsupportedOperationException if the key states are unknown
     */
    public void setState(int key, int value) {
        this.requireStates()[key] = (byte) value;
    }

    /**
     * @param key the virtual-key code in {@code 0..0xFE}
     * @return the unsigned state byte
     * @throws UnsupportedOperationException if the key states are unknown
     */
    public int getState(int key) {
        return this.requireStates()[key] & BYTE_MASK;
    }

    /**
     * @return {@code true} if the key states are known, {@code false} otherwise
     */
    public boolean isKnown() {
        return this.states != null;
    }


    private byte @NotNull [] requireStates() {
        byte[] current = this.states;

        if (current == null) {
            throw Exceptions.newUnsupportedOperationException("The key states 0x596A18 are a local input (input manager, GetAsyncKeyState) " +
                                                              "that was not supplied.");
        }

        return current;
    }
}

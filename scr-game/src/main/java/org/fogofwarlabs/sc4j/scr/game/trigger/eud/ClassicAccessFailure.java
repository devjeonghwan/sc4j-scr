package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * Signals an EUD access whose address has no represented region or whose region cannot serve the offset.
 */
public final class ClassicAccessFailure extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int address;

    /**
     * Creates a {@link ClassicAccessFailure} instance.
     *
     * @param address the classic address of the failed access
     * @param message the reason
     */
    public ClassicAccessFailure(int address, @NotNull String message) {
        super(message);

        this.address = address;
    }

    /**
     * @return the classic address of the failed access
     */
    public int getAddress() {
        return this.address;
    }
}

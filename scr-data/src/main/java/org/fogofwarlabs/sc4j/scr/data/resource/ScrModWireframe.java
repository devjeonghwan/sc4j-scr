package org.fogofwarlabs.sc4j.scr.data.resource;

import org.jetbrains.annotations.NotNull;

/**
 * Names the wireframe that a mod ships in its jar for an added unit type, in the three views of the status panel: the single selection, a slot
 * of a group selection and a cargo slot. Each is a strip of square cells as in the installed wireframe sheets: cells {@code 0..3} are the white
 * masks of the four damage parts, cells {@code 4} and {@code 5} the shield outlines; the HD strips may hold further cells. The HD qualities draw
 * every view from the single-selection strip, as they do for installed wireframes.
 */
public final class ScrModWireframe {
    private final @NotNull ScrModSheetFrame single;
    private final @NotNull ScrModSheetFrame group;
    private final @NotNull ScrModSheetFrame transport;

    /**
     * Creates a {@link ScrModWireframe} instance.
     *
     * @param single    the strip of the single selection
     * @param group     the strip of a group selection slot
     * @param transport the strip of a cargo slot
     */
    public ScrModWireframe(@NotNull ScrModSheetFrame single, @NotNull ScrModSheetFrame group, @NotNull ScrModSheetFrame transport) {
        this.single    = single;
        this.group     = group;
        this.transport = transport;
    }

    /**
     * @return the strip of the single selection
     */
    public @NotNull ScrModSheetFrame getSingle() {
        return this.single;
    }

    /**
     * @return the strip of a group selection slot
     */
    public @NotNull ScrModSheetFrame getGroup() {
        return this.group;
    }

    /**
     * @return the strip of a cargo slot
     */
    public @NotNull ScrModSheetFrame getTransport() {
        return this.transport;
    }
}

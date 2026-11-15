package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction;

/**
 * Says whether a bounded update of the iscript interpreter (one with an instruction budget, as the frame updates and the
 * image preview run) runs an instruction or fails at it.
 */
public enum ScrPreviewSupport {
    /**
     * A bounded update runs the instruction.
     */
    ALWAYS,
    /**
     * A bounded update runs the instruction only with a unit context.
     */
    WITH_UNIT,
    /**
     * A bounded update fails at the instruction.
     */
    NEVER;

    /**
     * Checks whether a bounded update runs an instruction of this support.
     *
     * @param hasUnit whether the update has a unit context
     * @return {@code true} if a bounded update runs the instruction, {@code false} if it fails at it
     */
    public boolean isSupported(boolean hasUnit) {
        return this == ALWAYS || this == WITH_UNIT && hasUnit;
    }
}

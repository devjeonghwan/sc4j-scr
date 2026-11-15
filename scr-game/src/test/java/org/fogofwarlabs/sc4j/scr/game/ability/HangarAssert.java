package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

/**
 * Compares the hangar storage of a child unit with its expected parent.
 */
public final class HangarAssert {
    /**
     * Asserts that a child is stored in the inside list of its hangar parent with a hidden sprite.
     *
     * @param parent the expected hangar parent
     * @param child  the stored child
     */
    public static void assertStored(@NotNull Unit parent, @NotNull Unit child) {
        Assertions.assertSame(parent, child.getHangarParent());
        Assertions.assertFalse(child.isHangarOutside());
        Assertions.assertNotEquals(0, child.requireSprite().getFlags() & Sprite.FLAG_HIDDEN);
    }


    /**
     * Creates a {@link HangarAssert} instance.
     */
    private HangarAssert() {
    }
}

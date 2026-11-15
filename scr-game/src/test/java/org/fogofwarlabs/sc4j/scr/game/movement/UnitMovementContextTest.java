package org.fogofwarlabs.sc4j.scr.game.movement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that a {@link UnitMovement.Context} requires set values, keeps them across source changes and truncates them.
 */
final class UnitMovementContextTest {
    @Test
    void context_requires_set_values_and_keeps_them_when_source_byte_changes() {
        UnitMovement.Context context = new UnitMovement.Context(255, 123);

        Assertions.assertThrows(IllegalStateException.class, context::getPositionX);
        Assertions.assertThrows(IllegalStateException.class, context::getFixedPositionY);
        Assertions.assertThrows(IllegalStateException.class, context::getPostMovementFlags);

        context.setPositionX(65534);
        context.setFixedPositionY(Integer.MIN_VALUE);
        context.setPostMovementFlags(0x134);

        Assertions.assertEquals(-2, context.getPositionX());
        Assertions.assertEquals(Integer.MIN_VALUE, context.getFixedPositionY());
        Assertions.assertEquals(0x34, context.getPostMovementFlags());

        context.setSourceByte(256);

        Assertions.assertEquals(0, context.getSourceByte());
        Assertions.assertEquals(123, context.getResult());
        Assertions.assertEquals(-2, context.getPositionX());
        Assertions.assertThrows(IllegalStateException.class, context::getPositionY);
    }

    @Test
    void context_truncates_values_to_their_integer_widths() {
        UnitMovement.Context context = new UnitMovement.Context(0, 0);

        context.setPositionX(0xFFFF);
        context.setPositionY(0x8000);
        context.setFixedPositionX(0x80123456);
        context.setStartingMovement(0xFE);
        context.setStoppingMovement(256);

        Assertions.assertEquals(-1, context.getPositionX());
        Assertions.assertEquals(Short.MIN_VALUE, context.getPositionY());
        Assertions.assertEquals(0x80123456, context.getFixedPositionX());
        Assertions.assertEquals(254, context.getStartingMovement());
        Assertions.assertTrue(context.getStartingMovement() != 0);
        Assertions.assertFalse(context.getStoppingMovement() != 0);
    }
}

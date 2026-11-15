package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that every native button function address names exactly one button condition or one button action.
 */
final class ButtonConditionTest {
    @Test
    void of_native_address_should_map_each_function_to_one_condition_or_action() {
        for (ButtonCondition condition : ButtonCondition.values()) {
            Assertions.assertSame(condition, ButtonCondition.ofNativeAddress(condition.getNativeAddress()));
            Assertions.assertNull(ButtonAction.ofNativeAddress(condition.getNativeAddress()));
        }

        for (ButtonAction action : ButtonAction.values()) {
            Assertions.assertSame(action, ButtonAction.ofNativeAddress(action.getNativeAddress()));
        }
    }
}

package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerEvents;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compares the presentation events of a trigger update with the expected events.
 */
public final class TriggerEventsAssert {
    /**
     * Asserts that the events hold exactly the given EUD failure notices, in order.
     *
     * @param events the presentation events of the last trigger update
     * @param codes  the expected values the notices show, the complements of the failing trigger addresses
     */
    public static void assertEudFailureNotices(@NotNull TriggerEvents events, int @NotNull ... codes) {
        List<Integer> notices = new ArrayList<>();

        for (int index = 0; index < events.getCount(); index++) {
            if (events.getKind(index) == TriggerEvents.EUD_FAILURE_NOTICE) {
                notices.add(events.getSecond(index));
            }
        }

        Assertions.assertEquals(Arrays.stream(codes).boxed().toList(), notices, "the EUD failure notices");
    }


    /**
     * Creates a {@link TriggerEventsAssert} instance.
     */
    private TriggerEventsAssert() {
    }
}

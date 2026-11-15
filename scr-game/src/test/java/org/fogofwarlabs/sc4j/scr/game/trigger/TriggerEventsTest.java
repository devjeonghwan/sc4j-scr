package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a presentation pass presents each retained trigger event once and keeps the batch when the pass fails.
 */
final class TriggerEventsTest {
    /**
     * The failed-region selector of an EUD failure notice.
     */
    private static final int FAILED_REGION_NOTICE = 1;
    /**
     * The value an EUD failure notice shows, the complement of a failing trigger address.
     */
    private static final int FAILURE_CODE = 0x7FFED3A7;
    /**
     * The event kind of an EUD failure notice.
     */
    private static final int EUD_FAILURE_NOTICE_EVENT = 13;


    @Test
    void present_should_present_the_failure_notice_once_when_no_trigger_update_follows() {
        TriggerEvents events = new TriggerEvents();
        List<Integer> notices = new ArrayList<>();
        TriggerEvents.Presenter presenter = (kind, first, second, third, fourth) -> {
            if (kind == TriggerEvents.EUD_FAILURE_NOTICE) {
                notices.add(second);
            }
        };

        events.add(TriggerEvents.EUD_FAILURE_NOTICE, FAILED_REGION_NOTICE, FAILURE_CODE, 0);

        // The failure stops the game: later client updates present the queue again without a trigger update in between.
        events.present(presenter);
        events.present(presenter);

        Assertions.assertEquals(List.of(FAILURE_CODE), notices);
        Assertions.assertEquals(0, events.getCount());
    }

    @Test
    void present_should_retain_the_batch_when_presentation_fails() {
        TriggerEvents events = new TriggerEvents();
        IllegalStateException failure = Exceptions.newIllegalStateException("The presentation failed.");
        TriggerEvents.Presenter presenter = (kind, first, second, third, fourth) -> {
            throw failure;
        };

        events.add(TriggerEvents.EUD_FAILURE_NOTICE, FAILED_REGION_NOTICE, FAILURE_CODE, 0);

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> events.present(presenter));

        Assertions.assertSame(failure, thrown);
        Assertions.assertEquals(1, events.getCount());
        Assertions.assertEquals(EUD_FAILURE_NOTICE_EVENT, events.getKind(0));
        Assertions.assertEquals(FAILURE_CODE, events.getSecond(0));
    }
}

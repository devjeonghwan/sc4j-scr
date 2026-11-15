package org.fogofwarlabs.sc4j.scr.game.production;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that every requirement test result word has one shared engine result with its native meaning.
 */
final class ScrRequirementResultTest {
    private static final int FAILURE_CODE = 0x3A;

    @Test
    void of_should_return_one_shared_result_per_native_word() {
        Assertions.assertSame(ScrRequirementResult.of(FAILURE_CODE), ScrRequirementResult.of(FAILURE_CODE));
        Assertions.assertNotSame(ScrRequirementResult.of(FAILURE_CODE), ScrRequirementResult.of(FAILURE_CODE | ScrRequirementSystem.DISABLED));
        Assertions.assertTrue(ScrRequirementResult.of(0).isMet());
        Assertions.assertFalse(ScrRequirementResult.of(FAILURE_CODE).isMet());
        Assertions.assertEquals(FAILURE_CODE, ScrRequirementResult.of(FAILURE_CODE | ScrRequirementSystem.DISABLED).getFailureCode());
        Assertions.assertEquals(FAILURE_CODE | ScrRequirementSystem.DISABLED,
                                ScrRequirementResult.of(FAILURE_CODE | ScrRequirementSystem.DISABLED).getResult());
        Assertions.assertEquals(0, ScrRequirementResult.of(ScrRequirementSystem.FAILED_WITHOUT_CODE).getFailureCode());
        Assertions.assertThrows(IllegalArgumentException.class, () -> ScrRequirementResult.of(ScrRequirementSystem.FAILED_WITHOUT_CODE + 1));
    }
}

package org.fogofwarlabs.screcorder.sample.unit;

import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that the class-dependent unit union decodes to process-independent values for each unit class.
 */
final class UnitUnionDecodingTest {
    private static final long POOL_ADDRESS = 0x7FF461300010L;
    private static final int POOL_SLOTS = 10;
    private static final long FIFTH_UNIT_ADDRESS = POOL_ADDRESS + 4L * StarCraftLayout.UNIT_STRIDE;
    private static final int TYPE_SCV = 7;
    private static final int TYPE_GHOST = 1;
    private static final int TYPE_PYLON = 156;
    private static final int TYPE_MINERAL_FIELD = 176;

    private static final int UNION_WORDS = 0;
    private static final int UNION_UNIT = 1;
    private static final int UNION_SPRITE = 2;
    private static final long UNRESOLVED_UNIT = -1;

    @Test
    void get_union_kind_should_follow_the_unit_class() {
        Assertions.assertEquals(UNION_UNIT, UnitsSampleDefinition.getUnionKind(TYPE_SCV));
        Assertions.assertEquals(UNION_SPRITE, UnitsSampleDefinition.getUnionKind(TYPE_GHOST));
        Assertions.assertEquals(UNION_SPRITE, UnitsSampleDefinition.getUnionKind(TYPE_PYLON));
        Assertions.assertEquals(UNION_WORDS, UnitsSampleDefinition.getUnionKind(TYPE_MINERAL_FIELD));
    }

    @Test
    void decode_union_should_replace_pointers_by_process_independent_values() throws IOException {
        Assertions.assertEquals(5, UnitsSampleDefinition.decodeUnion(TYPE_SCV, FIFTH_UNIT_ADDRESS, POOL_ADDRESS, POOL_SLOTS));
        Assertions.assertEquals(0, UnitsSampleDefinition.decodeUnion(TYPE_SCV, 0, POOL_ADDRESS, POOL_SLOTS));
        // The nuke dot sprite pointer of a ghost differs per process; only its presence is recorded.
        Assertions.assertEquals(1, UnitsSampleDefinition.decodeUnion(TYPE_GHOST, 0x7FF4614A0740L, POOL_ADDRESS, POOL_SLOTS));
        Assertions.assertEquals(1, UnitsSampleDefinition.decodeUnion(TYPE_GHOST, 0x7FF40C650800L, POOL_ADDRESS, POOL_SLOTS));
        Assertions.assertEquals(0, UnitsSampleDefinition.decodeUnion(TYPE_GHOST, 0, POOL_ADDRESS, POOL_SLOTS));
        Assertions.assertEquals(0x0001000200031500L,
                                UnitsSampleDefinition.decodeUnion(TYPE_MINERAL_FIELD, 0x0001000200031500L, POOL_ADDRESS, POOL_SLOTS));
        Assertions.assertEquals(UNRESOLVED_UNIT, UnitsSampleDefinition.decodeUnion(TYPE_SCV, POOL_ADDRESS + 3, POOL_ADDRESS, POOL_SLOTS));
        Assertions.assertEquals(UNRESOLVED_UNIT,
                                UnitsSampleDefinition.decodeUnion(TYPE_SCV, POOL_ADDRESS - StarCraftLayout.UNIT_STRIDE, POOL_ADDRESS, POOL_SLOTS));
    }
}

package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.scr.data.type.constant.RequirementOpcodes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the words the requirement script builder writes for the native requirement opcodes.
 */
final class ScrRequirementScriptTest {
    @Test
    void builder_writes_the_owned_burrow_and_expansion_requirements_as_native_words() {
        ScrRequirementScript script = new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HATCHERY)
                                                                        .requireOwned(ScrUnits.TERRAN_MARINE)
                                                                        .requireNotBurrowed()
                                                                        .or()
                                                                        .requireExpansion()
                                                                        .build();

        Assertions.assertArrayEquals(new int[]{RequirementOpcodes.CURRENT_UNIT, ScrUnitIds.ZERG_HATCHERY,
                                               RequirementOpcodes.MUST_HAVE, ScrUnitIds.TERRAN_MARINE,
                                               RequirementOpcodes.NOT_BURROWED, RequirementOpcodes.OR, RequirementOpcodes.EXPANSION,
                                               RequirementOpcodes.END},
                                     script.copyWords());
    }
}

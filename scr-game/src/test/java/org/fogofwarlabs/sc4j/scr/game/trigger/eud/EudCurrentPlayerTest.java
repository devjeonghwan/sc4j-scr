package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicHostValueRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks reads and writes of the current trigger player DWORD.
 */
final class EudCurrentPlayerTest {
    @Test
    void current_player_region_reads_and_writes_the_trigger_player_dword() {
        TriggerContext context = new TriggerContext();
        ClassicRegion region = ClassicHostValueRegion.createCurrentPlayerRegion(context);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(region);

        Assertions.assertEquals(0, space.readDword(ClassicHostValueRegion.CURRENT_PLAYER), "The .bss value before the first evaluation.");

        context.writeCurrentPlayerDword(3);

        Assertions.assertEquals(3, space.readDword(ClassicHostValueRegion.CURRENT_PLAYER));

        space.writeDword(ClassicHostValueRegion.CURRENT_PLAYER, 6);

        Assertions.assertEquals(6, context.getCurrentPlayerIndex(), "Conditions and actions read the written player.");

        region.writeByte(1, 1);

        Assertions.assertEquals(0x106, context.readCurrentPlayerDword());
    }
}

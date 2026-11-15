package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that DAT, context and host-table writes remain within one game.
 */
@RequiresInstallation
final class EudGameScopeTest {
    @Test
    void second_game_sees_installed_values_after_the_first_game_writes() throws IOException {
        try (GameData installed = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game first = Game.create(installed.createGameCopy(), new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
            int marineHitPoints = installed.getUnits().get("Hit Points", ScrUnitIds.TERRAN_MARINE);
            ClassicAddressSpace space = new ClassicAddressSpace();
            GameData firstDatData = first.getGameData();

            Assertions.assertNotNull(firstDatData);

            space.addAll(ClassicDatRegion.create(firstDatData));
            space.add(new ClassicHostTableRegion(0x515B70, first.getUnitDamage().getDamageMultipliers()));

            GameData firstOverlayData = first.getGameData();

            Assertions.assertNotNull(firstOverlayData);

            space.add(ClassicHostByteTableRegion.createImageDamageOverlayRegion(firstOverlayData));
            space.add(new ClassicStorageRegion(LocalInterfaceState.SCROLL_STEPS_ADDRESS,
                                               first.getInterfaceLink().getLocalInterface().getScrollStepBytes()));

            space.writeDword(0x662350, 1234 << 8);
            space.writeDword(0x515B70 + 13 * 4, 7);
            space.writeDword(0x5240D0, 0x09090909);
            space.writeDword(0x513B68, 0x40404040);
            first.getGameContext().setStatTextTable(new byte[]{1, 0, 4, 0, 'x', 0});

            GameData writtenGameData = first.getGameData();

            Assertions.assertNotNull(writtenGameData);

            Assertions.assertEquals(1234 << 8, writtenGameData.getUnits().get("Hit Points", ScrUnitIds.TERRAN_MARINE));

            Game second = Game.create(installed.createGameCopy(), new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

            GameData secondDatData = second.getGameData();

            Assertions.assertNotNull(secondDatData);

            Assertions.assertEquals(marineHitPoints, secondDatData.getUnits().get("Hit Points", ScrUnitIds.TERRAN_MARINE));
            Assertions.assertEquals(marineHitPoints,
                                    installed.getUnits().get("Hit Points", ScrUnitIds.TERRAN_MARINE),
                                    "The installed tables are never written.");
            Assertions.assertEquals(192, second.getUnitDamage().getDamageMultipliers()[13]);

            int installedOverlayCount = installed.getImageDamageOverlayCount(0);
            GameData secondOverlayData = second.getGameData();

            Assertions.assertNotNull(secondOverlayData);

            Assertions.assertEquals(installedOverlayCount, secondOverlayData.getImageDamageOverlayCount(0));
            Assertions.assertEquals(0, second.getInterfaceLink().getLocalInterface().getScrollStep(0, 0));
            Assertions.assertNull(second.getGameContext().getStatTextTable(), "The committed stat_txt belongs to its game.");
            Assertions.assertFalse(second.getGameContext().getHostButtonSets().isReplaced());
        }
    }
}

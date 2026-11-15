package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicDatRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks per-game DAT arrays, interleaved fields and validated shadows.
 */
@RequiresInstallation
final class EudDatRegionTest {
    private static final int UNITS_HIT_POINTS = 0x662350;
    private static final int UNITS_PLACEMENT_BOX = 0x662860;
    private static final int UNITS_GRAPHICS = 0x6644F8;
    private static final int UNITS_HEALTH_BAR = 0x665E50;
    private static final int IMAGES_ISCRIPT = 0x66EC48;

    @Test
    void dat_arrays_write_the_game_copy_and_shadow_invalid_values() throws IOException {
        try (GameData installed = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            GameData game = installed.createGameCopy();
            ClassicAddressSpace space = new ClassicAddressSpace();

            space.addAll(ClassicDatRegion.create(game));

            Assertions.assertEquals(40 << 8, space.readDword(UNITS_HIT_POINTS));

            space.writeDword(UNITS_HIT_POINTS, 100 << 8);

            Assertions.assertEquals(100 << 8, game.getUnits().get("Hit Points", ScrUnitIds.TERRAN_MARINE), "The write changes the game's array.");
            Assertions.assertEquals(40 << 8, installed.getUnits().get("Hit Points", ScrUnitIds.TERRAN_MARINE),
                                    "The installed tables stay unchanged.");

            Assertions.assertEquals(game.getUnits().get("Placement Box Width", ScrUnitIds.TERRAN_MARINE) |
                                    game.getUnits().get("Placement Box Height", ScrUnitIds.TERRAN_MARINE) << 16,
                                    space.readDword(UNITS_PLACEMENT_BOX));

            int healthBar = space.readDword(UNITS_HEALTH_BAR);

            space.writeDword(UNITS_HEALTH_BAR, 0x01020304);

            Assertions.assertEquals(healthBar, space.readDword(UNITS_HEALTH_BAR));

            int graphics = space.readDword(UNITS_GRAPHICS);

            space.writeDword(UNITS_GRAPHICS, graphics & 0x00FFFFFF | 0xD1 << 24);

            Assertions.assertEquals(graphics & 0x00FFFFFF | 0xD1 << 24, space.readDword(UNITS_GRAPHICS), "Reads return the flagged shadow.");
            Assertions.assertEquals(graphics >>> 24, game.getUnits().get("Graphics", ScrUnitIds.TERRAN_GOLIATH), "The game keeps its flingy.");

            space.writeDword(UNITS_GRAPHICS, graphics & 0x00FFFFFF | 0x05 << 24);

            Assertions.assertEquals(5, game.getUnits().get("Graphics", ScrUnitIds.TERRAN_GOLIATH), "A valid DWORD is stored and clears the shadow.");

            space.writeDword(IMAGES_ISCRIPT, 0x19B);

            Assertions.assertEquals(0x19B, space.readDword(IMAGES_ISCRIPT));
            Assertions.assertNotEquals(0x19B, game.getImages().get("Iscript", 0));

            space.writeDword(IMAGES_ISCRIPT, 7);

            Assertions.assertEquals(7, game.getImages().get("Iscript", 0));
        }
    }
}

package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Checks user-selectable melee-unit slots against the expected header bytes.
 */
final class UseMapSettingsMeleeUnitsTest {
    private static final int MELEE_UNIT_FLAGS_OFFSET = 0x271;
    private static final int MELEE_UNIT_FLAG_COUNT = 8;

    @Test
    void user_selectable_slots_get_melee_units_in_the_replay_header() throws IOException {
        Path map = GameTestResources.getPath(GameTestResources.MAP_EUD_COMPUTER);

        MeleeGameSetup setup = MeleeGameSetup.loadUseMapSettings(map, "asd", "zerg", 0, GameSpeed.FASTEST);

        Assertions.assertArrayEquals(new boolean[]{true, true, true, false, false, false, false, false}, setup.getUseMapSettingsMeleeUnitPlayers());

        byte[] header = ReplayFile.readHeaderSection(new LiveGame(setup, 1).writeReplay(0, 0));

        Assertions.assertArrayEquals(new byte[]{1, 1, 1, 0, 0, 0, 0, 0},
                                     Arrays.copyOfRange(header, MELEE_UNIT_FLAGS_OFFSET, MELEE_UNIT_FLAGS_OFFSET + MELEE_UNIT_FLAG_COUNT));
    }
}

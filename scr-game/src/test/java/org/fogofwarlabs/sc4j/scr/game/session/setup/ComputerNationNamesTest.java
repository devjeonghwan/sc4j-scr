package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Checks computer names formed from resolved race and color entries.
 */
@RequiresInstallation
final class ComputerNationNamesTest {
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int SEED = 0x1234;

    @Test
    void resolve_should_name_computers_by_race_and_color_entry() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, "(4)Lost Civilization.scm");

        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        MeleeGameSetup setup = new MeleeGameSetup(mapData,
                                                  PLAYER_NAME, ScrRaceIds.TERRAN, 0,
                                                  new int[]{ScrRaceIds.ZERG, ScrRaceIds.PROTOSS},
                                                  GameSpeed.FASTEST);

        setup.setNationNames((race, index) -> race + "_" + index);

        int[] colors = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];
        MeleeGameSetup.PlayerRecord[] records = setup.resolve(SEED, colors);
        int named = 0;

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            MeleeGameSetup.PlayerRecord record = records[slot];

            if (record.getType() == PlayerTypes.COMPUTER) {
                String race = record.getRace() == ScrRaceIds.ZERG ? "ZERG" : record.getRace() == ScrRaceIds.PROTOSS ? "PROTOSS" : "TERRAN";

                Assertions.assertEquals(race + "_" + colors[slot], record.getName());

                named++;
            } else if (record.getType() == MeleeGameSetup.TYPE_HUMAN) {
                Assertions.assertEquals(PLAYER_NAME, record.getName(), "Humans keep their name.");
            }
        }

        Assertions.assertEquals(2, named);

        // The same seed names the same computers on every client.
        int[] again = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];
        MeleeGameSetup.PlayerRecord[] second = setup.resolve(SEED, again);

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            Assertions.assertEquals(records[slot].getName(), second[slot].getName());
        }
    }
}

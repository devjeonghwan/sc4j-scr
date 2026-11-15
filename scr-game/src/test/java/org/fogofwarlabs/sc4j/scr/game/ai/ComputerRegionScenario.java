package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.game.save.SaveReferences;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Prepares computer region records for tests in other packages.
 */
public final class ComputerRegionScenario {
    /**
     * The unit handle of the ground target in a saved region record.
     */
    private static final int SAVED_GROUND_TARGET = 0x20;

    /**
     * Sets the ground target of one owner's region record. The owner's records are rewritten through their saved region
     * section, so every other record field stays as it was.
     *
     * @param game   the game whose computer-player phase holds the records
     * @param owner  the owner in {@code 0..7}
     * @param region the region index
     * @param target the ground target
     */
    public static void setGroundTarget(@NotNull Game game, int owner, int region, @NotNull Unit target) {
        ComputerPlayerPhase phase = game.getComputerPlayerPhase();

        Assertions.assertNotNull(phase);

        ComputerRegions regions = phase.getRegions();
        SaveReferences references = new SaveReferences(game);
        ByteBuffer saved = ByteBuffer.allocate(regions.getRegionCount() * ComputerRegions.SAVED_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        regions.writeSaveRecords(saved, owner, references);
        saved.putInt(region * ComputerRegions.SAVED_RECORD_SIZE + SAVED_GROUND_TARGET, references.encodeUnit(target));
        regions.readSaveRecords(saved, owner, phase.getMilitary().getMilitaryRecords(), references);
    }


    /**
     * Creates a {@link ComputerRegionScenario} instance.
     */
    private ComputerRegionScenario() {
    }
}
